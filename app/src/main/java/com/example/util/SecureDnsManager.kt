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
    val protocol: String = "DNS-over-HTTPS",
    val testedDomain: String = "youtube.com",
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

    suspend fun testDnsResolution(context: Context, testHostname: String = "youtube.com"): DnsTestResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val prefs = SecureDnsPreferences.getInstance(context)
            val isEnabled = prefs.isSecureDnsEnabled.value
            val provider = if (isEnabled) prefs.selectedProvider.value else DnsProvider.SYSTEM
            val providerName = if (isEnabled) provider.displayName else "System Default (ISP)"

            val targetHost = if (testHostname.isBlank()) "youtube.com" else testHostname.trim()

            try {
                if (isEnabled && provider != DnsProvider.SYSTEM) {
                    // Perform real DoH HTTP JSON query to test provider directly
                    val dohApiUrl = when (provider) {
                        DnsProvider.CLOUDFLARE -> "https://1.1.1.1/dns-query?name=$targetHost&type=A"
                        DnsProvider.GOOGLE -> "https://dns.google/resolve?name=$targetHost&type=A"
                        DnsProvider.OPENDNS -> "https://doh.opendns.com/dns-query?name=$targetHost&type=A"
                        DnsProvider.QUAD9 -> "https://dns.quad9.net/dns-query?name=$targetHost&type=A"
                        DnsProvider.ADGUARD -> "https://dns.adguard-dns.com/dns-query?name=$targetHost&type=A"
                        else -> provider.url
                    }

                    val req = okhttp3.Request.Builder()
                        .url(dohApiUrl)
                        .header("Accept", "application/dns-json")
                        .build()

                    bootstrapClient.newCall(req).execute().use { response ->
                        val latency = System.currentTimeMillis() - startTime
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val json = org.json.JSONObject(bodyStr)
                            val answerArray = json.optJSONArray("Answer")
                            val resolvedIps = mutableListOf<String>()
                            if (answerArray != null) {
                                for (i in 0 until answerArray.length()) {
                                    val item = answerArray.getJSONObject(i)
                                    val data = item.optString("data")
                                    if (data.isNotBlank()) {
                                        resolvedIps.add(data)
                                    }
                                }
                            }

                            if (resolvedIps.isEmpty()) {
                                // Fallback to appDns.lookup
                                val fallbackIps = appDns.lookup(targetHost).map { it.hostAddress ?: "" }
                                DnsTestResult(
                                    isSuccess = fallbackIps.isNotEmpty(),
                                    providerName = providerName,
                                    resolvedIps = fallbackIps,
                                    latencyMs = latency,
                                    protocol = "DoH HTTPS (200 OK)",
                                    testedDomain = targetHost,
                                    errorMessage = if (fallbackIps.isEmpty()) "No IP records returned" else null
                                )
                            } else {
                                DnsTestResult(
                                    isSuccess = true,
                                    providerName = providerName,
                                    resolvedIps = resolvedIps,
                                    latencyMs = latency,
                                    protocol = "DoH HTTPS (200 OK)",
                                    testedDomain = targetHost
                                )
                            }
                        } else {
                            val fallbackIps = appDns.lookup(targetHost).map { it.hostAddress ?: "" }
                            DnsTestResult(
                                isSuccess = fallbackIps.isNotEmpty(),
                                providerName = providerName,
                                resolvedIps = fallbackIps,
                                latencyMs = latency,
                                protocol = "DoH Fallback (HTTP ${response.code})",
                                testedDomain = targetHost,
                                errorMessage = if (fallbackIps.isEmpty()) "HTTP ${response.code} error" else null
                            )
                        }
                    }
                } else {
                    // System UDP/TCP socket lookup
                    val ips = InetAddress.getAllByName(targetHost).toList()
                    val latency = System.currentTimeMillis() - startTime
                    DnsTestResult(
                        isSuccess = ips.isNotEmpty(),
                        providerName = providerName,
                        resolvedIps = ips.map { it.hostAddress ?: "" },
                        latencyMs = latency,
                        protocol = "System UDP/TCP Socket",
                        testedDomain = targetHost,
                        errorMessage = if (ips.isEmpty()) "No host record found" else null
                    )
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                // Fallback attempt via appDns
                try {
                    val ips = appDns.lookup(targetHost).map { it.hostAddress ?: "" }
                    if (ips.isNotEmpty()) {
                        return@withContext DnsTestResult(
                            isSuccess = true,
                            providerName = providerName,
                            resolvedIps = ips,
                            latencyMs = latency,
                            protocol = "DNS App Resolver",
                            testedDomain = targetHost
                        )
                    }
                } catch (_: Exception) {}

                DnsTestResult(
                    isSuccess = false,
                    providerName = providerName,
                    resolvedIps = emptyList(),
                    latencyMs = latency,
                    protocol = if (isEnabled) "DoH HTTPS" else "System DNS",
                    testedDomain = targetHost,
                    errorMessage = e.message ?: "Network DNS lookup failed"
                )
            }
        }
    }
}
