package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

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
        loadTrending()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(query: String? = null) {
        val q = query ?: _searchQuery.value
        if (q.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            val results = YouTubeExtractorHelper.searchVideos(q)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun loadTrending() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingTrending.value = true
            val trending = YouTubeExtractorHelper.fetchTrendingVideos()
            _trendingVideos.value = trending
            _isLoadingTrending.value = false
        }
    }

    fun playVideo(videoIdOrUrl: String) {
        val cleanIdOrUrl = videoIdOrUrl.trim()
        if (cleanIdOrUrl.isEmpty()) return

        _activeVideoId.value = cleanIdOrUrl
        _isExtracting.value = true
        _extractionResult.value = null
        _selectedStreamOption.value = null
        _selectedCaptionOption.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val result = YouTubeExtractorHelper.fetchStreamData(cleanIdOrUrl)
            _extractionResult.value = result
            _isExtracting.value = false

            if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                _selectedStreamOption.value = result.streamData.selectedStreamOption
                _selectedCaptionOption.value = result.streamData.captionOptions.firstOrNull()
            }
        }
    }

    fun selectStreamOption(option: PlayableStreamOption) {
        _selectedStreamOption.value = option
    }

    fun selectCaptionOption(caption: CaptionOption?) {
        _selectedCaptionOption.value = caption
    }
}
