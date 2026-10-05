package com.example.model

enum class SearchTypeFilter(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
    CHANNELS("Channels")
}

enum class SearchDurationFilter(val label: String) {
    ANY("Any duration"),
    UNDER_4_MIN("Under 4 minutes"),
    FOUR_TO_TWENTY_MIN("4 – 20 minutes"),
    OVER_20_MIN("Over 20 minutes")
}

enum class SearchUploadDateFilter(val label: String) {
    ANY("Any time"),
    TODAY("Last 24 hours"),
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    THIS_YEAR("This year"),
    CLASSIC("Classic (< 2015)")
}

enum class SearchSortFilter(val label: String) {
    RELEVANCE("Relevance"),
    UPLOAD_DATE("Upload date (Newest)"),
    VIEW_COUNT("View count"),
    DURATION("Duration (Longest)"),
    QUALITY("Quality (4K / 1080p)")
}

data class SearchFilterState(
    val type: SearchTypeFilter = SearchTypeFilter.ALL,
    val sourceProviderId: String = "ALL", // "ALL" or specific provider ID (e.g. "youtube", "archive_org", etc.)
    val duration: SearchDurationFilter = SearchDurationFilter.ANY,
    val uploadDate: SearchUploadDateFilter = SearchUploadDateFilter.ANY,
    val sortBy: SearchSortFilter = SearchSortFilter.RELEVANCE,
    // Feature Badges / Toggles
    val is4kOnly: Boolean = false,
    val isFullHdOnly: Boolean = false,
    val isDirectStreamOnly: Boolean = false,
    val isSubtitlesOnly: Boolean = false,
    val isWatchedOnly: Boolean = false,
    val isUnwatchedOnly: Boolean = false
) {
    val isActive: Boolean
        get() = type != SearchTypeFilter.ALL ||
                sourceProviderId != "ALL" ||
                duration != SearchDurationFilter.ANY ||
                uploadDate != SearchUploadDateFilter.ANY ||
                sortBy != SearchSortFilter.RELEVANCE ||
                is4kOnly || isFullHdOnly || isDirectStreamOnly ||
                isSubtitlesOnly || isWatchedOnly || isUnwatchedOnly

    val activeFilterCount: Int
        get() {
            var count = 0
            if (type != SearchTypeFilter.ALL) count++
            if (sourceProviderId != "ALL") count++
            if (duration != SearchDurationFilter.ANY) count++
            if (uploadDate != SearchUploadDateFilter.ANY) count++
            if (sortBy != SearchSortFilter.RELEVANCE) count++
            if (is4kOnly) count++
            if (isFullHdOnly) count++
            if (isDirectStreamOnly) count++
            if (isSubtitlesOnly) count++
            if (isWatchedOnly) count++
            if (isUnwatchedOnly) count++
            return count
        }

    fun applyTo(
        items: List<VideoItem>,
        watchedIds: Set<String>
    ): List<VideoItem> {
        var filtered = items

        // 1. Source / Provider Filter
        if (sourceProviderId != "ALL" && sourceProviderId.isNotBlank()) {
            filtered = filtered.filter { item ->
                com.example.util.SourceTagHelper.matchesProvider(item.providerId, sourceProviderId)
            }
        }

        // 2. Type Filter
        when (type) {
            SearchTypeFilter.ALL -> {}
            SearchTypeFilter.MOVIES -> {
                filtered = filtered.filter { item ->
                    val title = item.title.lowercase()
                    val pId = (item.providerId ?: "").lowercase()
                    val isCinemaProvider = pId.startsWith("tmdb") || pId.startsWith("vidsrc") || pId.startsWith("decryptor") ||
                            pId.startsWith("nuvio") || pId == "tubitv" || pId == "tubi" || pId == "popcorntv" || pId.startsWith("vega_") ||
                            pId == "imdb"
                    val isEpisodic = title.matches(Regex("(?i).*s\\d{1,2}e\\d{1,2}.*")) || title.contains("season ") ||
                            title.contains("episode ") || title.contains(" ep.") || title.contains(" ep ")
                    val hasMovieKeyword = title.contains("movie") || title.contains("film") || title.contains("cinema") ||
                            title.contains("1080p") || title.contains("720p") || title.contains("bluray")
                    val dur = extractEffectiveDuration(item)
                    (isCinemaProvider || hasMovieKeyword || dur > 2400 || item.tags.any { it.equals("movie", ignoreCase = true) }) && !isEpisodic
                }
            }
            SearchTypeFilter.TV_SHOWS -> {
                filtered = filtered.filter { item ->
                    val title = item.title.lowercase()
                    val pId = (item.providerId ?: "").lowercase()
                    val isSeries = title.matches(Regex("(?i).*s\\d{1,2}e\\d{1,2}.*")) || title.contains("season") ||
                            title.contains("episode") || title.contains("ep.") || title.contains("ep ") ||
                            title.contains("series") || title.contains("drama") || title.contains("part ") ||
                            title.contains("anime") || title.contains("donghua")
                    val isTvProvider = pId == "bilibili" || pId == "tencent" || pId.contains("wetv") ||
                            item.id.contains(":tv:") || item.tags.any { it.equals("series", ignoreCase = true) || it.equals("tv", ignoreCase = true) }
                    isSeries || isTvProvider
                }
            }
            SearchTypeFilter.VIDEOS -> {
                filtered = filtered.filter { item ->
                    val dur = extractEffectiveDuration(item)
                    val title = item.title.lowercase()
                    // Regular videos are under 2 hours and not full feature films
                    dur < 7200 || !title.contains("full movie")
                }
            }
            SearchTypeFilter.CHANNELS -> {
                filtered = filtered.filter { item ->
                    item.uploaderName.isNotBlank() && !item.uploaderName.equals("Unknown", ignoreCase = true)
                }.distinctBy { it.uploaderName.lowercase() }
            }
        }

        // 3. Duration Filter
        when (duration) {
            SearchDurationFilter.ANY -> {}
            SearchDurationFilter.UNDER_4_MIN -> {
                filtered = filtered.filter {
                    val dur = extractEffectiveDuration(it)
                    val t = it.title.lowercase()
                    (dur in 1..240) || t.contains("shorts") || t.contains("#shorts") || t.contains("teaser") || t.contains("trailer") || t.contains("clip")
                }
            }
            SearchDurationFilter.FOUR_TO_TWENTY_MIN -> {
                filtered = filtered.filter {
                    val dur = extractEffectiveDuration(it)
                    val t = it.title.lowercase()
                    (dur in 241..1200) || (dur <= 0 && !t.contains("shorts") && !t.contains("full movie"))
                }
            }
            SearchDurationFilter.OVER_20_MIN -> {
                filtered = filtered.filter {
                    val dur = extractEffectiveDuration(it)
                    val t = it.title.lowercase()
                    val pId = (it.providerId ?: "").lowercase()
                    dur > 1200 || t.contains("full movie") || t.contains("episode") || pId.startsWith("tmdb") || pId.startsWith("vega") || pId == "tencent" || pId == "tubitv"
                }
            }
        }

        // 4. Upload Date Filter
        when (uploadDate) {
            SearchUploadDateFilter.ANY -> {}
            SearchUploadDateFilter.TODAY -> {
                filtered = filtered.filter { item ->
                    isWithinUploadDateWindow(item, maxDaysAgo = 1)
                }
            }
            SearchUploadDateFilter.THIS_WEEK -> {
                filtered = filtered.filter { item ->
                    isWithinUploadDateWindow(item, maxDaysAgo = 7)
                }
            }
            SearchUploadDateFilter.THIS_MONTH -> {
                filtered = filtered.filter { item ->
                    isWithinUploadDateWindow(item, maxDaysAgo = 31)
                }
            }
            SearchUploadDateFilter.THIS_YEAR -> {
                filtered = filtered.filter { item ->
                    isWithinUploadDateWindow(item, maxDaysAgo = 365)
                }
            }
            SearchUploadDateFilter.CLASSIC -> {
                filtered = filtered.filter { item ->
                    val ts = parseUploadTimestamp(item)
                    if (ts > 0) {
                        val ageDays = (System.currentTimeMillis() - ts) / 86400_000L
                        ageDays > 365 * 4
                    } else {
                        val year = extractYearFromItem(item)
                        val date = (item.uploadDate ?: "").lowercase()
                        (year != null && year < 2021) || date.contains("5 year") || date.contains("6 year") || date.contains("7 year") || date.contains("10 year")
                    }
                }
            }
        }

        // 5. Feature Badges
        if (is4kOnly) {
            filtered = filtered.filter {
                it.title.contains("4k", ignoreCase = true) ||
                it.title.contains("2160p", ignoreCase = true) ||
                it.title.contains("uhd", ignoreCase = true)
            }
        }
        if (isFullHdOnly) {
            filtered = filtered.filter {
                it.title.contains("1080p", ignoreCase = true) ||
                it.title.contains("fhd", ignoreCase = true) ||
                it.title.contains("4k", ignoreCase = true) ||
                it.title.contains("2160p", ignoreCase = true)
            }
        }
        if (isDirectStreamOnly) {
            filtered = filtered.filter {
                val pId = (it.providerId ?: "").lowercase()
                pId in setOf("youtube", "dailymotion", "archive_org", "archive", "bilibili", "tencent", "tubitv", "tmdb_embed", "torrent")
            }
        }
        if (isSubtitlesOnly) {
            filtered = filtered.filter {
                val title = it.title.lowercase()
                title.contains("sub") || title.contains("cc") || title.contains("multi") || (it.providerId ?: "").contains("youtube") || (it.providerId ?: "").contains("tmdb") || (it.providerId ?: "").contains("tencent")
            }
        }
        if (isWatchedOnly) {
            filtered = filtered.filter { watchedIds.contains(it.id) }
        }
        if (isUnwatchedOnly) {
            filtered = filtered.filter { !watchedIds.contains(it.id) }
        }

        // 6. Sorting
        return when (sortBy) {
            SearchSortFilter.RELEVANCE -> filtered
            SearchSortFilter.UPLOAD_DATE -> filtered.sortedByDescending { parseUploadTimestamp(it) }
            SearchSortFilter.VIEW_COUNT -> filtered.sortedByDescending {
                if (it.viewCount > 0) it.viewCount else parseViewsFromText("${it.title} ${it.description.orEmpty()}")
            }
            SearchSortFilter.DURATION -> filtered.sortedByDescending { extractEffectiveDuration(it) }
            SearchSortFilter.QUALITY -> filtered.sortedByDescending {
                val t = it.title.lowercase()
                when {
                    t.contains("4k") || t.contains("2160p") || t.contains("uhd") -> 3
                    t.contains("1080p") || t.contains("fhd") -> 2
                    t.contains("720p") || t.contains("hd") -> 1
                    else -> 0
                }
            }
        }
    }

    private fun extractEffectiveDuration(item: VideoItem): Long {
        if (item.durationSeconds > 0) return item.durationSeconds
        val combined = "${item.title} ${item.description.orEmpty()}"
        val match = Regex("""(?:\[|\(|\b)(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\]|\)|\b)""").find(combined)
        if (match != null) {
            val p1 = match.groupValues[1].toLongOrNull() ?: 0L
            val p2 = match.groupValues[2].toLongOrNull() ?: 0L
            val p3 = match.groupValues[3].toLongOrNull()
            return if (p3 != null) p1 * 3600 + p2 * 60 + p3 else p1 * 60 + p2
        }
        val lower = item.title.lowercase()
        if (lower.contains("shorts") || lower.contains("#shorts") || lower.contains("teaser") || lower.contains("clip")) {
            return 60L
        }
        if (lower.contains("full movie") || lower.contains("cinema")) {
            return 5400L
        }
        return -1L
    }

    private fun isWithinUploadDateWindow(item: VideoItem, maxDaysAgo: Int): Boolean {
        val ts = parseUploadTimestamp(item)
        if (ts > 0L) {
            val ageMs = System.currentTimeMillis() - ts
            val ageDays = if (ageMs < 0) 0L else ageMs / 86400_000L
            return ageDays <= maxDaysAgo
        }

        val dateText = (item.uploadDate ?: "").lowercase().trim()
        if (dateText.isBlank()) {
            val year = extractYearFromItem(item) ?: return true
            return if (maxDaysAgo >= 365) year >= 2025 else true
        }

        if (dateText.contains("second") || dateText.contains("minute") || dateText.contains("hour") || dateText.contains("today")) {
            return true
        }
        if (maxDaysAgo <= 1) return false

        if (dateText.contains("day") || dateText.contains("yesterday")) {
            val days = Regex("""(\d+)\s*day""").find(dateText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            return days <= maxDaysAgo
        }
        if (maxDaysAgo <= 7) return false

        if (dateText.contains("week")) {
            val weeks = Regex("""(\d+)\s*week""").find(dateText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            return (weeks * 7) <= maxDaysAgo
        }
        if (maxDaysAgo <= 31) return false

        if (dateText.contains("month")) {
            val months = Regex("""(\d+)\s*month""").find(dateText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            return (months * 30) <= maxDaysAgo
        }
        if (dateText.contains("2026") || dateText.contains("2025")) return true
        return false
    }

    private fun extractYearFromItem(item: VideoItem): Int? {
        val dateText = item.uploadDate.orEmpty()
        val yearMatchDate = Regex("""(19\d\d|20\d\d)""").find(dateText)?.value?.toIntOrNull()
        if (yearMatchDate != null) return yearMatchDate
        return Regex("""\b(19\d\d|20\d\d)\b""").find(item.title)?.value?.toIntOrNull()
    }

    private fun parseUploadTimestamp(item: VideoItem): Long {
        val text = (item.uploadDate ?: "").trim()
        if (text.isBlank()) return 0L

        // Try standard ISO formats first (e.g. 2026-04-02T15:30:00Z or 2026-04-02)
        try {
            val instant = java.time.Instant.parse(text)
            return instant.toEpochMilli()
        } catch (_: Exception) {}
        try {
            val ldt = java.time.LocalDateTime.parse(text, java.time.format.DateTimeFormatter.ISO_DATE_TIME)
            return ldt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {}
        try {
            if (text.length >= 10 && text[4] == '-' && text[7] == '-') {
                val ld = java.time.LocalDate.parse(text.take(10), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                return ld.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        } catch (_: Exception) {}

        // Relative text formats (e.g. "2 hours ago", "yesterday", "3 days ago")
        val lower = text.lowercase()
        val now = System.currentTimeMillis()
        if (lower.contains("second") || lower.contains("minute") || lower.contains("hour")) return now - 3600_000L
        if (lower.contains("today")) return now - 43200_000L
        if (lower.contains("yesterday")) return now - 86400_000L
        if (lower.contains("day")) {
            val days = Regex("""(\d+)\s*day""").find(lower)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
            return now - days * 86400_000L
        }
        if (lower.contains("week")) {
            val weeks = Regex("""(\d+)\s*week""").find(lower)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
            return now - weeks * 7 * 86400_000L
        }
        if (lower.contains("month")) {
            val months = Regex("""(\d+)\s*month""").find(lower)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
            return now - months * 30 * 86400_000L
        }
        if (lower.contains("year")) {
            val years = Regex("""(\d+)\s*year""").find(lower)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
            return now - years * 365 * 86400_000L
        }
        val year = extractYearFromItem(item)
        if (year != null && year in 1970..2030) {
            return (year - 1970L) * 365L * 86400_000L
        }
        return 0L
    }

    private fun parseViewsFromText(text: String): Long {
        val match = Regex("""(?i)(\d+(?:\.\d+)?)\s*([kmb])\s*views?""").find(text)
        if (match != null) {
            val num = match.groupValues[1].toDoubleOrNull() ?: return 0L
            val multiplier = when (match.groupValues[2].lowercase()) {
                "k" -> 1_000L
                "m" -> 1_000_000L
                "b" -> 1_000_000_000L
                else -> 1L
            }
            return (num * multiplier).toLong()
        }
        val commaMatch = Regex("""(?i)\b(\d{1,3}(?:,\d{3})+)\s*views?""").find(text)
        if (commaMatch != null) {
            val raw = commaMatch.groupValues[1].replace(",", "")
            return raw.toLongOrNull() ?: 0L
        }
        return 0L
    }
}
