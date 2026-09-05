package com.example.util

import com.example.model.PlayableStreamOption
import com.example.model.ProviderType

object StreamCategorizer {

    fun detectSourceName(option: PlayableStreamOption): String {
        if (option.sourceName.isNotBlank()) return option.sourceName
        val label = option.qualityLabel.lowercase()
        return when {
            option.providerType == ProviderType.VEGA || label.contains("vega") -> "Vega"
            label.contains("vidrock") -> "VidRock"
            label.contains("vidsrc") -> "VidSrc"
            label.contains("seedr") -> "Seedr"
            label.contains("torrentio") -> "Torrentio"
            label.contains("yts") -> "YTS"
            label.contains("eztv") -> "EZTV"
            label.contains("nyaa") -> "Nyaa"
            label.contains("1337x") -> "1337x"
            label.contains("torrentgalaxy") || label.contains("tgx") -> "TorrentGalaxy"
            label.contains("bilibili") -> "Bilibili"
            label.contains("youtube") -> "YouTube"
            option.providerType == ProviderType.TORRENT -> "Torrentio"
            option.providerType == ProviderType.DIRECT -> "Direct CDN"
            else -> "Default"
        }
    }

    fun detectQualityCategory(option: PlayableStreamOption): String {
        if (option.qualityCategory.isNotBlank()) return option.qualityCategory
        return detectQualityFromText(option.qualityLabel, option.isDolbyVision, option.isHdr)
    }

    fun detectQualityFromText(text: String, isDolbyVisionHint: Boolean = false, isHdrHint: Boolean = false): String {
        val lower = text.lowercase()
        return when {
            isDolbyVisionHint || lower.contains("dolby vision") || lower.contains("dovi") || lower.contains(" dv ") || lower.endsWith(" dv") || lower.startsWith("dv ") -> "Dolby Vision"
            lower.contains("2160p") || lower.contains("4k") || lower.contains("uhd") -> "4K"
            lower.contains("1080p") || lower.contains("fhd") -> "1080p"
            lower.contains("720p") || lower.contains("hd") -> "720p"
            lower.contains("480p") || lower.contains("sd") -> "480p"
            lower.contains("360p") -> "360p"
            isHdrHint || lower.contains("hdr") -> "HDR"
            else -> "1080p"
        }
    }

    fun extractSizeFromText(text: String): String {
        val regex = Regex("""(?:\(|\s)(\d+(?:\.\d+)?\s*(?:GB|MB|KB|B))(?:\)|\s|$)""", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    fun extractCleanSearchTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""\s*\(\d{4}\).*"""), "")
            .replace(Regex("""\s*\[.*?\]"""), "")
            .replace(Regex("""(?i)\b(s\d{1,2}(e\d{1,2})?|season\s*\d+|episode\s*\d+)\b.*"""), "")
            .replace(Regex("""(?i)\b(1080p|720p|480p|2160p|4k|bluray|web-?dl|hdr|hevc|x264|x265)\b.*"""), "")
            .trim()
    }

    fun titlesMatch(targetTitle: String, candidateTitle: String): Boolean {
        val cleanTarget = extractCleanSearchTitle(targetTitle).lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .trim()
        val cleanCandidate = candidateTitle.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .trim()

        if (cleanTarget.isBlank() || cleanCandidate.isBlank()) return false

        // 1. Direct contains check
        if (cleanCandidate.contains(cleanTarget) || cleanTarget.contains(cleanCandidate)) {
            return true
        }

        // 2. Tokenized word comparison
        val targetTokens = cleanTarget.split(Regex("""\s+""")).filter { it.length > 2 }
        if (targetTokens.isEmpty()) return false

        // All significant target tokens present
        if (targetTokens.all { cleanCandidate.contains(it) }) {
            return true
        }

        // For multi-word titles (like Spider-Man No Way Home, Mushoku Tensei), match top 2 words
        if (targetTokens.size >= 2) {
            val primaryTokens = targetTokens.take(2)
            if (primaryTokens.all { cleanCandidate.contains(it) }) {
                return true
            }
        }

        return false
    }
}
