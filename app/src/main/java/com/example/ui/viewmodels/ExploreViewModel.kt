package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ExploreMediaItem
import com.example.model.ExploreMediaType
import com.example.model.ExploreSection
import com.example.util.ExploreMediaHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val _sections = MutableStateFlow<List<ExploreSection>>(emptyList())
    val sections: StateFlow<List<ExploreSection>> = _sections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAdultMode = MutableStateFlow(false)
    val isAdultMode: StateFlow<Boolean> = _isAdultMode.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ExploreMediaItem>>(emptyList())
    val searchResults: StateFlow<List<ExploreMediaItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _trendingTopics = MutableStateFlow<List<String>>(emptyList())
    val trendingTopics: StateFlow<List<String>> = _trendingTopics.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setAdultMode(enabled: Boolean) {
        if (_isAdultMode.value != enabled) {
            _isAdultMode.value = enabled
            loadExploreFeed(isAdult = enabled, forceRefresh = true)
            loadTrendingTopics(isAdult = enabled)
        }
    }

    fun loadExploreFeed(isAdult: Boolean = _isAdultMode.value, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = ExploreMediaHelper.fetchExploreFeed(isAdult = isAdult)
                if (result.isNotEmpty()) {
                    _sections.value = result
                } else if (_sections.value.isEmpty()) {
                    _sections.value = ExploreMediaHelper.getInstantInitialFeed(isAdult = isAdult)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load explore feed"
                if (_sections.value.isEmpty()) {
                    _sections.value = ExploreMediaHelper.getInstantInitialFeed(isAdult = isAdult)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun performSearch(query: String, isAdult: Boolean = _isAdultMode.value) {
        val clean = query.trim()
        if (clean.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val sanitized = com.example.util.SmartSearchSanitizer.sanitizeQuery(clean)
                val results = ExploreMediaHelper.searchAll(sanitized.cleanQuery, isAdult = isAdult)
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun loadTrendingTopics(isAdult: Boolean = _isAdultMode.value) {
        viewModelScope.launch {
            try {
                val topics = ExploreMediaHelper.fetchTrendingSearchTopics(isAdult = isAdult)
                _trendingTopics.value = topics
            } catch (_: Exception) {}
        }
    }

    suspend fun resolveFullDetails(item: ExploreMediaItem, isAdult: Boolean = _isAdultMode.value): ExploreMediaItem {
        return try {
            ExploreMediaHelper.resolveFullMediaDetails(item, isAdult = isAdult)
        } catch (e: Exception) {
            item
        }
    }

    suspend fun fetchCategoryItems(mediaType: ExploreMediaType, isAdult: Boolean = _isAdultMode.value): List<ExploreMediaItem> {
        return try {
            ExploreMediaHelper.fetchCategoryItems(mediaType, isAdult = isAdult)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
