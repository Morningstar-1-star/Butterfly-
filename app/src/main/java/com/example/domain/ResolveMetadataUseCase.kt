package com.example.domain

import android.content.Context
import com.example.model.MediaMetadata
import com.example.repository.MetadataRepository

/**
 * Use case to search media metadata across all enabled engines.
 */
class SearchMediaUseCase(private val context: Context) {
    private val repository = MetadataRepository.getInstance(context)

    suspend operator fun invoke(query: String): List<MediaMetadata> {
        if (query.isBlank()) return emptyList()
        return repository.search(query)
    }
}

/**
 * Use case to resolve deep, enriched media metadata.
 */
class ResolveMetadataUseCase(private val context: Context) {
    private val repository = MetadataRepository.getInstance(context)

    suspend operator fun invoke(queryOrId: String): MediaMetadata? {
        if (queryOrId.isBlank()) return null
        return repository.resolveMetadata(queryOrId)
    }
}
