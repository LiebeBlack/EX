package com.apex.files.ui.screens.viewer

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexMono
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun TextViewerScreen(node: FileNode) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val key = remember { "text-${node.path}" }
    val vm: TextViewerViewModel = apexViewModel(key = key) { c -> TextViewerViewModel(c, node) }
    val state by vm.state.collectAsStateWithLifecycle()

    // One-shot feedback (guardado / errores) surfaced as a toast.
    LaunchedEffect(state.notice) {
        state.notice?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.consumeNotice()
        }
    }

    val lineStyle = TextStyle(
        fontFamily = ApexMono,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
    )

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = node.name,
            onBack = { navigator.pop() },
            subtitle = when {
                state.editing -> "Editando…"
                state.totalLines != null -> "${state.encoding} · ${state.totalLines} líneas"
                else -> state.encoding
            },
            actions = {
                if (state.editing) {
                    TextButton(onClick = { vm.saveEditing() }, enabled = !state.saving) {
                        Text(
                            "Guardar",
                            color = if (state.saving) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    TextButton(onClick = { vm.cancelEditing() }, enabled = !state.saving) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (state.editable) {
                    ApexIconButton(Icons.Outlined.Edit, "Editar") { vm.startEditing() }
                }
            },
        )

        when {
            state.editing -> {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    BasicTextField(
                        value = state.draft,
                        onValueChange = vm::updateDraft,
                        textStyle = lineStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            state.loading -> NeonProgressBar(progress = null, modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp))
            state.error != null -> EmptyState(Icons.Outlined.ReportProblem, state.error ?: "Error")
            else -> {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(state.lines) { index, line ->
                        Text(
                            line.ifEmpty { " " },
                            style = lineStyle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val end = state.baseLine + state.lines.size
                    Text(
                        "Mostrando ${state.baseLine + 1}–$end de ${state.totalLines ?: "?"}",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.backToStart() }, enabled = state.baseLine > 0) {
                        Text(
                            "Inicio",
                            color = if (state.baseLine > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (state.truncated) {
                        TextButton(onClick = { vm.loadMore() }) {
                            Text("Cargar más", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}