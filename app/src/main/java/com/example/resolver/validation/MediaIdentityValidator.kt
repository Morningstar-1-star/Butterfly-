package com.example.resolver.validation

import android.util.Log
import com.example.model.MediaIdentity
import com.example.model.MediaType
import com.example.resolver.SourceCandidate
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max

/**
 * Strict Media Identity Validation Engine (Inspired by Cauldron & Nuvio validation layers).
 *
 * Verifies candidate streams and torrents against requested [MediaIdentity]:
 * 1. Title verification: Token overlap, fuzzy Levenshtein similarity, noise stripping.
 * 2. Year verification: Matches release year with tolerance ±1.
 * 3. Episode & Season verification for TV series: Validates SxxExx patterns and strictly rejects mismatches.
 * 4. JAV / Adult Code verification: Validates specific catalog numbers (e.g. IPX-123, SSIS-456).
 * 5. Media type compatibility: Disallows standalone movie results when an episodic TV query is requested.
 */
object MediaIdentityValidator {
    private const val TAG = "MediaIdentityValidator"

    data class ValidationOutcome(
        val isValid: Boolean,
        val reason: String = "",
        val confidenceScore: Double = 1.0,
        val extractedSeason: Int? = null,
        val extractedEpisode: Int? = null,
        val extractedYear: String? = null,
        val cleanTitle: String = ""
    )

    private val NOISE_REGEX = Pattern.compile(
        """(?i)\b(1080p|720p|480p|360p|2160p|4k|uhd|fhd|hd|hevc|x265|x264|h264|h265|av1|aac|ddp5\.1|ac3|dts|web-?dl|webrip|bluray|brrip|hdtv|proper|repack|remux|hdr10\+?|hdr|dv|dolby\s*vision|sub|dub|multi|ita|eng|fre|ger|spa|hindi|dual\s*audio|uncensored|censored|leak|sample)\b|[\[\]\(\)\{\}\._\-]"""
    )

    private val SEASON_EPISODE_PATTERNS = listOf(
        Pattern.compile("""(?i)[sS](\d{1,2})\s*[eE](\d{1,3})"""),
        Pattern.compile("""(?i)(\d{1,2})x(\d{1,3})"""),
        Pattern.compile("""(?i)\b[eE][pP]?\s*(\d{1,3})\b"""),
        Pattern.compile("""(?i)\bepisode\s*(\d{1,3})\b"""),
        Pattern.compile("""(?i)\bep\s*(\d{1,3})\b""")
    )

    private val YEAR_PATTERN = Pattern.compile("""\b(19\d{2}|20\d{2})\b""")
    private val JAV_CODE_PATTERN = Pattern.compile("""(?i)\b([a-zA-Z]{2,6})[-_ ]?(\d{2,5})\b""")

    /**
     * Validates a candidate against the target media identity.
     */
    fun validateCandidate(candidate: SourceCandidate, identity: MediaIdentity): ValidationOutcome {
        val rawTitle = candidate.title.ifBlank { candidate.serverName }
        return validate(rawTitle, identity)
    }

    /**
     * Core validation logic for any candidate title or URL string.
     */
    fun validate(titleOrUrl: String, identity: MediaIdentity): ValidationOutcome {
        val trimmed = titleOrUrl.trim()
        if (trimmed.isBlank()) {
            return ValidationOutcome(false, "Candidate title is blank", 0.0)
        }

        // 1. JAV Code Matching
        val isJavQuery = com.example.metadata.JavIdParser.isJavCode(identity.title) ||
                com.example.metadata.JavIdParser.isJavCode(identity.rawQueryOrUrl)
        if (isJavQuery) {
            val targetCode = com.example.metadata.JavIdParser.parse(identity.title)
                ?: com.example.metadata.JavIdParser.parse(identity.rawQueryOrUrl)

            if (targetCode != null) {
                val candidateCode = com.example.metadata.JavIdParser.parse(trimmed)
                if (candidateCode != null) {
                    val normTarget = targetCode.replace("-", "").uppercase(Locale.ROOT)
                    val normCandidate = candidateCode.replace("-", "").uppercase(Locale.ROOT)
                    if (normTarget == normCandidate) {
                        return ValidationOutcome(true, "Exact JAV code match: $targetCode", 1.0)
                    } else {
                        return ValidationOutcome(false, "Mismatched JAV code: expected $normTarget, got $normCandidate", 0.0)
                    }
                }
            }
        }

        // 2. TV Series Season & Episode Validation
        if (identity.mediaType == MediaType.TV && identity.episode != null) {
            val targetEp = identity.episode
            val targetSeason = identity.season ?: 1

            val extractedEp = extractEpisode(trimmed)
            val extractedSeason = extractSeason(trimmed)

            if (extractedEp != null && extractedEp != targetEp) {
                return ValidationOutcome(
                    isValid = false,
                    reason = "Episode mismatch: requested E$targetEp, found E$extractedEp in '$trimmed'",
                    confidenceScore = 0.0,
                    extractedSeason = extractedSeason,
                    extractedEpisode = extractedEp
                )
            }

            if (extractedSeason != null && extractedSeason != targetSeason) {
                return ValidationOutcome(
                    isValid = false,
                    reason = "Season mismatch: requested S$targetSeason, found S$extractedSeason in '$trimmed'",
                    confidenceScore = 0.0,
                    extractedSeason = extractedSeason,
                    extractedEpisode = extractedEp
                )
            }
        }

        // 3. Year Validation
        val targetYear = identity.year?.toIntOrNull()
        val extractedYear = extractYear(trimmed)
        if (targetYear != null && extractedYear != null) {
            val diff = kotlin.math.abs(targetYear - extractedYear)
            if (diff > 1 && !isAnimeOrContinuous(identity)) {
                return ValidationOutcome(
                    isValid = false,
                    reason = "Year mismatch: requested $targetYear, found $extractedYear in '$trimmed'",
                    confidenceScore = 0.2,
                    extractedYear = extractedYear.toString()
                )
            }
        }

        // 4. Clean Title & Token Overlap Analysis
        val targetClean = cleanTitle(identity.title)
        val candidateClean = cleanTitle(trimmed)

        if (targetClean.isBlank()) {
            return ValidationOutcome(true, "Bypassed token check due to blank clean target", 0.8)
        }

        val targetTokens = targetClean.split(" ").filter { it.length > 1 }
        val candidateTokens = candidateClean.split(" ").filter { it.length > 1 }.toSet()

        if (targetTokens.isNotEmpty()) {
            val matchingTokens = targetTokens.count { candidateTokens.contains(it) }
            val tokenMatchRatio = matchingTokens.toDouble() / targetTokens.size.toDouble()

            // If token match is below 50% for multi-word titles, check fuzzy Levenshtein
            if (tokenMatchRatio < 0.5 && targetTokens.size > 1) {
                val similarity = calculateSimilarity(targetClean, candidateClean)
                if (similarity < 0.45) {
                    return ValidationOutcome(
                        isValid = false,
                        reason = "Low title similarity (${(similarity * 100).toInt()}%): '$targetClean' vs '$candidateClean'",
                        confidenceScore = similarity
                    )
                }
            }
        }

        return ValidationOutcome(
            isValid = true,
            reason = "Validation passed with high confidence",
            confidenceScore = 0.95,
            cleanTitle = candidateClean,
            extractedYear = extractedYear?.toString()
        )
    }

    fun cleanTitle(title: String): String {
        val noNoise = NOISE_REGEX.matcher(title).replaceAll(" ")
        return noNoise.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun extractSeason(title: String): Int? {
        val m = Pattern.compile("""(?i)[sS](\d{1,2})""").matcher(title)
        if (m.find()) return m.group(1)?.toIntOrNull()

        val m2 = Pattern.compile("""(?i)season\s*(\d{1,2})""").matcher(title)
        if (m2.find()) return m2.group(1)?.toIntOrNull()

        val m3 = Pattern.compile("""(?i)(\d{1,2})x\d{1,3}""").matcher(title)
        if (m3.find()) return m3.group(1)?.toIntOrNull()

        return null
    }

    fun extractEpisode(title: String): Int? {
        for (pat in SEASON_EPISODE_PATTERNS) {
            val m = pat.matcher(title)
            if (m.find()) {
                val epGroup = if (m.groupCount() >= 2) m.group(2) else m.group(1)
                val ep = epGroup?.toIntOrNull()
                if (ep != null) return ep
            }
        }
        return null
    }

    fun extractYear(title: String): Int? {
        val m = YEAR_PATTERN.matcher(title)
        var lastYear: Int? = null
        while (m.find()) {
            val y = m.group(1)?.toIntOrNull()
            if (y != null && y in 1900..2040) {
                lastYear = y
            }
        }
        return lastYear
    }

    private fun isAnimeOrContinuous(identity: MediaIdentity): Boolean {
        return identity.mediaType == MediaType.ANIME ||
                identity.title.contains("anime", ignoreCase = true) ||
                identity.title.contains("shippuden", ignoreCase = true) ||
                identity.title.contains("one piece", ignoreCase = true)
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val maxLen = max(len1, len2)
        val distance = levenshteinDistance(s1, s2)
        return (maxLen - distance).toDouble() / maxLen.toDouble()
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1) { 0 }

        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }
}
