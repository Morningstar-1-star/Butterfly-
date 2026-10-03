package com.example.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

data class BrandLogoInfo(
    val logoUrls: List<String>,
    val brandName: String,
    val brandShortText: String,
    val backgroundColor: Color,
    val textColor: Color,
    val subscriberCountText: String = "Verified Channel"
)

object ChannelLogoHelper {

    private val brandInfoCache = java.util.concurrent.ConcurrentHashMap<String, BrandLogoInfo>(256)

    private val AVATAR_PALETTE = listOf(
        Pair(Color(0xFFE50914), Color.White), // Red
        Pair(Color(0xFF0078D4), Color.White), // Blue
        Pair(Color(0xFF107C41), Color.White), // Green
        Pair(Color(0xFF673AB7), Color.White), // Deep Purple
        Pair(Color(0xFFFF6F00), Color.White), // Orange
        Pair(Color(0xFFD81B60), Color.White), // Pink
        Pair(Color(0xFF00838F), Color.White), // Cyan
        Pair(Color(0xFF283593), Color.White), // Indigo
        Pair(Color(0xFF4E342E), Color.White), // Brown
        Pair(Color(0xFF37474F), Color.White)  // Blue Grey
    )

    private fun getColorPair(name: String): Pair<Color, Color> {
        val index = abs(name.hashCode()) % AVATAR_PALETTE.size
        return AVATAR_PALETTE[index]
    }

    fun getProductionCompanyForTitle(videoTitle: String?, isTv: Boolean = false): BrandLogoInfo {
        if (videoTitle.isNullOrBlank()) {
            return getBrandInfo(null, null, null)
        }
        val studio = StudioDetector.detectStudio(videoTitle, isTv)
        return getBrandInfo(studio, null, videoTitle)
    }

    fun getBrandInfo(uploaderName: String?, rawAvatarUrl: String?, videoTitle: String? = null): BrandLogoInfo {
        val cacheKey = "${uploaderName.orEmpty()}|${rawAvatarUrl.orEmpty()}|${videoTitle.orEmpty()}"
        brandInfoCache[cacheKey]?.let { return it }

        val info = computeBrandInfo(uploaderName, rawAvatarUrl, videoTitle)
        if (brandInfoCache.size < 1500) {
            brandInfoCache[cacheKey] = info
        }
        return info
    }

    private fun computeBrandInfo(uploaderName: String?, rawAvatarUrl: String?, videoTitle: String? = null): BrandLogoInfo {
        val trimmed = uploaderName?.trim().orEmpty()
        val isTencentSpecific = trimmed.contains("tencent", ignoreCase = true) ||
                trimmed.contains("腾讯") ||
                trimmed.contains("wetv", ignoreCase = true) ||
                trimmed.contains("v.qq", ignoreCase = true)

        val cleanName = when {
            trimmed.isBlank() -> "Official Creator"
            trimmed.lowercase() == "tv network" -> "Verified Studio"
            isTencentSpecific -> com.example.extractor.TencentProvider.sanitizeTencentChannelName(trimmed)
            else -> trimmed
        }

        val effectiveAvatar = when {
            rawAvatarUrl.isNullOrBlank() -> null
            rawAvatarUrl.startsWith("//") -> "https:$rawAvatarUrl"
            else -> rawAvatarUrl
        }

        val isGenericProvider = trimmed.lowercase().let { low ->
            low.isBlank() || low.contains("vidsrc") || low.contains("decryptor") || low.contains("tmdb") ||
            low.contains("vixsrc") || low.contains("superembed") || low.contains("vega") || low.contains("hdhub") ||
            low.contains("katmovie") || low.contains("1cinevood") || low.contains("bollyflix") || low.contains("movies4u") ||
            low.contains("allmovieshub") || low.contains("world4u") || low.contains("cinema release") ||
            low.contains("popular movie") || low.contains("verified studio") || low.contains("official creator") ||
            low.contains("multi-server") || low == "t" || low.contains("tv network")
        }

        val isGenericAvatar = effectiveAvatar.isNullOrBlank() ||
            effectiveAvatar.contains("favicon") ||
            effectiveAvatar.contains("baseline_lock") ||
            effectiveAvatar.contains("material-design-icons") ||
            effectiveAvatar.contains("unsplash.com") ||
            effectiveAvatar.contains("placeholder") ||
            effectiveAvatar.contains("noface") ||
            effectiveAvatar.contains("default_avatar")

        val isArchive = trimmed.contains("archive", ignoreCase = true) ||
                effectiveAvatar?.contains("archive.org") == true ||
                videoTitle?.contains("archive.org") == true

        if (isArchive) {
            val rawName = if (cleanName.isNotBlank() && cleanName != "Official Creator" && cleanName != "Verified Creator") cleanName else "Internet Archive"
            val uploaderDisplay = rawName.replace("_", " ").trim()
            val archiveLogos = if (!effectiveAvatar.isNullOrBlank() && !effectiveAvatar.contains("placeholder") && !effectiveAvatar.contains("favicon")) {
                listOf(effectiveAvatar, "https://archive.org/images/glogo.png")
            } else {
                listOf(
                    "https://archive.org/images/glogo.png",
                    "https://archive.org/download/ia-logo/ia-logo.png"
                )
            }
            return BrandLogoInfo(
                logoUrls = archiveLogos,
                brandName = uploaderDisplay,
                brandShortText = getInitials(uploaderDisplay),
                backgroundColor = Color(0xFF1E212A),
                textColor = Color.White,
                subscriberCountText = if (uploaderDisplay.equals("Internet Archive", ignoreCase = true)) "Internet Archive • Public Domain" else "Archive • Public Domain"
            )
        }

        // If a real remote creator avatar URL is provided (and it's not a generic placeholder/favicon on a movie feed), respect it completely
        if (!effectiveAvatar.isNullOrEmpty() && (effectiveAvatar.startsWith("http://") || effectiveAvatar.startsWith("https://")) && !isGenericAvatar && !isGenericProvider) {
            return BrandLogoInfo(
                logoUrls = listOf(effectiveAvatar),
                brandName = cleanName,
                brandShortText = getInitials(cleanName),
                backgroundColor = Color(0xFF1E212A),
                textColor = Color.White,
                subscriberCountText = "Verified Channel"
            )
        }

        // For non-generic platforms (YouTube, Bilibili, Dailymotion, Archive, etc.) where uploader is a real creator,
        // NEVER let words in the video title override the creator with a movie studio (unless the creator name itself is a studio)
        if (!isGenericProvider && cleanName.isNotBlank() && !cleanName.equals("Official Creator", ignoreCase = true) && !cleanName.equals("Verified Creator", ignoreCase = true)) {
            val isKnownStudio = StudioDetector.isStudioName(cleanName)
            if (!isKnownStudio) {
                val pair = getColorPair(cleanName)
                return BrandLogoInfo(
                    logoUrls = if (!effectiveAvatar.isNullOrBlank()) listOf(effectiveAvatar) else emptyList(),
                    brandName = cleanName,
                    brandShortText = getInitials(cleanName),
                    backgroundColor = pair.first,
                    textColor = pair.second,
                    subscriberCountText = "Verified Channel"
                )
            }
        }

        val name = cleanName.lowercase()
        val title = videoTitle?.lowercase()?.trim() ?: ""
        val combined = "$name $title"

        // Production Studio & Company Matching
        return when {
            // --- MARVEL STUDIOS ---
            combined.contains("marvel") || combined.contains("iron man") || combined.contains("avengers") ||
            combined.contains("captain america") || combined.contains("thor") || combined.contains("black panther") ||
            combined.contains("doctor strange") || combined.contains("ant-man") || combined.contains("guardians of the galaxy") ||
            combined.contains("deadpool") || combined.contains("wolverine") || combined.contains("x-men") ||
            combined.contains("fantastic four") || combined.contains("loki") || combined.contains("wandavision") ||
            combined.contains("moon knight") || combined.contains("shang-chi") || combined.contains("eternals") ||
            combined.contains("secret invasion") || combined.contains("the marvels") || combined.contains("she-hulk") ||
            combined.contains("thunderbolts") || combined.contains("agatha") || combined.contains("what if") ||
            combined.contains("daredevil") || combined.contains("punisher") || combined.contains("hawkeye") ||
            combined.contains("mcu") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Marvel_Logo.svg/320px-Marvel_Logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/420.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Marvel_Studios_2016_logo.svg/320px-Marvel_Studios_2016_logo.svg.png"
                ),
                brandName = "Marvel Studios",
                brandShortText = "MARVEL",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "34.1M subscribers • Official Studio"
            )

            // --- DC STUDIOS / DC COMICS ---
            combined.contains("superman") || combined.contains("batman") || combined.contains("the dark knight") ||
            combined.contains("dark knight") || combined.contains("joker") || combined.contains("dc studios") ||
            combined.contains("dc comics") || combined.contains("wonder woman") || combined.contains("aquaman") ||
            combined.contains("the flash") || combined.contains("flash ") || combined.contains("justice league") ||
            combined.contains("suicide squad") || combined.contains("peacemaker") || combined.contains("shazam") ||
            combined.contains("black adam") || combined.contains("harley quinn") || combined.contains("blue beetle") ||
            combined.contains("watchmen") || combined.contains("swamp thing") || combined.contains("sandman") ||
            combined.contains("titans") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1c/DC_Comics_logo.svg/320px-DC_Comics_logo.svg.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3d/DC_logo.svg/320px-DC_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/93xA627NdsM6L0Qi4h2y8V1vQn3.png"
                ),
                brandName = "DC Studios",
                brandShortText = "DC",
                backgroundColor = Color(0xFF0078D4),
                textColor = Color.White,
                subscriberCountText = "22.3M subscribers • Official Studio"
            )

            // --- WARNER BROS. PICTURES ---
            combined.contains("warner") || combined.contains("wb ") || combined.contains("harry potter") ||
            combined.contains("fantastic beasts") || combined.contains("matrix") || combined.contains("wonka") ||
            combined.contains("barbie") || combined.contains("beetlejuice") || combined.contains("mad max") ||
            combined.contains("furiosa") || combined.contains("tenet") || combined.contains("dune") ||
            combined.contains("godzilla") || combined.contains("kong") || combined.contains("monsterverse") ||
            combined.contains("the conjuring") || combined.contains("annabelle") || combined.contains("the nun") ||
            combined.contains("casablanca") || combined.contains("twister") || combined.contains("blade runner") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Warner_Bros_logo.svg/320px-Warner_Bros_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/ky0xOc5Orh9Z1Nsu0RP490xJ2lm.png"
                ),
                brandName = "Warner Bros. Pictures",
                brandShortText = "WB",
                backgroundColor = Color(0xFF002B49),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "28.9M subscribers • Official Studio"
            )

            // --- PIXAR ANIMATION STUDIOS ---
            combined.contains("pixar") || combined.contains("toy story") || combined.contains("inside out") ||
            combined.contains("finding nemo") || combined.contains("finding dory") || combined.contains("cars ") ||
            combined.contains("incredibles") || combined.contains("the incredibles") || combined.contains("monsters inc") ||
            combined.contains("monsters university") || combined.contains("wall-e") || combined.contains("ratatouille") ||
            combined.contains("coco") || combined.contains("soul ") || combined.contains("turning red") ||
            combined.contains("elemental") || combined.contains("lightyear") || combined.contains("luca") ||
            combined.contains("onward") || combined.contains("brave") || combined.contains("a bug's life") ||
            combined.contains("good dinosaur") || combined.contains("elio") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Pixar_Animation_Studios_logo.svg/320px-Pixar_Animation_Studios_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/1TjvG00r0167cW40Bf5V9o35yE6.png"
                ),
                brandName = "Pixar Animation Studios",
                brandShortText = "PIXAR",
                backgroundColor = Color(0xFF003366),
                textColor = Color.White,
                subscriberCountText = "15.8M subscribers • Official Studio"
            )

            // --- WALT DISNEY PICTURES / DISNEY+ ---
            combined.contains("disney") || combined.contains("walt disney") || combined.contains("frozen") ||
            combined.contains("moana") || combined.contains("lion king") || combined.contains("the lion king") ||
            combined.contains("aladdin") || combined.contains("beauty and the beast") || combined.contains("cinderella") ||
            combined.contains("mulan") || combined.contains("tangled") || combined.contains("zootopia") ||
            combined.contains("encanto") || combined.contains("wish") || combined.contains("little mermaid") ||
            combined.contains("the little mermaid") || combined.contains("pinocchio") || combined.contains("peter pan") ||
            combined.contains("snow white") || combined.contains("bambi") || combined.contains("dumbo") ||
            combined.contains("jungle book") || combined.contains("the jungle book") || combined.contains("pirates of the caribbean") ||
            combined.contains("national treasure") || combined.contains("tron") || combined.contains("hocus pocus") ||
            combined.contains("nightmare before christmas") || combined.contains("cruella") || combined.contains("maleficent") ||
            combined.contains("wreck-it ralph") || combined.contains("big hero 6") || combined.contains("raya") ||
            combined.contains("princess and the frog") || combined.contains("mickey mouse") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Disney%2B_logo.svg/320px-Disney%2B_logo.svg.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/Walt_Disney_Pictures_2011_logo.svg/320px-Walt_Disney_Pictures_2011_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/wdrCwmRnLFJhEoH8GSfymY85KHT.png"
                ),
                brandName = "Walt Disney Pictures",
                brandShortText = "DISNEY",
                backgroundColor = Color(0xFF113CCF),
                textColor = Color.White,
                subscriberCountText = "52.0M subscribers • Official Studio"
            )

            // --- LUCASFILM ---
            combined.contains("lucasfilm") || combined.contains("star wars") || combined.contains("mandalorian") ||
            combined.contains("the mandalorian") || combined.contains("ahsoka") || combined.contains("andor") ||
            combined.contains("obi-wan") || combined.contains("boba fett") || combined.contains("bad batch") ||
            combined.contains("clone wars") || combined.contains("skywalker") || combined.contains("indiana jones") ||
            combined.contains("willow") || combined.contains("rogue one") || combined.contains("solo: a star wars") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/Lucasfilm_logo.svg/320px-Lucasfilm_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/1t5q0jF0rYc05vGZtXlO1cWnE3Q.png"
                ),
                brandName = "Lucasfilm",
                brandShortText = "LUCASFILM",
                backgroundColor = Color(0xFF18181B),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "19.8M subscribers • Official Studio"
            )

            // --- 20TH CENTURY STUDIOS / SEARCHLIGHT ---
            combined.contains("20th century") || combined.contains("fox") || combined.contains("searchlight") ||
            combined.contains("avatar") || combined.contains("planet of the apes") || combined.contains("kingdom of the planet") ||
            combined.contains("alien") || combined.contains("alien: romulus") || combined.contains("predator") ||
            combined.contains("prey") || combined.contains("die hard") || combined.contains("titanic") ||
            combined.contains("home alone") || combined.contains("ice age") || combined.contains("night at the museum") ||
            combined.contains("kingsman") || combined.contains("bohemian rhapsody") || combined.contains("the martian") ||
            combined.contains("grand budapest hotel") || combined.contains("poor things") || combined.contains("shape of water") ||
            combined.contains("nomadland") || combined.contains("jojo rabbit") || combined.contains("birdman") ||
            combined.contains("the menu") || combined.contains("free guy") || combined.contains("maze runner") ||
            combined.contains("logan") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/77/20th_Century_Studios_logo.svg/320px-20th_Century_Studios_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/h0rjX5v3gn5QI89R62Wc9n8i56k.png"
                ),
                brandName = "20th Century Studios",
                brandShortText = "20TH",
                backgroundColor = Color(0xFF1E293B),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "22.0M subscribers • Official Studio"
            )

            // --- UNIVERSAL PICTURES / ILLUMINATION / DREAMWORKS ---
            combined.contains("universal") || combined.contains("dreamworks") || combined.contains("illumination") ||
            combined.contains("jurassic") || combined.contains("fast & furious") || combined.contains("fast and furious") ||
            combined.contains("furious 7") || combined.contains("fate of the furious") || combined.contains("hobbs & shaw") ||
            combined.contains("despicable me") || combined.contains("minions") || combined.contains("super mario") ||
            combined.contains("shrek") || combined.contains("kung fu panda") || combined.contains("how to train your dragon") ||
            combined.contains("madagascar") || combined.contains("puss in boots") || combined.contains("trolls") ||
            combined.contains("sing ") || combined.contains("secret life of pets") || combined.contains("wicked") ||
            combined.contains("the grinch") || combined.contains("lorax") || combined.contains("oppenheimer") ||
            combined.contains("back to the future") || combined.contains("jaws") || combined.contains("e.t.") ||
            combined.contains("gladiator") || combined.contains("mamma mia") || combined.contains("get out") ||
            combined.contains("us ") || combined.contains("nope") || combined.contains("twisters") ||
            combined.contains("five nights at freddy") || combined.contains("fnaf") || combined.contains("m3gan") ||
            combined.contains("the purge") || combined.contains("bourne") || combined.contains("the fall guy") ||
            combined.contains("speak no evil") || combined.contains("nosferatu") || combined.contains("wolf man") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/29/Universal_Pictures_logo.svg/320px-Universal_Pictures_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/837bNpkm0hwNt2FFEtIHVOxiOk8.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/DreamWorks_Animation_logo.svg/320px-DreamWorks_Animation_logo.svg.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/86/Illumination_logo.svg/320px-Illumination_logo.svg.png"
                ),
                brandName = "Universal Pictures",
                brandShortText = "UNIVERSAL",
                backgroundColor = Color(0xFF001F3F),
                textColor = Color.White,
                subscriberCountText = "31.0M subscribers • Official Studio"
            )

            // --- PARAMOUNT PICTURES / NICKELODEON ---
            combined.contains("paramount") || combined.contains("nickelodeon") || combined.contains("top gun") ||
            combined.contains("mission: impossible") || combined.contains("mission impossible") || combined.contains("transformers") ||
            combined.contains("sonic the hedgehog") || combined.contains("sonic") || combined.contains("gladiator ii") ||
            combined.contains("a quiet place") || combined.contains("quiet place") || combined.contains("scream") ||
            combined.contains("star trek") || combined.contains("the godfather") || combined.contains("godfather") ||
            combined.contains("forrest gump") || combined.contains("saving private ryan") || combined.contains("spongebob") ||
            combined.contains("paw patrol") || combined.contains("ninja turtles") || combined.contains("tmnt") ||
            combined.contains("mean girls") || combined.contains("smile") || combined.contains("dungeons & dragons") ||
            combined.contains("babylon") || combined.contains("wolf of wall street") || combined.contains("shutter island") ||
            combined.contains("yellowstone") || combined.contains("south park") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Paramount_Pictures_logo.svg/320px-Paramount_Pictures_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/fycMZt242LVjagMByZOLUGbCvv3.png"
                ),
                brandName = "Paramount Pictures",
                brandShortText = "PARAMOUNT",
                backgroundColor = Color(0xFF002B49),
                textColor = Color.White,
                subscriberCountText = "26.5M subscribers • Official Studio"
            )

            // --- SONY PICTURES / COLUMBIA PICTURES ---
            combined.contains("sony pictures") || combined.contains("columbia pictures") || combined.contains("tristar") ||
            combined.contains("spider-verse") || combined.contains("into the spider-verse") || combined.contains("across the spider-verse") ||
            combined.contains("spider-man") || combined.contains("venom") || combined.contains("jumanji") ||
            combined.contains("ghostbusters") || combined.contains("bad boys") || combined.contains("men in black") ||
            combined.contains("uncharted") || combined.contains("gran turismo") || combined.contains("karate kid") ||
            combined.contains("cobra kai") || combined.contains("equalizer") || combined.contains("hotel transylvania") ||
            combined.contains("smurfs") || combined.contains("resident evil") || combined.contains("underworld") ||
            combined.contains("zombieland") || combined.contains("21 jump street") || combined.contains("22 jump street") ||
            combined.contains("social network") || combined.contains("once upon a time in hollywood") || combined.contains("little women") ||
            combined.contains("baby driver") || combined.contains("da vinci code") || combined.contains("casino royale") ||
            combined.contains("skyfall") || combined.contains("spectre") || combined.contains("no time to die") ||
            combined.contains("anyone but you") || combined.contains("it ends with us") || combined.contains("madame web") ||
            combined.contains("kraven") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Sony_Pictures_logo.svg/320px-Sony_Pictures_logo.svg.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Columbia_Pictures_2024_logo.svg/320px-Columbia_Pictures_2024_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/71BqEFAF4V3qjjMPCpLuyJFB9A.png"
                ),
                brandName = "Sony Pictures",
                brandShortText = "SONY",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "29.8M subscribers • Official Studio"
            )

            // --- LIONSGATE FILMS ---
            combined.contains("lionsgate") || combined.contains("summit") || combined.contains("hunger games") ||
            combined.contains("the hunger games") || combined.contains("songbirds and snakes") || combined.contains("john wick") ||
            combined.contains("ballerina") || combined.contains("twilight") || combined.contains("saw ") ||
            combined.contains("saw x") || combined.contains("spiral") || combined.contains("now you see me") ||
            combined.contains("la la land") || combined.contains("knives out") || combined.contains("expendables") ||
            combined.contains("the expendables") || combined.contains("rambo") || combined.contains("hacksaw ridge") ||
            combined.contains("american psycho") || combined.contains("borderlands") || combined.contains("the crow") ||
            combined.contains("boy kills world") || combined.contains("ungentlemanly warfare") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Lionsgate_logo.svg/320px-Lionsgate_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/16DOo4uBqF71z0178H7803k2jG.png"
                ),
                brandName = "Lionsgate Films",
                brandShortText = "LG",
                backgroundColor = Color(0xFF1E1B18),
                textColor = Color(0xFFFFC107),
                subscriberCountText = "21.2M subscribers • Official Studio"
            )

            // --- A24 ---
            combined.contains("a24") || combined.contains("everything everywhere") || combined.contains("civil war") ||
            combined.contains("the whale") || combined.contains("talk to me") || combined.contains("hereditary") ||
            combined.contains("midsommar") || combined.contains("uncut gems") || combined.contains("the witch") ||
            combined.contains("lady bird") || combined.contains("moonlight") || combined.contains("beef") ||
            combined.contains("past lives") || combined.contains("the iron claw") || combined.contains("iron claw") ||
            combined.contains("priscilla") || combined.contains("zone of interest") || combined.contains("beau is afraid") ||
            combined.contains("pearl") || combined.contains("maxxxine") || combined.contains("heretic") ||
            combined.contains("we live in time") || combined.contains("sing sing") || combined.contains("ex machina") ||
            combined.contains("the lobster") || combined.contains("the lighthouse") || combined.contains("the green knight") ||
            combined.contains("green knight") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/22/A24_logo.svg/320px-A24_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/1ZXsFk98b6ipk91yS9c1k808j.png"
                ),
                brandName = "A24",
                brandShortText = "A24",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "12.6M subscribers • Official Studio"
            )

            // --- LEGENDARY ENTERTAINMENT ---
            combined.contains("legendary") || combined.contains("pacific rim") || combined.contains("detective pikachu") ||
            combined.contains("enola holmes") || combined.contains("interstellar") || combined.contains("inception") ||
            combined.contains("300") || combined.contains("warcraft") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/Legendary_Pictures_logo.svg/320px-Legendary_Pictures_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/b13a776110a12e2c2b3d81017.png"
                ),
                brandName = "Legendary Entertainment",
                brandShortText = "LEGENDARY",
                backgroundColor = Color(0xFF111827),
                textColor = Color.White,
                subscriberCountText = "18.3M subscribers • Official Studio"
            )

            // --- BLUMHOUSE PRODUCTIONS ---
            combined.contains("blumhouse") || combined.contains("the black phone") || combined.contains("paranormal activity") ||
            combined.contains("insidious") || combined.contains("sinister") || combined.contains("halloween ends") ||
            combined.contains("halloween kills") || combined.contains("invisible man") || combined.contains("split") ||
            combined.contains("glass") || combined.contains("whiplash") || combined.contains("happy death day") ||
            combined.contains("imaginary") || combined.contains("night swim") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Blumhouse_Productions_logo.svg/320px-Blumhouse_Productions_logo.svg.png"
                ),
                brandName = "Blumhouse Productions",
                brandShortText = "BH",
                backgroundColor = Color(0xFF000000),
                textColor = Color.Red,
                subscriberCountText = "14.8M subscribers • Official Studio"
            )

            // --- METRO-GOLDWYN-MAYER / AMAZON MGM ---
            combined.contains("mgm") || combined.contains("metro-goldwyn") || combined.contains("james bond") ||
            combined.contains("007") || combined.contains("creed") || combined.contains("rocky") ||
            combined.contains("road house") || combined.contains("red one") || combined.contains("the beekeeper") ||
            combined.contains("beekeeper") || combined.contains("challengers") || combined.contains("saltburn") ||
            combined.contains("air ") || combined.contains("robocop") || combined.contains("silence of the lambs") ||
            combined.contains("the silence of the lambs") || combined.contains("fargo") || combined.contains("vikings") ||
            combined.contains("handmaid's tale") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/Metro-Goldwyn-Mayer_logo.svg/320px-Metro-Goldwyn-Mayer_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/101712a149f131a478c1872a.png"
                ),
                brandName = "Metro-Goldwyn-Mayer",
                brandShortText = "MGM",
                backgroundColor = Color(0xFF261C14),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "24.1M subscribers • Official Studio"
            )

            // --- NEW LINE CINEMA ---
            combined.contains("new line") || combined.contains("lord of the rings") || combined.contains("the hobbit") ||
            combined.contains("hobbit") || combined.contains("war of the rohirrim") || combined.contains("nightmare on elm street") ||
            combined.contains("rush hour") || combined.contains("elf") || combined.contains("the mask") ||
            combined.contains("dumb and dumber") || combined.contains("final destination") || combined.contains("mortal kombat") ||
            combined.contains("san andreas") || combined.contains("rampage") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/03/New_Line_Cinema_logo.svg/320px-New_Line_Cinema_logo.svg.png"
                ),
                brandName = "New Line Cinema",
                brandShortText = "NLC",
                backgroundColor = Color(0xFF0F172A),
                textColor = Color.White,
                subscriberCountText = "19.4M subscribers • Official Studio"
            )

            // --- HBO / MAX ---
            combined.contains("hbo") || combined.contains("max") || combined.contains("game of thrones") ||
            combined.contains("house of the dragon") || combined.contains("last of us") || combined.contains("the last of us") ||
            combined.contains("succession") || combined.contains("white lotus") || combined.contains("the white lotus") ||
            combined.contains("euphoria") || combined.contains("the penguin") || combined.contains("chernobyl") ||
            combined.contains("sopranos") || combined.contains("the sopranos") || combined.contains("the wire") ||
            combined.contains("true detective") || combined.contains("westworld") || combined.contains("barry") ||
            combined.contains("silicon valley") || combined.contains("curb your enthusiasm") || combined.contains("dune: prophecy") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/HBO_logo.svg/320px-HBO_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/a3Wd7H11a681c2f90a4176d78c.png"
                ),
                brandName = "HBO",
                brandShortText = "HBO",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "48.5M subscribers • Official Studio"
            )

            // --- NETFLIX ---
            combined.contains("netflix") || combined.contains("stranger things") || combined.contains("squid game") ||
            combined.contains("wednesday") || combined.contains("bridgerton") || combined.contains("one piece live action") ||
            combined.contains("the witcher") || combined.contains("money heist") || combined.contains("la casa de papel") ||
            combined.contains("lupin") || combined.contains("the crown") || combined.contains("ozark") ||
            combined.contains("mindhunter") || combined.contains("black mirror") || combined.contains("dark ") ||
            combined.contains("outer banks") || combined.contains("queen's gambit") || combined.contains("dahmer") ||
            combined.contains("baby reindeer") || combined.contains("rebel ridge") || combined.contains("red notice") ||
            combined.contains("extraction") || combined.contains("glass onion") || combined.contains("the gray man") ||
            combined.contains("bird box") || combined.contains("klaus") || combined.contains("damsel") ||
            combined.contains("atlas") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Netflix_2015_logo.svg/320px-Netflix_2015_logo.svg.png",
                    "https://image.tmdb.org/t/p/w300/wwemzKWzjKYJFfCeiB57q3r4Bcm.png"
                ),
                brandName = "Netflix",
                brandShortText = "NETFLIX",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "68.2M subscribers • Official Studio"
            )

            // --- APPLE TV+ ---
            combined.contains("apple tv") || combined.contains("ted lasso") || combined.contains("severance") ||
            combined.contains("morning show") || combined.contains("the morning show") || combined.contains("for all mankind") ||
            combined.contains("foundation") || combined.contains("slow horses") || combined.contains("silo") ||
            combined.contains("pachinko") || combined.contains("black bird") || combined.contains("shrinking") ||
            combined.contains("hijack") || combined.contains("masters of the air") || combined.contains("presumed innocent") ||
            combined.contains("killers of the flower moon") || combined.contains("napoleon") || combined.contains("coda") ||
            combined.contains("tetris") || combined.contains("wolfs") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/28/Apple_TV_Plus_Logo.svg/320px-Apple_TV_Plus_Logo.svg.png"
                ),
                brandName = "Apple TV+",
                brandShortText = "APPLE",
                backgroundColor = Color(0xFF222222),
                textColor = Color.White,
                subscriberCountText = "11.1M subscribers • Official Studio"
            )

            // --- AMAZON PRIME VIDEO ---
            combined.contains("prime video") || combined.contains("amazon") || combined.contains("the boys") ||
            combined.contains("gen v") || combined.contains("rings of power") || combined.contains("reacher") ||
            combined.contains("invincible") || combined.contains("fallout") || combined.contains("wheel of time") ||
            combined.contains("the wheel of time") || combined.contains("jack ryan") || combined.contains("terminal list") ||
            combined.contains("good omens") || combined.contains("mrs. maisel") || combined.contains("fleabag") ||
            combined.contains("hazbin hotel") || combined.contains("idea of you") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Prime_Video.png/320px-Prime_Video.png"
                ),
                brandName = "Prime Video",
                brandShortText = "PRIME",
                backgroundColor = Color(0xFF00A8E1),
                textColor = Color.White,
                subscriberCountText = "25.3M subscribers • Official Studio"
            )

            // --- ANIME STUDIOS ---
            // Studio Ghibli
            combined.contains("ghibli") || combined.contains("spirited away") || combined.contains("totoro") ||
            combined.contains("princess mononoke") || combined.contains("howl's moving castle") || combined.contains("howl") ||
            combined.contains("ponyo") || combined.contains("kiki's delivery") || combined.contains("castle in the sky") ||
            combined.contains("boy and the heron") || combined.contains("grave of the fireflies") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0d/Studio_Ghibli_logo.svg/320px-Studio_Ghibli_logo.svg.png"
                ),
                brandName = "Studio Ghibli",
                brandShortText = "GHIBLI",
                backgroundColor = Color(0xFF0077B6),
                textColor = Color.White,
                subscriberCountText = "20.3M subscribers • Official Studio"
            )

            // Toei Animation
            combined.contains("toei") || combined.contains("one piece") || combined.contains("dragon ball") ||
            combined.contains("dragon ball z") || combined.contains("dragon ball super") || combined.contains("dragon ball daima") ||
            combined.contains("sailor moon") || combined.contains("digimon") || combined.contains("saint seiya") ||
            combined.contains("slam dunk") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/Toei_Animation_logo.svg/320px-Toei_Animation_logo.svg.png"
                ),
                brandName = "Toei Animation",
                brandShortText = "TOEI",
                backgroundColor = Color(0xFFD32F2F),
                textColor = Color.White,
                subscriberCountText = "27.5M subscribers • Official Studio"
            )

            // ufotable
            combined.contains("ufotable") || combined.contains("demon slayer") || combined.contains("kimetsu no yaiba") ||
            combined.contains("mugen train") || combined.contains("swordsmith village") || combined.contains("hashira training") ||
            combined.contains("fate/stay night") || combined.contains("fate/zero") || combined.contains("kara no kyoukai") ||
            combined.contains("garden of sinners") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/Ufotable_logo.svg/320px-Ufotable_logo.svg.png"
                ),
                brandName = "ufotable",
                brandShortText = "UFOTABLE",
                backgroundColor = Color(0xFF880E4F),
                textColor = Color.White,
                subscriberCountText = "19.2M subscribers • Official Studio"
            )

            // MAPPA
            combined.contains("mappa") || combined.contains("jujutsu kaisen") || combined.contains("chainsaw man") ||
            combined.contains("final season") || combined.contains("hell's paradise") || combined.contains("vinland saga season 2") ||
            combined.contains("yuri on ice") || combined.contains("dororo") || combined.contains("banana fish") ||
            combined.contains("kakegurui") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/MAPPA_Logo.svg/320px-MAPPA_Logo.svg.png"
                ),
                brandName = "MAPPA",
                brandShortText = "MAPPA",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "16.4M subscribers • Official Studio"
            )

            // Studio Pierrot
            combined.contains("pierrot") || combined.contains("naruto") || combined.contains("shippuden") ||
            combined.contains("boruto") || combined.contains("bleach") || combined.contains("thousand-year blood war") ||
            combined.contains("tokyo ghoul") || combined.contains("black clover") || combined.contains("yu yu hakusho") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Studio_Pierrot_logo.svg/320px-Studio_Pierrot_logo.svg.png"
                ),
                brandName = "Studio Pierrot",
                brandShortText = "PIERROT",
                backgroundColor = Color(0xFFED6C02),
                textColor = Color.White,
                subscriberCountText = "11.2M subscribers • Official Studio"
            )

            // Bones
            combined.contains("bones") || combined.contains("my hero academia") || combined.contains("fullmetal alchemist") ||
            combined.contains("fullmetal") || combined.contains("mob psycho") || combined.contains("bungo stray dogs") ||
            combined.contains("soul eater") || combined.contains("noragami") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Studio_Bones_logo.svg/320px-Studio_Bones_logo.svg.png"
                ),
                brandName = "Bones",
                brandShortText = "BONES",
                backgroundColor = Color(0xFF1E1E1E),
                textColor = Color.White,
                subscriberCountText = "9.8M subscribers • Official Studio"
            )

            // Kyoto Animation
            combined.contains("kyoani") || combined.contains("kyoto animation") || combined.contains("violet evergarden") ||
            combined.contains("silent voice") || combined.contains("a silent voice") || combined.contains("k-on") ||
            combined.contains("clannad") || combined.contains("sound! euphonium") || combined.contains("miss kobayashi") ||
            combined.contains("dragon maid") || combined.contains("hyouka") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/Kyoto_Animation_logo.svg/320px-Kyoto_Animation_logo.svg.png"
                ),
                brandName = "Kyoto Animation",
                brandShortText = "KYOANI",
                backgroundColor = Color(0xFFE91E63),
                textColor = Color.White,
                subscriberCountText = "14.0M subscribers • Official Studio"
            )

            // Madhouse
            combined.contains("madhouse") || combined.contains("death note") || combined.contains("hunter x hunter") ||
            combined.contains("one punch man") || combined.contains("frieren") || combined.contains("beyond journey's end") ||
            combined.contains("no game no life") || combined.contains("overlord") || combined.contains("monster") ||
            combined.contains("perfect blue") || combined.contains("paprika") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Madhouse_logo.svg/320px-Madhouse_logo.svg.png"
                ),
                brandName = "Madhouse",
                brandShortText = "MAD",
                backgroundColor = Color(0xFF9C27B0),
                textColor = Color.White,
                subscriberCountText = "12.1M subscribers • Official Studio"
            )

            // Wit Studio / CloverWorks
            combined.contains("wit studio") || combined.contains("cloverworks") || combined.contains("spy x family") ||
            combined.contains("attack on titan") || combined.contains("vinland saga") || combined.contains("ranking of kings") ||
            combined.contains("bocchi the rock") || combined.contains("dress-up darling") || combined.contains("promised neverland") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e9/Wit_Studio_logo.svg/320px-Wit_Studio_logo.svg.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/CloverWorks_logo.svg/320px-CloverWorks_logo.svg.png"
                ),
                brandName = "Wit Studio",
                brandShortText = "WIT",
                backgroundColor = Color(0xFF00796B),
                textColor = Color.White,
                subscriberCountText = "15.3M subscribers • Official Studio"
            )

            // Production I.G
            combined.contains("production i.g") || combined.contains("ginga eiyuu") || combined.contains("galactic heroes") ||
            combined.contains("legend of the galactic") || combined.contains("psycho-pass") || combined.contains("ghost in the shell") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7f/Production_I.G_logo.svg/200px-Production_I.G_logo.svg.png"
                ),
                brandName = "Production I.G",
                brandShortText = "I.G",
                backgroundColor = Color(0xFF0288D1),
                textColor = Color.White,
                subscriberCountText = "8.9M subscribers • Official Studio"
            )

            // --- OTHER PLATFORMS ---
            combined.contains("vimeo") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://i.vimeocdn.com/favicon/main-touch_180.png",
                    "https://vimeo.com/favicon.ico",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1c/Vimeo_Logo.svg/200px-Vimeo_Logo.svg.png"
                ),
                brandName = cleanName.ifBlank { "Vimeo" },
                brandShortText = "VIMEO",
                backgroundColor = Color(0xFF1AB7EA),
                textColor = Color.White,
                subscriberCountText = "Vimeo Creator"
            )

            combined.contains("tencent") || combined.contains("腾讯") || combined.contains("wetv") || combined.contains("vqq") -> {
                val isAnim = combined.contains("anim") || combined.contains("donghua") || combined.contains("动漫") ||
                        combined.contains("soul land") || combined.contains("perfect world") || combined.contains("battle through") ||
                        combined.contains("swallowed star") || combined.contains("shrouding the heavens") || combined.contains("renegade immortal")
                val isDrama = combined.contains("drama") || combined.contains("series") || combined.contains("电视剧") ||
                        combined.contains("剧场") || combined.contains("untamed") || combined.contains("blossoms") || combined.contains("joy of life")
                val isWeTv = combined.contains("wetv")

                val logos = when {
                    isAnim -> listOf(
                        "https://yt3.googleusercontent.com/5VMDdtEdC4OnWS7MoJBSKYTdOyXuuYmAFU36_COU5bJdYfwmSnsfiCequ_QFdxw6uAokzlnuClE=s900-c-k-c0x00ffffff-no-rj",
                        "https://yt3.googleusercontent.com/BZ0BcoBm1IDjD4a3XbhnHyNZ3MkLnT9FnRxj_ioc6V3yT2nqcCxR0acvzokp7B019c036G5LHQ=s900-c-k-c0x00ffffff-no-rj",
                        "https://v.qq.com/favicon.ico"
                    )
                    isDrama -> listOf(
                        "https://yt3.googleusercontent.com/aDk0tvbNx7OLTimpt12Nm3cWhpvaJtS_DUCE0Si_poqSthHUAqsPwrfoQ-hb1sPq77TTj5Na=s900-c-k-c0x00ffffff-no-rj",
                        "https://yt3.googleusercontent.com/BZ0BcoBm1IDjD4a3XbhnHyNZ3MkLnT9FnRxj_ioc6V3yT2nqcCxR0acvzokp7B019c036G5LHQ=s900-c-k-c0x00ffffff-no-rj",
                        "https://v.qq.com/favicon.ico"
                    )
                    isWeTv -> listOf(
                        "https://yt3.googleusercontent.com/iI9wCyPjt51JS1jObvCKs7n9GCxjDVT7w7wVgTs6ehgDwswVysdYxIEbusqigsJADtlJ-72X75c=s900-c-k-c0x00ffffff-no-rj",
                        "https://yt3.googleusercontent.com/BZ0BcoBm1IDjD4a3XbhnHyNZ3MkLnT9FnRxj_ioc6V3yT2nqcCxR0acvzokp7B019c036G5LHQ=s900-c-k-c0x00ffffff-no-rj",
                        "https://v.qq.com/favicon.ico"
                    )
                    else -> listOf(
                        "https://yt3.googleusercontent.com/BZ0BcoBm1IDjD4a3XbhnHyNZ3MkLnT9FnRxj_ioc6V3yT2nqcCxR0acvzokp7B019c036G5LHQ=s900-c-k-c0x00ffffff-no-rj",
                        "https://yt3.googleusercontent.com/5VMDdtEdC4OnWS7MoJBSKYTdOyXuuYmAFU36_COU5bJdYfwmSnsfiCequ_QFdxw6uAokzlnuClE=s900-c-k-c0x00ffffff-no-rj",
                        "https://v.qq.com/favicon.ico"
                    )
                }

                val finalBrandName = when {
                    cleanName.isNotBlank() && cleanName != "Official Creator" && cleanName != "T" && !cleanName.contains("tv network", ignoreCase = true) -> cleanName
                    isAnim -> "Tencent Video Animation"
                    isDrama -> "Tencent Video Drama"
                    isWeTv -> "WeTV"
                    else -> "Tencent Video"
                }

                BrandLogoInfo(
                    logoUrls = logos,
                    brandName = finalBrandName,
                    brandShortText = if (isWeTv) "WETV" else "V.QQ",
                    backgroundColor = Color(0xFF0052D9),
                    textColor = Color.White,
                    subscriberCountText = "12.8M subscribers"
                )
            }

            combined.contains("bilibili") || combined.contains("哔哩哔哩") || combined.contains("bili") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://i0.hdslb.com/bfs/face/member/noface.jpg",
                    "https://www.bilibili.com/favicon.ico"
                ),
                brandName = cleanName.ifBlank { "哔哩哔哩" },
                brandShortText = "BILI",
                backgroundColor = Color(0xFF00AEEC),
                textColor = Color.White,
                subscriberCountText = "Bilibili Creator"
            )

            combined.contains("hotstar") || combined.contains("jiohotstar") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Disney%2B_Hotstar_logo.svg/200px-Disney%2B_Hotstar_logo.svg.png"
                ),
                brandName = if (name.contains("hotstar", ignoreCase = true)) cleanName else "JioHotstar",
                brandShortText = "HOTSTAR",
                backgroundColor = Color(0xFF0F1014),
                textColor = Color(0xFF0078FF),
                subscriberCountText = "Official Stream • Verified"
            )

            combined.contains("amc") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/AMC_logo_2019.svg/200px-AMC_logo_2019.svg.png"),
                brandName = "AMC Studios",
                brandShortText = "AMC",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "19.5M subscribers"
            )

            // Internet Archive Authentic Brand Matching
            combined.contains("archive.org") || combined.contains("internet archive") || name.contains("archive") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://archive.org/images/glogo.png",
                    "https://archive.org/download/ia-logo/ia-logo.png"
                ),
                brandName = if (cleanName.isNotBlank() && cleanName != "Official Creator") cleanName else "Internet Archive",
                brandShortText = getInitials(if (cleanName.isNotBlank()) cleanName else "IA"),
                backgroundColor = Color(0xFF1E212A),
                textColor = Color.White,
                subscriberCountText = "Internet Archive • Public Domain"
            )

            // Detected movie / TV studio (only if genuine studio name recognized from title)
            videoTitle?.isNotBlank() == true && StudioDetector.detectStudio(videoTitle, isTv = false).isNotBlank() -> {
                val detected = StudioDetector.detectStudio(videoTitle, isTv = false)
                getBrandInfo(detected, null, null)
            }
            // Major Movie / TV Studios
            combined.contains("new line") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/0/03/New_Line_Cinema_logo.svg/200px-New_Line_Cinema_logo.svg.png"),
                brandName = "New Line Cinema",
                brandShortText = "NLC",
                backgroundColor = Color(0xFF0F172A),
                textColor = Color.White,
                subscriberCountText = "19.4M subscribers"
            )

            combined.contains("lionsgate") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Lionsgate_logo.svg/200px-Lionsgate_logo.svg.png"),
                brandName = "Lionsgate Films",
                brandShortText = "LG",
                backgroundColor = Color(0xFF1E1B18),
                textColor = Color(0xFFFFC107),
                subscriberCountText = "21.2M subscribers"
            )

            combined.contains("blumhouse") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Blumhouse_Productions_logo.svg/200px-Blumhouse_Productions_logo.svg.png"),
                brandName = "Blumhouse Productions",
                brandShortText = "BH",
                backgroundColor = Color(0xFF000000),
                textColor = Color.Red,
                subscriberCountText = "14.8M subscribers"
            )

            combined.contains("legendary") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/Legendary_Pictures_logo.svg/200px-Legendary_Pictures_logo.svg.png"),
                brandName = "Legendary Entertainment",
                brandShortText = "LEGENDARY",
                backgroundColor = Color(0xFF111827),
                textColor = Color.White,
                subscriberCountText = "18.3M subscribers"
            )

            combined.contains("a24") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/2/22/A24_logo.svg/200px-A24_logo.svg.png"),
                brandName = "A24",
                brandShortText = "A24",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "12.6M subscribers"
            )

            combined.contains("metro-goldwyn-mayer") || combined.contains("mgm") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/Metro-Goldwyn-Mayer_logo.svg/200px-Metro-Goldwyn-Mayer_logo.svg.png"),
                brandName = "Metro-Goldwyn-Mayer",
                brandShortText = "MGM",
                backgroundColor = Color(0xFF261C14),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "24.1M subscribers"
            )

            combined.contains("universal") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/2/29/Universal_Pictures_logo.svg/200px-Universal_Pictures_logo.svg.png"),
                brandName = "Universal Pictures",
                brandShortText = "UNIVERSAL",
                backgroundColor = Color(0xFF001F3F),
                textColor = Color.White,
                subscriberCountText = "31.0M subscribers"
            )

            combined.contains("paramount") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Paramount_Pictures_logo.svg/200px-Paramount_Pictures_logo.svg.png"),
                brandName = "Paramount Pictures",
                brandShortText = "PARAMOUNT",
                backgroundColor = Color(0xFF002B49),
                textColor = Color.White,
                subscriberCountText = "26.5M subscribers"
            )

            combined.contains("sony pictures") || combined.contains("columbia pictures") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Sony_Pictures_logo.svg/200px-Sony_Pictures_logo.svg.png"),
                brandName = "Sony Pictures",
                brandShortText = "SONY",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "29.8M subscribers"
            )

            combined.contains("20th century") || combined.contains("fox") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/7/77/20th_Century_Studios_logo.svg/200px-20th_Century_Studios_logo.svg.png"),
                brandName = "20th Century Studios",
                brandShortText = "20TH",
                backgroundColor = Color(0xFF1E293B),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "22.0M subscribers"
            )

            // Anime Studios
            combined.contains("production i.g") || combined.contains("ginga") || combined.contains("galactic heroes") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/7/7f/Production_I.G_logo.svg/200px-Production_I.G_logo.svg.png"),
                brandName = "Production I.G",
                brandShortText = "I.G",
                backgroundColor = Color(0xFF0288D1),
                textColor = Color.White,
                subscriberCountText = "8.9M subscribers"
            )

            combined.contains("mappa") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/MAPPA_Logo.svg/200px-MAPPA_Logo.svg.png"),
                brandName = "MAPPA",
                brandShortText = "MAPPA",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "16.4M subscribers"
            )

            combined.contains("toei") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/Toei_Animation_logo.svg/200px-Toei_Animation_logo.svg.png"),
                brandName = "Toei Animation",
                brandShortText = "TOEI",
                backgroundColor = Color(0xFFD32F2F),
                textColor = Color.White,
                subscriberCountText = "27.5M subscribers"
            )

            combined.contains("pierrot") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Studio_Pierrot_logo.svg/200px-Studio_Pierrot_logo.svg.png"),
                brandName = "Studio Pierrot",
                brandShortText = "PIERROT",
                backgroundColor = Color(0xFFED6C02),
                textColor = Color.White,
                subscriberCountText = "11.2M subscribers"
            )

            combined.contains("bones") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Studio_Bones_logo.svg/200px-Studio_Bones_logo.svg.png"),
                brandName = "Bones",
                brandShortText = "BONES",
                backgroundColor = Color(0xFF1E1E1E),
                textColor = Color.White,
                subscriberCountText = "9.8M subscribers"
            )

            combined.contains("madhouse") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Madhouse_logo.svg/200px-Madhouse_logo.svg.png"),
                brandName = "Madhouse",
                brandShortText = "MAD",
                backgroundColor = Color(0xFF9C27B0),
                textColor = Color.White,
                subscriberCountText = "12.1M subscribers"
            )

            combined.contains("ghibli") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/0/0d/Studio_Ghibli_logo.svg/200px-Studio_Ghibli_logo.svg.png"),
                brandName = "Studio Ghibli",
                brandShortText = "GHIBLI",
                backgroundColor = Color(0xFF0077B6),
                textColor = Color.White,
                subscriberCountText = "20.3M subscribers"
            )

            combined.contains("kyoto animation") || combined.contains("kyoani") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/Kyoto_Animation_logo.svg/200px-Kyoto_Animation_logo.svg.png"),
                brandName = "Kyoto Animation",
                brandShortText = "KYOANI",
                backgroundColor = Color(0xFFE91E63),
                textColor = Color.White,
                subscriberCountText = "14.0M subscribers"
            )

            // Major Movie / TV Studios
            combined.contains("house of the dragon") || combined.contains("game of thrones") || combined.contains("last of us") || combined.contains("hbo") || combined.contains("euphoria") || combined.contains("succession") || combined.contains("white lotus") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/HBO_logo.svg/200px-HBO_logo.svg.png",
                    "https://image.tmdb.org/t/p/w200/qq2330a108a.png"
                ),
                brandName = "HBO Max",
                brandShortText = "HBO",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "48.5M subscribers"
            )

            combined.contains("marvel") || combined.contains("avengers") || combined.contains("spider-man") || combined.contains("loki") || combined.contains("wandavision") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Marvel_Logo.svg/200px-Marvel_Logo.svg.png",
                    "https://image.tmdb.org/t/p/w200/420.png"
                ),
                brandName = if (name.contains("marvel")) cleanName else "Marvel Studios",
                brandShortText = "MARVEL",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "34.1M subscribers"
            )

            combined.contains("pixar") || combined.contains("toy story") || combined.contains("inside out") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Pixar_Animation_Studios_logo.svg/200px-Pixar_Animation_Studios_logo.svg.png"
                ),
                brandName = if (name.contains("pixar")) cleanName else "Pixar",
                brandShortText = "PIXAR",
                backgroundColor = Color(0xFF003366),
                textColor = Color.White,
                subscriberCountText = "15.8M subscribers"
            )

            combined.contains("dc studios") || combined.contains("dc comics") || combined.contains("batman") || combined.contains("superman") || combined.contains("joker") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1c/DC_Comics_logo.svg/200px-DC_Comics_logo.svg.png"
                ),
                brandName = if (name.contains("dc")) cleanName else "DC Comics",
                brandShortText = "DC",
                backgroundColor = Color(0xFF0078D4),
                textColor = Color.White,
                subscriberCountText = "22.3M subscribers"
            )

            combined.contains("disney") || combined.contains("mandalorian") || combined.contains("star wars") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Disney%2B_logo.svg/200px-Disney%2B_logo.svg.png"
                ),
                brandName = if (name.contains("disney")) cleanName else "Disney+",
                brandShortText = "DISNEY",
                backgroundColor = Color(0xFF113CCF),
                textColor = Color.White,
                subscriberCountText = "52.0M subscribers"
            )

            combined.contains("hotstar") || combined.contains("jiohotstar") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Disney%2B_Hotstar_logo.svg/200px-Disney%2B_Hotstar_logo.svg.png"
                ),
                brandName = if (name.contains("hotstar", ignoreCase = true)) cleanName else "JioHotstar",
                brandShortText = "HOTSTAR",
                backgroundColor = Color(0xFF0F1014),
                textColor = Color(0xFF0078FF),
                subscriberCountText = "Official Stream • Verified"
            )

            combined.contains("warner") || combined.contains("wb") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Warner_Bros_logo.svg/200px-Warner_Bros_logo.svg.png"
                ),
                brandName = if (name.contains("warner")) cleanName else "Warner Bros",
                brandShortText = "WB",
                backgroundColor = Color(0xFF002B49),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "28.9M subscribers"
            )

            combined.contains("netflix") || combined.contains("stranger things") || combined.contains("squid game") || combined.contains("wednesday") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Netflix_2015_logo.svg/200px-Netflix_2015_logo.svg.png"
                ),
                brandName = if (name.contains("netflix")) cleanName else "Netflix",
                brandShortText = "NETFLIX",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "68.2M subscribers"
            )

            combined.contains("apple tv") || combined.contains("ted lasso") || combined.contains("severance") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/28/Apple_TV_Plus_Logo.svg/200px-Apple_TV_Plus_Logo.svg.png"
                ),
                brandName = if (name.contains("apple")) cleanName else "Apple TV+",
                brandShortText = "APPLE",
                backgroundColor = Color(0xFF222222),
                textColor = Color.White,
                subscriberCountText = "11.1M subscribers"
            )

            combined.contains("agbo") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/AGBO_logo.svg/200px-AGBO_logo.svg.png"),
                brandName = "AGBO",
                brandShortText = "AGBO",
                backgroundColor = Color(0xFF18181B),
                textColor = Color.White,
                subscriberCountText = "Official Studio • Verified"
            )

            combined.contains("punch palace") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = "Punch Palace Productions",
                brandShortText = "PPP",
                backgroundColor = Color(0xFF27272A),
                textColor = Color.White,
                subscriberCountText = "Official Production"
            )

            combined.contains("tea shop") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = "Tea Shop Productions",
                brandShortText = "TEA SHOP",
                backgroundColor = Color(0xFF1E293B),
                textColor = Color.White,
                subscriberCountText = "Official Production"
            )

            combined.contains("amc") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/AMC_logo_2019.svg/200px-AMC_logo_2019.svg.png"),
                brandName = "AMC Studios",
                brandShortText = "AMC",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "19.5M subscribers"
            )

            combined.contains("amazon") || combined.contains("prime video") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Prime_Video.png/200px-Prime_Video.png"
                ),
                brandName = if (name.contains("amazon") || name.contains("prime")) cleanName else "Prime Video",
                brandShortText = "PRIME",
                backgroundColor = Color(0xFF00A8E1),
                textColor = Color.White,
                subscriberCountText = "25.3M subscribers"
            )

            // Internet Archive / Public Domain
            combined.contains("archive.org") || combined.contains("internet archive") || combined.contains("prelinger") || combined.contains("librivox") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://archive.org/images/ia-logo.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/84/Internet_Archive_logo_and_wordmark.svg/200px-Internet_Archive_logo_and_wordmark.svg.png"
                ),
                brandName = cleanName,
                brandShortText = "ARCHIVE",
                backgroundColor = Color(0xFF333333),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "Public Library • Free Access"
            )

            // Adult Studios & Channels
            combined.contains("sislovesme") || combined.contains("sis loves me") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://ci.phncdn.com/users/sislovesme/avatar.jpg",
                    "https://ei.phncdn.com/channels/sislovesme/avatar.jpg"
                ),
                brandName = "SisLovesMe",
                brandShortText = "SLM",
                backgroundColor = Color(0xFFFF4081),
                textColor = Color.White,
                subscriberCountText = "Official Studio • 2.8M Subscribers"
            )

            combined.contains("brazzers") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d7/Brazzers_logo.svg/320px-Brazzers_logo.svg.png",
                    "https://ci.phncdn.com/users/brazzersofficial/avatar.jpg",
                    "https://ei.phncdn.com/channels/brazzers/avatar.jpg"
                ),
                brandName = "Brazzers",
                brandShortText = "ZZ",
                backgroundColor = Color(0xFFFFB300),
                textColor = Color.Black,
                subscriberCountText = "Official Studio • 8.4M Subscribers"
            )

            combined.contains("reality kings") || combined.contains("realitykings") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/Reality_Kings_logo.svg/320px-Reality_Kings_logo.svg.png",
                    "https://ci.phncdn.com/users/realitykings/avatar.jpg",
                    "https://ei.phncdn.com/channels/realitykings/avatar.jpg"
                ),
                brandName = "Reality Kings",
                brandShortText = "RK",
                backgroundColor = Color(0xFFE91E63),
                textColor = Color.White,
                subscriberCountText = "Official Studio • 4.9M Subscribers"
            )

            combined.contains("familystrokes") || combined.contains("family strokes") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://ci.phncdn.com/users/familystrokes/avatar.jpg",
                    "https://ei.phncdn.com/channels/familystrokes/avatar.jpg"
                ),
                brandName = "Family Strokes",
                brandShortText = "FS",
                backgroundColor = Color(0xFF9C27B0),
                textColor = Color.White,
                subscriberCountText = "Official Studio • 3.2M Subscribers"
            )

            combined.contains("blacked") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d0/Blacked_logo.svg/320px-Blacked_logo.svg.png",
                    "https://ci.phncdn.com/users/blacked/avatar.jpg",
                    "https://ei.phncdn.com/channels/blacked/avatar.jpg"
                ),
                brandName = "Blacked",
                brandShortText = "BL",
                backgroundColor = Color(0xFF111111),
                textColor = Color.White,
                subscriberCountText = "Vixen Media Group • Verified"
            )

            combined.contains("vixen") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/68/Vixen_logo.svg/320px-Vixen_logo.svg.png",
                    "https://ci.phncdn.com/users/vixen/avatar.jpg",
                    "https://ei.phncdn.com/channels/vixen/avatar.jpg"
                ),
                brandName = "Vixen",
                brandShortText = "VX",
                backgroundColor = Color(0xFF1A1A1A),
                textColor = Color.White,
                subscriberCountText = "Vixen Media Group • Verified"
            )

            combined.contains("tushy") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d9/Tushy_logo.svg/320px-Tushy_logo.svg.png",
                    "https://ci.phncdn.com/users/tushy/avatar.jpg",
                    "https://ei.phncdn.com/channels/tushy/avatar.jpg"
                ),
                brandName = "Tushy",
                brandShortText = "TY",
                backgroundColor = Color(0xFF0D0D0D),
                textColor = Color.White,
                subscriberCountText = "Vixen Media Group • Verified"
            )

            combined.contains("fakehub") || combined.contains("fake taxi") || combined.contains("faketaxi") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/32/Fake_Taxi_logo.svg/320px-Fake_Taxi_logo.svg.png",
                    "https://ci.phncdn.com/users/faketaxi/avatar.jpg"
                ),
                brandName = "Fake Taxi",
                brandShortText = "FT",
                backgroundColor = Color(0xFFFFCC00),
                textColor = Color.Black,
                subscriberCountText = "Official Channel • 3.5M Subscribers"
            )

            combined.contains("naughty america") || combined.contains("naughtyamerica") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7c/Naughty_America_logo.svg/320px-Naughty_America_logo.svg.png",
                    "https://ci.phncdn.com/users/naughtyamerica/avatar.jpg"
                ),
                brandName = "Naughty America",
                brandShortText = "NA",
                backgroundColor = Color(0xFFB71C1C),
                textColor = Color.White,
                subscriberCountText = "Official Studio • 5.1M Subscribers"
            )

            combined.contains("teamske") || combined.contains("team ske") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://ci.phncdn.com/users/teamske/avatar.jpg",
                    "https://ei.phncdn.com/channels/teamske/avatar.jpg"
                ),
                brandName = "TeamSke",
                brandShortText = "TS",
                backgroundColor = Color(0xFF00ACC1),
                textColor = Color.White,
                subscriberCountText = "Official Studio • 2.6M Subscribers"
            )

            combined.contains("sinstv") || combined.contains("johnny sins") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://ci.phncdn.com/users/sinspov/avatar.jpg",
                    "https://di.phncdn.com/pornstar/johnny-sins.jpg"
                ),
                brandName = "SinsTV",
                brandShortText = "JS",
                backgroundColor = Color(0xFF212121),
                textColor = Color(0xFFFF9900),
                subscriberCountText = "Johnny Sins Official • Verified"
            )

            combined.contains("pornhub") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Pornhub-logo.svg/320px-Pornhub-logo.svg.png"),
                brandName = cleanName,
                brandShortText = "PH",
                backgroundColor = Color(0xFF222222),
                textColor = Color(0xFFFF9900),
                subscriberCountText = "Verified Channel"
            )

            com.example.util.AdultModelMatcher.findModel(cleanName) != null || com.example.util.AdultModelMatcher.isModelInText(cleanName) -> {
                val matched = com.example.util.AdultModelMatcher.findModel(cleanName)
                val modelName = matched?.primaryName ?: cleanName
                val slug = modelName.lowercase().trim().replace(" ", "-").replace(Regex("[^a-z0-9-]"), "")
                BrandLogoInfo(
                    logoUrls = listOf(
                        "https://di.phncdn.com/pornstar/$slug.jpg",
                        "https://ci.phncdn.com/users/$slug/avatar.jpg",
                        "https://ei.phncdn.com/channels/$slug/avatar.jpg"
                    ),
                    brandName = modelName,
                    brandShortText = getInitials(modelName),
                    backgroundColor = Color(0xFF880E4F),
                    textColor = Color.White,
                    subscriberCountText = "Verified Model • Pornhub Partner"
                )
            }

            combined.contains("eporner") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = cleanName,
                brandShortText = "EP",
                backgroundColor = Color(0xFF1E88E5),
                textColor = Color.White,
                subscriberCountText = "HD Creator Network"
            )

            combined.contains("dailymotion") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/d/d2/Dailymotion_logo_%282015%29.svg/200px-Dailymotion_logo_%282015%29.svg.png"),
                brandName = cleanName,
                brandShortText = "DM",
                backgroundColor = Color(0xFF0066DC),
                textColor = Color.White,
                subscriberCountText = "Official Partner"
            )

            combined.contains("twitch") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/d/d3/Twitch_Glitch_Logo_Purple.svg/200px-Twitch_Glitch_Logo_Purple.svg.png"),
                brandName = cleanName,
                brandShortText = "TW",
                backgroundColor = Color(0xFF9146FF),
                textColor = Color.White,
                subscriberCountText = "Twitch Verified Partner"
            )

            combined.contains("bigo") -> BrandLogoInfo(
                logoUrls = listOf("https://static-web.bigolive.tv/as/bigo-static/fe_sdk/img/logo.png", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6f/Bigo_Live_logo.png/200px-Bigo_Live_logo.png"),
                brandName = cleanName,
                brandShortText = "BL",
                backgroundColor = Color(0xFF00E5FF),
                textColor = Color(0xFF002244),
                subscriberCountText = "Bigo Live Broadcaster"
            )

            combined.contains("hanime1") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = cleanName,
                brandShortText = "H1",
                backgroundColor = Color(0xFFFF4081),
                textColor = Color.White,
                subscriberCountText = "Anime Network"
            )

            combined.contains("beeg") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = cleanName,
                brandShortText = "BG",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "Official Partner"
            )

            // Top Creators & Channels
            combined.contains("mrbeast") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/fxGKYucJAVme-Yz4fsdCro6FCrNsBs0x6GcZNquNZP4b0ScG9P_AhXYtqSLQ5WZa8nA2qQDpjw=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "MrBeast",
                brandShortText = "MB",
                backgroundColor = Color(0xFF00A2FF),
                textColor = Color.White,
                subscriberCountText = "310M subscribers"
            )

            combined.contains("marques brownlee") || combined.contains("mkbhd") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/lkH37D712tiyphnu0Id0D5MwwQ7IRuwgQLVD05iMXlDWO-kDHqqdM_5QDEtdSemgnYGSneaO_w=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "Marques Brownlee",
                brandShortText = "MKBHD",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "19.2M subscribers"
            )

            combined.contains("pewdiepie") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/5o-5KMrHGupWwKGMBsqCdJbmTrGLkuSSZrstn290_VnxTopiUBuckWRLTB69q5PramWnIjyT0Q=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "PewDiePie",
                brandShortText = "PDP",
                backgroundColor = Color(0xFFD81B60),
                textColor = Color.White,
                subscriberCountText = "111M subscribers"
            )

            combined.contains("mark rober") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/ytc/AIdro_k6T6x8sLq6Xb1V4e0m4R0Gk3LqgWfP2G3N=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "Mark Rober",
                brandShortText = "MR",
                backgroundColor = Color(0xFF107C41),
                textColor = Color.White,
                subscriberCountText = "58.4M subscribers"
            )

            combined.contains("linus tech tips") || combined.contains("linustechtips") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/Vy6CVSmEcAE2oxgqLjnnN1tCo6UC6vi44_0PLj_GsmOgVvO13_8YxI_1=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "Linus Tech Tips",
                brandShortText = "LTT",
                backgroundColor = Color(0xFFFF6F00),
                textColor = Color.White,
                subscriberCountText = "15.8M subscribers"
            )

            combined.contains("veritasium") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/ytc/AIdro_ljb4k3r2iGq=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "Veritasium",
                brandShortText = "VER",
                backgroundColor = Color(0xFF0078D4),
                textColor = Color.White,
                subscriberCountText = "16.5M subscribers"
            )

            combined.contains("ign") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/H_2n9gqA1P4G7=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "IGN",
                brandShortText = "IGN",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "18.1M subscribers"
            )

            combined.contains("kurzgesagt") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/ytc/AIdro_mDqYk1gWbF=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "Kurzgesagt – In a Nutshell",
                brandShortText = "KG",
                backgroundColor = Color(0xFF673AB7),
                textColor = Color.White,
                subscriberCountText = "23.0M subscribers"
            )

            combined.contains("t-series") || combined.contains("tseries") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/v_PwNTRNXaPZSTYYavvrZrPvdYICeoTvBWqn0SwaBoYTyFLyKnPIvdKnNxWTa3rKnW_GQ87Wd1s=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "T-Series",
                brandShortText = "TS",
                backgroundColor = Color(0xFFD81B60),
                textColor = Color.White,
                subscriberCountText = "270M subscribers"
            )

            combined.contains("ted") || combined.contains("ted-ed") || combined.contains("tedx") -> BrandLogoInfo(
                logoUrls = listOf("https://yt3.googleusercontent.com/ytc/AIdro_l2v6bL=s176-c-k-c0x00ffffff-no-rj"),
                brandName = "TED",
                brandShortText = "TED",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "24.5M subscribers"
            )

            // Dynamic Hash-derived branding for any creator / channel
            else -> {
                val hash = abs(cleanName.hashCode())
                val palette = AVATAR_PALETTE[hash % AVATAR_PALETTE.size]
                val initials = getInitials(cleanName)

                BrandLogoInfo(
                    logoUrls = emptyList(),
                    brandName = cleanName,
                    brandShortText = initials,
                    backgroundColor = palette.first,
                    textColor = palette.second,
                    subscriberCountText = "Verified Creator"
                )
            }
        }
    }

        private fun getInitials(name: String): String {
        val words = name.trim().split(Regex("[\\s•/_-]+")).filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "C"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        }
    }
}
