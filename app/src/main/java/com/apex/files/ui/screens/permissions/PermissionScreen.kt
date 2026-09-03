package com.apex.files.ui.screens.permissions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.theme.ApexTextMuted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Storage gate shown before the app: All Files Access, media permissions,
 * and a SAF fallback for devices that deny the broad permission.
 */
@Composable
fun PermissionScreen(
    onGranted: () -> Unit,
    onSafFallback: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val allFilesGranted = Permissions.allFilesGranted(context)
    val mediaGranted = Permissions.mediaGranted(context)

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Only advance when every requested permission was granted;
        // otherwise the gate recomposes and the user can retry.
        if (result.values.all { it }) onGranted()
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onSafFallback(uri)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ApexTopBar(title = "APEX", subtitle = "Acceso al almacenamiento")
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = Icons.Outlined.Lock,
                title = "Acceso total a archivos",
                description = "Necesario para explorar, limpiar y buscar en todo el almacenamiento",
                granted = allFilesGranted,
                actionLabel = "Conceder",
                onAction = {
                    context.startActivity(Permissions.allFilesSettingsIntent(context))
                },
            )
            PermissionCard(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Permisos de medios",
                description = "Para las categorías Imágenes, Vídeos y Audio",
                granted = mediaGranted,
                actionLabel = "Conceder",
                onAction = { mediaLauncher.launch(Permissions.mediaPermissions()) },
            )
            PermissionCard(
                icon = Icons.Outlined.Usb,
                title = "Explorar con SAF",
                description = "Alternativa sin acceso total: elige una carpeta (SD o USB-OTG)",
                granted = false,
                actionLabel = "Elegir carpeta",
                onAction = { treeLauncher.launch(null) },
            )
            if (allFilesGranted && mediaGranted) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onGranted) {
                        Text("Iniciar APEX", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                "Cero dependencias · Cero red · 100% local",
                style = MaterialTheme.typography.labelSmall,
                color = ApexTextMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    ApexCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (granted) {
                Icon(
                    Icons.Outlined.Check,
                    "Concedido",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}