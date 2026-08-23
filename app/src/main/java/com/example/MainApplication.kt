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

        // Configure ultra high-performance Coil ImageLoader with parallel OkHttp throughput and domain-specific headers
        val imageOkHttpClient = okhttp3.OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 128
                maxRequestsPerHost = 32
            })
            .connectionPool(okhttp3.ConnectionPool(32, 5, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val urlStr = originalRequest.url.toString().lowercase()
                val requestBuilder = originalRequest.newBuilder()

                // Standard desktop user agent for all thumbnail CDN fetches
                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

                when {
                    urlStr.contains("externulls.com") || urlStr.contains("beeg.com") -> {
                        requestBuilder.header("Referer", "https://beeg.com/")
                        requestBuilder.header("Origin", "https://beeg.com")
                    }
                    urlStr.contains("phncdn.com") || urlStr.contains("pornhub.com") -> {
                        requestBuilder.header("Referer", "https://www.pornhub.com/")
                        requestBuilder.header("Origin", "https://www.pornhub.com")
                        requestBuilder.header("Cookie", "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US")
                    }
                    urlStr.contains("xvideos.com") || urlStr.contains("xv-cdn.com") -> {
                        requestBuilder.header("Referer", "https://www.xvideos.com/")
                    }
                    urlStr.contains("xhamster.com") || urlStr.contains("xhcdn.com") -> {
                        requestBuilder.header("Referer", "https://xhamster.com/")
                    }
                    urlStr.contains("eporner.com") || urlStr.contains("static-cluster") -> {
                        requestBuilder.header("Referer", "https://www.eporner.com/")
                    }
                    urlStr.contains("youporn.com") || urlStr.contains("ypncdn.com") -> {
                        requestBuilder.header("Referer", "https://www.youporn.com/")
                    }
                    urlStr.contains("rule34video.com") -> {
                        requestBuilder.header("Referer", "https://rule34video.com/")
                    }
                    urlStr.contains("bilibili.com") || urlStr.contains("hdslb.com") || urlStr.contains("bilivideo.com") -> {
                        requestBuilder.header("Referer", "https://www.bilibili.com/")
                    }
                    urlStr.contains("hotstar.com") || urlStr.contains("hotstar-cdn") || urlStr.contains("starott.com") -> {
                        requestBuilder.header("Referer", "https://www.hotstar.com/")
                    }
                }

                chain.proceed(requestBuilder.build())
            }
            .build()

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache_v2"))
                    .maxSizeBytes(250L * 1024L * 1024L) // 250 MB dedicated disk cache
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

        // Initialize yt-dlp engine
        try {
            dev.ffmpegkit_maintained.ytdlp.YtDlp.init(this)
            Log.i("MainApplication", "yt-dlp engine initialized successfully")
        } catch (e: Throwable) {
            Log.w("MainApplication", "yt-dlp init note: ${e.message}")
        }

        // Pre-warm yt-dlp asynchronously and check for background engine updates on app startup
        applicationScope.launch {
            try {
                Log.d("MainApplication", "Pre-warming YtDlpResolver...")
                YtDlpResolver.prewarm(this@MainApplication)
                Log.d("MainApplication", "YtDlpResolver pre-warmed successfully")

                // Background automatic update check
                if (com.example.extractor.YtDlpUpdateManager.isAutoUpdateEnabled.value) {
                    com.example.extractor.YtDlpUpdateManager.checkForUpdates(this@MainApplication, isManual = false)
                }
            } catch (e: Throwable) {
                Log.e("MainApplication", "Error during yt-dlp startup routine", e)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            Coil.imageLoader(this).memoryCache?.let { memCache ->
                memCache.trimMemory(level)
            }
        } catch (e: Exception) {
            Log.w("MainApplication", "Error trimming memory cache: ${e.message}")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            Coil.imageLoader(this).memoryCache?.clear()
        } catch (e: Exception) {
            Log.w("MainApplication", "Error clearing memory cache on low memory: ${e.message}")
        }
    }
}

