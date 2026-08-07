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

    private suspend fun loadMetricsFromDb() = withContext(Dispatchers.IO) {
        try {
            val list = metricsDao.getAllMetricsList()
            for (item in list) {
                liveReportsMap[item.providerId] = calculateReport(item)
            }
            _reportsState.value = HashMap(liveReportsMap)
        } catch (e: Exception) {
            Log.e("SourceIntelligence", "Failed loading metrics: ${e.message}")
        }
    }

    fun recordRequestStart(providerId: String) {
        scope.launch {
            val current = metricsDao.getMetricsForProvider(providerId) ?: SourceMetricsEntity(providerId = providerId)
            val updated = current.copy(
                totalRequests = current.totalRequests + 1,
                lastUpdated = System.currentTimeMillis()
            )
            metricsDao.insertOrUpdateMetrics(updated)
            updateMemoryAndState(updated)
        }
    }

    fun recordRequestSuccess(providerId: String, startupTimeMs: Long) {
        scope.launch {
            val current = metricsDao.getMetricsForProvider(providerId) ?: SourceMetricsEntity(providerId = providerId)
            val totalSuccess = current.successfulRequests + 1
            val newTotalTime = current.totalStartupTimeMs + startupTimeMs
            val updated = current.copy(
                successfulRequests = totalSuccess,
                totalStartupTimeMs = newTotalTime,
                lastUpdated = System.currentTimeMillis()
            )
            metricsDao.insertOrUpdateMetrics(updated)
            updateMemoryAndState(updated)
        }
    }

    fun recordBufferingEvent(providerId: String) {
        scope.launch {
            val current = metricsDao.getMetricsForProvider(providerId) ?: SourceMetricsEntity(providerId = providerId)
            val updated = current.copy(
                bufferingEventsCount = current.bufferingEventsCount + 1,
                lastUpdated = System.currentTimeMillis()
            )
            metricsDao.insertOrUpdateMetrics(updated)
            updateMemoryAndState(updated)
        }
    }

    fun recordPlaybackCrash(providerId: String) {
        scope.launch {
            val current = metricsDao.getMetricsForProvider(providerId) ?: SourceMetricsEntity(providerId = providerId)
            val updated = current.copy(
                crashCount = current.crashCount + 1,
                lastUpdated = System.currentTimeMillis()
            )
            metricsDao.insertOrUpdateMetrics(updated)
            updateMemoryAndState(updated)
        }
    }

    fun getIntelligenceScore(providerId: String): Double {
        val report = liveReportsMap[providerId] ?: return 75.0 // Neutral default score
        return report.intelligenceScore
    }

    fun getReport(providerId: String): ProviderIntelligenceReport? = liveReportsMap[providerId]

    private fun updateMemoryAndState(entity: SourceMetricsEntity) {
        val report = calculateReport(entity)
        liveReportsMap[entity.providerId] = report
        _reportsState.value = HashMap(liveReportsMap)
    }

    private fun calculateReport(e: SourceMetricsEntity): ProviderIntelligenceReport {
        val total = maxOf(1, e.totalRequests)
        val successRate = (e.successfulRequests.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val avgStartup = if (e.successfulRequests > 0) e.totalStartupTimeMs / e.successfulRequests else 2000L

        // Score formula:
        // Success Weight (0 to 50 pts)
        val successComponent = successRate * 50.0
        // Speed Weight (0 to 30 pts) - 0ms gives 30pts, >3000ms gives 0pts
        val speedComponent = maxOf(0.0, 30.0 - (avgStartup / 100.0))
        // Buffering Penalty (0 to 15 pts)
        val bufferPenalty = minOf(15.0, e.bufferingEventsCount * 1.5)
        // Crash Penalty (0 to 25 pts)
        val crashPenalty = minOf(25.0, e.crashCount * 5.0)

        val rawScore = successComponent + speedComponent - bufferPenalty - crashPenalty
        val finalScore = (if (e.totalRequests == 0) 75.0 else rawScore).coerceIn(5.0, 100.0)

        return ProviderIntelligenceReport(
            providerId = e.providerId,
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
