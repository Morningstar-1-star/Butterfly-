package com.example.core.interfaces

import com.example.ui.player.audio.AudioEnhancementConfig
import kotlinx.coroutines.flow.StateFlow

interface AudioEngineInterface {
    val config: StateFlow<AudioEnhancementConfig>
    fun setEnabled(enabled: Boolean)
    fun setVolumeBoostPercent(boostPct: Int)
    fun setNightModeEnabled(enabled: Boolean)
    fun setDialogueClarityEnabled(enabled: Boolean)
    fun setBiquadEqGainDb(bandIndex: Int, gainDb: Float)
}
