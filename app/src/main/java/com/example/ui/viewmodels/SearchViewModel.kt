package com.example.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.extractor.MultiSourceProvider
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.SearchFilterState
import com.example.model.SearchSuggestionItem
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated ViewModel managing the Search screen state, query suggestions, filters, and multi-source resolution.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "SearchViewModel"

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<VideoItem>>(emptyList())
    val searchResults: StateFlow<List<VideoItem>> = _searchResults.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<SearchSuggestionItem>>(emptyList())
    val searchSuggestions: StateFlow<List<SearchSuggestionItem>> = _searchSuggestions.asStateFlow()

    private val _searchFilterState = MutableStateFlow(SearchFilterState())
    val searchFilterState: StateFlow<SearchFilterState> = _searchFilterState.asStateFlow()

    private val _searchRecs = MutableStateFlow<List<VideoItem>>(emptyList())
    val searchRecs: StateFlow<List<VideoItem>> = _searchRecs.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

    init {
        // Auto-fetch suggestions on debounced query changes
        viewModelScope.launch {
            _searchQuery
                .debounce(250)
                .collectLatest { query ->
                    if (query.trim().length >= 2) {
                        fetchSuggestions(query.trim())
                    } else {
                        _searchSuggestions.value = emptyList()
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchSuggestions.value = emptyList()
        _isSearching.value = false
    }

    fun updateFilter(filter: SearchFilterState) {
        _searchFilterState.value = filter
        if (_searchQuery.value.isNotBlank()) {
            performSearch(_searchQuery.value)
        }
    }

    private fun fetchSuggestions(query: String) {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val suggestions = YouTubeExtractorHelper.fetchSearchSuggestions(query)
                val items = suggestions.map { SearchSuggestionItem(it) }
                _searchSuggestions.value = items
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch suggestions: ${e.message}")
            }
        }
    }

    fun performSearch(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return

        _searchQuery.value = cleanQuery
        _isSearching.value = true

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val results = withContext(Dispatchers.IO) {
                    val ytList = try {
                        YouTubeExtractorHelper.searchYouTube(cleanQuery, ctx)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (ytList.isNotEmpty()) {
                        ytList
                    } else {
                        MultiSourceProvider.search(ctx, "youtube", cleanQuery, 25, 1)
                    }
                }
                _searchResults.value = results
            } catch (e: Exception) {
                Log.e(TAG, "Search execution failed", e)
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }
}
