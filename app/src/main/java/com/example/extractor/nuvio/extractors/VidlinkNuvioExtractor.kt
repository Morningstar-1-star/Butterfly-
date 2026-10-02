package com.example.extractor.nuvio.extractors

import android.util.Log
import com.example.extractor.nuvio.NuvioStreamResult
import com.example.extractor.nuvio.NuvioSubtitle
import com.example.extractor.tmdbembed.TMDBMediaRequest
import com.example.extractor.tmdbembed.extractors.VidlinkExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VidlinkNuvioExtractor {
    private const val TAG = "VidlinkNuvio"
    private const val PROVIDER_ID = "vidlink"

    suspend fun extract(request: TMDBMediaRequest): List<NuvioStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NuvioStreamResult>()
        try {
            val streams = VidlinkExtractor.extract(request)
            for (s in streams) {
                results.add(
                    NuvioStreamResult(
                        name = "Vidlink Pro",
                        title = s.title.ifBlank { "${request.title} (${s.quality})" },
                        url = s.url,
                        quality = s.quality.ifBlank { "1080p" },
                        formattedSize = s.sizeText,
                        headers = s.headers,
                        subtitles = s.subtitles.map { NuvioSubtitle(it.url, it.languageCode, it.languageName) },
                        isHls = s.isHls,
                        providerId = PROVIDER_ID
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vidlink Nuvio extraction error: ${e.message}")
        }
        return@withContext results
    }
}
