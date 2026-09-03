package com.apex.files.ui.screens.drives

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.storage.DrivesRepository
import com.apex.files.data.storage.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DrivesViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val volumes: List<DrivesRepository.Volume> = emptyList(),
        val usages: Map<String, StorageStats.Usage> = emptyMap(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val (volumes, usages) = withContext(Dispatchers.IO) {
                val vols = container.drives.volumes()
                val usageMap = HashMap<String, StorageStats.Usage>()
                for (v in vols) {
                    v.path?.let { path ->
                        StorageStats.usageOf(path)?.let { usageMap[v.key] = it }
                    }
                }
                Pair(vols, usageMap)
            }
            _state.update { it.copy(volumes = volumes, usages = usages) }
        }
    }

    fun addSafTree(uri: Uri, name: String) {
        container.drives.addSafTree(uri, name)
        refresh()
    }

    fun removeSafTree(uri: Uri) {
        container.drives.removeSafTree(uri)
        refresh()
    }
}