package com.apex.files

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.apex.files.core.AppContainer
import com.apex.files.ui.ApexAppUi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main activity with Hilt dependency injection.
 * AppContainer is injected by Hilt to ensure proper lifecycle scoping.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // AppContainer is injected by Hilt with proper Singleton scope
            val container = remember { appContainer }
            ApexAppUi(container)
        }
    }
}