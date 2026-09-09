package com.example.extractor.sextb

import android.webkit.CookieManager
import com.example.util.SecureDnsManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Shared network infrastructure for the authoritative SEXТB provider, resolver, and extractors.
 * Maintains session cookies and bridges seamlessly with Android WebKit CookieManager.
 */
object SextbNetwork {

    private const val TAG = "SextbNetwork"

    val cookieJar = object : CookieJar {
        private val memoryStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val domainMap = memoryStore.getOrPut(host) { ConcurrentHashMap() }
            for (cookie in cookies) {
                domainMap[cookie.name] = cookie
            }
            // Sync to WebKit CookieManager if available
            try {
                val cm = CookieManager.getInstance()
                for (c in cookies) {
                    val cookieStr = "${c.name}=${c.value}; Domain=${c.domain}; Path=${c.path}"
                    cm.setCookie(url.toString(), cookieStr)
                }
            } catch (_: Exception) {}
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val matched = mutableListOf<Cookie>()
            val now = System.currentTimeMillis()

            // 1. In-memory cookies
            val host = url.host
            for ((savedHost, map) in memoryStore) {
                if (host == savedHost || host.endsWith(".$savedHost") || savedHost.endsWith(".$host")) {
                    for (c in map.values) {
                        if (c.expiresAt > now) {
                            matched.add(c)
                        }
                    }
                }
            }

            // 2. Sync from WebKit CookieManager
            try {
                val cm = CookieManager.getInstance()
                val webKitCookieStr = cm.getCookie(url.toString())
                if (!webKitCookieStr.isNullOrBlank()) {
                    val pairs = webKitCookieStr.split(";")
                    for (pair in pairs) {
                        val parts = pair.trim().split("=", limit = 2)
                        if (parts.size == 2) {
                            val name = parts[0].trim()
                            val value = parts[1].trim()
                            if (name.isNotBlank() && matched.none { it.name == name }) {
                                matched.add(
                                    Cookie.Builder()
                                        .name(name)
                                        .value(value)
                                        .domain(host)
                                        .path("/")
                                        .build()
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            return matched
        }

        fun clear() {
            memoryStore.clear()
        }
    }

    private val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
        object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
    )

    private val sslContext: javax.net.ssl.SSLContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, java.security.SecureRandom())
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .dns(SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
