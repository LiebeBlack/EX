package com.apex.files.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexMono
import com.apex.files.ui.theme.MonoTextStyleSmall
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.ContentCopy

/** Built-in hexadecimal viewer for any file. Bounded memory windows. */
@Composable
fun HexViewerScreen(node: FileNode) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val key = remember { "hex-${node.path}" }
    val vm: HexViewerViewModel = apexViewModel(key = key) { c -> HexViewerViewModel(c, node) }
    val state by vm.state.collectAsStateWithLifecycle()

    val lineStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = ApexMono,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
    )

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = node.name,
            onBack = { navigator.pop() },
            subtitle = "Hexadecimal · ${SizeFormatter.format(state.totalBytes)}" +
                if (state.loadedBytes > 0) " · ${state.loadedBytes} leídos" else "",
            actions = {
                ApexIconButton(
                    Icons.Outlined.ContentCopy,
                    "Copiar vista",
                    tint = MaterialTheme.colorScheme.onBackground,
                ) {
                    val text = state.lines.joinToString("\n")
                    if (text.isNotEmpty()) {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, "Vista copiada", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )

        when {
            state.loading && state.lines.isEmpty() -> {
                NeonProgressBar(progress = null, modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp))
            }
            state.error != null -> EmptyState(Icons.Outlined.ReportProblem, state.error ?: "Error")
            state.lines.isEmpty() -> EmptyState(Icons.Outlined.ReportProblem, "Archivo vacío")
            else -> {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Text(
                                "Desplazamiento   Hex                                                                                   ASCII",
                                style = MonoTextStyleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.lines) { line ->
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Text(
                                line,
                                style = lineStyle,
                                modifier = Modifier.widthIn(min = 0.dp),
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(ApexContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Mostrados ${SizeFormatter.format(state.loadedBytes)} de ${SizeFormatter.format(state.totalBytes)}",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.truncated) {
                        Text(
                            "Límite de memoria alcanzado",
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TextButton(onClick = { vm.loadMore() }, enabled = !state.loading) {
                            Text(
                                if (state.loading) "Cargando…" else "Cargar más",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}