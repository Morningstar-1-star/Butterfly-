package com.example.effects

/**
 * Available color grading and atmosphere presets for video playback.
 */
enum class PresetFilter(val displayName: String, val description: String) {
    NONE("None", "Original unfiltered video"),
    VIVID("Vivid", "High saturation, punchy contrast and vibrant colors"),
    WARM_TONE("Warm Tone", "Golden hour warmth with boosted orange/yellow spectrum"),
    COOL_TONE("Cool Tone", "Crisp cyan/blue undertones for modern clean look"),
    SOFT_PASTEL("Soft Pastel", "Gentle lifted shadows with soft dreamy palette"),
    CINEMATIC("Cinematic", "Teal and orange Hollywood movie color grading"),
    DRAMATIC("Dramatic", "Deep shadows, high contrast and intense highlights"),
    NIGHT_MODE("Night Mode", "Dimmed highlights, reduced blue light, eye comfort"),
    NOSTALGIC("Nostalgic", "Vintage retro feel with subtle warm tint and faded blacks"),
    GHIBLI_STYLE("Ghibli Style", "Vibrant lush greens, bright sky blues and anime glow"),
    NEON_POP("Neon Pop", "Ultra-saturated electric neon styling"),
    DEEP_BLACK("Deep Black", "Crushed deep blacks for high-contrast OLED displays")
}

/**
 * Basic video image adjustments.
 * Ranges:
 * - brightness: -100..100 (default 0)
 * - contrast: -100..100 (default 0)
 * - saturation: -100..100 (default 0)
 * - hue: -180..180 (default 0)
 * - gamma: -100..100 (default 0)
 * - sharpness: 0..100 (default 0)
 */
data class BasicEffectsState(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val hue: Float = 0f,
    val gamma: Float = 0f,
    val sharpness: Float = 0f
) {
    fun isDefault(): Boolean =
        brightness == 0f && contrast == 0f && saturation == 0f &&
                hue == 0f && gamma == 0f && sharpness == 0f
}

/**
 * Advanced color grading adjustments.
 * Ranges: -100..100 for all sliders (default 0)
 */
data class ColorAdvancedEffectsState(
    val exposure: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val blacks: Float = 0f,
    val whites: Float = 0f,
    val vibrance: Float = 0f
) {
    fun isDefault(): Boolean =
        exposure == 0f && temperature == 0f && tint == 0f &&
                highlights == 0f && shadows == 0f && blacks == 0f &&
                whites == 0f && vibrance == 0f
}

/**
 * Deband filter parameters matching professional video player engines.
 */
data class DebandConfig(
    val enabled: Boolean = false,
    val iterations: Int = 1,
    val threshold: Float = 48f,
    val range: Float = 16f,
    val grain: Float = 32f
) {
    fun isDefault(): Boolean = !enabled
}

/**
 * Video enhancement and post-processing filters.
 */
data class EnhancementEffectsState(
    val denoise: Float = 0f,
    val deband: DebandConfig = DebandConfig(),
    val deinterlace: Boolean = false,
    val filmGrain: Float = 0f,
    val vignette: Float = 0f,
    val blur: Float = 0f
) {
    fun isDefault(): Boolean =
        denoise == 0f && deband.isDefault() && !deinterlace &&
                filmGrain == 0f && vignette == 0f && blur == 0f
}

/**
 * Root configuration model for all player video effects.
 */
data class VideoEffectsConfig(
    val isEnabled: Boolean = false,
    val selectedPreset: PresetFilter = PresetFilter.NONE,
    val basic: BasicEffectsState = BasicEffectsState(),
    val color: ColorAdvancedEffectsState = ColorAdvancedEffectsState(),
    val enhancement: EnhancementEffectsState = EnhancementEffectsState()
) {
    fun hasActiveEffects(): Boolean {
        if (!isEnabled) return false
        return selectedPreset != PresetFilter.NONE ||
                !basic.isDefault() ||
                !color.isDefault() ||
                !enhancement.isDefault()
    }
}
