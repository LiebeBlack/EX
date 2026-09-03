package com.apex.files.ui.screens.benchmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.storage.DrivesRepository
import com.apex.files.data.storage.StorageStats
import com.apex.files.tools.StorageBenchmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BenchmarkViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val volumes: List<DrivesRepository.Volume> = emptyList(),
        val selected: String? = null,
        val running: Boolean = false,
        val result: StorageBenchmark.Result? = null,
        val error: String? = null,
        val freeBytes: Long = 0L,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val volumes = container.drives.volumes()
        _state.update {
            it.copy(
                volumes = volumes,
                selected = it.selected?.takeIf { s -> volumes.any { v -> v.key == s } } ?: volumes.firstOrNull()?.key,
            )
        }
        updateFree()
    }

    fun select(key: String) {
        _state.update { it.copy(selected = key) }
        updateFree()
    }

    private fun updateFree() {
        val vol = _state.value.volumes.firstOrNull { it.key == _state.value.selected }
        val free = vol?.path?.let { StorageStats.usageOf(it)?.availableBytes } ?: 0L
        _state.update { it.copy(freeBytes = free) }
    }

    fun run() {
        val vol = _state.value.volumes.firstOrNull { it.key == _state.value.selected } ?: return
        if (_state.value.running) return
        _state.update { it.copy(running = true, result = null, error = null) }
        viewModelScope.launch {
            try {
                val result = container.benchmark.run(vol.location)
                _state.update { it.copy(result = result, running = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Error", running = false) }
            }
        }
    }
}