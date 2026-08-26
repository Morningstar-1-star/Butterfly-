package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DnsProvider(
    val id: String,
    val displayName: String,
    val url: String,
    val bootstrapIps: List<String>,
    val description: String
) {
    SYSTEM(
        id = "system",
        displayName = "System Default (ISP)",
        url = "",
        bootstrapIps = emptyList(),
        description = "Uses your mobile operator or Wi-Fi provider's standard DNS."
    ),
    CLOUDFLARE(
        id = "cloudflare",
        displayName = "Cloudflare (1.1.1.1)",
        url = "https://cloudflare-dns.com/dns-query",
        bootstrapIps = listOf("1.1.1.1", "1.0.0.1"),
        description = "Fast, private encrypted DNS. Bypasses ISP domain blocks."
    ),
    GOOGLE(
        id = "google",
        displayName = "Google Public DNS",
        url = "https://dns.google/dns-query",
        bootstrapIps = listOf("8.8.8.8", "8.8.4.4"),
        description = "Reliable global DoH service provided by Google."
    ),
    OPENDNS(
        id = "opendns",
        displayName = "OpenDNS",
        url = "https://doh.opendns.com/dns-query",
        bootstrapIps = listOf("208.67.222.222", "208.67.220.220"),
        description = "Cisco OpenDNS secure encrypted resolution."
    ),
    ADGUARD(
        id = "adguard",
        displayName = "AdGuard DNS",
        url = "https://dns.adguard-dns.com/dns-query",
        bootstrapIps = listOf("94.140.14.14", "94.140.15.15"),
        description = "Blocks tracking servers and ad domains at DNS level."
    ),
    QUAD9(
        id = "quad9",
        displayName = "Quad9 Security",
        url = "https://dns.quad9.net/dns-query",
        bootstrapIps = listOf("9.9.9.9", "149.112.112.112"),
        description = "Blocks malicious domains and phishing sites."
    ),
    CLEANBROWSING(
        id = "cleanbrowsing",
        displayName = "CleanBrowsing",
        url = "https://doh.cleanbrowsing.org/doh/family-filter/",
        bootstrapIps = listOf("185.228.168.168", "185.228.169.168"),
        description = "Family-safe DNS filtering."
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom Service Provider",
        url = "",
        bootstrapIps = emptyList(),
        description = "Enter your custom DNS-over-HTTPS endpoint URL."
    );

    companion object {
        fun fromId(id: String): DnsProvider {
            return values().find { it.id.equals(id, ignoreCase = true) } ?: CLOUDFLARE
        }
    }
}

class SecureDnsPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSecureDnsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SECURE_DNS_ENABLED, true)
    )
    val isSecureDnsEnabled: StateFlow<Boolean> = _isSecureDnsEnabled.asStateFlow()

    private val _selectedProvider = MutableStateFlow(
        DnsProvider.fromId(prefs.getString(KEY_SELECTED_PROVIDER, DnsProvider.CLOUDFLARE.id) ?: DnsProvider.CLOUDFLARE.id)
    )
    val selectedProvider: StateFlow<DnsProvider> = _selectedProvider.asStateFlow()

    private val _customDnsUrl = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_DNS_URL, "https://dns.nextdns.io/doh") ?: "https://dns.nextdns.io/doh"
    )
    val customDnsUrl: StateFlow<String> = _customDnsUrl.asStateFlow()

    fun setSecureDnsEnabled(enabled: Boolean) {
        _isSecureDnsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SECURE_DNS_ENABLED, enabled).apply()
    }

    fun setSelectedProvider(provider: DnsProvider) {
        _selectedProvider.value = provider
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider.id).apply()
    }

    fun setCustomDnsUrl(url: String) {
        val trimmed = url.trim()
        _customDnsUrl.value = trimmed
        prefs.edit().putString(KEY_CUSTOM_DNS_URL, trimmed).apply()
    }

    companion object {
        private const val PREFS_NAME = "butterfly_secure_dns_prefs"
        private const val KEY_SECURE_DNS_ENABLED = "secure_dns_enabled"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val KEY_CUSTOM_DNS_URL = "custom_dns_url"

        @Volatile
        private var INSTANCE: SecureDnsPreferences? = null

        fun getInstance(context: Context): SecureDnsPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureDnsPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
