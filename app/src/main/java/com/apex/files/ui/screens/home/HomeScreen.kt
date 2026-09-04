package com.apex.files.ui.screens.home

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.Screen
import com.apex.files.data.fs.DateFormatter
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.NodeOpener
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.FileIcon
import com.apex.files.ui.components.StorageBar
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall
import java.io.File

@Composable
fun HomeScreen() {
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val vm: HomeViewModel = apexViewModel(key = "home") { container -> HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by container.favorites.items.collectAsStateWithLifecycle()
    val recents by container.recents.items.collectAsStateWithLifecycle()

    fun openFavorite(node: FileNode) {
        if (node.isDir) {
            container.recents.record(node)
            val location = node.uri?.let { Location.Saf(it, node.name) }
                ?: Location.Fs(File(node.path))
            navigator.push(Screen.Explorer(location))
        } else {
            NodeOpener.open(node, container, navigator, context) { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // ---- Premium header ----
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "APEX",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "FILE MANAGER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ApexIconButton(Icons.Outlined.Search, "Buscar") {
                    navigator.push(Screen.Search())
                }
                ApexIconButton(Icons.Outlined.Refresh, "Actualizar") { vm.refresh() }
                ApexIconButton(Icons.Outlined.Settings, "Ajustes") {
                    navigator.push(Screen.Settings)
                }
            }
        }

        // ---- Storage card ----
        item {
            ApexCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Storage,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Almacenamiento",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.indexing) {
                        Text("Indexando…", style = MaterialTheme.typography.labelSmall, color = ApexTextMuted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                val fraction = if (state.totalBytes > 0) state.usedBytes.toFloat() / state.totalBytes else 0f
                StorageBar(fraction)
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(
                        "${SizeFormatter.format(state.usedBytes)} ${"de"} ${SizeFormatter.format(state.totalBytes)}",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Libre ${SizeFormatter.format(state.totalBytes - state.usedBytes)}",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Quick tools ----
        item {
            SectionLabel("Herramientas")
        }
        item {
            val tools = listOf(
                ToolSpec(Icons.Outlined.CleaningServices, "Limpiador Vacío", "Elimina carpetas vacías") { navigator.push(Screen.Cleaner) },
                ToolSpec(Icons.Outlined.ContentCopy, "Buscador de Duplicados", "Detección por SHA-256") { navigator.push(Screen.Duplicates) },
                ToolSpec(Icons.Outlined.Android, "Filtro APK", "Instaladores redundantes") { navigator.push(Screen.Apk) },
                ToolSpec(Icons.Outlined.Bolt, "Analizador de espacio", "Mapa de bloques") { navigator.push(Screen.SpaceAnalyzer(com.apex.files.data.model.Location.Fs(com.apex.files.data.fs.Paths.internalRoot()))) },
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tools.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { spec ->
                            ToolCard(spec.icon, spec.title, spec.subtitle, spec.onClick, Modifier.weight(1f))
                        }
                    }
                }
                // Full-width row: system log console.
                ToolCard(
                    icon = Icons.Outlined.BugReport,
                    title = "Consola de sistema",
                    subtitle = "Registro logcat del dispositivo",
                    onClick = { navigator.push(Screen.Logcat) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Storage insights ----
        item {
            ApexCard(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp),
                onClick = { navigator.push(Screen.Stats) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Insights, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Estadísticas de almacenamiento",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Tipos de archivo, tamaños y más grandes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- Smart suggestions: largest files ----
        if (state.largest.isNotEmpty()) {
            item { SectionLabel("Sugerencias") }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.largest.forEach { node ->
                        SuggestionRow(
                            node = node,
                            onClick = { openFavorite(node) },
                        )
                    }
                }
            }
        }

        // ---- Categories ----
        item { SectionLabel("Categorías") }
        item {
            // Adaptive column count (min tile ~104dp) with content-sized rows:
            // no fixed heights, so nothing clips or overlaps at any font scale.
            val columns = ((LocalConfiguration.current.screenWidthDp - 40) / 104).coerceIn(2, 6)
            val entries = listOf(
                Triple(Category.IMAGE, Icons.Outlined.Image, "Imágenes"),
                Triple(Category.VIDEO, Icons.Outlined.Movie, "Videos"),
                Triple(Category.AUDIO, Icons.Outlined.Audiotrack, "Audio"),
                Triple(Category.DOCUMENT, Icons.Outlined.Description, "Documentos"),
                Triple(Category.ARCHIVE, Icons.Outlined.FolderZip, "Archivos"),
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (category, icon, label) ->
                            CategoryTile(
                                category = category,
                                icon = icon,
                                label = label,
                                count = state.categoryCounts[category] ?: 0,
                                onClick = { navigator.push(Screen.Category(category)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // ---- Favorites ----
        if (favorites.isNotEmpty()) {
            item { SectionLabel("Favoritos") }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    favorites.forEach { favorite ->
                        FavoriteCard(
                            node = favorite.node,
                            onClick = { openFavorite(favorite.node) },
                            onRemove = { container.favorites.remove(favorite.node.path) },
                        )
                    }
                }
            }
        }

        // ---- Recents ----
        if (recents.isNotEmpty()) {
            item { SectionLabel("Recientes") }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    recents.forEach { entry ->
                        RecentRow(
                            node = entry.node,
                            openedAt = entry.openedAt,
                            onClick = { openFavorite(entry.node) },
                            onRemove = { container.recents.remove(entry.node.path) },
                        )
                    }
                }
            }
        }

        // ---- Drives ----
        item { SectionLabel("Unidades") }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (drive in state.drives) {
                    ApexCard(
                        Modifier.fillMaxWidth(),
                        onClick = {
                            navigator.push(Screen.Explorer(drive.location))
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Storage,
                                null,
                                tint = if (drive.removable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                drive.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                ApexCard(
                    Modifier.fillMaxWidth(),
                    onClick = { navigator.push(Screen.Drives) },
                ) {
                    Text(
                        "Gestionar unidades · USB-OTG",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp),
    )
}

/** Suggestion row: one of the largest files, tappable to open it. */
@Composable
private fun SuggestionRow(
    node: FileNode,
    onClick: () -> Unit,
) {
    ApexCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileIcon(node.category, node.isDir, Modifier.size(24.dp), size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.titleSmall,
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

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ApexCard(onClick = onClick, modifier = modifier) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(22.dp).width(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun FavoriteCard(
    node: FileNode,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    ApexCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (node.isDir) {
                Icon(Icons.Outlined.Star, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                FileIcon(node.category, false, Modifier.size(22.dp), size = 20.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (node.isDir) "Carpeta" else SizeFormatter.format(node.size),
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ApexIconButton(
                Icons.Outlined.Close,
                "Quitar de favoritos",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun RecentRow(
    node: FileNode,
    openedAt: Long,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    ApexCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileIcon(node.category, node.isDir, Modifier.size(28.dp), size = 18.dp)
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
                    "${DateFormatter.relative(openedAt)} · ${node.path.substringBeforeLast('/').ifBlank { "/" }}",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ApexIconButton(
                Icons.Outlined.Close,
                "Quitar de recientes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onRemove,
            )
        }
    }
}

/** Tool entry rendered by the two-per-row quick-tools grid. */
private data class ToolSpec(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun CategoryTile(
    category: Category,
    icon: ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ApexCard(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(10.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(20.dp).width(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            count.toString(),
            style = MonoTextStyleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}