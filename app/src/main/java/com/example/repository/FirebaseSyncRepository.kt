package com.example.repository

import android.content.Context
import android.util.Log
import com.example.db.WatchHistoryEntity
import com.example.model.UserPlaylist
import com.example.model.VideoItem
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class CloudSyncStatus(
    val isAuthenticated: Boolean = false,
    val userDisplayName: String? = null,
    val userEmail: String? = null,
    val userPhotoUrl: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long = 0,
    val syncError: String? = null,
    val customApiKeySet: Boolean = false
)

object FirebaseSyncRepository {

    private const val TAG = "FirebaseSyncRepository"
    private const val FIREBASE_PROJECT_ID = "butterfly-208b2"
    private const val REAL_FIREBASE_API_KEY = "AIzaSyCm-jBkprDMZbM9EkFguaWc_JxO3YfvzCY"
    private const val REAL_APP_ID = "1:561024071996:android:f1c35af1b17e162e94c639"
    private const val PREFS_NAME = "butterfly_firebase_prefs"
    private const val KEY_CUSTOM_API_KEY = "custom_firebase_api_key"

    private var appContext: Context? = null
    private var customAuthInstance: FirebaseAuth? = null
    private var customFirestoreInstance: FirebaseFirestore? = null

    private val auth: FirebaseAuth
        get() = customAuthInstance ?: FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = customFirestoreInstance ?: FirebaseFirestore.getInstance()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus())
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedApiKey = prefs?.getString(KEY_CUSTOM_API_KEY, REAL_FIREBASE_API_KEY) ?: REAL_FIREBASE_API_KEY
        setupCustomFirebaseApp(savedApiKey)
    }

    fun setCustomApiKey(apiKey: String): Boolean {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return false
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_CUSTOM_API_KEY, trimmed)
            ?.apply()
        return setupCustomFirebaseApp(trimmed)
    }

    private fun setupCustomFirebaseApp(apiKey: String): Boolean {
        val ctx = appContext ?: return false
        return try {
            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(REAL_APP_ID)
                .setProjectId(FIREBASE_PROJECT_ID)
                .setStorageBucket("$FIREBASE_PROJECT_ID.firebasestorage.app")
                .build()

            val appName = "ButterflyCustomFirebase"
            val app = try {
                FirebaseApp.getInstance(appName)
            } catch (e: Exception) {
                FirebaseApp.initializeApp(ctx, options, appName)
            }

            customAuthInstance = FirebaseAuth.getInstance(app)
            customFirestoreInstance = FirebaseFirestore.getInstance(app)
            _syncStatus.value = _syncStatus.value.copy(customApiKeySet = true, syncError = null)
            checkAndInitUser()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up custom Firebase API key", e)
            _syncStatus.value = _syncStatus.value.copy(syncError = "Invalid API Key format: ${e.localizedMessage}")
            false
        }
    }

    private fun checkAndInitUser() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _syncStatus.value = _syncStatus.value.copy(
                    isAuthenticated = true,
                    userDisplayName = user.displayName ?: if (user.isAnonymous) "Guest User" else (user.email?.substringBefore("@") ?: "User"),
                    userEmail = user.email ?: if (user.isAnonymous) "butterfly.guest@cloud.sync" else null,
                    userPhotoUrl = user.photoUrl?.toString(),
                    syncError = null
                )
            } else {
                auth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        val anonUser = result.user
                        _syncStatus.value = _syncStatus.value.copy(
                            isAuthenticated = true,
                            userDisplayName = "Guest Account (${anonUser?.uid?.take(5)})",
                            userEmail = "guest@butterfly.cloud",
                            syncError = null
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Anonymous sign-in failed", e)
                        val errorMsg = parseFirebaseError(e)
                        _syncStatus.value = _syncStatus.value.copy(
                            isAuthenticated = false,
                            syncError = errorMsg
                        )
                    }
            }
        }
    }

    fun signInWithEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val u = result.user
                _syncStatus.value = _syncStatus.value.copy(
                    isAuthenticated = true,
                    userDisplayName = u?.displayName ?: u?.email?.substringBefore("@") ?: "User",
                    userEmail = u?.email,
                    syncError = null
                )
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                val friendlyError = parseFirebaseError(e)
                onResult(false, friendlyError)
            }
    }

    fun registerWithEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val u = result.user
                _syncStatus.value = _syncStatus.value.copy(
                    isAuthenticated = true,
                    userDisplayName = u?.email?.substringBefore("@") ?: "User",
                    userEmail = u?.email,
                    syncError = null
                )
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                val friendlyError = parseFirebaseError(e)
                onResult(false, friendlyError)
            }
    }

    private fun parseFirebaseError(e: Exception): String {
        val msg = e.localizedMessage ?: "Authentication failed"
        return if (msg.contains("API key not valid", ignoreCase = true) || msg.contains("API_KEY_INVALID", ignoreCase = true)) {
            "API Key Invalid: The default google-services.json uses a placeholder API key. Please enter your real Firebase Web API Key (AIzaSy...) from Firebase Console -> Project Settings."
        } else {
            msg
        }
    }

    fun signOutUser() {
        auth.signOut()
        checkAndInitUser()
    }

    fun getCurrentUserId(): String? {
        return try { auth.currentUser?.uid } catch (t: Throwable) { null }
    }

    /**
     * Converts any raw string/URL/path into a valid, safe Firestore document ID without slashes or illegal characters.
     */
    fun safeDocId(rawId: String?): String {
        if (rawId.isNullOrBlank()) return "id_${System.currentTimeMillis()}"
        val sanitized = rawId
            .replace("/", "_")
            .replace("\\", "_")
            .replace(".", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace(":", "_")
            .replace("?", "_")
            .replace("&", "_")
            .replace("=", "_")
            .replace(" ", "_")
        return if (sanitized.length > 180) {
            val hash = Math.abs(rawId.hashCode()).toString()
            sanitized.take(130) + "_" + hash
        } else sanitized
    }

    /**
     * Sync local watch history entry to Firestore cloud document securely.
     */
    suspend fun pushWatchHistoryToCloud(entry: WatchHistoryEntity) = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext
        try {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true)
            val docId = safeDocId(entry.videoId)

            val docData = hashMapOf<String, Any>(
                "videoId" to (entry.videoId ?: ""),
                "title" to (entry.title ?: ""),
                "channelName" to (entry.channelName ?: ""),
                "thumbnailUrl" to (entry.thumbnailUrl ?: ""),
                "progressFraction" to entry.progressFraction,
                "timestamp" to entry.timestamp,
                "providerId" to (entry.providerId ?: "youtube")
            )

            firestore.collection("users")
                .document(safeDocId(uid))
                .collection("watch_history")
                .document(docId)
                .set(docData, SetOptions.merge())
                .await()

            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis(),
                syncError = null
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error pushing watch history to Firestore", t)
            _syncStatus.value = _syncStatus.value.copy(isSyncing = false, syncError = t.localizedMessage)
        }
    }

    /**
     * Fetch cloud watch history from Firestore (e.g. after fresh install or login on new device).
     */
    suspend fun fetchCloudWatchHistory(): List<WatchHistoryEntity> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext emptyList()
        try {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true)
            val snapshot = firestore.collection("users")
                .document(safeDocId(uid))
                .collection("watch_history")
                .get()
                .await()

            val cloudHistory = snapshot.documents.mapNotNull { doc ->
                val videoId = doc.getString("videoId") ?: doc.id
                val title = doc.getString("title") ?: ""
                val channelName = doc.getString("channelName") ?: ""
                val thumbnailUrl = doc.getString("thumbnailUrl")?.takeIf { it.isNotBlank() }
                val progressFraction = doc.getDouble("progressFraction")?.toFloat() ?: 0f
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                val providerId = doc.getString("providerId") ?: "youtube"

                if (title.isNotBlank() || videoId.isNotBlank()) {
                    WatchHistoryEntity(
                        videoId = videoId,
                        title = title.ifBlank { videoId },
                        channelName = channelName,
                        thumbnailUrl = thumbnailUrl,
                        progressFraction = progressFraction,
                        timestamp = timestamp,
                        providerId = providerId
                    )
                } else null
            }

            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis(),
                syncError = null
            )
            cloudHistory
        } catch (t: Throwable) {
            Log.e(TAG, "Error fetching cloud watch history", t)
            _syncStatus.value = _syncStatus.value.copy(isSyncing = false, syncError = t.localizedMessage)
            emptyList()
        }
    }

    /**
     * Sync liked / disliked video IDs to cloud document.
     */
    suspend fun pushLikesToCloud(likedIds: Set<String>, dislikedIds: Set<String>) = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext
        try {
            val data = hashMapOf<String, Any>(
                "likedVideoIds" to likedIds.map { safeDocId(it) },
                "dislikedVideoIds" to dislikedIds.map { safeDocId(it) },
                "lastUpdated" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(safeDocId(uid))
                .collection("preferences")
                .document("feedback")
                .set(data, SetOptions.merge())
                .await()
        } catch (t: Throwable) {
            Log.e(TAG, "Error syncing likes to cloud", t)
        }
    }

    /**
     * Sync Watch Later list to cloud document.
     */
    suspend fun pushWatchLaterToCloud(watchLaterList: List<VideoItem>) = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext
        try {
            val serializedList = watchLaterList.map { video ->
                hashMapOf<String, Any>(
                    "id" to (video.id ?: ""),
                    "title" to (video.title ?: ""),
                    "uploaderName" to (video.uploaderName ?: ""),
                    "thumbnailUrl" to (video.thumbnailUrl ?: ""),
                    "providerId" to (video.providerId ?: ""),
                    "durationSeconds" to (video.durationSeconds ?: 0L),
                    "viewCount" to (video.viewCount ?: 0L)
                )
            }

            firestore.collection("users")
                .document(safeDocId(uid))
                .collection("playlists")
                .document("watch_later")
                .set(hashMapOf<String, Any>("items" to serializedList, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                .await()
        } catch (t: Throwable) {
            Log.e(TAG, "Error syncing Watch Later to cloud", t)
        }
    }

    /**
     * Sync Not Interested channels and video IDs to cloud document.
     */
    suspend fun pushNotInterestedToCloud(notInterestedChannels: Set<String>, notInterestedVideoIds: Set<String>) = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext
        try {
            val data = hashMapOf<String, Any>(
                "notInterestedChannels" to notInterestedChannels.toList(),
                "notInterestedVideoIds" to notInterestedVideoIds.map { safeDocId(it) },
                "lastUpdated" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(safeDocId(uid))
                .collection("preferences")
                .document("suppressions")
                .set(data, SetOptions.merge())
                .await()
        } catch (t: Throwable) {
            Log.e(TAG, "Error syncing Not Interested preferences to cloud", t)
        }
    }

    /**
     * Fetch Not Interested suppressions from cloud document.
     */
    suspend fun fetchNotInterestedFromCloud(): Pair<Set<String>, Set<String>> = withContext(Dispatchers.IO) {
        val uid = getCurrentUserId() ?: return@withContext Pair(emptySet(), emptySet())
        try {
            val doc = firestore.collection("users")
                .document(safeDocId(uid))
                .collection("preferences")
                .document("suppressions")
                .get()
                .await()

            if (doc.exists()) {
                val channels = (doc.get("notInterestedChannels") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                val videoIds = (doc.get("notInterestedVideoIds") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                Pair(channels, videoIds)
            } else Pair(emptySet(), emptySet())
        } catch (t: Throwable) {
            Log.e(TAG, "Error fetching Not Interested preferences from cloud", t)
            Pair(emptySet(), emptySet())
        }
    }

    /**
     * Trigger manual full backup sync to Butterfly Firebase project (`butterfly-208b2`).
     */
    suspend fun triggerFullSync(
        watchHistory: List<WatchHistoryEntity>,
        likedIds: Set<String>,
        dislikedIds: Set<String>,
        watchLaterList: List<VideoItem>
    ) = withContext(Dispatchers.IO) {
        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, syncError = null)
        try {
            pushLikesToCloud(likedIds, dislikedIds)
            pushWatchLaterToCloud(watchLaterList)
            watchHistory.take(50).forEach { pushWatchHistoryToCloud(it) }

            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis(),
                syncError = null
            )
        } catch (t: Throwable) {
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                syncError = "Sync failed: ${t.localizedMessage}"
            )
        }
    }
}
