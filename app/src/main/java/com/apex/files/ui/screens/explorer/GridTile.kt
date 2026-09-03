package com.apex.files.ui.screens.explorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.ui.components.FileIcon
import com.apex.files.ui.theme.ApexShapes
import com.apex.files.ui.theme.ApexSurface1
import com.apex.files.ui.theme.MonoTextStyleSmall
import java.io.File

/** Square tile for the LazyVerticalGrid view. */
@Composable
fun GridTile(
    node: FileNode,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ApexShapes.medium
    val container = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier
            .clip(shape)
            .background(container)
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
                shape,
            )
            .clickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(ApexSurface1),
            contentAlignment = Alignment.Center,
        ) {
            if (node.category == Category.IMAGE) {
                val context = LocalContext.current
                val model = if (node.uri != null) node.uri else File(node.path)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .size(160)
                        .build(),
                    contentDescription = node.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                FileIcon(node.category, node.isDir, Modifier.size(44.dp), size = 30.dp)
            }
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    "Seleccionado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(4.dp).size(18.dp).align(Alignment.TopEnd),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            node.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (node.isDir) "Carpeta" else SizeFormatter.format(node.size),
            style = MonoTextStyleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}