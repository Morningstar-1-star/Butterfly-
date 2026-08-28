package com.example.metadata

/**
 * Universal JAV Code and Media ID Parser & Normalizer.
 * Standardizes messy user queries, URLs, and filenames into canonical JAV identifiers.
 *
 * Supported Patterns:
 * - Standard Censored: "ssis-001", "IPX-123", "mide001" -> "SSIS-001", "IPX-123", "MIDE-001"
 * - Multi-letter Prefixes: "MIDV-001", "PRED-456", "HUNTA-001" -> "MIDV-001", "PRED-456", "HUNTA-001"
 * - Uncensored / Amateur: "1pondo 123456_789", "caribbeancom-123456-789", "010121_001", "123456-789"
 * - FC2-PPV: "FC2-PPV-1234567", "FC2 1234567", "fc2ppv-1234567" -> "FC2-PPV-1234567"
 * - Giga / Special: "GIGA-123", "T28-123", "MKBD-S123"
 * - Suffix/Prefix noise stripping: "SSIS-001-C", "[4K] IPX-123.mp4", "hhd800.com@MIDE-001-uncensored"
 */
object JavIdParser {

    private val STANDARD_JAV_REGEX = Regex("(?i)\\b([A-Z]{2,6})[-_ ]?(\\d{2,5})\\b")
    private val FC2_REGEX = Regex("(?i)\\bFC2[-_ ]?(?:PPV)?[-_ ]?(\\d{5,8})\\b")
    private val CARIBBEAN_REGEX = Regex("(?i)\\b(\\d{6})[-_ ](\\d{3})[-_ ](carib|caribbean|caribbeancom)\\b|(?i)\\b(carib|caribbean|caribbeancom)[-_ ]?(\\d{6})[-_ ](\\d{3})\\b")
    private val ONE_PONDO_REGEX = Regex("(?i)\\b(\\d{6})_(\\d{3})\\b|(?i)\\b(1pondo|10musume|heyzo)[-_ ]?(\\d{6})[-_ ]?(\\d{3})?\\b")
    private val MGS_REGEX = Regex("(?i)\\b(\\d{3}[A-Z]{2,5}-\\d{3,5}|SIRO-\\d{3,5})\\b")
    private val HEYZO_REGEX = Regex("(?i)\\b(?:HEYZO|heyzo)[-_ ]?(\\d{4})\\b")

    /**
     * Attempts to normalize any input text into a canonical JAV code.
     * Returns the standardized ID (e.g. "SSIS-001", "FC2-PPV-1234567") or null if no JAV code was recognized.
     */
    fun parse(input: String): String? {
        val cleanInput = input.trim()
            .replace(Regex("(?i)\\[(?:4k|fhd|hd|1080p|720p|uncensored|chinese|sub|chs|ct)\\]"), "")
            .replace(Regex("(?i)@(.*?)\\b"), "")
            .replace(Regex("(?i)\\.(?:mp4|mkv|avi|wmv|ts|iso)$"), "")
            .trim()

        // 1. FC2 PPV check
        FC2_REGEX.find(cleanInput)?.let { match ->
            val number = match.groupValues[1]
            return "FC2-PPV-$number"
        }

        // 2. Caribbeancom check
        CARIBBEAN_REGEX.find(cleanInput)?.let { match ->
            val date = match.groupValues.getOrNull(1)?.ifBlank { null } ?: match.groupValues.getOrNull(5) ?: ""
            val seq = match.groupValues.getOrNull(2)?.ifBlank { null } ?: match.groupValues.getOrNull(6) ?: ""
            if (date.isNotBlank() && seq.isNotBlank()) {
                return "caribbeancom-$date-$seq"
            }
        }

        // 3. Heyzo check
        HEYZO_REGEX.find(cleanInput)?.let { match ->
            return "HEYZO-${match.groupValues[1]}"
        }

        // 4. MGS / SIRO check
        MGS_REGEX.find(cleanInput)?.let { match ->
            return match.groupValues[1].uppercase()
        }

        // 5. Standard JAV regex (e.g., SSIS-001, IPX-123, MIDE-456)
        STANDARD_JAV_REGEX.find(cleanInput)?.let { match ->
            val prefix = match.groupValues[1].uppercase()
            val number = match.groupValues[2]

            // Ignore common false positives
            if (prefix in listOf("HTTP", "HTTPS", "WWW", "COM", "NET", "ORG", "VIDEO", "EPISODE", "SEASON")) {
                return@let
            }

            val paddedNumber = if (number.length == 2) "0$number" else number
            return "$prefix-$paddedNumber"
        }

        // 6. 1Pondo / 10Musume check
        ONE_PONDO_REGEX.find(cleanInput)?.let { match ->
            val p1 = match.groupValues[1].ifBlank { match.groupValues[4] }
            val p2 = match.groupValues[2].ifBlank { match.groupValues[5] }
            if (p1.isNotBlank() && p2.isNotBlank()) {
                return "1pondo-$p1-$p2"
            } else if (p1.isNotBlank()) {
                return "1pondo-$p1"
            }
        }

        return null
    }

    /**
     * Checks if a given text looks like a valid JAV ID.
     */
    fun isJavCode(input: String): Boolean {
        return parse(input) != null
    }

    /**
     * Generates DMM Content ID search variants (e.g. "SSIS-001" -> "ssis00001", "ssis001").
     */
    fun toDmmContentId(javCode: String): String {
        val parsed = parse(javCode) ?: javCode
        val parts = parsed.split("-")
        if (parts.size >= 2) {
            val prefix = parts[0].lowercase()
            val numStr = parts[1]
            val num = numStr.toIntOrNull()
            return if (num != null) {
                String.format("%s%05d", prefix, num)
            } else {
                "${prefix}${numStr.lowercase()}"
            }
        }
        return parsed.lowercase().replace("-", "").replace("_", "")
    }
}
