package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        .dns(Dns.SYSTEM)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val defaultDohDns: Dns by lazy {
        try {
            val cf1 = InetAddress.getByAddress("1.1.1.1", byteArrayOf(1, 1, 1, 1))
            val cf2 = InetAddress.getByAddress("1.0.0.1", byteArrayOf(1, 0, 0, 1))
            val gg1 = InetAddress.getByAddress("8.8.8.8", byteArrayOf(8, 8, 8, 8))
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrlOrNull()!!)
                .bootstrapDnsHosts(listOf(cf1, cf2, gg1))
                .includeIPv6(false)
                .build()
        } catch (_: Throwable) {
            Dns.SYSTEM
        }
    }

    private val dnsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    private const val DNS_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes TTL

    fun isValidPublicIp(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress || addr.isMulticastAddress
        ) {
            return false
        }
        val bytes = addr.address
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            // Reject 0.x.x.x, 10.x.x.x, 127.x.x.x, 100.64.0.0/10 (CGNAT/sinkhole), 198.18.0.0/15 (Fake-IP / benchmark)
            if (b0 == 0 || b0 == 10 || b0 == 127) return false
            if (b0 == 100 && (b1 in 64..127)) return false
            if (b0 == 169 && b1 == 254) return false
            if (b0 == 172 && (b1 in 16..31)) return false
            if (b0 == 192 && b1 == 168) return false
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false
            if (b0 >= 224) return false
        }
        return true
    }

    fun prioritizeIpv4(addresses: List<InetAddress>): List<InetAddress> {
        val valid = addresses.filter { isValidPublicIp(it) }
        val pool = if (valid.isNotEmpty()) valid else addresses
        if (pool.isEmpty()) return pool
        val v4 = pool.filterIsInstance<java.net.Inet4Address>()
        val v6 = pool.filterIsInstance<java.net.Inet6Address>()
        return if (v4.isNotEmpty()) v4 + v6 else pool
    }

    private fun resolveViaDohJson(hostname: String): List<InetAddress> {
        val endpoints = listOf(
            "https://1.1.1.1/dns-query?name=$hostname&type=A",
            "https://8.8.8.8/resolve?name=$hostname&type=A"
        )
        for (url in endpoints) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()
                bootstrapClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = org.json.JSONObject(body)
                            val answers = json.optJSONArray("Answer")
                            if (answers != null && answers.length() > 0) {
                                val resolved = mutableListOf<InetAddress>()
                                for (i in 0 until answers.length()) {
                                    val ans = answers.optJSONObject(i) ?: continue
                                    if (ans.optInt("type") == 1) { // A record (IPv4)
                                        val ipStr = ans.optString("data", "").trim()
                                        if (ipStr.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
                                            val parts = ipStr.split(".").map { it.toInt().toByte() }.toByteArray()
                                            val addr = InetAddress.getByAddress(hostname, parts)
                                            if (isValidPublicIp(addr)) {
                                                resolved.add(addr)
                                            }
                                        }
                                    }
                                }
                                if (resolved.isNotEmpty()) return resolved
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        return emptyList()
    }

    val bilibiliDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()
            dnsCache[hostname]?.let { (cachedAt, ips) ->
                if (now - cachedAt < DNS_CACHE_TTL_MS && ips.isNotEmpty()) {
                    return ips
                }
            }

            val combined = mutableListOf<InetAddress>()
            val activeDoh = if (delegateDns != Dns.SYSTEM) delegateDns else defaultDohDns

            // 1. Resolve via DoH first so mobile ISP DNS poisoning/sinkholing on bilibili/bilivideo/hdslb is bypassed
            val dohResult = try {
                prioritizeIpv4(activeDoh.lookup(hostname))
            } catch (_: Exception) {
                emptyList()
            }
            combined.addAll(dohResult)

            // 2. If DoH returned empty, try direct IP-literal DoH JSON query (1.1.1.1 / 8.8.8.8)
            if (combined.isEmpty()) {
                val jsonDoh = resolveViaDohJson(hostname)
                combined.addAll(jsonDoh)
            }

            // 3. Also query System DNS (filtered for valid public IPs) and append distinct IPs as fallback routes
            val sysResult = try {
                prioritizeIpv4(Dns.SYSTEM.lookup(hostname).filter { isValidPublicIp(it) })
            } catch (_: Exception) {
                emptyList()
            }
            for (addr in sysResult) {
                if (combined.none { it.hostAddress == addr.hostAddress }) {
                    combined.add(addr)
                }
            }

            val finalIps = prioritizeIpv4(combined)
            if (finalIps.isNotEmpty()) {
                dnsCache[hostname] = Pair(now, finalIps)
                return finalIps
            }

            // 4. Last-resort fallback lookup
            val fallback = prioritizeIpv4(fallbackLookup(hostname))
            if (fallback.isNotEmpty()) {
                dnsCache[hostname] = Pair(now, fallback)
            }
            return fallback
        }
    }

    val appDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val lowerHost = hostname.lowercase()
            if (lowerHost.contains("bilibili") || lowerHost.contains("bilivideo") ||
                lowerHost.contains("hdslb") || lowerHost.contains("szbdyd") ||
                lowerHost.contains("mcdn") || lowerHost.contains("upgcxcode") ||
                lowerHost.contains("upos") || lowerHost.contains("bcache") ||
                lowerHost.contains("biliapi") || lowerHost.contains("mirrorakam") ||
                lowerHost.contains("akamaized") || lowerHost.contains("bstar") ||
                lowerHost.contains("biliintl") ||
                lowerHost.contains("qq.com") || lowerHost.contains("tencent") ||
                lowerHost.contains("qpic.cn") || lowerHost.contains("gtimg.com") ||
                lowerHost.contains("myqcloud.com") || lowerHost.contains("wetv.vip") ||
                lowerHost.contains("wetvinfo.com")
            ) {
                return bilibiliDns.lookup(hostname)
            }

            val now = System.currentTimeMillis()
            dnsCache[hostname]?.let { (cachedAt, ips) ->
                if (now - cachedAt < DNS_CACHE_TTL_MS && ips.isNotEmpty()) {
                    return ips
                }
            }

            val primaryDns = if (delegateDns != Dns.SYSTEM) delegateDns else defaultDohDns
            val resolved = try {
                val results = prioritizeIpv4(primaryDns.lookup(hostname))
                if (results.isNotEmpty()) results else fallbackLookup(hostname)
            } catch (e: Exception) {
                Log.w(TAG, "Primary DoH lookup failed for $hostname (${e.message}), falling back to system DNS")
                fallbackLookup(hostname)
            }

            if (resolved.isNotEmpty()) {
                dnsCache[hostname] = Pair(now, resolved)
            }
            return resolved
        }
    }

    private fun fallbackLookup(hostname: String): List<InetAddress> {
        val dohJson = resolveViaDohJson(hostname)
        if (dohJson.isNotEmpty()) return prioritizeIpv4(dohJson)

        return try {
            val sys = prioritizeIpv4(Dns.SYSTEM.lookup(hostname).filter { isValidPublicIp(it) })
            if (sys.isNotEmpty()) sys else throw UnknownHostException("Empty valid system DNS for $hostname")
        } catch (e: Exception) {
            try {
                val activeDoh = if (delegateDns != Dns.SYSTEM) delegateDns else defaultDohDns
                val doh = prioritizeIpv4(activeDoh.lookup(hostname))
                if (doh.isNotEmpty()) doh else prioritizeIpv4(InetAddress.getAllByName(hostname).toList())
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
                dnsCache.clear()
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

    private fun pingHost(ip: String, port: Int = 443, timeoutMs: Int = 1200): Long {
        val start = System.nanoTime()
        return try {
            java.net.Socket().use { sock ->
                sock.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
            }
            ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Exception) {
            try {
                val start53 = System.nanoTime()
                java.net.Socket().use { sock ->
                    sock.connect(java.net.InetSocketAddress(ip, 53), timeoutMs)
                }
                ((System.nanoTime() - start53) / 1_000_000L).coerceAtLeast(1L)
            } catch (_: Exception) {
                -1L
            }
        }
    }

    suspend fun measureAllProviderLatencies(context: Context): Map<DnsProvider, Long> = withContext(Dispatchers.IO) {
        val results = java.util.concurrent.ConcurrentHashMap<DnsProvider, Long>()
        val providers = DnsProvider.values()

        kotlinx.coroutines.coroutineScope {
            providers.map { provider ->
                async {
                    val startNano = System.nanoTime()
                    val latency = try {
                        when (provider) {
                            DnsProvider.SYSTEM -> {
                                val ips = try {
                                    InetAddress.getAllByName("google.com")
                                } catch (_: Exception) {
                                    emptyArray<InetAddress>()
                                }
                                val elapsedMs = (System.nanoTime() - startNano) / 1_000_000L
                                if (ips.isNotEmpty()) elapsedMs.coerceAtLeast(1L) else pingHost("8.8.8.8")
                            }
                            DnsProvider.CUSTOM -> {
                                val prefs = SecureDnsPreferences.getInstance(context)
                                val customUrl = prefs.customDnsUrl.value
                                if (customUrl.isBlank()) -1L
                                else {
                                    val req = okhttp3.Request.Builder()
                                        .url(if (customUrl.contains("?")) "$customUrl&name=google.com&type=A" else "$customUrl?name=google.com&type=A")
                                        .header("Accept", "application/dns-json")
                                        .build()
                                    bootstrapClient.newCall(req).execute().use { resp ->
                                        val elapsedMs = (System.nanoTime() - startNano) / 1_000_000L
                                        if (resp.isSuccessful) elapsedMs.coerceAtLeast(1L) else -1L
                                    }
                                }
                            }
                            else -> {
                                val testUrl = when (provider) {
                                    DnsProvider.CLOUDFLARE -> "https://cloudflare-dns.com/dns-query?name=google.com&type=A"
                                    DnsProvider.GOOGLE -> "https://dns.google/resolve?name=google.com&type=A"
                                    DnsProvider.OPENDNS -> "https://doh.opendns.com/dns-query?name=google.com&type=A"
                                    DnsProvider.ADGUARD -> "https://dns.adguard-dns.com/dns-query?name=google.com&type=A"
                                    DnsProvider.QUAD9 -> "https://dns.quad9.net/dns-query?name=google.com&type=A"
                                    DnsProvider.CLEANBROWSING -> "https://doh.cleanbrowsing.org/doh/family-filter/?name=google.com&type=A"
                                    else -> provider.url
                                }
                                val req = okhttp3.Request.Builder()
                                    .url(testUrl)
                                    .header("Accept", "application/dns-json")
                                    .build()
                                bootstrapClient.newCall(req).execute().use { resp ->
                                    val elapsedMs = (System.nanoTime() - startNano) / 1_000_000L
                                    if (resp.isSuccessful) {
                                        elapsedMs.coerceAtLeast(1L)
                                    } else {
                                        val bootstrapIp = provider.bootstrapIps.firstOrNull() ?: "8.8.8.8"
                                        pingHost(bootstrapIp)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        val bootstrapIp = provider.bootstrapIps.firstOrNull() ?: if (provider == DnsProvider.SYSTEM) "8.8.8.8" else null
                        if (bootstrapIp != null) pingHost(bootstrapIp) else -1L
                    }
                    if (latency > 0) {
                        results[provider] = latency
                    }
                }
            }.awaitAll()
        }
        results
    }
}
