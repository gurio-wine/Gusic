package com.gurio.gusic.data.library

import android.net.Uri

/** Result for one SAF document, preserving input order in [SafImportReport.results]. */
data class SafImportResult(
    val uri: Uri,
    val status: SafImportStatus,
    val songId: Long? = null,
    val error: String? = null,
)

enum class SafImportStatus {
    IMPORTED,
    ALREADY_PRESENT,
    FAILED,
}

data class SafImportReport(
    val results: List<SafImportResult>,
)
