package com.apex.files.ui.screens.logcat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun LogcatScreen() {
    val navigator = LocalNavigator.current
    val vm: LogcatViewModel = apexViewModel(key = "logcat") { LogcatViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = "Consola de sistema",
            onBack = { navigator.pop() },
            subtitle = if (state.error != null) "Permiso limitado" else "logcat · ${state.lines.size} líneas",
            actions = {
                ApexIconButton(Icons.Outlined.Refresh, "Actualizar registro") { vm.capture() }
            },
        )

        // ---- level chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogcatViewModel.Level.entries.forEach { level ->
                val selected = state.minLevel == level
                TextButton(onClick = { vm.setLevel(level) }) {
                    Text(
                        level.ch.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "≥",
                style = MonoTextStyleSmall,
                color = ApexTextMuted,
            )
        }

        // ---- query bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                textStyle = MonoTextStyleSmall.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.setQuery(state.query.trim()) }),
                modifier = Modifier
                    .weight(1f)
                    .background(ApexContainerHigh, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                decorationBox = { inner ->
                    if (state.query.isEmpty()) {
                        Text(
                            "Filtrar por texto…",
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            TextButton(onClick = { vm.clear() }) {
                Text("Vaciar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            state.loading -> NeonProgressBar(progress = null, modifier = Modifier.padding(40.dp))
            state.error != null -> {
                ApexCard(Modifier.fillMaxWidth().padding(16.dp)) {
                    Icon(
                        Icons.Outlined.BugReport,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error ?: "Error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Concede el permiso una vez con: adb shell pm grant com.apex.files android.permission.READ_LOGS",
                        style = MonoTextStyleSmall,
                        color = ApexTextMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { vm.capture() }) {
                        Text("Reintentar", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            state.lines.isEmpty() -> EmptyState(Icons.Outlined.BugReport, "Sin registros")
            else -> {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(state.lines) { line ->
                        LogRow(line)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildString {
                            append("${state.lines.size} líneas")
                            if (state.truncated) append(" (recortado)")
                        },
                        style = MonoTextStyleSmall,
                        color = ApexTextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.capture() }) {
                        Text("Actualizar", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(line: LogcatViewModel.LogLine) {
    val levelColor = when (line.level) {
        'V' -> Color(0xFF90A4AE)
        'D' -> Color(0xFF4FC3F7)
        'I' -> Color(0xFF66BB6A)
        'W' -> Color(0xFFFFB74D)
        'E' -> Color(0xFFEF5350)
        'F' -> Color(0xFFEC407A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp),
    ) {
        Text(
            line.level.toString(),
            style = MonoTextStyleSmall.copy(color = levelColor),
            modifier = Modifier.width(12.dp),
        )
        Spacer(Modifier.width(6.dp))
        if (line.tag.isNotEmpty()) {
            Text(
                line.tag,
                style = MonoTextStyleSmall.copy(color = MaterialTheme.colorScheme.secondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            line.text.ifEmpty { " " },
            style = MonoTextStyleSmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)),
            modifier = Modifier.weight(1f),
        )
    }
}
