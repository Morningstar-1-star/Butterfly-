package com.example.ui

import android.app.Application
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val pluginManager = PluginManager(application)
    val repositoryManager = RepositoryManager(application, pluginManager)
    val extensionManager = ExtensionManager(application, pluginManager, repositoryManager)

    val repositories: StateFlow<List<Repository>> = repositoryManager.repositories
    val extensionStatuses: StateFlow<List<ExtensionStatus>> = extensionManager.extensionStatuses

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _activeProviderId = MutableStateFlow("peertube")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _enabledProviderIds = MutableStateFlow<Set<String>>(
        setOf(
            "peertube", "youtube", "dailymotion", "vimeo",
            "archive_org", "ted", "nasa", "direct_mp4",
            "direct_hls", "rss_video", "json"
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

    private val _selectedCaptionOption = MutableStateFlow<CaptionOption?>(null)
    val selectedCaptionOption: StateFlow<CaptionOption?> = _selectedCaptionOption.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repositoryManager.loadRepositories()
            extensionManager.refreshExtensions()
            refreshProvidersList()
            setActiveProvider("peertube")
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
        return pluginManager.getProvider(_activeProviderId.value)
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
        refreshProvidersList()
        loadTrending()
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
        val all = pluginManager.getAllAvailableProviders()
        val activeId = _activeProviderId.value
        val enabledSet = _enabledProviderIds.value

        val uiList = all.map { provider ->
            val id = provider.providerId
            val name = getReadableProviderName(id)
            ProviderUiItem(
                id = id,
                name = name,
                description = "Streaming provider ($id)",
                isEnabled = enabledSet.contains(id),
                isDefault = (id == activeId)
            )
        }
        _availableProviders.value = uiList
    }

    private fun getReadableProviderName(id: String): String {
        return when (id) {
            "peertube" -> "PeerTube"
            "youtube" -> "YouTube"
            "dailymotion" -> "Dailymotion"
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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(query: String? = null) {
        val q = query ?: _searchQuery.value
        if (q.isBlank()) return

        Log.d("MainViewModel", "Search query: '$q' on active provider: ${_activeProviderId.value}")

        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            _feedError.value = null
            try {
                val provider = getActiveProvider()
                if (provider != null) {
                    val paged = provider.search(q)
                    val items = paged.items.map { item ->
                        VideoItem(
                            id = item.id,
                            title = item.title,
                            uploaderName = item.uploaderName,
                            uploaderUrl = item.uploaderUrl,
                            uploaderAvatarUrl = item.uploaderAvatarUrl,
                            viewCount = item.viewCount,
                            durationSeconds = item.durationSeconds,
                            uploadDate = item.uploadDate,
                            thumbnailUrl = item.thumbnailUrl
                        )
                    }
                    _searchResults.value = items
                } else {
                    _searchResults.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Search failed: ${e.localizedMessage}", e)
                _searchResults.value = emptyList()
                _feedError.value = FeedErrorDetails(
                    rawExceptionName = e.javaClass.simpleName,
                    message = e.localizedMessage ?: "Search failed on active provider",
                    fullStackTrace = e.stackTraceToString(),
                    urlOrQuery = q
                )
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun loadTrending() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingTrending.value = true
            _feedError.value = null
            try {
                val provider = getActiveProvider()
                if (provider == null) {
                    _trendingVideos.value = emptyList()
                    _feedError.value = FeedErrorDetails(
                        rawExceptionName = "NoActiveProvider",
                        message = "Active provider '${_activeProviderId.value}' is not registered.",
                        fullStackTrace = "No active content provider available in PluginManager."
                    )
                    _isLoadingTrending.value = false
                    return@launch
                }

                val paged = provider.home()
                val items = paged.items.map { item ->
                    VideoItem(
                        id = item.id,
                        title = item.title,
                        uploaderName = item.uploaderName,
                        uploaderUrl = item.uploaderUrl,
                        uploaderAvatarUrl = item.uploaderAvatarUrl,
                        viewCount = item.viewCount,
                        durationSeconds = item.durationSeconds,
                        uploadDate = item.uploadDate,
                        thumbnailUrl = item.thumbnailUrl
                    )
                }
                _trendingVideos.value = items
                _searchResults.value = emptyList()
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadTrending failed: ${e.localizedMessage}", e)
                _trendingVideos.value = emptyList()
                _feedError.value = FeedErrorDetails(
                    rawExceptionName = e.javaClass.simpleName,
                    message = e.localizedMessage ?: "Failed to fetch content from active provider",
                    fullStackTrace = e.stackTraceToString()
                )
            } finally {
                _isLoadingTrending.value = false
            }
        }
    }

    fun playVideo(videoIdOrUrl: String) {
        val cleanIdOrUrl = videoIdOrUrl.trim()
        if (cleanIdOrUrl.isEmpty()) return

        Log.d("MainViewModel", "playVideo requested for: '$cleanIdOrUrl' on provider: ${_activeProviderId.value}")

        _activeVideoId.value = cleanIdOrUrl
        _isExtracting.value = true
        _extractionResult.value = null
        _selectedStreamOption.value = null
        _selectedCaptionOption.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = getActiveProvider()
                if (provider != null) {
                    val streamInfo = provider.getStreams(cleanIdOrUrl)
                    val streamData = streamInfo.toStreamData()
                    _extractionResult.value = YouTubeExtractorHelper.ExtractionResult.Success(streamData)
                    _selectedStreamOption.value = streamData.selectedStreamOption
                    _selectedCaptionOption.value = streamData.captionOptions.firstOrNull()
                } else {
                    val result = YouTubeExtractorHelper.fetchStreamData(cleanIdOrUrl)
                    _extractionResult.value = result
                    if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                        _selectedStreamOption.value = result.streamData.selectedStreamOption
                        _selectedCaptionOption.value = result.streamData.captionOptions.firstOrNull()
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

    fun selectStreamOption(option: PlayableStreamOption) {
        _selectedStreamOption.value = option
    }

    fun selectCaptionOption(caption: CaptionOption?) {
        _selectedCaptionOption.value = caption
    }

    private fun PluginStreamInfo.toStreamData(): StreamData {
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
            relatedVideos = emptyList()
        )
    }
}
