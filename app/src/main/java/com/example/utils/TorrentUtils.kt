package com.example.utils

import java.net.URLEncoder

object TorrentUtils {

    private val WEBTORRENT_TRACKERS = listOf(
        "wss://tracker.openwebtorrent.com",
        "wss://tracker.btorrent.xyz",
        "wss://tracker.webtorrent.dev",
        "wss://tracker.files.fm:7070/announce"
    )

    private val STANDARD_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.bittor.org:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.openbittorrent.com:80",
        "udp://tracker.coppersurfer.tk:6969",
        "udp://glotorrents.pw:6969/announce",
        "udp://tracker.cyberia.is:6969/announce"
    )

    val ALL_TRACKERS = WEBTORRENT_TRACKERS + STANDARD_TRACKERS

    /**
     * Formats and enriches magnet links or raw info hashes with standard BitTorrent (udp://)
     * and WebTorrent (wss://) trackers required for WebTor and browser clients to discover peers.
     */
    fun formatMagnetUrl(rawOrHash: String, title: String? = null): String {
        var clean = rawOrHash.trim()
            .replace("&#038;", "&")
            .replace("&amp;", "&")
            .replace("&#38;", "&")

        if (clean.isEmpty()) return ""

        if (!clean.startsWith("magnet:?")) {
            clean = "magnet:?xt=urn:btih:$clean"
        }

        val sb = StringBuilder(clean)

        if (title != null && !clean.contains("&dn=")) {
            val encodedTitle = try { URLEncoder.encode(title, "UTF-8") } catch (e: Exception) { title }
            sb.append("&dn=").append(encodedTitle)
        }

        ALL_TRACKERS.forEach { tracker ->
            if (!clean.contains(tracker)) {
                val encodedTracker = try { URLEncoder.encode(tracker, "UTF-8") } catch (e: Exception) { tracker }
                sb.append("&tr=").append(encodedTracker)
            }
        }

        return sb.toString()
    }

    /**
     * Cleans up long, cluttered torrent titles and provider names into short, scannable labels.
     * Examples: "1080p BluRay • Torrentio", "1080p WEB-DL • YTS (1.8 GB)", "HD • VidSrc Pro"
     */
    fun formatCleanQualityLabel(rawLabel: String, fallbackProvider: String = ""): String {
        if (rawLabel.isBlank()) return "HD Stream"

        var text = rawLabel
            .replace("\n", " ")
            .replace("\r", " ")

        // Remove redundant Server prefixes like "Server 14 (", "Main 1 ("
        text = text.replace(Regex("""^(Server|Main)\s*\d+\s*\(?""", RegexOption.IGNORE_CASE), "")
        if (text.endsWith(")") && text.count { it == ')' } > text.count { it == '(' }) {
            text = text.dropLast(1).trim()
        }

        val lower = text.lowercase()

        // Detect Provider Name
        val provider = when {
            lower.contains("vidsrc pro") -> "VidSrc Pro"
            lower.contains("vidsrc mirror") -> "VidSrc Mirror"
            lower.contains("vidsrc") -> "VidSrc"
            lower.contains("superembed") -> "SuperEmbed"
            lower.contains("2embed") -> "2Embed"
            lower.contains("torrentio") -> "Torrentio"
            lower.contains("yts") -> "YTS"
            lower.contains("eztv") -> "EZTV"
            lower.contains("nyaa") -> "Nyaa"
            lower.contains("tmdb") -> "TMDB"
            lower.contains("mediafusion") -> "MediaFusion"
            lower.contains("torrent_api") || lower.contains("torrent api") -> "Torrent API"
            fallbackProvider.isNotEmpty() -> fallbackProvider
            else -> ""
        }

        // Detect Quality / Resolution
        val quality = when {
            lower.contains("4k") || lower.contains("2160p") || lower.contains("uhd") -> "4K UHD"
            lower.contains("1080p") || lower.contains("fhd") -> "1080p"
            lower.contains("720p") || lower.contains("hd") -> "720p"
            lower.contains("480p") || lower.contains("sd") -> "480p"
            else -> if (provider.contains("VidSrc") || provider.contains("Embed")) "HD" else "1080p"
        }

        // Detect Source / Codec Tags
        val tags = mutableListOf<String>()
        if (lower.contains("bluray") || lower.contains("bdrip") || lower.contains("brrip")) tags.add("BluRay")
        else if (lower.contains("web-dl") || lower.contains("webrip") || lower.contains("web")) tags.add("WEB-DL")
        else if (lower.contains("hdtv")) tags.add("HDTV")

        if (lower.contains("hdr")) tags.add("HDR")
        if (lower.contains("dv") || lower.contains("dovi")) tags.add("DV")

        // Detect Language Tags (Indian & Regional Languages + Multi Audio / Dubbed)
        val langTags = mutableListOf<String>()
        if (lower.contains("hindi") || lower.contains("hin")) langTags.add("Hindi")
        if (lower.contains("tamil") || lower.contains("tam")) langTags.add("Tamil")
        if (lower.contains("malayalam") || lower.contains("mal")) langTags.add("Malayalam")
        if (lower.contains("kannada") || lower.contains("kan")) langTags.add("Kannada")
        if (lower.contains("telugu") || lower.contains("tel")) langTags.add("Telugu")
        if (lower.contains("bengali") || lower.contains("ben")) langTags.add("Bengali")
        if (lower.contains("punjabi") || lower.contains("pun")) langTags.add("Punjabi")
        if (lower.contains("dual audio") || lower.contains("dual-audio")) langTags.add("Dual Audio")
        else if (lower.contains("multi audio") || lower.contains("multi-audio") || lower.contains("multiaudio") || lower.contains("multi")) langTags.add("Multi")
        else if (lower.contains("dubbed") || lower.contains("dub")) langTags.add("Dubbed")
        else if (lower.contains("english") || lower.contains("eng")) {
            if (langTags.isNotEmpty()) langTags.add("Eng")
        }

        // Extract File Size if available
        val sizeRegex = Regex("""(\d+(?:\.\d+)?\s*(?:GB|MB))""", RegexOption.IGNORE_CASE)
        val sizeMatch = sizeRegex.find(rawLabel)?.value

        val qualityTag = if (tags.isNotEmpty()) "$quality ${tags.joinToString(" ")}" else quality

        val langSuffix = if (langTags.isNotEmpty()) " [${langTags.distinct().joinToString(" + ")}]" else ""

        val mainLabel = when {
            provider.isNotEmpty() -> "$qualityTag • $provider$langSuffix"
            else -> "$qualityTag$langSuffix"
        }

        return if (sizeMatch != null) "$mainLabel ($sizeMatch)" else mainLabel
    }

    /**
     * Extracts a 40-character hexadecimal infoHash from a magnet link or raw hash string.
     */
    fun extractInfoHash(rawOrMagnet: String): String? {
        if (rawOrMagnet.isBlank()) return null
        val clean = rawOrMagnet.trim()
        val match = Regex("""([a-fA-F0-9]{40})""").find(clean)
        if (match != null) return match.value.lowercase()
        val match32 = Regex("""([a-zA-Z2-7]{32})""").find(clean)
        return match32?.value?.lowercase()
    }

    /**
     * Extracts seeders count from torrent title/description if present (e.g., "👤 42", "S: 120").
     */
    fun parseSeeders(text: String): Int {
        val lower = text.lowercase()
        val match = Regex("""(?:👤|s:|seeders?:?\s*)(\d+)""").find(lower)
        if (match != null) {
            return match.groupValues[1].toIntOrNull() ?: 0
        }
        return 0
    }

    /**
     * Extracts size in bytes from text (e.g., "1.8 GB", "750 MB").
     */
    fun parseSizeBytes(text: String): Long {
        val match = Regex("""(\d+(?:\.\d+)?)\s*(GB|MB|KB)""", RegexOption.IGNORE_CASE).find(text)
            ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (match.groupValues[2].uppercase()) {
            "GB" -> (value * 1024 * 1024 * 1024).toLong()
            "MB" -> (value * 1024 * 1024).toLong()
            "KB" -> (value * 1024).toLong()
            else -> 0L
        }
    }

    /**
     * Calculates a health/ranking score for a torrent option based on seeders, size, resolution, and provider.
     */
    fun calculateTorrentScore(
        qualityLabel: String,
        seeders: Int = 0,
        sizeBytes: Long = 0L,
        providerName: String = ""
    ): Int {
        var score = 0
        val lower = qualityLabel.lowercase()

        // Resolution score
        when {
            lower.contains("4k") || lower.contains("2160p") -> score += 100
            lower.contains("1080p") -> score += 80
            lower.contains("720p") -> score += 50
            else -> score += 30
        }

        // Codec score
        if (lower.contains("av1")) score += 30
        else if (lower.contains("hevc") || lower.contains("h265") || lower.contains("x265")) score += 20

        // Seeders score
        score += minOf(seeders * 2, 100)

        // File size sanity check (prefer 1GB - 15GB for streaming)
        val sizeGb = sizeBytes.toDouble() / (1024 * 1024 * 1024)
        when {
            sizeGb in 1.0..10.0 -> score += 40
            sizeGb in 0.5..1.0 -> score += 20
            sizeGb > 25.0 -> score -= 10 // Huge 40GB+ files take long to buffer
        }

        // Provider reliability score
        if (providerName.contains("Torrentio", ignoreCase = true)) score += 30
        if (providerName.contains("YTS", ignoreCase = true)) score += 25

        return score
    }
}
