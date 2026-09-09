package com.example.util

import android.util.Log
import com.example.model.VideoHeatmap
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

object HeatmapHelper {
    private const val TAG = "HeatmapHelper"

    /**
     * Normalizes a raw array of numerical activity points (0 to 100 or raw viewer pings)
     * into a smooth normalized [VideoHeatmap] (0.0f .. 1.0f).
     */
    fun fromNumericList(rawNumbers: List<Number>, durationMs: Long = 0L): VideoHeatmap? {
        if (rawNumbers.size < 4) return null
        return try {
            val floats = rawNumbers.map { it.toFloat() }
            val maxVal = floats.maxOrNull() ?: 1f
            val minVal = floats.minOrNull() ?: 0f
            val range = (maxVal - minVal).coerceAtLeast(0.001f)

            // Normalized to 0.0f..1.0f curve
            val normalized = floats.map { ((it - minVal) / range).coerceIn(0f, 1f) }

            // Find peak moment
            val peakIdx = floats.indexOf(maxVal)
            val peakFrac = if (floats.size > 1) peakIdx.toFloat() / (floats.size - 1).toFloat() else 0f
            val peakMs = (peakFrac * durationMs.toFloat()).toLong()

            VideoHeatmap(
                points = normalized,
                peakPositionMs = peakMs,
                peakFraction = peakFrac
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to normalize heatmap list: ${e.message}")
            null
        }
    }

    /**
     * Parses a JSONArray of numeric values (e.g. from Pornhub hotspots `[19834, 10036, ...]`).
     */
    fun fromJSONArray(jsonArr: JSONArray?, durationMs: Long = 0L): VideoHeatmap? {
        if (jsonArr == null || jsonArr.length() < 4) return null
        val numbers = mutableListOf<Float>()
        for (i in 0 until jsonArr.length()) {
            numbers.add(jsonArr.optDouble(i, 0.0).toFloat())
        }
        return fromNumericList(numbers, durationMs)
    }

    /**
     * Parses yt-dlp heatmap array:
     * [{"start_time": 0.0, "end_time": 10.0, "value": 0.3}, ...]
     */
    fun fromYtDlpHeatmap(rawHeatmap: JSONArray?, durationMs: Long = 0L): VideoHeatmap? {
        if (rawHeatmap == null || rawHeatmap.length() < 4) return null
        return try {
            val values = mutableListOf<Float>()
            var maxVal = -1f
            var peakTimeMs = 0L

            for (i in 0 until rawHeatmap.length()) {
                val item = rawHeatmap.optJSONObject(i)
                if (item != null) {
                    val v = item.optDouble("value", 0.0).toFloat()
                    val s = item.optDouble("start_time", 0.0)
                    val e = item.optDouble("end_time", s)
                    values.add(v)
                    if (v > maxVal) {
                        maxVal = v
                        peakTimeMs = ((s + e) / 2.0 * 1000.0).toLong()
                    }
                } else {
                    val v = rawHeatmap.optDouble(i, 0.0).toFloat()
                    values.add(v)
                }
            }

            if (values.size < 4) return null

            val high = values.maxOrNull() ?: 1f
            val low = values.minOrNull() ?: 0f
            val span = (high - low).coerceAtLeast(0.001f)
            val normalized = values.map { ((it - low) / span).coerceIn(0f, 1f) }

            val peakIdx = values.indexOf(high)
            val peakFrac = if (values.size > 1) peakIdx.toFloat() / (values.size - 1).toFloat() else 0f
            if (peakTimeMs <= 0L && durationMs > 0L) {
                peakTimeMs = (peakFrac * durationMs.toFloat()).toLong()
            }

            VideoHeatmap(
                points = normalized,
                peakPositionMs = peakTimeMs,
                peakFraction = peakFrac
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing yt-dlp heatmap: ${e.message}")
            null
        }
    }

    /**
     * Regex scans HTML for hotspots array definitions:
     * `"hotspots":[123,456,...]` or `hotspots = [123,456,...]`
     */
    fun extractFromHtml(html: String, durationMs: Long = 0L): VideoHeatmap? {
        if (html.isBlank()) return null
        return try {
            val pattern = Pattern.compile("""(?:"hotspots"|hotspots)\s*[:=]\s*(\[[0-9,\s]+\])""", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val jsonStr = matcher.group(1) ?: return null
                val arr = JSONArray(jsonStr)
                fromJSONArray(arr, durationMs)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses YouTube heatMarkerRenderer array from InnerTube or watch endpoint.
     */
    fun fromYouTubeMarkers(markersArr: JSONArray?, durationMs: Long = 0L): VideoHeatmap? {
        if (markersArr == null || markersArr.length() < 4) return null
        return try {
            val values = mutableListOf<Float>()
            var maxVal = -1f
            var peakTimeMs = 0L

            for (i in 0 until markersArr.length()) {
                val item = markersArr.optJSONObject(i) ?: continue
                val renderer = item.optJSONObject("heatMarkerRenderer") ?: item
                val intensity = renderer.optDouble(
                    "heatMarkerIntensityScoreNormalized",
                    renderer.optDouble("intensityScoreNormalized", -1.0)
                ).toFloat()
                val startMs = renderer.optLong("timeRangeStartMillis", 0L)
                val durMarkerMs = renderer.optLong("markerDurationMillis", 0L)

                if (intensity >= 0f) {
                    values.add(intensity)
                    if (intensity > maxVal) {
                        maxVal = intensity
                        peakTimeMs = startMs + (durMarkerMs / 2L)
                    }
                }
            }

            if (values.size < 4) return null
            val peakIdx = values.indexOf(maxVal)
            val peakFrac = if (values.size > 1) peakIdx.toFloat() / (values.size - 1).toFloat() else 0f
            if (peakTimeMs <= 0L && durationMs > 0L) {
                peakTimeMs = (peakFrac * durationMs.toFloat()).toLong()
            }

            VideoHeatmap(
                points = values,
                peakPositionMs = peakTimeMs,
                peakFraction = peakFrac
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing YouTube markers: ${e.message}")
            null
        }
    }
}
