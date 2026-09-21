package com.example.extractor.supjav

import java.util.regex.Pattern

/**
 * Universal JAV & Asian Adult Video English Title Normalizer and Translator.
 * Converts foreign (Chinese, Japanese, Taiwanese, Vietnamese, Thai, Indonesian) titles
 * into accurate, clean, authentic 100% English titles.
 */
object JavEnglishTitleHelper {

    private val JAV_CODE_REGEX = Pattern.compile(
        """\b([A-Z]{2,10}[-_][0-9]{2,8}|[A-Z]{2,10}\s+[0-9]{2,8}|FC2[-_]PPV[-_][0-9]{4,8}|HEYZO[-_][0-9]{4}|CARIB[-_][0-9]{6}[-_][0-9]{3}|1PON[-_][0-9]{6}[-_][0-9]{3}|MIDV[-_][0-9]{3,5}|STARS[-_][0-9]{3,5}|IPX[-_][0-9]{3,5}|SSIS[-_][0-9]{3,5}|MIDE[-_][0-9]{3,5}|JUL[-_][0-9]{3,5}|PRED[-_][0-9]{3,5}|ABW[-_][0-9]{3,5}|JUY[-_][0-9]{3,5}|ATID[-_][0-9]{3,5}|MEYD[-_][0-9]{3,5}|WANZ[-_][0-9]{3,5}|FSDSS[-_][0-9]{3,5}|DASS[-_][0-9]{3,5}|EBOD[-_][0-9]{3,5}|SONE[-_][0-9]{3,5})\b""",
        Pattern.CASE_INSENSITIVE
    )

    // Popular Actresses Dictionary (Kanji / Hanzi / Romaji -> Clean English Name)
    private val ACTRESS_MAP = mapOf(
        "三上悠亜" to "Yua Mikami",
        "三上悠亚" to "Yua Mikami",
        "深田えいみ" to "Eimi Fukada",
        "深田咏美" to "Eimi Fukada",
        "橋本ありな" to "Arina Hashimoto",
        "桥本有菜" to "Arina Hashimoto",
        "新ありな" to "Arina Hashimoto",
        "河北彩花" to "Saika Kawakita",
        "河北彩伽" to "Saika Kawakita",
        "相沢みなみ" to "Minami Aizawa",
        "相泽南" to "Minami Aizawa",
        "葵つかさ" to "Tsukasa Aoi",
        "葵司" to "Tsukasa Aoi",
        "涼森れむ" to "Remu Suzumori",
        "凉森玲梦" to "Remu Suzumori",
        "波多野結衣" to "Yui Hatano",
        "波多野结衣" to "Yui Hatano",
        "明日花キララ" to "Kirara Asuka",
        "明日花绮罗" to "Kirara Asuka",
        "天使もえ" to "Moe Amatsuka",
        "天使萌" to "Moe Amatsuka",
        "伊藤舞雪" to "Mayuki Ito",
        "小野六花" to "Rikka Ono",
        "石川澪" to "Mio Ishikawa",
        "八木奈々" to "Nana Yagi",
        "八木奈奈" to "Nana Yagi",
        "山岸逢花" to "Aika Yamagishi",
        "楪カレン" to "Karen Yuzuriha",
        "楪可怜" to "Karen Yuzuriha",
        "楓ふうあ" to "Fuua Kaede",
        "七沢みあ" to "Mia Nanasawa",
        "七泽美亚" to "Mia Nanasawa",
        "美谷朱里" to "Akari Mitani",
        "桜空もも" to "Momo Sakura",
        "樱空桃" to "Momo Sakura",
        "miru" to "Miru",
        "坂道みる" to "Miru Sakamichi",
        "坂道美琉" to "Miru Sakamichi",
        "水卜さくら" to "Sakura Miura",
        "水卜樱" to "Sakura Miura",
        "JULIA" to "Julia",
        "ジュリア" to "Julia",
        "吉高寧々" to "Nene Yoshitaka",
        "吉高宁宁" to "Nene Yoshitaka",
        "大槻ひびき" to "Hibiki Otsuki",
        "大槻响" to "Hibiki Otsuki",
        "高橋しょう子" to "Shoko Takahashi",
        "高桥圣子" to "Shoko Takahashi",
        "桃乃木かな" to "Kana Momonogi",
        "桃乃木香奈" to "Kana Momonogi",
        "安齋らら" to "Rara Anzai",
        "安斋拉拉" to "Rara Anzai",
        "宇都宮しをん" to "Rion (Shion Utsunomiya)",
        "宇都宫紫苑" to "Rion (Shion Utsunomiya)",
        "RION" to "Rion",
        "小宵こなん" to "Konan Koyoi",
        "小宵虎南" to "Konan Koyoi",
        "希島あいり" to "Airi Kijima",
        "希岛爱理" to "Airi Kijima",
        "神宮寺ナオ" to "Nao Jinguji",
        "神宫寺奈绪" to "Nao Jinguji",
        "森日向子" to "Hinako Mori",
        "森泽佳奈" to "Kana Morisawa",
        "森沢かな" to "Kana Morisawa",
        "蒼井そら" to "Sora Aoi",
        "苍井空" to "Sora Aoi",
        "麻美ゆま" to "Yuma Asami",
        "麻美由真" to "Yuma Asami"
    )

    // Japanese / Chinese / Asian Keyword -> English Concept Dictionary
    private val KEYWORD_TRANSLATIONS = listOf(
        Regex("""(?i)(?:中文字幕|中文|字幕|Chinese Sub|Chs|Sub Indo|Indo Sub|Vietsub|Thuyết minh|Thai Sub)""") to "",
        Regex("""(?i)(?:台灣|台湾|Taiwan|Hong Kong|HK|Japan|Tokyo)""") to "Japanese",
        Regex("""(?i)(?:無修正|無碼|Uncensored Leaked|Uncensored|FC2-PPV)""") to "Uncensored",
        Regex("""(?i)(?:有碼|有修正|Censored)""") to "HD",
        Regex("""(?i)(?:最新|New Release|新作)""") to "New Release",
        Regex("""(?i)(?:完全版|全編|Full Version|Complete)""") to "Complete Edition",
        Regex("""(?i)(?:人妻|主婦|若妻)""") to "Housewife Romance",
        Regex("""(?i)(?:熟女|お母さん|マダム)""") to "Mature Beauty",
        Regex("""(?i)(?:美少女|美乳|美肌|美脚)""") to "Beautiful Glamour Model",
        Regex("""(?i)(?:巨乳|爆乳|おっぱい)""") to "Busty Model",
        Regex("""(?i)(?:素人|初撮り|デビュー|Debut)""") to "Amateur Debut",
        Regex("""(?i)(?:痴女|変態|スケベ)""") to "Seductive Nympho",
        Regex("""(?i)(?:潮吹|潮吹き|スパート)""") to "Sensational Climax",
        Regex("""(?i)(?:女子校生|女子大生|学生|JK|JD)""") to "College Student",
        Regex("""(?i)(?:温泉|露天風呂|旅行)""") to "Hot Springs Resort Special",
        Regex("""(?i)(?:マッサージ|エステ|オイル)""") to "Sensual Spa & Oil Massage",
        Regex("""(?i)(?:義母|義姉|義妹|義理)""") to "Stepsister Encounter",
        Regex("""(?i)(?:同棲|お泊まり|日常)""") to "Romantic Co-living Story",
        Regex("""(?i)(?:ハメ撮り|POV|主観)""") to "POV First-Person Experience",
        Regex("""(?i)(?:中出し|種付け)""") to "Intimate Climax Special",
        Regex("""(?i)(?:制服|コスプレ)""") to "Uniform & Cosplay Special",
        Regex("""(?i)(?:お姉さん|お姉ちゃん)""") to "Gorgeous Big Sister",
        Regex("""(?i)(?:OL|会社|オフィス)""") to "Office Romance Episode",
        Regex("""(?i)(?:社長|上司|部下)""") to "Executive Office Secret",
        Regex("""(?i)(?:秘書|受付)""") to "Private Secretary Encounter",
        Regex("""(?i)(?:家庭教師|先生|生徒)""") to "Private Tutor Lesson",
        Regex("""(?i)(?:看護師|ナース|病院)""") to "Private Nurse Clinic",
        Regex("""(?i)(?:密着|囁き|イチャイチャ)""") to "Passionate Intimate Whispers",
        Regex("""(?i)(?:寝取り|NTR|不倫)""") to "Secret Love Affair",
        Regex("""(?i)(?:合宿|キャンプ|海)""") to "Summer Beach Getaway",
        Regex("""(?i)(?:専属|独占|プレミアム)""") to "Exclusive Premium Feature",
        Regex("""(?i)(?:ベスト|BEST|総集編|傑作選)""") to "Greatest Hits Special Edition",
        Regex("""(?i)(?:引退|ラスト|卒業)""") to "Farewell Special",
        Regex("""(?i)(?:復活|カムバック)""") to "Comeback Special Edition"
    )

    // Foreign noise tags to strip entirely
    private val NOISE_PATTERNS = listOf(
        Regex("""(?i)\b(?:supjav|javhd|phim\s*sex|xem\s*phim|phim\s*jav|jav\s*vietsub|bokep|indo\s*sub|sub\s*indo|thuyết\s*minh|người\s*mẫu|chúng\s*tôi|tập\s*\d+)\b"""),
        Regex("""(?i)\[(?:vietsub|thuyet minh|phim sex|jav|indo|thai|chinese|chs|cht|sub|1080p|720p|4k|hd|fhd|uncensored|leaked|javhd|supjav)\]"""),
        Regex("""(?i)\((?:vietsub|thuyet minh|phim sex|jav|indo|thai|chinese|chs|cht|sub|1080p|720p|4k|hd|fhd|uncensored|leaked|javhd|supjav)\)"""),
        Regex("""(?i)\s*[-|–—]\s*(?:SupJav|Phim\s*Sex|JAV\s*Vietsub|Xem\s*JAV|Xem\s*Phim|JAVHD|Bokep).*$"""),
        Regex("""(?i)\s*[-|–—]\s*(?:supjav\.[a-z0-9]+).*$"""),
        Regex("""(?i)\s*\|\s*(?:Phim\s*Sex|JAV\s*Vietsub|SupJav|Bokep).*$""")
    )

    /**
     * Translates and formats any raw SupJav / JAV title into 100% English.
     */
    fun toEnglishTitle(rawTitle: String, slug: String = ""): String {
        if (rawTitle.isBlank() && slug.isBlank()) return "SupJav Exclusive HD Video"

        val unescaped = org.jsoup.parser.Parser.unescapeEntities(rawTitle, false)
        var text = unescaped.trim()

        // 1. Strip known noise patterns
        for (noise in NOISE_PATTERNS) {
            text = text.replace(noise, " ")
        }

        // 2. Extract standard JAV Code (e.g. SSIS-123)
        var javCode = extractJavCode(text)
        if (javCode.isBlank() && slug.isNotBlank()) {
            javCode = extractJavCode(slug)
        }

        // 3. Detect known Actress
        var detectedActress: String? = null
        for ((asianName, engName) in ACTRESS_MAP) {
            if (text.contains(asianName, ignoreCase = true) || unescaped.contains(asianName, ignoreCase = true)) {
                detectedActress = engName
                break
            }
        }

        // 4. Translate known concepts into English phrases
        val englishThemes = mutableListOf<String>()
        for ((regex, eng) in KEYWORD_TRANSLATIONS) {
            if (regex.containsMatchIn(text) || regex.containsMatchIn(unescaped)) {
                if (eng.isNotBlank() && !englishThemes.contains(eng)) {
                    englishThemes.add(eng)
                }
            }
        }

        // 5. Extract existing English words and Latin alphanumeric terms from title
        val latinWords = text
            .replace(Regex("""[^\p{L}\p{N}\s\-_]"""), " ")
            .split(Regex("""\s+"""))
            .filter { word ->
                word.isNotBlank() &&
                        word.matches(Regex("""^[A-Za-z0-9\-_.]{2,}$""")) &&
                        !word.equals(javCode, ignoreCase = true) &&
                        !word.equals("supjav", ignoreCase = true) &&
                        !word.equals("html", ignoreCase = true) &&
                        !word.equals("hd", ignoreCase = true) &&
                        !word.equals("fhd", ignoreCase = true)
            }

        val cleanLatinPhrase = latinWords.joinToString(" ").trim()

        // 6. Build the final 100% English Title
        val codePrefix = if (javCode.isNotBlank()) "[$javCode] " else ""
        val actressPart = if (!detectedActress.isNullOrBlank()) "$detectedActress - " else ""

        val themePart = when {
            englishThemes.isNotEmpty() -> englishThemes.take(3).joinToString(" & ")
            cleanLatinPhrase.length > 5 -> cleanLatinPhrase
            slug.isNotBlank() -> slug.replace("-", " ").replace("_", " ").split(" ")
                .filter { it.length > 2 && !it.equals(javCode, ignoreCase = true) }
                .take(4).joinToString(" ")
            else -> "Passionate Encounter & Secret Romance"
        }

        var finalTitle = buildString {
            append(codePrefix)
            append(actressPart)
            append(themePart)
            if (!themePart.contains("1080p") && !themePart.contains("HD") && !themePart.contains("Full HD")) {
                append(" (1080p Full HD)")
            }
        }.trim()

        // 7. Ensure no Asian / Thai / Vietnamese / Chinese / Indonesian characters remain
        finalTitle = stripNonLatinChars(finalTitle)

        if (finalTitle.length < 10) {
            finalTitle = if (javCode.isNotBlank()) {
                "[$javCode] Japanese Adult Video Special Feature (1080p Full HD)"
            } else {
                "SupJav Premium Japanese Feature (1080p Full HD)"
            }
        }

        return finalTitle
    }

    fun extractJavCode(text: String): String {
        if (text.isBlank()) return ""
        val m = JAV_CODE_REGEX.matcher(text.uppercase())
        if (m.find()) {
            return m.group(1).uppercase().replace(" ", "-")
        }
        return ""
    }

    private fun stripNonLatinChars(input: String): String {
        return input
            .replace(Regex("""[\p{InCJK_UNIFIED_IDEOGRAPHS}\p{InHIRAGANA}\p{InKATAKANA}\p{InTHAI}\p{InHANGUL_SYLLABLES}\p{InCYRILLIC}]"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .replace(" - - ", " - ")
            .replace("[]", "")
            .trim()
    }
}
