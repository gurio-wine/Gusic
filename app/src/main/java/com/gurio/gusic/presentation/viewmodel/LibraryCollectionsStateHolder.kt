package com.gurio.gusic.presentation.viewmodel

import com.gurio.gusic.data.lxmusic.LxAlbum
import com.gurio.gusic.data.lxmusic.LxArtist
import com.gurio.gusic.data.lxmusic.LxMusicRepository
import com.gurio.gusic.data.lxmusic.LxSong
import com.gurio.gusic.data.lxmusic.LxSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** “收藏”分类二级页的两个内容页签 */
enum class CollectionsContentTab { ARTISTS, ALBUMS }

/** 收藏三级详情：歌手（拉歌手热门歌曲）或专辑（拉专辑曲目） */
data class CollectionDetail(
    val kind: CollectionsContentTab,
    val id: String,
    val name: String,
)

/**
 * “收藏”分类的三级结构状态（平台收藏内容的歌手/专辑）：
 * - [collectionsScope] == null：一级平台入口列表
 * - [collectionsScope] != null：二级“歌手/专辑”两个页签的内容列表（同页签内再分三级详情）
 *
 * 与“我喜欢/歌单”不同：进入平台即直接拉取（不做会话级缓存），
 * 每次进入都刷新，保证收藏变化能及时反映。
 * 酷我协议不可达（无关注歌手/收藏专辑读取接口），入口仅显示不可用提示，不发起请求。
 */
@Singleton
class LibraryCollectionsStateHolder @Inject constructor(
    private val lxMusicRepository: LxMusicRepository,
    likedStateHolder: LibraryLikedStateHolder,
) {
    companion object {
        /** 平台入口固定顺序：wy/tx/kg/kw（与“我喜欢/歌单”一致） */
        val COLLECTION_PLATFORMS = listOf(
            LxSources.NETEASE,
            LxSources.TENCENT,
            LxSources.KUGOU,
            LxSources.KUWO,
        )

        /** 协议不可达的平台：只显示不可用提示 */
        fun isPlatformSupported(source: String): Boolean = source != LxSources.KUWO

        /** 歌手热门歌曲单页拉取上限（无分页，够展示与播放） */
        private const val ARTIST_SONGS_LIMIT = 100
    }

    private val holderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 复用“我喜欢”holder 的登录平台判定，不重复实现登录状态逻辑 */
    val loggedInPlatforms: StateFlow<List<String>> = likedStateHolder.loggedInPlatforms

    private val _collectionsScope = MutableStateFlow<String?>(null)
    val collectionsScope: StateFlow<String?> = _collectionsScope.asStateFlow()

    private val _collectionsTab = MutableStateFlow(CollectionsContentTab.ARTISTS)
    val collectionsTab: StateFlow<CollectionsContentTab> = _collectionsTab.asStateFlow()

    private val _platformArtists = MutableStateFlow<List<LxArtist>>(emptyList())
    val platformArtists: StateFlow<List<LxArtist>> = _platformArtists.asStateFlow()

    private val _artistsLoading = MutableStateFlow(false)
    val artistsLoading: StateFlow<Boolean> = _artistsLoading.asStateFlow()

    private val _artistsError = MutableStateFlow<String?>(null)
    val artistsError: StateFlow<String?> = _artistsError.asStateFlow()

    private val _platformAlbums = MutableStateFlow<List<LxAlbum>>(emptyList())
    val platformAlbums: StateFlow<List<LxAlbum>> = _platformAlbums.asStateFlow()

    private val _albumsLoading = MutableStateFlow(false)
    val albumsLoading: StateFlow<Boolean> = _albumsLoading.asStateFlow()

    private val _albumsError = MutableStateFlow<String?>(null)
    val albumsError: StateFlow<String?> = _albumsError.asStateFlow()

    private val _selectedDetail = MutableStateFlow<CollectionDetail?>(null)
    val selectedDetail: StateFlow<CollectionDetail?> = _selectedDetail.asStateFlow()

    private val _detailSongs = MutableStateFlow<List<LxSong>>(emptyList())
    val detailSongs: StateFlow<List<LxSong>> = _detailSongs.asStateFlow()

    private val _detailSongsLoading = MutableStateFlow(false)
    val detailSongsLoading: StateFlow<Boolean> = _detailSongsLoading.asStateFlow()

    private val _detailSongsError = MutableStateFlow<String?>(null)
    val detailSongsError: StateFlow<String?> = _detailSongsError.asStateFlow()

    private var artistsLoadJob: Job? = null
    private var albumsLoadJob: Job? = null
    private var detailLoadJob: Job? = null

    init {
        holderScope.launch {
            var previous: Set<String> = emptySet()
            loggedInPlatforms.collect { logged ->
                // 登出后若正处于该平台层级（含三级详情），整体回到一级入口
                val loggedOut = previous - logged.toSet()
                loggedOut.forEach { source ->
                    if (_collectionsScope.value == source) {
                        backToCollectionsEntries()
                    }
                }
                previous = logged.toSet()
            }
        }
    }

    /**
     * 切换收藏二级作用域。与“我喜欢/歌单”不同：无缓存，每次进入（含同平台重进）都直接拉取。
     */
    fun selectCollectionsScope(scope: String?) {
        when (scope) {
            null -> backToCollectionsEntries()
            in COLLECTION_PLATFORMS -> {
                // 同平台且两个列表仍在途时去重，避免连点入口重复发起请求
                val inFlight = artistsLoadJob?.isActive == true || albumsLoadJob?.isActive == true
                if (_collectionsScope.value == scope && inFlight) return
                _collectionsScope.value = scope
                _collectionsTab.value = CollectionsContentTab.ARTISTS
                loadPlatformArtists(scope)
                loadPlatformAlbums(scope)
            }
            // 未知 scope 直接回到一级入口，避免悬空状态
            else -> backToCollectionsEntries()
        }
    }

    /** 切换二级“歌手/专辑”页签（数据在进入平台时已并发拉取，仅切显示） */
    fun selectCollectionsTab(tab: CollectionsContentTab) {
        _collectionsTab.value = tab
    }

    // 歌手点击不再进三级列表：跳发现页歌手详情浮层（LibraryScreen 接 lxMusicViewModel.openArtistFromSearch），
    // 三级仅保留专辑曲目

    /** 进入三级：专辑曲目 */
    fun openAlbum(album: LxAlbum) {
        openDetail(CollectionDetail(CollectionsContentTab.ALBUMS, album.id, album.name))
    }

    private fun openDetail(detail: CollectionDetail) {
        if (_selectedDetail.value == detail && detailLoadJob?.isActive == true) return
        detailLoadJob?.cancel()
        _selectedDetail.value = detail
        loadDetailSongs(detail)
    }

    /** 逐级返回：三级→二级（清详情歌曲状态），二级→一级（清 scope），供 BackHandler 用 */
    fun back() {
        if (_selectedDetail.value != null) {
            closeDetail()
        } else if (_collectionsScope.value != null) {
            backToCollectionsEntries()
        }
    }

    /** 三级→二级 */
    fun closeDetail() {
        detailLoadJob?.cancel()
        _selectedDetail.value = null
        resetDetailSongsState()
    }

    /** 二级→一级 */
    fun backToCollectionsEntries() {
        closeDetail()
        artistsLoadJob?.cancel()
        albumsLoadJob?.cancel()
        _collectionsScope.value = null
        _collectionsTab.value = CollectionsContentTab.ARTISTS
        resetPlatformArtistsState()
        resetPlatformAlbumsState()
    }

    /** 下拉强刷当前层：三级重拉详情歌曲；二级无缓存直接重拉两个列表；一级无操作 */
    fun refresh() {
        val detail = _selectedDetail.value
        if (detail != null) {
            loadDetailSongs(detail)
            return
        }
        val current = _collectionsScope.value ?: return
        loadPlatformArtists(current)
        loadPlatformAlbums(current)
    }

    private fun resetPlatformArtistsState() {
        _platformArtists.value = emptyList()
        _artistsError.value = null
        _artistsLoading.value = false
    }

    private fun resetPlatformAlbumsState() {
        _platformAlbums.value = emptyList()
        _albumsError.value = null
        _albumsLoading.value = false
    }

    private fun resetDetailSongsState() {
        _detailSongs.value = emptyList()
        _detailSongsError.value = null
        _detailSongsLoading.value = false
    }

    private fun loadPlatformArtists(source: String) {
        artistsLoadJob?.cancel()
        _artistsError.value = null
        _platformArtists.value = emptyList()
        _artistsLoading.value = true
        artistsLoadJob = holderScope.launch {
            val result = runCatching { fetchPlatformFollowedArtists(source) }.getOrNull()
            // 被取消的加载（切走后立刻重入）不落地状态，防止过期结果覆盖新一次加载
            if (!isActive) return@launch
            if (_collectionsScope.value != source) return@launch
            if (result == null) {
                _artistsError.value = "load_failed"
                _platformArtists.value = emptyList()
            } else {
                _platformArtists.value = result
            }
            _artistsLoading.value = false
        }
    }

    private fun loadPlatformAlbums(source: String) {
        albumsLoadJob?.cancel()
        _albumsError.value = null
        _platformAlbums.value = emptyList()
        _albumsLoading.value = true
        albumsLoadJob = holderScope.launch {
            val result = runCatching { fetchPlatformCollectedAlbums(source) }.getOrNull()
            if (!isActive) return@launch
            if (_collectionsScope.value != source) return@launch
            if (result == null) {
                _albumsError.value = "load_failed"
                _platformAlbums.value = emptyList()
            } else {
                _platformAlbums.value = result
            }
            _albumsLoading.value = false
        }
    }

    /**
     * 平台关注歌手：wy 走 /weapi/artist/sublist；tx 走 fcg_order_singer_getlist；
     * kg 走 v4/follow_list；kw 协议不可达返回 null。null 表示失败（照 load_failed 约定）。
     */
    private suspend fun fetchPlatformFollowedArtists(source: String): List<LxArtist>? = when (source) {
        LxSources.NETEASE -> lxMusicRepository.fetchNeteaseSubArtists()
        LxSources.TENCENT -> lxMusicRepository.fetchTxFollowedArtists()
        LxSources.KUGOU -> lxMusicRepository.fetchKgFollowedArtists()
        else -> null
    }

    /**
     * 平台收藏专辑：wy 走 /weapi/album/sublist；tx 走 order_asset reqtype=2；
     * kg 复用云歌单列表区分出的专辑条目（id 已带 kg_album_ 前缀，三级直接按引用拉曲）；
     * kw 协议不可达返回 null。
     */
    private suspend fun fetchPlatformCollectedAlbums(source: String): List<LxAlbum>? = when (source) {
        LxSources.NETEASE -> lxMusicRepository.fetchNeteaseSubAlbums()
        LxSources.TENCENT -> lxMusicRepository.fetchTxLikedAlbums()
        // force=true：收藏无会话缓存，每次进入/刷新都清酷狗云歌单内存缓存重拉
        LxSources.KUGOU -> lxMusicRepository.fetchKgCollectedAlbums(force = true).map { playlist ->
            LxAlbum(
                id = playlist.id,
                name = playlist.name,
                pic = playlist.pic,
                size = playlist.trackCount ?: 0,
            )
        }
        else -> null
    }

    private fun loadDetailSongs(detail: CollectionDetail) {
        detailLoadJob?.cancel()
        _detailSongsError.value = null
        _detailSongs.value = emptyList()
        _detailSongsLoading.value = true
        val source = _collectionsScope.value.orEmpty()
        detailLoadJob = holderScope.launch {
            val result = runCatching {
                when (detail.kind) {
                    CollectionsContentTab.ARTISTS -> fetchArtistSongs(source, detail)
                    CollectionsContentTab.ALBUMS -> fetchAlbumSongs(source, detail)
                }
            }.getOrNull()
            // 同歌单三级约定：被取消/已切换详情的过期结果不落地
            if (!isActive) return@launch
            if (_selectedDetail.value != detail) return@launch
            if (result == null) {
                _detailSongsError.value = "load_failed"
                _detailSongs.value = emptyList()
            } else {
                _detailSongs.value = result
            }
            _detailSongsLoading.value = false
        }
    }

    /** 歌手热门歌曲：单页拉取（无分页，够展示与播放） */
    private suspend fun fetchArtistSongs(source: String, detail: CollectionDetail): List<LxSong>? =
        when (source) {
            LxSources.NETEASE ->
                lxMusicRepository.fetchNeteaseArtistSongs(detail.id, offset = 0, limit = ARTIST_SONGS_LIMIT).first
            LxSources.TENCENT ->
                lxMusicRepository.fetchTxArtistSongs(detail.id, detail.name, offset = 0, limit = ARTIST_SONGS_LIMIT).first
            LxSources.KUGOU ->
                lxMusicRepository.fetchKgArtistSongs(detail.id, offset = 0, limit = ARTIST_SONGS_LIMIT).first
            else -> null
        }

    /** 专辑曲目：kg 收藏专辑与云歌单同一 listid 体系，走 kg_album_ 前缀引用；wy/tx 走专辑详情 */
    private suspend fun fetchAlbumSongs(source: String, detail: CollectionDetail): List<LxSong>? =
        when (source) {
            LxSources.KUGOU -> lxMusicRepository.fetchUserPlaylistSongsByRef(source, detail.id)
            LxSources.NETEASE -> lxMusicRepository.fetchNeteaseAlbumDetail(detail.id).second
            LxSources.TENCENT -> lxMusicRepository.fetchTxAlbumDetail(detail.id, detail.name).second
            else -> null
        }
}
