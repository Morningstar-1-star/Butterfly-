package com.example.util

import android.util.Log
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Centralized NetworkManager for Butterfly.
 * Provides unified, shared OkHttpClient profiles with managed connection pools,
 * thread dispatchers, DNS-over-HTTPS integration, and request helpers.
 */
object NetworkManager {

    private const val TAG = "NetworkManager"

    // Default Browser User-Agent string
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // Shared connection pool across standard clients to reuse TCP & TLS sockets
    private val sharedConnectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

    // Shared dispatcher with controlled concurrency
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 10
    }

    // Default logging / diagnostic interceptor
    private val commonHeaderInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        if (original.header("User-Agent").isNullOrBlank()) {
            builder.header("User-Agent", DEFAULT_USER_AGENT)
        }
        if (original.header("Accept-Language").isNullOrBlank()) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
        }

        chain.proceed(builder.build())
    }

    /**
     * 1. Default Client: Fast general-purpose client for standard web calls.
     */
    val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SecureDnsManager.appDns)
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(commonHeaderInterceptor)
            .build()
    }

    /**
     * 2. Scraper Client: Optimized for web scrapers, HTML parsers, and multi-source extractors.
     */
    val scraperClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SecureDnsManager.appDns)
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor(commonHeaderInterceptor)
            .build()
    }

    /**
     * 3. API Client: Strict, fast client for structured JSON/REST APIs (e.g. TMDB, Subtitles, SmartSkip).
     */
    val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SecureDnsManager.appDns)
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * 4. Media Client: Tuned for media streaming, video buffering, and large segment downloads.
     */
    val mediaClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SecureDnsManager.appDns)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Helper to execute a simple HTTP GET request and return the body string.
     */
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        client: OkHttpClient = defaultClient
    ): String? {
        return try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET error for $url: ${e.message}")
            null
        }
    }

    /**
     * Builds a Request with optional custom headers, referer, cookies, and user agent.
     */
    fun buildRequest(
        url: String,
        headers: Map<String, String>? = null,
        referer: String? = null,
        cookie: String? = null,
        userAgent: String? = null
    ): Request {
        val builder = Request.Builder().url(url)
        headers?.forEach { (k, v) -> builder.header(k, v) }
        referer?.let { builder.header("Referer", it) }
        cookie?.let { builder.header("Cookie", it) }
        userAgent?.let { builder.header("User-Agent", it) }
        return builder.build()
    }
}
