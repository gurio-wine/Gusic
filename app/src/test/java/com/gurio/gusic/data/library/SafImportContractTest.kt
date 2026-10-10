package com.gurio.gusic.data.library

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SafImportContractTest {
    @Test
    fun `report retains ordered per document results and defaults optional fields`() {
        val uri = mockk<android.net.Uri>(relaxed = true)
        val failed = SafImportResult(uri, SafImportStatus.FAILED, error = "Permission denied")
        val report = SafImportReport(listOf(failed))

        assertEquals(listOf(failed), report.results)
        assertNull(failed.songId)
    }

    @Test
    fun `SAF source is distinct from cloud and MediaStore local`() {
        assertFalse(com.gurio.gusic.data.database.SourceType.isCloud(
            com.gurio.gusic.data.database.SourceType.SAF,
        ))
        assertFalse(com.gurio.gusic.data.database.SourceType.isMediaStoreOwned(
            com.gurio.gusic.data.database.SourceType.SAF,
        ))
    }
}
