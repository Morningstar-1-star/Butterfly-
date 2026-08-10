package com.example.intelligence

import android.content.Context
import android.util.Log
import com.example.db.AppDatabase
import com.example.db.SourceMetricsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class ProviderIntelligenceReport(
    val providerId: String,
    val mediaType: String = "unknown",
    val quality: String = "auto",
    val networkResult: String = "SUCCESS",
    val startupLatencyMs: Long = 0L,
    val failureReason: String = "",
    val successRatePct: Float,
    val avgStartupTimeMs: Long,
    val bufferingEventsCount: Int,
    val crashCount: Int,
    val intelligenceScore: Double,
    val totalRequests: Int
)

class SourceIntelligenceEngine private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val metricsDao = db.sourceMetricsDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val liveReportsMap = ConcurrentHashMap<String, ProviderIntelligenceReport>()
    private val _reportsState = MutableStateFlow<Map<String, ProviderIntelligenceReport>>(emptyMap())
    val reportsState: StateFlow<Map<String, ProviderIntelligenceReport>> = _reportsState.asStateFlow()

    init {
        scope.launch {
            loadMetricsFromDb()
        }
    }

    private fun buildKey(providerId: String, mediaType: String, quality: String): String {
        return "$providerId:${mediaType.lowercase()}:${quality.lowercase()}"
    }

    private suspend fun loadMetricsFromDb() = withContext(Dispatchers.IO) {
        try {
            val list = metricsDao.getAllMetricsList()
            for (item in list) {
                liveReportsMap[item.metricKey] = calculateReport(item)
            }
            _reportsState.value = HashMap(liveReportsMap)
        } catch (e: Exception) {
            Log.e("SourceIntelligence", "Failed loading metrics: ${e.message}")
        }
    }

    fun recordRequestStart(
        providerId: String,
        mediaType: String = "unknown",
        quality: String = "auto"
    ) {
        val key = buildKey(providerId, mediaType, quality)
        scope.launch {
            val current = metricsDao.getMetricsByKey(key) ?: SourceMetricsEntity(
                metricKey = key,
                providerId = providerId,
                mediaType = mediaType,
                quality = quality
            )
            val updated = current.copy(
                totalRequests = current.totalRequests + 1,
                lastUpdated = System.currentTimeMillis()
            )
            metricsDao.insertOrUpdateMetrics(updated)
            updateMemoryAndState(updated)
        }
    }

    fun recordRequestSuccess(
        providerId: String,
        startupTimeMs: Long,
        mediaType: String = "unknown",
        quality: String = "auto",
        networkResult: String = "SUCCESS"
    ) {
        val key = buildKey(providerId, mediaType, quality)
        scope.launch {
            val current = metricsDao.getMetricsByKey(key) ?: SourceMetricsEntity(
                metricKey = key,
                providerId = providerId,
                mediaType = mediaType,
                quality = quality
            )
            val totalSuccess = current.successfulRequests + 1
            val newTotalTime = current.totalStartupTimeMs + startupTimeMs
            val updated = current.copy(
                successfulRequests = totalSuccess,
                totalStartupTimeMs = newTotalTime,
                networkResult = networkResult,
                startupLatencyMs = startupTimeMs,
                failureReason = "",
                lastUpdated = System.currentTimeMillis()
            )
            metricsDao.insertOrUpdateMetrics(updated)
            updateMemoryAndState(updated)
        }
    }

    fun recordRequestFailure(
        providerId: String,
        failureReason: String,
        mediaType: String = "unknown",
        quality: String = "auto",
        networkResult: String = "FAILED"
    ) {
        val key = buildKey(providerId, mediaType, quality)
        scope.launch {
            val current = metricsDao.getMetricsByKey(key) ?: SourceMetricsEntity(
                metricKey = key,
                providerId = providerId,
                mediaType = mediaType,
                quality = quality
            )
            val updated = current.copy(
                networkResult = networkResult,
                failureReason = failureReason,
                lastUpdated = System.currentTimeMillis()
            )
            metricsDao.insertOrUpdateMetrics(updated)
            updateMemoryAndState(updated)
        }
    }

    fun recordBufferingEvent(
        providerId: String,
        mediaType: String = "unknown",
        quality: String = "auto"
    ) {
        val key = buildKey(providerId, mediaType, quality)
        scope.launch {
            val current = metricsDao.getMetricsByKey(key) ?: SourceMetricsEntity(
                metricKey = key,
                providerId = providerId,
                mediaType = mediaType,
                quality = quality
            )
            val updated = current.copy(
                bufferingEventsCount = current.bufferingEventsCount + 1,
                lastUpdated = System.currentTimeMillis()
            )
            metricsDao.insertOrUpdateMetrics(updated)
            updateMemoryAndState(updated)
        }
    }

    fun recordPlaybackCrash(
        providerId: String,
        mediaType: String = "unknown",
        quality: String = "auto"
    ) {
        val key = buildKey(providerId, mediaType, quality)
        scope.launch {
            val current = metricsDao.getMetricsByKey(key) ?: SourceMetricsEntity(
                metricKey = key,
                providerId = providerId,
                mediaType = mediaType,
                quality = quality
            )
            val updated = current.copy(
                crashCount = current.crashCount + 1,
                lastUpdated = System.currentTimeMillis()
            )
            metricsDao.insertOrUpdateMetrics(updated)
            updateMemoryAndState(updated)
        }
    }

    fun getIntelligenceScore(
        providerId: String,
        mediaType: String = "unknown",
        quality: String = "auto"
    ): Double {
        val key = buildKey(providerId, mediaType, quality)
        val report = liveReportsMap[key] ?: liveReportsMap.values.firstOrNull { it.providerId == providerId }
        return report?.intelligenceScore ?: 75.0
    }

    fun getReport(providerId: String, mediaType: String = "unknown", quality: String = "auto"): ProviderIntelligenceReport? {
        val key = buildKey(providerId, mediaType, quality)
        return liveReportsMap[key] ?: liveReportsMap.values.firstOrNull { it.providerId == providerId }
    }

    private fun updateMemoryAndState(entity: SourceMetricsEntity) {
        val report = calculateReport(entity)
        liveReportsMap[entity.metricKey] = report
        _reportsState.value = HashMap(liveReportsMap)
    }

    private fun calculateReport(e: SourceMetricsEntity): ProviderIntelligenceReport {
        val total = maxOf(1, e.totalRequests)
        val successRate = (e.successfulRequests.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val avgStartup = if (e.successfulRequests > 0) e.totalStartupTimeMs / e.successfulRequests else 2000L

        val successComponent = successRate * 50.0
        val speedComponent = maxOf(0.0, 30.0 - (avgStartup / 100.0))
        val bufferPenalty = minOf(15.0, e.bufferingEventsCount * 1.5)
        val crashPenalty = minOf(25.0, e.crashCount * 5.0)
        val networkPenalty = if (e.networkResult != "SUCCESS" && e.networkResult != "UNKNOWN") 15.0 else 0.0

        val rawScore = successComponent + speedComponent - bufferPenalty - crashPenalty - networkPenalty
        val finalScore = (if (e.totalRequests == 0) 75.0 else rawScore).coerceIn(5.0, 100.0)

        return ProviderIntelligenceReport(
            providerId = e.providerId,
            mediaType = e.mediaType,
            quality = e.quality,
            networkResult = e.networkResult,
            startupLatencyMs = e.startupLatencyMs,
            failureReason = e.failureReason,
            successRatePct = successRate * 100f,
            avgStartupTimeMs = avgStartup,
            bufferingEventsCount = e.bufferingEventsCount,
            crashCount = e.crashCount,
            intelligenceScore = finalScore,
            totalRequests = e.totalRequests
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: SourceIntelligenceEngine? = null

        fun getInstance(context: Context): SourceIntelligenceEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = SourceIntelligenceEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
