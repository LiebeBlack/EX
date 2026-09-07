package com.apex.files.ui.screens.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Warning dialog that shows for exactly 3 seconds before allowing the user to accept.
 * The "Accept" button is disabled during the countdown period.
 */
@Composable
fun PermissionWarningDialog(
    title: String,
    message: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    var countdown by remember { mutableIntStateOf(3) }
    var showDialog by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        for (i in 3 downTo 1) {
            countdown = i
            delay(1000L)
        }
        countdown = 0
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                showDialog = false
                                onAccept()
                            },
                            enabled = countdown == 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.width(120.dp)
                        ) {
                            Text(if (countdown > 0) "Aceptar ($countdown)" else "Aceptar")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wi-Fi and network permission warning dialog with 3-second countdown.
 */
@Composable
fun WifiPermissionWarningDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    PermissionWarningDialog(
        title = "Advertencia de Uso de Datos",
        message = "Esta aplicación realizará un escaneo de redes Wi-Fi y dispositivos conectados para análisis de red local. No se transmitirán datos a servidores externos. El escaneo se realiza completamente en el dispositivo.",
        onAccept = onAccept,
        onDismiss = onDismiss
    )
}

/**
 * Device discovery permission warning dialog with 3-second countdown.
 */
@Composable
fun DevicePermissionWarningDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    PermissionWarningDialog(
        title = "Advertencia de Escaneo de Dispositivos",
        message = "Esta aplicación realizará un escaneo de dispositivos en la red local para análisis de conectividad. No se accederá a datos personales de los dispositivos externos. El análisis se limita a información de red básica.",
        onAccept = onAccept,
        onDismiss = onDismiss
    )
}