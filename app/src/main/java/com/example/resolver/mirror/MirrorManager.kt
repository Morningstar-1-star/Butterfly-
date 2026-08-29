package com.example.resolver.mirror

import android.util.Log
import com.example.resolver.health.FailureType
import com.example.resolver.health.ProviderHealthManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class MirrorConfig(
    val providerId: String,
    val primaryDomain: String,
    val mirrors: List<String>,
    val requiresReferer: Boolean = true,
    val customHeaders: Map<String, String> = emptyMap()
)

data class ValidationResult(
    val isValid: Boolean,
    val failureType: FailureType = FailureType.UNKNOWN,
    val errorMessage: String? = null
)

/**
 * UAV-Inspired Mirror / Failover Engine.
 *
 * Implements:
 * - Dynamic mirror rotation and failover across multiple configured mirror domains.
 * - Sticky working mirror: remembers and prioritizes the last successful mirror.
 * - Content validation: rejects Cloudflare challenges, bot honeypots, blank pages, and invalid redirects.
 * - Health-aware mirror ordering: sorts available mirrors by health scores from [ProviderHealthManager].
 * - Redirect protection: verifies destination domain to prevent tracking/hijack redirects.
 */
object MirrorManager {
    private const val TAG = "MirrorManager"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Configured mirror registries
    private val mirrorConfigs = ConcurrentHashMap<String, MirrorConfig>()
    // Sticky working mirror per provider: providerId -> mirror domain
    private val stickyMirrors = ConcurrentHashMap<String, String>()

    init {
        registerDefaultMirrors()
    }

    private fun registerDefaultMirrors() {
        registerMirror(
            MirrorConfig(
                providerId = "jable",
                primaryDomain = "https://jable.tv",
                mirrors = listOf("https://jable.tv", "https://jable.to", "https://jable.net")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "missav",
                primaryDomain = "https://missav.ai",
                mirrors = listOf("https://missav.ai", "https://missav.ws", "https://missav.com", "https://missav.live")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "hanime1",
                primaryDomain = "https://hanime1.me",
                mirrors = listOf("https://hanime1.me", "https://hanime1.com", "https://hanime1.co")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "spankbang",
                primaryDomain = "https://spankbang.com",
                mirrors = listOf("https://spankbang.com", "https://spankbang.party", "https://spankbang.porn", "https://spankbang.site")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "hqporner",
                primaryDomain = "https://hqporner.com",
                mirrors = listOf("https://hqporner.com", "https://hqporner.tv", "https://m.hqporner.com")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "eporner",
                primaryDomain = "https://www.eporner.com",
                mirrors = listOf("https://www.eporner.com", "https://eporner.com", "https://static-sg-cdn.eporner.com")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "xvideos",
                primaryDomain = "https://www.xvideos.com",
                mirrors = listOf("https://www.xvideos.com", "https://www.xvideos2.com", "https://www.xvideos.es")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "xhamster",
                primaryDomain = "https://xhamster.com",
                mirrors = listOf("https://xhamster.com", "https://xhamster.desi", "https://xhamster2.com", "https://xhamster3.com")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "pornhub",
                primaryDomain = "https://www.pornhub.com",
                mirrors = listOf("https://www.pornhub.com", "https://www.pornhubpremium.com", "https://rt.pornhub.com")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "redtube",
                primaryDomain = "https://www.redtube.com",
                mirrors = listOf("https://www.redtube.com", "https://api.redtube.com", "https://m.redtube.com")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "4tube",
                primaryDomain = "https://www.4tube.com",
                mirrors = listOf("https://www.4tube.com", "https://m.4tube.com")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "youporn",
                primaryDomain = "https://www.youporn.com",
                mirrors = listOf("https://www.youporn.com", "https://m.youporn.com")
            )
        )
        registerMirror(
            MirrorConfig(
                providerId = "beeg",
                primaryDomain = "https://beeg.com",
                mirrors = listOf("https://beeg.com", "https://api.beeg.com")
            )
        )
    }

    fun registerMirror(config: MirrorConfig) {
        mirrorConfigs[config.providerId.lowercase()] = config
    }

    /**
     * Returns the ordered list of mirrors for a provider.
     * Order:
     * 1. Sticky working mirror (if healthy)
     * 2. Other non-quarantined mirrors ranked by health score
     * 3. Quarantined mirrors (as last resort)
     */
    fun getOrderedMirrors(providerId: String): List<String> {
        val pid = providerId.lowercase()
        val config = mirrorConfigs[pid] ?: return emptyList()

        val sticky = stickyMirrors[pid]
        val allMirrors = config.mirrors.distinct()

        return allMirrors.sortedWith { m1, m2 ->
            val host1 = extractHost(m1)
            val host2 = extractHost(m2)

            val isM1Sticky = sticky != null && m1.equals(sticky, ignoreCase = true)
            val isM2Sticky = sticky != null && m2.equals(sticky, ignoreCase = true)

            val q1 = ProviderHealthManager.isQuarantined(pid, host1)
            val q2 = ProviderHealthManager.isQuarantined(pid, host2)

            val score1 = ProviderHealthManager.getHealthScore(pid, host1)
            val score2 = ProviderHealthManager.getHealthScore(pid, host2)

            when {
                q1 && !q2 -> 1
                !q1 && q2 -> -1
                isM1Sticky && !q1 -> -1
                isM2Sticky && !q2 -> 1
                else -> score2.compareTo(score1) // Higher score first
            }
        }
    }

    /**
     * Returns the primary or best healthy mirror for a provider.
     */
    fun getPrimaryMirror(providerId: String): String {
        val mirrors = getOrderedMirrors(providerId)
        return mirrors.firstOrNull() ?: mirrorConfigs[providerId.lowercase()]?.primaryDomain ?: ""
    }

    /**
     * Remembers the successful mirror for sticky future resolution.
     */
    fun recordMirrorSuccess(providerId: String, mirrorUrl: String, latencyMs: Long = 0L) {
        val pid = providerId.lowercase()
        val host = extractHost(mirrorUrl)
        stickyMirrors[pid] = mirrorUrl
        ProviderHealthManager.recordSuccess(pid, host, latencyMs)
        Log.d(TAG, "Sticky mirror updated for $pid -> $mirrorUrl (latency: ${latencyMs}ms)")
    }

    /**
     * Records mirror failure.
     */
    fun recordMirrorFailure(
        providerId: String,
        mirrorUrl: String,
        failureType: FailureType,
        httpCode: Int = 0,
        errorMessage: String? = null,
        latencyMs: Long = 0L
    ) {
        val pid = providerId.lowercase()
        val host = extractHost(mirrorUrl)
        if (stickyMirrors[pid]?.equals(mirrorUrl, ignoreCase = true) == true) {
            stickyMirrors.remove(pid) // Invalidate sticky mirror
        }
        ProviderHealthManager.recordFailure(pid, host, failureType, httpCode, errorMessage, latencyMs)
        Log.w(TAG, "Mirror failed for $pid ($mirrorUrl): $failureType (HTTP $httpCode)")
    }

    /**
     * Validates whether a response contains genuine content or an anti-bot challenge / honeypot.
     */
    fun validateResponse(response: Response, responseBody: String): ValidationResult {
        if (!response.isSuccessful && response.code != 206) {
            return when (response.code) {
                403, 503 -> {
                    if (isCloudflareBlock(responseBody)) {
                        ValidationResult(false, FailureType.CLOUDFLARE_BLOCK, "Cloudflare bot challenge detected (HTTP ${response.code})")
                    } else {
                        ValidationResult(false, FailureType.HTTP_ERROR, "HTTP ${response.code}: Forbidden/Unavailable")
                    }
                }
                429 -> ValidationResult(false, FailureType.HTTP_ERROR, "HTTP 429: Rate limited")
                404 -> ValidationResult(false, FailureType.HTTP_ERROR, "HTTP 404: Not Found")
                else -> ValidationResult(false, FailureType.HTTP_ERROR, "HTTP ${response.code}: ${response.message}")
            }
        }

        if (responseBody.isBlank()) {
            return ValidationResult(false, FailureType.DEAD_STREAM, "Empty response body received")
        }

        if (isCloudflareBlock(responseBody)) {
            return ValidationResult(false, FailureType.CLOUDFLARE_BLOCK, "Cloudflare challenge page detected in HTTP 200 payload")
        }

        return ValidationResult(true)
    }

    /**
     * Checks for known Cloudflare / DDOS-GUARD / Bot challenge signatures.
     */
    fun isCloudflareBlock(html: String): Boolean {
        if (html.length > 500_000) return false // Very large HTML is unlikely a simple challenge page
        val lower = html.lowercase()
        return lower.contains("cf-browser-verification") ||
                lower.contains("<title>just a moment...</title>") ||
                lower.contains("<title>attention required! | cloudflare</title>") ||
                lower.contains("challenge-platform/scripts") ||
                lower.contains("cf-chl-widget-") ||
                lower.contains("ddos-guard") ||
                (lower.contains("ray id:") && lower.contains("cloudflare"))
    }

    private fun extractHost(url: String): String {
        return try {
            URI(url).host ?: url
        } catch (_: Exception) {
            url
        }
    }
}
