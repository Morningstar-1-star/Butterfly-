package com.example.extractor.plugins

import android.content.Context
import android.util.Log
import com.example.extractor.Hanime1Provider
import com.example.model.StreamData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hanime Stream Extractor Plugin.
 * Delegates to Hanime1Provider for unified, multi-stage stream resolution.
 */
class HanimeExtractor : ExtractorPlugin {

    override val id: String = "hanime"
    override val name: String = "Hanime Extractor"
    override val version: String = "1.1.0"
    override val isEnabled: Boolean = true

    override fun canHandle(url: String): Boolean {
        val u = url.lowercase().trim()
        return u.contains("hanime.tv") || u.contains("hanime1") ||
                u.startsWith("hanimetv:") || u.startsWith("hanime1:") || u.startsWith("hanime:")
    }

    override suspend fun extract(context: Context, url: String): StreamData? = withContext(Dispatchers.IO) {
        try {
            Hanime1Provider.getStreamData(url, context)
        } catch (e: Exception) {
            Log.w("HanimeExtractor", "Hanime extraction error: ${e.message}")
            null
        }
    }
}
