package com.apex.files.ui.screens.viewer

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.apex.files.data.fs.ArchiveEntry
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.LocalOperationCenter
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun ArchiveViewerScreen(node: FileNode) {
    val navigator = LocalNavigator.current
    val center = LocalOperationCenter.current
    val context = LocalContext.current
    val key = remember { "archive-${node.path}" }
    val vm: ArchiveViewerViewModel = apexViewModel(key = key) { c -> ArchiveViewerViewModel(c, node) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Set once the Downloads destination is resolved; invoked by the top bar.
    var extractAll by remember { mutableStateOf<(() -> Unit)?>(null) }

    BackHandler(enabled = vm.canGoUp()) { vm.goUp() }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = node.name,
            onBack = {
                if (vm.canGoUp()) vm.goUp() else navigator.pop()
            },
            subtitle = if (state.currentPath.isNotEmpty()) {
                state.currentPath.trimEnd('/')
            } else {
                "Archivo comprimido · raíz"
            },
            actions = {
                ApexIconButton(
                    Icons.Outlined.FileDownload,
                    "Extraer todo",
                    onClick = { extractAll?.invoke() },
                )
            },
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.openRoot() }) {
                Text("Raíz", color = if (state.currentPath.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                state.currentPath.ifEmpty { "/" },
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            state.loading -> NeonProgressBar(progress = null, modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp))
            state.error != null -> EmptyState(Icons.Outlined.FolderZip, state.error ?: "Error")
            state.currentEntries.isEmpty() -> EmptyState(Icons.Outlined.FolderZip, "Carpeta vacía")
            else -> {
                // Resolved once, outside composition: mkdirs() is a side
                // effect and must not run on every recomposition.
                val destDir = remember {
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (downloads.exists() || downloads.mkdirs()) {
                        FileNode.forDirectory("Descargas", downloads.absolutePath)
                    } else {
                        FileNode.forDirectory(
                            "Almacenamiento interno",
                            Environment.getExternalStorageDirectory().absolutePath,
                        )
                    }
                }
                // Wired to the “Extraer todo” top-bar action.
                extractAll = {
                    val dir = destDir
                    center.launch(OpType.EXTRACT, vm.extractAllFlow(dir)) { ok ->
                        val msg = if (ok) {
                            vm.consumeSummary() ?: "Extraído en Descargas"
                        } else {
                            center.lastError.value ?: "Extracción cancelada"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    items(state.currentEntries, key = { it.name }) { entry ->
                        ArchiveEntryRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDir) vm.openDir(entry)
                            },
                            onExtract = {
                                center.launch(OpType.EXTRACT, vm.extractFlow(entry, destDir)) { ok ->
                                    val msg = if (ok) {
                                        vm.consumeSummary() ?: "Extraído en Descargas"
                                    } else {
                                        center.lastError.value ?: "Extracción cancelada"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveEntryRow(
    entry: ArchiveEntry,
    onClick: () -> Unit,
    onExtract: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.isDir) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
            null,
            tint = if (entry.isDir) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (entry.isDir) "Carpeta" else SizeFormatter.format(entry.size.coerceAtLeast(0)),
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!entry.isDir) {
            IconButton(onClick = onExtract, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.FileDownload,
                    "Extraer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}