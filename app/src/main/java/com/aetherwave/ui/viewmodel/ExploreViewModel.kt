package com.aetherwave.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetherwave.domain.model.MusicSection
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ExploreUiState(
    val sections: List<MusicSection> = emptyList(),
    val topPlayed: List<MusicTrack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeMostPlayed(20).collect { tracks ->
                mutableState.update { it.copy(topPlayed = tracks) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (state.value.isLoading) return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            try {
                val sections = repository.getExploreSections()
                mutableState.update { it.copy(sections = sections, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(isLoading = false, error = error.localizedMessage ?: "Unable to load Explore")
                }
            }
        }
    }
}
