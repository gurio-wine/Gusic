package com.gurio.gusic.presentation.viewmodel

import com.gurio.gusic.data.lxmusic.LxMusicRepository
import com.gurio.gusic.data.lxmusic.LxPlaylist
import com.gurio.gusic.data.lxmusic.LxSong
import com.gurio.gusic.data.lxmusic.LxSources
import com.gurio.gusic.data.lxmusic.netease.NeteaseCookieStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * “歌单”分类的三级结构状态：
 * - [playlistsScope] == null 且 [selectedPlaylist] == null：本软件歌单一层（列表数据仍走 PlaylistContainer）
 * - [playlistsScope] == "tx"/"kg"/"kw"/"wy"：对应在线平台的用户歌单列表（[platformPlaylists]）
 * - [selectedPlaylist] != null：平台歌单内的歌曲列表（[playlistSongs]）
 */
@Singleton
class LibraryPlaylistsStateHolder @Inject constructor(
    private val lxMusicRepository: LxMusicRepository,
    private val neteaseCookieStore: NeteaseCookieStore,
    likedStateHolder: LibraryLikedStateHolder,
) {
    companion object {
        /** 平台入口固定顺序：wy/tx/kg/kw（与“我喜欢”一致） */
        val PLAYLIST_PLATFORMS = listOf(
            LxSources.NETEASE,
            LxSources.TENCENT,
            LxSources.KUGOU,
            LxSources.KUWO,
        )
    }

    private val holderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _playlistsScope = MutableStateFlow<String?>(null)
    val playlistsScope: StateFlow<String?> = _playlistsScope.asStateFlow()

    private val _platformPlaylists = MutableStateFlow<List<LxPlaylist>>(emptyList())
    val platformPlaylists: StateFlow<List<LxPlaylist>> = _platformPlaylists.asStateFlow()

    private val _platformPlaylistsLoading = MutableStateFlow(false)
    val platformPlaylistsLoading: StateFlow<Boolean> = _platformPlaylistsLoading.asStateFlow()

    private val _platformPlaylistsError = MutableStateFlow<String?>(null)
    val platformPlaylistsError: StateFlow<String?> = _platformPlaylistsError.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<LxPlaylist?>(null)
    val selectedPlaylist: StateFlow<LxPlaylist?> = _selectedPlaylist.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<LxSong>>(emptyList())
    val playlistSongs: StateFlow<List<LxSong>> = _playlistSongs.asStateFlow()

    private val _playlistSongsLoading = MutableStateFlow(false)
    val playlistSongsLoading: StateFlow<Boolean> = _playlistSongsLoading.asStateFlow()

    private val _playlistSongsError = MutableStateFlow<String?>(null)
    val playlistSongsError: StateFlow<String?> = _playlistSongsError.asStateFlow()

    /** 复用“我喜欢”holder 的登录平台判定，不重复实现登录状态逻辑 */
    val loggedInPlatforms: StateFlow<List<String>> = likedStateHolder.loggedInPlatforms

    /** 会话级内存缓存：source -> 平台用户歌单列表（有缓存直接用，不做后台刷新） */
    private val platformPlaylistsCache = ConcurrentHashMap<String, List<LxPlaylist>>()

    private var playlistsLoadJob: Job? = null
    private var songsLoadJob: Job? = null

    init {
        holderScope.launch {
            var previous: Set<String> = emptySet()
            loggedInPlatforms.collect { logged ->
                // 登出后清掉该平台歌单缓存，防止换账号后读到前一账号的数据
                val loggedOut = previous - logged.toSet()
                loggedOut.forEach { source ->
                    platformPlaylistsCache.remove(source)
                    // 若正处于该平台层级（含三级歌单），整体回到一级入口
                    if (_playlistsScope.value == source) {
                        backToPlaylistsEntries()
                    }
                }
                previous = logged.toSet()
            }
        }
    }

    /**
     * 切换歌单二级作用域：
     * - null 回到一级入口；平台则置 scope 并加载。
     * 进入平台（含同平台重进）时绕过会话缓存直接拉取（清该平台缓存再 load），
     * 保证平台歌单变化进入即刷新；仅保留在途加载去重防连点。
     */
    fun selectPlaylistsScope(scope: String?) {
        when (scope) {
            null -> backToPlaylistsEntries()
            in PLAYLIST_PLATFORMS -> {
                // 同平台在途加载中时去重，避免连点入口重复发起请求
                if (_playlistsScope.value == scope && playlistsLoadJob?.isActive == true) return
                _playlistsScope.value = scope
                // 进入即刷新：清掉该平台缓存再加载，不命中旧数据（酷狗还要清仓库层云歌单缓存）
                platformPlaylistsCache.remove(scope)
                loadPlatformPlaylists(scope, force = true)
            }
            // 未知 scope 直接回到一级入口，避免悬空状态
            else -> backToPlaylistsEntries()
        }
    }

    /** 进入三级：打开平台歌单并异步加载歌曲（同歌单在途加载时去重） */
    fun openPlatformPlaylist(playlist: LxPlaylist) {
        if (_selectedPlaylist.value?.id == playlist.id && songsLoadJob?.isActive == true) return
        songsLoadJob?.cancel()
        _selectedPlaylist.value = playlist
        loadPlaylistSongs(playlist)
    }

    /** 逐级返回：三级→二级（清歌单歌曲状态），二级→一级（清 scope），供 BackHandler 用 */
    fun back() {
        if (_selectedPlaylist.value != null) {
            closePlatformPlaylist()
        } else if (_playlistsScope.value != null) {
            backToPlaylistsEntries()
        }
    }

    /** 三级→二级：关闭当前歌单并清空歌曲状态 */
    fun closePlatformPlaylist() {
        songsLoadJob?.cancel()
        _selectedPlaylist.value = null
        resetPlaylistSongsState()
    }

    /** 二级→一级：回到本软件歌单列表 */
    fun backToPlaylistsEntries() {
        closePlatformPlaylist()
        playlistsLoadJob?.cancel()
        _playlistsScope.value = null
        resetPlatformListState()
    }

    /** 下拉强刷当前层：三级重拉当前歌单歌曲；二级清缓存重拉歌单列表；一级无操作 */
    fun refresh() {
        val selected = _selectedPlaylist.value
        if (selected != null) {
            loadPlaylistSongs(selected)
            return
        }
        val current = _playlistsScope.value ?: return
        platformPlaylistsCache.remove(current)
        loadPlatformPlaylists(current, force = true)
    }

    private fun resetPlatformListState() {
        _platformPlaylists.value = emptyList()
        _platformPlaylistsError.value = null
        _platformPlaylistsLoading.value = false
    }

    private fun resetPlaylistSongsState() {
        _playlistSongs.value = emptyList()
        _playlistSongsError.value = null
        _playlistSongsLoading.value = false
    }

    private fun loadPlatformPlaylists(source: String, force: Boolean) {
        playlistsLoadJob?.cancel()
        _platformPlaylistsError.value = null
        val cached = platformPlaylistsCache[source]
        if (!force && cached != null) {
            _platformPlaylists.value = cached
            _platformPlaylistsLoading.value = false
            return
        }
        _platformPlaylists.value = emptyList()
        _platformPlaylistsLoading.value = true
        playlistsLoadJob = holderScope.launch {
            val result = runCatching { fetchPlatformUserPlaylists(source, force) }.getOrNull()
            // runCatching 会吞掉取消异常：被取消的加载（切走后立刻重入同平台）不落地任何状态，
            // 否则过期失败结果会短暂覆盖新一次加载的在途 UI
            if (!isActive) return@launch
            // 结果返回前用户可能已切换作用域，丢弃过期结果
            if (_playlistsScope.value != source) return@launch
            if (result == null) {
                _platformPlaylistsError.value = "load_failed"
                _platformPlaylists.value = emptyList()
            } else {
                platformPlaylistsCache[source] = result
                _platformPlaylists.value = result
            }
            _platformPlaylistsLoading.value = false
        }
    }

    /**
     * 拉取平台用户歌单；返回 null 表示失败（照 liked 的 load_failed 约定）。
     * 数据链路与 LxMusicViewModel.loadPlatformMineHome 一致：
     * kg/kw/tx 仓库已带前缀或需 tx 加 tx_pl_ 前缀，wy 需先解析 uid。
     */
    private suspend fun fetchPlatformUserPlaylists(source: String, force: Boolean): List<LxPlaylist>? {
        val raw = when (source) {
            // force 透传给酷狗：进入/下拉刷新时清云歌单内存缓存，否则永远拿到同一份旧列表
            LxSources.KUGOU -> lxMusicRepository.fetchKgUserPlaylists(force)
            LxSources.KUWO -> lxMusicRepository.fetchKwUserPlaylists()
            // 打开歌单时按 "tx_pl_" 前缀分流到用户歌单接口，缺失前缀会全部落进红心分支
            LxSources.TENCENT -> lxMusicRepository.fetchTxUserPlaylists()
                .map { it.copy(id = "tx_pl_${it.id}") }
            LxSources.NETEASE -> {
                var uid = neteaseCookieStore.getAccountId().takeIf { it >= 0 }
                if (uid == null) {
                    uid = runCatching { lxMusicRepository.fetchNeteaseLoginProfile() }.getOrNull()?.uid
                }
                val uidStr = uid?.toString() ?: return null
                lxMusicRepository.fetchNeteaseMyPlaylists(uidStr)
            }
            else -> return null
        }
        // 平台“我喜欢”入口已在“我喜欢”tab，这里过滤掉 specialType==5 的红心歌单
        return raw.filter { it.specialType != 5 }
    }

    private fun loadPlaylistSongs(playlist: LxPlaylist) {
        songsLoadJob?.cancel()
        _playlistSongsError.value = null
        _playlistSongs.value = emptyList()
        _playlistSongsLoading.value = true
        songsLoadJob = holderScope.launch {
            val result = runCatching {
                lxMusicRepository.fetchUserPlaylistSongsByRef(_playlistsScope.value.orEmpty(), playlist.id)
            }.getOrNull()
            // 同上：被取消的加载不落地状态，防止返回后重进同一歌单时过期结果覆盖新加载
            if (!isActive) return@launch
            // 结果返回前用户可能已返回上级或切换歌单，丢弃过期结果
            if (_selectedPlaylist.value?.id != playlist.id) return@launch
            if (result == null) {
                _playlistSongsError.value = "load_failed"
                _playlistSongs.value = emptyList()
            } else {
                _playlistSongs.value = result
            }
            _playlistSongsLoading.value = false
        }
    }
}
