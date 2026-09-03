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
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CheckCircle
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
import com.apex.files.data.fs.SizeFormatter
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

@Composable
fun ApkScreen() {
    val navigator = LocalNavigator.current
    val center = LocalOperationCenter.current
    val context = LocalContext.current
    val vm: ApkViewModel = apexViewModel(key = "apk") { c -> ApkViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(title = "Filtro APK", onBack = { navigator.pop() })

        when {
            state.scanning -> {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    NeonProgressBar(progress = null)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Escaneando… · ${state.apks.size} APKs",
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
                    Icon(Icons.Outlined.Android, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Localiza instaladores .apk y marca cuáles corresponden a apps ya instaladas (redundantes).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { vm.scan() }) {
                        Text("Escanear", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            state.apks.isEmpty() -> {
                EmptyState(Icons.Outlined.Android, "Sin APKs")
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
                            "${state.apks.size} instaladores · ${state.notInstalled.size} no instalados",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    TextButton(onClick = { vm.selectNotInstalled() }) {
                        Text("No instaladas", color = MaterialTheme.colorScheme.primary)
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.apks, key = { it.node.path }) { info ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggleSelect(info.node.path) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (info.node.path in state.selection) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                null,
                                tint = if (info.node.path in state.selection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                Icons.Outlined.Android,
                                null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    info.node.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${SizeFormatter.format(info.node.size)} · ${info.packageName ?: "?"}",
                                    style = MonoTextStyleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Badge(info)
                        }
                    }
                }
                if (state.selection.isNotEmpty()) {
                    TextButton(
                        onClick = { showConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Eliminar seleccionados (${state.selection.size})", color = ApexDanger)
                    }
                }
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = "¿Eliminar?",
            message = "Se eliminarán ${state.selection.size} instalador(es) de forma permanente.",
            confirmLabel = "Eliminar",
            onConfirm = {
                showConfirm = false
                center.launch(OpType.DELETE, vm.deleteFlow()) { ok ->
                    Toast.makeText(
                        context,
                        if (ok) "Operación completada" else "Operación cancelada",
                        Toast.LENGTH_SHORT,
                    ).show()
                    vm.scan()
                }
            },
            onDismiss = { showConfirm = false },
        )
    }
}

@Composable
private fun Badge(info: com.apex.files.tools.ApkScanner.ApkInfo) {
    val (label, color) = when (info.installed) {
        true -> "Instalada" to MaterialTheme.colorScheme.primary
        false -> "No instalada" to ApexDanger
        null -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(start = 8.dp),
    )
}