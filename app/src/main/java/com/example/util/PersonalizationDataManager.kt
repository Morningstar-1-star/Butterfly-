package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.db.AppDatabase
import com.example.db.BookmarkEntity
import com.example.db.LikedVideoEntity
import com.example.db.UserPlaylistEntity
import com.example.db.WatchHistoryEntity
import com.example.model.UserPlaylist
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles exporting and importing user personalization data:
 * - Likes & Dislikes
 * - Not Interested videos & Blocked channels
 * - Watch History & Watch Progress percentages
 * - Search Queries & History
 * - Bookmarks (Watch Later) & Custom Playlists
 * - Tag & Playback Preferences
 * - Recommendation & Taste Signals
 */
object PersonalizationDataManager {

    private const val TAG = "PersonalizationData"
    private const val SCHEMA_VERSION = 2

    data class ImportSummary(
        val success: Boolean,
        val likesCount: Int = 0,
        val dislikesCount: Int = 0,
        val watchHistoryCount: Int = 0,
        val notInterestedCount: Int = 0,
        val blockedChannelsCount: Int = 0,
        val searchHistoryCount: Int = 0,
        val bookmarksCount: Int = 0,
        val playlistsCount: Int = 0,
        val errorMessage: String? = null
    )

    data class PersonalizationStats(
        val likesCount: Int,
        val dislikesCount: Int,
        val watchHistoryCount: Int,
        val notInterestedCount: Int,
        val blockedChannelsCount: Int,
        val searchHistoryCount: Int,
        val bookmarksCount: Int,
        val playlistsCount: Int
    )

    /**
     * Get current count stats for UI summary.
     */
    fun getStats(
        watchHistory: List<VideoItem>,
        likedVideoIds: Set<String>,
        dislikedVideoIds: Set<String>,
        notInterestedVideoIds: Set<String>,
        notInterestedChannels: Set<String>,
        recentSearches: List<String>,
        watchLaterList: List<VideoItem>,
        userPlaylists: List<UserPlaylist>
    ): PersonalizationStats {
        return PersonalizationStats(
            likesCount = likedVideoIds.size,
            dislikesCount = dislikedVideoIds.size,
            watchHistoryCount = watchHistory.size,
            notInterestedCount = notInterestedVideoIds.size,
            blockedChannelsCount = notInterestedChannels.size,
            searchHistoryCount = recentSearches.size,
            bookmarksCount = watchLaterList.size,
            playlistsCount = userPlaylists.size
        )
    }

    /**
     * Export all personalization data into a structured JSON string.
     */
    suspend fun exportToJson(
        context: Context,
        viewModel: MainViewModel
    ): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("appName", "Butterfly")
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val db = AppDatabase.getInstance(context)
        val dao = db.userDataDao()

        // 1. Likes
        val likedEntities = viewModel.likedVideoIds.value
        val likesArray = JSONArray()
        likedEntities.forEach { vid ->
            val match = (viewModel.searchResults.value + viewModel.trendingVideos.value + viewModel.watchHistory.value).firstOrNull { it.id == vid }
            val obj = JSONObject()
            obj.put("videoId", vid)
            obj.put("title", match?.title ?: vid)
            obj.put("channelName", match?.uploaderName ?: "")
            obj.put("thumbnailUrl", match?.thumbnailUrl ?: "")
            obj.put("providerId", match?.providerId ?: "")
            likesArray.put(obj)
        }
        root.put("likes", likesArray)

        // 2. Dislikes
        val dislikesArray = JSONArray()
        viewModel.dislikedVideoIds.value.forEach { dislikesArray.put(it) }
        root.put("dislikes", dislikesArray)

        // 3. Not Interested
        val notInterestedObj = JSONObject()
        val notIntVids = JSONArray()
        val hiddenVids = NotInterestedManager.getHiddenVideoIds(context)
        hiddenVids.forEach { notIntVids.put(it) }
        notInterestedObj.put("videoIds", notIntVids)

        val blockedChans = JSONArray()
        val blockedChanSet = NotInterestedManager.getBlockedChannels(context)
        blockedChanSet.forEach { blockedChans.put(it) }
        notInterestedObj.put("blockedChannels", blockedChans)
        root.put("notInterested", notInterestedObj)

        // 4. Watch History with progress
        val historyArray = JSONArray()
        viewModel.watchHistory.value.forEach { video ->
            val obj = JSONObject()
            obj.put("videoId", video.id)
            obj.put("title", video.title)
            obj.put("channelName", video.uploaderName)
            obj.put("thumbnailUrl", video.thumbnailUrl ?: "")
            obj.put("providerId", video.providerId ?: "")
            obj.put("progressFraction", viewModel.watchProgressMap.value[video.id] ?: 0.5f)
            historyArray.put(obj)
        }
        root.put("watchHistory", historyArray)

        // 5. Watch Progress Map
        val progressObj = JSONObject()
        viewModel.watchProgressMap.value.forEach { (k, v) ->
            progressObj.put(k, v.toDouble())
        }
        root.put("watchProgressMap", progressObj)

        // 6. Search History
        val searchesArray = JSONArray()
        viewModel.recentSearches.value.forEach { searchesArray.put(it) }
        root.put("searchHistory", searchesArray)

        // 7. Bookmarks (Watch Later)
        val bookmarksArray = JSONArray()
        viewModel.watchLaterList.value.forEach { video ->
            val obj = JSONObject()
            obj.put("videoId", video.id)
            obj.put("title", video.title)
            obj.put("channelName", video.uploaderName)
            obj.put("thumbnailUrl", video.thumbnailUrl ?: "")
            obj.put("providerId", video.providerId ?: "")
            bookmarksArray.put(obj)
        }
        root.put("bookmarks", bookmarksArray)

        // 8. Custom Playlists
        val playlistsArray = JSONArray()
        viewModel.userPlaylists.value.forEach { pl ->
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("title", pl.title)
            val vidsArray = JSONArray()
            pl.videos.forEach { v ->
                val vObj = JSONObject()
                vObj.put("id", v.id)
                vObj.put("title", v.title)
                vObj.put("uploaderName", v.uploaderName)
                vObj.put("thumbnailUrl", v.thumbnailUrl ?: "")
                vObj.put("providerId", v.providerId ?: "")
                vidsArray.put(vObj)
            }
            obj.put("videos", vidsArray)
            playlistsArray.put(obj)
        }
        root.put("playlists", playlistsArray)

        // 9. User Preferences
        val prefsObj = JSONObject()
        prefsObj.put("themeMode", viewModel.themeMode.value.name)
        prefsObj.put("accentColor", viewModel.accentColor.value.name)
        prefsObj.put("adultContentEnabled", viewModel.adultContentEnabled.value)
        val tagPrefs = VideoTagPreferences.getInstance(context)
        prefsObj.put("hideAllTags", tagPrefs.hideAllTags.value)
        val hiddenTagsArr = JSONArray()
        tagPrefs.hiddenTags.value.forEach { hiddenTagsArr.put(it) }
        prefsObj.put("hiddenTags", hiddenTagsArr)
        val playbackPrefs = PlaybackPreferences.getInstance(context)
        prefsObj.put("defaultSpeed", playbackPrefs.defaultSpeed.value.toDouble())
        prefsObj.put("forceCustomSpeed", playbackPrefs.forceCustomSpeed.value)
        root.put("preferences", prefsObj)

        // 10. Recommendation Signals
        val signalsObj = JSONObject()
        signalsObj.put("preferredLanguages", JSONArray(listOf("en", "hi", "ja")))
        signalsObj.put("circadianSlot", com.example.engine.SmartRecommendationEngine.getCurrentTimeSlot())
        root.put("recommendationSignals", signalsObj)

        return@withContext root.toString(2)
    }

    /**
     * Import and apply personalization JSON into database, preferences, and ViewModel.
     */
    suspend fun importFromJson(
        context: Context,
        viewModel: MainViewModel,
        jsonString: String
    ): ImportSummary = withContext(Dispatchers.IO) {
        if (jsonString.isBlank()) {
            return@withContext ImportSummary(success = false, errorMessage = "Empty JSON payload")
        }

        try {
            val root = JSONObject(jsonString)
            val db = AppDatabase.getInstance(context)
            val dao = db.userDataDao()

            var likesCount = 0
            var dislikesCount = 0
            var historyCount = 0
            var notInterestedCount = 0
            var blockedChannelsCount = 0
            var searchHistoryCount = 0
            var bookmarksCount = 0
            var playlistsCount = 0

            // 1. Import Likes
            val likesArray = root.optJSONArray("likes")
            if (likesArray != null) {
                val importedLikes = mutableSetOf<String>()
                for (i in 0 until likesArray.length()) {
                    val item = likesArray.get(i)
                    if (item is JSONObject) {
                        val vid = item.optString("videoId")
                        if (vid.isNotBlank()) {
                            importedLikes.add(vid)
                            dao.insertLikedVideo(
                                LikedVideoEntity(
                                    videoId = vid,
                                    title = item.optString("title", vid),
                                    channelName = item.optString("channelName", ""),
                                    thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                                    providerId = item.optString("providerId").takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    } else if (item is String && item.isNotBlank()) {
                        importedLikes.add(item)
                        dao.insertLikedVideo(LikedVideoEntity(videoId = item, title = item, channelName = ""))
                    }
                }
                viewModel.setLikedVideoIds(importedLikes)
                likesCount = importedLikes.size
            }

            // 2. Import Dislikes
            val dislikesArray = root.optJSONArray("dislikes")
            if (dislikesArray != null) {
                val importedDislikes = mutableSetOf<String>()
                for (i in 0 until dislikesArray.length()) {
                    val vid = dislikesArray.optString(i)
                    if (vid.isNotBlank()) importedDislikes.add(vid)
                }
                viewModel.setDislikedVideoIds(importedDislikes)
                dislikesCount = importedDislikes.size
            }

            // 3. Import Not Interested
            val notInterestedObj = root.optJSONObject("notInterested")
            if (notInterestedObj != null) {
                val vidsArray = notInterestedObj.optJSONArray("videoIds")
                val importedVids = mutableSetOf<String>()
                if (vidsArray != null) {
                    for (i in 0 until vidsArray.length()) {
                        val vid = vidsArray.optString(i)
                        if (vid.isNotBlank()) importedVids.add(vid)
                    }
                }

                val chansArray = notInterestedObj.optJSONArray("blockedChannels")
                val importedChans = mutableSetOf<String>()
                if (chansArray != null) {
                    for (i in 0 until chansArray.length()) {
                        val ch = chansArray.optString(i).lowercase()
                        if (ch.isNotBlank()) importedChans.add(ch)
                    }
                }

                NotInterestedManager.setHiddenVideoIds(context, importedVids)
                NotInterestedManager.setBlockedChannels(context, importedChans)
                viewModel.setNotInterestedData(importedVids, importedChans)
                notInterestedCount = importedVids.size
                blockedChannelsCount = importedChans.size
            }

            // 4. Import Watch Progress Map
            val progressObj = root.optJSONObject("watchProgressMap")
            val progressMap = mutableMapOf<String, Float>()
            if (progressObj != null) {
                val keys = progressObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    progressMap[key] = progressObj.optDouble(key, 0.5).toFloat()
                }
                viewModel.setWatchProgressMap(progressMap)
            }

            // 5. Import Watch History
            val historyArray = root.optJSONArray("watchHistory")
            if (historyArray != null) {
                val importedHistory = mutableListOf<VideoItem>()
                for (i in 0 until historyArray.length()) {
                    val item = historyArray.getJSONObject(i)
                    val vid = item.optString("videoId")
                    val title = item.optString("title", vid)
                    val channel = item.optString("channelName", "")
                    val thumb = item.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                    val providerId = item.optString("providerId").takeIf { it.isNotBlank() }
                    val prog = item.optDouble("progressFraction", progressMap[vid]?.toDouble() ?: 0.5).toFloat()

                    if (vid.isNotBlank()) {
                        dao.insertWatchHistory(
                            WatchHistoryEntity(
                                videoId = vid,
                                title = title,
                                channelName = channel,
                                thumbnailUrl = thumb,
                                providerId = providerId,
                                progressFraction = prog
                            )
                        )
                        importedHistory.add(
                            VideoItem(
                                id = vid,
                                title = title,
                                uploaderName = channel,
                                thumbnailUrl = thumb,
                                providerId = providerId
                            )
                        )
                    }
                }
                viewModel.setWatchHistory(importedHistory)
                historyCount = importedHistory.size
            }

            // 6. Import Search History
            val searchesArray = root.optJSONArray("searchHistory")
            if (searchesArray != null) {
                val searchesList = mutableListOf<String>()
                for (i in 0 until searchesArray.length()) {
                    val q = searchesArray.optString(i)
                    if (q.isNotBlank()) searchesList.add(q)
                }
                viewModel.setRecentSearches(searchesList)
                searchHistoryCount = searchesList.size
            }

            // 7. Import Bookmarks
            val bookmarksArray = root.optJSONArray("bookmarks")
            if (bookmarksArray != null) {
                val bookmarksList = mutableListOf<VideoItem>()
                for (i in 0 until bookmarksArray.length()) {
                    val item = bookmarksArray.getJSONObject(i)
                    val vid = item.optString("videoId")
                    val title = item.optString("title", vid)
                    val channel = item.optString("channelName", "")
                    val thumb = item.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                    val providerId = item.optString("providerId").takeIf { it.isNotBlank() }

                    if (vid.isNotBlank()) {
                        dao.insertBookmark(
                            BookmarkEntity(
                                videoId = vid,
                                title = title,
                                channelName = channel,
                                thumbnailUrl = thumb,
                                providerId = providerId
                            )
                        )
                        bookmarksList.add(
                            VideoItem(
                                id = vid,
                                title = title,
                                uploaderName = channel,
                                thumbnailUrl = thumb,
                                providerId = providerId
                            )
                        )
                    }
                }
                viewModel.setWatchLaterList(bookmarksList)
                bookmarksCount = bookmarksList.size
            }

            // 8. Import Playlists
            val playlistsArray = root.optJSONArray("playlists")
            if (playlistsArray != null) {
                val playlistsList = mutableListOf<UserPlaylist>()
                for (i in 0 until playlistsArray.length()) {
                    val plObj = playlistsArray.getJSONObject(i)
                    val plId = plObj.optString("id", System.currentTimeMillis().toString())
                    val plTitle = plObj.optString("title", "Imported Playlist")
                    val vidsArr = plObj.optJSONArray("videos") ?: JSONArray()
                    val vids = mutableListOf<VideoItem>()
                    for (j in 0 until vidsArr.length()) {
                        val vObj = vidsArr.getJSONObject(j)
                        vids.add(
                            VideoItem(
                                id = vObj.optString("id"),
                                title = vObj.optString("title"),
                                uploaderName = vObj.optString("uploaderName"),
                                thumbnailUrl = vObj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                                providerId = vObj.optString("providerId").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                    dao.insertOrUpdatePlaylist(
                        UserPlaylistEntity(
                            id = plId,
                            title = plTitle,
                            videosJson = vidsArr.toString()
                        )
                    )
                    playlistsList.add(UserPlaylist(id = plId, title = plTitle, videos = vids))
                }
                viewModel.setUserPlaylists(playlistsList)
                playlistsCount = playlistsList.size
            }

            // 9. Import Preferences if present
            val prefsObj = root.optJSONObject("preferences")
            if (prefsObj != null) {
                if (prefsObj.has("adultContentEnabled")) {
                    viewModel.setAdultContentEnabled(prefsObj.optBoolean("adultContentEnabled", false))
                }
                if (prefsObj.has("themeMode")) {
                    val tmStr = prefsObj.optString("themeMode")
                    try {
                        viewModel.setThemeMode(com.example.ui.ThemeMode.valueOf(tmStr))
                    } catch (e: Exception) {}
                }
                if (prefsObj.has("accentColor")) {
                    val acStr = prefsObj.optString("accentColor")
                    try {
                        viewModel.setAccentColor(com.example.ui.AppAccentColor.valueOf(acStr))
                    } catch (e: Exception) {}
                }
            }

            // 10. Trigger recommendation & feed recalculation
            viewModel.updateRecommendedVideosAsync()
            viewModel.loadTrending(forceRefresh = true)

            return@withContext ImportSummary(
                success = true,
                likesCount = likesCount,
                dislikesCount = dislikesCount,
                watchHistoryCount = historyCount,
                notInterestedCount = notInterestedCount,
                blockedChannelsCount = blockedChannelsCount,
                searchHistoryCount = searchHistoryCount,
                bookmarksCount = bookmarksCount,
                playlistsCount = playlistsCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error importing personalization data", e)
            return@withContext ImportSummary(
                success = false,
                errorMessage = e.localizedMessage ?: "Invalid JSON format"
            )
        }
    }

    /**
     * Copy text to Android Clipboard.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "Butterfly Personalization Data"): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Read text from Android Clipboard.
     */
    fun readFromClipboard(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Share personalization data as JSON text via Android Share Intent.
     */
    fun shareJson(context: Context, jsonString: String) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, jsonString)
                putExtra(Intent.EXTRA_TITLE, "Butterfly Personalization Data")
                type = "application/json"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Export Personalization Data")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing JSON", e)
        }
    }
}
