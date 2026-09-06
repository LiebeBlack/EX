package com.apex.files.ui.screens.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.wifi.LanDevice
import com.apex.files.data.wifi.WifiConnection
import com.apex.files.data.wifi.WifiNetwork
import com.apex.files.data.wifi.WifiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wi-Fi analyzer state. Refreshes on demand and on a gentle interval while
 * the screen is open; scan results arrive a second or two after [refresh].
 */
class WifiViewModel(private val container: AppContainer) : ViewModel() {

    private val repo: WifiRepository by lazy { WifiRepository(container.appContext) }

    data class UiState(
        val loading: Boolean = true,
        val scanning: Boolean = false,
        val wifiEnabled: Boolean = true,
        val permissionMissing: Boolean = false,
        val connection: WifiConnection? = null,
        val networks: List<WifiNetwork> = emptyList(),
        val devices: List<LanDevice> = emptyList(),
        val devicesNote: String? = null,
        val lastUpdated: Long = 0L,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
        // Gentle live refresh while the screen stays open.
        viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                refresh()
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val missing = !repo.hasScanPermission
            val enabled = repo.isEnabled
            val connection = if (enabled) repo.currentConnection() else null
            _state.update {
                it.copy(
                    loading = false,
                    wifiEnabled = enabled,
                    permissionMissing = missing,
                    connection = connection,
                )
            }
            if (missing || !enabled) return@launch

            _state.update { it.copy(scanning = true) }
            repo.startScan()
            // Scan results land asynchronously; sample briefly until they
            // appear (or a short timeout elapses).
            var networks = emptyList<WifiNetwork>()
            for (i in 0 until SCAN_SAMPLE_ATTEMPTS) {
                delay(SCAN_SAMPLE_DELAY_MS)
                networks = repo.scanNetworks()
                if (networks.isNotEmpty()) break
            }
            val devices = repo.connectedDevices()
            _state.update {
                it.copy(
                    scanning = false,
                    networks = networks,
                    devices = devices,
                    devicesNote = if (devices.isEmpty()) DEVICES_UNAVAILABLE else null,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _state.update { it.copy(permissionMissing = !granted) }
        if (granted) refresh()
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 8_000L
        const val SCAN_SAMPLE_ATTEMPTS = 12
        const val SCAN_SAMPLE_DELAY_MS = 350L
        const val DEVICES_UNAVAILABLE =
            "No se detectaron dispositivos: la tabla ARP del dispositivo no es accesible o la red está vacía."
    }
}