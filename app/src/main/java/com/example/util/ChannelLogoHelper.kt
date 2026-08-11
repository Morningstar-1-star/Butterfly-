package com.example.util

import androidx.compose.ui.graphics.Color

data class BrandLogoInfo(
    val logoUrls: List<String>,
    val brandName: String,
    val brandShortText: String,
    val backgroundColor: Color,
    val textColor: Color
)

object ChannelLogoHelper {

    fun getBrandInfo(uploaderName: String?, rawAvatarUrl: String?): BrandLogoInfo {
        if (!rawAvatarUrl.isNullOrEmpty() && (rawAvatarUrl.startsWith("http://") || rawAvatarUrl.startsWith("https://"))) {
            return BrandLogoInfo(
                logoUrls = listOf(rawAvatarUrl),
                brandName = uploaderName ?: "Channel",
                brandShortText = (uploaderName ?: "C").take(2).uppercase(),
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White
            )
        }

        val name = uploaderName?.lowercase()?.trim() ?: ""

        return when {
            name.contains("marvel") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Marvel_Logo.svg/200px-Marvel_Logo.svg.png",
                    "https://image.tmdb.org/t/p/w200/95213.png"
                ),
                brandName = "Marvel Studios",
                brandShortText = "MARVEL",
                backgroundColor = Color(0xFFE50914), // Marvel Red
                textColor = Color.White
            )

            name.contains("pixar") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Pixar_Animation_Studios_logo.svg/200px-Pixar_Animation_Studios_logo.svg.png",
                    "https://image.tmdb.org/t/p/w200/5LocL12X8mCGPKhxOHPA83P4asB.png"
                ),
                brandName = "Pixar",
                brandShortText = "PIXAR",
                backgroundColor = Color(0xFF003366), // Pixar Navy
                textColor = Color.White
            )

            name.contains("dc studios") || name.contains("dc comics") || name.contains("dc ") || name == "dc" -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1c/DC_Comics_logo.svg/200px-DC_Comics_logo.svg.png"
                ),
                brandName = "DC Comics",
                brandShortText = "DC",
                backgroundColor = Color(0xFF0078D4), // DC Blue
                textColor = Color.White
            )

            name.contains("disney") || name.contains("feature studio") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Disney%2B_logo.svg/200px-Disney%2B_logo.svg.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Disney_wordmark.svg/200px-Disney_wordmark.svg.png"
                ),
                brandName = "Disney",
                brandShortText = "DISNEY",
                backgroundColor = Color(0xFF113CCF), // Disney Royal Blue
                textColor = Color.White
            )

            name.contains("universal") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f0/Universal_Pictures_logo_2012.svg/200px-Universal_Pictures_logo_2012.svg.png"
                ),
                brandName = "Universal",
                brandShortText = "UNI",
                backgroundColor = Color(0xFF0D1B2A),
                textColor = Color.White
            )

            name.contains("warner") || name.contains("wb") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Warner_Bros_logo.svg/200px-Warner_Bros_logo.svg.png"
                ),
                brandName = "Warner Bros",
                brandShortText = "WB",
                backgroundColor = Color(0xFF002B49), // WB Shield Blue
                textColor = Color(0xFFFFD700) // Gold
            )

            name.contains("sony") || name.contains("columbia") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Sony_Pictures_Entertainment_logo.svg/200px-Sony_Pictures_Entertainment_logo.svg.png"
                ),
                brandName = "Sony Pictures",
                brandShortText = "SONY",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White
            )

            name.contains("paramount") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/Paramount_Pictures_2022.svg/200px-Paramount_Pictures_2022.svg.png"
                ),
                brandName = "Paramount",
                brandShortText = "PARA",
                backgroundColor = Color(0xFF0033A0),
                textColor = Color.White
            )

            name.contains("20th century") || name.contains("fox") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/20th_Century_Studios_logo_2020.svg/200px-20th_Century_Studios_logo_2020.svg.png"
                ),
                brandName = "20th Century Studios",
                brandShortText = "20TH",
                backgroundColor = Color(0xFFD4AF37),
                textColor = Color.Black
            )

            name.contains("netflix") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Netflix_2015_logo.svg/200px-Netflix_2015_logo.svg.png"
                ),
                brandName = "Netflix",
                brandShortText = "NETFLIX",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White
            )

            name.contains("hbo") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/HBO_logo.svg/200px-HBO_logo.svg.png"
                ),
                brandName = "HBO",
                brandShortText = "HBO",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White
            )

            name.contains("white fox") || name.contains("ghibli") || name.contains("toei") ||
            name.contains("mappa") || name.contains("aniplex") || name.contains("madhouse") ||
            name.contains("anime") || name.contains("jikan") || name.contains("nyaa") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/eb/Studio_Ghibli_logo.svg/200px-Studio_Ghibli_logo.svg.png"
                ),
                brandName = uploaderName ?: "Anime Studio",
                brandShortText = "ANIME",
                backgroundColor = Color(0xFF673AB7), // Anime Purple
                textColor = Color.White
            )

            else -> BrandLogoInfo(
                logoUrls = emptyList(),
                brandName = uploaderName ?: "Studio",
                brandShortText = (uploaderName ?: "S").split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase().ifEmpty { "S" },
                backgroundColor = Color(0xFF333333),
                textColor = Color(0xFFFFC107)
            )
        }
    }
}
