package com.example.domain

import android.content.Context
import com.example.model.MediaIdentity
import com.example.model.StreamResult
import com.example.repository.StreamRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case to resolve and rank playback streams for a given media identity.
 */
class ResolveStreamsUseCase(private val context: Context) {
    private val repository = StreamRepository.getInstance(context)

    operator fun invoke(identity: MediaIdentity): Flow<List<StreamResult>> {
        return repository.getStreams(identity)
    }
}
