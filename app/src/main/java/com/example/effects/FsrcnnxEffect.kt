package com.example.effects

import android.content.Context
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * FSRCNNX (Fast Super-Resolution Convolutional Neural Network) Effect.
 */
data class FsrcnnxEffect(
    val sharpen: Float = 50f,
    val deband: Float = 30f
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return UpscalingShaderProgram(
            context = context,
            useHdr = useHdr,
            pipelineMode = 3, // FSRCNNX
            sharpenAmount = sharpen,
            debandAmount = deband
        )
    }
}
