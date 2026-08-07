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
            lower.contains("knightcrawler") -> "KnightCrawler"
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

        // Extract File Size if available
        val sizeRegex = Regex("""(\d+(?:\.\d+)?\s*(?:GB|MB))""", RegexOption.IGNORE_CASE)
        val sizeMatch = sizeRegex.find(rawLabel)?.value

        val qualityTag = if (tags.isNotEmpty()) "$quality ${tags.joinToString(" ")}" else quality

        val mainLabel = when {
            provider.isNotEmpty() -> "$qualityTag • $provider"
            else -> qualityTag
        }

        return if (sizeMatch != null) "$mainLabel ($sizeMatch)" else mainLabel
    }
}
