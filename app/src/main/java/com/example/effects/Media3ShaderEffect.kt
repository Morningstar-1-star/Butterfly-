package com.example.effects

import android.content.Context
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Media3 GlEffect for real-time video upscaling and shader enhancements.
 */
data class Media3ShaderEffect(
    val pipelineMode: Int = 1, // 1=Anime4K, 2=ArtCNN, 3=FSRCNNX, 4=RAVU
    val scaleFactor: Float = 1.0f,
    val sharpen: Float = 50f,
    val deband: Float = 30f,
    val denoise: Float = 20f,
    val antiRinging: Boolean = true,
    val cfl: Boolean = true
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ShaderEnhancementProgram(
            context = context,
            useHdr = useHdr,
            pipelineMode = pipelineMode,
            scaleFactor = scaleFactor,
            sharpenAmount = sharpen,
            debandAmount = deband,
            denoiseAmount = denoise,
            antiRinging = antiRinging,
            cfl = cfl
        )
    }
}
