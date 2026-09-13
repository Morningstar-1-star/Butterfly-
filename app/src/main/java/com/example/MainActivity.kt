package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.supabase.SupabaseAuthManager
import com.example.ui.MainViewModel
import com.example.ui.animation.ButterflyOpeningAnimation
import com.example.ui.animation.FairyBunnyOpeningAnimation
import com.example.ui.animation.MtvMoonButterflyOpeningAnimation
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.remove()
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupHighRefreshRate()

        handleDeepLinkIntent(intent)

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
                // Safety watchdog: ensure opening animation is guaranteed to dismiss within 1.2s
                LaunchedEffect(showOpeningAnimation, isOpeningAnimationEnabled) {
                    if (showOpeningAnimation && isOpeningAnimationEnabled) {
                        kotlinx.coroutines.delay(1200L)
                        viewModel.dismissOpeningAnimation()
                    }
                }

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
                            MainViewModel.OpeningAnimationStyle.MTV_MOON_FLAG -> {
                                MtvMoonButterflyOpeningAnimation(
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
                        val params = buildPipParams()
                        enterPictureInPictureMode(params)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun buildPipParams(): android.app.PictureInPictureParams {
        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder.setActions(createPipActions())
        }
        return builder.build()
    }

    private fun createPipActions(): List<android.app.RemoteAction> {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return emptyList()
        val actions = mutableListOf<android.app.RemoteAction>()

        // 1. Headphones / Audio Mode Action (Matches user screenshot: tap to switch video to audio + dynamic island)
        val audioIntent = Intent(this, com.example.ui.player.dynamicisland.ButterflyPipActionReceiver::class.java).apply {
            action = com.example.ui.player.dynamicisland.ButterflyPipActionReceiver.ACTION_PIP_AUDIO_MODE
        }
        val audioPendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            201,
            audioIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val audioIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_headphones)
        actions.add(
            android.app.RemoteAction(
                audioIcon,
                "Audio Mode",
                "Switch to background Audio Mode & Dynamic Island",
                audioPendingIntent
            )
        )

        // 2. Play / Pause Action
        val isPlaying = com.example.ui.player.GlobalPlayerManager.isPlaying.value
        val playIntent = Intent(this, com.example.ui.player.dynamicisland.ButterflyPipActionReceiver::class.java).apply {
            action = com.example.ui.player.dynamicisland.ButterflyPipActionReceiver.ACTION_PIP_TOGGLE_PLAY
        }
        val playPendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            202,
            playIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val playIcon = android.graphics.drawable.Icon.createWithResource(
            this,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
        actions.add(
            android.app.RemoteAction(
                playIcon,
                if (isPlaying) "Pause" else "Play",
                "Toggle playback",
                playPendingIntent
            )
        )

        // 3. Forward 10s Action
        val forwardIntent = Intent(this, com.example.ui.player.dynamicisland.ButterflyPipActionReceiver::class.java).apply {
            action = com.example.ui.player.dynamicisland.ButterflyPipActionReceiver.ACTION_PIP_FORWARD_10
        }
        val forwardPendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            203,
            forwardIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val forwardIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_forward_10)
        actions.add(
            android.app.RemoteAction(
                forwardIcon,
                "Forward 10s",
                "Seek forward 10 seconds",
                forwardPendingIntent
            )
        )

        return actions
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                setPictureInPictureParams(buildPipParams())
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCustomIntents(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleCustomIntents(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("EXIT_PIP_TO_AUDIO", false)) {
            moveTaskToBack(true)
        }
        if (intent.getBooleanExtra("FROM_DYNAMIC_ISLAND", false)) {
            viewModel.navigateToScreen(com.example.model.AppScreen.PLAYER)
        }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "butterfly") {
            lifecycleScope.launch {
                val result = SupabaseAuthManager.handleAuthCallback(uri)
                if (result.isSuccess) {
                    val email = result.getOrNull()?.user?.email
                    val msg = if (!email.isNullOrBlank()) "Account confirmed! Logged in as $email" else "Email confirmed! You are now logged in."
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Authentication failed"
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
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



