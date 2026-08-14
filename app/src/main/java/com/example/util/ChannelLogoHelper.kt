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

            // Dynamic Hash-derived branding for any YouTube channel / general creator
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

