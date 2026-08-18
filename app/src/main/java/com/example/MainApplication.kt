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

    companion object {
        lateinit var appContext: Application
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appContext = this

        // Configure ultra high-performance Coil ImageLoader with parallel OkHttp throughput
        val imageOkHttpClient = okhttp3.OkHttpClient.Builder()
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

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache_v2"))
                    .maxSizeBytes(500L * 1024L * 1024L) // 500 MB dedicated disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Cache regardless of server max-age headers
            .bitmapConfig(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) android.graphics.Bitmap.Config.ARGB_8888 else android.graphics.Bitmap.Config.RGB_565)
            .allowHardware(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            .allowRgb565(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(80)
            .build()
        Coil.setImageLoader(imageLoader)

        // Pre-warm yt-dlp asynchronously on app startup so video extraction starts instantly
        applicationScope.launch {
            try {
                Log.d("MainApplication", "Pre-warming YtDlpResolver...")
                YtDlpResolver.prewarm(this@MainApplication)
                Log.d("MainApplication", "YtDlpResolver pre-warmed successfully")
            } catch (e: Throwable) {
                Log.e("MainApplication", "Error pre-warming YtDlpResolver", e)
            }
        }
    }
}

