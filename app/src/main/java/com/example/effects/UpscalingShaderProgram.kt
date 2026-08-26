package com.example.effects

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.SingleFrameGlShaderProgram
import java.io.IOException

/**
 * Media3 GlShaderProgram that executes GPU shaders (Anime4K, ArtCNN, FSRCNNX, RAVU)
 * directly on decoded OpenGL video frames in real-time.
 */
class UpscalingShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val pipelineMode: Int, // 1=Anime4K, 2=ArtCNN, 3=FSRCNNX, 4=RAVU
    private val sharpenAmount: Float = 50f,
    private val debandAmount: Float = 30f,
    private val denoiseAmount: Float = 20f,
    private val antiRinging: Boolean = true,
    private val cfl: Boolean = true
) : SingleFrameGlShaderProgram(useHdr) {

    private val glProgram: GlProgram

    init {
        try {
            glProgram = GlProgram(
                VERTEX_SHADER,
                VideoShaderManager.UPSCALE_PIPELINE_FRAGMENT_SHADER
            )
        } catch (e: IOException) {
            throw VideoFrameProcessingException(e)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        // Output frame dimension matching input texture
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()

            // Bind input texture from decoder
            glProgram.setSamplerTexIdUniform("uTexture", inputTexId, /* texUnitIndex= */ 0)

            // Feed shader uniforms
            glProgram.setIntUniform("uPipelineMode", pipelineMode)
            glProgram.setFloatUniform("uSharpen", sharpenAmount)
            glProgram.setFloatUniform("uDeband", debandAmount)
            glProgram.setFloatUniform("uDenoise", denoiseAmount)
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                /* size= */ 4
            )

            // Update telemetry with actual frame timestamp
            VideoEnhancementEngine.onGpuFrameProcessed(presentationTimeUs)

            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            // Log/ignore on teardown
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            #version 300 es
            in vec4 aFramePosition;
            out vec2 vTexCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexCoord = (aFramePosition.xy + vec2(1.0, 1.0)) * 0.5;
            }
        """
    }
}
