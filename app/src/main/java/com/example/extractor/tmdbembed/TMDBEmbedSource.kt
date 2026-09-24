package com.example.extractor.tmdbembed

enum class TMDBEmbedSource(
    val id: String,
    val displayName: String,
    val supportsTv: Boolean = true,
    val defaultPriority: Int = 50
) {
    SHOWBOX("showbox", "Showbox/FebBox", supportsTv = true, defaultPriority = 60),
    FOUR_K_HD_HUB("4khdhub", "4KHDHub", supportsTv = true, defaultPriority = 55),
    VIXSRC("vixsrc", "VixSrc", supportsTv = true, defaultPriority = 95),
    VIDEASY("videasy", "Videasy", supportsTv = true, defaultPriority = 90),
    VIDLINK("vidlink", "Vidlink", supportsTv = true, defaultPriority = 92),
    DAHMER_MOVIES("dahmermovies", "DahmerMovies", supportsTv = true, defaultPriority = 50),
    STREAMFLIX("streamflix", "StreamFlix", supportsTv = true, defaultPriority = 70),
    VAPLAYER("vaplayer", "VaPlayer", supportsTv = true, defaultPriority = 85),
    CASTLE_TV("castletv", "CastleTV", supportsTv = true, defaultPriority = 75),
    HDGHAR_TV("hdghartv", "HDGharTV", supportsTv = true, defaultPriority = 65),
    NETMIRROR("netmirror", "NetMirror", supportsTv = true, defaultPriority = 94),
    ONETOUCH_TV("onetouchtv", "OneTouchTV", supportsTv = true, defaultPriority = 72),
    ZXCSTREAMS("zxcstreams", "ZXCStreams", supportsTv = true, defaultPriority = 68);

    companion object {
        fun fromId(id: String): TMDBEmbedSource? {
            val clean = id.trim().lowercase().replace("-", "_").replace(" ", "_")
            return entries.firstOrNull {
                it.id.equals(clean, ignoreCase = true) ||
                        it.name.equals(clean, ignoreCase = true) ||
                        clean.contains(it.id)
            }
        }

        val allSources: List<TMDBEmbedSource> = listOf(
            SHOWBOX,
            FOUR_K_HD_HUB,
            VIXSRC,
            VIDEASY,
            VIDLINK,
            DAHMER_MOVIES,
            STREAMFLIX,
            VAPLAYER,
            CASTLE_TV,
            HDGHAR_TV,
            NETMIRROR,
            ONETOUCH_TV,
            ZXCSTREAMS
        )
    }
}
