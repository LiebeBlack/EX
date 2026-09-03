package com.apex.files.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apex.files.ui.theme.ApexBorder

/**
 * Neon progress bar. Pass a fraction for determinate progress or null for
 * an indeterminate marquee. Pure Canvas-free boxes (GPU-cheap).
 */
@Composable
fun NeonProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    if (progress == null) {
        val transition = rememberInfiniteTransition(label = "neon_indeterminate")
        val offset by transition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "neon_offset",
        )
        BoxWithConstraints(
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexBorder)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .offset(x = maxWidth * offset)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    } else {
        val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "neon_progress")
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(2.dp))
                .background(ApexBorder)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}