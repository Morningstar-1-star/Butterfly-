package com.example.supabase

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.bunkr.db.BunkrAlbumEntity
import com.example.bunkr.db.BunkrFileEntity
import com.example.cloudsocial.db.CloudSocialMediaEntity
import com.example.cloudsocial.db.CloudSocialSourceEntity
import com.example.db.*
import com.example.model.SubscribedChannel
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

    var onSubscriptionsUpdated: (() -> Unit)? = null
    var onPlaylistsUpdated: (() -> Unit)? = null
    var onProfileUpdated: (() -> Unit)? = null

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
            try {
                db?.syncQueueDao()?.getQueueSizeFlow()?.collect { count ->
                    _syncState.value = _syncState.value.copy(pendingQueueCount = count)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Queue flow error: ${e.message}")
            }
        }

        // Automatic retry when connectivity returns
        registerNetworkCallback(app)
    }

    private fun registerNetworkCallback(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Internet connectivity restored; triggering sync")
                    if (SupabaseAuthManager.isLoggedIn.value && _syncState.value.autoSyncEnabled) {
                        triggerSync(forceFull = false)
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
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
                val queueDao = getDb().syncQueueDao()
                // Replace any pending operation for same entity to avoid queue bloat
                queueDao.deleteByEntity(entityType, entityId)
                queueDao.enqueue(
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
                _syncState.value = _syncState.value.copy(
                    isSyncing = true,
                    syncMessage = "Connecting to Supabase...",
                    lastError = null
                )

                val syncErrors = mutableListOf<String>()

                try {
                    val token = SupabaseAuthManager.getValidAccessToken()
                    if (token == null) {
                        _syncState.value = _syncState.value.copy(
                            isSyncing = false,
                            syncMessage = "Authentication expired. Please sign in again.",
                            lastError = "Authentication token expired or null"
                        )
                        return@withLock
                    }

                    val user = SupabaseAuthManager.currentUser.value
                    if (user == null || user.id.isBlank()) {
                        _syncState.value = _syncState.value.copy(
                            isSyncing = false,
                            syncMessage = "No user found",
                            lastError = "User profile missing"
                        )
                        return@withLock
                    }

                    val userId = user.id

                    // 1. Drain offline queue first
                    _syncState.value = _syncState.value.copy(syncMessage = "Uploading offline changes...")
                    drainSyncQueue(token, userId, syncErrors)

                    // 2. Perform Full Bidirectional Sync if forced or overdue
                    val now = System.currentTimeMillis()
                    val lastSync = _syncState.value.lastSyncTimestamp
                    if (forceFull || (now - lastSync > 60_000L)) {
                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing user profile...")
                        syncUserProfile(token, userId, syncErrors)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing watch history & bookmarks...")
                        syncWatchHistory(token, userId, syncErrors)
                        syncWatchLater(token, userId, syncErrors)
                        syncLikedVideos(token, userId, syncErrors)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing playlists...")
                        syncPlaylists(token, userId, syncErrors)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing channel subscriptions...")
                        syncSubscriptions(token, userId, syncErrors)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing saved links & sources...")
                        syncSavedLinks(token, userId, syncErrors)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing search history...")
                        syncSearchHistory(token, userId, syncErrors)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing Bunkr albums & files...")
                        syncBunkr(token, userId, syncErrors)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing CloudSocial sources & media...")
                        syncCloudSocial(token, userId, syncErrors)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing offline downloads metadata...")
                        syncOfflineDownloadsMetadata(token, userId, syncErrors)

                        _syncState.value = _syncState.value.copy(syncMessage = "Syncing app preferences & profile...")
                        syncAppPreferences(token, userId, syncErrors)
                        syncBehaviorSignals(token, userId, syncErrors)
                    }

                    val ctx = appContext
                    if (ctx != null && syncErrors.isEmpty()) {
                        ctx.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_SYNC_TIME, now)
                            .apply()
                    }

                    if (syncErrors.isEmpty()) {
                        _syncState.value = _syncState.value.copy(
                            isSyncing = false,
                            lastSyncTimestamp = now,
                            syncMessage = "Synced successfully",
                            lastError = null
                        )
                        Log.i(TAG, "Supabase sync successfully completed at $now")
                    } else {
                        val errorSummary = syncErrors.joinToString("; ")
                        _syncState.value = _syncState.value.copy(
                            isSyncing = false,
                            syncMessage = "Sync completed with ${syncErrors.size} error(s)",
                            lastError = errorSummary
                        )
                        Log.e(TAG, "Supabase sync completed with errors: $errorSummary")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed with unhandled exception", e)
                    val errMsg = e.message ?: "Network error"
                    _syncState.value = _syncState.value.copy(
                        isSyncing = false,
                        syncMessage = "Sync failed: $errMsg",
                        lastError = errMsg
                    )
                }
            }
        }
    }

    // =========================================================================
    // DRAIN OFFLINE QUEUE
    // =========================================================================

    private suspend fun drainSyncQueue(token: String, userId: String, syncErrors: MutableList<String>) {
        val queueDao = getDb().syncQueueDao()
        val items = queueDao.peek(limit = 60)
        if (items.isEmpty()) return

        val client = getClient()
        val toDelete = mutableListOf<Long>()

        for (item in items) {
            val tableName = mapEntityToTable(item.entityType)
            try {
                val success = when (item.action) {
                    "UPSERT" -> {
                        // Ensure authenticated userId is always injected into payload
                        val effectivePayload = try {
                            val obj = JSONObject(item.payloadJson)
                            obj.put("user_id", userId)
                            if (item.entityType == "SAVED_LINK") {
                                val url = obj.optString("url")
                                if (obj.optString("id").isBlank() && url.isNotBlank()) {
                                    obj.put("id", UUID.nameUUIDFromBytes("$userId:$url".toByteArray()).toString())
                                }
                            }
                            if (item.entityType == "USER_SUBSCRIPTION") {
                                val now = System.currentTimeMillis()
                                if (!obj.has("created_at") || obj.optLong("created_at", 0L) <= 0L) obj.put("created_at", now)
                                if (!obj.has("updated_at") || obj.optLong("updated_at", 0L) <= 0L) obj.put("updated_at", now)
                            }
                            obj.toString()
                        } catch (_: Exception) {
                            item.payloadJson
                        }
                        val res = client.upsert(tableName, effectivePayload, token)
                        if (res.isFailure) {
                            val err = "[table: $tableName, op: UPSERT] ${res.exceptionOrNull()?.message ?: "Failed"}"
                            Log.e(TAG, "Queue item failure: $err")
                            syncErrors.add(err)
                            queueDao.recordFailure(item.id, err)
                            false
                        } else true
                    }
                    "DELETE" -> {
                        val idColumn = mapEntityToIdColumn(item.entityType)
                        val encId = java.net.URLEncoder.encode(item.entityId, "UTF-8")
                        val res = client.delete(tableName, "user_id=eq.$userId&$idColumn=eq.$encId", token)
                        if (res.isFailure) {
                            val err = "[table: $tableName, op: DELETE] ${res.exceptionOrNull()?.message ?: "Failed"}"
                            Log.e(TAG, "Queue item delete failure: $err")
                            syncErrors.add(err)
                            queueDao.recordFailure(item.id, err)
                            false
                        } else true
                    }
                    else -> true
                }

                if (success) {
                    toDelete.add(item.id)
                }
            } catch (e: Exception) {
                val err = "[table: $tableName] ${e.message ?: "Queue error"}"
                Log.e(TAG, "Queue exception: $err", e)
                syncErrors.add(err)
                queueDao.recordFailure(item.id, err)
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
        "USER_SUBSCRIPTION" -> "user_subscriptions"
        "SAVED_LINK" -> "saved_links"
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
        "USER_SUBSCRIPTION" -> "channel_id"
        "SAVED_LINK" -> "url"
        "SEARCH_HISTORY" -> "query"
        "BUNKR_ALBUM" -> "album_id"
        "BUNKR_FILE" -> "file_id"
        "CLOUD_SOCIAL_SOURCE" -> "source_id"
        "CLOUD_SOCIAL_MEDIA" -> "media_id"
        "USER_PROFILE", "APP_PREFERENCES", "PREFERENCE_PROFILE" -> "user_id"
        else -> "id"
    }

    // =========================================================================
    // 1. USER PROFILE SYNC
    // =========================================================================

    private suspend fun syncUserProfile(token: String, userId: String, syncErrors: MutableList<String>) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)

        // 1. Download cloud profile
        val remoteRes = getClient().select("user_profiles", "user_id=eq.$userId&select=*", token)
        if (remoteRes.isFailure) {
            val err = "[table: user_profiles, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            if (jsonArr.length() > 0) {
                val cloud = jsonArr.getJSONObject(0)
                val cName = cloud.optString("name", "")
                val cHandle = cloud.optString("handle", "")
                val cBio = cloud.optString("bio", "")
                val cAvatar = cloud.optString("avatar_url").takeIf { it.isNotBlank() && it != "null" }
                val cPreset = cloud.optString("avatar_preset").takeIf { it.isNotBlank() && it != "null" }

                val localName = prefs.getString("user_name", "") ?: ""
                // Restore if local is blank or default and cloud has a real name
                if ((localName.isBlank() || localName == "Lucifer") && cName.isNotBlank()) {
                    prefs.edit()
                        .putString("user_name", cName)
                        .putString("user_handle", cHandle)
                        .putString("user_bio", cBio)
                        .putString("user_avatar_url", cAvatar)
                        .putString("user_avatar_preset", cPreset ?: "purple")
                        .apply()

                    // Also mirror into butterfly_user_profile for legacy safety
                    ctx.getSharedPreferences("butterfly_user_profile", Context.MODE_PRIVATE).edit()
                        .putString("profile_name", cName)
                        .putString("profile_handle", cHandle)
                        .putString("profile_bio", cBio)
                        .putString("profile_avatar_url", cAvatar)
                        .putString("profile_avatar_preset", cPreset ?: "purple")
                        .apply()

                    withContext(Dispatchers.Main) {
                        onProfileUpdated?.invoke()
                    }
                }
            }
        }

        // 2. Push local profile if populated
        val localName = prefs.getString("user_name", "") ?: ""
        if (localName.isNotBlank()) {
            val profileObj = JSONObject().apply {
                put("user_id", userId)
                put("name", localName)
                put("handle", prefs.getString("user_handle", "@lucifer") ?: "@lucifer")
                put("bio", prefs.getString("user_bio", "Passionate video lover & content curator.") ?: "Passionate video lover & content curator.")
                val aUrl = prefs.getString("user_avatar_url", null)
                val aPreset = prefs.getString("user_avatar_preset", "purple")
                put("avatar_url", aUrl ?: JSONObject.NULL)
                put("avatar_preset", aPreset ?: "purple")
            }
            val upRes = getClient().upsert("user_profiles", profileObj.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: user_profiles, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 2 & 3. WATCH HISTORY & PROGRESS SYNC
    // =========================================================================

    private suspend fun syncWatchHistory(token: String, userId: String, syncErrors: MutableList<String>) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllWatchHistoryList()

        // 1. Download cloud rows
        val remoteRes = getClient().select("watch_history", "user_id=eq.$userId&select=*&order=timestamp.desc&limit=150", token)
        if (remoteRes.isFailure) {
            val err = "[table: watch_history, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localIds = localList.associateBy { it.videoId }
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val vId = obj.optString("video_id")
                val time = obj.optLong("timestamp", 0L)
                val progress = obj.optDouble("progress_fraction", 0.0).toFloat()
                val local = localIds[vId]
                if (local == null || time > local.timestamp || progress > local.progressFraction) {
                    dao.insertWatchHistory(
                        WatchHistoryEntity(
                            videoId = vId,
                            title = obj.optString("title", vId),
                            channelName = obj.optString("channel_name"),
                            thumbnailUrl = obj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                            duration = obj.optString("duration"),
                            progressFraction = progress,
                            providerId = obj.optString("provider_id", "youtube"),
                            timestamp = time
                        )
                    )
                }
            }
        }

        // 2. Upload local to Cloud
        if (localList.isNotEmpty()) {
            val arr = JSONArray()
            for (item in localList.take(150)) {
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
            val upRes = getClient().upsert("watch_history", arr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: watch_history, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 4. WATCH LATER SYNC
    // =========================================================================

    private suspend fun syncWatchLater(token: String, userId: String, syncErrors: MutableList<String>) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllBookmarksList()

        // 1. Download cloud rows
        val remoteRes = getClient().select("watch_later", "user_id=eq.$userId&select=*&order=timestamp.desc&limit=200", token)
        if (remoteRes.isFailure) {
            val err = "[table: watch_later, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
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

        // 2. Upload local to Cloud
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
            val upRes = getClient().upsert("watch_later", arr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: watch_later, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 5. LIKED VIDEOS SYNC
    // =========================================================================

    private suspend fun syncLikedVideos(token: String, userId: String, syncErrors: MutableList<String>) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllLikedVideosList()

        // 1. Download cloud rows
        val remoteRes = getClient().select("liked_videos", "user_id=eq.$userId&select=*&order=timestamp.desc&limit=200", token)
        if (remoteRes.isFailure) {
            val err = "[table: liked_videos, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
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

        // 2. Upload local to Cloud
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
            val upRes = getClient().upsert("liked_videos", arr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: liked_videos, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 6 & 7. PLAYLISTS & PLAYLIST VIDEOS SYNC
    // =========================================================================

    private suspend fun syncPlaylists(token: String, userId: String, syncErrors: MutableList<String>) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllPlaylistsList()

        // 1. Download cloud playlists
        val remoteRes = getClient().select("user_playlists", "user_id=eq.$userId&select=*", token)
        if (remoteRes.isFailure) {
            val err = "[table: user_playlists, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localIds = localList.associateBy { it.id }
            var changed = false
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val pId = obj.optString("playlist_id")
                val title = obj.optString("title", "Playlist")
                val rawVideos = obj.opt("videos_json")
                val videosStr = when (rawVideos) {
                    is JSONArray -> rawVideos.toString()
                    is String -> rawVideos
                    else -> "[]"
                }
                val createdAt = obj.optLong("created_at", System.currentTimeMillis())
                val local = localIds[pId]
                if (local == null) {
                    dao.insertOrUpdatePlaylist(
                        UserPlaylistEntity(
                            id = pId,
                            title = title,
                            videosJson = videosStr,
                            createdAt = createdAt
                        )
                    )
                    changed = true
                } else {
                    // Two-way merge: if remote has videos and local is empty, or remote is newer
                    if (local.videosJson.trim() == "[]" && videosStr.trim() != "[]") {
                        dao.insertOrUpdatePlaylist(local.copy(videosJson = videosStr))
                        changed = true
                    }
                }
            }
            if (changed) {
                withContext(Dispatchers.Main) {
                    onPlaylistsUpdated?.invoke()
                }
            }
        }

        // 2. Upload local playlists
        val updatedLocal = dao.getAllPlaylistsList()
        if (updatedLocal.isNotEmpty()) {
            val arr = JSONArray()
            for (item in updatedLocal) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("playlist_id", item.id)
                    put("title", item.title.ifBlank { "Playlist" })
                    // Store as native json array so PostgreSQL jsonb validates properly
                    val parsedJson = try { JSONArray(item.videosJson) } catch (_: Exception) { JSONArray() }
                    put("videos_json", parsedJson)
                    put("created_at", if (item.createdAt > 0L) item.createdAt else System.currentTimeMillis())
                }
                arr.put(obj)
            }
            val upRes = getClient().upsert("user_playlists", arr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: user_playlists, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 8. SEARCH HISTORY SYNC
    // =========================================================================

    private suspend fun syncSearchHistory(token: String, userId: String, syncErrors: MutableList<String>) {
        val dao = getDb().searchHistoryDao()
        val localList = dao.getAllSearchHistoryList()

        // 1. Download cloud searches
        val remoteRes = getClient().select("search_history", "user_id=eq.$userId&select=*&order=timestamp.desc&limit=50", token)
        if (remoteRes.isFailure) {
            val err = "[table: search_history, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localQueries = localList.map { it.query.trim().lowercase() }.toSet()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val q = obj.optString("query")
                if (q.isNotBlank() && !localQueries.contains(q.trim().lowercase())) {
                    dao.insertSearchQuery(
                        SearchHistoryEntity(
                            query = q,
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        // 2. Upload local searches
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
            val upRes = getClient().upsert("search_history", arr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: search_history, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 9. CHANNEL SUBSCRIPTIONS SYNC
    // =========================================================================

    private suspend fun syncSubscriptions(token: String, userId: String, syncErrors: MutableList<String>) {
        val ctx = appContext ?: return
        val subPrefs = ctx.getSharedPreferences("subscriptions_prefs", Context.MODE_PRIVATE)

        // Helper to load local channels
        fun readLocalChannels(): MutableList<SubscribedChannel> {
            val jsonStr = subPrefs.getString("subscribed_channels_json", null) ?: return mutableListOf()
            return try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<SubscribedChannel>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SubscribedChannel(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            handle = obj.optString("handle"),
                            avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() },
                            subscriberCount = obj.optString("subscriberCount", "Subscribed"),
                            hasUnreadUpdates = obj.optBoolean("hasUnreadUpdates", false),
                            notificationEnabled = obj.optBoolean("notificationEnabled", true),
                            description = obj.optString("description")
                        )
                    )
                }
                list
            } catch (_: Exception) {
                mutableListOf()
            }
        }

        fun writeLocalChannels(channels: List<SubscribedChannel>) {
            val arr = JSONArray()
            for (c in channels) {
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("handle", c.handle)
                    put("avatarUrl", c.avatarUrl ?: "")
                    put("subscriberCount", c.subscriberCount)
                    put("hasUnreadUpdates", c.hasUnreadUpdates)
                    put("notificationEnabled", c.notificationEnabled)
                    put("description", c.description ?: "")
                }
                arr.put(obj)
            }
            subPrefs.edit().putString("subscribed_channels_json", arr.toString()).apply()
        }

        val localChannels = readLocalChannels()

        // 1. Pull remote subscriptions
        val remoteRes = getClient().select("user_subscriptions", "user_id=eq.$userId&select=*", token)
        if (remoteRes.isFailure) {
            val err = "[table: user_subscriptions, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localIds = localChannels.map { it.id }.toSet()
            var added = false
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val cId = obj.optString("channel_id")
                val cName = obj.optString("channel_name", cId)
                if (cId.isNotBlank() && !localIds.contains(cId)) {
                    localChannels.add(
                        SubscribedChannel(
                            id = cId,
                            name = cName,
                            handle = obj.optString("handle").ifBlank { "@${cName.replace(" ", "")}" },
                            avatarUrl = obj.optString("avatar_url").takeIf { it.isNotBlank() },
                            subscriberCount = obj.optString("subscriber_count", "Subscribed"),
                            hasUnreadUpdates = false,
                            notificationEnabled = obj.optBoolean("notification_enabled", true),
                            description = obj.optString("description")
                        )
                    )
                    added = true
                }
            }
            if (added) {
                writeLocalChannels(localChannels)
                withContext(Dispatchers.Main) {
                    onSubscriptionsUpdated?.invoke()
                }
            }
        }

        // 2. Push local channels to remote
        if (localChannels.isNotEmpty()) {
            val arr = JSONArray()
            val now = System.currentTimeMillis()
            for (c in localChannels) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("channel_id", c.id)
                    put("channel_name", c.name.ifBlank { c.id })
                    put("handle", c.handle)
                    put("avatar_url", c.avatarUrl ?: "")
                    put("banner_url", "")
                    put("description", c.description ?: "")
                    put("subscriber_count", c.subscriberCount)
                    put("notification_enabled", c.notificationEnabled)
                    put("provider_id", "youtube")
                    put("created_at", now)
                    put("updated_at", now)
                }
                arr.put(obj)
            }
            val upRes = getClient().upsert("user_subscriptions", arr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: user_subscriptions, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 10, 11, 12. SAVED EXTERNAL, TELEGRAM & MEGA LINKS SYNC
    // =========================================================================

    private suspend fun syncSavedLinks(token: String, userId: String, syncErrors: MutableList<String>) {
        val dao = getDb().savedLinkDao()
        val localList = dao.getAllSavedLinksList()

        // 1. Download cloud saved links
        val remoteRes = getClient().select("saved_links", "user_id=eq.$userId&select=*&order=created_at.desc&limit=200", token)
        if (remoteRes.isFailure) {
            val err = "[table: saved_links, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localUrls = localList.associateBy { it.url }
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val url = obj.optString("url")
                if (url.isNotBlank() && !localUrls.containsKey(url)) {
                    val id = obj.optString("id").ifBlank { UUID.nameUUIDFromBytes("$userId:$url".toByteArray()).toString() }
                    dao.insertSavedLink(
                        SavedLinkEntity(
                            id = id,
                            url = url,
                            title = obj.optString("title"),
                            provider = obj.optString("provider"),
                            linkType = obj.optString("link_type", "external"),
                            thumbnailUrl = obj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                            metadataJson = obj.opt("metadata_json")?.toString() ?: "{}",
                            createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updated_at", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        // 2. Upload local saved links
        val currentLocal = dao.getAllSavedLinksList()
        if (currentLocal.isNotEmpty()) {
            val arr = JSONArray()
            val now = System.currentTimeMillis()
            for (link in currentLocal.take(200)) {
                val detId = UUID.nameUUIDFromBytes("$userId:${link.url}".toByteArray()).toString()
                val obj = JSONObject().apply {
                    put("id", detId)
                    put("user_id", userId)
                    put("url", link.url)
                    put("title", link.title)
                    put("provider", link.provider)
                    put("link_type", link.linkType.ifBlank { "external" })
                    put("thumbnail_url", link.thumbnailUrl ?: "")
                    put("metadata_json", try { JSONObject(link.metadataJson) } catch (_: Exception) { JSONObject() })
                    put("created_at", if (link.createdAt > 0L) link.createdAt else now)
                    put("updated_at", if (link.updatedAt > 0L) link.updatedAt else now)
                }
                arr.put(obj)
            }
            val upRes = getClient().upsert("saved_links", arr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: saved_links, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 13 & 14. BUNKR ALBUMS & FILES SYNC
    // =========================================================================

    private suspend fun syncBunkr(token: String, userId: String, syncErrors: MutableList<String>) {
        val bunkrDao = getDb().bunkrDao()
        val localAlbums = bunkrDao.getAllAlbumsList()
        val localFiles = bunkrDao.getAllFilesList()

        // 1. Download remote albums
        val remoteAlbums = getClient().select("bunkr_albums", "user_id=eq.$userId&select=*", token)
        if (remoteAlbums.isFailure) {
            val err = "[table: bunkr_albums, op: SELECT] ${remoteAlbums.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val arr = JSONArray(remoteAlbums.getOrThrow())
            val localIds = localAlbums.associateBy { it.albumId }
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val aId = obj.optString("album_id")
                if (aId.isNotBlank() && !localIds.containsKey(aId)) {
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

        // 2. Download remote files
        val remoteFiles = getClient().select("bunkr_files", "user_id=eq.$userId&select=*&limit=300", token)
        if (remoteFiles.isFailure) {
            val err = "[table: bunkr_files, op: SELECT] ${remoteFiles.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val arr = JSONArray(remoteFiles.getOrThrow())
            val localFileIds = localFiles.associateBy { it.fileId }
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fId = obj.optString("file_id")
                if (fId.isNotBlank() && !localFileIds.containsKey(fId)) {
                    bunkrDao.insertFile(
                        BunkrFileEntity(
                            fileId = fId,
                            albumId = obj.optString("album_id"),
                            title = obj.optString("title"),
                            sourceUrl = obj.optString("source_url"),
                            thumbnailUrl = obj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                            mediaType = obj.optString("media_type", "video/mp4"),
                            duration = obj.optString("duration"),
                            resolution = obj.optString("resolution", "HD"),
                            fileSize = obj.optString("file_size"),
                            orderIndex = obj.optInt("order_index", 0),
                            lastUpdated = obj.optLong("last_updated", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        // 3. Upload local albums
        if (localAlbums.isNotEmpty()) {
            val albumArr = JSONArray()
            val now = System.currentTimeMillis()
            for (a in localAlbums) {
                val obj = JSONObject().apply {
                    put("user_id", userId)
                    put("album_id", a.albumId)
                    put("title", a.title)
                    put("source_url", a.sourceUrl)
                    put("is_enabled", a.isEnabled)
                    put("last_scan_time", a.lastScanTime)
                    put("item_count", a.itemCount)
                    put("created_at", if (a.createdAt > 0L) a.createdAt else now)
                }
                albumArr.put(obj)
            }
            val upRes = getClient().upsert("bunkr_albums", albumArr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: bunkr_albums, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }

        // 4. Upload local files
        if (localFiles.isNotEmpty()) {
            val fileArr = JSONArray()
            val now = System.currentTimeMillis()
            for (f in localFiles.take(300)) {
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
                    put("last_updated", if (f.lastUpdated > 0L) f.lastUpdated else now)
                }
                fileArr.put(obj)
            }
            val upRes = getClient().upsert("bunkr_files", fileArr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: bunkr_files, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 15 & 16. CLOUD SOCIAL SOURCES & MEDIA SYNC
    // =========================================================================

    private suspend fun syncCloudSocial(token: String, userId: String, syncErrors: MutableList<String>) {
        val csDao = getDb().cloudSocialDao()
        val localSources = csDao.getAllSourcesList()
        val localMedia = csDao.getAllMediaList()

        // 1. Download remote sources
        val remoteSources = getClient().select("cloud_social_sources", "user_id=eq.$userId&select=*", token)
        if (remoteSources.isFailure) {
            val err = "[table: cloud_social_sources, op: SELECT] ${remoteSources.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val arr = JSONArray(remoteSources.getOrThrow())
            val localSourceIds = localSources.associateBy { it.id }
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sId = obj.optString("source_id")
                if (sId.isNotBlank() && !localSourceIds.containsKey(sId)) {
                    csDao.insertSource(
                        CloudSocialSourceEntity(
                            id = sId,
                            type = obj.optString("type", "TELEGRAM"),
                            name = obj.optString("name"),
                            sourceUrl = obj.optString("source_url"),
                            enabled = obj.optBoolean("enabled", true),
                            lastSyncTimestamp = obj.optLong("last_sync_timestamp", 0L),
                            itemCount = obj.optInt("item_count", 0),
                            newItemCount = obj.optInt("new_item_count", 0),
                            extraConfigJson = obj.optString("extra_config_json", "")
                        )
                    )
                }
            }
        }

        // 2. Download remote media
        val remoteMedia = getClient().select("cloud_social_media", "user_id=eq.$userId&select=*&limit=300", token)
        if (remoteMedia.isFailure) {
            val err = "[table: cloud_social_media, op: SELECT] ${remoteMedia.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val arr = JSONArray(remoteMedia.getOrThrow())
            val localMediaIds = localMedia.associateBy { it.id }
            val toInsert = mutableListOf<CloudSocialMediaEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val mId = obj.optString("media_id")
                if (mId.isNotBlank() && !localMediaIds.containsKey(mId)) {
                    toInsert.add(
                        CloudSocialMediaEntity(
                            id = mId,
                            sourceId = obj.optString("source_id"),
                            type = obj.optString("type", "TELEGRAM"),
                            remoteId = obj.optString("remote_id"),
                            parentId = obj.optString("parent_id").takeIf { it.isNotBlank() },
                            title = obj.optString("title"),
                            caption = obj.optString("caption").takeIf { it.isNotBlank() },
                            sourceUrl = obj.optString("source_url"),
                            directStreamUrl = obj.optString("direct_stream_url").takeIf { it.isNotBlank() },
                            thumbnailUrl = obj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                            mimeType = obj.optString("mime_type", "video/mp4"),
                            fileSize = obj.optLong("file_size", 0L),
                            formattedSize = obj.optString("formatted_size"),
                            durationMs = obj.optLong("duration_ms", 0L),
                            mediaCategory = obj.optString("media_category", "video"),
                            dateTimestamp = obj.optLong("date_timestamp", System.currentTimeMillis()),
                            resolution = obj.optString("resolution", "HD"),
                            headersJson = obj.optString("headers_json", "{}")
                        )
                    )
                }
            }
            if (toInsert.isNotEmpty()) {
                csDao.insertMediaBatch(toInsert)
            }
        }

        // 3. Upload local sources
        if (localSources.isNotEmpty()) {
            val sourceArr = JSONArray()
            for (s in localSources) {
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
            val upRes = getClient().upsert("cloud_social_sources", sourceArr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: cloud_social_sources, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }

        // 4. Upload local media
        if (localMedia.isNotEmpty()) {
            val mediaArr = JSONArray()
            val now = System.currentTimeMillis()
            for (m in localMedia.take(300)) {
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
                    put("date_timestamp", if (m.dateTimestamp > 0L) m.dateTimestamp else now)
                    put("resolution", m.resolution)
                    put("headers_json", m.headersJson)
                }
                mediaArr.put(obj)
            }
            val upRes = getClient().upsert("cloud_social_media", mediaArr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: cloud_social_media, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 17. OFFLINE DOWNLOADS METADATA SYNC
    // =========================================================================

    private suspend fun syncOfflineDownloadsMetadata(token: String, userId: String, syncErrors: MutableList<String>) {
        val dao = getDb().userDataDao()
        val localList = dao.getAllOfflineDownloadsList()

        // 1. Download remote metadata
        val remoteRes = getClient().select("offline_downloads_metadata", "user_id=eq.$userId&select=*", token)
        if (remoteRes.isFailure) {
            val err = "[table: offline_downloads_metadata, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            val localIds = localList.associateBy { it.videoId }
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val vId = obj.optString("video_id")
                if (vId.isNotBlank() && !localIds.containsKey(vId)) {
                    // Record metadata for cloud-restored download record
                    dao.insertOrUpdateDownload(
                        OfflineDownloadEntity(
                            videoId = vId,
                            title = obj.optString("title", vId),
                            channelName = obj.optString("channel_name"),
                            thumbnailUrl = obj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                            localFilePath = "", // file content remains on cloud/original host
                            qualityLabel = obj.optString("quality_label", "720p"),
                            totalBytes = obj.optLong("total_bytes", 0L),
                            downloadedBytes = 0L,
                            status = obj.optString("status", "COMPLETED"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        // 2. Upload local metadata
        if (localList.isNotEmpty()) {
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
            val upRes = getClient().upsert("offline_downloads_metadata", arr.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: offline_downloads_metadata, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 18. APP PREFERENCES SYNC
    // =========================================================================

    private suspend fun syncAppPreferences(token: String, userId: String, syncErrors: MutableList<String>) {
        val ctx = appContext ?: return
        val settingsPrefs = ctx.getSharedPreferences("butterfly_settings", Context.MODE_PRIVATE)

        // 1. Download cloud preferences
        val remoteRes = getClient().select("app_preferences", "user_id=eq.$userId&select=*", token)
        if (remoteRes.isFailure) {
            val err = "[table: app_preferences, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            syncErrors.add(err)
        } else {
            val jsonArr = JSONArray(remoteRes.getOrThrow())
            if (jsonArr.length() > 0) {
                val obj = jsonArr.getJSONObject(0)
                val prefJson = obj.optJSONObject("preferences_json")
                if (prefJson != null) {
                    val editor = settingsPrefs.edit()
                    for (k in prefJson.keys()) {
                        // Do not overwrite today's date if already updated
                        if (k == "last_app_open_date") continue
                        when (val v = prefJson.get(k)) {
                            is Boolean -> editor.putBoolean(k, v)
                            is Int -> editor.putInt(k, v)
                            is Long -> editor.putLong(k, v)
                            is Double -> editor.putFloat(k, v.toFloat())
                            is String -> editor.putString(k, v)
                        }
                    }
                    editor.apply()
                }
            }
        }

        // 2. Upload local settings
        val allSettings = settingsPrefs.all
        if (allSettings.isNotEmpty()) {
            val settingsObj = JSONObject().apply {
                put("user_id", userId)
                val jsonMap = JSONObject()
                for ((k, v) in allSettings) {
                    jsonMap.put(k, v)
                }
                put("preferences_json", jsonMap)
            }
            val upRes = getClient().upsert("app_preferences", settingsObj.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: app_preferences, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        }
    }

    // =========================================================================
    // 19 & 20. BEHAVIOR SIGNALS & USER PREFERENCE PROFILE SYNC
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
        val signalId = UUID.randomUUID().toString()
        val user = SupabaseAuthManager.currentUser.value
        val signalObj = JSONObject().apply {
            put("id", signalId)
            if (user != null && user.id.isNotBlank()) put("user_id", user.id)
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

        enqueueSync("BEHAVIOR_SIGNAL", signalId, "UPSERT", signalObj.toString())
    }

    private suspend fun syncBehaviorSignals(token: String, userId: String, syncErrors: MutableList<String>) {
        val ctx = appContext ?: return
        try {
            // 1. Download cloud user preference profile on reinstall/login to avoid wiping cloud data
            val remoteRes = getClient().select("user_preference_profile", "user_id=eq.$userId&select=*", token)
            if (remoteRes.isFailure) {
                val err = "[table: user_preference_profile, op: SELECT] ${remoteRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            } else {
                val jsonArr = JSONArray(remoteRes.getOrThrow())
                if (jsonArr.length() > 0) {
                    val cloudProf = jsonArr.getJSONObject(0)
                    val cloudDislikesJson = cloudProf.optJSONArray("disliked_videos")?.toString() ?: "[]"
                    val cloudDislikedChans = mutableListOf<String>()
                    val cChans = cloudProf.optJSONArray("disliked_channels")
                    if (cChans != null) {
                        for (i in 0 until cChans.length()) cloudDislikedChans.add(cChans.getString(i))
                    }
                    val cloudDislikedKw = mutableListOf<String>()
                    val cKw = cloudProf.optJSONArray("disliked_keywords")
                    if (cKw != null) {
                        for (i in 0 until cKw.length()) cloudDislikedKw.add(cKw.getString(i))
                    }
                    val cloudFavChans = mutableListOf<String>()
                    val cFav = cloudProf.optJSONArray("favorite_channels")
                    if (cFav != null) {
                        for (i in 0 until cFav.length()) cloudFavChans.add(cFav.getString(i))
                    }
                    UserActivityMemory.restorePreferencesFromCloud(
                        context = ctx,
                        dislikedVideosJsonStr = cloudDislikesJson,
                        dislikedChannelsList = cloudDislikedChans,
                        dislikedKeywordsList = cloudDislikedKw,
                        favoriteChannelsList = cloudFavChans
                    )
                }
            }

            // 2. Upload merged local + cloud preference profile
            val dislikedVideosJson = UserActivityMemory.exportDislikedVideosJson(ctx)
            val dislikedChannelsSet = UserActivityMemory.getDislikedChannels()
            val dislikedKeywordsSet = UserActivityMemory.getDislikedKeywords()
            val dislikedCategories = UserActivityMemory.getDislikedCategories()
            val favoriteChannels = UserActivityMemory.getFavoriteChannels()

            val profileObj = JSONObject().apply {
                put("user_id", userId)
                put("disliked_videos", JSONArray(dislikedVideosJson))
                put("disliked_channels", JSONArray(dislikedChannelsSet))
                put("disliked_keywords", JSONArray(dislikedKeywordsSet))
                val catObj = JSONObject()
                for ((k, v) in dislikedCategories) catObj.put(k, v)
                put("disliked_categories", catObj)
                put("favorite_channels", JSONArray(favoriteChannels))
                put("hourly_categories", JSONObject())
                put("hourly_channels", JSONObject())
                put("high_completion_channels", JSONArray())
                put("early_bounce_channels", JSONArray())
                put("taste_vector_json", JSONObject())
            }
            val upRes = getClient().upsert("user_preference_profile", profileObj.toString(), token)
            if (upRes.isFailure) {
                val err = "[table: user_preference_profile, op: UPSERT] ${upRes.exceptionOrNull()?.message}"
                Log.e(TAG, err)
                syncErrors.add(err)
            }
        } catch (e: Exception) {
            val err = "[table: user_preference_profile] ${e.message}"
            Log.w(TAG, err)
            syncErrors.add(err)
        }
    }
}
