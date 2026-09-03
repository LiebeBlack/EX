package com.apex.files.ui.screens.stats

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.NodeOpener
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.FileIcon
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexAmber
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainer
import com.apex.files.ui.theme.ApexCyan
import com.apex.files.ui.theme.ApexDanger
import com.apex.files.ui.theme.ApexEmerald
import com.apex.files.ui.theme.ApexShapes
import com.apex.files.ui.theme.ApexTextSecondary
import com.apex.files.ui.theme.ApexViolet
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun StatsScreen() {
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val vm: StatsViewModel = apexViewModel(key = "stats") { c -> StatsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()

    fun openFile(node: FileNode) {
        NodeOpener.open(node, container, navigator, context) { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(title = "Estadísticas", onBack = { navigator.pop() })
        when {
            state.computing -> {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    NeonProgressBar(progress = null)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Calculando…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.empty -> {
                EmptyState(Icons.Outlined.Insights, "Índice vacío. Reindexa desde Búsqueda (⟳) para analizar el almacenamiento.")
            }
            state.stats != null -> {
                StatsContent(
                    stats = state.stats!!,
                    onOpenFile = ::openFile,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatsContent(
    stats: StorageStats,
    onOpenFile: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // ---- Totals ----
        item {
            ApexCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Insights, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "${stats.fileCount} archivos",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "${SizeFormatter.format(stats.totalBytes)} en total",
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- By category ----
        item { StatsHeader("Por categoría") }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (cs in stats.byCategory) {
                    CategoryRow(cs, stats.totalBytes)
                }
            }
        }

        // ---- Largest files ----
        if (stats.topLargest.isNotEmpty()) {
            item { StatsHeader("Archivos más grandes") }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    stats.topLargest.forEach { node ->
                        ApexCard(
                            Modifier.fillMaxWidth(),
                            onClick = { onOpenFile(node) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FileIcon(node.category, false, Modifier.size(30.dp), size = 19.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        node.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${SizeFormatter.format(node.size)} · ${node.path.substringBeforeLast('/').ifBlank { "/" }}",
                                        style = MonoTextStyleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Top extensions ----
        if (stats.topExtensions.isNotEmpty()) {
            item { StatsHeader("Extensiones más comunes") }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    stats.topExtensions.forEach { (ext, count) ->
                        Text(
                            ".$ext · $count",
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(ApexShapes.small)
                                .background(ApexContainer)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun CategoryRow(stat: CategoryStat, totalBytes: Long) {
    val color = categoryColor(stat.category)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                categoryLabel(stat.category),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${SizeFormatter.format(stat.bytes)} · ${stat.count}",
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        val fraction = if (totalBytes > 0) stat.bytes.toFloat() / totalBytes else 0f
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexBorder)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

private fun categoryLabel(category: Category): String = when (category) {
    Category.IMAGE -> "Imágenes"
    Category.VIDEO -> "Videos"
    Category.AUDIO -> "Audio"
    Category.DOCUMENT -> "Documentos"
    Category.ARCHIVE -> "Archivos comprimidos"
    Category.APK -> "APKs"
    Category.DIRECTORY -> "Carpetas"
    else -> "Otros"
}

private fun categoryColor(category: Category): Color = when (category) {
    Category.IMAGE -> ApexCyan
    Category.VIDEO -> ApexViolet
    Category.AUDIO -> ApexEmerald
    Category.DOCUMENT -> ApexAmber
    Category.ARCHIVE -> ApexDanger
    Category.APK -> ApexCyan
    else -> ApexTextSecondary
}