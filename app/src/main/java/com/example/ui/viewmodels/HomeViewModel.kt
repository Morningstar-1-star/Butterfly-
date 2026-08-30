package com.example.ui.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.extractor.MultiSourceProvider
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.FeedErrorDetails
import com.example.model.ProviderUiItem
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated ViewModel managing the Home feed state, categorized sections, infinite feed scrolling, and source switching.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HomeViewModel"

    private val _videoItems = MutableStateFlow<List<VideoItem>>(emptyList())
    val videoItems: StateFlow<List<VideoItem>> = _videoItems.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreContent = MutableStateFlow(true)
    val hasMoreContent: StateFlow<Boolean> = _hasMoreContent.asStateFlow()

    private val _feedErrorDetails = MutableStateFlow<FeedErrorDetails?>(null)
    val feedErrorDetails: StateFlow<FeedErrorDetails?> = _feedErrorDetails.asStateFlow()

    private val _activeProviderId = MutableStateFlow("all")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderUiItem>>(emptyList())
    val providers: StateFlow<List<ProviderUiItem>> = _providers.asStateFlow()

    private val _selectedTopic = MutableStateFlow("All")
    val selectedTopic: StateFlow<String> = _selectedTopic.asStateFlow()

    private var homeCurrentPage = 1

    init {
        refreshProvidersList()
        loadTrending(forceRefresh = true)
    }

    fun selectTopic(topic: String) {
        _selectedTopic.value = topic
        loadTrending(forceRefresh = true, topic = if (topic == "All") null else topic)
    }

    fun setActiveProvider(providerId: String) {
        _activeProviderId.value = providerId
        loadTrending(forceRefresh = true)
    }

    fun refreshProvidersList() {
        val list = mutableListOf(
            ProviderUiItem("all", "All Sources", "🌐", true),
            ProviderUiItem("youtube", "YouTube", "▶️", true),
            ProviderUiItem("twitch", "Twitch", "🟣", true),
            ProviderUiItem("torrent", "Torrents", "🧲", true),
            ProviderUiItem("archive_org", "Internet Archive", "🏛️", true),
            ProviderUiItem("dailymotion", "Dailymotion", "📺", true),
            ProviderUiItem("bilibili", "Bilibili", "⚡", true),
            ProviderUiItem("vimeo", "Vimeo", "🎬", true),
            ProviderUiItem("hotstar", "JioCinema / Hotstar", "🌟", true),
            ProviderUiItem("bun-tel-meg", "bun-tel-meg", "☁️", true)
        )
        _providers.value = list
    }

    fun loadTrending(forceRefresh: Boolean = false, topic: String? = null) {
        viewModelScope.launch {
            if (forceRefresh) {
                _isRefreshing.value = true
                homeCurrentPage = 1
                _hasMoreContent.value = true
            }
            _feedErrorDetails.value = null

            try {
                val ctx = getApplication<Application>()
                val targetProvider = _activeProviderId.value
                val fetched = withContext(Dispatchers.IO) {
                    val query = topic ?: "trending popular videos 2026"
                    if (targetProvider == "all" || targetProvider == "youtube") {
                        val ytList = try {
                            YouTubeExtractorHelper.fetchYouTubeTrending(ctx)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        if (ytList.isNotEmpty()) ytList else MultiSourceProvider.search(ctx, "youtube", query, 20, 1)
                    } else {
                        MultiSourceProvider.getHome(ctx, targetProvider, 20, 1).ifEmpty {
                            MultiSourceProvider.search(ctx, targetProvider, query, 20, 1)
                        }
                    }
                }

                if (forceRefresh) {
                    _videoItems.value = fetched
                } else {
                    val current = _videoItems.value.toMutableList()
                    val existingIds = current.map { it.id }.toSet()
                    val newUnique = fetched.filter { it.id !in existingIds }
                    current.addAll(newUnique)
                    _videoItems.value = current
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading home feed", e)
                _feedErrorDetails.value = FeedErrorDetails(
                    rawExceptionName = e.javaClass.simpleName,
                    message = e.localizedMessage ?: "Please check connection or switch sources",
                    fullStackTrace = Log.getStackTraceString(e)
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMoreContent() {
        if (_isLoadingMore.value || !_hasMoreContent.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                homeCurrentPage++
                val ctx = getApplication<Application>()
                val targetProvider = _activeProviderId.value
                val topic = if (_selectedTopic.value == "All") "popular videos" else _selectedTopic.value
                val more = withContext(Dispatchers.IO) {
                    MultiSourceProvider.search(ctx, targetProvider, topic, 20, homeCurrentPage)
                }
                if (more.isEmpty()) {
                    _hasMoreContent.value = false
                } else {
                    val current = _videoItems.value.toMutableList()
                    val existingIds = current.map { it.id }.toSet()
                    current.addAll(more.filter { it.id !in existingIds })
                    _videoItems.value = current
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadMoreContent failed: ${e.message}")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}
