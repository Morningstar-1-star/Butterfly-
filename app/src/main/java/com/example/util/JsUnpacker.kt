package com.example.util

import java.util.regex.Pattern

/**
 * High-performance Dean Edwards Packer unpacker (`eval(function(p,a,c,k,e,d)...)`).
 * Decodes obfuscated streaming and player JavaScript commonly used in web video players.
 */
object JsUnpacker {

    private val PACKER_REGEX = Pattern.compile(
        """\}\s*\(\s*['"](.+?)['"]\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*['"](.*?)['"]\s*\.\s*split\s*\(\s*['"]\|['"]\s*\)""",
        Pattern.DOTALL
    )

    fun isPacked(script: String): Boolean {
        return script.contains("eval(function(p,a,c,k,e,d)") || script.contains("eval(function(p,a,c,k,e,r)")
    }

    /**
     * Unpacks a packed JavaScript string if present, or returns the original script.
     */
    fun unpack(script: String): String {
        if (!isPacked(script)) return script

        val matcher = PACKER_REGEX.matcher(script)
        if (!matcher.find()) {
            return script
        }

        return try {
            val payload = matcher.group(1) ?: return script
            val radix = matcher.group(2)?.toIntOrNull() ?: return script
            val count = matcher.group(3)?.toIntOrNull() ?: return script
            val keyString = matcher.group(4) ?: ""
            val keywords = keyString.split("|")

            val wordPattern = Pattern.compile("""\b\w+\b""")
            val wordMatcher = wordPattern.matcher(payload)
            val sb = StringBuffer()

            while (wordMatcher.find()) {
                val word = wordMatcher.group()
                val index = unbase(word, radix)
                if (index in keywords.indices && index < count) {
                    val replacement = keywords[index]
                    if (replacement.isNotEmpty()) {
                        wordMatcher.appendReplacement(sb, MatcherQuoteReplacement(replacement))
                        continue
                    }
                }
                wordMatcher.appendReplacement(sb, MatcherQuoteReplacement(word))
            }
            wordMatcher.appendTail(sb)
            sb.toString()
        } catch (e: Exception) {
            script
        }
    }

    private fun unbase(str: String, radix: Int): Int {
        if (radix < 2 || radix > 62) return -1
        var result = 0
        for (c in str) {
            val digit = when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'z' -> c - 'a' + 10
                in 'A'..'Z' -> c - 'A' + 36
                else -> return -1
            }
            if (digit >= radix) return -1
            result = result * radix + digit
        }
        return result
    }

    private fun MatcherQuoteReplacement(s: String): String {
        return s.replace("\\", "\\\\").replace("$", "\\$")
    }
}
