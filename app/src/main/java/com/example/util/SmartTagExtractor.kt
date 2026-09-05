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

    /**
     * Extracts ALL internal categories and semantic tags without arbitrary limits.
     * Used exclusively by the AI Intelligence and Smart Recommendation Engine so it
     * builds multi-dimensional taste profiles across all user content (likes, dislikes,
     * playlists, watch history).
     */
    fun extractInternalCategoryTags(video: VideoItem): List<TagInfo> {
        val detected = detectAllCategories(video)
        return detected.distinctBy { it.category }
            .filter { 
                !it.category.contains("torrent", ignoreCase = true) && 
                !it.displayName.contains("torrent", ignoreCase = true) && 
                it.category != "video" 
            }
            .sortedBy { it.priority }
    }

    /**
     * Extracts high-accuracy semantic tags for UI presentation (badges, chips).
     * Defaults to the top [maxTags] for clean and uncluttered layouts.
     */
    fun extractTags(video: VideoItem, maxTags: Int = 2): List<TagInfo> {
        val detected = detectAllCategories(video)
        return detected.distinctBy { it.category }
            .filter { 
                !it.category.contains("torrent", ignoreCase = true) && 
                !it.displayName.contains("torrent", ignoreCase = true) && 
                it.category != "video" 
            }
            .sortedBy { it.priority }
            .take(maxTags)
    }

    /**
     * Core detection engine parsing title, description, uploader, provider, and explicit tags.
     */
    private fun detectAllCategories(video: VideoItem): List<TagInfo> {
        val title = video.title ?: ""
        val titleLower = title.lowercase(Locale.ROOT)
        val uploaderLower = (video.uploaderName ?: "").lowercase(Locale.ROOT)
        val descriptionLower = (video.description ?: "").lowercase(Locale.ROOT)
        val providerLower = (video.providerId ?: "").lowercase(Locale.ROOT)
        val rawTagsString = video.tags.joinToString(" ").lowercase(Locale.ROOT)
        val fullText = "$titleLower $uploaderLower $descriptionLower $rawTagsString $providerLower"

        val detected = mutableListOf<TagInfo>()

        // 0. Adult / 18+ / Erotic Sources & Content
        val isAdultProvider = providerLower in setOf(
            "pornhub", "xvideos", "youporn", "xhamster",
            "rule34video", "hanime1", "redtube", "tube8", "coomer", "pmvhaven"
        )
        val hasAdultKeywords = titleLower.contains("porn") || titleLower.contains("xxx") ||
                titleLower.contains("hentai") || titleLower.contains("jav ") || titleLower.contains("erotic") ||
                titleLower.contains("nsfw") || titleLower.contains("uncensored") || titleLower.contains("creampie") ||
                titleLower.contains("milf") || titleLower.contains("bdsm") || titleLower.contains("fetish") ||
                titleLower.contains("pmv") || titleLower.contains("doujin") || titleLower.contains("lewd") ||
                rawTagsString.contains("porn") || rawTagsString.contains("hentai") || rawTagsString.contains("nsfw") ||
                rawTagsString.contains("erotic") || rawTagsString.contains("xxx")

        if (isAdultProvider || hasAdultKeywords) {
            when {
                titleLower.contains("hentai") || rawTagsString.contains("hentai") || providerLower == "hanime1" || providerLower == "rule34video" -> {
                    detected.add(TagInfo("hentai", "Hentai & 2D", "🔞", 5))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 8))
                }
                titleLower.contains("pmv") || rawTagsString.contains("pmv") || providerLower == "pmvhaven" -> {
                    detected.add(TagInfo("pmv", "PMV & Music Edit", "🔥", 6))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 8))
                }
                titleLower.contains("bdsm") || titleLower.contains("fetish") || titleLower.contains("cosplay") ||
                        rawTagsString.contains("fetish") || rawTagsString.contains("bdsm") -> {
                    detected.add(TagInfo("fetish", "Specialty", "🌶️", 7))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 8))
                }
                titleLower.contains("lingerie") || titleLower.contains("sensual") || titleLower.contains("glamour") -> {
                    detected.add(TagInfo("glamour", "Glamour & Sensual", "💋", 8))
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 10))
                }
                else -> {
                    detected.add(TagInfo("nsfw_adult", "Adult 18+", "🔞", 8))
                }
            }
        }

        // 1. MUSIC & SONGS (High-Precision Detection across artists, features, stems, & channels)
        val featRegex = Regex("""(?i)\b(ft\.?|feat\.?|featuring)\b""")
        val prodRegex = Regex("""(?i)\b(prod\.?|produced\s+by|prod\s+by)\b""")
        val collabRegex = Regex("""(?i)\s+(x|&|\+)\s+""")

        val hasFeat = featRegex.containsMatchIn(title) || featRegex.containsMatchIn(descriptionLower)
        val hasProd = prodRegex.containsMatchIn(title) || prodRegex.containsMatchIn(descriptionLower)
        val hasArtistDashTitle = title.contains(" - ") || title.contains(" – ") || title.contains(" — ")

        val hasMusicSuffix = titleLower.contains("official music video") || titleLower.contains("official video") ||
                titleLower.contains("music video") || titleLower.contains("official audio") ||
                titleLower.contains("lyric video") || titleLower.contains("visualizer") ||
                titleLower.contains("official visualizer") || titleLower.contains("video clip") ||
                titleLower.contains("clip officiel") || titleLower.contains("audio track") ||
                titleLower.contains("[mv]") || titleLower.contains("(mv)") ||
                titleLower.contains("[m/v]") || titleLower.contains("(m/v)") ||
                titleLower.contains("official mv") || titleLower.contains("[audio]") ||
                titleLower.contains("(audio)") || titleLower.contains("[lyrics]") ||
                titleLower.contains("(lyrics)") || titleLower.contains("slowed + reverb") ||
                titleLower.contains("slowed and reverb") || titleLower.contains("sped up") ||
                titleLower.contains("bass boosted") || titleLower.contains("nightcore") ||
                titleLower.contains("instrumental") || titleLower.contains("karaoke")

        val hasMusicKeyword = titleLower.contains("song") || titleLower.contains("songs") ||
                titleLower.contains("gana") || titleLower.contains("geet") ||
                titleLower.contains("singing") || titleLower.contains("singer") ||
                titleLower.contains("soundtrack") || titleLower.contains(" ost") ||
                titleLower.startsWith("ost ") || titleLower.contains("ost:") ||
                titleLower.contains("album") || titleLower.contains("single") ||
                titleLower.contains("track") || titleLower.contains("lyrics") ||
                titleLower.contains("remix") || titleLower.contains("acoustic") ||
                titleLower.contains("unplugged") || titleLower.contains("live concert") ||
                titleLower.contains("live session") || titleLower.contains("live at") ||
                titleLower.contains("lofi") || titleLower.contains("lo-fi") ||
                titleLower.contains("chill beats") || titleLower.contains("synthwave") ||
                titleLower.contains("phonk") || titleLower.contains("afrobeats") ||
                titleLower.contains("afropop") || titleLower.contains("hip hop") ||
                titleLower.contains("hip-hop") || titleLower.contains("rap ") ||
                titleLower.contains("rapper") || titleLower.contains("r&b") ||
                titleLower.contains("rnb") || titleLower.contains("k-pop") ||
                titleLower.contains("kpop") || titleLower.contains("j-pop") ||
                titleLower.contains("jpop") || titleLower.contains("edm ") ||
                titleLower.contains("techno") || titleLower.contains("house music") ||
                titleLower.contains("reggae") || titleLower.contains("dancehall") ||
                titleLower.contains("piano cover") || titleLower.contains("guitar cover") ||
                titleLower.contains("melody") || titleLower.contains("tune") ||
                titleLower.contains("vocals") || titleLower.contains("audio song") ||
                titleLower.contains("video song") || titleLower.contains("official song") ||
                rawTagsString.contains("music") || rawTagsString.contains("song")

        val isMusicChannel = uploaderLower.endsWith(" - topic") || uploaderLower.contains("vevo") ||
                uploaderLower.contains("records") || uploaderLower.contains("music") ||
                uploaderLower.contains("audio") || uploaderLower.contains("t-series") ||
                uploaderLower.contains("tseries") || uploaderLower.contains("zee music") ||
                uploaderLower.contains("sony music") || uploaderLower.contains("speed records") ||
                uploaderLower.contains("saregama") || uploaderLower.contains("tips") ||
                uploaderLower.contains("yrf") || uploaderLower.contains("geet mp3") ||
                uploaderLower.contains("desimusic") || uploaderLower.contains("spinnin") ||
                uploaderLower.contains("monstercat") || uploaderLower.contains("def jam") ||
                uploaderLower.contains("atlantic") || uploaderLower.contains("warner") ||
                uploaderLower.contains("interscope") || uploaderLower.contains("columbia") ||
                uploaderLower.contains("bad boy") || uploaderLower.contains("republic records") ||
                uploaderLower.contains("hybe") || uploaderLower.contains("sm entertainment") ||
                uploaderLower.contains("yg entertainment") || uploaderLower.contains("bighit")

        val isKnownMusicArtist = titleLower.contains("burna boy") || titleLower.contains("m.anifest") ||
                titleLower.contains("wizkid") || titleLower.contains("davido") || titleLower.contains("rema") ||
                titleLower.contains("asake") || titleLower.contains("tiwa savage") || titleLower.contains("drake") ||
                titleLower.contains("kendrick lamar") || titleLower.contains("travis scott") ||
                titleLower.contains("the weeknd") || titleLower.contains("taylor swift") ||
                titleLower.contains("eminem") || titleLower.contains("kanye") ||
                titleLower.contains("bad bunny") || titleLower.contains("bts") ||
                titleLower.contains("blackpink") || titleLower.contains("billie eilish") ||
                titleLower.contains("post malone") || titleLower.contains("dua lipa") ||
                titleLower.contains("ed sheeran") || titleLower.contains("justin bieber") ||
                titleLower.contains("coldplay") || titleLower.contains("queen") ||
                titleLower.contains("bob marley") || titleLower.contains("sza") ||
                titleLower.contains("olivia rodrigo") || titleLower.contains("metro boomin") ||
                titleLower.contains("21 savage") || titleLower.contains("future") ||
                titleLower.contains("arijit") || titleLower.contains("badshah") ||
                titleLower.contains("sidhu moose") || titleLower.contains("diljit") ||
                titleLower.contains("shreya ghoshal") || titleLower.contains("ar rahman") ||
                titleLower.contains("pritam") || titleLower.contains("neha kakkar") ||
                titleLower.contains("honey singh") || titleLower.contains("karan aujla") ||
                titleLower.contains("ap dhillon") || titleLower.contains("anirudh") ||
                titleLower.contains("bruno mars") || titleLower.contains("adele") ||
                titleLower.contains("alan walker") || titleLower.contains("marshmello")

        val isSongStructuralPattern = hasArtistDashTitle && (
                hasFeat || hasProd || hasMusicSuffix || hasMusicKeyword || isMusicChannel || isKnownMusicArtist ||
                (video.durationSeconds in 45..600 && !titleLower.contains("news") && !titleLower.contains("review") &&
                 !titleLower.contains("episode") && !titleLower.contains("tutorial") && !titleLower.contains("gameplay"))
        )

        val isMusic = hasFeat || hasProd || hasMusicSuffix || hasMusicKeyword || isMusicChannel || isKnownMusicArtist || isSongStructuralPattern

        if (isMusic) {
            detected.add(TagInfo("song", "Song", "🎵", 4))
            detected.add(TagInfo("music", "Music", "🎧", 5))

            // Sub-genre identification
            when {
                titleLower.contains("afrobeats") || titleLower.contains("afropop") || titleLower.contains("burna boy") ||
                        titleLower.contains("wizkid") || titleLower.contains("davido") || titleLower.contains("rema") ||
                        titleLower.contains("asake") || titleLower.contains("m.anifest") -> {
                    detected.add(TagInfo("afrobeats", "Afrobeats", "🌍", 12))
                }
                titleLower.contains("hip hop") || titleLower.contains("hip-hop") || titleLower.contains("rap") ||
                        titleLower.contains("trap") || titleLower.contains("freestyle") || titleLower.contains("drake") ||
                        titleLower.contains("kendrick") || titleLower.contains("travis scott") || titleLower.contains("eminem") -> {
                    detected.add(TagInfo("hip_hop", "Hip-Hop", "🎤", 12))
                }
                titleLower.contains("lofi") || titleLower.contains("lo-fi") || titleLower.contains("chill beats") -> {
                    detected.add(TagInfo("lofi", "Lo-Fi", "☕", 12))
                }
                titleLower.contains("rock") || titleLower.contains("metal") || titleLower.contains("punk") -> {
                    detected.add(TagInfo("rock", "Rock", "🎸", 12))
                }
                titleLower.contains("edm") || titleLower.contains("techno") || titleLower.contains("house") ||
                        titleLower.contains("phonk") || titleLower.contains("electronic") -> {
                    detected.add(TagInfo("edm", "EDM & Beats", "⚡", 12))
                }
                titleLower.contains("k-pop") || titleLower.contains("kpop") || titleLower.contains("bts") ||
                        titleLower.contains("blackpink") -> {
                    detected.add(TagInfo("kpop", "K-Pop", "🌟", 12))
                }
                titleLower.contains("acoustic") || titleLower.contains("unplugged") || titleLower.contains("piano cover") -> {
                    detected.add(TagInfo("acoustic", "Acoustic", "🎻", 12))
                }
                titleLower.contains("soundtrack") || titleLower.contains(" ost") || titleLower.startsWith("ost ") -> {
                    detected.add(TagInfo("soundtrack", "Soundtrack", "🎼", 12))
                }
            }
        }

        // 2. Trailers & Teasers
        if (titleLower.contains("trailer") || titleLower.contains("teaser") || titleLower.contains("first look") || titleLower.contains("pv ")) {
            when {
                titleLower.contains("anime") || titleLower.contains("pv ") ->
                    detected.add(TagInfo("anime_trailer", "Anime Trailer", "🎌", 12))
                titleLower.contains("gameplay") || titleLower.contains("game trailer") || titleLower.contains("launch trailer") ->
                    detected.add(TagInfo("gaming_trailer", "Gaming Trailer", "🎮", 12))
                titleLower.contains("movie") || titleLower.contains("official trailer") || titleLower.contains("extended") ->
                    detected.add(TagInfo("movie_trailer", "Movie Trailer", "🎬", 12))
                else ->
                    detected.add(TagInfo("trailer", "Trailer", "🎬", 12))
            }
        }

        // 3. Movies & Cinema (Strictly validated to avoid false positives on short videos or random years)
        val yearPattern = Regex("\\((19\\d{2}|20\\d{2})\\)")
        val hasYear = yearPattern.containsMatchIn(title)
        val isArchive = uploaderLower.contains("archive") || fullText.contains("internet archive") || uploaderLower.contains("classic")
        val isExplicitMovie = titleLower.contains("full movie") || titleLower.contains("entire movie") ||
                titleLower.contains("feature film") || titleLower.contains("full length movie") ||
                titleLower.contains("motion picture")

        // Only classify as Movie if it has explicit movie keywords OR long-form cinema duration
        if (!isMusic && !titleLower.contains("trailer") && !titleLower.contains("teaser")) {
            if (isExplicitMovie) {
                detected.add(TagInfo("movie", "Movie", "🍿", 15))
            } else if (isArchive && (titleLower.contains("movie") || titleLower.contains("film") || video.durationSeconds >= 2400)) {
                detected.add(TagInfo("movie", "Movie", "🍿", 15))
                detected.add(TagInfo("classic_cinema", "Classic Cinema", "📽️", 18))
            } else if (hasYear && video.durationSeconds >= 2400 && (titleLower.contains("movie") || titleLower.contains("film"))) {
                // Must be at least 40 mins
                detected.add(TagInfo("movie", "Movie", "🍿", 15))
            }
        }

        // 4. Anime, Manga & Animation
        if (titleLower.contains("anime") || titleLower.contains("manga") || titleLower.contains("shonen") ||
            titleLower.contains("isekai") || fullText.contains("subbed") || fullText.contains("crunchyroll") ||
            titleLower.contains("shadow realm") || titleLower.contains("daemons of the shadow realm") ||
            titleLower.contains("naruto") || titleLower.contains("one piece") || titleLower.contains("bleach") ||
            titleLower.contains("jujutsu kaisen") || titleLower.contains("demon slayer") || titleLower.contains("chainsaw man") ||
            titleLower.contains("solo leveling") || titleLower.contains("frieren") || titleLower.contains("dragon ball") ||
            titleLower.contains("attack on titan") || titleLower.contains("shingeki") || titleLower.contains("boku no hero") ||
            titleLower.contains("my hero academia") || titleLower.contains("spy x family") ||
            providerLower == "hianime" || providerLower == "aniwatch") {
            detected.add(TagInfo("anime", "Anime", "🎌", 12))
        }
        if (titleLower.contains("animation") || titleLower.contains("animated") || titleLower.contains("pixar") ||
            titleLower.contains("disney") || titleLower.contains("cartoon")) {
            detected.add(TagInfo("animation", "Animation", "🎨", 14))
        }

        // 5. Series, Shows & K-Drama
        if (titleLower.contains("episode") || titleLower.contains("season ") || titleLower.contains("series") ||
            titleLower.contains("web series") || titleLower.contains(" ep ") || titleLower.contains("ep.")) {
            if (titleLower.contains("k-drama") || titleLower.contains("kdrama") || titleLower.contains("korean drama")) {
                detected.add(TagInfo("kdrama", "K-Drama", "✨", 18))
            }
            detected.add(TagInfo("series", "Series & TV", "📺", 20))
        }

        // 6. Action, Romance, Sci-Fi & Genres (Only if not already tagged as music)
        if (!isMusic) {
            if (titleLower.contains("action") || titleLower.contains("fight") || titleLower.contains("battle") ||
                titleLower.contains("combat") || titleLower.contains("stunt") || titleLower.contains("chase")) {
                detected.add(TagInfo("action", "Action", "💥", 18))
            }
            if (titleLower.contains("romance") || titleLower.contains("romantic") || titleLower.contains("love story") ||
                titleLower.contains("relationship") || titleLower.contains("dating")) {
                detected.add(TagInfo("romance", "Romance", "💕", 18))
            }
            if (titleLower.contains("sci-fi") || titleLower.contains("scifi") || titleLower.contains("superhero") ||
                titleLower.contains("marvel") || titleLower.contains("dc comic")) {
                detected.add(TagInfo("scifi", "Sci-Fi & Fantasy", "⚡", 18))
            }
            if (titleLower.contains("horror") || titleLower.contains("thriller") || titleLower.contains("creepy") ||
                titleLower.contains("scary") || titleLower.contains("ghost") || titleLower.contains("paranormal")) {
                detected.add(TagInfo("horror", "Thriller & Horror", "😱", 18))
            }
        }

        // 7. Video Essays, Philosophy & Deep Dives
        if (titleLower.contains("essay") || titleLower.contains("deep dive") || titleLower.contains("retrospective") ||
            titleLower.contains("analysis") || titleLower.contains("philosophy") || titleLower.contains("psychology") ||
            titleLower.contains("morality") || titleLower.contains("humanity") || titleLower.contains("meaning of")) {
            if (titleLower.contains("essay") || titleLower.contains("deep dive") || titleLower.contains("analysis")) {
                detected.add(TagInfo("video_essay", "Video Essay", "📖", 16))
            }
            if (titleLower.contains("philosophy") || titleLower.contains("psychology") || titleLower.contains("meaning of")) {
                detected.add(TagInfo("philosophy", "Philosophy", "🧠", 18))
            }
        }

        // 8. Gaming, Gameplay & Esports
        if (titleLower.contains("gameplay") || titleLower.contains("apex") || titleLower.contains("kills") ||
            titleLower.contains("gta") || titleLower.contains("minecraft") || titleLower.contains("roblox") ||
            titleLower.contains("fortnite") || titleLower.contains("cod ") || titleLower.contains("valorant") ||
            titleLower.contains("ps5") || titleLower.contains("xbox") || titleLower.contains("nintendo") ||
            titleLower.contains("walkthrough") || titleLower.contains("esports") || uploaderLower.contains("gaming") ||
            rawTagsString.contains("gaming") || rawTagsString.contains("gameplay")) {
            detected.add(TagInfo("gaming", "Gaming", "🎮", 15))
            if (titleLower.contains("gameplay") || titleLower.contains("kills") || titleLower.contains("walkthrough")) {
                detected.add(TagInfo("gameplay", "Gameplay", "🕹️", 18))
            }
            if (titleLower.contains("esports") || titleLower.contains("tournament") || titleLower.contains("championship")) {
                detected.add(TagInfo("esports", "Esports", "🏆", 18))
            }
        }

        // 9. Podcasts & Talk Shows
        if (titleLower.contains("podcast") || titleLower.contains("joe rogan") || titleLower.contains("lex fridman") ||
            titleLower.contains("huberman") || titleLower.contains("interview") || uploaderLower.contains("podcast") ||
            titleLower.contains("talk show") || rawTagsString.contains("podcast")) {
            detected.add(TagInfo("podcast", "Podcast", "🎙️", 16))
        }

        // 10. Comedy, Stand-up & Memes
        if (titleLower.contains("stand up") || titleLower.contains("stand-up") || titleLower.contains("funny") ||
            titleLower.contains("parody") || titleLower.contains("comedy") || titleLower.contains("prank") ||
            titleLower.contains("roast") || titleLower.contains("sketch") || titleLower.contains("meme") ||
            titleLower.contains("humor") || rawTagsString.contains("comedy")) {
            detected.add(TagInfo("comedy", "Comedy", "🎭", 18))
            if (titleLower.contains("meme") || titleLower.contains("shitpost")) {
                detected.add(TagInfo("meme", "Meme & Fun", "😂", 20))
            }
        }

        // 11. Documentaries & History
        if (titleLower.contains("documentary") || titleLower.contains("docuseries") || titleLower.contains("untold story") ||
            titleLower.contains("history of") || titleLower.contains("ancient") || titleLower.contains("archaeology")) {
            detected.add(TagInfo("documentary", "Documentary", "🍿", 18))
            if (titleLower.contains("history") || titleLower.contains("ancient") || titleLower.contains("war ")) {
                detected.add(TagInfo("history", "History", "🏛️", 20))
            }
        }

        // 12. Tech, AI & Coding
        if (titleLower.contains("unboxing") || titleLower.contains("iphone") || titleLower.contains("samsung") ||
            titleLower.contains("pixel") || titleLower.contains("tech") || titleLower.contains("gadgets") ||
            uploaderLower.contains("mkbhd") || fullText.contains("laptop") || fullText.contains("gpu") ||
            rawTagsString.contains("technology") || rawTagsString.contains("gadgets")) {
            detected.add(TagInfo("tech", "Tech", "💻", 20))
        }
        if (titleLower.contains("ai ") || titleLower.contains("chatgpt") || titleLower.contains("gemini") ||
            titleLower.contains("artificial intelligence") || titleLower.contains("machine learning") ||
            titleLower.contains("coding") || titleLower.contains("programming") || titleLower.contains("developer") ||
            titleLower.contains("python") || titleLower.contains("kotlin") || titleLower.contains("javascript")) {
            detected.add(TagInfo("ai", "AI & Dev", "🤖", 20))
        }

        // 13. News & Geopolitics
        if (titleLower.contains("news") || titleLower.contains("breaking") || uploaderLower.contains("news") ||
            uploaderLower.contains("bbc") || uploaderLower.contains("cnn") || rawTagsString.contains("news") ||
            titleLower.contains("shooting") || titleLower.contains("police") || titleLower.contains("press conference") ||
            titleLower.contains("white house") || titleLower.contains("pentagon") || titleLower.contains("times square")) {
            detected.add(TagInfo("news", "News", "📰", 15))
        }
        if (titleLower.contains("geopolitics") || titleLower.contains("world affairs") || titleLower.contains("military") ||
            titleLower.contains("defense") || titleLower.contains("war ") || titleLower.contains("foreign policy")) {
            detected.add(TagInfo("world_affairs", "World Affairs", "🌐", 20))
        }

        // 14. Learning, Science & Education
        if (titleLower.contains("explained") || titleLower.contains("how to") || titleLower.contains("tutorial") ||
            titleLower.contains("guide") || titleLower.contains("learn") || uploaderLower.contains("academy")) {
            detected.add(TagInfo("education", "Education", "📚", 22))
        }
        if (titleLower.contains("science") || titleLower.contains("physics") || titleLower.contains("space") ||
            titleLower.contains("nasa") || titleLower.contains("quantum") || titleLower.contains("biology")) {
            detected.add(TagInfo("science", "Science", "🔬", 22))
        }

        // 15. Reaction & Reviews
        if (titleLower.contains("reaction") || titleLower.contains("reacts")) {
            detected.add(TagInfo("reaction", "Reaction", "😲", 22))
        } else if (titleLower.contains("review")) {
            detected.add(TagInfo("review", "Review", "⭐", 22))
        }

        // 16. Sports & Fitness
        if (titleLower.contains("cricket") || titleLower.contains("football") || titleLower.contains("soccer") ||
            titleLower.contains("nba") || titleLower.contains("match highlights") || titleLower.contains("goals")) {
            detected.add(TagInfo("sports", "Sports", "⚽", 18))
        }
        if (titleLower.contains("workout") || titleLower.contains("fitness") || titleLower.contains("gym") ||
            titleLower.contains("bodybuilding") || titleLower.contains("exercise") || titleLower.contains("calisthenics")) {
            detected.add(TagInfo("fitness", "Fitness & Health", "💪", 22))
        }

        // 17. Automotive, Food, ASMR & Travel
        if (titleLower.contains("tesla") || titleLower.contains("supercar") || titleLower.contains("car review") ||
            titleLower.contains("drive") || titleLower.contains("ev ") || titleLower.contains("bmw") || titleLower.contains("porsche")) {
            detected.add(TagInfo("auto", "Auto", "🚗", 25))
        }
        if (titleLower.contains("recipe") || titleLower.contains("cooking") || titleLower.contains("street food") ||
            titleLower.contains("food") || titleLower.contains("chef")) {
            detected.add(TagInfo("food", "Food & Cooking", "🍔", 25))
        }
        if (titleLower.contains("asmr") || titleLower.contains("whispering") || titleLower.contains("relaxation") ||
            titleLower.contains("sleep sounds")) {
            detected.add(TagInfo("asmr", "ASMR & Relax", "🎧", 25))
        }
        if (titleLower.contains("travel") || titleLower.contains("trip to") || titleLower.contains("tokyo") ||
            titleLower.contains("vacation") || (titleLower.contains("vlog") && !titleLower.contains("daily"))) {
            detected.add(TagInfo("travel", "Travel & Vlogs", "✈️", 25))
        }

        // Incorporate explicit tags if present
        for (explicitTag in video.tags) {
            val cleanExp = explicitTag.trim().lowercase(Locale.ROOT)
            if (cleanExp.length >= 3) {
                mapExplicitTagToCategory(cleanExp)?.let { detected.add(it) }
            }
        }

        // Never emit dumb fallback tags like "Watch" or "video". If nothing matches, return empty.
        return detected
    }

    private fun mapExplicitTagToCategory(tag: String): TagInfo? {
        return when {
            tag in setOf("anime", "manga", "animation", "otaku") -> TagInfo("anime", "Anime", "🎌", 12)
            tag in setOf("gaming", "game", "gameplay", "walkthrough", "speedrun") -> TagInfo("gaming", "Gaming", "🎮", 15)
            tag in setOf("movie", "film", "cinema", "trailer") -> TagInfo("movie", "Movie", "🍿", 15)
            tag in setOf("music", "song", "songs", "audio", "soundtrack", "ost", "singing", "gana", "geet") -> TagInfo("song", "Song", "🎵", 4)
            tag in setOf("tech", "technology", "gadgets", "computer", "smartphone") -> TagInfo("tech", "Tech", "💻", 25)
            tag in setOf("ai", "artificial intelligence", "coding", "programming", "software") -> TagInfo("ai", "AI & Dev", "🤖", 22)
            tag in setOf("comedy", "funny", "humor", "meme") -> TagInfo("comedy", "Comedy", "🎭", 20)
            tag in setOf("podcast", "interview", "discussion") -> TagInfo("podcast", "Podcast", "🎙️", 15)
            tag in setOf("news", "politics", "journalism") -> TagInfo("news", "News", "📰", 25)
            tag in setOf("sports", "fitness", "workout", "football", "cricket") -> TagInfo("sports", "Sports", "⚽", 25)
            tag in setOf("education", "tutorial", "howto", "learning", "science") -> TagInfo("education", "Education", "📚", 25)
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

        // Add categories
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
            val tags = extractTags(video, maxTags = 2)
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
        // Always include "All" at the start
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
