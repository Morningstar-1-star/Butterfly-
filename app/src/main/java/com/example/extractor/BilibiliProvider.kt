package com.example.extractor

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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object BilibiliProvider {
    private const val TAG = "BilibiliProvider"
    const val PROVIDER_ID = "bilibili"

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val REFERER = "https://www.bilibili.com/"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        if (cleanInput.isBlank()) return@withContext null

        try {
            var targetUrl = cleanInput
            // Follow short link if b23.tv
            if (targetUrl.contains("b23.tv", ignoreCase = true)) {
                try {
                    val req = Request.Builder()
                        .url(if (targetUrl.startsWith("http")) targetUrl else "https://$targetUrl")
                        .header("User-Agent", USER_AGENT)
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        targetUrl = resp.request.url.toString()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed resolving b23.tv redirect: ${e.message}")
                }
            }

            // Extract BV id or AV id
            var bvid = ""
            var aid = ""

            val bvMatcher = Pattern.compile("(BV[a-zA-Z0-9]{10})", Pattern.CASE_INSENSITIVE).matcher(targetUrl)
            if (bvMatcher.find()) {
                bvid = bvMatcher.group(1) ?: ""
            }

            if (bvid.isBlank()) {
                val avMatcher = Pattern.compile("av(\\d+)", Pattern.CASE_INSENSITIVE).matcher(targetUrl)
                if (avMatcher.find()) {
                    aid = avMatcher.group(1) ?: ""
                }
            }

            if (bvid.isBlank() && aid.isBlank()) {
                if (targetUrl.startsWith("BV", ignoreCase = true)) {
                    bvid = targetUrl.substringBefore("?").substringBefore("/")
                } else if (targetUrl.startsWith("av", ignoreCase = true)) {
                    aid = targetUrl.substringAfter("av").substringBefore("?").substringBefore("/")
                }
            }

            if (bvid.isBlank() && aid.isBlank()) {
                Log.w(TAG, "Could not extract bvid or aid from $cleanInput")
                return@withContext null
            }

            // 1. Fetch Metadata from Bilibili Web API
            val metaUrl = if (bvid.isNotBlank()) {
                "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
            } else {
                "https://api.bilibili.com/x/web-interface/view?aid=$aid"
            }

            val metaReq = Request.Builder()
                .url(metaUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()

            val metaJsonStr = httpClient.newCall(metaReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            val metaJson = JSONObject(metaJsonStr)
            val code = metaJson.optInt("code", -1)
            if (code != 0) {
                Log.w(TAG, "Bilibili metadata API returned error code $code: ${metaJson.optString("message")}")
                return@withContext null
            }

            val dataObj = metaJson.optJSONObject("data") ?: return@withContext null
            val resolvedBvid = dataObj.optString("bvid", bvid)
            val resolvedAid = dataObj.optLong("aid", 0L)
            val title = dataObj.optString("title", "Bilibili Video")
            var pic = dataObj.optString("pic", "")
            if (pic.startsWith("//")) pic = "https:$pic"
            val desc = dataObj.optString("desc", "")
            val ownerObj = dataObj.optJSONObject("owner")
            val uploader = ownerObj?.optString("name", "Bilibili") ?: "Bilibili"
            val avatar = ownerObj?.optString("face", "")
            val statObj = dataObj.optJSONObject("stat")
            val viewCount = statObj?.optLong("view", 0L) ?: 0L
            val likeCount = statObj?.optLong("like", 0L) ?: 0L

            var cid = dataObj.optLong("cid", 0L)
            if (cid == 0L) {
                val pagesArr = dataObj.optJSONArray("pages")
                if (pagesArr != null && pagesArr.length() > 0) {
                    cid = pagesArr.optJSONObject(0)?.optLong("cid", 0L) ?: 0L
                }
            }

            if (cid == 0L) {
                Log.w(TAG, "Could not determine CID for $resolvedBvid")
                return@withContext null
            }

            val biliHeaders = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to REFERER
            )

            val streamOptions = mutableListOf<PlayableStreamOption>()

            // 2. Fetch DASH streams (Adaptive 1080p, 720p, 480p, 360p with AAC/M4A audio)
            try {
                val dashUrl = "https://api.bilibili.com/x/player/playurl?bvid=$resolvedBvid&cid=$cid&qn=80&fnval=16&fnver=0&fourk=1"
                val dashReq = Request.Builder()
                    .url(dashUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .build()

                val dashJsonStr = httpClient.newCall(dashReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!dashJsonStr.isNullOrBlank()) {
                    val playJson = JSONObject(dashJsonStr)
                    val playData = playJson.optJSONObject("data")
                    val dashObj = playData?.optJSONObject("dash")

                    if (dashObj != null) {
                        val audioArr = dashObj.optJSONArray("audio")
                        var bestAudioUrl = ""
                        var bestAudioId = 0

                        if (audioArr != null) {
                            for (i in 0 until audioArr.length()) {
                                val aItem = audioArr.optJSONObject(i) ?: continue
                                var aUrl = aItem.optString("baseUrl", aItem.optString("base_url", ""))
                                val aBackup = aItem.optJSONArray("backupUrl") ?: aItem.optJSONArray("backup_url")
                                if (aUrl.contains("mcdn") && aBackup != null && aBackup.length() > 0) {
                                    for (b in 0 until aBackup.length()) {
                                        val cand = aBackup.optString(b, "")
                                        if (cand.isNotBlank() && !cand.contains("mcdn")) {
                                            aUrl = cand
                                            break
                                        }
                                    }
                                }
                                val aId = aItem.optInt("id", 0)
                                if (aUrl.isNotBlank() && (bestAudioUrl.isBlank() || aId > bestAudioId)) {
                                    bestAudioUrl = aUrl
                                    bestAudioId = aId
                                }
                            }
                        }

                        val videoArr = dashObj.optJSONArray("video")
                        if (videoArr != null) {
                            for (i in 0 until videoArr.length()) {
                                val vItem = videoArr.optJSONObject(i) ?: continue
                                var vUrl = vItem.optString("baseUrl", vItem.optString("base_url", ""))
                                val vBackup = vItem.optJSONArray("backupUrl") ?: vItem.optJSONArray("backup_url")
                                if (vUrl.contains("mcdn") && vBackup != null && vBackup.length() > 0) {
                                    for (b in 0 until vBackup.length()) {
                                        val cand = vBackup.optString(b, "")
                                        if (cand.isNotBlank() && !cand.contains("mcdn")) {
                                            vUrl = cand
                                            break
                                        }
                                    }
                                }
                                if (vUrl.isBlank()) continue

                                val qnId = vItem.optInt("id", 0)
                                val width = vItem.optInt("width", 0)
                                val height = vItem.optInt("height", 0)
                                val frameRate = vItem.optString("frameRate", vItem.optString("frame_rate", "30"))
                                val codecs = vItem.optString("codecs", "")

                                val heightLabel = when (qnId) {
                                    120 -> "4K 2160p"
                                    116 -> "1080p 60fps"
                                    80 -> "1080p"
                                    64 -> "720p"
                                    32 -> "480p"
                                    16 -> "360p"
                                    else -> if (height > 0) "${height}p" else "Stream $qnId"
                                }

                                val fpsStr = if (frameRate.contains("60")) "60fps" else ""
                                val codecLabel = if (codecs.contains("avc", ignoreCase = true) || codecs.contains("h264", ignoreCase = true)) "H.264" else if (codecs.contains("hev", ignoreCase = true) || codecs.contains("h265", ignoreCase = true)) "HEVC" else if (codecs.contains("av01", ignoreCase = true)) "AV1" else "MP4"
                                val label = "$heightLabel $fpsStr Adaptive ($codecLabel)".replace("  ", " ").trim()

                                streamOptions.add(
                                    PlayableStreamOption(
                                        qualityLabel = label,
                                        format = "m4s",
                                        isMuxed = bestAudioUrl.isBlank(),
                                        videoUrl = vUrl,
                                        audioUrl = if (bestAudioUrl.isNotBlank()) bestAudioUrl else null,
                                        providerType = ProviderType.DIRECT,
                                        headers = biliHeaders,
                                        audioHeaders = biliHeaders
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching DASH playurl: ${e.message}")
            }

            // 3. Fetch Progressive Muxed Streams (fnval=0 fallback)
            try {
                val progUrl = "https://api.bilibili.com/x/player/playurl?bvid=$resolvedBvid&cid=$cid&qn=80&fnval=0&fnver=0"
                val progReq = Request.Builder()
                    .url(progUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .build()

                val progJsonStr = httpClient.newCall(progReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!progJsonStr.isNullOrBlank()) {
                    val playJson = JSONObject(progJsonStr)
                    val playData = playJson.optJSONObject("data")
                    val durlArr = playData?.optJSONArray("durl")

                    if (durlArr != null && durlArr.length() > 0) {
                        for (i in 0 until durlArr.length()) {
                            val dItem = durlArr.optJSONObject(i) ?: continue
                            val sUrl = dItem.optString("url", "")
                            if (sUrl.isNotBlank()) {
                                val quality = playData.optInt("quality", 80)
                                val qLabel = when (quality) {
                                    80 -> "1080p Progressive (MP4 Direct)"
                                    64 -> "720p Progressive (MP4 Direct)"
                                    32 -> "480p Progressive (MP4 Direct)"
                                    16 -> "360p Progressive (MP4 Direct)"
                                    else -> "Progressive Stream (MP4 Direct)"
                                }

                                streamOptions.add(
                                    0, // Add progressive at top for instant reliable playback
                                    PlayableStreamOption(
                                        qualityLabel = qLabel,
                                        format = "mp4",
                                        isMuxed = true,
                                        videoUrl = sUrl,
                                        providerType = ProviderType.DIRECT,
                                        headers = biliHeaders
                                    )
                                )
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching progressive playurl: ${e.message}")
            }

            if (streamOptions.isEmpty()) {
                Log.w(TAG, "No playable streams extracted directly for Bilibili $resolvedBvid")
                return@withContext null
            }

            val distinctOptions = streamOptions.distinctBy { it.qualityLabel }
            val selectedOption = distinctOptions.firstOrNull { it.isMuxed && it.qualityLabel.contains("1080p") }
                ?: distinctOptions.firstOrNull { it.isMuxed && it.qualityLabel.contains("720p") }
                ?: distinctOptions.firstOrNull { it.isMuxed }
                ?: distinctOptions.firstOrNull { it.qualityLabel.contains("1080p") }
                ?: distinctOptions.firstOrNull { it.qualityLabel.contains("720p") }
                ?: distinctOptions.first()

            Log.i(TAG, "Bilibili direct extraction success: ${distinctOptions.size} formats, selected '${selectedOption.qualityLabel}'")

            StreamData(
                videoId = "https://www.bilibili.com/video/$resolvedBvid",
                videoUrl = selectedOption.videoUrl ?: "",
                title = title,
                channelName = uploader,
                channelAvatarUrl = avatar,
                description = desc,
                thumbnailUrl = pic,
                viewCount = viewCount,
                likeCount = likeCount,
                availableStreamOptions = distinctOptions,
                selectedStreamOption = selectedOption,
                providerId = PROVIDER_ID,
                providerType = ProviderType.DIRECT,
                headers = selectedOption.headers
            )
        } catch (e: Exception) {
            Log.e(TAG, "Bilibili extraction failed: ${e.message}", e)
            null
        }
    }
}
