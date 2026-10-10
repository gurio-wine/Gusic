package com.gurio.gusic.data.lxmusic

import com.gurio.gusic.data.lxmusic.platform.PlatformResult
import com.gurio.gusic.data.lxmusic.tencent.TencentAccountApi
import timber.log.Timber

internal interface LxAccountExtension {
    val isAvailable: Boolean

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

    suspend fun txApplySongLike(songId: String, like: Boolean): Boolean


    suspend fun kwFetchProfile(): Pair<String?, String?>?

    suspend fun kwFetchUserPlaylists(): List<LxPlaylist>?

    suspend fun kwFetchFollowedArtists(): List<LxArtist>?

    suspend fun kwFetchLikedAlbums(): List<LxAlbum>?

    suspend fun kwFetchLikedSongIds(): Set<String>?

    suspend fun kwFetchLikedSongs(): List<LxSong>?

    suspend fun kwFetchUserPlaylistSongs(playlistId: String): List<LxSong>?


    suspend fun kgFetchUserPlaylists(): List<LxPlaylist>?

    suspend fun kgFetchCloudPlaylistSongs(listid: String): List<LxSong>?

    suspend fun kgFetchLikedSongIds(): Set<String>?

    suspend fun kgFetchLikedSongs(): List<LxSong>?

    suspend fun kgFetchProfile(): Pair<String?, String?>?


    suspend fun txFetchProfile(): Pair<String?, String?>?

    suspend fun txFetchLikedSongs(): List<LxSong>?

    suspend fun txFetchLikedAlbums(): List<LxAlbum>?

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
    override suspend fun txApplySongLike(songId: String, like: Boolean): Boolean = false
    override suspend fun kwFetchProfile(): Pair<String?, String?>? = null
    override suspend fun kwFetchUserPlaylists(): List<LxPlaylist>? = null
    override suspend fun kwFetchFollowedArtists(): List<LxArtist>? = null
    override suspend fun kwFetchLikedAlbums(): List<LxAlbum>? = null
    override suspend fun kwFetchLikedSongIds(): Set<String>? = null
    override suspend fun kwFetchLikedSongs(): List<LxSong>? = null
    override suspend fun kwFetchUserPlaylistSongs(playlistId: String): List<LxSong>? = null
    override suspend fun kgFetchUserPlaylists(): List<LxPlaylist>? = null
    override suspend fun kgFetchCloudPlaylistSongs(listid: String): List<LxSong>? = null
    override suspend fun kgFetchLikedSongIds(): Set<String>? = null
    override suspend fun kgFetchLikedSongs(): List<LxSong>? = null
    override suspend fun kgFetchProfile(): Pair<String?, String?>? = null
    override suspend fun txFetchProfile(): Pair<String?, String?>? = null
    override suspend fun txFetchLikedSongs(): List<LxSong>? = null
    override suspend fun txFetchLikedAlbums(): List<LxAlbum>? = null
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
 * 内置账号扩展：QQ音乐（tx）的账号读取走 [TencentAccountApi]，其余平台沿用不可用实现。
 * isAvailable 保持 false（账号写操作仍不可用），读取类功能照常工作。
 */
internal class LxBuiltinAccountExtension(host: LxMusicRepository) : LxAccountExtension {
    private val unavailable = LxUnavailableAccountExtension
    private val tencentApi = TencentAccountApi(host.okHttpClient, host.tencentCookieStore)

    override val isAvailable: Boolean = false

    override suspend fun kgSendSmsCode(mobile: String): Boolean = unavailable.kgSendSmsCode(mobile)
    override suspend fun kgLoginBySmsCode(mobile: String, code: String): Boolean =
        unavailable.kgLoginBySmsCode(mobile, code)
    override suspend fun kgApplySongLike(
        songmid: String,
        hash: String?,
        name: String,
        singer: String,
        albumId: String?,
        mixSongId: String?,
        like: Boolean,
    ): Boolean = unavailable.kgApplySongLike(songmid, hash, name, singer, albumId, mixSongId, like)
    override suspend fun kgQueryFollowInfo(singerId: String): Boolean? =
        unavailable.kgQueryFollowInfo(singerId)
    override suspend fun kgApplyFollowSinger(singerId: String, follow: Boolean, source: Int): Boolean =
        unavailable.kgApplyFollowSinger(singerId, follow, source)
    override suspend fun kgSubscribePlaylist(name: String, gid: String): Boolean =
        unavailable.kgSubscribePlaylist(name, gid)
    override suspend fun kgUnsubscribe(gid: String?, name: String?): Boolean =
        unavailable.kgUnsubscribe(gid, name)
    override suspend fun kgDeleteCloudList(listid: String): Boolean =
        unavailable.kgDeleteCloudList(listid)
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

    // 红心写接口经上游实测被业务码 80105 拒绝，保持只读
    override suspend fun txApplySongLike(songId: String, like: Boolean): Boolean = false

    override suspend fun kwFetchProfile(): Pair<String?, String?>? = unavailable.kwFetchProfile()
    override suspend fun kwFetchUserPlaylists(): List<LxPlaylist>? = unavailable.kwFetchUserPlaylists()
    override suspend fun kwFetchFollowedArtists(): List<LxArtist>? = unavailable.kwFetchFollowedArtists()
    override suspend fun kwFetchLikedAlbums(): List<LxAlbum>? = unavailable.kwFetchLikedAlbums()
    override suspend fun kwFetchLikedSongIds(): Set<String>? = unavailable.kwFetchLikedSongIds()
    override suspend fun kwFetchLikedSongs(): List<LxSong>? = unavailable.kwFetchLikedSongs()
    override suspend fun kwFetchUserPlaylistSongs(playlistId: String): List<LxSong>? =
        unavailable.kwFetchUserPlaylistSongs(playlistId)
    override suspend fun kgFetchUserPlaylists(): List<LxPlaylist>? = unavailable.kgFetchUserPlaylists()
    override suspend fun kgFetchCloudPlaylistSongs(listid: String): List<LxSong>? =
        unavailable.kgFetchCloudPlaylistSongs(listid)
    override suspend fun kgFetchLikedSongIds(): Set<String>? = unavailable.kgFetchLikedSongIds()
    override suspend fun kgFetchLikedSongs(): List<LxSong>? = unavailable.kgFetchLikedSongs()
    override suspend fun kgFetchProfile(): Pair<String?, String?>? = unavailable.kgFetchProfile()

    override suspend fun txFetchProfile(): Pair<String?, String?>? = tencentApi.fetchProfile()
    override suspend fun txFetchLikedSongs(): List<LxSong>? = tencentApi.fetchLikedSongs()
    override suspend fun txFetchLikedAlbums(): List<LxAlbum>? = null
    override suspend fun txFetchUserPlaylists(): List<LxPlaylist>? = tencentApi.fetchUserPlaylists()
    override suspend fun txFetchPlaylistSongs(disstid: String): List<LxSong>? =
        tencentApi.fetchPlaylistSongs(disstid)

    override suspend fun txQrCreate(type: LxTxQrType): Pair<ByteArray, String>? =
        unavailable.txQrCreate(type)
    override suspend fun txQrPoll(type: LxTxQrType, session: String): LxTxQrPoll =
        unavailable.txQrPoll(type, session)

    override suspend fun kwFetchPlaylistSquareSongs(playlistId: String): List<LxSong>? =
        unavailable.kwFetchPlaylistSquareSongs(playlistId)
    override suspend fun kwResolvePlayUrl(songmid: String): PlatformResult? =
        unavailable.kwResolvePlayUrl(songmid)

    override fun clearReadCaches() {}
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
