package com.gurio.gusic.presentation.viewmodel

import com.gurio.gusic.data.library.SafImportReport
import com.gurio.gusic.data.library.SafImportResult
import com.gurio.gusic.data.library.SafImportStatus

/** UI-facing counts and the exact failures from a multi-document import. */
data class LibraryImportSummary(
    val importedCount: Int,
    val alreadyPresentCount: Int,
    val failedResults: List<SafImportResult>,
)

fun SafImportReport.toLibraryImportSummary(): LibraryImportSummary = LibraryImportSummary(
    importedCount = results.count { it.status == SafImportStatus.IMPORTED },
    alreadyPresentCount = results.count { it.status == SafImportStatus.ALREADY_PRESENT },
    failedResults = results.filter { it.status == SafImportStatus.FAILED },
)
