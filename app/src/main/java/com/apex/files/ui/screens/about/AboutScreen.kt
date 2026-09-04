package com.apex.files.ui.screens.about

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.files.BuildConfig
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.screens.permissions.Permissions
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall

/**
 * “Acerca de” screen: branding, version, the zero-network / zero-AI /
 * zero-background guarantees and current storage-permission state.
 */
@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val allFiles = Permissions.allFilesGranted(context)
    val media = Permissions.mediaGranted(context)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ApexTopBar(title = "Acerca de APEX", onBack = { navigator.pop() })
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "APEX",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "FILE MANAGER · OLED",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            ApexCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Versión", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "v${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE})",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Permiso de acceso total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (allFiles) "Concedido" else "No concedido",
                        style = MonoTextStyleSmall,
                        color = if (allFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Permisos de medios", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (media) "Concedidos" else "Parcial o no concedidos",
                        style = MonoTextStyleSmall,
                        color = if (media) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                "Garantías",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            GuaranteeRow(Icons.Outlined.OfflineBolt, "Cero red", "Sin permiso INTERNET: la app no puede hacer llamadas de red.")
            GuaranteeRow(Icons.Outlined.Android, "Cero IA", "Sin Firebase, ML Kit ni modelos locales: solo algoritmos clásicos.")
            GuaranteeRow(Icons.Outlined.Lock, "Cero segundo plano", "Sin services ni WorkManager: 0% batería en reposo.")
            GuaranteeRow(Icons.Outlined.Code, "100% local", "Kotlin + Compose. SHA-256, regex y visores nativos.")
            GuaranteeRow(Icons.Outlined.Storage, "Oled puro", "Tema oscuro OLED #000000 con acento neón personalizable.")

            ApexCard(Modifier.fillMaxWidth()) {
                Text(
                    "Cero dependencias de terceros · Cero red · 100% local",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexTextMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Documentación: docs/index.html incluida en el repositorio.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexTextMuted,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GuaranteeRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    ApexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
