package com.apex.files.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.Screen
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.StorageBar
import com.apex.files.ui.theme.MonoTextStyleSmall
import com.apex.files.ui.theme.ApexTextMuted

@Composable
fun HomeScreen() {
    val container = LocalContainer.current
    val navigator = LocalNavigator.current
    val vm: HomeViewModel = apexViewModel(key = "home") { container -> HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

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
                    Text("Almacenamiento", style = MaterialTheme.typography.titleMedium)
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(192.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                item { ToolCard(Icons.Outlined.CleaningServices, "Limpiador Vacío", "Elimina carpetas vacías") { navigator.push(Screen.Cleaner) } }
                item { ToolCard(Icons.Outlined.ContentCopy, "Buscador de Duplicados", "Detección por SHA-256") { navigator.push(Screen.Duplicates) } }
                item { ToolCard(Icons.Outlined.Android, "Filtro APK", "Instaladores redundantes") { navigator.push(Screen.Apk) } }
                item { ToolCard(Icons.Outlined.Bolt, "Analizador de espacio", "Mapa de bloques") { navigator.push(Screen.SpaceAnalyzer(com.apex.files.data.model.Location.Fs(com.apex.files.data.fs.Paths.internalRoot()))) } }
            }
        }

        // ---- Categories ----
        item { SectionLabel("Categorías") }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(168.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false,
            ) {
                categoryItems(state, navigator)
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
                            Text(drive.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                ApexCard(
                    Modifier.fillMaxWidth(),
                    onClick = { navigator.push(Screen.Drives) },
                ) {
                    Text("Gestionar unidades · USB-OTG", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ApexCard(onClick = onClick) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(22.dp).width(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.categoryItems(
    state: HomeViewModel.UiState,
    navigator: com.apex.files.Navigator,
) {
    val entries = listOf(
        Triple(Category.IMAGE, Icons.Outlined.Image, "Imágenes"),
        Triple(Category.VIDEO, Icons.Outlined.Movie, "Videos"),
        Triple(Category.AUDIO, Icons.Outlined.Audiotrack, "Audio"),
        Triple(Category.DOCUMENT, Icons.Outlined.Description, "Documentos"),
        Triple(Category.ARCHIVE, Icons.Outlined.FolderZip, "Archivos"),
    )
    items(entries, key = { it.first.name }) { (category, icon, label) ->
        ApexCard(
            onClick = { navigator.push(Screen.Category(category)) },
            contentPadding = PaddingValues(10.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(20.dp).width(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (state.categoryCounts[category] ?: 0).toString(),
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}