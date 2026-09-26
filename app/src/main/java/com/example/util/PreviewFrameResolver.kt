package com.example.util

import android.content.Context
import android.util.Log
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object PreviewFrameResolver {
    private const val TAG = "PreviewFrameResolver"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val validatedCache = ConcurrentHashMap<String, List<String>>()

    /**
     * Checks if this video supports horizontal scrub teaser frames.
     * Returns true ONLY if the provider actually supports dynamic scene previews (e.g. Adult tubes with storyboard CDNs)
     * or if explicit multi-frame storyboard preview thumbnails are present.
     */
    fun supportsScrubbing(video: VideoItem): Boolean {
        val provider = (video.providerId ?: "").lowercase()
        val nonSupporting = listOf("youtube", "vimeo", "dailymotion", "tencent", "qq", "wetv", "bilibili", "twitch", "torrent", "tmdb", "anilist", "jikan", "vega", "vegacloud", "vidsrc", "vidrock", "archiveorg", "mxplayer", "sonyliv", "hotstar", "disney", "discovery", "amazon", "amazonminitv", "curiositystream", "crunchyroll", "all")
        if (provider in nonSupporting) return false
        val isAdultTube = com.example.util.SourceTagHelper.isAdultSource(provider) ||
                provider in listOf("spankbang", "eporner", "xvideos", "xnxx", "hellporno", "pornhub", "thumbzilla", "xhamster", "redtube", "youporn", "4tube", "rule34", "rule34video", "thisvid", "playvid", "txxx")
        if (isAdultTube) {
            if (video.previewThumbnails.size > 1) return true
            val frames = resolvePreviewFrames(video)
            return frames.size > 1
        }
        return false
    }

    /**
     * Gets previously validated frame list from memory cache if available.
     */
    fun getCachedFrames(video: VideoItem): List<String>? {
        val key = video.id.ifBlank { video.thumbnailUrl ?: "" }
        return validatedCache[key]
    }

    /**
     * Preloads and validates all candidate teaser frames for a video in parallel.
     * Filters out broken/404 URLs, caches the validated result in memory,
     * and returns the list of ready-to-display frame URLs.
     */
    suspend fun preloadAndValidateFrames(context: Context, video: VideoItem): List<String> {
        val key = video.id.ifBlank { video.thumbnailUrl ?: "" }
        validatedCache[key]?.let { cached ->
            if (cached.isNotEmpty()) return cached
        }

        val candidateFrames = resolvePreviewFrames(video)
        if (candidateFrames.size <= 1) {
            val fallback = if (candidateFrames.isNotEmpty()) candidateFrames else listOfNotNull(video.thumbnailUrl)
            validatedCache[key] = fallback
            return fallback
        }

        val rawThumb = video.thumbnailUrl?.trim()
        val imageLoader = context.imageLoader

        val validFrames = coroutineScope {
            candidateFrames.map { frameUrl ->
                async(Dispatchers.IO) {
                    try {
                        val req = ThumbnailOptimizer.buildThumbnailRequest(context, frameUrl, preferCompact = true)
                        if (req != null) {
                            val result = imageLoader.execute(req)
                            if (result is SuccessResult) {
                                return@async frameUrl
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Frame preload error for $frameUrl: ${e.message}")
                    }
                    null
                }
            }.awaitAll().filterNotNull()
        }

        val finalFrames = if (validFrames.size >= 2) {
            validFrames
        } else if (rawThumb != null) {
            listOf(rawThumb)
        } else {
            candidateFrames
        }

        validatedCache[key] = finalFrames
        return finalFrames
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

        val provider = (video.providerId ?: "").lowercase()
        val thumbLower = rawThumb.lowercase()

        // 1. SPANKBANG (16 scene screenshots: /1.jpg .. /16.jpg or CDN paths)
        if (provider.contains("spankbang") || thumbLower.contains("spankbang") || thumbLower.contains("sb-cd.com") || thumbLower.contains("spankcdn")) {
            val sbMatcher = Regex("""/(\d+)\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (sbMatcher != null) {
                val ext = sbMatcher.groupValues[2]
                val base = rawThumb.substring(0, sbMatcher.range.first)
                return (1..16).map { idx -> "$base/$idx.$ext" }
            }
            if (thumbLower.contains("/t/")) {
                val lastSlash = rawThumb.lastIndexOf('/')
                if (lastSlash != -1) {
                    val base = rawThumb.substring(0, lastSlash)
                    return (1..16).map { idx -> "$base/$idx.jpg" }
                }
            }
        }

        // 2. EPORNER (16 storyboard teaser frames across video timeline)
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

        // 3. XVIDEOS & XNXX (30 storyboard teaser frames: xv_1_t.jpg .. xv_30_t.jpg or hash.1.jpg .. hash.30.jpg)
        if (provider.contains("xvideos") || provider.contains("xnxx") || thumbLower.contains("xvideos") || thumbLower.contains("xnxx") || thumbLower.contains("xnxx-cdn")) {
            // Hash format with frame index: .../thumbs169ll/2c/b4/.../2cb4b3012903847a98a09b3c4a259c84.14.jpg
            val hashMatcher = Regex("""/([a-f0-9]{16,40})\.(\d+)\.jpg""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (hashMatcher != null) {
                val hash = hashMatcher.groupValues[1]
                val base = rawThumb.substring(0, hashMatcher.range.first)
                return (1..30).map { idx -> "$base/$hash.$idx.jpg" }
            }

            // New CDN style: .../xv_XX_t.jpg or .../xv_XX.jpg
            val xvMatcher = Regex("""/xv_(\d+)(_t)?\.jpg""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (xvMatcher != null) {
                val hasT = xvMatcher.groupValues[2]
                val base = rawThumb.substring(0, xvMatcher.range.first)
                val tSuffix = if (hasT.isNotEmpty()) "_t.jpg" else ".jpg"
                return (1..30).map { idx -> "$base/xv_$idx$tSuffix" }
            }

            // Older style: .../thumbs169.../XX.jpg (only 1 to 30 frame index, NOT large video IDs)
            val numMatcher = Regex("""/([1-9]|[12]\d|30)\.jpg""").find(rawThumb)
            if (numMatcher != null) {
                val base = rawThumb.substring(0, numMatcher.range.first)
                return (1..30).map { idx -> "$base/$idx.jpg" }
            }
        }

        // 4. HELLPORNO (16 scene screenshots)
        if (provider.contains("hellporno") || thumbLower.contains("hellporno") || thumbLower.contains("videos_screenshots")) {
            val hpMatcher = Regex("""/(\d+)\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (hpMatcher != null) {
                val ext = hpMatcher.groupValues[2]
                val base = rawThumb.substring(0, hpMatcher.range.first)
                return (1..16).map { idx -> "$base/$idx.$ext" }
            }
            if (rawThumb.contains("/preview.jpg")) {
                val base = rawThumb.substringBeforeLast("/preview.jpg")
                return (1..16).map { idx -> "$base/$idx.jpg" }
            }
        }

        // 5. PORNHUB (16 teaser scene frames: 1.jpg .. 16.jpg, handles (m=eaAaGwObaaaa)1.jpg & CDN paths)
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

        // 6. XHAMSTER (12 teaser storyboard cuts)
        if (provider.contains("xhamster") || thumbLower.contains("xhcdn.com") || thumbLower.contains("xhamster")) {
            val xhMatcher = Regex("""/(\d+)\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (xhMatcher != null) {
                val ext = xhMatcher.groupValues[2]
                val base = rawThumb.substring(0, xhMatcher.range.first)
                return (1..12).map { idx -> "$base/$idx.$ext" }
            }
        }

        // 7. REDTUBE & YOUPORN
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

        // 8. 4TUBE (16 storyboard teaser frames: 1.jpg .. 16.jpg)
        if (provider.contains("4tube") || thumbLower.contains("ttcache.com") || thumbLower.contains("4tube")) {
            val ttHostMatch = Regex("""(https://c\d+\.ttcache\.com/thumbnail/[^/]+/288x162/)[^/]+""").find(rawThumb)
            if (ttHostMatch != null) {
                val prefix = ttHostMatch.groupValues[1]
                return (1..16).map { idx -> "${prefix}${idx}.jpg" }
            }
            val numMatch = Regex("""/([1-9]|1[0-6])\.jpg""").find(rawThumb)
            if (numMatch != null) {
                val base = rawThumb.substring(0, numMatch.range.first)
                return (1..16).map { idx -> "$base/$idx.jpg" }
            }
        }

        // 9. RULE34VIDEO & KVS (10-15 frames: /1.jpg .. /10.jpg)
        if (provider.contains("rule34") || thumbLower.contains("rule34video") || thumbLower.contains("videos_screenshots")) {
            val r34Matcher = Regex("""/([1-9]|1[0-5])\.jpg""").find(rawThumb)
            if (r34Matcher != null) {
                val base = rawThumb.substring(0, r34Matcher.range.first)
                return (1..10).map { idx -> "$base/$idx.jpg" }
            }
        }

        // 10. THISVID & PLAYVIDS & TXXX (10-16 storyboard cuts)
        if (provider.contains("thisvid") || provider.contains("playvid") || provider.contains("txxx") ||
            thumbLower.contains("thisvid") || thumbLower.contains("playvid") || thumbLower.contains("txxx") || thumbLower.contains("ahcdn.com")) {
            val kvsMatcher = Regex("""/(\d+)\.(jpg|webp|jpeg)""", RegexOption.IGNORE_CASE).find(rawThumb)
            if (kvsMatcher != null) {
                val ext = kvsMatcher.groupValues[2]
                val base = rawThumb.substring(0, kvsMatcher.range.first)
                return (1..12).map { idx -> "$base/$idx.$ext" }
            }
            if (rawThumb.contains("/preview.jpg")) {
                val base = rawThumb.substringBeforeLast("/preview.jpg")
                return (1..12).map { idx -> "$base/$idx.jpg" }
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

    /**
     * Proactively preloads teasers for a list of feed items in background.
     */
    fun prefetchTeasersForFeed(context: Context, videos: List<VideoItem>, maxItems: Int = 8) {
        scope.launch {
            val targets = videos.filter { supportsScrubbing(it) }.take(maxItems)
            for (v in targets) {
                try {
                    preloadAndValidateFrames(context, v)
                } catch (_: Exception) {}
            }
        }
    }
}
