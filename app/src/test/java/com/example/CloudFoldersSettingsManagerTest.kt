package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.CloudFoldersSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudFoldersSettingsManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CloudFoldersSettingsManager.clearAllMegaFolders(context)
        CloudFoldersSettingsManager.clearAllTelegramChannels(context)
    }

    @Test
    fun testEmptyByDefaultAndNoZombieLinks() {
        val megaList = CloudFoldersSettingsManager.getMegaFolderUrls(context)
        val tgList = CloudFoldersSettingsManager.getTelegramChannelUrls(context)
        assertEquals(0, megaList.size)
        assertEquals(0, tgList.size)
    }

    @Test
    fun testAddAndPermanentlyRemoveMegaFolder() {
        val url = "https://mega.nz/folder/abc12345#secretkey"
        CloudFoldersSettingsManager.addMegaFolderUrl(context, url)
        var list = CloudFoldersSettingsManager.getMegaFolderUrls(context)
        assertEquals(1, list.size)
        assertEquals(url, list[0])

        // Remove it permanently
        CloudFoldersSettingsManager.removeMegaFolderUrl(context, url)
        list = CloudFoldersSettingsManager.getMegaFolderUrls(context)
        assertEquals(0, list.size)
    }

    @Test
    fun testAddMultipleMegaFolders() {
        val multiInput = """
            https://mega.nz/folder/folder1#key1
            https://mega.nz/folder/folder2#key2, https://mega.nz/folder/folder3#key3
        """.trimIndent()

        val added = CloudFoldersSettingsManager.addMultipleMegaFolderUrls(context, multiInput)
        assertEquals(3, added)

        val list = CloudFoldersSettingsManager.getMegaFolderUrls(context)
        assertEquals(3, list.size)

        // Clear all
        CloudFoldersSettingsManager.clearAllMegaFolders(context)
        assertTrue(CloudFoldersSettingsManager.getMegaFolderUrls(context).isEmpty())
    }

    @Test
    fun testTelegramFormattingAndMultiAdd() {
        val multiInput = """
            @best_movies
            t.me/news_channel
            https://t.me/s/anime_clips
        """.trimIndent()

        val added = CloudFoldersSettingsManager.addMultipleTelegramChannelUrls(context, multiInput)
        assertEquals(3, added)

        val list = CloudFoldersSettingsManager.getTelegramChannelUrls(context)
        assertEquals(3, list.size)
        assertTrue(list.contains("https://t.me/s/best_movies"))
        assertTrue(list.contains("https://t.me/s/news_channel"))
        assertTrue(list.contains("https://t.me/s/anime_clips"))

        // Remove one channel by handle
        CloudFoldersSettingsManager.removeTelegramChannelUrl(context, "@best_movies")
        val updated = CloudFoldersSettingsManager.getTelegramChannelUrls(context)
        assertEquals(2, updated.size)
        assertTrue(!updated.contains("https://t.me/s/best_movies"))
    }
}
