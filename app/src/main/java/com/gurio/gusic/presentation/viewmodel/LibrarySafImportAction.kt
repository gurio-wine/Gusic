package com.gurio.gusic.presentation.viewmodel

import android.net.Uri
import com.gurio.gusic.data.library.SafSongImporter
import javax.inject.Inject
import javax.inject.Singleton

/** Converts an explicitly selected SAF batch into the summary consumed by Library UI. */
@Singleton
class LibrarySafImportAction @Inject constructor(
    private val importer: SafSongImporter,
) {
    suspend fun importSelected(uris: List<Uri>): LibraryImportSummary =
        importer.importSelected(uris).toLibraryImportSummary()
}
