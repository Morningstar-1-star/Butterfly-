package com.example.model

enum class PlaybackSourceType {
    DIRECT_STREAM // Direct playable video stream for ExoPlayer
}

object PlaybackDecisionResolver {

    fun determineSourceType(rawUrl: String?, format: String? = null): PlaybackSourceType {
        return PlaybackSourceType.DIRECT_STREAM
    }
}




