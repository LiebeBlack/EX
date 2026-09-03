package com.apex.files.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apex.files.data.model.Category

/** Monochrome linear icon per category, accent-tinted for media. */
@Composable
fun FileIcon(
    category: Category,
    isDir: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    val icon: ImageVector = when {
        isDir -> Icons.Outlined.Folder
        category == Category.IMAGE -> Icons.Outlined.Image
        category == Category.VIDEO -> Icons.Outlined.Movie
        category == Category.AUDIO -> Icons.Outlined.Audiotrack
        category == Category.DOCUMENT -> Icons.Outlined.Description
        category == Category.ARCHIVE -> Icons.Outlined.FolderZip
        category == Category.APK -> Icons.Outlined.Android
        else -> Icons.Outlined.InsertDriveFile
    }
    val effectiveTint = when (category) {
        Category.IMAGE, Category.VIDEO, Category.AUDIO, Category.APK ->
            MaterialTheme.colorScheme.primary
        else -> tint
    }
    Icon(icon, null, tint = effectiveTint, modifier = modifier.size(size))
}