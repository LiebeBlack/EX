package com.apex.files.ui.screens.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexContainer
import com.apex.files.ui.theme.ApexShapes
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun BenchmarkScreen() {
    val navigator = LocalNavigator.current
    val vm: BenchmarkViewModel = apexViewModel(key = "benchmark") { c -> BenchmarkViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ApexTopBar(title = "Benchmark", onBack = { navigator.pop() })

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ApexCard(Modifier.fillMaxWidth()) {
                Text("Benchmark de almacenamiento", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Escribe y lee una carga de prueba de 32 MB (fragmentos de 64 KB, fsync) y mide el rendimiento real en MB/s. El archivo temporal se elimina automáticamente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ApexCard(Modifier.fillMaxWidth()) {
                Text("Volumen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.volumes.forEach { vol ->
                        Text(
                            vol.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (vol.key == state.selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { vm.select(vol.key) }
                                .background(
                                    if (vol.key == state.selected) MaterialTheme.colorScheme.primary else ApexContainer,
                                    ApexShapes.small,
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Libre: ${SizeFormatter.format(state.freeBytes)}",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.running) {
                ApexCard(Modifier.fillMaxWidth()) {
                    NeonProgressBar(progress = null)
                    Spacer(Modifier.height(10.dp))
                    Text("Probando…", style = MonoTextStyleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            state.result?.let { result ->
                ApexCard(Modifier.fillMaxWidth()) {
                    Text("Prueba completada", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    ResultRow("Escritura", result.writeLabel)
                    Spacer(Modifier.height(6.dp))
                    ResultRow("Lectura", result.readLabel)
                    Spacer(Modifier.height(6.dp))
                    ResultRow("Tiempo", "${result.writeMs + result.readMs} ms")
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            TextButton(
                onClick = { vm.run() },
                enabled = !state.running && state.selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ejecutar", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(18.dp).height(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MonoTextStyleSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}