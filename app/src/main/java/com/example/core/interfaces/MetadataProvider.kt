package com.example.core.interfaces

import com.example.model.ExploreMediaItem

interface MetadataProvider {
    val providerName: String
    suspend fun fetchTrending(): List<ExploreMediaItem>
    suspend fun fetchTopRated(): List<ExploreMediaItem>
    suspend fun searchMedia(query: String): List<ExploreMediaItem>
    suspend fun fetchDetails(mediaId: String): ExploreMediaItem?
}
