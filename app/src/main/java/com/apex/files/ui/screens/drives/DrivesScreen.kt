package com.apex.files.ui.screens.drives

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.Screen
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.storage.DrivesRepository
import com.apex.files.data.storage.StorageStats
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.StorageBar
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun DrivesScreen() {
    val navigator = LocalNavigator.current
    val vm: DrivesViewModel = apexViewModel(key = "drives") { container -> DrivesViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment
                ?.substringAfterLast(':')
                ?.ifBlank { "USB-OTG" }
                ?: "USB-OTG"
            vm.addSafTree(uri, name)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = "Unidades",
            onBack = { navigator.pop() },
            actions = {
                ApexIconButton(Icons.Outlined.Usb, "Conectar USB-OTG") { treeLauncher.launch(null) }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Conecta una tarjeta SD o una memoria USB-OTG mediante el selector del sistema.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.volumes, key = { it.key }) { volume ->
                DriveCard(
                    volume = volume,
                    usage = state.usages[volume.key],
                    onOpen = { navigator.push(Screen.Explorer(volume.location)) },
                    onDisconnect = { volume.safUri?.let(vm::removeSafTree) },
                )
            }
            item {
                ApexCard(
                    Modifier.fillMaxWidth(),
                    onClick = { treeLauncher.launch(null) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Usb, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Conectar USB-OTG", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Concede acceso a un pendrive o tarjeta mediante ACTION_OPEN_DOCUMENT_TREE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveCard(
    volume: DrivesRepository.Volume,
    usage: StorageStats.Usage?,
    onOpen: () -> Unit,
    onDisconnect: () -> Unit,
) {
    ApexCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon: ImageVector = when {
                volume.safUri != null -> Icons.Outlined.Usb
                volume.removable -> Icons.Outlined.SdCard
                else -> Icons.Outlined.Storage
            }
            Icon(
                icon,
                null,
                tint = if (volume.removable || volume.safUri != null) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(volume.name, style = MaterialTheme.typography.titleMedium)
                if (usage != null) {
                    Spacer(Modifier.height(6.dp))
                    StorageBar(
                        fraction = if (usage.totalBytes > 0) usage.usedBytes.toFloat() / usage.totalBytes else 0f,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${SizeFormatter.format(usage.usedBytes)} de ${SizeFormatter.format(usage.totalBytes)}",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Disponible",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (volume.safUri != null) {
                Spacer(Modifier.width(6.dp))
                ApexIconButton(Icons.Outlined.Close, "Desconectar", onDisconnect)
            }
        }
    }
}