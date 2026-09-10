package com.example.subtitles

import android.content.Context
import android.util.Log
import com.example.model.MediaIdentity
import com.example.model.StreamData
import com.example.subtitles.plugin.SubtitlePluginRegistry
import com.example.util.SubtitleCue
import com.example.util.SubtitleTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Butterfly Subtitle Manager.
 * Implements the unified subtitle architecture:
 * "Butterfly Core → Subtitle Manager → Provider Plugins → unified results → deduplicate/rank → download"
 *
 * Concurrently queries enabled plugins with timeout and error fallback,
 * ranks results according to the strict criteria:
 * language → release/title match → season/episode → FPS → resolution → HI/Forced → hash,
 * and deduplicates identical subtitle tracks.
 */
object SubtitleManager {
    private const val TAG = "SubtitleManager"
    private const val DEFAULT_PLUGIN_TIMEOUT_MS = 8000L

    private val directClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val _discoveredSubtitles = MutableStateFlow<List<SubtitleItem>>(emptyList())
    val discoveredSubtitles: StateFlow<List<SubtitleItem>> = _discoveredSubtitles.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _activeSubtitleItem = MutableStateFlow<SubtitleItem?>(null)
    val activeSubtitleItem: StateFlow<SubtitleItem?> = _activeSubtitleItem.asStateFlow()

    private val _activeCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val activeCues: StateFlow<List<SubtitleCue>> = _activeCues.asStateFlow()

    private val _currentActiveOriginalText = MutableStateFlow("")
    val currentActiveOriginalText: StateFlow<String> = _currentActiveOriginalText.asStateFlow()

    private val _currentActiveTranslatedText = MutableStateFlow("")
    val currentActiveTranslatedText: StateFlow<String> = _currentActiveTranslatedText.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _preferHearingImpaired = MutableStateFlow(false)
    val preferHearingImpaired: StateFlow<Boolean> = _preferHearingImpaired.asStateFlow()

    private var searchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun setPreferHearingImpaired(prefer: Boolean) {
        _preferHearingImpaired.value = prefer
    }

    /**
     * Resolves subtitles for playback by running the concurrent discovery pipeline.
     */
    fun resolveSubtitlesForPlayback(
        context: Context,
        streamData: StreamData?,
        mediaIdentity: MediaIdentity? = null,
        onUsableSubtitleFound: ((SubtitleItem) -> Unit)? = null,
        onFallbackToWhisper: (() -> Unit)? = null
    ) {
        searchJob?.cancel()
        _discoveredSubtitles.value = emptyList()
        _activeSubtitleItem.value = null
        _activeCues.value = emptyList()
        _currentActiveOriginalText.value = ""
        _currentActiveTranslatedText.value = ""

        if (streamData == null) {
            onFallbackToWhisper?.invoke()
            return
        }

        searchJob = scope.launch {
            _isSearching.value = true
            val combinedResults = mutableListOf<SubtitleItem>()

            // Step 1: Detect Embedded Subtitles & Bilibili Native Subtitles
            if (streamData.captionOptions.isNotEmpty()) {
                streamData.captionOptions.forEachIndexed { idx, cap ->
                    val isBilibili = streamData.providerId == "bilibili" ||
                            cap.url.contains("bilibili") ||
                            cap.url.contains("biliapi")

                    val subItem = SubtitleItem(
                        id = "native_${cap.languageCode}_$idx",
                        providerId = if (isBilibili) "bilibili" else "embedded",
                        providerName = if (isBilibili) "Bilibili Subtitles" else "Embedded Subtitle",
                        title = "${cap.languageName} (${cap.languageCode})",
                        languageCode = cap.languageCode,
                        languageName = cap.languageName,
                        format = if (cap.format.contains("json", ignoreCase = true)) SubtitleFormat.JSON else SubtitleFormat.VTT,
                        downloadUrl = cap.url,
                        matchScore = 100,
                        sourceType = if (isBilibili) SubtitleSourceType.BILIBILI else SubtitleSourceType.EMBEDDED
                    )
                    combinedResults.add(subItem)
                }
            }

            // Step 2: Concurrently query all enabled plugins
            val query = buildSearchQuery(streamData, mediaIdentity)
            val externalSubtitles = searchPluginProviders(context, query)
            combinedResults.addAll(externalSubtitles)

            // Step 3: Deduplicate identical subtitles across providers
            val deduplicated = deduplicateSubtitles(combinedResults)

            // Step 4: Strict Rank: language → release/title match → season/episode → FPS → resolution → HI/Forced → hash
            val ranked = rankSubtitles(deduplicated, query, _selectedLanguage.value, _preferHearingImpaired.value)
            _discoveredSubtitles.value = ranked
            _isSearching.value = false

            // Subtitles are stored in _discoveredSubtitles for on-demand user selection via CC button
            if (ranked.isNotEmpty()) {
                Log.i(TAG, "Discovered ${ranked.size} subtitle tracks ready for user selection. Top: [${ranked.first().providerName}] ${ranked.first().title}")
            }
        }
    }

    /**
     * Concurrently searches all enabled provider plugins with individual timeouts.
     */
    suspend fun searchPluginProviders(
        context: Context,
        query: SubtitleSearchQuery,
        timeoutMs: Long = DEFAULT_PLUGIN_TIMEOUT_MS
    ): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val plugins = SubtitlePluginRegistry.getEnabledPlugins(context)
        if (plugins.isEmpty()) {
            Log.w(TAG, "No subtitle plugins are currently enabled in settings")
            return@withContext emptyList()
        }

        supervisorScope {
            val deferredList = plugins.map { plugin ->
                async {
                    try {
                        withTimeoutOrNull(timeoutMs) {
                            plugin.search(query)
                        } ?: run {
                            Log.w(TAG, "Plugin ${plugin.name} timed out after ${timeoutMs}ms")
                            emptyList()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Plugin ${plugin.name} search failed: ${e.message}")
                        emptyList()
                    }
                }
            }
            deferredList.map { it.await() }.flatten()
        }
    }

    /**
     * Deduplicates identical or duplicate subtitles across providers.
     * Keeps the track with higher matchScore and more complete metadata.
     */
    fun deduplicateSubtitles(items: List<SubtitleItem>): List<SubtitleItem> {
        val seenUrls = mutableSetOf<String>()
        val groupedByKey = mutableMapOf<String, SubtitleItem>()

        for (item in items) {
            val normUrl = item.downloadUrl.trim()
            if (normUrl.isNotBlank() && seenUrls.contains(normUrl)) {
                continue
            }
            if (normUrl.isNotBlank()) {
                seenUrls.add(normUrl)
            }

            // Normalization key: language + normalized title + season/episode
            val cleanTitle = normalizeReleaseName(item.releaseInfo ?: item.title)
            val dedupeKey = "${item.languageCode.lowercase()}_${cleanTitle}_s${item.season ?: 0}e${item.episode ?: 0}"

            val existing = groupedByKey[dedupeKey]
            if (existing == null) {
                groupedByKey[dedupeKey] = item
            } else {
                // If this duplicate has better metadata (e.g. FPS or resolution), prefer it
                val existingQuality = (if (existing.fps != null) 1 else 0) + (if (existing.resolution != null) 1 else 0)
                val newQuality = (if (item.fps != null) 1 else 0) + (if (item.resolution != null) 1 else 0)
                if (newQuality > existingQuality || item.matchScore > existing.matchScore) {
                    groupedByKey[dedupeKey] = item
                }
            }
        }
        return groupedByKey.values.toList()
    }

    /**
     * Ranks subtitles by:
     * 1. Language (Exact target match > Prefix match > English fallback)
     * 2. Release / Title match (Exact name > token overlap)
     * 3. Season & Episode (Exact match > season only > penalty on mismatch)
     * 4. FPS (Matches video stream fps or standard 23.976/24/25)
     * 5. Resolution (2160p/4K > 1080p > 720p match)
     * 6. Hearing Impaired / Forced (Preferred HI badge, non-auto-generated)
     * 7. Hash / Exact match
     */
    fun rankSubtitles(
        items: List<SubtitleItem>,
        query: SubtitleSearchQuery,
        targetLang: String,
        preferHi: Boolean
    ): List<SubtitleItem> {
        val queryTokens = extractTokens(query.releaseName ?: query.title)

        return items.map { item ->
            val score = calculateSubtitleScore(item, query, queryTokens, targetLang, preferHi)
            item.copy(matchScore = score)
        }.sortedByDescending { it.matchScore }
    }

    private fun calculateSubtitleScore(
        item: SubtitleItem,
        query: SubtitleSearchQuery,
        queryTokens: Set<String>,
        targetLang: String,
        preferHi: Boolean
    ): Int {
        var score = 0

        // Inbuilt captions (Bilibili / container) given top baseline
        if (item.sourceType == SubtitleSourceType.EMBEDDED || item.sourceType == SubtitleSourceType.BILIBILI) {
            score += 20000
        }

        // 1. Language Hierarchy
        val itemLang = item.languageCode.lowercase().trim()
        val target = targetLang.lowercase().trim()
        when {
            itemLang == target -> score += 10000
            itemLang.startsWith(target) || target.startsWith(itemLang) -> score += 6000
            itemLang == "en" -> score += 2500
            else -> score += 200
        }

        // 2. Release & Title Match
        val itemText = (item.releaseInfo ?: item.title).lowercase()
        val normQueryTitle = query.title.lowercase().trim()

        if (normQueryTitle.isNotBlank() && itemText.contains(normQueryTitle)) {
            score += 3000
        }

        val itemTokens = extractTokens(itemText)
        val matchingTokens = queryTokens.intersect(itemTokens)
        score += (matchingTokens.size * 250).coerceAtMost(2500)

        // Quality token bonus: e.g. "bluray", "web-dl", "yify", "x264", "x265", "hevc"
        val qualityKeywords = setOf("bluray", "bdrip", "brrip", "web-dl", "webrip", "yify", "rarbg", "x264", "x265", "hevc")
        val commonQuality = qualityKeywords.intersect(matchingTokens)
        score += commonQuality.size * 300

        // 3. Season & Episode Match
        if (query.season != null && query.episode != null) {
            if (item.season == query.season && item.episode == query.episode) {
                score += 3500
            } else if (item.season != null && item.episode != null &&
                (item.season != query.season || item.episode != query.episode)) {
                // Heavily penalize wrong season or wrong episode to guarantee it never ranks
                score -= 20000
            } else {
                // Check if string contains SxxExx matching
                val sStr = "s%02de%02d".format(query.season, query.episode)
                val sShort = "%dx%d".format(query.season, query.episode)
                if (itemText.contains(sStr) || itemText.contains(sShort)) {
                    score += 3000
                }
            }
        }

        // 4. FPS Match
        if (query.fps != null && item.fps != null) {
            if (abs(query.fps - item.fps) < 0.05f) {
                score += 1200
            } else if (abs(query.fps - item.fps) < 0.5f) {
                score += 500
            }
        } else if (item.fps != null && abs(item.fps - 23.976f) < 0.05f) {
            score += 300
        }

        // 5. Resolution Match
        val queryRes = query.resolution ?: extractResolution(query.releaseName ?: query.title)
        val itemRes = item.resolution ?: extractResolution(itemText)
        if (queryRes != null && itemRes != null && queryRes.equals(itemRes, ignoreCase = true)) {
            score += 800
        }

        // 6. HI / Forced Match
        if (preferHi) {
            if (item.isHearingImpaired) score += 500
        } else {
            if (!item.isHearingImpaired) score += 500
        }
        if (!item.isAutoGenerated) {
            score += 300
        }

        // 7. Hash / Exact match
        if (!query.movieHash.isNullOrBlank() && query.movieHash == item.movieHash) {
            score += 6000
        }

        // Add intrinsic provider match score
        score += item.matchScore

        return score
    }

    /**
     * Selects and loads a subtitle track, downloading through its provider plugin.
     */
    fun selectSubtitle(context: Context, item: SubtitleItem?) {
        _activeSubtitleItem.value = item
        if (item == null) {
            _activeCues.value = emptyList()
            _currentActiveOriginalText.value = ""
            _currentActiveTranslatedText.value = ""
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val cacheKey = "${item.id}_${item.languageCode}"
                val memoryCues = SubtitleCache.getMemoryCues(cacheKey)
                if (memoryCues != null && memoryCues.isNotEmpty()) {
                    applyCuesWithTranslation(memoryCues, _selectedLanguage.value, item.languageCode)
                    return@launch
                }

                // Check Disk Cache
                var rawContent = SubtitleCache.getDiskCachedSubtitle(context, cacheKey)
                if (rawContent == null) {
                    val plugin = SubtitlePluginRegistry.getPlugin(item.providerId, context)
                    rawContent = if (plugin != null) {
                        plugin.fetchContent(item)
                    } else {
                        downloadDirect(item.downloadUrl, item.headers)
                    }

                    if (!rawContent.isNullOrBlank()) {
                        SubtitleCache.saveDiskCachedSubtitle(context, cacheKey, rawContent)
                    }
                }

                if (!rawContent.isNullOrBlank()) {
                    val parsedCues = SubtitleParser.parse(rawContent, item.format)
                    if (parsedCues.isNotEmpty()) {
                        SubtitleCache.putMemoryCues(cacheKey, parsedCues)
                        applyCuesWithTranslation(parsedCues, _selectedLanguage.value, item.languageCode)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading subtitle: ${e.message}")
            }
        }
    }

    /**
     * Changes user's target language with automatic cue translation.
     */
    fun setSelectedLanguage(targetLang: String) {
        _selectedLanguage.value = targetLang
        val item = _activeSubtitleItem.value
        if (item != null) {
            val cacheKey = "${item.id}_${item.languageCode}"
            val baseCues = SubtitleCache.getMemoryCues(cacheKey)
            if (baseCues != null && baseCues.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    applyCuesWithTranslation(baseCues, targetLang, item.languageCode)
                }
            }
        }
    }

    private suspend fun applyCuesWithTranslation(
        baseCues: List<SubtitleCue>,
        targetLang: String,
        sourceLang: String
    ) {
        val activeItem = _activeSubtitleItem.value ?: return
        val cacheKey = "${activeItem.id}_${activeItem.languageCode}"

        val cachedTranslated = SubtitleCache.getTranslatedCues(cacheKey, targetLang)
        if (cachedTranslated != null && cachedTranslated.isNotEmpty()) {
            _activeCues.value = cachedTranslated
            return
        }

        if (targetLang == sourceLang || targetLang == "auto" || targetLang == "orig") {
            _activeCues.value = baseCues
            return
        }

        val translated = SubtitleTranslator.translateCues(baseCues, targetLang = targetLang, sourceLang = sourceLang)
        SubtitleCache.putTranslatedCues(cacheKey, targetLang, translated)
        _activeCues.value = translated
    }

    fun updatePlaybackPosition(positionMs: Long) {
        val cues = _activeCues.value
        if (cues.isEmpty()) return

        val posSec = positionMs / 1000f
        val activeCue = cues.find { posSec >= it.fromSeconds && posSec <= it.toSeconds }
        if (activeCue != null) {
            _currentActiveOriginalText.value = activeCue.text
            _currentActiveTranslatedText.value = activeCue.translatedText ?: activeCue.text
        } else {
            _currentActiveOriginalText.value = ""
            _currentActiveTranslatedText.value = ""
        }
    }

    private fun buildSearchQuery(streamData: StreamData, mediaIdentity: MediaIdentity?): SubtitleSearchQuery {
        val title = streamData.title
        var year: Int? = null
        val yearMatch = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(title)
        if (yearMatch != null) {
            year = yearMatch.groupValues[1].toIntOrNull()
        }

        val resolution = extractResolution(title)

        return SubtitleSearchQuery(
            title = title,
            year = year,
            season = mediaIdentity?.season,
            episode = mediaIdentity?.episode,
            tmdbId = mediaIdentity?.tmdbId,
            imdbId = mediaIdentity?.imdbId,
            languageCode = _selectedLanguage.value,
            releaseName = title,
            mediaIdentity = mediaIdentity,
            resolution = resolution
        )
    }

    private fun extractTokens(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .toSet()
    }

    private fun normalizeReleaseName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(30)
    }

    private fun extractResolution(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") -> "2160p"
            lower.contains("1080p") -> "1080p"
            lower.contains("720p") -> "720p"
            lower.contains("480p") -> "480p"
            else -> null
        }
    }

    private suspend fun downloadDirect(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (!headers.containsKey("User-Agent")) {
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Butterfly/2.0")
            }
            if (url.contains("bilibili")) {
                reqBuilder.header("Referer", "https://www.bilibili.com/")
            }
            directClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct download failed for $url: ${e.message}")
            null
        }
    }
}
