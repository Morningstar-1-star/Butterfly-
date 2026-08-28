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
     * - Rewrites heavy 1080p `maxresdefault.jpg` (400KB+) or `hq720.jpg` to ultra-fast `mqdefault.jpg` (~10-15KB).
     * - Rewrites heavy TMDB 4K/Original/w500 poster images to lightweight `w185` (~12KB).
     * - Rewrites Unsplash full-res queries to optimized `w=360&q=65&auto=format`.
     * - Rewrites Dailymotion 720p/1080p thumbnails to 240p/360p.
     */
    fun getOptimizedThumbnailUrl(rawUrl: String?, preferCompact: Boolean = false): String? {
        if (rawUrl.isNullOrBlank()) return null
        var trimmed = rawUrl.trim()
        if (trimmed.startsWith("//")) {
            trimmed = "https:$trimmed"
        }

        // 1. Optimize YouTube thumbnails (use ultra-lightweight mqdefault.jpg ~10-15KB for instant loading)
        if (trimmed.contains("i.ytimg.com") || trimmed.contains("img.youtube.com")) {
            val vIdPattern = java.util.regex.Pattern.compile("/(vi|vi_webp)/([a-zA-Z0-9_-]{11})/")
            val matcher = vIdPattern.matcher(trimmed)
            if (matcher.find()) {
                val vId = matcher.group(2)
                if (!vId.isNullOrBlank()) {
                    return "https://i.ytimg.com/vi/$vId/mqdefault.jpg"
                }
            }
            if (trimmed.contains("/maxresdefault.")) return trimmed.replace(Regex("/maxresdefault\\.[a-z]+.*"), "/mqdefault.jpg")
            if (trimmed.contains("/sddefault.")) return trimmed.replace(Regex("/sddefault\\.[a-z]+.*"), "/mqdefault.jpg")
            if (trimmed.contains("/hqdefault.")) return trimmed.replace(Regex("/hqdefault\\.[a-z]+.*"), "/mqdefault.jpg")
            if (trimmed.contains("/hq720.")) return trimmed.replace(Regex("/hq720\\.[a-z]+.*"), "/mqdefault.jpg")
            return trimmed
        }

        // 2. Optimize TMDB (The Movie Database) poster & backdrop images
        if (trimmed.contains("image.tmdb.org/t/p/")) {
            val targetSize = "w185"
            return trimmed
                .replace("/original/", "/$targetSize/")
                .replace("/w1280/", "/$targetSize/")
                .replace("/w780/", "/$targetSize/")
                .replace("/w500/", "/$targetSize/")
                .replace("/w342/", "/$targetSize/")
        }

        // 3. Optimize Unsplash dynamic images
        if (trimmed.contains("images.unsplash.com")) {
            return if (trimmed.contains("w=")) {
                trimmed.replace(Regex("w=\\d+"), "w=360")
            } else {
                "$trimmed&w=360&q=65&auto=format"
            }
        }

        // 4. Optimize Dailymotion thumbnails (downscale to fast 240p/360p)
        if (trimmed.contains("dailymotion.com/thumbnail/")) {
            if (trimmed.contains("thumbnail_720_url") || trimmed.contains("/720") || trimmed.contains("thumbnail_1080_url") || trimmed.contains("/1080")) {
                return trimmed
                    .replace("/1080", "/360")
                    .replace("/720", "/360")
                    .replace("thumbnail_1080_url", "thumbnail_360_url")
                    .replace("thumbnail_720_url", "thumbnail_360_url")
            }
        }

        // 5. Optimize Vimeo thumbnails
        if (trimmed.contains("vimeocdn.com")) {
            if (trimmed.contains("_640") || trimmed.contains("_960") || trimmed.contains("_1280")) {
                return trimmed.replace(Regex("_\\d+x?\\d*"), "_320")
            }
        }

        return trimmed
    }

    /**
     * Build an optimized ImageRequest with aggressive memory + disk caching,
     * downsampled 480x270 decode size, low-memory RGB_565 bitmap config for low RAM overhead,
     * rapid native decoding, and provider-appropriate headers to prevent hotlinking 403 blocks.
     */
    fun buildThumbnailRequest(
        context: Context,
        url: String?,
        crossfadeMillis: Int = 120,
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
            .size(coil.size.Size(width = 480, height = 270))
            .precision(coil.size.Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(true)
            .allowRgb565(true)
            .crossfade(crossfadeMillis)
            .dispatcher(Dispatchers.IO)
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")

        // Domain-specific anti-hotlinking headers
        when {
            lowerUrl.contains("externulls.com") || lowerUrl.contains("beeg.com") -> {
                builder.setHeader("Referer", "https://beeg.com/")
                builder.setHeader("Origin", "https://beeg.com")
            }
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
                builder.setHeader("Cookie", "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1")
            }
            lowerUrl.contains("xvideos.com") || lowerUrl.contains("xv-cdn.com") || lowerUrl.contains("xvideos-cdn.com") -> {
                builder.setHeader("Referer", "https://www.xvideos.com/")
            }
            lowerUrl.contains("xhamster.com") || lowerUrl.contains("xhcdn.com") -> {
                builder.setHeader("Referer", "https://xhamster.com/")
            }
            lowerUrl.contains("redtube.com") || lowerUrl.contains("rdtcdn.com") -> {
                builder.setHeader("Referer", "https://www.redtube.com/")
            }
            lowerUrl.contains("youporn.com") || lowerUrl.contains("ypncdn.com") -> {
                builder.setHeader("Referer", "https://www.youporn.com/")
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
                    val request = buildThumbnailRequest(context, video.thumbnailUrl, crossfadeMillis = 0, preferCompact = true)
                    if (request != null) {
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
                urls.filterNotNull().take(maxCount).forEach { rawUrl ->
                    val request = buildThumbnailRequest(context, rawUrl, crossfadeMillis = 0, preferCompact = true)
                    if (request != null) {
                        imageLoader.enqueue(request)
                    }
                }
            } catch (ignored: Exception) {
                // Ignore background prefetch errors gracefully
            }
        }
    }
}

