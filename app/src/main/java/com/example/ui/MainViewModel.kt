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
import com.example.model.NodeScanState
import com.example.model.PlayableStreamOption
import com.example.model.ProviderUiItem
import com.example.model.ServerNode
import com.example.model.ServerScanState
import com.example.model.StreamData
import com.example.model.UserPlaylist
import com.example.model.UserProfile
import com.example.model.VideoItem
import com.example.plugin.manager.ExtensionManager
import com.example.plugin.manager.ExtensionStatus
import com.example.plugin.manager.InstallationResult
import com.example.plugin.manager.PluginManager
import com.example.plugin.manager.Repository
import com.example.plugin.manager.RepositoryManager
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.PluginStreamInfo
import com.example.plugin.jav.orchestrator.UnifiedJavOrchestrator
import com.example.plugin.jav.ProviderStatusState
import com.example.plugin.jav.ProviderDiagnosticResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import com.example.engine.SearchAutocompleteEngine

import com.example.db.AppDatabase
import com.example.db.WatchHistoryEntity
import com.example.db.BookmarkEntity
import com.example.db.LikedVideoEntity
import com.example.db.UserPlaylistEntity
import com.example.db.OfflineDownloadEntity
import com.example.util.OfflineDownloadManager
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
        "eztv torrent releases",
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
    val pluginManager = PluginManager(application)
    val repositoryManager = RepositoryManager(application, pluginManager)
    val extensionManager = ExtensionManager(application, pluginManager, repositoryManager)
    
    val libraryRepository = com.example.repository.LibraryRepository(application)
    val syncStatus = com.example.repository.SyncRepository.syncStatus
    val videoTagPreferences = com.example.util.VideoTagPreferences.getInstance(application)

    fun triggerCloudBackup() {
        com.example.repository.SyncRepository.triggerSync()
    }

    fun signOutCloudUser() {
        com.example.repository.SyncRepository.signOut(getApplication())
    }

    fun registerWithEmail(email: String, pass: String, callback: (Boolean, String?) -> Unit) {
        com.example.repository.SyncRepository.setUserEmail(getApplication(), email)
        callback(true, null)
    }

    fun signInWithEmail(email: String, pass: String, callback: (Boolean, String?) -> Unit) {
        com.example.repository.SyncRepository.setUserEmail(getApplication(), email)
        callback(true, null)
    }
    
    val searchEngine = com.example.engine.SearchEngine(application)
    val providerEngine = com.example.engine.ProviderEngine(application, pluginManager, repositoryManager, extensionManager)
    val playbackEngine = com.example.engine.PlaybackEngine(application)

    private val sourcePipelineEngine = com.example.plugin.manager.SourcePipelineEngine(context = application)

    private val _orionApiKey = MutableStateFlow(
        com.example.util.DebridSettingsManager.getOrionApiKey(application)
    )
    val orionApiKey: StateFlow<String> = _orionApiKey.asStateFlow()

    private val _reactionGroups = MutableStateFlow<List<com.example.util.ReactionGroup>>(emptyList())
    val reactionGroups: StateFlow<List<com.example.util.ReactionGroup>> = _reactionGroups.asStateFlow()

    private val _isLoadingReactions = MutableStateFlow(false)
    val isLoadingReactions: StateFlow<Boolean> = _isLoadingReactions.asStateFlow()

    fun loadReactionsForCurrentVideo(title: String, uploaderName: String, isTorrent: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingReactions.value = true
            try {
                val groups = com.example.util.ReactionHelper.fetchReactions(title, uploaderName, isTorrent)
                _reactionGroups.value = groups
            } catch (e: Exception) {
                _reactionGroups.value = emptyList()
            } finally {
                _isLoadingReactions.value = false
            }
        }
    }

    fun updateOrionApiKey(key: String) {
        com.example.util.DebridSettingsManager.setOrionApiKey(getApplication(), key)
        _orionApiKey.value = key.trim()
    }

    private val _cometUrl = MutableStateFlow(
        com.example.util.DebridSettingsManager.getCometEndpoint(application)
    )
    val cometUrl: StateFlow<String> = _cometUrl.asStateFlow()

    fun updateCometUrl(url: String) {
        com.example.util.DebridSettingsManager.setCometEndpoint(getApplication(), url)
        _cometUrl.value = url.trim()
    }

    private val _mediaFusionUrl = MutableStateFlow(
        com.example.util.DebridSettingsManager.getMediaFusionEndpoint(application)
    )
    val mediaFusionUrl: StateFlow<String> = _mediaFusionUrl.asStateFlow()

    fun updateMediaFusionUrl(url: String) {
        com.example.util.DebridSettingsManager.setMediaFusionEndpoint(getApplication(), url)
        _mediaFusionUrl.value = url.trim()
    }

    private val _zileanUrl = MutableStateFlow(
        com.example.util.DebridSettingsManager.getZileanEndpoint(application)
    )
    val zileanUrl: StateFlow<String> = _zileanUrl.asStateFlow()

    fun updateZileanUrl(url: String) {
        com.example.util.DebridSettingsManager.setZileanEndpoint(getApplication(), url)
        _zileanUrl.value = url.trim()
    }

    private val _apijavUrl = MutableStateFlow(
        com.example.util.DebridSettingsManager.getApijavEndpoint(getApplication())
    )
    val apijavUrl: StateFlow<String> = _apijavUrl.asStateFlow()

    fun updateApijavUrl(url: String) {
        com.example.util.DebridSettingsManager.setApijavEndpoint(getApplication(), url)
        _apijavUrl.value = url.trim()
    }

    private val _javinfoUrl = MutableStateFlow(
        com.example.util.DebridSettingsManager.getJavInfoEndpoint(getApplication())
    )
    val javinfoUrl: StateFlow<String> = _javinfoUrl.asStateFlow()

    fun updateJavinfoUrl(url: String) {
        com.example.util.DebridSettingsManager.setJavInfoEndpoint(getApplication(), url)
        _javinfoUrl.value = url.trim()
    }

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

    fun isAdultProviderId(providerId: String?): Boolean {
        val pid = providerId?.lowercase() ?: return false
        return pid.contains("apijav") || pid.contains("eporner") || pid.contains("porn") ||
               pid.contains("hentai") || pid.contains("javinfo") || pid == "adult" || pid.contains("jav") ||
               pid.contains("pornhub") || pid.contains("redtube") || pid.contains("xhamster")
    }

    fun isAdultVideoItem(item: VideoItem): Boolean {
        if (isAdultProviderId(item.providerId)) return true
        val uploader = item.uploaderName?.lowercase() ?: ""
        val title = item.title.lowercase()
        val id = item.id.lowercase()
        val adultKeywords = listOf("18+", "jav", "porn", "hentai", "xxx", "nsfw", "erotic", "adult", "uncensored", "brazzers", "fc2")
        return adultKeywords.any { uploader.contains(it) || title.contains(it) } ||
               id.startsWith("jav_") || id.startsWith("adult_") || id.contains("apijav") || id.contains("eporner") ||
               id.contains("pornhub") || id.contains("redtube") || id.contains("xhamster")
    }

    fun isAdultSearchQuery(query: String): Boolean {
        val q = query.lowercase().trim()
        if (q.isBlank()) return false
        val adultKeywords = listOf(
            "jav", "hentai", "porn", "xxx", "18+", "nsfw", "eporner", "pornhub", "redtube",
            "xhamster", "javinfo", "apijav", "uncensored", "censored jav", "r18", "erotic",
            "nude", "sex", "brazzers", "fc2", "ssis", "ipx", "stars-", "sone-", "mide-", "ssni-", "juq-"
        )
        if (adultKeywords.any { q.contains(it) }) return true
        return _watchHistory.value.any { isAdultVideoItem(it) && (it.title.contains(q, ignoreCase = true) || q.contains(it.title, ignoreCase = true)) }
    }

    fun isAdultDownload(entity: OfflineDownloadEntity): Boolean {
        val title = entity.title.lowercase()
        val channel = entity.channelName.lowercase()
        val id = entity.videoId.lowercase()
        val adultKeywords = listOf("18+", "jav", "porn", "hentai", "xxx", "nsfw", "erotic", "adult", "uncensored", "brazzers")
        return adultKeywords.any { title.contains(it) || channel.contains(it) } ||
               id.startsWith("jav_") || id.startsWith("adult_") || isAdultProviderId(id)
    }

    // Theme & Appearance Settings
    private val prefs = application.getSharedPreferences("app_settings_prefs", android.content.Context.MODE_PRIVATE)

    private val _adultContentEnabled = MutableStateFlow(
        prefs.getBoolean("adult_content_enabled", false)
    )
    val adultContentEnabled: StateFlow<Boolean> = _adultContentEnabled.asStateFlow()

    fun setAdultContentEnabled(enabled: Boolean) {
        _adultContentEnabled.value = enabled
        prefs.edit().putBoolean("adult_content_enabled", enabled).apply()

        if (!enabled) {
            _trendingVideos.value = _trendingVideos.value.filterNot { isAdultVideoItem(it) }
            _searchResults.value = _searchResults.value.filterNot { isAdultVideoItem(it) }
            _recommendedVideos.value = _recommendedVideos.value.filterNot { isAdultVideoItem(it) }
            if (isAdultProviderId(_activeProviderId.value)) {
                _activeProviderId.value = "all"
            }
        }

        refreshProvidersList()
    }

    private val _themeMode = MutableStateFlow(ThemeMode.AMOLED_DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(AppAccentColor.YELLOW)
    val accentColor: StateFlow<AppAccentColor> = _accentColor.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setAccentColor(accent: AppAccentColor) {
        _accentColor.value = accent
    }

    val repositories: StateFlow<List<Repository>> = repositoryManager.repositories
    val extensionStatuses: StateFlow<List<ExtensionStatus>> = extensionManager.extensionStatuses

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _isPipMode = MutableStateFlow(false)
    val isPipMode: StateFlow<Boolean> = _isPipMode.asStateFlow()

    fun setPipMode(enabled: Boolean) {
        _isPipMode.value = enabled
    }

    private val _activeProviderId = MutableStateFlow("all")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val subTorrentProviderIds = setOf(
        "yts_torrents", "eztv_torrents", "torrentio_aggregator", "torrent_api_multi", "tmdb_movies", "nyaa_si"
    )

    private val _enabledProviderIds = MutableStateFlow<Set<String>>(
        setOf(
            "all", "unified_torrents", "youtube", "jikan_anime",
            "dailymotion", "javinfo", "apijav_server", "apijav_hentai", "apijav_porn", "eporner",
            "archive_org", "mega", "telegram", "direct_mp4", "direct_hls", "rss_video", "json",
            "javinizer_go", "avm_engine", "javdex", "openaver", "mdcx", "fss", "javlibrary", "jav321", "javdb", "javbus", "javmenu", "airav", "arzon", "gfriends",
            "javpy_resolver", "missav_surrit", "jable_tv", "avgle_api", "jav_trailers", "supjav", "javcl", "jav18", "hanime_tv", "iwara",
            "orion", "comet", "mediafusion", "zilean"
        )
    )
    val enabledProviderIds: StateFlow<Set<String>> = _enabledProviderIds.asStateFlow()

    private val _availableProviders = MutableStateFlow<List<ProviderUiItem>>(emptyList())
    val availableProviders: StateFlow<List<ProviderUiItem>> = _availableProviders.asStateFlow()

    private val _providerDiagnosticsMap = MutableStateFlow<Map<String, ProviderDiagnosticResult>>(emptyMap())
    val providerDiagnosticsMap: StateFlow<Map<String, ProviderDiagnosticResult>> = _providerDiagnosticsMap.asStateFlow()

    fun runAllDiagnostics(testJavId: String = "IPX-800") {
        viewModelScope.launch(Dispatchers.IO) {
            val results = UnifiedJavOrchestrator.runDiagnostics(testJavId)
            val map = results.associateBy { it.providerId }
            _providerDiagnosticsMap.value = map
            refreshProvidersList()
        }
    }

    private val _watchProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val watchProgressMap: StateFlow<Map<String, Float>> = _watchProgressMap.asStateFlow()

    private val _watchPositionMsMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val watchPositionMsMap: StateFlow<Map<String, Long>> = _watchPositionMsMap.asStateFlow()

    private val _hiddenVideoIds = MutableStateFlow<Set<String>>(
        com.example.util.NotInterestedManager.getHiddenVideoIds(application)
    )
    val hiddenVideoIds: StateFlow<Set<String>> = _hiddenVideoIds.asStateFlow()

    private val _notInterestedChannels = MutableStateFlow<Set<String>>(
        com.example.util.NotInterestedManager.getBlockedChannels(application)
    )
    val notInterestedChannels: StateFlow<Set<String>> = _notInterestedChannels.asStateFlow()

    private val _notInterestedVideoIds = MutableStateFlow<Set<String>>(
        com.example.util.NotInterestedManager.getHiddenVideoIds(application)
    )
    val notInterestedVideoIds: StateFlow<Set<String>> = _notInterestedVideoIds.asStateFlow()

    fun isBlockedVideo(item: VideoItem): Boolean {
        val vid = item.id.trim()
        val ch = item.uploaderName?.trim()?.lowercase() ?: ""
        val embed = item.embedUrl ?: ""
        val hidden = _hiddenVideoIds.value
        val notInt = _notInterestedVideoIds.value
        val blockedChans = _notInterestedChannels.value

        if (vid.isNotEmpty() && (hidden.contains(vid) || notInt.contains(vid))) return true
        if (ch.isNotEmpty() && blockedChans.contains(ch)) return true
        if (embed.isNotEmpty() && (hidden.any { it.isNotBlank() && embed.contains(it) } || notInt.any { it.isNotBlank() && embed.contains(it) })) return true

        // Filter out videos where watched fraction >= 85%
        val watchedFraction = _watchProgressMap.value[vid] ?: 0f
        if (watchedFraction >= 0.85f) return true

        return false
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    private val _trendingVideos = MutableStateFlow<List<VideoItem>>(emptyList())
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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoadingTrending = MutableStateFlow(true)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

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

    private val _dislikedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val dislikedVideoIds: StateFlow<Set<String>> = _dislikedVideoIds.asStateFlow()

    fun markNotInterested(video: VideoItem) {
        markNotInterested(video.id, video.uploaderName)
    }

    fun markNotInterested(videoId: String, channelName: String? = null) {
        val cleanVid = videoId.trim()
        if (cleanVid.isEmpty()) return
        val cleanChannel = channelName?.trim() ?: ""

        // 1. Permanently persist in NotInterestedManager (SharedPreferences)
        com.example.util.NotInterestedManager.markNotInterested(getApplication(), cleanVid, cleanChannel)

        // 2. Update reactive StateFlow sets
        val updatedIds = _hiddenVideoIds.value + cleanVid
        _hiddenVideoIds.value = updatedIds
        _notInterestedVideoIds.value = _notInterestedVideoIds.value + cleanVid
        if (cleanChannel.isNotBlank()) {
            _notInterestedChannels.value = _notInterestedChannels.value + cleanChannel.lowercase()
        }

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
        com.example.util.NotInterestedManager.removeNotInterested(getApplication(), cleanVid)
        _hiddenVideoIds.value = _hiddenVideoIds.value - cleanVid
        _notInterestedVideoIds.value = _notInterestedVideoIds.value - cleanVid
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

    private val _isSearchExpanded = MutableStateFlow(false)
    val isSearchExpanded: StateFlow<Boolean> = _isSearchExpanded.asStateFlow()

    fun setSearchExpanded(expanded: Boolean) {
        _isSearchExpanded.value = expanded
        if (!expanded) {
            _searchQuery.value = ""
            _searchResults.value = emptyList()
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchExpanded.value = false
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

    fun recordWatchProgress(videoId: String, currentPositionMs: Long, totalDurationMs: Long) {
        if (totalDurationMs <= 0) return
        val fraction = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        _watchProgressMap.value = _watchProgressMap.value + (videoId to fraction)
        _watchPositionMsMap.value = _watchPositionMsMap.value + (videoId to currentPositionMs)
    }

    fun recordVideoView(video: VideoItem) {
        val filtered = _watchHistory.value.filterNot { it.id == video.id }
        _watchHistory.value = listOf(video) + filtered
        // Initialize default progress if not recorded
        if (!_watchProgressMap.value.containsKey(video.id)) {
            _watchProgressMap.value = _watchProgressMap.value + (video.id to 0.15f)
        }
        val historyEntity = WatchHistoryEntity(
            videoId = video.id,
            title = video.title ?: video.id,
            channelName = video.uploaderName ?: "",
            thumbnailUrl = video.thumbnailUrl,
            providerId = video.providerId,
            progressFraction = _watchProgressMap.value[video.id] ?: 0.15f
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
     * Smart Circadian & Persona Recommendation Engine Pipeline:
     * Ranks videos using multi-signal taste vectors, completion ratios,
     * channel affinity, time-of-day circadian learning, and channel diversity caps.
     */
    fun getRecommendedVideos(): List<VideoItem> {
        val allAvailable = (_trendingVideos.value + _searchResults.value)
            .distinctBy { (it.providerId ?: "") + "_" + it.id }
            .filterNot { isBlockedVideo(it) }
        if (allAvailable.isEmpty()) return emptyList()

        val historyEntities = _watchHistory.value.map { video ->
            WatchHistoryEntity(
                videoId = video.id,
                title = video.title ?: video.id,
                channelName = video.uploaderName ?: "",
                thumbnailUrl = video.thumbnailUrl,
                providerId = video.providerId ?: "general",
                progressFraction = _watchProgressMap.value[video.id] ?: 0.5f
            )
        }

        val bookmarkEntities = _watchLaterList.value.map { video ->
            com.example.db.BookmarkEntity(
                videoId = video.id,
                title = video.title ?: video.id,
                channelName = video.uploaderName ?: "",
                thumbnailUrl = video.thumbnailUrl,
                providerId = video.providerId
            )
        }

        val profile = com.example.engine.RecommendationPipelineEngine.buildTasteProfile(
            watchHistory = historyEntities,
            bookmarks = bookmarkEntities,
            likedVideoIds = _likedVideoIds.value,
            dislikedVideoIds = _dislikedVideoIds.value,
            notInterestedChannels = _notInterestedChannels.value,
            notInterestedVideoIds = _notInterestedVideoIds.value,
            recentSearches = if (_searchQuery.value.isNotBlank()) listOf(_searchQuery.value) else emptyList()
        )

        val ranked = com.example.engine.RecommendationPipelineEngine.processPipeline(
            candidates = allAvailable,
            tasteProfile = profile,
            watchHistory = historyEntities,
            likedVideoIds = _likedVideoIds.value,
            dislikedVideoIds = _dislikedVideoIds.value
        ).filterNot { isBlockedVideo(it) }

        return ranked.take(20)
    }

    private val _userPlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val userPlaylists: StateFlow<List<UserPlaylist>> = _userPlaylists.asStateFlow()

    fun addToQueue(video: VideoItem) {
        _playbackQueue.value = _playbackQueue.value + video
    }

    fun removeFromQueue(video: VideoItem) {
        _playbackQueue.value = _playbackQueue.value.filter { it.id != video.id }
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

    fun playNextInQueue() {
        val currentQueue = _playbackQueue.value
        if (currentQueue.isNotEmpty()) {
            val nextVideo = currentQueue.first()
            _playbackQueue.value = currentQueue.drop(1)
            playVideo(nextVideo.id, nextVideo.providerId)
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

    val downloadLiveProgress = OfflineDownloadManager.liveProgress

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
        viewModelScope.launch(Dispatchers.IO) {
            val targetUrl = streamOption?.videoUrl
                ?: streamOption?.videoStream?.content
                ?: streamOption?.videoStream?.url
                ?: (if (!streamOption?.audioUrl.isNullOrBlank()) streamOption?.audioUrl else null)

            if (!targetUrl.isNullOrBlank()) {
                OfflineDownloadManager.downloadVideo(
                    context = getApplication(),
                    videoId = videoId,
                    title = title,
                    channelName = channelName,
                    videoUrl = targetUrl,
                    thumbnailUrl = thumbnailUrl,
                    qualityLabel = qualityLabel,
                    headers = streamOption?.headers ?: emptyMap()
                )
            } else {
                try {
                    val result = com.example.extractor.YouTubeFastStreamResolver.resolveStream(videoId, getApplication())
                    if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                        val opts = result.streamData.availableStreamOptions
                        val cleanQ = qualityLabel.replace("p", "").trim()
                        val matched = opts.firstOrNull { it.qualityLabel.contains(cleanQ) }
                            ?: opts.firstOrNull { it.isMuxed }
                            ?: opts.firstOrNull()

                        val urlToDownload = matched?.videoUrl
                            ?: matched?.videoStream?.content
                            ?: matched?.videoStream?.url
                            ?: result.streamData.videoUrl

                        if (!urlToDownload.isNullOrBlank()) {
                            OfflineDownloadManager.downloadVideo(
                                context = getApplication(),
                                videoId = videoId,
                                title = title.ifBlank { result.streamData.title },
                                channelName = channelName.ifBlank { result.streamData.channelName },
                                videoUrl = urlToDownload,
                                thumbnailUrl = thumbnailUrl ?: result.streamData.channelAvatarUrl,
                                qualityLabel = qualityLabel,
                                headers = matched?.headers ?: emptyMap()
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to extract stream for download: ${e.message}")
                }
            }
        }
    }

    fun pauseDownload(videoId: String) {
        OfflineDownloadManager.pauseDownload(getApplication(), videoId)
    }

    fun resumeDownload(videoId: String) {
        OfflineDownloadManager.resumeDownload(getApplication(), videoId)
    }

    fun deleteDownload(videoId: String, localFilePath: String? = null) {
        OfflineDownloadManager.deleteDownload(getApplication(), videoId, localFilePath)
    }

    fun clearAllDownloads() {
        OfflineDownloadManager.clearAllDownloads(getApplication())
    }

    fun playOfflineDownload(download: OfflineDownloadEntity) {
        val file = java.io.File(download.localFilePath)
        val fileUri = if (file.exists()) "file://${file.absolutePath}" else download.localFilePath

        val option = PlayableStreamOption(
            qualityLabel = download.qualityLabel,
            format = "mp4",
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
            captionOption = null,
            embedUrl = null
        )
    }

    private val _selectedCaptionOption = MutableStateFlow<CaptionOption?>(null)
    val selectedCaptionOption: StateFlow<CaptionOption?> = _selectedCaptionOption.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun togglePlayback() {
        _isPlaying.value = !_isPlaying.value
    }

    fun closeVideo() {
        com.example.ui.player.GlobalPlayerManager.stopAndClear()
        _activeVideoId.value = null
        _extractionResult.value = null
        _selectedStreamOption.value = null
        _selectedCaptionOption.value = null
        _isPlaying.value = false
        if (_currentScreen.value == AppScreen.PLAYER) {
            _currentScreen.value = AppScreen.HOME
        }
    }

    init {
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
                repositoryManager.loadRepositories()
                extensionManager.refreshExtensions()
                refreshProvidersList()
                setActiveProvider("all")
            }
            @OptIn(FlowPreview::class)
            launch {
                _searchQuery
                    .debounce(250)
                    .collectLatest { query ->
                        if (query.isBlank()) {
                            _searchSuggestions.value = emptyList()
                        } else {
                            val suggestions = SearchAutocompleteEngine.getSuggestions(
                                query = query,
                                recentHistory = _recentSearches.value,
                                adultEnabled = _adultContentEnabled.value
                            )
                            _searchSuggestions.value = suggestions
                        }
                    }
            }
        }
    }

    fun addRepositorySource(url: String, name: String = "Custom Repository") {
        viewModelScope.launch(Dispatchers.IO) {
            repositoryManager.addRepository(url, name)
            extensionManager.refreshExtensions()
            refreshProvidersList()
        }
    }

    fun removeRepositorySource(repoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repositoryManager.removeRepository(repoId)
        }
    }

    fun installExtension(sourceStr: String, onResult: (InstallationResult) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = extensionManager.installExtensionFromSource(sourceStr)
            refreshProvidersList()
            onResult(res)
        }
    }

    fun uninstallExtension(pluginId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            extensionManager.uninstallExtension(pluginId)
            refreshProvidersList()
        }
    }

    fun updateExtension(pluginId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            extensionManager.updateExtension(pluginId)
            refreshProvidersList()
        }
    }

    fun navigateToScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun getActiveProvider(): ContentProviderApi? {
        val id = _activeProviderId.value
        if (id == "all") return null
        return pluginManager.getProvider(id)
    }

    fun reloadProviders() {
        viewModelScope.launch(Dispatchers.IO) {
            pluginManager.loadInstalledPlugins()
            refreshProvidersList()
            loadTrending()
        }
    }

    fun setActiveProvider(providerId: String) {
        _activeProviderId.value = providerId
        if (!_enabledProviderIds.value.contains(providerId)) {
            _enabledProviderIds.value = _enabledProviderIds.value + providerId
        }
        if (isAdultProviderId(providerId) && !_adultContentEnabled.value) {
            setAdultContentEnabled(true)
        }
        _searchResults.value = emptyList()
        _searchQuery.value = ""
        refreshProvidersList()
        loadTrending(forceRefresh = true)
    }

    fun toggleProviderEnabled(providerId: String) {
        val current = _enabledProviderIds.value.toMutableSet()
        val newState = !current.contains(providerId)
        if (newState) {
            current.add(providerId)
            if (isAdultProviderId(providerId) && !_adultContentEnabled.value) {
                setAdultContentEnabled(true)
            }
        } else {
            if (providerId == _activeProviderId.value) return
            current.remove(providerId)
        }
        _enabledProviderIds.value = current
        UnifiedJavOrchestrator.setProviderEnabled(providerId, newState)
        refreshProvidersList()
    }

    private fun refreshProvidersList() {
        val allNative = pluginManager.getAllAvailableProviders()
        val activeId = _activeProviderId.value
        val enabledSet = _enabledProviderIds.value
        val diagMap = _providerDiagnosticsMap.value

        val uiList = mutableListOf<ProviderUiItem>()

        // 1. All-In-One Feed
        uiList.add(
            ProviderUiItem(
                id = "all",
                name = "All Sources",
                description = "Aggregated feed combining all enabled content providers",
                category = "Aggregator",
                isEnabled = enabledSet.contains("all"),
                isDefault = (activeId == "all"),
                statusState = ProviderStatusState.SUCCESS,
                statusMessage = "Ready"
            )
        )

        // 2. Native Content Providers
        allNative.forEach { provider ->
            val id = provider.providerId
            if (id in subTorrentProviderIds) return@forEach

            val name = getReadableProviderName(id)
            val desc = getProviderRoleDescription(id)
            val category = getProviderCategoryName(id)
            val diag = diagMap[id]

            val isTorrent = provider.capabilities.supportsTorrent || provider.capabilities.providerType == com.example.plugin.sdk.model.ProviderType.TORRENT
            val pType = if (isTorrent) com.example.plugin.sdk.model.ProviderType.TORRENT else provider.capabilities.providerType

            val isEnabled = enabledSet.contains(id)
            val stState = diag?.status ?: if (isEnabled) ProviderStatusState.SUCCESS else ProviderStatusState.NO_RESULT
            val stMsg = diag?.let { "${it.status.name} (${it.responseTimeMs}ms)" } ?: if (isEnabled) "Ready / Active" else "Disabled"

            uiList.add(
                ProviderUiItem(
                    id = id,
                    name = name,
                    description = desc,
                    category = category,
                    isEnabled = isEnabled,
                    isDefault = (id == activeId),
                    providerType = pType,
                    statusState = stState,
                    statusMessage = stMsg,
                    responseTimeMs = diag?.responseTimeMs ?: 0L
                )
            )
        }

        // 3. Orchestrator Metadata Scrapers (Javinizer-Go, AVM, Javdex, OpenAver, MDCx, FSS, etc.)
        UnifiedJavOrchestrator.metadataProviders.forEach { meta ->
            val id = meta.id
            if (uiList.none { it.id == id }) {
                val diag = diagMap[id]
                val isEnabled = enabledSet.contains(id) && meta.isEnabled
                val stState = diag?.status ?: if (isEnabled) ProviderStatusState.SUCCESS else ProviderStatusState.NO_RESULT
                val stMsg = diag?.let { "${it.status.name} (${it.responseTimeMs}ms)" } ?: if (isEnabled) "Ready / Active" else "Disabled"

                uiList.add(
                    ProviderUiItem(
                        id = id,
                        name = getReadableProviderName(id).ifBlank { meta.name },
                        description = getProviderRoleDescription(id),
                        category = "Repository Catalog Scraper",
                        isEnabled = isEnabled,
                        isDefault = (id == activeId),
                        statusState = stState,
                        statusMessage = stMsg,
                        responseTimeMs = diag?.responseTimeMs ?: 0L
                    )
                )
            }
        }

        // 4. Orchestrator Stream Resolvers (JavPy, MissAV, Jable.tv, Avgle, etc.)
        UnifiedJavOrchestrator.streamProviders.forEach { stream ->
            val id = stream.id
            if (uiList.none { it.id == id }) {
                val diag = diagMap[id]
                val isEnabled = enabledSet.contains(id) && stream.isEnabled
                val stState = diag?.status ?: if (isEnabled) ProviderStatusState.SUCCESS else ProviderStatusState.NO_RESULT
                val stMsg = diag?.let { "${it.status.name} (${it.responseTimeMs}ms)" } ?: if (isEnabled) "Ready / Active" else "Disabled"

                uiList.add(
                    ProviderUiItem(
                        id = id,
                        name = getReadableProviderName(id).ifBlank { stream.name },
                        description = getProviderRoleDescription(id),
                        category = "Stream Resolver Engine",
                        isEnabled = isEnabled,
                        isDefault = (id == activeId),
                        statusState = stState,
                        statusMessage = stMsg,
                        responseTimeMs = diag?.responseTimeMs ?: 0L
                    )
                )
            }
        }

        _availableProviders.value = uiList
    }

    private fun getProviderRoleDescription(id: String): String {
        return when (id) {
            "all" -> "Aggregated feed combining all enabled content providers"
            "unified_torrents" -> "Multi-indexer torrent aggregator (YTS, EZTV, Torrentio, TMDB, Nyaa & Torrent API)"
            "youtube" -> "YouTube fast stream resolution, video search & channel metadata"
            "jikan_anime" -> "MyAnimeList & Jikan API for anime catalog search, episode guides & artwork"
            "dailymotion" -> "Dailymotion video discovery & embedded player stream resolver"
            "eporner" -> "Full HD adult video search & direct MP4 video stream provider"
            "apijav_server" -> "apiJAV WordPress REST API video server endpoint"
            "apijav_hentai" -> "apiJAV dedicated anime & hentai stream category provider"
            "apijav_porn" -> "apiJAV main adult movie & scenes stream provider"
            "javinfo" -> "Asian Cinema & JAV video code metadata & stream indexer"
            "archive_org" -> "Internet Archive public domain movies, documentaries & video library"
            "mega" -> "Mega.nz direct cloud storage video link stream resolver"
            "telegram" -> "Telegram public channel direct video stream resolver"
            "direct_mp4" -> "Direct .mp4 video URL player engine"
            "direct_hls" -> "Direct .m3u8 HTTP Live Streaming player engine"
            "rss_video" -> "Custom XML/RSS video feed parser"
            "json" -> "Custom JSON playlist & feed engine"

            "javinizer_go" -> "Go-based multi-source metadata fetcher querying R18 & MGStage catalog APIs"
            "avm_engine" -> "Native adult scraper for FC2 Club and DMM CID adult database entries"
            "javdex" -> "JavDB metadata indexer bypass with over18 session headers & cover art extractor"
            "openaver" -> "Go adult content search engine pipeline for JavMenu & JavBooks API endpoints"
            "mdcx" -> "Python metadata scraper module using AirAV barcode API & MGStage age-gate bypass"
            "fss" -> "Arzon adult catalog parser & DMM metadata aggregator"
            "javlibrary" -> "Core JAV catalog indexer for release dates, studios, actresses, and tags"
            "jav321" -> "Japanese video code search scraper for studio & release metadata"
            "javdb" -> "Community database indexer for JAV metadata, ratings, and cover art"
            "javbus" -> "JAV catalog scraper & magnet link aggregator"
            "javmenu" -> "Online JAV catalog & video page metadata scraper"
            "airav" -> "Barcode & video code metadata API resolver"
            "arzon" -> "Arzon adult DVD store detail page parser"
            "gfriends" -> "Actresses high-res avatar artwork provider"

            "javpy_resolver" -> "Python JavPy native stream resolver querying Avgle JAV API streams"
            "missav_surrit" -> "Surrit HLS video stream extractor for MissAV video player"
            "jable_tv" -> "Direct HLS .m3u8 video stream parser for Jable.tv"
            "avgle_api" -> "Direct REST API resolver for Avgle embedded video streams"
            "jav_trailers" -> "Official DMM PV preview trailer stream fetcher"
            "supjav" -> "Supjav streaming video embed parser"
            "javcl" -> "JavCL video embed link resolver"
            "jav18" -> "Jav18 video stream link extractor"
            "hanime_tv" -> "Anime & Hentai video stream resolver"
            "iwara" -> "Iwara 3D animation video stream parser"

            "orion" -> "Orion Stremio API - Torrent & Debrid hash indexer"
            "comet" -> "Comet Stremio - Fast Stremio add-on stream indexer"
            "mediafusion" -> "MediaFusion Stremio - Multi-source Stremio add-on indexer"
            "zilean" -> "Zilean DMM Indexer - DMM torrent indexer for Debrid playback"
            else -> "Media & Data Provider ($id)"
        }
    }

    private fun getProviderCategoryName(id: String): String {
        return when (id) {
            "all" -> "Aggregator"
            "youtube", "jikan_anime", "dailymotion", "archive_org", "mega", "telegram", "direct_mp4", "direct_hls", "rss_video", "json" -> "Media Content Feeds"
            "eporner", "apijav_server", "apijav_hentai", "apijav_porn", "javinfo" -> "Adult Media Feeds"
            "javinizer_go", "avm_engine", "javdex", "openaver", "mdcx", "fss", "javlibrary", "jav321", "javdb", "javbus", "javmenu", "airav", "arzon", "gfriends" -> "Repository Catalog Scrapers"
            "javpy_resolver", "missav_surrit", "jable_tv", "avgle_api", "jav_trailers", "supjav", "javcl", "jav18", "hanime_tv", "iwara" -> "Stream Resolver Engines"
            "orion", "comet", "mediafusion", "zilean", "unified_torrents" -> "Debrid & Indexers"
            else -> "Plugins & Extensions"
        }
    }

    private fun getReadableProviderName(id: String): String {
        return when (id) {
            "all" -> "All Sources"
            "unified_torrents" -> "Torrents (All Indexers)"
            "youtube" -> "YouTube"
            "bilibili" -> "Bilibili"
            "jikan_anime" -> "Anime (Jikan / MAL)"
            "dailymotion" -> "Dailymotion"
            "javinfo" -> "JavInfo API"
            "apijav" -> "APIJAV Network"
            "apijav_server" -> "APIJAV Server"
            "apijav_hentai" -> "APIJAV Hentai"
            "apijav_porn" -> "APIJAV Porn"
            "eporner" -> "Eporner HD"
            "peertube" -> "PeerTube"
            "vimeo" -> "Vimeo"
            "archive_org" -> "Archive.org"
            "ted" -> "TED Talks"
            "nasa" -> "NASA TV"
            "direct_mp4" -> "Direct MP4 Stream"
            "direct_hls" -> "Direct HLS Stream"
            "rss_video" -> "RSS Video Feed"
            "json" -> "Custom JSON Feed"

            "javinizer_go" -> "Javinizer-Go"
            "avm_engine" -> "Adult Video Manager (AVM)"
            "javdex" -> "Javdex"
            "openaver" -> "OpenAver"
            "mdcx" -> "MDCx Engine"
            "fss" -> "Film Scraper System (FSS)"
            "javlibrary" -> "JavLibrary"
            "jav321" -> "Jav321"
            "javdb" -> "JavDB"
            "javbus" -> "JavBus"
            "javmenu" -> "JavMenu"
            "airav" -> "AirAV API"
            "arzon" -> "Arzon Catalog"
            "gfriends" -> "GFriends Avatars"

            "javpy_resolver" -> "JavPy Stream Resolver"
            "missav_surrit" -> "MissAV / Surrit"
            "jable_tv" -> "Jable.tv"
            "avgle_api" -> "Avgle API"
            "jav_trailers" -> "DMM Free PV Trailers"
            "supjav" -> "Supjav"
            "javcl" -> "JavCL"
            "jav18" -> "Jav18"
            "hanime_tv" -> "Hanime.tv"
            "iwara" -> "Iwara 3D"

            "orion" -> "Orion Stremio"
            "comet" -> "Comet Stremio"
            "mediafusion" -> "MediaFusion Stremio"
            "zilean" -> "Zilean DMM Indexer"
            else -> id.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }

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

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val filtered = _recentSearches.value.filterNot { it.equals(q, ignoreCase = true) }
        val updated = (listOf(q) + filtered).take(10)
        _recentSearches.value = updated
        saveRecentSearches(updated)
    }

    fun removeRecentSearch(query: String) {
        val updated = _recentSearches.value.filterNot { it.equals(query, ignoreCase = true) }
        _recentSearches.value = updated
        saveRecentSearches(updated)
    }

    fun clearAllRecentSearches() {
        _recentSearches.value = emptyList()
        saveRecentSearches(emptyList())
    }

    fun openChannel(channelName: String) {
        val trimmed = channelName.trim()
        if (trimmed.isBlank()) return
        _isSearchExpanded.value = true
        updateSearchQuery(trimmed)
        performSearch(trimmed)
        if (_currentScreen.value == AppScreen.PLAYER) {
            _currentScreen.value = AppScreen.HOME
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(query: String? = null) {
        val q = query ?: _searchQuery.value
        if (q.isBlank()) return
        addRecentSearch(q)
        currentSearchPage = 1

        Log.d("MainViewModel", "Search query: '$q' on active provider: ${_activeProviderId.value}")

        _isSearching.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _feedError.value = null
            try {
                val activeId = _activeProviderId.value
                val combined = mutableListOf<VideoItem>()

                if (activeId == "all") {
                    val activeProviders = pluginManager.getAllAvailableProviders().filter {
                        _enabledProviderIds.value.contains(it.providerId) &&
                        (_adultContentEnabled.value || !isAdultProviderId(it.providerId))
                    }

                    val deferreds = activeProviders.map { provider ->
                        async {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    val paged = provider.search(q, "1")
                                    paged.items.map { item ->
                                        VideoItem(
                                            id = item.id,
                                            title = item.title,
                                            uploaderName = item.uploaderName,
                                            uploaderUrl = item.uploaderUrl,
                                            uploaderAvatarUrl = item.uploaderAvatarUrl,
                                            viewCount = item.viewCount,
                                            durationSeconds = item.durationSeconds,
                                            uploadDate = item.uploadDate,
                                            thumbnailUrl = item.thumbnailUrl,
                                            providerId = provider.providerId,
                                            description = item.description
                                        )
                                    }
                                } ?: emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val resultsList = deferreds.awaitAll()
                    val interleaved = interleaveLists(resultsList)
                    combined.addAll(interleaved)
                } else {
                    val provider = getActiveProvider()
                    if (provider != null) {
                        val items = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                            val paged = provider.search(q, "1")
                            paged.items.map { item ->
                                VideoItem(
                                    id = item.id,
                                    title = item.title,
                                    uploaderName = item.uploaderName,
                                    uploaderUrl = item.uploaderUrl,
                                    uploaderAvatarUrl = item.uploaderAvatarUrl,
                                    viewCount = item.viewCount,
                                    durationSeconds = item.durationSeconds,
                                    uploadDate = item.uploadDate,
                                    thumbnailUrl = item.thumbnailUrl,
                                    providerId = provider.providerId,
                                    description = item.description
                                )
                            }
                        } ?: emptyList()
                        combined.addAll(items)
                    }
                }
                _searchResults.value = if (_adultContentEnabled.value) combined else combined.filterNot { isAdultVideoItem(it) }
                updateRecommendedVideosAsync()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Search failed: ${e.localizedMessage}", e)
                _searchResults.value = emptyList()
                _feedError.value = FeedErrorDetails(
                    rawExceptionName = e.javaClass.simpleName,
                    message = e.localizedMessage ?: "Search failed",
                    fullStackTrace = e.stackTraceToString(),
                    urlOrQuery = q
                )
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
        _isLoadingTrending.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _feedError.value = null
            if (forceRefresh) {
                _searchResults.value = emptyList()
            }
            try {
                val activeId = _activeProviderId.value
                val combined = mutableListOf<VideoItem>()
                val pageToken: String? = "1"

                if (activeId == "all") {
                    val activeProviders = pluginManager.getAllAvailableProviders().filter {
                        _enabledProviderIds.value.contains(it.providerId) &&
                        (_adultContentEnabled.value || !isAdultProviderId(it.providerId))
                    }

                    val deferreds = activeProviders.map { provider ->
                        async {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    val paged = provider.home(pageToken)
                                    paged.items.map { item ->
                                        VideoItem(
                                            id = item.id,
                                            title = item.title,
                                            uploaderName = item.uploaderName,
                                            uploaderUrl = item.uploaderUrl,
                                            uploaderAvatarUrl = item.uploaderAvatarUrl,
                                            viewCount = item.viewCount,
                                            durationSeconds = item.durationSeconds,
                                            uploadDate = item.uploadDate,
                                            thumbnailUrl = item.thumbnailUrl,
                                            providerId = provider.providerId,
                                            description = item.description
                                        )
                                    }
                                } ?: emptyList()
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "Provider ${provider.providerId} failed home: ${e.message}")
                                emptyList()
                            }
                        }
                    }

                    val resultsList = deferreds.awaitAll()
                    val interleaved = interleaveLists(resultsList)
                    combined.addAll(if (forceRefresh) interleaved.shuffled() else interleaved)
                } else {
                    val provider = getActiveProvider()
                    if (provider != null) {
                        val items = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                            val paged = provider.home(pageToken)
                            paged.items.map { item ->
                                VideoItem(
                                    id = item.id,
                                    title = item.title,
                                    uploaderName = item.uploaderName,
                                    uploaderUrl = item.uploaderUrl,
                                    uploaderAvatarUrl = item.uploaderAvatarUrl,
                                    viewCount = item.viewCount,
                                    durationSeconds = item.durationSeconds,
                                    uploadDate = item.uploadDate,
                                    thumbnailUrl = item.thumbnailUrl,
                                    providerId = provider.providerId,
                                    description = item.description
                                )
                            }
                        } ?: emptyList()
                        combined.addAll(if (forceRefresh) items.shuffled() else items)
                    }
                }

                _trendingVideos.value = if (_adultContentEnabled.value) combined else combined.filterNot { isAdultVideoItem(it) }
                updateRecommendedVideosAsync()
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadTrending failed: ${e.localizedMessage}", e)
                _trendingVideos.value = emptyList()
                _feedError.value = FeedErrorDetails(
                    rawExceptionName = e.javaClass.simpleName,
                    message = e.localizedMessage ?: "Failed to fetch content",
                    fullStackTrace = e.stackTraceToString()
                )
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

                if (isSearchMode && q.isNotBlank()) {
                    currentSearchPage++
                    val activeId = _activeProviderId.value
                    val pageToken = currentSearchPage.toString()
                    val newItems = mutableListOf<VideoItem>()

                    if (activeId == "all") {
                        val activeProviders = pluginManager.getAllAvailableProviders().filter {
                            _enabledProviderIds.value.contains(it.providerId) &&
                            (_adultContentEnabled.value || !isAdultProviderId(it.providerId))
                        }
                        val deferreds = activeProviders.map { provider ->
                            async {
                                try {
                                    kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                        val paged = provider.search(q, pageToken)
                                        paged.items.map { item ->
                                            VideoItem(
                                                id = item.id,
                                                title = item.title,
                                                uploaderName = item.uploaderName,
                                                uploaderUrl = item.uploaderUrl,
                                                uploaderAvatarUrl = item.uploaderAvatarUrl,
                                                viewCount = item.viewCount,
                                                durationSeconds = item.durationSeconds,
                                                uploadDate = item.uploadDate,
                                                thumbnailUrl = item.thumbnailUrl,
                                                providerId = provider.providerId,
                                                description = item.description
                                            )
                                        }
                                    } ?: emptyList()
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                        }
                        val results = deferreds.awaitAll()
                        newItems.addAll(interleaveLists(results))
                    } else {
                        val provider = getActiveProvider()
                        if (provider != null) {
                            val items = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                val paged = provider.search(q, pageToken)
                                paged.items.map { item ->
                                    VideoItem(
                                        id = item.id,
                                        title = item.title,
                                        uploaderName = item.uploaderName,
                                        uploaderUrl = item.uploaderUrl,
                                        uploaderAvatarUrl = item.uploaderAvatarUrl,
                                        viewCount = item.viewCount,
                                        durationSeconds = item.durationSeconds,
                                        uploadDate = item.uploadDate,
                                        thumbnailUrl = item.thumbnailUrl,
                                        providerId = provider.providerId,
                                        description = item.description
                                    )
                                }
                            } ?: emptyList()
                            newItems.addAll(items)
                        }
                    }

                    val filtered = if (_adultContentEnabled.value) newItems else newItems.filterNot { isAdultVideoItem(it) }
                    val currentList = _searchResults.value
                    val combined = (currentList + filtered).distinctBy { it.id }
                    _searchResults.value = combined
                } else {
                    currentTrendingPage++
                    val activeId = _activeProviderId.value
                    val pageToken = currentTrendingPage.toString()
                    val newItems = mutableListOf<VideoItem>()

                    if (activeId == "all") {
                        val activeProviders = pluginManager.getAllAvailableProviders().filter {
                            _enabledProviderIds.value.contains(it.providerId) &&
                            (_adultContentEnabled.value || !isAdultProviderId(it.providerId))
                        }
                        val deferreds = activeProviders.map { provider ->
                            async {
                                try {
                                    kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                        val paged = provider.home(pageToken)
                                        paged.items.map { item ->
                                            VideoItem(
                                                id = item.id,
                                                title = item.title,
                                                uploaderName = item.uploaderName,
                                                uploaderUrl = item.uploaderUrl,
                                                uploaderAvatarUrl = item.uploaderAvatarUrl,
                                                viewCount = item.viewCount,
                                                durationSeconds = item.durationSeconds,
                                                uploadDate = item.uploadDate,
                                                thumbnailUrl = item.thumbnailUrl,
                                                providerId = provider.providerId,
                                                description = item.description
                                            )
                                        }
                                    } ?: emptyList()
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                        }
                        val results = deferreds.awaitAll()
                        newItems.addAll(interleaveLists(results))
                    } else {
                        val provider = getActiveProvider()
                        if (provider != null) {
                            val items = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                val paged = provider.home(pageToken)
                                paged.items.map { item ->
                                    VideoItem(
                                        id = item.id,
                                        title = item.title,
                                        uploaderName = item.uploaderName,
                                        uploaderUrl = item.uploaderUrl,
                                        uploaderAvatarUrl = item.uploaderAvatarUrl,
                                        viewCount = item.viewCount,
                                        durationSeconds = item.durationSeconds,
                                        uploadDate = item.uploadDate,
                                        thumbnailUrl = item.thumbnailUrl,
                                        providerId = provider.providerId,
                                        description = item.description
                                    )
                                }
                            } ?: emptyList()
                            newItems.addAll(items)
                        }
                    }

                    val filtered = if (_adultContentEnabled.value) newItems else newItems.filterNot { isAdultVideoItem(it) }
                    val currentList = _trendingVideos.value
                    var combined = (currentList + filtered).distinctBy { it.id }

                    // If provider didn't return any new unique items for page > 1, query diverse topics so infinite scroll continues
                    if (combined.size <= currentList.size) {
                        val topicIndex = (currentTrendingPage - 1) % DIVERSE_TOPICS.size
                        val fallbackTopic = DIVERSE_TOPICS[topicIndex]
                        try {
                            when (val res = YouTubeExtractorHelper.searchVideos(fallbackTopic)) {
                                is com.example.model.FeedResult.Success -> {
                                    val fallbackItems = res.items.map { it.copy(providerId = activeId.ifBlank { "youtube" }) }
                                    val filteredFallback = if (_adultContentEnabled.value) fallbackItems else fallbackItems.filterNot { isAdultVideoItem(it) }
                                    combined = (currentList + filteredFallback).distinctBy { it.id }
                                }
                                else -> {}
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Error fetching fallback topic for pagination", e)
                        }
                    }

                    _trendingVideos.value = combined
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadMoreContent failed: ${e.message}")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun playVideo(videoIdOrUrl: String, providerIdHint: String? = null) {
        val cleanIdOrUrl = videoIdOrUrl.trim()
        if (cleanIdOrUrl.isEmpty()) return

        if (cleanIdOrUrl == _activeVideoId.value && _extractionResult.value is YouTubeExtractorHelper.ExtractionResult.Success) {
            _currentScreen.value = AppScreen.PLAYER
            _isPlaying.value = true
            com.example.ui.player.GlobalPlayerManager.play()
            return
        }

        // Resolve target provider
        var targetProviderId = providerIdHint
        if (targetProviderId.isNullOrEmpty() || targetProviderId == "all") {
            val matchingItem = (_searchResults.value + _trendingVideos.value + _watchHistory.value).firstOrNull { it.id == cleanIdOrUrl }
            targetProviderId = matchingItem?.providerId
        }
        if (targetProviderId.isNullOrEmpty() || targetProviderId == "all") {
            targetProviderId = when {
                cleanIdOrUrl.startsWith("tt") || cleanIdOrUrl.startsWith("movie_") || cleanIdOrUrl.startsWith("tv_") -> "unified_torrents"
                cleanIdOrUrl.contains("youtube.com", ignoreCase = true) || cleanIdOrUrl.contains("youtu.be", ignoreCase = true) -> "youtube"
                cleanIdOrUrl.contains("dailymotion.com", ignoreCase = true) || cleanIdOrUrl.contains("dai.ly", ignoreCase = true) -> "dailymotion"
                cleanIdOrUrl.contains("eporner.com", ignoreCase = true) -> "eporner"
                cleanIdOrUrl.contains("archive.org", ignoreCase = true) -> "internet_archive"
                cleanIdOrUrl.contains("vimeo.com", ignoreCase = true) -> "vimeo"
                cleanIdOrUrl.contains("bitchute.com", ignoreCase = true) -> "bitchute"
                cleanIdOrUrl.contains("rumble.com", ignoreCase = true) -> "rumble"
                cleanIdOrUrl.contains("tiktok.com", ignoreCase = true) -> "tiktok"
                cleanIdOrUrl.contains("reddit.com", ignoreCase = true) -> "reddit"
                cleanIdOrUrl.contains("twitch.tv", ignoreCase = true) -> "twitch"
                cleanIdOrUrl.contains("soundcloud.com", ignoreCase = true) -> "soundcloud"
                cleanIdOrUrl.contains("bandcamp.com", ignoreCase = true) -> "bandcamp"
                _activeProviderId.value != "all" && _activeProviderId.value.isNotBlank() -> _activeProviderId.value
                else -> "all"
            }
        }
        if (targetProviderId in subTorrentProviderIds || targetProviderId.contains("torrent") || targetProviderId == "tmdb" || targetProviderId == "tmdb_movies") {
            targetProviderId = "unified_torrents"
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
        _isExtracting.value = true
        _extractionResult.value = null
        _selectedStreamOption.value = null
        _selectedCaptionOption.value = null
        _isPlaying.value = true
        com.example.ui.player.GlobalPlayerManager.resetFirstFrameState()

        // Immediately navigate to dedicated player screen
        _currentScreen.value = AppScreen.PLAYER

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allProviders = pluginManager.getAllAvailableProviders()
                val enabledProvidersList = if (_enabledProviderIds.value.contains("all") || _enabledProviderIds.value.isEmpty()) {
                    allProviders
                } else {
                    allProviders.filter { _enabledProviderIds.value.contains(it.providerId) }
                }

                val pipelineResult = sourcePipelineEngine.discoverAndRankStreams(
                    idOrUrl = cleanIdOrUrl,
                    providers = enabledProvidersList,
                    targetProviderId = targetProviderId
                )

                if (pipelineResult.failedLogs.isNotEmpty()) {
                    pipelineResult.failedLogs.forEach { recordFailedSource(it) }
                }

                if (pipelineResult.playableStreams.isNotEmpty()) {
                    val activeProvider = pluginManager.getProvider(targetProviderId ?: "")
                    val providerInfo = try { activeProvider?.getStreams(cleanIdOrUrl) } catch (e: Exception) { null }
                    val providerRecs = (try { activeProvider?.getRecommendations(cleanIdOrUrl) } catch (e: Exception) { null }) ?: emptyList()

                    val resolvedTitle = currentMatch?.title?.takeIf { it.isNotBlank() && it != "Torrentio Stream" && it != "Torrent Stream" }
                        ?: providerInfo?.title?.takeIf { it.isNotBlank() && it != "Torrentio Stream" && it != "Torrent Stream" }
                        ?: cleanIdOrUrl
                    val resolvedChannelName = currentMatch?.uploaderName?.takeIf { it.isNotBlank() && !it.contains("Torrent", ignoreCase = true) && it != "Butterfly Stream" }
                        ?: providerInfo?.channelName?.takeIf { it.isNotBlank() && !it.contains("Torrent", ignoreCase = true) && it != "Butterfly Stream" }
                        ?: com.example.util.StudioDetector.detectStudio(resolvedTitle, cleanIdOrUrl.startsWith("tv_"))
                    val resolvedAvatarUrl = providerInfo?.channelAvatarUrl
                    val resolvedDesc = providerInfo?.description
                    val resolvedThumb = providerInfo?.thumbnailUrl ?: currentMatch?.thumbnailUrl

                    val mappedRelated = if (providerRecs.isNotEmpty()) {
                        providerRecs.map { r ->
                            VideoItem(
                                id = r.id,
                                title = r.title,
                                uploaderName = r.uploaderName,
                                thumbnailUrl = r.thumbnailUrl,
                                durationSeconds = r.durationSeconds,
                                viewCount = r.viewCount,
                                providerId = targetProviderId
                            )
                        }
                    } else {
                        _trendingVideos.value.filter { it.id != cleanIdOrUrl }.take(15)
                    }

                    val mappedCaptions = providerInfo?.subtitles?.map { sub ->
                        CaptionOption(
                            languageName = sub.languageName ?: sub.languageCode ?: "English",
                            languageCode = sub.languageCode ?: "en",
                            format = sub.format ?: "VTT",
                            url = sub.url
                        )
                    } ?: emptyList()

                    val streamData = StreamData(
                        videoId = cleanIdOrUrl,
                        title = resolvedTitle,
                        channelName = resolvedChannelName,
                        channelAvatarUrl = resolvedAvatarUrl,
                        description = resolvedDesc,
                        thumbnailUrl = resolvedThumb,
                        availableStreamOptions = pipelineResult.playableStreams,
                        selectedStreamOption = pipelineResult.playableStreams.first(),
                        captionOptions = mappedCaptions,
                        relatedVideos = mappedRelated,
                        providerId = targetProviderId
                    )
                    _extractionResult.value = YouTubeExtractorHelper.ExtractionResult.Success(streamData)
                    _selectedStreamOption.value = streamData.selectedStreamOption
                    _selectedCaptionOption.value = mappedCaptions.firstOrNull()
                    startServerAutoScanner(streamData.availableStreamOptions)
                } else {
                    if (targetProviderId == "youtube" || cleanIdOrUrl.contains("youtube.com", ignoreCase = true) || cleanIdOrUrl.contains("youtu.be", ignoreCase = true) || cleanIdOrUrl.length == 11) {
                        val result = com.example.extractor.YouTubeFastStreamResolver.resolveStream(cleanIdOrUrl, getApplication())
                        _extractionResult.value = result
                        if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                            _selectedStreamOption.value = result.streamData.selectedStreamOption
                            _selectedCaptionOption.value = result.streamData.captionOptions.firstOrNull()
                            startServerAutoScanner(result.streamData.availableStreamOptions)
                        }
                    } else {
                        val isWebUrl = cleanIdOrUrl.startsWith("http://") || cleanIdOrUrl.startsWith("https://")
                        val fallbackUrl = cleanIdOrUrl
                        val fallbackOption = PlayableStreamOption(
                            qualityLabel = "Auto Quality",
                            format = "mp4",
                            isMuxed = true,
                            videoUrl = fallbackUrl,
                            providerType = com.example.plugin.sdk.model.ProviderType.OTHER
                        )
                        val fallbackData = StreamData(
                            videoId = cleanIdOrUrl,
                            videoUrl = fallbackUrl,
                            title = initialVideoItem.title ?: "Streaming Video",
                            channelName = initialVideoItem.uploaderName ?: "Video Creator",
                            thumbnailUrl = initialVideoItem.thumbnailUrl,
                            availableStreamOptions = listOf(fallbackOption),
                            selectedStreamOption = fallbackOption,
                            providerId = targetProviderId ?: "web",
                            description = "Direct stream video playback."
                        )
                        _extractionResult.value = YouTubeExtractorHelper.ExtractionResult.Success(fallbackData)
                        _selectedStreamOption.value = fallbackOption
                    }
                }
            } catch (e: Exception) {
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
                _isExtracting.value = false
            }
        }
    }

    init {
        // Wire GlobalPlayerManager error listener for automatic fallback
        com.example.ui.player.GlobalPlayerManager.setPlaybackFailedListener {
            tryNextFallbackStream()
        }
    }

    private fun startServerAutoScanner(options: List<PlayableStreamOption>) {
        if (options.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var selected: PlayableStreamOption? = null
            for (option in options) {
                val url = option.videoUrl ?: option.audioUrl ?: ""
                if (url.isBlank()) continue
                selected = option
                break
            }
            _selectedStreamOption.value = selected ?: options.firstOrNull()
        }
    }

    fun tryNextFallbackStream() {
        val current = _selectedStreamOption.value ?: return
        val ext = _extractionResult.value
        if (ext is YouTubeExtractorHelper.ExtractionResult.Success) {
            val options = ext.streamData.availableStreamOptions
            val currentIndex = options.indexOfFirst { it.videoUrl == current.videoUrl || it.qualityLabel == current.qualityLabel }
            if (currentIndex >= 0 && currentIndex + 1 < options.size) {
                val nextOption = options[currentIndex + 1]
                Log.d("MainViewModel", "[Fallback] Playback failed for '${current.qualityLabel}'. Auto falling back to option ${currentIndex + 1}: '${nextOption.qualityLabel}'")
                _selectedStreamOption.value = nextOption
            }
        }
    }

    fun selectStreamOption(option: PlayableStreamOption) {
        _selectedStreamOption.value = option
    }

    fun selectCaptionOption(caption: CaptionOption?) {
        _selectedCaptionOption.value = caption
    }

    private fun PluginStreamInfo.toStreamData(
        providerId: String,
        recommendations: List<VideoItem>
    ): StreamData {
        val provider = pluginManager.getProvider(providerId)
        val isTorrentProvider = provider?.capabilities?.supportsTorrent == true ||
                provider?.capabilities?.providerType == com.example.plugin.sdk.model.ProviderType.TORRENT ||
                providerId == "unified_torrents" ||
                providerId.contains("torrent")

        val pType = if (isTorrentProvider) com.example.plugin.sdk.model.ProviderType.TORRENT else (provider?.capabilities?.providerType ?: com.example.plugin.sdk.model.ProviderType.OTHER)

        val captions = subtitles.map { sub ->
            CaptionOption(
                languageName = sub.languageName,
                languageCode = sub.languageCode,
                format = sub.format,
                url = sub.url
            )
        }

        val streamOptions = mutableListOf<PlayableStreamOption>()
        val seenInfoHashes = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()

        videoStreams.forEach { vs ->
            val rawUrl = vs.url ?: return@forEach
            val infoHash = sourcePipelineEngine.extractInfoHash(rawUrl)

            if (infoHash != null) {
                if (seenInfoHashes.contains(infoHash)) {
                    val log = com.example.model.FailedSourceLog(
                        providerId = providerId,
                        sourceTitle = vs.qualityLabel,
                        rawUrl = rawUrl,
                        errorType = "DUPLICATE_TORRENT",
                        httpStatus = null,
                        urlType = "MAGNET",
                        stage = com.example.model.SourceLifecycleStage.PARSED,
                        failureReason = "Duplicate infoHash: $infoHash"
                    )
                    recordFailedSource(log)
                    return@forEach
                }
                seenInfoHashes.add(infoHash)
            } else {
                val normUrl = sourcePipelineEngine.normalizeUrl(rawUrl)
                if (seenUrls.contains(normUrl)) {
                    val log = com.example.model.FailedSourceLog(
                        providerId = providerId,
                        sourceTitle = vs.qualityLabel,
                        rawUrl = rawUrl,
                        errorType = "DUPLICATE_EMBED",
                        httpStatus = null,
                        urlType = "EMBED_DIRECT",
                        stage = com.example.model.SourceLifecycleStage.PARSED,
                        failureReason = "Duplicate normalized URL: $normUrl"
                    )
                    recordFailedSource(log)
                    return@forEach
                }
                seenUrls.add(normUrl)
            }

            val isVsTorrent = isTorrentProvider || vs.format.equals("torrent", ignoreCase = true) || vs.format.equals("p2p", ignoreCase = true) || vs.url.startsWith("magnet:")
            val vsType = if (isVsTorrent) com.example.plugin.sdk.model.ProviderType.TORRENT else pType

            streamOptions.add(
                PlayableStreamOption(
                    qualityLabel = vs.qualityLabel,
                    format = vs.format,
                    isMuxed = true,
                    videoStream = null,
                    audioStream = null,
                    videoUrl = vs.url,
                    audioUrl = null,
                    providerType = vsType
                )
            )
        }

        return StreamData(
            videoId = id,
            videoUrl = url,
            title = title,
            channelName = channelName,
            channelAvatarUrl = channelAvatarUrl,
            subscriberCountText = null,
            viewCount = viewCount,
            likeCount = likeCount,
            uploadDate = uploadDate,
            description = description,
            progressiveStreams = emptyList(),
            videoOnlyStreams = emptyList(),
            audioStreams = emptyList(),
            captionOptions = captions,
            availableStreamOptions = streamOptions,
            selectedStreamOption = streamOptions.firstOrNull(),
            hlsUrl = hlsUrl,
            relatedVideos = recommendations,
            embedUrl = url,
            providerId = providerId,
            thumbnailUrl = thumbnailUrl,
            providerType = pType
        )
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
