package com.example.effects

import android.content.Context
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * RAVU (Rapid and Accurate Video Upscaling) Directional Super-Resolution Effect.
 */
data class RavuEffect(
    val sharpen: Float = 50f
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return UpscalingShaderProgram(
            context = context,
            useHdr = useHdr,
            pipelineMode = 4, // RAVU
            sharpenAmount = sharpen
        )
    }
}
