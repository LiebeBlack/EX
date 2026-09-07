package com.apex.files.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.CategoryEngine
import com.apex.files.data.fs.SizeFormatter
import com.apex.files.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StatsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    data class FileStats(
        val totalFiles: Int = 0,
        val totalDirs: Int = 0,
        val totalSize: Long = 0L,
        val byCategory: Map<Category, Int> = emptyMap(),
        val byExtension: Map<String, Int> = emptyMap(),
        val largestFiles: List<FileStatsItem> = emptyList(),
    )

    data class FileStatsItem(
        val name: String,
        val path: String,
        val size: Long,
        val category: Category,
    )

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val stats: FileStats? = null,
        val scannedPath: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun scanPath(path: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val scanPath = path ?: container.fs.rootNode(
                    com.apex.files.data.model.Location.Fs(
                        com.apex.files.data.fs.Paths.internalRoot()
                    )
                ).path
                
                val stats = withContext(Dispatchers.IO) {
                    scanDirectory(File(scanPath))
                }
                
                _state.value = UiState(
                    loading = false,
                    stats = stats,
                    scannedPath = scanPath
                )
            } catch (e: Exception) {
                _state.value = UiState(
                    loading = false,
                    error = e.message ?: "Error al escanear"
                )
            }
        }
    }

    private fun scanDirectory(file: File): FileStats {
        val categoryCount = mutableMapOf<Category, Int>()
        val extensionCount = mutableMapOf<String, Int>()
        val largestFiles = mutableListOf<FileStatsItem>()
        var totalFiles = 0
        var totalDirs = 0
        var totalSize = 0L

        fun scanRecursive(f: File) {
            if (f.isDirectory) {
                totalDirs++
                f.listFiles()?.forEach { child ->
                    if (!child.name.startsWith(".") && !com.apex.files.data.fs.Paths.isExcluded(child)) {
                        scanRecursive(child)
                    }
                }
            } else {
                totalFiles++
                val size = f.length()
                totalSize += size
                
                val category = CategoryEngine.classify(f.name)
                categoryCount[category] = (categoryCount[category] ?: 0) + 1
                
                val ext = CategoryEngine.extensionOf(f.name)
                if (ext.isNotEmpty()) {
                    extensionCount[ext] = (extensionCount[ext] ?: 0) + 1
                }
                
                if (largestFiles.size < 10 || size > largestFiles.last().size) {
                    largestFiles.add(FileStatsItem(
                        name = f.name,
                        path = f.absolutePath,
                        size = size,
                        category = category
                    ))
                    largestFiles.sortByDescending { it.size }
                    if (largestFiles.size > 10) {
                        largestFiles.removeAt(largestFiles.size - 1)
                    }
                }
            }
        }

        scanRecursive(file)
        
        return FileStats(
            totalFiles = totalFiles,
            totalDirs = totalDirs,
            totalSize = totalSize,
            byCategory = categoryCount,
            byExtension = extensionCount,
            largestFiles = largestFiles
        )
    }
}