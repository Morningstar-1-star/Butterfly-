package com.example.model

data class ChannelDetails(
    val channelId: String,
    val name: String,
    val handle: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val subscriberCount: String = "1.2M subscribers",
    val videoCount: String = "90+ videos",
    val description: String = "",
    val isVerified: Boolean = true,
    val isSubscribed: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val shorts: List<VideoItem> = emptyList(),
    val playlists: List<ChannelPlaylist> = emptyList(),
    val joinedDate: String = "Joined 2021",
    val totalViews: String = "12.4M views",
    val providerId: String = "youtube"
)

data class ChannelPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val videoCount: Int = 0,
    val videos: List<VideoItem> = emptyList()
)
