package com.example.torrent.cardigann.executor

import android.util.Log
import com.example.torrent.bencode.Bencode
import com.example.torrent.cardigann.filter.CardigannFilterEngine
import com.example.torrent.cardigann.model.*
import com.example.torrent.cardigann.template.CardigannTemplateEngine
import com.example.torrent.model.TorrentResult
import com.example.torrent.protocol.MagnetParser
import com.example.torrent.provider.MediaIdentity
import com.example.torrent.provider.TorrentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Universal Cardigann / Prowlarr V11 Indexer Executor.
 * Executes any Prowlarr YAML definition as a native Butterfly TorrentProvider.
 */
class CardigannIndexerExecutor(
    val definition: CardigannIndexerDefinition,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    private var customBaseUrl: String? = null
) : TorrentProvider {

    companion object {
        private const val TAG = "CardigannExecutor"
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Butterfly/1.0"
    }

    override val id: String = definition.id
    override val name: String = definition.name

    private var activeMirror: String = customBaseUrl ?: definition.links.firstOrNull() ?: ""
    private var isWorkingMirrorCached: Boolean = false

    override val isEnabled: Boolean
        get() = activeMirror.isNotBlank()

    fun getActiveUrl(): String = activeMirror

    fun setActiveUrl(url: String) {
        activeMirror = url.trimEnd('/')
        isWorkingMirrorCached = true
    }

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> = withContext(Dispatchers.IO) {
        val baseUrl = resolveWorkingBaseUrl()
        if (baseUrl.isBlank()) return@withContext emptyList()

        val searchConfig = definition.search
        if (searchConfig.paths.isEmpty()) {
            return@withContext emptyList()
        }

        val configMap = mutableMapOf<String, String>()
        definition.settings.forEach { s ->
            configMap[s.name] = s.value.ifBlank { s.default }
        }

        val templateContext = CardigannTemplateEngine.TemplateContext.from(query, identity, configMap)
        val selectedPath = selectBestPath(searchConfig.paths, identity)

        val renderedPath = CardigannTemplateEngine.render(selectedPath.path, templateContext).trim()
        val requestUrl = if (renderedPath.startsWith("http://") || renderedPath.startsWith("https://")) {
            renderedPath
        } else {
            val sep = if (baseUrl.endsWith("/") || renderedPath.startsWith("/")) "" else "/"
            "$baseUrl$sep$renderedPath"
        }

        val method = selectedPath.method.ifBlank { searchConfig.method }.uppercase()
        val headersBuilder = Headers.Builder()
            .add("User-Agent", DEFAULT_USER_AGENT)
            .add("Accept", when (searchConfig.responseType.lowercase()) {
                "json" -> "application/json, text/plain, */*"
                "xml" -> "application/xml, text/xml, */*"
                else -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            })

        // Apply headers from definition
        searchConfig.headers.forEach { (k, v) ->
            headersBuilder.set(k, CardigannTemplateEngine.render(v, templateContext))
        }
        selectedPath.headers.forEach { (k, v) ->
            headersBuilder.set(k, CardigannTemplateEngine.render(v, templateContext))
        }

        val requestBuilder = Request.Builder()
            .headers(headersBuilder.build())

        if (method == "POST") {
            val formBodyBuilder = FormBody.Builder()
            val effectiveInputs = searchConfig.inputs + selectedPath.inputs
            effectiveInputs.forEach { (k, v) ->
                val renderedKey = CardigannTemplateEngine.render(k, templateContext)
                val renderedVal = CardigannTemplateEngine.render(v, templateContext)
                formBodyBuilder.add(renderedKey, renderedVal)
            }
            requestBuilder.url(requestUrl).post(formBodyBuilder.build())
        } else {
            // GET request: append inputs if any
            val effectiveInputs = searchConfig.inputs + selectedPath.inputs
            val finalUrl = if (effectiveInputs.isNotEmpty()) {
                val httpUrlBuilder = requestUrl.toHttpUrlOrNull()?.newBuilder()
                if (httpUrlBuilder != null) {
                    effectiveInputs.forEach { (k, v) ->
                        httpUrlBuilder.addQueryParameter(
                            CardigannTemplateEngine.render(k, templateContext),
                            CardigannTemplateEngine.render(v, templateContext)
                        )
                    }
                    httpUrlBuilder.build().toString()
                } else {
                    requestUrl
                }
            } else {
                requestUrl
            }
            requestBuilder.url(finalUrl).get()
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "[$id] HTTP ${response.code} for $requestUrl")
                return@withContext emptyList()
            }

            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val results = parseSearchResults(responseBody, searchConfig, baseUrl, identity)
            Log.i(TAG, "[$id] Found ${results.size} releases for '${templateContext.keywords}'")
            results
        } catch (e: Exception) {
            Log.w(TAG, "[$id] Search failed: ${e.message}")
            emptyList()
        }
    }

    private fun selectBestPath(paths: List<CardigannPath>, identity: MediaIdentity): CardigannPath {
        if (paths.size == 1) return paths.first()

        // Check if path has category matching
        if (identity.season != null || identity.episode != null) {
            val tvPath = paths.firstOrNull { it.path.contains("tv", true) || it.categories.any { c -> c.startsWith("5") } }
            if (tvPath != null) return tvPath
        }

        if (identity.mediaType.equals("movie", true)) {
            val moviePath = paths.firstOrNull { it.path.contains("movie", true) || it.categories.any { c -> c.startsWith("2") } }
            if (moviePath != null) return moviePath
        }

        return paths.first()
    }

    private fun parseSearchResults(
        body: String,
        searchConfig: CardigannSearchConfig,
        baseUrl: String,
        identity: MediaIdentity
    ): List<TorrentResult> {
        return when (searchConfig.responseType.lowercase()) {
            "json" -> parseJsonResults(body, searchConfig, baseUrl, identity)
            "xml" -> parseXmlResults(body, searchConfig, baseUrl, identity)
            else -> parseHtmlResults(body, searchConfig, baseUrl, identity)
        }
    }

    private fun parseHtmlResults(
        html: String,
        searchConfig: CardigannSearchConfig,
        baseUrl: String,
        identity: MediaIdentity
    ): List<TorrentResult> {
        val doc = Jsoup.parse(html, baseUrl)
        val rowSelector = searchConfig.rows.selector.ifBlank { "table tr, .torrent-row, .item" }
        val rows = doc.select(rowSelector)
        if (rows.isEmpty()) return emptyList()

        val results = mutableListOf<TorrentResult>()
        val skipCount = searchConfig.rows.after

        for (i in skipCount until rows.size) {
            val row = rows[i]
            if (searchConfig.rows.remove.isNotBlank()) {
                row.select(searchConfig.rows.remove).remove()
            }

            val parsed = extractResultFromElement(row, searchConfig.fields, baseUrl, identity)
            if (parsed != null && (parsed.magnet.isNotBlank() || parsed.infoHash.isNotBlank())) {
                results.add(parsed)
            }
        }

        return results
    }

    private fun parseXmlResults(
        xml: String,
        searchConfig: CardigannSearchConfig,
        baseUrl: String,
        identity: MediaIdentity
    ): List<TorrentResult> {
        val doc = Jsoup.parse(xml, baseUrl, Parser.xmlParser())
        val rowSelector = searchConfig.rows.selector.ifBlank { "item, entry" }
        val rows = doc.select(rowSelector)
        if (rows.isEmpty()) return emptyList()

        val results = mutableListOf<TorrentResult>()
        for (row in rows) {
            val parsed = extractResultFromElement(row, searchConfig.fields, baseUrl, identity)
            if (parsed != null && (parsed.magnet.isNotBlank() || parsed.infoHash.isNotBlank())) {
                results.add(parsed)
            }
        }
        return results
    }

    private fun parseJsonResults(
        jsonStr: String,
        searchConfig: CardigannSearchConfig,
        baseUrl: String,
        identity: MediaIdentity
    ): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()
        try {
            val rowPath = searchConfig.rows.selector.removePrefix("$.").trim()
            val jsonArray: JSONArray = if (jsonStr.trim().startsWith("[")) {
                JSONArray(jsonStr)
            } else {
                val obj = JSONObject(jsonStr)
                if (rowPath.isNotBlank()) {
                    extractJsonArrayByPath(obj, rowPath) ?: JSONArray()
                } else {
                    obj.optJSONArray("results")
                        ?: obj.optJSONArray("torrents")
                        ?: obj.optJSONArray("data")
                        ?: obj.optJSONArray("items")
                        ?: JSONArray()
                }
            }

            for (i in 0 until jsonArray.length()) {
                val itemObj = jsonArray.optJSONObject(i) ?: continue
                val parsed = extractResultFromJson(itemObj, searchConfig.fields, baseUrl, identity)
                if (parsed != null && (parsed.magnet.isNotBlank() || parsed.infoHash.isNotBlank())) {
                    results.add(parsed)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[$id] JSON parsing error: ${e.message}")
        }
        return results
    }

    private fun extractJsonArrayByPath(root: JSONObject, path: String): JSONArray? {
        val parts = path.split(".")
        var current: Any? = root
        for (p in parts) {
            if (current is JSONObject) {
                current = current.opt(p)
            } else {
                return null
            }
        }
        return current as? JSONArray
    }

    private fun extractResultFromElement(
        element: Element,
        fields: Map<String, CardigannFieldConfig>,
        baseUrl: String,
        identity: MediaIdentity
    ): TorrentResult? {
        val title = extractFieldValue(element, fields["title"], "title")
        if (title.isBlank()) return null

        var magnet = extractFieldValue(element, fields["magnet"], "magnet")
        var downloadUrl = extractFieldValue(element, fields["download"], "download")
        var infoHash = extractFieldValue(element, fields["infohash"], "infohash")
        val sizeStr = extractFieldValue(element, fields["size"], "size")
        val seedersStr = extractFieldValue(element, fields["seeders"], "seeders")
        val leechersStr = extractFieldValue(element, fields["leechers"], "leechers")
        val dateStr = extractFieldValue(element, fields["date"], "date")
        val rawCategory = extractFieldValue(element, fields["category"], "category")

        // Magnet / InfoHash resolution
        if (magnet.isBlank() && downloadUrl.startsWith("magnet:?", ignoreCase = true)) {
            magnet = downloadUrl
        }

        if (magnet.isNotBlank()) {
            val parsed = MagnetParser.parse(magnet)
            if (parsed != null && infoHash.isBlank()) {
                infoHash = parsed.infoHashHex
            }
        }

        if (infoHash.isBlank() && downloadUrl.isNotBlank() && !downloadUrl.startsWith("magnet:?")) {
            // Check if download URL contains 40-character hex infohash
            val hashMatch = Regex("""[0-9a-fA-F]{40}""").find(downloadUrl)
            if (hashMatch != null) {
                infoHash = hashMatch.value.lowercase()
            }
        }

        if (magnet.isBlank() && infoHash.isNotBlank()) {
            magnet = MagnetParser.buildMagnetUrl(infoHash.lowercase(), title)
        }

        if (magnet.isBlank() && downloadUrl.isNotBlank()) {
            // Keep the complete download URL without stripping query parameters
            val fullDownloadUrl = if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                downloadUrl
            } else {
                val sep = if (baseUrl.endsWith("/") || downloadUrl.startsWith("/")) "" else "/"
                "$baseUrl$sep$downloadUrl"
            }
            // Resolve infoHash from torrent file if needed
            infoHash = fetchInfoHashFromTorrentUrl(fullDownloadUrl)
            if (infoHash.isNotBlank()) {
                magnet = MagnetParser.buildMagnetUrl(infoHash.lowercase(), title)
            }
        }

        if (magnet.isBlank() && infoHash.isBlank()) return null

        val sizeBytes = sizeStr.toLongOrNull() ?: TorrentResult.parseBytes(sizeStr)
        val seeders = seedersStr.filter { it.isDigit() }.toIntOrNull() ?: 0
        val leechers = leechersStr.filter { it.isDigit() }.toIntOrNull() ?: 0

        val category = mapCategory(rawCategory, identity)
        val quality = extractQuality(title)
        val codec = extractCodec(title)
        val hdr = extractHdr(title)
        val audio = extractAudio(title)

        return TorrentResult(
            title = title,
            magnet = magnet,
            infoHash = infoHash.lowercase().trim(),
            size = sizeBytes,
            formattedSize = if (sizeBytes > 0) TorrentResult.formatBytes(sizeBytes) else sizeStr,
            seeders = seeders,
            leechers = leechers,
            source = name,
            category = category,
            quality = quality,
            codec = codec,
            hdr = hdr,
            audioChannels = audio,
            season = identity.season,
            episode = identity.episode,
            uploadDate = dateStr
        )
    }

    private fun extractResultFromJson(
        obj: JSONObject,
        fields: Map<String, CardigannFieldConfig>,
        baseUrl: String,
        identity: MediaIdentity
    ): TorrentResult? {
        val title = extractJsonFieldValue(obj, fields["title"], "title")
        if (title.isBlank()) return null

        var magnet = extractJsonFieldValue(obj, fields["magnet"], "magnet")
        val downloadUrl = extractJsonFieldValue(obj, fields["download"], "download")
        var infoHash = extractJsonFieldValue(obj, fields["infohash"], "infohash")
        val sizeStr = extractJsonFieldValue(obj, fields["size"], "size")
        val seedersStr = extractJsonFieldValue(obj, fields["seeders"], "seeders")
        val leechersStr = extractJsonFieldValue(obj, fields["leechers"], "leechers")
        val dateStr = extractJsonFieldValue(obj, fields["date"], "date")
        val rawCategory = extractJsonFieldValue(obj, fields["category"], "category")

        if (magnet.isBlank() && downloadUrl.startsWith("magnet:?", ignoreCase = true)) {
            magnet = downloadUrl
        }

        if (infoHash.isBlank() && magnet.isNotBlank()) {
            val parsed = MagnetParser.parse(magnet)
            if (parsed != null) infoHash = parsed.infoHashHex
        }

        if (infoHash.isBlank() && downloadUrl.isNotBlank()) {
            val hashMatch = Regex("""[0-9a-fA-F]{40}""").find(downloadUrl)
            if (hashMatch != null) infoHash = hashMatch.value.lowercase()
        }

        if (magnet.isBlank() && infoHash.isNotBlank()) {
            magnet = MagnetParser.buildMagnetUrl(infoHash.lowercase(), title)
        }

        if (magnet.isBlank() && infoHash.isBlank()) return null

        val sizeBytes = sizeStr.toLongOrNull() ?: TorrentResult.parseBytes(sizeStr)
        val seeders = seedersStr.filter { it.isDigit() }.toIntOrNull() ?: 0
        val leechers = leechersStr.filter { it.isDigit() }.toIntOrNull() ?: 0

        val category = mapCategory(rawCategory, identity)
        val quality = extractQuality(title)
        val codec = extractCodec(title)
        val hdr = extractHdr(title)
        val audio = extractAudio(title)

        return TorrentResult(
            title = title,
            magnet = magnet,
            infoHash = infoHash.lowercase().trim(),
            size = sizeBytes,
            formattedSize = if (sizeBytes > 0) TorrentResult.formatBytes(sizeBytes) else sizeStr,
            seeders = seeders,
            leechers = leechers,
            source = name,
            category = category,
            quality = quality,
            codec = codec,
            hdr = hdr,
            audioChannels = audio,
            season = identity.season,
            episode = identity.episode,
            uploadDate = dateStr
        )
    }

    private fun extractFieldValue(
        element: Element,
        fieldConfig: CardigannFieldConfig?,
        fallbackSelector: String
    ): String {
        if (fieldConfig == null) {
            // Default element text lookup
            val found = element.select(fallbackSelector).firstOrNull() ?: return ""
            return found.text().trim()
        }

        var rawValue = ""
        val selector = fieldConfig.selector
        val attribute = fieldConfig.attribute
        val textConstant = fieldConfig.text

        if (textConstant != null && textConstant.isNotBlank()) {
            rawValue = textConstant
        } else if (selector != null && selector.isNotBlank()) {
            val targetEl = element.select(selector).firstOrNull()
            if (targetEl != null) {
                rawValue = if (attribute != null && attribute.isNotBlank()) {
                    when (attribute.lowercase()) {
                        "text" -> targetEl.text()
                        "html" -> targetEl.html()
                        "href", "src" -> targetEl.absUrl(attribute).ifBlank { targetEl.attr(attribute) }
                        else -> targetEl.attr(attribute)
                    }
                } else {
                    targetEl.text()
                }
            }
        } else if (attribute != null && attribute.isNotBlank()) {
            rawValue = element.attr(attribute)
        } else {
            rawValue = element.text()
        }

        if (fieldConfig.caseMap != null && fieldConfig.caseMap.isNotEmpty()) {
            val mapped = fieldConfig.caseMap[rawValue.trim()] ?: fieldConfig.caseMap["*"]
            if (mapped != null) rawValue = mapped
        }

        if (fieldConfig.filters.isNotEmpty()) {
            rawValue = CardigannFilterEngine.applyFilters(rawValue, fieldConfig.filters)
        }

        if (rawValue.isBlank() && fieldConfig.default != null) {
            rawValue = fieldConfig.default
        }

        return rawValue.trim()
    }

    private fun extractJsonFieldValue(
        obj: JSONObject,
        fieldConfig: CardigannFieldConfig?,
        defaultKey: String
    ): String {
        val key = fieldConfig?.selector ?: defaultKey
        var rawValue = obj.optString(key, "")

        if (rawValue.isBlank() && key.contains(".")) {
            val parts = key.split(".")
            var cur: Any? = obj
            for (p in parts) {
                if (cur is JSONObject) {
                    cur = cur.opt(p)
                }
            }
            rawValue = cur?.toString() ?: ""
        }

        if (fieldConfig?.text != null && fieldConfig.text.isNotBlank()) {
            rawValue = fieldConfig.text
        }

        if (fieldConfig?.caseMap != null && fieldConfig.caseMap.isNotEmpty()) {
            val mapped = fieldConfig.caseMap[rawValue.trim()] ?: fieldConfig.caseMap["*"]
            if (mapped != null) rawValue = mapped
        }

        if (fieldConfig != null && fieldConfig.filters.isNotEmpty()) {
            rawValue = CardigannFilterEngine.applyFilters(rawValue, fieldConfig.filters)
        }

        if (rawValue.isBlank() && fieldConfig?.default != null) {
            rawValue = fieldConfig.default
        }

        return rawValue.trim()
    }

    private fun fetchInfoHashFromTorrentUrl(url: String): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return ""
            val bytes = resp.body?.bytes() ?: return ""
            val decoded = Bencode.decode(bytes) as? Map<*, *> ?: return ""
            val info = decoded["info"] as? Map<*, *> ?: return ""
            val infoEncoded = Bencode.encode(info)
            val sha1 = MessageDigest.getInstance("SHA-1").digest(infoEncoded)
            val hexSb = StringBuilder()
            for (b in sha1) {
                hexSb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            hexSb.toString().lowercase()
        } catch (_: Exception) {
            ""
        }
    }

    private fun mapCategory(rawCat: String, identity: MediaIdentity): String {
        val torznabCat = definition.caps.getTorznabCategory(rawCat) ?: rawCat
        return when {
            torznabCat.contains("movie", ignoreCase = true) || torznabCat.startsWith("2") || identity.mediaType.equals("movie", true) -> "Movies"
            torznabCat.contains("tv", ignoreCase = true) || torznabCat.startsWith("5") || identity.mediaType.equals("tv", true) -> "TV"
            torznabCat.contains("anime", ignoreCase = true) || torznabCat == "5070" || identity.mediaType.equals("anime", true) -> "Anime"
            torznabCat.contains("xxx", ignoreCase = true) || torznabCat.contains("adult", ignoreCase = true) || torznabCat.startsWith("6") -> "JAV/Adult"
            else -> "Other"
        }
    }

    private fun extractQuality(title: String): String {
        return when {
            title.contains("2160p", ignoreCase = true) || title.contains("4K", ignoreCase = true) || title.contains("UHD", ignoreCase = true) -> "4K UHD"
            title.contains("1080p", ignoreCase = true) || title.contains("FHD", ignoreCase = true) -> "1080p"
            title.contains("720p", ignoreCase = true) || title.contains("HD", ignoreCase = true) -> "720p"
            title.contains("480p", ignoreCase = true) || title.contains("SD", ignoreCase = true) -> "480p"
            else -> "1080p"
        }
    }

    private fun extractCodec(title: String): String {
        return when {
            title.contains("x265", ignoreCase = true) || title.contains("HEVC", ignoreCase = true) || title.contains("H.265", ignoreCase = true) -> "x265 HEVC"
            title.contains("AV1", ignoreCase = true) -> "AV1"
            title.contains("x264", ignoreCase = true) || title.contains("H.264", ignoreCase = true) || title.contains("AVC", ignoreCase = true) -> "x264"
            else -> ""
        }
    }

    private fun extractHdr(title: String): String {
        return when {
            title.contains("DV", ignoreCase = true) && title.contains("HDR", ignoreCase = true) -> "DV HDR10"
            title.contains("Dolby Vision", ignoreCase = true) -> "Dolby Vision"
            title.contains("HDR10+", ignoreCase = true) -> "HDR10+"
            title.contains("HDR", ignoreCase = true) -> "HDR"
            else -> ""
        }
    }

    private fun extractAudio(title: String): String {
        return when {
            title.contains("Atmos", ignoreCase = true) -> "Dolby Atmos"
            title.contains("7.1", ignoreCase = true) -> "7.1 Surround"
            title.contains("5.1", ignoreCase = true) -> "5.1 Surround"
            title.contains("AAC", ignoreCase = true) -> "AAC"
            title.contains("DTS", ignoreCase = true) -> "DTS"
            else -> ""
        }
    }

    private suspend fun resolveWorkingBaseUrl(): String = withContext(Dispatchers.IO) {
        if (isWorkingMirrorCached && activeMirror.isNotBlank()) return@withContext activeMirror

        val candidateLinks = (definition.links + definition.legacyLinks).distinct()
        if (candidateLinks.isEmpty()) return@withContext ""

        for (link in candidateLinks) {
            val clean = link.trimEnd('/')
            try {
                val req = Request.Builder()
                    .url(clean)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .head()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful || resp.code in 300..399 || resp.code == 403) {
                    activeMirror = clean
                    isWorkingMirrorCached = true
                    return@withContext clean
                }
            } catch (_: Exception) {
                // Try next link
            }
        }

        // Fallback to first link
        val fallback = candidateLinks.first().trimEnd('/')
        activeMirror = fallback
        isWorkingMirrorCached = true
        fallback
    }

    suspend fun test(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val testIdentity = MediaIdentity(title = "Big Buck Bunny", year = "2008", mediaType = "movie")
        try {
            val results = search("Big Buck Bunny", testIdentity)
            val duration = System.currentTimeMillis() - start
            if (results.isNotEmpty()) {
                Pair(true, "Online (${results.size} results in ${duration}ms)")
            } else {
                Pair(false, "No results returned (${duration}ms)")
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            Pair(false, "Error: ${e.message} (${duration}ms)")
        }
    }
}
