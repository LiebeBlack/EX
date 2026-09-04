package com.apex.files.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.apex.files.core.Accent
import com.apex.files.core.AppContainer
import com.apex.files.data.model.SortDirection
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val accent: StateFlow<Accent> = container.settings.accent
    val customAccent: StateFlow<Long> = container.settings.customAccent
    val showHidden: StateFlow<Boolean> = container.settings.showHidden
    val sortDirection: StateFlow<SortDirection> = container.settings.sortDirection

    fun setAccent(accent: Accent) = container.settings.setAccent(accent)

    fun setCustomAccent(hex: Long) = container.settings.setCustomAccent(hex)

    fun setShowHidden(show: Boolean) = container.settings.setShowHidden(show)

    fun setSortDirection(direction: SortDirection) = container.settings.setSortDirection(direction)
}
