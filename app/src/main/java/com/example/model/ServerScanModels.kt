package com.example.model

enum class NodeScanState {
    QUEUE,
    CHECKING,
    CONNECTING,
    SUCCESS,
    FAILED
}

data class ServerNode(
    val id: String,
    val name: String,
    val description: String = "",
    val providerName: String = "",
    val quality: String = "4K UHD HDR",
    val state: NodeScanState = NodeScanState.QUEUE,
    val pingMs: Long = 0L,
    val streamOption: PlayableStreamOption? = null
)

data class ServerScanState(
    val isScanning: Boolean = false,
    val totalCount: Int = 0,
    val analyzedCount: Int = 0,
    val remainingCount: Int = 0,
    val nodes: List<ServerNode> = emptyList(),
    val selectedNodeId: String? = null,
    val activeServerIndex: Int = 1, // Server 1, Server 2, Server 3, Server 4
    val statusMessage: String = "Scanning high-speed torrent servers..."
)

