package com.gurio.gusic.data.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.gurio.gusic.R

@Immutable
enum class LibraryTabId(
    val storageKey: String,
    @StringRes val titleRes: Int,
    val defaultSort: SortOption
) {
    SONGS("SONGS", R.string.library_tab_songs, SortOption.SongRecentlyPlayed),
    ALBUMS("ALBUMS", R.string.library_tab_albums, SortOption.AlbumTitleAZ),
    ARTISTS("ARTIST", R.string.library_tab_artists, SortOption.ArtistNameAZ),
    PLAYLISTS("PLAYLISTS", R.string.library_tab_playlists, SortOption.PlaylistNameAZ),
    FOLDERS("FOLDERS", R.string.library_tab_folders, SortOption.FolderNameAZ),
    LIKED("LIKED", R.string.library_tab_liked, SortOption.LikedSongDateLiked),
    // 平台收藏内容（歌手/专辑两级）；纯在线内容不提供本地排序，defaultSort 仅为占位
    COLLECTIONS("COLLECTIONS", R.string.library_tab_collections, SortOption.PlaylistNameAZ);

    companion object {
        fun fromStorageKey(key: String): LibraryTabId =
            entries.firstOrNull { it.storageKey == key } ?: SONGS
    }
}

fun String.toLibraryTabIdOrNull(): LibraryTabId? =
    LibraryTabId.entries.firstOrNull { it.storageKey == this }