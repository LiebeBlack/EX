package com.apex.files.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.data.fs.DateFormatter
import com.apex.files.data.fs.SearchFilters
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalContainer
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.NodeOpener
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.FileIcon
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainer
import com.apex.files.ui.theme.ApexShapes
import com.apex.files.ui.theme.MonoTextStyleSmall
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
fun SearchScreen() {
    val navigator = LocalNavigator.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val vm: SearchViewModel = apexViewModel(key = "search") { c -> SearchViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = "Buscar",
            onBack = { navigator.pop() },
            actions = {
                ApexIconButton(Icons.Outlined.Refresh, "Reindexar") { vm.refreshIndex() }
            },
        )

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            SearchField(state, vm)
            Spacer(Modifier.height(8.dp))
            FilterRow(state, vm)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.searching) {
                item {
                    Text(
                        "Buscando…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    "${state.results.size} resultados · índice ${state.indexed} archivos",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            if (state.results.isEmpty() && !state.searching) {
                item {
                    EmptyState(Icons.Outlined.FolderOpen, "Sin resultados")
                }
            } else {
                items(state.results, key = { it.path }) { node ->
                    SearchResultRow(node) {
                        NodeOpener.open(node, container, navigator, context, imageContext = state.results) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(state: SearchViewModel.UiState, vm: SearchViewModel) {
    // Tapping anywhere in the pill (icon, padding, placeholder) focuses the
    // field and raises the keyboard — not just the narrow text strip.
    val focusRequester = remember { FocusRequester() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ApexShapes.small)
            .background(ApexContainer)
            .border(1.dp, ApexBorder, ApexShapes.small)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        BasicTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                if (state.query.isEmpty()) {
                    Text(
                        "Buscar archivos…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        if (state.query.isNotEmpty()) {
            Spacer(Modifier.size(4.dp))
            Icon(
                Icons.Outlined.Close,
                "Borrar búsqueda",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { vm.setQuery("") },
            )
        }
    }
}

@Composable
private fun FilterRow(state: SearchViewModel.UiState, vm: SearchViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchFilters.SizeBand.entries.forEach { band ->
                FilterChip(
                    when (band) {
                        SearchFilters.SizeBand.SMALL -> "<1 MB"
                        SearchFilters.SizeBand.MEDIUM -> "1–100 MB"
                        SearchFilters.SizeBand.GIANT -> ">1 GB"
                    },
                    active = state.sizeBand == band,
                    onClick = { vm.toggleSize(band) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchFilters.DateRange.entries.forEach { range ->
                FilterChip(
                    when (range) {
                        SearchFilters.DateRange.TODAY -> "Hoy"
                        SearchFilters.DateRange.WEEK -> "Semana"
                        SearchFilters.DateRange.MONTH -> "Mes"
                        SearchFilters.DateRange.YEAR -> "Año"
                    },
                    active = state.dateRange == range,
                    onClick = { vm.toggleDate(range) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ext:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(6.dp))
            val extFocus = remember { FocusRequester() }
            BasicTextField(
                value = state.extFilter,
                onValueChange = vm::setExtFilter,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Row(Modifier.fillMaxWidth()) {
                        if (state.extFilter.isEmpty()) {
                            Text(
                                "*.apk, *.pdf",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ApexContainer)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { extFocus.requestFocus() }
                    .focusRequester(extFocus)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(ApexShapes.small)
            .background(if (active) MaterialTheme.colorScheme.primary else ApexContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SearchResultRow(node: FileNode, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ApexShapes.small)
            .background(ApexContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.category == Category.IMAGE) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(node.uri ?: File(node.path))
                    .size(96)
                    .build(),
                contentDescription = node.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)),
            )
        } else {
            FileIcon(node.category, node.isDir, Modifier.size(32.dp), size = 20.dp)
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                "${SizeFormatter.format(node.size)} · ${DateFormatter.format(node.lastModified)}",
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                node.path.substringBeforeLast('/').ifBlank { "/" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}