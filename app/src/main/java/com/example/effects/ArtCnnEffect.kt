package com.example.effects

import android.content.Context
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * ArtCNN C4F16 (Convolutional 4-channel 16-feature) Perceptual Neural Super-Resolution Effect.
 */
data class ArtCnnEffect(
    val sharpen: Float = 50f,
    val antiRinging: Boolean = true,
    val cfl: Boolean = true
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ShaderEnhancementProgram(
            context = context,
            useHdr = useHdr,
            pipelineMode = 2, // ArtCNN
            sharpenAmount = sharpen,
            antiRinging = antiRinging,
            cfl = cfl
        )
    }
}
