package com.apex.files.ui.screens.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apex.files.BuildConfig
import com.apex.files.core.PerfMetrics
import com.apex.files.data.fs.MemoryIndex
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.search.ContentIndex
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.theme.MonoTextStyleSmall
import kotlinx.coroutines.delay

/**
 * Hidden diagnostics surface (long-press on the version in “Acerca de”).
 * Read-only view of the internal [PerfMetrics] ring buffer and the two index
 * footprints; polls cheap in-memory counters only — no work is triggered by
 * opening this screen.
 */
@Composable
fun DiagnosticsScreen() {
    val navigator = LocalNavigator.current
    val container = LocalContainer.current

    // Light 500 ms ticker so sizes/uptime feel alive. Each tick only re-reads
    // volatile counters and one file length.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick++
        }
    }

    val searchIndexSize = remember(tick) { container.index.size }
    val semanticOn = container.settings.semanticSearchEnabled.value
    val contentIndexSize = remember(tick) { container.contentIndex.size }
    val contentBytes = remember(tick) { container.contentIndex.snapshotBytes() }
    val uptime = remember(tick) {
        val s = android.os.SystemClock.elapsedRealtime() / 1000
        "${s / 60}m ${"%02d".format(s % 60)}s"
    }
    val samples = remember(tick) { PerfMetrics.snapshot() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ApexTopBar(title = "Diagnóstico", onBack = { navigator.pop() })
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SectionTitle("Proceso")
            ApexCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetricRow("Versión", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    MetricRow("Tipo de build", if (BuildConfig.DEBUG) "debug" else "release")
                    MetricRow("Uptime", uptime)
                }
            }

            SectionTitle("Índices")
            ApexCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetricRow("Índice de búsqueda", "$searchIndexSize / ${MemoryIndex.CAP} archivos")
                    MetricRow("Módulo semántico", if (semanticOn) "Activo" else "Desactivado")
                    MetricRow("Índice de contenido", "$contentIndexSize / ${ContentIndex.MAX_ENTRIES} entradas")
                    MetricRow("Snapshot de contenido", SizeFormatter.format(contentBytes))
                }
            }

            SectionTitle("Últimas operaciones (${samples.size}/32)")
            ApexCard(Modifier.fillMaxWidth()) {
                if (samples.isEmpty()) {
                    Text(
                        "Aún sin registros: navega, busca o reindexa y vuelve.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Plain Column: the buffer is capped at 32 rows and this
                    // card lives inside the screen's own vertical scroll, so a
                    // nested LazyColumn (infinite height) would crash.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        samples.asReversed().forEach { s ->
                            Text(
                                buildString {
                                    append(s.tag)
                                    append(" · ")
                                    append(s.durationMillis)
                                    append(" ms")
                                    s.count?.let { append(" · "); append(it); append(" items") }
                                    s.detail?.let { append(" · "); append(it) }
                                },
                                style = MonoTextStyleSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            ApexCard(Modifier.fillMaxWidth()) {
                Text(
                    "Buffer fijo de 32 muestras · nada sale del proceso · " +
                        "logs solo en builds debug.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MonoTextStyleSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}
