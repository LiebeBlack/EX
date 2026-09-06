package com.apex.files.data.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import java.net.Inet4Address

/**
 * Local Wi-Fi diagnostics. Zero network traffic: connection state, link
 * speed and the surrounding networks come from [WifiManager]; the "connected
 * devices" list is read from the device's local ARP table (/proc/net/arp),
 * i.e. only the LAN peers this phone has recently exchanged frames with.
 *
 * Scanning requires the NEARBY_WIFI_DEVICES runtime permission (Android 13+)
 * or ACCESS_FINE_LOCATION (Android 8–12); connection info only needs the
 * install-time ACCESS_WIFI_STATE.
 */
class WifiRepository(context: Context) {

    private val appContext = context.applicationContext
    private val wifi: WifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** Runtime permission required to read scan results on this API level. */
    val scanPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

    val hasScanPermission: Boolean
        get() = ContextCompat.checkSelfPermission(appContext, scanPermission) ==
            PackageManager.PERMISSION_GRANTED

    val isEnabled: Boolean get() = runCatching { wifi.isWifiEnabled }.getOrDefault(false)

    val currentBssid: String?
        @SuppressLint("MissingPermission") get() = runCatching { currentWifiInfo()?.bssid }.getOrNull()

    /** Detailed snapshot of the network this device is currently on. */
    @SuppressLint("MissingPermission")
    fun currentConnection(): WifiConnection? {
        val info = runCatching { currentWifiInfo() }.getOrNull() ?: return null
        if (info.ssid == null) return null
        val ssid = info.ssid.trim('"').takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        val rssi = info.rssi
        val freq = info.frequency
        val (standardLabel, theoretical) = standardOf(info)
        val ipInfo = currentIpInfo()
        return WifiConnection(
            ssid = ssid ?: "Red desconocida",
            bssid = info.bssid,
            rssi = rssi,
            frequencyMhz = freq,
            channel = channelOf(freq),
            band = bandOf(freq),
            linkSpeedMbps = info.linkSpeed,
            theoreticalMbps = theoretical,
            standardLabel = standardLabel,
            signalPercent = signalPercent(rssi),
            signalLevel = signalLevel(rssi),
            ipAddress = ipInfo?.ipAddress,
            gateway = ipInfo?.gateway,
            efficiencyPercent = if (theoretical > 0) {
                ((info.linkSpeed.toFloat() / theoretical) * 100).toInt().coerceIn(0, 100)
            } else 0,
        )
    }

    /** Kicks off an asynchronous scan; results land within a second or two. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // third-party scan requests have no replacement; throttled since API 28
    fun startScan(): Boolean = runCatching { wifi.startScan() }.getOrDefault(false)

    /**
     * Latest scan results, deduplicated by BSSID (strongest reading wins)
     * and sorted by signal strength. Empty when the permission is missing,
     * Wi-Fi is off, or no scan has completed yet.
     */
    @SuppressLint("MissingPermission")
    fun scanNetworks(): List<WifiNetwork> {
        if (!hasScanPermission || !isEnabled) return emptyList()
        val raw = runCatching { wifi.scanResults }.getOrDefault(emptyList())
        val current = currentBssid
        val best = HashMap<String, ScanResult>()
        for (r in raw) {
            val ssid = ssidOf(r)?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            if (ssid == null && r.BSSID.isNullOrBlank()) continue
            val key = r.BSSID ?: ssid ?: continue
            val prev = best[key]
            if (prev == null || r.level > prev.level) best[key] = r
        }
        return best.values.map { r ->
            val ssid = ssidOf(r)?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            WifiNetwork(
                ssid = ssid ?: "(red oculta)",
                bssid = r.BSSID.orEmpty(),
                rssi = r.level,
                frequencyMhz = r.frequency,
                channel = channelOf(r.frequency),
                band = bandOf(r.frequency),
                security = securityOf(r.capabilities),
                capabilities = r.capabilities.orEmpty(),
                signalPercent = signalPercent(r.level),
                signalLevel = signalLevel(r.level),
                theoreticalMbps = theoreticalOf(r.capabilities, r.frequency),
                isCurrent = current != null && r.BSSID == current,
            )
        }.sortedByDescending { it.signalPercent }
    }

    /**
     * LAN peers visible in the local ARP table. Best-effort: many OEM builds
     * restrict /proc/net/arp to privileged apps, in which case the list is
     * empty and the UI explains the limitation. Never touches the network.
     */
    fun connectedDevices(): List<LanDevice> {
        val gateway = currentIpInfo()?.gateway
        val lines = try {
            val f = File("/proc/net/arp")
            if (!f.canRead()) return emptyList()
            f.readLines()
        } catch (e: Exception) {
            return emptyList()
        }
        val out = ArrayList<LanDevice>(lines.size)
        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 6) continue
            val ip = parts[0]
            val mac = parts[3]
            if (!ip.contains('.') || mac == "00:00:00:00:00:00") continue
            out.add(
                LanDevice(
                    ip = ip,
                    mac = mac,
                    hostname = "Dispositivo ${out.size + 1}",
                    isGateway = ip == gateway,
                )
            )
        }
        return out
    }

    // ------------------------------------------------------------- helpers

    /** Best guess of the 802.11 standard + theoretical max throughput. */
    private fun standardOf(info: WifiInfo): Pair<String, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (info.wifiStandard) {
                // WIFI_STANDARD_* live on ScanResult in the public SDK (the
                // WifiInfo copies are @hide); the numeric values are identical.
                ScanResult.WIFI_STANDARD_11AX -> "802.11ax" to if (info.frequency < 2500) 574 else 2401
                ScanResult.WIFI_STANDARD_11AC -> "802.11ac" to 1300
                ScanResult.WIFI_STANDARD_11N -> "802.11n" to if (info.frequency < 2500) 300 else 600
                // 11a / 11g / 11b are @hide even on ScanResult, so they are
                // matched by their frozen SDK values (UNKNOWN=0, LEGACY=1,
                // 11B=2, 11A=3, 11G=4, 11AC=5, 11N=6, 11AX=7).
                3 -> "802.11a" to 54
                4 -> "802.11g" to 54
                2 -> "802.11b" to 11
                else -> "802.11" to 54
            }
        }
        // API < 30: fall back to the band (2.4 GHz is almost always n).
        return if (info.frequency < 2500) {
            "802.11n (aprox.)" to 300
        } else {
            "802.11ac (aprox.)" to 1300
        }
    }

    /** Theoretical max for a nearby network, derived from its capabilities. */
    private fun theoreticalOf(capabilities: String, frequency: Int): Int = when {
        capabilities.contains("HE") -> if (frequency < 2500) 574 else 2401 // 802.11ax
        capabilities.contains("VHT") -> 1300                             // 802.11ac
        capabilities.contains("HT") -> if (frequency < 2500) 300 else 600 // 802.11n
        frequency < 2500 -> 54                                           // 802.11g
        else -> 54
    }

    private fun securityOf(capabilities: String): String = when {
        capabilities.contains("SAE") || capabilities.contains("WPA3") -> "WPA3"
        capabilities.contains("WPA2") || capabilities.contains("RSN") -> "WPA2"
        capabilities.contains("WPA") || capabilities.contains("PSK") -> "WPA"
        capabilities.contains("WEP") -> "WEP"
        capabilities.contains("OWE") -> "OWE"
        capabilities.contains("EAP") -> "Empresa"
        else -> "Abierta"
    }

    /** Rough signal quality: -100 dBm → 0 %, -50 dBm → 100 %. */
    private fun signalPercent(rssi: Int): Int = ((rssi + 100) * 2).coerceIn(0, 100)

    /**
     * 0–4 bars. The one-arg calculateSignalLevel overload is not part of the
     * public SDK (@SystemApi), so the deprecated five-level overload is the
     * only option — both return a level in [0, numLevels).
     */
    private fun signalLevel(rssi: Int): Int {
        @Suppress("DEPRECATION")
        return WifiManager.calculateSignalLevel(rssi, 5)
    }

    /** Scan SSID; the SSID field is deprecated since API 33 in favor of wifiSsid. */
    private fun ssidOf(r: ScanResult): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            r.wifiSsid?.toString()
        } else {
            @Suppress("DEPRECATION") r.SSID
        }

    /** Channel number for a frequency in MHz (0 when unrecognized). */
    private fun channelOf(frequency: Int): Int = when {
        frequency in 2412..2472 -> (frequency - 2412) / 5 + 1
        frequency == 2484 -> 14
        frequency in 5170..5825 -> (frequency - 5180) / 5 + 36
        frequency in 5955..7115 -> (frequency - 5955) / 5 + 1
        else -> 0
    }

    private fun bandOf(frequency: Int): String = when {
        frequency > 0 && frequency < 2500 -> "2.4 GHz"
        frequency in 2500 until 5900 -> "5 GHz"
        frequency >= 5900 -> "6 GHz"
        else -> "—"
    }

    /**
     * Current Wi-Fi link details without the deprecated
     * WifiManager#connectionInfo / #dhcpInfo pair: the [WifiInfo] comes from
     * the active network's capabilities (transportInfo) and the IP / default
     * gateway from its LinkProperties. Null when not on Wi-Fi.
     */
    private fun currentWifiInfo(): WifiInfo? = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return@runCatching null
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@runCatching null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION") wifi.connectionInfo
        }
    }.getOrNull()

    /** IPv4 address + default gateway of the active Wi-Fi network. */
    private fun currentIpInfo(): IpInfo? = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return@runCatching null
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@runCatching null
        val lp = cm.getLinkProperties(network) ?: return@runCatching null
        IpInfo(
            ipAddress = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
                ?.address?.hostAddress,
            gateway = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway?.hostAddress,
        )
    }.getOrNull()

    private data class IpInfo(val ipAddress: String?, val gateway: String?)
}

/** Snapshot of the network this device is currently connected to. */
data class WifiConnection(
    val ssid: String,
    val bssid: String?,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val linkSpeedMbps: Int,
    val theoreticalMbps: Int,
    val standardLabel: String,
    val signalPercent: Int,
    val signalLevel: Int,
    val ipAddress: String?,
    val gateway: String?,
    val efficiencyPercent: Int,
)

/** A nearby network found by the last scan. */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val security: String,
    val capabilities: String,
    val signalPercent: Int,
    val signalLevel: Int,
    val theoreticalMbps: Int,
    val isCurrent: Boolean,
)

/** One LAN peer visible in the local ARP table. */
data class LanDevice(
    val ip: String,
    val mac: String,
    val hostname: String,
    val isGateway: Boolean,
)