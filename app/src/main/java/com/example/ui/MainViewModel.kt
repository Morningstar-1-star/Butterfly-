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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ThemeMode {
    AMOLED_DARK,
    LIGHT
}

enum class AppAccentColor(val label: String, val color: Color) {
    YELLOW("Electric Yellow", Color(0xFFFFD600)),
    CYAN("Cyan Blue", Color(0xFF00E5FF)),
    PINK("Neon Pink", Color(0xFFFF4081)),
    PURPLE("Royal Purple", Color(0xFFAB47BC)),
    GREEN("Emerald Green", Color(0xFF00E676))
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val pluginManager = PluginManager(application)
    val repositoryManager = RepositoryManager(application, pluginManager)
    val extensionManager = ExtensionManager(application, pluginManager, repositoryManager)

    // Theme & Appearance Settings
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

    private val _activeProviderId = MutableStateFlow("all")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _enabledProviderIds = MutableStateFlow<Set<String>>(
        setOf(
            "all", "unified_torrents", "youtube", "tmdb_movies", "yts_torrents", "jikan_anime", "eztv_torrents", "torrentio_aggregator", "torrent_api_multi", "nyaa_si",
            "dailymotion", "apijav_server", "apijav_hentai", "apijav_porn", "eporner",
            "peertube", "vimeo", "archive_org", "ted", "nasa", "direct_mp4", "direct_hls", "rss_video", "json"
        )
    )
    val enabledProviderIds: StateFlow<Set<String>> = _enabledProviderIds.asStateFlow()

    private val _availableProviders = MutableStateFlow<List<ProviderUiItem>>(emptyList())
    val availableProviders: StateFlow<List<ProviderUiItem>> = _availableProviders.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<VideoItem>>(emptyList())
    val searchResults: StateFlow<List<VideoItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _trendingVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val trendingVideos: StateFlow<List<VideoItem>> = _trendingVideos.asStateFlow()

    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

    private val _feedError = MutableStateFlow<FeedErrorDetails?>(null)
    val feedError: StateFlow<FeedErrorDetails?> = _feedError.asStateFlow()

    private val _activeVideoId = MutableStateFlow<String?>(null)
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()

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

    private val _showShortsFeed = MutableStateFlow(false)
    val showShortsFeed: StateFlow<Boolean> = _showShortsFeed.asStateFlow()

    fun setShowShortsFeed(show: Boolean) {
        _showShortsFeed.value = show
    }

    private val _playbackQueue = MutableStateFlow<List<VideoItem>>(emptyList())
    val playbackQueue: StateFlow<List<VideoItem>> = _playbackQueue.asStateFlow()

    private val _watchLaterList = MutableStateFlow<List<VideoItem>>(emptyList())
    val watchLaterList: StateFlow<List<VideoItem>> = _watchLaterList.asStateFlow()

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
        }
    }

    fun removeFromWatchLater(video: VideoItem) {
        _watchLaterList.value = _watchLaterList.value.filter { it.id != video.id }
    }

    fun createPlaylist(title: String) {
        val newPl = UserPlaylist(id = System.currentTimeMillis().toString(), title = title, videos = emptyList())
        _userPlaylists.value = _userPlaylists.value + newPl
    }

    fun addToPlaylist(playlistId: String, video: VideoItem) {
        _userPlaylists.value = _userPlaylists.value.map { pl ->
            if (pl.id == playlistId) {
                if (pl.videos.none { it.id == video.id }) {
                    pl.copy(videos = pl.videos + video)
                } else pl
            } else pl
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

    private val _selectedCaptionOption = MutableStateFlow<CaptionOption?>(null)
    val selectedCaptionOption: StateFlow<CaptionOption?> = _selectedCaptionOption.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun togglePlayback() {
        _isPlaying.value = !_isPlaying.value
    }

    fun closeVideo() {
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
            repositoryManager.loadRepositories()
            extensionManager.refreshExtensions()
            refreshProvidersList()
            setActiveProvider("all")
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
        _searchResults.value = emptyList()
        _searchQuery.value = ""
        refreshProvidersList()
        loadTrending(forceRefresh = true)
    }

    fun toggleProviderEnabled(providerId: String) {
        val current = _enabledProviderIds.value.toMutableSet()
        if (current.contains(providerId)) {
            if (providerId == _activeProviderId.value) return
            current.remove(providerId)
        } else {
            current.add(providerId)
        }
        _enabledProviderIds.value = current
        refreshProvidersList()
    }

    private fun refreshProvidersList() {
        val allNative = pluginManager.getAllAvailableProviders()
        val activeId = _activeProviderId.value
        val enabledSet = _enabledProviderIds.value

        val uiList = mutableListOf<ProviderUiItem>()

        // Prepend All-In-One Feed (Mix)
        uiList.add(
            ProviderUiItem(
                id = "all",
                name = "All Sources (Mixed)",
                description = "Aggregated feed from YouTube, Dailymotion, APIJAV & more",
                isEnabled = enabledSet.contains("all"),
                isDefault = (activeId == "all")
            )
        )

        allNative.forEach { provider ->
            val id = provider.providerId
            val name = getReadableProviderName(id)
            uiList.add(
                ProviderUiItem(
                    id = id,
                    name = name,
                    description = "Streaming provider ($id)",
                    isEnabled = enabledSet.contains(id),
                    isDefault = (id == activeId)
                )
            )
        }
        _availableProviders.value = uiList
    }

    private fun getReadableProviderName(id: String): String {
        return when (id) {
            "all" -> "All Sources (Mixed)"
            "unified_torrents" -> "Unified Torrents (Auto Scanner)"
            "youtube" -> "YouTube"
            "tmdb_movies" -> "TMDB Movies & TV"
            "yts_torrents" -> "YTS Torrents"
            "eztv_torrents" -> "EZTV Torrents"
            "torrentio_aggregator" -> "Torrentio Multi"
            "torrent_api_multi" -> "Torrent API Multi"
            "nyaa_si" -> "Nyaa.si Anime"
            "dailymotion" -> "Dailymotion"
            "apijav_server" -> "APIJAV Server"
            "apijav_hentai" -> "APIJAV Hentai"
            "apijav_porn" -> "APIJAV Porn"
            "eporner" -> "Eporner"
            "peertube" -> "PeerTube"
            "vimeo" -> "Vimeo"
            "archive_org" -> "Archive.org"
            "ted" -> "TED Talks"
            "nasa" -> "NASA TV"
            "direct_mp4" -> "Direct MP4 Stream"
            "direct_hls" -> "Direct HLS Stream"
            "rss_video" -> "RSS Video Feed"
            "json" -> "Custom JSON Feed"
            else -> id.replaceFirstChar { it.uppercase() }
        }
    }

    private val _recentSearches = MutableStateFlow<List<String>>(
        listOf(
            "Lost Girls & Love Hotels",
            "Ballerina",
            "The White Lotus",
            "John Carpenter's Suburban Scream",
            "Spiderman",
            "Detention",
            "Summerslam 2026",
            "Missing 2009"
        )
    )
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val filtered = _recentSearches.value.filterNot { it.equals(q, ignoreCase = true) }
        _recentSearches.value = listOf(q) + filtered.take(9)
    }

    fun removeRecentSearch(query: String) {
        _recentSearches.value = _recentSearches.value.filterNot { it.equals(query, ignoreCase = true) }
    }

    fun clearAllRecentSearches() {
        _recentSearches.value = emptyList()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(query: String? = null) {
        val q = query ?: _searchQuery.value
        if (q.isBlank()) return
        addRecentSearch(q)

        Log.d("MainViewModel", "Search query: '$q' on active provider: ${_activeProviderId.value}")

        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            _feedError.value = null
            try {
                val activeId = _activeProviderId.value
                val combined = mutableListOf<VideoItem>()

                if (activeId == "all") {
                    val activeProviders = pluginManager.getAllAvailableProviders().filter {
                        _enabledProviderIds.value.contains(it.providerId)
                    }

                    val deferreds = activeProviders.map { provider ->
                        async {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(8000L) {
                                    val paged = provider.search(q)
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
                                            providerId = provider.providerId
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
                        val items = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                            val paged = provider.search(q)
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
                                    providerId = provider.providerId
                                )
                            }
                        } ?: emptyList()
                        combined.addAll(items)
                    }
                }
                _searchResults.value = combined
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
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingTrending.value = true
            _feedError.value = null
            _searchResults.value = emptyList()
            try {
                val activeId = _activeProviderId.value
                val combined = mutableListOf<VideoItem>()
                val pageToken = if (forceRefresh) (1..5).random().toString() else null

                if (activeId == "all") {
                    val activeProviders = pluginManager.getAllAvailableProviders().filter {
                        _enabledProviderIds.value.contains(it.providerId)
                    }

                    val deferreds = activeProviders.map { provider ->
                        async {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(8000L) {
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
                                            providerId = provider.providerId
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
                        val items = kotlinx.coroutines.withTimeoutOrNull(8000L) {
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
                                    providerId = provider.providerId
                                )
                            }
                        } ?: emptyList()
                        combined.addAll(if (forceRefresh) items.shuffled() else items)
                    }
                }

                _trendingVideos.value = combined
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

    fun playVideo(videoIdOrUrl: String, providerIdHint: String? = null) {
        val cleanIdOrUrl = videoIdOrUrl.trim()
        if (cleanIdOrUrl.isEmpty()) return

        // Resolve target provider
        var targetProviderId = providerIdHint
        if (targetProviderId.isNullOrEmpty()) {
            val matchingItem = (_searchResults.value + _trendingVideos.value).firstOrNull { it.id == cleanIdOrUrl }
            targetProviderId = matchingItem?.providerId
        }
        if (targetProviderId.isNullOrEmpty() || targetProviderId == "all") {
            targetProviderId = if (_activeProviderId.value != "all") _activeProviderId.value else "dailymotion"
        }

        Log.d("MainViewModel", "playVideo for: '$cleanIdOrUrl' on provider: $targetProviderId")

        _activeVideoId.value = cleanIdOrUrl
        _isExtracting.value = true
        _extractionResult.value = null
        _selectedStreamOption.value = null
        _selectedCaptionOption.value = null
        _isPlaying.value = true

        // Immediately navigate to dedicated player screen
        _currentScreen.value = AppScreen.PLAYER

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = pluginManager.getProvider(targetProviderId)
                if (provider != null) {
                    val streamInfo = provider.getStreams(cleanIdOrUrl)
                    val primaryRecs = try { provider.getRecommendations(cleanIdOrUrl) } catch (e: Exception) { emptyList() }
                    val primaryVideoItems = primaryRecs.map { item ->
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
                            providerId = targetProviderId
                        )
                    }

                    // MULTI-SOURCE RECOMMENDATIONS: Interleave recommendations with different active providers & sources
                    val otherSourceItems = (_trendingVideos.value + _searchResults.value)
                        .filter { it.providerId != targetProviderId && it.id != cleanIdOrUrl }
                        .distinctBy { (it.providerId ?: "") + "_" + it.id }

                    val multiSourceRecs = mutableListOf<VideoItem>()
                    val maxItems = maxOf(primaryVideoItems.size, otherSourceItems.size)
                    for (i in 0 until maxItems) {
                        if (i < primaryVideoItems.size) multiSourceRecs.add(primaryVideoItems[i])
                        if (i < otherSourceItems.size) multiSourceRecs.add(otherSourceItems[i])
                    }

                    val recVideoItems = if (multiSourceRecs.isNotEmpty()) {
                        multiSourceRecs.distinctBy { (it.providerId ?: "") + "_" + it.id }.take(20)
                    } else {
                        _trendingVideos.value.filter { it.id != cleanIdOrUrl }.take(15)
                    }

                    val streamData = streamInfo.toStreamData(targetProviderId, recVideoItems)
                    _extractionResult.value = YouTubeExtractorHelper.ExtractionResult.Success(streamData)
                    _selectedStreamOption.value = streamData.selectedStreamOption
                    _selectedCaptionOption.value = streamData.captionOptions.firstOrNull()
                    startServerAutoScanner(streamData.availableStreamOptions)
                } else {
                    val result = YouTubeExtractorHelper.fetchStreamData(cleanIdOrUrl)
                    _extractionResult.value = result
                    if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                        _selectedStreamOption.value = result.streamData.selectedStreamOption
                        _selectedCaptionOption.value = result.streamData.captionOptions.firstOrNull()
                        startServerAutoScanner(result.streamData.availableStreamOptions)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "playVideo failed: ${e.localizedMessage}", e)
                _extractionResult.value = YouTubeExtractorHelper.ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.NETWORK_ERROR,
                        message = e.localizedMessage ?: "Stream extraction failed for $cleanIdOrUrl",
                        rawExceptionName = e.javaClass.simpleName,
                        fullStackTrace = e.stackTraceToString(),
                        urlOrId = cleanIdOrUrl
                    )
                )
            } finally {
                _isExtracting.value = false
            }
        }
    }

    private fun startServerAutoScanner(options: List<PlayableStreamOption>) {
        if (options.isEmpty()) return
        val firstWorking = options.firstOrNull { 
            val url = it.videoUrl ?: it.audioUrl ?: ""
            url.isNotBlank() && !url.startsWith("magnet:")
        } ?: options.firstOrNull {
            val url = it.videoUrl ?: it.audioUrl ?: ""
            url.isNotBlank()
        } ?: options.first()
        _selectedStreamOption.value = firstWorking
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
        val captions = subtitles.map { sub ->
            CaptionOption(
                languageName = sub.languageName,
                languageCode = sub.languageCode,
                format = sub.format,
                url = sub.url
            )
        }

        val streamOptions = mutableListOf<PlayableStreamOption>()
        videoStreams.forEach { vs ->
            streamOptions.add(
                PlayableStreamOption(
                    qualityLabel = vs.qualityLabel,
                    format = vs.format,
                    isMuxed = true,
                    videoStream = null,
                    audioStream = null,
                    videoUrl = vs.url,
                    audioUrl = null
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
            thumbnailUrl = thumbnailUrl
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
