package com.example.torrent.cardigann.parser

import android.util.Log
import com.example.torrent.cardigann.model.*
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * YAML Parser for Cardigann / Prowlarr V11 Indexer Definitions.
 * Transforms raw YAML files into type-safe CardigannIndexerDefinition objects.
 */
object CardigannYamlParser {

    private const val TAG = "CardigannYamlParser"

    fun parse(yamlContent: String): CardigannIndexerDefinition? {
        val cleanYaml = sanitizeYaml(yamlContent)
        if (cleanYaml.isBlank()) return null

        return try {
            val loaderOptions = LoaderOptions()
            val yaml = Yaml(SafeConstructor(loaderOptions))
            val map: Map<*, *>? = yaml.load(cleanYaml) as? Map<*, *>
            if (map == null) return null

            buildDefinitionFromMap(map, yamlContent)
        } catch (e: Exception) {
            Log.w(TAG, "SnakeYAML failed to parse: ${e.message}, attempting fallback parse", e)
            tryFallbackParse(yamlContent)
        }
    }

    private fun sanitizeYaml(content: String): String {
        // Strip BOM and normalize line endings
        var sanitized = content.replace("\uFEFF", "").replace("\r\n", "\n")
        return sanitized
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildDefinitionFromMap(map: Map<*, *>, rawSource: String): CardigannIndexerDefinition {
        val id = map["id"]?.toString()?.trim() ?: ""
        val name = map["name"]?.toString()?.trim() ?: id
        val description = map["description"]?.toString()?.trim() ?: ""
        val language = map["language"]?.toString()?.trim() ?: "en-US"
        val type = map["type"]?.toString()?.trim() ?: "public"
        val encoding = map["encoding"]?.toString()?.trim() ?: "UTF-8"

        // Links
        val links = mutableListOf<String>()
        val rawLinks = map["links"]
        if (rawLinks is List<*>) {
            rawLinks.forEach { it?.toString()?.trim()?.let { l -> if (l.isNotBlank()) links.add(l) } }
        } else if (rawLinks is String) {
            links.add(rawLinks.trim())
        }

        val legacyLinks = mutableListOf<String>()
        val rawLegacy = map["legacylinks"] ?: map["legacy_links"]
        if (rawLegacy is List<*>) {
            rawLegacy.forEach { it?.toString()?.trim()?.let { l -> if (l.isNotBlank()) legacyLinks.add(l) } }
        }

        // Capabilities (caps)
        val caps = parseCaps(map["caps"] as? Map<*, *>)

        // Settings
        val settings = parseSettings(map["settings"] as? List<*>)

        // Search block
        val search = parseSearchConfig(map["search"] as? Map<*, *>)

        // Download block
        val download = parseDownloadConfig(map["download"] as? Map<*, *>)

        // Login block
        val login = parseLoginConfig(map["login"] as? Map<*, *>)

        return CardigannIndexerDefinition(
            id = id,
            name = name,
            description = description,
            language = language,
            type = type,
            encoding = encoding,
            links = links,
            legacyLinks = legacyLinks,
            caps = caps,
            settings = settings,
            search = search,
            download = download,
            login = login,
            sourceYaml = rawSource
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCaps(capsMap: Map<*, *>?): CardigannCaps {
        if (capsMap == null) return CardigannCaps()

        val categoryMappings = mutableListOf<CardigannCategoryMapping>()
        val rawCatMappings = capsMap["categorymappings"] ?: capsMap["categories"]
        if (rawCatMappings is List<*>) {
            for (item in rawCatMappings) {
                if (item is Map<*, *>) {
                    val id = item["id"]?.toString() ?: ""
                    val cat = item["cat"]?.toString() ?: item["category"]?.toString() ?: ""
                    val desc = item["desc"]?.toString() ?: item["description"]?.toString() ?: ""
                    val subcat = item["subcat"]?.toString() ?: ""
                    if (id.isNotBlank() || cat.isNotBlank()) {
                        categoryMappings.add(CardigannCategoryMapping(id, cat, desc, subcat))
                    }
                }
            }
        }

        val modes = mutableMapOf<String, List<String>>()
        val rawModes = capsMap["modes"] as? Map<*, *>
        if (rawModes != null) {
            for ((k, v) in rawModes) {
                val modeName = k?.toString() ?: continue
                val paramList = mutableListOf<String>()
                if (v is List<*>) {
                    v.forEach { it?.toString()?.let { p -> paramList.add(p) } }
                } else if (v is String) {
                    paramList.add(v)
                }
                modes[modeName] = paramList
            }
        }

        return CardigannCaps(categoryMappings, modes)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSettings(settingsList: List<*>?): List<CardigannSetting> {
        if (settingsList == null) return emptyList()

        val result = mutableListOf<CardigannSetting>()
        for (item in settingsList) {
            if (item is Map<*, *>) {
                val name = item["name"]?.toString() ?: continue
                val type = item["type"]?.toString() ?: "text"
                val label = item["label"]?.toString() ?: name
                val default = item["default"]?.toString() ?: ""

                val options = mutableMapOf<String, String>()
                val rawOptions = item["options"] as? Map<*, *>
                if (rawOptions != null) {
                    for ((ok, ov) in rawOptions) {
                        options[ok.toString()] = ov.toString()
                    }
                }

                result.add(CardigannSetting(name, type, label, default, options, default))
            }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSearchConfig(searchMap: Map<*, *>?): CardigannSearchConfig {
        if (searchMap == null) return CardigannSearchConfig()

        val method = searchMap["method"]?.toString()?.lowercase() ?: "get"
        val responseType = (searchMap["response"] as? Map<*, *>)?.get("type")?.toString()?.lowercase()
            ?: searchMap["responsetype"]?.toString()?.lowercase()
            ?: "html"

        // Paths
        val paths = mutableListOf<CardigannPath>()
        val rawPaths = searchMap["paths"]
        if (rawPaths is List<*>) {
            for (p in rawPaths) {
                if (p is Map<*, *>) {
                    val pathStr = p["path"]?.toString() ?: ""
                    val pathMethod = p["method"]?.toString()?.lowercase() ?: method
                    val pathInputs = parseStringMap(p["inputs"] as? Map<*, *>)
                    val pathHeaders = parseStringMap(p["headers"] as? Map<*, *>)
                    val pathCats = (p["categories"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    paths.add(CardigannPath(pathStr, pathMethod, pathInputs, pathCats, pathHeaders))
                }
            }
        } else if (searchMap.containsKey("path")) {
            val pathStr = searchMap["path"]?.toString() ?: ""
            paths.add(CardigannPath(path = pathStr, method = method))
        }

        val inputs = parseStringMap(searchMap["inputs"] as? Map<*, *>)
        val headers = parseStringMap(searchMap["headers"] as? Map<*, *>)

        // Rows
        val rawRows = searchMap["rows"] as? Map<*, *>
        val rows = if (rawRows != null) {
            CardigannRowSelector(
                selector = rawRows["selector"]?.toString() ?: "",
                after = rawRows["after"]?.toString()?.toIntOrNull() ?: 0,
                remove = rawRows["remove"]?.toString() ?: "",
                dateheaders = rawRows["dateheaders"]?.toString() ?: ""
            )
        } else {
            CardigannRowSelector()
        }

        // Fields
        val fields = mutableMapOf<String, CardigannFieldConfig>()
        val rawFields = searchMap["fields"] as? Map<*, *>
        if (rawFields != null) {
            for ((k, v) in rawFields) {
                val fieldName = k?.toString() ?: continue
                if (v is Map<*, *>) {
                    fields[fieldName] = parseFieldConfig(fieldName, v)
                }
            }
        }

        return CardigannSearchConfig(
            paths = paths,
            method = method,
            inputs = inputs,
            headers = headers,
            responseType = responseType,
            rows = rows,
            fields = fields
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFieldConfig(name: String, map: Map<*, *>): CardigannFieldConfig {
        val selector = map["selector"]?.toString()
        val attribute = map["attribute"]?.toString()
        val text = map["text"]?.toString()
        val optional = map["optional"]?.toString()?.toBoolean() ?: false
        val defaultVal = map["default"]?.toString()

        val caseMap = parseStringMap(map["case"] as? Map<*, *>).ifEmpty { null }

        val filters = mutableListOf<CardigannFilterConfig>()
        val rawFilters = map["filters"] as? List<*>
        if (rawFilters != null) {
            for (f in rawFilters) {
                if (f is Map<*, *>) {
                    val filterName = f["name"]?.toString() ?: continue
                    val args = f["args"]
                    filters.add(CardigannFilterConfig(filterName, args))
                }
            }
        }

        return CardigannFieldConfig(
            name = name,
            selector = selector,
            attribute = attribute,
            text = text,
            caseMap = caseMap,
            filters = filters,
            optional = optional,
            default = defaultVal
        )
    }

    private fun parseDownloadConfig(downloadMap: Map<*, *>?): CardigannDownloadConfig? {
        if (downloadMap == null) return null
        return CardigannDownloadConfig(
            selector = downloadMap["selector"]?.toString(),
            attribute = downloadMap["attribute"]?.toString(),
            method = downloadMap["method"]?.toString()?.lowercase() ?: "get",
            inputs = parseStringMap(downloadMap["inputs"] as? Map<*, *>),
            headers = parseStringMap(downloadMap["headers"] as? Map<*, *>)
        )
    }

    private fun parseLoginConfig(loginMap: Map<*, *>?): CardigannLoginConfig? {
        if (loginMap == null) return null
        return CardigannLoginConfig(
            path = loginMap["path"]?.toString() ?: "",
            method = loginMap["method"]?.toString()?.lowercase() ?: "form",
            form = loginMap["form"]?.toString() ?: "form",
            inputs = parseStringMap(loginMap["inputs"] as? Map<*, *>)
        )
    }

    private fun parseStringMap(map: Map<*, *>?): Map<String, String> {
        if (map == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        for ((k, v) in map) {
            if (k != null && v != null) {
                result[k.toString()] = v.toString()
            }
        }
        return result
    }

    private fun tryFallbackParse(yaml: String): CardigannIndexerDefinition? {
        // Simple regex fallback for basic Cardigann indexers
        val id = Regex("""(?m)^id:\s*([^\s#]+)""").find(yaml)?.groupValues?.get(1) ?: return null
        val name = Regex("""(?m)^name:\s*([^\r\n#]+)""").find(yaml)?.groupValues?.get(1)?.trim() ?: id
        val links = Regex("""(?m)^\s*-\s*(https?://[^\s#]+)""").findAll(yaml).map { it.groupValues[1] }.toList()

        return CardigannIndexerDefinition(
            id = id,
            name = name,
            links = links,
            sourceYaml = yaml
        )
    }
}
