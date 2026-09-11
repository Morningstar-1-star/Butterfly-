package com.example.torrent.cardigann.template

import com.example.torrent.provider.MediaIdentity
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Locale

/**
 * Go-Template Engine for Cardigann / Prowlarr V11 Indexer path & parameter interpolation.
 * Interprets conditionals ({{ if ... }} {{ else }} {{ end }}), variable replacements (.Keywords, .Query, .Config),
 * formatters (printf, urlquery, join, add), and loops.
 */
object CardigannTemplateEngine {

    data class TemplateContext(
        val keywords: String = "",
        val title: String = "",
        val cleanTitle: String = "",
        val year: String = "",
        val season: Int? = null,
        val episode: Int? = null,
        val imdbId: String? = null,
        val imdbIdShort: String? = null,
        val tmdbId: String? = null,
        val tvdbId: String? = null,
        val categories: List<String> = emptyList(),
        val config: Map<String, String> = emptyMap(),
        val page: Int = 1,
        val offset: Int = 0,
        val limit: Int = 100
    ) {
        companion object {
            fun from(query: String, identity: MediaIdentity, config: Map<String, String> = emptyMap(), page: Int = 1): TemplateContext {
                val cleanImdb = identity.imdbId?.trim()?.takeIf { it.isNotBlank() }
                val shortImdb = cleanImdb?.removePrefix("tt")

                val effectiveTitle = if (identity.title.isNotBlank()) identity.title else query
                val cleanTitle = effectiveTitle.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim().replace(Regex("\\s+"), " ")

                val effectiveYear = identity.year?.toString() ?: ""

                val effectiveKeywords = when {
                    query.isNotBlank() -> query.trim()
                    identity.season != null && identity.episode != null -> {
                        String.format(Locale.US, "%s S%02dE%02d", effectiveTitle, identity.season, identity.episode)
                    }
                    identity.season != null -> {
                        String.format(Locale.US, "%s S%02d", effectiveTitle, identity.season)
                    }
                    effectiveYear.isNotBlank() -> {
                        "$effectiveTitle $effectiveYear"
                    }
                    else -> effectiveTitle
                }

                return TemplateContext(
                    keywords = effectiveKeywords,
                    title = effectiveTitle,
                    cleanTitle = cleanTitle,
                    year = effectiveYear,
                    season = identity.season,
                    episode = identity.episode,
                    imdbId = cleanImdb,
                    imdbIdShort = shortImdb,
                    tmdbId = identity.tmdbId,
                    config = config,
                    page = page,
                    offset = (page - 1) * 100
                )
            }
        }
    }

    fun render(template: String, context: TemplateContext): String {
        if (!template.contains("{{")) return template

        var result = template

        // 1. Process block conditionals: {{ if <cond> }}<true_block>{{ else }}<false_block>{{ end }}
        result = processConditionals(result, context)

        // 2. Process range blocks: {{ range .Categories }}...{{ end }}
        result = processRange(result, context)

        // 3. Process expressions and variables
        result = processExpressions(result, context)

        return result
    }

    private fun processConditionals(template: String, context: TemplateContext): String {
        val ifRegex = Regex("""\{\{\s*if\s+(.*?)\s*\}\}([\s\S]*?)(?:\{\{\s*else\s*\}\}([\s\S]*?))?\{\{\s*end\s*\}\}""")
        var current = template
        var matcher = ifRegex.find(current)

        while (matcher != null) {
            val condition = matcher.groupValues[1].trim()
            val trueBranch = matcher.groupValues[2]
            val falseBranch = matcher.groupValues.getOrNull(3) ?: ""

            val condResult = evaluateCondition(condition, context)
            val replacement = if (condResult) trueBranch else falseBranch

            current = current.substring(0, matcher.range.first) + replacement + current.substring(matcher.range.last + 1)
            matcher = ifRegex.find(current)
        }

        return current
    }

    private fun evaluateCondition(condition: String, context: TemplateContext): Boolean {
        val trimmed = condition.trim()

        if (trimmed.startsWith("and ", ignoreCase = true)) {
            val terms = trimmed.substring(4).split(Regex("\\s+")).filter { it.isNotBlank() }
            return terms.all { evaluateCondition(it, context) }
        }

        if (trimmed.startsWith("or ", ignoreCase = true)) {
            val terms = trimmed.substring(3).split(Regex("\\s+")).filter { it.isNotBlank() }
            return terms.any { evaluateCondition(it, context) }
        }

        if (trimmed.startsWith("not ", ignoreCase = true)) {
            val sub = trimmed.substring(4).trim()
            return !evaluateCondition(sub, context)
        }

        if (trimmed.contains(" eq ") || trimmed.contains(" ne ") || trimmed.contains(" == ") || trimmed.contains(" != ")) {
            val parts = trimmed.split(Regex("\\s+(?:eq|==|ne|!=)\\s+"), limit = 2)
            if (parts.size == 2) {
                val left = resolveValue(parts[0].trim(), context)
                val right = parts[1].trim().trim('"', '\'')
                val isEqual = left.equals(right, ignoreCase = true)
                return if (trimmed.contains("ne") || trimmed.contains("!=")) !isEqual else isEqual
            }
        }

        val value = resolveValue(trimmed, context)
        return value.isNotBlank() && value != "0" && !value.equals("false", ignoreCase = true)
    }

    private fun processRange(template: String, context: TemplateContext): String {
        val rangeRegex = Regex("""\{\{\s*range\s+(.*?)\s*\}\}([\s\S]*?)\{\{\s*end\s*\}\}""")
        var current = template
        var matcher = rangeRegex.find(current)

        while (matcher != null) {
            val target = matcher.groupValues[1].trim()
            val body = matcher.groupValues[2]

            val list = when {
                target.contains(".Categories") -> context.categories
                else -> emptyList()
            }

            val sb = StringBuilder()
            for (item in list) {
                var itemRendered = body.replace("{{ . }}", item).replace("{{.}}", item)
                sb.append(itemRendered)
            }

            current = current.substring(0, matcher.range.first) + sb.toString() + current.substring(matcher.range.last + 1)
            matcher = rangeRegex.find(current)
        }

        return current
    }

    private fun processExpressions(template: String, context: TemplateContext): String {
        val exprRegex = Regex("""\{\{\s*(.*?)\s*\}\}""")
        return exprRegex.replace(template) { matchResult ->
            val expr = matchResult.groupValues[1].trim()
            renderExpression(expr, context)
        }
    }

    private fun renderExpression(expr: String, context: TemplateContext): String {
        if (expr.isEmpty()) return ""

        // Pipe operations: e.g. .Keywords | urlquery or urlencode .Keywords
        if (expr.contains("|")) {
            val parts = expr.split("|").map { it.trim() }
            var value = renderExpression(parts[0], context)
            for (i in 1 until parts.size) {
                val filter = parts[i]
                value = applyPipeFilter(value, filter)
            }
            return value
        }

        // printf "%02d" .Query.Season
        if (expr.startsWith("printf ", ignoreCase = true)) {
            val match = Regex("""printf\s+"([^"]+)"\s+(.*)""").find(expr)
            if (match != null) {
                val format = match.groupValues[1]
                val paramExpr = match.groupValues[2].trim()
                val value = resolveValue(paramExpr, context)
                val intVal = value.toIntOrNull()
                return if (intVal != null) {
                    try {
                        String.format(Locale.US, format, intVal)
                    } catch (_: Exception) {
                        value
                    }
                } else {
                    try {
                        String.format(Locale.US, format, value)
                    } catch (_: Exception) {
                        value
                    }
                }
            }
        }

        // join .Categories ","
        if (expr.startsWith("join ", ignoreCase = true)) {
            val match = Regex("""join\s+(\.[a-zA-Z0-9_\.]+)\s+"([^"]*)"""").find(expr)
            if (match != null) {
                val target = match.groupValues[1]
                val delimiter = match.groupValues[2]
                if (target.contains(".Categories")) {
                    return context.categories.joinToString(delimiter)
                }
            }
        }

        // add .Page 1
        if (expr.startsWith("add ", ignoreCase = true)) {
            val parts = expr.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size >= 3) {
                val left = resolveValue(parts[1], context).toIntOrNull() ?: 0
                val right = parts[2].toIntOrNull() ?: 0
                return (left + right).toString()
            }
        }

        // urlencode / urlquery functions: urlencode .Keywords
        if (expr.startsWith("urlencode ", ignoreCase = true) || expr.startsWith("urlquery ", ignoreCase = true)) {
            val param = expr.substringAfter(" ").trim()
            val value = resolveValue(param, context)
            return urlEncode(value)
        }

        // Standard variable lookup
        return resolveValue(expr, context)
    }

    private fun applyPipeFilter(value: String, filter: String): String {
        return when (filter.lowercase()) {
            "urlquery", "urlencode" -> urlEncode(value)
            "urldecode" -> try { java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name()) } catch (_: Exception) { value }
            "tolower" -> value.lowercase()
            "toupper" -> value.uppercase()
            "trim" -> value.trim()
            else -> value
        }
    }

    private fun resolveValue(key: String, context: TemplateContext): String {
        val clean = key.trim().trim('"', '\'')
        if (!clean.startsWith(".")) {
            // Check if numeric or boolean constant
            if (clean.toIntOrNull() != null || clean.equals("true", true) || clean.equals("false", true)) {
                return clean
            }
        }

        val normalized = clean.removePrefix(".")
        return when {
            normalized.equals("Keywords", ignoreCase = true) || normalized.equals("Query.Keywords", ignoreCase = true) -> context.keywords
            normalized.equals("Query.Title", ignoreCase = true) || normalized.equals("Title", ignoreCase = true) -> context.title
            normalized.equals("Query.CleanTitle", ignoreCase = true) || normalized.equals("CleanTitle", ignoreCase = true) -> context.cleanTitle
            normalized.equals("Query.Year", ignoreCase = true) || normalized.equals("Year", ignoreCase = true) -> context.year
            normalized.equals("Query.Season", ignoreCase = true) || normalized.equals("Season", ignoreCase = true) -> context.season?.toString() ?: ""
            normalized.equals("Query.Ep", ignoreCase = true) || normalized.equals("Query.Episode", ignoreCase = true) || normalized.equals("Ep", ignoreCase = true) -> context.episode?.toString() ?: ""
            normalized.equals("Query.IMDBID", ignoreCase = true) || normalized.equals("IMDBID", ignoreCase = true) -> context.imdbId ?: ""
            normalized.equals("Query.IMDBIDShort", ignoreCase = true) || normalized.equals("IMDBIDShort", ignoreCase = true) -> context.imdbIdShort ?: ""
            normalized.equals("Query.TMDBID", ignoreCase = true) || normalized.equals("TMDBID", ignoreCase = true) -> context.tmdbId ?: ""
            normalized.equals("Query.TVDBID", ignoreCase = true) || normalized.equals("TVDBID", ignoreCase = true) -> context.tvdbId ?: ""
            normalized.equals("Page", ignoreCase = true) -> context.page.toString()
            normalized.equals("Offset", ignoreCase = true) -> context.offset.toString()
            normalized.equals("Limit", ignoreCase = true) -> context.limit.toString()
            normalized.equals("Today.Year", ignoreCase = true) || normalized.equals("Now.Year", ignoreCase = true) -> Calendar.getInstance().get(Calendar.YEAR).toString()
            normalized.startsWith("Config.", ignoreCase = true) -> {
                val configKey = normalized.substring(7)
                context.config[configKey] ?: ""
            }
            context.config.containsKey(normalized) -> context.config[normalized] ?: ""
            else -> ""
        }
    }

    private fun urlEncode(value: String): String {
        return try {
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
        } catch (_: Exception) {
            value
        }
    }
}
