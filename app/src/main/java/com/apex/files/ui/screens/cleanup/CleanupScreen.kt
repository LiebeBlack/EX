package com.apex.files.ui.screens.cleanup

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.core.OpType
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.tools.JunkAnalyzer
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.LocalOperationCenter
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.ConfirmDialog
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexDanger
import com.apex.files.ui.theme.MonoTextStyleSmall

/**
 * Limpieza Inteligente: orphan app folders, caches and temp files. Analysis
 * only — deletion always requires explicit confirmation and respects the
 * Papelera setting.
 */
@Composable
fun CleanupScreen() {
    val navigator = LocalNavigator.current
    val center = LocalOperationCenter.current
    val context = LocalContext.current
    val vm: CleanupViewModel = apexViewModel(key = "cleanup") { c -> CleanupViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val trashEnabled by LocalContainer.current.settings.trashEnabled.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    
    val toast: (String) -> Unit = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(title = "Limpieza Inteligente", onBack = { navigator.pop() })

        when {
            state.scanning -> {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    NeonProgressBar(progress = null)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Escaneando… · ${state.items.size} hallazgos",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.currentPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            !state.done -> {
                ApexCard(Modifier.fillMaxWidth().padding(20.dp)) {
                    Icon(Icons.Outlined.CleaningServices, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Detecta carpetas de apps desinstaladas, cachés y archivos temporales. " +
                            "Nada se borra sin tu confirmación.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { vm.scan() }) {
                        Text("Escanear", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            state.items.isEmpty() -> {
                EmptyState(Icons.Outlined.CleaningServices, "Sin residuos detectados")
                TextButton(onClick = { vm.reset() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Volver a escanear", color = MaterialTheme.colorScheme.primary)
                }
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${state.items.size} hallazgos",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Recuperables: ${SizeFormatter.format(state.items.sumOf { it.bytes })}",
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.selectAll() }) { Text("Todo", color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = { vm.clearSelection() }) { Text("Nada", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { vm.analyzeByAge() }) { Text("Antiguos", color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = { 
                        val stats = vm.getStatistics()
                        toast("Estadísticas: ${stats.entries.joinToString { "${it.key}: ${it.value}" }}")
                    }) { Text("Estadísticas", color = MaterialTheme.colorScheme.primary) }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    JunkAnalyzer.JunkKind.entries.forEach { kind ->
                        val grouped = state.items.filter { it.kind == kind }
                        if (grouped.isNotEmpty()) {
                            item(key = "header-${kind.name}") {
                                Text(
                                    kindLabel(kind).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                                )
                            }
                            items(grouped, key = { it.path }) { item ->
                                JunkRow(item, selected = item.path in state.selection) {
                                    vm.toggleSelect(item)
                                }
                            }
                        }
                    }
                }
                if (state.selection.isNotEmpty()) {
                    TextButton(
                        onClick = { showConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Eliminar seleccionados (${state.selection.size}) · ${SizeFormatter.format(state.selectedBytes)}",
                            color = ApexDanger,
                        )
                    }
                }
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = "¿Eliminar?",
            message = "Se eliminarán ${state.selection.size} elemento(s) " +
                "(${SizeFormatter.format(state.selectedBytes)}). " +
                if (trashEnabled) {
                    "Los elementos de este volumen se moverán a la Papelera y podrán restaurarse."
                } else {
                    "Se eliminarán de forma permanente."
                },
            confirmLabel = "Eliminar",
            destructive = true,
            onConfirm = {
                showConfirm = false
                center.launch(OpType.DELETE, vm.deleteFlow()) { ok ->
                    val msg = if (ok) {
                        vm.consumeSummary() ?: "Operación completada"
                    } else {
                        center.lastError.value ?: "Operación cancelada"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    vm.scan()
                }
            },
            onDismiss = { showConfirm = false },
        )
    }
}

private fun kindLabel(kind: JunkAnalyzer.JunkKind): String = when (kind) {
    JunkAnalyzer.JunkKind.ORPHAN_APP -> "Apps desinstaladas"
    JunkAnalyzer.JunkKind.CACHE -> "Cachés"
    JunkAnalyzer.JunkKind.TEMP -> "Temporales"
}

@Composable
private fun JunkRow(item: JunkAnalyzer.JunkItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            kindIcon(item.kind),
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${SizeFormatter.format(item.bytes)} · ${item.path}",
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun kindIcon(kind: JunkAnalyzer.JunkKind): ImageVector = when (kind) {
    JunkAnalyzer.JunkKind.ORPHAN_APP -> Icons.Outlined.Android
    JunkAnalyzer.JunkKind.CACHE -> Icons.Outlined.CleaningServices
    JunkAnalyzer.JunkKind.TEMP -> Icons.Outlined.Schedule
}