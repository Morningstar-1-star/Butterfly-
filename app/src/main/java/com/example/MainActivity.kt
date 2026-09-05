package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.ui.MainViewModel
import com.example.ui.animation.ButterflyOpeningAnimation
import com.example.ui.animation.FairyBunnyOpeningAnimation
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupHighRefreshRate()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()
            val showOpeningAnimation by viewModel.showOpeningAnimation.collectAsState()
            val isOpeningAnimationEnabled by viewModel.isOpeningAnimationEnabled.collectAsState()
            val openingAnimationStyle by viewModel.openingAnimationStyle.collectAsState()

            MyApplicationTheme(
                themeMode = themeMode,
                accentColor = accentColor
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(viewModel = viewModel)

                    if (showOpeningAnimation && isOpeningAnimationEnabled) {
                        when (openingAnimationStyle) {
                            MainViewModel.OpeningAnimationStyle.CLASSIC_BUTTERFLY -> {
                                ButterflyOpeningAnimation(
                                    themeMode = themeMode,
                                    accentColor = accentColor,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(9999f),
                                    onAnimationFinished = {
                                        viewModel.dismissOpeningAnimation()
                                    }
                                )
                            }
                            MainViewModel.OpeningAnimationStyle.FAIRY_BUNNY -> {
                                FairyBunnyOpeningAnimation(
                                    themeMode = themeMode,
                                    accentColor = accentColor,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(9999f),
                                    onAnimationFinished = {
                                        viewModel.dismissOpeningAnimation()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.activeVideoId.value != null && com.example.ui.player.GlobalPlayerManager.isPlaying.value) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                        val params = android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(android.util.Rational(16, 9))
                            .build()
                        enterPictureInPictureMode(params)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setPipMode(isInPictureInPictureMode)
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



