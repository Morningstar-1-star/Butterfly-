package com.example.repository

import android.content.Context
import com.example.model.MediaIdentity
import com.example.model.StreamResult
import com.example.resolver.UniversalProviderAggregator
import kotlinx.coroutines.flow.Flow

/**
 * Stream Repository coordinating universal provider aggregation,
 * caching, and stream resolution.
 */
class StreamRepository(private val context: Context) {

    companion object {
        @Volatile
        private var instance: StreamRepository? = null

        fun getInstance(context: Context): StreamRepository {
            return instance ?: synchronized(this) {
                instance ?: StreamRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val aggregator = UniversalProviderAggregator.getInstance(context)

    /**
     * Resolves streams across all registered providers via AIOStreams architecture.
     */
    fun getStreams(identity: MediaIdentity): Flow<List<StreamResult>> {
        return aggregator.aggregateStreams(identity)
    }
}
