package com.example.effects

/**
 * Top-level presets for the Butterfly Video GLSL Shader Enhancement Engine.
 * Provides GPU-accelerated spatial unsharp masking, line refinement, debanding, and chroma reconstruction.
 */
enum class VideoEnhancementPreset(val displayName: String, val description: String) {
    OFF("Off", "Native video rendering with zero shader passes"),
    AUTO("Auto (Adaptive)", "Intelligently selects GLSL spatial filter pipeline based on resolution and content"),
    SHARPEN_HIGH("High-Precision Sharpen", "Fine-detail unsharp mask + CfL chroma reconstruction + anti-ringing bounds"),
    PERFORMANCE("Fast Edge Sharpen", "Lightweight 3x3 Laplacian edge sharpening shader with low GPU load"),
    SHARPEN_ANIME("Anime Line Sharpen", "Spatial gradient line push, outline contrast darkening & deband shader"),
    LIVE_ACTION("Cinema Contour Sharpen", "Spatial contour unsharp filter tuned for film grain and natural textures");

    companion object {
        // Backward-compatible alias references
        val QUALITY: VideoEnhancementPreset get() = SHARPEN_HIGH
        val ANIME: VideoEnhancementPreset get() = SHARPEN_ANIME
    }
}

/**
 * Shader enhancement algorithms available in the engine.
 */
enum class UpscalerEngine(val displayName: String, val shortTag: String) {
    AUTO("Auto (Adaptive Shader Filter)", "AUTO"),
    SHARPEN_CONV("Spatial Convolution Sharpen", "ConvSharpen"),
    EDGE_DECONV("Laplacian Edge Sharpen", "EdgeSharpen"),
    DIRECTIONAL_INTERP("Directional Edge Filter", "DirFilter"),
    ANIME_LINE_PUSH("Anime Outline Gradient Push", "LinePush"),
    SSIM_SUPER_RES("Anti-Ringing Unsharp Mask", "AntiRing");

    companion object {
        val ART_CNN_C4F16: UpscalerEngine get() = SHARPEN_CONV
        val FSRCNNX_X2: UpscalerEngine get() = EDGE_DECONV
        val RAVU_ZOOM: UpscalerEngine get() = DIRECTIONAL_INTERP
        val ANIME4K: UpscalerEngine get() = ANIME_LINE_PUSH
    }
}

/**
 * Anime detection mode.
 */
enum class AnimeDetectionMode(val displayName: String) {
    AUTO("Auto Detect (Metadata / Tags)"),
    ALWAYS_ON("Always On (Force Anime)"),
    ALWAYS_OFF("Always Off (Force Live Action)")
}

/**
 * Real-time GPU Safety & Performance status.
 */
enum class GpuSafetyState(val displayName: String, val badgeColorHex: Long) {
    OPTIMAL("Optimal (60 FPS)", 0xFF00E676),
    BALANCED("Balanced", 0xFF00E5FF),
    HIGH_LOAD("High GPU Load", 0xFFFFB74D),
    THROTTLED_DOWNGRADED("Safety Throttled (Downgraded)", 0xFFFF5252)
}

/**
 * Configuration model for the Video Enhancement & Upscaling Engine.
 */
data class VideoEnhancementConfig(
    val isEnabled: Boolean = false,
    val preset: VideoEnhancementPreset = VideoEnhancementPreset.OFF,
    val upscalerEngine: UpscalerEngine = UpscalerEngine.AUTO,
    val animeMode: AnimeDetectionMode = AnimeDetectionMode.AUTO,
    val sharpen: Float = 35f, // 0..100
    val deband: Float = 25f, // 0..100
    val denoise: Float = 15f, // 0..100
    val chromaReconstructionCfL: Boolean = true,
    val antiRinging: Boolean = true,
    val gpuPerformanceMode: Boolean = false,
    val autoGpuSafety: Boolean = true,
    val showDebugHud: Boolean = false
) {
    fun hasActiveEnhancement(): Boolean {
        if (!isEnabled || preset == VideoEnhancementPreset.OFF) return false
        return true
    }
}

/**
 * Live GPU and video enhancement telemetry.
 */
data class VideoEnhancementTelemetry(
    val activePipelineName: String = "ArtCNN-style Shader (Auto)",
    val inputResolution: String = "1080p",
    val enhancedFramebufferResolution: String = "1080p (Shader Enhanced)",
    val isAnimeDetected: Boolean = false,
    val isNative4kPassthrough: Boolean = false,
    val currentFps: Float = 60.0f,
    val droppedFramesCount: Long = 0L,
    val gpuSafetyState: GpuSafetyState = GpuSafetyState.OPTIMAL,
    val activePassesCount: Int = 3
)
