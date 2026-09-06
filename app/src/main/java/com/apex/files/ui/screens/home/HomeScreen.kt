package com.apex.files.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
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
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.NodeOpener
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.FileIcon
import com.apex.files.ui.components.StorageBar
import com.apex.files.ui.components.WelcomeTour
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall
import java.io.File

/**
 * Home dashboard: compact storage hero, quick tools (including the Wi-Fi
 * analyzer), smart suggestions, categories, favorites, recents and drives.
 */
@Composable
fun HomeScreen() {
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val vm: HomeViewModel = apexViewModel(key = "home") { container -> HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by container.favorites.items.collectAsStateWithLifecycle()
    val recents by container.recents.items.collectAsStateWithLifecycle()

    // First run: show the welcome tour once (replayable from Ajustes).
    val onboarding = container.onboarding
    var showTour by remember { mutableStateOf(!onboarding.tourSeen) }

    fun openFavorite(node: FileNode) {
        if (node.isDir) {
            container.recents.record(node)
            val location = node.uri?.let { com.apex.files.data.model.Location.Saf(it, node.name) }
                ?: com.apex.files.data.model.Location.Fs(File(node.path))
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
        // ---- Compact header ----
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "APEX",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "FILE MANAGER",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ApexIconButton(Icons.Outlined.Search, "Buscar") { navigator.push(Screen.Search()) }
                ApexIconButton(Icons.Outlined.Refresh, "Actualizar") { vm.refresh() }
                ApexIconButton(Icons.Outlined.Settings, "Ajustes") { navigator.push(Screen.Settings) }
            }
        }

        // ---- Storage hero (tap → stats) ----
        item {
            ApexCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = { navigator.push(Screen.Stats) },
                contentPadding = PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Storage,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Almacenamiento",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (state.indexing) {
                            Text("Indexando…", style = MaterialTheme.typography.labelSmall, color = ApexTextMuted)
                        }
                    }
                    val percent = if (state.totalBytes > 0) ((state.usedBytes * 100) / state.totalBytes).toInt() else 0
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$percent%", style = MonoTextStyleSmall, color = MaterialTheme.colorScheme.onBackground)
                        Text("usado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                val fraction = if (state.totalBytes > 0) state.usedBytes.toFloat() / state.totalBytes else 0f
                StorageBar(fraction)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        "${SizeFormatter.format(state.usedBytes)} de ${SizeFormatter.format(state.totalBytes)}",
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
        item { SectionLabel("Herramientas") }
        item {
            val tools = remember { listOf(
                ToolSpec(Icons.Outlined.CleaningServices, "Limpiador Vacío", "Carpetas vacías") { navigator.push(Screen.Cleaner) },
                ToolSpec(Icons.Outlined.ContentCopy, "Duplicados", "Detección SHA-256") { navigator.push(Screen.Duplicates) },
                ToolSpec(Icons.Outlined.Android, "Filtro APK", "Instaladores redundantes") { navigator.push(Screen.Apk) },
                ToolSpec(Icons.Outlined.Bolt, "Analizador de espacio", "Mapa de bloques") {
                    navigator.push(Screen.SpaceAnalyzer(com.apex.files.data.model.Location.Fs(com.apex.files.data.fs.Paths.internalRoot())))
                },
            ) }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tools.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { spec ->
                            ToolTile(spec.icon, spec.title, spec.subtitle, spec.onClick, Modifier.weight(1f))
                        }
                    }
                }
                FullWidthToolRow(Icons.Outlined.Wifi, "Analizador Wi-Fi", "Redes, señal y dispositivos") {
                    navigator.push(Screen.Wifi)
                }
                FullWidthToolRow(Icons.Outlined.DeleteSweep, "Papelera", "Recupera elementos eliminados") {
                    navigator.push(Screen.Trash)
                }
            }
        }

        // ---- Smart suggestions: largest files ----
        if (state.largest.isNotEmpty()) {
            item { SectionLabel("Sugerencias") }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.largest.forEach { node ->
                        SuggestionRow(node = node, onClick = { openFavorite(node) })
                    }
                }
            }
        }

        // ---- Categories ----
        item { SectionLabel("Categorías") }
        item {
            val columns = ((LocalConfiguration.current.screenWidthDp - 42) / 104).coerceIn(2, 6)
            val entries = listOf(
                Triple(Category.IMAGE, Icons.Outlined.Image, "Imágenes"),
                Triple(Category.VIDEO, Icons.Outlined.Movie, "Videos"),
                Triple(Category.AUDIO, Icons.Outlined.Audiotrack, "Audio"),
                Triple(Category.DOCUMENT, Icons.Outlined.Description, "Documentos"),
                Triple(Category.ARCHIVE, Icons.Outlined.FolderZip, "Archivos"),
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Recientes", modifier = Modifier.weight(1f))
                    TextButton(onClick = { container.recents.clear() }) {
                        Text("Limpiar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (drive in state.drives) {
                    ApexCard(
                        Modifier.fillMaxWidth(),
                        onClick = { navigator.push(Screen.Explorer(drive.location)) },
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Storage,
                                null,
                                tint = if (drive.removable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                drive.name,
                                style = MaterialTheme.typography.titleSmall,
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
                    contentPadding = PaddingValues(12.dp),
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

    if (showTour) {
        WelcomeTour(onFinish = {
            showTour = false
            onboarding.markTourSeen()
        })
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** Compact suggestion row: one of the largest files, tappable to open it. */
@Composable
private fun SuggestionRow(
    node: FileNode,
    onClick: () -> Unit,
) {
    ApexCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileIcon(node.category, node.isDir, Modifier.size(22.dp), size = 18.dp)
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

/** Compact two-per-row tool tile (icon, title, one-line subtitle). */
@Composable
private fun ToolTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ApexCard(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Slim horizontal full-width entry (icon left, title/subtitle, chevron). */
@Composable
private fun FullWidthToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ApexCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun FavoriteCard(
    node: FileNode,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    ApexCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (node.isDir) {
                Icon(Icons.Outlined.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            } else {
                FileIcon(node.category, false, Modifier.size(20.dp), size = 18.dp)
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
    ApexCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileIcon(node.category, node.isDir, Modifier.size(26.dp), size = 16.dp)
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
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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