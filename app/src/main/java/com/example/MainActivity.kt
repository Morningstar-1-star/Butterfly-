package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.repository.SyncRepository.init(applicationContext)
        enableEdgeToEdge()
        setupHighRefreshRate()
        setupCoilCache()

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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.activeVideoId.value != null && com.example.ui.player.GlobalPlayerManager.isPlaying.value) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
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

    private fun setupCoilCache() {
        try {
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .dispatcher(okhttp3.Dispatcher().apply {
                    maxRequests = 128
                    maxRequestsPerHost = 32
                })
                .connectionPool(okhttp3.ConnectionPool(32, 5, java.util.concurrent.TimeUnit.MINUTES))
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val imageLoader = ImageLoader.Builder(applicationContext)
                .okHttpClient(okHttpClient)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.35)
                        .strongReferencesEnabled(true)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(applicationContext.cacheDir.resolve("image_cache_v2"))
                        .maxSizeBytes(500L * 1024 * 1024) // 500MB dedicated disk cache
                        .build()
                }
                .respectCacheHeaders(false)
                .bitmapConfig(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) android.graphics.Bitmap.Config.ARGB_8888 else android.graphics.Bitmap.Config.RGB_565)
                .allowHardware(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                .allowRgb565(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .crossfade(80)
                .build()
            Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            // Optional
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



