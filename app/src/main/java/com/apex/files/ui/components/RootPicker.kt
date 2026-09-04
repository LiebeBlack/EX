package com.apex.files.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.apex.files.data.storage.DrivesRepository
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.MonoTextStyleSmall

/**
 * Row + picker dialog that lets a cleaning tool choose which volume it
 * scans (internal storage, SD card or a granted SAF tree).
 */
@Composable
fun RootPickerRow(
    currentName: String,
    currentKey: String,
    volumes: List<DrivesRepository.Volume>,
    enabled: Boolean = true,
    onPick: (DrivesRepository.Volume) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    ApexCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Carpeta de análisis", style = MaterialTheme.typography.titleMedium)
                Text(
                    currentName,
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { open = true }, enabled = enabled && volumes.isNotEmpty()) {
                Text(
                    "Cambiar",
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = ApexContainerHigh,
                border = BorderStroke(1.dp, ApexBorder),
            ) {
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(
                        "Elegir carpeta de análisis",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp)
                            .heightIn(max = 380.dp)
                    ) {
                        volumes.forEach { volume ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPick(volume)
                                        open = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val icon = when {
                                    volume.safUri != null -> Icons.Outlined.Usb
                                    volume.removable -> Icons.Outlined.SdCard
                                    else -> Icons.Outlined.Storage
                                }
                                Icon(
                                    icon,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    volume.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (volume.key == currentKey) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        "Seleccionada",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(end = 10.dp, bottom = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                        TextButton(onClick = { open = false }) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
