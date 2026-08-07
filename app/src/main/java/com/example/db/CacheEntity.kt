package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "response_cache")
data class ResponseCacheEntity(
    @PrimaryKey val key: String,
    val responseBody: String,
    val statusCode: Int = 200,
    val eTag: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val ttlMs: Long = 300_000L // Default 5 minutes
)

@Entity(tableName = "source_metrics")
data class SourceMetricsEntity(
    @PrimaryKey val providerId: String,
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val totalStartupTimeMs: Long = 0L,
    val bufferingEventsCount: Int = 0,
    val crashCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
