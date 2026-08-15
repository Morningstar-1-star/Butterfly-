package com.example.model

import com.example.plugin.sdk.model.ProviderType
import com.example.plugin.jav.ProviderStatusState

enum class AppScreen {
    HOME,
    EXPLORE,
    SHORTS,
    SUBSCRIPTIONS,
    ACCOUNT,
    LIBRARY,
    PROVIDERS,
    PLAYER,
    SETTINGS,
    SPONSORBLOCK_SETTINGS
}

data class ProviderUiItem(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = "",
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val providerType: ProviderType = ProviderType.OTHER,
    val statusState: ProviderStatusState = ProviderStatusState.NO_RESULT,
    val statusMessage: String = "",
    val responseTimeMs: Long = 0L
) {
    val isTorrent: Boolean
        get() = providerType == ProviderType.TORRENT
}

data class UserPlaylist(
    val id: String,
    val title: String,
    val videos: List<VideoItem> = emptyList()
)

data class UserProfile(
    val name: String = "Lucifer",
    val handle: String = "@lucifer",
    val bio: String = "Passionate video lover & content curator.",
    val avatarUrl: String? = null,
    val avatarPreset: String = "purple"
)
