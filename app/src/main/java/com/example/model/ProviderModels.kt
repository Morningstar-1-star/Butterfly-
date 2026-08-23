package com.example.model

enum class AppScreen {
    HOME,
    EXPLORE,
    SUBSCRIPTIONS,
    LIBRARY,
    ACCOUNT,
    CHANNEL,
    PLAYER,
    SETTINGS
}

enum class ProviderType {
    DIRECT,
    SCRAPER,
    TORRENT,
    DEBRID,
    OTHER
}

enum class ProviderStatusState {
    ACTIVE,
    DEGRADED,
    BLOCKED,
    ERROR,
    NO_RESULT
}

data class ProviderUiItem(
    val id: String,
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val providerType: ProviderType = ProviderType.OTHER,
    val category: String = "Other",
    val statusState: ProviderStatusState = ProviderStatusState.NO_RESULT,
    val statusMessage: String = "Inactive",
    val responseTimeMs: Long = 0L
) {
    val isTorrent: Boolean get() = providerType == ProviderType.TORRENT
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

data class ServerScanState(
    val nodes: List<ServerNode> = emptyList(),
    val selectedNodeId: String? = null,
    val activeServerIndex: Int = 1
)

data class ServerNode(
    val id: String,
    val name: String,
    val streamOption: PlayableStreamOption? = null
)

