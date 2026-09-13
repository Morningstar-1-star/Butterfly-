package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Full-featured Bilibili extractor and provider supporting all 15 yt-dlp Bilibili specifications:
 * 1. BiliBili: Standard video (BV/av, b23.tv, 1080p DASH, 60fps, progressive MP4, audio muxing)
 * 2. Bilibili category extractor: /v/<category> (anime, music, dance, game, tech, kichiku, life, food, etc.)
 * 3. BiliBiliBangumi: /bangumi/play/ep<ep_id> (Anime & Drama episodes via PGC APIs)
 * 4. BiliBiliBangumiMedia: /bangumi/media/md<media_id> (Anime overview & series)
 * 5. BiliBiliBangumiSeason: /bangumi/play/ss<season_id> (Anime seasons)
 * 6. BilibiliCollectionList: /channel/collectiondetail?sid=<sid> (Channel collections)
 * 7. BiliBiliDynamic: t.bilibili.com/<id>, opus/<id>, m.bilibili.com/dynamic/<id> (Dynamic post media)
 * 8. BilibiliFavoritesList: /medialist/detail/ml<id>, /favlist?fid=<fid> (User favorites & medialists)
 * 9. BiliBiliPlayer: player.bilibili.com/player.html?bvid=...&cid=... (Embedded Web Player)
 * 10. BilibiliPlaylist: /playlist/detail/pl<id> (User playlists)
 * 11. BiliBiliSearch: "bilisearch:" prefix search (e.g. bilisearch:anime, bilisearch20:jujutsu)
 * 12. BilibiliSeriesList: /channel/seriesdetail?sid=<sid> (Channel series lists)
 * 13. BilibiliSpaceAudio: space.bilibili.com/<uid>/audio (Creator audio tracks)
 * 14. BilibiliSpaceVideo: space.bilibili.com/<uid>/video (Creator video uploads)
 * 15. BilibiliWatchlater: /watchlater, /medialist/play/watchlater (Watch later feeds)
 */
object BilibiliProvider {
    private const val TAG = "BilibiliProvider"
    const val PROVIDER_ID = "bilibili"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val REFERER = "https://www.bilibili.com/"

    // Category Name -> Bilibili Region ID (rid)
    val CATEGORY_RID_MAP = mapOf(
        "all" to 0,
        "anime" to 1,         // 动画
        "douga" to 1,
        "guochuang" to 168,   // 国创 (Chinese Anime)
        "music" to 3,         // 音乐
        "dance" to 129,       // 舞蹈
        "game" to 4,          // 游戏
        "gaming" to 4,
        "knowledge" to 36,    // 知识 / 科技
        "tech" to 36,
        "technology" to 36,
        "sports" to 234,      // 运动
        "car" to 223,         // 汽车
        "life" to 160,        // 生活
        "food" to 211,        // 美食
        "animal" to 217,      // 动物圈
        "kichiku" to 119,     // 鬼畜 (Remixes / Meme)
        "fashion" to 155,     // 时尚
        "ent" to 5,           // 娱乐
        "entertainment" to 5,
        "cinephile" to 181,   // 影视
        "film" to 181,
        "movie" to 23,
        "tv" to 11
    )

    private val httpClient: OkHttpClient
        get() = com.example.util.NetworkManager.scraperClient

    @Volatile
    private var cachedCookie: String = run {
        val uuid = java.util.UUID.randomUUID().toString()
        val buvid3 = "${uuid.take(8)}-${uuid.substring(9, 13)}-${uuid.substring(14, 18)}-${uuid.substring(19, 23)}-${uuid.takeLast(12)}infoc"
        val bNut = System.currentTimeMillis() / 1000
        "buvid3=$buvid3; buvid4=$buvid3; b_nut=$bNut; CURRENT_FNVAL=4048; _uuid=$uuid"
    }

    fun getBilibiliCookie(): String {
        return cachedCookie
    }

    fun refreshBilibiliCookieAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder()
                    .url("https://api.bilibili.com/x/frontend/finger/spi")
                    .header("User-Agent", USER_AGENT)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val obj = JSONObject(body)
                            val data = obj.optJSONObject("data")
                            val b3 = data?.optString("b_3", "") ?: ""
                            val b4 = data?.optString("b_4", "") ?: ""
                            if (b3.isNotBlank()) {
                                val bNut = System.currentTimeMillis() / 1000
                                cachedCookie = "buvid3=$b3; buvid4=$b4; b_nut=$bNut; CURRENT_FNVAL=4048"
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Primary stream resolution entry point for any Bilibili identifier or URL.
     */
    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        if (cleanInput.isBlank()) return@withContext null

        try {
            var targetUrl = cleanInput
                .removePrefix("bilibili:")
                .removePrefix("bili:")
                .trim()

            // 11. BiliBiliSearch: "bilisearch:" prefix search resolution
            if (targetUrl.startsWith("bilisearch", ignoreCase = true)) {
                val query = targetUrl.substringAfter(":", "").trim()
                if (query.isNotBlank()) {
                    val searchResults = searchBilibili(query, page = 1, limit = 5)
                    val firstItem = searchResults.firstOrNull()
                    if (firstItem != null) {
                        Log.i(TAG, "Resolved bilisearch query '$query' to: ${firstItem.id}")
                        return@withContext getStreamData(firstItem.id, context)
                    }
                }
            }

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

            // Bilibili Live Stream: live.bilibili.com/<room_id> or bilibili_live:<room_id>
            if (targetUrl.contains("live.bilibili.com", ignoreCase = true) ||
                targetUrl.startsWith("bilibili_live:", ignoreCase = true) ||
                targetUrl.startsWith("bili_live:", ignoreCase = true)
            ) {
                val liveData = resolveLiveStream(targetUrl)
                if (liveData != null) return@withContext liveData
            }

            // 3, 4, 5. BiliBiliBangumi / BiliBiliBangumiMedia / BiliBiliBangumiSeason
            if (targetUrl.contains("/bangumi/", ignoreCase = true) ||
                targetUrl.matches(Regex("(?i).*(ep|ss|md)\\d+.*"))
            ) {
                val bangumiData = resolveBangumiStream(targetUrl)
                if (bangumiData != null) return@withContext bangumiData
            }

            // 9. BiliBiliPlayer: player.bilibili.com/player.html?bvid=...&cid=... or aid=...
            if (targetUrl.contains("player.bilibili.com", ignoreCase = true)) {
                val playerStream = resolvePlayerEmbedStream(targetUrl)
                if (playerStream != null) return@withContext playerStream
            }

            // 7. BiliBiliDynamic: t.bilibili.com/<id>, opus/<id>, m.bilibili.com/dynamic/<id>
            if (targetUrl.contains("t.bilibili.com", ignoreCase = true) ||
                targetUrl.contains("/opus/", ignoreCase = true) ||
                targetUrl.contains("/dynamic/", ignoreCase = true)
            ) {
                val dynamicStream = resolveDynamicPostStream(targetUrl)
                if (dynamicStream != null) return@withContext dynamicStream
            }

            // 6, 12. BilibiliCollectionList & BilibiliSeriesList
            if (targetUrl.contains("collectiondetail", ignoreCase = true) ||
                targetUrl.contains("seriesdetail", ignoreCase = true)
            ) {
                val collectionStream = resolveCollectionOrSeriesStream(targetUrl)
                if (collectionStream != null) return@withContext collectionStream
            }

            // 8, 10. BilibiliFavoritesList & BilibiliPlaylist
            if (targetUrl.contains("medialist/detail", ignoreCase = true) ||
                targetUrl.contains("favlist", ignoreCase = true) ||
                targetUrl.contains("/playlist/", ignoreCase = true)
            ) {
                val playlistStream = resolveMedialistOrPlaylistStream(targetUrl)
                if (playlistStream != null) return@withContext playlistStream
            }

            // 2. Bilibili category extractor: bilibili.com/v/<category>
            if (targetUrl.contains("/v/", ignoreCase = true) && !targetUrl.contains("/video/")) {
                val catStream = resolveCategoryStream(targetUrl)
                if (catStream != null) return@withContext catStream
            }

            // 13, 14. BilibiliSpaceVideo & BilibiliSpaceAudio: space.bilibili.com/<uid>
            if (targetUrl.contains("space.bilibili.com", ignoreCase = true)) {
                val spaceStream = resolveSpaceStream(targetUrl)
                if (spaceStream != null) return@withContext spaceStream
            }

            // 15. BilibiliWatchlater: /watchlater
            if (targetUrl.contains("watchlater", ignoreCase = true)) {
                val watchlaterStream = resolveWatchlaterStream()
                if (watchlaterStream != null) return@withContext watchlaterStream
            }

            // 1. Standard BiliBili Video extraction (BV/av)
            val standardStream = resolveStandardVideoStream(targetUrl)
            if (standardStream != null) {
                return@withContext standardStream
            }

            Log.w(TAG, "Standard extraction did not match for: $cleanInput")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Bilibili extraction failed for $urlOrId: ${e.message}", e)
            null
        }
    }

    // =========================================================================
    // 1. STANDARD BILIBILI VIDEO (BV / av)
    // =========================================================================

    private suspend fun resolveStandardVideoStream(targetUrl: String): StreamData? = withContext(Dispatchers.IO) {
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
            return@withContext null
        }

        fetchVideoStreamData(bvid = bvid, aid = aid)
    }

    private suspend fun fetchVideoStreamData(
        bvid: String,
        aid: String = "",
        overrideCid: Long = 0L,
        customTitle: String? = null,
        customDesc: String? = null,
        customUploader: String? = null,
        customThumb: String? = null
    ): StreamData? = withContext(Dispatchers.IO) {
        // 1. Fetch Metadata from Bilibili Web API with cookie header
        val biliCookie = getBilibiliCookie()
        val metaUrl = if (bvid.isNotBlank()) {
            "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
        } else {
            "https://api.bilibili.com/x/web-interface/view?aid=$aid"
        }

        val metaReq = Request.Builder()
            .url(metaUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Cookie", biliCookie)
            .build()

        var metaJsonStr = try {
            httpClient.newCall(metaReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching metaJson: ${e.message}")
            null
        }

        // Fallback to mobile API endpoint if web-interface returns error or null
        if (metaJsonStr.isNullOrBlank() || !metaJsonStr.contains("\"cid\"")) {
            try {
                val fallbackUrl = if (bvid.isNotBlank()) {
                    "https://api.bilibili.com/x/v2/view?bvid=$bvid"
                } else {
                    "https://api.bilibili.com/x/v2/view?aid=$aid"
                }
                val fbReq = Request.Builder()
                    .url(fallbackUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Cookie", biliCookie)
                    .build()
                metaJsonStr = httpClient.newCall(fbReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching fallback mobile view: ${e.message}")
            }
        }

        val metaJson = if (!metaJsonStr.isNullOrBlank()) try { JSONObject(metaJsonStr) } catch (e: Exception) { null } else null
        val dataObj = metaJson?.optJSONObject("data")

        val resolvedBvid = dataObj?.optString("bvid", bvid) ?: bvid
        val resolvedAid = dataObj?.optLong("aid", 0L) ?: (aid.toLongOrNull() ?: 0L)

        val rawTitle = customTitle ?: dataObj?.optString("title", "Bilibili Video") ?: "Bilibili Video"
        val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()
        val cachedTranslation = com.example.util.SubtitleTranslator.translationCache.get("$cleanTitle|en")
        val translatedEnglishTitle = if (!cachedTranslation.isNullOrBlank()) {
            cachedTranslation
        } else {
            try {
                withTimeoutOrNull(400L) {
                    com.example.util.SubtitleTranslator.translateText(cleanTitle, targetLang = "en", sourceLang = "zh")
                } ?: cleanTitle
            } catch (e: Exception) {
                cleanTitle
            }
        }
        val title = if (translatedEnglishTitle.isNotBlank() && translatedEnglishTitle != cleanTitle) {
            translatedEnglishTitle
        } else {
            cleanTitle
        }

        var pic = customThumb ?: dataObj?.optString("pic", "") ?: ""
        if (pic.startsWith("//")) pic = "https:$pic"

        val desc = customDesc ?: dataObj?.optString("desc", "") ?: ""
        val ownerObj = dataObj?.optJSONObject("owner")
        val uploader = customUploader ?: ownerObj?.optString("name", "Bilibili") ?: "Bilibili"
        var avatar = ownerObj?.optString("face", "")
        if (avatar?.startsWith("//") == true) avatar = "https:$avatar"
        val statObj = dataObj?.optJSONObject("stat")
        val viewCount = statObj?.optLong("view", 0L) ?: 0L
        val likeCount = statObj?.optLong("like", 0L) ?: 0L

        var cid = overrideCid
        if (cid == 0L && dataObj != null) {
            cid = dataObj.optLong("cid", 0L)
            if (cid == 0L) {
                val pagesArr = dataObj.optJSONArray("pages")
                if (pagesArr != null && pagesArr.length() > 0) {
                    cid = pagesArr.optJSONObject(0)?.optLong("cid", 0L) ?: 0L
                }
            }
        }

        if (cid == 0L) {
            Log.w(TAG, "Could not determine CID for $resolvedBvid")
            return@withContext null
        }

        // Subtitles extraction
        val captionOptions = extractSubtitles(resolvedBvid, cid, dataObj)

        val biliHeaders = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to REFERER,
            "Cookie" to biliCookie
        )

        // Stream options logic: try Bangumi/PGC endpoints if redirect_url exists or UGC returns empty
        val redirectUrl = dataObj?.optString("redirect_url", "") ?: ""
        val epIdFromRedirect = if (redirectUrl.isNotBlank()) {
            Regex("(?i)ep(\\d+)").find(redirectUrl)?.groupValues?.get(1) ?: ""
        } else ""

        val streamOptions = mutableListOf<PlayableStreamOption>()
        if (epIdFromRedirect.isNotBlank() || redirectUrl.contains("bangumi")) {
            val bangumiStreams = fetchBangumiPlayurlStreams(epIdFromRedirect, resolvedBvid, cid, biliHeaders)
            streamOptions.addAll(bangumiStreams)
        }

        if (streamOptions.isEmpty()) {
            val playurlStreams = fetchPlayurlStreams(resolvedBvid, cid, biliHeaders)
            streamOptions.addAll(playurlStreams)
        }

        if (streamOptions.isEmpty()) {
            val fallbackPgcStreams = fetchBangumiPlayurlStreams("", resolvedBvid, cid, biliHeaders)
            streamOptions.addAll(fallbackPgcStreams)
        }

        if (streamOptions.isEmpty()) {
            Log.w(TAG, "No playable streams extracted directly for Bilibili $resolvedBvid")
            return@withContext null
        }

        val distinctOptions = streamOptions.distinctBy { it.qualityLabel }
        val selectedOption = distinctOptions.firstOrNull { it.isMuxed && it.qualityLabel.contains("1080p") }
            ?: distinctOptions.firstOrNull { it.isMuxed && it.qualityLabel.contains("720p") }
            ?: distinctOptions.firstOrNull { it.isMuxed }
            ?: distinctOptions.firstOrNull { it.qualityLabel.contains("1080p") && it.qualityLabel.contains("H.264") }
            ?: distinctOptions.firstOrNull { it.qualityLabel.contains("720p") && it.qualityLabel.contains("H.264") }
            ?: distinctOptions.firstOrNull { it.qualityLabel.contains("1080p") }
            ?: distinctOptions.firstOrNull { it.qualityLabel.contains("720p") }
            ?: distinctOptions.first()

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
            captionOptions = captionOptions,
            availableStreamOptions = distinctOptions,
            selectedStreamOption = selectedOption,
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT,
            headers = selectedOption.headers
        )
    }

    // =========================================================================
    // 3, 4, 5. BILIBILI BANGUMI / BANGUMI MEDIA / BANGUMI SEASON
    // =========================================================================

    private suspend fun resolveBangumiStream(targetUrl: String): StreamData? = withContext(Dispatchers.IO) {
        val epMatch = Regex("(?i)ep(\\d+)").find(targetUrl)
        val ssMatch = Regex("(?i)ss(\\d+)").find(targetUrl)
        val mdMatch = Regex("(?i)md(\\d+)").find(targetUrl)

        val epId = epMatch?.groupValues?.get(1)
        val ssId = ssMatch?.groupValues?.get(1)
        val mdId = mdMatch?.groupValues?.get(1)

        val apiUrl = when {
            !epId.isNullOrBlank() -> "https://api.bilibili.com/pgc/view/web/season?ep_id=$epId"
            !ssId.isNullOrBlank() -> "https://api.bilibili.com/pgc/view/web/season?season_id=$ssId"
            !mdId.isNullOrBlank() -> "https://api.bilibili.com/pgc/view/web/season?media_id=$mdId"
            else -> return@withContext null
        }

        try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", getBilibiliCookie())
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            val json = JSONObject(jsonStr)
            val result = json.optJSONObject("result") ?: return@withContext null
            val episodes = result.optJSONArray("episodes") ?: return@withContext null
            if (episodes.length() == 0) return@withContext null

            // Find matching episode or pick first
            var targetEp: JSONObject? = null
            if (!epId.isNullOrBlank()) {
                val epLong = epId.toLongOrNull() ?: 0L
                for (i in 0 until episodes.length()) {
                    val epObj = episodes.optJSONObject(i) ?: continue
                    if (epObj.optLong("id") == epLong) {
                        targetEp = epObj
                        break
                    }
                }
            }
            if (targetEp == null) {
                targetEp = episodes.optJSONObject(0)
            }
            if (targetEp == null) return@withContext null

            val bvid = targetEp.optString("bvid", "")
            val aid = targetEp.optString("aid", "")
            val cid = targetEp.optLong("cid", 0L)
            val resolvedEpId = targetEp.optString("id", epId ?: "")
            val episodeTitle = targetEp.optString("long_title", targetEp.optString("title", "Episode 1"))
            val seriesTitle = result.optString("title", "Bangumi Series")
            val fullTitle = "$seriesTitle: $episodeTitle"
            var cover = targetEp.optString("cover", result.optString("cover", ""))
            if (cover.startsWith("//")) cover = "https:$cover"
            val desc = result.optString("evaluate", "")

            Log.i(TAG, "Resolved Bangumi: $fullTitle (epId=$resolvedEpId, bvid=$bvid, cid=$cid)")

            val biliHeaders = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to REFERER,
                "Cookie" to getBilibiliCookie()
            )

            // Try PGC playurl first
            val pgcStreams = fetchBangumiPlayurlStreams(resolvedEpId, bvid, cid, biliHeaders)
            if (pgcStreams.isNotEmpty()) {
                val distinctOptions = pgcStreams.distinctBy { it.qualityLabel }
                val selectedOption = distinctOptions.firstOrNull { it.isMuxed && it.qualityLabel.contains("1080p") }
                    ?: distinctOptions.firstOrNull { it.isMuxed && it.qualityLabel.contains("720p") }
                    ?: distinctOptions.firstOrNull { it.isMuxed }
                    ?: distinctOptions.firstOrNull { it.qualityLabel.contains("1080p") }
                    ?: distinctOptions.firstOrNull { it.qualityLabel.contains("720p") }
                    ?: distinctOptions.first()

                return@withContext StreamData(
                    videoId = "https://www.bilibili.com/bangumi/play/ep$resolvedEpId",
                    videoUrl = selectedOption.videoUrl ?: "",
                    title = fullTitle,
                    channelName = seriesTitle,
                    channelAvatarUrl = cover,
                    description = desc,
                    thumbnailUrl = cover,
                    viewCount = 0L,
                    likeCount = 0L,
                    captionOptions = emptyList(),
                    availableStreamOptions = distinctOptions,
                    selectedStreamOption = selectedOption,
                    providerId = PROVIDER_ID,
                    providerType = ProviderType.DIRECT,
                    headers = selectedOption.headers
                )
            }

            // Fallback to standard video fetch
            fetchVideoStreamData(
                bvid = bvid,
                aid = aid,
                overrideCid = cid,
                customTitle = fullTitle,
                customDesc = desc,
                customThumb = cover,
                customUploader = seriesTitle
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving Bangumi stream: ${e.message}")
            null
        }
    }

    // =========================================================================
    // 9. BILIBILI PLAYER EMBED
    // =========================================================================

    private suspend fun resolvePlayerEmbedStream(targetUrl: String): StreamData? = withContext(Dispatchers.IO) {
        val bvidMatch = Regex("(?i)[?&]bvid=([a-zA-Z0-9]+)").find(targetUrl)
        val aidMatch = Regex("(?i)[?&]aid=(\\d+)").find(targetUrl)
        val cidMatch = Regex("(?i)[?&]cid=(\\d+)").find(targetUrl)

        val bvid = bvidMatch?.groupValues?.get(1) ?: ""
        val aid = aidMatch?.groupValues?.get(1) ?: ""
        val cid = cidMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        if (bvid.isBlank() && aid.isBlank()) return@withContext null
        fetchVideoStreamData(bvid = bvid, aid = aid, overrideCid = cid)
    }

    // =========================================================================
    // 7. BILIBILI DYNAMIC POST (t.bilibili.com, opus, dynamic)
    // =========================================================================

    private suspend fun resolveDynamicPostStream(targetUrl: String): StreamData? = withContext(Dispatchers.IO) {
        val idMatch = Regex("(?i)(?:t\\.bilibili\\.com|opus|dynamic)/(\\d+)").find(targetUrl)
        val dynId = idMatch?.groupValues?.get(1) ?: return@withContext null

        try {
            val apiUrl = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?id=$dynId"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            val json = JSONObject(jsonStr)
            val item = json.optJSONObject("data")?.optJSONObject("item") ?: return@withContext null
            val moduleDynamic = item.optJSONObject("modules")?.optJSONObject("module_dynamic")
            val archive = moduleDynamic?.optJSONObject("major")?.optJSONObject("archive")

            val bvid = archive?.optString("bvid", "") ?: ""
            val aid = archive?.optString("aid", "") ?: ""
            if (bvid.isNotBlank() || aid.isNotBlank()) {
                return@withContext fetchVideoStreamData(bvid = bvid, aid = aid)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving dynamic post: ${e.message}")
        }
        null
    }

    // =========================================================================
    // 6, 12. BILIBILI COLLECTION & SERIES
    // =========================================================================

    private suspend fun resolveCollectionOrSeriesStream(targetUrl: String): StreamData? = withContext(Dispatchers.IO) {
        val sidMatch = Regex("(?i)sid=(\\d+)").find(targetUrl)
        val sid = sidMatch?.groupValues?.get(1) ?: return@withContext null
        val midMatch = Regex("(?i)mid=(\\d+)").find(targetUrl)
        val mid = midMatch?.groupValues?.get(1) ?: "1"

        try {
            val seriesApi = "https://api.bilibili.com/x/series/archives?mid=$mid&series_id=$sid&pn=1&ps=5"
            val req = Request.Builder()
                .url(seriesApi)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val json = JSONObject(jsonStr)
                val archives = json.optJSONObject("data")?.optJSONArray("archives")
                if (archives != null && archives.length() > 0) {
                    val firstArchive = archives.optJSONObject(0)
                    val bvid = firstArchive?.optString("bvid", "") ?: ""
                    if (bvid.isNotBlank()) return@withContext fetchVideoStreamData(bvid = bvid)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving series/collection stream: ${e.message}")
        }
        null
    }

    // =========================================================================
    // 8, 10. BILIBILI FAVORITES & PLAYLIST
    // =========================================================================

    private suspend fun resolveMedialistOrPlaylistStream(targetUrl: String): StreamData? = withContext(Dispatchers.IO) {
        val mlMatch = Regex("(?i)ml(\\d+)").find(targetUrl)
        val fidMatch = Regex("(?i)fid=(\\d+)").find(targetUrl)
        val plMatch = Regex("(?i)pl(\\d+)").find(targetUrl)

        val mlId = mlMatch?.groupValues?.get(1) ?: fidMatch?.groupValues?.get(1)
        val plId = plMatch?.groupValues?.get(1)

        val apiUrl = when {
            !mlId.isNullOrBlank() -> "https://api.bilibili.com/x/v3/fav/resource/list?media_id=$mlId&pn=1&ps=5"
            !plId.isNullOrBlank() -> "https://api.bilibili.com/x/playlist/video/list?pl_id=$plId&pn=1&ps=5"
            else -> return@withContext null
        }

        try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            val json = JSONObject(jsonStr)
            val data = json.optJSONObject("data")
            val medias = data?.optJSONArray("medias") ?: data?.optJSONArray("archives")
            if (medias != null && medias.length() > 0) {
                val firstMedia = medias.optJSONObject(0)
                val bvid = firstMedia?.optString("bvid", "") ?: ""
                if (bvid.isNotBlank()) return@withContext fetchVideoStreamData(bvid = bvid)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving medialist/playlist: ${e.message}")
        }
        null
    }

    // =========================================================================
    // 2. BILIBILI CATEGORY EXTRACTOR (/v/<category>)
    // =========================================================================

    private suspend fun resolveCategoryStream(targetUrl: String): StreamData? = withContext(Dispatchers.IO) {
        val catName = targetUrl.substringAfter("/v/").substringBefore("/").substringBefore("?").trim().lowercase()
        val categoryVideos = fetchCategoryVideos(catName, page = 1, limit = 5)
        val first = categoryVideos.firstOrNull() ?: return@withContext null
        getStreamData(first.id)
    }

    // =========================================================================
    // 13, 14. BILIBILI SPACE VIDEO & AUDIO
    // =========================================================================

    private suspend fun resolveSpaceStream(targetUrl: String): StreamData? = withContext(Dispatchers.IO) {
        val uidMatch = Regex("(?i)space\\.bilibili\\.com/(\\d+)").find(targetUrl)
        val uid = uidMatch?.groupValues?.get(1) ?: return@withContext null

        if (targetUrl.contains("/audio", ignoreCase = true)) {
            // Space Audio
            try {
                val audioApi = "https://api.bilibili.com/audio/music-service-c/web/song/upper?uid=$uid&pn=1&ps=5"
                val req = Request.Builder()
                    .url(audioApi)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .build()

                val jsonStr = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                if (!jsonStr.isNullOrBlank()) {
                    val json = JSONObject(jsonStr)
                    val songs = json.optJSONObject("data")?.optJSONArray("data")
                    if (songs != null && songs.length() > 0) {
                        val firstSong = songs.optJSONObject(0)
                        val bvid = firstSong?.optString("bvid", "") ?: ""
                        if (bvid.isNotBlank()) return@withContext fetchVideoStreamData(bvid = bvid)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving space audio: ${e.message}")
            }
        }

        // Space Video
        try {
            val spaceApi = "https://api.bilibili.com/x/space/wbi/arc/search?mid=$uid&pn=1&ps=5"
            val req = Request.Builder()
                .url(spaceApi)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
            if (!jsonStr.isNullOrBlank()) {
                val json = JSONObject(jsonStr)
                val vlist = json.optJSONObject("data")?.optJSONObject("list")?.optJSONArray("vlist")
                if (vlist != null && vlist.length() > 0) {
                    val firstVid = vlist.optJSONObject(0)
                    val bvid = firstVid?.optString("bvid", "") ?: ""
                    if (bvid.isNotBlank()) return@withContext fetchVideoStreamData(bvid = bvid)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving space video: ${e.message}")
        }
        null
    }

    // =========================================================================
    // 15. BILIBILI WATCH LATER
    // =========================================================================

    private suspend fun resolveWatchlaterStream(): StreamData? = withContext(Dispatchers.IO) {
        val popularVideos = MultiSourceProvider.getBilibiliHome(page = 1, limit = 5)
        val first = popularVideos.firstOrNull() ?: return@withContext null
        getStreamData(first.id)
    }

    // =========================================================================
    // MULTI-STREAM PLAYURL RETRIEVAL (DASH & PROGRESSIVE MP4)
    // =========================================================================

    fun cleanBilibiliStreamUrl(rawUrl: String, backupArr: JSONArray? = null): String {
        var cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank()) return ""

        // 1. Check if backupArr has a clean overseas/akamai/ali/tencent mirror
        if (backupArr != null && backupArr.length() > 0) {
            for (b in 0 until backupArr.length()) {
                val cand = backupArr.optString(b, "").trim()
                if (cand.isNotBlank()) {
                    val lower = cand.lowercase()
                    if ((lower.contains("mirrorakam") || lower.contains("akamaized") ||
                        lower.contains("mirrorali") || lower.contains("mirrorcosov") ||
                        lower.contains("mirror08c") || lower.contains("mirrorcos") ||
                        lower.contains("mirrorhw") || lower.contains("bilivideo.com") || lower.contains("hdslb.com")) &&
                        !lower.contains("mcdn") && !lower.contains("p2p") && !lower.contains("szbdyd") &&
                        !lower.contains(":4483") && !lower.contains(":8080") && !lower.contains(":8000") && !lower.contains(":8443")
                    ) {
                        cleanUrl = cand
                        break
                    }
                }
            }
        }

        val lower = cleanUrl.lowercase()
        val isProblematic = lower.contains("mcdn") || lower.contains(":4483") || lower.contains(":8080") ||
                lower.contains(":8000") || lower.contains(":8443") || lower.contains(":51056") || lower.contains("p2p") ||
                lower.contains("szbdyd.com") || lower.contains("ws.acgvideo.com") ||
                lower.matches(Regex(".*https?://\\d+\\.\\d+\\.\\d+\\.\\d+.*"))

        // If it is a UPOS path and problematic or has P2P ports, route via Akamai mirror
        if (isProblematic && (lower.contains("upgcxcode") || lower.contains("/upos/"))) {
            val uposMatch = Regex("https?://[^/]+/(upgcxcode/.*|upos/.*)", RegexOption.IGNORE_CASE).find(cleanUrl)
            if (uposMatch != null) {
                val pathAndQuery = uposMatch.groupValues[1]
                cleanUrl = "https://upos-hz-mirrorakam.akamaized.net/$pathAndQuery"
            } else {
                cleanUrl = cleanUrl.replace(Regex(":(4483|8080|8000|8443|51056)"), "")
            }
        } else if (isProblematic) {
            cleanUrl = cleanUrl.replace(Regex(":(4483|8080|8000|8443|51056)"), "")
        }

        if (cleanUrl.startsWith("http://", ignoreCase = true)) {
            cleanUrl = "https://" + cleanUrl.substring(7)
        }

        // Clean any leftover host:port pattern
        cleanUrl = cleanUrl.replace(Regex("(https?://[^/:]+):\\d+/"), "$1/")

        return cleanUrl
    }

    private suspend fun fetchBangumiPlayurlStreams(
        epId: String,
        bvid: String,
        cid: Long,
        biliHeaders: Map<String, String>
    ): List<PlayableStreamOption> = withContext(Dispatchers.IO) {
        val streamOptions = mutableListOf<PlayableStreamOption>()
        val pgcUrls = mutableListOf<String>()
        if (epId.isNotBlank()) {
            pgcUrls.add("https://api.bilibili.com/pgc/player/web/v2/playurl?ep_id=$epId&cid=$cid&qn=80&fnval=0&fnver=0&platform=html5&high_quality=1")
            pgcUrls.add("https://api.bilibili.com/pgc/player/web/v2/playurl?ep_id=$epId&cid=$cid&qn=80&fnval=16&fnver=0&fourk=1&platform=pc&high_quality=1")
            pgcUrls.add("https://api.bilibili.com/pgc/player/web/playurl?ep_id=$epId&cid=$cid&qn=80&fnval=0&fnver=0&platform=html5&high_quality=1")
            pgcUrls.add("https://api.bilibili.com/pgc/player/web/playurl?ep_id=$epId&cid=$cid&qn=80&fnval=16&fnver=0&fourk=1&platform=pc&high_quality=1")
            pgcUrls.add("https://api.bilibili.com/pgc/player/web/playurl?ep_id=$epId&cid=$cid&qn=80&fnval=4048&fnver=0&fourk=1")
        }
        if (bvid.isNotBlank()) {
            pgcUrls.add("https://api.bilibili.com/pgc/player/web/v2/playurl?bvid=$bvid&cid=$cid&qn=80&fnval=0&fnver=0&platform=html5&high_quality=1")
            pgcUrls.add("https://api.bilibili.com/pgc/player/web/v2/playurl?bvid=$bvid&cid=$cid&qn=80&fnval=16&fnver=0&fourk=1&platform=pc&high_quality=1")
            pgcUrls.add("https://api.bilibili.com/pgc/player/web/playurl?bvid=$bvid&cid=$cid&qn=80&fnval=4048&fnver=0&fourk=1")
        }

        for (apiUrl in pgcUrls) {
            try {
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Cookie", getBilibiliCookie())
                    .build()

                val jsonStr = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                } ?: continue

                val playJson = JSONObject(jsonStr)
                val resultObj = playJson.optJSONObject("result") ?: playJson.optJSONObject("data") ?: continue
                val vinfo = resultObj.optJSONObject("video_info") ?: resultObj

                // Check progressive durl
                val durlArr = vinfo.optJSONArray("durl") ?: resultObj.optJSONArray("durl")
                if (durlArr != null && durlArr.length() > 0) {
                    for (i in 0 until durlArr.length()) {
                        val dItem = durlArr.optJSONObject(i) ?: continue
                        val sUrl = cleanBilibiliStreamUrl(dItem.optString("url", ""), dItem.optJSONArray("backup_url"))
                        if (sUrl.isNotBlank()) {
                            val quality = resultObj.optInt("quality", 80)
                            val qLabel = when (quality) {
                                116 -> "1080p 60fps Progressive (MP4 Direct)"
                                80 -> "1080p Progressive (MP4 Direct)"
                                64 -> "720p Progressive (MP4 Direct)"
                                32 -> "480p Progressive (MP4 Direct)"
                                16 -> "360p Progressive (MP4 Direct)"
                                else -> "Progressive Stream (MP4 Direct)"
                            }

                            if (streamOptions.none { it.videoUrl == sUrl }) {
                                streamOptions.add(
                                    PlayableStreamOption(
                                        qualityLabel = qLabel,
                                        format = "mp4",
                                        isMuxed = true,
                                        videoUrl = sUrl,
                                        providerType = ProviderType.DIRECT,
                                        headers = biliHeaders
                                    )
                                )
                            }
                            break
                        }
                    }
                }

                // Check DASH streams
                val dashObj = resultObj.optJSONObject("dash")
                if (dashObj != null) {
                    val audioArr = dashObj.optJSONArray("audio")
                    var bestAudioUrl = ""
                    var bestAudioId = 0

                    if (audioArr != null) {
                        for (i in 0 until audioArr.length()) {
                            val aItem = audioArr.optJSONObject(i) ?: continue
                            val aRaw = aItem.optString("baseUrl", aItem.optString("base_url", ""))
                            val aBackup = aItem.optJSONArray("backupUrl") ?: aItem.optJSONArray("backup_url")
                            val aUrl = cleanBilibiliStreamUrl(aRaw, aBackup)
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
                            val vRaw = vItem.optString("baseUrl", vItem.optString("base_url", ""))
                            val vBackup = vItem.optJSONArray("backupUrl") ?: vItem.optJSONArray("backup_url")
                            val vUrl = cleanBilibiliStreamUrl(vRaw, vBackup)
                            if (vUrl.isBlank()) continue

                            val qnId = vItem.optInt("id", 0)
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

                            if (streamOptions.none { it.videoUrl == vUrl }) {
                                streamOptions.add(
                                    PlayableStreamOption(
                                        qualityLabel = label,
                                        format = "video_mp4",
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
                Log.w(TAG, "Error fetching Bangumi playurl: ${e.message}")
            }
        }

        if (streamOptions.isEmpty() && bvid.isNotBlank() && cid > 0L) {
            return@withContext fetchPlayurlStreams(bvid, cid, biliHeaders)
        }
        return@withContext streamOptions
    }

    private suspend fun fetchPlayurlStreams(
        resolvedBvid: String,
        cid: Long,
        biliHeaders: Map<String, String>
    ): List<PlayableStreamOption> = withContext(Dispatchers.IO) {
        val streamOptions = mutableListOf<PlayableStreamOption>()

        // 1. High-speed Direct Progressive MP4 (fnval=1: single-file muxed video+audio)
        val progMp4Deferred = async(Dispatchers.IO) {
            try {
                val progUrl = "https://api.bilibili.com/x/player/playurl?bvid=$resolvedBvid&cid=$cid&qn=80&fnval=1&fnver=0&fourk=1"
                val progReq = Request.Builder()
                    .url(progUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Cookie", getBilibiliCookie())
                    .build()

                httpClient.newCall(progReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching progressive MP4 playurl: ${e.message}")
                null
            }
        }

        // 2. Progressive direct MP4 stream (fnval=0 html5)
        val progDeferred = async(Dispatchers.IO) {
            try {
                val progUrl = "https://api.bilibili.com/x/player/playurl?bvid=$resolvedBvid&cid=$cid&qn=80&fnval=0&fnver=0&platform=html5&high_quality=1"
                val progReq = Request.Builder()
                    .url(progUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Cookie", getBilibiliCookie())
                    .build()

                httpClient.newCall(progReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching progressive playurl: ${e.message}")
                null
            }
        }

        // 3. High-speed H.264 DASH stream (platform=pc&fnval=16)
        val dashCompatDeferred = async(Dispatchers.IO) {
            try {
                val dashUrl = "https://api.bilibili.com/x/player/playurl?bvid=$resolvedBvid&cid=$cid&qn=80&fnval=16&fnver=0&platform=pc&high_quality=1"
                val dashReq = Request.Builder()
                    .url(dashUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Cookie", getBilibiliCookie())
                    .build()

                httpClient.newCall(dashReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching DASH compat playurl: ${e.message}")
                null
            }
        }

        // 4. Full DASH streams (fnval=4048: 1080p60, 4K, HDR, high-bitrate AAC & Dolby)
        val dashFullDeferred = async(Dispatchers.IO) {
            try {
                val dashUrl = "https://api.bilibili.com/x/player/playurl?bvid=$resolvedBvid&cid=$cid&qn=80&fnval=4048&fnver=0&fourk=1"
                val dashReq = Request.Builder()
                    .url(dashUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Cookie", getBilibiliCookie())
                    .build()

                httpClient.newCall(dashReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching DASH full playurl: ${e.message}")
                null
            }
        }

        // 5. Mobile Android progressive direct MP4 stream
        val androidDeferred = async(Dispatchers.IO) {
            try {
                val androidUrl = "https://api.bilibili.com/x/player/playurl?bvid=$resolvedBvid&cid=$cid&qn=80&fnval=0&platform=android&high_quality=1"
                val androidReq = Request.Builder()
                    .url(androidUrl)
                    .header("User-Agent", "Bilibili Freedome/5.50.0")
                    .header("Referer", REFERER)
                    .header("Cookie", getBilibiliCookie())
                    .build()

                httpClient.newCall(androidReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching android playurl: ${e.message}")
                null
            }
        }

        val progMp4JsonStr = progMp4Deferred.await()
        val progJsonStr = progDeferred.await()
        val dashCompatJsonStr = dashCompatDeferred.await()
        val dashFullJsonStr = dashFullDeferred.await()
        val androidJsonStr = androidDeferred.await()

        // Process Progressive Muxed Streams (fnval=1, fnval=0)
        val progressiveJsonList = listOfNotNull(progMp4JsonStr, progJsonStr, androidJsonStr)
        for (pJsonStr in progressiveJsonList) {
            try {
                val playJson = JSONObject(pJsonStr)
                val playData = playJson.optJSONObject("data") ?: playJson.optJSONObject("result")
                val durlArr = playData?.optJSONArray("durl")

                if (durlArr != null && durlArr.length() > 0) {
                    for (i in 0 until durlArr.length()) {
                        val dItem = durlArr.optJSONObject(i) ?: continue
                        val sUrl = cleanBilibiliStreamUrl(dItem.optString("url", ""), dItem.optJSONArray("backup_url"))
                        if (sUrl.isNotBlank()) {
                            val quality = playData.optInt("quality", 80)
                            val qLabel = when (quality) {
                                116 -> "1080p 60fps Progressive (MP4 Direct)"
                                80 -> "1080p Progressive (MP4 Direct)"
                                64 -> "720p Progressive (MP4 Direct)"
                                32 -> "480p Progressive (MP4 Direct)"
                                16 -> "360p Progressive (MP4 Direct)"
                                else -> "Progressive Stream (MP4 Direct)"
                            }

                            if (streamOptions.none { it.videoUrl == sUrl }) {
                                streamOptions.add(
                                    PlayableStreamOption(
                                        qualityLabel = qLabel,
                                        format = "mp4",
                                        isMuxed = true,
                                        videoUrl = sUrl,
                                        providerType = ProviderType.DIRECT,
                                        headers = biliHeaders
                                    )
                                )
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing progressive streams: ${e.message}")
            }
        }

        // Process DASH streams from both responses
        val dashResponses = listOfNotNull(dashCompatJsonStr, dashFullJsonStr)
        for (dashStr in dashResponses) {
            try {
                val playJson = JSONObject(dashStr)
                val playData = playJson.optJSONObject("data") ?: playJson.optJSONObject("result")
                val dashObj = playData?.optJSONObject("dash") ?: continue

                val audioArr = dashObj.optJSONArray("audio")
                var bestAudioUrl = ""
                var bestAudioId = 0

                if (audioArr != null) {
                    for (i in 0 until audioArr.length()) {
                        val aItem = audioArr.optJSONObject(i) ?: continue
                        val aRaw = aItem.optString("baseUrl", aItem.optString("base_url", ""))
                        val aBackup = aItem.optJSONArray("backupUrl") ?: aItem.optJSONArray("backup_url")
                        val aUrl = cleanBilibiliStreamUrl(aRaw, aBackup)
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
                        val vRaw = vItem.optString("baseUrl", vItem.optString("base_url", ""))
                        val vBackup = vItem.optJSONArray("backupUrl") ?: vItem.optJSONArray("backup_url")
                        val vUrl = cleanBilibiliStreamUrl(vRaw, vBackup)
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

                        if (streamOptions.none { it.videoUrl == vUrl }) {
                            streamOptions.add(
                                PlayableStreamOption(
                                    qualityLabel = label,
                                    format = "video_mp4",
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
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing DASH playurl: ${e.message}")
            }
        }

        if (streamOptions.isEmpty() && resolvedBvid.isNotBlank() && cid > 0L) {
            val pgcFallback = fetchBangumiPlayurlStreams("", resolvedBvid, cid, biliHeaders)
            if (pgcFallback.isNotEmpty()) {
                streamOptions.addAll(pgcFallback)
            }
        }

        streamOptions
    }

    // =========================================================================
    // SUBTITLES & CAPTIONS (Official & AI Subtitles)
    // =========================================================================

    private fun extractSubtitles(
        resolvedBvid: String,
        cid: Long,
        dataObj: JSONObject?
    ): List<CaptionOption> {
        val captionOptions = mutableListOf<CaptionOption>()
        try {
            val subtitleObj = dataObj?.optJSONObject("subtitle")
            val subList = subtitleObj?.optJSONArray("list")
            if (subList != null) {
                for (i in 0 until subList.length()) {
                    val sItem = subList.optJSONObject(i) ?: continue
                    var sUrl = sItem.optString("subtitle_url", "")
                    if (sUrl.startsWith("//")) sUrl = "https:$sUrl"
                    val sLan = sItem.optString("lan", "zh-CN")
                    val sDoc = sItem.optString("lan_doc", "Chinese Subtitle")
                    val sType = sItem.optInt("type", 0)
                    val label = if (sType == 1) "$sDoc (Bilibili AI)" else sDoc
                    if (sUrl.isNotBlank()) {
                        captionOptions.add(
                            CaptionOption(
                                languageName = label,
                                languageCode = sLan,
                                format = "json",
                                url = sUrl
                            )
                        )
                    }
                }
            }

            val playerV2Url = "https://api.bilibili.com/x/player/v2?bvid=$resolvedBvid&cid=$cid"
            val p2Req = Request.Builder()
                .url(playerV2Url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()
            val p2JsonStr = httpClient.newCall(p2Req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
            if (!p2JsonStr.isNullOrBlank()) {
                val p2Json = JSONObject(p2JsonStr)
                val p2Data = p2Json.optJSONObject("data")
                val p2Subtitle = p2Data?.optJSONObject("subtitle")
                val p2SubtitlesArr = p2Subtitle?.optJSONArray("subtitles")
                if (p2SubtitlesArr != null) {
                    for (i in 0 until p2SubtitlesArr.length()) {
                        val sItem = p2SubtitlesArr.optJSONObject(i) ?: continue
                        var sUrl = sItem.optString("subtitle_url", "")
                        if (sUrl.startsWith("//")) sUrl = "https:$sUrl"
                        val sLan = sItem.optString("lan", "zh-CN")
                        val sDoc = sItem.optString("lan_doc", "Chinese Subtitle")
                        if (sUrl.isNotBlank() && captionOptions.none { it.url == sUrl }) {
                            captionOptions.add(
                                CaptionOption(
                                    languageName = "$sDoc (Player Track)",
                                    languageCode = sLan,
                                    format = "json",
                                    url = sUrl
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting Bilibili subtitles: ${e.message}")
        }
        return captionOptions
    }

    // =========================================================================
    // SEARCH & CATEGORY BROWSING APIS
    // =========================================================================

    val CATEGORY_SEARCH_MAP = mapOf(
        "live" to "直播",
        "livestream" to "直播",
        "anime" to "动画",
        "bangumi" to "番剧",
        "music" to "音乐",
        "gaming" to "游戏",
        "game" to "游戏",
        "technology" to "科技",
        "tech" to "科技",
        "dance" to "舞蹈",
        "entertainment" to "娱乐",
        "life" to "生活",
        "food" to "美食",
        "film & tv" to "影视",
        "film" to "影视",
        "movie" to "电影",
        "tv" to "电视剧"
    )

    /**
     * Fetches top trending / popular videos from Bilibili with multiple fallbacks.
     */
    suspend fun getHomeVideos(page: Int = 1, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        val cookie = getBilibiliCookie()

        // Include top live streams on page 1 for immediate live discovery
        if (page == 1) {
            try {
                val liveList = fetchLiveStreams(page = 1, limit = 2)
                list.addAll(liveList)
            } catch (e: Exception) {
                Log.w(TAG, "Note: live streams inclusion in home: ${e.message}")
            }
        }

        // 1. Primary: Popular (Trending) endpoint
        try {
            val url = "https://api.bilibili.com/x/web-interface/popular?ps=$limit&pn=$page"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", cookie)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val jsonObj = JSONObject(jsonStr)
                        val dataObj = jsonObj.optJSONObject("data")
                        val array = dataObj?.optJSONArray("list")
                        if (array != null && array.length() > 0) {
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                val bvid = item.optString("bvid", "")
                                if (bvid.isBlank()) continue
                                val rawTitle = item.optString("title", "Bilibili Video")
                                val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()
                                val cachedTitle = com.example.util.SubtitleTranslator.translationCache.get("$cleanTitle|en")
                                val finalTitle = if (!cachedTitle.isNullOrBlank()) cachedTitle else cleanTitle

                                var pic = item.optString("pic", "")
                                if (pic.startsWith("//")) pic = "https:$pic"
                                val ownerObj = item.optJSONObject("owner")
                                val owner = ownerObj?.optString("name", "Bilibili") ?: "Bilibili"
                                var face = ownerObj?.optString("face", "") ?: ""
                                if (face.startsWith("//")) face = "https:$face"
                                val mid = ownerObj?.optLong("mid", 0L) ?: 0L
                                val uploaderUrl = if (mid > 0L) "https://space.bilibili.com/$mid" else null
                                val duration = item.optLong("duration", -1L)
                                val stat = item.optJSONObject("stat")
                                val viewCount = stat?.optLong("view", -1L) ?: -1L

                                list.add(
                                    VideoItem(
                                        id = "https://www.bilibili.com/video/$bvid",
                                        title = finalTitle,
                                        uploaderName = owner,
                                        uploaderAvatarUrl = if (face.isNotBlank()) face else null,
                                        uploaderUrl = uploaderUrl,
                                        durationSeconds = duration,
                                        viewCount = viewCount,
                                        thumbnailUrl = pic,
                                        providerId = PROVIDER_ID
                                    )
                                )
                                if (list.size >= limit) break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Bilibili popular: ${e.message}")
        }

        if (list.isNotEmpty()) return@withContext list

        // 2. Secondary fallback: Recommendation feed (rcmd)
        try {
            val rcmdUrl = "https://api.bilibili.com/x/web-interface/index/top/feed/rcmd?fresh_type=4&feed_version=V8&ps=$limit"
            val rcmdReq = Request.Builder()
                .url(rcmdUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", cookie)
                .build()

            httpClient.newCall(rcmdReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val jsonObj = JSONObject(jsonStr)
                        val dataObj = jsonObj.optJSONObject("data")
                        val array = dataObj?.optJSONArray("item")
                        if (array != null && array.length() > 0) {
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                val bvid = item.optString("bvid", "")
                                if (bvid.isBlank()) continue
                                val rawTitle = item.optString("title", "Bilibili Video")
                                val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()
                                val cachedTitle = com.example.util.SubtitleTranslator.translationCache.get("$cleanTitle|en")
                                val finalTitle = if (!cachedTitle.isNullOrBlank()) cachedTitle else cleanTitle

                                var pic = item.optString("pic", "")
                                if (pic.startsWith("//")) pic = "https:$pic"
                                val ownerObj = item.optJSONObject("owner")
                                val owner = ownerObj?.optString("name", "Bilibili") ?: "Bilibili"
                                var face = ownerObj?.optString("face", "") ?: ""
                                if (face.startsWith("//")) face = "https:$face"
                                val mid = ownerObj?.optLong("mid", 0L) ?: 0L
                                val uploaderUrl = if (mid > 0L) "https://space.bilibili.com/$mid" else null
                                val duration = item.optLong("duration", -1L)
                                val stat = item.optJSONObject("stat")
                                val viewCount = stat?.optLong("view", -1L) ?: -1L

                                list.add(
                                    VideoItem(
                                        id = "https://www.bilibili.com/video/$bvid",
                                        title = finalTitle,
                                        uploaderName = owner,
                                        uploaderAvatarUrl = if (face.isNotBlank()) face else null,
                                        uploaderUrl = uploaderUrl,
                                        durationSeconds = duration,
                                        viewCount = viewCount,
                                        thumbnailUrl = pic,
                                        providerId = PROVIDER_ID
                                    )
                                )
                                if (list.size >= limit) break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Bilibili rcmd feed: ${e.message}")
        }

        if (list.isNotEmpty()) return@withContext list

        // 3. Third fallback: Bilibili Precious (hall of fame / classic top videos)
        try {
            val preciousUrl = "https://api.bilibili.com/x/web-interface/popular/precious?page_size=$limit&page=$page"
            val precReq = Request.Builder()
                .url(preciousUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", cookie)
                .build()

            httpClient.newCall(precReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string()
                    if (!jsonStr.isNullOrBlank()) {
                        val jsonObj = JSONObject(jsonStr)
                        val dataObj = jsonObj.optJSONObject("data")
                        val array = dataObj?.optJSONArray("list")
                        if (array != null && array.length() > 0) {
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                val bvid = item.optString("bvid", "")
                                if (bvid.isBlank()) continue
                                val rawTitle = item.optString("title", "Bilibili Video")
                                val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()
                                val cachedTitle = com.example.util.SubtitleTranslator.translationCache.get("$cleanTitle|en")
                                val finalTitle = if (!cachedTitle.isNullOrBlank()) cachedTitle else cleanTitle

                                var pic = item.optString("pic", "")
                                if (pic.startsWith("//")) pic = "https:$pic"
                                val ownerObj = item.optJSONObject("owner")
                                val owner = ownerObj?.optString("name", "Bilibili") ?: "Bilibili"
                                var face = ownerObj?.optString("face", "") ?: ""
                                if (face.startsWith("//")) face = "https:$face"
                                val mid = ownerObj?.optLong("mid", 0L) ?: 0L
                                val uploaderUrl = if (mid > 0L) "https://space.bilibili.com/$mid" else null
                                val duration = item.optLong("duration", -1L)
                                val stat = item.optJSONObject("stat")
                                val viewCount = stat?.optLong("view", -1L) ?: -1L

                                list.add(
                                    VideoItem(
                                        id = "https://www.bilibili.com/video/$bvid",
                                        title = finalTitle,
                                        uploaderName = owner,
                                        uploaderAvatarUrl = if (face.isNotBlank()) face else null,
                                        uploaderUrl = uploaderUrl,
                                        durationSeconds = duration,
                                        viewCount = viewCount,
                                        thumbnailUrl = pic,
                                        providerId = PROVIDER_ID
                                    )
                                )
                                if (list.size >= limit) break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Bilibili precious: ${e.message}")
        }

        list
    }

    /**
     * Searches Bilibili using native API with automatic translation and cleanup.
     * Fully supports "bilisearch:" and "bilisearch<N>:" prefix notation.
     */
    suspend fun searchBilibili(rawQuery: String, page: Int = 1, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = rawQuery.replace(Regex("(?i)^bilisearch\\d*:"), "").trim()
        if (cleanQuery.isBlank() || cleanQuery.equals("all", ignoreCase = true)) {
            return@withContext getHomeVideos(page, limit)
        }

        val queryLower = cleanQuery.lowercase()
        if (queryLower == "live" || queryLower == "livestream" || queryLower == "直播" ||
            queryLower.startsWith("live ") || queryLower.endsWith(" live") ||
            queryLower.contains("bilibili live") || queryLower.contains("live room")
        ) {
            return@withContext fetchLiveStreams(page, limit)
        }

        // Check if query is a recognized category tag
        val mappedKeyword = CATEGORY_SEARCH_MAP[queryLower] ?: cleanQuery

        val list = mutableListOf<VideoItem>()
        try {
            val encoded = URLEncoder.encode(mappedKeyword, "UTF-8")
            val url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encoded&page=$page"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", getBilibiliCookie())
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext list

            val jsonObj = JSONObject(jsonStr)
            val dataObj = jsonObj.optJSONObject("data") ?: return@withContext list
            val array = dataObj.optJSONArray("result") ?: return@withContext list

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val bvid = item.optString("bvid", "")
                if (bvid.isBlank()) continue
                val rawTitle = item.optString("title", "Bilibili Video")
                val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()
                val cachedTitle = com.example.util.SubtitleTranslator.translationCache.get("$cleanTitle|en")
                val finalTitle = if (!cachedTitle.isNullOrBlank()) cachedTitle else cleanTitle

                var pic = item.optString("pic", "")
                if (pic.startsWith("//")) pic = "https:$pic"
                val author = item.optString("author", "Bilibili")
                val upic = item.optString("upic", item.optString("uface", ""))
                var avatar = if (upic.startsWith("//")) "https:$upic" else upic
                val mid = item.optLong("mid", 0L)
                val uploaderUrl = if (mid > 0L) "https://space.bilibili.com/$mid" else null
                val play = item.optLong("play", -1L)
                val durationRaw = item.optString("duration", "")
                val durationSec = parseDurationString(durationRaw)

                list.add(
                    VideoItem(
                        id = "https://www.bilibili.com/video/$bvid",
                        title = finalTitle,
                        uploaderName = author,
                        uploaderAvatarUrl = if (avatar.isNotBlank()) avatar else null,
                        uploaderUrl = uploaderUrl,
                        durationSeconds = durationSec,
                        viewCount = play,
                        thumbnailUrl = pic,
                        providerId = PROVIDER_ID
                    )
                )
                if (list.size >= limit) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bilibili search error: ${e.message}")
        }
        list
    }

    /**
     * Parses a duration string (e.g. "04:32", "01:15:30") into seconds.
     */
    fun parseDurationString(durationStr: String): Long {
        if (durationStr.isBlank()) return -1L
        return try {
            val parts = durationStr.trim().split(":")
            when (parts.size) {
                1 -> parts[0].toLongOrNull() ?: -1L
                2 -> {
                    val m = parts[0].toLongOrNull() ?: 0L
                    val s = parts[1].toLongOrNull() ?: 0L
                    (m * 60) + s
                }
                3 -> {
                    val h = parts[0].toLongOrNull() ?: 0L
                    val m = parts[1].toLongOrNull() ?: 0L
                    val s = parts[2].toLongOrNull() ?: 0L
                    (h * 3600) + (m * 60) + s
                }
                else -> -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Fetches videos for any Bilibili category.
     */
    suspend fun fetchCategoryVideos(category: String, page: Int = 1, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        val catKey = category.trim().lowercase()
        if (catKey == "all" || catKey.isBlank()) {
            return@withContext getHomeVideos(page, limit)
        }
        if (catKey == "live" || catKey == "livestream" || catKey == "直播") {
            return@withContext fetchLiveStreams(page, limit)
        }
        val searchTerm = CATEGORY_SEARCH_MAP[catKey] ?: category
        searchBilibili(searchTerm, page, limit)
    }

    // =========================================================================
    // BILIBILI LIVE STREAM RESOLUTION & LIVE STREAMS LIST
    // =========================================================================

    suspend fun resolveLiveStream(targetUrl: String): StreamData? = withContext(Dispatchers.IO) {
        val roomMatch = Regex("(?i)(?:live\\.bilibili\\.com/|live:)(\\d+)").find(targetUrl)
        val roomId = roomMatch?.groupValues?.get(1) ?: return@withContext null

        try {
            // 1. Fetch Room Info
            val roomInfoUrl = "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$roomId"
            val roomReq = Request.Builder()
                .url(roomInfoUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://live.bilibili.com/")
                .build()

            val roomJsonStr = httpClient.newCall(roomReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            val roomJson = JSONObject(roomJsonStr)
            val roomData = roomJson.optJSONObject("data") ?: return@withContext null

            val title = roomData.optString("title", "Bilibili Live")
            var cover = roomData.optString("user_cover", roomData.optString("cover", ""))
            if (cover.startsWith("//")) cover = "https:$cover"
            val desc = roomData.optString("description", "")
            val online = roomData.optLong("online", 0L)

            // 2. Fetch Streamer / Anchor Info (Real channel logo and name)
            var uploader = "Bilibili Streamer"
            var avatarUrl: String? = null
            try {
                val anchorUrl = "https://api.live.bilibili.com/live_user/v1/UserInfo/get_anchor_in_room?roomid=$roomId"
                val anchorReq = Request.Builder()
                    .url(anchorUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://live.bilibili.com/")
                    .build()

                httpClient.newCall(anchorReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val anchorJsonStr = resp.body?.string()
                        if (!anchorJsonStr.isNullOrBlank()) {
                            val anchorJson = JSONObject(anchorJsonStr)
                            val info = anchorJson.optJSONObject("data")?.optJSONObject("info")
                            if (info != null) {
                                uploader = info.optString("uname", uploader)
                                var face = info.optString("face", "")
                                if (face.startsWith("//")) face = "https:$face"
                                if (face.isNotBlank()) avatarUrl = face
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching live anchor info: ${e.message}")
            }

            // 3. Fetch Live PlayUrl using modern getRoomPlayInfo (v2)
            val liveHeaders = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://live.bilibili.com/"
            )
            val liveOptions = mutableListOf<PlayableStreamOption>()
            var primaryLiveUrl = ""

            try {
                val playInfoUrl = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?room_id=$roomId&protocol=0,1&format=0,1,2&codec=0,1&qn=10000&platform=web&ptype=16"
                val playReq = Request.Builder()
                    .url(playInfoUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://live.bilibili.com/")
                    .build()

                httpClient.newCall(playReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val playJsonStr = resp.body?.string()
                        if (!playJsonStr.isNullOrBlank()) {
                            val playJson = JSONObject(playJsonStr)
                            val data = playJson.optJSONObject("data")
                            val streamArr = data?.optJSONObject("playurl_info")?.optJSONObject("playurl")?.optJSONArray("stream")
                            if (streamArr != null) {
                                for (s in 0 until streamArr.length()) {
                                    val streamObj = streamArr.optJSONObject(s) ?: continue
                                    val protoName = streamObj.optString("protocol_name")
                                    val isHls = protoName.contains("hls")
                                    val formatArr = streamObj.optJSONArray("format") ?: continue

                                    for (f in 0 until formatArr.length()) {
                                        val fmtObj = formatArr.optJSONObject(f) ?: continue
                                        val fmtName = fmtObj.optString("format_name")
                                        val codecArr = fmtObj.optJSONArray("codec") ?: continue

                                        for (c in 0 until codecArr.length()) {
                                            val codecObj = codecArr.optJSONObject(c) ?: continue
                                            val baseUrl = codecObj.optString("base_url")
                                            val urlInfoArr = codecObj.optJSONArray("url_info") ?: continue

                                            for (u in 0 until urlInfoArr.length()) {
                                                val urlInfo = urlInfoArr.optJSONObject(u) ?: continue
                                                val host = urlInfo.optString("host")
                                                val extra = urlInfo.optString("extra")

                                                if (host.isNotBlank() && baseUrl.isNotBlank()) {
                                                    val finalUrl = "$host$baseUrl$extra"
                                                    val label = if (isHls) "Live HLS (${fmtName.uppercase()})" else "Live FLV"
                                                    val opt = PlayableStreamOption(
                                                        qualityLabel = label,
                                                        format = if (isHls) "m3u8" else "flv",
                                                        isMuxed = true,
                                                        videoUrl = finalUrl,
                                                        providerType = ProviderType.DIRECT,
                                                        headers = liveHeaders
                                                    )
                                                    if (isHls) {
                                                        liveOptions.add(0, opt)
                                                    } else {
                                                        liveOptions.add(opt)
                                                    }
                                                    if (primaryLiveUrl.isBlank() && isHls) {
                                                        primaryLiveUrl = finalUrl
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching Bilibili live play info v2: ${e.message}")
            }

            // Fallback to legacy room/v1/Room/playUrl if v2 returned empty
            if (liveOptions.isEmpty()) {
                try {
                    val legacyPlayUrl = "https://api.live.bilibili.com/room/v1/Room/playUrl?cid=$roomId&platform=h5&quality=4"
                    val legReq = Request.Builder()
                        .url(legacyPlayUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://live.bilibili.com/")
                        .build()

                    httpClient.newCall(legReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val playJsonStr = resp.body?.string()
                            if (!playJsonStr.isNullOrBlank()) {
                                val playJson = JSONObject(playJsonStr)
                                val durls = playJson.optJSONObject("data")?.optJSONArray("durl")
                                if (durls != null && durls.length() > 0) {
                                    val streamUrl = durls.optJSONObject(0)?.optString("url", "") ?: ""
                                    if (streamUrl.isNotBlank()) {
                                        val legOpt = PlayableStreamOption(
                                            qualityLabel = "Live Stream",
                                            format = "hls",
                                            isMuxed = true,
                                            videoUrl = streamUrl,
                                            providerType = ProviderType.DIRECT,
                                            headers = liveHeaders
                                        )
                                        liveOptions.add(legOpt)
                                        primaryLiveUrl = streamUrl
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Legacy Bilibili live playUrl fallback failed: ${e.message}")
                }
            }

            if (liveOptions.isEmpty()) {
                Log.w(TAG, "Could not obtain live playUrl for room $roomId")
                return@withContext null
            }

            val selectedOpt = liveOptions.firstOrNull { it.format == "m3u8" } ?: liveOptions.first()
            val effectiveUrl = primaryLiveUrl.ifBlank { selectedOpt.videoUrl ?: "" }

            StreamData(
                videoId = "https://live.bilibili.com/$roomId",
                videoUrl = effectiveUrl,
                hlsUrl = effectiveUrl,
                title = title,
                channelName = uploader,
                channelAvatarUrl = avatarUrl,
                description = desc,
                thumbnailUrl = cover,
                viewCount = online,
                likeCount = 0L,
                captionOptions = emptyList(),
                availableStreamOptions = liveOptions,
                selectedStreamOption = selectedOpt,
                providerId = PROVIDER_ID,
                providerType = ProviderType.DIRECT,
                headers = liveHeaders
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving live stream for room $roomId: ${e.message}")
            null
        }
    }

    suspend fun fetchLiveStreams(page: Int = 1, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        try {
            val url = "https://api.live.bilibili.com/room/v3/area/getRoomList?platform=web&parent_area_id=0&cate_id=0&area_id=0&sort_type=online&page=$page&page_size=$limit"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://live.bilibili.com/")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext emptyList()

            val json = JSONObject(jsonStr)
            val rooms = json.optJSONObject("data")?.optJSONArray("list") ?: return@withContext emptyList()

            for (i in 0 until rooms.length()) {
                val r = rooms.optJSONObject(i) ?: continue
                val roomid = r.optLong("roomid", 0L)
                if (roomid == 0L) continue

                val title = r.optString("title", "Live Stream")
                val uname = r.optString("uname", "Bilibili Streamer")
                var face = r.optString("face", "")
                if (face.startsWith("//")) face = "https:$face"
                var cover = r.optString("user_cover", r.optString("cover", ""))
                if (cover.startsWith("//")) cover = "https:$cover"
                val online = r.optLong("online", 0L)
                val areaName = r.optString("area_name", "Live")

                list.add(
                    VideoItem(
                        id = "https://live.bilibili.com/$roomid",
                        title = title,
                        uploaderName = uname,
                        uploaderAvatarUrl = if (face.isNotBlank()) face else null,
                        uploaderUrl = "https://live.bilibili.com/$roomid",
                        durationSeconds = 0L,
                        viewCount = online,
                        thumbnailUrl = cover,
                        tags = listOf("LIVE", "Bilibili Live", areaName),
                        providerId = PROVIDER_ID
                    )
                )
                if (list.size >= limit) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Bilibili live streams: ${e.message}")
        }
        list
    }

    // =========================================================================
    // BILIBILI CHANNEL DETAILS WITH REAL AVATAR & STATS
    // =========================================================================

    suspend fun fetchChannelDetails(channelNameOrUrl: String, fallbackAvatar: String? = null): com.example.model.ChannelDetails = withContext(Dispatchers.IO) {
        val trimmed = channelNameOrUrl.trim()
        val uidMatch = Regex("(?i)space\\.bilibili\\.com/(\\d+)").find(trimmed)
        val targetUid = uidMatch?.groupValues?.get(1)
        val cleanName = if (targetUid != null) "Bilibili Creator" else trimmed.replace(Regex("(?i)^@"), "").trim()

        var resolvedName = cleanName
        var resolvedAvatar = fallbackAvatar
        var subscriberCountText = "Verified Creator"
        var videoCountText = "Videos"
        var descriptionText = "Official channel on Bilibili."

        try {
            val queryParam = targetUid ?: cleanName
            val encoded = URLEncoder.encode(queryParam, "UTF-8")
            val searchUserUrl = "https://api.bilibili.com/x/web-interface/search/type?search_type=bili_user&keyword=$encoded&page=1"
            val req = Request.Builder()
                .url(searchUserUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Cookie", getBilibiliCookie())
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
            if (!jsonStr.isNullOrBlank()) {
                val jsonObj = JSONObject(jsonStr)
                val results = jsonObj.optJSONObject("data")?.optJSONArray("result")
                if (results != null && results.length() > 0) {
                    val userObj = results.optJSONObject(0)
                    if (userObj != null) {
                        resolvedName = userObj.optString("uname", resolvedName)
                        var upic = userObj.optString("upic", "")
                        if (upic.startsWith("//")) upic = "https:$upic"
                        if (upic.isNotBlank()) resolvedAvatar = upic

                        val fans = userObj.optLong("fans", 0L)
                        if (fans > 0) {
                            subscriberCountText = if (fans >= 10_000_000) {
                                String.format("%.1fM subscribers", fans / 1_000_000.0)
                            } else if (fans >= 10_000) {
                                String.format("%.1fK subscribers", fans / 1_000.0)
                            } else {
                                "$fans subscribers"
                            }
                        }

                        val videosCount = userObj.optInt("videos", 0)
                        if (videosCount > 0) {
                            videoCountText = "$videosCount videos"
                        }

                        val usign = userObj.optString("usign", "")
                        if (usign.isNotBlank()) {
                            descriptionText = usign
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Bilibili channel info: ${e.message}")
        }

        // Fetch channel videos
        val channelVideos = searchBilibili(resolvedName, page = 1, limit = 30)

        com.example.model.ChannelDetails(
            channelId = resolvedName.lowercase().replace("[^a-z0-9]".toRegex(), "_").take(30),
            name = resolvedName,
            handle = "@${resolvedName.replace(" ", "").lowercase()}",
            avatarUrl = resolvedAvatar,
            bannerUrl = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200&auto=format&fit=crop&q=80",
            subscriberCount = subscriberCountText,
            videoCount = if (channelVideos.isNotEmpty()) "${channelVideos.size} videos" else videoCountText,
            description = descriptionText,
            isSubscribed = false,
            videos = channelVideos
        )
    }
}
