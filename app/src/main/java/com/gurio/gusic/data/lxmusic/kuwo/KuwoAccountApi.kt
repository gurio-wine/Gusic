package com.gurio.gusic.data.lxmusic.kuwo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.gurio.gusic.data.lxmusic.LxPlaylist
import com.gurio.gusic.data.lxmusic.LxSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLDecoder

/**
 * 酷我账号资料读取：基于 WebView 登录后保存在 [KuwoCookieStore] 的 Cookie。
 *
 * www.kuwo.cn 的 api/www/ 下接口族现在要求 `Secret` 头（由 cookie
 * `Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324` 的值经 LCG 流 XOR 算出，
 * 服务端只校验 Secret 能解密回请求 Cookie 中的同名值，故 cookie 缺失时可自造一对）；
 * 旧的 csrf=kw_token 头已被官网弃用，保留仅为兼容。接口失败或无登录态字段时，
 * 退回直接解析 Cookie：昵称 uname3/username/nickName、头像 pic3/pic——这些值可能是
 * 多重 URL 编码，且含老式 escape() 的 %uXXXX UTF-16 码元，
 * 需先还原码元再 URLDecoder.decode，循环最多 3 次直至稳定。
 */
internal class KuwoAccountApi(
    private val okHttpClient: OkHttpClient,
    private val cookieStore: KuwoCookieStore,
) {
    companion object {
        private const val TAG = "KuwoAccountApi"
        private const val USER_INFO_URL = "https://www.kuwo.cn/api/www/user/userInfo"
        private const val PLA_SVC_PAGE_SIZE = 100
        private const val FAVORITE_MAX_PAGES = 20
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /** Secret 头对应的 cookie 名（由 www.kuwo.cn 首页 Set-Cookie 下发，domain=.kuwo.cn）。 */
        internal const val SECRET_COOKIE_NAME = "Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324"

        // Secret 流密码常量：官网 bundle 按 cookie 名折叠出的固定值（seed 即折叠终值），
        // 与 walkman-tv KuwoSecret、MusicBot-Go session.go 的整数移植一致
        private const val SECRET_LCG_SEED = 59910100L
        private const val SECRET_LCG_MULTIPLIER = 9253L
        private const val SECRET_LCG_INCREMENT = 23L
        private const val SECRET_LCG_MODULUS = 2147483647L

        /**
         * 酷我网关 Secret 头：LCG 流 XOR cookie 值逐字符输出小写 hex，末尾追加 8 位
         * 十进制随机数的 8 位 hex。nonce 取 8 位十进制是为了与官网折叠语义一致
         * （过短的 nonce 会折叠出不同种子）。
         */
        internal fun buildKuwoSecret(cookieValue: String, nonce: Long = (10000000L..99999999L).random()): String {
            var state = SECRET_LCG_SEED
            val hex = StringBuilder(cookieValue.length * 2 + 8)
            for (ch in cookieValue) {
                state = (SECRET_LCG_MULTIPLIER * state + SECRET_LCG_INCREMENT) % SECRET_LCG_MODULUS
                val key = (state * 255L / SECRET_LCG_MODULUS).toInt()
                hex.append((ch.code xor key).toString(16).padStart(2, '0'))
            }
            return hex.append(nonce.toString(16).padStart(8, '0')).toString()
        }


        /** Cookie 串解析为 name→value；同名后出现者覆盖前者。 */
        internal fun parseCookieMap(cookie: String?): Map<String, String> {
            if (cookie.isNullOrBlank()) return emptyMap()
            val map = LinkedHashMap<String, String>()
            cookie.split(';').forEach { part ->
                val name = part.substringBefore('=').trim()
                val value = part.substringAfter('=', "").trim()
                if (name.isNotEmpty()) map[name] = value
            }
            return map
        }

        /**
         * 多重 URL 编码解码（最多 3 轮）：每轮先把 %uXXXX 还原为对应 UTF-16 码元
         * （等价 JS unescape 的行为，代理对按出现顺序拼接），再走标准 URLDecoder；
         * 值稳定（不再变化）时提前返回。
         */
        internal fun decodeKuwoCookieValue(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var value: String = raw
            repeat(3) {
                val next = decodeOnce(value)
                if (next == value) return value
                value = next
            }
            return value
        }

        private fun decodeOnce(value: String): String {
            val sb = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                if (ch == '%' && i + 5 < value.length && value[i + 1] == 'u') {
                    val code = value.substring(i + 2, i + 6).toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 6
                        continue
                    }
                }
                sb.append(ch)
                i++
            }
            return runCatching { URLDecoder.decode(sb.toString().replace("+", "%20"), "UTF-8") }
                .getOrDefault(sb.toString())
        }

        private fun JsonObject.strOf(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }
                ?.takeIf(String::isNotBlank)

        /**
         * 重写 Cookie：剔除全部 [SECRET_COOKIE_NAME] 条目后只追加一条与 Secret 输入
         * 一致的值。服务端网关按 Cookie 头首条同名 cookie 校验 Secret，多条同名不同值
         * （WebView 采集拼接出的 host-only 轮换条目 + 登录写入的 domain 条目）会导致
         * “首条 ≠ Secret 输入”而必然被拒。
         */
        internal fun rebuildCookieWithSecret(cookie: String, value: String): String = buildString {
            cookie.split(';')
                .filter { part -> part.trim().substringBefore('=').trim() != SECRET_COOKIE_NAME }
                .forEach { part ->
                    if (part.isNotBlank()) append("$part;")
                }
            if (isNotEmpty()) append(" ")
            append("$SECRET_COOKIE_NAME=$value")
        }
    }

    /**
     * 拉取昵称+头像。Cookie 为空返回 null；主接口 code==200 时取 data.uname / data.pic，
     * 否则退回 Cookie 兜底字段；两者皆取不到返回 null。
     */
    suspend fun fetchProfile(): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie()?.takeIf(String::isNotBlank) ?: return@withContext null
        val cookies = parseCookieMap(cookie)
        fetchUserInfoApi(cookie, cookies) ?: fetchFromCookie(cookies)
    }

    /**
     * 为 api/www 族请求补齐 Secret 鉴权：返回完整 Cookie 串与对应 Secret 头。
     * cookie 里没有 [SECRET_COOKIE_NAME]（或值为空）时自造 32 位随机值。
     * 无论是否已有同名条目都重写 Cookie：剔除全部同名条目后只追加一条与 Secret
     * 输入一致的值——服务端网关按 Cookie 头首条同名 cookie 校验 Secret，WebView
     * 采集拼接出的多条同名不同值（host-only 轮换条目 + 登录写入的 domain 条目）
     * 会导致“首条 ≠ Secret 输入”而必然被拒（实测 "The request is illegal!"）。
     */
    private fun secretPair(cookie: String, cookies: Map<String, String>): Pair<String, String> {
        val existing = cookies[SECRET_COOKIE_NAME]?.takeIf(String::isNotBlank)
        val value = existing ?: buildString {
            val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            repeat(32) { append(chars.random()) }
        }
        return rebuildCookieWithSecret(cookie, value) to buildKuwoSecret(value)
    }

    private fun fetchUserInfoApi(cookie: String, cookies: Map<String, String>): Pair<String?, String?>? {
        val (fullCookie, secret) = secretPair(cookie, cookies)
        val request = Request.Builder()
            .url(USER_INFO_URL.toHttpUrl().newBuilder()
                .addQueryParameter("httpsStatus", "1")
                .addQueryParameter("plat", "web_www")
                .addQueryParameter("reqId", java.util.UUID.randomUUID().toString())
                .build())
            .header("User-Agent", USER_AGENT)
            .header("Cookie", fullCookie)
            .header("Secret", secret)
            .header("csrf", cookies["kw_token"].orEmpty())
            .header("Referer", "https://www.kuwo.cn/")
            .get()
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = runCatching { JsonParser.parseString(response.body?.string().orEmpty()) }
                    .getOrNull() as? JsonObject ?: return@use null
                if (root.strOf("code") != "200") {
                    // 无 Secret/被反爬拒绝时网关回 {"success":false,"message":"The request is illegal!"}（无 code 字段）
                    Timber.w("$TAG: userInfo code=%s success=%s msg=%s",
                        root.strOf("code"), root.strOf("success"), root.strOf("message"))
                    return@use null
                }
                val data = root.get("data") as? JsonObject ?: return@use null
                val nick = data.strOf("uname")
                val avatar = data.strOf("pic")
                if (nick.isNullOrBlank() && avatar.isNullOrBlank()) return@use null
                nick to avatar
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: userInfo request failed")
            null
        }
    }

    private fun fetchFromCookie(cookies: Map<String, String>): Pair<String?, String?>? {
        val nick = decodeKuwoCookieValue(
            cookies["uname3"] ?: cookies["username"] ?: cookies["nickName"],
        )?.takeIf(String::isNotBlank)
        val avatar = decodeKuwoCookieValue(cookies["pic3"] ?: cookies["pic"])?.takeIf(String::isNotBlank)
        if (nick.isNullOrBlank() && avatar.isNullOrBlank()) return null
        return nick to avatar
    }

    /**
     * pl.svc getlistbyuid：拉取登录用户歌单列表的原始 plist 数组
     * （含 type=="MYFAVORITE" 的“我喜欢”系统歌单条目）。
     * 未登录（无 userid cookie）/请求失败/result!=ok 返回 null。
     */
    private suspend fun requestPlsList(cookie: String, cookies: Map<String, String>): JsonArray? =
        withContext(Dispatchers.IO) {
            val uid = cookies["userid"]?.takeIf(String::isNotBlank) ?: return@withContext null
            val request = Request.Builder()
                .url("https://nplserver.kuwo.cn/pl.svc".toHttpUrl().newBuilder()
                    .addQueryParameter("op", "getlistbyuid")
                    .addQueryParameter("bigid", "1")
                    .addQueryParameter("uid", uid)
                    .addQueryParameter("encode", "utf8")
                    .addQueryParameter("pn", "0")
                    .addQueryParameter("rn", "1000")
                    .addQueryParameter("loginUid", uid)
                    .addQueryParameter("loginSid", cookies["sid"].orEmpty())
                    .addQueryParameter("plat", "ar")
                    .addQueryParameter("prod", "kwplayer_ar")
                    .addQueryParameter("newver", "3")
                    .build())
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .header("Referer", "https://www.kuwo.cn/")
                .get()
                .build()
            val root = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: pl.svc getlistbyuid http %s", response.code)
                        return@use null
                    }
                    runCatching { JsonParser.parseString(response.body?.string().orEmpty()) }
                        .getOrNull() as? JsonObject
                }
            }.getOrElse { e ->
                Timber.w(e, "$TAG: pl.svc getlistbyuid request failed")
                null
            } ?: return@withContext null
            if (root.strOf("result") != "ok") {
                Timber.w("$TAG: pl.svc getlistbyuid result=%s", root.strOf("result"))
                return@withContext null
            }
            // result=ok 即请求成功；plist 缺省（零歌单账号）按空列表处理，避免误报加载失败
            root.get("plist") as? JsonArray ?: JsonArray()
        }

    /**
     * 拉取“我喜欢”歌曲。酷我体系的“我喜欢”就是歌单列表里 type=="MYFAVORITE"
     * 的系统歌单（web 端 favorite/musicList 接口已不存在）：先 getlistbyuid 找到
     * 其 pid，再用同族 getlistinfo 分页拉歌。账号从未喜欢过（无 MYFAVORITE 条目）
     * 返回空列表；未登录/请求失败返回 null，与“成功但为空”区分。
     */
    suspend fun fetchLikedSongs(): List<LxSong>? = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie()?.takeIf(String::isNotBlank) ?: return@withContext null
        val cookies = parseCookieMap(cookie)
        val plist = requestPlsList(cookie, cookies) ?: return@withContext null
        val favoriteId = plist.asSequence()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.strOf("type") == "MYFAVORITE" }
            ?.strOf("id")
            ?: return@withContext emptyList()
        fetchPlsSongs(favoriteId, cookie, cookies)
    }

    /**
     * 拉取登录用户的自建/收藏歌单列表（酷我客户端歌单同步服务 pl.svc getlistbyuid；
     * web 端无此接口，实测该服务无需 Secret、https 证书有效，附登录 Cookie 覆盖私有歌单）。
     * 单请求全量返回；跳过 type=="MYFAVORITE" 的“我喜欢”系统歌单
     * （其歌曲由 [fetchLikedSongs] 单独承载）；未登录返回 null。
     */
    suspend fun fetchUserPlaylists(): List<LxPlaylist>? = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie()?.takeIf(String::isNotBlank) ?: return@withContext null
        val cookies = parseCookieMap(cookie)
        val plist = requestPlsList(cookie, cookies) ?: return@withContext null
        plist.mapNotNull { raw ->
            val obj = raw as? JsonObject ?: return@mapNotNull null
            if (obj.strOf("type") == "MYFAVORITE") return@mapNotNull null
            val id = obj.strOf("id") ?: return@mapNotNull null
            LxPlaylist(
                id = id,
                name = obj.strOf("title") ?: "",
                // pic 为 http 明文 CDN 链接，统一升级 https
                pic = obj.strOf("pic")?.takeIf(String::isNotBlank)?.replaceFirst("http://", "https://"),
                playCount = obj.longOf("playnum"),
                trackCount = obj.intOf("musicnum"),
                creator = null,
            )
        }
    }

    /**
     * 拉取歌单内歌曲：pl.svc op=getlistinfo（listen1 / lx-music-desktop 的标准
     * 公开歌单接口，与 getlistbyuid 同族，无需 Secret；pn 0-based，歌曲数组在顶层
     * musiclist）。带登录 loginUid/loginSid 覆盖自建私有歌单，匿名（歌单广场）也可用；
     * 失败返回 null，与“成功但为空”区分。
     */
    suspend fun fetchPlaylistSongs(playlistId: String): List<LxSong>? = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie().orEmpty()
        val cookies = parseCookieMap(cookie)
        fetchPlsSongs(playlistId, cookie, cookies)
    }

    private suspend fun fetchPlsSongs(
        playlistId: String,
        cookie: String,
        cookies: Map<String, String>,
    ): List<LxSong>? = withContext(Dispatchers.IO) {
        val uid = cookies["userid"].orEmpty()
        val sid = cookies["sid"].orEmpty()
        val songs = mutableListOf<LxSong>()
        var total = -1
        var pn = 0
        while ((total < 0 || songs.size < total) && pn < FAVORITE_MAX_PAGES) {
            val urlBuilder = "https://nplserver.kuwo.cn/pl.svc".toHttpUrl().newBuilder()
                .addQueryParameter("op", "getlistinfo")
                .addQueryParameter("pid", playlistId)
                .addQueryParameter("pn", pn.toString())
                .addQueryParameter("rn", PLA_SVC_PAGE_SIZE.toString())
                .addQueryParameter("encode", "utf8")
                .addQueryParameter("keyset", "pl2012")
                .addQueryParameter("pcmp4", "1")
                .addQueryParameter("identity", "kuwo")
                .addQueryParameter("vipver", "MUSIC_9.0.5.0_W1")
                .addQueryParameter("newver", "1")
            if (uid.isNotBlank()) {
                // 附登录态以读取自建私有歌单（对齐 getlistbyuid 的做法）
                urlBuilder.addQueryParameter("loginUid", uid)
                urlBuilder.addQueryParameter("loginSid", sid)
            }
            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", USER_AGENT)
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .header("Referer", "https://www.kuwo.cn/")
                .get()
                .build()
            val root = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: pl.svc getlistinfo http %s pn=%s pid=%s", response.code, pn, playlistId)
                        return@use null
                    }
                    runCatching { JsonParser.parseString(response.body?.string().orEmpty()) }
                        .getOrNull() as? JsonObject
                }
            }.getOrElse { e ->
                Timber.w(e, "$TAG: pl.svc getlistinfo request failed pn=%s pid=%s", pn, playlistId)
                null
            } ?: return@withContext null
            if (root.strOf("result") != "ok") {
                Timber.w("$TAG: pl.svc getlistinfo result=%s pid=%s", root.strOf("result"), playlistId)
                return@withContext null
            }
            if (total < 0) total = root.intOf("total") ?: 0
            val arr = root.get("musiclist") as? JsonArray ?: JsonArray()
            val mapped = arr.mapNotNull { el -> kwLikedSongOf(el as? JsonObject) }
            if (mapped.isEmpty()) break
            songs += mapped
            if (arr.size() < PLA_SVC_PAGE_SIZE) break
            pn++
        }
        if (total > 0 && songs.size < total) {
            Timber.w("$TAG: pl.svc getlistinfo truncated pid=%s total=%s got=%s", playlistId, total, songs.size)
        }
        songs.toList()
    }

    /** pl.svc musiclist 条目 → LxSong；主键 id（getlistinfo 族），web 族为 rid/songid；名称类字段带 URL 编码/HTML 实体需解码。duration 返回秒，与 LxSong.interval 约定一致。 */
    private fun kwLikedSongOf(o: JsonObject?): LxSong? {
        o ?: return null
        val rid = o.strOf("rid") ?: o.strOf("songid") ?: o.strOf("songId") ?: o.strOf("id")
            ?: return null
        return LxSong(
            source = "kw",
            songmid = rid,
            name = decodePlsText(o.strOf("name") ?: o.strOf("songName")) ?: "",
            singer = decodePlsText(o.strOf("artist") ?: o.strOf("artistName")) ?: "",
            albumName = decodePlsText(o.strOf("album") ?: o.strOf("albumName")) ?: "",
            interval = o.longOf("duration") ?: 0L,
            pic = o.strOf("pic")?.takeIf { it.isNotBlank() },
        )
    }

    /** pl.svc 歌名/歌手/专辑字段解码：多重 URL 编码 + 常见 HTML 实体（listen1 用 html_decode、lx 用 decodeName 的等价简化）。 */
    private fun decodePlsText(raw: String?): String? =
        decodeKuwoCookieValue(raw)
            ?.replace("&amp;", "&")
            ?.replace("&#39;", "'")
            ?.replace("&quot;", "\"")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")

    private fun JsonObject.intOf(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.longOf(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() }
}
