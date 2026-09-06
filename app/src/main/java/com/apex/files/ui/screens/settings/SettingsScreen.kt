package com.apex.files.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.BuildConfig
import com.apex.files.Screen
import com.apex.files.core.Accent
import com.apex.files.core.ListDensity
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.SortDirection
import com.apex.files.data.model.SortOrder
import com.apex.files.data.model.ViewMode
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.ConfirmDialog
import kotlinx.coroutines.launch
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexCustomAccentPalette
import com.apex.files.ui.theme.MonoTextStyleSmall
import com.apex.files.ui.theme.accentColor

@Composable
fun SettingsScreen() {
    val navigator = LocalNavigator.current
    val vm: SettingsViewModel = apexViewModel(key = "settings") { c -> SettingsViewModel(c) }
    val accent by vm.accent.collectAsStateWithLifecycle()
    val customAccent by vm.customAccent.collectAsStateWithLifecycle()
    val showHidden by vm.showHidden.collectAsStateWithLifecycle()
    val sortDirection by vm.sortDirection.collectAsStateWithLifecycle()
    val trashEnabled by vm.trashEnabled.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val sortOrder by vm.sortOrder.collectAsStateWithLifecycle()
    val density by vm.density.collectAsStateWithLifecycle()
    val confirmPermanentDelete by vm.confirmPermanentDelete.collectAsStateWithLifecycle()

    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var cacheSize by remember { mutableStateOf(0L) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { cacheSize = vm.thumbnailCacheBytes() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ApexTopBar(title = "Ajustes", onBack = { navigator.pop() })

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Hidden files
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Mostrar archivos ocultos", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Archivos que empiezan por «.» y carpetas con .nomedia",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showHidden,
                        onCheckedChange = vm::setShowHidden,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = ApexBorder,
                            uncheckedBorderColor = ApexBorder,
                        ),
                    )
                }
            }

            // Default sort: criterion + direction (applied when opening a folder)
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SwapVert, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Orden por defecto", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Criterio y dirección al abrir una carpeta",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Criterio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DirectionChip("Nombre", sortOrder == SortOrder.NAME) { vm.setSortOrder(SortOrder.NAME) }
                    DirectionChip("Tamaño", sortOrder == SortOrder.SIZE) { vm.setSortOrder(SortOrder.SIZE) }
                    DirectionChip("Fecha", sortOrder == SortOrder.DATE) { vm.setSortOrder(SortOrder.DATE) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Dirección",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DirectionChip("Ascendente", sortDirection == SortDirection.ASC) { vm.setSortDirection(SortDirection.ASC) }
                    DirectionChip("Descendente", sortDirection == SortDirection.DESC) { vm.setSortDirection(SortDirection.DESC) }
                }
            }

            // Default Explorer view (List / Grid)
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (viewMode == ViewMode.LIST) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Vista por defecto", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Lista o cuadrícula al abrir una carpeta",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DirectionChip("Lista", viewMode == ViewMode.LIST) { vm.setViewMode(ViewMode.LIST) }
                    DirectionChip("Cuadrícula", viewMode == ViewMode.GRID) { vm.setViewMode(ViewMode.GRID) }
                }
            }

            // Papelera (soft delete)
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeleteSweep, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Papelera", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (trashEnabled) {
                                "Los borrados se pueden restaurar; guarda una copia por volumen en .apex_trash"
                            } else {
                                "Desactivada: los archivos se eliminan de forma permanente"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = trashEnabled,
                        onCheckedChange = vm::setTrashEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = ApexBorder,
                            uncheckedBorderColor = ApexBorder,
                        ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { navigator.push(Screen.Trash) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Abrir papelera",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Neon accent (presets + custom palette)
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Acento neón", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Accent.PRESETS.forEach { candidate ->
                        AccentSwatch(
                            color = accentColor(candidate, customAccent),
                            selected = accent == candidate,
                            label = candidate.name,
                            onClick = { vm.setAccent(candidate) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(ApexContainerHigh)
                        .clickable { showCustomDialog = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(accentColor(accent, customAccent), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Personalizado",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (accent == Accent.CUSTOM) "Activo" else "Toca para elegir",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // List density
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.ViewList, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Densidad de la lista", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ajusta el tamaño de las filas y la cuadrícula",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DirectionChip("Compacta", density == ListDensity.COMPACT) { vm.setDensity(ListDensity.COMPACT) }
                    DirectionChip("Normal", density == ListDensity.NORMAL) { vm.setDensity(ListDensity.NORMAL) }
                    DirectionChip("Amplia", density == ListDensity.COMFORTABLE) { vm.setDensity(ListDensity.COMFORTABLE) }
                }
            }

            // Permanent-delete confirmation
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeleteSweep, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Confirmar borrado permanente", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Pide confirmación antes de borrar de forma permanente y de vaciar la papelera",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = confirmPermanentDelete,
                        onCheckedChange = vm::setConfirmPermanentDelete,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = ApexBorder,
                            uncheckedBorderColor = ApexBorder,
                        ),
                    )
                }
            }

            // Thumbnail cache manager
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Miniaturas (caché)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Caché local de imágenes · ${SizeFormatter.format(cacheSize)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                vm.clearThumbnailCache()
                                cacheSize = vm.thumbnailCacheBytes()
                            }
                        },
                    ) {
                        Text("Limpiar", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Welcome tour replay
            ApexCard(
                Modifier.fillMaxWidth(),
                onClick = {
                    container.onboarding.resetTour()
                    navigator.popToRoot()
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ver guía de bienvenida", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Muestra el tour de inicio de nuevo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Reset settings
            ApexCard(
                Modifier.fillMaxWidth(),
                onClick = { showResetConfirm = true },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Restablecer ajustes", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Vuelve a los valores por defecto",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Benchmark
            ApexCard(
                Modifier.fillMaxWidth(),
                onClick = { navigator.push(Screen.Benchmark) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Speed, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Benchmark de almacenamiento", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Mide la velocidad de lectura y escritura reales",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // About entry
            ApexCard(
                Modifier.fillMaxWidth(),
                onClick = { navigator.push(Screen.About) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Acerca de APEX", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Versión ${BuildConfig.VERSION_NAME} · garantías y estado de permisos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomAccentDialog(
            isCustomActive = accent == Accent.CUSTOM,
            customHex = customAccent,
            onPick = { hex ->
                vm.setCustomAccent(hex)
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false },
        )
    }
    if (showResetConfirm) {
        ConfirmDialog(
            title = "Restablecer ajustes",
            message = "Se restaurarán el tema, el orden, la papelera y las preferencias de la interfaz. La guía de bienvenida se mostrará de nuevo.",
            confirmLabel = "Restablecer",
            destructive = false,
            onConfirm = {
                showResetConfirm = false
                vm.resetSettings()
                container.onboarding.resetTour()
            },
            onDismiss = { showResetConfirm = false },
        )
    }
}

@Composable
private fun DirectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .border(
                BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else ApexBorder),
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CustomAccentDialog(
    isCustomActive: Boolean,
    customHex: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ApexContainerHigh,
            border = BorderStroke(1.dp, ApexBorder),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Color personalizado",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Elige un color; se aplica a toda la interfaz al instante.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ApexCustomAccentPalette.chunked(6).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            row.forEach { hex ->
                                val color = Color(hex)
                                val selected = isCustomActive && customHex == hex
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(color, CircleShape)
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape,
                                        )
                                        .clickable { onPick(hex) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(color, CircleShape)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = Color.White,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
