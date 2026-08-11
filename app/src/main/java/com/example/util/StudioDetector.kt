package com.example.util

object StudioDetector {

    fun detectStudio(title: String, isTv: Boolean): String {
        val lower = title.lowercase()
        return when {
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
            lower.contains("harry potter") || lower.contains("fantastic beasts") || lower.contains("dune") ||
            lower.contains("godzilla") || lower.contains("kong") || lower.contains("matrix") ||
            lower.contains("interstellar") || lower.contains("inception") || lower.contains("wonka") ||
            lower.contains("barbie") || lower.contains("oppenheimer") || lower.contains("warner") -> "Warner Bros. Pictures"

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

            // Anime
            lower.contains("steins;gate") || lower.contains("naruto") || lower.contains("one piece") ||
            lower.contains("bleach") || lower.contains("demon slayer") || lower.contains("attack on titan") ||
            lower.contains("jujutsu kaisen") || lower.contains("my hero academia") || lower.contains("dragon ball") ||
            lower.contains("death note") || lower.contains("fullmetal") || lower.contains("hunter x hunter") ||
            lower.contains("sword art online") || lower.contains("tokyo ghoul") || lower.contains("chainsaw man") ||
            lower.contains("spy x family") || lower.contains("vinland") || lower.contains("solo leveling") ||
            lower.contains("haikyuu") || lower.contains("mob psycho") || lower.contains("overlord") ||
            lower.contains("re:zero") || lower.contains("konosuba") || lower.contains("fate/") ||
            lower.contains("code geass") || lower.contains("evangelion") || lower.contains("white fox") ||
            lower.contains("mappa") || lower.contains("ufotable") || lower.contains("toei") ||
            lower.contains("bones") || lower.contains("madhouse") || lower.contains("ghibli") ||
            lower.contains("kyoani") || lower.contains("anime") -> "White Fox (Anime)"

            // Streaming / TV Originals
            lower.contains("stranger things") || lower.contains("squid game") || lower.contains("wednesday") ||
            lower.contains("witcher") || lower.contains("bridgerton") || lower.contains("netflix") -> "Netflix"

            lower.contains("game of thrones") || lower.contains("house of the dragon") ||
            lower.contains("last of us") || lower.contains("euphoria") || lower.contains("hbo") -> "HBO Original"

            isTv -> "TV Network"
            else -> "Hollywood Cinema"
        }
    }
}
