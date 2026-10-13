package com.gurio.gusic.data.lxmusic

import com.gurio.gusic.data.lxmusic.kugou.KugouAccountApi
import com.gurio.gusic.data.lxmusic.kugou.KugouCloudListApi
import com.gurio.gusic.data.lxmusic.kugou.KugouCloudSong
import com.gurio.gusic.data.lxmusic.kugou.KugouRelationApi
import com.gurio.gusic.data.lxmusic.kuwo.KuwoAccountApi
import com.gurio.gusic.data.lxmusic.platform.PlatformResult
import com.gurio.gusic.data.lxmusic.tencent.TencentAccountApi
import timber.log.Timber

/**
 * 平台账号写操作粒度：UI 门控按“具体操作 × 平台”查询能力，而非全局开关，
 * 以便只放行当前实现真实支持的写操作（其余入口保持现状不亮起）。
 */
enum class LxWriteOp {
    /** 红心/喜欢歌曲（播放页爱心、歌曲信息页爱心）。 */
    SONG_LIKE,

    /** 收藏/取消收藏歌单（歌单详情、榜单、搜索页收藏胶囊）。 */
    PLAYLIST_COLLECT,

    /** 添加歌曲到自建歌单（“加入歌单”弹层）。 */
    PLAYLIST_ADD_SONGS,

    /** 从自建歌单移除歌曲。 */
    PLAYLIST_REMOVE_SONGS,

    /** 删除自建歌单。 */
    PLAYLIST_DELETE,

    /** 关注/取关歌手。 */
    ARTIST_FOLLOW,

    /** 收藏/取消收藏专辑。 */
    ALBUM_COLLECT,
}

internal interface LxAccountExtension {
    val isAvailable: Boolean

    /**
     * 平台账号写能力矩阵：source（LxSources.*）→ 支持的写操作集合。
     * 未声明的组合一律视为不可用；网易（wy）不走本扩展，由 cookie 门控。
     */
    fun writeCapabilities(): Map<String, Set<LxWriteOp>>

    suspend fun kgSendSmsCode(mobile: String): Boolean

    suspend fun kgLoginBySmsCode(mobile: String, code: String): Boolean

    suspend fun kgApplySongLike(
        songmid: String,
        hash: String?,
        name: String,
        singer: String,
        albumId: String?,
        mixSongId: String?,
        like: Boolean,
    ): Boolean

    suspend fun kgQueryFollowInfo(singerId: String): Boolean?

    suspend fun kgApplyFollowSinger(singerId: String, follow: Boolean, source: Int): Boolean

    suspend fun kgSubscribePlaylist(name: String, gid: String): Boolean

    suspend fun kgUnsubscribe(gid: String?, name: String?): Boolean

    suspend fun kgDeleteCloudList(listid: String): Boolean

    suspend fun kgCollectByCopy(name: String, songs: List<LxSong>): Boolean

    suspend fun kgAddSongsToPlaylist(listid: String, songs: List<LxSong>): LxAddToPlaylistResult

    suspend fun kgRemoveSongsFromPlaylist(listid: String, songs: List<LxSong>): Boolean

    suspend fun kwApplySongLike(songId: String, like: Boolean): Boolean

    suspend fun kwAddSongsToPlaylist(playlistId: String, songIds: List<String>): LxAddToPlaylistResult

    suspend fun kwRemoveSongsFromPlaylist(playlistId: String, songIds: List<String>): Boolean

    suspend fun kwSetPlaylistCollect(playlistId: String, collect: Boolean): Boolean

    suspend fun kwApplyArtistFollow(artistId: String, follow: Boolean): Boolean

    suspend fun kwApplyAlbumLike(albumId: String, like: Boolean): Boolean

    suspend fun txApplySongLike(songId: String, songType: Int, like: Boolean): TxSongLikeResult

    /** 按 songmid 反查红心需要的数字 songId/songType；无票据或查不到返回 null。 */
    suspend fun txResolveSongLikeInfo(songmid: String): Pair<Long, Int>?

    /** tx 收藏/取消收藏歌单（PlaylistFavWrite.FavPlaylist/CancelFavPlaylist）。 */
    suspend fun txSetPlaylistFav(disstid: String, fav: Boolean): Boolean


    suspend fun kwFetchProfile(): Pair<String?, String?>?

    suspend fun kwFetchUserPlaylists(): List<LxPlaylist>?

    suspend fun kwFetchFollowedArtists(): List<LxArtist>?

    suspend fun kwFetchLikedAlbums(): List<LxAlbum>?

    suspend fun kwFetchLikedSongIds(): Set<String>?

    suspend fun kwFetchLikedSongs(): List<LxSong>?

    suspend fun kwFetchUserPlaylistSongs(playlistId: String): List<LxSong>?


    suspend fun kgFetchUserPlaylists(): List<LxPlaylist>?

    /** 酷狗收藏的专辑（混在云歌单列表里，按 gid 前缀区分出来的条目）。 */
    suspend fun kgFetchCollectedAlbums(): List<LxPlaylist>?

    /** 酷狗关注的歌手（v4/follow_list）；未登录/不可用返回 null。 */
    suspend fun kgFetchFollowedArtists(): List<LxArtist>?

    suspend fun kgFetchCloudPlaylistSongs(listid: String): List<LxSong>?

    suspend fun kgFetchLikedSongIds(): Set<String>?

    suspend fun kgFetchLikedSongs(): List<LxSong>?

    suspend fun kgFetchProfile(): Pair<String?, String?>?


    suspend fun txFetchProfile(): Pair<String?, String?>?

    suspend fun txFetchLikedSongs(): List<LxSong>?

    suspend fun txFetchLikedAlbums(): List<LxAlbum>?

    /** tx 关注的歌手（musicu.fcg RelationList.GetFollowSingerList）；未登录/不可用返回 null。 */
    suspend fun txFetchFollowedArtists(): List<LxArtist>?

    suspend fun txFetchUserPlaylists(): List<LxPlaylist>?

    suspend fun txFetchPlaylistSongs(disstid: String): List<LxSong>?


    suspend fun txQrCreate(type: LxTxQrType): Pair<ByteArray, String>?

    suspend fun txQrPoll(type: LxTxQrType, session: String): LxTxQrPoll


    suspend fun kwFetchPlaylistSquareSongs(playlistId: String): List<LxSong>?

    suspend fun kwResolvePlayUrl(songmid: String): PlatformResult?

    fun clearReadCaches()
}

internal object LxUnavailableAccountExtension : LxAccountExtension {
    override val isAvailable: Boolean = false
    override fun writeCapabilities(): Map<String, Set<LxWriteOp>> = emptyMap()
    override suspend fun kgSendSmsCode(mobile: String): Boolean = false
    override suspend fun kgLoginBySmsCode(mobile: String, code: String): Boolean = false
    override suspend fun kgApplySongLike(
        songmid: String,
        hash: String?,
        name: String,
        singer: String,
        albumId: String?,
        mixSongId: String?,
        like: Boolean,
    ): Boolean = false
    override suspend fun kgQueryFollowInfo(singerId: String): Boolean? = null
    override suspend fun kgApplyFollowSinger(singerId: String, follow: Boolean, source: Int): Boolean = false
    override suspend fun kgSubscribePlaylist(name: String, gid: String): Boolean = false
    override suspend fun kgUnsubscribe(gid: String?, name: String?): Boolean = false
    override suspend fun kgDeleteCloudList(listid: String): Boolean = false
    override suspend fun kgCollectByCopy(name: String, songs: List<LxSong>): Boolean = false
    override suspend fun kgAddSongsToPlaylist(listid: String, songs: List<LxSong>): LxAddToPlaylistResult = LxAddToPlaylistResult.FAILED
    override suspend fun kgRemoveSongsFromPlaylist(listid: String, songs: List<LxSong>): Boolean = false
    override suspend fun kwApplySongLike(songId: String, like: Boolean): Boolean = false
    override suspend fun kwAddSongsToPlaylist(playlistId: String, songIds: List<String>): LxAddToPlaylistResult = LxAddToPlaylistResult.FAILED
    override suspend fun kwRemoveSongsFromPlaylist(playlistId: String, songIds: List<String>): Boolean = false
    override suspend fun kwSetPlaylistCollect(playlistId: String, collect: Boolean): Boolean = false
    override suspend fun kwApplyArtistFollow(artistId: String, follow: Boolean): Boolean = false
    override suspend fun kwApplyAlbumLike(albumId: String, like: Boolean): Boolean = false
    override suspend fun txApplySongLike(songId: String, songType: Int, like: Boolean): TxSongLikeResult =
        TxSongLikeResult(false, -1, "account unavailable")
    override suspend fun txResolveSongLikeInfo(songmid: String): Pair<Long, Int>? = null
    override suspend fun txSetPlaylistFav(disstid: String, fav: Boolean): Boolean = false
    override suspend fun kwFetchProfile(): Pair<String?, String?>? = null
    override suspend fun kwFetchUserPlaylists(): List<LxPlaylist>? = null
    override suspend fun kwFetchFollowedArtists(): List<LxArtist>? = null
    override suspend fun kwFetchLikedAlbums(): List<LxAlbum>? = null
    override suspend fun kwFetchLikedSongIds(): Set<String>? = null
    override suspend fun kwFetchLikedSongs(): List<LxSong>? = null
    override suspend fun kwFetchUserPlaylistSongs(playlistId: String): List<LxSong>? = null
    override suspend fun kgFetchUserPlaylists(): List<LxPlaylist>? = null
    override suspend fun kgFetchCollectedAlbums(): List<LxPlaylist>? = null
    override suspend fun kgFetchFollowedArtists(): List<LxArtist>? = null
    override suspend fun kgFetchCloudPlaylistSongs(listid: String): List<LxSong>? = null
    override suspend fun kgFetchLikedSongIds(): Set<String>? = null
    override suspend fun kgFetchLikedSongs(): List<LxSong>? = null
    override suspend fun kgFetchProfile(): Pair<String?, String?>? = null
    override suspend fun txFetchProfile(): Pair<String?, String?>? = null
    override suspend fun txFetchLikedSongs(): List<LxSong>? = null
    override suspend fun txFetchLikedAlbums(): List<LxAlbum>? = null
    override suspend fun txFetchFollowedArtists(): List<LxArtist>? = null
    override suspend fun txFetchUserPlaylists(): List<LxPlaylist>? = null
    override suspend fun txFetchPlaylistSongs(disstid: String): List<LxSong>? = null
    override suspend fun txQrCreate(type: LxTxQrType): Pair<ByteArray, String>? = null
    override suspend fun txQrPoll(type: LxTxQrType, session: String): LxTxQrPoll =
        LxTxQrPoll(LxTxQrPoll.Status.ERROR)
    override suspend fun kwFetchPlaylistSquareSongs(playlistId: String): List<LxSong>? = null
    override suspend fun kwResolvePlayUrl(songmid: String): PlatformResult? = null
    override fun clearReadCaches() {}
}

enum class LxTxQrType { QQ, WECHAT }

data class LxTxQrPoll(
    val status: Status,
    val cookie: String? = null,
) {
    enum class Status { PENDING, SCANNED, EXPIRED, SUCCESS, ERROR }
}

/**
 * 内置账号扩展（公开发布形态）：
 * - tx：账号读取、红心写（web PlaylistDetailWrite）、收藏/取消收藏歌单（PlaylistFavWrite，需真机验证）；
 * - kg：短信登录、资料读取，以及云歌单族读写——“我喜欢”红心读/写（get_list_all_file_v3 + v6/add_song +
 *   v4/delete_songs）、收藏/取消收藏歌单（v5/add_list + v2/delete_list）、用户云歌单与云歌单曲目读取；
 * - kw：昵称头像读取；全部写操作不可用（协议受限，另行实现）。
 *
 * 能力矩阵（writeCapabilities）只声明真实支持的写操作，UI 门控按操作粒度放行，
 * 其余写入口保持隐藏，避免出现“入口亮起但必然失败”的死路径。
 */
internal class LxBuiltinAccountExtension(host: LxMusicRepository) : LxAccountExtension {
    private val unavailable = LxUnavailableAccountExtension
    private val tencentApi = TencentAccountApi(host.okHttpClient, host.tencentCookieStore)
    private val kugouApi = KugouAccountApi(host.okHttpClient, host.kugouCookieStore)
    private val kugouCloudApi = KugouCloudListApi(host.okHttpClient, host.kugouCookieStore)
    private val kugouRelationApi = KugouRelationApi(host.okHttpClient, host.kugouCookieStore)
    private val kuwoApi = KuwoAccountApi(host.okHttpClient, host.kuwoCookieStore)

    override val isAvailable: Boolean = true

    override fun writeCapabilities(): Map<String, Set<LxWriteOp>> = mapOf(
        // kg：红心读写（云歌单“我喜欢”）+ 收藏/取消收藏他人歌单；加歌/删歌/自建歌单删除/歌手关注未实现
        LxSources.KUGOU to setOf(LxWriteOp.SONG_LIKE, LxWriteOp.PLAYLIST_COLLECT),
        // tx：红心写 + 收藏/取消收藏歌单；专辑/歌手写未实现
        LxSources.TENCENT to setOf(LxWriteOp.SONG_LIKE, LxWriteOp.PLAYLIST_COLLECT),
        // kw：写协议受限，本轮全部不开放
    )

    override suspend fun kgSendSmsCode(mobile: String): Boolean = kugouApi.sendSmsCode(mobile).ok
    override suspend fun kgLoginBySmsCode(mobile: String, code: String): Boolean =
        kugouApi.loginBySmsCode(mobile, code)

    /**
     * kg 红心写：songmid/hash 双轨——本实现拉取的 kg 歌曲 songmid 即 hash，
     * 脚本源歌曲 songmid 为 audio id，此时用 hash；两者都不是 32 位 hex 时无法定位歌曲。
     */
    override suspend fun kgApplySongLike(
        songmid: String,
        hash: String?,
        name: String,
        singer: String,
        albumId: String?,
        mixSongId: String?,
        like: Boolean,
    ): Boolean {
        val songHash = hash?.takeIf { it.length == 32 }
            ?: songmid.takeIf { it.length == 32 }
            ?: return false
        return runCatching {
            kugouCloudApi.applySongLike(songHash, name, singer, albumId, mixSongId, like)
        }.onFailure { Timber.w(it, "kgApplySongLike failed hash=%s", songHash) }
            .getOrDefault(false)
    }

    override suspend fun kgQueryFollowInfo(singerId: String): Boolean? =
        unavailable.kgQueryFollowInfo(singerId)
    override suspend fun kgApplyFollowSinger(singerId: String, follow: Boolean, source: Int): Boolean =
        unavailable.kgApplyFollowSinger(singerId, follow, source)

    /** 收藏他人歌单：listid 从全局 gid（collection_a_b_listid_c）解析；原作者 uid 暂缺省传 0。 */
    override suspend fun kgSubscribePlaylist(name: String, gid: String): Boolean {
        val sourceListid = KugouCloudListApi.parseListidFromGid(gid) ?: run {
            Timber.w("kgSubscribePlaylist: cannot parse listid from gid=%s", gid)
            return false
        }
        return runCatching {
            kugouCloudApi.applyPlaylistCollect(
                name = name,
                sourceListid = sourceListid,
                sourceUserid = 0L,
                sourceGid = gid,
            )
        }.onFailure { Timber.w(it, "kgSubscribePlaylist failed gid=%s", gid) }
            .getOrDefault(false)
    }

    override suspend fun kgUnsubscribe(gid: String?, name: String?): Boolean =
        runCatching { kugouCloudApi.applyPlaylistUnsubscribe(gid, name) }
            .onFailure { Timber.w(it, "kgUnsubscribe failed gid=%s", gid) }
            .getOrDefault(false)

    override suspend fun kgDeleteCloudList(listid: String): Boolean {
        val id = listid.toLongOrNull() ?: return false
        return runCatching { kugouCloudApi.deleteCloudList(id) }.getOrDefault(false)
    }

    override suspend fun kgCollectByCopy(name: String, songs: List<LxSong>): Boolean =
        unavailable.kgCollectByCopy(name, songs)
    override suspend fun kgAddSongsToPlaylist(listid: String, songs: List<LxSong>): LxAddToPlaylistResult =
        unavailable.kgAddSongsToPlaylist(listid, songs)
    override suspend fun kgRemoveSongsFromPlaylist(listid: String, songs: List<LxSong>): Boolean =
        unavailable.kgRemoveSongsFromPlaylist(listid, songs)
    override suspend fun kwApplySongLike(songId: String, like: Boolean): Boolean =
        unavailable.kwApplySongLike(songId, like)
    override suspend fun kwAddSongsToPlaylist(playlistId: String, songIds: List<String>): LxAddToPlaylistResult =
        unavailable.kwAddSongsToPlaylist(playlistId, songIds)
    override suspend fun kwRemoveSongsFromPlaylist(playlistId: String, songIds: List<String>): Boolean =
        unavailable.kwRemoveSongsFromPlaylist(playlistId, songIds)
    override suspend fun kwSetPlaylistCollect(playlistId: String, collect: Boolean): Boolean =
        unavailable.kwSetPlaylistCollect(playlistId, collect)
    override suspend fun kwApplyArtistFollow(artistId: String, follow: Boolean): Boolean =
        unavailable.kwApplyArtistFollow(artistId, follow)
    override suspend fun kwApplyAlbumLike(albumId: String, like: Boolean): Boolean =
        unavailable.kwApplyAlbumLike(albumId, like)

    // 红心开关走 web 端 PlaylistDetailWrite(dirId=201)，不再依赖被 80105 拒绝的脚本写接口
    override suspend fun txApplySongLike(songId: String, songType: Int, like: Boolean): TxSongLikeResult =
        songId.toLongOrNull()?.let { tencentApi.applySongLike(it, songType, like) }
            ?: TxSongLikeResult(false, -1, "invalid songId")

    override suspend fun txResolveSongLikeInfo(songmid: String): Pair<Long, Int>? =
        tencentApi.resolveSongLikeInfoByMid(songmid)

    override suspend fun txSetPlaylistFav(disstid: String, fav: Boolean): Boolean =
        runCatching { tencentApi.applyPlaylistFav(disstid, fav) }
            .onFailure { Timber.w(it, "txSetPlaylistFav failed disstid=%s fav=%s", disstid, fav) }
            .getOrDefault(false)

    override suspend fun kwFetchProfile(): Pair<String?, String?>? = kuwoApi.fetchProfile()
    // kw 用户歌单：客户端歌单同步服务 pl.svc?op=getlistbyuid（web 端无此接口）
    override suspend fun kwFetchUserPlaylists(): List<LxPlaylist>? = kuwoApi.fetchUserPlaylists()
    override suspend fun kwFetchFollowedArtists(): List<LxArtist>? = unavailable.kwFetchFollowedArtists()
    override suspend fun kwFetchLikedAlbums(): List<LxAlbum>? = unavailable.kwFetchLikedAlbums()
    // 酷我喜欢=pl.svc 歌单列表里的 MYFAVORITE 系统歌单（getlistbyuid+getlistinfo）；写操作仍不可用
    override suspend fun kwFetchLikedSongIds(): Set<String>? =
        kuwoApi.fetchLikedSongs()?.map { it.songmid }?.toSet()
    override suspend fun kwFetchLikedSongs(): List<LxSong>? = kuwoApi.fetchLikedSongs()
    override suspend fun kwFetchUserPlaylistSongs(playlistId: String): List<LxSong>? =
        kuwoApi.fetchPlaylistSongs(playlistId)

    /**
     * kg 用户云歌单：跳过“我喜欢”默认歌单（其内容已由“我喜欢”平台入口单独展示，避免重复条目——
     * 酷狗默认歌单在不同端有“我喜欢”/“我喜欢的音乐”两种叫法，按 isDefault、已定位的 likedListid
     * 与两个默认名三重过滤）；收藏的他人歌单带 subscribed=true；收藏专辑条目（isAlbum）不混入歌单。
     */
    override suspend fun kgFetchUserPlaylists(): List<LxPlaylist>? =
        runCatching {
            val likedListid = kugouCloudApi.likedListid()
            kugouCloudApi.fetchAllCloudLists()
                ?.filter { list ->
                    !list.isDefault &&
                        list.listid != likedListid &&
                        !list.isAlbum &&
                        list.name != "我喜欢" && list.name != "我喜欢的音乐"
                }
                ?.map { list ->
                    LxPlaylist(
                        id = "kg_cloud_${list.listid}",
                        name = list.name,
                        pic = list.pic,
                        trackCount = list.trackCount,
                        subscribed = list.sourceListid != null,
                        originGid = list.gid,
                    )
                }
        }.onFailure { Timber.w(it, "kgFetchUserPlaylists failed") }.getOrNull()

    /** kg 收藏的专辑：云歌单列表中 source==2（或 musiclib_id>0）的条目，点击可用同一 listid 拉取歌曲。 */
    override suspend fun kgFetchCollectedAlbums(): List<LxPlaylist>? =
        runCatching {
            kugouCloudApi.fetchAllCloudLists()
                ?.filter { it.isAlbum }
                ?.map { list ->
                    LxPlaylist(
                        id = "kg_album_${list.listid}",
                        name = list.name,
                        pic = list.pic,
                        trackCount = list.trackCount,
                        subscribed = true,
                        originGid = list.gid,
                    )
                }
        }.onFailure { Timber.w(it, "kgFetchCollectedAlbums failed") }.getOrNull()

    override suspend fun kgFetchFollowedArtists(): List<LxArtist>? =
        runCatching { kugouRelationApi.fetchFollowedArtists() }
            .onFailure { Timber.w(it, "kgFetchFollowedArtists failed") }.getOrNull()

    override suspend fun kgFetchCloudPlaylistSongs(listid: String): List<LxSong>? =
        runCatching {
            val id = listid.toLongOrNull() ?: return@runCatching null
            kugouCloudApi.fetchCloudListSongs(id)?.map { it.toLxSong() }
        }.onFailure { Timber.w(it, "kgFetchCloudPlaylistSongs failed listid=%s", listid) }.getOrNull()

    override suspend fun kgFetchLikedSongIds(): Set<String>? =
        runCatching { kugouCloudApi.fetchLikedSongIds() }
            .onFailure { Timber.w(it, "kgFetchLikedSongIds failed") }.getOrNull()

    override suspend fun kgFetchLikedSongs(): List<LxSong>? =
        runCatching { kugouCloudApi.fetchLikedSongs()?.map { it.toLxSong() } }
            .onFailure { Timber.w(it, "kgFetchLikedSongs failed") }.getOrNull()

    override suspend fun kgFetchProfile(): Pair<String?, String?>? = kugouApi.fetchProfile()

    override suspend fun txFetchProfile(): Pair<String?, String?>? = tencentApi.fetchProfile()
    override suspend fun txFetchLikedSongs(): List<LxSong>? = tencentApi.fetchLikedSongs()
    // tx 收藏的专辑（order_asset reqtype=2）；未登录/请求失败为 null，仓库层转空列表
    override suspend fun txFetchLikedAlbums(): List<LxAlbum>? =
        runCatching { tencentApi.fetchLikedAlbums() }
            .onFailure { Timber.w(it, "txFetchLikedAlbums failed") }.getOrNull()
    override suspend fun txFetchFollowedArtists(): List<LxArtist>? =
        runCatching { tencentApi.fetchFollowedArtists() }
            .onFailure { Timber.w(it, "txFetchFollowedArtists failed") }.getOrNull()
    override suspend fun txFetchUserPlaylists(): List<LxPlaylist>? = tencentApi.fetchUserPlaylists()
    override suspend fun txFetchPlaylistSongs(disstid: String): List<LxSong>? =
        tencentApi.fetchPlaylistSongs(disstid)

    override suspend fun txQrCreate(type: LxTxQrType): Pair<ByteArray, String>? =
        unavailable.txQrCreate(type)
    override suspend fun txQrPoll(type: LxTxQrType, session: String): LxTxQrPoll =
        unavailable.txQrPoll(type, session)

    // kw 歌单详情（歌单广场与用户歌单共用）：pl.svc getlistinfo，带登录态覆盖自建私有歌单
    override suspend fun kwFetchPlaylistSquareSongs(playlistId: String): List<LxSong>? =
        kuwoApi.fetchPlaylistSongs(playlistId)
    override suspend fun kwResolvePlayUrl(songmid: String): PlatformResult? =
        unavailable.kwResolvePlayUrl(songmid)

    /** 云歌单歌曲 → LxSong：songmid 直接用 hash（kg 播放与红心匹配都按 hash 定位）。 */
    private fun KugouCloudSong.toLxSong(): LxSong = LxSong(
        source = LxSources.KUGOU,
        songmid = hash,
        name = name,
        singer = singer,
        albumName = albumName,
        interval = timelenMs / 1000,
        hash = hash,
        extraFields = buildMap {
            albumId?.let { put("albumId", it) }
            mixSongId?.let { put("mixSongId", it) }
        },
    )

    override fun clearReadCaches() {
        kugouCloudApi.clearCaches()
    }
}

internal object LxAccountExtensionLoader {
    private const val PRIVATE_CLASS_NAME =
        "com.gurio.gusic.data.lxmusic.LxPrivateAccountExtension"

    // 账号实现类加载失败时退回内置实现：TX 读取可用，其余平台只读
    fun load(host: LxMusicRepository): LxAccountExtension =
        runCatching {
            val clazz = Class.forName(PRIVATE_CLASS_NAME)
            val ctor = clazz.getDeclaredConstructor(LxMusicRepository::class.java)
            ctor.isAccessible = true
            (ctor.newInstance(host) as LxAccountExtension)
                .also { Timber.i("LxAccountExtension: private implementation loaded") }
        }.getOrElse { e ->
            Timber.i(
                "LxAccountExtension: private impl unavailable, using built-in TX reads (${e.javaClass.simpleName})",
            )
            LxBuiltinAccountExtension(host)
        }
}
