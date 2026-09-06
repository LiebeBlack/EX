package com.apex.files.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainerHigh

private data class TourStep(val title: String, val text: String)

private val STEPS = listOf(
    TourStep(
        "Bienvenido a APEX",
        "Un gestor de archivos local, rápido y sin anuncios. No usa Internet: todo el contenido se queda en tu dispositivo.",
    ),
    TourStep(
        "Explora tus archivos",
        "Abre una carpeta para navegar, usa el buscador para encontrar al instante y revisa las categorías (imágenes, vídeos, audio, documentos…).",
    ),
    TourStep(
        "Selección y operaciones",
        "Mantén pulsado un archivo para seleccionarlo: podrás copiar, mover, renombrar, comprimir o eliminar varios a la vez.",
    ),
    TourStep(
        "Herramientas",
        "Limpiador de carpetas vacías, duplicados, filtro APK, analizador de espacio, papelera y analizador Wi-Fi.",
    ),
)

/** First-run walkthrough shown over the Home screen. */
@Composable
fun WelcomeTour(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val last = STEPS.lastIndex
    Dialog(onDismissRequest = onFinish) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ApexContainerHigh,
            border = BorderStroke(1.dp, ApexBorder),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    "APEX · PRIMERA VEZ",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    STEPS[step].title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    STEPS[step].text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        STEPS.forEachIndexed { i, _ ->
                            Box(
                                Modifier
                                    .size(if (i == step) 8.dp else 6.dp)
                                    .background(
                                        if (i == step) MaterialTheme.colorScheme.primary else ApexBorder,
                                        CircleShape,
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onFinish) {
                        Text("Saltar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { if (step == last) onFinish() else step++ }) {
                        Text(
                            if (step == last) "Entendido" else "Siguiente",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}