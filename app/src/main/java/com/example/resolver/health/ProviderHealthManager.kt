package com.example.resolver.health

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    QUARANTINED,
    PROBING
}

enum class FailureType {
    HTTP_ERROR,
    CLOUDFLARE_BLOCK,
    TIMEOUT,
    EXTRACTION_FAILED,
    DEAD_STREAM,
    PARSE_ERROR,
    UNKNOWN
}

data class ProviderHealthStats(
    val providerId: String,
    val domain: String,
    val totalRequests: Long = 0L,
    val successfulRequests: Long = 0L,
    val failedRequests: Long = 0L,
    val consecutiveFailures: Int = 0,
    val averageLatencyMs: Long = 0L,
    val lastFailureType: FailureType? = null,
    val lastFailureCode: Int = 0,
    val lastFailureMessage: String? = null,
    val lastFailureTimestamp: Long = 0L,
    val quarantineExpiresAt: Long = 0L,
    val healthStatus: HealthStatus = HealthStatus.HEALTHY,
    val healthScore: Int = 100 // 0 to 100
)

/**
 * Production-Grade Provider Reliability & Health Engine.
 *
 * Implements:
 * - Dynamic health score computation (0-100) based on rolling success rate, latency, and error types.
 * - Circuit breaker quarantine: after repeated consecutive failures, temporarily isolates a provider.
 * - Automatic recovery & probe states: after quarantine cooldown, allows half-open probe requests.
 * - Non-permanent degradation: a single failure never permanently blacklists a source.
 * - Error classification: tracks Cloudflare challenges, HTTP 403/429/502/503, timeouts, and dead streams.
 */
object ProviderHealthManager {
    private const val TAG = "ProviderHealthManager"

    private const val CONSECUTIVE_FAILURE_THRESHOLD = 3
    private const val DEFAULT_QUARANTINE_COOLDOWN_MS = 60_000L // 1 minute
    private const val MAX_QUARANTINE_COOLDOWN_MS = 600_000L // 10 minutes

    private val statsMap = ConcurrentHashMap<String, ProviderHealthStats>()

    private fun getKey(providerId: String, domain: String? = null): String {
        val cleanP = providerId.trim().lowercase()
        val cleanD = (domain ?: "").trim().lowercase()
        return if (cleanD.isNotBlank()) "$cleanP@$cleanD" else cleanP
    }

    /**
     * Records a successful operation for a provider/mirror.
     */
    fun recordSuccess(providerId: String, domain: String? = null, latencyMs: Long = 0L) {
        val key = getKey(providerId, domain)
        val current = statsMap[key] ?: ProviderHealthStats(providerId = providerId, domain = domain ?: "")

        val newTotal = current.totalRequests + 1
        val newSuccess = current.successfulRequests + 1
        val newLatency = if (current.averageLatencyMs == 0L) latencyMs else ((current.averageLatencyMs * 4) + latencyMs) / 5

        val updated = current.copy(
            totalRequests = newTotal,
            successfulRequests = newSuccess,
            consecutiveFailures = 0,
            averageLatencyMs = newLatency,
            healthStatus = HealthStatus.HEALTHY,
            quarantineExpiresAt = 0L,
            healthScore = calculateHealthScore(newTotal, newSuccess, 0, newLatency, HealthStatus.HEALTHY)
        )

        statsMap[key] = updated
        Log.d(TAG, "Provider $key success recorded. Score: ${updated.healthScore}, Latency: ${newLatency}ms")
    }

    /**
     * Records a failure for a provider/mirror.
     */
    fun recordFailure(
        providerId: String,
        domain: String? = null,
        failureType: FailureType,
        httpCode: Int = 0,
        errorMessage: String? = null,
        latencyMs: Long = 0L
    ) {
        val key = getKey(providerId, domain)
        val current = statsMap[key] ?: ProviderHealthStats(providerId = providerId, domain = domain ?: "")

        val newTotal = current.totalRequests + 1
        val newFailed = current.failedRequests + 1
        val newConsecutive = current.consecutiveFailures + 1
        val now = System.currentTimeMillis()

        // Determine quarantine
        val shouldQuarantine = newConsecutive >= CONSECUTIVE_FAILURE_THRESHOLD
        val cooldown = min(DEFAULT_QUARANTINE_COOLDOWN_MS * newConsecutive, MAX_QUARANTINE_COOLDOWN_MS)
        val quarantineExpires = if (shouldQuarantine) now + cooldown else 0L

        val newStatus = when {
            shouldQuarantine -> HealthStatus.QUARANTINED
            newConsecutive > 0 -> HealthStatus.DEGRADED
            else -> HealthStatus.HEALTHY
        }

        val updated = current.copy(
            totalRequests = newTotal,
            failedRequests = newFailed,
            consecutiveFailures = newConsecutive,
            lastFailureType = failureType,
            lastFailureCode = httpCode,
            lastFailureMessage = errorMessage,
            lastFailureTimestamp = now,
            quarantineExpiresAt = quarantineExpires,
            healthStatus = newStatus,
            healthScore = calculateHealthScore(newTotal, current.successfulRequests, newConsecutive, current.averageLatencyMs, newStatus)
        )

        statsMap[key] = updated
        Log.w(TAG, "Provider $key failure recorded: $failureType (HTTP $httpCode). Consecutive: $newConsecutive, Score: ${updated.healthScore}, Status: $newStatus")
    }

    /**
     * Checks if a provider/mirror is currently quarantined. If quarantine cooldown has passed, transitions to PROBING.
     */
    fun isQuarantined(providerId: String, domain: String? = null): Boolean {
        val key = getKey(providerId, domain)
        val stats = statsMap[key] ?: return false

        if (stats.healthStatus != HealthStatus.QUARANTINED) return false

        val now = System.currentTimeMillis()
        if (now >= stats.quarantineExpiresAt) {
            // Quarantine expired -> transition to PROBING
            statsMap[key] = stats.copy(healthStatus = HealthStatus.PROBING)
            Log.i(TAG, "Provider $key quarantine expired. Switched to PROBING state.")
            return false
        }

        return true
    }

    /**
     * Returns the computed health score (0 to 100) for a provider/domain.
     */
    fun getHealthScore(providerId: String, domain: String? = null): Int {
        val key = getKey(providerId, domain)
        val stats = statsMap[key] ?: return 100 // Default optimal for unknown

        // Check if quarantine expired
        if (stats.healthStatus == HealthStatus.QUARANTINED && System.currentTimeMillis() >= stats.quarantineExpiresAt) {
            return 50 // Recovery baseline for probing
        }

        return stats.healthScore
    }

    /**
     * Retrieves health statistics for a provider/domain.
     */
    fun getStats(providerId: String, domain: String? = null): ProviderHealthStats {
        val key = getKey(providerId, domain)
        return statsMap[key] ?: ProviderHealthStats(providerId = providerId, domain = domain ?: "")
    }

    /**
     * Returns all tracked health stats.
     */
    fun getAllStats(): List<ProviderHealthStats> = statsMap.values.toList()

    /**
     * Resets health state for a provider (e.g. user manually requests diagnostics reset).
     */
    fun resetHealth(providerId: String, domain: String? = null) {
        val key = getKey(providerId, domain)
        statsMap.remove(key)
    }

    private fun calculateHealthScore(
        total: Long,
        success: Long,
        consecutiveFailures: Int,
        latencyMs: Long,
        status: HealthStatus
    ): Int {
        if (total == 0L) return 100

        val successRate = (success.toDouble() / total.toDouble()) * 100.0
        var score = successRate.toInt()

        // Penalty for consecutive failures
        score -= (consecutiveFailures * 15)

        // Latency penalty
        if (latencyMs > 3000) {
            score -= 15
        } else if (latencyMs > 1500) {
            score -= 8
        }

        // Status adjustments
        when (status) {
            HealthStatus.QUARANTINED -> score = min(score, 10)
            HealthStatus.DEGRADED -> score = min(score, 65)
            HealthStatus.PROBING -> score = max(min(score, 50), 30)
            HealthStatus.HEALTHY -> {}
        }

        return max(0, min(100, score))
    }
}
