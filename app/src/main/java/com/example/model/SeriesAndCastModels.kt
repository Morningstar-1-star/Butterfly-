package com.example.model

data class CastMember(
    val name: String,
    val role: String? = null,
    val avatarUrl: String? = null
)

data class EpisodeItem(
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val durationText: String = "24:00",
    val thumbnailUrl: String? = null,
    val providerId: String? = null,
    val viewsText: String = "1.2M views"
)

data class SeriesSeason(
    val seasonNumber: Int,
    val seasonName: String,
    val episodes: List<EpisodeItem>
)

data class VideoComment(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val commentText: String,
    val timeAgo: String = "2 hours ago",
    val likeCount: Int = 0,
    val isLikedByMe: Boolean = false
)
