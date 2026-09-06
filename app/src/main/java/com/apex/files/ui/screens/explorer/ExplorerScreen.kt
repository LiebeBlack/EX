package com.apex.files.ui.screens.explorer

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ControlPointDuplicate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.Screen
import com.apex.files.core.ListDensity
import com.apex.files.core.OpProgress
import com.apex.files.core.OpType
import com.apex.files.data.fs.FileKinds
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.data.model.SortDirection
import com.apex.files.data.model.SortOrder
import com.apex.files.data.model.ViewMode
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.LocalOperationCenter
import com.apex.files.ui.NodeOpener
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
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexDanger
import com.apex.files.ui.theme.MonoTextStyleSmall
import kotlinx.coroutines.flow.Flow

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
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    /** Name prefilled in the compress dialog (depends on the current selection). */
    var compressDefaultName by remember { mutableStateOf("archivo.zip") }
    /** Node whose long-press context sheet is open (null = none). */
    var contextNode by remember { mutableStateOf<FileNode?>(null) }
    val trashEnabled by container.settings.trashEnabled.collectAsStateWithLifecycle()
    val confirmPermanentDelete by container.settings.confirmPermanentDelete.collectAsStateWithLifecycle()
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()

    val toast: (String) -> Unit = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    // One-shot failures (rename / new folder) surfaced as toasts.
    LaunchedEffect(state.notice) {
        state.notice?.let { msg ->
            toast(msg)
            vm.consumeNotice()
        }
    }

    // Internal back handling (selection / destination / directory up).
    BackHandler(enabled = selectionMode || state.destMode != null || vm.canGoUp()) {
        when {
            selectionMode -> vm.clearSelection()
            state.destMode != null -> vm.cancelDestMode()
            else -> vm.goUp()
        }
    }

    fun openFile(node: FileNode) {
        NodeOpener.open(node, container, navigator, context, imageContext = state.entries) { msg -> toast(msg) }
    }

    fun openWithChooser(node: FileNode) {
        val uri = container.fs.shareUri(node) ?: run {
            toast("No se pudo abrir con otra aplicación")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, FileKinds.mimeOf(node))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(intent, "Abrir con")) }
            .onFailure { toast("No hay aplicación para este tipo de archivo") }
    }

    fun shareNodes(nodes: List<FileNode>) {
        val uris = nodes.mapNotNull { container.fs.shareUri(it) }
        if (uris.isEmpty()) {
            toast("No se pudo compartir")
            return
        }
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

    fun copyPathsOf(nodes: List<FileNode>) {
        val paths = nodes.map { it.path }.joinToString("\n")
        if (paths.isEmpty()) return
        clipboard.setText(AnnotatedString(paths))
        toast("Ruta(s) copiadas al portapapeles")
    }

    fun shareSelected() = shareNodes(vm.selectedNodes())

    fun copyPaths() = copyPathsOf(vm.selectedNodes())

    /** Adds all selected nodes to favorites (or removes them when all are already there). */
    fun toggleFavorite(nodes: List<FileNode>): Boolean {
        if (nodes.isEmpty()) return false
        val allFavorites = nodes.all { container.favorites.isFavorite(it.path) }
        nodes.forEach { n ->
            if (container.favorites.isFavorite(n.path) == allFavorites) container.favorites.toggle(n)
        }
        return allFavorites
    }

    /** Default archive name: one selection keeps its own name ("foto.jpg" → "foto.zip"). */
    fun defaultZipName(): String {
        val sel = vm.selectedNodes()
        if (sel.size != 1) return "archivo.zip"
        val name = sel[0].name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        return "$base.zip"
    }

    /** Filesystem-safe name check shown before creating/renaming/compressing. */
    fun isValidName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." &&
            !name.contains('/') && !name.contains('\\')

    // Local funs can't be forward-referenced, so declare launchOperation first.
    fun launchOperation(type: OpType, flow: Flow<OpProgress>) {
        center.launch(type, flow) { ok ->
            val error = center.lastError.value
            val msg = when {
                ok -> vm.consumeSummary() ?: "Operación completada"
                error != null -> error
                else -> vm.consumeError() ?: "Operación cancelada"
            }
            // A real failure keeps the selection/destination so the user can
            // retry; an explicit Cancel (no error message) cleans up instead.
            vm.onOperationFinished(ok, cancelled = !ok && error == null)
            toast(msg)
        }
    }

    fun extractHere() {
        launchOperation(OpType.EXTRACT, vm.extractHereFlow())
    }

    // Single archive selected → the selection bar shows “Extraer aquí”.
    val selected = vm.selectedNodes()
    val canExtract = selected.size == 1 && !selected[0].isDir && container.archive.isSupported(selected[0])

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
                    ApexIconButton(Icons.AutoMirrored.Outlined.Sort, "Ordenar") { sortMenuOpen = true }
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
                        HorizontalDivider(color = ApexBorder, thickness = 1.dp)
                        SortDirection.entries.forEach { direction ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (direction == SortDirection.ASC) "Ascendente (A→Z)" else "Descendente (Z→A)",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                onClick = {
                                    vm.setSortDirection(direction)
                                    sortMenuOpen = false
                                },
                                leadingIcon = if (state.sortDir == direction) {
                                    { Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                } else null,
                            )
                        }
                    }
                }
                ApexIconButton(
                    if (state.viewMode == ViewMode.LIST) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                    "Cambiar vista",
                ) { vm.toggleViewMode() }
                ApexIconButton(
                    if (state.showHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (state.showHidden) "Ocultar archivos ocultos" else "Mostrar archivos ocultos",
                ) { vm.setShowHidden(!state.showHidden) }
                ApexIconButton(
                    Icons.Outlined.FilterList,
                    if (showFilter) "Ocultar filtro" else "Filtrar",
                    tint = if (showFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                ) {
                    showFilter = !showFilter
                    if (!showFilter) vm.setFilterQuery("")
                }
                Box {
                    ApexIconButton(Icons.Outlined.Add, "Nuevo") { addMenuOpen = true }
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Nueva carpeta", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                addMenuOpen = false
                                showNewFolderDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Nuevo archivo de texto", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                addMenuOpen = false
                                showNewFileDialog = true
                            },
                        )
                    }
                }
            },
        )

        if (showFilter) {
            FilterBar(
                query = state.filterQuery,
                onQueryChange = vm::setFilterQuery,
                onClose = {
                    showFilter = false
                    vm.setFilterQuery("")
                },
                matchLabel = if (state.filterQuery.isNotBlank()) {
                    "${state.visibleEntries.size}/${state.entries.size}"
                } else {
                    ""
                },
            )
        }

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
            state.loading && state.entries.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    NeonProgressBar(progress = null, modifier = Modifier.padding(horizontal = 40.dp))
                }
            }
            state.error != null -> {
                RefreshableBox(
                    refreshing = false,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    RefreshableEmpty(Icons.Outlined.FolderOpen, state.error ?: "Error")
                }
            }
            state.entries.isEmpty() -> {
                RefreshableBox(
                    refreshing = false,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    RefreshableEmpty(Icons.Outlined.FolderOpen, "Carpeta vacía")
                }
            }
            else -> {
                // weight(1f) instead of fillMaxSize so the folder summary and
                // the SelectionBar below stay pinned on screen; a full-height
                // child would push them off the bottom of the Column.
                RefreshableBox(
                    refreshing = state.loading,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    val visible = state.visibleEntries
                    if (state.viewMode == ViewMode.LIST) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(visible, key = { it.path }, contentType = { it.isDir }) { node ->
                                FileRow(
                                    node = node,
                                    selected = vm.selection.containsKey(node.path),
                                    compact = state.density == ListDensity.COMPACT,
                                    onClick = {
                                        when {
                                            selectionMode -> vm.toggleSelect(node)
                                            state.destMode != null -> if (node.isDir) vm.openDir(node)
                                            node.isDir -> vm.openDir(node)
                                            else -> openFile(node)
                                        }
                                    },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (selectionMode) vm.longPress(node) else contextNode = node
                                    },
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(
                                minSize = when (state.density) {
                                    ListDensity.COMPACT -> 88.dp
                                    ListDensity.NORMAL -> 104.dp
                                    ListDensity.COMFORTABLE -> 120.dp
                                },
                            ),
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(visible, key = { it.path }, contentType = { it.isDir }) { node ->
                                GridTile(
                                    node = node,
                                    selected = vm.selection.containsKey(node.path),
                                    compact = state.density == ListDensity.COMPACT,
                                    onClick = {
                                        when {
                                            selectionMode -> vm.toggleSelect(node)
                                            state.destMode != null -> if (node.isDir) vm.openDir(node)
                                            node.isDir -> vm.openDir(node)
                                            else -> openFile(node)
                                        }
                                    },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (selectionMode) vm.longPress(node) else contextNode = node
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Folder summary bar (hidden while selecting / pasting).
        if (!selectionMode && state.destMode == null && state.entries.isNotEmpty()) {
            val folders = state.entries.count { it.isDir }
            val files = state.entries.size - folders
            val bytes = state.entries.filterNot { it.isDir }.sumOf { it.size }
            Surface(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small,
                color = ApexContainer,
                border = BorderStroke(1.dp, ApexBorder),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val summary = buildList {
                        if (folders > 0) add(if (folders == 1) "1 carpeta" else "$folders carpetas")
                        if (files > 0) add(if (files == 1) "1 archivo" else "$files archivos")
                        if (bytes > 0) add(SizeFormatter.format(bytes))
                    }.joinToString(" · ")
                    Text(
                        summary,
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    if (state.filterQuery.isNotBlank()) {
                        Text(
                            "${state.filteredOut} ocultos por filtro",
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (selectionMode && state.destMode == null) {
            SelectionBar(
                count = vm.selection.size,
                onCopy = { vm.startDestMode(ExplorerViewModel.DestMode.COPY) },
                onSelectAll = { vm.selectAll() },
                onMove = { vm.startDestMode(ExplorerViewModel.DestMode.MOVE) },
                onRename = {
                    val sel = vm.selectedNodes()
                    if (sel.size == 1) showRenameDialog = true
                    else if (sel.size > 1) navigator.push(Screen.BatchRename(sel))
                },
                onDelete = {
                    // Trash-safe deletes always confirm; permanent deletes
                    // (SAF items or trash off) skip it when disabled.
                    val permanent = !trashEnabled || vm.selectedNodes().any { it.uri != null }
                    if (permanent && !confirmPermanentDelete) {
                        launchOperation(OpType.DELETE, vm.deleteFlow())
                    } else {
                        showDeleteConfirm = true
                    }
                },
                onShare = { shareSelected() },
                onCompress = {
                    compressDefaultName = defaultZipName()
                    showCompressDialog = true
                },
                onDuplicate = {
                    val targets = vm.selectedNodes()
                    if (targets.isEmpty() || targets.any { it.uri != null || it.isDir }) {
                        toast("Solo se pueden duplicar archivos del almacenamiento interno")
                    } else {
                        launchOperation(OpType.COPY, vm.duplicateFlow())
                    }
                },
                onFavorite = {
                    val removed = toggleFavorite(vm.selectedNodes())
                    toast(if (removed) "Quitados de favoritos" else "Añadidos a favoritos")
                },
                onProperties = { vm.selectedNodes().firstOrNull()?.let(vm::showProperties) },
                onCopyPaths = { copyPaths() },
                onExtract = if (canExtract) ({ extractHere() }) else null,
                onClear = { vm.clearSelection() },
            )
        }
    }

    // ---- Dialogs ----
    if (showDeleteConfirm) {
        val hasSaf = vm.selectedNodes().any { it.uri != null }
        ConfirmDialog(
            title = "¿Eliminar?",
            message = if (trashEnabled) {
                val trashable = vm.selectedNodes().count { it.uri == null }
                if (hasSaf && trashable > 0) {
                    "$trashable elemento(s) irá(n) a la Papelera (se pueden restaurar). " +
                        "Los elementos SAF se eliminarán de forma permanente."
                } else {
                    "${vm.selection.size} elemento(s) se moverá(n) a la Papelera. Se pueden restaurar desde Inicio → Papelera."
                }
            } else {
                "Se eliminará ${vm.selection.size} elemento(s) de forma permanente. Esta acción no se puede deshacer."
            },
            confirmLabel = if (trashEnabled) "Mover a papelera" else "Eliminar",
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
                if (isValidName(name)) vm.renameSelected(name) else toast("Nombre no válido")
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
                if (isValidName(name)) vm.createFolder(name) else toast("Nombre no válido")
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }
    if (showNewFileDialog) {
        InputDialog(
            title = "Nuevo archivo de texto",
            initialValue = "nuevo.txt",
            onConfirm = { name ->
                showNewFileDialog = false
                if (isValidName(name)) vm.createFile(name) else toast("Nombre no válido")
            },
            onDismiss = { showNewFileDialog = false },
        )
    }
    if (showCompressDialog) {
        InputDialog(
            title = "Comprimir",
            initialValue = compressDefaultName,
            onConfirm = { name ->
                showCompressDialog = false
                if (isValidName(name)) {
                    launchOperation(OpType.COMPRESS, vm.compressFlow(name))
                } else {
                    toast("Nombre no válido")
                }
            },
            onDismiss = { showCompressDialog = false },
        )
    }
    contextNode?.let { node ->
        val isFav = container.favorites.isFavorite(node.path)
        val sheetCanExtract = !node.isDir && container.archive.isSupported(node)
        NodeContextSheet(
            node = node,
            isFavorite = isFav,
            canExtract = sheetCanExtract,
            onOpen = {
                contextNode = null
                if (node.isDir) vm.openDir(node) else openFile(node)
            },
            onOpenWith = {
                contextNode = null
                openWithChooser(node)
            },
            onShare = {
                contextNode = null
                shareNodes(listOf(node))
            },
            onSelect = {
                contextNode = null
                vm.enterSelection(node)
            },
            onCopyTo = {
                contextNode = null
                vm.enterSelection(node)
                vm.startDestMode(ExplorerViewModel.DestMode.COPY)
            },
            onMoveTo = {
                contextNode = null
                vm.enterSelection(node)
                vm.startDestMode(ExplorerViewModel.DestMode.MOVE)
            },
            onRename = {
                contextNode = null
                vm.enterSelection(node)
                showRenameDialog = true
            },
            onDuplicate = {
                contextNode = null
                if (node.uri != null || node.isDir) {
                    toast("Solo se pueden duplicar archivos del almacenamiento interno")
                } else {
                    vm.enterSelection(node)
                    launchOperation(OpType.COPY, vm.duplicateFlow())
                }
            },
            onCompress = {
                contextNode = null
                vm.enterSelection(node)
                compressDefaultName = defaultZipName()
                showCompressDialog = true
            },
            onExtract = {
                contextNode = null
                vm.enterSelection(node)
                extractHere()
            },
            onToggleFavorite = {
                val nowFavorite = container.favorites.toggle(node)
                toast(if (nowFavorite) "Añadido a favoritos" else "Quitado de favoritos")
            },
            onCopyPath = {
                contextNode = null
                copyPathsOf(listOf(node))
            },
            onProperties = {
                contextNode = null
                vm.showProperties(node)
            },
            onDelete = {
                contextNode = null
                vm.enterSelection(node)
                showDeleteConfirm = true
            },
            onDismiss = { contextNode = null },
        )
    }

    state.properties?.let { props ->
        // Local mirror of the star state so the sheet updates instantly on
        // toggle; remember is keyed per node because the sheet can switch files.
        var favorite by remember(props.node.path) {
            mutableStateOf(container.favorites.isFavorite(props.node.path))
        }
        PropertiesSheet(
            state = props,
            isFavorite = favorite,
            onToggleFavorite = {
                favorite = container.favorites.toggle(props.node)
                toast(if (favorite) "Añadido a favoritos" else "Quitado de favoritos")
            },
            onDismiss = { vm.dismissProperties() },
            onComputeHash = { vm.computeHash(it) },
            onCopyText = { text ->
                clipboard.setText(AnnotatedString(text))
                toast("Copiado")
            },
            onOpenWith = { if (!props.node.isDir) openWithChooser(props.node) },
            onHexView = { if (!props.node.isDir) navigator.push(Screen.HexViewer(props.node)) },
        )
    }
}

/** Material3 pull-to-refresh wrapper used by the list/grid/empty/error states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        content()
    }
}

/** Empty/error content on a scrollable surface so pull-to-refresh works there too. */
@Composable
private fun RefreshableEmpty(icon: ImageVector, message: String) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EmptyState(icon, message)
    }
}

/** Instant in-folder filter bar (client-side, matches names case-insensitively). */
@Composable
private fun FilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchLabel: String,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MonoTextStyleSmall.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { /* filtering is live */ }),
            modifier = Modifier
                .weight(1f)
                .background(ApexContainerHigh, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Filtrar por nombre…",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
        if (matchLabel.isNotBlank()) {
            Text(
                matchLabel,
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        ApexIconButton(
            Icons.Outlined.Close,
            "Cerrar filtro",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onClose,
        )
    }
    HorizontalDivider(color = ApexBorder, thickness = 1.dp)
}

/**
 * Bottom sheet with the per-file options shown on long-press: open/share the
 * file, or run every folder/file operation from one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeContextSheet(
    node: FileNode,
    isFavorite: Boolean,
    canExtract: Boolean,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onSelect: () -> Unit,
    onCopyTo: () -> Unit,
    onMoveTo: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyPath: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ApexContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                node.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${typeLabel(node)} · ${SizeFormatter.format(node.size)}",
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = ApexBorder, thickness = 1.dp)

            ContextRow(
                if (node.isDir) Icons.Outlined.FolderOpen else Icons.AutoMirrored.Outlined.OpenInNew,
                "Abrir",
                onClick = onOpen,
            )
            if (!node.isDir) {
                ContextRow(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir con…", onClick = onOpenWith)
                ContextRow(Icons.Outlined.Share, "Compartir", onClick = onShare)
            }
            ContextRow(Icons.Outlined.SelectAll, "Seleccionar", onClick = onSelect)

            HorizontalDivider(color = ApexBorder, thickness = 1.dp)
            ContextRow(Icons.Outlined.ContentCopy, "Copiar a…", onClick = onCopyTo)
            ContextRow(Icons.AutoMirrored.Outlined.DriveFileMove, "Mover a…", onClick = onMoveTo)
            if (!node.isDir) {
                ContextRow(Icons.Outlined.ControlPointDuplicate, "Duplicar", onClick = onDuplicate)
            }
            ContextRow(Icons.Outlined.Edit, "Renombrar", onClick = onRename)
            ContextRow(Icons.Outlined.FolderZip, "Comprimir", onClick = onCompress)
            if (canExtract) {
                ContextRow(Icons.Outlined.Unarchive, "Extraer aquí", onClick = onExtract)
            }

            HorizontalDivider(color = ApexBorder, thickness = 1.dp)
            ContextRow(
                if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                onClick = onToggleFavorite,
            )
            ContextRow(Icons.Outlined.ContentPaste, "Copiar ruta", onClick = onCopyPath)

            HorizontalDivider(color = ApexBorder, thickness = 1.dp)
            ContextRow(Icons.Outlined.Info, "Propiedades", onClick = onProperties)
            ContextRow(Icons.Outlined.Delete, "Eliminar", onClick = onDelete, danger = true)
        }
    }
}

/** One tappable option row inside [NodeContextSheet]. */
@Composable
private fun ContextRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            label,
            tint = if (danger) ApexDanger else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) ApexDanger else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Human label for a file/folder used in the context sheet header. */
private fun typeLabel(node: FileNode): String {
    if (node.isDir) return "Carpeta"
    return when (node.category) {
        Category.IMAGE -> "Imagen"
        Category.VIDEO -> "Vídeo"
        Category.AUDIO -> "Audio"
        Category.DOCUMENT -> "Documento"
        Category.ARCHIVE -> "Comprimido"
        Category.APK -> "APK"
        else -> node.extension.ifBlank { "Archivo" }
    }
}
