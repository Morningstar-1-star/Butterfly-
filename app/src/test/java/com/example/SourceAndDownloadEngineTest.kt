package com.example

import com.example.downloader.HlsSegmentDownloader
import com.example.extractor.plugins.JsChallengeEvaluator
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceRankingEngine
import com.example.resolver.SourceStreamType
import com.example.resolver.UniversalProviderAggregator
import com.example.resolver.health.FailureType
import com.example.resolver.health.HealthStatus
import com.example.resolver.health.ProviderHealthManager
import com.example.resolver.mirror.MirrorConfig
import com.example.resolver.mirror.MirrorManager
import com.example.resolver.registry.ProviderCategory
import com.example.resolver.registry.ProviderDescriptor
import com.example.resolver.registry.ProviderRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SourceAndDownloadEngineTest {

    @Before
    fun setUp() {
        ProviderHealthManager.resetHealth("test_provider")
        ProviderHealthManager.resetHealth("failing_provider")
        ProviderHealthManager.resetHealth("healthy_provider")
    }

    @Test
    fun testProviderHealthManager_SuccessAndFailureTracking() {
        // Initial health score should be 100
        assertEquals(100, ProviderHealthManager.getHealthScore("test_provider"))
        assertFalse(ProviderHealthManager.isQuarantined("test_provider"))

        // Record a success
        ProviderHealthManager.recordSuccess("test_provider", latencyMs = 200L)
        val stats1 = ProviderHealthManager.getStats("test_provider")
        assertEquals(1L, stats1.totalRequests)
        assertEquals(1L, stats1.successfulRequests)
        assertEquals(0, stats1.consecutiveFailures)
        assertEquals(HealthStatus.HEALTHY, stats1.healthStatus)

        // Record 1 failure -> DEGRADED
        ProviderHealthManager.recordFailure("test_provider", failureType = FailureType.HTTP_ERROR, httpCode = 503)
        val stats2 = ProviderHealthManager.getStats("test_provider")
        assertEquals(2L, stats2.totalRequests)
        assertEquals(1, stats2.consecutiveFailures)
        assertEquals(HealthStatus.DEGRADED, stats2.healthStatus)
        assertFalse(ProviderHealthManager.isQuarantined("test_provider"))

        // Record 2 more consecutive failures -> QUARANTINED (threshold = 3)
        ProviderHealthManager.recordFailure("test_provider", failureType = FailureType.TIMEOUT)
        ProviderHealthManager.recordFailure("test_provider", failureType = FailureType.DEAD_STREAM)
        val stats3 = ProviderHealthManager.getStats("test_provider")
        assertEquals(3, stats3.consecutiveFailures)
        assertEquals(HealthStatus.QUARANTINED, stats3.healthStatus)
        assertTrue(ProviderHealthManager.isQuarantined("test_provider"))
        assertTrue(stats3.healthScore <= 10)
    }

    @Test
    fun testMirrorManager_StickyMirrorAndFailover() {
        MirrorManager.registerMirror(
            MirrorConfig(
                providerId = "test_mirror_provider",
                primaryDomain = "https://mirror1.com",
                mirrors = listOf("https://mirror1.com", "https://mirror2.com", "https://mirror3.com")
            )
        )

        // Initially returns mirrors
        val initialMirrors = MirrorManager.getOrderedMirrors("test_mirror_provider")
        assertEquals(3, initialMirrors.size)
        assertEquals("https://mirror1.com", initialMirrors[0])

        // Record success on mirror 2 -> becomes sticky primary
        MirrorManager.recordMirrorSuccess("test_mirror_provider", "https://mirror2.com", latencyMs = 120L)
        val stickyMirrors = MirrorManager.getOrderedMirrors("test_mirror_provider")
        assertEquals("https://mirror2.com", stickyMirrors[0])

        // Mirror 2 fails 3 times -> quarantined, falls back to mirror 1 or 3
        MirrorManager.recordMirrorFailure("test_mirror_provider", "https://mirror2.com", FailureType.HTTP_ERROR, 500)
        MirrorManager.recordMirrorFailure("test_mirror_provider", "https://mirror2.com", FailureType.HTTP_ERROR, 500)
        MirrorManager.recordMirrorFailure("test_mirror_provider", "https://mirror2.com", FailureType.HTTP_ERROR, 500)

        val failoverMirrors = MirrorManager.getOrderedMirrors("test_mirror_provider")
        assertFalse(failoverMirrors[0].contains("mirror2.com"))
    }

    @Test
    fun testMirrorManager_CloudflareDetection() {
        val cloudflareHtml = "<html><head><title>Just a moment...</title></head><body><div class='cf-browser-verification'>Checking browser</div></body></html>"
        assertTrue(MirrorManager.isCloudflareBlock(cloudflareHtml))

        val cleanHtml = "<html><head><title>Clean Video Stream</title></head><body><h1>Watch Video</h1></body></html>"
        assertFalse(MirrorManager.isCloudflareBlock(cleanHtml))
    }

    @Test
    fun testIntelligentSourceRanking_Healthy720pOutranksFailing1080p() {
        // Record 3 failures for failing_provider
        ProviderHealthManager.recordFailure("failing_provider", failureType = FailureType.HTTP_ERROR, httpCode = 503)
        ProviderHealthManager.recordFailure("failing_provider", failureType = FailureType.HTTP_ERROR, httpCode = 503)
        ProviderHealthManager.recordFailure("failing_provider", failureType = FailureType.HTTP_ERROR, httpCode = 503)

        // Record successes for healthy_provider
        ProviderHealthManager.recordSuccess("healthy_provider", latencyMs = 150L)
        ProviderHealthManager.recordSuccess("healthy_provider", latencyMs = 150L)

        val failing1080p = SourceCandidate(
            id = "c1",
            providerId = "failing_provider",
            providerName = "Failing Source",
            serverName = "Server 1",
            type = SourceStreamType.DIRECT,
            title = "Sample Video",
            urlOrMagnet = "https://failing.com/video1080.mp4",
            quality = "1080p",
            qualityScore = 1080,
            format = "mp4",
            healthScore = 10
        )

        val healthy720p = SourceCandidate(
            id = "c2",
            providerId = "healthy_provider",
            providerName = "Healthy Source",
            serverName = "Server 2",
            type = SourceStreamType.DIRECT,
            title = "Sample Video",
            urlOrMagnet = "https://healthy.com/video720.mp4",
            quality = "720p",
            qualityScore = 720,
            format = "mp4",
            healthScore = 100
        )

        val scoreFailing = SourceRankingEngine.calculateCompositeScore(failing1080p)
        val scoreHealthy = SourceRankingEngine.calculateCompositeScore(healthy720p)

        assertTrue(
            "Healthy 720p score ($scoreHealthy) must be strictly greater than quarantined/failing 1080p score ($scoreFailing)",
            scoreHealthy > scoreFailing
        )

        val ranked = SourceRankingEngine.rank(listOf(failing1080p, healthy720p))
        assertEquals("c2", ranked.first().id)
    }

    @Test
    fun testProviderRegistry_RegistrationAndLookup() {
        val testDesc = ProviderDescriptor(
            id = "custom_test_stream",
            displayName = "Custom Test Stream",
            category = ProviderCategory.ADULT,
            baseDomain = "https://customstream.test",
            mirrors = listOf("https://customstream.test", "https://customstream.mirror"),
            priority = 98
        )
        ProviderRegistry.register(testDesc)

        val retrieved = ProviderRegistry.get("custom_test_stream")
        assertNotNull(retrieved)
        assertEquals("Custom Test Stream", retrieved?.displayName)
        assertEquals(2, retrieved?.mirrors?.size)

        val active = ProviderRegistry.getActiveProviders()
        assertTrue(active.any { it.id == "custom_test_stream" })
    }

    @Test
    fun testJsChallengeEvaluator_DeanEdwardsPackerUnpacking() {
        val packed = """eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c.toString(a)+'\\b','g'),k[c]);return p}('0 1="2";',3,3,'var|videoUrl|https'.split('|')))"""
        val unpacked = JsChallengeEvaluator.unpack(packed)
        assertTrue(unpacked.contains("var videoUrl=\"https\"") || unpacked.contains("videoUrl"))
    }

    @Test
    fun testJsChallengeEvaluator_SignatureDeobfuscation() {
        val sig = "abcdef123456"
        val deobfuscated = JsChallengeEvaluator.deobfuscateSignature(sig, listOf("reverse", "slice 2"))
        assertEquals("4321fedcba", deobfuscated)
    }
}
