package com.apex.files.ui.screens.space

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.Screen
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.data.model.Location
import com.apex.files.tools.SpaceAnalyzer
import com.apex.files.tools.TreemapLayout
import com.apex.files.tools.TreemapRect
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun SpaceAnalyzerScreen(location: Location) {
    val navigator = LocalNavigator.current
    val key = remember { "space-${location.key()}-${(navigator.current as? Screen.SpaceAnalyzer)?.serial ?: 0}" }
    val vm: SpaceAnalyzerViewModel = apexViewModel(key = key) { c -> SpaceAnalyzerViewModel(c, location) }
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler(enabled = vm.canGoUp()) { vm.up() }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = "Analizador de espacio",
            onBack = {
                if (vm.canGoUp()) vm.up() else navigator.pop()
            },
            subtitle = location.label,
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.toRoot() }) {
                Text("Raíz", color = if (state.breadcrumb.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.breadcrumb.forEach { node ->
                Icon(Icons.Outlined.ArrowUpward, null, tint = ApexTextMuted, modifier = Modifier.size(10.dp))
                Text(
                    node.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 90.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Toca un bloque para entrar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp),
            )
        }

        when {
            state.scanning -> {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    NeonProgressBar(progress = null)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Analizando…",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.currentPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            state.error != null -> {
                Text(
                    state.error ?: "Error",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            else -> {
                val current = state.current
                if (current != null && current.children.isNotEmpty()) {
                    TreemapCanvas(
                        node = current,
                        onDrill = { vm.drill(it) },
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(10.dp),
                    )
                } else {
                    Text(
                        "Sin datos suficientes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TreemapCanvas(
    node: SpaceAnalyzer.SpaceNode,
    onDrill: (SpaceAnalyzer.SpaceNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasWidth by remember { mutableStateOf(0f) }
    var canvasHeight by remember { mutableStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()

    val rects: List<TreemapRect> = remember(node, canvasWidth, canvasHeight) {
        if (canvasWidth <= 0f) emptyList()
        else TreemapLayout.layout(node.children, canvasWidth, canvasHeight)
    }
    val accent = MaterialTheme.colorScheme.primary
    val onBackground = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                canvasWidth = size.width.toFloat()
                canvasHeight = size.height.toFloat()
            }
            .pointerInput(rects) {
                detectTapGestures { pos ->
                    rects.firstOrNull { it.contains(pos.x, pos.y) }?.let { onDrill(it.node) }
                }
            },
    ) {
        for (rect in rects) {
            drawRoundRect(
                color = colorFor(rect.node, accent),
                topLeft = androidx.compose.ui.geometry.Offset(rect.x, rect.y),
                size = Size(rect.w, rect.h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
            drawRoundRect(
                color = ApexBorder,
                topLeft = androidx.compose.ui.geometry.Offset(rect.x, rect.y),
                size = Size(rect.w, rect.h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                style = Stroke(width = 1f),
            )
            if (rect.w > 46f && rect.h > 22f) {
                val layoutResult = textMeasurer.measure(
                    rect.node.name.take(12),
                    style = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 9.sp,
                    ),
                )
                drawText(
                    textLayoutResult = layoutResult,
                    topLeft = androidx.compose.ui.geometry.Offset(rect.x + 6f, rect.y + 5f),
                )
            }
        }
        // Total size label at top-left corner
        val label = textMeasurer.measure(
            SizeFormatter.format(node.size),
            style = androidx.compose.ui.text.TextStyle(
                color = onBackground,
                fontSize = 10.sp,
            ),
        )
        drawText(textLayoutResult = label, topLeft = androidx.compose.ui.geometry.Offset(8f, 8f))
    }
}

private fun colorFor(node: SpaceAnalyzer.SpaceNode, accent: Color): Color {
    if (!node.isFile) return accent.copy(alpha = 0.30f)
    return when (node.category) {
        Category.IMAGE -> Color(0xFF00E5FF).copy(alpha = 0.85f)
        Category.VIDEO -> Color(0xFF7C4DFF).copy(alpha = 0.85f)
        Category.AUDIO -> Color(0xFF00E676).copy(alpha = 0.85f)
        Category.DOCUMENT -> Color(0xFFFFAB00).copy(alpha = 0.85f)
        Category.ARCHIVE -> Color(0xFFFF2A6D).copy(alpha = 0.85f)
        Category.APK -> Color(0xFF00E5FF).copy(alpha = 0.85f)
        else -> Color(0xFF8E8E93).copy(alpha = 0.7f)
    }
}