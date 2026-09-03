package com.apex.files.ui.screens.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.MonoTextStyleSmall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PdfViewerScreen(node: FileNode) {
    val navigator = LocalNavigator.current
    val key = remember { "pdf-${node.path}" }
    val vm: PdfViewerViewModel = apexViewModel(key = key) { c -> PdfViewerViewModel(c, node) }
    val state by vm.state.collectAsStateWithLifecycle()

    val density = LocalDensity.current.density
    val listState = rememberLazyListState()
    val pageIndicator by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex + 1).coerceIn(1, state.pageCount.coerceAtLeast(1))
        }
    }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = node.name,
            onBack = { navigator.pop() },
            subtitle = if (state.pageCount > 0) {
                "página $pageIndicator / ${state.pageCount}"
            } else {
                "Visor PDF"
            },
        )

        when {
            state.loading -> NeonProgressBar(progress = null, modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp))
            state.error != null -> EmptyState(Icons.Outlined.PictureAsPdf, state.error ?: "Error")
            else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt().coerceAtLeast(320) }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.pageCount, key = { it }) { index ->
                        val bitmap by produceState<Bitmap?>(null, index, widthPx) {
                            value = withContext(Dispatchers.IO) {
                                vm.renderPage(index, widthPx, density)
                            }
                        }
                        bitmap?.let { bmp ->
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Página ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}