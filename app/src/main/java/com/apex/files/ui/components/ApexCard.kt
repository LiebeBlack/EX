package com.apex.files.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.apex.files.ui.theme.ApexShapes

/**
 * The standard flat container: #0F0F14 fill, 1dp #1E1E28 border,
 * 14dp rounded corners, zero elevation (borders replace shadows).
 */
@Composable
fun ApexCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = ApexShapes.medium,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
    if (onClick != null) {
        Column(base.clickable(onClick = onClick).padding(contentPadding), content = content)
    } else {
        Column(base.padding(contentPadding), content = content)
    }
}