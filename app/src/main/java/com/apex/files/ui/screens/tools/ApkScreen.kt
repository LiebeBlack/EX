package com.apex.files.ui.screens.tools

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.core.OpType
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.tools.ApkManifestDecoder
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.LocalOperationCenter
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.ConfirmDialog
import com.apex.files.ui.components.EmptyState
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.components.RootPickerRow
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexDanger
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyleSmall

@Composable
fun ApkScreen() {
    val navigator = LocalNavigator.current
    val center = LocalOperationCenter.current
    val context = LocalContext.current
    val vm: ApkViewModel = apexViewModel(key = "apk") { c -> ApkViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(title = "Filtro APK", onBack = { navigator.pop() })

        RootPickerRow(
            currentName = state.rootName,
            currentKey = state.rootKey,
            volumes = state.volumes,
            enabled = !state.scanning,
            onPick = { vm.setRoot(it.key) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        when {
            state.scanning -> {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    NeonProgressBar(progress = null)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Escaneando… · ${state.apks.size} APKs",
                        style = MonoTextStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.currentPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            !state.done -> {
                ApexCard(Modifier.fillMaxWidth().padding(20.dp)) {
                    Icon(Icons.Outlined.Android, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Localiza instaladores .apk y marca cuáles corresponden a apps ya instaladas (redundantes).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { vm.scan() }) {
                        Text("Escanear", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            state.apks.isEmpty() -> {
                EmptyState(Icons.Outlined.Android, "Sin APKs")
                TextButton(onClick = { vm.reset() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Volver a escanear", color = MaterialTheme.colorScheme.primary)
                }
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${state.apks.size} instaladores · ${state.notInstalled.size} no instalados",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    TextButton(onClick = { vm.selectNotInstalled() }) {
                        Text("No instaladas", color = MaterialTheme.colorScheme.primary)
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.apks, key = { it.node.path }) { info ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggleSelect(info.node.path) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (info.node.path in state.selection) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                null,
                                tint = if (info.node.path in state.selection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                Icons.Outlined.Android,
                                null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    info.node.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${SizeFormatter.format(info.node.size)} · ${info.packageName ?: "?"}",
                                    style = MonoTextStyleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Badge(info)
                            ApexIconButton(
                                Icons.Outlined.Info,
                                "Análisis profundo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { vm.requestDetail(info.node.path) },
                            )
                        }
                    }
                }
                if (state.selection.isNotEmpty()) {
                    TextButton(
                        onClick = { showConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Eliminar seleccionados (${state.selection.size})", color = ApexDanger)
                    }
                }
            }
        }
    }

    val detail by vm.detail.collectAsStateWithLifecycle()
    detail?.takeIf { it.open }?.let { d ->
        ApkDetailDialog(detail = d, onDismiss = { vm.dismissDetail() })
    }

    if (showConfirm) {
        ConfirmDialog(
            title = "¿Eliminar?",
            message = "Se eliminarán ${state.selection.size} instalador(es) de forma permanente.",
            confirmLabel = "Eliminar",
            onConfirm = {
                showConfirm = false
                center.launch(OpType.DELETE, vm.deleteFlow()) { ok ->
                    val msg = if (ok) {
                        vm.consumeDeleteSummary() ?: "Operación completada"
                    } else {
                        center.lastError.value ?: "Operación cancelada"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    vm.scan()
                }
            },
            onDismiss = { showConfirm = false },
        )
    }
}

@Composable
private fun Badge(info: com.apex.files.tools.ApkScanner.ApkInfo) {
    val (label, color) = when (info.installed) {
        true -> "Instalada" to MaterialTheme.colorScheme.primary
        false -> "No instalada" to ApexDanger
        null -> if (info.container) "Contenedor" to MaterialTheme.colorScheme.secondary
        else "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(start = 8.dp),
    )
}

/** Dialog showing the native decoded manifest of one APK / container. */
@Composable
private fun ApkDetailDialog(
    detail: com.apex.files.ui.screens.tools.ApkViewModel.ApkDetail,
    onDismiss: () -> Unit,
) {
    var showXml by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                val deep = detail.deep
                val fileName = detail.deep?.fileName ?: ""

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = detail.icon
                    if (icon != null) {
                        Image(
                            bitmap = icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(46.dp),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Android,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            fileName.ifEmpty { "Análisis" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(deep?.summary?.packageName ?: "")
                                if (detail.error != null) append(" · error")
                            },
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = ApexBorder, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))

                when {
                    detail.loading -> {
                        NeonProgressBar(progress = null)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Decodificando AndroidManifest.xml…",
                            style = MonoTextStyleSmall,
                            color = ApexTextMuted,
                        )
                    }
                    detail.error != null && deep == null -> {
                        Text(
                            detail.error ?: "Error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    deep != null && deep.summary != null -> {
                        ManifestFields(deep)

                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { showXml = !showXml }) {
                                Text(
                                    if (showXml) "Ocultar XML decodificado" else "Ver XML decodificado",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onDismiss) {
                                Text("Cerrar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (showXml && deep.decodedXml != null) {
                            Text(
                                deep.decodedXml,
                                style = MonoTextStyleSmall.copy(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }
                    }
                    else -> {
                        Text(
                            "Sin datos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onDismiss) {
                            Text("Cerrar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManifestFields(deep: ApkManifestDecoder.DeepInfo) {
    val s = deep.summary ?: return
    val rows = buildList {
        s.packageName?.let { add("Paquete" to it) }
        s.label?.let { add("Nombre" to it) }
        if (s.versionName != null || s.versionCode != null) {
            add("Versión" to listOfNotNull(s.versionName, s.versionCode?.let { "código $it" }).joinToString(" · "))
        }
        val sdkParts = buildList {
            s.minSdk?.let { add("min $it") }
            s.targetSdk?.let { add("target $it") }
            s.compileSdk?.let { add("compile $it") }
        }
        if (sdkParts.isNotEmpty()) add("SDK" to sdkParts.joinToString(" · "))
        s.debuggable?.let { add("Debuggable" to if (it) "sí" else "no") }
        s.iconRef?.let { add("Icono" to it) }
        if (deep.splits.isNotEmpty()) {
            add("Contenido" to "${deep.splits.size} APK internos")
        }
        if (deep.splits.isNotEmpty() && deep.splits.size <= 6) {
            add("Splits" to deep.splits.joinToString("\n"))
        }
        if (s.permissions.isNotEmpty()) add("Permisos" to s.permissions.joinToString("\n"))
        if (s.features.isNotEmpty()) add("Funciones" to "${s.features.size} declaradas")
        if (s.components.isNotEmpty()) {
            val parts = s.components.entries.sortedByDescending { it.value }.map { (tag, count) ->
                "$count $tag"
            }
            add("Componentes" to parts.joinToString(" · "))
        }
    }

    rows.forEach { (label, value) ->
        if (rows.indexOfFirst { it.first == label } != 0) {
            Spacer(Modifier.height(6.dp))
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            ),
        )
        Text(
            value,
            style = if (label == "Permisos" || label == "Splits") {
                MonoTextStyleSmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f))
            } else {
                MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground)
            },
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    if (rows.isEmpty()) {
        Text(
            "Manifest sin atributos destacables",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}