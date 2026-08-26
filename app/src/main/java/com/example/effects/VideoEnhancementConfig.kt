package com.example.effects

/**
 * Top-level presets for the Butterfly Video Shader Enhancement Engine.
 */
enum class VideoEnhancementPreset(val displayName: String, val description: String) {
    OFF("Off", "Native video rendering with zero shader passes"),
    AUTO("Auto", "Intelligently selects GLSL shader filter pipeline based on resolution and content"),
    QUALITY("Quality", "High visual fidelity using ArtCNN-style spatial filter + CfL chroma + anti-ringing"),
    PERFORMANCE("Performance", "Lightweight FSRCNNX-style edge sharpening shader with low GPU load"),
    ANIME("Anime4K Shader", "Anime4K-style line restoration, dark-line push, and bilateral chroma shader"),
    LIVE_ACTION("Live Action Shader", "ArtCNN-style spatial shader optimized for cinema film grain and contours")
}

/**
 * Shader enhancement algorithms available in the engine.
 */
enum class UpscalerEngine(val displayName: String, val shortTag: String) {
    AUTO("Auto (Smart Shader Pipeline)", "AUTO"),
    ART_CNN_C4F16("ArtCNN-style Shader (Spatial HD)", "ArtCNN"),
    FSRCNNX_X2("FSRCNNX-style Shader (Edge Sharpen)", "FSRCNNX"),
    RAVU_ZOOM("RAVU-style Shader (Directional)", "RAVU"),
    ANIME4K("Anime4K-style Shader (Line Push)", "Anime4K"),
    SSIM_SUPER_RES("SSimSuperRes Shader (Anti-Ring)", "SSimRes")
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
