package com.apex.files.ui.screens.explorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apex.files.core.HashAlgorithm
import com.apex.files.data.fs.DateFormatter
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.ui.components.FileIcon
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainer
import com.apex.files.ui.theme.ApexShapes
import com.apex.files.ui.theme.MonoTextStyleSmall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesSheet(
    state: ExplorerViewModel.PropertiesState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onComputeHash: (HashAlgorithm) -> Unit,
    onCopyText: (String) -> Unit,
    onOpenWith: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ApexContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val node = state.node
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIcon(node.category, node.isDir, Modifier.size(40.dp), size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(node.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.mime, style = MonoTextStyleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = ApexBorder, thickness = 1.dp)

            PropertyRow("Ruta", node.path)
            PropertyRow("Tamaño", if (state.computingSize) "Calculando…" else SizeFormatter.format(state.size ?: 0L))
            if (state.count != null && node.isDir) {
                PropertyRow("Contenido", "${state.count.files} archivos · ${state.count.dirs} carpetas")
            }
            PropertyRow("Modificado", DateFormatter.format(node.lastModified))

            HorizontalDivider(color = ApexBorder, thickness = 1.dp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Permisos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                PermissionBadge("Lectura", state.canRead)
                Spacer(Modifier.width(6.dp))
                PermissionBadge("Escritura", state.canWrite)
                Spacer(Modifier.width(6.dp))
                PermissionBadge("Ejecución", state.canExecute)
            }

            HorizontalDivider(color = ApexBorder, thickness = 1.dp)

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ApexShapes.small)
                    .background(ApexContainer)
                    .clickable(onClick = onToggleFavorite)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    "Favorito",
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isFavorite) "Quitar de Favoritos" else "Añadir a Favoritos",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            HorizontalDivider(color = ApexBorder, thickness = 1.dp)

            if (!node.isDir && onOpenWith != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(ApexShapes.small)
                        .background(ApexContainer)
                        .clickable(onClick = onOpenWith)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        "Abrir con",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Abrir con otra aplicación",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                HorizontalDivider(color = ApexBorder, thickness = 1.dp)
            }

            if (!node.isDir) {
                HashRow(
                    label = "SHA-256",
                    value = state.sha256,
                    computing = state.computingHash,
                    onCompute = { onComputeHash(HashAlgorithm.SHA256) },
                    onCopy = { state.sha256?.let(onCopyText) },
                )
                HashRow(
                    label = "MD5",
                    value = state.md5,
                    computing = state.computingHash,
                    onCompute = { onComputeHash(HashAlgorithm.MD5) },
                    onCopy = { state.md5?.let(onCopyText) },
                )
                Text(
                    "El hash se calcula bajo demanda sobre el archivo completo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Hash disponible solo para archivos individuales.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MonoTextStyleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PermissionBadge(label: String, granted: Boolean) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun HashRow(
    label: String,
    value: String?,
    computing: Boolean,
    onCompute: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Fingerprint,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp).height(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        when {
            value != null -> {
                Text(
                    value,
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Copiar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp).height(16.dp))
                }
            }
            computing -> {
                Text("Calculando…", style = MonoTextStyleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            else -> {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCompute) {
                    Text("Calcular", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}