package com.example.model

import androidx.media3.common.Tracks

data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val languageCode: String,
    val displayLanguage: String,
    val isSelected: Boolean,
    val channelInfo: String,
    val trackGroup: Tracks.Group
)
