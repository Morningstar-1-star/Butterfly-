package com.example.bunkr.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bunkr.model.BunkrAlbum
import com.example.bunkr.model.BunkrFile

@Entity(tableName = "bunkr_albums")
data class BunkrAlbumEntity(
    @PrimaryKey val albumId: String,
    val title: String,
    val sourceUrl: String,
    val isEnabled: Boolean = true,
    val lastScanTime: Long = 0L,
    val itemCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): BunkrAlbum = BunkrAlbum(
        albumId = albumId,
        title = title,
        sourceUrl = sourceUrl,
        isEnabled = isEnabled,
        lastScanTime = lastScanTime,
        itemCount = itemCount,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(album: BunkrAlbum): BunkrAlbumEntity = BunkrAlbumEntity(
            albumId = album.albumId,
            title = album.title,
            sourceUrl = album.sourceUrl,
            isEnabled = album.isEnabled,
            lastScanTime = album.lastScanTime,
            itemCount = album.itemCount,
            createdAt = album.createdAt
        )
    }
}

@Entity(
    tableName = "bunkr_files",
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["sourceUrl"])
    ]
)
data class BunkrFileEntity(
    @PrimaryKey val fileId: String,
    val albumId: String,
    val title: String,
    val sourceUrl: String,
    val thumbnailUrl: String? = null,
    val mediaType: String = "video",
    val duration: String = "",
    val resolution: String = "",
    val fileSize: String = "",
    val streamUrl: String? = null,
    val streamUrlExpiry: Long = 0L,
    val isAvailable: Boolean = true,
    val orderIndex: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toDomain(): BunkrFile = BunkrFile(
        fileId = fileId,
        albumId = albumId,
        title = title,
        sourceUrl = sourceUrl,
        thumbnailUrl = thumbnailUrl,
        mediaType = mediaType,
        duration = duration,
        resolution = resolution,
        fileSize = fileSize,
        streamUrl = streamUrl,
        streamUrlExpiry = streamUrlExpiry,
        isAvailable = isAvailable,
        orderIndex = orderIndex,
        lastUpdated = lastUpdated
    )

    companion object {
        fun fromDomain(file: BunkrFile): BunkrFileEntity = BunkrFileEntity(
            fileId = file.fileId,
            albumId = file.albumId,
            title = file.title,
            sourceUrl = file.sourceUrl,
            thumbnailUrl = file.thumbnailUrl,
            mediaType = file.mediaType,
            duration = file.duration,
            resolution = file.resolution,
            fileSize = file.fileSize,
            streamUrl = file.streamUrl,
            streamUrlExpiry = file.streamUrlExpiry,
            isAvailable = file.isAvailable,
            orderIndex = file.orderIndex,
            lastUpdated = file.lastUpdated
        )
    }
}
