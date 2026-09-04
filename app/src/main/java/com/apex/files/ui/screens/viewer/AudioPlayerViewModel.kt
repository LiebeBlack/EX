package com.apex.files.ui.screens.viewer

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.model.FileNode
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Minimal zero-dependency audio player over the platform [MediaPlayer]:
 * play/pause, seek, and next/prev through the surrounding track list
 * (folder or category context). Foreground-only by design — leaving the
 * app stops playback, preserving the zero-background-services guarantee.
 */
class AudioPlayerViewModel(
    private val container: AppContainer,
    val nodes: List<FileNode>,
    startIndex: Int,
) : ViewModel() {

    data class UiState(
        val index: Int = 0,
        val name: String = "",
        val loading: Boolean = true,
        val prepared: Boolean = false,
        val isPlaying: Boolean = false,
        val durationMs: Long = 0L,
        val positionMs: Long = 0L,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var tickerJob: Job? = null

    init {
        val last = nodes.lastIndex
        loadTrack(if (last < 0) 0 else startIndex.coerceIn(0, last))
    }

    private fun currentTrack(): FileNode? = nodes.getOrNull(_state.value.index)

    private fun loadTrack(idx: Int) {
        val track = nodes.getOrNull(idx)
        if (track == null) {
            _state.update { it.copy(index = 0, error = "No hay pistas de audio", loading = false, prepared = false, isPlaying = false) }
            return
        }
        stopTicker()
        releasePlayer()
        _state.update {
            it.copy(
                index = idx,
                name = track.name,
                loading = true,
                prepared = false,
                isPlaying = false,
                durationMs = 0L,
                positionMs = 0L,
                error = null,
            )
        }
        try {
            val mp = MediaPlayer()
            if (track.uri != null) {
                mp.setDataSource(container.appContext, track.uri)
            } else {
                mp.setDataSource(File(track.path).absolutePath)
            }
            mp.setOnPreparedListener { ready ->
                _state.update {
                    it.copy(
                        loading = false,
                        prepared = true,
                        durationMs = ready.duration.toLong().coerceAtLeast(0L),
                    )
                }
                startPlayback()
            }
            mp.setOnCompletionListener { nextTrack() }
            mp.setOnErrorListener { _, what, extra ->
                releasePlayer()
                _state.update {
                    it.copy(loading = false, prepared = false, isPlaying = false, error = "No se pudo reproducir (error $what/$extra)")
                }
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            _state.update { it.copy(loading = false, prepared = false, error = "No se pudo abrir la pista") }
        }
    }

    fun togglePlayPause() {
        val s = _state.value
        if (!s.prepared) return
        val mp = player ?: return
        if (s.isPlaying) {
            mp.pause()
            _state.update { it.copy(isPlaying = false) }
            stopTicker()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val mp = player ?: return
        if (!_state.value.prepared) return
        runCatching { mp.start() }
        _state.update { it.copy(isPlaying = true, error = null) }
        startTicker()
    }

    fun seekTo(ms: Long) {
        val mp = player ?: return
        runCatching { mp.seekTo(ms.toInt().coerceAtLeast(0)) }
        _state.update { it.copy(positionMs = ms.coerceAtLeast(0L)) }
    }

    fun nextTrack() {
        val s = _state.value
        if (nodes.isEmpty()) return
        loadTrack((s.index + 1) % nodes.size)
    }

    fun prevTrack() {
        val s = _state.value
        if (nodes.isEmpty()) return
        loadTrack((s.index - 1 + nodes.size) % nodes.size)
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                val mp = player
                if (mp != null && _state.value.isPlaying) {
                    runCatching {
                        _state.update { it.copy(positionMs = mp.currentPosition.toLong()) }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun releasePlayer() {
        runCatching { player?.release() }
        player = null
    }

    override fun onCleared() {
        stopTicker()
        releasePlayer()
    }
}
