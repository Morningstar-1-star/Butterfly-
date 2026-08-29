package com.example.resolver

/**
 * Provider capability declarations.
 * Used by the resolver and registry to dynamically select and route
 * providers based on requested media features.
 */
enum class ProviderCapability {
    SEARCH,
    STREAM,
    DOWNLOAD,
    HLS,
    DASH,
    DIRECT_HTTP,
    TORRENT,
    SUBTITLE,
    LIVE,
    CAPABILITY_4K,
    HDR,
    MULTI_AUDIO
}
