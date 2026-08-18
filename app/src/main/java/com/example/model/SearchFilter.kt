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
        if (sourceProviderId != "ALL") {
            filtered = filtered.filter { item ->
                val pId = (item.providerId ?: "").lowercase()
                val targetId = sourceProviderId.lowercase()
                pId == targetId ||
                pId.contains(targetId) ||
                targetId.contains(pId) ||
                (targetId.startsWith("apijav") && pId.startsWith("apijav"))
            }
        }

        // 2. Type Filter
        when (type) {
            SearchTypeFilter.ALL -> {}
            SearchTypeFilter.MOVIES -> {
                filtered = filtered.filter { item ->
                    val title = item.title.lowercase()
                    val pId = (item.providerId ?: "").lowercase()
                    title.contains("movie") || title.contains("1080p") || title.contains("720p") ||
                            (item.durationSeconds > 3000 && !title.matches(Regex(".*s\\d{1,2}e\\d{1,2}.*")) && !title.contains("episode"))
                }
            }
            SearchTypeFilter.TV_SHOWS -> {
                filtered = filtered.filter { item ->
                    val title = item.title.lowercase()
                    val pId = (item.providerId ?: "").lowercase()
                    title.matches(Regex(".*s\\d{1,2}e\\d{1,2}.*")) || title.contains("season ") || title.contains("episode ")
                }
            }
            SearchTypeFilter.VIDEOS -> {
                filtered = filtered.filter { item ->
                    val pId = (item.providerId ?: "").lowercase()
                    pId == "youtube" || pId == "dailymotion" || pId == "archive_org" || pId == "mega" || pId == "telegram"
                }
            }
            SearchTypeFilter.CHANNELS -> {
                // Keep all or items matching creator/uploader
            }
        }

        // 3. Duration Filter
        when (duration) {
            SearchDurationFilter.ANY -> {}
            SearchDurationFilter.UNDER_4_MIN -> {
                filtered = filtered.filter { it.durationSeconds in 1..239 }
            }
            SearchDurationFilter.FOUR_TO_TWENTY_MIN -> {
                filtered = filtered.filter { it.durationSeconds in 240..1200 }
            }
            SearchDurationFilter.OVER_20_MIN -> {
                filtered = filtered.filter { it.durationSeconds > 1200 }
            }
        }

        // 4. Upload Date Filter
        when (uploadDate) {
            SearchUploadDateFilter.ANY -> {}
            SearchUploadDateFilter.TODAY -> {
                filtered = filtered.filter {
                    val date = (it.uploadDate ?: "").lowercase()
                    date.contains("hour") || date.contains("minute") || date.contains("second") || date.contains("today") || date.contains("1 day")
                }
            }
            SearchUploadDateFilter.THIS_WEEK -> {
                filtered = filtered.filter {
                    val date = (it.uploadDate ?: "").lowercase()
                    date.contains("hour") || date.contains("day") || date.contains("week") || date.contains("today")
                }
            }
            SearchUploadDateFilter.THIS_MONTH -> {
                filtered = filtered.filter {
                    val date = (it.uploadDate ?: "").lowercase()
                    !date.contains("year") && (date.contains("month") || date.contains("week") || date.contains("day") || date.contains("hour"))
                }
            }
            SearchUploadDateFilter.THIS_YEAR -> {
                filtered = filtered.filter {
                    val date = (it.uploadDate ?: "").lowercase()
                    date.contains("2025") || date.contains("2026") || (!date.contains("year") && date.isNotBlank()) || date.contains("1 year")
                }
            }
            SearchUploadDateFilter.CLASSIC -> {
                filtered = filtered.filter {
                    val date = (it.uploadDate ?: "").lowercase()
                    val title = it.title
                    val yearMatch = Regex("(19\\d\\d|200\\d|201[0-4])").find(title)?.value
                    yearMatch != null || date.contains("10 year") || date.contains("15 year") || date.contains("20 year")
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
                pId == "youtube" || pId == "dailymotion" || pId == "archive_org" || pId == "mega" || pId == "telegram"
            }
        }
        if (isSubtitlesOnly) {
            filtered = filtered.filter {
                val title = it.title.lowercase()
                title.contains("sub") || title.contains("cc") || title.contains("multi") || (it.providerId ?: "").contains("youtube")
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
            SearchSortFilter.UPLOAD_DATE -> filtered.sortedByDescending { it.uploadDate ?: "" }
            SearchSortFilter.VIEW_COUNT -> filtered.sortedByDescending { it.viewCount }
            SearchSortFilter.DURATION -> filtered.sortedByDescending { it.durationSeconds }
            SearchSortFilter.QUALITY -> filtered.sortedByDescending {
                val t = it.title.lowercase()
                when {
                    t.contains("4k") || t.contains("2160p") -> 3
                    t.contains("1080p") || t.contains("fhd") -> 2
                    t.contains("720p") || t.contains("hd") -> 1
                    else -> 0
                }
            }
        }
    }
}
