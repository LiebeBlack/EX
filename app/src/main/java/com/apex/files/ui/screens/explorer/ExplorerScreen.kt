package com.apex.files.ui.screens.explorer

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.Screen
import com.apex.files.core.OpType
import com.apex.files.data.fs.FileKinds
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.data.model.SortOrder
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.LocalOperationCenter
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.ConfirmDialog
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.InputDialog
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.components.SelectionBar
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainer

@Composable
fun ExplorerScreen(location: Location) {
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val center = LocalOperationCenter.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    val key = remember { "explorer-${location.key()}-${(navigator.current as? Screen.Explorer)?.serial ?: 0}" }
    val vm: ExplorerViewModel = apexViewModel(key = key) { c -> ExplorerViewModel(c, location) }
    val state by vm.state.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val toast: (String) -> Unit = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    // Internal back handling (selection / destination / directory up).
    BackHandler(enabled = state.selectionMode || state.destMode != null || vm.canGoUp()) {
        when {
            state.selectionMode -> vm.clearSelection()
            state.destMode != null -> vm.cancelDestMode()
            else -> vm.goUp()
        }
    }

    fun openFile(node: FileNode) {
        when {
            node.category == Category.IMAGE -> navigator.push(Screen.ImageViewer(node))
            FileKinds.isText(node) -> navigator.push(Screen.TextViewer(node))
            node.extension == "pdf" -> navigator.push(Screen.PdfViewer(node))
            container.archive.isSupported(node) -> navigator.push(Screen.ArchiveViewer(node))
            node.category == Category.APK -> {
                val uri = container.fs.shareUri(node)
                if (uri != null) {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(intent) }
                        .onFailure { toast("No hay aplicación para instalar") }
                }
            }
            else -> {
                val uri = container.fs.shareUri(node)
                if (uri != null) {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, FileKinds.mimeOf(node))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(intent, "Abrir con")) }
                        .onFailure { toast("No hay aplicación para este tipo de archivo") }
                }
            }
        }
    }

    fun shareSelected() {
        val uris = vm.selectedNodes().mapNotNull { container.fs.shareUri(it) }
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType("*/*")
                .putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(intent, "Compartir")) }
            .onFailure { toast("No se pudo compartir") }
    }

    fun launchOperation(type: OpType, flow: kotlinx.coroutines.flow.Flow<com.apex.files.core.OpProgress>) {
        center.launch(type, flow) { ok ->
            vm.onOperationFinished(ok)
            toast(if (ok) "Operación completada" else "Operación cancelada")
        }
    }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = state.current?.name ?: "…",
            onBack = {
                if (vm.canGoUp()) vm.goUp() else navigator.pop()
            },
            subtitle = state.current?.let { it.path.takeLast(52) },
            actions = {
                ApexIconButton(Icons.Outlined.Search, "Buscar") {
                    navigator.push(Screen.Search())
                }
                Box {
                    ApexIconButton(Icons.Outlined.Sort, "Ordenar") { sortMenuOpen = true }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (order) {
                                            SortOrder.NAME -> "Por nombre"
                                            SortOrder.SIZE -> "Por tamaño"
                                            SortOrder.DATE -> "Por fecha"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                onClick = {
                                    vm.setSort(order)
                                    sortMenuOpen = false
                                },
                                leadingIcon = if (state.sort == order) {
                                    { Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                } else null,
                            )
                        }
                    }
                }
                ApexIconButton(
                    if (state.viewMode == com.apex.files.data.model.ViewMode.LIST) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                    "Cambiar vista",
                ) { vm.toggleViewMode() }
                ApexIconButton(Icons.Outlined.Add, "Nueva carpeta") { showNewFolderDialog = true }
            },
        )

        Breadcrumbs(state.ancestors, state.current ?: FileNode.forDirectory("…", "…", isRoot = true), vm::navigateTo)

        if (state.destMode != null) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small,
                color = ApexContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Selecciona la carpeta de destino",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val flow = if (state.destMode == ExplorerViewModel.DestMode.COPY) vm.copyFlow() else vm.moveFlow()
                        launchOperation(if (state.destMode == ExplorerViewModel.DestMode.COPY) OpType.COPY else OpType.MOVE, flow)
                    }) {
                        Text("Pegar aquí", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { vm.cancelDestMode() }) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        when {
            state.loading -> {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    NeonProgressBar(progress = null, modifier = Modifier.padding(horizontal = 40.dp))
                }
            }
            state.error != null -> {
                EmptyState(Icons.Outlined.FolderOpen, state.error ?: "Error")
            }
            state.entries.isEmpty() -> {
                EmptyState(Icons.Outlined.FolderOpen, "Carpeta vacía")
            }
            else -> {
                if (state.viewMode == com.apex.files.data.model.ViewMode.LIST) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.entries, key = { it.path }) { node ->
                            FileRow(
                                node = node,
                                selected = node.path in state.selection,
                                onClick = {
                                    when {
                                        state.selectionMode -> vm.toggleSelect(node)
                                        state.destMode != null -> if (node.isDir) vm.openDir(node)
                                        node.isDir -> vm.openDir(node)
                                        else -> openFile(node)
                                    }
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.enterSelection(node)
                                },
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.entries, key = { it.path }) { node ->
                            GridTile(
                                node = node,
                                selected = node.path in state.selection,
                                onClick = {
                                    when {
                                        state.selectionMode -> vm.toggleSelect(node)
                                        state.destMode != null -> if (node.isDir) vm.openDir(node)
                                        node.isDir -> vm.openDir(node)
                                        else -> openFile(node)
                                    }
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.enterSelection(node)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (state.selectionMode && state.destMode == null) {
            SelectionBar(
                count = state.selection.size,
                onCopy = { vm.startDestMode(ExplorerViewModel.DestMode.COPY) },
                onMove = { vm.startDestMode(ExplorerViewModel.DestMode.MOVE) },
                onRename = {
                    if (vm.selectedNodes().size == 1) showRenameDialog = true
                    else toast("Selecciona un solo elemento para renombrar")
                },
                onDelete = { showDeleteConfirm = true },
                onShare = { shareSelected() },
                onCompress = { showCompressDialog = true },
                onProperties = { vm.selectedNodes().firstOrNull()?.let(vm::showProperties) },
                onClear = { vm.clearSelection() },
            )
        }
    }

    // ---- Dialogs ----
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "¿Eliminar?",
            message = "Se eliminará ${state.selection.size} elemento(s) de forma permanente. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            onConfirm = {
                showDeleteConfirm = false
                launchOperation(OpType.DELETE, vm.deleteFlow())
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
    if (showRenameDialog) {
        val node = vm.selectedNodes().firstOrNull()
        InputDialog(
            title = "Renombrar",
            initialValue = node?.name ?: "",
            onConfirm = { name ->
                showRenameDialog = false
                vm.renameSelected(name)
            },
            onDismiss = { showRenameDialog = false },
        )
    }
    if (showNewFolderDialog) {
        InputDialog(
            title = "Nueva carpeta",
            placeholder = "Nombre",
            onConfirm = { name ->
                showNewFolderDialog = false
                vm.createFolder(name)
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }
    if (showCompressDialog) {
        InputDialog(
            title = "Comprimir",
            initialValue = "archivo.zip",
            onConfirm = { name ->
                showCompressDialog = false
                launchOperation(OpType.COMPRESS, vm.compressFlow(name))
            },
            onDismiss = { showCompressDialog = false },
        )
    }
    state.properties?.let { props ->
        PropertiesSheet(
            state = props,
            onDismiss = { vm.dismissProperties() },
            onComputeHash = { vm.computeHash(it) },
            onCopyText = { text ->
                clipboard.setText(AnnotatedString(text))
                toast("Copiado")
            },
        )
    }
}