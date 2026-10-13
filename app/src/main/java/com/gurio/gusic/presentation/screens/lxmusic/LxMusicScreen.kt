package com.gurio.gusic.presentation.screens.lxmusic

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.gurio.gusic.presentation.viewmodel.MultiSelectionStateHolder
import com.gurio.gusic.presentation.components.CollapsibleCommonTopBar
import com.gurio.gusic.presentation.components.MultiSelectionBottomSheet
import com.gurio.gusic.presentation.components.ReorderPlatformsSheet
import com.gurio.gusic.presentation.components.subcomps.SelectionActionRow
import com.gurio.gusic.presentation.components.subcomps.SelectionCountPill
import com.gurio.gusic.data.lxmusic.LxQualities
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.abs
import com.gurio.gusic.R
import com.gurio.gusic.data.lxmusic.LxSongMapper
import com.gurio.gusic.data.lxmusic.LxSources
import com.gurio.gusic.data.lxmusic.LxWriteOp
import com.gurio.gusic.presentation.components.MiniPlayerHeight
import com.gurio.gusic.presentation.components.ExpressiveScrollBar
import com.gurio.gusic.presentation.components.SmartImage
import com.gurio.gusic.presentation.components.SmartImageListTargetSize
import com.gurio.gusic.presentation.components.resolveNavBarOccupiedHeight
import com.gurio.gusic.presentation.components.rememberModalSheetState
import com.gurio.gusic.presentation.components.SongInfoBottomSheet
import com.gurio.gusic.presentation.components.subcomps.EnhancedSongListItem
import com.gurio.gusic.presentation.screens.ListExtraBottomGap
import com.gurio.gusic.presentation.screens.MusicIconPattern
import com.gurio.gusic.presentation.viewmodel.PlayerViewModel
import com.gurio.gusic.ui.theme.LocalGusicDarkTheme
import com.gurio.gusic.ui.theme.GusicStatusBarStyle
import com.gurio.gusic.ui.theme.RoundedSans
import com.gurio.gusic.utils.formatSongCount
import com.gurio.gusic.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun LxMusicScreen(
    context: android.content.Context = LocalContext.current,
    playerViewModel: PlayerViewModel,
    viewModel: LxMusicViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
    ),
) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    LaunchedEffect(stablePlayerState.currentSong?.id) {
        viewModel.onPlaybackSongChanged(stablePlayerState.currentSong?.id)
    }
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val browseState by viewModel.browseState.collectAsStateWithLifecycle()
    val playerSheetState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    val queueSheetVisible by playerViewModel.isQueueSheetVisible.collectAsStateWithLifecycle()

    var lxInfoSong by remember { mutableStateOf<com.gurio.gusic.data.lxmusic.LxSong?>(null) }
    var showLxArtistPicker by remember { mutableStateOf(false) }
    var lxArtistPickerCandidates by remember { mutableStateOf<List<com.gurio.gusic.data.lxmusic.LxArtist>>(emptyList()) }
    var lxArtistPickerSource by remember { mutableStateOf("") }
    var showPlatformOrderSheet by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showTxWebLoginDialog by remember { mutableStateOf(false) }
    var showKgLoginDialog by remember { mutableStateOf(false) }
    var showKwLoginDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val neteaseLoggedIn by viewModel.neteaseLoggedIn.collectAsStateWithLifecycle()
    val neteaseNickname by viewModel.neteaseNickname.collectAsStateWithLifecycle()
    val neteaseAvatar by viewModel.neteaseAvatar.collectAsStateWithLifecycle()
    val tencentNickname by viewModel.tencentNickname.collectAsStateWithLifecycle()
    val tencentAvatar by viewModel.tencentAvatar.collectAsStateWithLifecycle()
    val tencentLoggedIn by viewModel.tencentLoggedIn.collectAsStateWithLifecycle()
    val kugouNickname by viewModel.kugouNickname.collectAsStateWithLifecycle()
    val kugouAvatar by viewModel.kugouAvatar.collectAsStateWithLifecycle()
    val kugouLoggedIn by viewModel.kugouLoggedIn.collectAsStateWithLifecycle()
    val kuwoNickname by viewModel.kuwoNickname.collectAsStateWithLifecycle()
    val kuwoAvatar by viewModel.kuwoAvatar.collectAsStateWithLifecycle()
    val kuwoLoggedIn by viewModel.kuwoLoggedIn.collectAsStateWithLifecycle()
    val currentSourceLoggedIn by viewModel.currentSourceLoggedIn.collectAsStateWithLifecycle()

    val platformOrder by viewModel.platformOrder.collectAsStateWithLifecycle()

    @Composable
    fun platformName(source: String): String {
        val resourceId = when (source) {
            "wy" -> R.string.lx_source_netease
            "kw" -> R.string.lx_source_kuwo
            "tx" -> R.string.lx_source_qq
            "kg" -> R.string.lx_source_kugou
            else -> null
        }
        return resourceId?.let { stringResource(it) } ?: source
    }

    val effectiveHiddenPlatforms by viewModel.effectiveHiddenPlatforms.collectAsStateWithLifecycle()

    val orderedSearchTabs = remember(platformOrder, effectiveHiddenPlatforms) {
        val orderMap = platformOrder.withIndex().associate { it.value to it.index }
        searchTabs.filter { it.first !in effectiveHiddenPlatforms }
            .sortedBy { (src, _) -> orderMap[src] ?: Int.MAX_VALUE }
            .ifEmpty { listOf("tx" to R.string.lx_source_qq) }
    }
    // 当前选中的平台被隐藏时回退到第一个可见平台
    LaunchedEffect(orderedSearchTabs, selectedSource) {
        if (orderedSearchTabs.none { it.first == selectedSource }) {
            orderedSearchTabs.firstOrNull()?.let { viewModel.selectSource(it.first) }
        }
    }
    val mineState by viewModel.mineState.collectAsStateWithLifecycle()

    val multiSelectionState = remember { MultiSelectionStateHolder() }
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val toggleLxSongSelection: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit =
        remember(haptics, multiSelectionState) {
            { song ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val uiSong = LxSongMapper.toSong(song)
                multiSelectionState.toggleSelection(uiSong)
            }
        }

    var showBatchDownloadDialog by remember { mutableStateOf(false) }
    var batchDownloadQuality by remember { mutableStateOf(LxQualities.MASTER) }

   
    suspend fun ensureScriptOrToast(song: com.gurio.gusic.data.lxmusic.LxSong): Boolean {
        if (viewModel.activeScript.value != null) return true
        val resolved = viewModel.ensureActiveScriptForPlayback()
        if (resolved != null) return true
        if (song.source in com.gurio.gusic.data.lxmusic.LxSources.ALL) return true
        val hasDisabled = viewModel.hasUserDisabledScripts()
        android.widget.Toast.makeText(
            context,
            if (hasDisabled) context.getString(R.string.lx_msg_source_disabled)
            else context.getString(com.gurio.gusic.R.string.lxmusic_need_script_or_cookie),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        return false
    }

    // 点击歌单/榜单内的一首：整个列表按顺序加入队列并从点击的歌曲开始播放
    val playLxSong: (com.gurio.gusic.data.lxmusic.LxSong, List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit =
        { song, songs, queueName ->
            scope.launch {
                if (!ensureScriptOrToast(song)) return@launch
                viewModel.recordPlay(song)
                val contextSongs = songs.ifEmpty { listOf(song) }
                viewModel.cacheSongsMetadata(contextSongs)
                viewModel.setPlaybackQueueLxSongs(contextSongs)
                playerViewModel.playSongs(
                    songsToPlay = contextSongs.map { LxSongMapper.toSong(it) },
                    startSong = LxSongMapper.toSong(song),
                    queueName = queueName.ifBlank { context.getString(R.string.lx_queue_name_playlist) },
                    origin = com.gurio.gusic.presentation.viewmodel.PlaybackOrigin.ONLINE,
                )
            }
        }

    // 搜索结果页点歌仍只播单首（结果跨歌单，不属于某个列表）
    val playSearchSong: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = { song ->
        scope.launch {
            if (!ensureScriptOrToast(song)) return@launch
            viewModel.recordPlay(song)
            viewModel.cacheSongsMetadata(listOf(song))
            val singleSong = LxSongMapper.toSong(song)
            playerViewModel.playSongs(
                songsToPlay = listOf(singleSong),
                startSong = singleSong,
                queueName = context.getString(R.string.lx_queue_name_playlist),
                origin = com.gurio.gusic.presentation.viewmodel.PlaybackOrigin.ONLINE,
            )
        }
    }

    val playAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit = { songs, queueName ->
        if (songs.isEmpty()) {
            android.widget.Toast.makeText(context, context.getString(R.string.lx_msg_playlist_no_songs), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                if (!ensureScriptOrToast(songs.first())) return@launch
            viewModel.recordPlay(songs.first())
                viewModel.cacheSongsMetadata(songs)
                viewModel.setPlaybackQueueLxSongs(songs)
                playerViewModel.playSongs(
                    songsToPlay = songs.map { LxSongMapper.toSong(it) },
                    startSong = LxSongMapper.toSong(songs.first()),
                    queueName = queueName,
                    origin = com.gurio.gusic.presentation.viewmodel.PlaybackOrigin.ONLINE,
                )
            }
        }
    }

    val shuffleAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit = { songs, queueName ->
        if (songs.isEmpty()) {
            android.widget.Toast.makeText(context, context.getString(R.string.lx_msg_playlist_no_songs), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                if (!ensureScriptOrToast(songs.first())) return@launch
                viewModel.cacheSongsMetadata(songs)
                viewModel.setPlaybackQueueLxSongs(songs)
                val songsById = songs.associateBy { LxSongMapper.toSong(it).id }
                playerViewModel.playSongsShuffled(
                    songsToPlay = songs.map { LxSongMapper.toSong(it) },
                    queueName = queueName,
                    startAtZero = true,
                    onStartSongResolved = { startSong ->
                        songsById[startSong.id]?.let { viewModel.recordPlay(it) }
                    },
                    origin = com.gurio.gusic.presentation.viewmodel.PlaybackOrigin.ONLINE,
                )
            }
        }
    }

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

    val mineAvailable = currentSourceLoggedIn

    val browseBackActive = (browseState.module == "recommend" &&
        mineState.page !is LxMusicViewModel.MinePage.Home) ||
        browseState.module == "playlist" ||
        browseState.selectedPlaylist != null ||
        browseState.isPlaylistSearchMode ||
        // 榜单详情返回列表（不依赖 module：网易混合列表下打开的详情 module 可能是 recommend）
        browseState.selectedBoard != null ||
        (browseState.module == "board" && selectedSource == LxSources.NETEASE) ||
        browseState.module == "search"
    val topLayerVisible = playerSheetState == com.gurio.gusic.presentation.viewmodel.PlayerSheetState.EXPANDED ||
        queueSheetVisible || showLoginDialog
    BackHandler(enabled = isSelectionMode && !topLayerVisible) {
        multiSelectionState.clearSelection()
    }
    BackHandler(enabled = browseBackActive && !topLayerVisible && !isSelectionMode) {
        viewModel.back()
    }

    val headerContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)

    val brandTitle = stringResource(R.string.tab_online)

    Scaffold(
        containerColor = headerContainerColor,
        topBar = {
            TopAppBar(
                title = {
                    // 顶栏平台名可点击：打开平台排序/隐藏面板（原 tab 行编辑按钮已移除）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPlatformOrderSheet = true }
                            .padding(horizontal = 4.dp),
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontSize = 40.sp)) {
                                    append(brandTitle)
                                    append(' ')
                                }
                                withStyle(SpanStyle(fontSize = 18.sp)) {
                                    // 顶栏动态文本显示当前平台名（随左右滑/顶部 tab 切换）
                                    append(platformName(selectedSource))
                                }
                            },
                            fontFamily = RoundedSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                        )
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = stringResource(R.string.lx_platform_order_title),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                actions = {
                    val chipNickname: String?
                    val chipAvatar: String?
                    when (selectedSource) {
                        LxSources.TENCENT -> {
                            chipNickname = tencentNickname
                            chipAvatar = tencentAvatar
                        }
                        LxSources.KUGOU -> {
                            chipNickname = kugouNickname
                            chipAvatar = kugouAvatar
                        }
                        LxSources.KUWO -> {
                            chipNickname = kuwoNickname
                            chipAvatar = kuwoAvatar
                        }
                        else -> {
                            chipNickname = neteaseNickname
                            chipAvatar = neteaseAvatar
                        }
                    }
                    LxAccountChip(
                        loggedIn = currentSourceLoggedIn,
                        nickname = chipNickname,
                        avatarUrl = chipAvatar,
                        fallbackPlatformName = platformName(selectedSource),
                        notLoggedInText = stringResource(R.string.lx_account_chip_not_logged_in),
                        onClick = {
                            when (selectedSource) {
                                LxSources.TENCENT -> clearAllWebViewSession { showTxWebLoginDialog = true }
                                LxSources.KUGOU -> clearAllWebViewSession { showKgLoginDialog = true }
                                LxSources.KUWO -> clearAllWebViewSession { showKwLoginDialog = true }
                                else -> clearNeteaseWebViewSession { showLoginDialog = true }
                            }
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        // 浏览态已无“推荐/排行榜”分类页签：搜索框下直接展示内容
        // （网易 = 推荐内容在上、榜单卡在下，同一滚动列表；其他音源 = 仅榜单卡）
        val recommendTabAvailable = selectedSource == LxSources.NETEASE
        val focusManager = LocalFocusManager.current
        LaunchedEffect(selectedSource) {
            focusManager.clearFocus()
        }
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
        ) {
            val sourceBoardsCache by viewModel.sourceBoardsCache.collectAsStateWithLifecycle()
            // 发现页左右滑 pager（与媒体库页一致的交互）：停稳后切音源；顶部平台 tab 点击滚动 pager。
            // 用 settledPageKey（=音源 id）而非索引映射：平台重排/登录态隐藏导致列表变化时，
            // 旧索引会短暂指向别的源而误触发 selectSource，按 key 读取则不受影响
            val browsePagerState = rememberPagerState(
                initialPage = orderedSearchTabs.indexOfFirst { it.first == selectedSource }.takeIf { it >= 0 } ?: 0,
                pageCount = { orderedSearchTabs.size },
            )
            val browseTabsState = rememberUpdatedState(orderedSearchTabs)
            // effect 只以 pagerState 为 key（不随 orderedSearchTabs 重启）：平台重排/登录态
            // 隐藏导致列表变化时，重启会重放旧 settledPage 索引→映射到别的源而误切；
            // tabs 通过 rememberUpdatedState 读最新值，重排后 pager 按 key 重定位页码，
            // 翻页发射时映射结果即正确
            LaunchedEffect(browsePagerState) {
                snapshotFlow { browsePagerState.settledPage }.collect { page ->
                    val src = browseTabsState.value.getOrNull(page)?.first ?: return@collect
                    if (src != viewModel.selectedSource.value) viewModel.selectSource(src)
                }
            }
            // 外部切源（偏好恢复/平台重排）时让 pager 跟随到对应页；pager 正在滑动时
            // 等滑动结束再对齐——直接跳过的话，本次滑动 settle 回原页后 settledPage 流
            // 不会再发射，pager 会停在旧页直到用户再次交互
            LaunchedEffect(selectedSource, orderedSearchTabs) {
                val idx = orderedSearchTabs.indexOfFirst { it.first == selectedSource }
                if (idx >= 0) {
                    if (browsePagerState.isScrollInProgress) {
                        snapshotFlow { browsePagerState.isScrollInProgress }.first { !it }
                    }
                    if (browsePagerState.settledPage != idx && !browsePagerState.isScrollInProgress) {
                        browsePagerState.animateScrollToPage(idx)
                    }
                }
            }
            val sourceSelectedIndex = orderedSearchTabs.indexOfFirst { it.first == selectedSource }
            val sourceTabsListState = rememberLazyListState()
            LaunchedEffect(sourceSelectedIndex) {
                if (sourceSelectedIndex >= 0) {
                    sourceTabsListState.animateScrollToItem(sourceSelectedIndex)
                }
            }
            androidx.compose.foundation.lazy.LazyRow(
                state = sourceTabsListState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(orderedSearchTabs, key = { _, it -> it.first }) { index, (src, _) ->
                    LxSourceTab(
                        label = platformName(src),
                        selected = src == selectedSource,
                        index = index,
                        selectedIndex = sourceSelectedIndex,
                        onClick = { scope.launch { browsePagerState.animateScrollToPage(index) } },
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxSize()
                    .padding(top = 68.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val searchActive = browseState.module == "search"
                    val playlistDetailActive = browseState.module == "playlist" ||
                        browseState.selectedPlaylist != null
                    // 常驻搜索框：浏览态提交搜索进入搜索态，搜索态左侧变为返回按钮
                    LxDockedSearchBar(
                        query = searchState.keyword,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = { viewModel.search(it) },
                        onClear = { viewModel.updateSearchQuery("") },
                        placeholder = stringResource(R.string.lx_placeholder_search_songs),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                        leadingBack = searchActive,
                        // 与系统返回键同一条路径：搜索态内若开着歌单详情，先回结果列表再退出搜索
                        onLeadingBack = { viewModel.back() },
                        enableInnerBox = false,
                    )
                    // 指示条只在浏览根 pager 可见时显示（榜单详情/网易推荐子页不含 pager）
                    if (!playlistDetailActive && !searchActive &&
                        browseState.selectedBoard == null &&
                        !(recommendTabAvailable && mineState.page !is LxMusicViewModel.MinePage.Home)
                    ) {
                        // 浏览态：页面指示条（与媒体库页同款长短线条），同时拉开榜单内容与搜索框的间距
                        LxBrowsePagerIndicator(
                            currentIndex = browsePagerState.currentPage,
                            pageCount = orderedSearchTabs.size,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    if (playlistDetailActive) {
                        // 歌单详情模式（含搜索直开）：隐藏模块行与 pager
                        Box(modifier = Modifier.fillMaxSize()) {
                            LxPlaylistPage(browseState, viewModel, playerViewModel, playLxSong,
                                playAllPlaylist, shuffleAllPlaylist, bottomBarHeightDp,
                                onSongMoreOptions = { lxInfoSong = it },
                                isSelectionMode = isSelectionMode,
                                selectedSongIds = selectedSongIds,
                                onSongLongPress = toggleLxSongSelection,
                                onSongSelectionToggle = toggleLxSongSelection,
                                getSelectionIndex = { songId ->
                                    multiSelectionState.getSelectionIndex(songId)
                                },
                                multiSelectionState = multiSelectionState,
                                onSelectionOptionsClick = { showMultiSelectionSheet = true })
                        }
                    } else if (searchActive) {
                        // 搜索态：隐藏模块行，显示分类切换与搜索结果
                        Column(modifier = Modifier.fillMaxSize()) {
                            LxSearchTypeSwitch(
                                selected = searchState.type,
                                onSelect = viewModel::setSearchType,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 10.dp, bottom = 6.dp),
                            )
                            Box(modifier = Modifier.fillMaxSize()) {
                                LxSearchPage(searchState, viewModel, playerViewModel, playSearchSong,
                                    bottomBarHeightDp,
                                    onSongMoreOptions = { lxInfoSong = it },
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onSongLongPress = toggleLxSongSelection,
                                    onSongSelectionToggle = toggleLxSongSelection,
                                    getSelectionIndex = { songId ->
                                        multiSelectionState.getSelectionIndex(songId)
                                    },
                                    multiSelectionState = multiSelectionState,
                                    onSelectionOptionsClick = { showMultiSelectionSheet = true })
                            }
                        }
                    } else {
                        // 浏览态主体（无“推荐/排行榜”分类页签，内容直接展示）：
                        // 网易 = 推荐子页全屏 / 榜单详情 / 混合列表（推荐入口在上、榜单卡在下）
                        // 其他音源 = 榜单详情 / 榜单卡列表
                        if (recommendTabAvailable && mineState.page !is LxMusicViewModel.MinePage.Home) {
                            LxRecommendPage(
                                recommendState = mineState,
                                viewModel = viewModel,
                                playerViewModel = playerViewModel,
                                playLxSong = playLxSong,
                                playAllPlaylist = playAllPlaylist,
                                shuffleAllPlaylist = shuffleAllPlaylist,
                                bottomBarHeightDp = bottomBarHeightDp,
                                onSongMoreOptions = { lxInfoSong = it },
                                isSelectionMode = isSelectionMode,
                                selectedSongIds = selectedSongIds,
                                onSongLongPress = toggleLxSongSelection,
                                onSongSelectionToggle = toggleLxSongSelection,
                                getSelectionIndex = { songId ->
                                    multiSelectionState.getSelectionIndex(songId)
                                },
                                multiSelectionState = multiSelectionState,
                                onSelectionOptionsClick = { showMultiSelectionSheet = true },
                            )
                        } else if (browseState.selectedBoard != null) {
                            LxBoardPage(browseState, viewModel, playerViewModel, playLxSong,
                                playAllPlaylist, shuffleAllPlaylist, bottomBarHeightDp,
                                onSongMoreOptions = { lxInfoSong = it },
                                isSelectionMode = isSelectionMode,
                                selectedSongIds = selectedSongIds,
                                onSongLongPress = toggleLxSongSelection,
                                onSongSelectionToggle = toggleLxSongSelection,
                                getSelectionIndex = { songId ->
                                    multiSelectionState.getSelectionIndex(songId)
                                },
                                multiSelectionState = multiSelectionState,
                                onSelectionOptionsClick = { showMultiSelectionSheet = true })
                        } else {
                            // 浏览根：左右滑 pager 切换音源（与媒体库页一致）。
                            // 当前页用 browseState（含预览/加载态），非当前页用每源榜单缓存渲染
                            HorizontalPager(
                                state = browsePagerState,
                                key = { orderedSearchTabs.getOrNull(it)?.first ?: "page_$it" },
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                val pageSource = orderedSearchTabs.getOrNull(page)?.first ?: selectedSource
                                val isCurrentSource = pageSource == selectedSource
                                val pageBoards = if (isCurrentSource) browseState.boards
                                else sourceBoardsCache[pageSource].orEmpty()
                                val pageRefreshing = isCurrentSource && (
                                    browseState.loadingBoardPreviews ||
                                        (pageSource == LxSources.NETEASE && mineState.loadingHome)
                                    )
                                PullToRefreshBox(
                                    isRefreshing = pageRefreshing,
                                    onRefresh = { if (isCurrentSource) viewModel.refreshBrowse() },
                                    enabled = isCurrentSource,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 12.dp,
                                            end = 12.dp,
                                            top = 10.dp,
                                            bottom = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap,
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        // 推荐段/登录占位属于全局当前源数据，只在当前源页渲染；
                                        // 网易推荐内容在上、榜单卡在下，其余音源仅榜单卡
                                        if (pageSource == LxSources.NETEASE && isCurrentSource) {
                                            if (mineAvailable) {
                                                mineRecommendSection(mineState, viewModel)
                                            } else {
                                                // 未登录：占位提示并提供登录入口（复用各平台现有登录弹窗）
                                                item(key = "mine_not_logged_in") {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 220.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        LxMineNotLoggedInPlaceholder(
                                                            platformLabel = platformName(selectedSource),
                                                            onLogin = {
                                                                when (selectedSource) {
                                                                    LxSources.TENCENT -> clearAllWebViewSession { showTxWebLoginDialog = true }
                                                                    LxSources.KUGOU -> clearAllWebViewSession { showKgLoginDialog = true }
                                                                    LxSources.KUWO -> clearAllWebViewSession { showKwLoginDialog = true }
                                                                    else -> clearNeteaseWebViewSession { showLoginDialog = true }
                                                                }
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        boardMenuItems(
                                            boards = pageBoards,
                                            previews = if (isCurrentSource) browseState.boardPreviews else emptyMap(),
                                            loadingPreviews = isCurrentSource && browseState.loadingBoardPreviews,
                                            source = pageSource,
                                            onSelectBoard = { board ->
                                                if (isCurrentSource) {
                                                    viewModel.selectBoard(board)
                                                } else {
                                                    // 滑动中间态点击相邻页：仅切源——紧接着 selectBoard
                                                    // 会取消切源刚启动的榜单加载，首次访问该源时会导致
                                                    // 返回浏览根后空白；settle 后再点即进详情
                                                    viewModel.selectSource(pageSource)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        val selectedLxSongs = remember(selectedSongs) {
            selectedSongs.mapNotNull { LxSongMapper.toLxSong(it) }
        }
        val batchSameSource = selectedLxSongs.size == selectedSongs.size &&
            selectedLxSongs.map { it.source }.distinct().size == 1
        val batchSource = selectedLxSongs.firstOrNull()?.source
        val canBatchAddToPlaylist = batchSameSource && when (batchSource) {
            LxSources.NETEASE -> neteaseLoggedIn
            LxSources.KUGOU -> kugouLoggedIn &&
                viewModel.writeEnabledFor(LxSources.KUGOU, LxWriteOp.PLAYLIST_ADD_SONGS)
            LxSources.KUWO -> kuwoLoggedIn &&
                viewModel.writeEnabledFor(LxSources.KUWO, LxWriteOp.PLAYLIST_ADD_SONGS)
            else -> false
        }
        val canBatchDelete = batchSameSource && batchSource != null &&
            viewModel.currentOwnPlaylistContext(batchSource) != null
        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            onDismiss = { showMultiSelectionSheet = false },
            showAddToPlaylist = canBatchAddToPlaylist,
            showDelete = canBatchDelete,
            showBatchEdit = false,
            onPlayAll = {
                val lxSongs = selectedSongs.mapNotNull { LxSongMapper.toLxSong(it) }
                if (lxSongs.isNotEmpty()) {
                    playAllPlaylist(lxSongs, "多选播放")
                }
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                selectedSongs.forEach { song ->
                    playerViewModel.addSongToQueue(song)
                }
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
                android.widget.Toast.makeText(
                    context, context.getString(R.string.toast_added_to_queue),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onPlayNext = {
                selectedSongs.forEach { song ->
                    playerViewModel.addSongNextToQueue(song)
                }
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
                android.widget.Toast.makeText(
                    context, context.getString(R.string.toast_added_to_queue),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onAddToPlaylist = {
                viewModel.openOnlineAddToPlaylistBatch(selectedLxSongs)
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
            },
            onToggleLikeAll = { shouldLike: Boolean ->
                selectedSongs.forEach { song ->
                    playerViewModel.togglePlatformFavorite(song)
                }
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedSongs(selectedSongs)
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
            },
            onDownloadAll = {
                showBatchDownloadDialog = true
                showMultiSelectionSheet = false
            },
            onDeleteAll = { _: android.app.Activity, onResult: (Boolean) -> Unit ->
                viewModel.requestRemoveSongsFromOwnPlaylist(selectedLxSongs)
                onResult(true)
                showMultiSelectionSheet = false
            },
        )
    }

    if (showBatchDownloadDialog && selectedSongs.isNotEmpty()) {
        LxBatchDownloadQualityDialog(
            songCount = selectedSongs.size,
            selectedQuality = batchDownloadQuality,
            onSelect = { batchDownloadQuality = it },
            onConfirm = {
                val lxSongs = selectedSongs.mapNotNull { LxSongMapper.toLxSong(it) }
                if (lxSongs.isNotEmpty()) {
                    viewModel.batchDownloadSongs(lxSongs, batchDownloadQuality)
                }
                showBatchDownloadDialog = false
                multiSelectionState.clearSelection()
            },
            onDismiss = { showBatchDownloadDialog = false }
        )
    }

    lxInfoSong?.let { infoLxSong ->
        val infoUiSong = remember(infoLxSong.source, infoLxSong.songmid, infoLxSong.pic) {
            LxSongMapper.toSong(infoLxSong)
        }
        val likeSnapshot by playerViewModel.platformLikeSnapshot.collectAsStateWithLifecycle()
        val (infoIsFavorite, infoFavoriteEnabled) = likeSnapshot.let {
            playerViewModel.platformLikeStateOf(infoUiSong)
        }
        SongInfoBottomSheet(
            song = infoUiSong,
            isFavorite = infoIsFavorite,
            favoriteEnabled = infoFavoriteEnabled,
            onToggleFavorite = { playerViewModel.togglePlatformFavorite(infoUiSong) },
            onDismiss = { lxInfoSong = null },
            onPlaySong = {
                playLxSong(infoLxSong, emptyList(), "")
                lxInfoSong = null
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(infoUiSong)
                lxInfoSong = null
                android.widget.Toast.makeText(
                    context, context.getString(R.string.toast_added_to_queue),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onAddNextToQueue = {
                playerViewModel.addSongNextToQueue(infoUiSong)
                lxInfoSong = null
                android.widget.Toast.makeText(
                    context, context.getString(R.string.toast_playing_next),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onAddToPlayList = {
                viewModel.openOnlineAddToPlaylist(infoLxSong)
                lxInfoSong = null
            },
            addToPlaylistEnabled = when (infoLxSong.source) {
                LxSources.NETEASE -> neteaseLoggedIn
                LxSources.KUGOU -> kugouLoggedIn &&
                    viewModel.writeEnabledFor(LxSources.KUGOU, LxWriteOp.PLAYLIST_ADD_SONGS)
                LxSources.KUWO -> kuwoLoggedIn &&
                    viewModel.writeEnabledFor(LxSources.KUWO, LxWriteOp.PLAYLIST_ADD_SONGS)
                else -> false
            },
            removeFromPlaylist = run {
                val loggedIn = when (infoLxSong.source) {
                    LxSources.NETEASE -> neteaseLoggedIn && viewModel.accountWritesEnabled
                    LxSources.KUGOU -> kugouLoggedIn &&
                        viewModel.writeEnabledFor(LxSources.KUGOU, LxWriteOp.PLAYLIST_REMOVE_SONGS)
                    LxSources.KUWO -> kuwoLoggedIn &&
                        viewModel.writeEnabledFor(LxSources.KUWO, LxWriteOp.PLAYLIST_REMOVE_SONGS)
                    else -> false
                }
                val ownCtx = if (loggedIn) viewModel.currentOwnPlaylistContext(infoLxSong.source) else null
                if (ownCtx != null) {
                    {
                        viewModel.requestRemoveSongFromOwnPlaylist(infoLxSong)
                        lxInfoSong = null
                    }
                } else null
            },
            cloudDownloadEnabled = false,
            onDeleteFromDevice = { activity, songToDelete, onResult ->
                playerViewModel.deleteFromDevice(activity, songToDelete, onResult)
                lxInfoSong = null
            },
            onNavigateToAlbum = {
                val albumId = when (infoLxSong.source) {
                    com.gurio.gusic.data.lxmusic.LxSources.TENCENT -> infoUiSong.lxMusicExtraFields["albumMid"]
                    else -> infoUiSong.lxMusicExtraFields["albumId"]
                }
                if (!albumId.isNullOrBlank()) {
                    viewModel.openAlbumFromSearch(
                        com.gurio.gusic.data.lxmusic.LxAlbum(
                            id = albumId,
                            name = infoUiSong.album.orEmpty(),
                        )
                    )
                    lxInfoSong = null
                }
            },
            onNavigateToArtist = {
                scope.launch {
                    val artists = playerViewModel.resolveLxSongArtists(infoUiSong)
                    when {
                        artists.size == 1 -> {
                            viewModel.openArtistFromSearch(artists.first(), infoLxSong.source)
                            lxInfoSong = null
                        }
                        artists.size > 1 -> {
                            lxArtistPickerCandidates = artists
                            lxArtistPickerSource = infoLxSong.source
                            showLxArtistPicker = true
                            lxInfoSong = null
                        }
                    }
                }
            },
            onNavigateToGenre = {},
            onEditSong = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
            removeFromListTrigger = {},
        )
    }

    if (showLxArtistPicker && lxArtistPickerCandidates.isNotEmpty()) {
        com.gurio.gusic.presentation.components.LxArtistPickerBottomSheet(
            artists = lxArtistPickerCandidates,
            source = lxArtistPickerSource,
            sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
            onDismiss = { showLxArtistPicker = false },
            onArtistClick = { artist ->
                viewModel.openArtistFromSearch(artist, lxArtistPickerSource)
                showLxArtistPicker = false
            },
        )
    }

    val showSearchAlbumOverlay by viewModel.searchAlbumOverlay.collectAsStateWithLifecycle()
    if (showSearchAlbumOverlay) {
        val albumPageState by viewModel.searchAlbumPageState.collectAsStateWithLifecycle()
        BackHandler {
            if (isSelectionMode) multiSelectionState.clearSelection()
            else viewModel.dismissSearchAlbumOverlay()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            LxSearchAlbumPage(
                state = albumPageState,
                viewModel = viewModel,
                playerViewModel = playerViewModel,
                playLxSong = playLxSong,
                playAllPlaylist = playAllPlaylist,
                shuffleAllPlaylist = shuffleAllPlaylist,
                bottomBarHeightDp = bottomBarHeightDp,
                onBack = viewModel::dismissSearchAlbumOverlay,
                onScrollingChange = {},
                onSongMoreOptions = { lxInfoSong = it },
                isSelectionMode = isSelectionMode,
                selectedSongIds = selectedSongIds,
                onSongLongPress = toggleLxSongSelection,
                onSongSelectionToggle = toggleLxSongSelection,
                getSelectionIndex = { songId -> multiSelectionState.getSelectionIndex(songId) },
                multiSelectionState = multiSelectionState,
                onSelectionOptionsClick = { showMultiSelectionSheet = true },
            )
        }
    }
    val showSearchArtistOverlay by viewModel.searchArtistOverlay.collectAsStateWithLifecycle()
    if (showSearchArtistOverlay) {
        val artistPageState by viewModel.searchArtistPageState.collectAsStateWithLifecycle()
        BackHandler {
            if (isSelectionMode) {
                multiSelectionState.clearSelection()
            } else if (artistPageState.selectedAlbum != null) {
                viewModel.backFromSearchArtistAlbum()
            } else {
                viewModel.dismissSearchArtistOverlay()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            LxSearchArtistPage(
                state = artistPageState,
                viewModel = viewModel,
                playerViewModel = playerViewModel,
                playLxSong = playLxSong,
                playAllPlaylist = playAllPlaylist,
                shuffleAllPlaylist = shuffleAllPlaylist,
                bottomBarHeightDp = bottomBarHeightDp,
                showFollow = when (artistPageState.source) {
                    LxSources.TENCENT -> tencentLoggedIn &&
                        viewModel.writeEnabledFor(LxSources.TENCENT, LxWriteOp.ARTIST_FOLLOW)
                    LxSources.KUGOU -> kugouLoggedIn &&
                        viewModel.writeEnabledFor(LxSources.KUGOU, LxWriteOp.ARTIST_FOLLOW)
                    LxSources.KUWO -> kuwoLoggedIn &&
                        viewModel.writeEnabledFor(LxSources.KUWO, LxWriteOp.ARTIST_FOLLOW)
                    else -> neteaseLoggedIn
                },
                onBack = viewModel::dismissSearchArtistOverlay,
                onScrollingChange = {},
                onSongMoreOptions = { lxInfoSong = it },
                isSelectionMode = isSelectionMode,
                selectedSongIds = selectedSongIds,
                onSongLongPress = toggleLxSongSelection,
                onSongSelectionToggle = toggleLxSongSelection,
                getSelectionIndex = { songId -> multiSelectionState.getSelectionIndex(songId) },
                multiSelectionState = multiSelectionState,
                onSelectionOptionsClick = { showMultiSelectionSheet = true },
            )
        }
    }

    val onlineAddSheetState by viewModel.onlineAddSheet.collectAsStateWithLifecycle()
    onlineAddSheetState?.let { addState ->
        com.gurio.gusic.presentation.components.NeteaseAddToPlaylistSheet(
            state = addState,
            onDismiss = { viewModel.dismissOnlineAddSheet() },
            onConfirm = { ids -> viewModel.onlineAddSongToPlaylists(ids) },
        )
    }

    val onlineRemoveConfirmState by viewModel.onlineRemoveConfirm.collectAsStateWithLifecycle()
    onlineRemoveConfirmState?.let { removeState ->
        LxRemoveFromPlaylistConfirmDialog(
            songTitle = removeState.songTitle,
            playlistName = removeState.playlistName,
            removing = removeState.removing,
            onConfirm = { viewModel.confirmOnlineRemoveFromPlaylist() },
            onDismiss = { viewModel.dismissOnlineRemoveConfirm() },
        )
    }

    val batchRemoveConfirmState by viewModel.batchOnlineRemoveConfirm.collectAsStateWithLifecycle()
    batchRemoveConfirmState?.let { batchRemoveState ->
        LxBatchRemoveFromPlaylistConfirmDialog(
            count = batchRemoveState.songs.size,
            playlistName = batchRemoveState.playlistName,
            removing = batchRemoveState.removing,
            onConfirm = { viewModel.confirmBatchOnlineRemoveFromPlaylist() },
            onDismiss = { viewModel.dismissBatchOnlineRemoveConfirm() },
        )
    }
    LaunchedEffect(Unit) {
        viewModel.batchRemovedSelectionIds.collect { removedIds ->
            multiSelectionState.removeSongs(removedIds)
        }
    }

    if (showPlatformOrderSheet) {
        ReorderPlatformsSheet(
            platforms = platformOrder.toImmutableList(),
            platformName = { platformName(it) },
            hiddenPlatforms = effectiveHiddenPlatforms,
            onToggleHidden = { viewModel.togglePlatformHidden(it) },
            onReorder = { viewModel.savePlatformOrder(it) },
            onReset = { viewModel.resetPlatformOrder() },
            onDismiss = { showPlatformOrderSheet = false },
        )
    }

    val uncollectConfirm by viewModel.uncollectConfirm.collectAsStateWithLifecycle()
    uncollectConfirm?.let { (kind, id) ->
        LxUncollectConfirmDialog(
            kind = kind,
            onConfirm = { viewModel.confirmUncollect(kind, id) },
            onDismiss = viewModel::dismissUncollectConfirm,
        )
    }
    LxLoginDialogsHost(
        lxMusicViewModel = viewModel,
        showNetease = showLoginDialog,
        showTencent = showTxWebLoginDialog,
        showKugou = showKgLoginDialog,
        showKuwo = showKwLoginDialog,
        onDismissNetease = { showLoginDialog = false },
        onDismissTencent = { showTxWebLoginDialog = false },
        onDismissKugou = { showKgLoginDialog = false },
        onDismissKuwo = { showKwLoginDialog = false },
    )
}

private val searchTabs = listOf(
    "wy" to R.string.lx_source_netease,
    "kw" to R.string.lx_source_kuwo,
    "tx" to R.string.lx_source_qq,
    "kg" to R.string.lx_source_kugou,
)

private val lxDialogCancelShape = RoundedCornerShape(
    topStart = 26.dp, bottomStart = 26.dp,
    topEnd = 8.dp, bottomEnd = 8.dp,
)
private val lxDialogConfirmShape = RoundedCornerShape(
    topStart = 8.dp, bottomStart = 8.dp,
    topEnd = 26.dp, bottomEnd = 26.dp,
)

/** 浏览根 pager 的页面指示条：选中长条、未选中短条（与媒体库页 CompactLibraryPagerIndicator 同款样式）。 */
@Composable
private fun LxBrowsePagerIndicator(
    currentIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    val safeIndex = ((currentIndex % pageCount) + pageCount) % pageCount
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == safeIndex
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 10.dp,
                label = "LxBrowsePagerIndicatorWidth",
            )
            val alpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.35f,
                label = "LxBrowsePagerIndicatorAlpha",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(4.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun LxSourceTab(
    label: String,
    selected: Boolean,
    index: Int,
    selectedIndex: Int,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    var hasAnimatedSelectionChange by remember { mutableStateOf(false) }
    val animationSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200),
        label = "LxSourceTabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 200),
        label = "LxSourceTabContent"
    )

    LaunchedEffect(selectedIndex) {
        if (!hasAnimatedSelectionChange) {
            hasAnimatedSelectionChange = true
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }
        if (selected) {
            scale.animateTo(1.05f, animationSpec)
            scale.animateTo(1f, animationSpec)
        } else {
            scale.snapTo(1f)
        }
        if (!selected) {
            val distance = index - selectedIndex
            if (abs(distance) == 1) {
                val direction = if (distance > 0) 1 else -1
                val offsetValue = 12f * direction
                offsetX.animateTo(offsetValue, animationSpec)
                offsetX.animateTo(0f, animationSpec)
            } else {
                offsetX.snapTo(0f)
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    Surface(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(22.dp),
        color = backgroundColor,
        modifier = Modifier
            .widthIn(min = 64.dp)
            .graphicsLayer {
            scaleX = scale.value
            translationX = offsetX.value
            transformOrigin = TransformOrigin.Center
        },
    ) {
        if (icon != null && label.isEmpty()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .size(20.dp),
            )
        } else {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

// “我的”页未登录占位：提示当前平台未登录，并提供登录入口（复用现有平台登录弹窗）
@Composable
private fun LxMineNotLoggedInPlaceholder(
    platformLabel: String,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.presentation_batch_g_lx_mine_not_logged_in_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = platformLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = onLogin,
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(text = stringResource(R.string.presentation_batch_g_lx_mine_login))
            }
        }
    }
}

// 发现页顶栏账号区：昵称在左、头像在右；未登录显示占位图标与文案；点击进入当前平台登录弹窗
@Composable
private fun LxAccountChip(
    loggedIn: Boolean,
    nickname: String?,
    avatarUrl: String?,
    fallbackPlatformName: String,
    notLoggedInText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(end = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (loggedIn) (nickname ?: fallbackPlatformName) else notLoggedInText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (loggedIn) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 80.dp),
        )
        if (loggedIn && !avatarUrl.isNullOrBlank()) {
            SmartImage(
                model = avatarUrl,
                contentDescription = null,
                targetSize = SmartImageListTargetSize,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = if (loggedIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun LxDockedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    solidBackground: Boolean = false,
    enableInnerBox: Boolean = true,
    leadingBack: Boolean = false,
    onLeadingBack: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val fieldColor = MaterialTheme.colorScheme.secondaryContainer
        .compositeOver(MaterialTheme.colorScheme.surface)
    val onFieldColor = MaterialTheme.colorScheme.onSecondaryContainer
    val doSearch: () -> Unit = {
        if (query.isNotBlank()) {
            onSearch(query)
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(fieldColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingBack && onLeadingBack != null) {
            // 搜索态：左侧返回按钮，与右侧搜索按钮镜像对称
            FilledIconButton(
                onClick = onLeadingBack,
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    bottomStart = 24.dp,
                    topEnd = 8.dp,
                    bottomEnd = 8.dp,
                ),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .padding(start = 5.dp)
                    .size(42.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.lx_desc_back),
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Spacer(Modifier.width(16.dp))
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = onFieldColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        if (enableInnerBox) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = onFieldColor),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                ) { innerTextField ->
                    if (query.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                            color = onFieldColor.copy(alpha = 0.6f))
                    }
                    innerTextField()
                }
            }
        } else {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = onFieldColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            ) { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                            color = onFieldColor.copy(alpha = 0.6f))
                    }
                    innerTextField()
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (query.isNotBlank()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.lx_desc_clear),
                tint = onFieldColor,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable { onClear() }
                    .padding(2.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        FilledIconButton(
            onClick = doSearch,
            shape = RoundedCornerShape(
                topStart = 8.dp,
                bottomStart = 8.dp,
                topEnd = 24.dp,
                bottomEnd = 24.dp,
            ),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .padding(end = 5.dp)
                .size(42.dp),
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = stringResource(R.string.lx_desc_search),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun LxPlaylistItem(
    playlist: com.gurio.gusic.data.lxmusic.LxPlaylist,
    onClick: () -> Unit,
) {
    val trackCountFormat = stringResource(R.string.lx_text_track_count_format)
    val playCountHundredMillionFormat = stringResource(R.string.lx_text_play_count_hundred_million_plus)
    val playCountTenThousandFormat = stringResource(R.string.lx_text_play_count_ten_thousand_plus)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pic = playlist.pic
        if (!pic.isNullOrBlank()) {
            SmartImage(
                model = pic,
                contentDescription = null,
                targetSize = SmartImageListTargetSize,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    playlist.creator?.let { append(it) }
                    if (!playlist.creator.isNullOrBlank()) append(" · ")
                    playlist.trackCount?.let { append(trackCountFormat.format(it)) }
                    playlist.playCount?.let {
                        if (it > 100000000) append(playCountHundredMillionFormat.format(it / 100000000))
                        else if (it > 10000) append(playCountTenThousandFormat.format(it / 10000))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun LxScrollToTopFab(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val isScrolled = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 120

    AnimatedVisibility(
        visible = isScrolled,
        enter = scaleIn(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
        exit = scaleOut(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        FilledIconButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(R.string.lx_desc_back_to_top),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
internal fun LxLocateCurrentSongFab(
    listState: LazyListState,
    currentSongId: String?,
    songIds: List<String>,
    leadingItems: Int = 0,
    modifier: Modifier = Modifier,
    perSongMatchIds: List<Set<String>>? = null,
    currentMatchIds: Set<String>? = null,
) {
    val scope = rememberCoroutineScope()
    val targetIndex = remember(currentSongId, songIds, leadingItems, perSongMatchIds, currentMatchIds) {
        val i = if (perSongMatchIds != null && currentMatchIds != null) {
            perSongMatchIds.indexOfFirst { ids -> ids.any { it in currentMatchIds } }
        } else {
            currentSongId?.let { songIds.indexOf(it) } ?: -1
        }
        if (i >= 0) i + leadingItems else -1
    }
    val visible by remember(targetIndex, listState) {
        derivedStateOf {
            if (targetIndex < 0) {
                false
            } else {
                val visibleIndices = listState.layoutInfo.visibleItemsInfo
                visibleIndices.none { it.index == targetIndex }
            }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
        exit = scaleOut(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        FilledIconButton(
            onClick = { scope.launch { listState.animateScrollToItem(targetIndex) } },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Rounded.MyLocation,
                contentDescription = stringResource(R.string.lx_desc_locate_current),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun Modifier.lxVerticalDragGate(onVerticalDrag: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var triggered = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (!triggered) {
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (abs(dy) > 2f && abs(dy) > abs(dx)) {
                            triggered = true
                            onVerticalDrag(true)
                        }
                    }
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                if (triggered) onVerticalDrag(false)
            }
        }
    }

@Composable
internal fun LxFloatingSelectionRow(
    visible: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        SelectionActionRow(
            selectedCount = selectedCount,
            onSelectAll = onSelectAll,
            onDeselect = onDeselect,
            onOptionsClick = onOptionsClick,
            modifier = Modifier.padding(end = 19.dp),
            trailingContentBeforeOptions = { SelectionCountPill(selectedCount) },
        )
    }
}

@Composable
internal fun LxSelectionSwitch(
    inSelectionMode: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    normalContent: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = inSelectionMode,
        label = "LxSelectionSwitch",
        transitionSpec = {
            (slideInHorizontally { -it } + fadeIn()) togetherWith
                (slideOutHorizontally { it } + fadeOut())
        },
        modifier = modifier,
    ) { selection ->
        if (selection) {
            SelectionActionRow(
                selectedCount = selectedCount,
                onSelectAll = onSelectAll,
                onDeselect = onDeselect,
                onOptionsClick = onOptionsClick,
                trailingContentBeforeOptions = { SelectionCountPill(selectedCount) },
            )
        } else {
            normalContent()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LxBoardPage(
    browseState: LxMusicViewModel.BrowseState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.gurio.gusic.data.lxmusic.LxSong, List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    playAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val boardQueueFallback = stringResource(R.string.lx_tab_board)

    val pullToRefreshState = rememberPullToRefreshState()
    val boardHeaderHeight = 66.dp
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val boardSongIds = remember(browseState.boardSongs) {
        browseState.boardSongs.map { LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val boardScope = rememberCoroutineScope()
    val boardPlaylistId = browseState.selectedBoard?.bangid
    LaunchedEffect(boardPlaylistId) {
        if (boardPlaylistId != null) viewModel.ensureCollectDataLoaded()
    }
    val (boardLocateVisible, boardLocateIndex) = rememberLocateButtonState(
        listState = listState,
        currentSongId = stablePlayerState.currentSong?.id,
        songIds = boardSongIds,
    )
    val collectBusyKeys by viewModel.collectBusyKeys.collectAsStateWithLifecycle()
    val onFolderShare = rememberFolderShare(viewModel)
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = browseState.loadingBoard,
            onRefresh = { viewModel.refreshBrowse() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = browseState.loadingBoard,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = boardHeaderHeight),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = boardHeaderHeight)
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 34.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = 34.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = 0.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = 0.dp,
                            smoothnessAsPercentBR = 60,
                        )
                    )
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 14.dp,
                    bottom = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(browseState.boardSongs, key = { "board_${it.source}_${it.songmid}" }) { song ->
                    val uiSong = remember(song.source, song.songmid, song.pic) { LxSongMapper.toSong(song) }
                    val isCurrent = stablePlayerState.currentSong?.id == uiSong.id
                    val isSelected = selectedSongIds.contains(uiSong.id)
                    val selectionIndex = getSelectionIndex(uiSong.id)
                    EnhancedSongListItem(
                        song = uiSong,
                        isPlaying = isCurrent && stablePlayerState.isPlaying,
                        isCurrentSong = isCurrent,
                        showAlbumArt = true,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        selectionIndex = selectionIndex,
                        onLongPress = { onSongLongPress(song) },
                        onMoreOptionsClick = { onSongMoreOptions(song) },
                        onClick = {
                            if (isSelectionMode) {
                                onSongSelectionToggle(song)
                            } else {
                                playLxSong(song, browseState.boardSongs, browseState.selectedBoard?.name ?: boardQueueFallback)
                            }
                        },
                    )
                }
                if (browseState.boardSongs.isNotEmpty()) {
                    item(key = "board_count_footer") {
                        Text(stringResource(R.string.lx_text_total_songs_count, browseState.boardSongs.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        ) {
            val boardSource = browseState.moduleSource
            val boardCollectState = if (boardSource == LxSources.KUGOU ||
                boardSource == LxSources.KUWO ||
                (boardSource != null && boardSource != LxSources.NETEASE &&
                    !viewModel.writeEnabledFor(boardSource, LxWriteOp.PLAYLIST_COLLECT))
            ) null else
                rememberCollectState(
                    viewModel, LxMusicViewModel.CollectKind.PLAYLIST, boardPlaylistId,
                    name = browseState.selectedBoard?.name,
                )
            val boardCapsule = @Composable {
                LxFolderActionCapsule(
                    modifier = Modifier.padding(start = 7.dp, end = 17.dp, top = 4.dp, bottom = 4.dp),
                    showLocate = boardLocateVisible,
                    onLocate = {
                        if (boardLocateIndex >= 0) boardScope.launch {
                            listState.animateScrollToItem(boardLocateIndex)
                        }
                    },
                    collectState = boardCollectState,
                    collectBusy = boardPlaylistId != null && collectBusyKeys.contains(
                        viewModel.collectKey(LxMusicViewModel.CollectKind.PLAYLIST, boardPlaylistId)
                    ),
                    onCollectClick = {
                        boardPlaylistId?.let {
                            viewModel.onCollectButtonClick(
                                LxMusicViewModel.CollectKind.PLAYLIST, it,
                                name = browseState.selectedBoard?.name,
                            )
                        }
                    },
                    onShare = {
                        boardPlaylistId?.let {
                            onFolderShare(LxMusicViewModel.CollectKind.PLAYLIST, it)
                        }
                    },
                    buttonElevation = if (boardCollectState == null) 4.dp else 0.dp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.backToBoardMenu() }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.lx_desc_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = browseState.selectedBoard?.name.orEmpty(),
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (boardCollectState == null) {
                    boardCapsule()
                } else {
                    Surface(
                        shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        boardCapsule()
                    }
                }
            }
        }
        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = boardHeaderHeight + 4.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 16.dp),
            listState = listState,
        )
        LxScrollToTopFab(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
        )
        if (browseState.boardSongs.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { shuffleAllPlaylist(browseState.boardSongs, browseState.selectedBoard?.name ?: boardQueueFallback) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.lx_desc_shuffle), modifier = Modifier.size(22.dp))
                }
                FilledIconButton(
                    onClick = { playAllPlaylist(browseState.boardSongs, browseState.selectedBoard?.name ?: boardQueueFallback) },
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.lx_desc_play_all), modifier = Modifier.size(26.dp))
                }
            }
        }
        LxFloatingSelectionRow(
            visible = isSelectionMode,
            selectedCount = selectedSongIds.size,
            onSelectAll = {
                multiSelectionState.selectAll(browseState.boardSongs.map { LxSongMapper.toSong(it) })
            },
            onDeselect = { multiSelectionState.clearSelection() },
            onOptionsClick = onSelectionOptionsClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = boardHeaderHeight + 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LxPlaylistPage(
    browseState: LxMusicViewModel.BrowseState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.gurio.gusic.data.lxmusic.LxSong, List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    playAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val playlistListState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val listState = if (browseState.selectedPlaylist != null) detailListState else playlistListState

    val pullToRefreshState = rememberPullToRefreshState()
    val isUserPlaylistMode = browseState.userPlaylistUid != null || browseState.loadingUserPlaylists
    val isRefreshing = if (browseState.selectedPlaylist != null) browseState.loadingPlaylistSongs
    else if (isUserPlaylistMode) browseState.loadingUserPlaylists
    else if (browseState.isPlaylistSearchMode) browseState.searchingPlaylists
    else browseState.loadingPlaylists
    val density = LocalDensity.current
    val mode = when {
        browseState.selectedPlaylist != null -> "detail"
        isUserPlaylistMode -> "user"
        browseState.isPlaylistSearchMode -> "search"
        else -> "category"
    }
    var headerHeight by remember(mode) {
        mutableStateOf(
            when (mode) {
                "detail" -> 50.dp
                "user", "search" -> 110.dp
                else -> 122.dp
            }
        )
    }
    var showNeteaseUidDialog by remember { mutableStateOf(false) }
    val consumeHorizontalScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset = Offset(available.x, 0f)
            override suspend fun onPreFling(available: Velocity): Velocity = Velocity(available.x, 0f)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val detailSongIds = remember(browseState.playlistSongs) {
        browseState.playlistSongs.map { LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val scope = rememberCoroutineScope()
    val detailPlaylistId = browseState.selectedPlaylist?.id
    LaunchedEffect(detailPlaylistId) {
        if (detailPlaylistId != null) viewModel.ensureCollectDataLoaded()
    }
    val (detailLocateVisible, detailLocateIndex) = rememberLocateButtonState(
        listState = detailListState,
        currentSongId = stablePlayerState.currentSong?.id,
        songIds = detailSongIds,
    )
    val collectBusyKeys by viewModel.collectBusyKeys.collectAsStateWithLifecycle()
    val onFolderShare = rememberFolderShare(viewModel)
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshBrowse() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState, isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = headerHeight),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = headerHeight)
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 34.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = 34.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = 0.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = 0.dp,
                            smoothnessAsPercentBR = 60,
                        )
                    )
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 14.dp,
                    bottom = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (browseState.selectedPlaylist == null) {
                    val listPlaylists = when {
                        isUserPlaylistMode -> browseState.userPlaylists
                        browseState.isPlaylistSearchMode -> browseState.playlistSearchResults
                        else -> browseState.playlists
                    }
                    items(listPlaylists, key = { "pl_${it.id}" }) { playlist ->
                        LxPlaylistItem(playlist = playlist, onClick = { viewModel.selectPlaylist(playlist) })
                    }
                    val playlistCount = listPlaylists.size
                    if (playlistCount > 0) {
                        item(key = "playlist_count_footer") {
                            Text(stringResource(R.string.lx_text_total_playlists_count, playlistCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                } else {
                    items(browseState.playlistSongs, key = { "pls_${it.source}_${it.songmid}" }) { song ->
                    val uiSong = remember(song.source, song.songmid, song.pic) { LxSongMapper.toSong(song) }
                        LaunchedEffect(song) { viewModel.loadPlaylistSongPicIfMissing(song) }
                        val isCurrent = stablePlayerState.currentSong?.id == uiSong.id
                        val isSelected = selectedSongIds.contains(uiSong.id)
                        val selectionIndex = getSelectionIndex(uiSong.id)
                        EnhancedSongListItem(
                            song = uiSong,
                            isPlaying = isCurrent && stablePlayerState.isPlaying,
                            isCurrentSong = isCurrent,
                            showAlbumArt = true,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            selectionIndex = selectionIndex,
                            onLongPress = { onSongLongPress(song) },
                            onMoreOptionsClick = { onSongMoreOptions(song) },
                            onClick = {
                                if (isSelectionMode) {
                                    onSongSelectionToggle(song)
                                } else {
                                    playLxSong(song, browseState.playlistSongs, browseState.selectedPlaylist?.name.orEmpty())
                                }
                            },
                        )
                    }
                    if (browseState.playlistSongsHasMore) {
                        item(key = "playlist_songs_load_more") {
                            LaunchedEffect(Unit) { viewModel.loadMorePlaylistSongs() }
                            LxLoadMoreButton(
                                loading = browseState.playlistSongsLoadingMore,
                                onClick = viewModel::loadMorePlaylistSongs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                    if (browseState.playlistSongs.isNotEmpty()) {
                        item(key = "playlist_song_count_footer") {
                            Text(stringResource(R.string.lx_text_total_songs_count, browseState.playlistSongs.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                if (browseState.error != null) {
                    item {
                        Text(browseState.error, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val h = with(density) { coords.size.height.toDp() }
                    if (h > 0.dp && h != headerHeight) headerHeight = h
                },
        ) {
            if (browseState.selectedPlaylist != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { viewModel.backToPlaylistList() },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.lx_text_playlist_list), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    browseState.selectedPlaylist?.name?.takeIf { it.isNotBlank() }?.let { pName ->
                        LxFolderTitleText(text = pName, modifier = Modifier.weight(1f))
                    }
                    LxFolderActionCapsule(
                        showLocate = detailLocateVisible,
                        onLocate = {
                            if (detailLocateIndex >= 0) scope.launch {
                                detailListState.animateScrollToItem(detailLocateIndex)
                            }
                        },
                        collectState = if (browseState.moduleSource != null &&
                            browseState.moduleSource != LxSources.NETEASE &&
                            !viewModel.writeEnabledFor(browseState.moduleSource!!, LxWriteOp.PLAYLIST_COLLECT)
                        ) null else
                            rememberCollectState(
                                viewModel, LxMusicViewModel.CollectKind.PLAYLIST, detailPlaylistId,
                                gid = browseState.selectedPlaylist?.originGid,
                                name = browseState.selectedPlaylist?.name,
                            ),
                        collectBusy = detailPlaylistId != null && collectBusyKeys.contains(
                            viewModel.collectKey(LxMusicViewModel.CollectKind.PLAYLIST, detailPlaylistId)
                        ),
                        onCollectClick = {
                            detailPlaylistId?.let {
                                viewModel.onCollectButtonClick(
                                    LxMusicViewModel.CollectKind.PLAYLIST, it,
                                    gid = browseState.selectedPlaylist?.originGid,
                                    name = browseState.selectedPlaylist?.name,
                                )
                            }
                        },
                        onShare = {
                            detailPlaylistId?.let {
                                onFolderShare(LxMusicViewModel.CollectKind.PLAYLIST, it)
                            }
                        },
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            LxDockedSearchBar(
                                query = browseState.playlistSearchKeyword,
                                onQueryChange = viewModel::updatePlaylistSearchQuery,
                                onSearch = { if (browseState.playlistSearchKeyword.isNotBlank())
                                    viewModel.submitPlaylistInput(browseState.playlistSearchKeyword) },
                                onClear = { viewModel.updatePlaylistSearchQuery("") },
                                placeholder = stringResource(R.string.lx_placeholder_search_playlist),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                enableInnerBox = false,
                            )
                        }
                        if (browseState.moduleSource == LxSources.NETEASE
                            && !browseState.isPlaylistSearchMode && !isUserPlaylistMode
                        ) {
                            FilledIconButton(
                                onClick = { showNeteaseUidDialog = true },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .shadow(4.dp, CircleShape)
                                    .size(40.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = stringResource(R.string.lx_desc_fetch_user_playlists),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    if (isUserPlaylistMode) {
                        Surface(
                            onClick = { viewModel.exitUserPlaylists() },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                                .compositeOver(MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    browseState.userPlaylistName?.takeIf { it.isNotBlank() }
                                        ?.let { stringResource(R.string.lx_text_netease_user_with_name, it) } ?: stringResource(R.string.lx_text_back_to_categories),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else if (browseState.isPlaylistSearchMode) {
                        Surface(
                            onClick = { viewModel.exitPlaylistSearch() },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                                .compositeOver(MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.lx_text_back_to_categories), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .nestedScroll(consumeHorizontalScroll),
                        ) {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                items(browseState.playlistTags, key = { it.id }) { tag ->
                                    Surface(
                                        onClick = { viewModel.selectPlaylistTag(tag) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (browseState.selectedTag?.id == tag.id)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(tag.name,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (browseState.selectedTag?.id == tag.id)
                                                MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ExpressiveScrollBar(
            modifier = Modifier.align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = headerHeight + 4.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 16.dp),
            listState = listState,
        )
        LxScrollToTopFab(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
        )
        if (browseState.selectedPlaylist != null && browseState.playlistSongs.isNotEmpty()) {
            LxBottomPlayActions(
                onShuffle = {
                    shuffleAllPlaylist(
                        browseState.playlistSongs, browseState.selectedPlaylist.name
                    )
                },
                onPlayAll = {
                    playAllPlaylist(
                        browseState.playlistSongs, browseState.selectedPlaylist.name
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
            )
        }
        if (showNeteaseUidDialog) {
            NeteaseUserPlaylistDialog(
                onDismiss = { showNeteaseUidDialog = false },
                onConfirm = { input ->
                    showNeteaseUidDialog = false
                    viewModel.loadNeteaseUserPlaylists(input)
                },
            )
        }
        LxFloatingSelectionRow(
            visible = isSelectionMode,
            selectedCount = selectedSongIds.size,
            onSelectAll = {
                multiSelectionState.selectAll(browseState.playlistSongs.map { LxSongMapper.toSong(it) })
            },
            onDeselect = { multiSelectionState.clearSelection() },
            onOptionsClick = onSelectionOptionsClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = headerHeight + 4.dp),
        )
    }
}

@Composable
private fun NeteaseUserPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val fieldColor = MaterialTheme.colorScheme.secondaryContainer
        .compositeOver(MaterialTheme.colorScheme.surface)
    val onFieldColor = MaterialTheme.colorScheme.onSecondaryContainer
    val dialogColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val doConfirm: () -> Unit = {
        if (text.isNotBlank()) {
            focusManager.clearFocus()
            keyboardController?.hide()
            onConfirm(text)
        }
    }
    AlertDialog(
        containerColor = dialogColor,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lx_title_netease_user_playlists)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.lx_msg_netease_user_playlists_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(fieldColor)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = onFieldColor),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { doConfirm() }),
                    ) { innerTextField ->
                        if (text.isEmpty()) {
                            Text(stringResource(R.string.lx_placeholder_uid_or_link),
                                style = MaterialTheme.typography.bodyLarge,
                                color = onFieldColor.copy(alpha = 0.6f))
                        }
                        innerTextField()
                    }
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    shape = lxDialogCancelShape,
                    modifier = Modifier
                        .shadow(2.dp, lxDialogCancelShape)
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                }
                FilledTonalIconButton(
                    enabled = text.isNotBlank(),
                    onClick = { doConfirm() },
                    shape = lxDialogConfirmShape,
                    modifier = Modifier
                        .then(
                            if (text.isNotBlank()) Modifier.shadow(2.dp, lxDialogConfirmShape)
                            else Modifier
                        )
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.lx_desc_view_playlists))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LxSearchPage(
    searchState: LxMusicViewModel.SearchState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit,
    bottomBarHeightDp: Dp,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()

    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(searchState.searchEpoch) {
        if (searchState.searchEpoch <= 0L) return@LaunchedEffect
        viewModel.searchState.first {
            if (it.searchEpoch != searchState.searchEpoch) false
            else when (it.type) {
                LxMusicViewModel.SearchType.SONG -> it.results.isNotEmpty()
                LxMusicViewModel.SearchType.ARTIST -> it.artistResults.isNotEmpty()
                LxMusicViewModel.SearchType.ALBUM -> it.albumResults.isNotEmpty()
                LxMusicViewModel.SearchType.PLAYLIST -> it.playlistResults.isNotEmpty()
            }
        }
        withFrameNanos { }
        listState.requestScrollToItem(0, 0)
    }

    LaunchedEffect(searchState.type) {
        listState.requestScrollToItem(0, 0)
    }

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val searchSongIds = remember(searchState.results) {
        searchState.results.map { LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val searchLeadingItems = if (searchState.error != null) 1 else 0

    Box(modifier = Modifier.fillMaxSize()) {
        val isSearching = when (searchState.type) {
            LxMusicViewModel.SearchType.ARTIST -> searchState.searchingArtists
            LxMusicViewModel.SearchType.ALBUM -> searchState.searchingAlbums
            LxMusicViewModel.SearchType.PLAYLIST -> searchState.searchingPlaylists
            else -> searchState.searching
        }
        PullToRefreshBox(
            isRefreshing = isSearching,
            onRefresh = { viewModel.refreshBrowse() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState, isRefreshing = isSearching,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 34.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = 34.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = 0.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = 0.dp,
                            smoothnessAsPercentBR = 60,
                        )
                    )
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (searchState.type == LxMusicViewModel.SearchType.ARTIST) {
                    if (searchState.searchingArtists && searchState.artistResults.isEmpty()) {
                        item(key = "artist_loading") {
                            LxLoadingSkeletonRows(3)
                        }
                    } else {
                        if (searchState.artistResults.isNotEmpty()) {
                            mineArtistItems(
                                artists = searchState.artistResults,
                                onClick = viewModel::openArtistFromSearch,
                            )
                        } else if (searchState.error != null) {
                            item(key = "artist_error") {
                                Text(
                                    searchState.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        if (searchState.canLoadMoreArtists) {
                            item(key = "load_more_artist") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LxLoadMoreButton(
                                        loading = searchState.loadingMoreArtists,
                                        onClick = { viewModel.loadMore() },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                } else if (searchState.type == LxMusicViewModel.SearchType.ALBUM) {
                    if (searchState.searchingAlbums && searchState.albumResults.isEmpty()) {
                        item(key = "album_loading") {
                            LxLoadingSkeletonRows(3)
                        }
                    } else {
                        if (searchState.albumResults.isNotEmpty()) {
                            mineAlbumItems(
                                albums = searchState.albumResults,
                                onClick = viewModel::openAlbumFromSearch,
                            )
                        } else if (searchState.error != null) {
                            item(key = "album_error") {
                                Text(
                                    searchState.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        if (searchState.canLoadMoreAlbums) {
                            item(key = "load_more_album") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LxLoadMoreButton(
                                        loading = searchState.loadingMoreAlbums,
                                        onClick = { viewModel.loadMore() },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                } else if (searchState.type == LxMusicViewModel.SearchType.PLAYLIST) {
                    if (searchState.searchingPlaylists && searchState.playlistResults.isEmpty()) {
                        item(key = "playlist_loading") {
                            LxLoadingSkeletonRows(3)
                        }
                    } else {
                        if (searchState.playlistResults.isNotEmpty()) {
                            items(searchState.playlistResults, key = { "spl_${it.id}" }) { playlist ->
                                LxPlaylistItem(playlist = playlist, onClick = { viewModel.selectPlaylist(playlist) })
                            }
                            item(key = "search_playlist_count_footer") {
                                Text(stringResource(R.string.lx_text_total_playlists_count, searchState.playlistResults.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        } else if (searchState.error != null) {
                            item(key = "playlist_error") {
                                Text(
                                    searchState.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        } else if (searchState.searchedSource != null) {
                            item(key = "playlist_empty") {
                                Text(
                                    stringResource(R.string.lx_search_no_results),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                                )
                            }
                        }
                    }
                } else {
                if (searchState.error != null) {
                    item {
                        Text(searchState.error, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                if (searchState.error == null && !searchState.searching &&
                    searchState.searchedSource != null && searchState.results.isEmpty()
                ) {
                    item(key = "search_empty") {
                        Text(
                            stringResource(R.string.lx_search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }
                }
                items(searchState.results, key = { "${it.source}_${it.songmid}" }) { song ->
                    val uiSong = remember(song.source, song.songmid, song.pic) { LxSongMapper.toSong(song) }
                    val isCurrent = stablePlayerState.currentSong?.id == uiSong.id
                    val isSelected = selectedSongIds.contains(uiSong.id)
                    val selectionIndex = getSelectionIndex(uiSong.id)
                    EnhancedSongListItem(
                        song = uiSong,
                        isPlaying = isCurrent && stablePlayerState.isPlaying,
                        isCurrentSong = isCurrent,
                        showAlbumArt = true,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        selectionIndex = selectionIndex,
                        onLongPress = { onSongLongPress(song) },
                        onMoreOptionsClick = { onSongMoreOptions(song) },
                        onClick = {
                            if (isSelectionMode) {
                                onSongSelectionToggle(song)
                            } else {
                                playLxSong(song)
                            }
                        },
                    )
                }
                if (searchState.results.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.lx_text_total_songs_count, searchState.results.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                if (searchState.canLoadMore) {
                    item(key = "load_more") {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center) {
                            LxLoadMoreButton(
                                loading = searchState.loadingMore,
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
                }
            }
        }
        ExpressiveScrollBar(
            modifier = Modifier.align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 8.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 16.dp),
            listState = listState,
        )
        LxLocateCurrentSongFab(
            listState = listState,
            currentSongId = stablePlayerState.currentSong?.id,
            songIds = searchSongIds,
            leadingItems = searchLeadingItems,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 74.dp),
        )
        LxScrollToTopFab(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
        )
        LxFloatingSelectionRow(
            visible = isSelectionMode,
            selectedCount = selectedSongIds.size,
            onSelectAll = {
                multiSelectionState.selectAll(searchState.results.map { LxSongMapper.toSong(it) })
            },
            onDeselect = { multiSelectionState.clearSelection() },
            onOptionsClick = onSelectionOptionsClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun LxSearchTypeSwitch(
    selected: LxMusicViewModel.SearchType,
    onSelect: (LxMusicViewModel.SearchType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldColor = MaterialTheme.colorScheme.secondaryContainer
        .compositeOver(MaterialTheme.colorScheme.surface)
    val fraction by animateFloatAsState(
        targetValue = when (selected) {
            LxMusicViewModel.SearchType.SONG -> 0f
            LxMusicViewModel.SearchType.ALBUM -> 1f
            LxMusicViewModel.SearchType.ARTIST -> 2f
            LxMusicViewModel.SearchType.PLAYLIST -> 3f
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "searchTypeThumb",
    )
    val songColor by animateColorAsState(
        targetValue = if (selected == LxMusicViewModel.SearchType.SONG)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220), label = "songColor",
    )
    val artistColor by animateColorAsState(
        targetValue = if (selected == LxMusicViewModel.SearchType.ARTIST)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220), label = "artistColor",
    )
    val albumColor by animateColorAsState(
        targetValue = if (selected == LxMusicViewModel.SearchType.ALBUM)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220), label = "albumColor",
    )
    val playlistColor by animateColorAsState(
        targetValue = if (selected == LxMusicViewModel.SearchType.PLAYLIST)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220), label = "playlistColor",
    )
    BoxWithConstraints(
        modifier = modifier
            .width(216.dp)
            .height(30.dp)
            .shadow(4.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(fieldColor),
    ) {
        val quarterWidthPx = with(LocalDensity.current) { (maxWidth / 4).toPx() }
        Box(
            Modifier
                .offset { IntOffset((fraction * quarterWidthPx).roundToInt(), 0) }
                .width(maxWidth / 4)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(LxMusicViewModel.SearchType.SONG) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.lx_text_song_type),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = songColor,
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(LxMusicViewModel.SearchType.ALBUM) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.lx_text_album_type),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = albumColor,
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(LxMusicViewModel.SearchType.ARTIST) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.lx_text_artist_type),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = artistColor,
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(LxMusicViewModel.SearchType.PLAYLIST) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.lx_text_playlist_type),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = playlistColor,
                )
            }
        }
    }
}

private class LxDetailHeaderState(
    val minHeightPx: Float,
    val maxHeightPx: Float,
    val height: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val connection: NestedScrollConnection,
)

@Composable
private fun rememberLxDetailHeaderState(listState: LazyListState): LxDetailHeaderState {
    val density = LocalDensity.current
    val statusBarHeightPx = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
    val minHeightPx = with(density) { 64.dp.toPx() } + statusBarHeightPx
    val maxHeightPx = with(density) { 300.dp.toPx() }
    val height = remember(minHeightPx, maxHeightPx) { Animatable(maxHeightPx) }
    val scope = rememberCoroutineScope()
    val connection = remember(minHeightPx, maxHeightPx, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0
                if (!isScrollingDown &&
                    (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }
                val previousHeight = height.value
                val newHeight = (previousHeight + delta).coerceIn(minHeightPx, maxHeightPx)
                val consumed = newHeight - previousHeight
                if (consumed.roundToInt() != 0) scope.launch { height.snapTo(newHeight) }
                val canConsumeScroll = !(isScrollingDown && newHeight == minHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }
    LaunchedEffect(listState.isScrollInProgress, minHeightPx, maxHeightPx) {
        if (!listState.isScrollInProgress) {
            val shouldExpand = height.value > (minHeightPx + maxHeightPx) / 2
            val canExpand = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
            val target = if (shouldExpand && canExpand) maxHeightPx else minHeightPx
            if (height.value != target) {
                height.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
            }
        }
    }
    return remember(minHeightPx, maxHeightPx) {
        LxDetailHeaderState(minHeightPx, maxHeightPx, height, connection)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LxCollapsingDetailHeader(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    collapseFraction: Float,
    headerHeightDp: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShuffle: (() -> Unit)? = null,
    showShuffleFab: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    expandedBottomContent: (@Composable () -> Unit)? = null,
    shuffleContentDescription: String? = null,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalGusicDarkTheme.current) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.4f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - solidAlpha
    val displayUrl = artworkUrl?.takeIf { it.isNotBlank() }
    val headerOverlayBrush = remember(surfaceColor, expandedContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.22f * expandedContentAlpha),
                surfaceColor.copy(alpha = 0.82f * expandedContentAlpha),
                surfaceColor,
            )
        )
    }
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent))
    }
    val expandedStatusBarFallback = remember(statusBarColor, surfaceColor) {
        statusBarColor.compositeOver(surfaceColor)
    }
    val fallbackStatusBarColor = remember(expandedStatusBarFallback, surfaceColor, solidAlpha) {
        lerpColor(expandedStatusBarFallback, surfaceColor, solidAlpha)
    }
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val shuffleAlignment = BiasAlignment(horizontalBias = 1f, verticalBias = titleVerticalBias)

    GusicStatusBarStyle(color = fallbackStatusBarColor)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeightDp)
            .clipToBounds()
    ) {
        if (expandedContentAlpha > 0.01f) {
            if (displayUrl != null) {
                SmartImage(
                    model = displayUrl,
                    contentDescription = title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    allowHardware = true,
                    crossfadeDurationMillis = 0,
                    alpha = expandedContentAlpha,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MusicIconPattern(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandedContentAlpha },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(headerOverlayBrush)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(statusBarBrush)
                .align(Alignment.TopCenter)
        )

        CollapsibleCommonTopBar(
            title = title,
            subtitle = subtitle,
            collapseFraction = collapseFraction,
            headerHeight = headerHeightDp,
            onBackClick = onBack,
            containerColor = surfaceColor.copy(alpha = solidAlpha),
            collapsedTitleStartPadding = 68.dp,
            expandedTitleStartPadding = 24.dp,
            collapsedTitleEndPadding = 24.dp,
            expandedTitleEndPadding = 136.dp,
            containerHeightRange = 112.dp to 56.dp,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = RoundedSans,
                fontWeight = FontWeight.SemiBold,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.08f)
            ),
            titleScaleRange = 1f to 1f,
            titleFontSizeRange = 30.sp to 18.sp,
            maxLines = if (collapseFraction < 0.5f) 2 else 1,
            collapsedSubtitleMaxLines = 1,
            expandedSubtitleMaxLines = 2,
            contentColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fadeSubtitleOnCollapse = false,
            syncStatusBarWithContainer = false,
            actions = actions,
        )

        expandedBottomContent?.let { content ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 136.dp, bottom = 14.dp)
                    .graphicsLayer { alpha = expandedContentAlpha },
            ) {
                content()
            }
        }

        if (showShuffleFab && onShuffle != null) {
            LargeExtendedFloatingActionButton(
                onClick = onShuffle,
                shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
                modifier = Modifier
                    .align(shuffleAlignment)
                    .statusBarsPadding()
                    .padding(end = 16.dp)
                    .graphicsLayer {
                        scaleX = expandedContentAlpha
                        scaleY = expandedContentAlpha
                        alpha = expandedContentAlpha
                    }
            ) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = shuffleContentDescription,
                )
            }
        }
    }
}

@Composable
private fun LxSearchArtistPage(
    state: LxMusicViewModel.SearchArtistPageState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.gurio.gusic.data.lxmusic.LxSong, List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    playAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    showFollow: Boolean,
    onBack: () -> Unit,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val artist = state.artist ?: return
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    state.selectedAlbum?.let { album ->
        BackHandler {
            if (isSelectionMode) multiSelectionState.clearSelection()
            else viewModel.backFromSearchArtistAlbum()
        }
        val albumListState = rememberLazyListState()
        LaunchedEffect(album.id) { runCatching { albumListState.scrollToItem(0) } }
        val albumSongIds = remember(state.albumSongs) {
            state.albumSongs.map {
                com.gurio.gusic.data.lxmusic.LxSongMapper.unifiedSongId(it.source, it.songmid).toString()
            }
        }
        LaunchedEffect(album.id) { viewModel.ensureCollectDataLoaded() }
        val albumKind = LxMusicViewModel.CollectKind.ALBUM
        val albumCollectBusyKeys by viewModel.collectBusyKeys.collectAsStateWithLifecycle()
        val albumOnFolderShare = rememberFolderShare(viewModel)
        val albumHeaderState = rememberLxDetailHeaderState(albumListState)
        val albumDensity = LocalDensity.current
        val albumHeaderHeightDp = with(albumDensity) { albumHeaderState.height.value.toDp() }
        val albumMinHeaderDp = with(albumDensity) { albumHeaderState.minHeightPx.toDp() }
        val albumCollapseFraction by remember(albumHeaderState) {
            derivedStateOf {
                1f - (
                    (albumHeaderState.height.value - albumHeaderState.minHeightPx) /
                        (albumHeaderState.maxHeightPx - albumHeaderState.minHeightPx)
                    ).coerceIn(0f, 1f)
            }
        }
        val albumBottomPadding = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap
        val showAlbumScrollBar by remember {
            derivedStateOf {
                albumCollapseFraction > 0.95f &&
                    (albumListState.canScrollForward || albumListState.canScrollBackward)
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(albumHeaderState.connection),
            ) {
                LazyColumn(
                    state = albumListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                0,
                                (albumHeaderState.height.value - albumHeaderState.minHeightPx).roundToInt(),
                            )
                        }
                        .lxVerticalDragGate(onScrollingChange),
                    contentPadding = PaddingValues(
                        top = albumMinHeaderDp + 8.dp,
                        start = 16.dp,
                        end = if (showAlbumScrollBar) 24.dp else 16.dp,
                        bottom = albumBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.albumSongs.isNotEmpty()) {
                        item(key = "artist_album_play_all") {
                            LxSelectionSwitch(
                                inSelectionMode = isSelectionMode,
                                selectedCount = selectedSongIds.size,
                                onSelectAll = {
                                    multiSelectionState.selectAll(state.albumSongs.map { LxSongMapper.toSong(it) })
                                },
                                onDeselect = { multiSelectionState.clearSelection() },
                                onOptionsClick = onSelectionOptionsClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FilledIconButton(
                                        onClick = { playAllPlaylist(state.albumSongs, album.name) },
                                        shape = CircleShape,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = stringResource(R.string.lx_desc_play_all),
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    mineSongItems(
                        songs = state.albumSongs,
                        loading = state.albumLoading,
                        error = null,
                        stablePlayerState = stablePlayerState,
                        playLxSong = playLxSong,
                        queueName = album.name,
                        onSongMoreOptions = onSongMoreOptions,
                        isSelectionMode = isSelectionMode,
                        selectedSongIds = selectedSongIds,
                        onSongLongPress = onSongLongPress,
                        onSongSelectionToggle = onSongSelectionToggle,
                        getSelectionIndex = getSelectionIndex,
                    )
                }
                if (showAlbumScrollBar) {
                    ExpressiveScrollBar(
                        listState = albumListState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(
                                top = albumMinHeaderDp + 12.dp,
                                end = 4.dp,
                                bottom = albumBottomPadding + 8.dp,
                            ),
                    )
                }
                LxCollapsingDetailHeader(
                    title = album.name,
                    subtitle = album.artistName?.takeIf { it.isNotBlank() }
                        ?.let { "$it · ${formatSongCount(state.albumSongs.size)}" }
                        ?: formatSongCount(state.albumSongs.size),
                    artworkUrl = album.pic,
                    collapseFraction = albumCollapseFraction,
                    headerHeightDp = albumHeaderHeightDp,
                    onBack = viewModel::backFromSearchArtistAlbum,
                    onShuffle = if (state.albumSongs.isNotEmpty()) {
                        { shuffleAllPlaylist(state.albumSongs, album.name) }
                    } else null,
                    shuffleContentDescription = stringResource(R.string.lx_desc_shuffle),
                    actions = {
                        Box(modifier = Modifier.padding(end = 12.dp, top = 4.dp)) {
                            LxFolderActionCapsule(
                                showLocate = false,
                                onLocate = {},
                                collectState = if (state.source == LxSources.KUGOU ||
                                    (state.source != LxSources.NETEASE &&
                                        !viewModel.writeEnabledFor(state.source, LxWriteOp.ALBUM_COLLECT))
                                ) null else
                                    rememberCollectState(viewModel, albumKind, album.id, name = album.name),
                                collectBusy = albumCollectBusyKeys.contains(
                                    viewModel.collectKey(albumKind, album.id),
                                ),
                                onCollectClick = {
                                    viewModel.onCollectButtonClick(albumKind, album.id, name = album.name)
                                },
                                onShare = { albumOnFolderShare(albumKind, album.id) },
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                if (state.albumSongs.isNotEmpty()) {
                    LxLocateCurrentSongFab(
                        listState = albumListState,
                        currentSongId = stablePlayerState.currentSong?.id,
                        songIds = albumSongIds,
                        leadingItems = 1,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 74.dp),
                    )
                    LxScrollToTopFab(
                        listState = albumListState,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(artist.id) { runCatching { listState.scrollToItem(0) } }
    val songIds = remember(state.songs) {
        state.songs.map { com.gurio.gusic.data.lxmusic.LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val artistHeaderState = rememberLxDetailHeaderState(listState)
    val artistDensity = LocalDensity.current
    val artistHeaderHeightDp = with(artistDensity) { artistHeaderState.height.value.toDp() }
    val artistMinHeaderDp = with(artistDensity) { artistHeaderState.minHeightPx.toDp() }
    val artistCollapseFraction by remember(artistHeaderState) {
        derivedStateOf {
            1f - (
                (artistHeaderState.height.value - artistHeaderState.minHeightPx) /
                    (artistHeaderState.maxHeightPx - artistHeaderState.minHeightPx)
                ).coerceIn(0f, 1f)
        }
    }
    val artistBottomPadding = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap
    val showArtistScrollBar by remember {
        derivedStateOf {
            artistCollapseFraction > 0.95f &&
                (listState.canScrollForward || listState.canScrollBackward)
        }
    }
    val artistSubtitle = if (state.tab == "songs") {
        formatSongCount(state.songsTotal.takeIf { it > 0 } ?: state.songs.size)
    } else null
    val artistHasBrief = !artist.briefDesc.isNullOrBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(artistHeaderState.connection),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            0,
                            (artistHeaderState.height.value - artistHeaderState.minHeightPx).roundToInt(),
                        )
                    }
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    top = artistMinHeaderDp + 8.dp,
                    start = 16.dp,
                    end = if (showArtistScrollBar) 24.dp else 16.dp,
                    bottom = artistBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "artist_tabs") {
                    LxSelectionSwitch(
                        inSelectionMode = isSelectionMode,
                        selectedCount = selectedSongIds.size,
                        onSelectAll = {
                            multiSelectionState.selectAll(state.songs.map { LxSongMapper.toSong(it) })
                        },
                        onDeselect = { multiSelectionState.clearSelection() },
                        onOptionsClick = onSelectionOptionsClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtistTabChip(stringResource(R.string.lx_tab_hot_songs), state.tab == "songs") {
                                viewModel.selectSearchArtistTab("songs")
                            }
                            Spacer(Modifier.width(8.dp))
                            ArtistTabChip(stringResource(R.string.lx_tab_all_albums), state.tab == "albums") {
                                viewModel.selectSearchArtistTab("albums")
                            }
                            if (state.tab == "songs" && state.songs.isNotEmpty()) {
                                Spacer(Modifier.weight(1f))
                                FilledIconButton(
                                    onClick = { playAllPlaylist(state.songs, artist.name) },
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.lx_desc_play_all),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (artistHasBrief) {
                    item(key = "artist_brief") {
                        var briefExpanded by remember(artist.id) { mutableStateOf(false) }
                        Text(
                            text = artist.briefDesc.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (briefExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { briefExpanded = !briefExpanded },
                        )
                    }
                }
                if (state.tab == "songs") {
                    mineSongItems(
                        songs = state.songs,
                        loading = state.loading,
                        error = state.error,
                        stablePlayerState = stablePlayerState,
                        playLxSong = playLxSong,
                        queueName = artist.name,
                        onSongMoreOptions = onSongMoreOptions,
                        isSelectionMode = isSelectionMode,
                        selectedSongIds = selectedSongIds,
                        onSongLongPress = onSongLongPress,
                        onSongSelectionToggle = onSongSelectionToggle,
                        getSelectionIndex = getSelectionIndex,
                    )
                    if (!state.loading && state.songs.isNotEmpty() &&
                        (state.songsHasMore || state.songsLoadingMore)
                    ) {
                        item(key = "search_artist_load_more") {
                            LxLoadMoreButton(
                                loading = state.songsLoadingMore,
                                onClick = viewModel::loadMoreSearchArtistSongs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                } else {
                    mineAlbumItems(
                        albums = state.albums,
                        onClick = viewModel::openSearchArtistAlbum,
                        loading = state.albumsLoading,
                    )
                    if (state.albumsLoading) item(key = "search_artist_albums_loading") { MineLoadingRow() }
                }
            }

            LxCollapsingDetailHeader(
                title = artist.name,
                subtitle = artistSubtitle,
                artworkUrl = artist.pic,
                collapseFraction = artistCollapseFraction,
                headerHeightDp = artistHeaderHeightDp,
                onBack = onBack,
                onShuffle = if (state.tab == "songs" && state.songs.isNotEmpty()) {
                    { shuffleAllPlaylist(state.songs, artist.name) }
                } else null,
                shuffleContentDescription = stringResource(R.string.lx_desc_shuffle),
                actions = {
                    if (showFollow && state.followed != null) {
                        Box(modifier = Modifier.padding(end = 12.dp, top = 4.dp)) {
                            ArtistFollowButton(
                                followed = state.followed == true,
                                busy = state.followBusy,
                                onClick = viewModel::toggleSearchArtistFollow,
                            )
                        }
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (showArtistScrollBar) {
                ExpressiveScrollBar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = artistMinHeaderDp + 12.dp,
                            end = 4.dp,
                            bottom = artistBottomPadding + 8.dp,
                        ),
                )
            }

            if (state.tab == "songs" && state.songs.isNotEmpty()) {
                LxLocateCurrentSongFab(
                    listState = listState,
                    currentSongId = stablePlayerState.currentSong?.id,
                    songIds = songIds,
                    leadingItems = if (artistHasBrief) 2 else 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 74.dp),
                )
                LxScrollToTopFab(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
                )
            }
        }
    }
}

@Composable
private fun LxSearchAlbumPage(
    state: LxMusicViewModel.SearchAlbumPageState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.gurio.gusic.data.lxmusic.LxSong, List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    playAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<com.gurio.gusic.data.lxmusic.LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    onBack: () -> Unit,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.gurio.gusic.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val album = state.album ?: return
    val listState = rememberLazyListState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val songIds = remember(state.songs) {
        state.songs.map { com.gurio.gusic.data.lxmusic.LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val collectKind = LxMusicViewModel.CollectKind.ALBUM
    LaunchedEffect(album.id) { viewModel.ensureCollectDataLoaded() }
    val collectBusyKeys by viewModel.collectBusyKeys.collectAsStateWithLifecycle()
    val onFolderShare = rememberFolderShare(viewModel)

    val albumHeaderState = rememberLxDetailHeaderState(listState)
    val albumDensity = LocalDensity.current
    val albumHeaderHeightDp = with(albumDensity) { albumHeaderState.height.value.toDp() }
    val albumMinHeaderDp = with(albumDensity) { albumHeaderState.minHeightPx.toDp() }
    val albumCollapseFraction by remember(albumHeaderState) {
        derivedStateOf {
            1f - (
                (albumHeaderState.height.value - albumHeaderState.minHeightPx) /
                    (albumHeaderState.maxHeightPx - albumHeaderState.minHeightPx)
                ).coerceIn(0f, 1f)
        }
    }
    val albumBottomPadding = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap
    val showAlbumScrollBar by remember {
        derivedStateOf {
            albumCollapseFraction > 0.95f &&
                (listState.canScrollForward || listState.canScrollBackward)
        }
    }
    val albumSubtitle = album.artistName?.takeIf { it.isNotBlank() }
        ?.let { "$it · ${formatSongCount(state.songs.size)}" }
        ?: formatSongCount(state.songs.size)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(albumHeaderState.connection),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            0,
                            (albumHeaderState.height.value - albumHeaderState.minHeightPx).roundToInt(),
                        )
                    }
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    top = albumMinHeaderDp + 8.dp,
                    start = 16.dp,
                    end = if (showAlbumScrollBar) 24.dp else 16.dp,
                    bottom = albumBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.songs.isNotEmpty()) {
                    item(key = "search_album_play_all") {
                        LxSelectionSwitch(
                            inSelectionMode = isSelectionMode,
                            selectedCount = selectedSongIds.size,
                            onSelectAll = {
                                multiSelectionState.selectAll(state.songs.map { LxSongMapper.toSong(it) })
                            },
                            onDeselect = { multiSelectionState.clearSelection() },
                            onOptionsClick = onSelectionOptionsClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledIconButton(
                                    onClick = { playAllPlaylist(state.songs, album.name) },
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.lx_desc_play_all),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                mineSongItems(
                    songs = state.songs,
                    loading = state.loading,
                    error = state.error,
                    stablePlayerState = stablePlayerState,
                    playLxSong = playLxSong,
                    queueName = album.name,
                    onSongMoreOptions = onSongMoreOptions,
                    isSelectionMode = isSelectionMode,
                    selectedSongIds = selectedSongIds,
                    onSongLongPress = onSongLongPress,
                    onSongSelectionToggle = onSongSelectionToggle,
                    getSelectionIndex = getSelectionIndex,
                )
            }
            LxCollapsingDetailHeader(
                title = album.name,
                subtitle = albumSubtitle,
                artworkUrl = album.pic,
                collapseFraction = albumCollapseFraction,
                headerHeightDp = albumHeaderHeightDp,
                onBack = onBack,
                onShuffle = if (state.songs.isNotEmpty()) {
                    { shuffleAllPlaylist(state.songs, album.name) }
                } else null,
                shuffleContentDescription = stringResource(R.string.lx_desc_shuffle),
                actions = {
                    Box(modifier = Modifier.padding(end = 12.dp, top = 4.dp)) {
                        LxFolderActionCapsule(
                            showLocate = false,
                            onLocate = {},
                            collectState = if (state.source == LxSources.KUGOU ||
                                (state.source != LxSources.NETEASE &&
                                    !viewModel.writeEnabledFor(state.source, LxWriteOp.ALBUM_COLLECT))
                            ) null else
                                rememberCollectState(viewModel, collectKind, album.id, name = album.name),
                            collectBusy = collectBusyKeys.contains(viewModel.collectKey(collectKind, album.id)),
                            onCollectClick = {
                                viewModel.onCollectButtonClick(collectKind, album.id, name = album.name)
                            },
                            onShare = { onFolderShare(collectKind, album.id) },
                        )
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (showAlbumScrollBar) {
                ExpressiveScrollBar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = albumMinHeaderDp + 12.dp,
                            end = 4.dp,
                            bottom = albumBottomPadding + 8.dp,
                        ),
                )
            }
            if (state.songs.isNotEmpty()) {
                LxLocateCurrentSongFab(
                    listState = listState,
                    currentSongId = stablePlayerState.currentSong?.id,
                    songIds = songIds,
                    leadingItems = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 74.dp),
                )
                LxScrollToTopFab(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
                )
            }
        }
    }
}


