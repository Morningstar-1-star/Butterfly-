package com.example.extractor.plugins

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Hanime Stream Extractor Plugin.
 * (Adapted from cynthia2006/hanime-plugin)
 */
class HanimeExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : ExtractorPlugin {

    override val id: String = "hanime"
    override val name: String = "Hanime Extractor"
    override val version: String = "1.0.8"
    override val isEnabled: Boolean = true

    override fun canHandle(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("hanime.tv")
    }

    override suspend fun extract(context: Context, url: String): StreamData? = withContext(Dispatchers.IO) {
        try {
            val slug = url.substringAfterLast("/").substringBefore("?")
            val apiUrl = "https://hw.hanime.tv/api/v8/video?id=$slug"

            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("X-Signature-Version", "app2")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val json = JSONObject(resp.body?.string() ?: "{}")
            val hentaiVideo = json.optJSONObject("hentai_video") ?: return@withContext null
            val title = hentaiVideo.optString("name", slug)
            val poster = hentaiVideo.optString("poster_url", null)
            val desc = hentaiVideo.optString("description", "")

            val streamsArr = json.optJSONArray("videos_manifest")?.optJSONObject(0)?.optJSONArray("servers")
                ?: json.optJSONArray("streams")

            val options = mutableListOf<PlayableStreamOption>()
            if (streamsArr != null) {
                for (i in 0 until streamsArr.length()) {
                    val sObj = streamsArr.optJSONObject(i) ?: continue
                    val streamUrl = sObj.optString("url", "")
                    val height = sObj.optString("height", "720")
                    if (streamUrl.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "${height}p HLS",
                                format = "m3u8",
                                isMuxed = true,
                                videoUrl = streamUrl,
                                providerType = ProviderType.DIRECT
                            )
                        )
                    }
                }
            }

            val bestOption = options.firstOrNull()
            if (bestOption != null) {
                return@withContext StreamData(
                    videoId = slug,
                    videoUrl = bestOption.videoUrl ?: "",
                    title = title,
                    channelName = "Hanime",
                    description = desc,
                    thumbnailUrl = poster,
                    availableStreamOptions = options,
                    selectedStreamOption = bestOption,
                    hlsUrl = bestOption.videoUrl,
                    providerId = id
                )
            }
        } catch (e: Exception) {
            Log.w("HanimeExtractor", "Hanime extraction error: ${e.message}")
        }
        null
    }
}
