package com.apex.files.ui.screens.wifi

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.files.data.wifi.LanDevice
import com.apex.files.data.wifi.WifiConnection
import com.apex.files.data.wifi.WifiNetwork
import com.apex.files.ui.LocalNavigator
import com.apex.files.ui.apexViewModel
import com.apex.files.ui.components.ApexCard
import com.apex.files.ui.components.ApexIconButton
import com.apex.files.ui.components.ApexTopBar
import com.apex.files.ui.components.NeonProgressBar
import com.apex.files.ui.theme.ApexBorder
import com.apex.files.ui.theme.ApexContainerHigh
import com.apex.files.ui.theme.ApexSuccess
import com.apex.files.ui.theme.ApexTextMuted
import com.apex.files.ui.theme.MonoTextStyle
import com.apex.files.ui.theme.MonoTextStyleSmall

/** Wi-Fi analyzer: current connection, LAN devices and nearby networks. */
@Composable
fun WifiScreen() {
    val navigator = LocalNavigator.current
    val vm: WifiViewModel = apexViewModel(key = "wifi") { c -> WifiViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setPermissionGranted(granted) }

    Column(Modifier.fillMaxSize()) {
        ApexTopBar(
            title = "Redes Wi-Fi",
            onBack = { navigator.pop() },
            subtitle = "Analizador local · sin conexión a Internet",
            actions = {
                ApexIconButton(Icons.Outlined.Refresh, "Actualizar") { vm.refresh() }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.permissionMissing) {
                item { PermissionBanner(onGrant = { permissionLauncher.launch(permission) }) }
            }
            if (!state.wifiEnabled) {
                item { InfoNote("El Wi-Fi está apagado. Actívalo para ver redes cercanas y dispositivos.") }
            }
            item { ConnectionCard(state.connection, state.loading) }
            item { DevicesCard(state.devices, state.devicesNote) }
            item { SectionHeader("Redes cercanas") }
            if (state.scanning) {
                item { NeonProgressBar(progress = null, modifier = Modifier.fillMaxWidth()) }
            }
            // Index-based keys: BSSIDs can be blank on some devices and two
            // entries could otherwise collide (duplicate keys crash LazyColumn).
            items(state.networks) { network ->
                NetworkRow(network)
            }
            if (!state.scanning && state.networks.isEmpty() && !state.permissionMissing && state.wifiEnabled) {
                item { InfoNote("Sin resultados. Pulsa actualizar para escanear de nuevo.") }
            }
            item {
                Text(
                    "Velocidades teóricas estimadas según banda y estándar. Los dispositivos provienen de la " +
                        "tabla ARP local; algunos fabricantes la restringen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexTextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    ApexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Permiso necesario", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "Concede el permiso para escanear redes Wi-Fi cercanas. Nunca se usa para localización."
                    } else {
                        "Se necesita la ubicación solo para escanear redes (Android 12 y anteriores). Actívala también en los Ajustes del sistema."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Conceder permiso")
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    ApexCard(Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectionCard(connection: WifiConnection?, loading: Boolean) {
    ApexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Wifi, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Conexión actual",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    connection?.ssid ?: if (loading) "Comprobando…" else "Sin conexión Wi-Fi",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusChip(connected = connection != null)
        }
        if (connection != null) {
            Spacer(Modifier.height(12.dp))
            NeonProgressBar(progress = connection.signalPercent / 100f)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Velocidad de enlace", "${connection.linkSpeedMbps} Mbps", Modifier.weight(1f))
                StatTile("Velocidad teórica", "${connection.theoreticalMbps} Mbps", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Calidad de señal", "${connection.signalPercent}%", Modifier.weight(1f))
                StatTile("Eficiencia del enlace", "${connection.efficiencyPercent}%", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${connection.standardLabel} · ${connection.band} · canal ${connection.channel} (${connection.frequencyMhz} MHz)",
                style = MonoTextStyleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            connection.ipAddress?.let { ip ->
                Text(
                    "IP $ip${connection.gateway?.let { " · Gateway $it" } ?: ""}",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(connected: Boolean) {
    val color = if (connected) ApexSuccess else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (connected) ApexSuccess.copy(alpha = 0.12f) else ApexBorder.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (connected) ApexSuccess.copy(alpha = 0.6f) else ApexBorder),
    ) {
        Text(
            if (connected) "Conectado" else "Desconectado",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(ApexContainerHigh, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = ApexTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DevicesCard(devices: List<LanDevice>, note: String?) {
    ApexCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Router, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Dispositivos en la red",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(devices.size.toString(), style = MonoTextStyle, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        when {
            note != null -> Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            devices.isEmpty() -> Text(
                "Sin dispositivos detectados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> devices.forEach { device ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            device.hostname,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (device.isGateway) "${device.ip} · ${device.mac} · Gateway"
                            else "${device.ip} · ${device.mac}",
                            style = MonoTextStyleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(network: WifiNetwork) {
    ApexCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        network.ssid,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (network.isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        SecurityBadge("Actual")
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${network.security} · ${network.band} · canal ${network.channel} · teórica ${network.theoreticalMbps} Mbps",
                    style = MonoTextStyleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${network.signalPercent}%",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(3.dp))
                SignalBars(network.signalLevel)
            }
        }
    }
}

@Composable
private fun SecurityBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Four little bars mirroring the classic Wi-Fi strength glyph. */
@Composable
private fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    val heights = listOf(6.dp, 9.dp, 12.dp, 15.dp)
    Row(
        modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        heights.forEachIndexed { index, h ->
            Box(
                Modifier
                    .width(4.dp)
                    .height(h)
                    .background(
                        if (index < level) MaterialTheme.colorScheme.primary else ApexBorder,
                        RoundedCornerShape(1.dp),
                    )
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}