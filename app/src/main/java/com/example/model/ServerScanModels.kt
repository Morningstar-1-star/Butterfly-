package com.example.model

enum class NodeScanState {
    SCANNING,
    SUCCESS,
    FAILED
}

data class ServerNode(
    val id: String,
    val name: String,
    val providerName: String,
    val quality: String,
    val state: NodeScanState = NodeScanState.SCANNING,
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
    val statusMessage: String = "Scanning high-speed servers..."
)
