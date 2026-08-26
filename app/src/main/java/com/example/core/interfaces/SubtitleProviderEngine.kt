package com.example.core.interfaces

import com.example.subtitles.SubtitleItem
import com.example.subtitles.SubtitleSourceType

interface SubtitleProviderEngine {
    val sourceType: SubtitleSourceType
    suspend fun searchSubtitles(title: String, imdbId: String? = null, language: String = "en"): List<SubtitleItem>
}
