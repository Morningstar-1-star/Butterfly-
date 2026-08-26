package com.example.effects

import androidx.media3.common.Effect

/**
 * Factory for creating configured Media3 effects according to active upscaling preset and parameters.
 */
object ShaderEnhancementLoader {

    fun createEffect(config: VideoEnhancementConfig, isAnime: Boolean): Effect? {
        if (!config.isEnabled) return null

        val mode = when (config.preset) {
            VideoEnhancementPreset.OFF -> 0
            VideoEnhancementPreset.QUALITY -> 2 // ArtCNN
            VideoEnhancementPreset.PERFORMANCE -> 3 // FSRCNNX
            VideoEnhancementPreset.ANIME -> 1 // Anime4K
            VideoEnhancementPreset.LIVE_ACTION -> 2 // ArtCNN
            VideoEnhancementPreset.AUTO -> {
                if (config.animeMode == AnimeDetectionMode.ALWAYS_ON || (config.animeMode == AnimeDetectionMode.AUTO && isAnime)) {
                    1 // Anime4K
                } else {
                    2 // ArtCNN
                }
            }
        }

        if (mode == 0) return null

        return Media3ShaderEffect(
            pipelineMode = mode,
            sharpen = config.sharpen,
            deband = config.deband,
            denoise = config.denoise,
            antiRinging = config.antiRinging,
            cfl = config.chromaReconstructionCfL
        )
    }
}
