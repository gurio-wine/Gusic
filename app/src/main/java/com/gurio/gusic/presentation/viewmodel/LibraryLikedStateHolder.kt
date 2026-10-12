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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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

    /** 各已登录平台的“我喜欢”歌曲数量：source -> count（拉取失败的平台不出现在 map 中） */
    private val _platformLikedCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val platformLikedCounts: StateFlow<Map<String, Int>> = _platformLikedCounts.asStateFlow()

    /** 会话级内存缓存：source -> 原始 LxSong 列表（有缓存直接用，不做后台刷新） */
    private val platformLikedCache = ConcurrentHashMap<String, List<LxSong>>()

    /** 计数拉取在途任务：source -> Job（防止重复拉取，完成/取消后移除） */
    private val countFetchJobs = ConcurrentHashMap<String, Job>()
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
                // 登出后清掉该平台缓存与计数，防止换账号后读到前一账号的数据
                val loggedOut = previous - logged.toSet()
                loggedOut.forEach { source ->
                    platformLikedCache.remove(source)
                    countFetchJobs.remove(source)?.cancel()
                    _platformLikedCounts.update { it - source }
                    if (_likedScope.value == source) {
                        backToLikedEntries()
                    }
                }
                previous = logged.toSet()
                // 已登录但尚无计数的平台异步拉取一次“我喜欢”数量（失败静默忽略，无计数时不显示数字）
                logged.forEach { source ->
                    if (source !in _platformLikedCounts.value && !countFetchJobs.containsKey(source)) {
                        fetchPlatformLikedCount(source)
                    }
                }
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

    /**
     * 拉取单个平台的“我喜欢”歌曲数量（每个平台一次，失败静默忽略、不重试重放）。
     * 拉取成功即写入（包括 0，与本软件“我喜欢”入口一样始终显示数量）；失败不写入。
     */
    private fun fetchPlatformLikedCount(source: String) {
        val job = holderScope.launch {
            val count = runCatching {
                lxMusicRepository.fetchPlatformLikedCountOrNull(source)
            }.getOrNull()
            // count == null 表示拉取失败：不写入，入口保持无数字；登录状态守卫防止登出竞态写入
            if (count != null && source in _loggedInPlatforms.value) {
                _platformLikedCounts.update { it + (source to count) }
            }
        }
        countFetchJobs[source] = job
        // 完成后清掉在途记录；失败的平台可在下次登录状态变化时重试
        job.invokeOnCompletion { countFetchJobs.remove(source, job) }
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
            // 被取消的加载（切走后立刻重入同平台）不落地状态，防止过期结果覆盖新一次加载
            if (!isActive) return@launch
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
