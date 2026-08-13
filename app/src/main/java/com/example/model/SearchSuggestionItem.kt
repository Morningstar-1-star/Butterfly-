package com.example.model

data class SearchSuggestionItem(
    val query: String,
    val isHistory: Boolean = false,
    val providerBadge: String? = null,
    val thumbnailUrl: String? = null,
    val subtitle: String? = null
)
