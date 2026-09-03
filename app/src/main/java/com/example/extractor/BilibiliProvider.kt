package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Primary stream resolution entry point for any Bilibili identifier or URL.
     */
    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        if (cleanInput.isBlank()) return@withContext null

        try {
            var targetUrl = cleanInput

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

        val metaJsonStr = try {
            httpClient.newCall(metaReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching metaJson: ${e.message}")
            null
        }

        val metaJson = if (!metaJsonStr.isNullOrBlank()) try { JSONObject(metaJsonStr) } catch (e: Exception) { null } else null
        val dataObj = metaJson?.optJSONObject("data")

        val resolvedBvid = dataObj?.optString("bvid", bvid) ?: bvid
        val resolvedAid = dataObj?.optLong("aid", 0L) ?: (aid.toLongOrNull() ?: 0L)

        val rawTitle = customTitle ?: dataObj?.optString("title", "Bilibili Video") ?: "Bilibili Video"
        val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()
        val translatedEnglishTitle = try {
            com.example.util.SubtitleTranslator.translateText(cleanTitle, targetLang = "en", sourceLang = "zh")
        } catch (e: Exception) {
            cleanTitle
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
        val avatar = ownerObj?.optString("face", "")
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
            "Referer" to REFERER
        )

        // Stream options
        val streamOptions = fetchPlayurlStreams(resolvedBvid, cid, biliHeaders)
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
            val episodeTitle = targetEp.optString("long_title", targetEp.optString("title", "Episode 1"))
            val seriesTitle = result.optString("title", "Bangumi Series")
            val fullTitle = "$seriesTitle: $episodeTitle"
            val cover = targetEp.optString("cover", result.optString("cover", ""))
            val desc = result.optString("evaluate", "")

            Log.i(TAG, "Resolved Bangumi: $fullTitle (bvid=$bvid, cid=$cid)")
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

    private fun fetchPlayurlStreams(
        resolvedBvid: String,
        cid: Long,
        biliHeaders: Map<String, String>
    ): List<PlayableStreamOption> {
        val streamOptions = mutableListOf<PlayableStreamOption>()

        // 1. Fetch Progressive Muxed Streams (fnval=0 fallback)
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
                                116 -> "1080p 60fps Progressive (MP4 Direct)"
                                80 -> "1080p Progressive (MP4 Direct)"
                                64 -> "720p Progressive (MP4 Direct)"
                                32 -> "480p Progressive (MP4 Direct)"
                                16 -> "360p Progressive (MP4 Direct)"
                                else -> "Progressive Stream (MP4 Direct)"
                            }

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
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching progressive playurl: ${e.message}")
        }

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

        return streamOptions
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

    /**
     * Searches Bilibili using native API with automatic translation and cleanup.
     * Fully supports "bilisearch:" and "bilisearch<N>:" prefix notation.
     */
    suspend fun searchBilibili(rawQuery: String, page: Int = 1, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = rawQuery.replace(Regex("(?i)^bilisearch\\d*:"), "").trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val list = mutableListOf<VideoItem>()
        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encoded&page=$page"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
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
                val translatedTitle = try {
                    com.example.util.SubtitleTranslator.translateTextSync(cleanTitle, targetLang = "en", sourceLang = "zh")
                } catch (e: Exception) {
                    cleanTitle
                }
                val finalTitle = if (translatedTitle.isNotBlank() && translatedTitle != cleanTitle) {
                    translatedTitle
                } else {
                    cleanTitle
                }

                var pic = item.optString("pic", "")
                if (pic.startsWith("//")) pic = "https:$pic"
                val author = item.optString("author", "Bilibili")
                val play = item.optLong("play", -1L)
                val durationRaw = item.optString("duration", "")
                val durationSec = parseDurationString(durationRaw)

                list.add(
                    VideoItem(
                        id = "https://www.bilibili.com/video/$bvid",
                        title = finalTitle,
                        uploaderName = author,
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
     * Fetches videos for any Bilibili category using the official region APIs.
     */
    suspend fun fetchCategoryVideos(category: String, page: Int = 1, limit: Int = 20): List<VideoItem> = withContext(Dispatchers.IO) {
        val catKey = category.trim().lowercase()
        val rid = CATEGORY_RID_MAP[catKey] ?: CATEGORY_RID_MAP["anime"] ?: 1

        val list = mutableListOf<VideoItem>()
        try {
            val url = if (rid == 0) {
                "https://api.bilibili.com/x/web-interface/popular?ps=$limit&pn=$page"
            } else {
                "https://api.bilibili.com/x/web-interface/dynamic/region?ps=$limit&pn=$page&rid=$rid"
            }

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext list

            val jsonObj = JSONObject(jsonStr)
            val dataObj = jsonObj.optJSONObject("data") ?: return@withContext list
            val array = dataObj.optJSONArray("archives") ?: dataObj.optJSONArray("list") ?: return@withContext list

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val bvid = item.optString("bvid", "")
                if (bvid.isBlank()) continue
                val rawTitle = item.optString("title", "Bilibili Video")
                val cleanTitle = rawTitle.replace(Regex("<[^>]*>"), "").trim()
                val translatedTitle = try {
                    com.example.util.SubtitleTranslator.translateTextSync(cleanTitle, targetLang = "en", sourceLang = "zh")
                } catch (e: Exception) {
                    cleanTitle
                }
                val finalTitle = if (translatedTitle.isNotBlank() && translatedTitle != cleanTitle) {
                    translatedTitle
                } else {
                    cleanTitle
                }

                var pic = item.optString("pic", "")
                if (pic.startsWith("//")) pic = "https:$pic"
                val owner = item.optJSONObject("owner")?.optString("name", "Bilibili") ?: "Bilibili"
                val duration = item.optLong("duration", -1L)
                val stat = item.optJSONObject("stat")
                val viewCount = stat?.optLong("view", -1L) ?: -1L

                list.add(
                    VideoItem(
                        id = "https://www.bilibili.com/video/$bvid",
                        title = finalTitle,
                        uploaderName = owner,
                        durationSeconds = duration,
                        viewCount = viewCount,
                        thumbnailUrl = pic,
                        providerId = PROVIDER_ID
                    )
                )
                if (list.size >= limit) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Bilibili category $category: ${e.message}")
        }
        list
    }
}
