package com.apex.files.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.Screen
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.MonoTextStyleSmall

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/**
 * Full-screen OLED audio player: play/pause, seek, previous/next track.
 * Playback is intentionally foreground-only (no background services).
 */
@Composable
fun AudioPlayerScreen(nodes: List<FileNode>, startIndex: Int) {
    if (nodes.isEmpty()) return
    val navigator = LocalNavigator.current
    val key = remember { "audio-${nodes.first().path}-${(navigator.current as? Screen.AudioPlayer)?.serial ?: 0}" }
    val vm: AudioPlayerViewModel = apexViewModel(key = key) { c -> AudioPlayerViewModel(c, nodes, startIndex) }
    val state by vm.state.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(0.4f))
            Box(
                Modifier
                    .size(140.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Audiotrack,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                state.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.index + 1} / ${nodes.size}",
                style = MonoTextStyleSmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(36.dp))

            when {
                state.loading -> NeonProgressBar(progress = null, modifier = Modifier.fillMaxWidth())
                state.error != null -> Text(
                    state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    Slider(
                        value = if (state.durationMs > 0) {
                            state.positionMs.toFloat().coerceIn(0f, state.durationMs.toFloat())
                        } else {
                            0f
                        },
                        onValueChange = { vm.seekTo(it.toLong()) },
                        valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
                        enabled = state.prepared,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            formatTime(state.positionMs),
                            style = MonoTextStyleSmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatTime(state.durationMs),
                            style = MonoTextStyleSmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.prevTrack() }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.width(18.dp))
                IconButton(
                    onClick = { vm.togglePlayPause() },
                    enabled = state.prepared,
                    modifier = Modifier.size(84.dp),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(12.dp),
                    )
                }
                Spacer(Modifier.width(18.dp))
                IconButton(onClick = { vm.nextTrack() }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Spacer(Modifier.weight(0.6f))
        }

        ApexIconButton(
            Icons.Outlined.Close,
            "Cerrar",
            onClick = { navigator.pop() },
            tint = Color.White,
            modifier = Modifier
                .statusBarsPadding()
                .padding(10.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape),
        )
    }
}
