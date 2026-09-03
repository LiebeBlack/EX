package com.apex.files.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.apex.files.core.AppContainer

/**
 * Builds a ViewModel through [AppContainer], keyed so independent screen
 * instances never share state.
 */
@Composable
inline fun <reified VM : ViewModel> apexViewModel(
    key: String,
    crossinline factory: (AppContainer) -> VM,
): VM {
    val container = LocalContainer.current
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { factory(container) }
        },
    )
}