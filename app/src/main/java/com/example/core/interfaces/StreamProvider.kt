package com.example.core.interfaces

import com.example.model.PlayableStreamOption

interface StreamProvider {
    val providerId: String
    suspend fun resolveStreams(mediaId: String, qualityHint: String? = null): List<PlayableStreamOption>
}
