package com.example.metadata.person

import android.util.Log
import com.example.metadata.JavActor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * GFriends Person Provider (Adapted from gfriends/gfriends).
 * High-definition actress portrait database indexed by Japanese Kanji, Hiragana, and Romaji.
 */
class GFriendsPersonProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : PersonProvider {

    companion object {
        private const val TAG = "GFriendsPersonProvider"
        private const val GFRIENDS_INDEX_URL = "https://raw.githubusercontent.com/gfriends/gfriends/master/Filetree.json"
        private const val GFRIENDS_BASE_RAW = "https://raw.githubusercontent.com/gfriends/gfriends/master/Content/"
        private const val GFRIENDS_JSDELIVR_CDN = "https://cdn.jsdelivr.net/gh/gfriends/gfriends@master/Content/"
    }

    override val id: String = "gfriends"
    override val name: String = "GFriends Database"

    // In-memory cache of actress name -> relative image path
    private val nameToPathCache = ConcurrentHashMap<String, String>()
    private var isIndexLoaded = false

    private suspend fun ensureIndexLoaded() = withContext(Dispatchers.IO) {
        if (isIndexLoaded) return@withContext
        try {
            val request = Request.Builder().url(GFRIENDS_INDEX_URL).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return@withContext
                val root = JSONObject(jsonStr)
                val contentObj = root.optJSONObject("Content")
                if (contentObj != null) {
                    val keys = contentObj.keys()
                    while (keys.hasNext()) {
                        val subFolder = keys.next()
                        val folderObj = contentObj.optJSONObject(subFolder) ?: continue
                        val fileKeys = folderObj.keys()
                        while (fileKeys.hasNext()) {
                            val actressFile = fileKeys.next()
                            val cleanName = actressFile.substringBeforeLast(".")
                            val relPath = "$subFolder/$actressFile"
                            nameToPathCache[cleanName] = relPath
                        }
                    }
                    isIndexLoaded = true
                    Log.i(TAG, "Loaded ${nameToPathCache.size} actress portraits from GFriends index.")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading GFriends index: ${e.message}")
        }
    }

    override suspend fun enrichActor(actor: JavActor): JavActor = withContext(Dispatchers.IO) {
        if (!actor.avatarUrl.isNullOrBlank()) return@withContext actor
        val avatar = getAvatarUrlForName(actor.name)
        if (avatar != null) {
            actor.copy(avatarUrl = avatar)
        } else {
            actor
        }
    }

    override suspend fun getActorDetails(name: String): JavActor? = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val avatar = getAvatarUrlForName(cleanName)
        if (avatar != null) {
            JavActor(
                name = cleanName,
                avatarUrl = avatar
            )
        } else {
            null
        }
    }

    private suspend fun getAvatarUrlForName(name: String): String? {
        ensureIndexLoaded()
        val clean = name.trim()
        val directPath = nameToPathCache[clean]
        if (directPath != null) {
            return "$GFRIENDS_JSDELIVR_CDN$directPath"
        }

        // Fuzzy match (contains / alias)
        for ((k, path) in nameToPathCache) {
            if (k.equals(clean, ignoreCase = true) || k.contains(clean) || clean.contains(k)) {
                return "$GFRIENDS_JSDELIVR_CDN$path"
            }
        }

        // Direct fallback attempt via raw URL
        return "$GFRIENDS_BASE_RAW${java.net.URLEncoder.encode(clean, "UTF-8")}.jpg"
    }
}
