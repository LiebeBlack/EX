package com.apex.files.ui.screens.sqlite

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.data.fs.Csv
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.fs.SqliteRepository
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun SqliteScreen(node: FileNode) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val key = remember { "sqlite-${node.path}" }
    val vm: SqliteViewModel = apexViewModel(key = key) { c -> SqliteViewModel(c, node) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            vm.consumeError()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = node.name,
            onBack = {
                if (state.showingTable) vm.backToTables() else navigator.pop()
            },
            subtitle = if (state.showingTable) state.table?.name else "Analizador SQLite",
        )

        when {
            state.loading -> NeonProgressBar(progress = null, modifier = Modifier.padding(40.dp))
            state.error != null && state.objects.isEmpty() && state.table == null -> {
                EmptyState(
                    icon = Icons.Outlined.ReportProblem,
                    message = state.error ?: "Error",
                )
            }
            state.showingTable -> state.table?.let { table ->
                TableDetail(
                    info = table,
                    query = state.query,
                    queryResult = state.queryResult,
                    querying = state.querying,
                    onBack = vm::backToTables,
                    onQueryChange = vm::onQueryChange,
                    onRunQuery = vm::runQuery,
                    onClearQuery = vm::clearQuery,
                    onShareCsv = { result ->
                        scope.launch {
                            val csv = withContext(Dispatchers.IO) {
                                // Hard budget so huge result sets never blow
                                // the Intent size limit.
                                val rows = ArrayList<List<String?>>()
                                var left = 350_000
                                for (row in result.rows) {
                                    val estimate = row.sumOf { (it?.length ?: 0) + 2 }
                                    if (left - estimate < 0 && rows.isNotEmpty()) break
                                    rows.add(row)
                                    left -= estimate
                                }
                                Csv.toCsv(result.columns, rows)
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, node.name)
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "Compartir CSV"))
                            }.onFailure {
                                Toast.makeText(context, "No hay aplicación para compartir CSV", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
            state.objects.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.DataUsage,
                    message = "Sin tablas ni vistas",
                )
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "${state.objects.size} objetos · ${SizeFormatter.format(node.size)} · solo lectura",
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.objects.forEach { (name, kind) ->
                                ApexCard(
                                    Modifier.fillMaxWidth(),
                                    onClick = { vm.openTable(name, kind) },
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                if (kind == "view") "VISTA" else "TABLA",
                                                style = MonoTextStyleSmall,
                                                color = ApexTextMuted,
                                            )
                                        }
                                        Text(
                                            "Abrir",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableDetail(
    info: SqliteRepository.TableInfo,
    query: String,
    queryResult: SqliteRepository.QueryResult?,
    querying: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRunQuery: () -> Unit,
    onClearQuery: () -> Unit,
    onShareCsv: (SqliteRepository.QueryResult) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val active = queryResult ?: info.preview
        // --- summary + schema
        item {
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Esquema · ${info.columns.size} columnas" +
                            if (info.rowCount != null) " · ${info.rowCount} filas" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(8.dp))
                info.columns.forEach { col ->
                    Text(
                        buildString {
                            append(if (col.pk > 0) "🔑 " else "   ")
                            append(col.name)
                            if (col.type.isNotBlank()) append("  ${col.type}")
                            if (col.notNull) append("  NOT NULL")
                        },
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }

        // --- query console
        item {
            ApexCard(Modifier.fillMaxWidth()) {
                Text(
                    "Consulta SQL (solo lectura)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MonoTextStyleSmall.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onRunQuery() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ApexContainerHigh, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "SELECT …",
                                style = MonoTextStyleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRunQuery, enabled = !querying) {
                        Text("Ejecutar", color = MaterialTheme.colorScheme.primary)
                    }
                    if (queryResult != null || query.isNotEmpty()) {
                        TextButton(onClick = onClearQuery) {
                            Text("Limpiar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (active != null && active.error == null && !active.isEmpty) {
                        TextButton(onClick = { onShareCsv(active) }) {
                            Text("CSV", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (querying) {
                        Text("Consultando…", style = MonoTextStyleSmall, color = ApexTextMuted)
                    }
                    TextButton(onClick = onBack) {
                        Text("← Tablas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // --- results / preview
        val result = queryResult ?: info.preview
        if (result != null) {
            if (result.error != null) {
                item {
                    Text(
                        result.error,
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (result.isEmpty) {
                item {
                    Text(
                        if (queryResult != null) "Sin resultados" else "Tabla vacía",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    Text(
                        buildString {
                            append(if (queryResult != null) "Resultado" else "Vista previa")
                            append(" · ${result.rows.size} filas")
                            if (result.limited) append(" (límite alcanzado)")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                item {
                    QueryGrid(result)
                }
            }
        }
    }
}

/** Read-only spreadsheet: header + up to N rows, cells 140.dp wide. */
@Composable
private fun QueryGrid(result: SqliteRepository.QueryResult) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .background(ApexContainerHigh, RoundedCornerShape(10.dp)),
    ) {
        Row {
            result.columns.forEachIndexed { index, name ->
                Cell(name, header = true)
            }
        }
        androidx.compose.material3.HorizontalDivider(color = ApexBorder, thickness = 1.dp)
        result.rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                androidx.compose.material3.HorizontalDivider(color = ApexBorder, thickness = 1.dp)
            }
            Row {
                row.forEach { value ->
                    Cell(value?.let { if (it.length > 400) it.take(400) + "…" else it }, header = false)
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String?, header: Boolean) {
    Text(
        text ?: if (header) "" else "NULL",
        style = if (header) {
            MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary)
        } else {
            MonoTextStyleSmall.copy(
                color = if (text == null) ApexTextMuted else MaterialTheme.colorScheme.onBackground,
            )
        },
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(140.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
