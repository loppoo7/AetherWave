package com.aetherwave.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<MusicTrack> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private var searchJob: Job? = null

    fun onQueryChanged(value: String) {
        mutableState.update { it.copy(query = value, error = null, isLoading = false, hasSearched = false, results = emptyList()) }
        searchJob?.cancel()
        val query = value.trim()
        if (query.isEmpty()) {
            mutableState.update { it.copy(results = emptyList(), isLoading = false, hasSearched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            mutableState.update { it.copy(isLoading = true, error = null, hasSearched = true) }
            try {
                val results = repository.searchYouTubeMusic(query, 25)
                if (mutableState.value.query.trim() == query) {
                    mutableState.update { it.copy(results = results, isLoading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mutableState.value.query.trim() == query) {
                    mutableState.update { it.copy(results = emptyList(), isLoading = false, error = error.localizedMessage ?: "Search failed") }
                }
            }
        }
    }
}
