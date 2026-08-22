package com.example.effects

import android.graphics.ColorMatrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPU-accelerated video effects math engine for real-time color grading and shader filters.
 */
object VideoEffectsEngine {

    /**
     * Preset configurations based on professional film color grading.
     */
    fun getPresetConfig(preset: PresetFilter): VideoEffectsConfig {
        return when (preset) {
            PresetFilter.NONE -> VideoEffectsConfig(
                isEnabled = false,
                selectedPreset = PresetFilter.NONE
            )

            PresetFilter.VIVID -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.VIVID,
                basic = BasicEffectsState(
                    brightness = 5f,
                    contrast = 20f,
                    saturation = 35f,
                    gamma = 8f,
                    sharpness = 25f
                ),
                color = ColorAdvancedEffectsState(
                    vibrance = 25f,
                    highlights = 10f,
                    shadows = -5f
                )
            )

            PresetFilter.WARM_TONE -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.WARM_TONE,
                basic = BasicEffectsState(
                    brightness = 2f,
                    contrast = 10f,
                    saturation = 15f
                ),
                color = ColorAdvancedEffectsState(
                    temperature = 40f,
                    tint = 5f,
                    highlights = 15f,
                    shadows = 5f
                )
            )

            PresetFilter.COOL_TONE -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.COOL_TONE,
                basic = BasicEffectsState(
                    brightness = 0f,
                    contrast = 12f,
                    saturation = 10f
                ),
                color = ColorAdvancedEffectsState(
                    temperature = -40f,
                    tint = -10f,
                    highlights = 8f
                )
            )

            PresetFilter.SOFT_PASTEL -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.SOFT_PASTEL,
                basic = BasicEffectsState(
                    brightness = 15f,
                    contrast = -15f,
                    saturation = -10f,
                    gamma = 15f
                ),
                color = ColorAdvancedEffectsState(
                    exposure = 10f,
                    temperature = 10f,
                    shadows = 25f,
                    blacks = 15f
                )
            )

            PresetFilter.CINEMATIC -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.CINEMATIC,
                basic = BasicEffectsState(
                    brightness = -2f,
                    contrast = 25f,
                    saturation = 15f,
                    sharpness = 20f
                ),
                color = ColorAdvancedEffectsState(
                    temperature = 15f,
                    tint = -10f,
                    highlights = -10f,
                    shadows = -15f,
                    blacks = -10f
                ),
                enhancement = EnhancementEffectsState(
                    vignette = 30f,
                    filmGrain = 15f
                )
            )

            PresetFilter.DRAMATIC -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.DRAMATIC,
                basic = BasicEffectsState(
                    brightness = -5f,
                    contrast = 45f,
                    saturation = -15f,
                    sharpness = 30f
                ),
                color = ColorAdvancedEffectsState(
                    exposure = -5f,
                    highlights = 20f,
                    shadows = -30f,
                    blacks = -25f
                ),
                enhancement = EnhancementEffectsState(
                    vignette = 40f
                )
            )

            PresetFilter.NIGHT_MODE -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.NIGHT_MODE,
                basic = BasicEffectsState(
                    brightness = -20f,
                    contrast = -10f,
                    saturation = -20f,
                    gamma = -10f
                ),
                color = ColorAdvancedEffectsState(
                    temperature = 35f,
                    highlights = -30f,
                    whites = -35f
                )
            )

            PresetFilter.NOSTALGIC -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.NOSTALGIC,
                basic = BasicEffectsState(
                    brightness = 5f,
                    contrast = 10f,
                    saturation = -25f,
                    gamma = 10f
                ),
                color = ColorAdvancedEffectsState(
                    temperature = 25f,
                    tint = 15f,
                    blacks = 20f,
                    shadows = 15f
                ),
                enhancement = EnhancementEffectsState(
                    filmGrain = 35f,
                    vignette = 25f
                )
            )

            PresetFilter.GHIBLI_STYLE -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.GHIBLI_STYLE,
                basic = BasicEffectsState(
                    brightness = 10f,
                    contrast = 15f,
                    saturation = 30f,
                    gamma = 8f,
                    sharpness = 15f
                ),
                color = ColorAdvancedEffectsState(
                    temperature = 10f,
                    tint = -8f,
                    vibrance = 35f,
                    highlights = 15f,
                    shadows = 10f
                )
            )

            PresetFilter.NEON_POP -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.NEON_POP,
                basic = BasicEffectsState(
                    brightness = 5f,
                    contrast = 35f,
                    saturation = 65f,
                    sharpness = 30f
                ),
                color = ColorAdvancedEffectsState(
                    vibrance = 50f,
                    highlights = 25f,
                    shadows = -20f,
                    blacks = -15f
                )
            )

            PresetFilter.DEEP_BLACK -> VideoEffectsConfig(
                isEnabled = true,
                selectedPreset = PresetFilter.DEEP_BLACK,
                basic = BasicEffectsState(
                    brightness = -5f,
                    contrast = 30f,
                    saturation = 10f
                ),
                color = ColorAdvancedEffectsState(
                    blacks = -45f,
                    shadows = -25f,
                    highlights = 5f
                )
            )
        }
    }

    /**
     * Computes the combined 4x5 ColorMatrix array for GPU rendering.
     */
    fun computeCombinedColorMatrix(config: VideoEffectsConfig): FloatArray {
        val finalMatrix = ColorMatrix()

        if (!config.isEnabled) {
            return finalMatrix.array
        }

        // 1. Basic Adjustments: Brightness & Exposure & Contrast
        val totalBrightness = config.basic.brightness + (config.color.exposure * 0.8f)
        val totalContrast = config.basic.contrast
        val totalGamma = config.basic.gamma

        // Contrast scale
        val contrastFactor = if (totalContrast >= 0) {
            1f + (totalContrast / 100f) * 1.5f
        } else {
            1f + (totalContrast / 100f) * 0.7f
        }

        // Gamma / Brightness offset
        val brightnessOffset = (totalBrightness / 100f) * 255f + (totalGamma / 100f) * 40f
        val contrastOffset = 128f * (1f - contrastFactor) + brightnessOffset

        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrastFactor, 0f, 0f, 0f, contrastOffset,
                0f, contrastFactor, 0f, 0f, contrastOffset,
                0f, 0f, contrastFactor, 0f, contrastOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        finalMatrix.postConcat(contrastMatrix)

        // 2. Saturation & Vibrance
        val totalSat = (config.basic.saturation + config.color.vibrance * 0.6f).coerceIn(-100f, 150f)
        val satFactor = if (totalSat >= 0) {
            1f + (totalSat / 100f) * 1.8f
        } else {
            1f + (totalSat / 100f)
        }
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(satFactor)
        finalMatrix.postConcat(satMatrix)

        // 3. Hue Rotation
        if (config.basic.hue != 0f) {
            val hueRad = (config.basic.hue * PI / 180f).toFloat()
            val cosVal = cos(hueRad)
            val sinVal = sin(hueRad)
            val lumR = 0.213f
            val lumG = 0.715f
            val lumB = 0.072f

            val hueMatrix = ColorMatrix(
                floatArrayOf(
                    lumR + cosVal * (1f - lumR) + sinVal * (-lumR),
                    lumG + cosVal * (-lumG) + sinVal * (-lumG),
                    lumB + cosVal * (-lumB) + sinVal * (1f - lumB),
                    0f, 0f,

                    lumR + cosVal * (-lumR) + sinVal * (0.143f),
                    lumG + cosVal * (1f - lumG) + sinVal * (0.140f),
                    lumB + cosVal * (-lumB) + sinVal * (-0.283f),
                    0f, 0f,

                    lumR + cosVal * (-lumR) + sinVal * (-(1f - lumR)),
                    lumG + cosVal * (-lumG) + sinVal * (lumG),
                    lumB + cosVal * (1f - lumB) + sinVal * (lumB),
                    0f, 0f,

                    0f, 0f, 0f, 1f, 0f
                )
            )
            finalMatrix.postConcat(hueMatrix)
        }

        // 4. Color Temperature (Warm/Cool) and Tint (Magenta/Green)
        val temp = config.color.temperature // -100 (Cool) to 100 (Warm)
        val tint = config.color.tint // -100 (Green) to 100 (Magenta)

        if (temp != 0f || tint != 0f) {
            val rScale = 1f + (temp / 100f) * 0.35f + (tint / 100f) * 0.15f
            val gScale = 1f - (tint / 100f) * 0.25f
            val bScale = 1f - (temp / 100f) * 0.35f + (tint / 100f) * 0.15f

            val tempTintMatrix = ColorMatrix(
                floatArrayOf(
                    rScale.coerceIn(0.2f, 2.0f), 0f, 0f, 0f, 0f,
                    0f, gScale.coerceIn(0.2f, 2.0f), 0f, 0f, 0f,
                    0f, 0f, bScale.coerceIn(0.2f, 2.0f), 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            finalMatrix.postConcat(tempTintMatrix)
        }

        // 5. Highlights, Shadows, Blacks, Whites
        val hl = config.color.highlights
        val sh = config.color.shadows
        val bl = config.color.blacks
        val wh = config.color.whites

        if (hl != 0f || sh != 0f || bl != 0f || wh != 0f) {
            val hlOffset = (hl / 100f) * 30f + (wh / 100f) * 30f
            val shOffset = (sh / 100f) * 30f + (bl / 100f) * 30f

            val levelsMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, hlOffset + shOffset,
                    0f, 1f, 0f, 0f, hlOffset + shOffset,
                    0f, 0f, 1f, 0f, hlOffset + shOffset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            finalMatrix.postConcat(levelsMatrix)
        }

        return finalMatrix.array
    }
}
