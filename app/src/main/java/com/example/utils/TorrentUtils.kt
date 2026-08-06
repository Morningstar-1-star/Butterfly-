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
}
