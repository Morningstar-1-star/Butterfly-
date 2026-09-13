package com.example.util

import com.example.metadata.JavIdParser
import com.example.model.VideoItem
import java.util.Locale

object SearchRelevanceScorer {

    /**
     * Compute a search relevance score for a VideoItem given the user query and optional corrected query.
     * Higher score means higher relevance. Unrelated items receive negative scores.
     */
    fun computeRelevanceScore(
        item: VideoItem,
        query: String,
        correctedQuery: String? = null
    ): Double {
        val title = item.title.lowercase(Locale.ROOT)
        val channel = (item.uploaderName ?: "").lowercase(Locale.ROOT)
        val desc = (item.description ?: "").lowercase(Locale.ROOT)
        val id = item.id.lowercase(Locale.ROOT)
        val combined = "$title $channel $desc $id"

        val qClean = query.lowercase(Locale.ROOT).trim()
        val cClean = correctedQuery?.lowercase(Locale.ROOT)?.trim()

        var score = 0.0

        // 1. Specialized JAV Code Matching (Highest Precision)
        val javCode = JavIdParser.parse(query) ?: (if (cClean != null) JavIdParser.parse(cClean) else null)
        if (javCode != null) {
            val codeNorm = javCode.lowercase(Locale.ROOT)
            val codeCompact = codeNorm.replace("-", "")
            val codeSpace = codeNorm.replace("-", " ")
            val codeUnder = codeNorm.replace("-", "_")

            val matchesJav = combined.contains(codeNorm) ||
                    combined.contains(codeCompact) ||
                    combined.contains(codeSpace) ||
                    combined.contains(codeUnder)

            if (matchesJav) {
                // Massive bonus for exact JAV code match
                score += 10000.0
                if (title.contains(codeNorm) || title.contains(codeCompact)) {
                    score += 2000.0
                }
            } else {
                // If query is specifically a JAV code, penalize any non-matching video
                val isStrictJavQuery = qClean == codeNorm || qClean == codeCompact || qClean.length <= codeNorm.length + 3
                if (isStrictJavQuery) {
                    return -10000.0
                }
            }
        }

        // 2. Specialized Adult Performer / Model Matching
        val detectedModel = AdultModelMatcher.findModel(query) ?: (if (cClean != null) AdultModelMatcher.findModel(cClean) else null)
        if (detectedModel != null) {
            val matchesModel = AdultModelMatcher.matchesModel(item, detectedModel)
            if (matchesModel) {
                score += 8000.0
                if (title.contains(detectedModel.primaryName.lowercase(Locale.ROOT))) {
                    score += 1500.0
                }
            } else {
                // If the user query was basically just the model name, discard unrelated videos
                val isPureModelQuery = detectedModel.aliases.any { alias ->
                    qClean == alias || SmartSearchSanitizer.levenshteinDistance(qClean, alias) <= 1
                }
                if (isPureModelQuery) {
                    return -8000.0
                }
            }
        }

        // 3. Exact & Prefix Phrase Matches
        if (title == qClean || (cClean != null && title == cClean)) {
            score += 5000.0
        } else if (title.startsWith(qClean) || (cClean != null && title.startsWith(cClean))) {
            score += 2500.0
        } else if (title.contains(qClean)) {
            score += 1800.0
        } else if (cClean != null && title.contains(cClean)) {
            score += 1500.0
        }

        // 4. Token-level matching (Supporting Multi-Language / CJK Unicode Characters)
        val queryTokens = qClean.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        val correctedTokens = cClean?.split(Regex("[^\\p{L}\\p{N}]+"))?.filter { it.length >= 2 } ?: emptyList()
        val allTokens = (queryTokens + correctedTokens).distinct()

        if (allTokens.isNotEmpty()) {
            var matchedTokens = 0
            val titleWords = title.split(Regex("[^\\p{L}\\p{N}]+")).toSet()

            for (token in allTokens) {
                if (titleWords.contains(token)) {
                    matchedTokens++
                    score += 300.0
                } else if (title.contains(token)) {
                    matchedTokens++
                    score += 200.0
                } else if (channel.contains(token)) {
                    matchedTokens++
                    score += 120.0
                } else if (desc.contains(token)) {
                    score += 40.0
                } else {
                    // Check fuzzy token match
                    val hasFuzzy = titleWords.any { w ->
                        if (w.length >= 4 && token.length >= 4) {
                            val dist = SmartSearchSanitizer.levenshteinDistance(w, token)
                            dist <= 1 || (dist <= 2 && (w.length >= 7 || token.length >= 7))
                        } else false
                    }
                    if (hasFuzzy) {
                        matchedTokens++
                        score += 100.0
                    }
                }
            }

            val qTokensCount = queryTokens.size.coerceAtLeast(1)
            val cTokensCount = correctedTokens.size.coerceAtLeast(1)
            if (matchedTokens >= qTokensCount || matchedTokens >= cTokensCount) {
                score += 800.0
            } else if (matchedTokens == 0 && javCode == null && detectedModel == null) {
                // Severe penalty if video matches NONE of the search terms!
                score -= 5000.0
            }
        }

        // 5. Popularity tie-breaker
        val views = item.viewCount
        if (views > 0) {
            val viewBonus = kotlin.math.log10(views.toDouble().coerceAtLeast(1.0)) * 5.0
            score += viewBonus.coerceIn(0.0, 50.0)
        }

        // 6. Prefer videos with thumbnail
        if (!item.thumbnailUrl.isNullOrBlank()) {
            score += 15.0
        }

        return score
    }

    /**
     * Rank search results strictly by relevance.
     * Only relevant videos matching query tokens, model, or JAV code are returned.
     * Completely unrelated videos are strictly omitted.
     */
    fun rankSearchResults(
        items: List<VideoItem>,
        query: String,
        correctedQuery: String? = null
    ): List<VideoItem> {
        if (items.isEmpty() || query.isBlank()) return items

        val scored = items.map { item ->
            item to computeRelevanceScore(item, query, correctedQuery)
        }

        val relevant = scored
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first }

        if (relevant.isNotEmpty()) {
            return relevant
        }

        // Only allow weak matches, never items that matched zero tokens or failed model/JAV tests
        return scored
            .filter { it.second > -500.0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
