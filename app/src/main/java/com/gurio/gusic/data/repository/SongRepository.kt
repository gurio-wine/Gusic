package com.gurio.gusic.data.repository

import androidx.paging.PagingData
import com.gurio.gusic.data.model.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getPaginatedSongs(
        sortOption: com.gurio.gusic.data.model.SortOption,
        storageFilter: com.gurio.gusic.data.model.StorageFilter
    ): Flow<PagingData<Song>>

    fun getPaginatedFavoriteSongs(
        sortOption: com.gurio.gusic.data.model.SortOption,
        storageFilter: com.gurio.gusic.data.model.StorageFilter
    ): Flow<PagingData<Song>>

    suspend fun getFavoriteSongsOnce(
        storageFilter: com.gurio.gusic.data.model.StorageFilter
    ): List<Song>

    fun getFavoriteSongCountFlow(
        storageFilter: com.gurio.gusic.data.model.StorageFilter
    ): Flow<Int>
}
