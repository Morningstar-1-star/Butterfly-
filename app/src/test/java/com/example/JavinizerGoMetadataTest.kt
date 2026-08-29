package com.example

import com.example.metadata.JavIdParser
import com.example.metadata.JavMetadata
import com.example.metadata.JavMetadataResolver
import com.example.metadata.javinizer.JavinizerGoClient
import com.example.metadata.providers.JavBusMetadataProvider
import com.example.metadata.providers.JavinizerGoMetadataProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JavinizerGoMetadataTest {

    @Test
    fun testJavIdParserValidation() {
        assertEquals("IPX-535", JavIdParser.parse("IPX-535"))
        assertEquals("IPX-535", JavIdParser.parse("ipx535"))
        assertEquals("SSIS-001", JavIdParser.parse("ssis-001"))
        assertEquals("FC2-PPV-1234567", JavIdParser.parse("FC2-PPV-1234567"))
        assertNull(JavIdParser.parse("invalid_id_without_digits"))
        assertNull(JavIdParser.parse(""))
    }

    @Test
    fun testJavinizerGoClientUnreachableServer() = runBlocking {
        val client = JavinizerGoClient(defaultTimeoutSec = 2)
        val health = client.checkHealth("http://127.0.0.1:59999", timeoutSec = 1)
        assertFalse("Unreachable server must report health failure", health.isSuccess)
        assertNull("Unreachable server must not report a version", health.serverVersion)
        assertTrue("Message should indicate unreachable status", health.statusMessage.contains("Unreachable"))

        val metadata = client.getMovieMetadata("IPX-535", baseUrl = "http://127.0.0.1:59999", timeoutSec = 1)
        assertNull("Unreachable server must return null metadata", metadata)
    }

    @Test
    fun testJavinizerGoMetadataProviderClassification() {
        val provider = JavinizerGoMetadataProvider()
        assertEquals("javinizer_go", provider.id)
        assertEquals("Javinizer-Go (REST Service)", provider.name)
        assertEquals(com.example.metadata.ProviderClassification.API_ADAPTER, provider.classification)
        assertEquals(200, provider.priority)
    }

    @Test
    fun testJavBusMetadataProviderClassification() {
        val provider = JavBusMetadataProvider()
        assertEquals("javbus", provider.id)
        assertEquals(com.example.metadata.ProviderClassification.SCRAPER, provider.classification)
        assertEquals(100, provider.priority)
    }

    @Test
    fun testResolverWithInvalidIdReturnsNull() = runBlocking {
        val result = JavMetadataResolver.resolve("non_existent_random_abc_12345")
        assertNull("Invalid JAV ID should return null without returning fake metadata", result)
    }
}
