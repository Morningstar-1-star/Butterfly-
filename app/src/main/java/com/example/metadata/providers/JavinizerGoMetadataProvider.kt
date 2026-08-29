package com.example.metadata.providers

import com.example.metadata.JavActor
import com.example.metadata.JavMetadata
import com.example.metadata.MetadataProvider
import com.example.metadata.ProviderClassification
import com.example.metadata.javinizer.JavinizerGoClient
import com.example.util.AppConfig

/**
 * Real Javinizer-Go (v1.5.1+) REST API Metadata Provider.
 * Connects directly to a running Javinizer-Go service instance over HTTP REST API.
 * Uses [JavinizerGoClient] to query documented REST endpoints.
 */
class JavinizerGoMetadataProvider(
    private val client: JavinizerGoClient = JavinizerGoClient()
) : MetadataProvider {

    companion object {
        data class JavinizerHealthResult(
            val isSuccess: Boolean,
            val latencyMs: Long,
            val serverVersion: String?,
            val message: String
        )
    }

    override val id: String = "javinizer_go"
    override val name: String = "Javinizer-Go (REST Service)"
    override val classification: ProviderClassification = ProviderClassification.API_ADAPTER
    override val priority: Int = 200 // Highest priority when enabled

    override val isEnabled: Boolean
        get() = AppConfig.isJavinizerEnabled()

    private fun getBaseUrl(): String = AppConfig.getJavinizerApiUrl()
    private fun getTimeoutSec(): Int = AppConfig.getJavinizerTimeoutSeconds()

    override suspend fun getMetadata(javCode: String): JavMetadata? {
        if (!isEnabled) return null
        return client.getMovieMetadata(
            javId = javCode,
            baseUrl = getBaseUrl(),
            timeoutSec = getTimeoutSec()
        )
    }

    override suspend fun search(query: String): List<JavMetadata> {
        if (!isEnabled) return emptyList()
        return client.search(
            query = query,
            baseUrl = getBaseUrl(),
            timeoutSec = getTimeoutSec()
        )
    }

    suspend fun getActressMetadata(name: String): JavActor? {
        if (!isEnabled) return null
        return client.getActress(
            name = name,
            baseUrl = getBaseUrl(),
            timeoutSec = getTimeoutSec()
        )
    }

    suspend fun testHealth(
        customBaseUrl: String? = null,
        customTimeoutSec: Int? = null
    ): JavinizerHealthResult {
        val targetUrl = customBaseUrl ?: getBaseUrl()
        val targetTimeout = customTimeoutSec ?: getTimeoutSec()
        val result = client.checkHealth(targetUrl, targetTimeout)
        return JavinizerHealthResult(
            isSuccess = result.isSuccess,
            latencyMs = result.latencyMs,
            serverVersion = result.serverVersion,
            message = result.statusMessage
        )
    }

    override suspend fun checkStatus(): Boolean {
        if (!isEnabled) return false
        val health = testHealth()
        return health.isSuccess
    }
}
