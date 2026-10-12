package com.gurio.gusic.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.gurio.gusic.data.model.Song
import com.gurio.gusic.data.preferences.FullPlayerLoadingTweaks
import com.gurio.gusic.presentation.components.player.FullPlayerContent
import com.gurio.gusic.presentation.components.player.validateLyricsImport
import com.gurio.gusic.presentation.components.scoped.FullPlayerVisualState
import com.gurio.gusic.presentation.components.scoped.LyricsRevealController
import com.gurio.gusic.presentation.components.scoped.rememberFullPlayerRuntimePolicy
import com.gurio.gusic.presentation.components.scoped.rememberLyricsRevealController
import com.gurio.gusic.presentation.viewmodel.PlayerSheetState
import com.gurio.gusic.presentation.viewmodel.PlayerViewModel
import com.gurio.gusic.presentation.viewmodel.StablePlayerState
import com.gurio.gusic.utils.LyricsImportSecurity
import com.gurio.gusic.utils.LyricsImportValidationResult
import com.gurio.gusic.utils.ValidatedLyricsImport
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(UnstableApi::class)
@Composable
internal fun BoxScope.UnifiedPlayerMiniAndFullLayers(
    currentSong: Song?,
    miniPlayerScheme: ColorScheme?,
    overallSheetTopCornerRadiusProvider: () -> Dp,
    infrequentPlayerState: StablePlayerState,
    isOutputConnecting: Boolean,
    isPreparingPlayback: Boolean,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    albumColorScheme: ColorScheme,
    bottomSheetOpenFraction: Float,
    fullPlayerVisualState: FullPlayerVisualState,
    containerHeight: Dp,
    currentQueueSourceName: String,
    currentSheetContentState: PlayerSheetState,
    carouselStyle: String,
    fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean = false,
    playerViewModel: PlayerViewModel,
    currentPositionProvider: () -> Long,
    isFavorite: Boolean,
    shouldRenderFullPlayer: Boolean = true,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit
) {
    currentSong?.let { currentSongNonNull ->
        miniPlayerScheme?.let { readyScheme ->
            CompositionLocalProvider(
                LocalMaterialTheme provides readyScheme
            ) {
                val miniPlayerZIndex by remember {
                    derivedStateOf {
                        if (playerContentExpansionFraction.value < 0.5f) 1f else 0f
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(MiniPlayerHeight)
                        .graphicsLayer {
                            alpha = (1f - playerContentExpansionFraction.value * 2f)
                                .coerceIn(0f, 1f)
                        }
                        .zIndex(miniPlayerZIndex)
                ) {
                    val isMiniPlayerVisible by remember {
                        derivedStateOf { playerContentExpansionFraction.value < 0.01f }
                    }
                    val miniPlaybackPosition by playerViewModel.currentPlaybackPosition.collectAsStateWithLifecycle()
                    val miniLyrics = infrequentPlayerState.lyrics
                    val miniPlayerLyricsOn by playerViewModel.miniPlayerLyricsEnabled.collectAsStateWithLifecycle()
                    val miniCurrentLine = remember(miniLyrics, miniPlaybackPosition, miniPlayerLyricsOn) {
                        if (!miniPlayerLyricsOn) null
                        else miniLyrics?.synced?.let { lines ->
                            val idx = resolveCurrentLineIndex(lines, miniPlaybackPosition.coerceAtLeast(0L))
                            lines.getOrNull(idx)?.line?.takeIf { it.isNotBlank() }
                        }
                    }
                    MiniPlayerContentInternal(
                        song = currentSongNonNull,
                        isPlaying = infrequentPlayerState.isPlaying,
                        isOutputConnecting = isOutputConnecting,
                        isPreparingPlayback = isPreparingPlayback,
                        isBuffering = infrequentPlayerState.isBuffering,
                        onPlayPause = { playerViewModel.playPause() },
                        onPrevious = { playerViewModel.previousSong() },
                        onNext = { playerViewModel.nextSong() },
                        currentLyricLine = miniCurrentLine,
                        canScroll = isMiniPlayerVisible && infrequentPlayerState.isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (shouldRenderFullPlayer) {
            CompositionLocalProvider(
                LocalMaterialTheme provides albumColorScheme
            ) {
                val fullPlayerScale by remember(bottomSheetOpenFraction) {
                    derivedStateOf { lerp(1f, 0.972f, bottomSheetOpenFraction) }
                }

                val fullPlayerZIndex by remember {
                    derivedStateOf {
                        if (playerContentExpansionFraction.value >= 0.5f) 1f else 0f
                    }
                }
                val fullPlayerOffset by remember {
                    derivedStateOf {
                        if (playerContentExpansionFraction.value <= 0.01f) IntOffset(0, 10000)
                        else IntOffset.Zero
                    }
                }
                val fullPlayerRuntimePolicy = rememberFullPlayerRuntimePolicy(
                    currentSheetState = currentSheetContentState,
                    expansionFraction = playerContentExpansionFraction,
                    bottomSheetOpenFraction = bottomSheetOpenFraction
                )

                val currentPlaybackQueue by playerViewModel.queueFlow
                    .collectAsStateWithLifecycle()

                val latestInfrequentPlayerState = rememberUpdatedState(infrequentPlayerState)
                val latestIsFavorite = rememberUpdatedState(isFavorite)
                // 大爱心已专用于本软件收藏（本地+在线均可写），不再随平台登录态弱化
                val favoriteEnabledProvider = remember {
                    { true }
                }
                val expansionFractionProvider = remember(playerContentExpansionFraction) {
                    { playerContentExpansionFraction.value }
                }
                val isPlayingProvider = remember {
                    { latestInfrequentPlayerState.value.isPlaying }
                }
                val playWhenReadyProvider = remember {
                    { latestInfrequentPlayerState.value.playWhenReady }
                }
                val repeatModeProvider = remember {
                    { latestInfrequentPlayerState.value.repeatMode }
                }
                val isShuffleEnabledProvider = remember {
                    { latestInfrequentPlayerState.value.isShuffleEnabled }
                }
                val totalDurationProvider = remember {
                    { latestInfrequentPlayerState.value.totalDuration }
                }
                val lyricsProvider = remember {
                    { latestInfrequentPlayerState.value.lyrics }
                }
                val isFavoriteProvider = remember {
                    { latestIsFavorite.value }
                }
                val onPlayPause = remember(playerViewModel) { playerViewModel::playPause }
                val onSeek = remember(playerViewModel) { playerViewModel::seekTo }
                val onNext = remember(playerViewModel) { playerViewModel::nextSong }
                val onPrevious = remember(playerViewModel) { playerViewModel::previousSong }
                val onCollapse = remember(playerViewModel) {
                    { playerViewModel.collapsePlayerSheet() }
                }
                val onShuffleToggle = remember(playerViewModel) {
                    { playerViewModel.toggleShuffle() }
                }
                val onFavoriteToggle = remember(playerViewModel) { playerViewModel::toggleLocalFavorite }

                // ---- 播放页 <-> 歌词页 转场管线 ----
                // 歌词层在「有歌词（同步或静态）且窗口开关开启」时挂载；
                // 窗口锚点/字号仅在同步歌词场景传入（静态歌词走纯 alpha 渐显，无窗口形变）。
                val lyricsRevealController = rememberLyricsRevealController(playerViewModel)
                val inPlayerLyricsOn by playerViewModel.inPlayerLyricsEnabled.collectAsStateWithLifecycle()
                val hasSyncedLyrics = !infrequentPlayerState.lyrics?.synced.isNullOrEmpty()
                val hasPlainLyrics = !infrequentPlayerState.lyrics?.plain.isNullOrEmpty()
                val lyricsLayerActive = (hasSyncedLyrics || hasPlainLyrics) && inPlayerLyricsOn

                // 歌词层失效（切到无词的歌 / 关闭窗口开关）时快速归零（约 100ms），
                // 避免 sections 属性瞬时消失造成可见跳变；否则播放页会停留在 reveal=1 的全透明态
                LaunchedEffect(lyricsLayerActive) {
                    if (!lyricsLayerActive && playerViewModel.lyricsRevealFraction.value > 0f) {
                        playerViewModel.lyricsRevealFraction.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
                        )
                    }
                }
                // 播放页折叠回 mini 时歌词层必然不可见，快速归零复位
                LaunchedEffect(playerViewModel, playerContentExpansionFraction) {
                    snapshotFlow { playerContentExpansionFraction.value }.collect { fraction ->
                        if (fraction <= 0.01f && playerViewModel.lyricsRevealFraction.value > 0f) {
                            playerViewModel.lyricsRevealFraction.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }

                // reveal>=0.999 且歌词层在挂载：播放页 UI 整体移出组合（不可见也不可交互），
                // 让底层歌词页完整接管触摸（行点击 seek / 滑动 / 返回键）
                val hidePlayerUiForLyrics by remember {
                    derivedStateOf {
                        playerViewModel.lyricsRevealFraction.value >= 0.999f
                    }
                }

                // 播放页"窗口"（InPlayerLyrics）当前行锚点，相对本 full-player Box 顶层。
                // 窗口与本层共享同一套祖先变换（offset/scale），做差后即得层内坐标。
                var fullBoxTopInRoot by remember { mutableFloatStateOf(0f) }
                var lyricsWindowBounds by remember { mutableStateOf<Rect?>(null) }
                val lyricsWindowAnchorYProvider = remember {
                    {
                        lyricsWindowBounds?.let { bounds ->
                            bounds.top + bounds.height * 0.25f - fullBoxTopInRoot
                        } ?: Float.NaN
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(containerHeight)
                        .graphicsLayer {
                            alpha = fullPlayerVisualState.contentAlpha
                            translationY = fullPlayerVisualState.translationY
                            scaleX = fullPlayerScale
                            scaleY = fullPlayerScale
                        }
                        .zIndex(fullPlayerZIndex)
                        .offset { fullPlayerOffset }
                        .onGloballyPositioned { fullBoxTopInRoot = it.positionInRoot().y }
                ) {
                    if (lyricsLayerActive) {
                        LyricsSheetHost(
                            playerViewModel = playerViewModel,
                            revealFraction = playerViewModel.lyricsRevealFraction,
                            revealController = lyricsRevealController,
                            // 窗口几何（锚点/字号）仅在同步歌词场景传入；静态歌词传 NaN/0，
                            // LyricsSheet 侧形变自动退化为恒等（纯 alpha 渐显）
                            provideWindowGeometry = hasSyncedLyrics,
                            windowAnchorYProvider = lyricsWindowAnchorYProvider,
                            onSeekTo = onSeek,
                            onNext = onNext,
                            onPrev = onPrevious
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
                        FullPlayerContent(
                            currentSong = currentSongNonNull,
                            currentPlaybackQueue = currentPlaybackQueue,
                            currentQueueSourceName = currentQueueSourceName,
                            currentMediaItemIndex = infrequentPlayerState.currentMediaItemIndex,
                            repeatMode = infrequentPlayerState.repeatMode,
                            allowRealtimeUpdates = fullPlayerRuntimePolicy.allowRealtimeUpdates,
                            expansionFractionProvider = expansionFractionProvider,
                            currentSheetState = currentSheetContentState,
                            carouselStyle = carouselStyle,
                            loadingTweaks = fullPlayerLoadingTweaks,
                            isSheetDragGestureActive = isSheetDragGestureActive,
                            playerViewModel = playerViewModel,
                            currentPositionProvider = currentPositionProvider,
                            isPlayingProvider = isPlayingProvider,
                            playWhenReadyProvider = playWhenReadyProvider,
                            repeatModeProvider = repeatModeProvider,
                            isShuffleEnabledProvider = isShuffleEnabledProvider,
                            totalDurationProvider = totalDurationProvider,
                            lyricsProvider = lyricsProvider,
                            isOutputConnecting = isOutputConnecting,
                            isFavoriteProvider = isFavoriteProvider,
                            favoriteEnabledProvider = favoriteEnabledProvider,
                            onPlayPause = onPlayPause,
                            onSeek = onSeek,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onCollapse = onCollapse,
                            onShowQueueClicked = onShowQueueClicked,
                            onQueueDragStart = onQueueDragStart,
                            onQueueDrag = onQueueDrag,
                            onQueueRelease = onQueueRelease,
                            onShuffleToggle = onShuffleToggle,
                            onFavoriteToggle = onFavoriteToggle,
                            lyricsRevealFractionProvider = { playerViewModel.lyricsRevealFraction.value },
                            hidePlayerUiForLyrics = hidePlayerUiForLyrics && lyricsLayerActive,
                            onLyricsWindowBoundsChange = { lyricsWindowBounds = it },
                            lyricsRevealController = lyricsRevealController
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun UnifiedPlayerPrewarmLayer(
    prewarmFullPlayer: Boolean,
    currentSong: Song?,
    containerHeight: Dp,
    albumColorScheme: ColorScheme,
    currentQueueSourceName: String,
    infrequentPlayerState: StablePlayerState,
    carouselStyle: String,
    fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
    playerViewModel: PlayerViewModel,
    currentPositionProvider: () -> Long,
    isOutputConnecting: Boolean,
    isFavorite: Boolean,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit
) {
    if (prewarmFullPlayer && currentSong != null) {
        val currentPlaybackQueue by playerViewModel.queueFlow
            .collectAsStateWithLifecycle()
        CompositionLocalProvider(
            LocalMaterialTheme provides albumColorScheme
        ) {
            Box(
                modifier = Modifier
                    .height(containerHeight)
                    .fillMaxWidth()
                    .alpha(0f)
                    .clipToBounds()
            ) {
                val latestInfrequentPlayerState = rememberUpdatedState(infrequentPlayerState)
                val latestIsFavorite = rememberUpdatedState(isFavorite)
                val isPlayingProvider = remember { { latestInfrequentPlayerState.value.isPlaying } }
                val playWhenReadyProvider = remember { { latestInfrequentPlayerState.value.playWhenReady } }
                val repeatModeProvider = remember { { latestInfrequentPlayerState.value.repeatMode } }
                val isShuffleEnabledProvider = remember { { latestInfrequentPlayerState.value.isShuffleEnabled } }
                val totalDurationProvider = remember { { latestInfrequentPlayerState.value.totalDuration } }
                val lyricsProvider = remember { { latestInfrequentPlayerState.value.lyrics } }
                val isFavoriteProvider = remember { { latestIsFavorite.value } }
                val onPlayPause = remember(playerViewModel) { playerViewModel::playPause }
                val onSeek = remember(playerViewModel) { playerViewModel::seekTo }
                val onNext = remember(playerViewModel) { playerViewModel::nextSong }
                val onPrevious = remember(playerViewModel) { playerViewModel::previousSong }
                val onShuffleToggle = remember(playerViewModel) { { playerViewModel.toggleShuffle() } }
                val onFavoriteToggle = remember(playerViewModel) { playerViewModel::toggleLocalFavorite }

                FullPlayerContent(
                    currentSong = currentSong,
                    currentPlaybackQueue = currentPlaybackQueue,
                    currentQueueSourceName = currentQueueSourceName,
                    currentMediaItemIndex = infrequentPlayerState.currentMediaItemIndex,
                    repeatMode = infrequentPlayerState.repeatMode,
                    allowRealtimeUpdates = false,
                    expansionFractionProvider = { 1f },
                    currentSheetState = PlayerSheetState.EXPANDED,
                    carouselStyle = carouselStyle,
                    loadingTweaks = fullPlayerLoadingTweaks,
                    playerViewModel = playerViewModel,
                    currentPositionProvider = currentPositionProvider,
                    isPlayingProvider = isPlayingProvider,
                    playWhenReadyProvider = playWhenReadyProvider,
                    repeatModeProvider = repeatModeProvider,
                    isShuffleEnabledProvider = isShuffleEnabledProvider,
                    totalDurationProvider = totalDurationProvider,
                    lyricsProvider = lyricsProvider,
                    isOutputConnecting = isOutputConnecting,
                    isFavoriteProvider = isFavoriteProvider,
                    onShowQueueClicked = onShowQueueClicked,
                    onQueueDragStart = onQueueDragStart,
                    onQueueDrag = onQueueDrag,
                    onQueueRelease = onQueueRelease,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onCollapse = {},
                    onShuffleToggle = onShuffleToggle,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }
    }
}

/**
 * 歌词页常驻底层宿主：与 FullPlayerContent 同挂载条件（有同步歌词 + 窗口开关开启），
 * 位于 full-player Box 的 zIndex(0f)，被 zIndex(1f) 的播放页覆盖。
 * 显隐/形变/手势门控全部由 lyricsRevealFraction 驱动，见 LyricsSheet。
 */
@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.LyricsSheetHost(
    playerViewModel: PlayerViewModel,
    revealFraction: Animatable<Float, AnimationVector1D>,
    revealController: LyricsRevealController,
    provideWindowGeometry: Boolean,
    windowAnchorYProvider: () -> Float,
    onSeekTo: (Long) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val context = LocalContext.current
    val fileImportScope = rememberCoroutineScope()
    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsStateWithLifecycle()
    val fullPlayerSlice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
    val immersiveLyricsEnabled by playerViewModel.immersiveLyricsEnabled.collectAsStateWithLifecycle()
    val immersiveLyricsTimeout by playerViewModel.immersiveLyricsTimeout.collectAsStateWithLifecycle()
    val isImmersiveTemporarilyDisabled by playerViewModel.isImmersiveTemporarilyDisabled.collectAsStateWithLifecycle()
    val inPlayerLyricsFontSize by playerViewModel.inPlayerLyricsFontSize.collectAsStateWithLifecycle()
    val isRevealed by remember(revealFraction) {
        derivedStateOf { revealFraction.value > 0.995f }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                fileImportScope.launch {
                    try {
                        val validation = validateLyricsImport(context, it)
                        val validatedImport: ValidatedLyricsImport = when (validation) {
                            is LyricsImportValidationResult.Valid -> validation.value
                            is LyricsImportValidationResult.Invalid -> {
                                playerViewModel.sendToast(
                                    LyricsImportSecurity.messageFor(validation.reason)
                                )
                                return@launch
                            }
                        }

                        val currentSongId = playerViewModel.stablePlayerState.value.currentSong
                            ?.id?.toLongOrNull()
                        if (currentSongId == null) {
                            playerViewModel.sendToast("No song selected for lyrics import.")
                            return@launch
                        }

                        playerViewModel.importLyricsFromFile(currentSongId, validatedImport)
                        revealController.expand()
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading imported lyrics file")
                        playerViewModel.sendToast("Error reading file.")
                    }
                }
            }
        }
    )

    LyricsSheet(
        stablePlayerStateFlow = playerViewModel.stablePlayerState,
        playbackPositionFlow = playerViewModel.currentPlaybackPosition,
        lyricsSearchUiState = lyricsSearchUiState,
        resetLyricsForCurrentSong = { playerViewModel.resetLyricsForCurrentSong() },
        onSearchLyrics = { forcePick -> playerViewModel.fetchLyricsForCurrentSong(forcePick) },
        onPickResult = { playerViewModel.acceptLyricsSearchResultForCurrentSong(it) },
        onManualSearch = { title, artist -> playerViewModel.searchLyricsManually(title, artist) },
        onImportLyrics = {
            filePickerLauncher.launch(LyricsImportSecurity.pickerMimeTypes())
        },
        onDismissLyricsSearch = { playerViewModel.resetLyricsSearchState() },
        lyricsSyncOffset = fullPlayerSlice.lyricsSyncOffset,
        onLyricsSyncOffsetChange = { offset ->
            playerViewModel.stablePlayerState.value.currentSong?.id?.let { songId ->
                playerViewModel.setLyricsSyncOffset(songId, offset)
            }
        },
        lyricsTextStyle = MaterialTheme.typography.titleLarge,
        colorScheme = LocalMaterialTheme.current,
        onBackClick = revealController::collapse,
        onSaveLyricsToFile = playerViewModel::saveLyricsToFile,
        onSeekTo = onSeekTo,
        onPlayPause = { playerViewModel.playPause() },
        onNext = onNext,
        onPrev = onPrev,
        immersiveLyricsEnabled = immersiveLyricsEnabled,
        immersiveLyricsTimeout = immersiveLyricsTimeout,
        isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
        onSetImmersiveTemporarilyDisabled = { playerViewModel.setImmersiveTemporarilyDisabled(it) },
        revealFractionProvider = { revealFraction.value },
        isRevealed = isRevealed,
        // 窗口字号/锚点仅在同步歌词场景提供：0f/NaN 时 LyricsSheet 的列表形变自动恒等
        windowFontSp = if (provideWindowGeometry) inPlayerLyricsFontSize.toFloat() else 0f,
        windowAnchorYInSheetProvider = if (provideWindowGeometry) {
            windowAnchorYProvider
        } else {
            { Float.NaN }
        },
        modifier = Modifier.matchParentSize()
    )
}
