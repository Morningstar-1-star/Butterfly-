package com.example.smartskip

import android.content.Context

interface SkipSegmentProvider {
    val source: SkipSource
    val providerId: String
    val displayName: String

    suspend fun fetchSegments(
        context: Context,
        videoId: String,
        durationMs: Long,
        title: String? = null,
        channelName: String? = null,
        providerIdParam: String? = null,
        extraMeta: Map<String, String> = emptyMap()
    ): List<SkipSegment>
}
