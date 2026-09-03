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

        com.example.util.AppConfig.init(this)
        com.example.util.SecureDnsManager.init(this)
        com.example.util.GoogleDriveSyncManager.init(this)

        // Configure YouTube Proof-of-Origin Token Provider for NewPipe extractor
        com.example.extractor.YouTubeExtractorHelper.setPoTokenProvider(object : com.example.extractor.YouTubeExtractorHelper.CustomPoTokenProvider {
            private val httpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            override fun getPoToken(visitorData: String?): String? {
                // 1. Check user-configured static custom token first
                val customToken = com.example.util.AppConfig.getCustomPoToken()
                if (customToken.isNotBlank()) return customToken

                // 2. Fetch from PO token generation server if configured
                val serverUrl = com.example.util.AppConfig.getPoTokenServerUrl().ifBlank { BuildConfig.PO_TOKEN_SERVER_URL }
                if (serverUrl.isBlank() || serverUrl.contains(".local")) return null

                return try {
                    val query = if (visitorData.isNullOrBlank()) "" else "?visitor_data=$visitorData"
                    val url = "${serverUrl.trimEnd('/')}/get_pot$query"
                    val req = okhttp3.Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: return null
                            val json = org.json.JSONObject(bodyStr)
                            json.optString("po_token", null)?.ifBlank { null }
                                ?: json.optString("potoken", null)?.ifBlank { null }
                        } else null
                    }
                } catch (e: Exception) {
                    Log.w("MainApplication", "PO token server request note: ${e.message}")
                    null
                }
            }
        })

        // Configure high-performance Coil ImageLoader with high concurrency & large RAM/disk cache
        val imageOkHttpClient = okhttp3.OkHttpClient.Builder()
            .dns(com.example.util.SecureDnsManager.appDns)
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 24
            })
            .connectionPool(okhttp3.ConnectionPool(32, 5, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
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
                    .maxSizePercent(0.35) // 35% RAM cache for instant back-and-forth scroll reuse
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache_v4"))
                    .maxSizeBytes(250L * 1024L * 1024L) // 250 MB dedicated disk cache
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

        // Note: yt-dlp engine is lazily initialized on-demand via YtDlpResolver.ensureInitialized(context)
        // when an extraction stream actually requests it, avoiding cold-start CPU spikes and SELinux audit rate limits.
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

