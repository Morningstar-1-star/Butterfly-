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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeenVersion = prefs.getInt(KEY_LAST_SEEN_VERSION, 0)
        // If lastSeenVersion is lower than current build version, show the what's new dialog
        return lastSeenVersion < BuildConfig.VERSION_CODE
    }

    /**
     * Marks the current version as seen so the dialog only pops up once per update.
     */
    fun markWhatsNewAsSeen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LAST_SEEN_VERSION, BuildConfig.VERSION_CODE).apply()
    }
}
