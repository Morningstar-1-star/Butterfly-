package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.subtitles.SubtitleFormat
import com.example.subtitles.SubtitleItem
import com.example.subtitles.SubtitleManager
import com.example.subtitles.SubtitleSearchQuery
import com.example.subtitles.SubtitleSourceType
import com.example.subtitles.plugin.SubtitlePluginRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubtitleProviderPluginPipelineTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SubtitlePluginRegistry.resetToDefaults(context)
    }

    @Test
    fun testPluginRegistryDefaultDescriptorsAndLazyLoading() {
        val descriptors = SubtitlePluginRegistry.getAllPluginDescriptors(context)
        assertEquals(5, descriptors.size)

        val subdl = descriptors.find { it.id == "subdl" }
        assertNotNull(subdl)
        assertTrue(subdl!!.isInstalled)
        assertTrue(subdl.isEnabled)
        assertTrue(subdl.supportsFps)
        assertTrue(subdl.supportsHi)

        val openSub = descriptors.find { it.id == "opensubtitles" }
        assertNotNull(openSub)
        assertTrue(openSub!!.isInstalled)
        assertTrue(openSub.isEnabled)

        val subcat = descriptors.find { it.id == "subtitlecat" }
        assertNotNull(subcat)
        assertTrue(subcat!!.isInstalled)
        assertTrue(subcat.isEnabled)

        val gestdown = descriptors.find { it.id == "gestdown" }
        assertNotNull(gestdown)
        assertTrue(gestdown!!.isInstalled)
        assertTrue(gestdown.isEnabled)

        val podnapisi = descriptors.find { it.id == "podnapisi" }
        assertNotNull(podnapisi)
        assertTrue(podnapisi!!.isInstalled)
        assertFalse("Podnapisi is disabled by default", podnapisi.isEnabled)

        // Check enabled plugins
        val enabledPlugins = SubtitlePluginRegistry.getEnabledPlugins(context)
        assertEquals(4, enabledPlugins.size)
        val enabledIds = enabledPlugins.map { it.id }.toSet()
        assertTrue(enabledIds.contains("subdl"))
        assertTrue(enabledIds.contains("opensubtitles"))
        assertTrue(enabledIds.contains("subtitlecat"))
        assertTrue(enabledIds.contains("gestdown"))
        assertFalse(enabledIds.contains("podnapisi"))
    }

    @Test
    fun testPluginEnableDisableAndInstallUninstall() {
        assertTrue(SubtitlePluginRegistry.isPluginEnabled(context, "subdl"))

        // Disable subdl
        SubtitlePluginRegistry.setPluginEnabled(context, "subdl", false)
        assertFalse(SubtitlePluginRegistry.isPluginEnabled(context, "subdl"))

        var enabledPlugins = SubtitlePluginRegistry.getEnabledPlugins(context)
        assertFalse(enabledPlugins.any { it.id == "subdl" })

        // Re-enable subdl
        SubtitlePluginRegistry.setPluginEnabled(context, "subdl", true)
        assertTrue(SubtitlePluginRegistry.isPluginEnabled(context, "subdl"))

        // Uninstall subtitlecat
        SubtitlePluginRegistry.setPluginInstalled(context, "subtitlecat", false)
        assertFalse(SubtitlePluginRegistry.isPluginInstalled(context, "subtitlecat"))
        assertFalse(SubtitlePluginRegistry.isPluginEnabled(context, "subtitlecat"))
        assertNull(SubtitlePluginRegistry.getPlugin("subtitlecat", context))

        // Reinstall subtitlecat
        SubtitlePluginRegistry.setPluginInstalled(context, "subtitlecat", true)
        assertTrue(SubtitlePluginRegistry.isPluginInstalled(context, "subtitlecat"))
        SubtitlePluginRegistry.setPluginEnabled(context, "subtitlecat", true)
        assertNotNull(SubtitlePluginRegistry.getPlugin("subtitlecat", context))
    }

    @Test
    fun testOptionalApiKeysPerProvider() {
        assertNull(SubtitlePluginRegistry.getPluginApiKey(context, "subdl"))
        assertNull(SubtitlePluginRegistry.getPluginApiKey(context, "opensubtitles"))

        // Set custom key for subdl
        SubtitlePluginRegistry.setPluginApiKey(context, "subdl", "CUSTOM_SUBDL_KEY_123")
        assertEquals("CUSTOM_SUBDL_KEY_123", SubtitlePluginRegistry.getPluginApiKey(context, "subdl"))

        // Set custom key for opensubtitles
        SubtitlePluginRegistry.setPluginApiKey(context, "opensubtitles", "CUSTOM_OS_KEY_456")
        assertEquals("CUSTOM_OS_KEY_456", SubtitlePluginRegistry.getPluginApiKey(context, "opensubtitles"))

        // Reset to default
        SubtitlePluginRegistry.setPluginApiKey(context, "subdl", null)
        assertNull(SubtitlePluginRegistry.getPluginApiKey(context, "subdl"))
    }

    @Test
    fun testStrictRankingHierarchy() {
        val query = SubtitleSearchQuery(
            title = "Inception",
            year = 2010,
            languageCode = "en",
            releaseName = "Inception.2010.1080p.BluRay.x264.YIFY",
            fps = 23.976f,
            resolution = "1080p",
            movieHash = "hash123456"
        )

        val targetLang = "en"

        // 1. Language hierarchy test: English (target) vs Spanish
        val englishItem = SubtitleItem(
            id = "sub1",
            providerId = "subdl",
            providerName = "SubDL",
            title = "Inception 2010",
            languageCode = "en",
            languageName = "English",
            downloadUrl = "https://dl.subdl.com/1.srt"
        )

        val spanishItem = SubtitleItem(
            id = "sub2",
            providerId = "opensubtitles",
            providerName = "OpenSubtitles",
            title = "Inception 2010",
            languageCode = "es",
            languageName = "Spanish",
            downloadUrl = "https://dl.subdl.com/2.srt"
        )

        var ranked = SubtitleManager.rankSubtitles(listOf(spanishItem, englishItem), query, targetLang, preferHi = false)
        assertEquals("Target language item must rank first", "sub1", ranked[0].id)

        // 2. Release & Title matching test: exact 1080p BluRay YIFY vs generic
        val exactReleaseItem = englishItem.copy(
            id = "exact_rel",
            title = "Inception 2010 1080p BluRay x264 YIFY",
            releaseInfo = "Inception.2010.1080p.BluRay.x264.YIFY",
            resolution = "1080p",
            fps = 23.976f
        )
        val genericItem = englishItem.copy(
            id = "generic",
            title = "Inception Movie",
            releaseInfo = "Inception.CAM.Rip"
        )

        ranked = SubtitleManager.rankSubtitles(listOf(genericItem, exactReleaseItem), query, targetLang, preferHi = false)
        assertEquals("Exact release match must rank highest", "exact_rel", ranked[0].id)
        assertTrue("Exact release score must exceed generic", ranked[0].matchScore > ranked[1].matchScore)

        // 3. FPS & Resolution matching test
        val matchingFpsItem = exactReleaseItem.copy(id = "fps_match", fps = 23.976f)
        val mismatchedFpsItem = exactReleaseItem.copy(id = "fps_mismatch", fps = 29.97f)
        ranked = SubtitleManager.rankSubtitles(listOf(mismatchedFpsItem, matchingFpsItem), query, targetLang, preferHi = false)
        assertEquals("Matching FPS must rank higher", "fps_match", ranked[0].id)

        // 4. HI / SDH preference test
        val hiItem = exactReleaseItem.copy(id = "hi_sub", isHearingImpaired = true)
        val cleanItem = exactReleaseItem.copy(id = "clean_sub", isHearingImpaired = false)

        // When preferHi == false, cleanItem should win
        val rankedClean = SubtitleManager.rankSubtitles(listOf(hiItem, cleanItem), query, targetLang, preferHi = false)
        assertEquals("clean_sub should win when preferHi is false", "clean_sub", rankedClean[0].id)

        // When preferHi == true, hiItem should win
        val rankedHi = SubtitleManager.rankSubtitles(listOf(hiItem, cleanItem), query, targetLang, preferHi = true)
        assertEquals("hi_sub should win when preferHi is true", "hi_sub", rankedHi[0].id)

        // 5. Exact Hash Match test
        val hashItem = exactReleaseItem.copy(id = "hash_sub", movieHash = "hash123456")
        val noHashItem = exactReleaseItem.copy(id = "no_hash_sub", movieHash = null)
        ranked = SubtitleManager.rankSubtitles(listOf(noHashItem, hashItem), query, targetLang, preferHi = false)
        assertEquals("Matching movieHash must rank top", "hash_sub", ranked[0].id)
    }

    @Test
    fun testTvSeriesSeasonAndEpisodeRankingPenalty() {
        val tvQuery = SubtitleSearchQuery(
            title = "Breaking Bad",
            season = 1,
            episode = 2,
            languageCode = "en"
        )

        val correctEpisode = SubtitleItem(
            id = "correct_ep",
            providerId = "gestdown",
            providerName = "Gestdown",
            title = "Breaking Bad S01E02",
            languageCode = "en",
            languageName = "English",
            downloadUrl = "https://api.gestdown.info/sub/1.srt",
            season = 1,
            episode = 2
        )

        val wrongEpisode = SubtitleItem(
            id = "wrong_ep",
            providerId = "gestdown",
            providerName = "Gestdown",
            title = "Breaking Bad S01E05",
            languageCode = "en",
            languageName = "English",
            downloadUrl = "https://api.gestdown.info/sub/2.srt",
            season = 1,
            episode = 5
        )

        val ranked = SubtitleManager.rankSubtitles(listOf(wrongEpisode, correctEpisode), tvQuery, "en", preferHi = false)
        assertEquals("Correct season and episode must rank top", "correct_ep", ranked[0].id)
        assertTrue("Wrong episode must be heavily penalized", ranked[1].matchScore < 0)
    }

    @Test
    fun testDeduplicationAcrossMultipleProviders() {
        val sub1 = SubtitleItem(
            id = "subdl_1",
            providerId = "subdl",
            providerName = "SubDL",
            title = "Inception 2010 1080p BluRay",
            languageCode = "en",
            languageName = "English",
            downloadUrl = "https://dl.subdl.com/inception.srt",
            releaseInfo = "Inception.2010.1080p.BluRay",
            matchScore = 80
        )

        // Duplicate from OpenSubtitles with better metadata (FPS + resolution)
        val sub2 = SubtitleItem(
            id = "os_1",
            providerId = "opensubtitles",
            providerName = "OpenSubtitles",
            title = "Inception 2010 1080p BluRay",
            languageCode = "en",
            languageName = "English",
            downloadUrl = "https://dl.subdl.com/inception.srt", // same URL
            releaseInfo = "Inception.2010.1080p.BluRay",
            fps = 23.976f,
            resolution = "1080p",
            matchScore = 90
        )

        // Distinct subtitle track in French
        val sub3 = SubtitleItem(
            id = "cat_1",
            providerId = "subtitlecat",
            providerName = "SubtitleCat",
            title = "Inception 2010 1080p French",
            languageCode = "fr",
            languageName = "French",
            downloadUrl = "https://subtitlecat.com/subs/1/orig.srt",
            releaseInfo = "Inception.2010.1080p.French",
            matchScore = 85
        )

        val deduplicated = SubtitleManager.deduplicateSubtitles(listOf(sub1, sub2, sub3))
        assertEquals("Duplicates with same URL/release must be merged to 1", 2, deduplicated.size)
        assertTrue(deduplicated.any { it.languageCode == "en" })
        assertTrue(deduplicated.any { it.languageCode == "fr" })
    }
}
