package com.gurio.gusic.data.lxmusic.kuwo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.gurio.gusic.data.lxmusic.LxPlaylist
import com.gurio.gusic.data.lxmusic.LxSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

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
        private const val PLA3_MAX_PAGES = 100
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /** 原生通道上报的设备名（dev/devicetype 参数），任意外显名均可 */
        private const val NATIVE_DEV_NAME = "Gusic"
        private const val DEVICE_REGISTER_URL = "https://wapi.kuwo.cn/openapi/v1/app/userInitData/selectFavour"
        private const val SMS_SEND_URL = "https://i.kuwo.cn/US_NEW/kuwo/send_sms"
        private const val SMS_LOGIN_URL = "https://i.kuwo.cn/US_NEW/kuwo/login_sms"

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
                    .addQueryParameter("loginSid", cookies["sid"]?.takeIf(String::isNotBlank)
                        ?: cookies["websid"].orEmpty())
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
     * 其 pid，再走客户端协议 pl3_getlist 分页拉歌（见 [fetchUserPlaylistSongs]）。
     * 账号从未喜欢过（无 MYFAVORITE 条目）返回空列表；
     * 未登录/请求失败返回 null，与“成功但为空”区分。
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
        fetchUserPlaylistSongs(favoriteId)
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
     * 拉取歌单内歌曲（web 公开歌单族，歌单广场用）：pl.svc op=getlistinfo，
     * listen1/lx-music-desktop 的标准匿名公开歌单接口；pid 为 web 体系 32 位 id。
     * 客户端体系（plat=ar）的 64 位自建歌单 pid 会被该接口按 32 位截断而寻址错误，
     * 用户歌单须走 [fetchUserPlaylistSongs]。失败返回 null，与“成功但为空”区分。
     */
    suspend fun fetchPlaylistSongs(playlistId: String): List<LxSong>? = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie().orEmpty()
        val cookies = parseCookieMap(cookie)
        fetchPlsSongs(playlistId, cookie, cookies)
    }

    /**
     * 拉取登录用户歌单内歌曲（自建/收藏/我喜欢，pid 来自 getlistbyuid）。
     * web/mobilist 族端点全部按 32 位寻址：超 2^32 的 pid 会被取模截断、静默命中
     * 他人歌单（实测），因此 64 位 pid 只能走原生协议 pl3_getlist；原生会话
     * （验证码登录签发的 uid/sid）优先，缺失时回退 web cookie 的 sid/websid。
     * 32 位 pid 优先 web 族（匿名即可读公开/老歌单）。
     * 未登录/无可用会话/两路皆失败返回 null。
     */
    suspend fun fetchUserPlaylistSongs(playlistId: String): List<LxSong>? = withContext(Dispatchers.IO) {
        val native = cookieStore.getNativeCredentials()
        val cookie = cookieStore.getCookie()
        val cookies = parseCookieMap(cookie)
        val uid = native?.uid ?: cookies["userid"]?.takeIf(String::isNotBlank)
            ?: return@withContext null
        // 原生 sid 优先：web 扫码 cookie 的 sid 不被原生协议接受（实测 errcode=1000）
        val sid = native?.sid
            ?: cookies["sid"]?.takeIf(String::isNotBlank)
            ?: cookies["websid"]?.takeIf(String::isNotBlank)
        val widePid = (playlistId.toLongOrNull() ?: 0L) > 0xFFFFFFFFL
        when {
            // 32 位 pid：web 族成熟可靠；空结果（含全被 digest 过滤）时若有会话再试原生
            !widePid -> fetchCollectedPlsSongs(playlistId, uid, sid.orEmpty(), cookie.orEmpty())
                ?.takeIf { it.isNotEmpty() }
                ?: sid?.let { fetchOwnedPlsSongs(playlistId, uid, it) }
            // 64 位 pid：只走原生协议，无会话直接失败（宁缺勿错——web 族会拿错歌单）
            sid == null -> null
            else -> fetchOwnedPlsSongs(playlistId, uid, sid)
        }
    }

    /**
     * pl3_getlist：自建歌单与“我喜欢”的内容读取（原生协议，64 位 pid 寻址）。
     * 按 TuneWeave 逆向的官方客户端构造：query 携带设备注册签发的 appuid（devid）
     * 与 device_user（user/imei），复数 Cookies 头携带 loginUid/loginSid/appUid——
     * 四元组与登录会话/设备注册不一致时服务端拒绝（errcode=1000）。
     */
    private suspend fun fetchOwnedPlsSongs(
        playlistId: String,
        uid: String,
        sid: String,
    ): List<LxSong>? = withContext(Dispatchers.IO) {
        val device = ensureDeviceIdentity() ?: return@withContext null
        val nativeCookie = "loginUid=$uid,loginSid=$sid,appUid=${device.appUid}"
        val songs = mutableListOf<LxSong>()
        var pagenum = -1
        var pn = 0
        while (pn < PLA3_MAX_PAGES && (pagenum < 0 || pn < pagenum)) {
            val request = Request.Builder()
                .url("https://nplserver.kuwo.cn/pl.svc".toHttpUrl().newBuilder()
                    .addQueryParameter("op", "pl3_getlist")
                    .addQueryParameter("pid", playlistId)
                    // sig=0 表示本地无缓存：服务端必回全量内容（songchange=1）
                    .addQueryParameter("sig", "0")
                    .addQueryParameter("uid", uid)
                    .addQueryParameter("sid", sid)
                    .addQueryParameter("encode", "utf-8")
                    .addQueryParameter("plat", "ar")
                    .addQueryParameter("devid", device.appUid)
                    .addQueryParameter("user", device.deviceUser)
                    .addQueryParameter("prod", "kwplayer_ar_12.2.2.0")
                    .addQueryParameter("source", KuwoNativeCrypto.CLIENT_SOURCE)
                    .addQueryParameter("corp", "kuwo")
                    .addQueryParameter("locationid", "1")
                    .addQueryParameter("approval", "false")
                    .addQueryParameter("city", "")
                    .addQueryParameter("province", "")
                    .addQueryParameter("imei", device.deviceUser)
                    .addQueryParameter("ttime", System.currentTimeMillis().toString())
                    .addQueryParameter("devicetype", NATIVE_DEV_NAME)
                    .addQueryParameter("allpay", "0")
                    .addQueryParameter("notrace", "1")
                    .addQueryParameter("oaid", "")
                    .addQueryParameter("vipMode", "0")
                    .addQueryParameter("pn", pn.toString())
                    .build())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                // 原生协议用复数 Cookies 头（客户端上行身份字段，非浏览器 cookie 语义）
                .header("Cookies", nativeCookie)
                .get()
                .build()
            val root = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: pl3_getlist http %s pn=%s pid=%s", response.code, pn, playlistId)
                        return@use null
                    }
                    runCatching { JsonParser.parseString(response.body?.string().orEmpty()) }
                        .getOrNull() as? JsonObject
                }
            }.getOrElse { e ->
                Timber.w(e, "$TAG: pl3_getlist request failed pn=%s pid=%s", pn, playlistId)
                null
            } ?: return@withContext null
            val errcode = root.intOf("errcode") ?: 0
            if (root.strOf("result") != "ok" || errcode != 0) {
                Timber.w("$TAG: pl3_getlist result=%s errcode=%s reason=%s pid=%s",
                    root.strOf("result"), errcode, root.strOf("reason"), playlistId)
                return@withContext null
            }
            if (pagenum < 0) pagenum = root.intOf("pagenum") ?: 1
            val info = root.get("info") as? JsonObject
            val arr = info?.get("musiclist") as? JsonArray ?: JsonArray()
            songs += arr.mapNotNull { el -> kwLikedSongOf(el as? JsonObject) }
            if (arr.size() == 0) break
            pn++
        }
        if (pn >= PLA3_MAX_PAGES && pagenum > PLA3_MAX_PAGES) {
            Timber.w("$TAG: pl3_getlist truncated at %s pages, pagenum=%s pid=%s",
                PLA3_MAX_PAGES, pagenum, playlistId)
        }
        songs.toList()
    }

    /** mobilist.kuwo.cn/list.s：web 族端点（32 位 pid 寻址；64 位 pid 会被取模截断、
     *  静默命中他人歌单，调用方 [fetchUserPlaylistSongs] 已按位数分流，勿直接传入 64 位 pid）。 */
    private suspend fun fetchCollectedPlsSongs(
        playlistId: String,
        uid: String,
        sid: String,
        cookie: String,
    ): List<LxSong>? = withContext(Dispatchers.IO) {
        val songs = mutableListOf<LxSong>()
        var total = -1
        var pn = 0
        while ((total < 0 || songs.size < total) && pn < PLA3_MAX_PAGES) {
            val request = Request.Builder()
                .url("https://mobilist.kuwo.cn/list.s".toHttpUrl().newBuilder()
                    .addQueryParameter("type", "songlist")
                    .addQueryParameter("id", playlistId)
                    .addQueryParameter("vipsec", "1")
                    .addQueryParameter("kubb", "2")
                    .addQueryParameter("fs", "1")
                    .addQueryParameter("showtype", "1")
                    .addQueryParameter("sorttype", "0")
                    .addQueryParameter("apiv", "0")
                    .addQueryParameter("pn", pn.toString())
                    .addQueryParameter("rn", PLA_SVC_PAGE_SIZE.toString())
                    .addQueryParameter("digest", "8")
                    .addQueryParameter("hasmv", "1")
                    .addQueryParameter("hasinner", "1")
                    .addQueryParameter("hasad", "1")
                    .addQueryParameter("hsy", "1")
                    .addQueryParameter("isnew", "2")
                    .addQueryParameter("newcate", "1")
                    .addQueryParameter("supportfan", "1")
                    .addQueryParameter("isg", "1")
                    .addQueryParameter("aioper", "0")
                    .addQueryParameter("loginUid", uid)
                    .addQueryParameter("loginSid", sid)
                    .addQueryParameter("prod", "kwplayer_ar_12.2.2.0")
                    .build())
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .header("Referer", "https://www.kuwo.cn/")
                .get()
                .build()
            val root = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: mobilist list.s http %s pn=%s pid=%s", response.code, pn, playlistId)
                        return@use null
                    }
                    runCatching { JsonParser.parseString(response.body?.string().orEmpty()) }
                        .getOrNull() as? JsonObject
                }
            }.getOrElse { e ->
                Timber.w(e, "$TAG: mobilist list.s request failed pn=%s pid=%s", pn, playlistId)
                null
            } ?: return@withContext null
            if (root.strOf("code") != "0" || root.strOf("msg") != "ok") {
                Timber.w("$TAG: mobilist list.s code=%s msg=%s pid=%s",
                    root.strOf("code"), root.strOf("msg"), playlistId)
                return@withContext null
            }
            val data = root.get("data") as? JsonObject ?: return@withContext null
            if (total < 0) total = data.intOf("total") ?: 0
            val arr = data.get("musiclist") as? JsonArray ?: JsonArray()
            songs += arr.mapNotNull { raw ->
                val obj = raw as? JsonObject ?: return@mapNotNull null
                // digest!=15 为非纯音乐条目（广告/有声等），跳过
                val digest = obj.intOf("digest")
                if (digest != null && digest != 15) null else kwLikedSongOf(obj)
            }
            if (arr.size() < PLA_SVC_PAGE_SIZE) break
            pn++
        }
        if (pn >= PLA3_MAX_PAGES && total > songs.size) {
            Timber.w("$TAG: mobilist list.s truncated at %s pages, got=%s total=%s pid=%s",
                PLA3_MAX_PAGES, songs.size, total, playlistId)
        }
        songs.toList()
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

    /** pl.svc/mobilist 条目 → LxSong；pl3_getlist 族主键 id，mobilist/getlistinfo 族 rid/songid；名称类字段带 URL 编码/HTML 实体需解码。duration 返回秒，与 LxSong.interval 约定一致。 */
    private fun kwLikedSongOf(o: JsonObject?): LxSong? {
        o ?: return null
        val rid = o.strOf("id") ?: o.strOf("rid") ?: o.strOf("songid") ?: o.strOf("songId")
            ?: return null
        return LxSong(
            source = "kw",
            songmid = rid,
            name = decodePlsText(o.strOf("name") ?: o.strOf("songName")) ?: "",
            singer = decodePlsText(o.strOf("artist") ?: o.strOf("artistName")) ?: "",
            albumName = decodePlsText(o.strOf("album") ?: o.strOf("albumName")) ?: "",
            interval = o.longOf("duration") ?: 0L,
            pic = (o.strOf("pic") ?: o.strOf("albumpic") ?: o.strOf("img"))?.takeIf { it.isNotBlank() },
        )
    }

    // ------------------------------------------------------------------
    // 原生通道：设备注册 + 短信验证码登录（魔改 DES 封包，见 [KuwoNativeCrypto]）
    // ------------------------------------------------------------------

    /** send_sms 成功后服务端回传的时间戳，login_sms 需原样回传 */
    @Volatile
    private var lastSmsServerTm: String? = null

    /** 发送短信验证码；成功返回 true（server_tm 留存供登录回传）。 */
    suspend fun sendSmsCode(mobile: String): Boolean = withContext(Dispatchers.IO) {
        val device = ensureDeviceIdentity() ?: return@withContext false
        val tm = System.currentTimeMillis().toString()
        val form = listOf(
            "mobile" to mobile,
            "type" to "4",
            "tm" to tm,
            "secret" to KuwoNativeCrypto.smsSecret(mobile, tm),
            "src" to KuwoNativeCrypto.CLIENT_SOURCE,
            "version" to KuwoNativeCrypto.CLIENT_VERSION,
            "dev_id" to device.appUid,
            "user" to device.deviceUser,
            "dev_name" to NATIVE_DEV_NAME,
            "devType" to "SDK",
            "from" to "android",
            "devResolution" to "0*0",
        ).joinToString("&") { (k, v) -> "$k=${urlEncodeNative(v)}" }
        val root = nativeSealedRequest(
            SMS_SEND_URL,
            form,
            nativeMetadataCookie(device, loginUid = "0", loginSid = "0"),
        ) ?: return@withContext false
        if (root.strOf("result") != "succ") {
            Timber.w("$TAG: send_sms result=%s msg=%s enum=%s",
                root.strOf("result"), root.strOf("msg"), root.strOf("enum"))
            return@withContext false
        }
        lastSmsServerTm = root.strOf("server_tm") ?: root.strOf("tm") ?: tm
        true
    }

    /**
     * 验证码登录。成功后持久化原生凭证（uid/sid）、合成 web cookie 并写入昵称，
     * 使 UI 登录态与 web 族接口立即可用；原生凭证供 pl3_getlist 读取 64 位自建歌单。
     */
    suspend fun loginBySmsCode(mobile: String, code: String): Boolean = withContext(Dispatchers.IO) {
        val device = ensureDeviceIdentity()
        if (device != null) {
            val ok = tryLoginSms(mobile, code, device)
            // 验证码一次一用：无论成败都消费掉留存的 server_tm，避免换号/跨次复用过期值
            lastSmsServerTm = null
            ok
        } else {
            false
        }
    }

    private fun tryLoginSms(
        mobile: String,
        code: String,
        device: KuwoCookieStore.DeviceIdentity,
    ): Boolean {
        val tm = lastSmsServerTm ?: System.currentTimeMillis().toString()
        val form = listOf(
            "mobile" to mobile,
            "code" to code,
            "tm" to tm,
            "src" to KuwoNativeCrypto.CLIENT_SOURCE,
            "version" to KuwoNativeCrypto.CLIENT_VERSION,
            "dev_id" to device.appUid,
            "dev_name" to NATIVE_DEV_NAME,
            "devType" to "SDK",
            "from" to "android",
            "devResolution" to "0*0",
        ).joinToString("&") { (k, v) -> "$k=${urlEncodeNative(v)}" }
        val root = nativeSealedRequest(
            SMS_LOGIN_URL,
            form,
            nativeMetadataCookie(device, loginUid = "0", loginSid = "0"),
        ) ?: return false
        if (root.strOf("result") != "succ") {
            Timber.w("$TAG: login_sms result=%s msg=%s enum=%s",
                root.strOf("result"), root.strOf("msg"), root.strOf("enum"))
            return false
        }
        val sid = root.strOf("sid")
        if (sid.isNullOrBlank()) {
            Timber.w("$TAG: login_sms succ without sid")
            return false
        }
        // userInfo 可能是对象，也可能是内嵌 JSON 字符串
        val userInfo = root.get("userInfo")?.let { el ->
            (el as? JsonObject) ?: runCatching {
                (el as? com.google.gson.JsonPrimitive)?.let { JsonParser.parseString(it.asString) as? JsonObject }
            }.getOrNull()
        }
        val uid = userInfo?.strOf("uid") ?: root.strOf("uid")
        if (uid.isNullOrBlank()) {
            Timber.w("$TAG: login_sms succ without uid")
            return false
        }
        cookieStore.saveNativeCredentials(uid, sid)
        // 合成 web cookie：userid/sid 命中登录态判定，fetchProfile/getlistbyuid 可直接使用
        cookieStore.setCookie("userid=$uid; sid=$sid; loginSid=$sid")
        cookieStore.setProfile(userInfo?.strOf("nickName") ?: root.strOf("nickName"), null)
        Timber.i("$TAG: kuwo sms login ok")
        return true
    }

    /** 装机级设备身份：本地生成 device_user/android_id，经 wapi selectFavour 注册换取 appuid。
     *  首次注册走网络，用互斥避免并发调用重复注册两台设备（四元组漂移会被服务端拒）。 */
    private val deviceIdentityMutex = Mutex()

    private suspend fun ensureDeviceIdentity(): KuwoCookieStore.DeviceIdentity? =
        deviceIdentityMutex.withLock {
            cookieStore.getDeviceIdentity()?.let { return it }
            val deviceUser = UUID.randomUUID().toString().replace("-", "")
            val androidId = UUID.randomUUID().toString().replace("-", "")
            val appUid = registerDevice(deviceUser, androidId) ?: return null
            val identity = KuwoCookieStore.DeviceIdentity(deviceUser, androidId, appUid)
            cookieStore.saveDeviceIdentity(identity)
            identity
        }

    /** 设备注册（wapi openapi，明文 token=base64(xor(plain,"yeelion "))）；返回 appuid。 */
    private fun registerDevice(deviceUser: String, androidId: String): String? {
        val plain = "&new_user=1&mac=$deviceUser&hd=$deviceUser&android_id=$androidId&oaid=" +
            "&q36=${KuwoNativeCrypto.FALLBACK_QIMEI36}&vmac=&ver=kwplayer_ar_12.2.2.0" +
            "&src=${urlEncodeNative(KuwoNativeCrypto.CLIENT_SOURCE)}&process=false" +
            "&dev=${urlEncodeNative(NATIVE_DEV_NAME)}"
        val request = Request.Builder()
            .url(DEVICE_REGISTER_URL.toHttpUrl().newBuilder()
                .addQueryParameter("len", plain.length.toString())
                .addQueryParameter("token", KuwoNativeCrypto.deviceRegisterToken(plain))
                .addQueryParameter("appuid", "0")
                .build())
            .header("Accept", "application/json")
            .get()
            .build()
        val root = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("$TAG: device register http %s", response.code)
                    return@use null
                }
                runCatching { JsonParser.parseString(response.body?.string().orEmpty()) }
                    .getOrNull() as? JsonObject
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: device register request failed")
            null
        } ?: return null
        val data = root.get("data") as? JsonObject
        val appUid = data?.strOf("appuid") ?: data?.strOf("appUid")
            ?: root.strOf("appuid") ?: root.strOf("appUid")
        if (appUid.isNullOrBlank()) {
            Timber.w("$TAG: device register missing appuid, code=%s msg=%s",
                root.strOf("code"), root.strOf("msg"))
            return null
        }
        return appUid
    }

    /** 原生封包通道：GET {url}?f=ar&q=seal(form&sx=key)，响应为 sx 加密的 base64，解密后解析 JSON。 */
    private fun nativeSealedRequest(url: String, form: String, cookieHeader: String): JsonObject? {
        val sx = KuwoNativeCrypto.responseKey()
        val request = Request.Builder()
            .url(url.toHttpUrl().newBuilder()
                .addQueryParameter("f", "ar")
                .addQueryParameter("q", KuwoNativeCrypto.sealQuery("$form&sx=$sx"))
                .build())
            .header("Accept", "application/json")
            .header("Cookies", cookieHeader)
            .get()
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Timber.w("$TAG: native sealed http %s url=%s body=%.120s", response.code, url, body)
                    return@use null
                }
                val plain = KuwoNativeCrypto.openResponse(body, sx)
                if (plain == null) {
                    Timber.w("$TAG: native sealed undecryptable url=%s body=%.120s", url, body)
                    return@use null
                }
                runCatching { JsonParser.parseString(plain) as? JsonObject }.getOrNull()
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: native sealed request failed url=%s", url)
            null
        }
    }

    /** 原生通道元数据 Cookies 头（复数；客户端上行身份字段，非浏览器 cookie 语义）。 */
    private fun nativeMetadataCookie(
        device: KuwoCookieStore.DeviceIdentity,
        loginUid: String,
        loginSid: String,
    ): String = "user=${device.deviceUser},ct=11,cv=12220,chid=newpcguanwangmobile," +
        "QIMEI36=${KuwoNativeCrypto.FALLBACK_QIMEI36},tmeAppID=kwplayer," +
        "loginUid=$loginUid,loginSid=$loginSid,appUid=${device.appUid},rom=android"

    private fun urlEncodeNative(value: String): String = URLEncoder.encode(value, "UTF-8")

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
