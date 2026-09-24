package com.example.util

import com.example.model.VideoItem
import java.util.Locale

object SmartTagExtractor {

    data class TagInfo(
        val category: String,
        val displayName: String,
        val emoji: String,
        val priority: Int = 100
    )

    data class SmartTagChip(
        val key: String,
        val label: String,
        val emoji: String,
        val count: Int
    )

    private val HASHTAG_REGEX = Regex("""#([a-zA-Z0-9_\-]+)""")

    /**
     * Extracts hashtags from text.
     */
    fun extractHashtags(vararg texts: String?): Set<String> {
        val result = mutableSetOf<String>()
        for (text in texts) {
            if (!text.isNullOrBlank()) {
                HASHTAG_REGEX.findAll(text).forEach { match ->
                    val tag = match.groupValues[1].lowercase(Locale.ROOT)
                    if (tag.isNotBlank()) {
                        result.add(tag)
                    }
                }
            }
        }
        return result
    }

    private fun String.hasWord(vararg words: String): Boolean {
        return words.any { word ->
            Regex("""(?i)\b${Regex.escape(word)}\b""").containsMatchIn(this)
        }
    }

    private fun String.hasPhrase(vararg phrases: String): Boolean {
        return phrases.any { phrase ->
            Regex("""(?i)\b${Regex.escape(phrase)}\b""").containsMatchIn(this)
        }
    }

    /**
     * Extracts ALL internal categories and semantic tags without arbitrary limits.
     * Used exclusively by the AI Intelligence and Smart Recommendation Engine.
     */
    fun extractInternalCategoryTags(video: VideoItem): List<TagInfo> {
        val detected = detectAllCategories(video)
        return filterAndSortTags(detected, maxTags = Int.MAX_VALUE)
    }

    /**
     * Extracts high-accuracy semantic tags for UI presentation (Watch Later, Playlists, Badges).
     * Defaults to the top [maxTags] (up to 4) for rich, comprehensive layouts.
     */
    fun extractTags(video: VideoItem, maxTags: Int = 4): List<TagInfo> {
        val detected = detectAllCategories(video)
        return filterAndSortTags(detected, maxTags = maxTags)
    }

    /**
     * Filters out conflicting tags and ensures high-priority specific tags surface first.
     */
    private val sourceKeywords = setOf(
        "youtube", "tencent", "bilibili", "dailymotion", "twitch", "hotstar", "sonyliv",
        "disney", "netflix", "crunchyroll", "v.qq.com", "v_qq_com", "qq", "vqqcom",
        "bunkr", "telegram", "mega", "bun-tel-meg", "xnxx", "hellporno", "stripchat",
        "chaturbate", "motherless", "txxx", "pornhub", "xvideos", "spankbang", "supjav",
        "123av", "javtiful", "hanime1", "rule34video", "pmvhaven", "piped", "invidious",
        "hianime", "aniwatch", "popcorntv", "amazonminitv", "bigo", "kick", "rumble",
        "vimeo", "soundcloud", "bandcamp", "tiktok"
    )

    private fun filterAndSortTags(detected: List<TagInfo>, maxTags: Int): List<TagInfo> {
        val distinct = detected.distinctBy { it.category }
            .filter {
                val cat = it.category.lowercase(Locale.ROOT)
                val disp = it.displayName.lowercase(Locale.ROOT)
                !cat.contains("torrent") &&
                !disp.contains("torrent") &&
                cat != "video" &&
                cat !in sourceKeywords &&
                disp !in sourceKeywords &&
                !cat.contains(".com") && !disp.contains(".com") &&
                !cat.contains(".org") && !disp.contains(".org") &&
                !cat.contains(".net") && !disp.contains(".net") &&
                !cat.contains("v.qq") && !disp.contains("v.qq")
            }

        val hasTrailer = distinct.any { it.category in setOf("trailer", "movie_trailer", "gameplay_trailer", "anime_trailer") }
        val hasSeries = distinct.any { it.category == "series" }
        val hasGameplay = distinct.any { it.category == "gameplay" }
        val hasSong = distinct.any { it.category == "song" }
        val hasShortFilm = distinct.any { it.category == "short_film" }

        val filtered = distinct.filter { tag ->
            when {
                // If it's a trailer, it's not the full movie!
                hasTrailer && tag.category in setOf("movie", "classic_cinema") -> false
                // If it's a series / web series, it's not a standalone movie!
                hasSeries && tag.category in setOf("movie", "classic_cinema") -> false
                // If gameplay is present, drop generic gaming so Gameplay is front and center
                hasGameplay && tag.category == "gaming" -> false
                // If song is present, drop redundant generic "music" tag (keep specific genre like lo-fi, hip-hop, acoustic)
                hasSong && tag.category == "music" -> false
                // If short film is present, drop generic movie tag
                hasShortFilm && tag.category == "movie" -> false
                else -> true
            }
        }

        return filtered.sortedBy { it.priority }.take(maxTags)
    }

    /**
     * Core detection engine parsing title, description, uploader, provider, explicit tags, and hashtags.
     */
    private fun detectAllCategories(video: VideoItem): List<TagInfo> {
        val title = video.title ?: ""
        val titleLower = title.lowercase(Locale.ROOT)
        val uploaderLower = (video.uploaderName ?: "").lowercase(Locale.ROOT)
        val descriptionLower = (video.description ?: "").lowercase(Locale.ROOT)
        val providerLower = (video.providerId ?: "").lowercase(Locale.ROOT)
        val rawTagsString = video.tags.joinToString(" ").lowercase(Locale.ROOT)
        val fullText = "$titleLower $uploaderLower $descriptionLower $rawTagsString $providerLower"

        val hashtags = extractHashtags(title, video.description, rawTagsString)

        val detected = mutableListOf<TagInfo>()

        // 0. Adult / 18+ / Erotic Sources & Content
        val isAdultProvider = providerLower in setOf(
            "pornhub", "xvideos", "youporn", "xhamster",
            "rule34video", "hanime1", "redtube", "tube8", "coomer", "pmvhaven", "eporner", "txxx", "motherless", "stripchat", "chaturbate", "sextb", "supjav", "123av", "hellporno"
        )
        val hasAdultKeywords = titleLower.hasWord("porn", "xxx", "hentai", "jav", "erotic", "nsfw", "uncensored", "creampie", "milf", "bdsm", "fetish", "pmv", "doujin", "lewd", "stripchat", "chaturbate") ||
                titleLower.contains("18+") || titleLower.contains("成人") || titleLower.contains("福利") || titleLower.contains("无码") ||
                hashtags.any { it in setOf("porn", "hentai", "nsfw", "erotic", "xxx", "18plus", "18+", "jav", "adult") }

        if (isAdultProvider || hasAdultKeywords) {
            when {
                titleLower.contains("hentai") || hashtags.contains("hentai") || providerLower in setOf("hanime1", "rule34video") -> {
                    detected.add(TagInfo("hentai", "Hentai & 2D", "🔞", 1))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 2))
                }
                titleLower.contains("pmv") || hashtags.contains("pmv") || providerLower == "pmvhaven" -> {
                    detected.add(TagInfo("pmv", "PMV & Music Edit", "🔥", 1))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 2))
                }
                titleLower.contains("bdsm") || titleLower.contains("fetish") || hashtags.any { it in setOf("fetish", "bdsm") } -> {
                    detected.add(TagInfo("fetish", "Specialty", "🌶️", 1))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 2))
                }
                else -> {
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 1))
                }
            }
        }

        // ==========================================
        // 1. TRAILERS & TEASERS (Highest Priority Precision)
        // ==========================================
        val hasTrailerHashtag = hashtags.any {
            it in setOf(
                "trailer", "trailers", "officialtrailer", "teasertrailer", "teaser", "gametrailer",
                "movietrailer", "sneakpeek", "firstlook", "launchtrailer", "storytrailer", "tvspot",
                "announcementtrailer", "gameplaytrailer", "finaltrailer", "animetrailer", "reveal", "promo"
            )
        }

        val hasTrailerPhrase = titleLower.hasPhrase(
            "official trailer", "teaser trailer", "final trailer", "main trailer",
            "launch trailer", "story trailer", "announcement trailer", "gameplay trailer",
            "reveal trailer", "cinematic trailer", "red band trailer", "green band trailer",
            "extended trailer", "exclusive trailer", "official teaser", "first look",
            "sneak peek", "tv spot", "exclusive preview", "official preview", "trailer 1",
            "trailer 2", "trailer 3", "trailer #1", "trailer #2", "official pv", "teaser pv",
            "main pv", "character pv"
        ) || titleLower.contains("预告片") || titleLower.contains("片花") || titleLower.contains("预告")

        val hasTrailerWord = titleLower.hasWord("trailer", "teaser", "tvspot", "promo") ||
                Regex("""(?i)\b(pv\s*\d+|pv\b)""").containsMatchIn(titleLower) && (titleLower.contains("anime") || titleLower.contains("official") || titleLower.contains("pv"))

        val isTrailer = hasTrailerHashtag || hasTrailerPhrase || hasTrailerWord

        if (isTrailer) {
            val isGameRelated = titleLower.hasWord("gameplay", "game", "ps5", "xbox", "pc", "switch", "nintendo", "playstation", "steam", "dlc") ||
                    titleLower.hasPhrase("game trailer", "launch trailer", "gameplay trailer", "story trailer") ||
                    hashtags.any { it in setOf("gametrailer", "gameplaytrailer", "gaming") }

            val isAnimeRelated = titleLower.hasWord("anime", "manga", "shonen", "season", "episode", "pv") ||
                    titleLower.contains("动漫") || titleLower.contains("动画") ||
                    hashtags.any { it in setOf("anime", "animetrailer", "pv") }

            val isMovieRelated = titleLower.hasPhrase("movie trailer", "official movie trailer", "theatrical trailer") ||
                    titleLower.hasWord("movie", "film", "cinema", "theaters", "cinemas") ||
                    uploaderLower.contains("pictures") || uploaderLower.contains("studios") || uploaderLower.contains("entertainment") ||
                    hashtags.any { it in setOf("movietrailer", "film") }

            when {
                isGameRelated -> detected.add(TagInfo("gameplay_trailer", "Game Trailer", "🎮", 1))
                isAnimeRelated -> detected.add(TagInfo("anime_trailer", "Anime Trailer", "🎌", 1))
                isMovieRelated -> detected.add(TagInfo("movie_trailer", "Movie Trailer", "🎬", 1))
                else -> detected.add(TagInfo("trailer", "Trailer", "🎬", 1))
            }
        }

        // ==========================================
        // 2. REACTION & COMMENTARY
        // ==========================================
        val isReaction = !isTrailer && (
            titleLower.hasPhrase("reaction", "reacts to", "reacting to", "react to", "first time watching", "first time hearing", "first time listening", "live reaction", "blind reaction", "commentary & reaction") ||
            titleLower.hasWord("reaction", "reacts", "reacting", "recap", "breakdown", "commentary") ||
            titleLower.contains("反应") || titleLower.contains("解说") || titleLower.contains("吐槽") || titleLower.contains("锐评") || titleLower.contains("速看") ||
            hashtags.any { it in setOf("reaction", "reacts", "react", "breakdown", "recap", "commentary") }
        )
        if (isReaction) {
            detected.add(TagInfo("reaction", "Reaction", "😲", 2))
        }

        // ==========================================
        // 3. SERIES, TV SHOWS & EPISODES
        // ==========================================
        val hasEpisodePattern = Regex("""(?i)\b(ep\s*\d+|ep\.\s*\d+|episode\s*\d+|e\d{1,3}|s\d{1,2}\s*e\d{1,3}|s\d{1,2})\b""").containsMatchIn(titleLower)
        val hasChineseSeries = titleLower.contains("系列") || titleLower.contains("电视剧") || titleLower.contains("连续剧") || titleLower.contains("剧集") ||
                Regex("""(\d+-\d+集|\d+集|第\s*\d+\s*集|全集)""").containsMatchIn(titleLower)
        val isSeries = !isTrailer && (
            titleLower.hasPhrase("web series", "tv series", "tv show", "original series", "mini series", "miniseries", "special series", "drama series", "k-drama", "c-drama", "j-drama", "complete series", "full episode") ||
            titleLower.hasWord("series", "episodes", "episode", "season", "seasons", "kdrama", "cdrama") ||
            hasEpisodePattern ||
            hasChineseSeries ||
            hashtags.any { it in setOf("series", "webseries", "tvseries", "tvshow", "episode", "season", "drama", "kdrama", "cdrama") }
        )
        if (isSeries) {
            detected.add(TagInfo("series", "Series", "📺", 2))
        }

        // ==========================================
        // 4. ANIME & DONGHUA
        // ==========================================
        val hasAnimeTitle = titleLower.hasWord(
            "anime", "manga", "shonen", "seinen", "isekai", "amv", "otaku", "sub", "dub", "donghua",
            "crunchyroll", "hianime", "aniwatch", "frieren", "naruto", "bleach", "haikyuu", "boruto",
            "gintama", "inuyasha", "jojo", "evangelion", "pokemon", "digimon", "beyblade"
        ) || titleLower.hasPhrase(
            "soul land", "one piece", "jujutsu kaisen", "demon slayer", "chainsaw man",
            "solo leveling", "attack on titan", "dragon ball", "my hero academia", "death note",
            "hunter x hunter", "sword art online", "black clover", "fairy tail", "spy x family",
            "vinland saga", "blue lock", "kaiju no 8", "dan da dan", "eng sub"
        ) || titleLower.contains("斗罗大陆") || titleLower.contains("完美世界") || titleLower.contains("吞噬星空") ||
           titleLower.contains("遮天") || titleLower.contains("凡人修仙传") || titleLower.contains("画江湖") ||
           titleLower.contains("武动乾坤") || titleLower.contains("全职高手") || titleLower.contains("魔道祖师") ||
           titleLower.contains("天官赐福") || titleLower.contains("一人之下") || titleLower.contains("狐妖小红娘") ||
           titleLower.contains("刺客伍六七") || titleLower.contains("动漫") || titleLower.contains("国漫") || titleLower.contains("番剧")
        
        val isAnime = (hasAnimeTitle || providerLower in setOf("hianime", "aniwatch") || hashtags.any { it in setOf("anime", "manga", "amv", "otaku", "donghua") }) && !isTrailer
        if (isAnime) {
            detected.add(TagInfo("anime", "Anime", "🎌", 2))
        }

        // ==========================================
        // 5. ANIMATION & CARTOONS
        // ==========================================
        val isAnimation = !isTrailer && (
            titleLower.hasWord("animation", "animated", "cartoon", "cartoons", "cgi") ||
            titleLower.hasPhrase("3d animation", "2d animation", "animated movie", "animated series", "stop motion") ||
            titleLower.hasWord("pixar", "dreamworks", "illumination", "nickelodeon") ||
            titleLower.hasPhrase("cartoon network", "warner bros animation", "looney tunes", "tom and jerry", "spongebob", "rick and morty", "south park", "simpsons", "family guy", "spider-verse") ||
            titleLower.contains("动画") || titleLower.contains("动画片") || titleLower.contains("卡通") ||
            hashtags.any { it in setOf("animation", "animated", "cartoon", "cartoons", "cgi") }
        )
        if (isAnimation) {
            detected.add(TagInfo("animation", "Animation", "🎨", 3))
        }

        // ==========================================
        // 6. TECH, AI & SOFTWARE
        // ==========================================
        val hasAiKeyword = titleLower.hasWord("ai", "chatgpt", "gemini", "openai", "claude", "coding", "programming", "python", "kotlin", "java", "developer", "software", "gpu", "nvidia", "intel", "amd", "gadgets", "smartphone", "laptop") ||
                titleLower.hasPhrase("artificial intelligence", "machine learning", "deep learning", "software engineering", "tech review", "gadget review") ||
                titleLower.contains("【ai") || titleLower.contains("ai】") || titleLower.contains("ai ") || titleLower.contains(" ai") || titleLower.contains("科技") || titleLower.contains("人工智能") || titleLower.contains("编程") ||
                hashtags.any { it in setOf("tech", "technology", "ai", "coding", "programming", "gadgets", "software", "developer") }
        val isTech = !isTrailer && hasAiKeyword
        if (isTech) {
            detected.add(TagInfo("tech", "Tech & AI", "💻", 3))
        }

        // ==========================================
        // 7. COMPILATIONS & TOP 10 / BEST OF
        // ==========================================
        val isCompilation = !isTrailer && (
            titleLower.hasPhrase("top 10", "top 5", "top 20", "top 50", "top 100", "best of", "countdown", "must watch", "must-watch", "all episodes") ||
            titleLower.hasWord("compilation", "countdown", "highlights") ||
            titleLower.contains("合集") || titleLower.contains("全集") || titleLower.contains("精选") || titleLower.contains("盘点") || titleLower.contains("榜单") ||
            hashtags.any { it in setOf("top10", "top5", "compilation", "bestof", "countdown") }
        )
        if (isCompilation) {
            detected.add(TagInfo("compilation", "Top 10 & Best", "🏆", 3))
        }

        // ==========================================
        // 8. NEWS & JOURNALISM
        // ==========================================
        val isJournalismChannel = uploaderLower.contains("new yorker") || uploaderLower.contains("the new yorker") ||
                uploaderLower.contains("bbc") || uploaderLower.contains("cnn") || uploaderLower.contains("reuters") ||
                uploaderLower.contains("al jazeera") || uploaderLower.contains("bloomberg") || uploaderLower.contains("guardian") ||
                uploaderLower.contains("vox") || uploaderLower.contains("vice") || uploaderLower.contains("associated press") ||
                uploaderLower.contains("dw news") || uploaderLower.contains("france 24") || uploaderLower.contains("ndtv") ||
                uploaderLower.hasWord("news", "journalism")
        val isNews = !isTrailer && (
            isJournalismChannel ||
            titleLower.hasPhrase("breaking news", "press conference", "live coverage", "special report", "the reality of", "the truth about", "investigation") ||
            titleLower.hasWord("news", "headline", "headlines", "geopolitics", "journalism") ||
            titleLower.contains("新闻") || titleLower.contains("时事") || titleLower.contains("报道") ||
            hashtags.any { it in setOf("news", "breakingnews", "worldnews", "journalism") }
        )
        if (isNews) {
            detected.add(TagInfo("news", "News", "📰", 3))
        }

        // ==========================================
        // 9. GAMEPLAY & GAMING
        // ==========================================
        val hasGameplayHashtag = hashtags.any {
            it in setOf(
                "gameplay", "gameplaywalkthrough", "walkthrough", "playthrough", "letsplay",
                "speedrun", "bossfight", "nohit", "nocommentary", "gamestream", "ps5gameplay",
                "pcgameplay", "xboxgameplay", "longplay", "gaming", "gamer", "esports"
            )
        }

        val hasGameplayPhrase = titleLower.hasPhrase(
            "gameplay walkthrough", "full walkthrough", "let's play", "lets play",
            "no commentary", "boss fight", "speedrun", "longplay", "playtest",
            "pc gameplay", "ps5 gameplay", "4k gameplay", "60fps gameplay", "ranked match",
            "multiplayer gameplay", "game test", "fps test", "graphics comparison"
        ) || titleLower.contains("游戏实况") || titleLower.contains("通关") || titleLower.contains("无解说")

        val hasGameplayWord = titleLower.hasWord(
            "gameplay", "walkthrough", "playthrough", "speedrun", "longplay", "playtest"
        )

        val isGamingTitle = titleLower.hasWord(
            "minecraft", "gta", "fortnite", "roblox", "valorant", "apex", "zelda",
            "genshin", "pokemon", "dota", "overwatch", "cs2", "pubg"
        ) || titleLower.hasPhrase(
            "grand theft auto", "call of duty", "warzone", "elden ring", "dark souls",
            "cyberpunk 2077", "resident evil", "god of war", "ea sports fc", "league of legends",
            "counter-strike", "black myth wukong", "mobile legends"
        ) || titleLower.contains("游戏")

        val hasGameContext = isGamingTitle && (
            titleLower.hasWord("part", "ep", "survival", "hardcore", "mod", "quest", "mission", "boss") ||
            video.durationSeconds >= 600
        )

        val isGameplay = !isTrailer && (hasGameplayHashtag || hasGameplayPhrase || hasGameplayWord || hasGameContext)

        if (isGameplay) {
            val isExplicitGameplay = hasGameplayWord || hasGameplayPhrase ||
                    hashtags.any { it in setOf("gameplay", "walkthrough", "playthrough", "letsplay", "speedrun", "nocommentary") }

            if (isExplicitGameplay) {
                detected.add(TagInfo("gameplay", "Gameplay", "🕹️", 2))
            } else {
                detected.add(TagInfo("gaming", "Gaming", "🎮", 3))
            }

            if (titleLower.hasWord("esports", "tournament", "championship") || hashtags.contains("esports")) {
                detected.add(TagInfo("esports", "Esports", "🏆", 3))
            }
        }

        // ==========================================
        // 10. SONGS & MUSIC (High-Precision User Intent)
        // ==========================================
        val featRegex = Regex("""(?i)\b(ft\.?|feat\.?|featuring)\b""")
        val prodRegex = Regex("""(?i)\b(prod\.?|produced\s+by|prod\s+by)\b""")
        val hasFeat = featRegex.containsMatchIn(title) || featRegex.containsMatchIn(descriptionLower)
        val hasProd = prodRegex.containsMatchIn(title) || prodRegex.containsMatchIn(descriptionLower)
        val hasArtistDashTitle = title.contains(" - ") || title.contains(" – ") || title.contains(" — ")

        val hasSongHashtag = hashtags.any {
            it in setOf(
                "song", "songs", "newsong", "music", "musicvideo", "officialvideo",
                "officialmusicvideo", "officialaudio", "audio", "audiotrack", "lyrics",
                "lyricvideo", "soundtrack", "ost", "lofi", "lo-fi", "remix", "acoustic",
                "cover", "singing", "singer", "gana", "geet", "bhajan", "naat", "qawwali",
                "kpop", "hiphop", "rap", "rock", "edm", "beats", "trap", "phonk", "slowed"
            )
        }

        val hasMusicSuffix = titleLower.hasPhrase(
            "official music video", "official video", "music video", "official audio",
            "audio track", "lyric video", "lyrics video", "official visualizer", "visualizer",
            "audio song", "video song", "new song", "full song", "title track", "full audio",
            "slowed + reverb", "slowed and reverb", "sped up", "nightcore", "bass boosted",
            "piano cover", "guitar cover", "live acoustic", "live concert", "live performance"
        ) || titleLower.contains("[mv]") || titleLower.contains("(mv)") ||
           titleLower.contains("[m/v]") || titleLower.contains("(m/v)") ||
           titleLower.contains("[audio]") || titleLower.contains("(audio)") ||
           titleLower.contains("[lyrics]") || titleLower.contains("(lyrics)") ||
           titleLower.contains("[official audio]") || titleLower.contains("[official video]") ||
           titleLower.contains("歌曲") || titleLower.contains("单曲")

        val hasMusicKeyword = titleLower.hasWord(
            "song", "songs", "gana", "geet", "singing", "singer", "soundtrack",
            "ost", "album", "single", "track", "lyrics", "remix", "acoustic", "unplugged",
            "lofi", "lo-fi", "synthwave", "phonk", "afrobeats", "afropop", "hip-hop",
            "rapper", "rnb", "kpop", "jpop", "reggae", "vocals", "mashup"
        ) || titleLower.hasPhrase("hip hop", "chill beats", "live concert", "live session", "live at", "k-pop", "j-pop", "house music") ||
           titleLower.contains("音乐")

        val isMusicChannel = uploaderLower.endsWith(" - topic") || uploaderLower.contains("vevo") ||
                uploaderLower.hasWord("records", "music", "audio", "sound") ||
                uploaderLower.contains("t-series") || uploaderLower.contains("tseries") ||
                uploaderLower.contains("zee music") || uploaderLower.contains("sony music") ||
                uploaderLower.contains("speed records") || uploaderLower.contains("saregama") ||
                uploaderLower.contains("yrf") || uploaderLower.contains("spinnin") ||
                uploaderLower.contains("monstercat") || uploaderLower.contains("warner") ||
                uploaderLower.contains("atlantic") || uploaderLower.contains("interscope") ||
                uploaderLower.contains("hybe") || uploaderLower.contains("bighit")

        val isKnownMusicArtist = titleLower.hasWord(
            "drake", "eminem", "kanye", "bts", "blackpink", "coldplay", "sza",
            "badshah", "diljit", "pritam", "anirudh", "adele", "marshmello"
        ) || titleLower.hasPhrase(
            "burna boy", "taylor swift", "the weeknd", "travis scott", "kendrick lamar",
            "billie eilish", "post malone", "dua lipa", "ed sheeran", "justin bieber",
            "arijit singh", "sidhu moose wala", "ar rahman", "shreya ghoshal", "karan aujla",
            "ap dhillon", "bruno mars", "alan walker"
        )

        val isSongStructural = hasArtistDashTitle && (
            hasFeat || hasProd || hasMusicSuffix || hasMusicKeyword || isMusicChannel || isKnownMusicArtist ||
            (video.durationSeconds in 45..600 && !titleLower.hasWord("news", "review", "episode", "tutorial", "gameplay", "walkthrough", "analysis", "podcast"))
        )

        val isMusicOrSong = !isTrailer && (hasSongHashtag || hasMusicSuffix || hasMusicKeyword || isMusicChannel || isKnownMusicArtist || isSongStructural)

        if (isMusicOrSong) {
            val isExplicitSong = hasSongHashtag || hasMusicSuffix || titleLower.hasWord("song", "songs", "track", "single", "gana", "geet", "remix", "acoustic", "lofi") ||
                    hasFeat || hasProd || titleLower.contains("[mv]") || titleLower.contains("(mv)")

            if (isExplicitSong) {
                detected.add(TagInfo("song", "Song", "🎵", 2))
            } else {
                detected.add(TagInfo("music", "Music", "🎧", 3))
            }

            // Sub-genre identification for richer, ultra-accurate context
            when {
                titleLower.hasWord("lofi", "lo-fi") || titleLower.hasPhrase("chill beats", "study beats") || hashtags.any { it in setOf("lofi", "lofitrack") } ->
                    detected.add(TagInfo("lofi", "Lo-Fi", "☕", 2))
                titleLower.hasPhrase("hip hop", "hip-hop") || titleLower.hasWord("rap", "trap", "freestyle", "drill") || hashtags.any { it in setOf("hiphop", "rap") } ->
                    detected.add(TagInfo("hip_hop", "Hip-Hop", "🎤", 2))
                titleLower.hasWord("acoustic", "unplugged") || titleLower.hasPhrase("piano cover", "guitar cover") || hashtags.contains("acoustic") ->
                    detected.add(TagInfo("acoustic", "Acoustic", "🎻", 2))
                titleLower.hasWord("soundtrack", "ost", "score") || hashtags.any { it in setOf("soundtrack", "ost") } ->
                    detected.add(TagInfo("soundtrack", "Soundtrack", "🎼", 2))
                titleLower.hasWord("edm", "techno", "phonk", "electronic") || titleLower.hasPhrase("house music") || hashtags.any { it in setOf("edm", "phonk") } ->
                    detected.add(TagInfo("edm", "EDM & Beats", "⚡", 2))
                titleLower.hasWord("rock", "metal", "punk", "grunge") || hashtags.contains("rock") ->
                    detected.add(TagInfo("rock", "Rock", "🎸", 2))
                titleLower.hasWord("kpop", "bts", "blackpink", "twice", "newjeans") || titleLower.hasPhrase("k-pop") || hashtags.contains("kpop") ->
                    detected.add(TagInfo("kpop", "K-Pop", "🌟", 2))
            }
        }

        // ==========================================
        // 11. MOVIES & CINEMA (Only genuine movies, not series/reviews/clips/top10)
        // ==========================================
        val hasMovieHashtag = hashtags.any {
            it in setOf(
                "movie", "movies", "fullmovie", "cinema", "film", "films", "shortfilm",
                "featurefilm", "fullfilm", "hindimovie", "telugumovie", "tamilmovie",
                "hollywoodmovie", "bollywoodmovie", "indiefilm"
            )
        }

        val hasMoviePhrase = titleLower.hasPhrase(
            "full movie", "entire movie", "full length movie", "full film", "feature film",
            "motion picture", "short film", "independent film", "indie film",
            "complete movie", "dubbed movie"
        ) || titleLower.contains("[full movie]") || titleLower.contains("(full movie)") ||
           titleLower.contains("[full hd movie]") || titleLower.contains("(full hd movie)") ||
           titleLower.contains("电影") || titleLower.contains("正片")

        val yearPattern = Regex("\\((19\\d{2}|20\\d{2})\\)")
        val hasYear = yearPattern.containsMatchIn(title)
        val isArchive = uploaderLower.contains("archive") || fullText.contains("internet archive")

        // Exclusions to avoid tagging series, compilations, clips, reactions, reviews, or songs as movies
        val hasMovieExclusions = isTrailer || isMusicOrSong || isSeries || isCompilation || isReaction ||
                titleLower.hasWord("review", "reaction", "scene", "clip", "breakdown", "recap", "ending", "explained", "web series", "series", "episode", "ep") ||
                titleLower.hasPhrase("behind the scenes", "blooper", "making of", "deleted scene", "top 10", "top 5", "special series")

        val isMovie = !hasMovieExclusions && (
            hasMoviePhrase ||
            (hasMovieHashtag && !titleLower.contains("series")) ||
            (isArchive && (titleLower.hasWord("movie", "film") || video.durationSeconds >= 2400)) ||
            (hasYear && (titleLower.hasWord("movie", "film", "cinema")) && (video.durationSeconds >= 1800 || video.durationSeconds == 0L))
        )

        if (isMovie) {
            if (titleLower.hasPhrase("short film") || hashtags.contains("shortfilm")) {
                detected.add(TagInfo("short_film", "Short Film", "🎬", 3))
            } else {
                detected.add(TagInfo("movie", "Movie", "🍿", 3))
            }
        }

        // ==========================================
        // 12. COMEDY & STAND-UP / FUNNY
        // ==========================================
        val isComedy = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("stand up", "stand-up", "comedy sketch", "funny moments", "try not to laugh") ||
            titleLower.hasWord("comedy", "funny", "parody", "prank", "roast", "meme", "memes", "hilarious", "bloopers", "fails") ||
            titleLower.contains("搞笑") || titleLower.contains("幽默") || titleLower.contains("段子") || titleLower.contains("恶搞") || titleLower.contains("喜剧") ||
            hashtags.any { it in setOf("comedy", "standup", "funny", "meme", "prank", "humor") }
        )
        if (isComedy) {
            detected.add(TagInfo("comedy", "Funny & Comedy", "😂", 3))
        }

        // ==========================================
        // 13. DOCUMENTARY & KNOWLEDGE
        // ==========================================
        val isDoc = !isTrailer && !isMusicOrSong && (
            titleLower.hasWord("documentary", "docuseries", "investigation", "biography") ||
            titleLower.hasPhrase("untold story", "the story of", "history of", "rise and fall", "the reality of", "true story") ||
            uploaderLower.contains("national geographic") || uploaderLower.contains("discovery") || uploaderLower.contains("bbc earth") ||
            titleLower.contains("纪录片") || titleLower.contains("纪实") ||
            hashtags.any { it in setOf("documentary", "docuseries", "history", "biography") }
        )
        if (isDoc) {
            detected.add(TagInfo("documentary", "Documentary", "📽️", 3))
        }

        // ==========================================
        // 14. TUTORIALS, GUIDES & HOW-TO
        // ==========================================
        val isTutorial = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("how to", "step by step", "beginner's guide", "beginners guide", "crash course", "tips & tricks", "tips and tricks", "complete guide", "for beginners") ||
            titleLower.hasWord("tutorial", "guide", "masterclass", "learn", "course") ||
            titleLower.contains("教程") || titleLower.contains("教学") || titleLower.contains("指南") ||
            hashtags.any { it in setOf("tutorial", "howto", "guide", "learn", "stepbystep", "coding", "programming") }
        )
        if (isTutorial) {
            detected.add(TagInfo("tutorial", "Tutorial", "💡", 3))
        }

        // ==========================================
        // 15. REVIEWS & UNBOXING
        // ==========================================
        val isUnboxing = titleLower.hasWord("unboxing") || titleLower.contains("开箱") || hashtags.contains("unboxing")
        val isReview = !isTrailer && (
            titleLower.hasPhrase("before you buy", "hands on", "hands-on", "is it worth it", "in-depth review", "honest review") ||
            titleLower.hasWord("review", "unboxing") ||
            titleLower.contains("测评") || titleLower.contains("评测") ||
            hashtags.any { it in setOf("review", "unboxing", "handson") }
        )
        if (isUnboxing) {
            detected.add(TagInfo("unboxing", "Unboxing", "📦", 3))
        } else if (isReview) {
            detected.add(TagInfo("review", "Review", "⭐", 3))
        }

        // ==========================================
        // 16. PODCASTS & INTERVIEWS
        // ==========================================
        val isPodcast = !isTrailer && !isMusicOrSong && (
            titleLower.hasWord("podcast", "interview") ||
            titleLower.hasPhrase("talk show", "full interview", "in conversation with") ||
            uploaderLower.contains("podcast") ||
            titleLower.contains("播客") || titleLower.contains("访谈") ||
            hashtags.any { it in setOf("podcast", "interview", "talkshow") }
        )
        if (isPodcast) {
            detected.add(TagInfo("podcast", "Podcast", "🎙️", 3))
        }

        // ==========================================
        // 17. SPORTS & FITNESS
        // ==========================================
        val isSports = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("match highlights", "full match", "full body workout", "home workout") ||
            titleLower.hasWord("highlights", "cricket", "football", "soccer", "nba", "ufc", "goals", "knockout", "workout", "fitness", "gym") ||
            titleLower.contains("体育") || titleLower.contains("运动") || titleLower.contains("健身") ||
            hashtags.any { it in setOf("sports", "highlights", "cricket", "football", "soccer", "nba", "ufc", "fitness", "workout") }
        )
        if (isSports) {
            detected.add(TagInfo("sports", "Sports & Fitness", "⚽", 4))
        }

        // ==========================================
        // 18. COOKING & FOOD
        // ==========================================
        val isFood = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("how to cook", "street food", "easy recipe") ||
            titleLower.hasWord("recipe", "cooking", "baking", "chef", "foodie", "mukbang", "food") ||
            titleLower.contains("美食") || titleLower.contains("做菜") || titleLower.contains("料理") ||
            hashtags.any { it in setOf("recipe", "cooking", "streetfood", "foodie", "food") }
        )
        if (isFood) {
            detected.add(TagInfo("food", "Food & Cooking", "🍳", 4))
        }

        // ==========================================
        // 19. SHORTS
        // ==========================================
        if (titleLower.contains("#shorts") || titleLower.hasWord("shorts", "tiktok", "reels") || hashtags.contains("shorts")) {
            detected.add(TagInfo("shorts", "Shorts", "⚡", 4))
        }

        // Incorporate explicit video tags & custom user tags (Unlimited tags support)
        for (explicitTag in video.tags) {
            val cleanExp = explicitTag.replace("#", "").trim().lowercase(Locale.ROOT)
            if (cleanExp.length >= 2) {
                val mapped = mapExplicitTagToCategory(cleanExp)
                if (mapped != null) {
                    detected.add(mapped)
                } else if (cleanExp.length in 3..25 && !cleanExp.contains("http") && !cleanExp.contains("torrent")) {
                    // Custom explicit tag as unlimited dynamic category
                    val formattedLabel = cleanExp.split(" ", "_", "-")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    detected.add(TagInfo(category = cleanExp, displayName = formattedLabel, emoji = "🏷️", priority = 5))
                }
            }
        }

        return detected
    }

    private fun mapExplicitTagToCategory(tag: String): TagInfo? {
        return when {
            tag in setOf("trailer", "trailers", "officialtrailer", "teasertrailer", "teaser", "promo", "pv") -> TagInfo("trailer", "Trailer", "🎬", 1)
            tag in setOf("reaction", "reacts", "reacting", "react", "recap", "breakdown") -> TagInfo("reaction", "Reaction", "😲", 2)
            tag in setOf("series", "webseries", "tvseries", "tvshow", "episode", "season", "drama", "kdrama", "cdrama") -> TagInfo("series", "Series", "📺", 2)
            tag in setOf("gameplay", "walkthrough", "playthrough", "letsplay", "speedrun") -> TagInfo("gameplay", "Gameplay", "🕹️", 2)
            tag in setOf("gaming", "game", "gamer", "esports") -> TagInfo("gaming", "Gaming", "🎮", 3)
            tag in setOf("movie", "movies", "film", "films", "cinema", "fullmovie", "shortfilm") -> TagInfo("movie", "Movie", "🍿", 3)
            tag in setOf("song", "songs", "gana", "geet", "newsong", "musicvideo", "track") -> TagInfo("song", "Song", "🎵", 2)
            tag in setOf("music", "audio", "soundtrack", "ost", "lofi", "remix", "acoustic") -> TagInfo("music", "Music", "🎧", 3)
            tag in setOf("tutorial", "howto", "guide", "learn", "course") -> TagInfo("tutorial", "Tutorial", "💡", 3)
            tag in setOf("review", "unboxing", "handson") -> TagInfo("review", "Review", "⭐", 3)
            tag in setOf("anime", "manga", "amv", "otaku", "donghua") -> TagInfo("anime", "Anime", "🎌", 2)
            tag in setOf("animation", "animated", "cartoon", "cartoons", "cgi") -> TagInfo("animation", "Animation", "🎨", 3)
            tag in setOf("tech", "technology", "gadgets", "ai", "coding", "programming", "software") -> TagInfo("tech", "Tech & AI", "💻", 3)
            tag in setOf("news", "breakingnews", "politics", "journalism") -> TagInfo("news", "News", "📰", 3)
            tag in setOf("comedy", "funny", "humor", "meme", "standup", "prank") -> TagInfo("comedy", "Funny & Comedy", "😂", 3)
            tag in setOf("top10", "top5", "compilation", "bestof", "countdown") -> TagInfo("compilation", "Top 10 & Best", "🏆", 3)
            tag in setOf("sports", "fitness", "workout", "football", "cricket") -> TagInfo("sports", "Sports & Fitness", "⚽", 4)
            tag in setOf("recipe", "cooking", "food", "chef") -> TagInfo("food", "Food & Cooking", "🍳", 4)
            tag in setOf("documentary", "history", "investigation") -> TagInfo("documentary", "Documentary", "📽️", 3)
            tag in setOf("podcast", "interview", "talkshow") -> TagInfo("podcast", "Podcast", "🎙️", 3)
            tag in setOf("hentai", "nsfw", "porn", "xxx", "erotic", "18+", "adult") -> TagInfo("nsfw_adult", "Adult 18+", "🔞", 1)
            tag in setOf("shorts", "tiktok", "reels") -> TagInfo("shorts", "Shorts", "⚡", 4)
            else -> null
        }
    }

    /**
     * Extracts clean, distinct keywords from a video for search & recommendation matching.
     */
    fun extractSemanticKeywords(video: VideoItem): List<String> {
        val keywords = mutableListOf<String>()
        val stopWords = setOf(
            "with", "from", "that", "this", "what", "video", "official", "full", "hd",
            "4k", "2024", "2025", "2026", "the", "and", "for", "you", "about", "are",
            "have", "more", "episode", "season", "part", "live", "stream", "torrent",
            "toreent", "magnet", "seeds", "seeders", "leechers", "infohash"
        )

        // Add explicit tags
        for (tag in video.tags) {
            val clean = tag.replace("#", "").trim().lowercase(Locale.ROOT)
            if (clean.length >= 3 && clean !in stopWords) {
                keywords.add(clean)
            }
        }

        // Add hashtags from title & description
        val hashtags = extractHashtags(video.title, video.description)
        for (ht in hashtags) {
            if (ht.length >= 3 && ht !in stopWords) {
                keywords.add(ht)
            }
        }

        // Add internal categories
        for (cat in extractInternalCategoryTags(video)) {
            keywords.add(cat.category)
            keywords.add(cat.displayName.lowercase(Locale.ROOT))
        }

        // Add tokens from title & uploader
        val tokens = "${video.title} ${video.uploaderName}"
            .split(" ", "-", "_", "|", "/", ":", ",", "[", "]", "(", ")")
            .map { it.replace("#", "").trim().lowercase(Locale.ROOT) }
            .filter { it.length >= 3 && it !in stopWords && it.any { c -> c.isLetter() } }

        keywords.addAll(tokens)
        return keywords.distinct().take(15)
    }

    /**
     * Helper to extract tags at playback/network resolution time from raw metadata.
     */
    fun extractTagsFromMetadata(
        title: String,
        description: String? = null,
        uploader: String? = null,
        explicitTags: List<String>? = null,
        providerId: String? = null
    ): List<String> {
        val dummy = VideoItem(
            id = "meta",
            title = title,
            uploaderName = uploader ?: "",
            description = description,
            tags = explicitTags ?: emptyList(),
            providerId = providerId
        )
        return extractSemanticKeywords(dummy)
    }

    /**
     * Builds smart category chip items with counts for a playlist / collection
     */
    fun buildSmartTagChips(videos: List<VideoItem>): List<SmartTagChip> {
        val tagCountMap = mutableMapOf<String, Pair<TagInfo, Int>>()

        videos.forEach { video ->
            val tags = extractInternalCategoryTags(video)
            tags.forEach { tagInfo ->
                if (tagInfo.category != "torrent" && tagInfo.category != "video") {
                    val current = tagCountMap[tagInfo.category]
                    if (current == null) {
                        tagCountMap[tagInfo.category] = Pair(tagInfo, 1)
                    } else {
                        tagCountMap[tagInfo.category] = Pair(current.first, current.second + 1)
                    }
                }
            }
        }

        val result = mutableListOf<SmartTagChip>()
        result.add(SmartTagChip(key = "all", label = "All", emoji = "•", count = videos.size))

        // Sort tags by frequency (descending) and then priority
        val sortedTags = tagCountMap.values
            .sortedWith(compareByDescending<Pair<TagInfo, Int>> { it.second }.thenBy { it.first.priority })

        for ((tagInfo, count) in sortedTags) {
            result.add(
                SmartTagChip(
                    key = tagInfo.category,
                    label = tagInfo.displayName,
                    emoji = tagInfo.emoji,
                    count = count
                )
            )
        }

        return result
    }

    /**
     * Matches a video against a smart tag filter key
     */
    fun matchesTag(video: VideoItem, tagKey: String): Boolean {
        if (tagKey == "all" || tagKey.isBlank()) return true
        val tags = extractInternalCategoryTags(video)
        return tags.any {
            it.category.equals(tagKey, ignoreCase = true) ||
            it.displayName.equals(tagKey, ignoreCase = true)
        } || video.tags.any {
            val clean = it.replace("#", "").trim()
            clean.equals(tagKey, ignoreCase = true)
        }
    }
}
