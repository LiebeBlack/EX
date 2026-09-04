package com.apex.files.ui.screens.rename

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.data.fs.BatchRenamer
import com.apex.files.data.model.FileNode
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexDanger
import com.apex.files.ui.theme.MonoTextStyleSmall

/**
 * Batch rename: find/replace, prefix/suffix and renumbering with a live
 * preview. Only the names that change are touched; collisions and invalid
 * names are reported instead of silently skipped.
 */
@Composable
fun BatchRenameScreen(nodes: List<FileNode>) {
    val navigator = LocalNavigator.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = remember { "batch-rename-" + nodes.firstOrNull()?.path.orEmpty() }
    val vm: BatchRenameViewModel = apexViewModel(key = key) { c -> BatchRenameViewModel(c, nodes) }
    val state by vm.state.collectAsStateWithLifecycle()

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = "Renombrar lote",
            onBack = { navigator.pop() },
            subtitle = "${nodes.size} elemento(s) · ${state.plan.changes} cambiarán",
            actions = {
                TextButton(
                    onClick = { vm.apply(toast) },
                    enabled = !state.applying && !state.done && state.plan.changes > 0,
                ) {
                    Text(
                        if (state.done) "Hecho" else if (state.applying) "Aplicando…" else "Aplicar",
                        color = if (state.applying || state.done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RuleField("Buscar", state.find, { vm.setFind(it) }, placeholder = "texto a reemplazar")
            RuleField("Reemplazar con", state.replace, { vm.setReplace(it) }, placeholder = "dejar vacío para eliminar")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuleField("Prefijo", state.prefix, { vm.setPrefix(it) }, Modifier.weight(1f))
                RuleField("Sufijo", state.suffix, { vm.setSuffix(it) }, Modifier.weight(1f))
            }

            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Renumerar", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Añade un contador antes de la extensión (archivo_01.txt)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.renumber,
                        onCheckedChange = vm::setRenumber,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = ApexBorder,
                            uncheckedBorderColor = ApexBorder,
                        ),
                    )
                }
                if (state.renumber) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Empieza en", state.start, { vm.setStart(it) }, Modifier.weight(1f))
                        NumberField("Cifras", state.digits, { vm.setDigits(it) }, Modifier.weight(1f))
                    }
                }
            }

            if (state.plan.errors.isNotEmpty()) {
                ApexCard(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.plan.errors.take(8).forEach { error ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Warning,
                                    null,
                                    tint = ApexDanger,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    error,
                                    style = MonoTextStyleSmall,
                                    color = ApexDanger,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "Vista previa",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 6.dp),
            )
            // Preview rows: only items that actually change, plus a hint row
            // when nothing changes yet.
            val changedItems = remember(state.plan) { state.plan.items.filter { it.changed } }
            if (changedItems.isEmpty()) {
                Text(
                    "Ajusta las reglas para ver el resultado…",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                changedItems.forEach { item ->
                    PreviewRow(item)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RuleField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { input -> input.toIntOrNull()?.let(onChange) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = ApexContainerHigh,
    unfocusedContainerColor = ApexContainerHigh,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = ApexBorder,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
)

@Composable
private fun PreviewRow(item: BatchRenamer.PlanItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ApexContainerHigh, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.from,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Outlined.ArrowForward,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp).size(14.dp),
        )
        Text(
            item.to,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}