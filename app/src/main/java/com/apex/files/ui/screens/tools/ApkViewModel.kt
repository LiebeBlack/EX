package com.apex.files.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.core.OpProgress
import com.apex.files.data.fs.OpResult
import com.apex.files.data.fs.Paths
import com.apex.files.data.model.FileNode
import com.apex.files.data.model.Location
import com.apex.files.data.storage.DrivesRepository
import com.apex.files.data.storage.ToolRoots
import com.apex.files.tools.ApkScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApkViewModel(private val container: AppContainer) : ViewModel() {

    private val toolRoots = ToolRoots(container.appContext)

    data class UiState(
        val scanning: Boolean = false,
        val currentPath: String = "",
        val apks: List<ApkScanner.ApkInfo> = emptyList(),
        val selection: Set<String> = emptySet(),
        val done: Boolean = false,
        val volumes: List<DrivesRepository.Volume> = emptyList(),
        val rootKey: String = "",
    ) {
        val notInstalled: List<ApkScanner.ApkInfo>
            get() = apks.filter { it.installed == false }

        val rootName: String
            get() = volumes.firstOrNull { it.key == rootKey }?.name ?: "Almacenamiento interno"
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadVolumes()
    }

    private fun loadVolumes() {
        viewModelScope.launch {
            val vols = withContext(Dispatchers.IO) { container.drives.volumes() }
            val defaultKey = vols.firstOrNull { !it.removable }?.key ?: "fs:${Paths.internalRoot().absolutePath}"
            _state.update {
                it.copy(volumes = vols, rootKey = toolRoots.get("apk") ?: defaultKey)
            }
        }
    }

    fun setRoot(key: String) {
        toolRoots.set("apk", key)
        _state.update {
            it.copy(rootKey = key, done = false, apks = emptyList(), selection = emptySet(), scanning = false)
        }
    }

    private fun scanRoot(): Location {
        val s = _state.value
        return s.volumes.firstOrNull { it.key == s.rootKey }?.location
            ?: Location.Fs(Paths.internalRoot())
    }

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, done = false, apks = emptyList(), selection = emptySet()) }
        viewModelScope.launch {
            val root = container.fs.rootNode(scanRoot())
            container.apkScanner.scan(root).collect { scan ->
                _state.update {
                    it.copy(
                        currentPath = scan.currentPath,
                        apks = if (scan.done) scan.apks else it.apks,
                        done = scan.done,
                        scanning = !scan.done,
                    )
                }
            }
        }
    }

    fun toggleSelect(path: String) {
        _state.update { s ->
            val sel = s.selection.toMutableSet()
            if (!sel.add(path)) sel.remove(path)
            s.copy(selection = sel)
        }
    }

    fun selectNotInstalled() {
        _state.update {
            it.copy(selection = it.notInstalled.map { a -> a.node.path }.toSet())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    // ------------------------------------------------------ deep analysis

    /** Per-APK detail: native binary-manifest decode + launcher icon. */
    data class ApkDetail(
        val path: String,
        val deep: ApkManifestDecoder.DeepInfo? = null,
        val icon: android.graphics.Bitmap? = null,
        val loading: Boolean = false,
        val error: String? = null,
    ) {
        val open: Boolean get() = loading || deep != null || error != null
    }

    private val _detail = MutableStateFlow<ApkDetail?>(null)
    val detail: StateFlow<ApkDetail?> = _detail.asStateFlow()

    /** Decode cache: reopening a detail never re-reads the file. */
    private val detailCache = HashMap<String, ApkDetail>()

    /** Decodes the selected APK/container on IO (manifest, splits, icon). */
    fun requestDetail(path: String) {
        if (_detail.value?.loading == true) return
        val info = _state.value.apks.firstOrNull { it.node.path == path } ?: return
        detailCache[path]?.let {
            _detail.value = it
            return
        }
        _detail.value = ApkDetail(path = path, loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val node = info.node
            val result: ApkDetail = if (node.uri != null) {
                ApkDetail(
                    path,
                    error = "El análisis profundo requiere el archivo en almacenamiento local.",
                )
            } else {
                val deep = ApkManifestDecoder.inspect(java.io.File(node.path))
                val icon = deep.iconBytes?.let { bytes ->
                    runCatching {
                        val opts = samplingOptions(bytes)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    }.getOrNull()
                }
                if (deep.error == null) ApkDetail(path, deep = deep, icon = icon)
                else ApkDetail(path, error = deep.error, icon = icon)
            }
            detailCache[path] = result
            _detail.value = result
        }
    }

    fun dismissDetail() {
        _detail.value = null
    }

    /** Bounds-aware downsampling so huge PNGs never OOM the dialog. */
    private fun samplingOptions(bytes: ByteArray): android.graphics.BitmapFactory.Options {
        val o = android.graphics.BitmapFactory.Options()
        o.inJustDecodeBounds = true
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 256 && o.outHeight / (sample * 2) >= 256 && sample < 16) sample *= 2
        o.inJustDecodeBounds = false
        o.inSampleSize = sample
        return o
    }

    private val _deleteSummary = MutableStateFlow<String?>(null)
    val deleteSummary: StateFlow<String?> = _deleteSummary.asStateFlow()

    fun deleteFlow(): Flow<OpProgress> = flow {
        val targets = _state.value.apks
            .map { it.node }
            .filter { it.path in _state.value.selection }
        var acc = OpResult()
        for (node in targets) {
            acc += container.fs.delete(node) { emit(it) }
        }
        val parts = buildList {
            add("Eliminados: ${acc.filesDone} instalador(es)")
            if (acc.errors > 0) add("${acc.errors} errores")
            if (acc.skipped > 0) add("${acc.skipped} omitidos")
        }
        _deleteSummary.value = parts.joinToString(" · ")
    }

    fun consumeDeleteSummary(): String? {
        val v = _deleteSummary.value
        _deleteSummary.value = null
        return v
    }

    fun reset() {
        _state.update { it.copy(done = false, apks = emptyList(), selection = emptySet(), scanning = false) }
    }
}
