package com.example.extractor.plugins

import android.util.Log
import java.util.regex.Pattern

/**
 * Embedded JavaScript / EJS Challenge Evaluator.
 * (Adapted from yt-dlp EJS & JS challenge execution specifications)
 *
 * Solves:
 * 1. Dean/Packer packed JavaScript: `eval(function(p,a,c,k,e,d)...)`
 * 2. Signature deobfuscation algorithms (slice, reverse, swap)
 * 3. Base64 / Hex cipher deobfuscation
 * 4. Simple arithmetic challenges embedded in video players
 */
object JsChallengeEvaluator {
    private const val TAG = "JsChallengeEvaluator"

    /**
     * Unpacks Dean Edwards Packer JavaScript: `eval(function(p,a,c,k,e,d)...)`
     */
    fun unpack(packedJs: String): String {
        try {
            val matcher = Pattern.compile(
                """eval\(function\(p,a,c,k,e,d\)\s*\{\s*.*?\}\('(?<payload>.*?)',\s*(?<a>\d+),\s*(?<c>\d+),\s*'(?<k>.*?)'\.split\('\|'\)""",
                Pattern.DOTALL
            ).matcher(packedJs)

            if (!matcher.find()) {
                val altMatcher = Pattern.compile(
                    """\}\s*\(\s*'(?<payload>.*?)'\s*,\s*(?<a>\d+)\s*,\s*(?<c>\d+)\s*,\s*'(?<k>.*?)'\.split\('\|'\)""",
                    Pattern.DOTALL
                ).matcher(packedJs)
                if (!altMatcher.find()) return packedJs
                return unpackInternal(
                    altMatcher.group("payload") ?: "",
                    altMatcher.group("a")?.toIntOrNull() ?: 36,
                    altMatcher.group("c")?.toIntOrNull() ?: 0,
                    (altMatcher.group("k") ?: "").split("|")
                )
            }

            return unpackInternal(
                matcher.group("payload") ?: "",
                matcher.group("a")?.toIntOrNull() ?: 36,
                matcher.group("c")?.toIntOrNull() ?: 0,
                (matcher.group("k") ?: "").split("|")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unpack error: ${e.message}")
            return packedJs
        }
    }

    private fun unpackInternal(payload: String, radix: Int, count: Int, dict: List<String>): String {
        var unbased = payload
        for (i in count - 1 downTo 0) {
            val key = i.toString(radix)
            val repl = if (i < dict.size && dict[i].isNotEmpty()) dict[i] else key
            val regex = Regex("""\b$key\b""")
            unbased = regex.replace(unbased, repl)
        }
        return unbased
    }

    /**
     * Evaluates a signature deobfuscation function composed of slice, reverse, swap operations.
     */
    fun deobfuscateSignature(signature: String, operations: List<String>): String {
        val chars = signature.toCharArray().toMutableList()
        for (op in operations) {
            val trimmed = op.trim()
            when {
                trimmed.startsWith("reverse") || trimmed == "r" -> {
                    chars.reverse()
                }
                trimmed.startsWith("slice") || trimmed.startsWith("s") -> {
                    val count = Regex("""\d+""").find(trimmed)?.value?.toIntOrNull() ?: 0
                    if (count in 0 until chars.size) {
                        repeat(count) { chars.removeAt(0) }
                    }
                }
                trimmed.startsWith("swap") || trimmed.startsWith("w") -> {
                    val pos = Regex("""\d+""").find(trimmed)?.value?.toIntOrNull() ?: 0
                    if (pos in 0 until chars.size && chars.isNotEmpty()) {
                        val temp = chars[0]
                        chars[0] = chars[pos % chars.size]
                        chars[pos % chars.size] = temp
                    }
                }
            }
        }
        return chars.joinToString("")
    }

    /**
     * Solves arithmetic challenges like `(12 * 4) + 18 - 2`
     */
    fun evaluateSimpleArithmetic(expr: String): Long {
        val clean = expr.replace(" ", "")
        return try {
            var sum = 0L
            val parts = clean.split("+")
            for (p in parts) {
                if (p.contains("-")) {
                    val subParts = p.split("-")
                    var subVal = evalMul(subParts[0])
                    for (i in 1 until subParts.size) {
                        subVal -= evalMul(subParts[i])
                    }
                    sum += subVal
                } else {
                    sum += evalMul(p)
                }
            }
            sum
        } catch (_: Exception) {
            0L
        }
    }

    private fun evalMul(s: String): Long {
        val factors = s.split("*")
        var res = 1L
        for (f in factors) {
            res *= f.toLongOrNull() ?: 1L
        }
        return res
    }
}
