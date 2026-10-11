@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalAnimationApi::class
)

package com.gurio.gusic.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import com.gurio.gusic.R
import com.gurio.gusic.data.lxmusic.LxSources
import com.gurio.gusic.data.model.LibraryTabId
import com.gurio.gusic.data.model.Song
import com.gurio.gusic.data.model.SortOption
import com.gurio.gusic.data.model.StorageFilter
import com.gurio.gusic.presentation.components.ExpressiveScrollBar
import com.gurio.gusic.presentation.components.MiniPlayerHeight
import com.gurio.gusic.presentation.components.songFastScrollLabel
import com.gurio.gusic.presentation.components.subcomps.EnhancedSongListItem
import com.gurio.gusic.presentation.viewmodel.PlayerViewModel
import com.gurio.gusic.presentation.viewmodel.StablePlayerState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.text.style.TextOverflow

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryFavoritesTab(
    favoriteSongs: LazyPagingItems<Song>,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    sortOption: SortOption,
    onLocateCurrentSongVisibilityChanged: (Boolean) -> Unit = {},
    onRegisterLocateCurrentSongAction: ((() -> Unit)?) -> Unit = {},
    storageFilter: StorageFilter = StorageFilter.ALL,
    hasCurrentSong: Boolean = false,
    likedScope: String? = null,
    favoriteSongCount: Int = 0,
    loggedInPlatforms: List<String> = emptyList(),
    platformLikedCounts: Map<String, Int> = emptyMap(),
    platformSongs: List<Song> = emptyList(),
    isLoadingPlatformSongs: Boolean = false,
    platformSongsError: String? = null,
    onSelectLikedScope: (String?) -> Unit = {},
    onPlayPlatformSong: (Song) -> Unit = {},
    onRefreshPlatformSongs: () -> Unit = {},
    onBackToLikedEntries: () -> Unit = {}
) {
    AnimatedContent(
        targetState = likedScope,
        label = "LikedScopeNavigation",
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            // null(一级入口) -> "local"/平台 为前进；平台 -> null 为后退
            val goingDeeper = targetState != null
            val slideIn = slideInHorizontally { width ->
                if (goingDeeper) width else -width
            } + fadeIn()
            val slideOut = slideOutHorizontally { width ->
                if (goingDeeper) -width else width
            } + fadeOut()
            slideIn togetherWith slideOut
        }
    ) { scope ->
        when (scope) {
            null -> LibraryLikedEntriesList(
                favoriteSongCount = favoriteSongCount,
                loggedInPlatforms = loggedInPlatforms,
                platformLikedCounts = platformLikedCounts,
                onSelect = onSelectLikedScope,
                bottomBarHeight = bottomBarHeight
            )
            LxSources.LOCAL -> {
                LibraryLocalFavoritesContent(
                    favoriteSongs = favoriteSongs,
                    playerViewModel = playerViewModel,
                    bottomBarHeight = bottomBarHeight,
                    onMoreOptionsClick = onMoreOptionsClick,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    isSelectionMode = isSelectionMode,
                    selectedSongIds = selectedSongIds,
                    onSongLongPress = onSongLongPress,
                    onSongSelectionToggle = onSongSelectionToggle,
                    getSelectionIndex = getSelectionIndex,
                    sortOption = sortOption,
                    onLocateCurrentSongVisibilityChanged = onLocateCurrentSongVisibilityChanged,
                    onRegisterLocateCurrentSongAction = onRegisterLocateCurrentSongAction,
                    storageFilter = storageFilter,
                    hasCurrentSong = hasCurrentSong,
                    onBack = onBackToLikedEntries
                )
            }
            else -> LibraryPlatformLikedSongsContent(
                title = LxSources.labelRes(scope)?.let { stringResource(it) }
                    ?: LxSources.shortLabel(scope),
                songs = platformSongs,
                isLoading = isLoadingPlatformSongs,
                error = platformSongsError,
                onPlaySong = onPlayPlatformSong,
                onRefresh = onRefreshPlatformSongs,
                bottomBarHeight = bottomBarHeight,
                onBack = onBackToLikedEntries
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LibraryLocalFavoritesContent(
    favoriteSongs: LazyPagingItems<Song>,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean,
    selectedSongIds: Set<String>,
    onSongLongPress: (Song) -> Unit,
    onSongSelectionToggle: (Song) -> Unit,
    getSelectionIndex: (String) -> Int?,
    sortOption: SortOption,
    onLocateCurrentSongVisibilityChanged: (Boolean) -> Unit,
    onRegisterLocateCurrentSongAction: ((() -> Unit)?) -> Unit,
    storageFilter: StorageFilter,
    hasCurrentSong: Boolean,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val visibilityCallback by rememberUpdatedState(onLocateCurrentSongVisibilityChanged)
    val registerActionCallback by rememberUpdatedState(onRegisterLocateCurrentSongAction)
    val favoriteFastScrollLabelProvider = remember(favoriteSongs, sortOption) {
        { index: Int ->
            songFastScrollLabel(
                song = favoriteSongs.peek(index),
                sortOption = sortOption
            )
        }
    }
    var lastHandledFavoriteSortKey by remember { mutableStateOf(sortOption.storageKey) }
    var pendingFavoriteSortScrollReset by remember { mutableStateOf(false) }
    var favoriteSortSawRefreshLoading by remember { mutableStateOf(false) }
    val currentSongId by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    val currentSongListIndex = remember(favoriteSongs.itemCount, currentSongId) {
        if (currentSongId == null) -1
        else {
            val items = favoriteSongs.itemSnapshotList
            items.indexOfFirst { it?.id == currentSongId }
        }
    }
    val locateCurrentSongAction: (() -> Unit)? = remember(currentSongId) {
        if (currentSongId == null) {
            null
        } else {
            {
                playerViewModel.requestLocateCurrentSong()
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(playerViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            playerViewModel.scrollToIndexEvent.collect { index ->
                if (index >= 0) {
                    val firstVisible = listState.firstVisibleItemIndex
                    if (Math.abs(index - firstVisible) > 20) {
                        listState.scrollToItem(index)
                    } else {
                        listState.animateScrollToItem(index)
                    }
                }
            }
        }
    }

    LaunchedEffect(locateCurrentSongAction) {
        registerActionCallback(locateCurrentSongAction)
    }

    LaunchedEffect(sortOption) {
        val currentSortKey = sortOption.storageKey
        if (currentSortKey == lastHandledFavoriteSortKey) return@LaunchedEffect
        lastHandledFavoriteSortKey = currentSortKey
        pendingFavoriteSortScrollReset = true
        favoriteSortSawRefreshLoading = false
        listState.scrollToItem(0)
    }

    LaunchedEffect(favoriteSongs.loadState.refresh, pendingFavoriteSortScrollReset) {
        if (!pendingFavoriteSortScrollReset) return@LaunchedEffect
        if (favoriteSongs.loadState.refresh is LoadState.Loading) {
            favoriteSortSawRefreshLoading = true
            return@LaunchedEffect
        }
        if (!favoriteSortSawRefreshLoading) return@LaunchedEffect
        listState.scrollToItem(0)
        pendingFavoriteSortScrollReset = false
    }

    LaunchedEffect(currentSongListIndex, favoriteSongs.itemCount, listState, currentSongId) {
        if (currentSongId == null || favoriteSongs.itemCount == 0) {
            visibilityCallback(false)
            return@LaunchedEffect
        }

        if (currentSongListIndex == -1) {
             visibilityCallback(true)
             return@LaunchedEffect
        }

        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                false
            } else {
                currentSongListIndex in visibleItems.first().index..visibleItems.last().index
            }
        }
            .distinctUntilChanged()
            .collect { isVisible ->
                visibilityCallback(!isVisible)
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            visibilityCallback(false)
            registerActionCallback(null)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryLikedSubScreenHeader(
            title = stringResource(R.string.library_liked_scope_local),
            onBack = onBack
        )
        Box(modifier = Modifier.weight(1f)) {
            if (favoriteSongs.itemCount == 0 && favoriteSongs.loadState.refresh !is LoadState.Loading) {
                LibraryExpressiveEmptyState(
                    tabId = LibraryTabId.LIKED,
                    storageFilter = storageFilter,
                    bottomBarHeight = bottomBarHeight
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val songsPullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = songsPullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = songsPullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(start = 12.dp, end = if (listState.canScrollForward || listState.canScrollBackward) 22.dp else 12.dp, bottom = 6.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 26.dp,
                                            topEnd = 26.dp,
                                            bottomStart = PlayerSheetCollapsedCornerRadius,
                                            bottomEnd = PlayerSheetCollapsedCornerRadius
                                        )
                                    ),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
                            ) {
                                items(
                                    count = favoriteSongs.itemCount,
                                    key = { index -> favoriteSongs.peek(index)?.id ?: index },
                                    contentType = { "song" }
                                ) { index ->
                                    val song = favoriteSongs[index]
                                    if (song != null) {
                                        LibraryPlaybackAwareSongItem(
                                            song = song,
                                            playerViewModel = playerViewModel,
                                            onMoreOptionsClick = { onMoreOptionsClick(song) },
                                            isSelected = selectedSongIds.contains(song.id),
                                            selectionIndex = if (isSelectionMode) getSelectionIndex(song.id) else null,
                                            isSelectionMode = isSelectionMode,
                                            onLongPress = { onSongLongPress(song) },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    onSongSelectionToggle(song)
                                                } else {
                                                    playerViewModel.showAndPlaySongFromFavorites(song)
                                                }
                                            }
                                        )
                                    } else {
                                        EnhancedSongListItem(
                                            song = Song.emptySong(),
                                            isPlaying = false,
                                            isLoading = true,
                                            isCurrentSong = false,
                                            onMoreOptionsClick = {},
                                            onClick = {}
                                        )
                                    }
                                }
                            }

                            val bottomPadding = if (hasCurrentSong)
                                bottomBarHeight + MiniPlayerHeight + 16.dp
                            else
                                bottomBarHeight + 16.dp

                            ExpressiveScrollBar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                                listState = listState,
                                dragLabelProvider = favoriteFastScrollLabelProvider
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibrarySongsTabPaginated(
    paginatedSongs: LazyPagingItems<Song>,
    stablePlayerState: StablePlayerState,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    when {
        paginatedSongs.loadState.refresh is LoadState.Loading && paginatedSongs.itemCount == 0 -> {
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = if (listState.canScrollForward || listState.canScrollBackward) 22.dp else 12.dp, bottom = 6.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp,
                            bottomStart = PlayerSheetCollapsedCornerRadius,
                            bottomEnd = PlayerSheetCollapsedCornerRadius
                        )
                    )
                    .fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
            ) {
                items(12, key = { "skeleton_song_$it" }) {
                    EnhancedSongListItem(
                        song = Song.emptySong(),
                        isPlaying = false,
                        isLoading = true,
                        isCurrentSong = false,
                        onMoreOptionsClick = {},
                        onClick = {}
                    )
                }
            }
        }

        paginatedSongs.loadState.refresh is LoadState.Error && paginatedSongs.itemCount == 0 -> {
            val error = (paginatedSongs.loadState.refresh as LoadState.Error).error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.library_error_loading_songs), style = MaterialTheme.typography.titleMedium)
                    Text(
                        error.localizedMessage ?: stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { paginatedSongs.retry() }) {
                        Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        paginatedSongs.itemCount == 0 && paginatedSongs.loadState.refresh is LoadState.NotLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_music_off_24),
                        contentDescription = stringResource(R.string.cd_no_songs_in_library),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.library_no_songs_in_library_message), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.library_no_songs_rescan_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        onRefresh()
                        paginatedSongs.refresh()
                    },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .padding(start = 12.dp, end = if (listState.canScrollForward || listState.canScrollBackward) 22.dp else 12.dp, bottom = 6.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 26.dp,
                                        topEnd = 26.dp,
                                        bottomStart = PlayerSheetCollapsedCornerRadius,
                                        bottomEnd = PlayerSheetCollapsedCornerRadius
                                    )
                                ),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
                        ) {
                            item(
                                key = "songs_top_spacer",
                                contentType = "songs_top_spacer"
                            ) { Spacer(Modifier.height(0.dp)) }

                            items(
                                count = paginatedSongs.itemCount,
                                key = { index -> paginatedSongs.peek(index)?.id ?: "paged_song_$index" },
                                contentType = paginatedSongs.itemContentType { "song" }
                            ) { index ->
                                val song = paginatedSongs[index]
                                if (song != null) {
                                    val isPlayingThisSong = song.id == stablePlayerState.currentSong?.id && stablePlayerState.isPlaying

                                    val rememberedOnMoreOptionsClick: (Song) -> Unit = remember(onMoreOptionsClick) {
                                        { songFromListItem -> onMoreOptionsClick(songFromListItem) }
                                    }
                                    val rememberedOnClick: () -> Unit = remember(song) {
                                        { playerViewModel.showAndPlaySongFromLibrary(song) }
                                    }

                                    EnhancedSongListItem(
                                        song = song,
                                        isPlaying = isPlayingThisSong,
                                        isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                        isLoading = false,
                                        onMoreOptionsClick = rememberedOnMoreOptionsClick,
                                        onClick = rememberedOnClick
                                    )
                                } else {
                                    EnhancedSongListItem(
                                        song = Song.emptySong(),
                                        isPlaying = false,
                                        isLoading = true,
                                        isCurrentSong = false,
                                        onMoreOptionsClick = {},
                                        onClick = {}
                                    )
                                }
                            }

                            if (paginatedSongs.loadState.append is LoadState.Loading) {
                                item(contentType = "songs_append_loading") {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }

                        val bottomPadding = if (stablePlayerState.currentSong != null && stablePlayerState.currentSong != Song.emptySong())
                            bottomBarHeight + MiniPlayerHeight + 16.dp
                        else
                            bottomBarHeight + 16.dp

                        ExpressiveScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                            listState = listState
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface, Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}

/**
 * “我喜欢”一级平台入口列表：首行为本软件“我喜欢”，其后为各已登录在线平台入口。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LibraryLikedEntriesList(
    favoriteSongCount: Int,
    loggedInPlatforms: List<String>,
    platformLikedCounts: Map<String, Int>,
    onSelect: (String?) -> Unit,
    bottomBarHeight: Dp
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 26.dp,
                    topEnd = 26.dp,
                    bottomStart = PlayerSheetCollapsedCornerRadius,
                    bottomEnd = PlayerSheetCollapsedCornerRadius
                )
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
    ) {
        item(key = "liked_entry_local") {
            LibraryLikedEntryRow(
                title = stringResource(R.string.library_liked_scope_local),
                subtitle = stringResource(R.string.lx_text_track_count_format, favoriteSongCount),
                onClick = { onSelect(LxSources.LOCAL) },
                leading = {
                    Icon(
                        painter = painterResource(id = R.drawable.round_favorite_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
        items(
            items = loggedInPlatforms,
            key = { it }
        ) { source ->
            LibraryLikedEntryRow(
                title = LxSources.labelRes(source)?.let { stringResource(it) }
                    ?: LxSources.shortLabel(source),
                subtitle = platformLikedCounts[source]?.let { likedCount ->
                    stringResource(R.string.lx_text_track_count_format, likedCount)
                },
                onClick = { onSelect(source) },
                leading = {
                    // 与本软件行图标槽同尺寸(24dp)并居中放置 10dp 品牌色圆点，
                    // 保证一级入口各行文字起始边垂直对齐（圆点本身大小不变）
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(LxSources.brandColor(source))
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun LibraryLikedEntryRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * 在线平台的“喜欢”歌曲二级列表：loading / 错误重试 / 空态 / 下拉强刷歌曲列表。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LibraryPlatformLikedSongsContent(
    title: String,
    songs: List<Song>,
    isLoading: Boolean,
    error: String?,
    onPlaySong: (Song) -> Unit,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        LibraryLikedSubScreenHeader(
            title = title,
            onBack = onBack
        )
        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading && songs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                error != null && songs.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.library_liked_platform_load_failed),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onRefresh) {
                                Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                else -> {
                    val platformPullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = onRefresh,
                        state = platformPullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = platformPullToRefreshState,
                                isRefreshing = isLoading,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    ) {
                        if (songs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.library_liked_platform_empty),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 26.dp,
                                            topEnd = 26.dp,
                                            bottomStart = PlayerSheetCollapsedCornerRadius,
                                            bottomEnd = PlayerSheetCollapsedCornerRadius
                                        )
                                    ),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
                            ) {
                                itemsIndexed(
                                    items = songs,
                                    key = { index, song -> "${song.id}_$index" },
                                    contentType = { _, _ -> "platform_song" }
                                ) { _, song ->
                                    EnhancedSongListItem(
                                        song = song,
                                        isPlaying = false,
                                        isCurrentSong = false,
                                        isLoading = false,
                                        // 平台“我喜欢”二级列表内所有歌同源，不再逐行显示平台标签
                                        showSourceTag = false,
                                        onMoreOptionsClick = {},
                                        onClick = { onPlaySong(song) }
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

/**
 * “我喜欢”二级页顶部返回行：返回按钮 + 标题（本软件二级用“我喜欢”，平台二级用平台名）。
 * 歌单 tab 的平台二级/三级页复用同一返回行样式。
 */
@Composable
internal fun LibraryLikedSubScreenHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.auth_cd_back),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
