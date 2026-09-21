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
     * Defaults to the top [maxTags] (up to 3) for clean, high-precision layouts.
     */
    fun extractTags(video: VideoItem, maxTags: Int = 3): List<TagInfo> {
        val detected = detectAllCategories(video)
        return filterAndSortTags(detected, maxTags = maxTags)
    }

    /**
     * Filters out conflicting tags and ensures high-priority specific tags surface first.
     */
    private fun filterAndSortTags(detected: List<TagInfo>, maxTags: Int): List<TagInfo> {
        val distinct = detected.distinctBy { it.category }
            .filter {
                !it.category.contains("torrent", ignoreCase = true) &&
                !it.displayName.contains("torrent", ignoreCase = true) &&
                it.category != "video"
            }

        val hasTrailer = distinct.any { it.category in setOf("trailer", "movie_trailer", "gameplay_trailer", "anime_trailer") }
        val hasGameplay = distinct.any { it.category == "gameplay" }
        val hasSong = distinct.any { it.category == "song" }
        val hasShortFilm = distinct.any { it.category == "short_film" }

        val filtered = distinct.filter { tag ->
            when {
                // If it's a trailer, it's not the full movie!
                hasTrailer && tag.category in setOf("movie", "classic_cinema") -> false
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
            "rule34video", "hanime1", "redtube", "tube8", "coomer", "pmvhaven", "eporner", "txxx", "motherless"
        )
        val hasAdultKeywords = titleLower.hasWord("porn", "xxx", "hentai", "jav", "erotic", "nsfw", "uncensored", "creampie", "milf", "bdsm", "fetish", "pmv", "doujin", "lewd") ||
                hashtags.any { it in setOf("porn", "hentai", "nsfw", "erotic", "xxx", "18plus") }

        if (isAdultProvider || hasAdultKeywords) {
            when {
                titleLower.contains("hentai") || hashtags.contains("hentai") || providerLower in setOf("hanime1", "rule34video") -> {
                    detected.add(TagInfo("hentai", "Hentai & 2D", "🔞", 5))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 8))
                }
                titleLower.contains("pmv") || hashtags.contains("pmv") || providerLower == "pmvhaven" -> {
                    detected.add(TagInfo("pmv", "PMV & Music Edit", "🔥", 6))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 8))
                }
                titleLower.contains("bdsm") || titleLower.contains("fetish") || hashtags.any { it in setOf("fetish", "bdsm") } -> {
                    detected.add(TagInfo("fetish", "Specialty", "🌶️", 7))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 8))
                }
                else -> {
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 8))
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
                "announcementtrailer", "gameplaytrailer", "finaltrailer", "animetrailer", "reveal"
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
        )

        val hasTrailerWord = titleLower.hasWord("trailer", "teaser", "tvspot") ||
                Regex("""(?i)\b(pv\s*\d+|pv\b)""").containsMatchIn(titleLower) && (titleLower.contains("anime") || titleLower.contains("official"))

        val isTrailer = hasTrailerHashtag || hasTrailerPhrase || hasTrailerWord

        if (isTrailer) {
            val isGameRelated = titleLower.hasWord("gameplay", "game", "ps5", "xbox", "pc", "switch", "nintendo", "playstation", "steam", "dlc") ||
                    titleLower.hasPhrase("game trailer", "launch trailer", "gameplay trailer", "story trailer") ||
                    hashtags.any { it in setOf("gametrailer", "gameplaytrailer", "gaming") }

            val isAnimeRelated = titleLower.hasWord("anime", "manga", "shonen", "season", "episode", "pv") ||
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
        // 2. SONGS & MUSIC (High-Precision User Intent)
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
           titleLower.contains("[official audio]") || titleLower.contains("[official video]")

        val hasMusicKeyword = titleLower.hasWord(
            "song", "songs", "gana", "geet", "singing", "singer", "soundtrack",
            "ost", "album", "single", "track", "lyrics", "remix", "acoustic", "unplugged",
            "lofi", "lo-fi", "synthwave", "phonk", "afrobeats", "afropop", "hip-hop",
            "rapper", "rnb", "kpop", "jpop", "reggae", "vocals", "mashup"
        ) || titleLower.hasPhrase("hip hop", "chill beats", "live concert", "live session", "live at", "k-pop", "j-pop", "house music")

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
                detected.add(TagInfo("song", "Song", "🎵", 1))
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
        // 3. GAMEPLAY & GAMING (High-Precision User Intent)
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
        )

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
        )

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
        // 4. MOVIES & CINEMA (High-Precision User Intent)
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
            "hindi movie", "tamil movie", "telugu movie", "malayalam movie", "kannada movie",
            "hollywood movie", "bollywood movie", "korean movie", "complete movie", "dubbed movie"
        ) || titleLower.contains("[full movie]") || titleLower.contains("(full movie)") ||
           titleLower.contains("[full hd movie]") || titleLower.contains("(full hd movie)")

        val yearPattern = Regex("\\((19\\d{2}|20\\d{2})\\)")
        val hasYear = yearPattern.containsMatchIn(title)
        val isArchive = uploaderLower.contains("archive") || fullText.contains("internet archive")

        // Exclusions to avoid tagging clips, reactions, reviews, or songs as movies
        val hasMovieExclusions = isTrailer || isMusicOrSong ||
                titleLower.hasWord("review", "reaction", "scene", "clip", "breakdown", "recap", "ending", "explained") ||
                titleLower.hasPhrase("behind the scenes", "blooper", "making of", "deleted scene")

        val isMovie = !hasMovieExclusions && (
            hasMoviePhrase ||
            hasMovieHashtag ||
            (isArchive && (titleLower.hasWord("movie", "film") || video.durationSeconds >= 2400)) ||
            (hasYear && (titleLower.hasWord("movie", "film", "cinema")) && (video.durationSeconds >= 1800 || video.durationSeconds == 0L))
        )

        if (isMovie) {
            if (titleLower.hasPhrase("short film") || hashtags.contains("shortfilm")) {
                detected.add(TagInfo("short_film", "Short Film", "🎬", 2))
            } else {
                detected.add(TagInfo("movie", "Movie", "🍿", 2))
            }
        }

        // ==========================================
        // 5. TUTORIALS, GUIDES & HOW-TO
        // ==========================================
        val isTutorial = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("how to", "step by step", "beginner's guide", "beginners guide", "crash course", "tips & tricks", "tips and tricks", "complete guide", "for beginners") ||
            titleLower.hasWord("tutorial", "guide", "masterclass", "learn", "course") ||
            hashtags.any { it in setOf("tutorial", "howto", "guide", "learn", "stepbystep", "coding", "programming") }
        )
        if (isTutorial) {
            detected.add(TagInfo("tutorial", "Tutorial", "💡", 3))
        }

        // ==========================================
        // 6. REVIEWS & UNBOXING
        // ==========================================
        val isUnboxing = titleLower.hasWord("unboxing") || hashtags.contains("unboxing")
        val isReview = !isTrailer && (
            titleLower.hasPhrase("before you buy", "hands on", "hands-on", "is it worth it", "in-depth review", "honest review") ||
            titleLower.hasWord("review", "unboxing") ||
            hashtags.any { it in setOf("review", "unboxing", "handson") }
        )
        if (isUnboxing) {
            detected.add(TagInfo("unboxing", "Unboxing", "📦", 3))
        } else if (isReview) {
            detected.add(TagInfo("review", "Review", "⭐", 3))
        }

        // ==========================================
        // 7. ANIME & ANIMATION
        // ==========================================
        val isAnime = titleLower.hasWord("anime", "manga", "shonen", "isekai", "amv", "otaku") ||
                titleLower.hasWord("naruto", "bleach", "frieren", "crunchyroll") ||
                titleLower.hasPhrase("one piece", "jujutsu kaisen", "demon slayer", "chainsaw man", "solo leveling", "attack on titan", "dragon ball", "my hero academia") ||
                providerLower in setOf("hianime", "aniwatch") ||
                hashtags.any { it in setOf("anime", "manga", "amv", "otaku") }
        if (isAnime && !isTrailer) {
            detected.add(TagInfo("anime", "Anime", "🎌", 3))
        }

        // ==========================================
        // 8. PODCASTS & INTERVIEWS
        // ==========================================
        val isPodcast = !isTrailer && !isMusicOrSong && (
            titleLower.hasWord("podcast", "interview") ||
            titleLower.hasPhrase("talk show", "full interview", "in conversation with") ||
            uploaderLower.contains("podcast") ||
            hashtags.any { it in setOf("podcast", "interview", "talkshow") }
        )
        if (isPodcast) {
            detected.add(TagInfo("podcast", "Podcast", "🎙️", 3))
        }

        // ==========================================
        // 9. DOCUMENTARY & HISTORY
        // ==========================================
        val isDoc = !isTrailer && !isMusicOrSong && (
            titleLower.hasWord("documentary", "docuseries", "investigation") ||
            titleLower.hasPhrase("untold story", "the story of", "history of", "rise and fall") ||
            hashtags.any { it in setOf("documentary", "docuseries", "history") }
        )
        if (isDoc) {
            detected.add(TagInfo("documentary", "Documentary", "📽️", 3))
        }

        // ==========================================
        // 10. COMEDY & STAND-UP
        // ==========================================
        val isComedy = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("stand up", "stand-up", "comedy sketch", "funny moments") ||
            titleLower.hasWord("comedy", "parody", "prank", "roast", "meme", "memes", "hilarious") ||
            hashtags.any { it in setOf("comedy", "standup", "funny", "meme", "prank") }
        )
        if (isComedy) {
            detected.add(TagInfo("comedy", "Comedy", "🎭", 4))
        }

        // ==========================================
        // 11. TECH, AI & DEV
        // ==========================================
        val isTech = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("artificial intelligence", "machine learning", "software engineering") ||
            titleLower.hasWord("chatgpt", "gemini", "coding", "programming", "python", "kotlin", "smartphone", "laptop", "gpu", "gadgets") ||
            hashtags.any { it in setOf("tech", "technology", "ai", "coding", "programming", "gadgets") }
        )
        if (isTech) {
            detected.add(TagInfo("tech", "Tech & AI", "💻", 4))
        }

        // ==========================================
        // 12. NEWS & CURRENT AFFAIRS
        // ==========================================
        val isNews = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("breaking news", "press conference", "live coverage", "special report") ||
            titleLower.hasWord("news", "headline", "geopolitics") ||
            uploaderLower.hasWord("news", "bbc", "cnn") ||
            hashtags.any { it in setOf("news", "breakingnews", "worldnews") }
        )
        if (isNews) {
            detected.add(TagInfo("news", "News", "📰", 3))
        }

        // ==========================================
        // 13. SPORTS & HIGHLIGHTS
        // ==========================================
        val isSports = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("match highlights", "full match") ||
            titleLower.hasWord("highlights", "cricket", "football", "soccer", "nba", "ufc", "goals", "knockout") ||
            hashtags.any { it in setOf("sports", "highlights", "cricket", "football", "soccer", "nba", "ufc") }
        )
        if (isSports) {
            detected.add(TagInfo("sports", "Sports", "⚽", 4))
        }

        // ==========================================
        // 14. FITNESS & WORKOUT
        // ==========================================
        val isFitness = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("full body workout", "home workout", "gym workout") ||
            titleLower.hasWord("workout", "fitness", "calisthenics", "bodybuilding", "exercises") ||
            hashtags.any { it in setOf("workout", "fitness", "gym", "bodybuilding") }
        )
        if (isFitness) {
            detected.add(TagInfo("fitness", "Fitness", "💪", 4))
        }

        // ==========================================
        // 15. COOKING & FOOD
        // ==========================================
        val isFood = !isTrailer && !isMusicOrSong && (
            titleLower.hasPhrase("how to cook", "street food", "easy recipe") ||
            titleLower.hasWord("recipe", "cooking", "baking", "chef", "foodie") ||
            hashtags.any { it in setOf("recipe", "cooking", "streetfood", "foodie") }
        )
        if (isFood) {
            detected.add(TagInfo("food", "Cooking & Food", "🍳", 4))
        }

        // Incorporate explicit tags if present
        for (explicitTag in video.tags) {
            val cleanExp = explicitTag.replace("#", "").trim().lowercase(Locale.ROOT)
            if (cleanExp.length >= 2) {
                mapExplicitTagToCategory(cleanExp)?.let { detected.add(it) }
            }
        }

        return detected
    }

    private fun mapExplicitTagToCategory(tag: String): TagInfo? {
        return when {
            tag in setOf("trailer", "trailers", "officialtrailer", "teasertrailer", "teaser") -> TagInfo("trailer", "Trailer", "🎬", 1)
            tag in setOf("gameplay", "walkthrough", "playthrough", "letsplay", "speedrun") -> TagInfo("gameplay", "Gameplay", "🕹️", 2)
            tag in setOf("gaming", "game", "gamer", "esports") -> TagInfo("gaming", "Gaming", "🎮", 4)
            tag in setOf("movie", "movies", "film", "films", "cinema", "fullmovie", "shortfilm") -> TagInfo("movie", "Movie", "🍿", 2)
            tag in setOf("song", "songs", "gana", "geet", "newsong", "musicvideo", "track") -> TagInfo("song", "Song", "🎵", 1)
            tag in setOf("music", "audio", "soundtrack", "ost", "lofi", "remix", "acoustic") -> TagInfo("music", "Music", "🎧", 3)
            tag in setOf("tutorial", "howto", "guide", "learn", "course") -> TagInfo("tutorial", "Tutorial", "💡", 3)
            tag in setOf("review", "unboxing", "handson") -> TagInfo("review", "Review", "⭐", 3)
            tag in setOf("anime", "manga", "animation", "amv", "otaku") -> TagInfo("anime", "Anime", "🎌", 3)
            tag in setOf("podcast", "interview", "talkshow") -> TagInfo("podcast", "Podcast", "🎙️", 3)
            tag in setOf("documentary", "history", "investigation") -> TagInfo("documentary", "Documentary", "📽️", 3)
            tag in setOf("comedy", "funny", "humor", "meme", "standup") -> TagInfo("comedy", "Comedy", "🎭", 4)
            tag in setOf("tech", "technology", "gadgets", "ai", "coding", "programming") -> TagInfo("tech", "Tech & AI", "💻", 4)
            tag in setOf("news", "breakingnews", "politics") -> TagInfo("news", "News", "📰", 3)
            tag in setOf("sports", "fitness", "workout", "football", "cricket") -> TagInfo("sports", "Sports", "⚽", 4)
            tag in setOf("recipe", "cooking", "food", "chef") -> TagInfo("food", "Cooking & Food", "🍳", 4)
            tag in setOf("hentai", "nsfw", "porn", "xxx", "erotic", "18+") -> TagInfo("nsfw_adult", "Adult 18+", "🔞", 8)
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
            val tags = extractTags(video, maxTags = 3)
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
        }
    }
}
