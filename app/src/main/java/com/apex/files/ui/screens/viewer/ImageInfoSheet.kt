package com.apex.files.ui.screens.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.FileNode
import com.apex.files.tools.ExifReader
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainer
import com.apex.files.ui.theme.MonoTextStyleSmall
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Bottom sheet with file facts + EXIF metadata for the current image, parsed
 * locally (zero dependencies) on [Dispatchers.IO].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageInfoSheet(
    node: FileNode,
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    var exif by remember(node.path) { mutableStateOf<ExifReader.ExifData?>(null) }

    LaunchedEffect(node.path) {
        exif = withContext(Dispatchers.IO) {
            try {
                ExifReader.read(container.fs.fileForReading(node))
            } catch (e: Exception) {
                null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ApexContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Info,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(node.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            HorizontalDivider(color = ApexBorder, thickness = 1.dp)

            val data = exif
            if (data == null) {
                Text(
                    "Leyendo metadatos…",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                InfoRow("Tamaño", SizeFormatter.format(node.size))
                data.width?.let { w -> data.height?.let { h -> InfoRow("Dimensiones", "${w} × ${h} px") } }
                InfoRow("Tipo", if (data.hasExif) "JPEG con EXIF" else "JPEG sin metadatos EXIF")
                if (data.hasExif) {
                    HorizontalDivider(color = ApexBorder, thickness = 1.dp)
                    data.make?.let { InfoRow("Fabricante", it) }
                    data.model?.let { InfoRow("Modelo", it) }
                    data.software?.let { InfoRow("Software", it) }
                    data.dateTimeOriginal?.let { InfoRow("Tomada", it.replace('T', ' ')) }
                        ?: data.dateTime?.let { InfoRow("Fecha", it.replace('T', ' ')) }
                    data.exposureTime?.let { InfoRow("Exposición", formatExposure(it)) }
                    data.fNumber?.let { InfoRow("Apertura", "f/${trimNumber(it)}") }
                    data.iso?.let { InfoRow("ISO", it.toString()) }
                    data.focalLength?.let { InfoRow("Dist. focal", "${trimNumber(it)} mm") }
                    data.flash?.let { InfoRow("Flash", if (it == 0) "No disparado" else "Disparado") }
                    data.orientation?.let { InfoRow("Orientación", orientationLabel(it)) }
                    if (data.hasLocation) {
                        InfoRow(
                            "Ubicación",
                            String.format(
                                Locale.US,
                                "%.5f, %.5f",
                                data.latitude ?: 0.0,
                                data.longitude ?: 0.0,
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(
            value,
            style = MonoTextStyleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatExposure(seconds: Double): String =
    if (seconds >= 1.0) "${trimNumber(seconds)} s"
    else if (seconds > 0.0) "1/${kotlin.math.round(1.0 / seconds).toInt()}"
    else "—"

private fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

private fun orientationLabel(o: Int): String = when (o) {
    1 -> "Normal"
    3 -> "Rotada 180°"
    6 -> "Rotada 90° (dcha.)"
    8 -> "Rotada 90° (izq.)"
    else -> "$o"
}