package com.gurio.gusic.data.lxmusic.tencent

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.gurio.gusic.data.lxmusic.LxAlbum
import com.gurio.gusic.data.lxmusic.LxArtist
import com.gurio.gusic.data.lxmusic.LxPlaylist
import com.gurio.gusic.data.lxmusic.LxSong
import com.gurio.gusic.data.lxmusic.TxSongLikeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64

/**
 * QQ 音乐账号读取接口：基于登录 Cookie（网页登录/扫码登录后保存在 [TencentCookieStore]）。
 *
 * 资料走 c.y.qq.com 的 fcg_get_profile_homepage.fcg；红心歌单与普通歌单曲目统一走
 * u.y.qq.com musicu.fcg 的 music.srfDissInfo.DissInfo/CgiGetDiss（dirid=201 表示"我喜欢"）；
 * 用户歌单列表由 fcg_user_created_diss（创建）与 fcg_get_profile_order_asset.fcg（收藏）合并。
 */
internal class TencentAccountApi(
    private val okHttpClient: OkHttpClient,
    private val cookieStore: TencentCookieStore,
) {
    companion object {
        private const val TAG = "TencentAccountApi"
        private const val MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val APP_MUSICU_URL = "https://u6.y.qq.com/cgi-bin/musics.fcg"
        private const val LEGACY_ORDER_DISS_URL = "https://c.y.qq.com/folder/fcgi-bin/fcg_qm_order_diss.fcg"
        private const val PROFILE_URL = "https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg"
        private const val CREATED_DISS_URL = "https://c.y.qq.com/rsc/fcgi-bin/fcg_user_created_diss"
        private const val ORDER_ASSET_URL = "https://c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg"
        private const val ORDER_SINGER_URL = "https://c.y.qq.com/rsc/fcgi-bin/fcg_order_singer_getlist.fcg"
        private const val FAV_WRITE_MODULE = "music.musicasset.PlaylistFavWrite"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 30
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val QZONE_PLAYLIST = Regex("qzone|空间|背景音乐", RegexOption.IGNORE_CASE)

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

        internal fun cookieUin(cookies: Map<String, String>): String {
            val raw = if (cookies["login_type"] == "2") {
                cookies["wxuin"] ?: cookies["uin"] ?: cookies["p_uin"]
            } else {
                cookies["uin"] ?: cookies["qqmusic_uin"] ?: cookies["wxuin"] ?: cookies["p_uin"]
            }
            return normalizeUin(raw)
        }

        internal fun normalizeUin(raw: String?): String =
            raw?.filter(Char::isDigit).orEmpty().dropWhile { it == '0' }

        internal fun musicKey(cookies: Map<String, String>): String = listOf(
            "qm_keyst", "qqmusic_key", "music_key", "p_skey", "skey",
            "psrf_qqaccess_token", "psrf_qqrefresh_token", "wxrefresh_token", "wxskey",
        ).firstNotNullOfOrNull { cookies[it]?.takeIf(String::isNotEmpty) }.orEmpty()

        /** ptlogin2 会在 Cookie 里留 ptnick_<uin>（uin 可能带前导 0），作为昵称兜底。 */
        internal fun cookieNickname(cookies: Map<String, String>, uin: String): String {
            listOf("ptnick_$uin", "ptnick_0$uin").forEach { key ->
                cookies[key]?.let(::decodeCookieValue)?.takeIf(String::isNotEmpty)?.let { return it }
            }
            return ""
        }

        internal fun avatarForUin(uin: String): String =
            if (uin.isEmpty()) "" else "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=100"

        internal fun decodeCookieValue(value: String): String = runCatching {
            URLDecoder.decode(value.replace("+", "%20"), "UTF-8").trim()
        }.getOrDefault(value.trim())

        /** musicu.fcg 的 comm 块：带票据时 ct=19 并附 authst。 */
        internal fun authComm(cookie: String?): JsonObject {
            val cookies = parseCookieMap(cookie)
            val key = musicKey(cookies)
            return JsonObject().apply {
                addProperty("uin", cookieUin(cookies).ifEmpty { "0" })
                addProperty("format", "json")
                addProperty("ct", if (key.isNotEmpty()) 19 else 24)
                addProperty("cv", 0)
                if (key.isNotEmpty()) addProperty("authst", key)
            }
        }

        /**
         * musicu.fcg 写接口的混合 comm（lx-music-desktop-enhanced 同款配方）：
         * ct=11 + authst=musickey + tmeAppID/tmeLoginType，鉴权内嵌在 comm 中，
         * 无需 sign、无需 g_tk、无需设备指纹；配合完整 Cookie 头与 Origin 使用。
         */
        internal fun hybridComm(cookie: String?): JsonObject {
            val cookies = parseCookieMap(cookie)
            val key = musicKey(cookies)
            val uin = cookieUin(cookies)
            // W_X 前缀的 musickey 对应微信登录（tmeLoginType=1），否则 QQ 登录（2）
            val loginType = if (key.startsWith("W_X")) 1L else 2L
            return JsonObject().apply {
                addProperty("ct", 11)
                addProperty("cv", 14090008)
                addProperty("v", 14090008)
                addProperty("chid", "10003505")
                addProperty("uid", uin.ifEmpty { "0" })
                addProperty("qq", uin.ifEmpty { "0" })
                addProperty("authst", key)
                addProperty("tmeAppID", "qqmusic")
                addProperty("tmeLoginType", loginType)
                addProperty("format", "json")
                addProperty("inCharset", "utf-8")
                addProperty("outCharset", "utf-8")
            }
        }

        /**
         * musicu.fcg 写接口的 URL sign（web 端 getSecuritySign 的 zza 版）：
         * "zza" + 10~16 位随机小写字母数字 + MD5("CJBPACrRuNy7" + 请求体)。
         */
        internal fun getSecuritySign(data: String): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val randomPart = buildString {
                repeat((10..16).random()) { append(chars[(0..35).random()]) }
            }
            return "zza$randomPart" + md5Hex("CJBPACrRuNy7$data")
        }

        private fun md5Hex(input: String): String =
            MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        internal fun albumCover(albumMid: String): String =
            if (albumMid.isBlank()) ""
            else "https://y.qq.com/music/photo_new/T002R300x300M000${albumMid}.jpg"

        private const val LxSources_TX = "tx"
        private const val DISS_MODULE = "music.srfDissInfo.DissInfo"
        private const val DISS_METHOD = "CgiGetDiss"

        /** CgiGetDiss 的曲目可能是 track_info/songInfo 包装，也可能扁平；统一拆包。 */
        internal fun mapTrack(raw: JsonElement): LxSong? {
            val outer = raw as? JsonObject ?: return null
            val track = (outer.get("track_info") ?: outer.get("songInfo") ?: outer.get("songinfo") ?: outer)
                as? JsonObject ?: return null
            val songmid = track.str("mid") ?: track.str("songmid") ?: outer.str("songmid") ?: return null
            if (songmid.isBlank()) return null
            val name = track.str("name") ?: track.str("songname") ?: outer.str("songname") ?: ""
            val singers = (track.get("singer") as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.let { s -> (s.str("name") ?: "") to (s.str("mid") ?: "") } }
                .orEmpty()
            // 无名歌手只参与 artistIds（匹配用），不进入展示名的拼接
            val singer = singers.filter { it.first.isNotBlank() }.joinToString("/") { it.first }
                .ifEmpty { track.str("singername") ?: outer.str("singername") ?: "" }
            val albumObj = track.get("album") as? JsonObject
            val album = albumObj?.str("name") ?: track.str("albumname") ?: outer.str("albumname") ?: ""
            val albumMid = albumObj?.str("mid") ?: track.str("albummid") ?: outer.str("albummid") ?: ""
            val fileObj = track.get("file") as? JsonObject
            val strMediaMid = fileObj?.str("media_mid") ?: track.str("strMediaMid") ?: outer.str("strMediaMid") ?: ""
            val numericId = track.str("id") ?: track.str("songid") ?: outer.str("songid") ?: ""
            val extra = mutableMapOf<String, String>()
            if (strMediaMid.isNotBlank()) extra["strMediaMid"] = strMediaMid
            if (albumMid.isNotBlank()) extra["albumMid"] = albumMid
            if (numericId.isNotBlank()) extra["songId"] = numericId
            (track.str("type") ?: outer.str("type"))?.let { type ->
                if (type.isNotBlank()) extra["songType"] = type
            }
            val artistIds = singers.map { it.second }.filter(String::isNotBlank).joinToString(",")
            if (artistIds.isNotEmpty()) extra["artistIds"] = artistIds
            return LxSong(
                source = LxSources_TX,
                songmid = songmid,
                name = name,
                singer = singer,
                albumName = album,
                interval = track.long("interval") ?: outer.long("interval") ?: 0L,
                pic = albumCover(albumMid).ifBlank { null },
                extraFields = extra,
            )
        }

        /** 兼容字符串与数字两种原始值（dirid、song_cnt 等字段上游时而是数字）。 */
        private fun JsonObject.str(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }

        private fun JsonObject.long(name: String): Long? =
            get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

        private fun JsonObject.intField(name: String): Int? =
            get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

        /** g_tk：hash33(token)（h += (h<<5)+charCode，取正 31 位；空串得 5381 即匿名 g_tk）。 */
        internal fun hash33(text: String): Long {
            var hash = 5381L
            text.forEach { ch -> hash += (hash shl 5) + ch.code }
            return hash and 0x7fffffffL
        }

        /**
         * 老接口 fcg_qm_order_diss.fcg 的 g_tk：按 skey 实算；
         * 微信登录（musickey W_ 前缀）改用 p_skey（jsososo/QQMusicApi 同款约定）。
         */
        internal fun legacyGtk(cookies: Map<String, String>): Long {
            val key = musicKey(cookies)
            val source = if (key.startsWith("W_")) cookies["p_skey"].orEmpty() else cookies["skey"].orEmpty()
            return hash33(source)
        }

        /**
         * musicu.fcg app 通道 zzc 签名（paperclip zzcSign）：
         * sha1(body) 大写 hex → 两位切片异或固定 scramble → base64 去掉 / + = →
         * "zzc" + hex[按 part1 索引拼接] + middle + hex[按 part2 索引拼接]，整体小写。
         */
        internal fun zzcSign(payload: String): String {
            val hash = sha1Hex(payload).uppercase()
            val part1Indexes = intArrayOf(23, 14, 6, 36, 16, 7, 19)
            val part2Indexes = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
            val scramble = intArrayOf(
                89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
                186, 123, 120, 64, 242, 133, 143, 161, 121, 179,
            )
            val part1 = part1Indexes.joinToString("") { hash[it].toString() }
            val part2 = part2Indexes.joinToString("") { hash[it].toString() }
            val bytes = ByteArray(scramble.size)
            scramble.forEachIndexed { i, value ->
                bytes[i] = (value xor hash.substring(i * 2, i * 2 + 2).toInt(16)).toByte()
            }
            val middle = Base64.getEncoder().encodeToString(bytes)
                .replace("/", "").replace("+", "").replace("=", "")
            return "zzc$part1$middle$part2".lowercase()
        }

        private fun sha1Hex(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    private fun loginCookie(): Pair<String, Map<String, String>>? {
        val cookie = cookieStore.getCookie()?.takeIf(String::isNotBlank) ?: return null
        val cookies = parseCookieMap(cookie)
        val uin = cookieUin(cookies)
        if (uin.isBlank() || musicKey(cookies).isBlank()) return null
        return cookie to cookies
    }

    suspend fun fetchProfile(): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        val (cookie, cookies) = loginCookie() ?: return@withContext null
        val uin = cookieUin(cookies)
        val cookieNick = cookieNickname(cookies, uin)
        val cookieAvatar = cookies["qqmusic_avatar"]?.let(::decodeCookieValue)
            ?.takeIf(String::isNotEmpty) ?: avatarForUin(uin)
        val request = Request.Builder()
            .url(
                PROFILE_URL.toHttpUrl().newBuilder().apply {
                    addQueryParameter("cid", "205360838")
                    addQueryParameter("userid", uin)
                    addQueryParameter("reqfrom", "1")
                    addQueryParameter("g_tk", "5381")
                    addQueryParameter("loginUin", uin)
                    addQueryParameter("hostUin", "0")
                    addQueryParameter("format", "json")
                    addQueryParameter("inCharset", "utf8")
                    addQueryParameter("outCharset", "utf-8")
                    addQueryParameter("notice", "0")
                    addQueryParameter("platform", "yqq.json")
                    addQueryParameter("needNewCode", "0")
                }.build(),
            )
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .header("Cookie", cookie)
            .get()
            .build()
        val body = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                runCatching { JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject }
                    .getOrNull()
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: profile request failed")
            null
        }
        if (body == null || body.get("code")?.takeIf { it.isJsonPrimitive }?.asInt == 1000 ||
            body.get("result")?.takeIf { it.isJsonPrimitive }?.asInt == 301
        ) {
            return@withContext cookieNick.ifBlank { null } to cookieAvatar.ifBlank { null }
        }
        val profile = body.get("data")?.let { (it as? JsonObject)?.get("creator") as? JsonObject }
            ?: body.get("data") as? JsonObject
            ?: body.get("profile") as? JsonObject
        val nick = profile?.str("nick") ?: profile?.str("nickname") ?: cookieNick
        val avatar = profile?.str("headpic") ?: profile?.str("avatar") ?: cookieAvatar
        nick.ifBlank { null } to avatar.ifBlank { null }
    }

    suspend fun fetchLikedSongs(): List<LxSong>? = fetchDissSongs(liked = true, disstid = null)

    /**
     * 红心开关：第四版配方（lx-music-desktop-enhanced 同款）——musicu.fcg 免 sign，
     * 混合 comm（ct=11 + authst + tmeAppID）+ 完整 Cookie + Origin。
     * 前三版（zza 客户端 sign、zzb+musics.fcg、WEB g_tk 模式）分别被 80105/500003/80105 拒绝。
     */
    suspend fun applySongLike(songId: Long, songType: Int, like: Boolean): TxSongLikeResult =
        withContext(Dispatchers.IO) {
            val (cookie, _) = loginCookie()
                ?: return@withContext TxSongLikeResult(false, -1, "not logged in")
            val param = JsonObject().apply {
                addProperty("dirId", 201L)
                addProperty("tid", 0L)
                addProperty("bFmtUtf8", true)
                add("v_songInfo", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("songType", songType)
                        addProperty("songId", songId)
                    })
                })
            }
            val method = if (like) "AddSonglist" else "DelSonglist"
            val block = musicuCall(
                cookie, "req_0", "music.musicasset.PlaylistDetailWrite", method, param,
                comm = hybridComm(cookie), withSign = false,
            ) ?: return@withContext TxSongLikeResult(false, -1, "network error")
            val code = block.intField("code") ?: -1
            if (code != 0) {
                Timber.w("$TAG: $method rejected code=$code block=$block")
                return@withContext TxSongLikeResult(false, code, "gateway")
            }
            // retCode 变体兼容：顶层/ret_code/data 内层都可能
            val data = block.get("data") as? JsonObject
            val retCode = block.intField("retCode")
                ?: block.intField("ret_code")
                ?: data?.intField("retCode")
                ?: (data?.get("result") as? JsonObject)?.intField("retCode")
            if (retCode != null && retCode != 0) {
                Timber.w("$TAG: $method retCode=$retCode")
                return@withContext TxSongLikeResult(false, retCode, "retCode")
            }
            TxSongLikeResult(true)
        }

    suspend fun fetchPlaylistSongs(disstid: String): List<LxSong>? {
        if (disstid.isBlank() || disstid.any { !it.isDigit() }) return null
        return fetchDissSongs(liked = false, disstid = disstid)
    }

    /**
     * 按 songmid 反查数字 songId/songType。榜单、专辑详情等入口只带 songmid，
     * 而红心接口 AddSonglist 需要数字 songId，收藏前先在这里补齐。
     */
    suspend fun resolveSongLikeInfoByMid(songmid: String): Pair<Long, Int>? =
        withContext(Dispatchers.IO) {
            if (songmid.isBlank()) return@withContext null
            val (cookie, _) = loginCookie() ?: return@withContext null
            val param = JsonObject().apply { addProperty("song_mid", songmid) }
            val block = musicuCall(cookie, "req_1", "music.pf_song_detail_svr", "GetSongDetail", param)
                ?: return@withContext null
            val trackSource = (block.get("data") as? JsonObject)?.takeIf { it.keySet().isNotEmpty() } ?: block
            val track = mapTrack(trackSource) ?: return@withContext null
            val id = track.extraFields["songId"]?.toLongOrNull() ?: return@withContext null
            id to (track.extraFields["songType"]?.toIntOrNull() ?: 0)
        }

    /**
     * 收藏/取消收藏歌单。三段式尝试：
     * ① 老接口 fcg_qm_order_diss.fcg（jsososo/QQMusicApi、CoCoMusic 等多项目长期使用，
     *    form-urlencoded + g_tk 按 skey 实算；code==0 成功、code==1000 未登录）；
     * ② musicu.fcg PlaylistFavWrite web 通道（lx-music-desktop-enhanced 配方：
     *    hybridComm ct=11 + authst + tmeAppID，免 sign）；
     * ③ musicu.fcg app 通道兜底（paperclip 配方 ct=1/cv=200605 + zzc 签名 + _webcgikey）。
     *
     * ⚠️ 三条通道均未真机验证；①②③失败原因会打日志便于排查。
     */
    suspend fun applyPlaylistFav(disstid: String, fav: Boolean): Boolean = withContext(Dispatchers.IO) {
        val id = disstid.toLongOrNull()?.takeIf { it > 0 } ?: return@withContext false
        val (cookie, cookies) = loginCookie() ?: return@withContext false
        val method = if (fav) "FavPlaylist" else "CancelFavPlaylist"
        val param = JsonObject().apply {
            add("v_playlistId", JsonArray().apply { add(id) })
        }

        // ① 老接口：false（被拒）与 null（网络/解析失败）都继续尝试 musicu 通道
        when (val legacy = runCatching { legacyOrderDiss(cookie, cookies, id, fav) }.getOrNull()) {
            true -> return@withContext true
            else -> Timber.w("$TAG: legacy order_diss not conclusive (%s), trying musicu %s", legacy, method)
        }

        val webBlock = runCatching {
            musicuCall(cookie, "req_0", FAV_WRITE_MODULE, method, param, comm = hybridComm(cookie), withSign = false)
        }.getOrNull()
        if (webBlock != null && favBlockOk(webBlock)) return@withContext true
        if (webBlock != null) {
            Timber.w("$TAG: %s web rejected block=%s, trying app channel", method, webBlock)
        }

        val appRoot = appChannelCall(cookies, method, param) ?: return@withContext false
        val ok = favBlockOk(appRoot)
        if (!ok) Timber.w("$TAG: %s app rejected root=%s", method, appRoot)
        ok
    }

    /**
     * 老接口收藏/取消收藏（fcg_qm_order_diss.fcg，optype 1=收藏 2=取消）。
     * 返回 true=code 0 成功；false=服务端明确拒绝；null=网络失败/响应不可解析。
     */
    private suspend fun legacyOrderDiss(
        cookie: String,
        cookies: Map<String, String>,
        disstid: Long,
        fav: Boolean,
    ): Boolean? = withContext(Dispatchers.IO) {
        val uin = cookieUin(cookies)
        if (uin.isEmpty()) return@withContext null
        val gtk = legacyGtk(cookies)
        val referer = "https://y.qq.com/n/yqq/playlist/$disstid.html"
        val form = linkedMapOf(
            "loginUin" to uin,
            "hostUin" to "0",
            "format" to "json",
            "inCharset" to "GB2312",
            "outCharset" to "utf8",
            "notice" to "0",
            "platform" to "yqq",
            "needNewCode" to "0",
            "g_tk" to gtk.toString(),
            "uin" to uin,
            "dissid" to disstid.toString(),
            "from" to "1",
            "optype" to if (fav) "1" else "2",
            "utf8" to "1",
            "qzreferrer" to referer,
        ).entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        val request = Request.Builder()
            .url("$LEGACY_ORDER_DISS_URL?g_tk=$gtk")
            .header("User-Agent", USER_AGENT)
            .header("Referer", referer)
            .header("Cookie", cookie)
            .post(form.toRequestBody(FORM_MEDIA_TYPE))
            .build()
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("$TAG: legacy order_diss http %s", response.code)
                    return@use null
                }
                val root = runCatching {
                    JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                }.getOrNull() ?: return@use null
                val code = root.intField("code")
                when (code) {
                    0 -> true
                    else -> {
                        Timber.w("$TAG: legacy order_diss rejected code=%s (1000=未登录)", code)
                        false
                    }
                }
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: legacy order_diss call failed")
            null
        }
    }

    /** FavPlaylist 家族成功判定：block code==0，且存在 retCode/ret_code 时要求为 0。 */
    private fun favBlockOk(block: JsonObject): Boolean {
        val code = block.intField("code") ?: -1
        if (code != 0) return false
        val data = block.get("data") as? JsonObject
        val retCode = block.intField("retCode")
            ?: block.intField("ret_code")
            ?: data?.intField("retCode")
            ?: (data?.get("result") as? JsonObject)?.intField("retCode")
        return retCode == null || retCode == 0
    }

    /**
     * app 通道（paperclip appPost 同款）：
     * POST https://u6.y.qq.com/cgi-bin/musics.fcg?_webcgikey=<method>&_=<ms>&sign=zzc<...>
     * comm: ct=1/cv=200605 + g_tk=hash33(musickey) + mesh_devops；
     * Cookie: uin=o<uin>; qm_keyst=<musickey>；content-type x-www-form-urlencoded。
     */
    private fun appChannelCall(cookies: Map<String, String>, method: String, param: JsonObject): JsonObject? {
        val key = musicKey(cookies)
        val uin = cookieUin(cookies)
        if (key.isEmpty() || uin.isEmpty()) return null
        val body = JsonObject().apply {
            add("comm", JsonObject().apply {
                addProperty("g_tk", hash33(key))
                addProperty("uin", uin.toLongOrNull() ?: 0L)
                addProperty("format", "json")
                addProperty("inCharset", "utf-8")
                addProperty("outCharset", "utf-8")
                addProperty("notice", 0)
                addProperty("platform", "h5")
                addProperty("needNewCode", 1)
                addProperty("ct", 1)
                addProperty("cv", 200605)
                addProperty("mesh_devops", "DevopsBase")
            })
            add("req_0", JsonObject().apply {
                addProperty("module", FAV_WRITE_MODULE)
                addProperty("method", method)
                add("param", param)
            })
        }.toString()
        val url = APP_MUSICU_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("_webcgikey", method)
            addQueryParameter("_", System.currentTimeMillis().toString())
            addQueryParameter("sign", zzcSign(body))
        }.build().toString()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .header("Cookie", "uin=o$uin; qm_keyst=$key")
            // 参考实现（paperclip）以 form content-type 发送 JSON 字符串：
            // OkHttp 会用请求体 contentType 决定最终 Content-Type 头，故直接用 FORM_MEDIA_TYPE
            .post(body.toRequestBody(FORM_MEDIA_TYPE))
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("$TAG: app channel http %s", response.code)
                    return@use null
                }
                runCatching {
                    JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                }.getOrNull()?.getAsJsonObject("req_0")
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: app channel call failed ($method)")
            null
        }
    }

    private suspend fun fetchDissSongs(liked: Boolean, disstid: String?): List<LxSong>? =
        withContext(Dispatchers.IO) {
            val (cookie, _) = loginCookie() ?: return@withContext null
            val songs = mutableListOf<LxSong>()
            var offset = 0
            var total = -1L
            repeat(MAX_PAGES) {
                val param = JsonObject().apply {
                    addProperty("disstid", if (liked) 0L else disstid?.toLongOrNull() ?: 0L)
                    addProperty("dirid", if (liked) 201 else 0)
                    addProperty("tag", true)
                    addProperty("song_begin", offset)
                    addProperty("song_num", PAGE_SIZE)
                    addProperty("userinfo", true)
                    addProperty("orderlist", true)
                    addProperty("onlysonglist", false)
                }
                val block = musicuCall(cookie, "req_1", DISS_MODULE, DISS_METHOD, param)
                    ?: return@withContext if (songs.isEmpty()) null else songs.toList()
                if (block.get("code")?.takeIf { it.isJsonPrimitive }?.asInt != 0) {
                    Timber.w("$TAG: diss page failed code=${block.get("code")}")
                    return@withContext if (songs.isEmpty()) null else songs.toList()
                }
                val data = block.get("data") as? JsonObject
                    ?: return@withContext if (songs.isEmpty()) null else songs.toList()
                if (total < 0) {
                    total = data.long("total_song_num") ?: data.long("songlist_size") ?: -1L
                }
                val songlist = data.get("songlist") as? JsonArray ?: JsonArray()
                val mapped = songlist.mapNotNull(::mapTrack)
                songs += mapped
                if (mapped.isEmpty() || songlist.size() < PAGE_SIZE) return@withContext songs.toList()
                offset += PAGE_SIZE
                if (total > 0 && offset >= total) return@withContext songs.toList()
            }
            songs.toList()
        }

    suspend fun fetchUserPlaylists(): List<LxPlaylist>? = withContext(Dispatchers.IO) {
        val (cookie, cookies) = loginCookie() ?: return@withContext null
        val uin = cookieUin(cookies)
        val created = runCatching {
            val url = CREATED_DISS_URL.toHttpUrl().newBuilder().apply {
                addQueryParameter("hostUin", "0")
                addQueryParameter("hostuin", uin)
                addQueryParameter("sin", "0")
                addQueryParameter("size", "200")
                addQueryParameter("g_tk", "5381")
                addQueryParameter("loginUin", uin)
                addQueryParameter("format", "json")
                addQueryParameter("inCharset", "utf8")
                addQueryParameter("outCharset", "utf-8")
                addQueryParameter("notice", "0")
                addQueryParameter("platform", "yqq.json")
                addQueryParameter("needNewCode", "0")
            }.build()
            val request = Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y.qq.com/portal/profile.html")
                .header("Cookie", cookie)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val root = runCatching {
                    JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                }.getOrNull() ?: return@use emptyList()
                ((root.get("data") as? JsonObject)?.get("disslist") as? JsonArray)
                    ?.mapNotNull(::mapPlaylist)
                    .orEmpty()
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: created diss failed")
            emptyList()
        }
        val collected = runCatching {
            val url = ORDER_ASSET_URL.toHttpUrl().newBuilder().apply {
                addQueryParameter("ct", "20")
                addQueryParameter("cid", "205360956")
                addQueryParameter("userid", uin)
                addQueryParameter("reqtype", "3")
                addQueryParameter("sin", "0")
                addQueryParameter("ein", "80")
            }.build()
            val request = Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y.qq.com/portal/profile.html")
                .header("Cookie", cookie)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val root = runCatching {
                    JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                }.getOrNull() ?: return@use emptyList()
                ((root.get("data") as? JsonObject)?.get("cdlist") as? JsonArray)
                    ?.mapNotNull { mapPlaylist(it, subscribed = true) }
                    .orEmpty()
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: order asset failed")
            emptyList()
        }
        // "我喜欢"(dirid=201) 由页面单独构造入口，这里排除避免重复；同时滤掉 QQ 空间背景音乐歌单。
        (created + collected)
            .filter { it.name.isNotBlank() && !QZONE_PLAYLIST.containsMatchIn("${it.name} ${it.creator.orEmpty()}") }
            .distinctBy { it.id }
    }

    /**
     * 收藏的专辑（order_asset reqtype=2，data.albumlist）：先 sin=0&ein=0 探测 data.total，
     * 再 sin=0&ein=<total> 拉全量。未登录/请求失败返回 null（与“成功但为空”区分）。
     */
    suspend fun fetchLikedAlbums(): List<LxAlbum>? = withContext(Dispatchers.IO) {
        val (cookie, cookies) = loginCookie() ?: return@withContext null
        val uin = cookieUin(cookies)

        fun orderAssetCall(sin: Int, ein: Int): JsonObject? {
            val url = ORDER_ASSET_URL.toHttpUrl().newBuilder().apply {
                addQueryParameter("ct", "20")
                addQueryParameter("cid", "205360956")
                addQueryParameter("userid", uin)
                addQueryParameter("reqtype", "2")
                addQueryParameter("sin", sin.toString())
                addQueryParameter("ein", ein.toString())
            }.build()
            val request = Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y.qq.com/portal/profile.html")
                .header("Cookie", cookie)
                .get()
                .build()
            return runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: liked albums http %s", response.code)
                        return@use null
                    }
                    runCatching {
                        JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                    }.getOrNull()
                }
            }.getOrElse { e ->
                Timber.w(e, "$TAG: liked albums request failed")
                null
            }
        }

        // 第一步：空区间探测 total。该端点的专辑计数在 data.totalalbum（老字段 total 不存在，
        // 读不到会误判为 0 而直接返回空列表——jsososo/QQMusicApi /collect/album 同端点同字段）
        val probe = orderAssetCall(sin = 0, ein = 0) ?: return@withContext null
        val total = ((probe.get("data") as? JsonObject)?.intField("totalalbum"))
            ?: ((probe.get("data") as? JsonObject)?.intField("total")) ?: 0
        if (total <= 0) return@withContext emptyList()

        // 第二步：sin=0&ein=<total> 拉全量（ein 按调研规格取 total）
        val root = orderAssetCall(sin = 0, ein = total) ?: return@withContext null
        val albumList = (root.get("data") as? JsonObject)?.get("albumlist") as? JsonArray
            ?: JsonArray()
        albumList.mapNotNull { raw ->
            val obj = raw as? JsonObject ?: return@mapNotNull null
            val albumMid = obj.str("albummid")?.takeIf(String::isNotBlank)
            val albumId = obj.str("albumid")?.takeIf { it.isNotBlank() && it != "0" }
            val id = albumMid ?: albumId ?: return@mapNotNull null
            val picMid = obj.str("picmid")?.takeIf(String::isNotBlank) ?: albumMid
            LxAlbum(
                id = id,
                name = obj.str("albumname") ?: obj.str("name") ?: "",
                pic = picMid?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" },
                artistName = ((obj.get("singers") as? JsonArray)
                    ?.mapNotNull { (it as? JsonObject)?.str("name") }
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() })
                    ?.joinToString("/"),
                size = obj.intField("song_count") ?: 0,
            )
        }
    }

    /**
     * 关注的歌手（fcg_order_singer_getlist.fcg）：perpage=30 翻页直到 num；
     * 字段做大小写多形态兼容（FSINGERNAME/name 等）。未登录/请求失败返回 null。
     */
    suspend fun fetchFollowedArtists(): List<LxArtist>? = withContext(Dispatchers.IO) {
        val (cookie, cookies) = loginCookie() ?: return@withContext null
        val uin = cookieUin(cookies)
        val result = mutableListOf<LxArtist>()
        var page = 1
        val perPage = 30
        val maxPages = 20
        var total = -1
        while (page <= maxPages) {
            val url = ORDER_SINGER_URL.toHttpUrl().newBuilder().apply {
                addQueryParameter("utf8", "1")
                addQueryParameter("page", page.toString())
                addQueryParameter("perpage", perPage.toString())
                addQueryParameter("uin", uin)
                // g_tk 按登录态实算（匿名硬编码 5381 在带 Cookie 请求时会被服务端拒绝）
                addQueryParameter("g_tk", legacyGtk(cookies).toString())
                addQueryParameter("format", "json")
            }.build()
            val request = Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y.qq.com/portal/profile.html")
                .header("Cookie", cookie)
                .get()
                .build()
            val root = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: followed artists http %s", response.code)
                        return@use null
                    }
                    runCatching {
                        JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                    }.getOrNull()
                }
            }.getOrElse { e ->
                Timber.w(e, "$TAG: followed artists request failed")
                null
            } ?: return@withContext if (result.isEmpty()) null else result

            // total 用 -1 表示未知（响应缺 num/total 字段时不再误判为 0 导致首页截断），
            // 未知时依赖“返回页未满 perPage”终止
            if (total < 0) total = root.intField("num") ?: root.intField("total") ?: -1
            val list = root.get("list") as? JsonArray ?: JsonArray()
            if (list.size() == 0) break
            list.mapNotNull { raw ->
                val obj = raw as? JsonObject ?: return@mapNotNull null
                // 响应字段存在 FSINGERMID/FSINGERNAME 大写下划线风格，同时兼容 mid/name
                val mid = obj.str("singermid") ?: obj.str("FSINGERMID") ?: obj.str("mid")
                val name = obj.str("FSINGERNAME") ?: obj.str("singername")
                    ?: obj.str("name") ?: ""
                val id = mid?.takeIf(String::isNotBlank)
                    ?: obj.str("singerid")?.takeIf { it.isNotBlank() && it != "0" }
                    ?: return@mapNotNull null
                LxArtist(
                    id = id,
                    name = name,
                    pic = (obj.str("pic") ?: obj.str("avatar"))?.takeIf(String::isNotBlank),
                )
            }.let { result += it }
            if (total >= 0 && result.size >= total) break
            if (list.size() < perPage) break
            page++
        }
        result.distinctBy { it.id }
    }

    private fun mapPlaylist(raw: JsonElement, subscribed: Boolean = false): LxPlaylist? {
        val obj = raw as? JsonObject ?: return null
        val dirid = obj.str("dirid") ?: obj.str("dirId")
        if (dirid?.trim() == "201") return null
        val id = obj.str("dissid") ?: obj.str("tid") ?: obj.str("dirid") ?: obj.str("diss_id")
            ?: obj.str("id") ?: return null
        if (id.isBlank()) return null
        return LxPlaylist(
            id = id,
            name = obj.str("diss_name") ?: obj.str("name") ?: obj.str("title") ?: "",
            pic = (obj.str("diss_cover") ?: obj.str("logo") ?: obj.str("picurl") ?: obj.str("cover"))
                ?.takeIf(String::isNotBlank),
            trackCount = (obj.str("song_cnt") ?: obj.str("songnum") ?: obj.str("total_song_num"))
                ?.let { runCatching { it.toDouble().toInt() }.getOrNull() },
            creator = (obj.str("hostname") ?: obj.str("nick"))?.takeIf(String::isNotBlank),
            // order_asset（收藏列表）来源标记为 subscribed，供收藏态判定区分自建/收藏
            subscribed = subscribed,
        )
    }

    private suspend fun musicuCall(
        cookie: String,
        reqKey: String,
        module: String,
        method: String,
        param: JsonObject,
        comm: JsonObject? = null,
        withSign: Boolean = true,
    ): JsonObject? {
        val payload = JsonObject().apply {
            add("comm", comm ?: authComm(cookie))
            add(reqKey, JsonObject().apply {
                addProperty("module", module)
                addProperty("method", method)
                add("param", param)
            })
        }
        // sign 只含字母数字，可直接拼进 query。写接口走混合 comm（hybridComm：ct=11 + authst）
        // 时免 sign；读接口默认 authComm 仍带 sign（zza）保持既有稳定行为
        val body = payload.toString()
        val url = if (withSign) "$MUSICU_URL?sign=${getSecuritySign(body)}" else MUSICU_URL
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .header("Origin", "https://y.qq.com")
            .header("Cookie", cookie)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = runCatching {
                    JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                }.getOrNull() ?: return@use null
                root.getAsJsonObject(reqKey)
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: musicu call failed ($module)")
            null
        }
    }
}
