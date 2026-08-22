package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * High-performance thumbnail rendering & preloading optimizer.
 * Designed to deliver instantaneous, flicker-free thumbnail rendering during fast scrolling
 * across all providers (YouTube, TMDB, Dailymotion, Unsplash, Jikan, etc.)
 * by using smart URL downscaling, rapid RGB_565 bitmap decoding, and proactive parallel preloading.
 */
object ThumbnailOptimizer {

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Optimizes thumbnail URL resolution for instant network loading:
     * - Rewrites heavy 1080p `maxresdefault.jpg` (400KB+) or `hq720.jpg` to lightweight `hqdefault.jpg` (~25KB).
     * - Rewrites heavy TMDB 4K/Original/w500 poster images to lightweight `w342` or `w185` (~15-30KB).
     * - Rewrites Unsplash full-res queries to optimized `w=360&q=75&auto=format`.
     * - Rewrites Dailymotion 720p/1080p thumbnails to 360p.
     */
    fun getOptimizedThumbnailUrl(rawUrl: String?, preferCompact: Boolean = false): String? {
        if (rawUrl.isNullOrBlank()) return null
        var trimmed = rawUrl.trim()
        if (trimmed.startsWith("//")) {
            trimmed = "https:$trimmed"
        }

        // 1. Optimize YouTube thumbnails (use reliable mqdefault.jpg for compact or hqdefault.jpg)
        if (trimmed.contains("i.ytimg.com") || trimmed.contains("img.youtube.com")) {
            val vIdPattern = java.util.regex.Pattern.compile("/(vi|vi_webp)/([a-zA-Z0-9_-]{11})/")
            val matcher = vIdPattern.matcher(trimmed)
            if (matcher.find()) {
                val vId = matcher.group(2)
                if (!vId.isNullOrBlank()) {
                    val quality = if (preferCompact) "mqdefault" else "hqdefault"
                    return "https://i.ytimg.com/vi/$vId/$quality.jpg"
                }
            }
            if (preferCompact) {
                if (trimmed.contains("/maxresdefault.")) return trimmed.replace(Regex("/maxresdefault\\.[a-z]+.*"), "/mqdefault.jpg")
                if (trimmed.contains("/sddefault.")) return trimmed.replace(Regex("/sddefault\\.[a-z]+.*"), "/mqdefault.jpg")
                if (trimmed.contains("/hqdefault.")) return trimmed.replace(Regex("/hqdefault\\.[a-z]+.*"), "/mqdefault.jpg")
                if (trimmed.contains("/hq720.")) return trimmed.replace(Regex("/hq720\\.[a-z]+.*"), "/mqdefault.jpg")
            } else {
                if (trimmed.contains("/maxresdefault.")) return trimmed.replace(Regex("/maxresdefault\\.[a-z]+.*"), "/hqdefault.jpg")
                if (trimmed.contains("/sddefault.")) return trimmed.replace(Regex("/sddefault\\.[a-z]+.*"), "/hqdefault.jpg")
                if (trimmed.contains("/hq720.")) return trimmed.replace(Regex("/hq720\\.[a-z]+.*"), "/hqdefault.jpg")
            }
            return trimmed
        }

        // 2. Optimize TMDB (The Movie Database) poster & backdrop images
        if (trimmed.contains("image.tmdb.org/t/p/")) {
            val targetSize = if (preferCompact) "w185" else "w342"
            return trimmed
                .replace("/original/", "/$targetSize/")
                .replace("/w1280/", "/$targetSize/")
                .replace("/w780/", "/$targetSize/")
                .replace("/w500/", "/$targetSize/")
        }

        // 3. Optimize Unsplash dynamic images
        if (trimmed.contains("images.unsplash.com")) {
            return if (trimmed.contains("w=")) {
                trimmed.replace(Regex("w=\\d+"), if (preferCompact) "w=200" else "w=400")
            } else {
                "$trimmed&w=400&q=75&auto=format"
            }
        }

        // 4. Optimize Dailymotion thumbnails
        if (trimmed.contains("dailymotion.com/thumbnail/")) {
            if (trimmed.contains("thumbnail_720_url") || trimmed.contains("/720")) {
                return trimmed.replace("/720", "/360").replace("thumbnail_720_url", "thumbnail_360_url")
            }
        }

        return trimmed
    }

    /**
     * Build an optimized ImageRequest with aggressive memory + disk caching,
     * low-memory RGB_565 bitmap config for low RAM overhead, rapid decoding,
     * and provider-appropriate headers (User-Agent, Referer, Accept) to prevent hotlinking 403 blocks.
     */
    fun buildThumbnailRequest(
        context: Context,
        url: String?,
        crossfadeMillis: Int = 80,
        preferCompact: Boolean = false
    ): ImageRequest? {
        val optimizedUrl = getOptimizedThumbnailUrl(url, preferCompact = preferCompact) ?: return null
        val lowerUrl = optimizedUrl.lowercase()

        val builder = ImageRequest.Builder(context)
            .data(optimizedUrl)
            .memoryCacheKey(optimizedUrl)
            .diskCacheKey(optimizedUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .bitmapConfig(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            .allowRgb565(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            .crossfade(crossfadeMillis)
            .dispatcher(Dispatchers.IO)
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")

        // Domain-specific anti-hotlinking headers
        when {
            lowerUrl.contains("apijav") || lowerUrl.contains("server.apijav") || lowerUrl.contains("hentai.apijav") -> {
                builder.setHeader("Referer", "https://apijav.com/")
            }
            lowerUrl.contains("dmm.co.jp") || lowerUrl.contains("pics.dmm") -> {
                builder.setHeader("Referer", "https://www.dmm.co.jp/")
                builder.setHeader("Cookie", "age_check_done=1")
            }
            lowerUrl.contains("r18.com") -> {
                builder.setHeader("Referer", "https://www.r18.com/")
            }
            lowerUrl.contains("javlibrary.com") -> {
                builder.setHeader("Referer", "https://www.javlibrary.com/")
            }
            lowerUrl.contains("javdb.com") -> {
                builder.setHeader("Referer", "https://javdb.com/")
            }
            lowerUrl.contains("javbus.com") -> {
                builder.setHeader("Referer", "https://www.javbus.com/")
            }
            lowerUrl.contains("eporner.com") || lowerUrl.contains("static-web.eporner") -> {
                builder.setHeader("Referer", "https://www.eporner.com/")
            }
            lowerUrl.contains("pornhub.com") || lowerUrl.contains("phncdn.com") -> {
                builder.setHeader("Referer", "https://www.pornhub.com/")
                builder.setHeader("Cookie", "age_verified=1")
            }
            lowerUrl.contains("rule34video.com") || lowerUrl.contains("r34v.com") -> {
                builder.setHeader("Referer", "https://rule34video.com/")
            }
            lowerUrl.contains("archive.org") -> {
                builder.setHeader("Referer", "https://archive.org/")
            }
            lowerUrl.contains("dailymotion.com") || lowerUrl.contains("dmcdn.net") -> {
                builder.setHeader("Referer", "https://www.dailymotion.com/")
            }
            lowerUrl.contains("hotstar.com") || lowerUrl.contains("hotstar-cdn") || lowerUrl.contains("jiohotstar") -> {
                builder.setHeader("Referer", "https://www.hotstar.com/")
            }
        }

        return builder.build()
    }

    /**
     * Preloads a batch of thumbnail URLs in parallel into Coil's RAM & disk cache
     * before the user scrolls to them, eliminating scrolling pop-in, blank boxes, and stutter.
     */
    fun preloadThumbnails(context: Context, videos: List<VideoItem>, maxCount: Int = 24) {
        if (videos.isEmpty()) return
        val imageLoader = Coil.imageLoader(context)

        preloadScope.launch {
            try {
                videos.take(maxCount).forEach { video ->
                    val url = getOptimizedThumbnailUrl(video.thumbnailUrl)
                    if (!url.isNullOrBlank()) {
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .memoryCacheKey(url)
                            .diskCacheKey(url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .bitmapConfig(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
                            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            .allowRgb565(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                            .dispatcher(Dispatchers.IO)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            } catch (ignored: Exception) {
                // Ignore background prefetch errors gracefully
            }
        }
    }

    /**
     * Preloads a list of raw thumbnail URLs directly into cache.
     */
    fun preloadUrls(context: Context, urls: List<String?>, maxCount: Int = 20) {
        if (urls.isEmpty()) return
        val imageLoader = Coil.imageLoader(context)

        preloadScope.launch {
            try {
                urls.filterNotNull().filter { it.isNotBlank() }.take(maxCount).forEach { rawUrl ->
                    val optimized = getOptimizedThumbnailUrl(rawUrl) ?: rawUrl
                    val request = ImageRequest.Builder(context)
                        .data(optimized)
                        .memoryCacheKey(optimized)
                        .diskCacheKey(optimized)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .bitmapConfig(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
                        .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        .allowRgb565(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                        .dispatcher(Dispatchers.IO)
                        .build()
                    imageLoader.enqueue(request)
                }
            } catch (ignored: Exception) {
            }
        }
    }
}

