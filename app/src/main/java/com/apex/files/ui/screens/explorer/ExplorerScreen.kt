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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.Screen
import com.apex.files.core.OpType
import com.apex.files.data.fs.FileKinds
import com.apex.files.data.fs.SizeFormatter
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
import com.apex.files.ui.theme.MonoTextStyleSmall

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
    val trashEnabled by container.settings.trashEnabled.collectAsStateWithLifecycle()

    val toast: (String) -> Unit = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    // One-shot failures (rename / new folder) surfaced as toasts.
    LaunchedEffect(state.notice) {
        state.notice?.let { msg ->
            toast(msg)
            vm.consumeNotice()
        }
    }

    // Internal back handling (selection / destination / directory up).
    BackHandler(enabled = state.selectionMode || state.destMode != null || vm.canGoUp()) {
        when {
            state.selectionMode -> vm.clearSelection()
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

    fun copyPaths() {
        val paths = vm.selectedNodes().map { it.path }.joinToString("\n")
        if (paths.isEmpty()) return
        clipboard.setText(AnnotatedString(paths))
        toast("Ruta(s) copiadas al portapapeles")
    }

    fun extractHere() {
        launchOperation(OpType.EXTRACT, vm.extractHereFlow())
    }

    // Single archive selected → the selection bar shows “Extraer aquí”.
    val selected = state.entries.filter { it.path in state.selection }
    val canExtract = selected.size == 1 && !selected[0].isDir && container.archive.isSupported(selected[0])

    fun launchOperation(type: OpType, flow: kotlinx.coroutines.flow.Flow<com.apex.files.core.OpProgress>) {
        center.launch(type, flow) { ok ->
            val msg = when {
                ok -> vm.consumeSummary() ?: "Operación completada"
                else -> center.lastError.value ?: vm.consumeError() ?: "Operación cancelada"
            }
            vm.onOperationFinished(ok)
            toast(msg)
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
                RefreshableBox(
                    refreshing = state.loading,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val visible = state.visibleEntries
                    if (state.viewMode == ViewMode.LIST) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(visible, key = { it.path }) { node ->
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
                                        vm.longPress(node)
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
                            items(visible, key = { it.path }) { node ->
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
                                        vm.longPress(node)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Folder summary bar (hidden while selecting / pasting).
        if (!state.selectionMode && state.destMode == null && state.entries.isNotEmpty()) {
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
                    Text(
                        "$folders carpeta(s) · $files archivo(s) · ${SizeFormatter.format(bytes)}",
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

        if (state.selectionMode && state.destMode == null) {
            SelectionBar(
                count = state.selection.size,
                onCopy = { vm.startDestMode(ExplorerViewModel.DestMode.COPY) },
                onSelectAll = { vm.selectAll() },
                onMove = { vm.startDestMode(ExplorerViewModel.DestMode.MOVE) },
                onRename = {
                    val sel = vm.selectedNodes()
                    if (sel.size == 1) showRenameDialog = true
                    else if (sel.size > 1) navigator.push(Screen.BatchRename(sel))
                },
                onDelete = { showDeleteConfirm = true },
                onShare = { shareSelected() },
                onCompress = { showCompressDialog = true },
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
                    "${state.selection.size} elemento(s) se moverá(n) a la Papelera. Se pueden restaurar desde Inicio → Papelera."
                }
            } else {
                "Se eliminará ${state.selection.size} elemento(s) de forma permanente. Esta acción no se puede deshacer."
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
    if (showNewFileDialog) {
        InputDialog(
            title = "Nuevo archivo de texto",
            initialValue = "nuevo.txt",
            onConfirm = { name ->
                showNewFileDialog = false
                vm.createFile(name)
            },
            onDismiss = { showNewFileDialog = false },
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
