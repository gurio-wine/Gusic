package com.gurio.gusic.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.gurio.gusic.data.library.SafImportReport
import com.gurio.gusic.data.library.SafImportResult
import com.gurio.gusic.data.library.SafImportStatus
import io.mockk.mockk
import org.junit.jupiter.api.Test

class LibraryImportReportMappingTest {
    private fun uri(): android.net.Uri = mockk()

    @Test
    fun `mixed import report retains successful selections and failed details`() {
        val importedUri = uri()
        val duplicateUri = uri()
        val failedUri = uri()
        val report = SafImportReport(
            results = listOf(
                SafImportResult(
                    uri = importedUri,
                    status = SafImportStatus.IMPORTED,
                    songId = 101L
                ),
                SafImportResult(
                    uri = duplicateUri,
                    status = SafImportStatus.ALREADY_PRESENT,
                    songId = 102L
                ),
                SafImportResult(
                    uri = failedUri,
                    status = SafImportStatus.FAILED,
                    error = "Provider denied access"
                )
            )
        )

        val summary = report.toLibraryImportSummary()

        assertThat(summary.importedCount).isEqualTo(1)
        assertThat(summary.alreadyPresentCount).isEqualTo(1)
        assertThat(summary.failedResults).containsExactly(
            SafImportResult(
                uri = failedUri,
                status = SafImportStatus.FAILED,
                error = "Provider denied access"
            )
        )
    }

    @Test
    fun `empty picker selection maps to an empty summary`() {
        val summary = SafImportReport(results = emptyList()).toLibraryImportSummary()

        assertThat(summary.importedCount).isEqualTo(0)
        assertThat(summary.alreadyPresentCount).isEqualTo(0)
        assertThat(summary.failedResults).isEmpty()
    }
}
