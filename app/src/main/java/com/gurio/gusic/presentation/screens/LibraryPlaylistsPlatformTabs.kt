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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.collections.immutable.ImmutableList
import com.gurio.gusic.R
import com.gurio.gusic.data.lxmusic.LxPlaylist
import com.gurio.gusic.data.lxmusic.LxSong
import com.gurio.gusic.data.lxmusic.LxSongMapper
import com.gurio.gusic.data.lxmusic.LxSources
import com.gurio.gusic.data.model.Playlist
import com.gurio.gusic.presentation.components.MiniPlayerHeight
import com.gurio.gusic.presentation.components.PlaylistContainer
import com.gurio.gusic.presentation.components.SmartImage
import com.gurio.gusic.presentation.components.subcomps.EnhancedSongListItem
import com.gurio.gusic.presentation.viewmodel.PlayerViewModel
import com.gurio.gusic.presentation.viewmodel.PlaylistUiState

/** 层级深度：一级本软件歌单(0) -> 平台歌单二级(1) -> 歌单歌曲三级(2) */
private fun playlistsDepth(state: Pair<String?, LxPlaylist?>): Int = when {
    state.second != null -> 2
    state.first != null -> 1
    else -> 0
}

/**
 * “歌单”分类的三级结构：
 * - 一级：本软件歌单列表（PlaylistContainer 原样保留）+ 末尾追加已登录平台入口行
 * - 二级：平台用户的歌单列表
 * - 三级：平台歌单内的歌曲列表
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryPlaylistsTab(
    playlistUiState: PlaylistUiState,
    filteredPlaylists: ImmutableList<Playlist> = playlistUiState.playlists,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    selectedPlaylistIds: Set<String> = emptySet(),
    onPlaylistLongPress: (Playlist) -> Unit = {},
    onPlaylistSelectionToggle: (Playlist) -> Unit = {},
    playlistsScope: String? = null,
    loggedInPlatforms: List<String> = emptyList(),
    platformPlaylists: List<LxPlaylist> = emptyList(),
    isLoadingPlatformPlaylists: Boolean = false,
    platformPlaylistsError: String? = null,
    selectedPlaylist: LxPlaylist? = null,
    playlistSongs: List<LxSong> = emptyList(),
    isLoadingPlaylistSongs: Boolean = false,
    playlistSongsError: String? = null,
    onSelectPlaylistsScope: (String?) -> Unit = {},
    onOpenPlatformPlaylist: (LxPlaylist) -> Unit = {},
    onPlayPlaylistSong: (LxSong) -> Unit = {},
    onRefreshPlatform: () -> Unit = {},
    onBackToPlaylistsEntries: () -> Unit = {},
    onClosePlatformPlaylist: () -> Unit = {}
) {
    AnimatedContent(
        targetState = playlistsScope to selectedPlaylist,
        label = "PlaylistsScopeNavigation",
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            // 一级 -> 二级 -> 三级 为前进（向左滑入），返回为后退（向右滑入），与“我喜欢”两级结构一致
            val goingDeeper = playlistsDepth(targetState) > playlistsDepth(initialState)
            val slideIn = slideInHorizontally { width ->
                if (goingDeeper) width else -width
            } + fadeIn()
            val slideOut = slideOutHorizontally { width ->
                if (goingDeeper) -width else width
            } + fadeOut()
            slideIn togetherWith slideOut
        }
    ) { (scope, selected) ->
        when {
            selected != null -> LibraryPlaylistSongsContent(
                title = selected.name,
                songs = playlistSongs,
                isLoading = isLoadingPlaylistSongs,
                error = playlistSongsError,
                onPlaySong = onPlayPlaylistSong,
                onRefresh = onRefreshPlatform,
                bottomBarHeight = bottomBarHeight,
                onBack = onClosePlatformPlaylist
            )
            scope != null -> LibraryPlatformPlaylistsContent(
                title = LxSources.labelRes(scope)?.let { stringResource(it) }
                    ?: LxSources.shortLabel(scope),
                playlists = platformPlaylists,
                isLoading = isLoadingPlatformPlaylists,
                error = platformPlaylistsError,
                onOpenPlaylist = onOpenPlatformPlaylist,
                onRefresh = onRefreshPlatform,
                bottomBarHeight = bottomBarHeight,
                onBack = onBackToPlaylistsEntries
            )
            else -> PlaylistContainer(
                playlistUiState = playlistUiState,
                filteredPlaylists = filteredPlaylists,
                currentSortOption = playlistUiState.currentPlaylistSortOption,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                bottomBarHeight = bottomBarHeight,
                navController = navController,
                playerViewModel = playerViewModel,
                isSelectionMode = isSelectionMode,
                selectedPlaylistIds = selectedPlaylistIds,
                onPlaylistLongPress = onPlaylistLongPress,
                onPlaylistSelectionToggle = onPlaylistSelectionToggle,
                footerContent = {
                    // 多选激活时隐藏平台入口行，避免与歌单多选操作冲突
                    if (!isSelectionMode) {
                        LibraryPlaylistPlatformEntries(
                            loggedInPlatforms = loggedInPlatforms,
                            onSelect = onSelectPlaylistsScope
                        )
                    }
                },
                hasPlatformEntries = loggedInPlatforms.isNotEmpty()
            )
        }
    }
}

/**
 * 一级歌单列表末尾的平台入口区：仅显示已登录平台（wy/tx/kg/kw 顺序）。
 */
@Composable
private fun LibraryPlaylistPlatformEntries(
    loggedInPlatforms: List<String>,
    onSelect: (String) -> Unit
) {
    Column {
        loggedInPlatforms.forEach { source ->
            key(source) {
                LibraryPlaylistPlatformEntryRow(
                    title = LxSources.labelRes(source)?.let { stringResource(it) }
                        ?: LxSources.shortLabel(source),
                    onClick = { onSelect(source) },
                    leading = {
                        // 与“我喜欢”一级平台行同款：24dp 槽位内居中 10dp 品牌色圆点，
                        // 保证各行文字起始边垂直对齐（圆点本身大小不变）
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
}

@Composable
private fun LibraryPlaylistPlatformEntryRow(
    title: String,
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * 平台用户歌单二级列表：loading / 错误重试 / 空态 / 下拉强刷歌单列表。
 */
@Composable
private fun LibraryPlatformPlaylistsContent(
    title: String,
    playlists: List<LxPlaylist>,
    isLoading: Boolean,
    error: String?,
    onOpenPlaylist: (LxPlaylist) -> Unit,
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
                isLoading && playlists.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                error != null && playlists.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.library_playlists_platform_load_failed),
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
                        if (playlists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.library_playlists_platform_empty),
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
                                    items = playlists,
                                    key = { _, playlist -> playlist.id },
                                    contentType = { _, _ -> "platform_playlist" }
                                ) { _, playlist ->
                                    LibraryPlatformPlaylistRow(
                                        playlist = playlist,
                                        onClick = { onOpenPlaylist(playlist) }
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

@Composable
private fun LibraryPlatformPlaylistRow(
    playlist: LxPlaylist,
    onClick: () -> Unit
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
        SmartImage(
            model = playlist.pic,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            playlist.trackCount?.let { trackCount ->
                Text(
                    text = stringResource(R.string.lx_text_track_count_format, trackCount),
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
 * 平台歌单三级歌曲列表：loading / 错误重试 / 空态 / 下拉强刷，骨架与“我喜欢”平台二级一致。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LibraryPlaylistSongsContent(
    title: String,
    songs: List<LxSong>,
    isLoading: Boolean,
    error: String?,
    onPlaySong: (LxSong) -> Unit,
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
                                text = stringResource(R.string.library_playlists_platform_songs_load_failed),
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
                    val songsPullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = onRefresh,
                        state = songsPullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = songsPullToRefreshState,
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
                                    text = stringResource(R.string.library_playlists_platform_songs_empty),
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
                                    key = { index, song -> "${song.songmid}_$index" },
                                    contentType = { _, _ -> "playlist_song" }
                                ) { _, song ->
                                    EnhancedSongListItem(
                                        song = remember(song) { LxSongMapper.toSong(song) },
                                        isPlaying = false,
                                        isCurrentSong = false,
                                        isLoading = false,
                                        // 同一歌单内所有歌同源，不再逐行显示平台标签
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
