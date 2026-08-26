package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class DnsTestResult(
    val isSuccess: Boolean,
    val providerName: String,
    val resolvedIps: List<String>,
    val latencyMs: Long,
    val errorMessage: String? = null
)

object SecureDnsManager {

    private const val TAG = "SecureDnsManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var delegateDns: Dns = Dns.SYSTEM

    @Volatile
    private var isInitialized = false

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val appDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val primaryDns = delegateDns
            return try {
                val results = primaryDns.lookup(hostname)
                if (results.isNotEmpty()) results else fallbackLookup(hostname)
            } catch (e: Exception) {
                Log.w(TAG, "Primary DoH lookup failed for $hostname (${e.message}), falling back to system DNS")
                fallbackLookup(hostname)
            }
        }
    }

    private fun fallbackLookup(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            try {
                InetAddress.getAllByName(hostname).toList()
            } catch (fallbackError: Exception) {
                throw UnknownHostException("Unable to resolve host $hostname: ${e.message}")
            }
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        update(context)
    }

    fun update(context: Context) {
        scope.launch {
            try {
                val prefs = SecureDnsPreferences.getInstance(context)
                if (!prefs.isSecureDnsEnabled.value) {
                    delegateDns = Dns.SYSTEM
                    Log.i(TAG, "Secure DNS disabled. Using System DNS.")
                    return@launch
                }

                val provider = prefs.selectedProvider.value
                if (provider == DnsProvider.SYSTEM) {
                    delegateDns = Dns.SYSTEM
                    Log.i(TAG, "Using System Default DNS.")
                    return@launch
                }

                val urlStr = if (provider == DnsProvider.CUSTOM) prefs.customDnsUrl.value else provider.url
                if (urlStr.isBlank()) {
                    delegateDns = Dns.SYSTEM
                    return@launch
                }

                val httpUrl = urlStr.toHttpUrlOrNull()
                if (httpUrl == null) {
                    Log.e(TAG, "Invalid DoH URL: $urlStr")
                    delegateDns = Dns.SYSTEM
                    return@launch
                }

                val dohBuilder = DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url(httpUrl)
                    .includeIPv6(false)

                if (provider.bootstrapIps.isNotEmpty()) {
                    val hosts = provider.bootstrapIps.mapNotNull { ip ->
                        try {
                            InetAddress.getByName(ip)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (hosts.isNotEmpty()) {
                        dohBuilder.bootstrapDnsHosts(hosts)
                    }
                }

                val newDoh = dohBuilder.build()
                delegateDns = newDoh
                Log.i(TAG, "Secure DNS updated to: ${provider.displayName} ($urlStr)")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to build DoH client: ${e.message}", e)
                delegateDns = Dns.SYSTEM
            }
        }
    }

    suspend fun testDnsResolution(context: Context, testHostname: String = "pornhub.com"): DnsTestResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val prefs = SecureDnsPreferences.getInstance(context)
            val providerName = if (prefs.isSecureDnsEnabled.value) {
                prefs.selectedProvider.value.displayName
            } else {
                "System Default (ISP)"
            }

            try {
                val ips = appDns.lookup(testHostname)
                val latency = System.currentTimeMillis() - startTime
                if (ips.isNotEmpty()) {
                    DnsTestResult(
                        isSuccess = true,
                        providerName = providerName,
                        resolvedIps = ips.map { it.hostAddress ?: "" },
                        latencyMs = latency
                    )
                } else {
                    DnsTestResult(
                        isSuccess = false,
                        providerName = providerName,
                        resolvedIps = emptyList(),
                        latencyMs = latency,
                        errorMessage = "No IP addresses returned"
                    )
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                DnsTestResult(
                    isSuccess = false,
                    providerName = providerName,
                    resolvedIps = emptyList(),
                    latencyMs = latency,
                    errorMessage = e.message ?: "Resolution error"
                )
            }
        }
    }
}
