package com.example.bunkr.repository

import android.content.Context
import android.util.Log
import com.example.bunkr.db.BunkrAlbumEntity
import com.example.bunkr.db.BunkrFileEntity
import com.example.bunkr.model.*
import com.example.bunkr.resolver.BunkrAlbumCrawler
import com.example.bunkr.resolver.BunkrFileResolver
import com.example.bunkr.resolver.BunkrUrlUtils
import com.example.db.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class BunkrRepository(private val context: Context) {

    companion object {
        private const val TAG = "BunkrRepository"
        private const val STANDALONE_ALBUM_ID = "standalone_imports"
        private const val STREAM_URL_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours

        @Volatile
        private var instance: BunkrRepository? = null

        fun getInstance(context: Context): BunkrRepository {
            return instance ?: synchronized(this) {
                instance ?: BunkrRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val db = AppDatabase.getInstance(context)
    private val dao = db.bunkrDao()

    private val crawler = BunkrAlbumCrawler()
    private val resolver = BunkrFileResolver()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val allAlbums: Flow<List<BunkrAlbum>> = dao.getAllAlbumsFlow()
        .map { list -> list.map { it.toDomain() } }

    val allFiles: Flow<List<BunkrFile>> = dao.getAllFilesFlow()
        .map { list -> list.map { it.toDomain() } }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanReport = MutableStateFlow<BunkrScanReport?>(null)
    val scanReport: StateFlow<BunkrScanReport?> = _scanReport.asStateFlow()

    fun getFilesForAlbumFlow(albumId: String): Flow<List<BunkrFile>> {
        return dao.getFilesForAlbumFlow(albumId)
            .map { list -> list.map { it.toDomain() } }
    }

    /**
     * Parse multiline text input containing mixed Bunkr /a/ and /f/ URLs,
     * save albums and files to DB, and trigger initial scan.
     */
    suspend fun importUrls(rawText: String): BunkrScanReport = withContext(Dispatchers.IO) {
        val parsedUrls = BunkrUrlUtils.extractUrlsFromText(rawText)
        if (parsedUrls.isEmpty()) {
            return@withContext BunkrScanReport(errors = listOf("No valid Bunkr URLs found in input"))
        }

        _isScanning.value = true
        val errors = mutableListOf<String>()
        var processedCount = 0
        var discoveredCount = 0
        var playableCount = 0

        val albumUrls = parsedUrls.filter { it.type == BunkrUrlType.ALBUM }
        val fileUrls = parsedUrls.filter { it.type == BunkrUrlType.FILE }

        // Handle standalone files under default album
        if (fileUrls.isNotEmpty()) {
            val standaloneAlbum = dao.getAlbumById(STANDALONE_ALBUM_ID) ?: BunkrAlbumEntity(
                albumId = STANDALONE_ALBUM_ID,
                title = "Direct Media Imports",
                sourceUrl = "https://${BunkrUrlUtils.DEFAULT_BUNKR_DOMAIN}/",
                isEnabled = true,
                lastScanTime = System.currentTimeMillis(),
                itemCount = 0,
                createdAt = System.currentTimeMillis()
            )
            dao.insertAlbum(standaloneAlbum)

            val standaloneFiles = fileUrls.mapIndexed { idx, urlInfo ->
                BunkrFileEntity(
                    fileId = urlInfo.id,
                    albumId = STANDALONE_ALBUM_ID,
                    title = "Imported File ${urlInfo.id}",
                    sourceUrl = urlInfo.canonicalUrl,
                    thumbnailUrl = null,
                    mediaType = "video",
                    orderIndex = idx,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            dao.insertFiles(standaloneFiles)
            discoveredCount += standaloneFiles.size
            playableCount += standaloneFiles.size
        }

        // Handle albums with parallel crawling & bounded concurrency (Semaphore=5)
        val semaphore = Semaphore(5)
        coroutineScope {
            albumUrls.map { urlInfo ->
                async {
                    semaphore.withPermit {
                        try {
                            val (album, files) = crawler.crawlAlbum(urlInfo.canonicalUrl)
                            dao.insertAlbum(BunkrAlbumEntity.fromDomain(album))
                            dao.insertFiles(files.map { BunkrFileEntity.fromDomain(it) })
                            synchronized(this) {
                                processedCount++
                                discoveredCount += files.size
                                playableCount += files.filter { it.isAvailable }.size
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error crawling album ${urlInfo.canonicalUrl}: ${e.message}", e)
                            val albumPlaceholder = BunkrAlbumEntity(
                                albumId = urlInfo.id,
                                title = "Album ${urlInfo.id} (Scan Failed)",
                                sourceUrl = urlInfo.canonicalUrl,
                                isEnabled = true,
                                lastScanTime = System.currentTimeMillis(),
                                itemCount = 0
                            )
                            dao.insertAlbum(albumPlaceholder)
                            synchronized(this) {
                                errors.add("Album ${urlInfo.id}: ${e.message ?: "Failed to crawl"}")
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        _isScanning.value = false
        val report = BunkrScanReport(
            totalAlbumsProcessed = albumUrls.size,
            totalItemsDiscovered = discoveredCount,
            playableCount = playableCount,
            failedCount = errors.size,
            skippedCount = 0,
            errors = errors,
            timestamp = System.currentTimeMillis()
        )
        _scanReport.value = report
        return@withContext report
    }

    /**
     * Rescan all or selected enabled albums in background.
     */
    suspend fun refreshAlbums(targetAlbumIds: List<String> = emptyList()): BunkrScanReport = withContext(Dispatchers.IO) {
        val allAlbums = dao.getAllAlbumsList()
        val toScan = if (targetAlbumIds.isNotEmpty()) {
            allAlbums.filter { targetAlbumIds.contains(it.albumId) && it.albumId != STANDALONE_ALBUM_ID }
        } else {
            allAlbums.filter { it.isEnabled && it.albumId != STANDALONE_ALBUM_ID }
        }

        if (toScan.isEmpty()) {
            return@withContext BunkrScanReport(errors = listOf("No enabled albums to refresh"))
        }

        _isScanning.value = true
        val errors = mutableListOf<String>()
        var processedCount = 0
        var discoveredCount = 0
        var playableCount = 0

        val semaphore = Semaphore(5)
        coroutineScope {
            toScan.map { albumEntity ->
                async {
                    semaphore.withPermit {
                        try {
                            val (album, files) = crawler.crawlAlbum(albumEntity.sourceUrl)

                            // Preserve existing stream URLs if still valid
                            val existingFiles = dao.getFilesForAlbumList(albumEntity.albumId).associateBy { it.fileId }
                            val updatedFiles = files.map { crawled ->
                                val existing = existingFiles[crawled.fileId]
                                if (existing != null && existing.streamUrl != null && existing.streamUrlExpiry > System.currentTimeMillis()) {
                                    crawled.copy(
                                        streamUrl = existing.streamUrl,
                                        streamUrlExpiry = existing.streamUrlExpiry
                                    )
                                } else crawled
                            }

                            dao.insertAlbum(BunkrAlbumEntity.fromDomain(album))
                            dao.insertFiles(updatedFiles.map { BunkrFileEntity.fromDomain(it) })

                            // Mark missing items as unavailable instead of deleting
                            val crawledIds = files.map { it.fileId }.toSet()
                            existingFiles.values.filter { !crawledIds.contains(it.fileId) }.forEach { missing ->
                                dao.setFileAvailable(missing.fileId, false)
                            }

                            synchronized(this) {
                                processedCount++
                                discoveredCount += files.size
                                playableCount += files.filter { it.isAvailable }.size
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Refresh failed for album ${albumEntity.albumId}: ${e.message}", e)
                            synchronized(this) {
                                errors.add("Album ${albumEntity.albumId}: ${e.message ?: "Refresh failed"}")
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        _isScanning.value = false
        val report = BunkrScanReport(
            totalAlbumsProcessed = toScan.size,
            totalItemsDiscovered = discoveredCount,
            playableCount = playableCount,
            failedCount = errors.size,
            errors = errors,
            timestamp = System.currentTimeMillis()
        )
        _scanReport.value = report
        return@withContext report
    }

    /**
     * Resolves the direct media CDN URL for a Bunkr file.
     * Uses cached stream URL if non-expired, otherwise resolves dynamically.
     */
    suspend fun resolveStreamForFile(file: BunkrFile): BunkrStreamResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!file.streamUrl.isNullOrBlank() && file.streamUrlExpiry > now) {
            val headers = BunkrUrlUtils.buildDefaultHeaders(file.sourceUrl)
            return@withContext BunkrStreamResult(
                source = "Bunkr",
                fileId = file.fileId,
                albumId = file.albumId,
                title = file.title,
                streamUrl = file.streamUrl,
                thumbnailUrl = file.thumbnailUrl,
                mimeType = if (file.streamUrl.contains(".m3u8", true)) "application/x-mpegURL" else "video/mp4",
                headers = headers
            )
        }

        val result = resolver.resolveFile(file.sourceUrl, file.albumId)

        // Cache stream URL in Room DB
        dao.updateStreamUrl(
            fileId = file.fileId,
            streamUrl = result.streamUrl,
            expiry = now + STREAM_URL_TTL_MS
        )

        return@withContext result
    }

    suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        dao.deleteFilesForAlbum(albumId)
        dao.deleteAlbum(albumId)
    }

    suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        dao.deleteFile(fileId)
    }

    suspend fun toggleAlbumEnabled(albumId: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setAlbumEnabled(albumId, isEnabled)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAllFiles()
        dao.clearAllAlbums()
    }
}
