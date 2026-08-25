package com.example.effects

/**
 * Top-level presets for the Butterfly Video Enhancement & Upscaling Engine.
 */
enum class VideoEnhancementPreset(val displayName: String, val description: String) {
    OFF("Off", "Native video rendering with zero shader passes"),
    AUTO("Auto", "Intelligently selects optimal neural/GLSL pipeline based on resolution, content, and GPU"),
    QUALITY("Quality", "Maximum visual fidelity using ArtCNN C4F16 + CfL + SSimSuperRes anti-ringing"),
    PERFORMANCE("Performance", "Lightweight FSRCNNX / Fast Bilateral scaler with low battery & GPU impact"),
    ANIME("Anime4K", "Anime4K line thinning, dark-line push, and bilateral chroma reconstruction"),
    LIVE_ACTION("Live Action", "ArtCNN neural-approximation filter optimized for cinema film grain and faces")
}

/**
 * Upscaler algorithms available in the engine.
 */
enum class UpscalerEngine(val displayName: String, val shortTag: String) {
    AUTO("Auto (Smart Pipeline)", "AUTO"),
    ART_CNN_C4F16("ArtCNN C4F16 (Neural HD)", "ArtCNN"),
    FSRCNNX_X2("FSRCNNX ×2 (SuperRes)", "FSRCNNX"),
    RAVU_ZOOM("RAVU-Zoom (SD/Directional)", "RAVU"),
    ANIME4K("Anime4K / Ani4K (Line Push)", "Anime4K"),
    SSIM_SUPER_RES("SSimSuperRes (Anti-Ring)", "SSimRes")
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
    val isEnabled: Boolean = true,
    val preset: VideoEnhancementPreset = VideoEnhancementPreset.AUTO,
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
    fun hasActiveUpscaling(): Boolean {
        if (!isEnabled || preset == VideoEnhancementPreset.OFF) return false
        return true
    }
}

/**
 * Live GPU and video enhancement telemetry.
 */
data class VideoEnhancementTelemetry(
    val activePipelineName: String = "ArtCNN C4F16 2x (Auto)",
    val inputResolution: String = "1080p",
    val upscaledResolution: String = "1440p / 4K",
    val isAnimeDetected: Boolean = false,
    val isNative4kPassthrough: Boolean = false,
    val currentFps: Float = 60.0f,
    val droppedFramesCount: Long = 0L,
    val gpuSafetyState: GpuSafetyState = GpuSafetyState.OPTIMAL,
    val activePassesCount: Int = 3
)
