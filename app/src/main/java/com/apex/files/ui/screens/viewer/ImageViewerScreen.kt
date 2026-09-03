package com.apex.files.ui.screens.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.theme.MonoTextStyleSmall
import java.io.File

/**
 * Fullscreen image gallery: swipe left/right to move between the images of
 * the folder/search/category the file was opened from. Pure black
 * background, pinch/pan/rotation via pointerInput while zoomed, double-tap
 * to zoom in / reset. No third-party viewer libraries.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(nodes: List<FileNode>, startIndex: Int) {
    if (nodes.isEmpty()) return
    val navigator = LocalNavigator.current

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, nodes.lastIndex),
    ) { nodes.size }
    val page = pagerState.currentPage

    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var failed by remember { mutableStateOf(false) }

    // Reset the transform when the page settles on another image.
    LaunchedEffect(page) {
        scale = 1f
        rotation = 0f
        offset = Offset.Zero
        failed = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            // While zoomed the transform gesture owns the pointer stream, so
            // horizontal swipes are locked to avoid page turns mid-pan.
            userScrollEnabled = scale <= 1f,
        ) { p ->
            val node = nodes[p]
            val isCurrent = p == page
            AsyncImage(
                model = if (node.uri != null) node.uri else File(node.path),
                contentDescription = node.name,
                contentScale = ContentScale.Fit,
                onError = { if (isCurrent) failed = true },
                onSuccess = { if (isCurrent) failed = false },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(p) {
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
                    .let { base ->
                        // Transform gestures only attach while zoomed, so the
                        // pager keeps single-finger horizontal drags at 1:1.
                        if (isCurrent && scale > 1f) {
                            base.pointerInput(p) {
                                detectTransformGestures { _, pan, zoom, rot ->
                                    scale = (scale * zoom).coerceIn(1f, 8f)
                                    rotation = ((rotation + rot) % 360f + 360f) % 360f
                                    offset += pan
                                }
                            }
                        } else {
                            base
                        }
                    },
            )
        }

        if (failed) {
            Text(
                "No se pudo mostrar la imagen",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        ApexIconButton(
            Icons.Outlined.Close,
            "Cerrar",
            onClick = { navigator.pop() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        )

        if (nodes.size > 1) {
            Text(
                "${page + 1} / ${nodes.size}",
                style = MonoTextStyleSmall,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 42.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }

        Text(
            nodes[page].name,
            style = MonoTextStyleSmall,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 40.dp, end = 40.dp, bottom = 18.dp),
        )
    }
}