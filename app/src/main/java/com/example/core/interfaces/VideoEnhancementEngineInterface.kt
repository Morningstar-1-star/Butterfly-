package com.example.core.interfaces

import com.example.effects.VideoEnhancementConfig
import com.example.effects.VideoEnhancementPreset
import com.example.effects.VideoEnhancementTelemetry
import kotlinx.coroutines.flow.StateFlow

interface VideoEnhancementEngineInterface {
    val config: StateFlow<VideoEnhancementConfig>
    val telemetry: StateFlow<VideoEnhancementTelemetry>
    fun setPreset(preset: VideoEnhancementPreset)
    fun setScaleFactor(factor: Float)
    fun setSharpenAmount(amount: Float)
    fun setDebandAmount(amount: Float)
    fun setDenoiseAmount(amount: Float)
}
