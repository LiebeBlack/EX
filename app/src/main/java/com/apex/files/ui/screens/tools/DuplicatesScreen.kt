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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import com.apex.files.tools.DuplicateFinder
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
fun DuplicatesScreen() {
    val navigator = LocalNavigator.current
    val center = LocalOperationCenter.current
    val context = LocalContext.current
    val vm: DuplicatesViewModel = apexViewModel(key = "duplicates") { c -> DuplicatesViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(title = "Buscador de Duplicados", onBack = { navigator.pop() })

        when {
            state.scanning -> {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    NeonProgressBar(progress = if (state.total > 0) state.hashed.toFloat() / state.total else null)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.total > 0) {
                            "Fase 2 · SHA-256 ${state.hashed}/${state.total}"
                        } else {
                            "Fase 1 · Agrupando por tamaño…"
                        },
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
                    Icon(Icons.Outlined.ContentCopy, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Algoritmo de 2 capas: primero agrupa por tamaño exacto; después aplica SHA-256 solo a los grupos coincidentes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { vm.scan() }) {
                        Text("Escanear", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            state.groups.isEmpty() -> {
                EmptyState(Icons.Outlined.ContentCopy, "Sin duplicados")
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
                            "${state.groups.size} grupos · ${state.groups.sumOf { it.files.size }} archivos",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Recuperable: ${SizeFormatter.format(state.reclaimable)}",
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.selectDuplicates() }) { Text("Seleccionar duplicados", color = MaterialTheme.colorScheme.primary) }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.groups, key = { it.hash }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            index = state.groups.indexOf(group) + 1,
                            expanded = group.hash in state.expanded,
                            selection = state.selection,
                            onToggleExpand = { vm.toggleExpand(group) },
                            onToggleSelect = vm::toggleSelect,
                        )
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
            message = "Se eliminarán ${state.selection.size} archivo(s) duplicado(s) de forma permanente.",
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
private fun DuplicateGroupCard(
    group: DuplicateFinder.DupGroup,
    index: Int,
    expanded: Boolean,
    selection: Set<String>,
    onToggleExpand: () -> Unit,
    onToggleSelect: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Grupo $index · ${SizeFormatter.format(group.size)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${group.files.size} copias · SHA-256 ${group.hash.take(12)}…",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "+${SizeFormatter.format(group.reclaimable)}",
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            group.files.forEachIndexed { i, file ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSelect(file.path) }
                        .padding(start = 26.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (file.path in selection) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        null,
                        tint = if (file.path in selection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (i == 0) "★ ${file.path}" else file.path,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}