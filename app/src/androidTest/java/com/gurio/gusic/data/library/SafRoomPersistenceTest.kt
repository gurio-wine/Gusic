package com.gurio.gusic.data.library

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gurio.gusic.data.database.AlbumEntity
import com.gurio.gusic.data.database.ArtistEntity
import com.gurio.gusic.data.database.LibraryItemIdentityEntity
import com.gurio.gusic.data.database.GusicDatabase
import com.gurio.gusic.data.database.SongEntity
import com.gurio.gusic.data.database.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafRoomPersistenceTest {
    private lateinit var database: GusicDatabase
    private lateinit var persistence: RoomSafSongPersistence

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            GusicDatabase::class.java,
        )
            .addCallback(GusicDatabase.createRuntimeArtifactsCallback())
            .allowMainThreadQueries()
            .build()
        persistence = RoomSafSongPersistence(database)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun collidingSongIdNeverMutatesLocalCloudOrDifferentSafRow() = runTest {
        val collisionCases = listOf(
            SourceType.LOCAL to "file:///storage/emulated/0/Music/original.mp3",
            SourceType.LX_MUSIC to "lxmusic://original-song",
            SourceType.SAF to "content://provider.example/document/other-document",
            SourceType.SAF to "content://other.provider/document/requested-document-3",
        )

        collisionCases.forEachIndexed { index, (sourceType, originalUri) ->
            val songId = 10_000L + index
            val identity = SafDocumentIdentity("provider.example", "requested-document-$index")
            val namespace = "saf:${identity.authority}"
            val originalSong = seedSong(songId, sourceType, originalUri)
            val mappingUri = "content://provider.example/document/original-mapping-$index"
            database.libraryItemIdentityDao().insertIdentity(
                LibraryItemIdentityEntity(
                    itemType = LibraryItemIdentityEntity.TYPE_SONG,
                    sourceNamespace = namespace,
                    nativeId = identity.documentId,
                    entityId = songId,
                    locatorUri = mappingUri,
                    hasPersistedReadGrant = true,
                    lastSeenAt = 1L,
                ),
            )

            val result = runCatching {
                persistence.persist(
                    Uri.parse("content://provider.example/tree/root/document/${identity.documentId}"),
                    identity,
                    metadata(title = "Replacement title"),
                )
            }

            assertTrue("Expected collision to fail for sourceType=$sourceType", result.isFailure)
            assertEquals(originalSong, database.musicDao().getSongByIdOnce(songId))
            val mapping = database.libraryItemIdentityDao().findIdentity(
                LibraryItemIdentityEntity.TYPE_SONG,
                namespace,
                identity.documentId,
            )
            assertEquals(mappingUri, mapping?.locatorUri)
        }
    }

    @Test
    fun locatorMustMatchSuppliedAuthorityAndDocumentId() = runTest {
        val identity = SafDocumentIdentity("provider.example", "opaque-song-identity")
        val mismatchedUris = listOf(
            Uri.parse("content://other.provider/document/opaque-song-identity"),
            Uri.parse("content://provider.example/document/different-opaque-id"),
        )

        mismatchedUris.forEach { mismatchedUri ->
            val result = runCatching {
                persistence.persist(mismatchedUri, identity, metadata())
            }

            assertTrue("Expected URI identity mismatch for $mismatchedUri", result.isFailure)
            assertNull(
                database.libraryItemIdentityDao().findIdentity(
                    LibraryItemIdentityEntity.TYPE_SONG,
                    "saf:${identity.authority}",
                    identity.documentId,
                ),
            )
        }
    }

    @Test
    fun sameDocumentRetainsSongIdAndNewGrantUpdatesOnlyLocator() = runTest {
        val identity = SafDocumentIdentity("provider.example", "opaque-song-1")
        val firstUri = Uri.parse("content://provider.example/document/opaque-song-1")
        val secondUri = Uri.parse("content://provider.example/tree/root/document/opaque-song-1")

        val first = persistence.persist(firstUri, identity, metadata())
        val second = persistence.persist(secondUri, identity, metadata(title = "Changed title"))

        assertEquals(SafImportStatus.IMPORTED, first.status)
        assertEquals(SafImportStatus.ALREADY_PRESENT, second.status)
        assertEquals(first.songId, second.songId)
        val stored = database.musicDao().getSongByIdOnce(checkNotNull(first.songId))
        assertEquals(secondUri.toString(), stored?.contentUriString)
        assertEquals(SourceType.SAF, stored?.sourceType)
        assertEquals("Stable title", stored?.title)
    }

    @Test
    fun sameMetadataSharesAlbumAndArtistRowsAcrossDocuments() = runTest {
        val uri1 = Uri.parse("content://provider.example/document/song-1")
        val uri2 = Uri.parse("content://provider.example/document/song-2")
        val identity1 = SafDocumentIdentity("provider.example", "song-1")
        val identity2 = SafDocumentIdentity("provider.example", "song-2")
        val first = persistence.persist(uri1, identity1, metadata())
        val second = persistence.persist(uri2, identity2, metadata())

        assertNotEquals(first.songId, second.songId)
        val firstSong = database.musicDao().getSongByIdOnce(checkNotNull(first.songId))
        val secondSong = database.musicDao().getSongByIdOnce(checkNotNull(second.songId))
        assertEquals(firstSong?.albumId, secondSong?.albumId)
        assertEquals(firstSong?.artistId, secondSong?.artistId)
        val albumMapping1 = database.libraryItemIdentityDao().findIdentity(
            LibraryItemIdentityEntity.TYPE_ALBUM,
            "saf:provider.example",
            SafGroupIdentity.albumNativeId("Album", "Artist", identity1.documentId),
        )
        val albumMapping2 = database.libraryItemIdentityDao().findIdentity(
            LibraryItemIdentityEntity.TYPE_ALBUM,
            "saf:provider.example",
            SafGroupIdentity.albumNativeId("Album", "Artist", identity2.documentId),
        )
        assertNotNull(albumMapping1)
        assertEquals(albumMapping1?.entityId, albumMapping2?.entityId)
    }

    @Test
    fun missingTrackArtistUsesDocumentFallbackButKnownAlbumArtistStillGroupsAlbum() = runTest {
        val firstIdentity = SafDocumentIdentity("provider.example", "missing-artist-1")
        val secondIdentity = SafDocumentIdentity("provider.example", "missing-artist-2")
        val first = persistence.persist(
            Uri.parse("content://provider.example/document/missing-artist-1"),
            firstIdentity,
            metadata().copy(artist = null, albumArtist = "Known Album Artist"),
        )
        val second = persistence.persist(
            Uri.parse("content://provider.example/document/missing-artist-2"),
            secondIdentity,
            metadata().copy(artist = null, albumArtist = "Known Album Artist"),
        )
        val firstSong = database.musicDao().getSongByIdOnce(checkNotNull(first.songId))
        val secondSong = database.musicDao().getSongByIdOnce(checkNotNull(second.songId))

        assertNotEquals(firstSong?.artistId, secondSong?.artistId)
        assertEquals(firstSong?.albumId, secondSong?.albumId)
        assertEquals(firstSong?.albumArtistId, secondSong?.albumArtistId)
    }

    @Test
    fun missingAlbumNameUsesDocumentSpecificAlbumRows() = runTest {
        val first = persistence.persist(
            Uri.parse("content://provider.example/document/missing-album-1"),
            SafDocumentIdentity("provider.example", "missing-album-1"),
            metadata().copy(album = null),
        )
        val second = persistence.persist(
            Uri.parse("content://provider.example/document/missing-album-2"),
            SafDocumentIdentity("provider.example", "missing-album-2"),
            metadata().copy(album = null),
        )
        val firstSong = database.musicDao().getSongByIdOnce(checkNotNull(first.songId))
        val secondSong = database.musicDao().getSongByIdOnce(checkNotNull(second.songId))

        assertNotEquals(firstSong?.albumId, secondSong?.albumId)
    }

    @Test
    fun missingTrackAndAlbumArtistUseDocumentSpecificArtistRows() = runTest {
        val first = persistence.persist(
            Uri.parse("content://provider.example/document/missing-artists-1"),
            SafDocumentIdentity("provider.example", "missing-artists-1"),
            metadata().copy(artist = null, albumArtist = null),
        )
        val second = persistence.persist(
            Uri.parse("content://provider.example/document/missing-artists-2"),
            SafDocumentIdentity("provider.example", "missing-artists-2"),
            metadata().copy(artist = null, albumArtist = null),
        )
        val firstSong = database.musicDao().getSongByIdOnce(checkNotNull(first.songId))
        val secondSong = database.musicDao().getSongByIdOnce(checkNotNull(second.songId))

        assertNotEquals(firstSong?.artistId, secondSong?.artistId)
        assertNotEquals(firstSong?.albumArtistId, secondSong?.albumArtistId)
        assertNotEquals(firstSong?.albumId, secondSong?.albumId)
    }

    @Test
    fun missingAlbumArtistTagFallsBackToTrackArtistIdentity() = runTest {
        val identity1 = SafDocumentIdentity("provider.example", "missing-album-artist-1")
        val identity2 = SafDocumentIdentity("provider.example", "missing-album-artist-2")
        val first = persistence.persist(
            Uri.parse("content://provider.example/document/missing-album-artist-1"),
            identity1,
            metadata().copy(albumArtist = null),
        )
        val second = persistence.persist(
            Uri.parse("content://provider.example/document/missing-album-artist-2"),
            identity2,
            metadata().copy(albumArtist = null),
        )
        val firstSong = database.musicDao().getSongByIdOnce(checkNotNull(first.songId))
        val secondSong = database.musicDao().getSongByIdOnce(checkNotNull(second.songId))

        assertEquals(firstSong?.artistId, firstSong?.albumArtistId)
        assertEquals(secondSong?.artistId, secondSong?.albumArtistId)
        assertEquals(firstSong?.albumArtistId, secondSong?.albumArtistId)
        assertEquals(firstSong?.albumId, secondSong?.albumId)
    }

    @Test
    fun existingSongIdentityWithoutSongRowIsRepairedAndReportedImported() = runTest {
        val identity = SafDocumentIdentity("provider.example", "orphan-song")
        val namespace = "saf:${identity.authority}"
        val reservation = checkNotNull(database.libraryItemIdentityDao().reserveId(
            itemType = LibraryItemIdentityEntity.TYPE_SONG,
            namespace = namespace,
            nativeId = identity.documentId,
            locatorUri = "content://provider.example/document/orphan-song",
            hasReadGrant = true,
            lastSeenAt = 1L,
        ))
        val uri = Uri.parse("content://provider.example/tree/root/document/orphan-song")

        val result = persistence.persist(uri, identity, metadata())
        val songRow = database.musicDao().getSongByIdOnce(reservation.entityId)
        val mapping = database.libraryItemIdentityDao().findIdentity(
            LibraryItemIdentityEntity.TYPE_SONG,
            namespace,
            identity.documentId,
        )

        assertEquals(SafImportStatus.IMPORTED, result.status)
        assertEquals(reservation.entityId, result.songId)
        assertNotNull(songRow)
        assertEquals(uri.toString(), mapping?.locatorUri)
        assertTrue(mapping?.hasPersistedReadGrant == true)
    }

    @Test
    fun positiveIdSafSongIsVisibleWithDirectoryFilterEnabled() = runTest {
        val uri = Uri.parse("content://provider.example/document/song-visible")
        val imported = persistence.persist(
            uri,
            SafDocumentIdentity("provider.example", "song-visible"),
            metadata(),
        )

        val visibleSongs = database.musicDao().getSongs(
            allowedParentDirs = emptyList(),
            applyDirectoryFilter = true,
        ).first()

        assertTrue(visibleSongs.any { it.id == imported.songId })
    }

    @Test
    fun failedRegrantDoesNotReplacePreviouslyStoredLocator() = runTest {
        val identity = SafDocumentIdentity("provider.example", "retained")
        val oldUri = Uri.parse("content://provider.example/document/retained")
        val newUri = Uri.parse("content://provider.example/tree/root/document/retained")
        val original = persistence.persist(oldUri, identity, metadata())
        val permissionStore = object : PersistedUriPermissionStore {
            override fun takePersistableReadPermission(uri: Uri) = Unit
            override fun persistedUriReadPermissions() = emptyList<PersistedUriReadPermission>()
        }
        val importer = SafSongImporter(
            grantVerifier = SafPersistedReadGrantVerifier(permissionStore),
            metadataReader = SafAudioMetadataReader { metadata() },
            identityReader = SafDocumentIdentityReader { identity },
            persistence = persistence,
        )

        val report = importer.importSelected(listOf(newUri))
        val stored = database.musicDao().getSongByIdOnce(checkNotNull(original.songId))

        assertEquals(SafImportStatus.FAILED, report.results.single().status)
        assertEquals(oldUri.toString(), stored?.contentUriString)
        assertFalse(stored?.contentUriString == newUri.toString())
    }

    private suspend fun seedSong(songId: Long, sourceType: Int, contentUri: String): SongEntity {
        val artistId = songId + 1_000_000L
        val albumId = songId + 2_000_000L
        val artist = ArtistEntity(artistId, "Original Artist", trackCount = 1)
        val album = AlbumEntity(
            id = albumId,
            title = "Original Album",
            artistName = artist.name,
            artistId = artist.id,
            albumArtUriString = null,
            songCount = 1,
            dateAdded = 1L,
            year = 2020,
            albumArtist = artist.name,
        )
        val song = SongEntity(
            id = songId,
            title = "Original title",
            artistName = artist.name,
            artistId = artist.id,
            albumArtist = artist.name,
            albumArtistId = artist.id,
            albumName = album.title,
            albumId = album.id,
            contentUriString = contentUri,
            albumArtUriString = null,
            duration = 4321L,
            genre = "Original genre",
            filePath = "",
            parentDirectoryPath = "",
            sourceType = sourceType,
        )
        database.musicDao().insertArtistsIgnoreConflicts(listOf(artist))
        database.musicDao().insertAlbumsIgnoreConflicts(listOf(album))
        database.musicDao().insertSongsIgnoreConflicts(listOf(song))
        return song
    }

    private fun metadata(title: String = "Stable title") = SafAudioMetadata(
        title = title,
        artist = "Artist",
        album = "Album",
        albumArtist = "Artist",
        durationMillis = 1234L,
        trackNumber = 1,
        year = 2024,
        genre = "Jazz",
        mimeType = "audio/mpeg",
        displayName = "$title.mp3",
    )
}
