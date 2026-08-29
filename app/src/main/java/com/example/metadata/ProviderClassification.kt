package com.example.metadata

/**
 * Architectural classification for providers and media engines.
 * Ensures clear distinction between native embedded engines,
 * remote REST API clients, direct HTML scrapers, and architectural adapters.
 */
enum class ProviderClassification(val displayName: String, val isEmbeddedEngine: Boolean) {
    REAL_ENGINE("Embedded / Native Engine", true),
    API_ADAPTER("Remote REST API Client", false),
    SCRAPER("Direct HTML / DOM Scraper", false),
    REFERENCE_ONLY("Architecture / Reference Adapter", false),
    BROKEN("Unreachable / Broken", false),
    DISABLED("Disabled", false)
}
