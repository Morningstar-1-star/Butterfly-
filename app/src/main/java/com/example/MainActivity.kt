package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupHighRefreshRate()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()

            MyApplicationTheme(
                themeMode = themeMode,
                accentColor = accentColor
            ) {
                HomeScreen(viewModel = viewModel)
            }
        }
    }

    private fun setupHighRefreshRate() {
        try {
            val currentWindow = window
            val params = currentWindow.attributes
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { maxMode ->
                    params.preferredDisplayModeId = maxMode.modeId
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                currentWindow.windowManager.defaultDisplay?.supportedModes?.maxByOrNull { it.refreshRate }?.let { maxMode ->
                    params.preferredDisplayModeId = maxMode.modeId
                }
            }
            currentWindow.attributes = params
        } catch (e: Exception) {
            // High refresh rate optional
        }
    }
}



