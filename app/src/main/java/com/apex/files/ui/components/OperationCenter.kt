package com.apex.files.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
import com.apex.files.core.formatSpeed
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainer
import com.apex.files.ui.theme.ApexShapes
import com.apex.files.ui.theme.MonoTextStyleSmall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App-scoped host for long-running file operations (single active op). */
class OperationCenterViewModel : ViewModel() {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _progress = MutableStateFlow<OpProgress?>(null)
    val progress: StateFlow<OpProgress?> = _progress.asStateFlow()

    private var job: Job? = null

    /**
     * Runs [flow] through the center. [onDone] receives true when the flow
     * completes normally, false when cancelled or failed.
     */
    fun launch(type: OpType, flow: Flow<OpProgress>, onDone: (Boolean) -> Unit = {}) {
        if (_active.value) return
        _active.value = true
        _progress.value = OpProgress(type, currentName = "")
        job = viewModelScope.launch {
            var completed = false
            try {
                flow.collect { _progress.value = it }
                completed = true
            } catch (e: CancellationException) {
                completed = false
                throw e
            } catch (e: Exception) {
                completed = false
            } finally {
                _active.value = false
                _progress.value = null
                onDone(completed)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}

/** Floating neon progress card pinned to the bottom of the app. */
@Composable
fun OperationCenter(
    center: OperationCenterViewModel,
    modifier: Modifier = Modifier,
    onDone: (Boolean) -> Unit = {},
) {
    val active by center.active.collectAsState()
    val progress by center.progress.collectAsState()

    AnimatedVisibility(
        visible = active,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        progress?.let { p ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 560.dp),
                shape = ApexShapes.medium,
                color = ApexContainer,
                border = BorderStroke(1.dp, ApexBorder),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            p.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatSpeed(p.speedBytesPerSec),
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    NeonProgressBar(progress = if (p.fraction >= 0f) p.fraction else null)
                    Row(
                        Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            p.currentName.ifBlank { "—" },
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (p.filesTotal != null) {
                            Text(
                                "${p.filesDone}/${p.filesTotal}",
                                style = MonoTextStyleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (p.fraction >= 0f) {
                            Text(
                                " · ${(p.fraction * 100).toInt()}%",
                                style = MonoTextStyleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { center.cancel() }) {
                            Text(
                                "Cancelar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}