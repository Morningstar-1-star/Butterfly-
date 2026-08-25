package com.example.effects

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton engine managing real-time video upscaling, shader pipelines, GPU safety throttling,
 * and content-adaptive engine selection.
 */
object VideoEnhancementEngine {

    private val _config = MutableStateFlow(VideoEnhancementConfig())
    val config: StateFlow<VideoEnhancementConfig> = _config.asStateFlow()

    private val _telemetry = MutableStateFlow(VideoEnhancementTelemetry())
    val telemetry: StateFlow<VideoEnhancementTelemetry> = _telemetry.asStateFlow()

    // Per-video memory
    private val perVideoCache = ConcurrentHashMap<String, VideoEnhancementConfig>()
    private var currentVideoId: String? = null
    private var isCurrentAnime = false

    // Frame drop tracking for GPU safety
    private var lastFpsCheckTime = System.currentTimeMillis()
    private var frameCountSinceLastCheck = 0
    private var totalDroppedFrames = 0L

    fun onVideoLoaded(
        videoId: String?,
        title: String?,
        channel: String?,
        tags: List<String>?,
        description: String?,
        width: Int,
        height: Int
    ) {
        currentVideoId = videoId
        isCurrentAnime = VideoShaderManager.isAnimeContent(title, tags, description, channel)

        if (videoId != null && perVideoCache.containsKey(videoId)) {
            _config.value = perVideoCache[videoId] ?: VideoEnhancementConfig()
        }

        updatePipelineResolution(width, height)
    }

    fun updateVideoDimensions(width: Int, height: Int) {
        updatePipelineResolution(width, height)
    }

    private fun updatePipelineResolution(width: Int, height: Int) {
        val inputRes = when {
            height >= 2160 || width >= 3840 -> "4K / 8K"
            height >= 1440 || width >= 2560 -> "1440p"
            height >= 1080 || width >= 1920 -> "1080p"
            height >= 720 || width >= 1280 -> "720p"
            height >= 480 -> "480p (SD)"
            height > 0 -> "${height}p"
            else -> "1080p"
        }

        val is4kNative = (height >= 2160 || width >= 3840)

        // Select pipeline according to Auto logic
        val activePipeline = when (_config.value.preset) {
            VideoEnhancementPreset.OFF -> "Off (Native Passthrough)"
            VideoEnhancementPreset.QUALITY -> "ArtCNN C4F16 + CfL + SSimSuperRes"
            VideoEnhancementPreset.PERFORMANCE -> "FSRCNNX ×2 (Fast Performance)"
            VideoEnhancementPreset.ANIME -> "Anime4K Line Thinning + Chroma Bilateral"
            VideoEnhancementPreset.LIVE_ACTION -> "ArtCNN C4F16 2× Live-Action HD"
            VideoEnhancementPreset.AUTO -> {
                if (is4kNative) {
                    "Native 4K Clarity (Deband & SSimRes Passthrough)"
                } else if (_config.value.animeMode == AnimeDetectionMode.ALWAYS_ON || (_config.value.animeMode == AnimeDetectionMode.AUTO && isCurrentAnime)) {
                    "Anime4K / Ani4K Intelligent Upscaler"
                } else if (height in 1..719) {
                    "RAVU-Zoom SD Directional Scaler"
                } else {
                    "ArtCNN C4F16 2× Neural HD Scaler"
                }
            }
        }

        val targetRes = if (is4kNative) "4K (Native)" else "4K Ultra-Enhanced"

        _telemetry.value = _telemetry.value.copy(
            activePipelineName = activePipeline,
            inputResolution = inputRes,
            upscaledResolution = targetRes,
            isAnimeDetected = isCurrentAnime,
            isNative4kPassthrough = is4kNative,
            activePassesCount = if (_config.value.isEnabled) (if (_config.value.gpuPerformanceMode) 2 else 4) else 0
        )
    }

    fun setEnabled(enabled: Boolean) {
        updateConfig { it.copy(isEnabled = enabled) }
    }

    fun setPreset(preset: VideoEnhancementPreset) {
        val newConfig = when (preset) {
            VideoEnhancementPreset.OFF -> _config.value.copy(isEnabled = false, preset = VideoEnhancementPreset.OFF)
            VideoEnhancementPreset.AUTO -> _config.value.copy(
                isEnabled = true,
                preset = VideoEnhancementPreset.AUTO,
                upscalerEngine = UpscalerEngine.AUTO,
                sharpen = 35f,
                deband = 25f,
                denoise = 15f,
                chromaReconstructionCfL = true,
                antiRinging = true,
                gpuPerformanceMode = false
            )
            VideoEnhancementPreset.QUALITY -> _config.value.copy(
                isEnabled = true,
                preset = VideoEnhancementPreset.QUALITY,
                upscalerEngine = UpscalerEngine.ART_CNN_C4F16,
                sharpen = 50f,
                deband = 40f,
                denoise = 20f,
                chromaReconstructionCfL = true,
                antiRinging = true,
                gpuPerformanceMode = false
            )
            VideoEnhancementPreset.PERFORMANCE -> _config.value.copy(
                isEnabled = true,
                preset = VideoEnhancementPreset.PERFORMANCE,
                upscalerEngine = UpscalerEngine.FSRCNNX_X2,
                sharpen = 25f,
                deband = 10f,
                denoise = 0f,
                chromaReconstructionCfL = false,
                antiRinging = false,
                gpuPerformanceMode = true
            )
            VideoEnhancementPreset.ANIME -> _config.value.copy(
                isEnabled = true,
                preset = VideoEnhancementPreset.ANIME,
                upscalerEngine = UpscalerEngine.ANIME4K,
                animeMode = AnimeDetectionMode.ALWAYS_ON,
                sharpen = 55f,
                deband = 30f,
                denoise = 20f,
                chromaReconstructionCfL = true,
                antiRinging = true,
                gpuPerformanceMode = false
            )
            VideoEnhancementPreset.LIVE_ACTION -> _config.value.copy(
                isEnabled = true,
                preset = VideoEnhancementPreset.LIVE_ACTION,
                upscalerEngine = UpscalerEngine.ART_CNN_C4F16,
                animeMode = AnimeDetectionMode.ALWAYS_OFF,
                sharpen = 30f,
                deband = 30f,
                denoise = 15f,
                chromaReconstructionCfL = true,
                antiRinging = true,
                gpuPerformanceMode = false
            )
        }
        _config.value = newConfig
        currentVideoId?.let { perVideoCache[it] = newConfig }
        updatePipelineResolution(0, 0)
    }

    fun setUpscalerEngine(engine: UpscalerEngine) {
        updateConfig { it.copy(upscalerEngine = engine) }
    }

    fun setAnimeMode(mode: AnimeDetectionMode) {
        updateConfig { it.copy(animeMode = mode) }
    }

    fun setSharpen(value: Float) {
        updateConfig { it.copy(sharpen = value.coerceIn(0f, 100f)) }
    }

    fun setDeband(value: Float) {
        updateConfig { it.copy(deband = value.coerceIn(0f, 100f)) }
    }

    fun setDenoise(value: Float) {
        updateConfig { it.copy(denoise = value.coerceIn(0f, 100f)) }
    }

    fun setChromaReconstruction(enabled: Boolean) {
        updateConfig { it.copy(chromaReconstructionCfL = enabled) }
    }

    fun setAntiRinging(enabled: Boolean) {
        updateConfig { it.copy(antiRinging = enabled) }
    }

    fun setGpuPerformanceMode(enabled: Boolean) {
        updateConfig { it.copy(gpuPerformanceMode = enabled) }
    }

    fun setAutoGpuSafety(enabled: Boolean) {
        updateConfig { it.copy(autoGpuSafety = enabled) }
    }

    fun toggleDebugHud() {
        updateConfig { it.copy(showDebugHud = !it.showDebugHud) }
    }

    /**
     * GPU cadence callback to check frame rates and safely downgrade shaders if dropped frames rise.
     */
    fun onRenderFrame() {
        frameCountSinceLastCheck++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsCheckTime
        if (elapsed >= 1000) {
            val fps = (frameCountSinceLastCheck * 1000f) / elapsed
            frameCountSinceLastCheck = 0
            lastFpsCheckTime = now

            val gpuState = when {
                fps >= 55f -> GpuSafetyState.OPTIMAL
                fps >= 45f -> GpuSafetyState.BALANCED
                fps >= 35f -> GpuSafetyState.HIGH_LOAD
                else -> GpuSafetyState.THROTTLED_DOWNGRADED
            }

            // Auto GPU safety downgrade if FPS is suffering
            if (_config.value.autoGpuSafety && fps < 40f && !_config.value.gpuPerformanceMode) {
                totalDroppedFrames += 5
                _config.value = _config.value.copy(
                    gpuPerformanceMode = true,
                    sharpen = (_config.value.sharpen * 0.75f).coerceAtLeast(15f)
                )
            }

            _telemetry.value = _telemetry.value.copy(
                currentFps = fps,
                gpuSafetyState = gpuState,
                droppedFramesCount = totalDroppedFrames
            )
        }
    }

    fun updateConfig(block: (VideoEnhancementConfig) -> VideoEnhancementConfig) {
        val updated = block(_config.value)
        _config.value = updated
        currentVideoId?.let { perVideoCache[it] = updated }
        updatePipelineResolution(0, 0)
    }
}
