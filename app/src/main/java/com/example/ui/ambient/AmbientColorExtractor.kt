package com.example.ui.ambient

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class AmbientPalette(
    val primaryColor: Color = Color(0xFF1E2228),
    val secondaryColor: Color = Color(0xFF181B20),
    val topColor: Color = Color(0xFF1A1D22),
    val isValidColor: Boolean = false
)

object AmbientColorExtractor {

    private val cache = mutableMapOf<String, AmbientPalette>()

    suspend fun extractColors(
        context: Context,
        thumbnailUrl: String?,
        isDarkTheme: Boolean
    ): AmbientPalette {
        if (thumbnailUrl.isNullOrBlank()) {
            return getDefaultPalette(isDarkTheme)
        }

        val cacheKey = "${thumbnailUrl}_${if (isDarkTheme) "dark" else "light"}"
        cache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val imageLoader = ImageLoader.Builder(context)
                    .allowHardware(false) // Must be software bitmap to read pixels
                    .build()

                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(96, 64) // Good sampling resolution
                    .allowHardware(false)
                    .build()

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = analyzeBitmap(bitmap, isDarkTheme)
                        cache[cacheKey] = palette
                        return@withContext palette
                    }
                }
                getDefaultPalette(isDarkTheme)
            } catch (e: Exception) {
                getDefaultPalette(isDarkTheme)
            }
        }
    }

    private fun analyzeBitmap(bitmap: Bitmap, isDarkTheme: Boolean): AmbientPalette {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return getDefaultPalette(isDarkTheme)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val topPixels = mutableListOf<Int>()
        val bottomPixels = mutableListOf<Int>()
        val allPixels = mutableListOf<Int>()

        val topCutoff = (height * 0.28f).toInt().coerceAtLeast(1)
        val bottomCutoff = (height * 0.65f).toInt().coerceAtMost(height - 1)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val maxC = max(r, max(g, b))
                val brightness = maxC / 255f

                // Ignore pure black letterbox bars (below 6% brightness)
                if (brightness > 0.06f) {
                    allPixels.add(pixel)
                    if (y <= topCutoff) topPixels.add(pixel)
                    if (y >= bottomCutoff) bottomPixels.add(pixel)
                }
            }
        }

        if (allPixels.isEmpty()) {
            return getDefaultPalette(isDarkTheme)
        }

        val topColor = extractDominantVibrantColor(if (topPixels.isNotEmpty()) topPixels else allPixels, isDarkTheme, isTop = true)
        val primaryBottomColor = extractDominantVibrantColor(if (bottomPixels.isNotEmpty()) bottomPixels else allPixels, isDarkTheme, isTop = false)
        val secondaryBottomColor = extractHarmonicAccentColor(primaryBottomColor, isDarkTheme)

        return AmbientPalette(
            primaryColor = primaryBottomColor,
            secondaryColor = secondaryBottomColor,
            topColor = topColor,
            isValidColor = true
        )
    }

    /**
     * Extracts the authentic dominant vibrant color using Hue-space clustering.
     * Unlike simple RGB averaging (which blends opposing hues into muddy brown/gray),
     * Hue clustering identifies the genuine dominant color family present in the video.
     */
    private fun extractDominantVibrantColor(pixels: List<Int>, isDarkTheme: Boolean, isTop: Boolean): Color {
        if (pixels.isEmpty()) return getDefaultPalette(isDarkTheme).primaryColor

        // 18 Hue buckets of 20 degrees each (0° to 360°)
        val numBins = 18
        val binScores = FloatArray(numBins)
        val binPixelCounts = IntArray(numBins)
        val binR = DoubleArray(numBins)
        val binG = DoubleArray(numBins)
        val binB = DoubleArray(numBins)

        var totalNonBlackPixels = 0
        var totalGrayR = 0.0
        var totalGrayG = 0.0
        var totalGrayB = 0.0

        val hsv = FloatArray(3)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            android.graphics.Color.colorToHSV(pixel, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            totalNonBlackPixels++
            totalGrayR += r
            totalGrayG += g
            totalGrayB += b

            // Give high significance to vibrant, saturated pixels
            if (sat > 0.15f && value > 0.12f) {
                val binIndex = ((hue / 360f) * numBins).toInt().coerceIn(0, numBins - 1)
                val weight = (sat * sat * 3.0f) + (value * 1.2f)

                binScores[binIndex] += weight
                binPixelCounts[binIndex]++
                binR[binIndex] += r * weight
                binG[binIndex] += g * weight
                binB[binIndex] += b * weight
            }
        }

        // Find the most prominent vibrant hue bin
        var bestBin = -1
        var maxScore = 0f
        for (i in 0 until numBins) {
            if (binScores[i] > maxScore) {
                maxScore = binScores[i]
                bestBin = i
            }
        }

        // If video has vibrant colors, use the dominant cluster
        if (bestBin >= 0 && maxScore > 2.0f && binPixelCounts[bestBin] > 0) {
            val avgR = (binR[bestBin] / binScores[bestBin]).toInt().coerceIn(0, 255)
            val avgG = (binG[bestBin] / binScores[bestBin]).toInt().coerceIn(0, 255)
            val avgB = (binB[bestBin] / binScores[bestBin]).toInt().coerceIn(0, 255)

            val baseColor = android.graphics.Color.rgb(avgR, avgG, avgB)
            android.graphics.Color.colorToHSV(baseColor, hsv)

            if (isDarkTheme) {
                hsv[1] = hsv[1].coerceIn(0.55f, 0.95f) // Rich authentic saturation
                hsv[2] = if (isTop) hsv[2].coerceIn(0.28f, 0.58f) else hsv[2].coerceIn(0.35f, 0.68f) // Deep ambient glow
            } else {
                hsv[1] = (hsv[1] * 0.7f).coerceIn(0.25f, 0.55f)
                hsv[2] = hsv[2].coerceIn(0.78f, 0.95f)
            }
            return Color(android.graphics.Color.HSVToColor(hsv))
        }

        // Fallback for monochromatic / low-saturation scenes: authentic grayscale / slate tint
        val avgR = (totalGrayR / totalNonBlackPixels.coerceAtLeast(1)).toInt().coerceIn(0, 255)
        val avgG = (totalGrayG / totalNonBlackPixels.coerceAtLeast(1)).toInt().coerceIn(0, 255)
        val avgB = (totalGrayB / totalNonBlackPixels.coerceAtLeast(1)).toInt().coerceIn(0, 255)
        val fallbackBase = android.graphics.Color.rgb(avgR, avgG, avgB)
        android.graphics.Color.colorToHSV(fallbackBase, hsv)

        if (isDarkTheme) {
            hsv[2] = hsv[2].coerceIn(0.22f, 0.48f)
        } else {
            hsv[2] = hsv[2].coerceIn(0.80f, 0.94f)
        }
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /**
     * Derives a smooth harmonic accent tone for YouTube ambient depth.
     */
    private fun extractHarmonicAccentColor(primaryColor: Color, isDarkTheme: Boolean): Color {
        val primaryHsv = FloatArray(3)
        android.graphics.Color.colorToHSV(primaryColor.toArgb(), primaryHsv)

        // Shift hue slightly (+18 degrees) for rich cinematic gradient
        val shiftedHue = (primaryHsv[0] + 18f) % 360f
        val accentHsv = floatArrayOf(
            shiftedHue,
            (primaryHsv[1] * 0.90f).coerceIn(0.20f, 0.85f),
            if (isDarkTheme) (primaryHsv[2] * 0.80f).coerceIn(0.20f, 0.55f) else (primaryHsv[2] * 0.95f).coerceIn(0.70f, 0.92f)
        )

        return Color(android.graphics.Color.HSVToColor(accentHsv))
    }

    fun getDefaultPalette(isDarkTheme: Boolean): AmbientPalette {
        return if (isDarkTheme) {
            AmbientPalette(
                primaryColor = Color(0xFF1E242B),
                secondaryColor = Color(0xFF161A20),
                topColor = Color(0xFF181C22),
                isValidColor = false
            )
        } else {
            AmbientPalette(
                primaryColor = Color(0xFFE4E9F0),
                secondaryColor = Color(0xFFDCE2EB),
                topColor = Color(0xFFE8EDF5),
                isValidColor = false
            )
        }
    }
}
