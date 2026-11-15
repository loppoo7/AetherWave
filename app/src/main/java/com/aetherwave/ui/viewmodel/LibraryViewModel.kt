package com.aetherwave.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aetherwave.domain.model.MusicPlaylist
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
data class LibraryUiState(
    val playlists: List<MusicPlaylist> = emptyList(),
    val downloads: List<MusicTrack> = emptyList(),
    val likedSongs: List<MusicTrack> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observePlaylists(),
                repository.observeDownloadedTracks(),
                repository.observeLikedTracks(),
            ) { playlists, downloads, liked -> Triple(playlists, downloads, liked) }
                .collect { (playlists, downloads, liked) ->
                    mutableState.update { it.copy(playlists = playlists, downloads = downloads, likedSongs = liked) }
                }
        }
    }

    fun createPlaylist(name: String) = perform("Playlist created") { repository.createPlaylist(name) }
    fun deletePlaylist(id: String) = perform("Playlist deleted") { repository.deletePlaylist(id) }
    fun addTrack(playlistId: String, track: MusicTrack) = perform("Added to playlist") { repository.addTrackToPlaylist(playlistId, track) }
    fun removeTrack(playlistId: String, trackId: String) = perform("Removed from playlist") { repository.removeTrackFromPlaylist(playlistId, trackId) }

    private fun perform(successMessage: String, action: suspend () -> Any?) {
        viewModelScope.launch {
            try {
                action()
                mutableState.update { it.copy(message = successMessage) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(message = error.localizedMessage ?: "Library update failed") }
            }
        }
    }
}
