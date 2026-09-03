package com.apex.files.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.Screen
import com.apex.files.core.Accent
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall
import com.apex.files.ui.theme.accentColor

@Composable
fun SettingsScreen() {
    val navigator = LocalNavigator.current
    val vm: SettingsViewModel = apexViewModel(key = "settings") { c -> SettingsViewModel(c) }
    val accent by vm.accent.collectAsStateWithLifecycle()
    val showHidden by vm.showHidden.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ApexTopBar(title = "Ajustes", onBack = { navigator.pop() })

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Hidden files
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Mostrar archivos ocultos", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Archivos que empiezan por «.» y carpetas con .nomedia",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showHidden,
                        onCheckedChange = vm::setShowHidden,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = ApexBorder,
                            uncheckedBorderColor = ApexBorder,
                        ),
                    )
                }
            }

            // Neon accent
            ApexCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Acento neón", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Accent.entries.forEach { candidate ->
                        AccentSwatch(
                            color = accentColor(candidate),
                            selected = accent == candidate,
                            label = candidate.name,
                            onClick = { vm.setAccent(candidate) },
                        )
                    }
                }
            }

            // Benchmark
            ApexCard(
                Modifier.fillMaxWidth(),
                onClick = { navigator.push(Screen.Benchmark) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Speed, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Benchmark de almacenamiento", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Mide la velocidad de lectura y escritura reales",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // About
            ApexCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Acerca de APEX", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Versión 1.0", style = MonoTextStyleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cero dependencias · Cero red · 100% local",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Algoritmos clásicos (recorrido recursivo, SHA-256 diferido, regex). Sin servicios en segundo plano, sin WorkManager, sin Internet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ApexTextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(color, CircleShape)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = Color.White,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}