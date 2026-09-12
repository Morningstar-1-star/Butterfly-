package com.example.extractor.supjav

import android.util.Log
import com.example.util.SecureDnsManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * High-performance network stack for SupJav scrapers and stream resolvers.
 */
object SupJavNetwork {

    private const val TAG = "SupJavNetwork"

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val existing = cookieStore.getOrPut(host) { mutableListOf() }
            synchronized(existing) {
                cookies.forEach { newCookie ->
                    existing.removeAll { it.name == newCookie.name }
                    existing.add(newCookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val list = mutableListOf<Cookie>()
            cookieStore[host]?.let {
                synchronized(it) { list.addAll(it) }
            }
            // Also include root domain cookies (e.g., .supjav.com for subdomains)
            val parts = host.split(".")
            if (parts.size >= 2) {
                val rootDomain = parts.takeLast(2).joinToString(".")
                cookieStore[rootDomain]?.let {
                    synchronized(it) { list.addAll(it) }
                }
            }
            return list
        }
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SecureDnsManager.appDns)
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()

                if (original.header("User-Agent") == null) {
                    requestBuilder.header("User-Agent", DEFAULT_USER_AGENT)
                }
                if (original.header("Accept") == null) {
                    requestBuilder.header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
                    )
                }
                if (original.header("Accept-Language") == null) {
                    requestBuilder.header("Accept-Language", "en-US,en;q=0.9,ja;q=0.8")
                }
                if (original.header("Sec-Ch-Ua") == null) {
                    requestBuilder.header("Sec-Ch-Ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                }
                if (original.header("Sec-Ch-Ua-Mobile") == null) {
                    requestBuilder.header("Sec-Ch-Ua-Mobile", "?0")
                }
                if (original.header("Sec-Ch-Ua-Platform") == null) {
                    requestBuilder.header("Sec-Ch-Ua-Platform", "\"Windows\"")
                }
                if (original.header("Referer") == null) {
                    requestBuilder.header("Referer", "${original.url.scheme}://${original.url.host}/")
                }
                if (original.header("Sec-Fetch-Dest") == null) {
                    requestBuilder.header("Sec-Fetch-Dest", "document")
                }
                if (original.header("Sec-Fetch-Mode") == null) {
                    requestBuilder.header("Sec-Fetch-Mode", "navigate")
                }
                if (original.header("Sec-Fetch-Site") == null) {
                    requestBuilder.header("Sec-Fetch-Site", "cross-site")
                }

                chain.proceed(requestBuilder.build())
            }
            .build()
    }
}
