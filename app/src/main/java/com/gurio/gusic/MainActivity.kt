package com.gurio.gusic

import com.gurio.gusic.presentation.navigation.navigateSafely
import com.gurio.gusic.presentation.navigation.navigateToTopLevelSafely

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.res.Configuration
import android.os.LocaleList
import android.os.Bundle
import android.view.WindowManager
import com.gurio.gusic.utils.traceSection
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.gurio.gusic.data.preferences.AppThemeMode
import com.gurio.gusic.data.preferences.NavBarStyle
import com.gurio.gusic.data.preferences.sanitizeNavBarCornerRadius
import com.gurio.gusic.data.preferences.ThemePreferencesRepository
import com.gurio.gusic.data.preferences.UserPreferencesRepository
import com.gurio.gusic.data.service.MusicService
import com.gurio.gusic.presentation.components.AppSidebarDrawer
import com.gurio.gusic.presentation.components.CrashReportDialog
import com.gurio.gusic.presentation.components.DismissUndoBar
import com.gurio.gusic.presentation.components.DrawerDestination
import com.gurio.gusic.presentation.components.MiniPlayerBottomSpacer
import com.gurio.gusic.presentation.components.MiniPlayerHeight
import com.gurio.gusic.presentation.components.PlayerInternalNavigationBar
import com.gurio.gusic.presentation.components.PlayerNavigationRail
import com.gurio.gusic.presentation.components.UnifiedPlayerSheetV2
import com.gurio.gusic.presentation.components.calculatePlayerSheetCollapsedTargetY
import com.gurio.gusic.presentation.components.resolveNavBarOccupiedHeight
import com.gurio.gusic.presentation.components.resolveNavBarSurfaceHeight
import com.gurio.gusic.presentation.components.sanitizeNavigationBarBottomInset
import com.gurio.gusic.presentation.navigation.AppNavigation
import com.gurio.gusic.presentation.navigation.mainRootTabSpecs
import com.gurio.gusic.presentation.navigation.Screen
import com.gurio.gusic.presentation.screens.SetupScreen
import com.gurio.gusic.presentation.viewmodel.MainViewModel
import com.gurio.gusic.presentation.viewmodel.PlayerViewModel
import com.gurio.gusic.ui.theme.PixelPlayerTheme
import com.gurio.gusic.ui.theme.resolveAppWideNowPlayingColorSchemePair
import com.gurio.gusic.utils.AppLocaleManager
import com.gurio.gusic.utils.CrashHandler
import com.gurio.gusic.utils.LogUtils
import com.gurio.gusic.utils.shouldKeepScreenAwake
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.gurio.gusic.presentation.utils.AppHapticsConfig
import com.gurio.gusic.presentation.utils.LocalAppHapticsConfig
import com.gurio.gusic.presentation.utils.NoOpHapticFeedback
import com.gurio.gusic.utils.CrashLogData
import javax.annotation.concurrent.Immutable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map


internal fun shouldShowStartupSetup(
    isSetupComplete: Boolean?,
    isBenchmarkMode: Boolean,
): Boolean? = when {
    isBenchmarkMode -> false
    isSetupComplete == null -> null
    else -> !isSetupComplete
}

@Immutable
data class BottomNavItem(
    @androidx.annotation.StringRes val labelResId: Int,
    @DrawableRes val iconResId: Int,
    @DrawableRes val selectedIconResId: Int? = null,
    val screen: Screen
)

private data class DismissUndoBarSlice(
    val isVisible: Boolean = false,
    val durationMillis: Long = 4000L
)

@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private var isUIVisiblyReady = false
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    private val _pendingPlaylistNavigation = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _pendingShuffleAll = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val requestAllFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
    }

    @CallSuper
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        val languageTag = AppLocaleManager.currentLanguageTag(this)
        if (languageTag.isNotBlank() && overrideConfiguration != null) {
            val locale = AppLocaleManager.localeForTag(languageTag)
            overrideConfiguration.setLocale(locale)
            overrideConfiguration.setLayoutDirection(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                overrideConfiguration.setLocales(LocaleList(locale))
            }
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogUtils.d(this, "onCreate")
        val splashScreen = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    combine(
                        userPreferencesRepository.keepScreenAwakeWhilePlayingFlow,
                        playerViewModel.stablePlayerState
                            .map { state -> state.isPlaying }
                            .distinctUntilChanged(),
                        ::shouldKeepScreenAwake,
                    ).distinctUntilChanged().collect { keepAwake ->
                        if (keepAwake) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                } finally {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        splashScreen.setKeepOnScreenCondition { false }

        val isBenchmarkMode = intent.getBooleanExtra("is_benchmark", false)
        val shouldBenchmarkRebuildDatabase =
            isBenchmarkMode && intent.getBooleanExtra("benchmark_rebuild_database", false)
        Timber.tag("PixelPlayerBenchmark").i(
            "onCreate benchmark=$isBenchmarkMode rebuildDatabase=$shouldBenchmarkRebuildDatabase"
        )
        if (shouldBenchmarkRebuildDatabase) {
            lifecycleScope.launch {
                userPreferencesRepository.setInitialSetupDone(true)
                Timber.tag("PixelPlayerBenchmark").i("Preparing benchmark player from the existing Room library")
                playerViewModel.prepareBenchmarkPlayerFromLibrary()
            }
        }

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            val appThemeMode by themePreferencesRepository.appThemeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.FOLLOW_SYSTEM)
            val useDarkTheme = when (appThemeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                else -> systemDarkTheme
            }
            val globalNowPlayingThemeEnabled by themePreferencesRepository
                .globalNowPlayingThemeEnabledFlow
                .collectAsStateWithLifecycle(initialValue = false)
            val activePlayerColorSchemePair by playerViewModel
                .activePlayerColorSchemePair
                .collectAsStateWithLifecycle()
            val themedAlbumArtUri by playerViewModel
                .currentThemedAlbumArtUri
                .collectAsStateWithLifecycle()
            val stablePlayerState by playerViewModel
                .stablePlayerState
                .collectAsStateWithLifecycle()
            val currentSongId = stablePlayerState.currentSong?.id
            val currentSongArtworkUri = stablePlayerState.currentSong?.albumArtUriString
            val currentSongScheme = activePlayerColorSchemePair.takeIf {
                !currentSongArtworkUri.isNullOrBlank() && currentSongArtworkUri == themedAlbumArtUri
            }
            val awaitingSongScheme = !currentSongArtworkUri.isNullOrBlank() && currentSongScheme == null
            var lastValidNowPlayingSongId by remember { mutableStateOf<String?>(null) }
            var lastValidNowPlayingScheme by remember {
                mutableStateOf<com.gurio.gusic.presentation.viewmodel.ColorSchemePair?>(null)
            }
            LaunchedEffect(currentSongId, currentSongScheme) {
                when {
                    currentSongId == null -> {
                        lastValidNowPlayingSongId = null
                        lastValidNowPlayingScheme = null
                    }
                    currentSongScheme != null -> {
                        lastValidNowPlayingSongId = currentSongId
                        lastValidNowPlayingScheme = currentSongScheme
                    }
                }
            }
            val appWideNowPlayingScheme = resolveAppWideNowPlayingColorSchemePair(
                enabled = globalNowPlayingThemeEnabled,
                currentSongId = currentSongId,
                isPlaying = stablePlayerState.isPlaying,
                currentSongScheme = currentSongScheme,
                awaitingScheme = awaitingSongScheme,
                lastValidSongId = lastValidNowPlayingSongId,
                lastValidScheme = lastValidNowPlayingScheme
            )
            val isSetupComplete by mainViewModel.isSetupComplete.collectAsStateWithLifecycle()
            
            var showCrashReportDialog by remember { mutableStateOf(false) }
            var crashLogData by remember { mutableStateOf<CrashLogData?>(null) }
            
            val showSetupScreen = remember(isSetupComplete, isBenchmarkMode) {
                shouldShowStartupSetup(
                    isSetupComplete = isSetupComplete,
                    isBenchmarkMode = isBenchmarkMode
                )
            }

            LaunchedEffect(Unit) {
                if (!isBenchmarkMode && CrashHandler.hasCrashLog()) {
                    crashLogData = CrashHandler.getCrashLog()
                    showCrashReportDialog = true
                }
            }

            PixelPlayerTheme(
                darkTheme = useDarkTheme,
                colorSchemePairOverride = appWideNowPlayingScheme
            ) {
                var contentVisible by remember { mutableStateOf(false) }
                val contentAlpha by animateFloatAsState(
                    targetValue = if (contentVisible) 1f else 0f,
                    animationSpec = tween(600, easing = LinearOutSlowInEasing),
                    label = "AppContentAlpha"
                )

                LaunchedEffect(Unit) {
                    delay(100)
                    contentVisible = true
                }

                Surface(
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }, 
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSetupScreen == null) {
                        SetupGateLoadingScreen()
                    } else {
                        AnimatedContent(
                            targetState = showSetupScreen,
                            transitionSpec = {
                                if (targetState) {
                                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                                } else {
                                    scaleIn(initialScale = 0.95f, animationSpec = tween(450)) + fadeIn(animationSpec = tween(450)) togetherWith
                                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(450)) + fadeOut(animationSpec = tween(450))
                                }
                            },
                            label = "SetupTransition"
                        ) { shouldShowSetup ->
                            if (shouldShowSetup) {
                                SetupScreen(onSetupComplete = {
                                })
                            } else {
                                MainAppContent(playerViewModel, mainViewModel)
                            }
                        }
                    }

                    if (showCrashReportDialog && crashLogData != null) {
                        CrashReportDialog(
                            crashLog = crashLogData!!,
                            onDismiss = {
                                CrashHandler.clearCrashLog()
                                crashLogData = null
                                showCrashReportDialog = false
                            }
                        )
                    }
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when {
            intent.action == MainActivityIntentContract.ACTION_SHUFFLE_ALL -> {
                Timber.tag("TileDebug").d("handleIntent: ACTION_SHUFFLE_ALL received")
                playerViewModel.triggerShuffleAllFromTile()
                intent.action = null
            }
            
            intent.action == MainActivityIntentContract.ACTION_OPEN_PLAYLIST -> {
                intent.getStringExtra(MainActivityIntentContract.EXTRA_PLAYLIST_ID)?.let { playlistId ->
                    _pendingPlaylistNavigation.value = playlistId
                }
                intent.action = null
            }

            intent.getBooleanExtra("ACTION_SHOW_PLAYER", false) -> {
                playerViewModel.showPlayer()
            }

            intent.action == android.content.Intent.ACTION_VIEW && intent.data != null -> {
                intent.data?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }

            intent.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true -> {
                resolveStreamUri(intent)?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }
            
            intent.action == "com.gurio.gusic.ACTION_PLAY_SONG" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     intent.getParcelableExtra("song", com.gurio.gusic.data.model.Song::class.java)?.let { song ->
                         playerViewModel.playSong(song)
                     }
                } else {
                     @Suppress("DEPRECATION")
                     intent.getParcelableExtra<com.gurio.gusic.data.model.Song>("song")?.let { song ->
                         playerViewModel.playSong(song)
                     }
                }
                intent.action = null
            }
        }
    }
    
    private fun resolveStreamUri(intent: Intent): android.net.Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let { return it }
        } else {
            @Suppress("DEPRECATION")
            val legacyUri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (legacyUri != null) return legacyUri
        }

        intent.clipData?.let { clipData ->
            if (clipData.itemCount > 0) {
                return clipData.getItemAt(0).uri
            }
        }

        return intent.data
    }

    private fun persistUriPermissionIfNeeded(intent: Intent, uri: android.net.Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val hasPersistablePermission = intent.flags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            if (hasPersistablePermission) {
                val takeFlags = intent.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (takeFlags != 0) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (securityException: SecurityException) {
                        Timber.tag("MainActivity").w(securityException, "Unable to persist URI permission for $uri")
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        Timber.tag("MainActivity").w(illegalArgumentException, "Persistable URI permission not granted for $uri")
                    }
                }
            }
        }
    }

    private fun clearExternalIntentPayload(intent: Intent) {
        intent.data = null
        intent.clipData = null
        intent.removeExtra(android.content.Intent.EXTRA_STREAM)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun SetupGateLoadingScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularWavyProgressIndicator()
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Preparing setup…",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainAppContent(
        playerViewModel: PlayerViewModel,
        mainViewModel: MainViewModel
    ) = traceSection("MainActivity.MainAppContent") {
        val navController = rememberNavController()
        val isMediaControllerReady by playerViewModel.isMediaControllerReady.collectAsStateWithLifecycle()
        
        val pendingPlaylistNav by _pendingPlaylistNavigation.collectAsStateWithLifecycle()
        var processedPlaylistId by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(pendingPlaylistNav, isMediaControllerReady) {
            val playlistId = pendingPlaylistNav
            if (playlistId != null && playlistId != processedPlaylistId && isMediaControllerReady) {
                processedPlaylistId = playlistId
                var success = false
                var attempts = 0
                while (!success && attempts < 50) {
                    try {
                        success = navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlistId))
                        if (success) {
                            _pendingPlaylistNavigation.value = null
                        } else {
                            delay(100)
                            attempts++
                        }
                    } catch (e: IllegalArgumentException) {
                        delay(100)
                        attempts++
                    }
                }
            } else if (playlistId == null) {
                processedPlaylistId = null
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainUI(playerViewModel, navController)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainUI(
        playerViewModel: PlayerViewModel,
        navController: NavHostController
    ) = traceSection("MainActivity.MainUI") {
        val showSearchTab by playerViewModel.showSearchTab.collectAsStateWithLifecycle()
        val commonNavItems = remember(showSearchTab) {
            val base = mainRootTabSpecs.fold(persistentListOf<BottomNavItem>()) { items, spec ->
                items.adding(BottomNavItem(spec.labelResId, spec.iconResId, spec.selectedIconResId, spec.screen))
            }
            if (showSearchTab) {
                base.adding(BottomNavItem(R.string.tab_search, R.drawable.rounded_search_24, R.drawable.rounded_search_24, Screen.Search))
            } else {
                base
            }
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        var isSearchBarActive by remember { mutableStateOf(false) }
        LaunchedEffect(showSearchTab, currentRoute) {
            if (!showSearchTab && currentRoute == Screen.Search.route) {
                navController.navigateToTopLevelSafely(Screen.Home.route)
            }
        }
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val useNavigationRail =
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

        val routesWithHiddenNavigationBar = remember {
            setOf(
                Screen.Settings.route,
                Screen.Accounts.route,
                Screen.CloudDownloads.route,
                Screen.PlaylistDetail.route,
                Screen.DailyMixScreen.route,
                Screen.RecentlyPlayed.route,
                Screen.GenreDetail.route,
                Screen.AlbumDetail.route,
                Screen.ArtistDetail.route,
                Screen.DJSpace.route,
                Screen.NavBarCrRad.route,
                Screen.About.route,
                Screen.Stats.route,
                Screen.EditTransition.route,
                Screen.Experimental.route,
                Screen.ArtistSettings.route,
                Screen.Equalizer.route,
                Screen.SettingsCategory.route,
                Screen.DelimiterConfig.route,
                Screen.PaletteStyle.route,
                Screen.RecentlyPlayed.route,
                Screen.DeviceCapabilities.route,
                Screen.EasterEgg.route,
                Screen.WordDelimiterConfig.route,
                Screen.AudioBookmarks.route,
                Screen.AudioBookmarkFolder.route
            )
        }
        val shouldHideNavigationBar by remember(currentRoute, isSearchBarActive) {
            derivedStateOf {
                if (currentRoute == Screen.Search.route && isSearchBarActive) {
                    true
                } else {
                    currentRoute?.let { route ->
                        routesWithHiddenNavigationBar.any { hiddenRoute ->
                            if (hiddenRoute.contains("{")) {
                                route.startsWith(hiddenRoute.substringBefore("{"))
                            } else {
                                route == hiddenRoute
                            }
                        }
                    } ?: false
                }
            }
        }

        val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
        val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
        val navBarCornerRadiusRaw by playerViewModel.navBarCornerRadius.collectAsStateWithLifecycle()
        val navBarCornerRadius = sanitizeNavBarCornerRadius(navBarCornerRadiusRaw)
        val useSmoothCorners by playerViewModel.useSmoothCorners.collectAsStateWithLifecycle()
        val isMiniPlayerDismissing by playerViewModel.isMiniPlayerDismissing.collectAsStateWithLifecycle()
        val hapticsEnabled by playerViewModel.hapticsEnabled.collectAsStateWithLifecycle()
        val rootView = LocalView.current
        val platformHapticFeedback = LocalHapticFeedback.current
        val appHapticsConfig = remember(hapticsEnabled) {
            AppHapticsConfig(enabled = hapticsEnabled)
        }
        val scopedHapticFeedback = remember(platformHapticFeedback, appHapticsConfig.enabled) {
            if (appHapticsConfig.enabled) platformHapticFeedback else NoOpHapticFeedback
        }

        val systemNavBarInset = sanitizeNavigationBarBottomInset(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )

        LaunchedEffect(hapticsEnabled, rootView) {
            rootView.isHapticFeedbackEnabled = hapticsEnabled
            rootView.rootView?.isHapticFeedbackEnabled = hapticsEnabled
        }

        val horizontalPadding = if (navBarStyle == NavBarStyle.DEFAULT) {
            if (systemNavBarInset > 30.dp) 14.dp else systemNavBarInset
        } else {
            0.dp
        }
        val animatedBottomBarPadding by animateDpAsState(
            targetValue = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else systemNavBarInset,
            animationSpec = tween(400),
            label = "BottomBarPadding"
        )
        val bottomBarPadding = animatedBottomBarPadding
        val navBarHeight = resolveNavBarSurfaceHeight(navBarStyle, systemNavBarInset, navBarCompactMode)
        val navBarOccupiedHeight by remember(systemNavBarInset, navBarCompactMode, useNavigationRail) {
            derivedStateOf {
                if (useNavigationRail) {
                    0.dp
                } else {
                    resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
                }
            }
        }
        val navBarVisibilityProgressState = animateFloatAsState(
            targetValue = if (shouldHideNavigationBar) 0f else 1f,
            animationSpec = tween(
                durationMillis = 220,
                easing = LinearOutSlowInEasing
            ),
            label = "NavBarVisibilityProgress"
        )
        val navBarVisibilityProgress by navBarVisibilityProgressState
        val visibleNavBarOccupiedHeight by remember(navBarOccupiedHeight, navBarVisibilityProgress) {
            derivedStateOf { navBarOccupiedHeight * navBarVisibilityProgress }
        }
        val miniPlayerBottomMargin by remember(systemNavBarInset, visibleNavBarOccupiedHeight) {
            derivedStateOf {
                if (visibleNavBarOccupiedHeight > systemNavBarInset) {
                    visibleNavBarOccupiedHeight
                } else {
                    systemNavBarInset
                }
            }
        }
        val shouldRenderNavigationBar by remember(shouldHideNavigationBar, navBarVisibilityProgress) {
            derivedStateOf {
                !shouldHideNavigationBar || navBarVisibilityProgress > 0.01f
            }
        }
        val isNavBarEffectivelyHidden by remember(shouldHideNavigationBar, navBarVisibilityProgress) {
            derivedStateOf {
                shouldHideNavigationBar && navBarVisibilityProgress <= 0.01f
            }
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        LaunchedEffect(userPreferencesRepository) {
            userPreferencesRepository.clearDeprecatedPlayerSheetPreference()
        }

        CompositionLocalProvider(
            LocalAppHapticsConfig provides appHapticsConfig,
            LocalHapticFeedback provides scopedHapticFeedback
        ) {
            AppSidebarDrawer(
                drawerState = drawerState,
                selectedRoute = currentRoute ?: Screen.Home.route,
                onDestinationSelected = { destination ->
                    scope.launch { drawerState.close() }
                    when (destination) {
                        DrawerDestination.Home -> navController.navigateSafely(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                        DrawerDestination.Equalizer -> navController.navigateSafely(Screen.Equalizer.route)
                        DrawerDestination.Settings -> navController.navigateSafely(Screen.Settings.route)
                    }
                }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (useNavigationRail) {
                    AnimatedVisibility(
                        visible = !shouldHideNavigationBar,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut()
                    ) {
                        val onSearchIconDoubleTap = remember(playerViewModel) {
                            { playerViewModel.onSearchNavIconDoubleTapped() }
                        }
                        PlayerNavigationRail(
                            navController = navController,
                            navItems = commonNavItems,
                            currentRoute = currentRoute,
                            onSearchIconDoubleTap = onSearchIconDoubleTap
                        )
                    }
                }

                Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                bottomBar = {
                    if (!useNavigationRail && shouldRenderNavigationBar) {
                        val currentSongId by remember {
                            playerViewModel.stablePlayerState
                                .map { it.currentSong?.id }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = null)
                        val showPlayerContentArea = currentSongId != null
                        val navBarElevation = 3.dp

                        val animatedNavBarCornerRadius = animateDpAsState(
                            targetValue = navBarCornerRadius.dp,
                            animationSpec = tween(400),
                            label = "NavBarCornerRadius"
                        )

                        val animatedDefaultTopCornerRadius = animateDpAsState(
                            targetValue = if (showPlayerContentArea && !isMiniPlayerDismissing) 10.dp else navBarCornerRadius.dp,
                            animationSpec = tween(400),
                            label = "NavBarDefaultTopCornerRadius"
                        )

                        var componentHeightPx by remember { mutableStateOf(0) }
                        val density = LocalDensity.current
                        val shadowOverflowPx = remember(navBarElevation, density) {
                            with(density) { (navBarElevation * 8).toPx() }
                        }
                        val bottomBarPaddingPx = remember(bottomBarPadding, density) {
                            with(density) { bottomBarPadding.toPx() }
                        }
                        val navBarElevationPx = remember(navBarElevation, density) {
                            with(density) { navBarElevation.toPx() }
                        }
                        val navBarShapeCache = remember { NavBarShapeCache() }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(navBarOccupiedHeight)
                                .clipToBounds()
                        ) {
                            val onSearchIconDoubleTap = remember(playerViewModel) {
                                { playerViewModel.onSearchNavIconDoubleTapped() }
                            }

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = bottomBarPadding)
                                    .onSizeChanged { componentHeightPx = it.height }
                                    .graphicsLayer {
                                        val expansionHide = if (showPlayerContentArea) {
                                            playerViewModel.playerContentExpansionFraction.value.coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                        val routeHide = (1f - navBarVisibilityProgressState.value).coerceIn(0f, 1f)
                                        val hideFraction = maxOf(expansionHide, routeHide)
                                        translationY = (componentHeightPx + shadowOverflowPx + bottomBarPaddingPx) * hideFraction
                                        alpha = 1f
                                    }
                                    .height(navBarHeight)
                                    .padding(horizontal = horizontalPadding)
                                    .graphicsLayer {
                                        val fraction = playerViewModel.playerContentExpansionFraction.value
                                        val topDp = when {
                                            navBarStyle == NavBarStyle.DEFAULT -> animatedDefaultTopCornerRadius.value
                                            navBarStyle == NavBarStyle.FULL_WIDTH -> lerp(navBarCornerRadius.dp, 26.dp, fraction)
                                            showPlayerContentArea -> if (fraction < 0.2f) {
                                                lerp(navBarCornerRadius.dp, 26.dp, (fraction / 0.2f).coerceIn(0f, 1f))
                                            } else {
                                                26.dp
                                            }
                                            else -> navBarCornerRadius.dp
                                        }
                                        val bottomDp = when (navBarStyle) {
                                            NavBarStyle.FULL_WIDTH -> 0.dp
                                            else -> animatedNavBarCornerRadius.value
                                        }
                                        shape = navBarShapeCache.get(this, topDp.toPx(), bottomDp.toPx(), useSmoothCorners)
                                        clip = true
                                        shadowElevation = navBarElevationPx
                                    },
                                color = NavigationBarDefaults.containerColor
                            ) {
                                PlayerInternalNavigationBar(
                                    navController = navController,
                                    navItems = commonNavItems,
                                    currentRoute = currentRoute,
                                    navBarStyle = navBarStyle,
                                    compactMode = navBarCompactMode,
                                    bottomBarPadding = bottomBarPadding,
                                    onSearchIconDoubleTap = onSearchIconDoubleTap,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                ) { innerPadding ->
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val containerHeight = this.maxHeight
                        val screenHeightPx = remember(containerHeight, density) {
                            with(density) { containerHeight.toPx() }
                        }

                        val showPlayerContentInitially by remember {
                            playerViewModel.stablePlayerState
                                .map { it.currentSong?.id != null }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)
                        val routesWithHiddenMiniPlayer = remember { setOf(Screen.NavBarCrRad.route) }
                        val shouldHideMiniPlayer by remember(currentRoute) {
                            derivedStateOf { currentRoute in routesWithHiddenMiniPlayer }
                        }

                        val miniPlayerH = with(density) { MiniPlayerHeight.toPx() }
                        val totalSheetHeightWhenContentCollapsedPx = if (showPlayerContentInitially && !shouldHideMiniPlayer) miniPlayerH else 0f

                        val bottomMargin = miniPlayerBottomMargin

                        val spacerPx = with(density) { MiniPlayerBottomSpacer.toPx() }
                        val bottomMarginPx = with(density) { bottomMargin.toPx() }
                        val sheetCollapsedTargetY = calculatePlayerSheetCollapsedTargetY(
                            containerHeightPx = screenHeightPx,
                            collapsedContentHeightPx = totalSheetHeightWhenContentCollapsedPx,
                            bottomMarginPx = bottomMarginPx,
                            bottomSpacerPx = spacerPx
                        )

                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            AppNavigation(
                                playerViewModel = playerViewModel,
                                navController = navController,
                                paddingValues = innerPadding,
                                userPreferencesRepository = userPreferencesRepository,
                                onSearchBarActiveChange = { isSearchBarActive = it },
                                onOpenSidebar = { scope.launch { drawerState.open() } }
                            )
                        }

                        val isExpandedOrExpanding by remember {
                            derivedStateOf {
                                playerViewModel.playerContentExpansionFraction.value > 0.01f
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpandedOrExpanding,
                            enter = fadeIn(animationSpec = tween(durationMillis = 350)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 350)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerLowest.copy(
                                            alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.35f else 0.6f
                                        )
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            playerViewModel.collapsePlayerSheet()
                                        }
                                    }
                            )
                        }

                        UnifiedPlayerSheetV2(
                            playerViewModel = playerViewModel,
                            sheetCollapsedTargetY = sheetCollapsedTargetY,
                            collapsedStateHorizontalPadding = horizontalPadding,
                            hideMiniPlayer = shouldHideMiniPlayer,
                            containerHeight = containerHeight,
                            navController = navController,
                            isNavBarHidden = isNavBarEffectivelyHidden
                        )

                        val dismissUndoBarSlice by remember {
                            playerViewModel.playerUiState
                                .map { state ->
                                    DismissUndoBarSlice(
                                        isVisible = state.showDismissUndoBar,
                                        durationMillis = state.undoBarVisibleDuration
                                    )
                                }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = DismissUndoBarSlice())
                        val onUndoDismissPlaylist = remember(playerViewModel) {
                            { playerViewModel.undoDismissPlaylist() }
                        }
                        val onCloseDismissUndoBar = remember(playerViewModel) {
                            { playerViewModel.hideDismissUndoBar() }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = dismissUndoBarSlice.isVisible,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = innerPadding.calculateBottomPadding() + MiniPlayerBottomSpacer)
                                .padding(horizontal = horizontalPadding)
                        ) {
                            DismissUndoBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MiniPlayerHeight)
                                    .padding(horizontal = 14.dp),
                                onUndo = onUndoDismissPlaylist,
                                onClose = onCloseDismissUndoBar,
                                durationMillis = dismissUndoBarSlice.durationMillis
                            )
                        }
                    }
                }
            }
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        LogUtils.d(this, "onStart")
        playerViewModel.onMainActivityStart()

        if (intent.getBooleanExtra("is_benchmark", false)) {
        }

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d(this, "onStop")
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    override fun onResume() {
        super.onResume()
    }


}

/**
 * Returns a cached Shape instance for a quantized (top, bottom) radius pair.
 * Because the instance identity is stable while the radii don't move past a
 * sub-pixel threshold, the graphics layer reuses its cached Outline between
 * frames and only re-clips when the radius actually changes.
 */
private class NavBarShapeCache {
    private var lastTopPx: Float = Float.NaN
    private var lastBottomPx: Float = Float.NaN
    private var lastSmooth: Boolean = true
    private var cached: androidx.compose.ui.graphics.Shape = RectangleShape

    fun get(
        density: androidx.compose.ui.unit.Density,
        topPx: Float,
        bottomPx: Float,
        smooth: Boolean
    ): androidx.compose.ui.graphics.Shape {
        if (smooth == lastSmooth &&
            !lastTopPx.isNaN() &&
            kotlin.math.abs(topPx - lastTopPx) < 0.5f &&
            kotlin.math.abs(bottomPx - lastBottomPx) < 0.5f
        ) {
            return cached
        }
        lastTopPx = topPx
        lastBottomPx = bottomPx
        lastSmooth = smooth
        cached = with(density) {
            DynamicSmoothCornerShape(
                useSmoothCorners = smooth,
                topRadius = topPx.toDp(),
                bottomRadius = bottomPx.toDp()
            )
        }
        return cached
    }
}

/**
 * Fixed-radius corner shape. Swaps AbsoluteSmoothCornerShape for a plain
 * RoundedCornerShape when smooth corners are disabled in settings. The radius
 * values are identical in both branches, so the animated radius behavior is
 * unchanged regardless of which delegate is active. The resulting Outline is
 * cached per (size, layoutDirection) so repeated draws are cheap.
 */
private class DynamicSmoothCornerShape(
    private val useSmoothCorners: Boolean,
    private val topRadius: androidx.compose.ui.unit.Dp,
    private val bottomRadius: androidx.compose.ui.unit.Dp
) : androidx.compose.ui.graphics.Shape {

    private var cachedSize: androidx.compose.ui.geometry.Size =
        androidx.compose.ui.geometry.Size.Unspecified
    private var cachedLayoutDirection: androidx.compose.ui.unit.LayoutDirection? = null
    private var cachedOutline: androidx.compose.ui.graphics.Outline? = null

    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        cachedOutline?.let {
            if (cachedSize == size && cachedLayoutDirection == layoutDirection) return it
        }

        val delegate: androidx.compose.ui.graphics.Shape = if (useSmoothCorners) {
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = topRadius,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = topRadius,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = bottomRadius,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = bottomRadius,
                smoothnessAsPercentBR = 60
            )
        } else {
            RoundedCornerShape(
                topStart = topRadius,
                topEnd = topRadius,
                bottomEnd = bottomRadius,
                bottomStart = bottomRadius
            )
        }

        return delegate.createOutline(size, layoutDirection, density).also {
            cachedSize = size
            cachedLayoutDirection = layoutDirection
            cachedOutline = it
        }
    }
}
