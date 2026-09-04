package com.apex.files.ui.screens.tools

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.core.OpType
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.LocalOperationCenter
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.ConfirmDialog
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.components.RootPickerRow
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun CleanerScreen() {
    val navigator = LocalNavigator.current
    val center = LocalOperationCenter.current
    val context = LocalContext.current
    val vm: CleanerViewModel = apexViewModel(key = "cleaner") { c -> CleanerViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(title = "Limpiador Vacío", onBack = { navigator.pop() })

        RootPickerRow(
            currentName = state.rootName,
            currentKey = state.rootKey,
            volumes = state.volumes,
            enabled = !state.scanning,
            onPick = { vm.setRoot(it.key) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        when {
            state.scanning -> {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    NeonProgressBar(progress = null)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Escaneando… · ${state.found.size} encontradas",
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
                        "Busca directorios cuyo contenido suma 0 bytes y permite eliminarlos de una vez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { vm.scan() }) {
                        Text("Escanear", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            state.found.isEmpty() -> {
                EmptyState(Icons.Outlined.FolderOff, "Sin carpetas vacías")
                TextButton(onClick = { vm.reset() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Volver a escanear", color = MaterialTheme.colorScheme.primary)
                }
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${state.found.size} carpetas vacías",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.selectAll() }) { Text("Todo", color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = { vm.clearSelection() }) { Text("Nada", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.found, key = { it.path }) { node ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggleSelect(node) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (node.path in state.selection) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                null,
                                tint = if (node.path in state.selection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                node.path,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (state.selection.isNotEmpty()) {
                    TextButton(
                        onClick = { showConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Eliminar seleccionadas (${state.selection.size})",
                            color = com.apex.files.ui.theme.ApexDanger,
                        )
                    }
                }
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = "¿Eliminar?",
            message = "Se eliminarán ${state.selection.size} carpeta(s) vacía(s) de forma permanente.",
            confirmLabel = "Eliminar",
            onConfirm = {
                showConfirm = false
                center.launch(OpType.DELETE, vm.deleteFlow()) { ok ->
                    val msg = if (ok) {
                        vm.consumeDeleteSummary() ?: "Operación completada"
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