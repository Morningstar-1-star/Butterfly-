package com.example.plugin.manager

import com.example.model.StreamFailureReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

enum class ProviderHealthStatus {
    ALIVE,
    SLOW,
    OFFLINE,
    BLOCKED,
    MAINTENANCE
}

data class ProviderHealthRecord(
    val providerId: String,
    val providerName: String,
    val status: ProviderHealthStatus = ProviderHealthStatus.ALIVE,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val avgLatencyMs: Long = 0L,
    val lastFailureReason: StreamFailureReason? = null,
    val lastCheckedTimestamp: Long = System.currentTimeMillis()
)

class ProviderHealthMonitor {

    private val healthMap = ConcurrentHashMap<String, ProviderHealthRecord>()
    private val _healthState = MutableStateFlow<Map<String, ProviderHealthRecord>>(emptyMap())
    val healthState: StateFlow<Map<String, ProviderHealthRecord>> = _healthState.asStateFlow()

    fun registerProvider(providerId: String, name: String) {
        healthMap.putIfAbsent(
            providerId,
            ProviderHealthRecord(providerId = providerId, providerName = name)
        )
        syncState()
    }

    fun recordSuccess(providerId: String, latencyMs: Long) {
        val current = healthMap[providerId] ?: return
        val newSuccess = current.successCount + 1
        val newAvgLatency = if (current.avgLatencyMs == 0L) latencyMs else (current.avgLatencyMs + latencyMs) / 2
        val newStatus = if (newAvgLatency > 4000) ProviderHealthStatus.SLOW else ProviderHealthStatus.ALIVE

        healthMap[providerId] = current.copy(
            status = newStatus,
            successCount = newSuccess,
            consecutiveFailures = 0,
            avgLatencyMs = newAvgLatency,
            lastCheckedTimestamp = System.currentTimeMillis()
        )
        syncState()
    }

    fun recordFailure(providerId: String, reason: StreamFailureReason) {
        val current = healthMap[providerId] ?: return
        val newFailures = current.failureCount + 1
        val newConsecutive = current.consecutiveFailures + 1

        val newStatus = when {
            reason == StreamFailureReason.CLOUDFLARE_BLOCKED -> ProviderHealthStatus.BLOCKED
            reason == StreamFailureReason.HTTP_403_FORBIDDEN -> ProviderHealthStatus.BLOCKED
            newConsecutive >= 3 -> ProviderHealthStatus.OFFLINE
            newConsecutive >= 2 -> ProviderHealthStatus.SLOW
            else -> current.status
        }

        healthMap[providerId] = current.copy(
            status = newStatus,
            failureCount = newFailures,
            consecutiveFailures = newConsecutive,
            lastFailureReason = reason,
            lastCheckedTimestamp = System.currentTimeMillis()
        )
        syncState()
    }

    fun isProviderHealthy(providerId: String): Boolean {
        val record = healthMap[providerId] ?: return true
        return record.status != ProviderHealthStatus.OFFLINE && record.status != ProviderHealthStatus.BLOCKED
    }

    fun getRecord(providerId: String): ProviderHealthRecord? = healthMap[providerId]

    fun resetHealth(providerId: String) {
        val current = healthMap[providerId] ?: return
        healthMap[providerId] = current.copy(
            status = ProviderHealthStatus.ALIVE,
            consecutiveFailures = 0,
            lastFailureReason = null
        )
        syncState()
    }

    private fun syncState() {
        _healthState.value = HashMap(healthMap)
    }
}
