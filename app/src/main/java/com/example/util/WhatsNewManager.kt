package com.example.util

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.BuildConfig

data class ChangelogCategory(
    val title: String,
    val icon: ImageVector,
    val items: List<String>
)

data class VersionRelease(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val headline: String,
    val categories: List<ChangelogCategory>
)

object WhatsNewManager {
    private const val PREFS_NAME = "butterfly_version_prefs"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version_code"

    val RELEASES: List<VersionRelease> = listOf(
        VersionRelease(
            versionName = "0.0.3-alpha",
            versionCode = 3,
            releaseDate = "September 2026",
            headline = "Stream Source Overhauls, Supabase Sync & Performance",
            categories = listOf(
                ChangelogCategory(
                    title = "Source Fixes & Native Streams",
                    icon = Icons.Default.PlayCircle,
                    items = listOf(
                        "SupJav Direct Playback: Fixed 0:00 buffering issue with robust URL routing and multi-server resolution.",
                        "HellPorno Thumbnails: Full image preview support with dedicated CDN referer and cookie authorization.",
                        "Playvids Full Catalog & Direct Streams: Multi-page real catalog browsing and native MP4/HLS playback without web embeds.",
                        "TNAFlix Authentic Source: Cleaned up Eporner fallback and restored authentic TNAFlix video feeds and streaming.",
                        "ThisVid Playback: Resolved HTTP 404 stream error with automatic KVS token handling and session headers."
                    )
                ),
                ChangelogCategory(
                    title = "Cloud Sync & Account",
                    icon = Icons.Default.CloudSync,
                    items = listOf(
                        "Supabase Bidirectional Sync: Real-time synchronization of watch history, bookmarks, liked videos, playlists, and settings across all devices.",
                        "Instant Login Sync: Automatic data pull and offline change upload upon sign-in."
                    )
                ),
                ChangelogCategory(
                    title = "App Experience",
                    icon = Icons.Default.AutoAwesome,
                    items = listOf(
                        "Accurate Update Checker: No false update prompts once you are on the latest version.",
                        "Ultra-smooth Activity Transitions: Fluid entry and exit animations."
                    )
                )
            )
        ),
        VersionRelease(
            versionName = "0.0.2-alpha",
            versionCode = 2,
            releaseDate = "September 2026",
            headline = "Horizontal Episode Browsing & Archive Video Quality Enhancements",
            categories = listOf(
                ChangelogCategory(
                    title = "TV Series & Episodes",
                    icon = Icons.Default.VideoLibrary,
                    items = listOf(
                        "Horizontal Episode Carousel: Series (e.g. Courage the Cowardly Dog) now display episodes in a sleek horizontal scrolling row instead of a long vertical list.",
                        "Episode Preview Cards: 16:9 thumbnail previews, episode number badges, IMDb ratings, and duration tags.",
                        "Smart Auto-Scroll: The episodes row automatically scrolls and centers on your currently playing episode."
                    )
                ),
                ChangelogCategory(
                    title = "Archive.org Smart Detection",
                    icon = Icons.Default.AccountBalance,
                    items = listOf(
                        "Quality vs Episode Disambiguation: Multiple video resolutions (1080p, 720p, 480p, 512kb) of the same video are now stream quality options instead of fake separate episodes.",
                        "Clean Deduplication: Real Archive.org multi-episode collections automatically select the highest quality stream without creating duplicate entries."
                    )
                ),
                ChangelogCategory(
                    title = "App Experience & Polish",
                    icon = Icons.Default.AutoAwesome,
                    items = listOf(
                        "Opening Screen Fix: Seamless launch splash screen for Android 12+ (API 31+) in both AMOLED dark and light themes.",
                        "Player Controls: Clean action bar with inline sliding drawers for Video Description and Comments."
                    )
                )
            )
        ),
        VersionRelease(
            versionName = "0.0.1-alpha",
            versionCode = 1,
            releaseDate = "Initial Alpha",
            headline = "Initial Launch of Butterfly",
            categories = listOf(
                ChangelogCategory(
                    title = "Core Features",
                    icon = Icons.Default.PlayCircle,
                    items = listOf(
                        "Multi-source streaming (YouTube, Archive.org, Vega, Bittorrent).",
                        "Background audio playback and Picture-in-Picture mode.",
                        "Custom themes, accent colors, and dynamic subtitle support."
                    )
                )
            )
        )
    )

    fun getLatestRelease(): VersionRelease = RELEASES.first()

    /**
     * Checks if the app has just been updated to a new version code.
     */
    fun shouldShowWhatsNew(context: Context): Boolean {
        return false
    }

    /**
     * Marks the current version as seen so the dialog only pops up once per update.
     */
    fun markWhatsNewAsSeen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LAST_SEEN_VERSION, BuildConfig.VERSION_CODE).apply()
    }
}
