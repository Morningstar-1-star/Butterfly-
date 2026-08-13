package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Required by ArchiveOrgProvider for provider operations.
        com.example.plugin.providers.ArchiveOrgProvider.contextRef =
            applicationContext

        enableEdgeToEdge()
        setupHighRefreshRate()
        setupCoilCache()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()

            com.example.ui.theme.MyApplicationTheme(
                themeMode = themeMode,
                accentColor = accentColor
            ) {
                HomeScreen(viewModel = viewModel)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (
            viewModel.activeVideoId.value != null &&
            com.example.ui.player.GlobalPlayerManager.isPlaying.value
        ) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val params =
                        android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(android.util.Rational(16, 9))
                            .build()

                    enterPictureInPictureMode(params)
                } catch (_: Exception) {
                    // PiP is optional.
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(
            isInPictureInPictureMode,
            newConfig
        )

        viewModel.setPipMode(isInPictureInPictureMode)
    }

    private fun setupCoilCache() {
        try {
            val imageLoader =
                ImageLoader.Builder(applicationContext)
                    .memoryCache {
                        MemoryCache.Builder(applicationContext)
                            .maxSizePercent(0.25)
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(
                                applicationContext.cacheDir
                                    .resolve("image_cache")
                            )
                            .maxSizeBytes(250L * 1024 * 1024)
                            .build()
                    }
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build()

            Coil.setImageLoader(imageLoader)
        } catch (_: Exception) {
            // Cache setup is optional.
        }
    }

    private fun setupHighRefreshRate() {
        try {
            val currentWindow = window
            val params = currentWindow.attributes

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display?.supportedModes
                    ?.maxByOrNull { it.refreshRate }
                    ?.let { maxMode ->
                        params.preferredDisplayModeId = maxMode.modeId
                    }
            } else if (
                android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.M
            ) {
                @Suppress("DEPRECATION")
                currentWindow.windowManager.defaultDisplay
                    ?.supportedModes
                    ?.maxByOrNull { it.refreshRate }
                    ?.let { maxMode ->
                        params.preferredDisplayModeId = maxMode.modeId
                    }
            }

            currentWindow.attributes = params
        } catch (_: Exception) {
            // High refresh rate is optional.
        }
    }
}
