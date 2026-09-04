package com.apex.files.ui.screens.rename

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apex.files.core.AppContainer
import com.apex.files.data.fs.BatchRenamer
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BatchRenameViewModel(
    private val container: AppContainer,
    private val nodes: List<FileNode>,
) : ViewModel() {

    data class UiState(
        val find: String = "",
        val replace: String = "",
        val prefix: String = "",
        val suffix: String = "",
        val renumber: Boolean = false,
        val start: Int = 1,
        val digits: Int = 2,
        /** Live preview computed from the options. */
        val plan: BatchRenamer.Plan = BatchRenamer.Plan(emptyList(), emptyList()),
        val applying: Boolean = false,
        val done: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val names: List<String> = nodes.map { it.name }

    private fun recompute() {
        val s = _state.value
        _state.update {
            it.copy(
                plan = BatchRenamer.plan(
                    names,
                    BatchRenamer.Options(
                        find = s.find,
                        replace = s.replace,
                        prefix = s.prefix,
                        suffix = s.suffix,
                        renumber = s.renumber,
                        start = s.start,
                        digits = s.digits,
                    ),
                )
            )
        }
    }

    fun setFind(v: String) { _state.update { it.copy(find = v) }; recompute() }
    fun setReplace(v: String) { _state.update { it.copy(replace = v) }; recompute() }
    fun setPrefix(v: String) { _state.update { it.copy(prefix = v) }; recompute() }
    fun setSuffix(v: String) { _state.update { it.copy(suffix = v) }; recompute() }
    fun setRenumber(v: Boolean) { _state.update { it.copy(renumber = v) }; recompute() }
    fun setStart(v: Int) { _state.update { it.copy(start = v.coerceAtLeast(0)) }; recompute() }
    fun setDigits(v: Int) { _state.update { it.copy(digits = v.coerceIn(1, 4)) }; recompute() }

    /** Applies the plan through the repository; reports failures via [notice]. */
    fun apply(onNotice: (String) -> Unit) {
        if (_state.value.applying || _state.value.done) return
        val plan = _state.value.plan
        val changes = plan.items.filter { it.changed }
        if (changes.isEmpty()) {
            onNotice("No hay cambios que aplicar")
            return
        }
        _state.update { it.copy(applying = true) }
        viewModelScope.launch {
            var done = 0
            var errors = 0
            var skipped = 0
            var firstError: String? = null
            for (item in changes) {
                val node = nodes.firstOrNull { it.name == item.from } ?: continue
                val renamed = container.fs.rename(node, item.to)
                when {
                    renamed == null -> {
                        skipped++
                        if (firstError == null) firstError = "«${item.to}» ya existe o no es válido"
                    }
                    else -> done++
                }
            }
            _state.update { it.copy(applying = false, done = true) }
            val msg = buildString {
                append("Renombrados: $done")
                if (skipped > 0) append(" · $skipped omitidos")
                if (errors > 0) append(" · $errors errores")
            }
            onNotice(msg)
        }
    }
}