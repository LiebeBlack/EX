package com.apex.files.ui.screens.trash

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.apex.files.data.fs.DateFormatter
import com.apex.files.data.fs.TrashManager
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.ConfirmDialog
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexDanger
import com.apex.files.ui.theme.MonoTextStyleSmall

/**
 * Papelera: items deleted through APEX land here (per-volume `.apex_trash`)
 * and can be restored to their original location or deleted forever.
 */
@Composable
fun TrashScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val vm: TrashViewModel = apexViewModel(key = "trash") { c -> TrashViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()

    var confirmEmpty by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TrashManager.TrashEntry?>(null) }

    LaunchedEffect(state.notice) {
        state.notice?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.consumeNotice()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = "Papelera",
            onBack = { navigator.pop() },
            subtitle = if (state.entries.isNotEmpty()) "${state.entries.size} elemento(s)" else null,
            actions = {
                if (state.entries.isNotEmpty()) {
                    ApexIconButton(
                        Icons.Outlined.DeleteSweep,
                        "Vaciar papelera",
                        tint = MaterialTheme.colorScheme.onBackground,
                    ) { confirmEmpty = true }
                }
            },
        )

        when {
            state.loading && state.entries.isEmpty() -> {
                NeonProgressBar(progress = null, modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp))
            }
            state.error != null -> EmptyState(Icons.Outlined.DeleteOutline, state.error ?: "Error")
            state.entries.isEmpty() -> EmptyState(
                Icons.Outlined.DeleteSweep,
                "La papelera está vacía\nLos elementos eliminados se pueden recuperar aquí",
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Eliminados con APEX · se guardan por volumen en .apex_trash",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                items(state.entries, key = { it.path }) { entry ->
                    TrashRow(
                        entry = entry,
                        onRestore = { vm.restore(entry) },
                        onDelete = { pendingDelete = entry },
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Vaciar papelera",
            message = "Se eliminarán definitivamente ${state.entries.size} elemento(s). Esta acción no se puede deshacer.",
            confirmLabel = "Vaciar",
            onConfirm = {
                confirmEmpty = false
                vm.empty()
            },
            onDismiss = { confirmEmpty = false },
        )
    }
    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = "Eliminar definitivamente",
            message = "«${entry.name}» se borrará para siempre y no se podrá recuperar.",
            confirmLabel = "Eliminar",
            onConfirm = {
                pendingDelete = null
                vm.deletePermanently(entry)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun TrashRow(
    entry: TrashManager.TrashEntry,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    ApexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (entry.isDir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (entry.originalPath.isNotBlank()) entry.originalPath
                    else "Origen desconocido",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Eliminado ${DateFormatter.relative(entry.trashedAt)}",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRestore, enabled = entry.originalPath.isNotBlank()) {
                Text(
                    "Restaurar",
                    color = if (entry.originalPath.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            ApexIconButton(
                Icons.Outlined.Restore,
                "Restaurar",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onRestore,
            )
            ApexIconButton(
                Icons.Outlined.DeleteForever,
                "Eliminar definitivamente",
                tint = ApexDanger,
                onClick = onDelete,
            )
        }
    }
}