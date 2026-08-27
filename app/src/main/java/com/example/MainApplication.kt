package com.example

import android.app.Application
import android.graphics.Bitmap
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

        com.example.util.SecureDnsManager.init(this)
        com.example.util.GoogleDriveSyncManager.init(this)

        // Configure optimized Coil ImageLoader with moderate concurrency and low memory footprint
        val imageOkHttpClient = okhttp3.OkHttpClient.Builder()
            .dns(com.example.util.SecureDnsManager.appDns)
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 24
                maxRequestsPerHost = 8
            })
            .connectionPool(okhttp3.ConnectionPool(16, 3, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val urlStr = originalRequest.url.toString().lowercase()
                val requestBuilder = originalRequest.newBuilder()

                // Standard desktop user agent for all thumbnail CDN fetches
                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                requestBuilder.header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                requestBuilder.header("Sec-Fetch-Dest", "image")
                requestBuilder.header("Sec-Fetch-Mode", "no-cors")
                requestBuilder.header("Sec-Fetch-Site", "cross-site")

                when {
                    urlStr.contains("externulls.com") || urlStr.contains("beeg.com") -> {
                        requestBuilder.header("Referer", "https://beeg.com/")
                        requestBuilder.header("Origin", "https://beeg.com")
                    }
                    urlStr.contains("phncdn.com") || urlStr.contains("pornhub.com") -> {
                        requestBuilder.header("Referer", "https://www.pornhub.com/")
                        requestBuilder.header("Origin", "https://www.pornhub.com")
                        requestBuilder.header("Cookie", "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1")
                    }
                    urlStr.contains("rdtcdn.com") || urlStr.contains("redtube.com") -> {
                        requestBuilder.header("Referer", "https://www.redtube.com/")
                        requestBuilder.header("Origin", "https://www.redtube.com")
                    }
                    urlStr.contains("xvideos.com") || urlStr.contains("xv-cdn.com") || urlStr.contains("xvideos-cdn.com") -> {
                        requestBuilder.header("Referer", "https://www.xvideos.com/")
                    }
                    urlStr.contains("xhamster.com") || urlStr.contains("xhcdn.com") || urlStr.contains("xhcdn.net") || urlStr.contains("xhamster.desi") -> {
                        requestBuilder.header("Referer", "https://xhamster.com/")
                        requestBuilder.header("Origin", "https://xhamster.com")
                    }
                    urlStr.contains("4tube.com") || urlStr.contains("f-cdn.com") || urlStr.contains("ttcache.com") -> {
                        requestBuilder.header("Referer", "https://www.4tube.com/")
                        requestBuilder.header("Origin", "https://www.4tube.com")
                    }
                    urlStr.contains("eporner.com") || urlStr.contains("static-cluster") || urlStr.contains("static-sg-cdn") -> {
                        requestBuilder.header("Referer", "https://www.eporner.com/")
                    }
                    urlStr.contains("youporn.com") || urlStr.contains("ypncdn.com") -> {
                        requestBuilder.header("Referer", "https://www.youporn.com/")
                        requestBuilder.header("Cookie", "age_verified=1; platform=pc; premium_redirect_cookie=1")
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
                    .maxSizePercent(0.25) // 25% RAM cache for instant back-and-forth scroll reuse
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache_v3"))
                    .maxSizeBytes(120L * 1024L * 1024L) // 120 MB dedicated disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(true)
            .allowRgb565(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(0) // 0ms crossfade eliminates composable animation lag on list scroll
            .build()
        Coil.setImageLoader(imageLoader)

        // Asynchronously initialize yt-dlp engine and sys.path without blocking main UI or downloading on launch
        applicationScope.launch(Dispatchers.IO) {
            try {
                dev.ffmpegkit_maintained.ytdlp.YtDlp.init(this@MainApplication)
                com.example.extractor.YtDlpUpdateManager.injectUpdatedPathIntoPython(this@MainApplication)
                Log.i("MainApplication", "yt-dlp engine initialized successfully")
            } catch (e: Throwable) {
                Log.w("MainApplication", "yt-dlp init note: ${e.message}")
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

