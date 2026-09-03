package com.apex.files.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.apex.files.Navigator
import com.apex.files.core.AppContainer
import com.apex.files.ui.components.OperationCenterViewModel

/** Manual DI: the app-wide container. */
val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no inyectado")
}

/** App-scoped operation center (copy/move/delete/compress/extract). */
val LocalOperationCenter = staticCompositionLocalOf<OperationCenterViewModel> {
    error("OperationCenter no inyectado")
}

/** The manual back-stack navigator. */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("Navigator no inyectado")
}