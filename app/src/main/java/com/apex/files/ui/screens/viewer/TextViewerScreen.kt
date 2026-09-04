package com.apex.files.ui.screens.viewer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WrapText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexMono
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun TextViewerScreen(node: FileNode) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val key = remember { "text-${node.path}" }
    val vm: TextViewerViewModel = apexViewModel(key = key) { c -> TextViewerViewModel(c, node) }
    val state by vm.state.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
                if (!state.editing && state.totalLines != null) {
                    ApexIconButton(
                        Icons.Outlined.WrapText,
                        if (state.wrap) "Desactivar ajuste de línea" else "Ajustar línea",
                        tint = if (state.wrap) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    ) { vm.toggleWrap() }
                }
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
                } else {
                    if (state.totalLines != null) {
                        ApexIconButton(Icons.Outlined.Search, "Buscar en el archivo") {
                            showSearch = !showSearch
                            if (!showSearch) vm.closeSearch()
                        }
                    }
                    if (state.editable) {
                        ApexIconButton(Icons.Outlined.Edit, "Editar") { vm.startEditing() }
                    }
                    if (state.structured && state.editable) {
                        TextButton(
                            onClick = { vm.formatStructured() },
                            enabled = !state.formatting,
                        ) {
                            Text(
                                if (state.formatting) "Formateando…" else "Formatear",
                                color = if (state.formatting) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }
            },
        )

        if (showSearch && !state.editing && state.totalLines != null) {
            SearchBarRow(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                matchLabel = when {
                    state.searching -> "Buscando…"
                    state.matchIndex >= 0 -> "${state.matchIndex + 1}/${state.matches.size}" + if (state.matchesTruncated) "+" else ""
                    else -> ""
                },
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        vm.startSearch(searchQuery)
                    }
                },
                onPrev = { vm.prevMatch() },
                onNext = { vm.nextMatch() },
                onClose = {
                    showSearch = false
                    searchQuery = ""
                    vm.closeSearch()
                },
                enabled = !state.searching,
            )
        }

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
                // Lines of the visible window that contain the search term.
                val highlighted = remember(state.matches, state.baseLine, state.lines.size) {
                    val from = state.baseLine
                    val to = from + state.lines.size
                    state.matches.filter { it in from until to }.toSet()
                }
                if (state.wrap) {
                    WrappedLines(
                        lines = state.lines,
                        baseLine = state.baseLine,
                        highlighted = highlighted,
                        lineStyle = lineStyle,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        itemsIndexed(state.lines) { index, line ->
                            val absolute = state.baseLine + index
                            LineRow(
                                number = absolute + 1,
                                content = line.ifEmpty { " " },
                                lineStyle = lineStyle,
                                color = if (absolute in highlighted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
                                },
                            )
                        }
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
                    TextButton(onClick = { vm.windowUp() }, enabled = state.baseLine > 0) {
                        Text(
                            "Subir",
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

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    matchLabel: String,
    enabled: Boolean,
    onSearch: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MonoTextStyleSmall.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .background(ApexContainerHigh, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Buscar en el archivo…",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            matchLabel,
            style = MonoTextStyleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ApexIconButton(
            Icons.Outlined.KeyboardArrowUp,
            "Coincidencia anterior",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onPrev,
        )
        ApexIconButton(
            Icons.Outlined.KeyboardArrowDown,
            "Coincidencia siguiente",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onNext,
        )
        ApexIconButton(
            Icons.Outlined.Close,
            "Cerrar búsqueda",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onClose,
        )
    }
    Spacer(Modifier.height(2.dp))
    androidx.compose.material3.HorizontalDivider(color = ApexBorder, thickness = 1.dp)
}

/** Gutter number (right-aligned, muted) + the line content. */
@Composable
private fun LineRow(
    number: Int,
    content: String,
    lineStyle: TextStyle,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            number.toString(),
            style = MonoTextStyleSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(52.dp).padding(end = 10.dp),
        )
        Text(
            content,
            style = lineStyle,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Wrap mode: one scroll container holding number + soft-wrapped content rows. */
@Composable
private fun WrappedLines(
    lines: List<String>,
    baseLine: Int,
    highlighted: Set<Int>,
    lineStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
        lines.forEachIndexed { index, line ->
            val absolute = baseLine + index
            LineRow(
                number = absolute + 1,
                content = line.ifEmpty { " " },
                lineStyle = lineStyle.copy(softWrap = true),
                color = if (absolute in highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
                },
            )
        }
    }
}
