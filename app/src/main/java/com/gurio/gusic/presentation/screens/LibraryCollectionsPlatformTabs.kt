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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import com.gurio.gusic.R
import com.gurio.gusic.data.lxmusic.LxAlbum
import com.gurio.gusic.data.lxmusic.LxArtist
import com.gurio.gusic.data.lxmusic.LxSong
import com.gurio.gusic.data.lxmusic.LxSongMapper
import com.gurio.gusic.data.lxmusic.LxSources
import com.gurio.gusic.presentation.components.MiniPlayerHeight
import com.gurio.gusic.presentation.components.SmartImage
import com.gurio.gusic.presentation.components.subcomps.EnhancedSongListItem
import com.gurio.gusic.presentation.viewmodel.CollectionDetail
import com.gurio.gusic.presentation.viewmodel.CollectionsContentTab
import com.gurio.gusic.presentation.viewmodel.LibraryCollectionsStateHolder

/** 层级深度：一级平台入口(0) -> 平台歌手/专辑二级(1) -> 歌手/专辑歌曲三级(2) */
private fun collectionsDepth(state: Pair<String?, CollectionDetail?>): Int = when {
    state.second != null -> 2
    state.first != null -> 1
    else -> 0
}

/**
 * “收藏”分类的三级结构（纯在线内容，无本地一层）：
 * - 一级：已登录平台入口列表
 * - 二级：平台“歌手/专辑”两个页签的内容列表
 * - 三级：歌手热门歌曲 / 专辑曲目
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryCollectionsTab(
    collectionsScope: String?,
    loggedInPlatforms: List<String>,
    collectionsTab: CollectionsContentTab,
    platformArtists: List<LxArtist>,
    artistsLoading: Boolean,
    artistsError: String?,
    platformAlbums: List<LxAlbum>,
    albumsLoading: Boolean,
    albumsError: String?,
    selectedDetail: CollectionDetail?,
    detailSongs: List<LxSong>,
    detailSongsLoading: Boolean,
    detailSongsError: String?,
    onSelectCollectionsScope: (String?) -> Unit,
    onSelectCollectionsTab: (CollectionsContentTab) -> Unit,
    onOpenArtist: (LxArtist) -> Unit,
    onOpenAlbum: (LxAlbum) -> Unit,
    onPlayDetailSong: (LxSong) -> Unit,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    onBackToEntries: () -> Unit,
    onCloseDetail: () -> Unit
) {
    AnimatedContent(
        targetState = collectionsScope to selectedDetail,
        label = "CollectionsScopeNavigation",
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            // 一级 -> 二级 -> 三级 为前进（向左滑入），返回为后退（向右滑入），与“歌单”三级结构一致
            val goingDeeper = collectionsDepth(targetState) > collectionsDepth(initialState)
            val slideIn = slideInHorizontally { width ->
                if (goingDeeper) width else -width
            } + fadeIn()
            val slideOut = slideOutHorizontally { width ->
                if (goingDeeper) -width else width
            } + fadeOut()
            slideIn togetherWith slideOut
        }
    ) { (scope, detail) ->
        when {
            detail != null -> LibraryCollectionDetailSongsContent(
                title = detail.name,
                songs = detailSongs,
                isLoading = detailSongsLoading,
                error = detailSongsError,
                onPlaySong = onPlayDetailSong,
                onRefresh = onRefresh,
                bottomBarHeight = bottomBarHeight,
                onBack = onCloseDetail
            )
            scope != null -> LibraryPlatformCollectionsContent(
                source = scope,
                collectionsTab = collectionsTab,
                artists = platformArtists,
                artistsLoading = artistsLoading,
                artistsError = artistsError,
                albums = platformAlbums,
                albumsLoading = albumsLoading,
                albumsError = albumsError,
                onSelectTab = onSelectCollectionsTab,
                onOpenArtist = onOpenArtist,
                onOpenAlbum = onOpenAlbum,
                onRefresh = onRefresh,
                bottomBarHeight = bottomBarHeight,
                onBack = onBackToEntries
            )
            else -> LibraryCollectionsEntriesList(
                loggedInPlatforms = loggedInPlatforms,
                onSelect = onSelectCollectionsScope,
                bottomBarHeight = bottomBarHeight
            )
        }
    }
}

/**
 * “收藏”一级平台入口列表：仅显示已登录平台（wy/tx/kg/kw 顺序），无本软件行。
 */
@Composable
private fun LibraryCollectionsEntriesList(
    loggedInPlatforms: List<String>,
    onSelect: (String) -> Unit,
    bottomBarHeight: Dp
) {
    if (loggedInPlatforms.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.library_collections_not_logged_in),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
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
        itemsIndexed(
            items = loggedInPlatforms,
            key = { _, source -> source }
        ) { _, source ->
            LibraryCollectionEntryRow(
                title = LxSources.labelRes(source)?.let { stringResource(it) }
                    ?: LxSources.shortLabel(source),
                onClick = { onSelect(source) },
                leading = {
                    // 与“我喜欢/歌单”一级平台行同款：24dp 槽位内居中 10dp 品牌色圆点
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
private fun LibraryCollectionEntryRow(
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
 * 平台收藏二级：顶部返回行 + “歌手/专辑”页签；页签内容为列表（酷我显示不可用提示）。
 */
@Composable
private fun LibraryPlatformCollectionsContent(
    source: String,
    collectionsTab: CollectionsContentTab,
    artists: List<LxArtist>,
    artistsLoading: Boolean,
    artistsError: String?,
    albums: List<LxAlbum>,
    albumsLoading: Boolean,
    albumsError: String?,
    onSelectTab: (CollectionsContentTab) -> Unit,
    onOpenArtist: (LxArtist) -> Unit,
    onOpenAlbum: (LxAlbum) -> Unit,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LibraryLikedSubScreenHeader(
            title = LxSources.labelRes(source)?.let { stringResource(it) }
                ?: LxSources.shortLabel(source),
            onBack = onBack
        )
        // “歌手/专辑”页签行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = collectionsTab == CollectionsContentTab.ARTISTS,
                onClick = { onSelectTab(CollectionsContentTab.ARTISTS) },
                label = { Text(stringResource(R.string.library_tab_artists)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            FilterChip(
                selected = collectionsTab == CollectionsContentTab.ALBUMS,
                onClick = { onSelectTab(CollectionsContentTab.ALBUMS) },
                label = { Text(stringResource(R.string.library_tab_albums)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (!LibraryCollectionsStateHolder.isPlatformSupported(source)) {
            // 酷我无关注歌手/收藏专辑读取协议：显示不可用提示，不展示伪造数据
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.library_collections_platform_unavailable),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return
        }
        Box(modifier = Modifier.weight(1f)) {
            when (collectionsTab) {
                CollectionsContentTab.ARTISTS -> LibraryPlatformArtistsContent(
                    artists = artists,
                    isLoading = artistsLoading,
                    error = artistsError,
                    onOpenArtist = onOpenArtist,
                    onRefresh = onRefresh,
                    bottomBarHeight = bottomBarHeight
                )
                CollectionsContentTab.ALBUMS -> LibraryPlatformAlbumsContent(
                    albums = albums,
                    isLoading = albumsLoading,
                    error = albumsError,
                    onOpenAlbum = onOpenAlbum,
                    onRefresh = onRefresh,
                    bottomBarHeight = bottomBarHeight
                )
            }
        }
    }
}

/**
 * 平台收藏歌手列表：loading / 错误重试 / 空态 / 下拉强刷。
 */
@Composable
private fun LibraryPlatformArtistsContent(
    artists: List<LxArtist>,
    isLoading: Boolean,
    error: String?,
    onOpenArtist: (LxArtist) -> Unit,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp
) {
    ListScaffold(
        isLoading = isLoading,
        isEmpty = artists.isEmpty(),
        error = error,
        emptyTextRes = R.string.lx_msg_no_followed_artists,
        onRefresh = onRefresh,
        bottomBarHeight = bottomBarHeight
    ) {
        itemsIndexed(
            items = artists,
            key = { _, artist -> "artist_${artist.id}" },
            contentType = { _, _ -> "collection_artist" }
        ) { _, artist ->
            LibraryCollectionArtistRow(
                artist = artist,
                onClick = { onOpenArtist(artist) }
            )
        }
    }
}

/**
 * 平台收藏专辑列表：loading / 错误重试 / 空态 / 下拉强刷。
 */
@Composable
private fun LibraryPlatformAlbumsContent(
    albums: List<LxAlbum>,
    isLoading: Boolean,
    error: String?,
    onOpenAlbum: (LxAlbum) -> Unit,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp
) {
    ListScaffold(
        isLoading = isLoading,
        isEmpty = albums.isEmpty(),
        error = error,
        emptyTextRes = R.string.lx_msg_no_albums,
        onRefresh = onRefresh,
        bottomBarHeight = bottomBarHeight
    ) {
        itemsIndexed(
            items = albums,
            key = { _, album -> "album_${album.id}" },
            contentType = { _, _ -> "collection_album" }
        ) { _, album ->
            LibraryCollectionAlbumRow(
                album = album,
                onClick = { onOpenAlbum(album) }
            )
        }
    }
}

/** 歌手/专辑两个列表共用的骨架：loading / 错误重试 / 空态 / 下拉强刷 + 圆角列表容器 */
@Composable
private fun ListScaffold(
    isLoading: Boolean,
    isEmpty: Boolean,
    error: String?,
    emptyTextRes: Int,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    content: LazyListScope.() -> Unit
) {
    val listState = rememberLazyListState()
    when {
        isLoading && isEmpty -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }
        error != null && isEmpty -> {
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
            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isLoading,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                if (isEmpty) {
                    // verticalScroll 建立嵌套滚动链，否则空态没有滚动子组件时下拉刷新手势收不到
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(emptyTextRes),
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
                        contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap),
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCollectionArtistRow(
    artist: LxArtist,
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
            model = artist.pic,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            shape = CircleShape
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (artist.albumSize > 0) {
                Text(
                    text = stringResource(R.string.lx_text_album_count_format, artist.albumSize),
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

@Composable
private fun LibraryCollectionAlbumRow(
    album: LxAlbum,
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
            model = album.pic,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOfNotNull(
                album.artistName?.takeIf { it.isNotBlank() },
                album.size.takeIf { it > 0 }
                    ?.let { stringResource(R.string.lx_text_track_count_format, it) }
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
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
 * 收藏三级歌曲列表（歌手热门歌曲/专辑曲目）：loading / 错误重试 / 空态 / 下拉强刷，
 * 骨架与“歌单”三级歌曲列表一致。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LibraryCollectionDetailSongsContent(
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
                            // 同上：空态需要可滚动容器才能把下拉手势传给 PullToRefreshBox
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
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
                                    contentType = { _, _ -> "collection_song" }
                                ) { _, song ->
                                    EnhancedSongListItem(
                                        song = remember(song) { LxSongMapper.toSong(song) },
                                        isPlaying = false,
                                        isCurrentSong = false,
                                        isLoading = false,
                                        // 同一歌手/专辑内所有歌同源，不再逐行显示平台标签
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
