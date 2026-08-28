package com.example.util

object StudioDetector {

    fun detectStudio(title: String, isTv: Boolean): String {
        val lower = title.lowercase()
        val hash = kotlin.math.abs(title.hashCode())

        return when {
            // Horror & Cult
            lower.contains("evil dead") || lower.contains("conjuring") || lower.contains("annabelle") || lower.contains("nun") || lower.contains("it ") || lower.contains("shining") -> "New Line Cinema"
            lower.contains("saw ") || lower.contains("spiral") || lower.contains("john wick") || lower.contains("hunger games") || lower.contains("expendables") || lower.contains("now you see me") -> "Lionsgate Films"
            lower.contains("paranormal") || lower.contains("insidious") || lower.contains("purge") || lower.contains("m3gan") || lower.contains("fnaf") || lower.contains("five nights") || lower.contains("get out") || lower.contains("us ") || lower.contains("nope") -> "Blumhouse Productions"

            // Legendary / Sci-Fi / Action
            lower.contains("colony") || lower.contains("dune") || lower.contains("godzilla") || lower.contains("kong") || lower.contains("pacific rim") || lower.contains("interstellar") || lower.contains("inception") -> "Legendary Entertainment"

            // A24 / Indie
            lower.contains("everything everywhere") || lower.contains("midsommar") || lower.contains("hereditary") || lower.contains("civil war") || lower.contains("the whale") || lower.contains("unctuous") || lower.contains("a24") -> "A24"

            // MGM
            lower.contains("james bond") || lower.contains("007") || lower.contains("creed") || lower.contains("rocky") || lower.contains("roboCop") || lower.contains("poltergeist") -> "Metro-Goldwyn-Mayer"

            // Pixar
            lower.contains("toy story") || lower.contains("monsters inc") || lower.contains("finding nemo") ||
            lower.contains("finding dory") || lower.contains("cars ") || lower.contains("incredibles") ||
            lower.contains("ratatouille") || lower.contains("wall-e") || lower.contains("coco") ||
            lower.contains("soul") || lower.contains("inside out") || lower.contains("turning red") ||
            lower.contains("elemental") || lower.contains("lightyear") || lower.contains("luca") ||
            lower.contains("onward") || lower.contains("pixar") -> "Pixar Animation Studios"

            // Marvel
            lower.contains("spider") || lower.contains("avengers") || lower.contains("marvel") ||
            lower.contains("iron man") || lower.contains("thor") || lower.contains("captain america") ||
            lower.contains("black panther") || lower.contains("doctor strange") || lower.contains("ant-man") ||
            lower.contains("guardians of the galaxy") || lower.contains("deadpool") || lower.contains("wolverine") ||
            lower.contains("x-men") || lower.contains("venom") || lower.contains("loki") || lower.contains("wandavision") -> "Marvel Studios"

            // DC
            lower.contains("batman") || lower.contains("superman") || lower.contains("joker") ||
            lower.contains("dc ") || lower.contains("wonder woman") || lower.contains("aquaman") ||
            lower.contains("the flash") || lower.contains("justice league") || lower.contains("suicide squad") ||
            lower.contains("shazam") || lower.contains("peacemaker") -> "DC Studios"

            // Disney
            lower.contains("frozen") || lower.contains("mickey") || lower.contains("lion king") ||
            lower.contains("aladdin") || lower.contains("moana") || lower.contains("encanto") ||
            lower.contains("zootopia") || lower.contains("tangled") || lower.contains("disney") ||
            lower.contains("maleficent") || lower.contains("cruella") || lower.contains("wish") -> "Walt Disney Pictures"

            // Warner Bros
            lower.contains("harry potter") || lower.contains("fantastic beasts") || lower.contains("matrix") ||
            lower.contains("wonka") || lower.contains("barbie") || lower.contains("oppenheimer") ||
            lower.contains("warner") -> "Warner Bros. Pictures"

            // Universal
            lower.contains("jurassic") || lower.contains("fast & furious") || lower.contains("fast and furious") ||
            lower.contains("minions") || lower.contains("despicable me") || lower.contains("shrek") ||
            lower.contains("puss in boots") || lower.contains("kung fu panda") || lower.contains("universal") -> "Universal Pictures"

            // 20th Century / Fox
            lower.contains("star wars") || lower.contains("avatar") || lower.contains("alien") ||
            lower.contains("predator") || lower.contains("planet of the apes") || lower.contains("kingsman") ||
            lower.contains("20th century") || lower.contains("fox") -> "20th Century Studios"

            // Paramount
            lower.contains("sonic") || lower.contains("top gun") || lower.contains("mission impossible") ||
            lower.contains("transformers") || lower.contains("star trek") || lower.contains("paramount") ||
            lower.contains("ninja turtles") || lower.contains("tmnt") -> "Paramount Pictures"

            // Sony
            lower.contains("ghostbusters") || lower.contains("jumanji") || lower.contains("men in black") ||
            lower.contains("karate kid") || lower.contains("equalizer") || lower.contains("sony") -> "Sony Pictures"

            // Anime Specific
            lower.contains("ginga eiyuu") || lower.contains("galactic heroes") || lower.contains("legend of the galactic") || lower.contains("psycho-pass") || lower.contains("ghost in the shell") -> "Production I.G"
            lower.contains("attack on titan") || lower.contains("jujutsu kaisen") || lower.contains("chainsaw man") || lower.contains("demon slayer") || lower.contains("mappa") -> "MAPPA"
            lower.contains("one piece") || lower.contains("dragon ball") || lower.contains("sailor moon") || lower.contains("digimon") || lower.contains("toei") -> "Toei Animation"
            lower.contains("naruto") || lower.contains("bleach") || lower.contains("tokyo ghoul") || lower.contains("pierrot") -> "Studio Pierrot"
            lower.contains("my hero academia") || lower.contains("fullmetal") || lower.contains("mob psycho") || lower.contains("bones") -> "Bones"
            lower.contains("death note") || lower.contains("hunter x hunter") || lower.contains("one punch man") || lower.contains("madhouse") -> "Madhouse"
            lower.contains("spirited away") || lower.contains("totoro") || lower.contains("mononoke") || lower.contains("howl") || lower.contains("ghibli") -> "Studio Ghibli"
            lower.contains("violet evergarden") || lower.contains("silent voice") || lower.contains("clannad") || lower.contains("kyoani") -> "Kyoto Animation"
            lower.contains("steins;gate") || lower.contains("re:zero") || lower.contains("white fox") -> "White Fox"
            lower.contains("sword art online") || lower.contains("solo leveling") || lower.contains("fate/") || lower.contains("a-1") -> "A-1 Pictures"

            // Streaming / TV
            lower.contains("stranger things") || lower.contains("squid game") || lower.contains("wednesday") || lower.contains("witcher") || lower.contains("bridgerton") || lower.contains("netflix") -> "Netflix"
            lower.contains("game of thrones") || lower.contains("house of the dragon") || lower.contains("last of us") || lower.contains("euphoria") || lower.contains("hbo") || lower.contains("succession") -> "HBO Max"
            lower.contains("breaking bad") || lower.contains("better call saul") || lower.contains("walking dead") || lower.contains("amc") -> "AMC Networks"
            lower.contains("boys") || lower.contains("rings of power") || lower.contains("reacher") || lower.contains("amazon") -> "Amazon MGM Studios"

            isTv -> {
                val tvStudios = listOf("HBO Max", "Universal Television", "Warner Bros. Television", "Paramount Network", "AMC Networks", "Netflix", "Sony Pictures Television", "FX Networks", "BBC Studios", "Showtime")
                tvStudios[hash % tvStudios.size]
            }
            else -> {
                val filmStudios = listOf("Warner Bros. Pictures", "Universal Pictures", "Paramount Pictures", "Sony Pictures", "20th Century Studios", "Lionsgate Films", "New Line Cinema", "Legendary Entertainment", "A24", "Metro-Goldwyn-Mayer")
                filmStudios[hash % filmStudios.size]
            }
        }
    }

    fun isStudioName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.trim().lowercase()
        return lower.contains("marvel") || lower.contains("dc studios") || lower.contains("dc comics") ||
               lower.contains("warner") || lower.contains("wb") || lower.contains("disney") ||
               lower.contains("pixar") || lower.contains("universal") || lower.contains("paramount") ||
               lower.contains("sony") || lower.contains("columbia") || lower.contains("20th century") ||
               lower.contains("fox") || lower.contains("lionsgate") || lower.contains("new line") ||
               lower.contains("legendary") || lower.contains("blumhouse") || lower.contains("a24") ||
               lower.contains("metro-goldwyn") || lower.contains("mgm") || lower.contains("ghibli") ||
               lower.contains("mappa") || lower.contains("toei") || lower.contains("pierrot") ||
               lower.contains("bones") || lower.contains("madhouse") || lower.contains("production i.g") ||
               lower.contains("kyoani") || lower.contains("netflix") || lower.contains("hbo") ||
               lower.contains("amc") || lower.contains("amazon") || lower.contains("apple tv") ||
               lower.contains("torrent stream") || lower.contains("p2p") || lower.contains("vegamovies") ||
               lower.contains("hdhub") || lower.contains("movie studio") || lower.contains("television")
    }

    fun getStudioSearchQuery(studioName: String): String {
        val lower = studioName.lowercase()
        return when {
            lower.contains("marvel") -> "Marvel"
            lower.contains("dc") -> "DC"
            lower.contains("warner") -> "Warner Bros"
            lower.contains("disney") -> "Disney"
            lower.contains("pixar") -> "Pixar"
            lower.contains("universal") -> "Universal"
            lower.contains("paramount") -> "Paramount"
            lower.contains("sony") -> "Sony"
            lower.contains("20th") || lower.contains("fox") -> "Avatar"
            lower.contains("a24") -> "A24"
            lower.contains("lionsgate") -> "John Wick"
            lower.contains("legendary") -> "Dune"
            lower.contains("netflix") -> "Netflix"
            lower.contains("hbo") -> "HBO"
            lower.contains("ghibli") -> "Studio Ghibli"
            lower.contains("mappa") -> "MAPPA"
            else -> studioName.replace("Pictures", "").replace("Studios", "").replace("Films", "").trim()
        }
    }
}
