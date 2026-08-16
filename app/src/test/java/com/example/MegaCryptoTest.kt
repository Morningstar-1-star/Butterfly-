package com.example

import com.example.util.MegaCrypto
import org.junit.Assert.*
import org.junit.Test

class MegaCryptoTest {

    @Test
    fun testParseMegaFolderUrl() {
        val url = "https://mega.nz/folder/Io4myLQC#0MV-ZU9NXIQZtRfcKSiqog/folder/1hogxZ7B"
        val info = MegaCrypto.parseMegaUrl(url)

        assertNotNull(info)
        assertEquals("Io4myLQC", info!!.folderId)
        assertNotNull(info.masterKey)
        assertEquals("1hogxZ7B", info.subFolderId)
        assertTrue(info.isFolder)
    }

    @Test
    fun testBase64UrlDecodeAndEncode() {
        val originalKeyB64 = "0MV-ZU9NXIQZtRfcKSiqog"
        val bytes = MegaCrypto.base64UrlDecode(originalKeyB64)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        val encoded = MegaCrypto.base64UrlEncode(bytes)
        assertEquals(originalKeyB64, encoded)
    }

    @Test
    fun testVideoFileDetector() {
        assertTrue(MegaCrypto.isVideoFile("movie.mp4"))
        assertTrue(MegaCrypto.isVideoFile("episode.1.mkv"))
        assertTrue(MegaCrypto.isVideoFile("clip.webm"))
        assertFalse(MegaCrypto.isVideoFile("subtitles.srt"))
        assertFalse(MegaCrypto.isVideoFile("document.pdf"))
    }
}
