package com.gurio.gusic.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibrarySafImportUiState(
    val isImporting: Boolean = false,
    val summary: LibraryImportSummary? = null,
    val unexpectedError: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryStateHolder: LibraryStateHolder,
    private val safImportAction: LibrarySafImportAction,
    val liked: LibraryLikedStateHolder,
    val playlists: LibraryPlaylistsStateHolder,
) : ViewModel() {

    private val _safImportUiState = MutableStateFlow(LibrarySafImportUiState())
    val safImportUiState: StateFlow<LibrarySafImportUiState> = _safImportUiState.asStateFlow()

    val songsPagingFlow = libraryStateHolder.songsPagingFlow.cachedIn(viewModelScope)

    val albumsPagingFlow = libraryStateHolder.albumsPagingFlow.cachedIn(viewModelScope)

    val artistsPagingFlow = libraryStateHolder.artistsPagingFlow.cachedIn(viewModelScope)

    val favoritesPagingFlow = libraryStateHolder.favoritesPagingFlow.cachedIn(viewModelScope)

    val favoriteSongCountFlow = libraryStateHolder.favoriteSongCountFlow

    val isLoadingLibrary = libraryStateHolder.isLoadingLibrary

    fun importSafDocuments(uris: List<Uri>) {
        if (uris.isEmpty() || _safImportUiState.value.isImporting) return
        viewModelScope.launch {
            _safImportUiState.value = LibrarySafImportUiState(isImporting = true)
            try {
                val summary = safImportAction.importSelected(uris)
                _safImportUiState.value = LibrarySafImportUiState(summary = summary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _safImportUiState.value = LibrarySafImportUiState(
                    unexpectedError = error.message,
                )
            }
        }
    }

    fun dismissSafImportFeedback() {
        _safImportUiState.value = LibrarySafImportUiState()
    }
}
