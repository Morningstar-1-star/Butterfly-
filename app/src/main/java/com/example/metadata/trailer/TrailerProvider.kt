package com.example.metadata.trailer

import com.example.metadata.JavMetadata
import com.example.model.VideoTrailerClip

/**
 * Universal interface for trailer and preview stream discovery.
 */
interface TrailerProvider {
    val id: String
    val name: String

    /**
     * Resolves playable trailer video clips and sample previews for a given JAV code and metadata.
     */
    suspend fun resolveTrailers(javCode: String, metadata: JavMetadata?): List<VideoTrailerClip>
}
