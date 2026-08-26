package com.example.util

import android.content.Context
import android.util.Log
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

object PreviewFrameResolver {
    private const val TAG = "PreviewFrameResolver"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Checks if this video supports horizontal scrub teaser frames.
     * Fast O(1) check to avoid regexes on non-scrubbable sources (like YouTube, Archive.org, etc.)
     */
    fun supportsScrubbing(video: VideoItem): Boolean {
        if (video.previewThumbnails.size > 1) return true
        val rawThumb = video.thumbnailUrl?.trim() ?: return false
        val provider = (video.providerId ?: "").lowercase()
        val thumbLower = rawThumb.lowercase()

        return provider.contains("eporner") || thumbLower.contains("eporner.com") ||
               provider.contains("xvideos") || thumbLower.contains("xvideos") ||
               provider.contains("xhamster") || thumbLower.contains("xhcdn.com") ||
               provider.contains("pornhub") || thumbLower.contains("phncdn.com") ||
               provider.contains("redtube") || thumbLower.contains("redtube") ||
               provider.contains("youporn") || thumbLower.contains("youporn") ||
               provider.contains("rule34") || thumbLower.contains("rule34video")
    }

    /**
     * Resolves an ordered list of timeline teaser preview frames for a video item.
     * If the video already has populated previewThumbnails, those are returned.
     * Otherwise, parses the video's thumbnail URL and provider architecture to generate
     * a 10 to 30 frame storyboard sequence for horizontal scrubbing.
     */
    fun resolvePreviewFrames(video: VideoItem): List<String> {
        if (video.previewThumbnails.isNotEmpty()) {
            return video.previewThumbnails
        }

        val rawThumb = video.thumbnailUrl?.trim() ?: return emptyList()
        if (!supportsScrubbing(video)) {
            return listOf(rawThumb)
        }

        val provider = (video.providerId ?: "").lowercase()
        val thumbLower = rawThumb.lowercase()

        // 1. EPORNER (16 storyboard teaser frames across video timeline)
        if (provider.contains("eporner") || thumbLower.contains("eporner.com")) {
            val epornerMatcher = Regex("""/(\d+)(_\d+\.jpg)""").find(rawThumb)
            if (epornerMatcher != null) {
                val suffix = epornerMatcher.groupValues[2]
                val base = rawThumb.substring(0, epornerMatcher.range.first)
                return (1..16).map { idx -> "$base/$idx$suffix" }
            }
            if (thumbLower.contains("/thumbs/")) {
                val lastSlash = rawThumb.lastIndexOf('/')
                if (lastSlash != -1) {
                    val base = rawThumb.substring(0, lastSlash)
                    return (1..16).map { idx -> "$base/${idx}_360.jpg" }
                }
            }
        }

        // 2. XVIDEOS (30 storyboard teaser frames: xv_1_t.jpg .. xv_30_t.jpg)
        if (provider.contains("xvideos") || thumbLower.contains("xvideos")) {
            // New CDN style: .../xv_XX_t.jpg or .../xv_XX.jpg
            val xvMatcher = Regex("""/xv_(\d+)(_t)?\.jpg""").find(rawThumb)
            if (xvMatcher != null) {
                val hasT = xvMatcher.groupValues[2]
                val base = rawThumb.substring(0, xvMatcher.range.first)
                val tSuffix = if (hasT.isNotEmpty()) "_t.jpg" else ".jpg"
                return (1..30).map { idx -> "$base/xv_$idx$tSuffix" }
            }
            // Older style: .../thumbs169.../XX.jpg
            val numMatcher = Regex("""/(\d+)\.jpg""").find(rawThumb)
            if (numMatcher != null) {
                val base = rawThumb.substring(0, numMatcher.range.first)
                return (1..30).map { idx -> "$base/$idx.jpg" }
            }
        }

        // 3. PORNHUB (16 teaser scene frames: 1.jpg .. 16.jpg, handles (m=eaAaGwObaaaa)1.jpg & CDN paths)
        if (provider.contains("pornhub") || thumbLower.contains("phncdn.com") || thumbLower.contains("pornhub")) {
            val phPrefixMatcher = Regex("""/((?:\([^\)]+\))*?)(\d+)\.(jpg|webp|jpeg|png)""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (phPrefixMatcher != null) {
                val prefix = phPrefixMatcher.groupValues[1]
                val ext = phPrefixMatcher.groupValues[3]
                val base = rawThumb.substring(0, phPrefixMatcher.range.first)
                return (1..16).map { idx -> "$base/${prefix}$idx.$ext" }
            }
            val phMatcher = Regex("""/(\d+)\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (phMatcher != null) {
                val ext = phMatcher.groupValues[2]
                val base = rawThumb.substring(0, phMatcher.range.first)
                return (1..16).map { idx -> "$base/$idx.$ext" }
            }
            if (rawThumb.contains("/original/") || rawThumb.contains("/thumbs_")) {
                val base = rawThumb.substringBeforeLast("/")
                return (1..16).map { idx -> "$base/$idx.jpg" }
            }
        }

        // 4. XHAMSTER (12 teaser storyboard cuts)
        if (provider.contains("xhamster") || thumbLower.contains("xhcdn.com") || thumbLower.contains("xhamster")) {
            val xhMatcher = Regex("""/(\d+)\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (xhMatcher != null) {
                val ext = xhMatcher.groupValues[2]
                val base = rawThumb.substring(0, xhMatcher.range.first)
                return (1..12).map { idx -> "$base/$idx.$ext" }
            }
        }

        // 5. REDTUBE & YOUPORN
        if (provider.contains("redtube") || provider.contains("youporn") || thumbLower.contains("redtube") || thumbLower.contains("youporn") || thumbLower.contains("rdtcdn.com") || thumbLower.contains("ypncdn.com")) {
            val rtPrefixMatcher = Regex("""/((?:\([^\)]+\))*?)(\d+)\.(jpg|webp|jpeg|png)""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (rtPrefixMatcher != null) {
                val prefix = rtPrefixMatcher.groupValues[1]
                val ext = rtPrefixMatcher.groupValues[3]
                val base = rawThumb.substring(0, rtPrefixMatcher.range.first)
                return (1..16).map { idx -> "$base/${prefix}$idx.$ext" }
            }
            val rtMatcher = Regex("""/(\d+)\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (rtMatcher != null) {
                val ext = rtMatcher.groupValues[2]
                val base = rawThumb.substring(0, rtMatcher.range.first)
                return (1..16).map { idx -> "$base/$idx.$ext" }
            }
        }

        // 6. RULE34VIDEO & KVS (10-15 frames: /1.jpg .. /10.jpg)
        if (provider.contains("rule34") || thumbLower.contains("rule34video") || thumbLower.contains("videos_screenshots")) {
            val r34Matcher = Regex("""/(\d+)\.jpg""").find(rawThumb)
            if (r34Matcher != null) {
                val base = rawThumb.substring(0, r34Matcher.range.first)
                return (1..10).map { idx -> "$base/$idx.jpg" }
            }
        }

        // 7. BEEG (thumbs.externulls.com/240x180/{id}.jpg)
        if (provider.contains("beeg") || thumbLower.contains("externulls.com")) {
            val beegMatcher = Regex("""thumbs\.externulls\.com/(\d+x\d+)/(\d+)\.jpg""").find(rawThumb)
            if (beegMatcher != null) {
                val res = beegMatcher.groupValues[1]
                val fileId = beegMatcher.groupValues[2]
                return listOf(
                    "https://thumbs.externulls.com/$res/$fileId.jpg"
                )
            }
        }

        // Fallback: Default to single thumbnail
        return listOf(rawThumb)
    }

    /**
     * Pre-fetches storyboard frames in background for smooth, zero-latency scrubbing.
     */
    fun prefetchFrames(context: Context, frames: List<String>, maxFrames: Int = 10) {
        if (frames.size <= 1) return
        val targetFrames = if (frames.size > maxFrames) {
            val step = frames.size / maxFrames
            frames.filterIndexed { index, _ -> index % step == 0 }.take(maxFrames)
        } else {
            frames
        }

        scope.launch {
            val imageLoader = context.imageLoader
            for (url in targetFrames) {
                try {
                    val req = ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(req)
                } catch (e: Exception) {
                    // Ignore prefetch network hiccups
                }
            }
        }
    }
}
