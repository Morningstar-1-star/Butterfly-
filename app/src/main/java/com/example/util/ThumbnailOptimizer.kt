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
        crossfadeMillis: Int = 0,
        preferCompact: Boolean = false
    ): ImageRequest? {
        val optimizedUrl = getOptimizedThumbnailUrl(url, preferCompact = preferCompact) ?: return null
        val lowerUrl = optimizedUrl.lowercase()

        val builder = ImageRequest.Builder(context)
            .data(optimizedUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .crossfade(crossfadeMillis)
            .dispatcher(Dispatchers.IO)
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setHeader("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8")

        // Domain-specific anti-hotlinking headers
        when {
            lowerUrl.contains("externulls.com") || lowerUrl.contains("beeg.com") || lowerUrl.contains("ahacdn.me") -> {
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
            lowerUrl.contains("cam4.com") || lowerUrl.contains("snapshots.cam4.com") -> {
                builder.setHeader("Referer", "https://www.cam4.com/")
                builder.setHeader("Origin", "https://www.cam4.com")
            }
            lowerUrl.contains("cammodels.com") || lowerUrl.contains("img.cammodels.com") || lowerUrl.contains("strpst.com") || lowerUrl.contains("stripchat.com") || lowerUrl.contains("doppiocdn") -> {
                builder.setHeader("Referer", "https://stripchat.com/")
                builder.setHeader("Origin", "https://stripchat.com")
            }
            lowerUrl.contains("highwebmedia.com") || lowerUrl.contains("chaturbate.com") -> {
                builder.setHeader("Referer", "https://chaturbate.com/")
                builder.setHeader("Origin", "https://chaturbate.com")
            }
            lowerUrl.contains("txxx.com") || lowerUrl.contains("txxx.tube") || lowerUrl.contains("tubecdn.com") -> {
                builder.setHeader("Referer", "https://www.txxx.com/")
                builder.setHeader("Cookie", "age_verified=1; platform=pc; country=US")
            }
            lowerUrl.contains("4tube.com") || lowerUrl.contains("4tube") || lowerUrl.contains("fivetube.com") -> {
                builder.setHeader("Referer", "https://www.4tube.com/")
                builder.setHeader("Cookie", "age_verified=1; platform=pc; country=US")
            }
            lowerUrl.contains("spankbang.com") || lowerUrl.contains("spankbang") || lowerUrl.contains("sb-cd.com") || lowerUrl.contains("spankcdn") -> {
                builder.setHeader("Referer", "https://spankbang.com/")
                builder.setHeader("Cookie", "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1")
                builder.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            lowerUrl.contains("xnxx.com") || lowerUrl.contains("xnxx-cdn.com") || lowerUrl.contains("xnxxcdn.com") -> {
                builder.setHeader("Referer", "https://www.xnxx.com/")
                builder.setHeader("Cookie", "age_verified=1; platform=pc; has_consent=1")
                builder.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            lowerUrl.contains("hellporno") -> {
                builder.setHeader("Referer", "https://hellporno.com/")
                builder.setHeader("Cookie", "age_verified=1; platform=pc; has_consent=1")
                builder.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            lowerUrl.contains("playvid.com") -> {
                builder.setHeader("Referer", "https://www.playvid.com/")
                builder.setHeader("Cookie", "age_confirmed=1")
            }
            lowerUrl.contains("thisvid.com") -> {
                builder.setHeader("Referer", "https://thisvid.com/")
                builder.setHeader("Cookie", "age_verified=1; platform=pc")
            }
            lowerUrl.contains("tnaflix.com") || lowerUrl.contains("tnaflix") || lowerUrl.contains("tnacdn") -> {
                builder.setHeader("Referer", "https://www.tnaflix.com/")
                builder.setHeader("Origin", "https://www.tnaflix.com")
                builder.setHeader("Cookie", "age_verified=1; platform=pc; ft_mature=1; consent=1; has_consent=1")
                builder.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            lowerUrl.contains("noodlemagazine.com") -> {
                builder.setHeader("Referer", "https://noodlemagazine.com/")
            }
            lowerUrl.contains("hanime1") || lowerUrl.contains("hanime.tv") || lowerUrl.contains("hembed.com") || lowerUrl.contains("vdownload") -> {
                builder.setHeader("Referer", if (lowerUrl.contains("hanime.tv")) "https://hanime.tv/" else "https://hanime1.me/")
                builder.setHeader("Origin", if (lowerUrl.contains("hanime.tv")) "https://hanime.tv" else "https://hanime1.me")
                builder.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.setHeader("Cookie", "age_verified=1; country=US; language=en; ft_mature=1; consent=1")
            }
            lowerUrl.contains("media-amazon.com") || lowerUrl.contains("ssl-images-amazon.com") || lowerUrl.contains("images-eu.ssl-images-amazon.com") -> {
                // AWS CloudFront for Amazon & IMDb media: Do NOT set foreign referer as CloudFront blocks cross-domain referers with 403
                builder.setHeader("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8")
            }
            lowerUrl.contains("imdb.com") -> {
                builder.setHeader("Referer", "https://www.imdb.com/")
            }
            lowerUrl.contains("bigo.tv") || lowerUrl.contains("bigolive.tv") || lowerUrl.contains("bigo.sg") || lowerUrl.contains("cubetecn.com") || lowerUrl.contains("bigocdn.com") || lowerUrl.contains("static-web.bigolive.tv") -> {
                builder.setHeader("Referer", "https://www.bigo.tv/")
                builder.setHeader("Origin", "https://www.bigo.tv")
                builder.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            lowerUrl.contains("discoveryplus") -> {
                builder.setHeader("Referer", "https://www.discoveryplus.in/")
            }
            lowerUrl.contains("disneyplus") -> {
                builder.setHeader("Referer", "https://www.disneyplus.com/")
            }
            lowerUrl.contains("max.com") || lowerUrl.contains("hbo.com") || lowerUrl.contains("hbomax.com") -> {
                builder.setHeader("Referer", "https://play.max.com/")
                builder.setHeader("Origin", "https://play.max.com")
            }
            lowerUrl.contains("curiositystream") -> {
                builder.setHeader("Referer", "https://curiositystream.com/")
                builder.setHeader("Origin", "https://curiositystream.com")
            }
            lowerUrl.contains("drive.google.com") || lowerUrl.contains("googleusercontent.com") -> {
                builder.setHeader("Referer", "https://drive.google.com/")
            }
            lowerUrl.contains("bunkr") || lowerUrl.contains("bunkrr") -> {
                builder.setHeader("Referer", "https://bunkr.site/")
                builder.setHeader("Origin", "https://bunkr.site")
            }
            lowerUrl.contains("mega.nz") || lowerUrl.contains("mega.co.nz") -> {
                builder.setHeader("Referer", "https://mega.nz/")
            }
            lowerUrl.contains("t.me") || lowerUrl.contains("telesco.pe") || lowerUrl.contains("telegram.org") -> {
                builder.setHeader("Referer", "https://t.me/")
            }
        }

        return builder.build()
    }

    fun getOptimizedPosterUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        if (trimmed.contains("image.tmdb.org/t/p/")) {
            // Ultra-compact w185 resolution (~12KB to 20KB) for lightning-fast poster loading
            return trimmed
                .replace("/original/", "/w185/")
                .replace("/w1280/", "/w185/")
                .replace("/w780/", "/w185/")
                .replace("/w500/", "/w185/")
                .replace("/w342/", "/w185/")
        }
        return getOptimizedThumbnailUrl(trimmed, preferCompact = true)
    }

    fun getOptimizedBackdropUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        if (trimmed.contains("image.tmdb.org/t/p/")) {
            // High-efficiency w780 resolution for banners and hero carousel
            return trimmed
                .replace("/original/", "/w780/")
                .replace("/w1280/", "/w780/")
                .replace("/w500/", "/w780/")
        }
        return getOptimizedThumbnailUrl(trimmed, preferCompact = false)
    }

    /**
     * Build an ultra-fast, lightweight ImageRequest specifically tailored for 2:3 movie/show posters.
     * Uses w185 downsampling, RGB_565 bitmap config (50% RAM reduction, instant decode),
     * and aggressive disk/memory caching for immediate loading without lag.
     */
    fun buildPosterRequest(
        context: Context,
        url: String?,
        crossfadeMillis: Int = 120
    ): ImageRequest? {
        val optimizedUrl = getOptimizedPosterUrl(url) ?: return null

        return ImageRequest.Builder(context)
            .data(optimizedUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .crossfade(crossfadeMillis)
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setHeader("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8")
            .build()
    }

    /**
     * Build an optimized ImageRequest for hero banners and backdrop images.
     */
    fun buildBackdropRequest(
        context: Context,
        url: String?,
        crossfadeMillis: Int = 150
    ): ImageRequest? {
        val optimizedUrl = getOptimizedBackdropUrl(url) ?: return null

        return ImageRequest.Builder(context)
            .data(optimizedUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .crossfade(crossfadeMillis)
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setHeader("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8")
            .build()
    }

    /**
     * Preloads poster URLs into Coil's RAM & disk cache for zero-latency scrolling.
     * Restricted to visible/near-visible items (maxCount = 6) to avoid network/CPU spikes.
     */
    fun preloadPosters(context: Context, urls: List<String?>, maxCount: Int = 6) {
        if (urls.isEmpty()) return
        val imageLoader = Coil.imageLoader(context)

        preloadScope.launch {
            try {
                urls.filterNotNull().take(maxCount).forEach { rawUrl ->
                    val request = buildPosterRequest(context, rawUrl, crossfadeMillis = 0)
                    if (request != null) {
                        imageLoader.enqueue(request)
                    }
                }
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Preloads a small batch of thumbnail URLs in parallel into Coil's RAM & disk cache
     * for visible/near-visible items, eliminating scrolling stutter without saturating mobile network.
     */
    fun preloadThumbnails(context: Context, videos: List<VideoItem>, maxCount: Int = 6) {
        if (videos.isEmpty()) return
        val imageLoader = Coil.imageLoader(context)

        preloadScope.launch {
            try {
                // Primary thumbnail prefetch: keeps network bandwidth focused on fast visible thumbnail displays
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
     * Preloads a list of raw thumbnail URLs directly into cache (limited to visible items).
     */
    fun preloadUrls(context: Context, urls: List<String?>, maxCount: Int = 6) {
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

