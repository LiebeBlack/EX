package com.apex.files.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.apex.files.data.fs.Conflict
import com.apex.files.data.fs.ConflictDecision
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexDanger
import com.apex.files.ui.theme.MonoTextStyleSmall

/**
 * Modal shown mid-operation when a destination already contains the name
 * being copied/moved/extracted. Four explicit actions; back press aborts
 * the operation safely.
 */
@Composable
fun ConflictDialog(
    conflict: Conflict,
    onDecision: (ConflictDecision, applyToAll: Boolean) -> Unit,
) {
    var applyAll by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { onDecision(ConflictDecision.CANCEL_OPERATION) }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .wrapContentWidth(Alignment.CenterHorizontally),
            shape = MaterialTheme.shapes.large,
            color = ApexContainerHigh,
            border = BorderStroke(1.dp, ApexBorder),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (conflict.isDir) "Carpeta existente" else "Archivo existente",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "\u201C${conflict.name}\u201D ya existe en el destino. ¿Qué quieres hacer?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    conflict.destPath,
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))

                TextButton(
                    onClick = { onDecision(ConflictDecision.OVERWRITE, applyAll) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (conflict.isDir) "Combinar carpetas" else "Sobrescribir",
                        color = if (conflict.isDir) MaterialTheme.colorScheme.primary else ApexDanger,
                    )
                }
                TextButton(
                    onClick = { onDecision(ConflictDecision.KEEP_BOTH, applyAll) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Conservar ambos (renombrar)", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(
                    onClick = { onDecision(ConflictDecision.SKIP, applyAll) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Omitir", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = { onDecision(ConflictDecision.CANCEL_OPERATION, applyAll = false) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancelar operación", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { applyAll = !applyAll }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
                    Text(
                        "Aplicar a todos los conflictos restantes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
