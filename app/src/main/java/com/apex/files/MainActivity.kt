package com.apex.files

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.apex.files.core.AppContainer
import com.apex.files.ui.ApexAppUi

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val container = remember { AppContainer(applicationContext) }
            ApexAppUi(container)
        }
    }
}