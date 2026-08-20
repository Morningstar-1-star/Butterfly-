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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
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
    private val _adultContentEnabled = MutableStateFlow(
        settingsPrefs.getBoolean("adult_content_enabled", false)
    )
    val adultContentEnabled: StateFlow<Boolean> = _adultContentEnabled.asStateFlow()

    fun setAdultContentEnabled(enabled: Boolean) {
        _adultContentEnabled.value = enabled
        settingsPrefs.edit().putBoolean("adult_content_enabled", enabled).apply()
        val currentSet = _enabledProviderIds.value.toMutableSet()
        val adultIds = listOf("pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn")
        if (enabled) {
            currentSet.addAll(adultIds)
        } else {
            currentSet.removeAll(adultIds)
        }
        _enabledProviderIds.value = currentSet
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

    private val _isChannelLoading = MutableStateFlow(false)
    val isChannelLoading: StateFlow<Boolean> = _isChannelLoading.asStateFlow()

    private val _isPipMode = MutableStateFlow(false)
    val isPipMode: StateFlow<Boolean> = _isPipMode.asStateFlow()

    fun setPipMode(enabled: Boolean) {
        _isPipMode.value = enabled
    }

    private val adultProviderIds = setOf("pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn", "apijav")

    fun isAdultProviderId(providerId: String?): Boolean {
        if (providerId.isNullOrBlank()) return false
        val lower = providerId.lowercase()
        return adultProviderIds.any { lower.contains(it) }
    }

    fun isAdultVideoItem(item: VideoItem): Boolean {
        if (isAdultProviderId(item.providerId)) return true
        val text = "${item.title} ${item.uploaderName} ${item.description}".lowercase()
        val adultKeywords = listOf("pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn", "apijav", "adult", "nsfw")
        return adultKeywords.any { text.contains(it) }
    }

    fun isAdultSearchQuery(query: String): Boolean {
        val q = query.lowercase()
        val adultKeywords = listOf("pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn", "apijav", "adult", "nsfw")
        return adultKeywords.any { q.contains(it) }
    }
    fun isAdultDownload(entity: OfflineDownloadEntity): Boolean = false
    fun isDemoOrPlaceholderVideo(item: VideoItem): Boolean = false

    private val _activeProviderId = MutableStateFlow("all")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _enabledProviderIds = MutableStateFlow<Set<String>>({
        val set = mutableSetOf("all", "youtube", "archive_org", "dailymotion", "bilibili", "vimeo", "eporner")
        if (settingsPrefs.getBoolean("adult_content_enabled", false)) {
            set.addAll(listOf("pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn"))
        }
        set
    }())
    val enabledProviderIds: StateFlow<Set<String>> = _enabledProviderIds.asStateFlow()

    private val _availableProviders = MutableStateFlow<List<ProviderUiItem>>(emptyList())
    val availableProviders: StateFlow<List<ProviderUiItem>> = _availableProviders.asStateFlow()

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

    fun isBlockedVideo(item: VideoItem): Boolean {
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
        }
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

        val ranked = allAvailable.filterNot { isBlockedVideo(it) }
        return ranked.distinctBy { it.id }.take(20)
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

    val downloadLiveProgress: StateFlow<Map<String, Any>> = MutableStateFlow(emptyMap())

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
    }

    fun pauseDownload(videoId: String) {
    }

    fun resumeDownload(videoId: String) {
    }

    fun deleteDownload(videoId: String, localFilePath: String? = null) {
    }

    fun clearAllDownloads() {
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
                refreshProvidersList()
                setActiveProvider("all")
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

    fun addRepositorySource(url: String, name: String = "Custom Repository") {}
    fun removeRepositorySource(repoId: String) {}
    fun installExtension(sourceStr: String, onResult: (Any) -> Unit = {}) {}
    fun uninstallExtension(pluginId: String) {}
    fun updateExtension(pluginId: String) {}

    fun navigateToScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun getActiveProvider(): Any? = null

    fun reloadProviders() {
        viewModelScope.launch(Dispatchers.IO) {
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
        val newState = !current.contains(providerId)
        if (newState) {
            current.add(providerId)
        } else {
            if (providerId == _activeProviderId.value) return
            current.remove(providerId)
        }
        _enabledProviderIds.value = current
        refreshProvidersList()
    }

    private fun refreshProvidersList() {
        val activeId = _activeProviderId.value
        val enabledSet = _enabledProviderIds.value
        val adultEnabled = _adultContentEnabled.value
        val uiList = mutableListOf<ProviderUiItem>()

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
                description = "Dailymotion video platform via yt-dlp",
                category = "Video",
                isEnabled = enabledSet.contains("dailymotion"),
                isDefault = (activeId == "dailymotion")
            )
        )
        uiList.add(
            ProviderUiItem(
                id = "bilibili",
                name = "Bilibili",
                description = "Bilibili streaming catalog via yt-dlp",
                category = "Video",
                isEnabled = enabledSet.contains("bilibili"),
                isDefault = (activeId == "bilibili")
            )
        )
        uiList.add(
            ProviderUiItem(
                id = "vimeo",
                name = "Vimeo",
                description = "Vimeo video catalog via yt-dlp",
                category = "Video",
                isEnabled = enabledSet.contains("vimeo"),
                isDefault = (activeId == "vimeo")
            )
        )
        uiList.add(
            ProviderUiItem(
                id = "eporner",
                name = "Eporner",
                description = "Eporner video catalog via yt-dlp",
                category = "Video",
                isEnabled = enabledSet.contains("eporner"),
                isDefault = (activeId == "eporner")
            )
        )

        val adultProviders = listOf(
            Triple("pornhub", "Pornhub", "Pornhub video catalog via yt-dlp"),
            Triple("xvideos", "XVideos", "XVideos video catalog via yt-dlp"),
            Triple("4tube", "4tube", "4tube video catalog via yt-dlp"),
            Triple("beeg", "Beeg", "Beeg video catalog via yt-dlp"),
            Triple("rule34video", "Rule34Video", "Rule34Video animation catalog via yt-dlp"),
            Triple("redtube", "RedTube", "RedTube video catalog via yt-dlp"),
            Triple("xhamster", "XHamster", "XHamster video catalog via yt-dlp"),
            Triple("youporn", "YouPorn", "YouPorn video catalog via yt-dlp")
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

        _availableProviders.value = uiList
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

    init {
        refreshProvidersList()
        loadTrending()
        viewModelScope.launch {
            videoCacheRepo.searchHistoryFlow.collect { roomSearches ->
                if (roomSearches.isNotEmpty()) {
                    _recentSearches.value = roomSearches
                }
            }
        }
        com.example.ui.player.GlobalPlayerManager.setPlaybackFailedListener {
            tryNextFallbackStream()
        }
    }

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val filtered = _recentSearches.value.filterNot { it.equals(q, ignoreCase = true) }
        val updated = (listOf(q) + filtered).take(20)
        _recentSearches.value = updated
        saveRecentSearches(updated)
        viewModelScope.launch {
            videoCacheRepo.addSearchQuery(q)
        }
    }

    fun removeRecentSearch(query: String) {
        val updated = _recentSearches.value.filterNot { it.equals(query, ignoreCase = true) }
        _recentSearches.value = updated
        saveRecentSearches(updated)
        viewModelScope.launch {
            videoCacheRepo.removeSearchQuery(query)
        }
    }

    fun clearAllRecentSearches() {
        _recentSearches.value = emptyList()
        saveRecentSearches(emptyList())
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

    fun openChannel(channelName: String, avatarUrl: String? = null) {
        val trimmed = channelName.trim()
        if (trimmed.isBlank()) return
        _selectedChannelName.value = trimmed
        _selectedChannelAvatarUrl.value = avatarUrl ?: com.example.util.ChannelLogoHelper.getBrandInfo(trimmed, avatarUrl).logoUrls.firstOrNull()
        _currentScreen.value = AppScreen.HOME
        updateSearchQuery(trimmed)
        performSearch(trimmed)
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
            kotlinx.coroutines.delay(200L)
            if (!isActive) return@launch

            val historyMatches = _recentSearches.value
                .filter { it.contains(q, ignoreCase = true) }
                .take(3)
                .map { SearchSuggestionItem(query = it, isHistory = true) }

            val fetchedSuggestions = com.example.extractor.YouTubeExtractorHelper.fetchSearchSuggestions(q)
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

            historyMatches.forEach { item ->
                val lower = item.query.lowercase()
                if (seenQueries.add(lower)) {
                    combined.add(item)
                }
            }

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

    fun performSearch(query: String? = null) {
        val q = (query ?: _searchQuery.value).trim()
        if (q.isBlank()) return
        addRecentSearch(q)
        currentSearchPage = 1

        _isSearching.value = true
        _searchResults.value = emptyList()

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
                    _searchResults.value = filtered
                }

                supervisorScope {
                    // 1. YouTube
                    if ((activeProv == "all" || activeProv == "youtube") && enabledSet.contains("youtube")) {
                        launch(Dispatchers.IO) {
                            try {
                                val ytResults = kotlinx.coroutines.withTimeoutOrNull(4500L) {
                                    com.example.extractor.YouTubeExtractorHelper.searchYouTube(q, getApplication())
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

                    // 2. Archive.org
                    if ((activeProv == "all" || activeProv == "archive_org") && enabledSet.contains("archive_org")) {
                        launch(Dispatchers.IO) {
                            try {
                                val archResults = kotlinx.coroutines.withTimeoutOrNull(4500L) {
                                    com.example.extractor.ArchiveOrgProvider.search(q, 1)
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

                    // 3. Eporner
                    if ((activeProv == "all" || activeProv == "eporner") && enabledSet.contains("eporner") && (adultEnabled || activeProv == "eporner")) {
                        launch(Dispatchers.IO) {
                            try {
                                val epResults = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    com.example.extractor.EpornerProvider.search(q, 25)
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

                    // 4. MultiSource providers
                    val ytDlpSources = listOf("dailymotion", "bilibili", "vimeo", "pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn")

                    val searchSources = when {
                        activeProv == "all" -> ytDlpSources.filter { enabledSet.contains(it) && (adultEnabled || !isAdultProviderId(it)) }
                        else -> if (ytDlpSources.contains(activeProv)) listOf(activeProv) else emptyList()
                    }

                    searchSources.forEach { prov ->
                        launch(Dispatchers.IO) {
                            try {
                                val provResults = kotlinx.coroutines.withTimeoutOrNull(4500L) {
                                    com.example.extractor.MultiSourceProvider.search(getApplication(), prov, q, 15)
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
        _isLoadingTrending.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _feedError.value = null
            if (forceRefresh) {
                _searchResults.value = emptyList()
            }
            try {
                val adultEnabled = _adultContentEnabled.value
                val activeProv = _activeProviderId.value
                val enabledSet = _enabledProviderIds.value

                val combinedItems = mutableListOf<VideoItem>()

                supervisorScope {
                    val deferredList = mutableListOf<kotlinx.coroutines.Deferred<List<VideoItem>>>()

                    // 1. YouTube
                    if ((activeProv == "all" || activeProv == "youtube") && enabledSet.contains("youtube")) {
                        deferredList.add(async(Dispatchers.IO) {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(15000L) {
                                    com.example.extractor.YouTubeExtractorHelper.fetchYouTubeTrending(getApplication())
                                } ?: emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        })
                    }

                    // 2. Archive.org
                    if ((activeProv == "all" || activeProv == "archive_org") && enabledSet.contains("archive_org")) {
                        deferredList.add(async(Dispatchers.IO) {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(15000L) {
                                    com.example.extractor.ArchiveOrgProvider.getHome(1)
                                } ?: emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        })
                    }

                    // 3. Eporner
                    if ((activeProv == "all" || activeProv == "eporner") && enabledSet.contains("eporner")) {
                        deferredList.add(async(Dispatchers.IO) {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(10000L) {
                                    com.example.extractor.EpornerProvider.getHome(25)
                                } ?: emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        })
                    }

                    // 4. MultiSource providers
                    val ytDlpSources = listOf("dailymotion", "bilibili", "vimeo", "pornhub", "xvideos", "4tube", "beeg", "rule34video", "redtube", "xhamster", "youporn")

                    val targetSources = when {
                        activeProv == "all" -> ytDlpSources.filter { enabledSet.contains(it) }
                        else -> listOf(activeProv)
                    }

                    targetSources.forEach { prov ->
                        deferredList.add(async(Dispatchers.IO) {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(12000L) {
                                    com.example.extractor.MultiSourceProvider.getHome(getApplication(), prov, 15)
                                } ?: emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        })
                    }

                    deferredList.awaitAll().filterNotNull().forEach { combinedItems.addAll(it) }
                }

                val combined = combinedItems
                    .distinctBy { it.id }
                    .filter {
                        if (activeProv != "all") {
                            it.providerId == activeProv
                        } else {
                            adultEnabled || !isAdultVideoItem(it)
                        }
                    }

                _trendingVideos.value = combined
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadTrending failed", e)
                _trendingVideos.value = emptyList()
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

                val newItems = mutableListOf<VideoItem>()

                if (isSearchMode && q.isNotBlank()) {
                    currentSearchPage++
                    if (activeProv == "all" || activeProv == "youtube") {
                        newItems.addAll(com.example.extractor.YouTubeExtractorHelper.searchYouTube(q, getApplication()))
                    }
                    if (activeProv == "all" || activeProv == "archive_org") {
                        newItems.addAll(com.example.extractor.ArchiveOrgProvider.search(q, currentSearchPage))
                    }
                    if (activeProv == "all" || activeProv == "eporner") {
                        newItems.addAll(com.example.extractor.EpornerProvider.search(q, 25))
                    }
                    if (activeProv != "youtube" && activeProv != "archive_org" && activeProv != "eporner") {
                        val prov = if (activeProv == "all") "vimeo" else activeProv
                        newItems.addAll(com.example.extractor.MultiSourceProvider.search(getApplication(), prov, q, 15))
                    }
                    val filtered = if (activeProv != "all") newItems.filter { it.providerId == activeProv } else newItems
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
                    if (activeProv == "all" || activeProv == "eporner") {
                        newItems.addAll(com.example.extractor.EpornerProvider.getHome(25))
                    }
                    if (activeProv != "youtube" && activeProv != "archive_org" && activeProv != "eporner") {
                        val prov = if (activeProv == "all") "vimeo" else activeProv
                        newItems.addAll(com.example.extractor.MultiSourceProvider.getHome(getApplication(), prov, 15))
                    }
                    val filtered = if (activeProv != "all") newItems.filter { it.providerId == activeProv } else newItems
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

                val current = _playerRecommendations.value
                val combined = (current + discovered).distinctBy { it.id }
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
        com.example.ui.player.GlobalPlayerManager.resetFirstFrameState()

        // Immediately navigate to dedicated player screen
        _currentScreen.value = AppScreen.PLAYER

        activePlaybackJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeExtractorHelper.resolveStream(cleanIdOrUrl, getApplication(), targetProviderId)
                if (!isActive || _activeVideoId.value != cleanIdOrUrl) return@launch
                _extractionResult.value = result
                if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                    val primary = result.streamData.selectedStreamOption
                        ?: result.streamData.availableStreamOptions.firstOrNull { it.isMuxed && !(it.videoUrl ?: it.audioUrl).isNullOrBlank() }
                        ?: result.streamData.availableStreamOptions.firstOrNull { !(it.videoUrl ?: it.audioUrl).isNullOrBlank() }
                    _selectedStreamOption.value = primary
                    _selectedCaptionOption.value = result.streamData.captionOptions.firstOrNull()
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

    private var lastFallbackAttemptTime = 0L
    private var fallbackAttemptsCount = 0

    fun tryNextFallbackStream() {
        val now = System.currentTimeMillis()
        if (now - lastFallbackAttemptTime < 1000L) {
            return
        }
        lastFallbackAttemptTime = now

        val current = _selectedStreamOption.value ?: return
        val ext = _extractionResult.value
        if (ext is YouTubeExtractorHelper.ExtractionResult.Success) {
            val options = ext.streamData.availableStreamOptions
            val currentIndex = options.indexOfFirst { it.videoUrl == current.videoUrl || it.qualityLabel == current.qualityLabel }
            if (currentIndex >= 0 && currentIndex + 1 < options.size && fallbackAttemptsCount < 4) {
                fallbackAttemptsCount++
                val nextOption = options[currentIndex + 1]
                Log.d("MainViewModel", "[Fallback] Playback failed for '${current.qualityLabel}'. Auto falling back ($fallbackAttemptsCount/4) to option ${currentIndex + 1}: '${nextOption.qualityLabel}'")
                _selectedStreamOption.value = nextOption
            } else {
                Log.w("MainViewModel", "[Fallback] All fallback stream options exhausted or maximum attempts reached.")
            }
        }
    }

    fun selectStreamOption(option: PlayableStreamOption) {
        _selectedStreamOption.value = option
    }

    fun selectCaptionOption(caption: CaptionOption?) {
        _selectedCaptionOption.value = caption
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
