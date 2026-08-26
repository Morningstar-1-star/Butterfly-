package com.example.torrent.model

import com.example.model.MediaType

enum class TorrentEngineState {
    IDLE,
    CONNECTING_TRACKERS,
    FETCHING_METADATA,
    INITIALIZING_STORAGE,
    BUFFERING,
    STREAMING,
    PAUSED,
    ERROR,
    COMPLETED
}

data class TorrentRelease(
    val title: String,
    val infoHash: String,
    val magnetUrl: String,
    val provider: String,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val sizeBytes: Long = 0L,
    val formattedSize: String = "",
    val quality: String = "1080p",
    val codec: String = "",
    val hdr: String = "",
    val audioChannels: String = "",
    val fileIndex: Int? = null,
    val fileName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val uploadDate: String? = null,
    val trackerUrls: List<String> = emptyList(),
    val isVerified: Boolean = true
) {
    val qualityScore: Int
        get() {
            var score = when {
                quality.contains("2160p", ignoreCase = true) || quality.contains("4K", ignoreCase = true) -> 400
                quality.contains("1080p", ignoreCase = true) -> 300
                quality.contains("720p", ignoreCase = true) -> 200
                quality.contains("480p", ignoreCase = true) -> 100
                else -> 150
            }
            if (codec.contains("265", ignoreCase = true) || codec.contains("hevc", ignoreCase = true) || codec.contains("av1", ignoreCase = true)) {
                score += 50
            }
            if (hdr.isNotBlank()) score += 30
            if (audioChannels.contains("5.1") || audioChannels.contains("7.1") || audioChannels.contains("Atmos")) score += 20
            score += minOf(seeders, 100)
            return score
        }
}

data class TorrentFileItem(
    val index: Int,
    val path: String,
    val name: String,
    val length: Long,
    val offset: Long,
    val isVideo: Boolean,
    val isSelected: Boolean = true,
    val progress: Float = 0f
)

data class TorrentMetadata(
    val infoHash: String,
    val name: String,
    val pieceLength: Int,
    val pieceHashes: List<ByteArray> = emptyList(),
    val totalLength: Long,
    val files: List<TorrentFileItem>,
    val mainVideoFile: TorrentFileItem
)

data class TorrentPeerInfo(
    val ip: String,
    val port: Int,
    val clientName: String = "",
    val downloadRateBps: Long = 0L,
    val uploadRateBps: Long = 0L,
    val isChoked: Boolean = true,
    val isInterested: Boolean = false,
    val isSeed: Boolean = false,
    val progress: Float = 0f,
    val flags: String = ""
)

data class TorrentTrackerInfo(
    val url: String,
    val status: String = "Working",
    val peersCount: Int = 0,
    val seedsCount: Int = 0,
    val message: String = ""
)

data class PieceMapInfo(
    val totalPieces: Int = 0,
    val downloadedPiecesCount: Int = 0,
    val piecesBitfield: BooleanArray = BooleanArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PieceMapInfo) return false
        return totalPieces == other.totalPieces &&
                downloadedPiecesCount == other.downloadedPiecesCount &&
                piecesBitfield.contentEquals(other.piecesBitfield)
    }

    override fun hashCode(): Int {
        var result = totalPieces
        result = 31 * result + downloadedPiecesCount
        result = 31 * result + piecesBitfield.contentHashCode()
        return result
    }
}

data class TorrentDownloadTask(
    val id: String,
    val infoHash: String,
    val title: String,
    val savePath: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progress: Float = 0f,
    val speedBps: Long = 0L,
    val etaSeconds: Long = 0L,
    val state: TorrentEngineState = TorrentEngineState.IDLE,
    val isSequential: Boolean = false,
    val selectedFileIndices: List<Int> = emptyList(),
    val dateAdded: Long = System.currentTimeMillis()
)

data class TorrentSettings(
    val dhtEnabled: Boolean = true,
    val pexEnabled: Boolean = true,
    val lsdEnabled: Boolean = true,
    val maxConnections: Int = 200,
    val maxDownloadSpeedBps: Long = 0L, // 0 = unlimited
    val maxUploadSpeedBps: Long = 0L,   // 0 = unlimited
    val downloadOnlyOnWifi: Boolean = false,
    val downloadOnlyWhenCharging: Boolean = false,
    val sequentialStreamingDefault: Boolean = true,
    val customTrackers: List<String> = emptyList()
)

data class TorrentEngineStats(
    val state: TorrentEngineState = TorrentEngineState.IDLE,
    val infoHash: String = "",
    val activeFileName: String = "",
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val connectedPeers: Int = 0,
    val totalPeersFound: Int = 0,
    val activeSeeders: Int = 0,
    val totalPieces: Int = 0,
    val downloadedPiecesCount: Int = 0,
    val bufferProgress: Float = 0f,
    val totalProgress: Float = 0f,
    val streamPort: Int = 8899,
    val streamUrl: String = "",
    val errorMessage: String? = null,
    val dhtNodes: Long = 0L,
    val activePeersList: List<TorrentPeerInfo> = emptyList(),
    val trackersList: List<TorrentTrackerInfo> = emptyList()
)

data class TorrentStreamSession(
    val sessionId: String,
    val release: TorrentRelease,
    val httpStreamUrl: String,
    val localFilePath: String? = null,
    val fileIndex: Int = 0
)
