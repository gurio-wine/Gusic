package com.gurio.gusic.presentation.viewmodel

import com.gurio.gusic.data.lxmusic.LxMusicRepository
import com.gurio.gusic.data.lxmusic.LxSong
import com.gurio.gusic.data.lxmusic.LxSongMapper
import com.gurio.gusic.data.lxmusic.LxSources
import com.gurio.gusic.data.lxmusic.kugou.KugouCookieStore
import com.gurio.gusic.data.lxmusic.kuwo.KuwoCookieStore
import com.gurio.gusic.data.lxmusic.netease.NeteaseCookieStore
import com.gurio.gusic.data.lxmusic.tencent.TencentCookieStore
import com.gurio.gusic.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * “我喜欢”分类的两级结构状态：
 * - [likedScope] == null：一级平台入口列表
 * - [likedScope] == "local"：本软件喜欢的歌曲（列表数据仍走 LibraryStateHolder.favoritesPagingFlow）
 * - [likedScope] == "tx"/"kg"/"kw"/"wy"：对应在线平台的喜欢歌曲列表（[platformSongs]）
 */
@Singleton
class LibraryLikedStateHolder @Inject constructor(
    private val lxMusicRepository: LxMusicRepository,
    private val neteaseCookieStore: NeteaseCookieStore,
    private val tencentCookieStore: TencentCookieStore,
    private val kugouCookieStore: KugouCookieStore,
    private val kuwoCookieStore: KuwoCookieStore,
) {
    companion object {
        /** 平台入口固定顺序：wy/tx/kg/kw */
        val LIKED_PLATFORMS = listOf(
            LxSources.NETEASE,
            LxSources.TENCENT,
            LxSources.KUGOU,
            LxSources.KUWO,
        )
    }

    private val holderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _likedScope = MutableStateFlow<String?>(null)
    val likedScope: StateFlow<String?> = _likedScope.asStateFlow()

    private val _platformSongs = MutableStateFlow<List<Song>>(emptyList())
    val platformSongs: StateFlow<List<Song>> = _platformSongs.asStateFlow()

    private val _loadingPlatformSongs = MutableStateFlow(false)
    val loadingPlatformSongs: StateFlow<Boolean> = _loadingPlatformSongs.asStateFlow()

    private val _platformSongsError = MutableStateFlow<String?>(null)
    val platformSongsError: StateFlow<String?> = _platformSongsError.asStateFlow()

    private val _loggedInPlatforms = MutableStateFlow<List<String>>(emptyList())
    val loggedInPlatforms: StateFlow<List<String>> = _loggedInPlatforms.asStateFlow()

    /** 会话级内存缓存：source -> 原始 LxSong 列表（有缓存直接用，不做后台刷新） */
    private val platformLikedCache = ConcurrentHashMap<String, List<LxSong>>()
    private var platformLoadJob: Job? = null

    init {
        holderScope.launch {
            var previous: Set<String> = emptySet()
            combine(
                neteaseCookieStore.cookieFlow,
                tencentCookieStore.cookieFlow,
                kugouCookieStore.loggedInFlow,
                kuwoCookieStore.cookieFlow,
            ) { wyCookie, txCookie, kgLoggedIn, kwCookie ->
                LIKED_PLATFORMS.filter { source ->
                    when (source) {
                        LxSources.NETEASE -> !wyCookie.isNullOrBlank()
                        LxSources.TENCENT -> !txCookie.isNullOrBlank()
                        LxSources.KUGOU -> kgLoggedIn
                        LxSources.KUWO -> !kwCookie.isNullOrBlank()
                        else -> false
                    }
                }
            }.collect { logged ->
                _loggedInPlatforms.value = logged
                // 登出后清掉该平台缓存，防止换账号后读到前一账号的喜欢列表
                val loggedOut = previous - logged.toSet()
                loggedOut.forEach { source ->
                    platformLikedCache.remove(source)
                    if (_likedScope.value == source) {
                        backToLikedEntries()
                    }
                }
                previous = logged.toSet()
            }
        }
    }

    /**
     * 切换“我喜欢”二级作用域：
     * - null/"local" 直接切状态；平台则置 scope 并异步加载（有缓存先用缓存）。
     */
    fun selectLikedScope(scope: String?) {
        when (scope) {
            null -> backToLikedEntries()
            LxSources.LOCAL -> {
                platformLoadJob?.cancel()
                _likedScope.value = LxSources.LOCAL
                resetPlatformListState()
            }
            in LIKED_PLATFORMS -> {
                // 同平台在途加载中时去重，避免连点入口重复发起请求
                if (_likedScope.value == scope && platformLoadJob?.isActive == true) return
                _likedScope.value = scope
                loadPlatformSongs(scope)
            }
            // 未知 scope 直接回到一级入口，避免悬空状态
            else -> backToLikedEntries()
        }
    }

    /** 返回一级平台入口列表 */
    fun backToLikedEntries() {
        platformLoadJob?.cancel()
        _likedScope.value = null
        resetPlatformListState()
    }

    /** 下拉强刷当前平台：清缓存后重新拉取 */
    fun refreshPlatformSongs() {
        val current = _likedScope.value ?: return
        if (current == LxSources.LOCAL) return
        platformLikedCache.remove(current)
        loadPlatformSongs(current)
    }

    private fun resetPlatformListState() {
        _platformSongs.value = emptyList()
        _platformSongsError.value = null
        _loadingPlatformSongs.value = false
    }

    private fun loadPlatformSongs(source: String) {
        platformLoadJob?.cancel()
        _platformSongsError.value = null
        val cached = platformLikedCache[source]
        if (cached != null) {
            _platformSongs.value = cached.map(LxSongMapper::toSong)
            _loadingPlatformSongs.value = false
            return
        }
        _platformSongs.value = emptyList()
        _loadingPlatformSongs.value = true
        platformLoadJob = holderScope.launch {
            val result = lxMusicRepository.fetchPlatformLikedLxSongs(source)
            // 结果返回前用户可能已切换作用域，丢弃过期结果
            if (_likedScope.value != source) return@launch
            if (result == null) {
                _platformSongsError.value = "load_failed"
                _platformSongs.value = emptyList()
            } else {
                platformLikedCache[source] = result
                _platformSongs.value = result.map(LxSongMapper::toSong)
            }
            _loadingPlatformSongs.value = false
        }
    }
}
