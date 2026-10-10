package com.gurio.gusic.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryItemIdentityDaoTest {

    private lateinit var db: PixelPlayerDatabase
    private lateinit var dao: LibraryItemIdentityDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PixelPlayerDatabase::class.java)
            .addCallback(PixelPlayerDatabase.createRuntimeArtifactsCallback())
            .allowMainThreadQueries()
            .build()
        dao = db.libraryItemIdentityDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun deniedRegrantDoesNotChangeExistingLocatorOrIdentityState() = runTest {
        val original = LibraryItemIdentityEntity(
            itemType = LibraryItemIdentityEntity.TYPE_SONG,
            sourceNamespace = "saf:provider.example",
            nativeId = "Opaque:ID/CaseSensitive",
            entityId = 42L,
            locatorUri = "content://provider.example/tree/root/document/Opaque%3AID%2FCaseSensitive",
            hasPersistedReadGrant = false,
            isMissing = true,
            lastSeenAt = 100L,
        )
        dao.insertIdentity(original)

        val result = dao.reserveId(
            itemType = original.itemType,
            namespace = original.sourceNamespace,
            nativeId = original.nativeId,
            locatorUri = "content://provider.example/document/Other",
            hasReadGrant = false,
            lastSeenAt = 999L,
        )

        assertNull(result)
        assertEquals(original, dao.findIdentity(original.itemType, original.sourceNamespace, original.nativeId))
    }

    @Test
    fun newIdentityIdDoesNotCollideWithSongAlbumOrArtistRows() = runTest {
        db.musicDao().insertArtists(listOf(ArtistEntity(id = 1L, name = "Artist", trackCount = 0)))
        db.musicDao().insertAlbums(
            listOf(
                AlbumEntity(
                    id = 2L,
                    title = "Album",
                    artistName = "Artist",
                    artistId = 1L,
                    albumArtUriString = null,
                    songCount = 0,
                    dateAdded = 0L,
                    year = 0,
                ),
            ),
        )
        db.musicDao().insertSongs(
            listOf(
                SongEntity(
                    id = 3L,
                    title = "Song",
                    artistName = "Artist",
                    artistId = 1L,
                    albumName = "Album",
                    albumId = 2L,
                    contentUriString = "content://media/external/audio/media/3",
                    albumArtUriString = null,
                    duration = 1L,
                    genre = null,
                    filePath = "/song.mp3",
                    parentDirectoryPath = "/",
                ),
            ),
        )

        val reservation = dao.reserveId(
            itemType = LibraryItemIdentityEntity.TYPE_SONG,
            namespace = "saf:provider.example",
            nativeId = "doc-id-1",
            locatorUri = "content://provider.example/document/doc-id-1",
            hasReadGrant = true,
            lastSeenAt = 1L,
        )

        assertNotNull(reservation)
        assertEquals(4L, reservation!!.entityId)
        assertFalse(reservation.alreadyExisted)
        assertTrue(dao.libraryIdExists(4L))
    }

    @Test
    fun sameNaturalIdentityKeepsReservedIdAndOnlyUpdatesAccessLocatorAfterGrant() = runTest {
        val first = dao.reserveId(
            itemType = LibraryItemIdentityEntity.TYPE_SONG,
            namespace = "saf:provider.example",
            nativeId = "opaque-id",
            locatorUri = "content://provider.example/document/opaque-id",
            hasReadGrant = true,
            lastSeenAt = 1L,
        )
        assertNotNull(first)

        val updated = dao.reserveId(
            itemType = LibraryItemIdentityEntity.TYPE_SONG,
            namespace = "saf:provider.example",
            nativeId = "opaque-id",
            locatorUri = "content://provider.example/tree/root/document/opaque-id",
            hasReadGrant = true,
            lastSeenAt = 2L,
        )

        assertNotNull(updated)
        assertEquals(first!!.entityId, updated!!.entityId)
        assertTrue(updated.alreadyExisted)
        val stored = dao.findIdentity("song", "saf:provider.example", "opaque-id")
        assertEquals("content://provider.example/tree/root/document/opaque-id", stored?.locatorUri)
        assertTrue(stored?.hasPersistedReadGrant == true)
        assertFalse(stored?.isMissing == true)
        assertEquals(2L, stored?.lastSeenAt)
    }
}
