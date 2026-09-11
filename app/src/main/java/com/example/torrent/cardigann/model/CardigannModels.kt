package com.example.torrent.cardigann.model

/**
 * Complete Cardigann / Prowlarr V11 Indexer Definition Schema.
 * Directly maps Prowlarr YAML indexer definitions.
 */
data class CardigannIndexerDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val language: String = "en-US",
    val type: String = "public", // public, semi-private, private
    val encoding: String = "UTF-8",
    val links: List<String> = emptyList(),
    val legacyLinks: List<String> = emptyList(),
    val caps: CardigannCaps = CardigannCaps(),
    val settings: List<CardigannSetting> = emptyList(),
    val search: CardigannSearchConfig = CardigannSearchConfig(),
    val download: CardigannDownloadConfig? = null,
    val login: CardigannLoginConfig? = null,
    val isCustom: Boolean = false,
    val isBuiltIn: Boolean = true,
    val sourceYaml: String = ""
)

data class CardigannCaps(
    val categoryMappings: List<CardigannCategoryMapping> = emptyList(),
    val modes: Map<String, List<String>> = emptyMap() // "search": ["q"], "tv-search": ["q", "season", "ep", "imdbid"], "movie-search": ["q", "imdbid"]
) {
    fun getTorznabCategory(siteCategoryId: String): String? {
        val clean = siteCategoryId.trim()
        val mapping = categoryMappings.firstOrNull { 
            it.id.equals(clean, ignoreCase = true) || it.cat.equals(clean, ignoreCase = true) 
        }
        return mapping?.cat ?: mapping?.desc
    }
}

data class CardigannCategoryMapping(
    val id: String,
    val cat: String,
    val desc: String = "",
    val subcat: String = ""
)

data class CardigannSetting(
    val name: String,
    val type: String = "text", // select, text, password, checkbox, info
    val label: String = "",
    val default: String = "",
    val options: Map<String, String> = emptyMap(),
    var value: String = default
)

data class CardigannSearchConfig(
    val paths: List<CardigannPath> = emptyList(),
    val method: String = "get", // get, post
    val inputs: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val responseType: String = "html", // html, json, xml
    val rows: CardigannRowSelector = CardigannRowSelector(),
    val fields: Map<String, CardigannFieldConfig> = emptyMap(),
    val errorHandling: List<CardigannErrorConfig> = emptyList()
)

data class CardigannPath(
    val path: String = "",
    val method: String = "get",
    val inputs: Map<String, String> = emptyMap(),
    val categories: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap()
)

data class CardigannRowSelector(
    val selector: String = "",
    val after: Int = 0,
    val remove: String = "",
    val dateheaders: String = ""
)

data class CardigannFieldConfig(
    val name: String = "",
    val selector: String? = null,
    val attribute: String? = null,
    val text: String? = null,
    val caseMap: Map<String, String>? = null,
    val filters: List<CardigannFilterConfig> = emptyList(),
    val optional: Boolean = false,
    val default: String? = null
)

data class CardigannFilterConfig(
    val name: String, // regexp, re_replace, replace, split, trim, tolower, toupper, dateparse, timeparse, timeago, relative, bytesparse, size, urlencode, urldecode, querystring, append, prepend, default, validfilename, hexdecode, base64decode, etc.
    val args: Any? = null // String, List<String>, Map<String, String>
) {
    fun getArgString(index: Int = 0, fallback: String = ""): String {
        return when (val a = args) {
            is String -> if (index == 0) a else fallback
            is List<*> -> a.getOrNull(index)?.toString() ?: fallback
            is Array<*> -> a.getOrNull(index)?.toString() ?: fallback
            else -> a?.toString() ?: fallback
        }
    }

    fun getArgInt(index: Int = 0, fallback: Int = 0): Int {
        return getArgString(index, fallback.toString()).toIntOrNull() ?: fallback
    }

    fun getArgList(): List<String> {
        return when (val a = args) {
            is List<*> -> a.mapNotNull { it?.toString() }
            is Array<*> -> a.mapNotNull { it?.toString() }
            is String -> listOf(a)
            else -> emptyList()
        }
    }
}

data class CardigannDownloadConfig(
    val selector: String? = null,
    val attribute: String? = null,
    val method: String = "get",
    val inputs: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val before: CardigannDownloadBeforeConfig? = null
)

data class CardigannDownloadBeforeConfig(
    val path: String = "",
    val method: String = "get",
    val inputs: Map<String, String> = emptyMap()
)

data class CardigannLoginConfig(
    val path: String = "",
    val method: String = "form", // form, post, get, cookie
    val form: String = "form",
    val inputs: Map<String, String> = emptyMap(),
    val error: List<CardigannErrorConfig> = emptyList(),
    val test: CardigannLoginTestConfig? = null
)

data class CardigannLoginTestConfig(
    val path: String = "",
    val selector: String = ""
)

data class CardigannErrorConfig(
    val selector: String = "",
    val message: CardigannFieldConfig? = null
)

enum class IndexerHealthState {
    UNKNOWN,
    TESTING,
    ONLINE,
    DEGRADED,
    OFFLINE,
    DISABLED
}

data class IndexerStatus(
    val indexerId: String,
    val isEnabled: Boolean = true,
    val state: IndexerHealthState = IndexerHealthState.UNKNOWN,
    val latencyMs: Long = 0L,
    val resultsCount: Int = 0,
    val lastCheckedTimestamp: Long = 0L,
    val lastError: String? = null,
    val activeMirror: String? = null
)
