package com.apex.files.ui.screens.category

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.NodeOpener
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.FileIcon
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexShapes
import com.apex.files.ui.theme.ApexSurface1
import com.apex.files.ui.theme.MonoTextStyleSmall
import java.io.File

@Composable
fun CategoryScreen(category: Category) {
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val key = remember { "category-${category.name}" }
    val vm: CategoryViewModel = apexViewModel(key = key) { c -> CategoryViewModel(c, category) }
    val state by vm.state.collectAsStateWithLifecycle()

    fun openFile(node: FileNode) {
        NodeOpener.open(node, container, navigator, context, imageContext = state.nodes) { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = when (category) {
                Category.IMAGE -> "Imágenes"
                Category.VIDEO -> "Videos"
                Category.AUDIO -> "Audio"
                Category.DOCUMENT -> "Documentos"
                Category.ARCHIVE -> "Archivos"
                else -> "Categoría"
            },
            onBack = { navigator.pop() },
        )

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                NeonProgressBar(progress = null, modifier = Modifier.padding(horizontal = 40.dp))
            }
        } else if (state.nodes.isEmpty()) {
            EmptyState(Icons.Outlined.FolderOpen, "Sin resultados")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.nodes, key = { it.path + it.name }) { node ->
                    CategoryTile(node, category) { openFile(node) }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(node: FileNode, category: Category, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(ApexShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, ApexShapes.medium)
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
            if (category == Category.IMAGE) {
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
            com.apex.files.data.fs.SizeFormatter.format(node.size),
            style = MonoTextStyleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}