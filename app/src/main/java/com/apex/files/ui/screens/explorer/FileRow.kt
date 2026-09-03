package com.apex.files.ui.screens.explorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.apex.files.data.fs.DateFormatter
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.ui.components.FileIcon
import com.apex.files.ui.theme.ApexShapes
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall
import java.io.File

/** Detailed list row: thumbnail, 1-line name, mono size · date. */
@Composable
fun FileRow(
    node: FileNode,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ApexShapes.small
    val container = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    val border = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, Color.Transparent)
    }
    Row(
        modifier
            .clip(shape)
            .background(container)
            .border(border, shape)
            .clickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.category == Category.IMAGE) {
            val context = LocalContext.current
            val model = if (node.uri != null) node.uri else File(node.path)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .size(96)
                    .build(),
                contentDescription = node.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
            )
        } else {
            FileIcon(
                category = node.category,
                isDir = node.isDir,
                modifier = Modifier.size(42.dp),
                size = 26.dp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (node.isDir) {
                    "Carpeta · ${DateFormatter.format(node.lastModified)}"
                } else {
                    "${SizeFormatter.format(node.size)} · ${DateFormatter.format(node.lastModified)}"
                },
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                "Seleccionado",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else if (node.isDir) {
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                null,
                tint = ApexTextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}