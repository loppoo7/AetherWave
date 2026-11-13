package com.aetherwave.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetherwave.domain.model.MusicHomeFeed
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class HomeUiState(
    val quickPicks: List<MusicTrack> = emptyList(),
    val trending: List<MusicTrack> = emptyList(),
    val recommended: List<MusicTrack> = emptyList(),
    val recentlyPlayed: List<MusicTrack> = emptyList(),
    val likedSongs: List<MusicTrack> = emptyList(),
    val localAudio: List<MusicTrack> = emptyList(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeRecentlyPlayed(30), repository.observeLikedTracks()) { recent, liked ->
                recent to liked
            }.collect { (recent, liked) ->
                mutableState.update { it.copy(recentlyPlayed = recent, likedSongs = liked) }
            }
        }
        refreshHomeFeed()
    }

    fun refreshHomeFeed() {
        if (state.value.isLoading) return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            try {
                val feed: MusicHomeFeed = repository.getHomeFeed()
                mutableState.update {
                    it.copy(
                        quickPicks = feed.quickPicks,
                        trending = feed.trending,
                        recommended = feed.recommended,
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(isLoading = false, error = error.localizedMessage ?: "Unable to load YouTube Music Home")
                }
            }
        }
    }

    fun refreshLocalAudio() {
        if (state.value.isScanning) return
        viewModelScope.launch {
            mutableState.update { it.copy(isScanning = true, error = null) }
            try {
                val tracks = repository.scanLocalAudio()
                mutableState.update { it.copy(localAudio = tracks, isScanning = false, error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(isScanning = false, error = error.localizedMessage ?: "Allow audio access to scan local files") }
            }
        }
    }
}
