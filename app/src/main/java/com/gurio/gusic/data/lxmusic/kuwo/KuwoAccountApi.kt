package com.gurio.gusic.data.lxmusic.kuwo

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
 * 主接口 www.kuwo.cn/api/www/user/userInfo（需要 csrf=kw_token 头 + Referer + 桌面 UA）；
 * 接口失败或无登录态字段时，退回直接解析 Cookie：昵称 uname3/username/nickName、
 * 头像 pic3/pic——这些值可能是多重 URL 编码，且含老式 escape() 的 %uXXXX UTF-16 码元，
 * 需先还原码元再 URLDecoder.decode，循环最多 3 次直至稳定。
 */
internal class KuwoAccountApi(
    private val okHttpClient: OkHttpClient,
    private val cookieStore: KuwoCookieStore,
) {
    companion object {
        private const val TAG = "KuwoAccountApi"
        private const val USER_INFO_URL = "https://www.kuwo.cn/api/www/user/userInfo"
        private const val FAVORITE_PAGE_SIZE = 30
        private const val FAVORITE_MAX_PAGES = 20
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

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

    private fun fetchUserInfoApi(cookie: String, cookies: Map<String, String>): Pair<String?, String?>? {
        val request = Request.Builder()
            .url(USER_INFO_URL.toHttpUrl().newBuilder()
                .addQueryParameter("httpsStatus", "1")
                .build())
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
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
                    Timber.w("$TAG: userInfo code=%s", root.strOf("code"))
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
     * 拉取“我喜欢”歌曲列表（web 端 favorite/musicList，与 userInfo 同族：csrf+Cookie+Referer+桌面 UA）。
     * 分页 pn/rn 直到拉满 total（封顶 20 页防 total 异常偏大时长串行请求）；
     * 失败（未登录/被拒/网络）返回 null，与“成功但为空”区分。
     */
    suspend fun fetchLikedSongs(): List<LxSong>? = withContext(Dispatchers.IO) {
        val cookie = cookieStore.getCookie()?.takeIf(String::isNotBlank) ?: return@withContext null
        val csrf = parseCookieMap(cookie)["kw_token"].orEmpty()
        val songs = mutableListOf<LxSong>()
        var total = -1
        var pn = 1
        while ((total < 0 || songs.size < total) && pn <= FAVORITE_MAX_PAGES) {
            val request = Request.Builder()
                .url("https://www.kuwo.cn/api/www/favorite/musicList"
                    .toHttpUrl().newBuilder()
                    .addQueryParameter("pn", pn.toString())
                    .addQueryParameter("rn", FAVORITE_PAGE_SIZE.toString())
                    .addQueryParameter("httpsStatus", "1")
                    .build())
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .header("csrf", csrf)
                .header("Referer", "https://www.kuwo.cn/")
                .get()
                .build()
            val root = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: favorite/musicList http %s pn=%s", response.code, pn)
                        return@use null
                    }
                    runCatching { JsonParser.parseString(response.body?.string().orEmpty()) }
                        .getOrNull() as? JsonObject
                }
            }.getOrElse { e ->
                Timber.w(e, "$TAG: favorite/musicList request failed pn=%s", pn)
                null
            } ?: return@withContext null
            if (root.strOf("code") != "200") {
                Timber.w("$TAG: favorite/musicList code=%s msg=%s", root.strOf("code"), root.strOf("message"))
                return@withContext null
            }
            val data = root.get("data") as? JsonObject ?: return@withContext null
            if (total < 0) total = data.intOf("total") ?: 0
            val arr = data.get("data") as? com.google.gson.JsonArray ?: com.google.gson.JsonArray()
            val mapped = arr.mapNotNull { el -> kwLikedSongOf(el as? JsonObject) }
            if (mapped.isEmpty()) break
            songs += mapped
            if (arr.size() < FAVORITE_PAGE_SIZE) break
            pn++
        }
        songs.toList()
    }

    /** favorite/musicList 条目 → LxSong；songid/rid 为必需字段。duration 该接口返回秒，与 LxSong.interval 约定一致（未真机验证）。 */
    private fun kwLikedSongOf(o: JsonObject?): LxSong? {
        o ?: return null
        val rid = o.strOf("rid") ?: o.strOf("songid") ?: o.strOf("songId")
            ?: o.get("songid")?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asString }.getOrNull() }
            ?: return null
        return LxSong(
            source = "kw",
            songmid = rid,
            name = o.strOf("name") ?: o.strOf("songName") ?: "",
            singer = o.strOf("artist") ?: o.strOf("artistName") ?: "",
            albumName = o.strOf("album") ?: o.strOf("albumName") ?: "",
            interval = o.longOf("duration") ?: 0L,
            pic = o.strOf("pic")?.takeIf { it.isNotBlank() },
        )
    }

    private fun JsonObject.intOf(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.longOf(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() }
}
