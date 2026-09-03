package com.example.cloudsocial.repository

import android.content.Context
import com.example.bunkr.repository.BunkrRepository
import com.example.cloudsocial.db.CloudSocialDao
import com.example.cloudsocial.db.CloudSocialMediaEntity
import com.example.cloudsocial.db.CloudSocialSourceEntity
import com.example.cloudsocial.mega.MegaSourceResolver
import com.example.cloudsocial.telegram.TelegramSourceResolver
import com.example.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CloudSyncProgress(
    val sourceId: String = "",
    val sourceName: String = "",
    val totalDiscovered: Int = 0,
    val newAdded: Int = 0,
    val errors: List<String> = emptyList()
)

class CloudSocialRepository private constructor(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao: CloudSocialDao = db.cloudSocialDao()
    private val bunkrRepo = BunkrRepository.getInstance(context)

    private val telegramResolver = TelegramSourceResolver()
    private val megaResolver = MegaSourceResolver()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val allSources: Flow<List<CloudSocialSourceEntity>> = dao.getAllSourcesFlow()
    val allMedia: Flow<List<CloudSocialMediaEntity>> = dao.getAllMediaFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncProgress = MutableStateFlow<CloudSyncProgress?>(null)
    val lastSyncProgress: StateFlow<CloudSyncProgress?> = _lastSyncProgress.asStateFlow()

    suspend fun importSource(urlOrIdentifier: String): CloudSyncProgress = withContext(Dispatchers.IO) {
        val input = urlOrIdentifier.trim()
        if (input.isBlank()) return@withContext CloudSyncProgress(errors = listOf("Empty input"))

        _isSyncing.value = true
        var progress = CloudSyncProgress()

        try {
            // 1. Detect source type
            val isTelegram = input.contains("t.me") || input.startsWith("@") || TelegramSourceResolver.parseUrl(input) != null
            val isMega = input.contains("mega.nz") || MegaSourceResolver.parseUrl(input) != null
            val isBunkr = input.contains("bunkr.") || input.contains("/a/") || input.contains("/f/")

            when {
                isTelegram -> {
                    val tgInfo = TelegramSourceResolver.parseUrl(input)
                    val channelName = tgInfo?.channelUsername ?: input.removePrefix("@")
                    val sourceId = "tg_$channelName"
                    val sourceEntity = CloudSocialSourceEntity(
                        id = sourceId,
                        type = "TELEGRAM",
                        name = "Telegram @$channelName",
                        sourceUrl = if (input.startsWith("http")) input else "https://t.me/$channelName",
                        enabled = true,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    dao.insertSource(sourceEntity)

                    val items = telegramResolver.scanChannel(sourceEntity)
                    dao.insertMediaBatch(items)

                    progress = CloudSyncProgress(
                        sourceId = sourceId,
                        sourceName = sourceEntity.name,
                        totalDiscovered = items.size,
                        newAdded = items.size
                    )
                }

                isMega -> {
                    val megaInfo = MegaSourceResolver.parseUrl(input)
                    val sourceId = "mega_${megaInfo?.id ?: System.currentTimeMillis()}"
                    val sourceEntity = CloudSocialSourceEntity(
                        id = sourceId,
                        type = "MEGA",
                        name = if (megaInfo?.isFolder == true) "MEGA Folder (${megaInfo.id})" else "MEGA File (${megaInfo?.id ?: "Link"})",
                        sourceUrl = input,
                        enabled = true,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    dao.insertSource(sourceEntity)

                    val items = megaResolver.scanSource(sourceEntity)
                    dao.insertMediaBatch(items)

                    progress = CloudSyncProgress(
                        sourceId = sourceId,
                        sourceName = sourceEntity.name,
                        totalDiscovered = items.size,
                        newAdded = items.size
                    )
                }

                isBunkr -> {
                    val report = bunkrRepo.importUrls(input)
                    // Sync Bunkr items into unified CloudSocial DB
                    syncBunkrToCloudSocial()

                    progress = CloudSyncProgress(
                        sourceId = "bunkr_import",
                        sourceName = "Bunkr Albums",
                        totalDiscovered = report.totalItemsDiscovered,
                        newAdded = report.totalItemsDiscovered,
                        errors = report.errors
                    )
                }

                else -> {
                    progress = CloudSyncProgress(errors = listOf("Unsupported Cloud/Social URL format"))
                }
            }
        } catch (e: Exception) {
            progress = CloudSyncProgress(errors = listOf("Import failed: ${e.message}"))
        } finally {
            _isSyncing.value = false
            _lastSyncProgress.value = progress
        }

        return@withContext progress
    }

    suspend fun syncAllSources() = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            val sources = dao.getAllSourcesFlow().firstOrNull() ?: emptyList()
            for (source in sources) {
                if (!source.enabled) continue
                when (source.type) {
                    "TELEGRAM" -> {
                        val items = telegramResolver.scanChannel(source)
                        dao.insertMediaBatch(items)
                    }
                    "MEGA" -> {
                        val items = megaResolver.scanSource(source)
                        dao.insertMediaBatch(items)
                    }
                }
            }
            // Sync Bunkr repo
            bunkrRepo.refreshAlbums()
            syncBunkrToCloudSocial()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun syncBunkrToCloudSocial() {
        val bunkrFiles = bunkrRepo.allFiles.firstOrNull() ?: emptyList()
        val bunkrAlbums = bunkrRepo.allAlbums.firstOrNull() ?: emptyList()

        val cloudMediaList = bunkrFiles.map { file ->
            val albumTitle = bunkrAlbums.firstOrNull { it.albumId == file.albumId }?.title ?: "Bunkr"
            CloudSocialMediaEntity(
                id = "bunkr_${file.fileId}",
                sourceId = "bunkr_${file.albumId}",
                type = "BUNKR",
                remoteId = file.fileId,
                parentId = file.albumId,
                title = file.title,
                caption = "Bunkr Media • Album: $albumTitle",
                sourceUrl = file.sourceUrl,
                directStreamUrl = file.streamUrl,
                thumbnailUrl = file.thumbnailUrl,
                mimeType = file.mediaType,
                fileSize = 0L,
                formattedSize = file.fileSize,
                durationMs = 0L,
                mediaCategory = "video",
                dateTimestamp = file.lastUpdated,
                resolution = file.resolution
            )
        }
        dao.insertMediaBatch(cloudMediaList)
    }

    suspend fun getAllMediaList(): List<CloudSocialMediaEntity> = withContext(Dispatchers.IO) {
        syncBunkrToCloudSocial()
        dao.getAllMediaList()
    }

    suspend fun resolveStreamUrlByUrlOrId(urlOrId: String): String = withContext(Dispatchers.IO) {
        val all = dao.getAllMediaList()
        val match = all.firstOrNull { it.id == urlOrId || it.sourceUrl == urlOrId || it.remoteId == urlOrId }
        if (match != null) {
            return@withContext resolveStreamUrl(match)
        }

        when {
            urlOrId.contains("mega.nz") || urlOrId.contains("mega.io") || urlOrId.contains("mega.co.nz") || urlOrId.startsWith("mega_") -> {
                val dummy = CloudSocialMediaEntity(
                    id = urlOrId,
                    sourceId = "standalone",
                    type = "MEGA",
                    remoteId = urlOrId,
                    title = "MEGA Stream",
                    sourceUrl = urlOrId
                )
                megaResolver.resolveStreamUrl(dummy)
            }
            urlOrId.contains("bunkr") -> {
                try {
                    com.example.bunkr.resolver.BunkrFileResolver().resolveFile(urlOrId).streamUrl
                } catch (_: Exception) {
                    urlOrId
                }
            }
            urlOrId.contains("t.me/") || urlOrId.startsWith("tg_") -> {
                val dummy = CloudSocialMediaEntity(
                    id = urlOrId,
                    sourceId = "standalone",
                    type = "TELEGRAM",
                    remoteId = urlOrId,
                    title = "Telegram Stream",
                    sourceUrl = urlOrId
                )
                telegramResolver.resolveStreamUrl(dummy)
            }
            else -> urlOrId
        }
    }

    suspend fun resolveStreamUrl(mediaItem: CloudSocialMediaEntity): String {
        return when (mediaItem.type) {
            "TELEGRAM" -> telegramResolver.resolveStreamUrl(mediaItem)
            "MEGA" -> megaResolver.resolveStreamUrl(mediaItem)
            "BUNKR" -> {
                try {
                    com.example.bunkr.resolver.BunkrFileResolver().resolveFile(mediaItem.sourceUrl).streamUrl
                } catch (e: Exception) {
                    if (!mediaItem.directStreamUrl.isNullOrBlank()) mediaItem.directStreamUrl!!
                    else mediaItem.sourceUrl
                }
            }
            else -> mediaItem.sourceUrl
        }
    }

    suspend fun deleteSource(sourceId: String) = withContext(Dispatchers.IO) {
        dao.deleteSource(sourceId)
        dao.deleteMediaBySource(sourceId)
    }

    suspend fun deleteMedia(mediaId: String) = withContext(Dispatchers.IO) {
        dao.deleteMediaById(mediaId)
    }

    companion object {
        @Volatile
        private var instance: CloudSocialRepository? = null

        fun getInstance(context: Context): CloudSocialRepository {
            return instance ?: synchronized(this) {
                instance ?: CloudSocialRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
