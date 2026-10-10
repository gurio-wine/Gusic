package com.gurio.gusic.data.library

import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SafSongImporterTest {
    @Test
    fun `failed persisted read grant skips metadata and persistence`() = runTest {
        val uri = mockk<Uri>()
        val permissionStore = FakePermissionStore(emptyList())
        val metadata = CountingMetadataReader()
        val persistence = RecordingPersistence()
        val importer = SafSongImporter(
            grantVerifier = SafPersistedReadGrantVerifier(permissionStore),
            metadataReader = metadata,
            identityReader = SafDocumentIdentityReader { error("identity must not be read") },
            persistence = persistence,
        )

        val result = importer.importSelected(listOf(uri)).results.single()

        assertEquals(SafImportStatus.FAILED, result.status)
        assertFalse(metadata.called)
        assertEquals(0, persistence.calls)
        assertTrue(permissionStore.takeCalled)
    }

    @Test
    fun `non audio selections are rejected before persistence`() = runTest {
        val uri = mockk<Uri>()
        val metadata = CountingMetadataReader(mimeType = "application/pdf")
        val persistence = RecordingPersistence()
        val importer = SafSongImporter(
            grantVerifier = SafPersistedReadGrantVerifier(FakePermissionStore(
                listOf(PersistedUriReadPermission(uri, isReadPermission = true)),
            )),
            metadataReader = metadata,
            identityReader = SafDocumentIdentityReader { SafDocumentIdentity("provider", "opaque") },
            persistence = persistence,
        )

        val result = importer.importSelected(listOf(uri)).results.single()

        assertEquals(SafImportStatus.FAILED, result.status)
        assertEquals(0, persistence.calls)
    }

    @Test
    fun `audio selection delegates persistence and preserves returned stable id`() = runTest {
        val uri = mockk<Uri>()
        val expected = SafImportResult(uri, SafImportStatus.IMPORTED, songId = 88L)
        val persistence = RecordingPersistence(expected)
        val importer = SafSongImporter(
            grantVerifier = SafPersistedReadGrantVerifier(FakePermissionStore(
                listOf(PersistedUriReadPermission(uri, isReadPermission = true)),
            )),
            metadataReader = CountingMetadataReader(mimeType = "audio/mpeg"),
            identityReader = SafDocumentIdentityReader { SafDocumentIdentity("provider", "opaque") },
            persistence = persistence,
        )

        val result = importer.importSelected(listOf(uri)).results.single()

        assertEquals(expected, result)
        assertEquals(1, persistence.calls)
    }

    private class CountingMetadataReader(private val mimeType: String? = "audio/mpeg") : SafAudioMetadataReader {
        var called = false
            private set

        override fun read(uri: Uri): SafAudioMetadata {
            called = true
            return SafAudioMetadata(
                title = "title", artist = "artist", album = "album", albumArtist = "artist",
                durationMillis = 1000L, trackNumber = 1, year = 2024, genre = null,
                mimeType = mimeType, displayName = "title.mp3",
            )
        }
    }

    private class RecordingPersistence(
        private val result: SafImportResult = SafImportResult(mockk(), SafImportStatus.IMPORTED, 88L),
    ) : SafSongPersistence {
        var calls = 0
            private set

        override suspend fun persist(
            uri: Uri,
            identity: SafDocumentIdentity,
            metadata: SafAudioMetadata,
        ): SafImportResult {
            calls++
            return result.copy(uri = uri)
        }
    }

    private class FakePermissionStore(
        private val permissions: List<PersistedUriReadPermission>,
    ) : PersistedUriPermissionStore {
        var takeCalled = false
            private set

        override fun takePersistableReadPermission(uri: Uri) {
            takeCalled = true
        }

        override fun persistedUriReadPermissions() = permissions
    }
}
