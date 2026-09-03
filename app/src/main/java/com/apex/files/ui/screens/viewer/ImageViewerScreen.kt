package com.apex.files.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.theme.MonoTextStyleSmall
import java.io.File

/**
 * Ultra-light fullscreen image viewer: pure black background and native
 * pointerInput gestures (pinch zoom, pan, rotation, double-tap zoom).
 * No third-party viewer libraries; rendered straight from the Coil cache.
 */
@Composable
fun ImageViewerScreen(node: FileNode) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = if (node.uri != null) node.uri else File(node.path),
            contentDescription = node.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rot ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        rotation = (rotation + rot) % 360f
                        offset += pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                rotation = 0f
                                offset = Offset.Zero
                            } else {
                                scale = 3f
                            }
                        },
                    )
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                    translationX = offset.x
                    translationY = offset.y
                },
        )

        ApexIconButton(
            Icons.Outlined.Close,
            "Cerrar",
            { navigator.pop() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        )

        Text(
            node.name,
            style = MonoTextStyleSmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
        )
    }
}