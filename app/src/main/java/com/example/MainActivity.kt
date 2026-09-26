package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.components.WhatsNewDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.WhatsNewManager
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

        // Register PiP action receiver for headphone (audio-only), play/pause, next
        val pipFilter = android.content.IntentFilter().apply {
            addAction(ACTION_PIP_HEADPHONES)
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_NEXT)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                pipActionReceiver,
                pipFilter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(pipActionReceiver, pipFilter)
        }

        // Keep PiP actions dynamically synced with playback state
        lifecycleScope.launch {
            com.example.ui.player.GlobalPlayerManager.isPlaying.collect { isPlaying ->
                updatePipParams()
                if (isPlaying) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        lifecycleScope.launch {
            com.example.ui.player.GlobalPlayerManager.isBuffering.collect { isBuffering ->
                if (isBuffering || com.example.ui.player.GlobalPlayerManager.isPlaying.value) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        lifecycleScope.launch {
            com.example.ui.player.GlobalPlayerManager.videoAspectRatio.collect {
                updatePipParams()
            }
        }
        lifecycleScope.launch {
            viewModel.activeVideoId.collect {
                updatePipParams()
            }
        }

        // Restore playback state if returning to app while audio is playing
        viewModel.syncWithGlobalPlayer()

        setContent {
            val context = LocalContext.current
            val themeMode by viewModel.themeMode.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()
            val showOpeningAnimation by viewModel.showOpeningAnimation.collectAsState()
            val isOpeningAnimationEnabled by viewModel.isOpeningAnimationEnabled.collectAsState()
            val openingAnimationStyle by viewModel.openingAnimationStyle.collectAsState()
            var showWhatsNewDialog by remember { mutableStateOf(false) }

            // Trigger "What's New" when the app was updated
            LaunchedEffect(Unit) {
                if (WhatsNewManager.shouldShowWhatsNew(context)) {
                    showWhatsNewDialog = true
                }
            }

            MyApplicationTheme(
                themeMode = themeMode,
                accentColor = accentColor
            ) {
                // Safety watchdog: ensure opening animation is guaranteed to dismiss quickly (450ms)
                LaunchedEffect(showOpeningAnimation, isOpeningAnimationEnabled) {
                    if (showOpeningAnimation && isOpeningAnimationEnabled) {
                        kotlinx.coroutines.delay(450L)
                        viewModel.dismissOpeningAnimation()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(viewModel = viewModel)

                    if (showWhatsNewDialog) {
                        WhatsNewDialog(
                            onDismiss = {
                                WhatsNewManager.markWhatsNewAsSeen(context)
                                showWhatsNewDialog = false
                            }
                        )
                    }

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
        if ((viewModel.activeVideoId.value != null || com.example.ui.player.GlobalPlayerManager.hasLoadedMedia()) &&
            com.example.ui.player.GlobalPlayerManager.isPlaying.value) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                        val params = buildPipParams()
                        if (params != null) {
                            enterPictureInPictureMode(params)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Stop background audio notification and restore video screen when returning to the app
        com.example.service.BackgroundAudioService.stop(this)
        com.example.ui.player.GlobalPlayerManager.setBackgroundAudioOnly(false)
        viewModel.syncWithGlobalPlayer()
        updatePipParams()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipActionReceiver)
        } catch (_: Exception) {}
        // If dismissed/swiped away from PiP (not audio-only headphone mode), clean up player
        if (!com.example.ui.player.GlobalPlayerManager.isBackgroundAudioOnly.value) {
            com.example.ui.player.GlobalPlayerManager.stopAndClear()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            updatePipParams()
        }
    }

    private fun handlePipHeadphonesAction() {
        // Switch to audio-only mode: video PiP disappears, audio keeps playing
        com.example.ui.player.GlobalPlayerManager.setBackgroundAudioOnly(true)
        com.example.service.BackgroundAudioService.start(this)
        // Dismiss the PiP window so the home screen is clear
        finish()
    }

    private fun buildPipParams(): android.app.PictureInPictureParams? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return null
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return null

        val builder = android.app.PictureInPictureParams.Builder()

        // 1. Aspect Ratio
        try {
            val aspect = com.example.ui.player.GlobalPlayerManager.videoAspectRatio.value
            val clampedAspect = if (aspect in 0.418410f..2.390000f) aspect else (16f / 9f)
            val rational = android.util.Rational((clampedAspect * 1000).toInt(), 1000)
            builder.setAspectRatio(rational)
        } catch (_: Exception) {
            builder.setAspectRatio(android.util.Rational(16, 9))
        }

        // 2. PiP Actions: Headphones (🎧 audio-only outside app), Play/Pause, Next
        val actions = ArrayList<android.app.RemoteAction>()

        // Action 1: Headphone 🎧 icon (Audio-only background mode)
        val headphonesIntent = Intent(ACTION_PIP_HEADPHONES).setPackage(packageName)
        val headphonesPendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            101,
            headphonesIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val headphonesIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pip_headphones)
        val headphonesAction = android.app.RemoteAction(
            headphonesIcon,
            "Audio Only",
            "Listen in background (audio only)",
            headphonesPendingIntent
        )
        actions.add(headphonesAction)

        // Action 2: Play/Pause toggle
        val isPlaying = com.example.ui.player.GlobalPlayerManager.isPlaying.value
        val playPauseIntent = Intent(ACTION_PIP_PLAY_PAUSE).setPackage(packageName)
        val playPausePendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            102,
            playPauseIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = if (isPlaying) {
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pip_pause)
        } else {
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pip_play)
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseAction = android.app.RemoteAction(
            playPauseIcon,
            playPauseTitle,
            playPauseTitle,
            playPausePendingIntent
        )
        actions.add(playPauseAction)

        // Action 3: Next track
        val nextIntent = Intent(ACTION_PIP_NEXT).setPackage(packageName)
        val nextPendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            103,
            nextIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val nextIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pip_next)
        val nextAction = android.app.RemoteAction(
            nextIcon,
            "Next",
            "Next video",
            nextPendingIntent
        )
        actions.add(nextAction)

        builder.setActions(actions)

        // Auto-enter PiP on Android 12+ (S)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val canAutoEnter = (viewModel.activeVideoId.value != null || com.example.ui.player.GlobalPlayerManager.hasLoadedMedia())
            builder.setAutoEnterEnabled(canAutoEnter)
        }

        return builder.build()
    }

    private fun updatePipParams() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val params = buildPipParams()
                if (params != null) {
                    setPictureInPictureParams(params)
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val ACTION_PIP_HEADPHONES = "com.example.action.PIP_HEADPHONES"
        const val ACTION_PIP_PLAY_PAUSE = "com.example.action.PIP_PLAY_PAUSE"
        const val ACTION_PIP_NEXT = "com.example.action.PIP_NEXT"
    }

    private val pipActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                ACTION_PIP_HEADPHONES -> {
                    handlePipHeadphonesAction()
                }
                ACTION_PIP_PLAY_PAUSE -> {
                    com.example.ui.player.GlobalPlayerManager.togglePlayPause()
                    updatePipParams()
                }
                ACTION_PIP_NEXT -> {
                    com.example.ui.player.GlobalPlayerManager.playNext()
                    updatePipParams()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
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



