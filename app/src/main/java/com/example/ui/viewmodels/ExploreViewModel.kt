package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ExploreMediaItem
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadExploreFeed()
    }

    fun loadExploreFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = ExploreMediaHelper.fetchExploreFeed()
                _sections.value = result
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load explore feed"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
