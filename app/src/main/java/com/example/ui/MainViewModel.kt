package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.CaptionOption
import com.example.model.FeedErrorDetails
import com.example.model.FeedResult
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
        loadTrending()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(query: String? = null) {
        val q = query ?: _searchQuery.value
        if (q.isBlank()) return

        android.util.Log.d("MainViewModel", "[TRACE] SearchBar -> performSearch input: '$q'")

        when (val parseResult = YouTubeExtractorHelper.parseYouTubeInput(q)) {
            is YouTubeExtractorHelper.UrlParseResult.ValidVideoId -> {
                android.util.Log.d("MainViewModel", "[TRACE] URL/ID parsed successfully -> Video ID: '${parseResult.videoId}'. DIRECT TO STREAMINFO & EXOPLAYER!")
                _feedError.value = null
                playVideo(parseResult.videoId)
            }
            is YouTubeExtractorHelper.UrlParseResult.InvalidUrl -> {
                android.util.Log.w("MainViewModel", "[TRACE] Invalid YouTube URL attempt: '$q'")
                _searchResults.value = emptyList()
                _feedError.value = FeedErrorDetails(
                    rawExceptionName = "InvalidUrlException",
                    message = parseResult.message,
                    fullStackTrace = "The input '$q' was recognized as a YouTube URL or ID attempt, but no valid 11-character video ID could be extracted.\n\nAccepted formats:\n - youtube.com/watch?v=VIDEO_ID\n - youtu.be/VIDEO_ID\n - m.youtube.com/watch?v=VIDEO_ID\n - youtube.com/shorts/VIDEO_ID\n - youtube.com/live/VIDEO_ID\n - Raw 11-character Video ID",
                    causeInfo = null,
                    urlOrQuery = q
                )
            }
            is YouTubeExtractorHelper.UrlParseResult.SearchQuery -> {
                android.util.Log.d("MainViewModel", "[TRACE] Plain search query: '$q'. Calling searchVideos()")
                viewModelScope.launch(Dispatchers.IO) {
                    _isSearching.value = true
                    _feedError.value = null
                    when (val result = YouTubeExtractorHelper.searchVideos(q)) {
                        is FeedResult.Success -> {
                            _searchResults.value = result.items
                        }
                        is FeedResult.Error -> {
                            _searchResults.value = emptyList()
                            _feedError.value = result.errorDetails
                        }
                    }
                    _isSearching.value = false
                }
            }
        }
    }

    fun loadTrending() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingTrending.value = true
            _feedError.value = null
            when (val result = YouTubeExtractorHelper.fetchTrendingVideos()) {
                is FeedResult.Success -> {
                    _trendingVideos.value = result.items
                }
                is FeedResult.Error -> {
                    _trendingVideos.value = emptyList()
                    _feedError.value = result.errorDetails
                }
            }
            _isLoadingTrending.value = false
        }
    }

    fun playVideo(videoIdOrUrl: String) {
        val cleanIdOrUrl = videoIdOrUrl.trim()
        if (cleanIdOrUrl.isEmpty()) return

        android.util.Log.d("MainViewModel", "[TRACE] playVideo requested for: '$cleanIdOrUrl'")

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
                android.util.Log.d(
                    "MainViewModel",
                    "[TRACE] StreamData fetched successfully for '${result.streamData.videoId}'. Stream option selected: '${result.streamData.selectedStreamOption?.qualityLabel}'. ExoPlayer ready to play!"
                )
                _selectedStreamOption.value = result.streamData.selectedStreamOption
                _selectedCaptionOption.value = result.streamData.captionOptions.firstOrNull()
            } else if (result is YouTubeExtractorHelper.ExtractionResult.Error) {
                android.util.Log.e(
                    "MainViewModel",
                    "[TRACE] StreamInfo extraction failed: ${result.errorDetails.message}"
                )
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
