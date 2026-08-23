package com.example.util

import android.content.Context
import android.util.Log
import com.example.db.BookmarkEntity
import com.example.db.LikedVideoEntity
import com.example.db.UserPlaylistEntity
import com.example.db.WatchHistoryEntity
import com.example.model.UserPlaylist
import com.example.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject

object UserDataBackupManager {

    private const val TAG = "UserDataBackupManager"
    private const val CURRENT_VERSION = 1

    data class BackupData(
        val version: Int = CURRENT_VERSION,
        val exportedAt: Long = System.currentTimeMillis(),
        val watchHistory: List<WatchHistoryEntity> = emptyList(),
        val bookmarks: List<BookmarkEntity> = emptyList(),
        val likedVideos: List<LikedVideoEntity> = emptyList(),
        val dislikedVideoIds: List<String> = emptyList(),
        val playlists: List<UserPlaylistEntity> = emptyList(),
        val hiddenVideoIds: List<String> = emptyList(),
        val notInterestedVideoIds: List<String> = emptyList(),
        val notInterestedChannels: List<String> = emptyList(),
        val recentSearches: List<String> = emptyList(),
        val watchProgressMap: Map<String, Float> = emptyMap()
    )

    data class ImportSummary(
        val historyCount: Int,
        val bookmarkCount: Int,
        val likedCount: Int,
        val playlistCount: Int,
        val blockedChannelsCount: Int,
        val hiddenCount: Int
    )

    /**
     * Serializes all user watch history, preferences, bookmarks, playlists, and blocklists into formatted JSON.
     */
    fun exportToJson(data: BackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("exportedAt", data.exportedAt)

        // 1. Watch History
        val historyArray = JSONArray()
        for (item in data.watchHistory) {
            val obj = JSONObject()
            obj.put("videoId", item.videoId)
            obj.put("title", item.title)
            obj.put("channelName", item.channelName)
            obj.put("thumbnailUrl", item.thumbnailUrl ?: "")
            obj.put("providerId", item.providerId ?: "youtube")
            obj.put("timestamp", item.timestamp)
            obj.put("progressFraction", item.progressFraction)
            historyArray.put(obj)
        }
        root.put("watchHistory", historyArray)

        // 2. Watch Later / Bookmarks
        val bookmarkArray = JSONArray()
        for (item in data.bookmarks) {
            val obj = JSONObject()
            obj.put("videoId", item.videoId)
            obj.put("title", item.title)
            obj.put("channelName", item.channelName)
            obj.put("thumbnailUrl", item.thumbnailUrl ?: "")
            obj.put("providerId", item.providerId ?: "youtube")
            obj.put("timestamp", item.timestamp)
            bookmarkArray.put(obj)
        }
        root.put("bookmarks", bookmarkArray)

        // 3. Liked Videos
        val likedArray = JSONArray()
        for (item in data.likedVideos) {
            val obj = JSONObject()
            obj.put("videoId", item.videoId)
            obj.put("title", item.title)
            obj.put("channelName", item.channelName)
            obj.put("thumbnailUrl", item.thumbnailUrl ?: "")
            obj.put("providerId", item.providerId ?: "youtube")
            obj.put("timestamp", item.timestamp)
            likedArray.put(obj)
        }
        root.put("likedVideos", likedArray)

        // 4. Disliked Video IDs
        val dislikedArray = JSONArray()
        for (id in data.dislikedVideoIds) {
            dislikedArray.put(id)
        }
        root.put("dislikedVideoIds", dislikedArray)

        // 5. User Playlists
        val playlistArray = JSONArray()
        for (item in data.playlists) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("createdAt", item.createdAt)
            obj.put("videosJson", item.videosJson)
            playlistArray.put(obj)
        }
        root.put("playlists", playlistArray)

        // 6. Hidden & Not Interested
        val hiddenArray = JSONArray()
        for (id in data.hiddenVideoIds) hiddenArray.put(id)
        root.put("hiddenVideoIds", hiddenArray)

        val notIntArray = JSONArray()
        for (id in data.notInterestedVideoIds) notIntArray.put(id)
        root.put("notInterestedVideoIds", notIntArray)

        val blockedChanArray = JSONArray()
        for (chan in data.notInterestedChannels) blockedChanArray.put(chan)
        root.put("notInterestedChannels", blockedChanArray)

        // 7. Recent Searches
        val searchArray = JSONArray()
        for (s in data.recentSearches) searchArray.put(s)
        root.put("recentSearches", searchArray)

        // 8. Watch Progress Map
        val progressObj = JSONObject()
        for ((vid, prog) in data.watchProgressMap) {
            progressObj.put(vid, prog.toDouble())
        }
        root.put("watchProgressMap", progressObj)

        return root.toString(2)
    }

    /**
     * Parses JSON string back into a structured BackupData object.
     */
    fun importFromJson(jsonString: String): BackupData {
        val root = JSONObject(jsonString)

        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

        // 1. History
        val historyList = mutableListOf<WatchHistoryEntity>()
        val historyArray = root.optJSONArray("watchHistory")
        if (historyArray != null) {
            for (i in 0 until historyArray.length()) {
                val obj = historyArray.optJSONObject(i) ?: continue
                val vid = obj.optString("videoId", "")
                if (vid.isNotBlank()) {
                    historyList.add(
                        WatchHistoryEntity(
                            videoId = vid,
                            title = obj.optString("title", vid),
                            channelName = obj.optString("channelName", ""),
                            thumbnailUrl = obj.optString("thumbnailUrl", "").ifBlank { null },
                            providerId = obj.optString("providerId", "youtube"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            progressFraction = obj.optDouble("progressFraction", 0.5).toFloat()
                        )
                    )
                }
            }
        }

        // 2. Bookmarks
        val bookmarkList = mutableListOf<BookmarkEntity>()
        val bookmarkArray = root.optJSONArray("bookmarks")
        if (bookmarkArray != null) {
            for (i in 0 until bookmarkArray.length()) {
                val obj = bookmarkArray.optJSONObject(i) ?: continue
                val vid = obj.optString("videoId", "")
                if (vid.isNotBlank()) {
                    bookmarkList.add(
                        BookmarkEntity(
                            videoId = vid,
                            title = obj.optString("title", vid),
                            channelName = obj.optString("channelName", ""),
                            thumbnailUrl = obj.optString("thumbnailUrl", "").ifBlank { null },
                            providerId = obj.optString("providerId", "youtube"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        // 3. Liked Videos
        val likedList = mutableListOf<LikedVideoEntity>()
        val likedArray = root.optJSONArray("likedVideos")
        if (likedArray != null) {
            for (i in 0 until likedArray.length()) {
                val obj = likedArray.optJSONObject(i) ?: continue
                val vid = obj.optString("videoId", "")
                if (vid.isNotBlank()) {
                    likedList.add(
                        LikedVideoEntity(
                            videoId = vid,
                            title = obj.optString("title", vid),
                            channelName = obj.optString("channelName", ""),
                            thumbnailUrl = obj.optString("thumbnailUrl", "").ifBlank { null },
                            providerId = obj.optString("providerId", "youtube"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        // 4. Disliked Video IDs
        val dislikedList = mutableListOf<String>()
        val dislikedArray = root.optJSONArray("dislikedVideoIds")
        if (dislikedArray != null) {
            for (i in 0 until dislikedArray.length()) {
                val s = dislikedArray.optString(i, "")
                if (s.isNotBlank()) dislikedList.add(s)
            }
        }

        // 5. Playlists
        val playlistList = mutableListOf<UserPlaylistEntity>()
        val playlistArray = root.optJSONArray("playlists")
        if (playlistArray != null) {
            for (i in 0 until playlistArray.length()) {
                val obj = playlistArray.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val title = obj.optString("title", "")
                if (id.isNotBlank() && title.isNotBlank()) {
                    playlistList.add(
                        UserPlaylistEntity(
                            id = id,
                            title = title,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            videosJson = obj.optString("videosJson", "[]")
                        )
                    )
                }
            }
        }

        // 6. Hidden & Not Interested
        val hiddenList = mutableListOf<String>()
        val hiddenArray = root.optJSONArray("hiddenVideoIds")
        if (hiddenArray != null) {
            for (i in 0 until hiddenArray.length()) {
                val s = hiddenArray.optString(i, "")
                if (s.isNotBlank()) hiddenList.add(s)
            }
        }

        val notIntList = mutableListOf<String>()
        val notIntArray = root.optJSONArray("notInterestedVideoIds")
        if (notIntArray != null) {
            for (i in 0 until notIntArray.length()) {
                val s = notIntArray.optString(i, "")
                if (s.isNotBlank()) notIntList.add(s)
            }
        }

        val blockedChanList = mutableListOf<String>()
        val blockedChanArray = root.optJSONArray("notInterestedChannels")
        if (blockedChanArray != null) {
            for (i in 0 until blockedChanArray.length()) {
                val s = blockedChanArray.optString(i, "")
                if (s.isNotBlank()) blockedChanList.add(s)
            }
        }

        // 7. Recent Searches
        val searchList = mutableListOf<String>()
        val searchArray = root.optJSONArray("recentSearches")
        if (searchArray != null) {
            for (i in 0 until searchArray.length()) {
                val s = searchArray.optString(i, "")
                if (s.isNotBlank()) searchList.add(s)
            }
        }

        // 8. Watch Progress
        val progressMap = mutableMapOf<String, Float>()
        val progressObj = root.optJSONObject("watchProgressMap")
        if (progressObj != null) {
            val keys = progressObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val valD = progressObj.optDouble(key, 0.0).toFloat()
                progressMap[key] = valD
            }
        }

        return BackupData(
            version = version,
            exportedAt = exportedAt,
            watchHistory = historyList,
            bookmarks = bookmarkList,
            likedVideos = likedList,
            dislikedVideoIds = dislikedList,
            playlists = playlistList,
            hiddenVideoIds = hiddenList,
            notInterestedVideoIds = notIntList,
            notInterestedChannels = blockedChanList,
            recentSearches = searchList,
            watchProgressMap = progressMap
        )
    }
}
