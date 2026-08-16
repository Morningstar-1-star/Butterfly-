package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ArchiveOrgProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "archive_org"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1

        // Rotate queries and sort modes across pages for unlimited variety and rare content discovery
        val categories = listOf(
            "mediatype:movies AND (collection:anime OR collection:animationandcartoons OR collection:the-anime-cascade OR " +
                    "collection:feature_films OR collection:movies OR collection:wrestling OR collection:vhsvault OR collection:movie_trailers OR " +
                    "\"wwe\" OR \"wwf\" OR \"anime\" OR \"vhs tape\" OR \"trailer\")",

            "mediatype:movies AND (collection:anime OR collection:the-anime-cascade OR collection:unsorted-anime-collection OR " +
                    "\"japanese animation\" OR \"rare anime\" OR \"anime movie\")",

            "mediatype:movies AND (collection:feature_films OR collection:cinema OR collection:cult_movies OR collection:classic_tv OR " +
                    "\"feature film\" OR \"cult classic\" OR \"full movie\")",

            "mediatype:movies AND (collection:wrestling OR \"wwe\" OR \"wwf\" OR \"pro wrestling\" OR \"wcw\" OR \"extreme championship wrestling\")",

            "mediatype:movies AND (collection:vhsvault OR collection:vhs OR \"vhs tape\" OR \"vhs rip\" OR \"vhs recording\" OR \"rare cassette\")",

            "mediatype:movies AND (collection:movie_trailers OR \"movie trailer\" OR \"teaser trailer\" OR \"cinema promo\" OR \"rare trailer\")"
        )

        val queryIndex = (page - 1) % categories.size
        val curatedQuery = categories[queryIndex]
        val sortModes = listOf("downloads+desc", "publicdate+desc", "addeddate+desc", "review_date+desc")
        val sortMode = sortModes[(page - 1) % sortModes.size]

        val encodedQuery = java.net.URLEncoder.encode(curatedQuery, "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl%5B%5D=identifier%2Ctitle%2Ccreator%2Cpublicdate%2Cdescription%2Cdownloads&sort%5B%5D=$sortMode&rows=30&page=$page&output=json"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, numFound) = parseArchiveList(resp.body)
        PagedResult(
            items = items,
            nextPageToken = (page + 1).toString(),
            hasMore = page * 30 < numFound
        )
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val clean = query.replace("[^a-zA-Z0-9 ]".toRegex(), " ").trim()

        val queryTerm = if (clean.isNotBlank()) {
            "($clean OR \"$clean\")"
        } else {
            "(anime OR feature films OR wwe OR vhs vault OR trailers)"
        }

        val encodedQuery = java.net.URLEncoder.encode("mediatype:movies AND $queryTerm", "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl%5B%5D=identifier%2Ctitle%2Ccreator%2Cpublicdate%2Cdescription%2Cdownloads&sort%5B%5D=downloads+desc&rows=30&page=$page&output=json"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, numFound) = parseArchiveList(resp.body)
        PagedResult(
            items = items,
            nextPageToken = (page + 1).toString(),
            hasMore = page * 30 < numFound
        )
    }

    companion object {
        @Volatile
        private var lastArchiveIdentifier: String? = null

        @Volatile
        var contextRef: android.content.Context? = null
    }

    private data class ArchiveRequestInfo(
        val identifier: String,
        val targetFileName: String? = null,
        val targetEpisodeIndex: Int? = null,
        val isDirectUrl: Boolean = false
    )

    private fun parseRequest(idOrUrl: String): ArchiveRequestInfo {
        val clean = idOrUrl.trim()

        // Direct Download URL: https://archive.org/download/{identifier}/{filename}
        if (clean.contains("archive.org/download/")) {
            val after = clean.substringAfter("archive.org/download/")
            val parts = after.split("/", limit = 2)
            val id = parts[0].substringBefore("?").substringBefore("#")
            val file = if (parts.size > 1) parts[1].substringBefore("?").substringBefore("#") else null
            return ArchiveRequestInfo(identifier = id, targetFileName = file, isDirectUrl = true)
        }

        // Details URL: https://archive.org/details/{identifier}
        if (clean.contains("archive.org/details/")) {
            val after = clean.substringAfter("archive.org/details/")
            val id = after.substringBefore("/").substringBefore("?").substringBefore("#")
            return ArchiveRequestInfo(identifier = id)
        }

        // Format: {identifier}::{filename or index}
        if (clean.contains("::")) {
            val parts = clean.split("::", limit = 2)
            val epIdx = parts[1].toIntOrNull()
            return ArchiveRequestInfo(
                identifier = parts[0],
                targetFileName = if (epIdx == null) parts[1] else null,
                targetEpisodeIndex = epIdx
            )
        }

        // Synthetic Episode ID from TMDB or VM (e.g., tv_30991_s1_e2 or show_s1_e2)
        if (clean.contains("_s") && clean.contains("_e")) {
            val epNumStr = clean.substringAfter("_e").takeWhile { it.isDigit() }
            val epIdx = epNumStr.toIntOrNull()
            val lastId = lastArchiveIdentifier ?: "feature_films"
            return ArchiveRequestInfo(identifier = lastId, targetEpisodeIndex = epIdx)
        }

        // Direct stream URL starting with http/https and ending with video extension
        if ((clean.startsWith("http://") || clean.startsWith("https://")) &&
            (clean.endsWith(".mp4", ignoreCase = true) || clean.endsWith(".mkv", ignoreCase = true) || clean.endsWith(".avi", ignoreCase = true))
        ) {
            val id = extractId(clean)
            return ArchiveRequestInfo(identifier = id.ifBlank { lastArchiveIdentifier ?: "archive" }, isDirectUrl = true)
        }

        val simpleId = extractId(clean)
        return ArchiveRequestInfo(identifier = simpleId)
    }

    private fun extractCreator(obj: JSONObject?): String {
        if (obj == null) return "Internet Archive"
        val keys = listOf("creator", "uploader", "artist", "author", "submitter", "collection")
        for (k in keys) {
            val v = obj.optString(k, "").trim()
            if (v.isNotBlank() && v != "null") return v
        }
        return "Internet Archive"
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val reqInfo = parseRequest(idOrUrl)
        val identifier = reqInfo.identifier
        lastArchiveIdentifier = identifier

        val url = "https://archive.org/metadata/$identifier"
        try {
            val resp = http.get(url)
            val json = JSONObject(resp.body)
            val meta = json.optJSONObject("metadata")
            val title = meta?.optString("title") ?: identifier
            val creator = extractCreator(meta)
            PluginVideoItem(
                id = identifier,
                title = title,
                uploaderName = creator,
                uploadDate = meta?.optString("publicdate"),
                thumbnailUrl = "https://archive.org/services/img/$identifier",
                providerId = providerId
            )
        } catch (e: Exception) {
            PluginVideoItem(
                id = identifier,
                title = identifier,
                uploaderName = "Internet Archive",
                thumbnailUrl = "https://archive.org/services/img/$identifier",
                providerId = providerId
            )
        }
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val reqInfo = parseRequest(idOrUrl)
        val identifier = reqInfo.identifier
        if (identifier.isNotBlank()) {
            lastArchiveIdentifier = identifier
        }

        val url = "https://archive.org/metadata/$identifier"
        val videoStreams = mutableListOf<PluginVideoStream>()
        val subtitles = mutableListOf<PluginSubtitle>()

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val meta = json.optJSONObject("metadata")
                val filesArr = json.optJSONArray("files") ?: JSONArray()
                val server = json.optString("server").ifBlank { json.optJSONArray("workable_servers")?.optString(0) ?: "" }
                val dir = json.optString("dir")

                // Collect video entries and subtitle entries
                val allVideoFiles = mutableListOf<JSONObject>()
                for (i in 0 until filesArr.length()) {
                    val f = filesArr.getJSONObject(i)
                    val name = f.optString("name")
                    val format = f.optString("format", "").lowercase()

                    val isThumb = name.endsWith("_thumb.mp4") || format.contains("thumbnail")
                    val isMetadata = name.endsWith(".xml") || name.endsWith(".sqlite") || name.endsWith(".torrent") || name.endsWith(".txt") || name.endsWith(".png") || name.endsWith(".jpg")

                    if (!isThumb && !isMetadata) {
                        if (format.contains("mp4") || format.contains("h.264") || format.contains("mpeg4") ||
                            format.contains("512kb") || format.contains("matroska") || format.contains("vob") ||
                            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".ia.mp4")
                        ) {
                            allVideoFiles.add(f)
                        } else if (format.contains("vtt") || name.endsWith(".vtt") || name.endsWith(".srt")) {
                            val encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                            val fileUrl = if (server.isNotBlank() && dir.isNotBlank()) "https://$server$dir/$encodedName" else "https://archive.org/download/$identifier/$encodedName"
                            subtitles.add(
                                PluginSubtitle(
                                    url = fileUrl,
                                    languageCode = "en",
                                    languageName = "English",
                                    format = if (name.endsWith(".vtt")) "vtt" else "srt"
                                )
                            )
                        }
                    }
                }

                // Prefer fast web stream formats (H.264 / MPEG4 / .ia.mp4 / MP4) over heavy raw files
                val preferredStreams = mutableListOf<PluginVideoStream>()
                val isMultiFile = allVideoFiles.size > 1

                for (f in allVideoFiles) {
                    val name = f.optString("name")
                    val format = f.optString("format", "").lowercase()
                    val title = f.optString("title", "").ifBlank {
                        name.removeSuffix(".ia.mp4")
                            .removeSuffix(".mp4")
                            .removeSuffix(".mkv")
                            .removeSuffix(".avi")
                            .replace("_", " ")
                            .replace("%20", " ")
                    }
                    val encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                    val fileUrl = if (server.isNotBlank() && dir.isNotBlank()) "https://$server$dir/$encodedName" else "https://archive.org/download/$identifier/$encodedName"
                    val height = f.optInt("height", 0)
                    val heightLabel = if (height > 0) "${height}p" else "1080p"

                    val isFastWebFormat = name.lowercase().endsWith(".ia.mp4") || name.lowercase().endsWith(".mp4") || format.contains("h.264") || format.contains("mpeg4") || format.contains("512kb") || format.contains("mp4")

                    val qualityTag = if (isFastWebFormat) "H.264 Fast MP4 ($heightLabel)" else "Direct Stream ($heightLabel)"
                    val label = if (isMultiFile) title else qualityTag

                    preferredStreams.add(
                        PluginVideoStream(
                            url = fileUrl,
                            qualityLabel = label,
                            format = "mp4",
                            height = if (height > 0) height else 1080,
                            isMuxed = true
                        )
                    )
                }

                // Organize streams according to user format preference (Fast H.264 vs Original Quality)
                val fastWebStreams = preferredStreams.filter { it.url.lowercase().endsWith(".ia.mp4") || it.url.lowercase().endsWith(".mp4") || it.qualityLabel.contains("H.264") || it.qualityLabel.contains("MP4") }
                val standardStreams = preferredStreams.filter { !fastWebStreams.contains(it) }

                val ctx = contextRef
                val prefersOriginal = reqInfo.identifier.contains("original") || (ctx != null && com.example.util.DebridSettingsManager.getArchiveFormatPreference(ctx) == "ORIGINAL_QUALITY")

                val orderedList = if (prefersOriginal) {
                    if (standardStreams.isNotEmpty()) (standardStreams + fastWebStreams).distinctBy { it.url } else preferredStreams
                } else {
                    if (fastWebStreams.isNotEmpty()) (fastWebStreams + standardStreams).distinctBy { it.url } else preferredStreams
                }

                videoStreams.addAll(orderedList)

                // If target file or target episode index was requested, move that target to index 0!
                if (reqInfo.targetFileName != null && videoStreams.isNotEmpty()) {
                    val targetDecoded = java.net.URLDecoder.decode(reqInfo.targetFileName, "UTF-8").lowercase()
                    val targetIdx = videoStreams.indexOfFirst {
                        val uDec = java.net.URLDecoder.decode(it.url, "UTF-8").lowercase()
                        uDec.contains(targetDecoded) || targetDecoded.contains(it.qualityLabel.lowercase())
                    }
                    if (targetIdx > 0) {
                        val targetItem = videoStreams.removeAt(targetIdx)
                        videoStreams.add(0, targetItem)
                    }
                } else if (reqInfo.targetEpisodeIndex != null && reqInfo.targetEpisodeIndex > 0 && videoStreams.isNotEmpty()) {
                    val targetIdx = (reqInfo.targetEpisodeIndex - 1).coerceIn(0, videoStreams.size - 1)
                    if (targetIdx > 0 && targetIdx < videoStreams.size) {
                        val targetItem = videoStreams.removeAt(targetIdx)
                        videoStreams.add(0, targetItem)
                    }
                }

                val title = meta?.optString("title") ?: identifier
                val creator = extractCreator(meta)
                val desc = meta?.optString("description") ?: "Internet Archive High Quality Media"

                if (videoStreams.isNotEmpty()) {
                    return@withContext PluginStreamInfo(
                        id = identifier,
                        url = "https://archive.org/details/$identifier",
                        title = title,
                        channelName = creator,
                        description = desc,
                        thumbnailUrl = "https://archive.org/services/img/$identifier",
                        videoStreams = videoStreams,
                        subtitles = subtitles
                    )
                }
            }
        } catch (e: Exception) {
            // Fallthrough to fallback
        }

        // Direct URL / Emergency Fallback
        val fallbackUrl = if (idOrUrl.startsWith("http://") || idOrUrl.startsWith("https://")) idOrUrl else "https://archive.org/download/$identifier/$identifier.mp4"
        videoStreams.add(
            PluginVideoStream(
                url = fallbackUrl,
                qualityLabel = "H.264 Fast Stream",
                format = "mp4",
                height = 720,
                isMuxed = true
            )
        )

        PluginStreamInfo(
            id = identifier,
            url = "https://archive.org/details/$identifier",
            title = identifier,
            channelName = "Internet Archive",
            description = "Internet Archive Direct Stream",
            thumbnailUrl = "https://archive.org/services/img/$identifier",
            videoStreams = videoStreams,
            subtitles = subtitles
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        val reqInfo = parseRequest(idOrUrl)
        val identifier = reqInfo.identifier
        val comments = mutableListOf<PluginComment>()

        try {
            val url = "https://archive.org/metadata/$identifier"
            val resp = http.get(url)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val json = JSONObject(resp.body)
                val reviewsArr = json.optJSONArray("reviews")
                if (reviewsArr != null) {
                    for (i in 0 until reviewsArr.length()) {
                        val r = reviewsArr.optJSONObject(i) ?: continue
                        val reviewer = r.optString("reviewer", "Archive User")
                        val reviewTitle = r.optString("reviewtitle", "")
                        val reviewBody = r.optString("reviewbody", "")
                        val reviewDate = r.optString("reviewdate", "Recently")
                        val stars = r.optInt("stars", 5)

                        val fullText = if (reviewTitle.isNotBlank() && reviewBody.isNotBlank()) {
                            "$reviewTitle\n$reviewBody"
                        } else reviewBody.ifBlank { reviewTitle }

                        if (fullText.isNotBlank()) {
                            comments.add(
                                PluginComment(
                                    id = "ia_rev_${identifier}_$i",
                                    authorName = reviewer,
                                    content = fullText,
                                    publishedTime = reviewDate,
                                    likeCount = (stars * 12).toLong()
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        PagedResult(items = comments, hasMore = false)
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        getStreams(idOrUrl).subtitles
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        val id = extractId(channelIdOrUrl)
        PluginChannel(id = id, name = id)
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        val id = extractId(playlistIdOrUrl)
        PluginPlaylist(id = id, title = "Internet Archive Collection", uploaderName = "Archive")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        val identifier = extractId(idOrUrl)
        val curatedQuery = "mediatype:movies AND (collection:feature_films OR collection:animationandcartoons OR collection:classic_tv OR collection:anime OR collection:movies)"
        val encodedQuery = java.net.URLEncoder.encode(curatedQuery, "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl%5B%5D=identifier%2Ctitle%2Ccreator%2Cpublicdate%2Cdescription&sort%5B%5D=downloads+desc&rows=20&output=json"
        val resp = http.get(url)
        if (resp.statusCode == 200) {
            val (items, _) = parseArchiveList(resp.body)
            return@withContext items.filter { it.id != identifier }
        }
        home().items.filter { it.id != identifier }.take(10)
    }

    private fun parseArchiveList(jsonStr: String): Pair<List<PluginVideoItem>, Long> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val response = json.optJSONObject("response") ?: JSONObject()
        val numFound = response.optLong("numFound", 0)
        val docs = response.optJSONArray("docs") ?: JSONArray()

        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val id = d.optString("identifier")
            val title = d.optString("title", id)
            val creator = extractCreator(d)
            val downloads = d.optLong("downloads", 0L)

            // Filter out raw uncurated TV news logs/dumps
            if (!isRawDumpOrLowQuality(id, title)) {
                list.add(
                    PluginVideoItem(
                        id = id,
                        title = title,
                        uploaderName = creator,
                        uploadDate = d.optString("publicdate"),
                        thumbnailUrl = "https://archive.org/services/img/$id",
                        viewCount = downloads,
                        providerId = providerId
                    )
                )
            }
        }
        return Pair(list, numFound)
    }

    private fun isRawDumpOrLowQuality(id: String, title: String): Boolean {
        val lowerId = id.lowercase()
        val lowerTitle = title.lowercase()
        if (lowerId.startsWith("hv_") || lowerId.startsWith("tv_") || lowerId.contains("zvezda") ||
            lowerId.contains("yementv") || lowerId.contains("tvri") || lowerId.contains("kron") ||
            lowerId.contains("wpvi") || lowerId.contains("blip") || lowerId.contains("cspan")
        ) return true

        if (lowerTitle.contains("zvezda") || lowerTitle.contains("yementv") || lowerTitle.contains("tvri")) return true

        // Check for raw timestamp pattern like name_20260811_100000
        if (Regex("""^[a-zA-Z0-9_]+_\d{8}_\d{6}$""").matches(id)) return true

        return false
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}

