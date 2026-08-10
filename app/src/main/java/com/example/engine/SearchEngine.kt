package com.example.engine

import android.content.Context
import android.util.Log
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.FeedResult
import com.example.model.VideoItem
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.util.TMDBHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class SearchEngine(private val context: Context) {

    companion object {
        private const val TAG = "SearchEngine"
    }

    suspend fun searchAll(
        query: String,
        providers: List<ContentProviderApi>
    ): List<VideoItem> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val cleanQuery = query.trim()

        Log.d(TAG, "Executing search for: '$cleanQuery'")

        val tmdbDeferred = async(Dispatchers.IO) {
            try {
                val details = TMDBHelper.fetchMediaDetails(cleanQuery)
                if (details.title.isNotBlank()) {
                    listOf(
                        VideoItem(
                            id = "tmdb_$cleanQuery",
                            title = details.title,
                            uploaderName = if (details.director.isNotBlank()) details.director else "TMDB",
                            thumbnailUrl = null,
                            providerId = "tmdb_movies"
                        )
                    )
                } else emptyList()
            } catch (e: Exception) {
                emptyList<VideoItem>()
            }
        }

        val ytDeferred = async(Dispatchers.IO) {
            try {
                when (val result = YouTubeExtractorHelper.searchVideos(cleanQuery)) {
                    is FeedResult.Success -> result.items
                    is FeedResult.Error -> emptyList()
                }
            } catch (e: Exception) {
                emptyList<VideoItem>()
            }
        }

        val providerJobs = providers.map { provider ->
            async(Dispatchers.IO) {
                try {
                    val paged = provider.search(cleanQuery)
                    paged.items.map { item ->
                        VideoItem(
                            id = item.id,
                            title = item.title,
                            uploaderName = item.uploaderName.ifBlank { provider.providerId.uppercase() },
                            thumbnailUrl = item.thumbnailUrl,
                            providerId = item.providerId
                        )
                    }
                } catch (e: Exception) {
                    emptyList<VideoItem>()
                }
            }
        }

        val tmdbList = tmdbDeferred.await()
        val ytList = ytDeferred.await()
        val providerLists = providerJobs.awaitAll().flatten()

        val combined = mutableListOf<VideoItem>()
        combined.addAll(tmdbList)
        combined.addAll(ytList)
        combined.addAll(providerLists)

        combined.distinctBy { if (it.id.isNotBlank()) it.id else it.title.lowercase() }
    }
}
