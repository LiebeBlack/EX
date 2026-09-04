package com.apex.files.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apex.files.ui.theme.ApexBlack
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexBorderSubtle
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexDanger
import com.apex.files.ui.theme.ApexShapes
import com.apex.files.ui.theme.ApexSurface1

/** Floating bottom bar shown during multi-selection. */
@Composable
fun SelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onCompress: () -> Unit,
    onProperties: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp),
        shape = ApexShapes.medium,
        color = ApexContainerHigh,
        border = BorderStroke(1.dp, ApexBorder),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$count seleccionados",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ApexIconButton(Icons.Outlined.Close, "Limpiar", onClick = onClear, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SelectionAction(Icons.Outlined.DoneAll, "Todo", onSelectAll)
                SelectionAction(Icons.Outlined.ContentCopy, "Copiar", onCopy)
                SelectionAction(Icons.AutoMirrored.Outlined.DriveFileMove, "Mover", onMove)
                SelectionAction(Icons.Outlined.Edit, "Renombrar", onRename)
                SelectionAction(Icons.Outlined.FolderZip, "Comprimir", onCompress)
                SelectionAction(Icons.Outlined.Share, "Compartir", onShare)
                SelectionAction(Icons.Outlined.Info, "Propiedades", onProperties)
                SelectionAction(Icons.Outlined.Delete, "Eliminar", onDelete, danger = true)
            }
        }
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Column(
        Modifier
            .padding(horizontal = 4.dp)
            .background(ApexSurface1, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            label,
            tint = if (danger) ApexDanger else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (danger) ApexDanger else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}