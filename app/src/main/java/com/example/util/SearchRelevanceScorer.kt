package com.example.util

import com.example.model.VideoItem
import java.util.Locale

object SearchRelevanceScorer {

    /**
     * Compute a search relevance score for a VideoItem given the user query and optional corrected query.
     * Higher score means higher relevance.
     */
    fun computeRelevanceScore(
        item: VideoItem,
        query: String,
        correctedQuery: String? = null
    ): Double {
        val title = item.title.lowercase(Locale.ROOT)
        val channel = (item.uploaderName ?: "").lowercase(Locale.ROOT)
        val desc = (item.description ?: "").lowercase(Locale.ROOT)
        val qClean = query.lowercase(Locale.ROOT).trim()
        val cClean = correctedQuery?.lowercase(Locale.ROOT)?.trim()

        var score = 0.0

        // 1. Exact match bonuses
        if (title == qClean || (cClean != null && title == cClean)) {
            score += 2500.0
        }

        // 2. Starts with query bonus
        if (title.startsWith(qClean) || (cClean != null && title.startsWith(cClean))) {
            score += 1200.0
        }

        // 3. Substring full phrase match bonus
        if (title.contains(qClean)) {
            score += 900.0
        } else if (cClean != null && title.contains(cClean)) {
            score += 850.0
        }

        // 4. Token-level matching
        val queryTokens = qClean.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 2 }
        val correctedTokens = cClean?.split(Regex("[^a-zA-Z0-9]+"))?.filter { it.length >= 2 } ?: emptyList()
        val allTokens = (queryTokens + correctedTokens).distinct()

        if (allTokens.isNotEmpty()) {
            var matchedTokens = 0
            val titleWords = title.split(Regex("[^a-zA-Z0-9]+")).toSet()

            for (token in allTokens) {
                if (titleWords.contains(token)) {
                    matchedTokens++
                    score += 250.0
                } else if (title.contains(token)) {
                    matchedTokens++
                    score += 150.0
                } else if (channel.contains(token)) {
                    score += 80.0
                } else if (desc.contains(token)) {
                    score += 20.0
                } else {
                    // Check fuzzy token match (e.g. slight typo in video title)
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

            // High reward if all tokens from either query or corrected query matched
            val qTokensCount = queryTokens.size.coerceAtLeast(1)
            val cTokensCount = correctedTokens.size.coerceAtLeast(1)
            if (matchedTokens >= qTokensCount || matchedTokens >= cTokensCount) {
                score += 600.0
            } else if (matchedTokens == 0) {
                // Severe penalty if video matches NONE of the search terms!
                score -= 1500.0
            }
        }

        // 5. Popularity tie-breaker (capped so it never overrides relevance)
        val views = item.viewCount
        if (views > 0) {
            val viewBonus = kotlin.math.log10(views.toDouble().coerceAtLeast(1.0)) * 6.0
            score += viewBonus.coerceIn(0.0, 60.0)
        }

        // 6. Prefer videos with thumbnail
        if (!item.thumbnailUrl.isNullOrBlank()) {
            score += 15.0
        }

        return score
    }

    /**
     * Rank search results strictly by relevance.
     * Guaranteed that matched videos are at the top, and random unrelated videos are pushed down.
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

        val relevant = scored.filter { it.second > 0.0 }.sortedByDescending { it.second }.map { it.first }
        val marginal = scored.filter { it.second <= 0.0 }.map { it.first }

        return relevant + marginal
    }
}
