package com.gurio.gusic.data.lxmusic.kugou

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/** 用户云歌单项（get_all_list）；收藏的他人歌单以 sourceListid/sourceUserid/gid 标记来源。 */
internal data class KugouCloudList(
    val listid: Long,
    val name: String,
    val isDefault: Boolean,
    /** 收藏的专辑混在云歌单列表里：收藏歌单的 gid 以 collection_ 开头（分享 URL 实证），其他前缀判为专辑。 */
    val isAlbum: Boolean,
    val pic: String?,
    val trackCount: Int?,
    val gid: String?,
    val sourceListid: Long?,
    val sourceUserid: Long?,
)

/** 云歌单歌曲（get_list_all_file_v3）；hash 即酷狗歌曲 id，fileid 用于从歌单删除。 */
internal data class KugouCloudSong(
    val hash: String,
    val fileid: Long,
    val name: String,
    val singer: String,
    val albumName: String,
    val albumId: String?,
    val mixSongId: String?,
    val timelenMs: Long,
    val sort: Int = Int.MAX_VALUE,
)

/**
 * 酷狗云歌单（cloudlist.service）读写：用户歌单列表、“我喜欢”歌单红心读写、收藏/取消收藏他人歌单。
 *
 * 协议参照 MakcRe/KuGouMusicApi（module/user_playlist.js、playlist_track_all_new.js、
 * playlist_tracks_add.js、playlist_tracks_del.js、playlist_add.js、playlist_del.js），
 * 并与任务调研规格交叉核对：
 *
 * - 网关统一 POST https://gateway.kugou.com，云歌单服务族用两种寻址：
 *   ① x-router: cloudlist.service.kugou.com 头 + 短路径（/v7/get_all_list、/v4/get_list_all_file_v3、
 *   /v4/delete_songs、/v2/delete_list）；
 *   ② URL 直接带 /cloudlist.service 前缀且无 x-router（/cloudlist.service/v6/add_song、
 *   /cloudlist.service/v5/add_list）。
 * - query 默认注入公共参数（dfid/mid/uuid/appid/clientver/clienttime），登录态请求再附加
 *   token/userid（request.js defaultParams 行为），全部参与 signature 签名。
 * - delete_list 走独立加密：tempKey=6 位随机小写；AES key=MD5(tempKey) 前 16 字符、iv=后 16 字符
 *   （AES-128-CBC-PKCS7），密文 Base64；p=RSA PKCS1-V1_5({"aes":tempKey,"uid":uid,"token":token})
 *   hex 大写；响应为整段 Base64 密文，用同 tempKey 解密后判定 status。
 *   （与登录的 AES-256/NO_PADDING 是不同代际的协议，参照 playlist_del.js 当前实现。）
 */
internal class KugouCloudListApi(
    private val okHttpClient: OkHttpClient,
    private val cookieStore: KugouCookieStore,
) {
    companion object {
        private const val TAG = "KugouCloudListApi"
        private const val GATEWAY_URL = "https://gateway.kugou.com"
        private const val CLOUDLIST_ROUTER = "cloudlist.service.kugou.com"
        private const val LAST_AREA = "gztx"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** get_list_all_file_v3 每页条数与最大页数（300*20=6000 首封顶，防止异常响应死循环）。 */
        private const val LIST_PAGE_SIZE = 300
        private const val LIST_MAX_PAGES = 20

        /** get_all_list 每页条数与最大页数。 */
        private const val ALL_LIST_PAGE_SIZE = 100
        private const val ALL_LIST_MAX_PAGES = 5

        /** 酷狗全局歌单 gid 形如 collection_<a>_<b>_<listid>_<c>，第 3 段为原歌单 listid。 */
        private val GID_LISTID_REGEX = Regex("^collection_\\d+_\\d+_(\\d+)_\\d+$")

        /**
         * “我喜欢”云歌单的固定 listid：酷狗客户端（playlistout）实证硬编码为 4。
         * get_all_list 定位 is_default 失败时回退该值。
         */
        private const val DEFAULT_LIKED_LISTID = 4L

        /** 该族 error_code 视为登录态失效（status!=1 且命中时打印专门日志，便于排查）。 */
        private val TOKEN_INVALID_ERROR_CODES =
            setOf(100, 200, 401, 403, 1000, 1001, 1002, 2000, 2001, 2002, 2003, 2004, 2005, 10001, 10002)

        fun parseListidFromGid(gid: String?): Long? =
            gid?.trim()?.let { GID_LISTID_REGEX.find(it)?.groupValues?.get(1)?.toLongOrNull() }
                ?.takeIf { it > 0 }
    }

    /** 云歌单列表内存缓存（invalidateKgCloudListsCache / 登出时清除）。 */
    @Volatile
    private var cloudListsCache: List<KugouCloudList>? = null

    fun clearCaches() {
        cloudListsCache = null
    }

    /** 登录态；未登录返回 null。 */
    private fun credentials(): Triple<String, String, String>? {
        val token = cookieStore.getAppToken()?.takeIf(String::isNotBlank) ?: return null
        val userid = cookieStore.getAppUserid()?.takeIf(String::isNotBlank) ?: return null
        val mid = cookieStore.getOrCreateMid()
        return Triple(token, userid, mid)
    }

    /**
     * 云歌单服务 POST。path 带 /cloudlist.service 前缀时寻址方式 ②（无 x-router），
     * 否则为方式 ①（x-router 头 + 短路径）。extraQuery 附加业务参数（均参与签名）。
     */
    private fun postCloud(
        token: String,
        userid: String,
        path: String,
        body: String,
        extraQuery: Map<String, String> = emptyMap(),
    ): JsonObject? {
        val mid = cookieStore.getOrCreateMid()
        val clienttimeSec = KugouAccountApi.epochSeconds()
        val params = KugouAccountApi.commonParams(mid, clienttimeSec) +
            mapOf("token" to token, "userid" to userid) + extraQuery
        val viaRouter = !path.startsWith("/cloudlist.service")
        val request = Request.Builder()
            .url(KugouAccountApi.buildUrl(GATEWAY_URL + path, params, KugouAccountApi.signParams(params, body)))
            .headers(KugouAccountApi.commonHeaders(mid, clienttimeSec))
            .apply { if (viaRouter) header("x-router", CLOUDLIST_ROUTER) }
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("$TAG: %s http %s", path, response.code)
                    return@use null
                }
                KugouAccountApi.parseJsonObject(response.body?.string().orEmpty())
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: %s request failed", path)
            null
        }
    }

    /** 酷狗读操作成功判定：status==1 || error_code==0。 */
    private fun JsonObject.writeOk(): Boolean {
        val status = intOf("status")
        val errorCode = intOf("error_code")
        if (status == 1 || errorCode == 0) return true
        if (errorCode != null && errorCode in TOKEN_INVALID_ERROR_CODES) {
            Timber.w("$TAG: token likely invalid (error_code=%s msg=%s)", errorCode, strOf("msg"))
        }
        return false
    }

    /**
     * 酷狗写操作严格成功判定（第二份调研修正）：
     * status==1 必须满足；data.status 存在则须为 1；
     * retCode/ret_code/error_code/err_code（含 data 内）存在才查且须为 0。
     */
    private fun JsonObject.strictWriteOk(): Boolean {
        if (intOf("status") != 1) {
            val errorCode = intOf("error_code")
            if (errorCode != null && errorCode in TOKEN_INVALID_ERROR_CODES) {
                Timber.w("$TAG: write token likely invalid (error_code=%s msg=%s)", errorCode, strOf("msg"))
            }
            return false
        }
        val data = get("data") as? JsonObject
        if (data != null && data.get("status") != null && data.intOf("status") != 1) return false
        val codes = listOf(
            intOf("retCode"), intOf("ret_code"), intOf("error_code"), intOf("err_code"),
            data?.intOf("retCode"), data?.intOf("ret_code"), data?.intOf("error_code"), data?.intOf("err_code"),
        )
        return codes.filterNotNull().all { it == 0 }
    }

    /**
     * 拉取用户全部云歌单（v7/get_all_list）。失败返回 null（与“成功但为空”区分）；
     * 结果缓存于内存，供收藏/取消收藏后失效重拉。
     */
    suspend fun fetchAllCloudLists(): List<KugouCloudList>? = withContext(Dispatchers.IO) {
        cloudListsCache?.let { return@withContext it }
        val (token, userid, _) = credentials() ?: return@withContext null
        val result = mutableListOf<KugouCloudList>()
        var page = 1
        while (page <= ALL_LIST_MAX_PAGES) {
            val body = JsonObject().apply {
                addProperty("userid", userid.toLongOrNull() ?: 0L)
                addProperty("token", token)
                addProperty("total_ver", 979)
                addProperty("type", 2)
                addProperty("page", page)
                addProperty("pagesize", ALL_LIST_PAGE_SIZE)
            }.toString()
            val root = postCloud(token, userid, "/v7/get_all_list", body, extraQuery = mapOf("plat" to "1"))
                ?: return@withContext null
            if (!root.writeOk()) {
                Timber.w("$TAG: get_all_list rejected status=%s error_code=%s msg=%s",
                    root.intOf("status"), root.intOf("error_code"), root.strOf("msg"))
                return@withContext null
            }
            val info = (root.get("data") as? JsonObject)?.get("info") as? JsonArray ?: JsonArray()
            info.mapNotNull { parseCloudList(it) }.let { result += it }
            if (info.size() < ALL_LIST_PAGE_SIZE) break
            page++
        }
        result.toList().also { cloudListsCache = it }
    }

    private fun parseCloudList(raw: Any?): KugouCloudList? {
        val obj = raw as? JsonObject ?: return null
        val listid = obj.longOf("listid") ?: return null
        if (listid <= 0) return null
        // get_all_list 响应里收藏来源标识是 global_collection_id（旧字段名 gid 做兼容）
        val gid = obj.strOf("global_collection_id")?.takeIf(String::isNotBlank)
            ?: obj.strOf("gid")?.takeIf(String::isNotBlank)
        val sourceListid = obj.longOf("list_create_listid")?.takeIf { it > 0 }
            ?: parseListidFromGid(gid)
        val isDefault = obj.boolOf("is_default") == true
        // 专辑区分依据：收藏歌单的 gid 形如 collection_<n>_<uid>_<listid>_<n>（分享 URL 实证）；
        // 收藏专辑条目带其他前缀的 gid。留日志便于真机核对判定的准确性。
        val isAlbum = gid != null && !gid.startsWith("collection_")
        Timber.i(
            "$TAG: cloud list item listid=%s name=%s gid=%s isDefault=%s isAlbum=%s type=%s source=%s",
            listid, obj.strOf("name") ?: obj.strOf("listname"), gid, isDefault, isAlbum,
            obj.get("type"), obj.get("source"),
        )
        return KugouCloudList(
            listid = listid,
            name = obj.strOf("name") ?: obj.strOf("listname") ?: "",
            isDefault = isDefault,
            isAlbum = isAlbum,
            pic = obj.strOf("pic")?.takeIf(String::isNotBlank),
            trackCount = obj.intOf("count") ?: obj.intOf("song_count")
                ?: obj.longOf("total_file_num")?.toInt(),
            gid = gid,
            sourceListid = sourceListid,
            sourceUserid = obj.longOf("list_create_userid")?.takeIf { it > 0 },
        )
    }

    /**
     * “我喜欢”歌单 listid：get_all_list 中 is_default==1 项（找不到时按名称兜底，再兜底固定 4）。
     * 固定 4 的依据：酷狗客户端 playlistout 对“我喜欢”硬编码 listid=4（第二份调研实证）。
     * 命中 get_all_list 后持久缓存（KugouCookieStore），登出清除。
     */
    suspend fun likedListid(): Long? = withContext(Dispatchers.IO) {
        cookieStore.getLikedListid().takeIf { it > 0 }?.let { return@withContext it }
        val lists = fetchAllCloudLists()
        val liked = lists
            ?.firstOrNull { it.isDefault }
            ?: lists?.firstOrNull { it.name.contains("我喜欢") }
            ?: return@withContext DEFAULT_LIKED_LISTID.also {
                Timber.i("$TAG: liked list not located via get_all_list, fallback to default listid=%s", it)
            }
        liked.listid.also {
            cookieStore.setLikedListid(it)
            Timber.i("$TAG: liked cloud list resolved listid=%s", it)
        }
    }

    /**
     * 拉取云歌单全部歌曲（v4/get_list_all_file_v3，按页遍历直到拉完）。
     * 失败（未登录/请求失败/被拒/分页中途失败）一律返回 null，不返回残缺的部分结果；
     * 成功但歌单为空返回空列表。
     */
    suspend fun fetchCloudListSongs(listid: Long): List<KugouCloudSong>? = withContext(Dispatchers.IO) {
        if (listid <= 0) return@withContext null
        val (token, userid, _) = credentials() ?: return@withContext null
        val songs = mutableListOf<KugouCloudSong>()
        repeat(LIST_MAX_PAGES) { page ->
            val body = JsonObject().apply {
                addProperty("listid", listid)
                addProperty("userid", userid.toLongOrNull() ?: 0L)
                addProperty("type", 0)
                addProperty("page", page + 1)
                addProperty("pagesize", LIST_PAGE_SIZE)
                addProperty("area_code", 1)
                addProperty("allplatform", 1)
                addProperty("show_cover", 1)
                addProperty("token", token)
            }.toString()
            val root = postCloud(token, userid, "/v4/get_list_all_file_v3", body)
                ?: return@withContext null
            if (!root.writeOk()) {
                Timber.w("$TAG: get_list_all_file_v3 rejected listid=%s status=%s error_code=%s",
                    listid, root.intOf("status"), root.intOf("error_code"))
                return@withContext null
            }
            val info = (root.get("data") as? JsonObject)?.get("info") as? JsonArray ?: JsonArray()
            info.mapNotNull { parseCloudSong(it) }.let { songs += it }
            if (info.size() < LIST_PAGE_SIZE) return@withContext songs.toList().sortedBy { it.sort }
        }
        songs.toList().sortedBy { it.sort }
    }

    /** 云歌单歌曲字段做多字段兼容解析（info 项字段随版本有差异，hash/fileid 为必需）。 */
    private fun parseCloudSong(raw: Any?): KugouCloudSong? {
        val obj = raw as? JsonObject ?: return null
        val hash = obj.strOf("hash")?.lowercase()?.takeIf { it.length == 32 } ?: return null
        var name = obj.strOf("name") ?: obj.strOf("songname") ?: ""
        var singer = obj.strOf("singername") ?: ""
        if (singer.isBlank()) {
            singer = (obj.get("singers") as? JsonArray ?: obj.get("singer") as? JsonArray)
                ?.mapNotNull { el -> (el as? JsonObject)?.strOf("name") }
                ?.filter(String::isNotBlank)
                ?.joinToString("、")
                .orEmpty()
        }
        // filename 形如 "歌手 - 歌名"，作为兜底拆分
        val filename = obj.strOf("filename")?.trim()?.removeSuffix(".mp3")?.removeSuffix(".flac")
        if (filename != null) {
            val sep = filename.indexOf(" - ")
            if (sep > 0) {
                if (singer.isBlank()) singer = filename.substring(0, sep).trim()
                if (name.isBlank()) name = filename.substring(sep + 3).trim()
            } else if (name.isBlank()) {
                name = filename
            }
        }
        if (name.isBlank()) return null
        return KugouCloudSong(
            hash = hash,
            fileid = obj.longOf("fileid") ?: 0L,
            name = name,
            singer = singer,
            albumName = obj.strOf("albumname") ?: obj.strOf("remark") ?: "",
            albumId = obj.strOf("album_id")?.takeIf { it.isNotBlank() && it != "0" },
            mixSongId = obj.strOf("mixsongid")?.takeIf { it.isNotBlank() && it != "0" }
                ?: obj.longOf("mixsongid")?.takeIf { it > 0 }?.toString(),
            timelenMs = obj.longOf("timelen") ?: obj.longOf("duration") ?: 0L,
            sort = obj.intOf("sort") ?: Int.MAX_VALUE,
        )
    }

    suspend fun fetchLikedSongs(): List<KugouCloudSong>? {
        val listid = likedListid() ?: return null
        return fetchCloudListSongs(listid)
    }

    suspend fun fetchLikedSongIds(): Set<String>? =
        fetchLikedSongs()?.map { it.hash }?.toSet()

    /**
     * 红心（加入“我喜欢”云歌单）/取消红心（从“我喜欢”移除）。
     *
     * 已知服务端行为：add_song 写成功后 get_list_all_file 的快照有延迟
     * （lx-music-desktop-enhanced kg provider 用 0/350/900ms 重试确认可见性）。
     * 本实现不阻塞 UI 乐观更新：写接口判定成功即返回 true，读取缓存随后自然收敛。
     *
     * 取消需要 fileid：在“我喜欢”歌单里按 hash 反查全部行（同 hash 可能重复收藏出现多行，
     * 需全部删除）；歌单里没有该 hash 视为本来就没红心，直接按成功处理（幂等）；
     * 反查本身失败（网络/被拒）则按失败处理，避免服务端仍有红心却报告成功。
     */
    suspend fun applySongLike(
        hash: String,
        name: String,
        singer: String,
        albumId: String?,
        mixSongId: String?,
        like: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val songHash = hash.lowercase().takeIf { it.length == 32 } ?: return@withContext false
        val listid = likedListid() ?: run {
            Timber.w("$TAG: applySongLike skipped, liked listid unresolved")
            return@withContext false
        }
        if (like) {
            addSong(listid, KugouCloudSong(
                hash = songHash,
                fileid = 0L,
                name = name,
                singer = singer,
                albumName = "",
                albumId = albumId?.takeIf { it.isNotBlank() && it != "0" },
                mixSongId = mixSongId?.takeIf { it.isNotBlank() && it != "0" },
                timelenMs = 0L,
            ))
        } else {
            when (val fileids = findFileidsInLikedList(songHash)) {
                // 拉取失败：无法确认服务端状态，按失败走提示，不假装幂等成功
                null -> false
                else -> if (fileids.isEmpty()) {
                    Timber.i("$TAG: unlike hash=%s not in liked list, treat as success", songHash)
                    true
                } else {
                    deleteSongs(listid, fileids)
                }
            }
        }
    }

    /**
     * 按 hash 在“我喜欢”歌单中反查全部 fileid（同 hash 重复收藏会有多行，取消红心需全删）。
     * 返回 null 表示拉取失败（与“歌单确无该 hash”的空列表区分）。
     */
    private suspend fun findFileidsInLikedList(hash: String): List<Long>? {
        val listid = likedListid() ?: return null
        return fetchCloudListSongs(listid)
            ?.filter { it.hash == hash }
            ?.mapNotNull { it.fileid.takeIf { id -> id > 0 } }
    }

    /** v6/add_song：把歌曲加入云歌单（红心 = 加入“我喜欢”）。 */
    private suspend fun addSong(listid: Long, song: KugouCloudSong): Boolean = withContext(Dispatchers.IO) {
        val (token, userid, _) = credentials() ?: return@withContext false
        val clienttimeSec = KugouAccountApi.epochSeconds()
        val body = JsonObject().apply {
            addProperty("userid", userid.toLongOrNull() ?: 0L)
            addProperty("token", token)
            addProperty("listid", listid)
            addProperty("list_ver", 0)
            addProperty("type", 0)
            addProperty("slow_upload", 1)
            addProperty("scene", "false;null")
            add("data", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("number", 1)
                    addProperty("name", song.name)
                    addProperty("hash", song.hash)
                    addProperty("size", 0)
                    addProperty("sort", 0)
                    addProperty("timelen", song.timelenMs)
                    addProperty("bitrate", 0)
                    addProperty("album_id", song.albumId?.toLongOrNull() ?: 0L)
                    addProperty("mixsongid", song.mixSongId?.toLongOrNull() ?: 0L)
                })
            })
        }.toString()
        val root = postCloud(
            token, userid,
            "/cloudlist.service/v6/add_song", body,
            extraQuery = mapOf(
                "last_time" to clienttimeSec,
                "last_area" to LAST_AREA,
            ),
        )
        val ok = root?.strictWriteOk() == true
        if (!ok) {
            Timber.w("$TAG: add_song rejected listid=%s resp=%s", listid, root)
        } else {
            // 写成功但读取快照有延迟，失效“我喜欢”相关缓存让下一次读取重拉
            cloudListsCache = null
        }
        ok
    }

    /** v4/delete_songs：按 fileid 从云歌单移除歌曲。 */
    private suspend fun deleteSongs(listid: Long, fileids: List<Long>): Boolean = withContext(Dispatchers.IO) {
        val (token, userid, _) = credentials() ?: return@withContext false
        if (fileids.isEmpty()) return@withContext false
        val body = JsonObject().apply {
            addProperty("listid", listid)
            addProperty("userid", userid.toLongOrNull() ?: 0L)
            add("data", JsonArray().apply {
                fileids.forEach { fileid -> add(JsonObject().apply { addProperty("fileid", fileid) }) }
            })
            addProperty("type", 0)
            addProperty("token", token)
            addProperty("list_ver", 0)
        }.toString()
        val root = postCloud(token, userid, "/v4/delete_songs", body)
        val ok = root?.strictWriteOk() == true
        if (!ok) {
            Timber.w("$TAG: delete_songs rejected listid=%s resp=%s", listid, root)
        } else {
            cloudListsCache = null
        }
        ok
    }

    /**
     * 收藏他人歌单（v5/add_list type=0）：把整个歌单 add_list 到自己账号。
     * 参照 playlist_add.js：type=0（收藏）时 query 需附 last_time/last_area（userid/token 由
     * request.js defaultParams 统一注入，本实现 postCloud 同构），body 带 list_create_* 三元组。
     * @param sourceUserid 原作者 uid；从 gid 解析不到时传 0
     */
    suspend fun applyPlaylistCollect(
        name: String,
        sourceListid: Long,
        sourceUserid: Long,
        sourceGid: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val (token, userid, _) = credentials() ?: return@withContext false
        if (sourceListid <= 0) {
            Timber.w("$TAG: collect playlist missing source listid (gid=%s)", sourceGid)
            return@withContext false
        }
        val clienttimeSec = KugouAccountApi.epochSeconds()
        val body = JsonObject().apply {
            addProperty("userid", userid.toLongOrNull() ?: 0L)
            addProperty("token", token)
            addProperty("total_ver", 0)
            addProperty("name", name)
            addProperty("type", 0)
            addProperty("source", 1)
            addProperty("is_pri", 0)
            addProperty("list_create_userid", sourceUserid)
            addProperty("list_create_listid", sourceListid.toString())
            addProperty("list_create_gid", sourceGid.orEmpty())
            addProperty("from_shupinmv", 0)
        }.toString()
        val root = postCloud(
            token, userid,
            "/cloudlist.service/v5/add_list", body,
            extraQuery = mapOf(
                "last_time" to clienttimeSec,
                "last_area" to LAST_AREA,
            ),
        )
        val ok = root?.strictWriteOk() == true
        if (!ok) {
            Timber.w("$TAG: add_list rejected resp=%s", root)
        } else {
            cloudListsCache = null
        }
        ok
    }

    /**
     * 取消收藏歌单（v2/delete_list，加密族）。
     * 需要“自己账号里收藏后生成的 listid”：优先在 get_all_list 中按 global_collection_id（gid）
     * 匹配，其次按来源 listid（gid 解析），再次按歌单名精确匹配；
     * 确实没有匹配项视为未收藏，按成功处理（幂等）；get_all_list 拉取失败则按失败处理。
     */
    suspend fun applyPlaylistUnsubscribe(sourceGid: String?, name: String?): Boolean = withContext(Dispatchers.IO) {
        val sourceListid = parseListidFromGid(sourceGid)
        val lists = fetchAllCloudLists() ?: run {
            Timber.w("$TAG: unsubscribe cannot load own cloud lists, treat as failure")
            return@withContext false
        }
        val own = lists.firstOrNull { entry ->
            (!sourceGid.isNullOrBlank() && entry.gid == sourceGid) ||
                (sourceListid != null && (
                    entry.sourceListid == sourceListid ||
                        parseListidFromGid(entry.gid) == sourceListid
                    ))
        } ?: lists.firstOrNull { entry ->
            !entry.isDefault && !name.isNullOrBlank() && entry.name == name
        }
        if (own == null) {
            Timber.i("$TAG: unsubscribe no own copy (gid=%s name=%s), treat as success", sourceGid, name)
            return@withContext true
        }
        deleteList(own.listid)
    }

    /** 删除自己账号的云歌单（v2/delete_list；取消收藏与删除共用，type=1 普通歌单）。 */
    suspend fun deleteCloudList(listid: Long): Boolean = withContext(Dispatchers.IO) {
        deleteList(listid)
    }

    private suspend fun deleteList(listid: Long): Boolean = withContext(Dispatchers.IO) {
        val (token, userid, mid) = credentials() ?: return@withContext false
        if (listid <= 0) return@withContext false

        // 明文 → AES-128-CBC-PKCS7 → Base64（key/iv = MD5(6位tempKey) 前后各 16 字符）
        val tempKey = KugouAccountApi.randomLowerAlphaNum(6)
        val md5OfTempKey = KugouAccountApi.md5Hex(tempKey)
        val key16 = md5OfTempKey.substring(0, 16)
        val iv16 = md5OfTempKey.substring(16, 32)
        val plain = JsonObject().apply {
            addProperty("listid", listid)
            addProperty("total_ver", 0)
            addProperty("type", 1)
        }.toString()
        val body = KugouAccountApi.aesCbcEncryptToBase64(plain, key16, iv16)

        // p = RSA PKCS1-V1_5({"aes":tempKey,"uid":uid,"token":token})，hex 大写
        val p = KugouAccountApi.rsaPkcs1EncryptHexUppercase(
            JsonObject().apply {
                addProperty("aes", tempKey)
                addProperty("uid", userid.toLongOrNull() ?: 0L)
                addProperty("token", token)
            }.toString(),
        )

        val clienttimeSec = KugouAccountApi.epochSeconds()
        val params = KugouAccountApi.commonParams(mid, clienttimeSec) +
            mapOf(
                "token" to token,
                "userid" to userid,
                "key" to KugouAccountApi.signParamsKey(clienttimeSec),
                "last_area" to LAST_AREA,
                "last_time" to clienttimeSec,
                "p" to p,
            )
        val request = Request.Builder()
            .url(KugouAccountApi.buildUrl("$GATEWAY_URL/v2/delete_list", params, KugouAccountApi.signParams(params, body)))
            .headers(KugouAccountApi.commonHeaders(mid, clienttimeSec))
            .header("x-router", CLOUDLIST_ROUTER)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val root = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val bytes = if (response.isSuccessful) response.body?.bytes() else null
                if (bytes == null) {
                    Timber.w("$TAG: delete_list http %s", response.code)
                    return@use null
                }
                parseDeleteListResponse(bytes, key16, iv16)
            }
        }.getOrElse { e ->
            Timber.w(e, "$TAG: delete_list request failed")
            null
        }
        val ok = root?.strictWriteOk() == true
        if (!ok) {
            Timber.w("$TAG: delete_list rejected listid=%s resp=%s", listid, root)
        } else {
            cloudListsCache = null
        }
        ok
    }

    /** delete_list 响应可能是整段 AES Base64 密文，也可能是明文 JSON；两种都尝试。 */
    private fun parseDeleteListResponse(bytes: ByteArray, key16: String, iv16: String): JsonObject? {
        val text = bytes.toString(Charsets.UTF_8).trim()
        if (text.startsWith("{")) return KugouAccountApi.parseJsonObject(text)
        return runCatching {
            KugouAccountApi.parseJsonObject(
                KugouAccountApi.aesCbcDecryptBase64(text, key16, iv16),
            )
        }.getOrNull() ?: run {
            Timber.w("$TAG: delete_list response not json nor decryptable (len=%s)", text.length)
            null
        }
    }
}
