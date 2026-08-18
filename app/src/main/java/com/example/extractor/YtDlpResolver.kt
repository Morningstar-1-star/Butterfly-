package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object YtDlpResolver {
    private const val TAG = "YtDlpResolver"
    private val httpClient = OkHttpClient()

    fun prewarm(context: Context) {}

    fun isYtDlpSupportedUrl(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be") || url.length == 11
    }

    suspend fun extractStreamInfo(ctx: Context, targetUrl: String): YouTubeExtractorHelper.ExtractionResult = withContext(Dispatchers.IO) {
        try {
            val videoId = if (targetUrl.length == 11) targetUrl else targetUrl.substringAfter("v=").substringBefore("&")
            Log.i(TAG, "Running yt-dlp / Piped API fallback for videoId: $videoId")
            
            val apiUrl = "https://pipedapi.kavin.rocks/streams/$videoId"
            val req = Request.Builder().url(apiUrl).header("User-Agent", "Mozilla/5.0").build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext YouTubeExtractorHelper.ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.NETWORK_ERROR,
                        message = "Fallback API error: ${resp.code}",
                        rawExceptionName = "HttpError",
                        fullStackTrace = "",
                        urlOrId = targetUrl,
                        causeInfo = resp.message,
                        technicalFixSuggestion = "Check network."
                    )
                )
            }

            val bodyStr = resp.body?.string() ?: ""
            val json = JSONObject(bodyStr)
            val title = json.optString("title", "YouTube Video")
            val uploader = json.optString("uploader", "YouTube")
            val description = json.optString("description", "")
            val thumbnail = json.optString("thumbnailUrl", "")

            val videoStreams = json.optJSONArray("videoStreams") ?: JSONArray()
            val options = mutableListOf<PlayableStreamOption>()

            for (i in 0 until videoStreams.length()) {
                val vs = videoStreams.optJSONObject(i) ?: continue
                val urlStr = vs.optString("url", "")
                if (urlStr.isBlank()) continue
                val quality = vs.optString("quality", "720p")
                val format = vs.optString("format", "mp4")
                val isMuxed = vs.optBoolean("videoOnly", false) == false

                options.add(
                    PlayableStreamOption(
                        qualityLabel = "yt-dlp Fallback $quality ($format)",
                        format = format,
                        isMuxed = isMuxed,
                        videoUrl = urlStr,
                        providerType = ProviderType.DIRECT,
                        headers = mapOf("Referer" to "https://www.youtube.com/")
                    )
                )
            }

            if (options.isEmpty()) {
                return@withContext YouTubeExtractorHelper.ExtractionResult.Error(
                    ExtractorErrorDetails(
                        errorType = ExtractorErrorType.NO_PLAYABLE_STREAMS,
                        message = "No streams found via fallback API",
                        rawExceptionName = "NoStreams",
                        fullStackTrace = "",
                        urlOrId = targetUrl,
                        causeInfo = "Empty stream array",
                        technicalFixSuggestion = "Try another video."
                    )
                )
            }

            val bestOption = options.first()
            val streamData = StreamData(
                videoId = targetUrl,
                videoUrl = bestOption.videoUrl ?: "",
                title = title,
                channelName = uploader,
                description = description,
                thumbnailUrl = thumbnail,
                availableStreamOptions = options,
                selectedStreamOption = bestOption,
                providerId = "youtube",
                providerType = ProviderType.DIRECT
            )
            Log.i(TAG, "yt-dlp fallback success: selected ${bestOption.qualityLabel}")
            YouTubeExtractorHelper.ExtractionResult.Success(streamData)
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp fallback exception: ${e.message}", e)
            YouTubeExtractorHelper.ExtractionResult.Error(
                ExtractorErrorDetails(
                    errorType = ExtractorErrorType.NETWORK_ERROR,
                    message = e.message ?: "Fallback failed",
                    rawExceptionName = e.javaClass.simpleName,
                    fullStackTrace = e.stackTraceToString(),
                    urlOrId = targetUrl,
                    causeInfo = e.message,
                    technicalFixSuggestion = "Check connection."
                )
            )
        }
    }
}
