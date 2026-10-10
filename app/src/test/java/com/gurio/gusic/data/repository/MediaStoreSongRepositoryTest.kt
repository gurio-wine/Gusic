package com.gurio.gusic.data.repository

import com.gurio.gusic.data.database.MusicDao
import com.gurio.gusic.data.database.SongEntity
import com.gurio.gusic.data.model.StorageFilter
import com.gurio.gusic.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaStoreSongRepositoryTest {

    @Test
    fun `favorite songs once uses Room and marks returned songs as favorite`() = runBlocking {
        val musicDao = mockk<MusicDao>()
        val preferences = mockk<UserPreferencesRepository>()
        val repository = MediaStoreSongRepository(musicDao, preferences)
        val song = SongEntity(
            id = 42L,
            title = "Room song",
            artistName = "Artist",
            artistId = 1L,
            albumName = "Album",
            albumId = 2L,
            contentUriString = "content://media/42",
            albumArtUriString = null,
            duration = 180_000L,
            genre = "Rock",
            filePath = "/music/room-song.mp3",
            parentDirectoryPath = "/music"
        )

        every { preferences.allowedDirectoriesFlow } returns flowOf(emptySet())
        every { preferences.blockedDirectoriesFlow } returns flowOf(emptySet())
        coEvery {
            musicDao.getFavoriteSongsList(
                allowedParentDirs = emptyList(),
                applyDirectoryFilter = false,
                filterMode = StorageFilter.ALL.value
            )
        } returns listOf(song)

        val result = repository.getFavoriteSongsOnce(StorageFilter.ALL)

        assertEquals(listOf("42"), result.map { it.id })
        assertEquals("Room song", result.single().title)
        assertTrue(result.single().isFavorite)
        coVerify(exactly = 1) {
            musicDao.getFavoriteSongsList(emptyList(), false, StorageFilter.ALL.value)
        }
        verify(exactly = 1) {
            preferences.allowedDirectoriesFlow
            preferences.blockedDirectoriesFlow
        }
    }

    @Test
    fun `favorite count flow forwards Room count and storage filter`() = runBlocking {
        val musicDao = mockk<MusicDao>()
        val preferences = mockk<UserPreferencesRepository>()
        val repository = MediaStoreSongRepository(musicDao, preferences)

        every { preferences.allowedDirectoriesFlow } returns flowOf(emptySet())
        every { preferences.blockedDirectoriesFlow } returns flowOf(emptySet())
        every {
            musicDao.getFavoriteSongCount(
                allowedParentDirs = emptyList(),
                applyDirectoryFilter = false,
                filterMode = StorageFilter.OFFLINE.value
            )
        } returns flowOf(3)

        val result = repository.getFavoriteSongCountFlow(StorageFilter.OFFLINE).first()

        assertEquals(3, result)
        verify(exactly = 1) {
            musicDao.getFavoriteSongCount(emptyList(), false, StorageFilter.OFFLINE.value)
        }
    }
}
