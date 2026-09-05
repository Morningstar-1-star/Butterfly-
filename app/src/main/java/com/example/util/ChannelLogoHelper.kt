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

    fun getBrandInfo(uploaderName: String?, rawAvatarUrl: String?, videoTitle: String? = null): BrandLogoInfo {
        val cleanName = when {
            uploaderName.isNullOrBlank() -> "Official Creator"
            uploaderName.trim().lowercase() == "tv network" -> "Verified Studio"
            else -> uploaderName.trim()
        }

        // If a real remote avatar URL is provided, respect it completely
        if (!rawAvatarUrl.isNullOrEmpty() && (rawAvatarUrl.startsWith("http://") || rawAvatarUrl.startsWith("https://"))) {
            return BrandLogoInfo(
                logoUrls = listOf(rawAvatarUrl),
                brandName = cleanName,
                brandShortText = getInitials(cleanName),
                backgroundColor = Color(0xFF1E212A),
                textColor = Color.White,
                subscriberCountText = "Verified Channel"
            )
        }

        val name = cleanName.lowercase()
        val title = videoTitle?.lowercase()?.trim() ?: ""
        val combined = "$name $title"

        return when {
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
                logoUrls = emptyList(),
                brandName = "SisLovesMe",
                brandShortText = "SLM",
                backgroundColor = Color(0xFFFF4081),
                textColor = Color.White,
                subscriberCountText = "Official Studio • Verified"
            )

            combined.contains("brazzers") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = "Brazzers",
                brandShortText = "ZZ",
                backgroundColor = Color(0xFFFFB300),
                textColor = Color.Black,
                subscriberCountText = "Official Studio • Verified"
            )

            combined.contains("reality kings") || combined.contains("realitykings") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = "Reality Kings",
                brandShortText = "RK",
                backgroundColor = Color(0xFFE91E63),
                textColor = Color.White,
                subscriberCountText = "Official Studio • Verified"
            )

            combined.contains("familystrokes") || combined.contains("family strokes") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = "Family Strokes",
                brandShortText = "FS",
                backgroundColor = Color(0xFF9C27B0),
                textColor = Color.White,
                subscriberCountText = "Official Studio • Verified"
            )

            combined.contains("blacked") || combined.contains("vixen") || combined.contains("tushy") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = cleanName,
                brandShortText = cleanName.take(3).uppercase(),
                backgroundColor = Color(0xFF212121),
                textColor = Color.White,
                subscriberCountText = "Official Channel • Verified"
            )

            combined.contains("pornhub") -> BrandLogoInfo(
                logoUrls = listOf("https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Pornhub-logo.svg/200px-Pornhub-logo.svg.png"),
                brandName = cleanName,
                brandShortText = "PH",
                backgroundColor = Color(0xFF222222),
                textColor = Color(0xFFFF9900),
                subscriberCountText = "Verified Channel"
            )

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

            combined.contains("hqporner") -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = cleanName,
                brandShortText = "HQ",
                backgroundColor = Color(0xFF00C853),
                textColor = Color.White,
                subscriberCountText = "Ultra HD 4K CDN"
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

