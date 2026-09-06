package com.apex.files.ui.screens.smart

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoveToInbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.apex.files.core.OpType
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.search.SmartGroup
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.LocalOperationCenter
import com.apex.files.ui.NodeOpener
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.ConfirmDialog
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.FileIcon
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexSurface1
import com.apex.files.ui.theme.MonoTextStyleSmall
import java.io.File

/**
 * Carpetas inteligentes: virtual groups computed from content (OCR text of
 * images/PDFs, plain text, extension and name). Files are never moved without
 * an explicit, confirmed user action.
 */
@Composable
fun SmartGroupsScreen() {
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val center = LocalOperationCenter.current
    val context = LocalContext.current
    val vm: SmartGroupsViewModel = apexViewModel(key = "smart-groups") { c -> SmartGroupsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    var moveGroup by remember { mutableStateOf<SmartGroup?>(null) }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = if (state.openGroup == null) "Carpetas inteligentes" else state.openGroup!!.label,
            onBack = {
                if (state.openGroup != null) vm.backToGroups() else navigator.pop()
            },
        )

        when {
            state.loading -> {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    NeonProgressBar(progress = null, modifier = Modifier.padding(horizontal = 40.dp))
                }
            }
            state.openGroup != null -> {
                val info = state.groups.firstOrNull { it.group == state.openGroup }
                if (info == null || info.nodes.isEmpty()) {
                    EmptyState(Icons.Outlined.FolderOpen, "Grupo vacío")
                } else {
                    Text(
                        "${info.nodes.size} archivos · ${SizeFormatter.format(info.totalBytes)}",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(info.nodes, key = { it.path }) { node ->
                            GroupFileTile(node) {
                                NodeOpener.open(node, container, navigator, context, imageContext = info.nodes) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            state.groups.isEmpty() -> {
                EmptyState(Icons.Outlined.AutoAwesome, "Sin grupos aún")
                Text(
                    "Activa la búsqueda semántica en Ajustes y espera a que el índice se construya por bloques.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.groups, key = { it.group.name }) { info ->
                        GroupCard(info) { vm.openGroup(info.group) }
                        TextButton(onClick = { moveGroup = info.group }) {
                            Text(
                                "Mover a ${vm.smartDestPath(info.group)}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }

    moveGroup?.let { group ->
        ConfirmDialog(
            title = "¿Mover grupo?",
            message = "Se moverán ${state.groups.firstOrNull { it.group == group }?.nodes?.size ?: 0} " +
                "archivos a ${vm.smartDestPath(group)}. ¿Continuar?",
            confirmLabel = "Mover",
            onConfirm = {
                moveGroup = null
                center.launch(OpType.MOVE, vm.moveFlow(group)) { ok ->
                    val msg = if (ok) {
                        vm.consumeSummary() ?: "Operación completada"
                    } else {
                        center.lastError.value ?: "Operación cancelada"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { moveGroup = null },
        )
    }
}

@Composable
private fun GroupCard(info: SmartGroupsViewModel.GroupInfo, onClick: () -> Unit) {
    ApexCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info.group.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${info.nodes.size} archivos · ${SizeFormatter.format(info.totalBytes)}",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.MoveToInbox,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun GroupFileTile(node: FileNode, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(ApexSurface1),
            contentAlignment = Alignment.Center,
        ) {
            if (node.category == Category.IMAGE) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(node.uri ?: File(node.path))
                        .size(160)
                        .build(),
                    contentDescription = node.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                FileIcon(node.category, false, Modifier.size(44.dp), size = 30.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            node.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            SizeFormatter.format(node.size),
            style = MonoTextStyleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}