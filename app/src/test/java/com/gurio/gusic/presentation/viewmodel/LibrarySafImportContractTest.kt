package com.gurio.gusic.presentation.viewmodel

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.gurio.gusic.data.library.PersistedUriReadPermission
import com.gurio.gusic.data.library.PersistedUriPermissionStore
import com.gurio.gusic.data.library.SafAudioMetadata
import com.gurio.gusic.data.library.SafAudioMetadataReader
import com.gurio.gusic.data.library.SafDocumentIdentity
import com.gurio.gusic.data.library.SafDocumentIdentityReader
import com.gurio.gusic.data.library.SafImportResult
import com.gurio.gusic.data.library.SafImportStatus
import com.gurio.gusic.data.library.SafPersistedReadGrantVerifier
import com.gurio.gusic.data.library.SafSongImporter
import com.gurio.gusic.data.library.SafSongPersistence
import com.gurio.gusic.data.library.SafImportReport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** SAF selected-audio contract only; M3U remains on its separate OpenDocument/PlaylistViewModel route. */
class LibrarySafImportContractTest {
    @Test
    fun `multi selection keeps input order and retains each item failure`() = runTest {
        val importedUri = mockk<Uri>()
        val metadataFailureUri = mockk<Uri>()
        val persistenceFailureUri = mockk<Uri>()
        val selectedUris = listOf(importedUri, metadataFailureUri, persistenceFailureUri)
        val audioMetadata = SafAudioMetadata(
            title = "Test track",
            artist = "Test artist",
            album = "Test album",
            albumArtist = "Test artist",
            durationMillis = 1_000L,
            trackNumber = 1,
            year = 2024,
            genre = null,
            mimeType = "audio/mpeg",
            displayName = "test-track.mp3",
        )
        val importer = SafSongImporter(
            grantVerifier = SafPersistedReadGrantVerifier(object : PersistedUriPermissionStore {
                override fun takePersistableReadPermission(uri: Uri) = Unit

                override fun persistedUriReadPermissions() = selectedUris.map { uri ->
                    PersistedUriReadPermission(uri, isReadPermission = true)
                }
            }),
            metadataReader = SafAudioMetadataReader { uri ->
                if (uri == metadataFailureUri) error("Metadata read failed")
                audioMetadata
            },
            identityReader = SafDocumentIdentityReader { uri ->
                SafDocumentIdentity("test.provider", "document-${selectedUris.indexOf(uri)}")
            },
            persistence = SafSongPersistence { uri, _, _ ->
                if (uri == persistenceFailureUri) error("Room insert failed")
                SafImportResult(uri, SafImportStatus.IMPORTED, songId = 101L)
            },
        )

        val report = importer.importSelected(selectedUris)

        assertThat(report.results).containsExactly(
            SafImportResult(importedUri, SafImportStatus.IMPORTED, songId = 101L),
            SafImportResult(metadataFailureUri, SafImportStatus.FAILED, error = "Metadata read failed"),
            SafImportResult(persistenceFailureUri, SafImportStatus.FAILED, error = "Room insert failed"),
        ).inOrder()
        val summary = report.toLibraryImportSummary()
        assertThat(summary.importedCount).isEqualTo(1)
        assertThat(summary.failedResults.map { it.uri }).containsExactly(
            metadataFailureUri,
            persistenceFailureUri,
        ).inOrder()
    }

    @Test
    fun `library action forwards selected URI order and maps every status`() = runTest {
        val firstUri = mockk<Uri>()
        val failedUri = mockk<Uri>()
        val duplicateUri = mockk<Uri>()
        val selectedUris = listOf(firstUri, failedUri, duplicateUri)
        val report = SafImportReport(
            results = listOf(
                SafImportResult(firstUri, SafImportStatus.IMPORTED, songId = 8L),
                SafImportResult(failedUri, SafImportStatus.FAILED, error = "No read access"),
                SafImportResult(duplicateUri, SafImportStatus.ALREADY_PRESENT, songId = 9L),
            ),
        )
        val importer = mockk<SafSongImporter>()
        coEvery { importer.importSelected(any()) } returns report

        val summary = LibrarySafImportAction(importer).importSelected(selectedUris)

        coVerify(exactly = 1) { importer.importSelected(selectedUris) }
        assertThat(summary.importedCount).isEqualTo(1)
        assertThat(summary.alreadyPresentCount).isEqualTo(1)
        assertThat(summary.failedResults).containsExactly(
            SafImportResult(failedUri, SafImportStatus.FAILED, error = "No read access"),
        ).inOrder()
    }
}
