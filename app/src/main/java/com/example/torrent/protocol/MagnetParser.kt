package com.example.torrent.protocol

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ParsedMagnet(
    val infoHashHex: String,
    val infoHashBytes: ByteArray,
    val displayName: String,
    val trackers: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedMagnet) return false
        return infoHashHex.equals(other.infoHashHex, ignoreCase = true)
    }

    override fun hashCode(): Int {
        return infoHashHex.lowercase().hashCode()
    }
}

object MagnetParser {

    val DEFAULT_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://9.rarbg.to:2710/announce",
        "udp://9.rarbg.me:2710/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://p4p.arenabg.com:1337/announce",
        "udp://movies.zsw.ca:6969/announce",
        "http://tracker.opentrackr.org:1337/announce",
        "https://tracker.tamersunion.org:443/announce"
    )

    fun parse(magnetOrHash: String): ParsedMagnet? {
        val trimmed = magnetOrHash.trim()
        if (trimmed.isEmpty()) return null

        var rawHash = ""
        var name = ""
        val trackers = mutableListOf<String>()

        if (trimmed.startsWith("magnet:?", ignoreCase = true)) {
            val query = trimmed.substring(8)
            val pairs = query.split("&")
            for (pair in pairs) {
                val parts = pair.split("=", limit = 2)
                if (parts.size != 2) continue
                val key = parts[0].trim()
                val value = try {
                    URLDecoder.decode(parts[1].trim(), StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    parts[1].trim()
                }

                when (key.lowercase()) {
                    "xt" -> {
                        if (value.startsWith("urn:btih:", ignoreCase = true)) {
                            rawHash = value.substring(9)
                        }
                    }
                    "dn" -> name = value
                    "tr" -> {
                        if (value.isNotBlank() && !trackers.contains(value)) {
                            trackers.add(value)
                        }
                    }
                }
            }
        } else {
            rawHash = trimmed
        }

        val cleanHash = rawHash.replace(Regex("[^a-zA-Z0-9]"), "").trim()
        val (hexHash, hashBytes) = normalizeHash(cleanHash) ?: return null

        if (trackers.isEmpty()) {
            trackers.addAll(DEFAULT_TRACKERS)
        } else {
            DEFAULT_TRACKERS.forEach { tr ->
                if (!trackers.contains(tr)) trackers.add(tr)
            }
        }

        return ParsedMagnet(
            infoHashHex = hexHash.lowercase(),
            infoHashBytes = hashBytes,
            displayName = if (name.isNotBlank()) name else "Torrent_$hexHash",
            trackers = trackers
        )
    }

    fun buildMagnetUrl(infoHash: String, name: String, trackers: List<String> = DEFAULT_TRACKERS): String {
        val encodedName = try {
            URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            name
        }
        val sb = StringBuilder("magnet:?xt=urn:btih:$infoHash&dn=$encodedName")
        trackers.forEach { tr ->
            val encTr = try {
                URLEncoder.encode(tr, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                tr
            }
            sb.append("&tr=$encTr")
        }
        return sb.toString()
    }

    private fun normalizeHash(raw: String): Pair<String, ByteArray>? {
        if (raw.length == 40 && raw.matches(Regex("^[0-9a-fA-F]{40}$"))) {
            val bytes = ByteArray(20)
            for (i in 0 until 20) {
                val byteStr = raw.substring(i * 2, i * 2 + 2)
                bytes[i] = byteStr.toInt(16).toByte()
            }
            return Pair(raw.lowercase(), bytes)
        }

        if (raw.length == 32 && raw.matches(Regex("^[2-7a-zA-Z]{32}$"))) {
            val bytes = decodeBase32(raw) ?: return null
            val hexSb = StringBuilder()
            for (b in bytes) {
                hexSb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            return Pair(hexSb.toString().lowercase(), bytes)
        }

        return null
    }

    private fun decodeBase32(input: String): ByteArray? {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val upper = input.uppercase()
        var buffer = 0
        var bitsLeft = 0
        val out = mutableListOf<Byte>()

        for (c in upper) {
            val valIndex = base32Chars.indexOf(c)
            if (valIndex < 0) return null
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        if (out.size != 20) return null
        return out.toByteArray()
    }
}
