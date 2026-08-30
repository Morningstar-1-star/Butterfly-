package com.example.ui

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ExtractorErrorDetails
import com.example.model.ExtractorErrorType
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.AppScreen
import com.example.model.CaptionOption
import com.example.model.FeedErrorDetails
import com.example.model.PlayableStreamOption
import com.example.model.ProviderUiItem
import com.example.model.StreamData
import com.example.model.UserPlaylist
import com.example.model.UserProfile
import com.example.model.ServerScanState
import com.example.model.ServerNode
import com.example.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import com.example.model.SearchSuggestionItem
import com.example.model.SearchFilterState

import com.example.db.AppDatabase
import com.example.db.WatchHistoryEntity
import com.example.db.BookmarkEntity
import com.example.db.LikedVideoEntity
import com.example.db.UserPlaylistEntity
import com.example.db.OfflineDownloadEntity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode {
    AMOLED_DARK,
    LIGHT
}

enum class AppAccentColor(val label: String, val color: Color) {
    YELLOW("Electric Yellow", Color(0xFFFFD600)),
    MONOCHROME("Black & White", Color(0xFFFFFFFF)),
    CYAN("Cyan Blue", Color(0xFF00E5FF)),
    PINK("Neon Pink", Color(0xFFFF4081)),
    PURPLE("Royal Purple", Color(0xFFAB47BC)),
    GREEN("Emerald Green", Color(0xFF00E676))
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val DIVERSE_TOPICS = listOf(
        "trending videos 2026",
        "popular movies 2026",
        "top music hits",
        "4k nature landscape",
        "gaming highlights",
        "science technology documentary",
        "official movie trailers",
        "tech news reviews",
        "viral video clips",
        "popular anime episodes",
        "internet archive movies",
        "classic comedy shows"
    )

    private val userDataDao = AppDatabase.getInstance(application).userDataDao()

    private fun serializeVideos(videos: List<VideoItem>): String {
        val arr = JSONArray()
        videos.forEach { v ->
            val obj = JSONObject()
            obj.put("id", v.id)
            obj.put("title", v.title)
            obj.put("uploaderName", v.uploaderName)
            obj.put("thumbnailUrl", v.thumbnailUrl)
            obj.put("providerId", v.providerId)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializeVideos(jsonStr: String): List<VideoItem> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<VideoItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VideoItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        uploaderName = obj.optString("uploaderName"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        providerId = obj.optString("providerId").takeIf { !it.isNullOrEmpty() },
                        durationSeconds = 0L
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    val libraryRepository = com.example.repository.LibraryRepository(application)
    private val prefs = application.getSharedPreferences("app_user_prefs", android.content.Context.MODE_PRIVATE)
    private val settingsPrefs = application.getSharedPreferences("app_settings_prefs", android.content.Context.MODE_PRIVATE)

    private val _failedSourceLogs = MutableStateFlow<List<com.example.model.FailedSourceLog>>(emptyList())
    val failedSourceLogs: StateFlow<List<com.example.model.FailedSourceLog>> = _failedSourceLogs.asStateFlow()

    private val _showFailedSources = MutableStateFlow<Boolean>(false)
    val showFailedSources: StateFlow<Boolean> = _showFailedSources.asStateFlow()

    fun toggleShowFailedSources() {
        _showFailedSources.value = !_showFailedSources.value
    }

    fun recordFailedSource(log: com.example.model.FailedSourceLog) {
        _failedSourceLogs.value = (_failedSourceLogs.value + log).takeLast(100)
    }

    // Theme & Appearance Settings
    val secureDnsPrefs = com.example.util.SecureDnsPreferences.getInstance(application)
    val isSecureDnsEnabled: StateFlow<Boolean> = secureDnsPrefs.isSecureDnsEnabled
    val selectedDnsProvider: StateFlow<com.example.util.DnsProvider> = secureDnsPrefs.selectedProvider
    val customDnsUrl: StateFlow<String> = secureDnsPrefs.customDnsUrl

    private val _dnsTestResult = MutableStateFlow<com.example.util.DnsTestResult?>(null)
    val dnsTestResult: StateFlow<com.example.util.DnsTestResult?> = _dnsTestResult.asStateFlow()

    fun setSecureDnsEnabled(enabled: Boolean) {
        secureDnsPrefs.setSecureDnsEnabled(enabled)
        com.example.util.SecureDnsManager.update(getApplication())
    }

    fun setSelectedDnsProvider(provider: com.example.util.DnsProvider) {
        secureDnsPrefs.setSelectedProvider(provider)
        com.example.util.SecureDnsManager.update(getApplication())
    }

    fun setCustomDnsUrl(url: String) {
        secureDnsPrefs.setCustomDnsUrl(url)
        com.example.util.SecureDnsManager.update(getApplication())
    }

    fun runDnsDiagnosticTest(testDomain: String = "pornhub.com") {
        viewModelScope.launch {
            _dnsTestResult.value = com.example.util.SecureDnsManager.testDnsResolution(getApplication(), testDomain)
        }
    }

    private val _adultContentEnabled = MutableStateFlow(
        settingsPrefs.getBoolean("adult_content_enabled", false)
    )
    val adultContentEnabled: StateFlow<Boolean> = _adultContentEnabled.asStateFlow()

    private val _showThumbnailTags = MutableStateFlow(
        settingsPrefs.getBoolean("show_thumbnail_tags", true)
    )
    val showThumbnailTags: StateFlow<Boolean> = _showThumbnailTags.asStateFlow()

    fun setShowThumbnailTags(show: Boolean) {
        _showThumbnailTags.value = show
        settingsPrefs.edit().putBoolean("show_thumbnail_tags", show).apply()
    }

    private val adultIdsList = listOf("eporner", "pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn", "spankbang", "hanime1", "hqporner")
    private val normalIdsList = listOf("youtube", "twitch", "torrent", "archive_org", "dailymotion", "bilibili", "vimeo", "hotstar", "bun-tel-meg")

    fun setAdultContentEnabled(enabled: Boolean) {
        _adultContentEnabled.value = enabled
        settingsPrefs.edit().putBoolean("adult_content_enabled", enabled).apply()
        val newSet = mutableSetOf<String>()
        if (enabled) {
            newSet.addAll(adultIdsList)
            if (!isAdultProviderId(_activeProviderId.value)) {
                _activeProviderId.value = "pornhub"
            }
        } else {
            newSet.addAll(normalIdsList)
            newSet.add("all")
            if (isAdultProviderId(_activeProviderId.value)) {
                _activeProviderId.value = "all"
            }
        }
        _enabledProviderIds.value = newSet
        settingsPrefs.edit().putStringSet("enabled_provider_ids", newSet).apply()
        refreshProvidersList()
        loadTrending(forceRefresh = true)
    }

    fun toggleProviderEnabled(providerId: String, isEnabled: Boolean) {
        val currentSet = _enabledProviderIds.value.toMutableSet()
        if (isEnabled) {
            currentSet.add(providerId)
        } else {
            currentSet.remove(providerId)
            if (_activeProviderId.value == providerId) {
                _activeProviderId.value = "all"
            }
        }
        _enabledProviderIds.value = currentSet
        settingsPrefs.edit().putStringSet("enabled_provider_ids", currentSet).apply()
        refreshProvidersList()
        loadTrending(forceRefresh = true)
    }

    private val _themeMode = MutableStateFlow(
        try { ThemeMode.valueOf(settingsPrefs.getString("theme_mode", ThemeMode.AMOLED_DARK.name) ?: ThemeMode.AMOLED_DARK.name) } catch (e: Exception) { ThemeMode.AMOLED_DARK }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(
        try { AppAccentColor.valueOf(settingsPrefs.getString("accent_color", AppAccentColor.YELLOW.name) ?: AppAccentColor.YELLOW.name) } catch (e: Exception) { AppAccentColor.YELLOW }
    )
    val accentColor: StateFlow<AppAccentColor> = _accentColor.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        settingsPrefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setAccentColor(accent: AppAccentColor) {
        _accentColor.value = accent
        settingsPrefs.edit().putString("accent_color", accent.name).apply()
    }

    // --- Battery Saver & Performance Optimization Engine ---
    val batterySaverManager: com.example.util.BatterySaverManager = com.example.util.BatterySaverManager.getInstance(getApplication())
    val isPowerSaveActive: StateFlow<Boolean> = batterySaverManager.isPowerSaveActive
    val batteryLevel: StateFlow<Int> = batterySaverManager.batteryLevel
    val isBatteryCharging: StateFlow<Boolean> = batterySaverManager.isCharging
    val isOsPowerSave: StateFlow<Boolean> = batterySaverManager.isOsPowerSave
    val batterySaverManualEnabled: StateFlow<Boolean> = batterySaverManager.manualEnabled
    val batterySaverAutoOnLow: StateFlow<Boolean> = batterySaverManager.autoOnLowBattery
    val batterySaverLowBatteryThreshold: StateFlow<Int> = batterySaverManager.lowBatteryThreshold
    val batterySaverResolutionCap: StateFlow<String> = batterySaverManager.resolutionCap
    val batterySaverDisableAmbient: StateFlow<Boolean> = batterySaverManager.disableAmbientGlow
    val batterySaverLowPowerTorrent: StateFlow<Boolean> = batterySaverManager.lowPowerTorrent
    val batterySaverDisableAnimations: StateFlow<Boolean> = batterySaverManager.disableAnimations
    val batterySaverPureBlackAmoled: StateFlow<Boolean> = batterySaverManager.pureBlackAmoled
    val batterySaverAudioOnlyForMusic: StateFlow<Boolean> = batterySaverManager.audioOnlyForMusic

    private val _appCacheSizeBytes = MutableStateFlow(0L)
    val appCacheSizeBytes: StateFlow<Long> = _appCacheSizeBytes.asStateFlow()

    init {
        com.example.ui.player.GlobalPlayerManager.setPlaybackFailedListener { httpStatus ->
            tryNextFallbackStream(httpStatus)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _appCacheSizeBytes.value = batterySaverManager.calculateCacheSizeBytes()
            val cachedFeed = com.example.util.HomeFeedCacheManager.loadCachedFeed(getApplication())
            if (cachedFeed.isNotEmpty() && _trendingVideos.value.isEmpty()) {
                _trendingVideos.value = cachedFeed
                _isLoadingTrending.value = false
                com.example.util.ThumbnailOptimizer.preloadThumbnails(getApplication(), cachedFeed)
            }
        }
    }

    fun refreshAppCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _appCacheSizeBytes.value = batterySaverManager.calculateCacheSizeBytes()
        }
    }

    fun clearAppCache(): Long {
        val freed = batterySaverManager.clearAppCaches()
        _appCacheSizeBytes.value = batterySaverManager.calculateCacheSizeBytes()
        return freed
    }

    fun setBatterySaverManual(enabled: Boolean) {
        batterySaverManager.setManualEnabled(enabled)
    }

    fun setBatterySaverAutoOnLow(enabled: Boolean) {
        batterySaverManager.setAutoOnLowBattery(enabled)
    }

    fun setBatterySaverLowThreshold(threshold: Int) {
        batterySaverManager.setLowBatteryThreshold(threshold)
    }

    fun setBatterySaverResolutionCap(cap: String) {
        batterySaverManager.setResolutionCap(cap)
    }

    fun setBatterySaverDisableAmbient(disabled: Boolean) {
        batterySaverManager.setDisableAmbientGlow(disabled)
    }

    fun setBatterySaverLowPowerTorrent(enabled: Boolean) {
        batterySaverManager.setLowPowerTorrent(enabled)
    }

    fun setBatterySaverDisableAnimations(disabled: Boolean) {
        batterySaverManager.setDisableAnimations(disabled)
    }

    fun setBatterySaverPureBlackAmoled(enabled: Boolean) {
        batterySaverManager.setPureBlackAmoled(enabled)
    }

    fun setBatterySaverAudioOnlyForMusic(enabled: Boolean) {
        batterySaverManager.setAudioOnlyForMusic(enabled)
    }

    val repositories: StateFlow<List<String>> = MutableStateFlow<List<String>>(emptyList()).asStateFlow()
    val extensionStatuses: StateFlow<List<String>> = MutableStateFlow<List<String>>(emptyList()).asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedChannelName = MutableStateFlow<String?>(null)
    val selectedChannelName: StateFlow<String?> = _selectedChannelName.asStateFlow()

    private val _selectedChannelAvatarUrl = MutableStateFlow<String?>(null)
    val selectedChannelAvatarUrl: StateFlow<String?> = _selectedChannelAvatarUrl.asStateFlow()

    private val _channelVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val channelVideos: StateFlow<List<VideoItem>> = _channelVideos.asStateFlow()

    private val _channelDetails = MutableStateFlow<com.example.model.ChannelDetails?>(null)
    val channelDetails: StateFlow<com.example.model.ChannelDetails?> = _channelDetails.asStateFlow()

    private val _isChannelLoading = MutableStateFlow(false)
    val isChannelLoading: StateFlow<Boolean> = _isChannelLoading.asStateFlow()

    private val _videoComments = MutableStateFlow<List<com.example.model.VideoComment>>(emptyList())
    val videoComments: StateFlow<List<com.example.model.VideoComment>> = _videoComments.asStateFlow()

    private val _isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoading: StateFlow<Boolean> = _isCommentsLoading.asStateFlow()

    private var commentsJob: Job? = null
    fun loadVideoComments(videoId: String, providerId: String? = null, videoTitle: String? = null) {
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch(Dispatchers.IO) {
            _isCommentsLoading.value = true
            try {
                val fetched = com.example.util.CommentExtractorHelper.fetchComments(
                    videoId = videoId,
                    providerId = providerId,
                    videoTitle = videoTitle
                )
                _videoComments.value = fetched
            } catch (e: Exception) {
                Log.w("MainViewModel", "loadVideoComments error: ${e.message}")
            } finally {
                _isCommentsLoading.value = false
            }
        }
    }

    fun addComment(commentText: String, videoId: String) {
        val text = commentText.trim()
        if (text.isBlank()) return
        val newComment = com.example.model.VideoComment(
            id = "user_cmt_${System.currentTimeMillis()}",
            authorName = "You",
            authorAvatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80",
            commentText = text,
            timeAgo = "Just now",
            likeCount = 1,
            isLikedByMe = true,
            sourceBadge = "Your Comment"
        )
        _videoComments.value = listOf(newComment) + _videoComments.value
    }

    fun toggleCommentLike(commentId: String) {
        _videoComments.value = _videoComments.value.map { c ->
            if (c.id == commentId) {
                val nowLiked = !c.isLikedByMe
                c.copy(
                    isLikedByMe = nowLiked,
                    likeCount = if (nowLiked) c.likeCount + 1 else (c.likeCount - 1).coerceAtLeast(0)
                )
            } else c
        }
    }

    private val _tvSeasons = MutableStateFlow<List<com.example.model.SeriesSeason>>(emptyList())
    val tvSeasons: StateFlow<List<com.example.model.SeriesSeason>> = _tvSeasons.asStateFlow()

    private val _isSeasonsLoading = MutableStateFlow(false)
    val isSeasonsLoading: StateFlow<Boolean> = _isSeasonsLoading.asStateFlow()

    private var seasonsJob: Job? = null
    fun loadTvSeasons(streamData: StreamData) {
        seasonsJob?.cancel()
        _tvSeasons.value = emptyList()
        _isSeasonsLoading.value = false

        val prov = (streamData.providerId ?: "").lowercase()
        val isTorrentOrVega = prov == "torrent" || prov == "vega" || prov.startsWith("vega_")
        val isArchive = prov == "archive" || prov == "archive_org" || prov == "archive.org"
        val isArchiveMultiVideo = isArchive && streamData.availableStreamOptions.size > 1

        if (!isTorrentOrVega && !isArchiveMultiVideo) {
            return
        }

        if (isTorrentOrVega) {
            val titleLower = streamData.title.lowercase()
            val isTvSeries = titleLower.contains("season") || titleLower.contains("s0") ||
                    titleLower.contains("s1") || titleLower.contains("s2") ||
                    titleLower.contains("episode") || titleLower.contains("ep0") ||
                    titleLower.contains(" complete ") || streamData.videoId.contains("tv_")
            if (!isTvSeries) {
                return
            }
        }

        seasonsJob = viewModelScope.launch(Dispatchers.IO) {
            _isSeasonsLoading.value = true
            try {
                val seasons = com.example.util.TMDBHelper.fetchTvSeasonsAndEpisodes(streamData)
                _tvSeasons.value = seasons
            } catch (e: Exception) {
                Log.w("MainViewModel", "loadTvSeasons error: ${e.message}")
                _tvSeasons.value = emptyList()
            } finally {
                _isSeasonsLoading.value = false
            }
        }
    }

    private val _subscriptionVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val subscriptionVideos: StateFlow<List<VideoItem>> = _subscriptionVideos.asStateFlow()

    private val _isSubscriptionLoading = MutableStateFlow(false)
    val isSubscriptionLoading: StateFlow<Boolean> = _isSubscriptionLoading.asStateFlow()

    private val _isPipMode = MutableStateFlow(false)
    val isPipMode: StateFlow<Boolean> = _isPipMode.asStateFlow()

    fun setPipMode(enabled: Boolean) {
        _isPipMode.value = enabled
    }

    private val adultProviderIds = setOf("eporner", "pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn", "apijav", "spankbang", "hanime1", "hqporner")

    fun isAdultProviderId(providerId: String?): Boolean {
        if (providerId.isNullOrBlank()) return false
        val lower = providerId.lowercase()
        return adultProviderIds.any { lower.contains(it) }
    }

    fun isAdultVideoItem(item: VideoItem): Boolean {
        if (isAdultProviderId(item.providerId)) return true
        val text = "${item.title} ${item.uploaderName} ${item.description}".lowercase()
        val adultKeywords = listOf("eporner", "pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn", "apijav", "spankbang", "hanime1", "hqporner", "adult", "nsfw", "porn", "xxx", "erotic", "hentai", "sex")
        return adultKeywords.any { text.contains(it) }
    }

    fun isAdultSearchQuery(query: String): Boolean {
        val q = query.lowercase()
        val adultKeywords = listOf("eporner", "pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn", "apijav", "spankbang", "hanime1", "hqporner", "adult", "nsfw", "porn", "xxx", "erotic", "hentai", "sex")
        return adultKeywords.any { q.contains(it) }
    }
    fun isAdultDownload(entity: OfflineDownloadEntity): Boolean {
        val combined = "${entity.title} ${entity.channelName} ${entity.videoId}".lowercase()
        return isAdultSearchQuery(combined)
    }

    fun isDemoOrPlaceholderVideo(item: VideoItem): Boolean {
        val lowerId = item.id.lowercase()
        val lowerTitle = item.title.lowercase()
        val lowerDesc = (item.description ?: "").lowercase()
        return lowerId.contains("bigbuckbunny") || lowerTitle.contains("big buck bunny") || lowerDesc.contains("big buck bunny")
    }

    private val _activeProviderId = MutableStateFlow("all")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _enabledProviderIds = MutableStateFlow<Set<String>>({
        val saved = settingsPrefs.getStringSet("enabled_provider_ids", null)
        val isAdult = settingsPrefs.getBoolean("adult_content_enabled", false)
        if (saved != null && saved.isNotEmpty()) {
            val filtered = saved.filterTo(mutableSetOf()) { pid ->
                if (pid == "all") true
                else if (isAdult) isAdultProviderId(pid)
                else !isAdultProviderId(pid)
            }
            filtered.add("all")
            if (isAdult) {
                filtered.addAll(adultIdsList)
            } else {
                filtered.addAll(normalIdsList)
            }
            filtered
        } else {
            if (isAdult) {
                (setOf("all") + adultIdsList).toSet()
            } else {
                (setOf("all") + normalIdsList).toSet()
            }
        }
    }())
    val enabledProviderIds: StateFlow<Set<String>> = _enabledProviderIds.asStateFlow()

    private val _availableProviders = MutableStateFlow<List<ProviderUiItem>>(emptyList())
    val availableProviders: StateFlow<List<ProviderUiItem>> = _availableProviders.asStateFlow()

    val vegaRepository = com.example.vega.VegaProviderRepository(getApplication())
    val installedVegaProviders: StateFlow<List<com.example.vega.InstalledVegaProvider>> = vegaRepository.installedProviders
    val vegaServerUrl: StateFlow<String> = vegaRepository.serverUrl

    private val _availableVegaProviders = MutableStateFlow<List<String>>(emptyList())
    val availableVegaProviders: StateFlow<List<String>> = _availableVegaProviders.asStateFlow()

    private val _isFetchingVegaProviders = MutableStateFlow(false)
    val isFetchingVegaProviders: StateFlow<Boolean> = _isFetchingVegaProviders.asStateFlow()

    private val _vegaProviderError = MutableStateFlow<String?>(null)
    val vegaProviderError: StateFlow<String?> = _vegaProviderError.asStateFlow()

    private val _providerHealthMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val providerHealthMap: StateFlow<Map<String, String>> = _providerHealthMap.asStateFlow()

    private val _isTestingVegaHealth = MutableStateFlow(false)
    val isTestingVegaHealth: StateFlow<Boolean> = _isTestingVegaHealth.asStateFlow()

    private val _watchProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val watchProgressMap: StateFlow<Map<String, Float>> = _watchProgressMap.asStateFlow()

    private val _watchPositionMsMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val watchPositionMsMap: StateFlow<Map<String, Long>> = _watchPositionMsMap.asStateFlow()

    private val _hiddenVideoIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet("hidden_video_ids", emptySet()) ?: emptySet()
    )
    val hiddenVideoIds: StateFlow<Set<String>> = _hiddenVideoIds.asStateFlow()

    private val _notInterestedChannels = MutableStateFlow<Set<String>>(
        prefs.getStringSet("blocked_channels", emptySet()) ?: emptySet()
    )
    val notInterestedChannels: StateFlow<Set<String>> = _notInterestedChannels.asStateFlow()

    private val _notInterestedVideoIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet("hidden_video_ids", emptySet()) ?: emptySet()
    )
    val notInterestedVideoIds: StateFlow<Set<String>> = _notInterestedVideoIds.asStateFlow()

    private val _appOpenStreak = MutableStateFlow<Int>(
        settingsPrefs.getInt("app_open_streak_days", 1)
    )
    val appOpenStreak: StateFlow<Int> = _appOpenStreak.asStateFlow()

    private val _longestAppStreak = MutableStateFlow<Int>(
        settingsPrefs.getInt("longest_app_streak_days", 1)
    )
    val longestAppStreak: StateFlow<Int> = _longestAppStreak.asStateFlow()

    fun isBlockedVideo(item: VideoItem): Boolean {
        if (isDemoOrPlaceholderVideo(item)) return true

        val vid = item.id.trim()
        val ch = item.uploaderName?.trim()?.lowercase() ?: ""
        val hidden = _hiddenVideoIds.value
        val notInt = _notInterestedVideoIds.value
        val blockedChans = _notInterestedChannels.value

        if (vid.isNotEmpty() && (hidden.contains(vid) || notInt.contains(vid))) return true
        if (ch.isNotEmpty() && blockedChans.contains(ch)) return true

        // Filter out videos where watched fraction >= 85%
        val watchedFraction = _watchProgressMap.value[vid] ?: 0f
        if (watchedFraction >= 0.85f) return true

        return false
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeSearchSanitizedResult = MutableStateFlow<com.example.util.SmartSearchSanitizer.CleanQueryResult?>(null)
    val activeSearchSanitizedResult: StateFlow<com.example.util.SmartSearchSanitizer.CleanQueryResult?> = _activeSearchSanitizedResult.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<SearchSuggestionItem>>(emptyList())
    val searchSuggestions: StateFlow<List<SearchSuggestionItem>> = _searchSuggestions.asStateFlow()

    private val _searchResults = MutableStateFlow<List<VideoItem>>(emptyList())
    val searchResults: StateFlow<List<VideoItem>> = combine(
        _searchResults,
        _hiddenVideoIds,
        _notInterestedVideoIds,
        _notInterestedChannels,
        _adultContentEnabled
    ) { list, hidden, notInt, blockedChans, adultEnabled ->
        list.filter { item ->
            !isBlockedVideo(item) && (adultEnabled || !isAdultVideoItem(item))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _searchFilter = MutableStateFlow(SearchFilterState())
    val searchFilter: StateFlow<SearchFilterState> = _searchFilter.asStateFlow()

    fun updateSearchFilter(filter: SearchFilterState) {
        _searchFilter.value = filter
    }

    fun resetSearchFilter() {
        _searchFilter.value = SearchFilterState()
    }

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val initialCachedHomeFeed: List<VideoItem> = try {
        com.example.util.HomeFeedCacheManager.loadCachedFeed(application)
    } catch (_: Exception) {
        emptyList()
    }

    private val _trendingVideos = MutableStateFlow<List<VideoItem>>(initialCachedHomeFeed)
    val trendingVideos: StateFlow<List<VideoItem>> = combine(
        _trendingVideos,
        _hiddenVideoIds,
        _notInterestedVideoIds,
        _notInterestedChannels,
        _adultContentEnabled
    ) { list, hidden, notInt, blockedChans, adultEnabled ->
        list.filter { item ->
            !isBlockedVideo(item) && (adultEnabled || !isAdultVideoItem(item))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialCachedHomeFeed)

    private val _isLoadingTrending = MutableStateFlow(initialCachedHomeFeed.isEmpty())
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _playerRecommendations = MutableStateFlow<List<VideoItem>>(emptyList())
    val playerRecommendations: StateFlow<List<VideoItem>> = combine(
        _playerRecommendations,
        _hiddenVideoIds,
        _notInterestedVideoIds,
        _notInterestedChannels,
        _adultContentEnabled
    ) { list, _, _, _, adultEnabled ->
        list.filter { item ->
            !isBlockedVideo(item) && (adultEnabled || !isAdultVideoItem(item))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var playerRecsPage = 1
    private val _isLoadingPlayerRecs = MutableStateFlow(false)
    val isLoadingPlayerRecs: StateFlow<Boolean> = _isLoadingPlayerRecs.asStateFlow()

    private var currentTrendingPage = 1
    private var currentSearchPage = 1

    private val _feedError = MutableStateFlow<FeedErrorDetails?>(null)
    val feedError: StateFlow<FeedErrorDetails?> = _feedError.asStateFlow()

    private val _activeVideoId = MutableStateFlow<String?>(null)
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()

    private val _activeVideoItem = MutableStateFlow<VideoItem?>(null)
    val activeVideoItem: StateFlow<VideoItem?> = _activeVideoItem.asStateFlow()

    private val _extractionResult = MutableStateFlow<YouTubeExtractorHelper.ExtractionResult?>(null)
    val extractionResult: StateFlow<YouTubeExtractorHelper.ExtractionResult?> = _extractionResult.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _selectedStreamOption = MutableStateFlow<PlayableStreamOption?>(null)
    val selectedStreamOption: StateFlow<PlayableStreamOption?> = _selectedStreamOption.asStateFlow()

    private val _serverScanState = MutableStateFlow(ServerScanState())
    val serverScanState: StateFlow<ServerScanState> = _serverScanState.asStateFlow()

    fun selectServerNode(nodeId: String) {
        val node = _serverScanState.value.nodes.find { it.id == nodeId }
        if (node?.streamOption != null) {
            _selectedStreamOption.value = node.streamOption
            _serverScanState.value = _serverScanState.value.copy(selectedNodeId = nodeId)
        }
    }

    fun selectServerIndex(index: Int) {
        val currentNode = _serverScanState.value.nodes.getOrNull(index - 1)
        _serverScanState.value = _serverScanState.value.copy(activeServerIndex = index)
        if (currentNode != null) {
            selectServerNode(currentNode.id)
        }
    }

    private val _showShortsFeed = MutableStateFlow(false)
    val showShortsFeed: StateFlow<Boolean> = _showShortsFeed.asStateFlow()

    fun setShowShortsFeed(show: Boolean) {
        _showShortsFeed.value = show
    }

    private val _playbackQueue = MutableStateFlow<List<VideoItem>>(emptyList())
    val playbackQueue: StateFlow<List<VideoItem>> = combine(
        _playbackQueue,
        _hiddenVideoIds,
        _notInterestedVideoIds,
        _notInterestedChannels,
        _adultContentEnabled
    ) { list, hidden, notInt, blockedChans, adultEnabled ->
        list.filter { item ->
            val vid = item.id.trim()
            val ch = item.uploaderName?.trim()?.lowercase() ?: ""
            val isBlocked = (vid.isNotEmpty() && (hidden.contains(vid) || notInt.contains(vid))) ||
                    (ch.isNotEmpty() && blockedChans.contains(ch))
            !isBlocked && (adultEnabled || !isAdultVideoItem(item))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _watchLaterList = MutableStateFlow<List<VideoItem>>(emptyList())
    val watchLaterList: StateFlow<List<VideoItem>> = combine(
        _watchLaterList,
        _hiddenVideoIds,
        _notInterestedVideoIds,
        _notInterestedChannels,
        _adultContentEnabled
    ) { list, hidden, notInt, blockedChans, adultEnabled ->
        list.filter { item ->
            val vid = item.id.trim()
            val ch = item.uploaderName?.trim()?.lowercase() ?: ""
            val isBlocked = (vid.isNotEmpty() && (hidden.contains(vid) || notInt.contains(vid))) ||
                    (ch.isNotEmpty() && blockedChans.contains(ch))
            !isBlocked && (adultEnabled || !isAdultVideoItem(item))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Watch History, Watch Progress (YouTube-style red bar), Likes & Dislikes Pattern Understanding
    private val _watchHistory = MutableStateFlow<List<VideoItem>>(emptyList())
    val watchHistory: StateFlow<List<VideoItem>> = combine(
        _watchHistory,
        _hiddenVideoIds,
        _notInterestedVideoIds,
        _notInterestedChannels,
        _adultContentEnabled
    ) { list, hidden, notInt, blockedChans, adultEnabled ->
        list.filter { item ->
            val vid = item.id.trim()
            val ch = item.uploaderName?.trim()?.lowercase() ?: ""
            val isBlocked = (vid.isNotEmpty() && (hidden.contains(vid) || notInt.contains(vid))) ||
                    (ch.isNotEmpty() && blockedChans.contains(ch))
            !isBlocked && (adultEnabled || !isAdultVideoItem(item))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _likedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val likedVideoIds: StateFlow<Set<String>> = _likedVideoIds.asStateFlow()

    private val _likedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val likedVideos: StateFlow<List<VideoItem>> = _likedVideos.asStateFlow()

    private val _dislikedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val dislikedVideoIds: StateFlow<Set<String>> = _dislikedVideoIds.asStateFlow()

    fun markNotInterested(video: VideoItem) {
        markNotInterested(video.id, video.uploaderName)
    }

    fun markNotInterested(videoId: String, channelName: String? = null) {
        val cleanVid = videoId.trim()
        if (cleanVid.isEmpty()) return
        val cleanChannel = channelName?.trim() ?: ""

        val updatedIds = _hiddenVideoIds.value + cleanVid
        _hiddenVideoIds.value = updatedIds
        _notInterestedVideoIds.value = _notInterestedVideoIds.value + cleanVid
        val updatedChans = if (cleanChannel.isNotBlank()) {
            _notInterestedChannels.value + cleanChannel.lowercase()
        } else {
            _notInterestedChannels.value
        }
        _notInterestedChannels.value = updatedChans

        prefs.edit()
            .putStringSet("hidden_video_ids", updatedIds)
            .putStringSet("blocked_channels", updatedChans)
            .apply()

        // 3. Purge immediately from all in-memory lists
        _trendingVideos.value = _trendingVideos.value.filterNot { isBlockedVideo(it) }
        _searchResults.value = _searchResults.value.filterNot { isBlockedVideo(it) }
        _recommendedVideos.value = _recommendedVideos.value.filterNot { isBlockedVideo(it) }
        _playbackQueue.value = _playbackQueue.value.filterNot { isBlockedVideo(it) }
        _watchLaterList.value = _watchLaterList.value.filterNot { isBlockedVideo(it) }
        _watchHistory.value = _watchHistory.value.filterNot { isBlockedVideo(it) }

        // 4. Remove from Room DB bookmarks & history asynchronously
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userDataDao.deleteBookmark(cleanVid)
                userDataDao.deleteWatchHistory(cleanVid)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting bookmark/history for not-interested item", e)
            }
        }

        // 5. If currently playing this video, close or advance player
        if (_activeVideoId.value == cleanVid) {
            playNextInQueue()
        }

        // 6. Update recommendations engine
        updateRecommendedVideosAsync()
    }

    fun removeNotInterested(videoId: String) {
        val cleanVid = videoId.trim()
        if (cleanVid.isEmpty()) return
        val updatedIds = _hiddenVideoIds.value - cleanVid
        _hiddenVideoIds.value = updatedIds
        _notInterestedVideoIds.value = _notInterestedVideoIds.value - cleanVid
        prefs.edit().putStringSet("hidden_video_ids", updatedIds).apply()
        updateRecommendedVideosAsync()
    }

    fun setNotInterestedData(hiddenIds: Set<String>, blockedChannels: Set<String>) {
        _hiddenVideoIds.value = hiddenIds
        _notInterestedVideoIds.value = hiddenIds
        _notInterestedChannels.value = blockedChannels
        _trendingVideos.value = _trendingVideos.value.filterNot { isBlockedVideo(it) }
        _searchResults.value = _searchResults.value.filterNot { isBlockedVideo(it) }
        _recommendedVideos.value = _recommendedVideos.value.filterNot { isBlockedVideo(it) }
        _playbackQueue.value = _playbackQueue.value.filterNot { isBlockedVideo(it) }
        _watchLaterList.value = _watchLaterList.value.filterNot { isBlockedVideo(it) }
        _watchHistory.value = _watchHistory.value.filterNot { isBlockedVideo(it) }
        updateRecommendedVideosAsync()
    }

    /**
     * Serializes all user watch history, bookmarks, liked videos, playlists,
     * dislike preferences, and blocklists into a portable JSON string.
     */
    fun exportUserDataJson(): String {
        val historyEntities = _watchHistory.value.map { video ->
            WatchHistoryEntity(
                videoId = video.id,
                title = video.title ?: video.id,
                channelName = video.uploaderName ?: "",
                thumbnailUrl = video.thumbnailUrl,
                providerId = video.providerId ?: "youtube",
                progressFraction = _watchProgressMap.value[video.id] ?: 0.5f
            )
        }

        val bookmarkEntities = _watchLaterList.value.map { video ->
            BookmarkEntity(
                videoId = video.id,
                title = video.title ?: video.id,
                channelName = video.uploaderName ?: "",
                thumbnailUrl = video.thumbnailUrl,
                providerId = video.providerId ?: "youtube"
            )
        }

        val likedEntities = _likedVideos.value.map { video ->
            LikedVideoEntity(
                videoId = video.id,
                title = video.title ?: video.id,
                channelName = video.uploaderName ?: "",
                thumbnailUrl = video.thumbnailUrl,
                providerId = video.providerId ?: "youtube"
            )
        }

        val playlistEntities = _userPlaylists.value.map { pl ->
            UserPlaylistEntity(
                id = pl.id,
                title = pl.title,
                createdAt = System.currentTimeMillis(),
                videosJson = serializeVideos(pl.videos)
            )
        }

        val backupData = com.example.util.UserDataBackupManager.BackupData(
            watchHistory = historyEntities,
            bookmarks = bookmarkEntities,
            likedVideos = likedEntities,
            dislikedVideoIds = _dislikedVideoIds.value.toList(),
            playlists = playlistEntities,
            hiddenVideoIds = _hiddenVideoIds.value.toList(),
            notInterestedVideoIds = _notInterestedVideoIds.value.toList(),
            notInterestedChannels = _notInterestedChannels.value.toList(),
            recentSearches = _recentSearches.value,
            watchProgressMap = _watchProgressMap.value
        )

        return com.example.util.UserDataBackupManager.exportToJson(backupData)
    }

    /**
     * Imports user profile JSON string, updating Room database, SharedPreferences, and live ViewModel state.
     */
    suspend fun importUserDataJson(jsonString: String): com.example.util.UserDataBackupManager.ImportSummary = withContext(Dispatchers.IO) {
        val backup = com.example.util.UserDataBackupManager.importFromJson(jsonString)

        // 1. Save to Room DB
        backup.watchHistory.forEach { userDataDao.insertWatchHistory(it) }
        backup.bookmarks.forEach { userDataDao.insertBookmark(it) }
        backup.likedVideos.forEach { userDataDao.insertLikedVideo(it) }
        backup.playlists.forEach { userDataDao.insertOrUpdatePlaylist(it) }

        // 2. Update SharedPreferences blocklists
        val updatedHidden = (_hiddenVideoIds.value + backup.hiddenVideoIds + backup.notInterestedVideoIds).toSet()
        val updatedBlockedChans = (_notInterestedChannels.value + backup.notInterestedChannels).toSet()

        prefs.edit()
            .putStringSet("hidden_video_ids", updatedHidden)
            .putStringSet("blocked_channels", updatedBlockedChans)
            .apply()

        // 3. Update State Flows in memory
        _hiddenVideoIds.value = updatedHidden
        _notInterestedVideoIds.value = (_notInterestedVideoIds.value + backup.notInterestedVideoIds).toSet()
        _notInterestedChannels.value = updatedBlockedChans
        _dislikedVideoIds.value = (_dislikedVideoIds.value + backup.dislikedVideoIds).toSet()
        _watchProgressMap.value = _watchProgressMap.value + backup.watchProgressMap

        if (backup.recentSearches.isNotEmpty()) {
            val combinedSearches = (_recentSearches.value + backup.recentSearches).distinct()
            _recentSearches.value = combinedSearches
            saveRecentSearches(combinedSearches)
        }

        // 4. Trigger AI recommendation engine re-calculation
        updateRecommendedVideosAsync()

        com.example.util.UserDataBackupManager.ImportSummary(
            historyCount = backup.watchHistory.size,
            bookmarkCount = backup.bookmarks.size,
            likedCount = backup.likedVideos.size,
            playlistCount = backup.playlists.size,
            blockedChannelsCount = backup.notInterestedChannels.size,
            hiddenCount = backup.hiddenVideoIds.size
        )
    }

    suspend fun restoreGoogleDriveBackup(): Boolean = withContext(Dispatchers.IO) {
        val backup = com.example.util.GoogleDriveSyncManager.restoreFromGoogleDrive(getApplication())
        var totalRestored = 0

        backup.history.forEach { video ->
            userDataDao.insertWatchHistory(
                WatchHistoryEntity(
                    videoId = video.id,
                    title = video.title ?: video.id,
                    channelName = video.uploaderName ?: "",
                    thumbnailUrl = video.thumbnailUrl,
                    providerId = video.providerId ?: "youtube",
                    progressFraction = 0.5f
                )
            )
            totalRestored++
        }

        backup.likedVideos.forEach { video ->
            userDataDao.insertLikedVideo(
                LikedVideoEntity(
                    videoId = video.id,
                    title = video.title ?: video.id,
                    channelName = video.uploaderName ?: "",
                    thumbnailUrl = video.thumbnailUrl,
                    providerId = video.providerId ?: "youtube"
                )
            )
            totalRestored++
        }

        backup.watchLaterList.forEach { video ->
            userDataDao.insertBookmark(
                BookmarkEntity(
                    videoId = video.id,
                    title = video.title ?: video.id,
                    channelName = video.uploaderName ?: "",
                    thumbnailUrl = video.thumbnailUrl,
                    providerId = video.providerId ?: "youtube"
                )
            )
            totalRestored++
        }

        backup.playlists.forEach { pl ->
            userDataDao.insertOrUpdatePlaylist(
                UserPlaylistEntity(
                    id = pl.id,
                    title = pl.title,
                    createdAt = System.currentTimeMillis(),
                    videosJson = serializeVideos(pl.videos)
                )
            )
            totalRestored++
        }

        val json = com.example.util.GoogleDriveSyncManager.getBackupJson(getApplication())
        if (!json.isNullOrBlank()) {
            try {
                importUserDataJson(json)
            } catch (_: Exception) {}
        }
        totalRestored > 0
    }

    private val _isSearchExpanded = MutableStateFlow(false)
    val isSearchExpanded: StateFlow<Boolean> = _isSearchExpanded.asStateFlow()

    fun setSearchExpanded(expanded: Boolean) {
        _isSearchExpanded.value = expanded
        if (!expanded) {
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            _activeSearchSanitizedResult.value = null
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchExpanded.value = false
        _activeSearchSanitizedResult.value = null
    }

    private val _recommendedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val recommendedVideos: StateFlow<List<VideoItem>> = combine(
        _recommendedVideos,
        _hiddenVideoIds,
        _notInterestedVideoIds,
        _notInterestedChannels,
        _adultContentEnabled
    ) { list, hidden, notInt, blockedChans, adultEnabled ->
        list.filter { item ->
            !isBlockedVideo(item) && (adultEnabled || !isAdultVideoItem(item))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun updateRecommendedVideosAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _recommendedVideos.value = getRecommendedVideos()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating recommendations", e)
            }
        }
    }

    private var lastRecordedProgressTime = 0L

    fun recordWatchProgress(videoId: String, currentPositionMs: Long, totalDurationMs: Long) {
        if (totalDurationMs <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastRecordedProgressTime < 1500L) return
        lastRecordedProgressTime = now

        val fraction = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        val currentFraction = _watchProgressMap.value[videoId] ?: -1f
        if (kotlin.math.abs(fraction - currentFraction) >= 0.01f) {
            _watchProgressMap.value = _watchProgressMap.value + (videoId to fraction)
            _watchPositionMsMap.value = _watchPositionMsMap.value + (videoId to currentPositionMs)
            com.example.util.PlaybackResumeManager.savePosition(getApplication(), videoId, currentPositionMs, totalDurationMs)
        }
    }

    fun recordVideoView(video: VideoItem) {
        val filtered = _watchHistory.value.filterNot { it.id == video.id }
        _watchHistory.value = listOf(video) + filtered
        val savedFraction = com.example.util.PlaybackResumeManager.getSavedFraction(getApplication(), video.id)
        val savedPos = com.example.util.PlaybackResumeManager.getSavedPosition(getApplication(), video.id)
        if (savedFraction > 0f) {
            _watchProgressMap.value = _watchProgressMap.value + (video.id to savedFraction)
        }
        if (savedPos > 0L) {
            _watchPositionMsMap.value = _watchPositionMsMap.value + (video.id to savedPos)
        }
        val historyEntity = WatchHistoryEntity(
            videoId = video.id,
            title = video.title ?: video.id,
            channelName = video.uploaderName ?: "",
            thumbnailUrl = video.thumbnailUrl,
            providerId = video.providerId,
            progressFraction = _watchProgressMap.value[video.id] ?: savedFraction
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userDataDao.insertWatchHistory(historyEntity)
            } catch (t: Throwable) {
                Log.e("MainViewModel", "Error saving watch history", t)
            }
        }
        updateRecommendedVideosAsync()
    }

    fun removeFromWatchHistory(video: VideoItem) {
        _watchHistory.value = _watchHistory.value.filterNot { it.id == video.id }
        viewModelScope.launch(Dispatchers.IO) {
            userDataDao.deleteWatchHistory(video.id)
        }
    }

    fun clearWatchHistory() {
        _watchHistory.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            userDataDao.clearWatchHistory()
        }
    }

    fun toggleLikeVideo(videoId: String) {
        val current = _likedVideoIds.value
        if (current.contains(videoId)) {
            _likedVideoIds.value = current - videoId
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    userDataDao.deleteLikedVideo(videoId)
                } catch (t: Throwable) {
                    Log.e("MainViewModel", "Error deleting liked video", t)
                }
            }
        } else {
            _likedVideoIds.value = current + videoId
            _dislikedVideoIds.value = _dislikedVideoIds.value - videoId
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val matchingItem = (_searchResults.value + _trendingVideos.value + _watchHistory.value).firstOrNull { it.id == videoId }
                    userDataDao.insertLikedVideo(
                        LikedVideoEntity(
                            videoId = videoId,
                            title = matchingItem?.title ?: videoId,
                            channelName = matchingItem?.uploaderName ?: "",
                            thumbnailUrl = matchingItem?.thumbnailUrl,
                            providerId = matchingItem?.providerId
                        )
                    )
                } catch (t: Throwable) {
                    Log.e("MainViewModel", "Error inserting liked video", t)
                }
            }
        }
        updateRecommendedVideosAsync()
    }

    fun toggleDislikeVideo(videoId: String) {
        val current = _dislikedVideoIds.value
        if (current.contains(videoId)) {
            _dislikedVideoIds.value = current - videoId
        } else {
            _dislikedVideoIds.value = current + videoId
            _likedVideoIds.value = _likedVideoIds.value - videoId
        }
        updateRecommendedVideosAsync()
    }

    fun setLikedVideoIds(ids: Set<String>) {
        _likedVideoIds.value = ids
    }

    fun setDislikedVideoIds(ids: Set<String>) {
        _dislikedVideoIds.value = ids
    }

    fun setWatchProgressMap(map: Map<String, Float>) {
        _watchProgressMap.value = map
    }

    fun setWatchHistory(history: List<VideoItem>) {
        _watchHistory.value = history
    }

    fun setRecentSearches(searches: List<String>) {
        _recentSearches.value = searches
        saveRecentSearches(searches)
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        saveRecentSearches(emptyList())
    }

    fun clearHistory() {
        clearWatchHistory()
    }

    fun setWatchLaterList(list: List<VideoItem>) {
        _watchLaterList.value = list
    }

    fun setUserPlaylists(playlists: List<UserPlaylist>) {
        _userPlaylists.value = playlists
    }

    /**
     * Smart Multi-Signal Recommendation Engine Pipeline:
     * Ranks videos using multi-signal taste vectors, completion ratios,
     * search intent & search tokens, channel affinity, time-of-day circadian learning, and channel diversity caps.
     */
    fun getRecommendedVideos(): List<VideoItem> {
        val allAvailable = (_trendingVideos.value + _searchResults.value + _searchDrivenRecommendations.value)
            .distinctBy { (it.providerId ?: "") + "_" + it.id }
            .filterNot { isBlockedVideo(it) }
        if (allAvailable.isEmpty()) return emptyList()

        val tasteVector = com.example.recommendation.SmartRecommendationEngine.computeTasteVector(
            watchHistory = _watchHistory.value,
            watchProgressMap = _watchProgressMap.value,
            likedVideoIds = _likedVideoIds.value,
            dislikedVideoIds = _dislikedVideoIds.value,
            bookmarks = _watchLaterList.value,
            notInterestedChannels = _notInterestedChannels.value,
            recentSearches = _recentSearches.value,
            watchPositionMsMap = _watchPositionMsMap.value
        )

        val ranked = com.example.recommendation.SmartRecommendationEngine.rankCandidateVideos(
            candidates = allAvailable,
            tasteVector = tasteVector,
            activeVideo = _activeVideoItem.value,
            blockedVideoIds = _hiddenVideoIds.value + _notInterestedVideoIds.value,
            blockedChannels = _notInterestedChannels.value
        )

        return ranked.take(25)
    }

    /**
     * Re-ranks any candidate video list (e.g. Home Feed or Search) using the user's live taste vector with search intent awareness.
     */
    fun rankFeedWithRecommendations(rawList: List<VideoItem>): List<VideoItem> {
        if (rawList.isEmpty()) return emptyList()
        val tasteVector = com.example.recommendation.SmartRecommendationEngine.computeTasteVector(
            watchHistory = _watchHistory.value,
            watchProgressMap = _watchProgressMap.value,
            likedVideoIds = _likedVideoIds.value,
            dislikedVideoIds = _dislikedVideoIds.value,
            bookmarks = _watchLaterList.value,
            notInterestedChannels = _notInterestedChannels.value,
            recentSearches = _recentSearches.value,
            watchPositionMsMap = _watchPositionMsMap.value
        )

        return com.example.recommendation.SmartRecommendationEngine.rankCandidateVideos(
            candidates = rawList,
            tasteVector = tasteVector,
            activeVideo = null,
            blockedVideoIds = _hiddenVideoIds.value + _notInterestedVideoIds.value,
            blockedChannels = _notInterestedChannels.value
        )
    }

    /**
     * Retrieves the current user AI taste profile summary based on all tracked signals.
     */
    fun getUserTasteSummary(): com.example.recommendation.SmartRecommendationEngine.TasteSummary {
        val tasteVector = com.example.recommendation.SmartRecommendationEngine.computeTasteVector(
            watchHistory = _watchHistory.value,
            watchProgressMap = _watchProgressMap.value,
            likedVideoIds = _likedVideoIds.value,
            dislikedVideoIds = _dislikedVideoIds.value,
            bookmarks = _watchLaterList.value,
            notInterestedChannels = _notInterestedChannels.value,
            recentSearches = _recentSearches.value,
            watchPositionMsMap = _watchPositionMsMap.value
        )
        return com.example.recommendation.SmartRecommendationEngine.buildTasteSummary(tasteVector)
    }

    // Contextual Search-Driven Recommendations for Home Screen
    private val _searchDrivenRecommendations = MutableStateFlow<List<VideoItem>>(emptyList())
    val searchDrivenRecommendations: StateFlow<List<VideoItem>> = _searchDrivenRecommendations.asStateFlow()

    private val _latestSearchIntent = MutableStateFlow<String?>(null)
    val latestSearchIntent: StateFlow<String?> = _latestSearchIntent.asStateFlow()

    private var searchRecsJob: kotlinx.coroutines.Job? = null

    fun refreshSearchDrivenRecommendations() {
        searchRecsJob?.cancel()
        searchRecsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val latest = _recentSearches.value.firstOrNull { it.isNotBlank() }
                if (latest.isNullOrBlank()) {
                    _searchDrivenRecommendations.value = emptyList()
                    _latestSearchIntent.value = null
                    return@launch
                }

                _latestSearchIntent.value = latest

                // Proactively discover related media across providers
                val discovered = mutableListOf<VideoItem>()
                val adultEnabled = _adultContentEnabled.value

                // 1. Fetch search related items from YouTube
                try {
                    val ytItems = com.example.extractor.YouTubeExtractorHelper.searchYouTube(latest, getApplication())
                    discovered.addAll(ytItems.take(8))
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Search recs YT error", e)
                }

                // 2. Fetch search related items from TMDB / Archive
                try {
                    val tmdbItems = com.example.extractor.MultiSourceProvider.search(getApplication(), "tmdb", latest, 6)
                    discovered.addAll(tmdbItems)
                } catch (e: Exception) {
                    // Continue
                }

                try {
                    val archiveItems = com.example.extractor.ArchiveOrgProvider.search(latest, 1)
                    discovered.addAll(archiveItems.take(4))
                } catch (e: Exception) {
                    // Continue
                }

                val cleanFiltered = discovered
                    .filterNot { isBlockedVideo(it) }
                    .filter { adultEnabled || !isAdultVideoItem(it) }
                    .distinctBy { (it.providerId ?: "") + "_" + it.id }

                val tasteVector = com.example.recommendation.SmartRecommendationEngine.computeTasteVector(
                    watchHistory = _watchHistory.value,
                    watchProgressMap = _watchProgressMap.value,
                    likedVideoIds = _likedVideoIds.value,
                    dislikedVideoIds = _dislikedVideoIds.value,
                    bookmarks = _watchLaterList.value,
                    notInterestedChannels = _notInterestedChannels.value,
                    recentSearches = _recentSearches.value,
                    watchPositionMsMap = _watchPositionMsMap.value
                )

                val ranked = com.example.recommendation.SmartRecommendationEngine.rankCandidateVideos(
                    candidates = cleanFiltered,
                    tasteVector = tasteVector,
                    blockedVideoIds = _hiddenVideoIds.value + _notInterestedVideoIds.value,
                    blockedChannels = _notInterestedChannels.value
                )

                _searchDrivenRecommendations.value = ranked.take(12)

                // Also infuse candidate items smoothly into the general trending pool
                if (ranked.isNotEmpty()) {
                    val currentTrending = _trendingVideos.value
                    val infused = (ranked.take(4) + currentTrending).distinctBy { (it.providerId ?: "") + "_" + it.id }
                    _trendingVideos.value = infused
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to refresh search recommendations", e)
            }
        }
    }

    private val _userPlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val userPlaylists: StateFlow<List<UserPlaylist>> = _userPlaylists.asStateFlow()

    fun addToQueue(video: VideoItem) {
        if (_activeVideoId.value == null) {
            playVideo(video.id, video.providerId)
        } else {
            // Append to end of queue without duplicates
            _playbackQueue.value = _playbackQueue.value.filterNot { it.id == video.id } + video
        }
    }

    fun playNextInQueue(video: VideoItem) {
        if (_activeVideoId.value == null) {
            playVideo(video.id, video.providerId)
        } else {
            // Insert at front of queue without duplicates
            _playbackQueue.value = listOf(video) + _playbackQueue.value.filterNot { it.id == video.id }
        }
    }

    fun playFromQueue(video: VideoItem) {
        _playbackQueue.value = _playbackQueue.value.filterNot { it.id == video.id }
        playVideo(video.id, video.providerId)
    }

    fun removeFromQueue(video: VideoItem) {
        _playbackQueue.value = _playbackQueue.value.filterNot { it.id == video.id }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val list = _playbackQueue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _playbackQueue.value = list
        }
    }

    fun clearQueue() {
        _playbackQueue.value = emptyList()
    }

    fun addToWatchLater(video: VideoItem) {
        if (_watchLaterList.value.none { it.id == video.id }) {
            _watchLaterList.value = listOf(video) + _watchLaterList.value
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    userDataDao.insertBookmark(
                        BookmarkEntity(
                            videoId = video.id,
                            title = video.title ?: video.id,
                            channelName = video.uploaderName ?: "",
                            thumbnailUrl = video.thumbnailUrl,
                            providerId = video.providerId
                        )
                    )
                } catch (t: Throwable) {
                    Log.e("MainViewModel", "Error adding to Watch Later", t)
                }
            }
        }
    }

    fun isExploreMediaSaved(mediaId: String): Boolean {
        return _watchLaterList.value.any { it.id == mediaId }
    }

    fun toggleSaveExploreMedia(item: com.example.model.ExploreMediaItem) {
        val isAlreadySaved = isExploreMediaSaved(item.id)
        if (isAlreadySaved) {
            _watchLaterList.value = _watchLaterList.value.filterNot { it.id == item.id }
            viewModelScope.launch(Dispatchers.IO) {
                userDataDao.deleteBookmark(item.id)
            }
        } else {
            val videoItem = VideoItem(
                id = item.id,
                title = item.title,
                uploaderName = "${item.mediaType.label} • ${if (item.releaseYear.isNotBlank()) item.releaseYear else "Popular"}",
                thumbnailUrl = item.posterUrl ?: item.backdropUrl,
                providerId = item.source.name.lowercase(),
                description = item.overview
            )
            _watchLaterList.value = listOf(videoItem) + _watchLaterList.value.filterNot { it.id == item.id }
            viewModelScope.launch(Dispatchers.IO) {
                userDataDao.insertBookmark(
                    BookmarkEntity(
                        videoId = videoItem.id,
                        title = videoItem.title,
                        channelName = videoItem.uploaderName,
                        thumbnailUrl = videoItem.thumbnailUrl,
                        providerId = videoItem.providerId
                    )
                )
            }
        }
    }

    fun removeFromWatchLater(video: VideoItem) {
        _watchLaterList.value = _watchLaterList.value.filter { it.id != video.id }
        viewModelScope.launch(Dispatchers.IO) {
            userDataDao.deleteBookmark(video.id)
        }
    }

    fun removeWatchedFromWatchLater() {
        val historyIds = _watchHistory.value.map { it.id }.toSet()
        val progressMap = _watchProgressMap.value
        val toRemove = _watchLaterList.value.filter { video ->
            val prog = progressMap[video.id] ?: 0f
            prog >= 0.85f || historyIds.contains(video.id)
        }
        if (toRemove.isNotEmpty()) {
            val toRemoveIds = toRemove.map { it.id }.toSet()
            _watchLaterList.value = _watchLaterList.value.filterNot { toRemoveIds.contains(it.id) }
            viewModelScope.launch(Dispatchers.IO) {
                toRemove.forEach { userDataDao.deleteBookmark(it.id) }
            }
        }
    }

    fun removeUnavailableFromWatchLater() {
        val toRemove = _watchLaterList.value.filter { video ->
            video.id.isBlank() || video.title.isNullOrBlank() || isBlockedVideo(video)
        }
        if (toRemove.isNotEmpty()) {
            val toRemoveIds = toRemove.map { it.id }.toSet()
            _watchLaterList.value = _watchLaterList.value.filterNot { toRemoveIds.contains(it.id) }
            viewModelScope.launch(Dispatchers.IO) {
                toRemove.forEach { userDataDao.deleteBookmark(it.id) }
            }
        }
    }

    fun clearWatchLater() {
        _watchLaterList.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            userDataDao.clearBookmarks()
        }
    }

    fun setWatchLaterOrder(reorderedList: List<VideoItem>) {
        _watchLaterList.value = reorderedList
    }

    fun removeWatchedFromPlaylist(playlistId: String) {
        val historyIds = _watchHistory.value.map { it.id }.toSet()
        val progressMap = _watchProgressMap.value
        _userPlaylists.value = _userPlaylists.value.map { pl ->
            if (pl.id == playlistId) {
                val updatedVideos = pl.videos.filterNot { video ->
                    val prog = progressMap[video.id] ?: 0f
                    prog >= 0.85f || historyIds.contains(video.id)
                }
                val updated = pl.copy(videos = updatedVideos)
                viewModelScope.launch(Dispatchers.IO) {
                    userDataDao.insertOrUpdatePlaylist(
                        UserPlaylistEntity(id = playlistId, title = pl.title, videosJson = serializeVideos(updated.videos))
                    )
                }
                updated
            } else pl
        }
    }

    fun removeUnavailableFromPlaylist(playlistId: String) {
        _userPlaylists.value = _userPlaylists.value.map { pl ->
            if (pl.id == playlistId) {
                val updatedVideos = pl.videos.filterNot { video ->
                    video.id.isBlank() || video.title.isNullOrBlank() || isBlockedVideo(video)
                }
                val updated = pl.copy(videos = updatedVideos)
                viewModelScope.launch(Dispatchers.IO) {
                    userDataDao.insertOrUpdatePlaylist(
                        UserPlaylistEntity(id = playlistId, title = pl.title, videosJson = serializeVideos(updated.videos))
                    )
                }
                updated
            } else pl
        }
    }

    fun clearPlaylist(playlistId: String) {
        _userPlaylists.value = _userPlaylists.value.map { pl ->
            if (pl.id == playlistId) {
                val updated = pl.copy(videos = emptyList())
                viewModelScope.launch(Dispatchers.IO) {
                    userDataDao.insertOrUpdatePlaylist(
                        UserPlaylistEntity(id = playlistId, title = pl.title, videosJson = "[]")
                    )
                }
                updated
            } else pl
        }
    }

    fun reorderPlaylist(playlistId: String, newVideos: List<VideoItem>) {
        _userPlaylists.value = _userPlaylists.value.map { pl ->
            if (pl.id == playlistId) {
                val updated = pl.copy(videos = newVideos)
                viewModelScope.launch(Dispatchers.IO) {
                    userDataDao.insertOrUpdatePlaylist(
                        UserPlaylistEntity(id = playlistId, title = pl.title, videosJson = serializeVideos(updated.videos))
                    )
                }
                updated
            } else pl
        }
    }

    fun createPlaylist(title: String) {
        val newId = System.currentTimeMillis().toString()
        val newPl = UserPlaylist(id = newId, title = title, videos = emptyList())
        _userPlaylists.value = _userPlaylists.value + newPl
        viewModelScope.launch(Dispatchers.IO) {
            userDataDao.insertOrUpdatePlaylist(
                UserPlaylistEntity(id = newId, title = title, videosJson = "[]")
            )
        }
    }

    fun addToPlaylist(playlistId: String, video: VideoItem) {
        _userPlaylists.value = _userPlaylists.value.map { pl ->
            if (pl.id == playlistId) {
                if (pl.videos.none { it.id == video.id }) {
                    val updated = pl.copy(videos = pl.videos + video)
                    viewModelScope.launch(Dispatchers.IO) {
                        userDataDao.insertOrUpdatePlaylist(
                            UserPlaylistEntity(id = playlistId, title = pl.title, videosJson = serializeVideos(updated.videos))
                        )
                    }
                    updated
                } else pl
            } else pl
        }
    }

    fun removeFromPlaylist(playlistId: String, video: VideoItem) {
        _userPlaylists.value = _userPlaylists.value.map { pl ->
            if (pl.id == playlistId) {
                val updated = pl.copy(videos = pl.videos.filter { it.id != video.id })
                viewModelScope.launch(Dispatchers.IO) {
                    userDataDao.insertOrUpdatePlaylist(
                        UserPlaylistEntity(id = playlistId, title = pl.title, videosJson = serializeVideos(updated.videos))
                    )
                }
                updated
            } else pl
        }
    }

    fun deletePlaylist(playlistId: String) {
        _userPlaylists.value = _userPlaylists.value.filter { it.id != playlistId }
        viewModelScope.launch(Dispatchers.IO) {
            userDataDao.deletePlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: String, newTitle: String) {
        _userPlaylists.value = _userPlaylists.value.map { pl ->
            if (pl.id == playlistId) {
                val updated = pl.copy(title = newTitle)
                viewModelScope.launch(Dispatchers.IO) {
                    userDataDao.insertOrUpdatePlaylist(
                        UserPlaylistEntity(id = playlistId, title = newTitle, videosJson = serializeVideos(updated.videos))
                    )
                }
                updated
            } else pl
        }
    }

    fun playNextInQueue() {
        val currentQueue = _playbackQueue.value
        if (currentQueue.isNotEmpty()) {
            val nextVideo = currentQueue.first()
            _playbackQueue.value = currentQueue.drop(1)
            playVideo(nextVideo.id, nextVideo.providerId)
        } else {
            val curId = _activeVideoId.value
            val candidate = _playerRecommendations.value.firstOrNull { it.id != curId && !isBlockedVideo(it) }
                ?: _trendingVideos.value.firstOrNull { it.id != curId && !isBlockedVideo(it) }
            if (candidate != null) {
                playVideo(candidate.id, candidate.providerId)
            }
        }
    }

    // User Profile & SharedPreferences Persistence
    private val profilePrefs = getApplication<Application>().getSharedPreferences("user_profile_prefs", android.content.Context.MODE_PRIVATE)

    // Subscriptions Management
    private val subPrefs = getApplication<Application>().getSharedPreferences("subscriptions_prefs", android.content.Context.MODE_PRIVATE)

    private fun loadSubscribedChannels(): List<com.example.model.SubscribedChannel> {
        val jsonStr = subPrefs.getString("subscribed_channels_json", null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<com.example.model.SubscribedChannel>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        com.example.model.SubscribedChannel(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            handle = obj.optString("handle"),
                            avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() },
                            subscriberCount = obj.optString("subscriberCount", "1.2M subscribers"),
                            hasUnreadUpdates = obj.optBoolean("hasUnreadUpdates", true),
                            notificationEnabled = obj.optBoolean("notificationEnabled", true),
                            description = obj.optString("description")
                        )
                    )
                }
                return list
            } catch (e: Exception) {
                // fallback
            }
        }
        return emptyList()
    }

    private val _subscribedChannels = MutableStateFlow<List<com.example.model.SubscribedChannel>>(loadSubscribedChannels())
    val subscribedChannels: StateFlow<List<com.example.model.SubscribedChannel>> = _subscribedChannels.asStateFlow()

    private val _selectedSubscriptionChannelId = MutableStateFlow<String?>(null)
    val selectedSubscriptionChannelId: StateFlow<String?> = _selectedSubscriptionChannelId.asStateFlow()

    private val _subscriptionFilterChip = MutableStateFlow("All")
    val subscriptionFilterChip: StateFlow<String> = _subscriptionFilterChip.asStateFlow()

    fun selectSubscriptionChannel(channelId: String?) {
        _selectedSubscriptionChannelId.value = if (_selectedSubscriptionChannelId.value == channelId) null else channelId
    }

    fun setSubscriptionFilterChip(chip: String) {
        _subscriptionFilterChip.value = chip
    }

    fun isSubscribed(channelName: String): Boolean {
        if (channelName.isBlank()) return false
        val clean = channelName.trim().lowercase()
        return _subscribedChannels.value.any { it.name.trim().lowercase() == clean || it.handle.trim().lowercase() == clean }
    }

    fun toggleSubscription(channelName: String, avatarUrl: String? = null, handle: String? = null) {
        if (channelName.isBlank()) return
        val clean = channelName.trim()
        val existing = _subscribedChannels.value.find { it.name.equals(clean, ignoreCase = true) }
        val updatedList = if (existing != null) {
            _subscribedChannels.value.filter { it.id != existing.id }
        } else {
            val newChan = com.example.model.SubscribedChannel(
                id = clean.lowercase().replace("[^a-z0-9]".toRegex(), "_").take(30),
                name = clean,
                handle = handle ?: "@${clean.replace(" ", "")}",
                avatarUrl = avatarUrl,
                subscriberCount = "Subscribed",
                hasUnreadUpdates = false
            )
            listOf(newChan) + _subscribedChannels.value
        }
        _subscribedChannels.value = updatedList
        saveSubscribedChannels(updatedList)

        val isNowSub = updatedList.any { it.name.equals(clean, ignoreCase = true) }
        _channelDetails.value?.let { current ->
            if (current.name.equals(clean, ignoreCase = true)) {
                _channelDetails.value = current.copy(isSubscribed = isNowSub)
            }
        }
        loadSubscriptionFeed()
    }

    private var subscriptionFeedJob: Job? = null
    fun loadSubscriptionFeed() {
        subscriptionFeedJob?.cancel()
        subscriptionFeedJob = viewModelScope.launch(Dispatchers.IO) {
            _isSubscriptionLoading.value = true
            try {
                val subs = _subscribedChannels.value
                val feed = mutableListOf<VideoItem>()
                val existingFeed = _trendingVideos.value + _recommendedVideos.value

                if (subs.isNotEmpty()) {
                    // 1. First collect existing cached items matching subscribed creators
                    subs.forEach { sub ->
                        val matching = existingFeed.filter { item ->
                            item.uploaderName.equals(sub.name, ignoreCase = true) ||
                            item.uploaderName.contains(sub.name, ignoreCase = true) ||
                            sub.name.contains(item.uploaderName, ignoreCase = true)
                        }
                        feed.addAll(matching)
                    }

                    // 2. Fetch fresh videos from top subscribed creators
                    for (sub in subs.take(6)) {
                        try {
                            val results = YouTubeExtractorHelper.searchYouTube(sub.name, getApplication())
                            val matches = results.filter {
                                it.uploaderName.equals(sub.name, ignoreCase = true) ||
                                it.uploaderName.contains(sub.name, ignoreCase = true) ||
                                sub.name.contains(it.uploaderName, ignoreCase = true)
                            }.take(8)
                            if (matches.isNotEmpty()) {
                                feed.addAll(matches)
                            } else {
                                feed.addAll(results.take(4))
                            }
                        } catch (e: Exception) {
                            Log.w("MainViewModel", "Failed fetching feed for ${sub.name}: ${e.message}")
                        }
                    }
                }

                // If feed is still small, enrich with top trending items
                if (feed.size < 6 && existingFeed.isNotEmpty()) {
                    feed.addAll(existingFeed.take(15))
                }

                _subscriptionVideos.value = feed.distinctBy { it.id }
            } catch (e: Exception) {
                Log.w("MainViewModel", "loadSubscriptionFeed failed: ${e.message}")
            } finally {
                _isSubscriptionLoading.value = false
            }
        }
    }

    fun toggleSubscriptionNotification(channelId: String) {
        val updated = _subscribedChannels.value.map {
            if (it.id == channelId) it.copy(notificationEnabled = !it.notificationEnabled) else it
        }
        _subscribedChannels.value = updated
        saveSubscribedChannels(updated)
    }

    private fun saveSubscribedChannels(channels: List<com.example.model.SubscribedChannel>) {
        val arr = JSONArray()
        channels.forEach { ch ->
            val obj = JSONObject()
            obj.put("id", ch.id)
            obj.put("name", ch.name)
            obj.put("handle", ch.handle)
            obj.put("avatarUrl", ch.avatarUrl ?: "")
            obj.put("subscriberCount", ch.subscriberCount)
            obj.put("hasUnreadUpdates", ch.hasUnreadUpdates)
            obj.put("notificationEnabled", ch.notificationEnabled)
            obj.put("description", ch.description)
            arr.put(obj)
        }
        subPrefs.edit().putString("subscribed_channels_json", arr.toString()).apply()
    }

    private val _userProfile = MutableStateFlow(
        UserProfile(
            name = profilePrefs.getString("user_name", "Lucifer") ?: "Lucifer",
            handle = profilePrefs.getString("user_handle", "@lucifer") ?: "@lucifer",
            bio = profilePrefs.getString("user_bio", "Passionate video lover & content curator.") ?: "Passionate video lover & content curator.",
            avatarUrl = profilePrefs.getString("user_avatar_url", null),
            avatarPreset = profilePrefs.getString("user_avatar_preset", "purple") ?: "purple"
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    fun updateUserProfile(name: String, handle: String, bio: String, avatarUrl: String?, avatarPreset: String = "purple") {
        val updatedName = name.ifBlank { "Lucifer" }
        val updatedHandle = if (handle.startsWith("@")) handle else "@$handle"
        val updatedBio = bio.ifBlank { "Passionate video lover & content curator." }
        val updatedAvatarUrl = avatarUrl?.ifBlank { null }

        val updated = UserProfile(
            name = updatedName,
            handle = updatedHandle,
            bio = updatedBio,
            avatarUrl = updatedAvatarUrl,
            avatarPreset = avatarPreset
        )
        _userProfile.value = updated

        profilePrefs.edit()
            .putString("user_name", updated.name)
            .putString("user_handle", updated.handle)
            .putString("user_bio", updated.bio)
            .putString("user_avatar_url", updated.avatarUrl)
            .putString("user_avatar_preset", updated.avatarPreset)
            .apply()
    }

    // Session Greeting state (plays only on app launch / restart)
    private val _hasShownGreeting = MutableStateFlow(false)
    val hasShownGreeting: StateFlow<Boolean> = _hasShownGreeting.asStateFlow()

    fun markGreetingShown() {
        _hasShownGreeting.value = true
    }

    // Offline Downloads State & Bottom Sheet Management
    private val _offlineDownloads = MutableStateFlow<List<OfflineDownloadEntity>>(emptyList())
    val offlineDownloads: StateFlow<List<OfflineDownloadEntity>> = combine(_offlineDownloads, _adultContentEnabled) { list, adultEnabled ->
        if (adultEnabled) list else list.filterNot { isAdultDownload(it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val downloadRepository by lazy { com.example.downloader.DownloadRepository(getApplication()) }

    var downloadSheetVideoItem by mutableStateOf<VideoItem?>(null)
        private set
    var downloadSheetStreamData by mutableStateOf<StreamData?>(null)
        private set
    var isDownloadSheetVisible by mutableStateOf(false)
        private set

    fun showDownloadSheet(videoItem: VideoItem, streamData: StreamData? = null) {
        downloadSheetVideoItem = videoItem
        downloadSheetStreamData = streamData
        isDownloadSheetVisible = true
    }

    fun dismissDownloadSheet() {
        isDownloadSheetVisible = false
        downloadSheetVideoItem = null
        downloadSheetStreamData = null
    }

    fun startDownload(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        streamOption: PlayableStreamOption? = null
    ) {
        val url = streamOption?.videoUrl ?: return
        downloadRepository.enqueueDownload(
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            qualityLabel = qualityLabel,
            downloadUrl = url
        )
    }

    fun pauseDownload(videoId: String) {
        downloadRepository.pauseDownload(videoId)
    }

    fun resumeDownload(videoId: String) {
        val dl = _offlineDownloads.value.firstOrNull { it.videoId == videoId } ?: return
        val currentOption = _selectedStreamOption.value
        val url = currentOption?.videoUrl ?: return
        downloadRepository.resumeDownload(
            videoId = videoId,
            title = dl.title,
            channelName = dl.channelName,
            thumbnailUrl = dl.thumbnailUrl,
            qualityLabel = dl.qualityLabel,
            downloadUrl = url
        )
    }

    fun deleteDownload(videoId: String, localFilePath: String? = null) {
        downloadRepository.deleteDownload(videoId, localFilePath)
    }

    fun clearAllDownloads() {
        _offlineDownloads.value.forEach { dl ->
            downloadRepository.deleteDownload(dl.videoId, dl.localFilePath)
        }
    }

    fun playOfflineDownload(download: OfflineDownloadEntity) {
        val file = java.io.File(download.localFilePath)
        val fileUri = if (file.exists()) "file://${file.absolutePath}" else download.localFilePath
        val isHls = download.localFilePath.endsWith(".m3u8", ignoreCase = true)

        val option = PlayableStreamOption(
            qualityLabel = download.qualityLabel,
            format = if (isHls) "hls" else "mp4",
            isMuxed = true,
            videoUrl = fileUri
        )

        val streamData = StreamData(
            videoId = download.videoId,
            videoUrl = fileUri,
            title = download.title,
            channelName = download.channelName,
            thumbnailUrl = download.thumbnailUrl,
            availableStreamOptions = listOf(option),
            selectedStreamOption = option
        )

        _activeVideoId.value = download.videoId
        _activeVideoItem.value = VideoItem(
            id = download.videoId,
            title = download.title,
            uploaderName = download.channelName,
            thumbnailUrl = download.thumbnailUrl,
            providerId = "offline"
        )
        _selectedStreamOption.value = option
        _extractionResult.value = YouTubeExtractorHelper.ExtractionResult.Success(streamData)
        _isExtracting.value = false
        _isPlaying.value = true
        _currentScreen.value = AppScreen.PLAYER

        com.example.ui.player.GlobalPlayerManager.prepareAndPlay(
            context = getApplication(),
            streamData = streamData,
            streamOption = option,
            hlsUrl = null,
            captionOption = null
        )
    }

    private val _selectedCaptionOption = MutableStateFlow<CaptionOption?>(null)
    val selectedCaptionOption: StateFlow<CaptionOption?> = _selectedCaptionOption.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun togglePlayback() {
        _isPlaying.value = !_isPlaying.value
    }

    private var activePlaybackJob: Job? = null

    fun closeVideo() {
        activePlaybackJob?.cancel()
        activePlaybackJob = null
        com.example.ui.player.GlobalPlayerManager.stopAndClear()
        _activeVideoId.value = null
        _activeVideoItem.value = null
        _extractionResult.value = null
        _selectedStreamOption.value = null
        _selectedCaptionOption.value = null
        _isPlaying.value = false
        if (_currentScreen.value == AppScreen.PLAYER) {
            _currentScreen.value = AppScreen.HOME
        }
    }

    init {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = settingsPrefs.getString("last_app_open_date", null)
        val currentStreak = settingsPrefs.getInt("app_open_streak_days", 1)
        val longestStreak = settingsPrefs.getInt("longest_app_streak_days", 1)
        if (lastDate != today) {
            val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(System.currentTimeMillis() - 86400000L))
            val newStreak = if (lastDate == yesterday) currentStreak + 1 else 1
            val newLongest = maxOf(longestStreak, newStreak)
            settingsPrefs.edit()
                .putString("last_app_open_date", today)
                .putInt("app_open_streak_days", newStreak)
                .putInt("longest_app_streak_days", newLongest)
                .apply()
            _appOpenStreak.value = newStreak
            _longestAppStreak.value = newLongest
        }

        viewModelScope.launch(Dispatchers.IO) {
            launch {
                userDataDao.getWatchHistoryFlow().collect { historyEntities ->
                    _watchHistory.value = historyEntities.map { entity ->
                        VideoItem(
                            id = entity.videoId,
                            title = entity.title,
                            uploaderName = entity.channelName,
                            thumbnailUrl = entity.thumbnailUrl,
                            providerId = entity.providerId
                        )
                    }
                    val progressMap = historyEntities.associate { it.videoId to it.progressFraction }
                    _watchProgressMap.value = _watchProgressMap.value + progressMap
                }
            }
            launch {
                userDataDao.getBookmarksFlow().collect { bookmarkEntities ->
                    _watchLaterList.value = bookmarkEntities.map { entity ->
                        VideoItem(
                            id = entity.videoId,
                            title = entity.title,
                            uploaderName = entity.channelName,
                            thumbnailUrl = entity.thumbnailUrl,
                            providerId = entity.providerId
                        )
                    }
                }
            }
            launch {
                userDataDao.getLikedVideosFlow().collect { likedEntities ->
                    _likedVideoIds.value = likedEntities.map { it.videoId }.toSet()
                    _likedVideos.value = likedEntities.map { entity ->
                        VideoItem(
                            id = entity.videoId,
                            title = entity.title,
                            uploaderName = entity.channelName,
                            thumbnailUrl = entity.thumbnailUrl,
                            providerId = entity.providerId
                        )
                    }
                }
            }
            launch {
                userDataDao.getPlaylistsFlow().collect { playlistEntities ->
                    _userPlaylists.value = playlistEntities.map { entity ->
                        UserPlaylist(
                            id = entity.id,
                            title = entity.title,
                            videos = deserializeVideos(entity.videosJson)
                        )
                    }
                }
            }
            launch {
                userDataDao.getOfflineDownloadsFlow().collect { downloads ->
                    _offlineDownloads.value = downloads
                }
            }
            launch {
                val cached = com.example.util.HomeFeedCache.loadFeed(getApplication())
                if (cached.isNotEmpty() && _trendingVideos.value.isEmpty()) {
                    _trendingVideos.value = cached
                    _isLoadingTrending.value = false
                }
            }
            launch {
                refreshProvidersList()
                setActiveProvider("all")
            }
            launch {
                com.example.ui.player.GlobalPlayerManager.setPlaybackFailedListener {
                    viewModelScope.launch(Dispatchers.Main) {
                        tryNextFallbackStream()
                    }
                }
            }
            @OptIn(FlowPreview::class)
            launch {
                _searchQuery
                    .debounce(250)
                    .collectLatest { query ->
                        _searchSuggestions.value = emptyList()
                    }
            }
        }
    }


    fun navigateToScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun getActiveProvider(): Any? = null

    fun updateVegaServerUrl(newUrl: String) {
        val cleanUrl = newUrl.trim().trimEnd('/')
        if (cleanUrl.isNotBlank()) {
            vegaRepository.setServerUrl(cleanUrl)
            fetchAvailableVegaProviders()
        }
    }

    fun fetchAvailableVegaProviders() {
        _isFetchingVegaProviders.value = true
        _vegaProviderError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentServer = vegaRepository.getServerUrl()
                val list = com.example.vega.VegaProviderClient.getAvailableProviders(currentServer)
                _availableVegaProviders.value = list
                if (list.isEmpty()) {
                    _vegaProviderError.value = "No providers currently returned from server ($currentServer)"
                }
            } catch (e: Exception) {
                _vegaProviderError.value = e.message ?: "Failed to reach server"
            } finally {
                _isFetchingVegaProviders.value = false
            }
        }
    }

    fun installAllVegaProviders() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = _availableVegaProviders.value
            if (available.isNotEmpty()) {
                vegaRepository.installAllProviders(available)
                val newKeys = available.map { "vega_${it.trim().lowercase()}" }.toSet()
                _enabledProviderIds.value = _enabledProviderIds.value + newKeys
                refreshProvidersList()
                loadTrending(forceRefresh = true)
            }
        }
    }

    fun testVegaProvidersHealth() {
        if (_isTestingVegaHealth.value) return
        _isTestingVegaHealth.value = true
        val currentInstalled = vegaRepository.getInstalledProviders()
        val currentServer = vegaRepository.getServerUrl()

        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, String>()
            currentInstalled.forEach { prov ->
                map[prov.id] = "Testing..."
            }
            _providerHealthMap.value = map.toMap()

            currentInstalled.forEach { prov ->
                try {
                    val res = com.example.vega.VegaProviderClient.search(prov.id, "2024", currentServer)
                    if (res.isNotEmpty()) {
                        map[prov.id] = "Online (${res.size} items)"
                    } else {
                        val altRes = com.example.vega.VegaProviderClient.search(prov.id, "spider", currentServer)
                        if (altRes.isNotEmpty()) {
                            map[prov.id] = "Online (${altRes.size} items)"
                        } else {
                            map[prov.id] = "Unresponsive / Empty"
                        }
                    }
                } catch (e: Exception) {
                    map[prov.id] = "Error: ${e.message ?: "Failed"}"
                }
                _providerHealthMap.value = map.toMap()
            }
            _isTestingVegaHealth.value = false
        }
    }

    fun installVegaProvider(id: String) {
        val cleanId = id.trim().lowercase()
        val formattedName = com.example.vega.VegaProviderClient.formatProviderDisplayName(cleanId)
        vegaRepository.installProvider(cleanId, formattedName)
        _enabledProviderIds.value = _enabledProviderIds.value + "vega_$cleanId"
        refreshProvidersList()
    }

    fun uninstallVegaProvider(id: String) {
        val cleanId = id.trim().lowercase()
        vegaRepository.uninstallProvider(cleanId)
        val vegaKey = "vega_$cleanId"
        if (_activeProviderId.value == vegaKey) {
            _activeProviderId.value = "all"
        }
        _enabledProviderIds.value = _enabledProviderIds.value - vegaKey
        refreshProvidersList()
        loadTrending(forceRefresh = true)
    }

    fun toggleVegaProvider(id: String, isEnabled: Boolean) {
        val cleanId = id.trim().lowercase()
        vegaRepository.setProviderEnabled(cleanId, isEnabled)
        val vegaKey = "vega_$cleanId"
        if (isEnabled) {
            _enabledProviderIds.value = _enabledProviderIds.value + vegaKey
        } else {
            if (_activeProviderId.value == vegaKey) {
                _activeProviderId.value = "all"
            }
            _enabledProviderIds.value = _enabledProviderIds.value - vegaKey
        }
        refreshProvidersList()
    }

    fun reloadProviders() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshProvidersList()
            loadTrending()
        }
    }

    fun setActiveProvider(providerId: String) {
        _activeProviderId.value = providerId
        if (isAdultProviderId(providerId)) {
            _adultContentEnabled.value = true
            try {
                settingsPrefs.edit().putBoolean("adult_content_enabled", true).apply()
            } catch (_: Exception) {}
        }
        if (!_enabledProviderIds.value.contains(providerId)) {
            _enabledProviderIds.value = _enabledProviderIds.value + providerId
        }
        _searchResults.value = emptyList()
        _trendingVideos.value = emptyList()
        _searchQuery.value = ""
        refreshProvidersList()
        loadTrending(forceRefresh = true)
    }

    fun toggleProviderEnabled(providerId: String) {
        val current = _enabledProviderIds.value
        val newState = !current.contains(providerId)
        toggleProviderEnabled(providerId, newState)
    }

    private fun refreshProvidersList() {
        val activeId = _activeProviderId.value
        val enabledSet = _enabledProviderIds.value
        val adultEnabled = _adultContentEnabled.value
        val uiList = mutableListOf<ProviderUiItem>()

        if (adultEnabled) {
            // ONLY 18+ adult providers shown when 18+ toggle is enabled
            uiList.add(
                ProviderUiItem(
                    id = "all",
                    name = "All 18+ Sources",
                    description = "Aggregated 18+ feed combining all enabled adult providers",
                    category = "18+",
                    isEnabled = enabledSet.contains("all"),
                    isDefault = (activeId == "all")
                )
            )
            val adultProviders = listOf(
                Triple("pornhub", "Pornhub", "Pornhub video catalog"),
                Triple("xvideos", "XVideos", "XVideos video catalog"),
                Triple("eporner", "Eporner", "Eporner video catalog"),
                Triple("spankbang", "SpankBang", "SpankBang 4K/1080p video catalog"),
                Triple("hanime1", "Hanime1", "Hanime1 Anime & HLS video catalog"),
                Triple("hqporner", "HQPorner", "HQPorner Ultra HD 4K CDN catalog"),
                Triple("redtube", "RedTube", "RedTube video catalog"),
                Triple("xhamster", "XHamster", "XHamster video catalog"),
                Triple("beeg", "Beeg", "Beeg video catalog"),
                Triple("4tube", "4tube", "4tube video catalog"),
                Triple("rule34video", "Rule34Video", "Rule34Video animation catalog"),
                Triple("youporn", "YouPorn", "YouPorn video catalog")
            )
            for ((id, name, desc) in adultProviders) {
                uiList.add(
                    ProviderUiItem(
                        id = id,
                        name = name,
                        description = desc,
                        category = "18+",
                        isEnabled = enabledSet.contains(id),
                        isDefault = (activeId == id)
                    )
                )
            }
        } else {
            // ONLY normal provider sources shown when 18+ toggle is disabled
            uiList.add(
                ProviderUiItem(
                    id = "all",
                    name = "All Sources",
                    description = "Aggregated feed combining all enabled content providers",
                    category = "Aggregator",
                    isEnabled = enabledSet.contains("all"),
                    isDefault = (activeId == "all")
                )
            )
            uiList.add(
                ProviderUiItem(
                    id = "youtube",
                    name = "YouTube",
                    description = "YouTube fast stream resolution, video search & channel metadata",
                    category = "Video",
                    isEnabled = enabledSet.contains("youtube"),
                    isDefault = (activeId == "youtube")
                )
            )
            uiList.add(
                ProviderUiItem(
                    id = "twitch",
                    name = "Twitch",
                    description = "Twitch Live Streams, Top Gaming Highlights & VODs",
                    category = "Live/Video",
                    isEnabled = enabledSet.contains("twitch"),
                    isDefault = (activeId == "twitch")
                )
            )
            uiList.add(
                ProviderUiItem(
                    id = "archive_org",
                    name = "Archive.org",
                    description = "Internet Archive video catalog",
                    category = "Library",
                    isEnabled = enabledSet.contains("archive_org"),
                    isDefault = (activeId == "archive_org")
                )
            )
            uiList.add(
                ProviderUiItem(
                    id = "dailymotion",
                    name = "Dailymotion",
                    description = "Dailymotion video platform",
                    category = "Video",
                    isEnabled = enabledSet.contains("dailymotion"),
                    isDefault = (activeId == "dailymotion")
                )
            )
            uiList.add(
                ProviderUiItem(
                    id = "bilibili",
                    name = "Bilibili",
                    description = "Bilibili streaming catalog",
                    category = "Video",
                    isEnabled = enabledSet.contains("bilibili"),
                    isDefault = (activeId == "bilibili")
                )
            )
            uiList.add(
                ProviderUiItem(
                    id = "vimeo",
                    name = "Vimeo",
                    description = "Vimeo video catalog",
                    category = "Video",
                    isEnabled = enabledSet.contains("vimeo"),
                    isDefault = (activeId == "vimeo")
                )
            )
            uiList.add(
                ProviderUiItem(
                    id = "hotstar",
                    name = "Hotstar",
                    description = "Hotstar & JioHotstar streaming catalog",
                    category = "Video",
                    isEnabled = enabledSet.contains("hotstar"),
                    isDefault = (activeId == "hotstar")
                )
            )
            uiList.add(
                ProviderUiItem(
                    id = "bun-tel-meg",
                    name = "bun-tel-meg",
                    description = "Telegram Channels, MEGA Folders & Bunkr Albums video links",
                    category = "Cloud & Social",
                    isEnabled = enabledSet.contains("bun-tel-meg") || enabledSet.contains("bunkr"),
                    isDefault = (activeId == "bun-tel-meg")
                )
            )
            uiList.add(
                ProviderUiItem(
                    id = "torrent",
                    name = "Torrent (P2P)",
                    description = "Stream Movies, TV Series & Anime via native BitTorrent releases",
                    category = "Torrent",
                    providerType = com.example.model.ProviderType.TORRENT,
                    isEnabled = enabledSet.contains("torrent"),
                    isDefault = (activeId == "torrent")
                )
            )

            // Dynamic Vega Providers installed by user
            val installedVega = vegaRepository.getInstalledProviders()
            for (vp in installedVega) {
                val vId = "vega_${vp.id}"
                uiList.add(
                    ProviderUiItem(
                        id = vId,
                        name = vp.name,
                        description = "Vega media provider: ${vp.name}",
                        category = "Vega",
                        providerType = com.example.model.ProviderType.VEGA,
                        isEnabled = vp.isEnabled,
                        isDefault = (activeId == vId)
                    )
                )
            }
        }

        _availableProviders.value = uiList

        // Ensure active provider matches the current mode's provider list
        if (uiList.none { it.id == _activeProviderId.value }) {
            _activeProviderId.value = uiList.firstOrNull()?.id ?: "pornhub"
        }
    }

    private val videoCacheRepo = com.example.db.VideoCacheRepository(getApplication())

    private val searchPrefs = getApplication<Application>().getSharedPreferences("user_recent_searches", android.content.Context.MODE_PRIVATE)

    private fun loadRecentSearches(): List<String> {
        val raw = searchPrefs.getString("recent_history", null) ?: return emptyList()
        return try {
            raw.split("|||").filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRecentSearches(list: List<String>) {
        searchPrefs.edit().putString("recent_history", list.joinToString("|||")).apply()
    }

    private val _recentSearches = MutableStateFlow<List<String>>(loadRecentSearches())
    val recentSearches: StateFlow<List<String>> = combine(_recentSearches, _adultContentEnabled) { searches, adultEnabled ->
        if (adultEnabled) searches else searches.filterNot { isAdultSearchQuery(it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _trendingTopics = MutableStateFlow<List<String>>(com.example.util.ExploreMediaHelper.getCuratedTrendingTopics())
    val trendingTopics: StateFlow<List<String>> = _trendingTopics.asStateFlow()

    fun loadTrendingTopics() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topics = com.example.util.ExploreMediaHelper.fetchTrendingSearchTopics()
                if (topics.isNotEmpty()) {
                    _trendingTopics.value = topics
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to load real trending topics: ${e.message}")
            }
        }
    }

    init {
        refreshProvidersList()
        fetchAvailableVegaProviders()
        loadTrending()
        loadTrendingTopics()
        loadSubscriptionFeed()
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.watchLaterFlow.collect { items ->
                _watchLaterList.value = items
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.watchHistoryFlow.collect { items ->
                _watchHistory.value = items
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.likedVideosFlow.collect { items ->
                _likedVideos.value = items
                _likedVideoIds.value = items.map { it.id }.toSet()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.userPlaylistsFlow.collect { playlists ->
                _userPlaylists.value = playlists
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            videoCacheRepo.searchHistoryFlow.collect { roomSearches ->
                if (roomSearches.isNotEmpty()) {
                    _recentSearches.value = roomSearches
                    refreshSearchDrivenRecommendations()
                }
            }
        }
        com.example.ui.player.GlobalPlayerManager.setPlaybackFailedListener {
            tryNextFallbackStream()
        }
        if (_recentSearches.value.isNotEmpty()) {
            refreshSearchDrivenRecommendations()
        }
    }

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val filtered = _recentSearches.value.filterNot { it.equals(q, ignoreCase = true) }
        val updated = (listOf(q) + filtered).take(20)
        _recentSearches.value = updated
        saveRecentSearches(updated)
        refreshSearchDrivenRecommendations()
        viewModelScope.launch {
            videoCacheRepo.addSearchQuery(q)
        }
    }

    fun removeRecentSearch(query: String) {
        val updated = _recentSearches.value.filterNot { it.equals(query, ignoreCase = true) }
        _recentSearches.value = updated
        saveRecentSearches(updated)
        refreshSearchDrivenRecommendations()
        viewModelScope.launch {
            videoCacheRepo.removeSearchQuery(query)
        }
    }

    fun clearAllRecentSearches() {
        _recentSearches.value = emptyList()
        saveRecentSearches(emptyList())
        _searchDrivenRecommendations.value = emptyList()
        _latestSearchIntent.value = null
        viewModelScope.launch {
            videoCacheRepo.clearSearchHistory()
        }
    }

    fun filterRankAndDeduplicateSearchResults(
        rawResults: List<VideoItem>,
        query: String
    ): List<VideoItem> {
        val qClean = query.trim().lowercase()
        if (qClean.isBlank()) return rawResults.distinctBy { "${it.providerId}_${it.id}" }

        val stopWords = setOf("a", "an", "the", "in", "of", "to", "for", "is", "and", "or", "on", "at", "by", "with", "from", "it", "video", "full", "hd")
        val queryTokens = qClean.split(Regex("[\\s\\-_/:]+")).filter { it.isNotBlank() }
        val significantTokens = queryTokens.filterNot { stopWords.contains(it) && queryTokens.size > 1 }
        val searchTokens = if (significantTokens.isNotEmpty()) significantTokens else queryTokens

        val scoredList = mutableListOf<Pair<VideoItem, Double>>()

        for (item in rawResults) {
            if (item.id.isBlank()) continue
            if (isBlockedVideo(item)) continue
            if (!com.example.util.LanguageFilterHelper.isAllowedVideoItem(item)) continue
            if (!_adultContentEnabled.value && isAdultVideoItem(item)) continue

            val titleLower = item.title.lowercase()
            val channelLower = (item.uploaderName ?: "").lowercase()
            val descLower = (item.description ?: "").lowercase()

            var score = 0.0

            if (titleLower == qClean) {
                score += 3000.0
            } else if (titleLower.contains(qClean)) {
                score += 1500.0
            } else if (qClean.contains(titleLower) && titleLower.length >= 4) {
                score += 800.0
            }

            if (channelLower == qClean) {
                score += 2000.0
            } else if (channelLower.contains(qClean) || qClean.contains(channelLower)) {
                score += 1000.0
            }

            var matchedTokensCount = 0
            for (token in searchTokens) {
                var tokenMatched = false
                if (titleLower.contains(token)) {
                    score += 400.0
                    tokenMatched = true
                }
                if (channelLower.contains(token)) {
                    score += 300.0
                    tokenMatched = true
                }
                if (descLower.contains(token)) {
                    score += 100.0
                    tokenMatched = true
                }
                if (tokenMatched) matchedTokensCount++
            }

            if (matchedTokensCount == 0) {
                continue
            }

            if (searchTokens.size > 1 && matchedTokensCount >= searchTokens.size) {
                score += 1000.0
            }

            if (item.providerId == "youtube") {
                score += 150.0
            }
            if (item.viewCount > 0) {
                score += Math.min(300.0, Math.log10(item.viewCount.toDouble() + 1.0) * 40.0)
            }

            scoredList.add(item to score)
        }

        val sortedCandidates = scoredList.sortedByDescending { it.second }.map { it.first }

        val finalResults = mutableListOf<VideoItem>()
        val seenIds = mutableSetOf<String>()
        val seenNormTitles = mutableSetOf<String>()

        for (item in sortedCandidates) {
            val uniqueKey = "${item.providerId}_${item.id}"
            if (seenIds.contains(uniqueKey) || seenIds.contains(item.id)) continue

            val normTitle = normalizeTitleForCleanDeduplication(item.title)
            if (normTitle.length > 5 && seenNormTitles.contains(normTitle)) {
                continue
            }

            seenIds.add(uniqueKey)
            seenIds.add(item.id)
            if (normTitle.length > 5) {
                seenNormTitles.add(normTitle)
            }
            finalResults.add(item)
        }

        return finalResults
    }

    private fun normalizeTitleForCleanDeduplication(title: String): String {
        return title.lowercase()
            .replace(Regex("\\[(4k|1080p|720p|hd|uhd|official video|official music video|official audio|official trailer|full movie|trailer)\\]"), "")
            .replace(Regex("\\((4k|1080p|720p|hd|uhd|official video|official music video|official audio|official trailer|full movie|trailer)\\)"), "")
            .replace(Regex("[^a-z0-9]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun openChannel(channelName: String, avatarUrl: String? = null, channelUrlOrId: String? = null) {
        val trimmed = channelName.trim()
        if (trimmed.isBlank()) return
        val cleanAvatar = avatarUrl ?: com.example.util.ChannelLogoHelper.getBrandInfo(trimmed, avatarUrl).logoUrls.firstOrNull()
        _selectedChannelName.value = trimmed
        _selectedChannelAvatarUrl.value = cleanAvatar
        _currentScreen.value = AppScreen.CHANNEL
        loadChannelDetails(trimmed, cleanAvatar, channelUrlOrId)
    }

    private var channelLoadJob: Job? = null
    fun loadChannelDetails(channelName: String, avatarUrl: String? = null, channelUrlOrId: String? = null) {
        channelLoadJob?.cancel()
        channelLoadJob = viewModelScope.launch(Dispatchers.IO) {
            _isChannelLoading.value = true
            try {
                val brand = com.example.util.ChannelLogoHelper.getBrandInfo(channelName, avatarUrl)
                val isAdult = com.example.extractor.AdultChannelExtractor.isAdultChannel(channelName, channelUrlOrId)
                val isStudio = com.example.util.StudioDetector.isStudioName(channelName) ||
                                (channelUrlOrId != null && (channelUrlOrId.contains("torrent") || channelUrlOrId.contains("vega")))

                if (isAdult) {
                    val adultDetails = com.example.extractor.AdultChannelExtractor.fetchAdultChannelDetails(
                        channelName = channelName,
                        fallbackAvatar = avatarUrl,
                        channelUrlOrId = channelUrlOrId
                    )
                    _channelDetails.value = adultDetails
                    _channelVideos.value = adultDetails.videos
                } else if (isStudio) {
                    val targetStudioName = brand.brandName.ifBlank { channelName }
                    val targetLogo = avatarUrl ?: brand.logoUrls.firstOrNull()
                    val studioVideos = fetchStudioCatalogVideos(targetStudioName, targetLogo)

                    val studioDetails = com.example.model.ChannelDetails(
                        channelId = channelName.lowercase().replace("[^a-z0-9]".toRegex(), "_").take(30),
                        name = targetStudioName,
                        handle = "@${targetStudioName.replace(" ", "").lowercase()}",
                        avatarUrl = targetLogo,
                        subscriberCount = brand.subscriberCountText.ifBlank { "Official Studio • Verified" },
                        videoCount = "${studioVideos.size} Movies & Series",
                        description = "Official Studio Channel for $targetStudioName. Featuring movie & series releases, BitTorrent streams, and studio catalog.",
                        isSubscribed = isSubscribed(channelName),
                        videos = studioVideos
                    )
                    _channelDetails.value = studioDetails
                    _channelVideos.value = studioVideos
                } else {
                    val initialVideos = (_trendingVideos.value + _recommendedVideos.value).filter { item ->
                        item.uploaderName.equals(channelName, ignoreCase = true) ||
                        item.uploaderName.contains(channelName, ignoreCase = true)
                    }

                    val initialDetails = com.example.model.ChannelDetails(
                        channelId = channelName.lowercase().replace("[^a-z0-9]".toRegex(), "_").take(30),
                        name = channelName,
                        handle = "@${channelName.replace(" ", "").lowercase()}",
                        avatarUrl = avatarUrl ?: brand.logoUrls.firstOrNull(),
                        subscriberCount = brand.subscriberCountText.ifBlank { "850K subscribers" },
                        videoCount = if (initialVideos.isNotEmpty()) "${initialVideos.size} videos" else "90+ videos",
                        isSubscribed = isSubscribed(channelName),
                        videos = initialVideos
                    )
                    _channelDetails.value = initialDetails
                    _channelVideos.value = initialVideos

                    // Fetch real channel information and uploads
                    val fetched = YouTubeExtractorHelper.fetchChannelDetails(
                        channelNameOrUrl = channelUrlOrId ?: channelName,
                        context = getApplication(),
                        fallbackAvatar = avatarUrl
                    )
                    val isSub = isSubscribed(fetched.name) || isSubscribed(channelName)
                    val finalDetails = fetched.copy(
                        isSubscribed = isSub,
                        avatarUrl = fetched.avatarUrl ?: avatarUrl ?: brand.logoUrls.firstOrNull()
                    )
                    _channelDetails.value = finalDetails
                    _channelVideos.value = finalDetails.videos
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "loadChannelDetails failed for $channelName: ${e.message}")
            } finally {
                _isChannelLoading.value = false
            }
        }
    }

    private suspend fun fetchStudioCatalogVideos(studioName: String, studioLogoUrl: String?): List<VideoItem> {
        val resultList = mutableListOf<VideoItem>()
        val cleanLogo = studioLogoUrl ?: com.example.util.ChannelLogoHelper.getBrandInfo(studioName, studioLogoUrl).logoUrls.firstOrNull()

        // 1. Local feed matches
        val localMatches = (_trendingVideos.value + _recommendedVideos.value + _searchResults.value).filter { item ->
            item.uploaderName.equals(studioName, ignoreCase = true) ||
            item.uploaderName.contains(studioName, ignoreCase = true) ||
            com.example.util.StudioDetector.detectStudio(item.title, item.id.contains("tv")).equals(studioName, ignoreCase = true)
        }.map { item ->
            item.copy(uploaderName = studioName, uploaderAvatarUrl = cleanLogo ?: item.uploaderAvatarUrl)
        }
        resultList.addAll(localMatches)

        // 2. Fetch ExploreMedia / TMDB items matching studio
        try {
            val helper = com.example.util.ExploreMediaHelper
            val searchQuery = com.example.util.StudioDetector.getStudioSearchQuery(studioName)
            val exploreItems = helper.searchAll(searchQuery) +
                               helper.fetchCategoryItems(com.example.model.ExploreMediaType.MOVIE) +
                               helper.fetchCategoryItems(com.example.model.ExploreMediaType.TV)

            val studioMatched = exploreItems.filter { item ->
                val detected = com.example.util.StudioDetector.detectStudio(item.title, item.mediaType == com.example.model.ExploreMediaType.TV)
                detected.equals(studioName, ignoreCase = true) || item.title.contains(searchQuery, ignoreCase = true)
            }.map { item ->
                val yearStr = if (!item.releaseYear.isNullOrBlank()) " (${item.releaseYear})" else ""
                val thumb = item.backdropUrl?.ifBlank { null } ?: item.posterUrl
                VideoItem(
                    id = "torrent_media_${item.id}",
                    title = "${item.title}$yearStr",
                    uploaderName = studioName,
                    uploaderAvatarUrl = cleanLogo,
                    thumbnailUrl = thumb,
                    durationSeconds = -1L,
                    providerId = "torrent",
                    description = item.overview ?: "",
                    uploadDate = item.releaseYear
                )
            }
            resultList.addAll(studioMatched)
        } catch (e: Exception) {
            Log.w("MainViewModel", "fetchStudioCatalogVideos error for $studioName: ${e.message}")
        }

        return resultList.distinctBy { if (it.id.isNotBlank()) it.id else it.title }
    }

    private var suggestionJob: kotlinx.coroutines.Job? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        suggestionJob?.cancel()

        val q = query.trim()
        if (q.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }

        suggestionJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(180L)
            if (!isActive) return@launch

            val sanitized = com.example.util.SmartSearchSanitizer.sanitizeQuery(q)

            val historyMatches = _recentSearches.value
                .filter { it.contains(q, ignoreCase = true) || it.contains(sanitized.cleanQuery, ignoreCase = true) }
                .take(3)
                .map { SearchSuggestionItem(query = it, isHistory = true) }

            val fetchedSuggestions = com.example.extractor.YouTubeExtractorHelper.fetchSearchSuggestions(sanitized.cleanQuery)
            if (!isActive) return@launch

            val ytSuggestions = fetchedSuggestions.map { sug ->
                SearchSuggestionItem(
                    query = sug,
                    isHistory = false,
                    providerBadge = "YouTube"
                )
            }

            val combined = mutableListOf<SearchSuggestionItem>()
            val seenQueries = mutableSetOf<String>()

            // 1. AI Sanitized / Typo Correction suggestion at top
            if (sanitized.wasCleaned) {
                val cleanLower = sanitized.cleanQuery.lowercase()
                if (seenQueries.add(cleanLower)) {
                    combined.add(
                        SearchSuggestionItem(
                            query = sanitized.cleanQuery,
                            isHistory = false,
                            providerBadge = if (sanitized.didYouMean != null) "Did You Mean" else "AI Cleaned"
                        )
                    )
                }
            }

            // 2. History matches
            historyMatches.forEach { item ->
                val lower = item.query.lowercase()
                if (seenQueries.add(lower)) {
                    combined.add(item)
                }
            }

            // 3. YouTube live suggestions
            ytSuggestions.forEach { item ->
                val lower = item.query.lowercase()
                if (seenQueries.add(lower)) {
                    combined.add(item)
                }
            }

            if (isActive) {
                _searchSuggestions.value = combined
            }
        }
    }

    private val searchFastCache = java.util.concurrent.ConcurrentHashMap<String, List<VideoItem>>()

    fun performSearch(query: String? = null) {
        val rawInput = (query ?: _searchQuery.value).trim()
        if (rawInput.isBlank()) return

        val sanitized = com.example.util.SmartSearchSanitizer.sanitizeQuery(rawInput)
        _activeSearchSanitizedResult.value = sanitized

        val searchTarget = sanitized.cleanQuery
        addRecentSearch(searchTarget)
        if (sanitized.wasCleaned && rawInput != searchTarget) {
            addRecentSearch(rawInput)
        }

        val cacheKey = "${searchTarget.lowercase()}_${_activeProviderId.value}"
        val cached = searchFastCache[cacheKey]
        if (cached != null && cached.isNotEmpty()) {
            _searchResults.value = cached
        } else {
            _searchResults.value = emptyList()
        }

        currentSearchPage = 1
        _isSearching.value = true

        viewModelScope.launch(Dispatchers.IO) {
            _feedError.value = null
            try {
                val adultEnabled = _adultContentEnabled.value
                val activeProv = _activeProviderId.value
                val enabledSet = _enabledProviderIds.value

                val collectedList = java.util.Collections.synchronizedList(mutableListOf<VideoItem>())

                val updateUiResults = {
                    val snapshot = synchronized(collectedList) { collectedList.toList() }
                    val distinct = snapshot.distinctBy { (it.providerId ?: "") + "_" + it.id }
                    val filtered = distinct.filter {
                        if (activeProv != "all") {
                            it.providerId == activeProv
                        } else {
                            adultEnabled || !isAdultVideoItem(it)
                        }
                    }
                    val ranked = rankFeedWithRecommendations(filtered)
                    _searchResults.value = ranked
                    if (ranked.isNotEmpty()) {
                        searchFastCache[cacheKey] = ranked
                    }
                }

                supervisorScope {
                    // 1. YouTube (Priority fast search)
                    if ((activeProv == "all" || activeProv == "youtube") && enabledSet.contains("youtube")) {
                        launch(Dispatchers.IO) {
                            try {
                                val ytResults = kotlinx.coroutines.withTimeoutOrNull(3500L) {
                                    com.example.extractor.YouTubeExtractorHelper.searchYouTube(searchTarget, getApplication())
                                } ?: emptyList()
                                if (ytResults.isNotEmpty()) {
                                    synchronized(collectedList) { collectedList.addAll(ytResults) }
                                    updateUiResults()
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "YouTube search note: ${e.message}")
                            }
                        }
                    }

                    // 2. Archive.org (Fast archive search)
                    if ((activeProv == "all" || activeProv == "archive_org") && enabledSet.contains("archive_org")) {
                        launch(Dispatchers.IO) {
                            try {
                                val archResults = kotlinx.coroutines.withTimeoutOrNull(3500L) {
                                    com.example.extractor.ArchiveOrgProvider.search(searchTarget, 1)
                                } ?: emptyList()
                                if (archResults.isNotEmpty()) {
                                    synchronized(collectedList) { collectedList.addAll(archResults) }
                                    updateUiResults()
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Archive.org search note: ${e.message}")
                            }
                        }
                    }

                    // 3. Torrent Media Search (Movies, Series, Anime with 16:9 Backdrops)
                    if ((activeProv == "all" || activeProv == "torrent") && enabledSet.contains("torrent")) {
                        launch(Dispatchers.IO) {
                            try {
                                val tResults = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    searchTorrentMedia(searchTarget)
                                } ?: emptyList()
                                if (tResults.isNotEmpty()) {
                                    synchronized(collectedList) { collectedList.addAll(tResults) }
                                    updateUiResults()
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Torrent search note: ${e.message}")
                            }
                        }
                    }

                    // 4. Eporner
                    if ((activeProv == "all" || activeProv == "eporner") && enabledSet.contains("eporner") && (adultEnabled || activeProv == "eporner")) {
                        launch(Dispatchers.IO) {
                            try {
                                val epResults = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                                    com.example.extractor.EpornerProvider.search(searchTarget, 25)
                                } ?: emptyList()
                                if (epResults.isNotEmpty()) {
                                    synchronized(collectedList) { collectedList.addAll(epResults) }
                                    updateUiResults()
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Eporner search note: ${e.message}")
                            }
                        }
                    }

                    // 5. MultiSource providers
                    val ytDlpSources = listOf("dailymotion", "bilibili", "vimeo", "hotstar", "twitch", "spankbang", "hanime1", "hqporner", "pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn")

                    val searchSources = when {
                        activeProv == "all" -> ytDlpSources.filter { enabledSet.contains(it) && (adultEnabled || !isAdultProviderId(it)) }
                        else -> if (ytDlpSources.contains(activeProv)) listOf(activeProv) else emptyList()
                    }

                    searchSources.forEach { prov ->
                        launch(Dispatchers.IO) {
                            try {
                                val provResults = kotlinx.coroutines.withTimeoutOrNull(3500L) {
                                    com.example.extractor.MultiSourceProvider.search(getApplication(), prov, searchTarget, 15)
                                } ?: emptyList()
                                if (provResults.isNotEmpty()) {
                                    synchronized(collectedList) { collectedList.addAll(provResults) }
                                    updateUiResults()
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "MultiSource search note for $prov: ${e.message}")
                            }
                        }
                    }

                    // 6. Installed & Enabled Vega Providers
                    val installedVega = vegaRepository.getInstalledProviders().filter { it.isEnabled }
                    val targetVega = when {
                        activeProv.startsWith("vega_") -> {
                            val raw = activeProv.removePrefix("vega_")
                            installedVega.filter { it.id.equals(raw, ignoreCase = true) }
                        }
                        activeProv == "all" -> installedVega
                        else -> emptyList()
                    }

                    targetVega.forEach { vegaProv ->
                        launch(Dispatchers.IO) {
                            try {
                                val vResults = kotlinx.coroutines.withTimeoutOrNull(4500L) {
                                    com.example.vega.VegaProviderClient.search(vegaProv.id, searchTarget)
                                } ?: emptyList()
                                if (vResults.isNotEmpty()) {
                                    val videoItems = vResults.map { vItem ->
                                        val isTv = vItem.title.contains("season", ignoreCase = true) || vItem.title.contains("series", ignoreCase = true) || vItem.title.contains("s0", ignoreCase = true)
                                        val studio = com.example.util.StudioDetector.detectStudio(vItem.title, isTv)
                                        val studioLogo = com.example.util.ChannelLogoHelper.getBrandInfo(studio, null, vItem.title).logoUrls.firstOrNull()
                                        VideoItem(
                                            id = "vega_${vegaProv.id}::${vItem.link}",
                                            title = vItem.title,
                                            uploaderName = studio,
                                            uploaderAvatarUrl = studioLogo,
                                            thumbnailUrl = vItem.imageUrl ?: "",
                                            durationSeconds = -1L,
                                            providerId = "vega_${vegaProv.id}"
                                        )
                                    }
                                    synchronized(collectedList) { collectedList.addAll(videoItems) }
                                    updateUiResults()
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Vega search error for ${vegaProv.id}: ${e.message}")
                            }
                        }
                    }
                }

                updateUiResults()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Search failed", e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun refreshFeed() {
        _searchResults.value = emptyList()
        _searchQuery.value = ""
        loadTrending(forceRefresh = true)
    }

    fun loadTrending(forceRefresh: Boolean = false) {
        currentTrendingPage = 1
        if (_trendingVideos.value.isEmpty()) {
            _isLoadingTrending.value = true
        }
        viewModelScope.launch(Dispatchers.IO) {
            _feedError.value = null
            if (forceRefresh && _trendingVideos.value.isEmpty()) {
                _searchResults.value = emptyList()
            }
            try {
                val adultEnabled = _adultContentEnabled.value
                val activeProv = _activeProviderId.value
                val enabledSet = _enabledProviderIds.value

                val collectedFeed = mutableListOf<VideoItem>()

                // Helper to produce a stable balanced feed without random reshuffling shifts
                fun buildBalancedFeed(items: List<VideoItem>): List<VideoItem> {
                    val filtered = items
                        .distinctBy { it.id }
                        .filter {
                            if (activeProv != "all") {
                                it.providerId == activeProv
                            } else {
                                adultEnabled || !isAdultVideoItem(it)
                            }
                        }

                    val groupedByProvider = filtered.groupBy { it.providerId }
                    val balancedList = mutableListOf<VideoItem>()
                    var index = 0
                    var hasMore = true
                    while (hasMore) {
                        hasMore = false
                        for ((_, providerItems) in groupedByProvider) {
                            if (index < providerItems.size) {
                                balancedList.add(providerItems[index])
                                hasMore = true
                            }
                        }
                        index++
                    }
                    return if (balancedList.isNotEmpty()) balancedList else filtered
                }

                // --- BATCH 1: FAST PRIMARY PROVIDERS (YouTube, Archive, Eporner, Dailymotion, Bilibili, Vimeo, Hotstar) ---
                // We fetch primary providers concurrently and update the UI ONCE when Batch 1 completes!
                supervisorScope {
                    // 1. YouTube
                    if ((activeProv == "all" || activeProv == "youtube") && enabledSet.contains("youtube")) {
                        launch(Dispatchers.IO) {
                            try {
                                val ytItems = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    com.example.extractor.YouTubeExtractorHelper.fetchYouTubeTrending(getApplication())
                                } ?: emptyList()
                                if (ytItems.isNotEmpty()) {
                                    synchronized(collectedFeed) { collectedFeed.addAll(ytItems) }
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "YouTube trending note: ${e.message}")
                            }
                        }
                    }

                    // 2. Archive.org
                    if ((activeProv == "all" || activeProv == "archive_org") && enabledSet.contains("archive_org")) {
                        launch(Dispatchers.IO) {
                            try {
                                val arcItems = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    com.example.extractor.ArchiveOrgProvider.getHome(1)
                                } ?: emptyList()
                                if (arcItems.isNotEmpty()) {
                                    synchronized(collectedFeed) { collectedFeed.addAll(arcItems) }
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "ArchiveOrg note: ${e.message}")
                            }
                        }
                    }

                    // 3. Eporner
                    if (((activeProv == "all" && adultEnabled) || activeProv == "eporner") && enabledSet.contains("eporner")) {
                        launch(Dispatchers.IO) {
                            try {
                                val epItems = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    com.example.extractor.EpornerProvider.getHome(25)
                                } ?: emptyList()
                                if (epItems.isNotEmpty()) {
                                    synchronized(collectedFeed) { collectedFeed.addAll(epItems) }
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Eporner note: ${e.message}")
                            }
                        }
                    }

                    // 4. MultiSource fast providers
                    val fastMultiSources = listOf("dailymotion", "bilibili", "vimeo", "hotstar", "twitch", "spankbang", "hanime1", "hqporner", "pornhub", "beeg", "bun-tel-meg")
                    val targetFastSources = when {
                        activeProv == "all" -> fastMultiSources.filter { enabledSet.contains(it) && (adultEnabled || !isAdultProviderId(it)) }
                        else -> if (fastMultiSources.contains(activeProv)) listOf(activeProv) else emptyList()
                    }

                    targetFastSources.forEach { prov ->
                        launch(Dispatchers.IO) {
                            try {
                                val srcItems = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    com.example.extractor.MultiSourceProvider.getHome(getApplication(), prov, 20)
                                } ?: emptyList()
                                if (srcItems.isNotEmpty()) {
                                    synchronized(collectedFeed) { collectedFeed.addAll(srcItems) }
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Source note for $prov: ${e.message}")
                            }
                        }
                    }
                }

                // Batch 1 Primary Load Complete! Emit single atomic update to UI.
                val batch1Feed: List<VideoItem>
                synchronized(collectedFeed) {
                    batch1Feed = ArrayList(collectedFeed)
                }
                val primaryResult = buildBalancedFeed(batch1Feed)
                if (primaryResult.isNotEmpty()) {
                    _trendingVideos.value = primaryResult
                    _isLoadingTrending.value = false
                    com.example.util.ThumbnailOptimizer.preloadThumbnails(getApplication(), primaryResult)
                }

                // --- BATCH 2: HEAVY SCRAPERS, VEGA & TORRENT (APPEND-ONLY) ---
                // We fetch secondary sources in background and append them to the bottom so top visible items NEVER shift!
                val secondaryCollected = mutableListOf<VideoItem>()

                val heavyAdultSources = listOf("xvideos", "4tube", "rule34video", "redtube", "xhamster", "youporn")
                val targetHeavySources = when {
                    activeProv == "all" -> heavyAdultSources.filter { enabledSet.contains(it) && (adultEnabled || !isAdultProviderId(it)) }
                    else -> if (heavyAdultSources.contains(activeProv)) listOf(activeProv) else emptyList()
                }

                supervisorScope {
                    targetHeavySources.forEach { prov ->
                        launch(Dispatchers.IO) {
                            try {
                                val srcItems = kotlinx.coroutines.withTimeoutOrNull(12000L) {
                                    com.example.extractor.MultiSourceProvider.getHome(getApplication(), prov, 20, 1)
                                } ?: emptyList()
                                if (srcItems.isNotEmpty()) {
                                    synchronized(secondaryCollected) { secondaryCollected.addAll(srcItems) }
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Heavy source note for $prov: ${e.message}")
                            }
                        }
                    }

                    // Vega Providers
                    val installedVega = vegaRepository.getInstalledProviders().filter { it.isEnabled }
                    val targetVega = when {
                        activeProv.startsWith("vega_") -> {
                            val raw = activeProv.removePrefix("vega_")
                            installedVega.filter { it.id.equals(raw, ignoreCase = true) }
                        }
                        activeProv == "all" -> installedVega
                        else -> emptyList()
                    }

                    targetVega.forEach { vegaProv ->
                        launch(Dispatchers.IO) {
                            try {
                                val vResults = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                                    com.example.vega.VegaProviderClient.getHomeContent(vegaProv.id)
                                } ?: emptyList()
                                if (vResults.isNotEmpty()) {
                                    val vItems = vResults.map { vItem ->
                                        val isTv = vItem.title.contains("season", ignoreCase = true) || vItem.title.contains("series", ignoreCase = true) || vItem.title.contains("s0", ignoreCase = true)
                                        val studio = com.example.util.StudioDetector.detectStudio(vItem.title, isTv)
                                        val studioLogo = com.example.util.ChannelLogoHelper.getBrandInfo(studio, null, vItem.title).logoUrls.firstOrNull()
                                        VideoItem(
                                            id = "vega_${vegaProv.id}::${vItem.link}",
                                            title = vItem.title,
                                            uploaderName = studio,
                                            uploaderAvatarUrl = studioLogo,
                                            thumbnailUrl = vItem.imageUrl ?: "",
                                            durationSeconds = -1L,
                                            providerId = "vega_${vegaProv.id}"
                                        )
                                    }
                                    synchronized(secondaryCollected) { secondaryCollected.addAll(vItems) }
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Vega trending note for ${vegaProv.id}: ${e.message}")
                            }
                        }
                    }

                    // Torrent Feed
                    if ((activeProv == "all" || activeProv == "torrent") && enabledSet.contains("torrent")) {
                        launch(Dispatchers.IO) {
                            try {
                                val torrentItems = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                                    fetchTorrentTrendingFeed()
                                } ?: emptyList()
                                if (torrentItems.isNotEmpty()) {
                                    synchronized(secondaryCollected) { secondaryCollected.addAll(torrentItems) }
                                }
                            } catch (e: Exception) {
                                Log.w("MainViewModel", "Torrent trending note: ${e.message}")
                            }
                        }
                    }
                }

                // Append secondary items to the end of the existing feed without shifting existing top items!
                val secondarySnapshot: List<VideoItem>
                synchronized(secondaryCollected) {
                    secondarySnapshot = ArrayList(secondaryCollected)
                }
                if (secondarySnapshot.isNotEmpty()) {
                    val currentList = _trendingVideos.value
                    val currentIds = currentList.mapTo(HashSet()) { it.id }
                    val newUniqueSecondary = secondarySnapshot.filter { !currentIds.contains(it.id) }
                    if (newUniqueSecondary.isNotEmpty()) {
                        _trendingVideos.value = currentList + newUniqueSecondary
                    }
                }

                // Save loaded feed to disk cache
                if (_trendingVideos.value.isNotEmpty()) {
                    com.example.util.HomeFeedCacheManager.saveCachedFeed(getApplication(), _trendingVideos.value)
                    com.example.util.HomeFeedCache.saveFeed(getApplication(), _trendingVideos.value)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadTrending failed", e)
            } finally {
                _isLoadingTrending.value = false
            }
        }
    }

    fun loadMoreContent() {
        if (_isLoadingMore.value || _isLoadingTrending.value || _isSearching.value) return
        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val q = _searchQuery.value
                val isSearchMode = _searchResults.value.isNotEmpty() || q.isNotBlank()
                val activeProv = _activeProviderId.value
                val adultEnabled = _adultContentEnabled.value
                val enabledSet = _enabledProviderIds.value

                val newItems = mutableListOf<VideoItem>()

                if (isSearchMode && q.isNotBlank()) {
                    currentSearchPage++
                    if (activeProv == "all" || activeProv == "youtube") {
                        newItems.addAll(com.example.extractor.YouTubeExtractorHelper.searchYouTube(q, getApplication()))
                    }
                    if (activeProv == "all" || activeProv == "archive_org") {
                        newItems.addAll(com.example.extractor.ArchiveOrgProvider.search(q, currentSearchPage))
                    }
                    if (((activeProv == "all" && adultEnabled) || activeProv == "eporner") && enabledSet.contains("eporner")) {
                        newItems.addAll(com.example.extractor.EpornerProvider.search(q, 25, currentSearchPage))
                    }
                    if (activeProv == "all") {
                        val multiProvs = listOf("vimeo", "dailymotion", "bilibili", "hotstar", "twitch") +
                                (if (adultEnabled) listOf("spankbang", "hanime1", "hqporner", "pornhub", "xvideos", "xhamster", "youporn", "redtube", "beeg", "4tube", "rule34video") else emptyList())
                        multiProvs.filter { enabledSet.contains(it) }.forEach { p ->
                            try {
                                newItems.addAll(com.example.extractor.MultiSourceProvider.search(getApplication(), p, q, 20, currentSearchPage))
                            } catch (_: Exception) {}
                        }
                    } else if (activeProv != "youtube" && activeProv != "archive_org" && activeProv != "eporner") {
                        if (adultEnabled || !isAdultProviderId(activeProv)) {
                            newItems.addAll(com.example.extractor.MultiSourceProvider.search(getApplication(), activeProv, q, 20, currentSearchPage))
                        }
                    }
                    val filtered = newItems.filter {
                        if (activeProv != "all") {
                            it.providerId == activeProv
                        } else {
                            adultEnabled || !isAdultVideoItem(it)
                        }
                    }
                    val currentList = _searchResults.value
                    val combined = (currentList + filtered).distinctBy { it.id }
                    _searchResults.value = combined
                } else {
                    currentTrendingPage++
                    if (activeProv == "all" || activeProv == "youtube") {
                        newItems.addAll(com.example.extractor.YouTubeExtractorHelper.fetchYouTubeTrending(getApplication()))
                    }
                    if (activeProv == "all" || activeProv == "archive_org") {
                        newItems.addAll(com.example.extractor.ArchiveOrgProvider.getHome(currentTrendingPage))
                    }
                    if (((activeProv == "all" && adultEnabled) || activeProv == "eporner") && enabledSet.contains("eporner")) {
                        newItems.addAll(com.example.extractor.EpornerProvider.getHome(25, currentTrendingPage))
                    }
                    if (activeProv == "all") {
                        val multiProvs = listOf("vimeo", "dailymotion", "bilibili", "hotstar", "twitch") +
                                (if (adultEnabled) listOf("spankbang", "hanime1", "hqporner", "pornhub", "xvideos", "xhamster", "youporn", "redtube", "beeg", "4tube", "rule34video") else emptyList())
                        multiProvs.filter { enabledSet.contains(it) }.forEach { p ->
                            try {
                                newItems.addAll(com.example.extractor.MultiSourceProvider.getHome(getApplication(), p, 20, currentTrendingPage))
                            } catch (_: Exception) {}
                        }
                    } else if (activeProv != "youtube" && activeProv != "archive_org" && activeProv != "eporner") {
                        if (adultEnabled || !isAdultProviderId(activeProv)) {
                            newItems.addAll(com.example.extractor.MultiSourceProvider.getHome(getApplication(), activeProv, 20, currentTrendingPage))
                        }
                    }
                    val filtered = newItems.filter {
                        if (activeProv != "all") {
                            it.providerId == activeProv
                        } else {
                            adultEnabled || !isAdultVideoItem(it)
                        }
                    }
                    val currentList = _trendingVideos.value
                    val combined = (currentList + filtered).distinctBy { it.id }
                    _trendingVideos.value = combined
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadMoreContent failed", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun loadMorePlayerRecommendations(streamData: StreamData? = null) {
        if (_isLoadingPlayerRecs.value) return
        _isLoadingPlayerRecs.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                playerRecsPage++
                val vid = streamData?.videoId ?: _activeVideoId.value ?: ""
                val title = streamData?.title ?: _activeVideoItem.value?.title ?: ""
                val channel = streamData?.channelName ?: _activeVideoItem.value?.uploaderName ?: ""
                val providerId = streamData?.providerId ?: _activeVideoItem.value?.providerId ?: "youtube"

                val discovered = mutableListOf<VideoItem>()

                // 1. Build rich search terms from title, channel, and keywords
                val cleanWords = title
                    .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)|-|_|\\||#\\S+|official|trailer|video|hd|4k|1080p|full movie|season|episode"), " ")
                    .split("\\s+".toRegex())
                    .filter { it.isNotBlank() && it.length > 2 }

                val searchTerms = mutableListOf<String>()
                if (cleanWords.size >= 2) {
                    searchTerms.add(cleanWords.take(3).joinToString(" "))
                }
                if (channel.isNotBlank() && !channel.contains("Torrent", ignoreCase = true) && channel != "Butterfly Stream") {
                    searchTerms.add(channel)
                }
                if (cleanWords.size >= 3) {
                    searchTerms.add(cleanWords.takeLast(2).joinToString(" "))
                }
                if (title.isNotBlank()) {
                    searchTerms.add(title.take(35))
                }

                val targetTerm = if (searchTerms.isNotEmpty()) {
                    searchTerms[(playerRecsPage - 1) % searchTerms.size]
                } else {
                    DIVERSE_TOPICS[(playerRecsPage - 1) % DIVERSE_TOPICS.size]
                }

                // Query search for related content
                if (providerId == "youtube" || vid.length == 11) {
                    when (val res = YouTubeExtractorHelper.searchVideos(targetTerm, getApplication())) {
                        is com.example.model.FeedResult.Success -> {
                            val items = res.items.filter { it.id != vid }
                            discovered.addAll(items)
                        }
                        else -> {}
                    }
                } else {
                    try {
                        val list = com.example.extractor.ArchiveOrgProvider.search(targetTerm, playerRecsPage)
                        discovered.addAll(list.map { item ->
                            VideoItem(
                                id = item.id,
                                title = item.title,
                                uploaderName = item.uploaderName,
                                thumbnailUrl = item.thumbnailUrl,
                                durationSeconds = item.durationSeconds,
                                viewCount = item.viewCount,
                                providerId = providerId,
                                description = item.description
                            )
                        }.filter { it.id != vid })
                    } catch (e: Exception) {
                        // Continue
                    }
                }

                // Fallback guarantee: query diverse topics so infinite scroll never ends
                if (discovered.isEmpty()) {
                    val fallbackTopic = DIVERSE_TOPICS[playerRecsPage % DIVERSE_TOPICS.size]
                    when (val res = YouTubeExtractorHelper.searchVideos(fallbackTopic, getApplication())) {
                        is com.example.model.FeedResult.Success -> {
                            discovered.addAll(res.items.filter { it.id != vid })
                        }
                        else -> {}
                    }
                }

                val tasteVector = com.example.recommendation.SmartRecommendationEngine.computeTasteVector(
                    watchHistory = _watchHistory.value,
                    watchProgressMap = _watchProgressMap.value,
                    likedVideoIds = _likedVideoIds.value,
                    dislikedVideoIds = _dislikedVideoIds.value,
                    bookmarks = _watchLaterList.value,
                    notInterestedChannels = _notInterestedChannels.value,
                    recentSearches = _recentSearches.value,
                    watchPositionMsMap = _watchPositionMsMap.value
                )

                val rankedDiscovered = com.example.recommendation.SmartRecommendationEngine.rankCandidateVideos(
                    candidates = discovered,
                    tasteVector = tasteVector,
                    activeVideo = _activeVideoItem.value,
                    blockedVideoIds = _hiddenVideoIds.value + _notInterestedVideoIds.value,
                    blockedChannels = _notInterestedChannels.value
                )

                val current = _playerRecommendations.value
                val combined = (current + rankedDiscovered).distinctBy { it.id }
                _playerRecommendations.value = combined

                // Ensure home feed keeps filling up as well
                if (_trendingVideos.value.size < 60) {
                    loadMoreContent()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadMorePlayerRecommendations error", e)
            } finally {
                _isLoadingPlayerRecs.value = false
            }
        }
    }

    fun playVideo(videoIdOrUrl: String, providerIdHint: String? = null) {
        val cleanIdOrUrl = videoIdOrUrl.trim()
        if (cleanIdOrUrl.isEmpty()) return

        playerRecsPage = 1
        _playerRecommendations.value = emptyList()

        if (cleanIdOrUrl == _activeVideoId.value && _extractionResult.value is YouTubeExtractorHelper.ExtractionResult.Success) {
            _currentScreen.value = AppScreen.PLAYER
            _isPlaying.value = true
            com.example.ui.player.GlobalPlayerManager.play()
            return
        }

        // Immediately cancel any in-flight extraction / playback preparation
        activePlaybackJob?.cancel()
        activePlaybackJob = null

        // Immediately halt and clear any ongoing ExoPlayer or WebView playback
        com.example.ui.player.GlobalPlayerManager.stopAndClear()

        // Resolve target provider
        var targetProviderId = providerIdHint
        if (targetProviderId.isNullOrEmpty() || targetProviderId == "all") {
            val matchingItem = (_searchResults.value + _trendingVideos.value + _watchHistory.value).firstOrNull { it.id == cleanIdOrUrl }
            targetProviderId = matchingItem?.providerId
        }
        if (targetProviderId.isNullOrEmpty() || targetProviderId == "all") {
            targetProviderId = when {
                cleanIdOrUrl.contains("mega.nz", ignoreCase = true) || cleanIdOrUrl.contains("mega.io", ignoreCase = true) || cleanIdOrUrl.contains("mega.co.nz", ignoreCase = true) || cleanIdOrUrl.contains("#F!") || cleanIdOrUrl.contains("#!") || cleanIdOrUrl.startsWith("mega_") -> "mega"
                cleanIdOrUrl.startsWith("tg_") || cleanIdOrUrl.contains("t.me/") -> "telegram"
                cleanIdOrUrl.contains("youtube.com", ignoreCase = true) || cleanIdOrUrl.contains("youtu.be", ignoreCase = true) -> "youtube"
                cleanIdOrUrl.contains("dailymotion.com", ignoreCase = true) || cleanIdOrUrl.contains("dai.ly", ignoreCase = true) -> "dailymotion"
                cleanIdOrUrl.contains("eporner.com", ignoreCase = true) -> "eporner"
                cleanIdOrUrl.contains("archive.org", ignoreCase = true) -> "archive_org"
                cleanIdOrUrl.contains("pornhub.com", ignoreCase = true) || cleanIdOrUrl.contains("phncdn.com", ignoreCase = true) -> "pornhub"
                cleanIdOrUrl.contains("rule34video.com", ignoreCase = true) -> "rule34video"
                cleanIdOrUrl.contains("xvideos.com", ignoreCase = true) -> "xvideos"
                cleanIdOrUrl.contains("xhamster.com", ignoreCase = true) -> "xhamster"
                cleanIdOrUrl.contains("redtube.com", ignoreCase = true) -> "redtube"
                cleanIdOrUrl.contains("youporn.com", ignoreCase = true) -> "youporn"
                cleanIdOrUrl.contains("4tube.com", ignoreCase = true) -> "4tube"
                cleanIdOrUrl.contains("beeg.com", ignoreCase = true) -> "beeg"
                cleanIdOrUrl.contains("bilibili.com", ignoreCase = true) -> "bilibili"
                cleanIdOrUrl.contains("vimeo.com", ignoreCase = true) -> "vimeo"
                cleanIdOrUrl.contains("hotstar.com", ignoreCase = true) || cleanIdOrUrl.contains("jiohotstar.com", ignoreCase = true) -> "hotstar"
                cleanIdOrUrl.contains("bitchute.com", ignoreCase = true) -> "bitchute"
                cleanIdOrUrl.contains("rumble.com", ignoreCase = true) -> "rumble"
                cleanIdOrUrl.contains("tiktok.com", ignoreCase = true) -> "tiktok"
                cleanIdOrUrl.contains("reddit.com", ignoreCase = true) -> "reddit"
                cleanIdOrUrl.contains("twitch.tv", ignoreCase = true) -> "twitch"
                cleanIdOrUrl.contains("spankbang.com", ignoreCase = true) || cleanIdOrUrl.contains("spankbang.") -> "spankbang"
                cleanIdOrUrl.contains("hanime1.me", ignoreCase = true) || cleanIdOrUrl.contains("hanime1.com", ignoreCase = true) -> "hanime1"
                cleanIdOrUrl.contains("hqporner.com", ignoreCase = true) -> "hqporner"
                cleanIdOrUrl.contains("soundcloud.com", ignoreCase = true) -> "soundcloud"
                cleanIdOrUrl.contains("bandcamp.com", ignoreCase = true) -> "bandcamp"
                _activeProviderId.value != "all" && _activeProviderId.value.isNotBlank() -> _activeProviderId.value
                else -> "all"
            }
        }

        Log.d("MainViewModel", "playVideo for: '$cleanIdOrUrl' on provider: $targetProviderId")

        // Record in watch history for pattern understanding & recommendation engine
        val currentMatch = (_searchResults.value + _trendingVideos.value).firstOrNull { it.id == cleanIdOrUrl }
        val initialVideoItem = currentMatch ?: VideoItem(
            id = cleanIdOrUrl,
            title = if (cleanIdOrUrl.length == 11) "YouTube Video" else cleanIdOrUrl,
            uploaderName = targetProviderId?.replaceFirstChar { it.uppercase() } ?: "YouTube",
            thumbnailUrl = if (cleanIdOrUrl.length == 11) "https://i.ytimg.com/vi/$cleanIdOrUrl/hqdefault.jpg" else null,
            providerId = targetProviderId ?: "youtube"
        )
        _activeVideoItem.value = initialVideoItem

        if (currentMatch != null) {
            recordVideoView(currentMatch)
        } else {
            recordVideoView(initialVideoItem)
        }

        // Fast metadata prefetch for instant UI rendering (title, author)
        if (cleanIdOrUrl.length == 11 && (currentMatch == null || currentMatch.title == cleanIdOrUrl)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val req = okhttp3.Request.Builder()
                        .url("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$cleanIdOrUrl&format=json")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = org.json.JSONObject(body)
                            val title = json.optString("title")
                            val author = json.optString("author_name")
                            val thumb = json.optString("thumbnail_url")
                            if (title.isNotBlank()) {
                                _activeVideoItem.value = _activeVideoItem.value?.copy(
                                    title = title,
                                    uploaderName = if (author.isNotBlank()) author else _activeVideoItem.value?.uploaderName ?: "YouTube",
                                    thumbnailUrl = if (thumb.isNotBlank()) thumb else _activeVideoItem.value?.thumbnailUrl
                                )
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
        }

        _activeVideoId.value = cleanIdOrUrl
        fallbackAttemptsCount = 0
        lastFallbackAttemptTime = 0L
        _isExtracting.value = true
        _extractionResult.value = null
        _selectedStreamOption.value = null
        _selectedCaptionOption.value = null
        _isPlaying.value = true
        _tvSeasons.value = emptyList()
        _isSeasonsLoading.value = false
        com.example.ui.player.GlobalPlayerManager.resetFirstFrameState()

        // Immediately navigate to dedicated player screen
        _currentScreen.value = AppScreen.PLAYER

        // Launch extraction job immediately with top priority
        activePlaybackJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = if (cleanIdOrUrl.startsWith("torrent_") || targetProviderId == "torrent" || initialVideoItem.providerId == "torrent") {
                    val torrentTitle = initialVideoItem.title
                    val cleanTitle = torrentTitle.replace(Regex("""\s*\(\d{4}\).*"""), "").trim()
                    val identity = com.example.torrent.provider.MediaIdentity(
                        title = cleanTitle,
                        mediaType = if (cleanIdOrUrl.contains("tv_") || cleanIdOrUrl.contains("series")) "tv" else "movie",
                        tmdbId = Regex("""\d+""").find(cleanIdOrUrl)?.value
                    )
                    val releases = com.example.torrent.provider.TorrentProviderManager.getInstance().searchReleases(cleanTitle, identity)
                    if (releases.isNotEmpty()) {
                        val assignedPort = getOrStartTorrentServer()

                        // Clean deduplication: distinct by quality, codec, hdr, provider, seeders
                        val deduplicatedReleases = releases
                            .distinctBy { "${it.quality}_${it.codec}_${it.hdr}_${it.formattedSize}_${it.provider}_${it.seeders}" }
                            .sortedWith(
                                compareByDescending<com.example.torrent.model.TorrentRelease> { it.seeders > 0 }
                                    .thenByDescending { it.seeders }
                                    .thenByDescending { it.qualityScore }
                            )

                        val topRelease = deduplicatedReleases.first()
                        if (!topRelease.magnetUrl.startsWith("http://") && !topRelease.magnetUrl.startsWith("https://")) {
                            torrentEngine.startSession(topRelease, streamPort = assignedPort)
                        }

                        deduplicatedReleases.forEach { rel ->
                            val isDebrid = rel.magnetUrl.startsWith("http://") || rel.magnetUrl.startsWith("https://")
                            val streamUrl = if (isDebrid) rel.magnetUrl else "http://127.0.0.1:$assignedPort/stream?hash=${rel.infoHash}"
                            activeTorrentReleasesMap[streamUrl] = rel
                            activeTorrentReleasesMap[rel.infoHash] = rel
                        }

                        val options = deduplicatedReleases.map { rel: com.example.torrent.model.TorrentRelease ->
                            val isDebrid = rel.magnetUrl.startsWith("http://") || rel.magnetUrl.startsWith("https://")
                            val streamUrl = if (isDebrid) rel.magnetUrl else "http://127.0.0.1:$assignedPort/stream?hash=${rel.infoHash}"
                            val label = buildString {
                                append(rel.quality)
                                if (rel.codec.isNotBlank()) append(" • ").append(rel.codec)
                                if (rel.hdr.isNotBlank()) append(" • ").append(rel.hdr)
                                if (rel.formattedSize.isNotBlank()) append(" (").append(rel.formattedSize).append(")")
                                append(" [${rel.provider} • ${rel.seeders} seeds]")
                            }
                            PlayableStreamOption(
                                qualityLabel = label,
                                format = if (isDebrid && rel.magnetUrl.contains(".mp4")) "mp4" else "mkv",
                                isMuxed = true,
                                videoUrl = streamUrl,
                                audioUrl = null,
                                providerType = if (isDebrid) com.example.model.ProviderType.DIRECT else com.example.model.ProviderType.TORRENT,
                                headers = mapOf("Accept-Ranges" to "bytes")
                            )
                        }
                        val studio = com.example.util.StudioDetector.detectStudio(initialVideoItem.title, cleanIdOrUrl.contains("tv"))
                        val studioLogo = com.example.util.ChannelLogoHelper.getBrandInfo(studio, null, initialVideoItem.title).logoUrls.firstOrNull()
                        val streamData = StreamData(
                            videoId = cleanIdOrUrl,
                            title = initialVideoItem.title,
                            channelName = studio,
                            channelAvatarUrl = studioLogo,
                            description = "Native BitTorrent Stream • Size: ${topRelease.formattedSize} • Seeds: ${topRelease.seeders}",
                            availableStreamOptions = options,
                            selectedStreamOption = options.first(),
                            providerId = "torrent",
                            providerType = com.example.model.ProviderType.TORRENT
                        )
                        YouTubeExtractorHelper.ExtractionResult.Success(streamData)
                    } else {
                        YouTubeExtractorHelper.ExtractionResult.Error(
                            ExtractorErrorDetails(
                                errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                                message = "No active torrent releases found with seeds for $torrentTitle",
                                rawExceptionName = "TorrentReleaseException",
                                fullStackTrace = "TorrentProviderManager searched Torrentio, YTS, EZTV, Nyaa",
                                urlOrId = cleanIdOrUrl
                            )
                        )
                    }
                } else if (cleanIdOrUrl.startsWith("vega_") || targetProviderId?.startsWith("vega_") == true) {
                    val fullId = cleanIdOrUrl
                    val parts = fullId.split("::", limit = 2)
                    val rawProv = if (parts.size == 2) {
                        parts[0].removePrefix("vega_")
                    } else {
                        (targetProviderId ?: "vega_").removePrefix("vega_")
                    }
                    val link = if (parts.size == 2) parts[1] else cleanIdOrUrl

                    val resolution = kotlinx.coroutines.withTimeoutOrNull(25000L) {
                        com.example.vega.VegaProviderClient.resolveFullVegaPlayback(rawProv, link)
                    }

                    if (resolution != null && resolution.success && resolution.streams.isNotEmpty()) {
                        val options = resolution.streams.map { st ->
                            val label = if (st.server.isNotBlank() && st.server != "Direct") {
                                "${st.quality} • ${st.server}"
                            } else {
                                st.quality
                            }
                            PlayableStreamOption(
                                qualityLabel = label,
                                format = st.format.lowercase(),
                                isMuxed = true,
                                videoUrl = st.url,
                                audioUrl = null,
                                providerType = com.example.model.ProviderType.VEGA,
                                headers = st.headers
                            )
                        }

                        val resolvedMeta = resolution.meta
                        val updatedTitle = resolvedMeta?.title?.ifBlank { null } ?: initialVideoItem.title
                        val updatedThumbnail = resolvedMeta?.poster ?: resolvedMeta?.image ?: initialVideoItem.thumbnailUrl
                        val updatedDescription = resolvedMeta?.synopsis ?: initialVideoItem.description
                        val isTv = cleanIdOrUrl.contains("tv") || cleanIdOrUrl.contains("series")
                        val studio = com.example.util.StudioDetector.detectStudio(updatedTitle, isTv)
                        val studioLogo = com.example.util.ChannelLogoHelper.getBrandInfo(studio, null, updatedTitle).logoUrls.firstOrNull()

                        val streamData = StreamData(
                            videoId = cleanIdOrUrl,
                            title = updatedTitle,
                            channelName = studio,
                            channelAvatarUrl = studioLogo,
                            description = updatedDescription,
                            availableStreamOptions = options,
                            selectedStreamOption = options.first(),
                            providerId = targetProviderId ?: "vega_$rawProv",
                            providerType = com.example.model.ProviderType.VEGA,
                            headers = options.firstOrNull()?.headers ?: emptyMap()
                        )
                        YouTubeExtractorHelper.ExtractionResult.Success(streamData)
                    } else {
                        val errMsg = resolution?.errorMessage
                            ?: "[Vega Resolution Timeout] Provider '$rawProv' did not return playable stream URLs within 25s."
                        YouTubeExtractorHelper.ExtractionResult.Error(
                            ExtractorErrorDetails(
                                errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                                message = errMsg,
                                rawExceptionName = "VegaResolutionException",
                                fullStackTrace = "Stage: ${resolution?.stageReached ?: "TIMEOUT"}",
                                urlOrId = cleanIdOrUrl
                            )
                        )
                    }
                } else {
                    kotlinx.coroutines.withTimeoutOrNull(20000L) {
                        YouTubeExtractorHelper.resolveStream(cleanIdOrUrl, getApplication(), targetProviderId)
                    } ?: YouTubeExtractorHelper.ExtractionResult.Error(
                        ExtractorErrorDetails(
                            errorType = ExtractorErrorType.NETWORK_ERROR,
                            message = "Stream extraction timed out. Please check your network connection or try again.",
                            rawExceptionName = "TimeoutException",
                            fullStackTrace = "resolveStream exceeded 20s timeout",
                            urlOrId = cleanIdOrUrl
                        )
                    )
                }

                if (!isActive || _activeVideoId.value != cleanIdOrUrl) return@launch
                _extractionResult.value = result
                if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                    val primary = result.streamData.selectedStreamOption
                        ?: result.streamData.availableStreamOptions.firstOrNull { it.isMuxed && !(it.videoUrl ?: it.audioUrl).isNullOrBlank() }
                        ?: result.streamData.availableStreamOptions.firstOrNull { !(it.videoUrl ?: it.audioUrl).isNullOrBlank() }
                    _selectedStreamOption.value = primary
                    _selectedCaptionOption.value = result.streamData.captionOptions.firstOrNull()
                    if (!result.streamData.channelAvatarUrl.isNullOrBlank()) {
                        _activeVideoItem.value = _activeVideoItem.value?.copy(
                            uploaderAvatarUrl = result.streamData.channelAvatarUrl
                        )
                    }
                    loadTvSeasons(result.streamData)
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                if (!isActive || _activeVideoId.value != cleanIdOrUrl) return@launch
                Log.e("MainViewModel", "playVideo caught exception: ${e.localizedMessage}", e)
                _extractionResult.value = YouTubeExtractorHelper.ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.UNKNOWN,
                        message = "Playback error: ${e.localizedMessage}",
                        rawExceptionName = e.javaClass.name,
                        fullStackTrace = e.stackTraceToString(),
                        urlOrId = cleanIdOrUrl
                    )
                )
            } finally {
                if (_activeVideoId.value == cleanIdOrUrl) {
                    _isExtracting.value = false
                }
            }
        }
    }

    fun applyBatterySaverCapIfNeeded(options: List<PlayableStreamOption>): List<PlayableStreamOption> {
        if (!batterySaverManager.isPowerSaveActive.value) return options
        val capLabel = batterySaverManager.resolutionCap.value.lowercase()
        val capHeight = when {
            capLabel.contains("240") -> 240
            capLabel.contains("360") -> 360
            capLabel.contains("480") -> 480
            capLabel.contains("720") -> 720
            capLabel.contains("1080") -> 1080
            else -> 480
        }
        val filtered = options.filter { opt ->
            val label = opt.qualityLabel.lowercase()
            val height = Regex("""(\d{3,4})p?""").find(label)?.groupValues?.get(1)?.toIntOrNull()
                ?: if (label.contains("4k") || label.contains("2160")) 2160
                else if (label.contains("1080")) 1080
                else if (label.contains("720")) 720
                else if (label.contains("480")) 480
                else if (label.contains("360")) 360
                else 480
            height <= capHeight
        }
        return filtered.ifEmpty { options }
    }

    private fun startServerAutoScanner(options: List<PlayableStreamOption>) {
        if (options.isEmpty()) return
        val cappedOptions = applyBatterySaverCapIfNeeded(options)
        viewModelScope.launch(Dispatchers.IO) {
            var selected: PlayableStreamOption? = null
            for (option in cappedOptions) {
                val url = option.videoUrl ?: option.audioUrl ?: ""
                if (url.isBlank()) continue
                selected = option
                break
            }
            _selectedStreamOption.value = selected ?: cappedOptions.firstOrNull() ?: options.firstOrNull()
        }
    }

    private var lastFallbackAttemptTime = 0L
    private var fallbackAttemptsCount = 0

    fun tryNextFallbackStream(errorCode: Int? = null) {
        val now = System.currentTimeMillis()
        if (now - lastFallbackAttemptTime < 1200L) {
            return
        }
        lastFallbackAttemptTime = now

        val current = _selectedStreamOption.value ?: return
        val ext = _extractionResult.value
        if (ext is YouTubeExtractorHelper.ExtractionResult.Success) {
            val options = ext.streamData.availableStreamOptions
            val currentIndex = options.indexOfFirst { it.videoUrl == current.videoUrl }
            val nextIndex = if (currentIndex >= 0) currentIndex + 1 else 1

            // On HTTP 403 or when candidate formats are exhausted, trigger direct yt-dlp engine fallback
            if (errorCode == 403 || nextIndex >= options.size || fallbackAttemptsCount >= 5) {
                fallbackAttemptsCount++
                val videoId = ext.streamData.videoId
                val currentPos = com.example.ui.player.GlobalPlayerManager.currentPositionMs.value.coerceAtLeast(0L)
                Log.w("MainViewModel", "[Fallback] HTTP $errorCode / formats exhausted for video $videoId. Triggering yt-dlp resolver recovery...")
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val ytDlpResult = com.example.extractor.YtDlpResolver.extractStreamInfo(getApplication(), videoId)
                        if (ytDlpResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                            withContext(Dispatchers.Main) {
                                _extractionResult.value = ytDlpResult
                                val bestOption = ytDlpResult.streamData.selectedStreamOption
                                    ?: ytDlpResult.streamData.availableStreamOptions.firstOrNull { it.isMuxed && !(it.videoUrl ?: it.audioUrl).isNullOrBlank() }
                                    ?: ytDlpResult.streamData.availableStreamOptions.firstOrNull()
                                if (bestOption != null) {
                                    _selectedStreamOption.value = bestOption
                                    selectStreamOption(bestOption)
                                }
                            }
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "[Fallback] yt-dlp fallback extraction error: ${e.message}")
                    }
                }
            } else if (nextIndex < options.size && fallbackAttemptsCount < 6) {
                fallbackAttemptsCount++
                val nextOption = options[nextIndex]
                Log.d("MainViewModel", "[Fallback] Playback failed for '${current.qualityLabel}'. Auto switching ($fallbackAttemptsCount/6) to backup option: '${nextOption.qualityLabel}'")
                selectStreamOption(nextOption)
            } else {
                Log.w("MainViewModel", "[Fallback] All fallback stream options exhausted.")
            }
        }
    }

    fun selectStreamOption(option: PlayableStreamOption) {
        _selectedStreamOption.value = option
        val ext = _extractionResult.value
        if (ext is YouTubeExtractorHelper.ExtractionResult.Success) {
            val currentPos = com.example.ui.player.GlobalPlayerManager.currentPositionMs.value.coerceAtLeast(0L)
            val updatedStreamData = ext.streamData.copy(selectedStreamOption = option)
            _extractionResult.value = YouTubeExtractorHelper.ExtractionResult.Success(updatedStreamData)

            val url = option.videoUrl ?: ""
            if (option.providerType == com.example.model.ProviderType.TORRENT || url.contains("/stream")) {
                val matchingRelease = activeTorrentReleasesMap[url]
                    ?: activeTorrentReleasesMap.values.firstOrNull { url.contains(it.infoHash) }
                    ?: activeTorrentReleasesMap.values.firstOrNull { option.qualityLabel.contains(it.quality) && option.qualityLabel.contains(it.provider) }
                if (matchingRelease != null) {
                    val assignedPort = getOrStartTorrentServer()
                    torrentEngine.startSession(matchingRelease, streamPort = assignedPort)
                }
            }

            com.example.ui.player.GlobalPlayerManager.prepareAndPlay(
                context = getApplication(),
                streamData = updatedStreamData,
                streamOption = option,
                hlsUrl = null,
                captionOption = _selectedCaptionOption.value,
                initialPos = currentPos
            )
        }
    }

    fun selectCaptionOption(caption: CaptionOption?) {
        _selectedCaptionOption.value = caption
    }

    fun playEpisode(episode: com.example.model.EpisodeItem, streamData: StreamData? = null) {
        val currentStream = streamData ?: (_extractionResult.value as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData
        val provider = currentStream?.providerId ?: ""

        // 1. Direct stream option match (e.g. Archive.org or multi-source options)
        if (currentStream?.availableStreamOptions?.isNotEmpty() == true) {
            val matchingOption = currentStream.availableStreamOptions.firstOrNull { option ->
                option.videoUrl == episode.id ||
                option.qualityLabel.contains(episode.title, ignoreCase = true) ||
                option.qualityLabel.contains(String.format("%02dx%02d", episode.seasonNumber, episode.episodeNumber), ignoreCase = true) ||
                option.qualityLabel.contains("s${episode.seasonNumber}e${episode.episodeNumber}", ignoreCase = true) ||
                option.qualityLabel.contains("S0${episode.seasonNumber}E0${episode.episodeNumber}", ignoreCase = true)
            }
            if (matchingOption != null) {
                selectStreamOption(matchingOption)
                return
            } else if (episode.id.startsWith("http")) {
                val newOption = PlayableStreamOption(
                    qualityLabel = "${episode.title} (Direct)",
                    format = "mp4",
                    isMuxed = true,
                    videoUrl = episode.id,
                    providerType = com.example.model.ProviderType.DIRECT,
                    headers = if (provider == "archive_org") mapOf("Referer" to "https://archive.org/") else emptyMap()
                )
                selectStreamOption(newOption)
                return
            }
        }

        // 2. Search and play episode via torrent / resolver
        val title = currentStream?.title ?: _activeVideoItem.value?.title ?: ""
        val cleanTitle = com.example.util.TMDBHelper.cleanTitleForSearch(title)

        val mediaIdentity = com.example.torrent.provider.MediaIdentity(
            title = cleanTitle,
            mediaType = "tv",
            season = episode.seasonNumber,
            episode = episode.episodeNumber,
            imdbId = currentStream?.videoId?.takeIf { it.startsWith("tt") }
        )
        searchTorrentReleases(mediaIdentity)

        val epVideoItem = VideoItem(
            id = episode.id,
            title = "$cleanTitle - S${episode.seasonNumber}E${episode.episodeNumber}: ${episode.title}",
            uploaderName = currentStream?.channelName ?: "TV Series",
            thumbnailUrl = episode.thumbnailUrl ?: currentStream?.thumbnailUrl,
            providerId = provider.ifBlank { "torrent" }
        )
        _activeVideoItem.value = epVideoItem
        playVideo(episode.id, provider.ifBlank { "torrent" })
    }

    // --- BitTorrent Native Engine & P2P Stream Pipeline ---
    private val torrentEngine = com.example.torrent.engine.TorrentEngine.getInstance(getApplication())
    private var torrentHttpServer: com.example.torrent.server.TorrentHttpServer? = null
    private val activeTorrentReleasesMap = java.util.concurrent.ConcurrentHashMap<String, com.example.torrent.model.TorrentRelease>()

    private fun getOrStartTorrentServer(): Int {
        val server = torrentHttpServer ?: com.example.torrent.server.TorrentHttpServer(torrentEngine, port = 0).also {
            torrentHttpServer = it
        }
        return server.start()
    }

    val torrentEngineStats: StateFlow<com.example.torrent.model.TorrentEngineStats> = torrentEngine.stats

    private val _torrentReleases = MutableStateFlow<List<com.example.torrent.model.TorrentRelease>>(emptyList())
    val torrentReleases: StateFlow<List<com.example.torrent.model.TorrentRelease>> = _torrentReleases.asStateFlow()

    private val _isSearchingTorrents = MutableStateFlow(false)
    val isSearchingTorrents: StateFlow<Boolean> = _isSearchingTorrents.asStateFlow()

    private var torrentSearchJob: Job? = null

    fun searchTorrentReleases(identity: com.example.torrent.provider.MediaIdentity) {
        torrentSearchJob?.cancel()
        _isSearchingTorrents.value = true
        _torrentReleases.value = emptyList()

        torrentSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val manager = com.example.torrent.provider.TorrentProviderManager.getInstance()
                val results = manager.searchReleases(identity.title, identity)
                _torrentReleases.value = results
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error searching torrents: ${e.message}")
                _torrentReleases.value = emptyList()
            } finally {
                _isSearchingTorrents.value = false
            }
        }
    }

    fun clearTorrentReleases() {
        _torrentReleases.value = emptyList()
        _isSearchingTorrents.value = false
    }

    private suspend fun fetchTorrentTrendingFeed(): List<VideoItem> {
        val helper = com.example.util.ExploreMediaHelper
        val movies = helper.fetchCategoryItems(com.example.model.ExploreMediaType.MOVIE).take(15)
        val tv = helper.fetchCategoryItems(com.example.model.ExploreMediaType.TV).take(15)
        val anime = helper.fetchCategoryItems(com.example.model.ExploreMediaType.ANIME).take(15)

        val combined = (movies + tv + anime).shuffled()
        return combined.map { item ->
            val isTv = item.mediaType == com.example.model.ExploreMediaType.TV
            val yearStr = if (!item.releaseYear.isNullOrBlank()) " (${item.releaseYear})" else ""
            val thumb = item.backdropUrl?.ifBlank { null } ?: item.posterUrl
            val studio = com.example.util.StudioDetector.detectStudio(item.title, isTv)
            val studioLogo = com.example.util.ChannelLogoHelper.getBrandInfo(studio, null, item.title).logoUrls.firstOrNull()
            VideoItem(
                id = "torrent_media_${item.id}",
                title = "${item.title}$yearStr",
                uploaderName = studio,
                uploaderAvatarUrl = studioLogo,
                thumbnailUrl = thumb,
                durationSeconds = -1L,
                providerId = "torrent",
                description = item.overview ?: "",
                uploadDate = item.releaseYear
            )
        }
    }

    private suspend fun searchTorrentMedia(query: String): List<VideoItem> {
        val helper = com.example.util.ExploreMediaHelper
        val results = helper.searchAll(query)
        return results.map { item ->
            val isTv = item.mediaType == com.example.model.ExploreMediaType.TV
            val yearStr = if (!item.releaseYear.isNullOrBlank()) " (${item.releaseYear})" else ""
            val thumb = item.backdropUrl?.ifBlank { null } ?: item.posterUrl
            val studio = com.example.util.StudioDetector.detectStudio(item.title, isTv)
            val studioLogo = com.example.util.ChannelLogoHelper.getBrandInfo(studio, null, item.title).logoUrls.firstOrNull()
            VideoItem(
                id = "torrent_media_${item.id}",
                title = "${item.title}$yearStr",
                uploaderName = studio,
                uploaderAvatarUrl = studioLogo,
                thumbnailUrl = thumb,
                durationSeconds = -1L,
                providerId = "torrent",
                description = item.overview ?: "",
                uploadDate = item.releaseYear
            )
        }
    }

    fun playTorrentRelease(
        release: com.example.torrent.model.TorrentRelease,
        identity: com.example.torrent.provider.MediaIdentity,
        posterUrl: String? = null
    ) {
        // 1. Immediately cancel previous job and stop prior playback
        activePlaybackJob?.cancel()
        com.example.ui.player.GlobalPlayerManager.stopAndClear()
        torrentEngine.stopSession(clearCache = false)

        // 2. Start local HTTP Range bridge server on free port
        val assignedPort = getOrStartTorrentServer()

        // 3. Start torrent session
        val session = torrentEngine.startSession(release, streamPort = assignedPort)

        // 4. Construct stream options & media data with hash-qualified URLs
        val currentReleases = _torrentReleases.value.ifEmpty { listOf(release) }
        currentReleases.forEach { rel ->
            val isDebrid = rel.magnetUrl.startsWith("http://") || rel.magnetUrl.startsWith("https://")
            val normHash = rel.infoHash.lowercase().trim()
            val streamUrl = if (isDebrid) rel.magnetUrl else "http://127.0.0.1:$assignedPort/stream?hash=$normHash"
            activeTorrentReleasesMap[streamUrl] = rel
            activeTorrentReleasesMap[normHash] = rel
        }

        val options = currentReleases.map { rel ->
            val isDebrid = rel.magnetUrl.startsWith("http://") || rel.magnetUrl.startsWith("https://")
            val normHash = rel.infoHash.lowercase().trim()
            val streamUrl = if (isDebrid) rel.magnetUrl else "http://127.0.0.1:$assignedPort/stream?hash=$normHash"
            val label = buildString {
                append(rel.quality)
                if (rel.codec.isNotBlank()) append(" • ").append(rel.codec)
                if (rel.hdr.isNotBlank()) append(" • ").append(rel.hdr)
                append(" [${rel.provider} - ${rel.seeders} seeds]")
            }
            PlayableStreamOption(
                qualityLabel = label,
                format = "mkv",
                isMuxed = true,
                videoUrl = streamUrl,
                audioUrl = null,
                providerType = if (isDebrid) com.example.model.ProviderType.DIRECT else com.example.model.ProviderType.TORRENT,
                headers = mapOf("Accept-Ranges" to "bytes")
            )
        }

        val targetHash = release.infoHash.lowercase().trim()
        val primaryOption = options.firstOrNull { it.videoUrl?.contains(targetHash) == true } ?: options.first()

        val displayTitle = if (release.title.isNotBlank()) release.title else identity.title
        val isTv = identity.mediaType.equals("tv", ignoreCase = true)
        val studio = com.example.util.StudioDetector.detectStudio(displayTitle, isTv)
        val studioLogo = posterUrl ?: com.example.util.ChannelLogoHelper.getBrandInfo(studio, null, displayTitle).logoUrls.firstOrNull()
        val desc = "Native BitTorrent Stream • Size: ${release.formattedSize} • Studio: $studio"

        val videoItem = VideoItem(
            id = "torrent_${targetHash}",
            title = displayTitle,
            uploaderName = studio,
            uploaderAvatarUrl = studioLogo,
            thumbnailUrl = posterUrl,
            providerId = "torrent"
        )
        _activeVideoItem.value = videoItem
        _activeVideoId.value = videoItem.id
        recordVideoView(videoItem)

        val streamData = StreamData(
            videoId = videoItem.id,
            title = displayTitle,
            channelName = studio,
            channelAvatarUrl = studioLogo,
            description = desc,
            availableStreamOptions = options,
            selectedStreamOption = primaryOption,
            providerId = "torrent",
            providerType = com.example.model.ProviderType.TORRENT
        )

        _extractionResult.value = YouTubeExtractorHelper.ExtractionResult.Success(streamData)
        _selectedStreamOption.value = primaryOption
        _isPlaying.value = true
        com.example.ui.player.GlobalPlayerManager.resetFirstFrameState()

        // 5. Navigate to Player screen
        _currentScreen.value = AppScreen.PLAYER

        // 6. Launch coroutine to await metadata & initial header buffer before ExoPlayer starts
        activePlaybackJob = viewModelScope.launch(Dispatchers.IO) {
            var waitMs = 0
            val maxWait = 30000
            while (torrentEngine.getActiveFileLength() <= 0 && waitMs < maxWait && isActive) {
                delay(300)
                waitMs += 300
            }

            if (torrentEngine.getActiveFileLength() <= 0) {
                Log.w("ButterflyTorrent", "Metadata resolution timed out for $targetHash")
                withContext(Dispatchers.Main) {
                    _unifiedStatusMessage.value = "Failed to retrieve swarm metadata for release"
                }
                return@launch
            }

            // Await initial piece range buffer (first 512 KB) so Media3 can parse headers smoothly
            torrentEngine.awaitRangeAvailable(0, 512 * 1024, timeoutMs = 15000L)

            if (!isActive) return@launch

            withContext(Dispatchers.Main) {
                com.example.ui.player.GlobalPlayerManager.prepareAndPlay(
                    context = getApplication(),
                    streamData = streamData,
                    streamOption = primaryOption,
                    hlsUrl = null,
                    captionOption = null
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        torrentEngine.stopSession(clearCache = false)
        torrentHttpServer?.stop()
        torrentHttpServer = null
        unifiedPlayback.release()
    }

    // --- Unified Source Resolver (Vega + BitTorrent) ---
    private val unifiedResolver by lazy { com.example.resolver.UnifiedSourceResolver.getInstance(getApplication()) }
    private val unifiedPlayback by lazy { com.example.resolver.UnifiedPlaybackResolver.getInstance(getApplication()) }

    private val _unifiedCandidates = MutableStateFlow<List<com.example.resolver.SourceCandidate>>(emptyList())
    val unifiedCandidates: StateFlow<List<com.example.resolver.SourceCandidate>> = _unifiedCandidates.asStateFlow()

    val activeSourceCandidate: StateFlow<com.example.resolver.SourceCandidate?> = unifiedPlayback.activeCandidate
    val isResolvingUnifiedPlayback: StateFlow<Boolean> = unifiedPlayback.isResolving

    private val _isResolvingUnifiedSources = MutableStateFlow(false)
    val isResolvingUnifiedSources: StateFlow<Boolean> = _isResolvingUnifiedSources.asStateFlow()

    private val _unifiedStatusMessage = MutableStateFlow("")
    val unifiedStatusMessage: StateFlow<String> = _unifiedStatusMessage.asStateFlow()

    private var unifiedResolutionJob: Job? = null

    fun resolveUnifiedSourcesForMedia(identity: com.example.model.MediaIdentity, force: Boolean = false) {
        val activeProv = _activeVideoItem.value?.providerId ?: ""
        val titleLower = identity.title.lowercase()
        val isJav = com.example.metadata.JavIdParser.isJavCode(identity.title) || com.example.metadata.JavIdParser.isJavCode(identity.rawQueryOrUrl)
        val isMultiSourceMedia = force || isJav || activeProv == "torrent" || activeProv == "vega" || activeProv.startsWith("vega_") ||
                titleLower.contains("movie") || titleLower.contains("season") || titleLower.contains("s0") || titleLower.contains("s1")

        if (!isMultiSourceMedia) {
            _isResolvingUnifiedSources.value = false
            return
        }

        unifiedResolutionJob?.cancel()
        _isResolvingUnifiedSources.value = true
        _unifiedCandidates.value = emptyList()
        _unifiedStatusMessage.value = if (isJav) "Resolving JAV streams & swarms..." else "Searching Vega, Debrid & Torrent swarms..."

        unifiedResolutionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                unifiedResolver.resolveSources(identity).collect { candidates ->
                    _unifiedCandidates.value = candidates
                    if (candidates.isNotEmpty()) {
                        _unifiedStatusMessage.value = "Found ${candidates.size} sources"
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Unified source resolution failed: ${e.message}")
            } finally {
                _isResolvingUnifiedSources.value = false
            }
        }
    }

    fun switchUnifiedSource(candidate: com.example.resolver.SourceCandidate) {
        viewModelScope.launch {
            unifiedPlayback.switchSource(
                candidate = candidate,
                onStatus = { status ->
                    _unifiedStatusMessage.value = status
                }
            )
        }
    }

    private fun interleaveLists(lists: List<List<VideoItem>>): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        var maxLen = 0
        lists.forEach { if (it.size > maxLen) maxLen = it.size }

        for (i in 0 until maxLen) {
            for (list in lists) {
                if (i < list.size) {
                    result.add(list[i])
                }
            }
        }
        return result
    }
}
