package com.gurio.gusic.data.repository

import com.gurio.gusic.data.database.AudioBookmarkEntity
import kotlinx.coroutines.flow.Flow

interface AudioBookmarkRepository {
    fun getAllBookmarksFlow(): Flow<List<AudioBookmarkEntity>>
    suspend fun getBookmarksForSong(songId: String): List<AudioBookmarkEntity>
    suspend fun insertBookmark(bookmark: AudioBookmarkEntity)
    suspend fun deleteBookmark(id: Long)
    suspend fun getBookmarkById(id: Long): AudioBookmarkEntity?
}
