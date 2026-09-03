package com.apex.files.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.apex.files.core.Accent
import com.apex.files.core.AppContainer
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val accent: StateFlow<Accent> = container.settings.accent
    val showHidden: StateFlow<Boolean> = container.settings.showHidden

    fun setAccent(accent: Accent) = container.settings.setAccent(accent)

    fun setShowHidden(show: Boolean) = container.settings.setShowHidden(show)
}