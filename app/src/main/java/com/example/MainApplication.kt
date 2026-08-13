package com.example

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.extractor.YtDlpResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Configure high-performance Coil ImageLoader for 120 FPS smooth scrolling
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024L * 1024L) // 100 MB
                    .build()
            }
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(150)
            .build()
        Coil.setImageLoader(imageLoader)

        // Pre-warm yt-dlp asynchronously on app startup so video extraction starts instantly
        applicationScope.launch {
            try {
                Log.d("MainApplication", "Pre-warming YtDlpResolver...")
                YtDlpResolver.init(this@MainApplication)
                Log.d("MainApplication", "YtDlpResolver pre-warmed successfully")
            } catch (e: Throwable) {
                Log.e("MainApplication", "Error pre-warming YtDlpResolver", e)
            }
        }
    }
}

