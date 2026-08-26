package com.example.subtitles

import android.content.Context
import android.util.Log
import com.example.model.CaptionOption
import com.example.model.MediaIdentity
import com.example.model.StreamData
import com.example.subtitles.providers.JimakuProvider
import com.example.subtitles.providers.OpenSubtitlesProvider
import com.example.subtitles.providers.SubDlProvider
import com.example.subtitles.providers.SubSourceProvider
import com.example.util.SubtitleCue
import com.example.util.SubtitleTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * External Subtitle Provider Manager orchestrates the strict fallback hierarchy:
 * 1. Embedded subtitles (detected from Media3/stream)
 * 2. Bilibili subtitles (native API JSON subtitles)
 * 3. External subtitle providers (OpenSubtitles, SubDL, Jimaku, SubSource, etc.)
 * 4. Cached transcript / Cached Subtitle
 * 5. Voice Activity Detection (RMS detection)
 */
object SubtitleManager {
    private const val TAG = "SubtitleManager"

    private val providers = listOf<SubtitleProvider>(
        OpenSubtitlesProvider(),
        SubDlProvider(),
        JimakuProvider(),
        SubSourceProvider()
    )

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

    private var searchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initializes and executes the full multi-tier subtitle discovery pipeline.
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
                    val isBilibili = streamData.providerId == "bilibili" || cap.url.contains("bilibili") || cap.url.contains("biliapi")
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

            // Step 2: Search External Subtitle Providers in parallel
            val query = buildSearchQuery(streamData, mediaIdentity)
            val externalSubtitles = searchExternalProviders(query)
            combinedResults.addAll(externalSubtitles)

            // Step 3: Sort & Rank Results
            val ranked = rankSubtitles(combinedResults, _selectedLanguage.value)
            _discoveredSubtitles.value = ranked
            _isSearching.value = false

            // Step 4: Check if usable subtitle exists
            if (ranked.isNotEmpty()) {
                val bestMatch = ranked.first()
                Log.i(TAG, "Best subtitle found: [${bestMatch.providerName}] ${bestMatch.title} (${bestMatch.languageCode})")
                selectSubtitle(context, bestMatch)
                onUsableSubtitleFound?.invoke(bestMatch)
            } else {
                Log.i(TAG, "No usable subtitle found across external providers. Triggering Whisper.cpp fallback.")
                onFallbackToWhisper?.invoke()
            }
        }
    }

    private suspend fun searchExternalProviders(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val deferredList = providers.filter { it.isEnabled }.map { provider ->
            async {
                try {
                    provider.search(query)
                } catch (e: Exception) {
                    Log.w(TAG, "Provider ${provider.name} search failed: ${e.message}")
                    emptyList()
                }
            }
        }
        deferredList.awaitAll().flatten()
    }

    /**
     * Selects and loads a subtitle track, parsing its cues and caching it.
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
                    val provider = providers.find { it.id == item.providerId }
                    rawContent = if (provider != null) {
                        provider.fetchContent(item)
                    } else {
                        // Direct download e.g. for Bilibili or embedded URL
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
     * Sets user's target language with automatic cue translation and cache reuse.
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

        // Check if translation is cached
        val cachedTranslated = SubtitleCache.getTranslatedCues(cacheKey, targetLang)
        if (cachedTranslated != null && cachedTranslated.isNotEmpty()) {
            _activeCues.value = cachedTranslated
            return
        }

        if (targetLang == sourceLang || targetLang == "auto" || targetLang == "orig") {
            _activeCues.value = baseCues
            return
        }

        // Translate cues
        val translated = SubtitleTranslator.translateCues(baseCues, targetLang = targetLang, sourceLang = sourceLang)
        SubtitleCache.putTranslatedCues(cacheKey, targetLang, translated)
        _activeCues.value = translated
    }

    /**
     * Updates active subtitle texts according to player playback position.
     */
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

        return SubtitleSearchQuery(
            title = title,
            year = year,
            season = mediaIdentity?.season,
            episode = mediaIdentity?.episode,
            tmdbId = mediaIdentity?.tmdbId,
            imdbId = mediaIdentity?.imdbId,
            languageCode = _selectedLanguage.value,
            mediaIdentity = mediaIdentity
        )
    }

    private fun rankSubtitles(items: List<SubtitleItem>, targetLang: String): List<SubtitleItem> {
        return items.sortedWith(
            compareByDescending<SubtitleItem> {
                // Priority 1: Source hierarchy (Embedded/Bilibili -> External)
                when (it.sourceType) {
                    SubtitleSourceType.EMBEDDED -> 500
                    SubtitleSourceType.BILIBILI -> 400
                    SubtitleSourceType.EXTERNAL_PROVIDER -> 300
                    SubtitleSourceType.CACHED -> 200
                    SubtitleSourceType.VOICE_ACTIVITY_DETECTION -> 100
                }
            }.thenByDescending {
                // Priority 2: Exact Language Match
                if (it.languageCode.equals(targetLang, ignoreCase = true)) 100 else 0
            }.thenByDescending {
                // Priority 3: Match score
                it.matchScore
            }
        )
    }

    private suspend fun downloadDirect(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = okhttp3.Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (!headers.containsKey("User-Agent")) {
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            if (url.contains("bilibili")) {
                reqBuilder.header("Referer", "https://www.bilibili.com/")
            }
            val client = okhttp3.OkHttpClient()
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct download failed for $url: ${e.message}")
            null
        }
    }
}
