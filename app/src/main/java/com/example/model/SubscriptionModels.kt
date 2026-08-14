package com.example.model

data class SubscribedChannel(
    val id: String,
    val name: String,
    val handle: String = "",
    val avatarUrl: String? = null,
    val subscriberCount: String = "1.2M subscribers",
    val hasUnreadUpdates: Boolean = true,
    val notificationEnabled: Boolean = true,
    val description: String = "",
    val bannerUrl: String? = null
)
