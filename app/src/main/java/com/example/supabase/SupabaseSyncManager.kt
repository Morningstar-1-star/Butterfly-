package com.example.supabase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.bunkr.db.BunkrAlbumEntity
import com.example.bunkr.db.BunkrFileEntity
import com.example.cloudsocial.db.CloudSocialMediaEntity
import com.example.cloudsocial.db.CloudSocialSourceEntity
import com.example.db.*
import com.example.recommendation.UserActivityMemory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object SupabaseSyncManager {

    private const val TAG = "SupabaseSyncManager"
    private const val PREFS_SYNC = "butterfly_supabase_sync_prefs"
    private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"
    private const val KEY_AUTO_SYNC = "auto_sync_enabled"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()

    @Volatile
    private var appContext: Context? = null
    private var db: AppDatabase? = null
    private var client: SupabaseClient? = null

    private val _syncState = MutableStateFlow(SupabaseSyncState())
    val syncState: StateFlow<SupabaseSyncState> = _syncState.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        db = AppDatabase.getInstance(app)
        client = SupabaseClient(app)

        val prefs = app.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val lastSync = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        val autoSync = prefs.getBoolean(KEY_AUTO_SYNC, true)

        _syncState.value = _syncState.value.copy(
            lastSyncTimestamp = lastSync,
            autoSyncEnabled = autoSync
        )

        // Observe queue size in background
        scope.launch {
            db?.syncQueueDao()?.getQueueSizeFlow()?.collect { count ->
                _syncState.value = _syncState.value.copy(pendingQueueCount = count)
            }
        }
    }

    private fun getDb(): AppDatabase {
        return db ?: synchronized(this) {
            val ctx = appContext ?: throw IllegalStateException("SupabaseSyncManager not initialized")
            val d = AppDatabase.getInstance(ctx)
            db = d
            d
        }
    }

    private fun getClient(): SupabaseClient {
        return client ?: synchronized(this) {
            val ctx = appContext ?: throw IllegalStateException("SupabaseSyncManager not initialized")
            val c = SupabaseClient(ctx)
            client = c
            c
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SYNC, enabled)
            .apply()
        _syncState.value = _syncState.value.copy(autoSyncEnabled = enabled)
    }

    // =========================================================================
    // OFFLINE SYNC QUEUE
    // =========================================================================

    fun enqueueSync(entityType: String, entityId: String, action: String, payloadJson: String) {
        scope.launch {
            try {
                getDb().syncQueueDao().enqueue(
                    SyncQueueEntity(
                        entityType = entityType,
                        entityId = entityId,
                        action = action,
                        payloadJson = payloadJson,
                        createdAt = System.currentTimeMillis()
                    )
                )
                if (_syncState.value.autoSyncEnabled && SupabaseAuthManager.isLoggedIn.value) {
                    triggerSync(forceFull = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue sync for $entityType:$entityId", e)
            }
        }
    }

    // =========================================================================
    // PRIMARY SYNC ENTRY POINT
    // =========================================================================

    fun triggerSync(forceFull: Boolean = false) {
        if (!SupabaseAuthManager.isLoggedIn.value) {
            _syncState.value = _syncState.value.copy(syncMessage = "Not signed in to Supabase")
            return
        }

        scope.launch {
            syncMutex.withLock {
                if (_syncState.value.isSyncing) return@withLock
                _syncState.value = _syncState.value.copy(isSyncing = true, syncMessage = "Connecting...")

                try {
                    val token = SupabaseAuthManager.getValidAccessToken()
                    if (token == null) {
                        _syncState.value = _syncState.value.copy(
                            isSyncing = false,
                            syncMessage = "Authentication expired. Please sign in again."
                        )
                        return@withLock
                    }

                    val user = SupabaseAuthManager.currentUser.value
                    if (user == null) {
                        _syncState.value = _syncState.value.copy(isSyncing = false, syncMessage = "No user found")
                        return@withLock
                    }

                    // 1. Drain offline queue first
                    _syncState.value = _syncState.value.copy(syncMessage = "Uploading offline changes...")
                    drainSyncQueue(token, user.id)

                    // 2. Perform Full Bidirectional Sync if forced or overdue
                    val now = System.currentTimeMillis()
                    val lastSync = _syncState.value.lastSyncTimestamp
                    if (forceFull || (now - lastSync > 60_000L)) {
                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing watch history & bookmarks...")
                        syncWatchHistory(token, user.id)
                        syncWatchLater(token, user.id)
                        syncLikedVideos(token, user.id)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing playlists & searches...")
                        syncPlaylists(token, user.id)
                        syncSearchHistory(token, user.id)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing Bunkr & CloudSocial...")
                        syncBunkr(token, user.id)
                        syncCloudSocial(token, user.id)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing preferences & intelligence...")
                        syncPreferencesAndProfile(token, user.id)
                        syncOfflineDownloadsMetadata(token, user.id)
                        syncBehaviorSignals(token, user.id)
                    }

                    val ctx = appContext
                    if (ctx != null) {
                        ctx.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_SYNC_TIME, now)
                            .apply()
                    }

                    _syncState.value = _syncState.value.copy(
                        isSyncing = false,
                        lastSyncTimestamp = now,
                        syncMessage = "Synced successfully"
                    )
                    Log.i(TAG, "Supabase sync complete at $now")
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed with exception", e)
                    _syncState.value = _syncState.value.copy(
                        isSyncing = false,
                        syncMessage = "Sync failed: ${e.message ?: "Network error"}"
                    )
                }
            }
        }
    }

    // =========================================================================
    // DRAIN OFFLINE QUEUE
    // =========================================================================

    private suspend fun drainSyncQueue(token: String, userId: String) {
        val queueDao = getDb().syncQueueDao()
        val items = queueDao.peek(limit = 40)
        if (items.isEmpty()) return

        val client = getClient()
        val toDelete = mutableListOf<Long>()

        for (item in items) {
            try {
                val success = when (item.action) {
                    "UPSERT" -> {
                        val tableName = mapEntityToTable(item.entityType)
                        val res = client.upsert(tableName, item.payloadJson, token)
                        res.isSuccess
                    }
                    "DELETE" -> {
                        val tableName = mapEntityToTable(item.entityType)
                        val idColumn = mapEntityToIdColumn(item.entityType)
                        val res = client.delete(tableName, "$idColumn=eq.${item.entityId}", token)
                        res.isSuccess
                    }
                    else -> true
                }

                if (success) {
                    toDelete.add(item.id)
                } else {
                    queueDao.recordFailure(item.id, "Upsert failed")
                }
            } catch (e: Exception) {
                queueDao.recordFailure(item.id, e.message ?: "Error")
            }
        }

        if (toDelete.isNotEmpty()) {
            queueDao.deleteByIds(toDelete)
        }
    }

    private fun mapEntityToTable(entityType: String): String = when (entityType) {
        "WATCH_HISTORY" -> "watch_history"
        "BOOKMARK" -> "watch_later"
        "LIKED_VIDEO" -> "liked_videos"
        "USER_PLAYLIST" -> "user_playlists"
        "SEARCH_HISTORY" -> "search_history"
        "BUNKR_ALBUM" -> "bunkr_albums"
        "BUNKR_FILE" -> "bunkr_files"
        "CLOUD_SOCIAL_SOURCE" -> "cloud_social_sources"
        "CLOUD_SOCIAL_MEDIA" -> "cloud_social_media"
        "USER_PROFILE" -> "user_profiles"
        "APP_PREFERENCES" -> "app_preferences"
        "DOWNLOAD_METADATA" -> "offline_downloads_metadata"
        "BEHAVIOR_SIGNAL" -> "user_behavior_signals"
        "PREFERENCE_PROFILE" -> "user_preference_profile"
        else -> "watch_history"
    }

    private fun mapEntityToIdColumn(entityType: String): String = when (entityType) {
        "WATCH_HISTORY", "BOOKMARK", "LIKED_VIDEO", "DOWNLOAD_METADATA" -> "video_id"
        "USER_PLAYLIST" -> "playlist_id"
        "SEARCH_HISTORY" -> "query"
        "BUNKR_ALBUM" -> "album_id"
        "BUNKR_FILE" -> "file_id"
        "CLOUD_SOCIAL_SOURCE" -> "source_id"
        "CLOUD_SOCIAL_MEDIA" -> "media_id"
        "USER_PROFILE", "APP_PREFERENCES", "PREFERENCE_PROFILE" -> "user_id"
        else -> "id"
    }

    // =========================================================================
    // ENTITY-SPECIFIC BIDIRECTIONAL SYNC METHODS
    // =========================================================================

    private suspend fun syncWatchHistory(token: String, userId: String) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllWatchHistoryList()

        // 1. Upload local to Cloud
        if (localList.isNotEmpty()) {
            val arr = JSONArray()
            for (item in localList.take(200)) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("video_id", item.videoId)
                    put("title", item.title)
                    put("channel_name", item.channelName)
                    put("thumbnail_url", item.thumbnailUrl ?: "")
                    put("duration", item.duration)
                    put("progress_fraction", item.progressFraction)
                    put("provider_id", item.providerId ?: "youtube")
                    put("timestamp", item.timestamp)
                }
                arr.put(obj)
            }
            getClient().upsert("watch_history", arr.toString(), token)
        }

        // 2. Download cloud rows
        val remoteRes = getClient().select("watch_history", "select=*&order=timestamp.desc&limit=150", token)
        if (remoteRes.isSuccess) {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localIds = localList.associateBy { it.videoId }
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val vId = obj.optString("video_id")
                val time = obj.optLong("timestamp", 0L)
                val local = localIds[vId]
                if (local == null || time > local.timestamp) {
                    dao.insertWatchHistory(
                        WatchHistoryEntity(
                            videoId = vId,
                            title = obj.optString("title", vId),
                            channelName = obj.optString("channel_name"),
                            thumbnailUrl = obj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                            duration = obj.optString("duration"),
                            progressFraction = obj.optDouble("progress_fraction", 0.0).toFloat(),
                            providerId = obj.optString("provider_id", "youtube"),
                            timestamp = time
                        )
                    )
                }
            }
        }
    }

    private suspend fun syncWatchLater(token: String, userId: String) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllBookmarksList()

        if (localList.isNotEmpty()) {
            val arr = JSONArray()
            for (item in localList.take(200)) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("video_id", item.videoId)
                    put("title", item.title)
                    put("channel_name", item.channelName)
                    put("thumbnail_url", item.thumbnailUrl ?: "")
                    put("duration", item.duration)
                    put("provider_id", item.providerId ?: "youtube")
                    put("timestamp", item.timestamp)
                }
                arr.put(obj)
            }
            getClient().upsert("watch_later", arr.toString(), token)
        }

        val remoteRes = getClient().select("watch_later", "select=*&order=timestamp.desc&limit=200", token)
        if (remoteRes.isSuccess) {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localIds = localList.associateBy { it.videoId }
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val vId = obj.optString("video_id")
                if (!localIds.containsKey(vId)) {
                    dao.insertBookmark(
                        BookmarkEntity(
                            videoId = vId,
                            title = obj.optString("title", vId),
                            channelName = obj.optString("channel_name"),
                            thumbnailUrl = obj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                            duration = obj.optString("duration"),
                            providerId = obj.optString("provider_id", "youtube"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
    }

    private suspend fun syncLikedVideos(token: String, userId: String) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllLikedVideosList()

        if (localList.isNotEmpty()) {
            val arr = JSONArray()
            for (item in localList.take(200)) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("video_id", item.videoId)
                    put("title", item.title)
                    put("channel_name", item.channelName)
                    put("thumbnail_url", item.thumbnailUrl ?: "")
                    put("duration", item.duration)
                    put("provider_id", item.providerId ?: "youtube")
                    put("timestamp", item.timestamp)
                }
                arr.put(obj)
            }
            getClient().upsert("liked_videos", arr.toString(), token)
        }

        val remoteRes = getClient().select("liked_videos", "select=*&order=timestamp.desc&limit=200", token)
        if (remoteRes.isSuccess) {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localIds = localList.associateBy { it.videoId }
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val vId = obj.optString("video_id")
                if (!localIds.containsKey(vId)) {
                    dao.insertLikedVideo(
                        LikedVideoEntity(
                            videoId = vId,
                            title = obj.optString("title", vId),
                            channelName = obj.optString("channel_name"),
                            thumbnailUrl = obj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                            duration = obj.optString("duration"),
                            providerId = obj.optString("provider_id", "youtube"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
    }

    private suspend fun syncPlaylists(token: String, userId: String) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllPlaylistsList()

        if (localList.isNotEmpty()) {
            val arr = JSONArray()
            for (item in localList) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("playlist_id", item.id)
                    put("title", item.title)
                    put("videos_json", item.videosJson)
                    put("created_at", item.createdAt)
                }
                arr.put(obj)
            }
            getClient().upsert("user_playlists", arr.toString(), token)
        }

        val remoteRes = getClient().select("user_playlists", "select=*", token)
        if (remoteRes.isSuccess) {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localIds = localList.associateBy { it.id }
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val pId = obj.optString("playlist_id")
                val local = localIds[pId]
                if (local == null) {
                    dao.insertOrUpdatePlaylist(
                        UserPlaylistEntity(
                            id = pId,
                            title = obj.optString("title", "Playlist"),
                            videosJson = obj.opt("videos_json")?.toString() ?: "[]",
                            createdAt = obj.optLong("created_at", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
    }

    private suspend fun syncSearchHistory(token: String, userId: String) {
        val dao = getDb().searchHistoryDao()
        val localList = dao.getAllSearchHistoryList()

        if (localList.isNotEmpty()) {
            val arr = JSONArray()
            for (item in localList.take(50)) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("query", item.query)
                    put("timestamp", item.timestamp)
                }
                arr.put(obj)
            }
            getClient().upsert("search_history", arr.toString(), token)
        }

        val remoteRes = getClient().select("search_history", "select=*&order=timestamp.desc&limit=50", token)
        if (remoteRes.isSuccess) {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localQueries = localList.map { it.query }.toSet()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val q = obj.optString("query")
                if (q.isNotBlank() && !localQueries.contains(q)) {
                    dao.insertSearchQuery(
                        SearchHistoryEntity(
                            query = q,
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
    }

    private suspend fun syncBunkr(token: String, userId: String) {
        val bunkrDao = getDb().bunkrDao()
        val albums = bunkrDao.getAllAlbumsList()
        val files = bunkrDao.getAllFilesList()

        if (albums.isNotEmpty()) {
            val albumArr = JSONArray()
            for (a in albums) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("album_id", a.albumId)
                    put("title", a.title)
                    put("source_url", a.sourceUrl)
                    put("is_enabled", a.isEnabled)
                    put("last_scan_time", a.lastScanTime)
                    put("item_count", a.itemCount)
                    put("created_at", a.createdAt)
                }
                albumArr.put(obj)
            }
            getClient().upsert("bunkr_albums", albumArr.toString(), token)
        }

        if (files.isNotEmpty()) {
            val fileArr = JSONArray()
            for (f in files.take(300)) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("file_id", f.fileId)
                    put("album_id", f.albumId)
                    put("title", f.title)
                    put("source_url", f.sourceUrl)
                    put("thumbnail_url", f.thumbnailUrl ?: "")
                    put("media_type", f.mediaType)
                    put("duration", f.duration)
                    put("resolution", f.resolution)
                    put("file_size", f.fileSize)
                    put("order_index", f.orderIndex)
                    put("last_updated", f.lastUpdated)
                }
                fileArr.put(obj)
            }
            getClient().upsert("bunkr_files", fileArr.toString(), token)
        }

        val remoteAlbums = getClient().select("bunkr_albums", "select=*", token)
        if (remoteAlbums.isSuccess) {
            val arr = JSONArray(remoteAlbums.getOrThrow())
            val localIds = albums.associateBy { it.albumId }
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val aId = obj.optString("album_id")
                if (!localIds.containsKey(aId)) {
                    bunkrDao.insertAlbum(
                        BunkrAlbumEntity(
                            albumId = aId,
                            title = obj.optString("title"),
                            sourceUrl = obj.optString("source_url"),
                            isEnabled = obj.optBoolean("is_enabled", true),
                            lastScanTime = obj.optLong("last_scan_time", 0L),
                            itemCount = obj.optInt("item_count", 0),
                            createdAt = obj.optLong("created_at", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
    }

    private suspend fun syncCloudSocial(token: String, userId: String) {
        val csDao = getDb().cloudSocialDao()
        val sources = csDao.getAllSourcesList()
        val media = csDao.getAllMediaList()

        if (sources.isNotEmpty()) {
            val sourceArr = JSONArray()
            for (s in sources) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("source_id", s.id)
                    put("type", s.type)
                    put("name", s.name)
                    put("source_url", s.sourceUrl)
                    put("enabled", s.enabled)
                    put("last_sync_timestamp", s.lastSyncTimestamp)
                    put("item_count", s.itemCount)
                    put("new_item_count", s.newItemCount)
                    put("extra_config_json", s.extraConfigJson)
                }
                sourceArr.put(obj)
            }
            getClient().upsert("cloud_social_sources", sourceArr.toString(), token)
        }

        if (media.isNotEmpty()) {
            val mediaArr = JSONArray()
            for (m in media.take(300)) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("media_id", m.id)
                    put("source_id", m.sourceId)
                    put("type", m.type)
                    put("remote_id", m.remoteId)
                    put("parent_id", m.parentId ?: "")
                    put("title", m.title)
                    put("caption", m.caption ?: "")
                    put("source_url", m.sourceUrl)
                    put("direct_stream_url", m.directStreamUrl ?: "")
                    put("thumbnail_url", m.thumbnailUrl ?: "")
                    put("mime_type", m.mimeType)
                    put("file_size", m.fileSize)
                    put("formatted_size", m.formattedSize)
                    put("duration_ms", m.durationMs)
                    put("media_category", m.mediaCategory)
                    put("date_timestamp", m.dateTimestamp)
                    put("resolution", m.resolution)
                    put("headers_json", m.headersJson)
                }
                mediaArr.put(obj)
            }
            getClient().upsert("cloud_social_media", mediaArr.toString(), token)
        }
    }

    private suspend fun syncPreferencesAndProfile(token: String, userId: String) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("butterfly_user_profile", Context.MODE_PRIVATE)
        val name = prefs.getString("profile_name", "") ?: ""
        val handle = prefs.getString("profile_handle", "") ?: ""
        val bio = prefs.getString("profile_bio", "") ?: ""
        val avatarUrl = prefs.getString("profile_avatar_url", null)
        val avatarPreset = prefs.getString("profile_avatar_preset", null)

        val profileObj = JSONObject().apply {
            put("user_id", userId)
            put("name", name)
            put("handle", handle)
            put("bio", bio)
            put("avatar_url", avatarUrl ?: JSONObject.NULL)
            put("avatar_preset", avatarPreset ?: JSONObject.NULL)
        }
        getClient().upsert("user_profiles", profileObj.toString(), token)

        // Read settings preferences
        val settingsPrefs = ctx.getSharedPreferences("butterfly_settings", Context.MODE_PRIVATE)
        val allSettings = settingsPrefs.all
        val settingsObj = JSONObject().apply {
            put("user_id", userId)
            val jsonMap = JSONObject()
            for ((k, v) in allSettings) {
                jsonMap.put(k, v)
            }
            put("preferences_json", jsonMap)
        }
        getClient().upsert("app_preferences", settingsObj.toString(), token)
    }

    private suspend fun syncOfflineDownloadsMetadata(token: String, userId: String) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllOfflineDownloadsList()
        if (localList.isEmpty()) return

        val arr = JSONArray()
        for (item in localList) {
            val obj = JSONObject().apply {
                put("user_id", userId)
                put("video_id", item.videoId)
                put("title", item.title)
                put("channel_name", item.channelName)
                put("thumbnail_url", item.thumbnailUrl ?: "")
                put("quality_label", item.qualityLabel)
                put("total_bytes", item.totalBytes)
                put("status", item.status)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        getClient().upsert("offline_downloads_metadata", arr.toString(), token)
    }

    // =========================================================================
    // RECOMMENDATION INTELLIGENCE SIGNALS & PROFILE SYNC
    // =========================================================================

    fun recordBehaviorSignal(
        videoId: String,
        eventType: String, // "watched", "completed", "liked", "disliked", "skipped", "dwell"
        watchTimeMs: Long = 0L,
        progressFraction: Float = 0f,
        category: String = "general",
        channelName: String = "",
        providerId: String = "youtube",
        metadata: Map<String, Any> = emptyMap()
    ) {
        val user = SupabaseAuthManager.currentUser.value
        val signalObj = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            if (user != null) put("user_id", user.id)
            put("video_id", videoId)
            put("event_type", eventType)
            put("watch_time_ms", watchTimeMs)
            put("progress_fraction", progressFraction)
            put("category", category)
            put("channel_name", channelName)
            put("provider_id", providerId)
            put("hour_of_day", java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
            val metaObj = JSONObject()
            for ((k, v) in metadata) metaObj.put(k, v)
            put("metadata", metaObj)
        }

        enqueueSync("BEHAVIOR_SIGNAL", videoId, "UPSERT", signalObj.toString())
    }

    private suspend fun syncBehaviorSignals(token: String, userId: String) {
        val ctx = appContext ?: return
        // Export behavioral intelligence from UserActivityMemory
        try {
            val dislikedVideosJson = UserActivityMemory.exportDislikedVideosJson(ctx)
            val dislikedChannelsSet = UserActivityMemory.getDislikedChannels()
            val dislikedKeywordsSet = UserActivityMemory.getDislikedKeywords()

            val profileObj = JSONObject().apply {
                put("user_id", userId)
                put("disliked_videos", JSONArray(dislikedVideosJson))
                put("disliked_channels", JSONArray(dislikedChannelsSet))
                put("disliked_keywords", JSONArray(dislikedKeywordsSet))
            }
            getClient().upsert("user_preference_profile", profileObj.toString(), token)
        } catch (e: Exception) {
            Log.w(TAG, "Failed syncing behavior intelligence: ${e.message}")
        }
    }
}
