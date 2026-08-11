package com.example.util

import androidx.compose.ui.graphics.Color

data class BrandLogoInfo(
    val logoUrls: List<String>,
    val brandName: String,
    val brandShortText: String,
    val backgroundColor: Color,
    val textColor: Color,
    val subscriberCountText: String = "48.5M subscribers"
)

object ChannelLogoHelper {

    fun getBrandInfo(uploaderName: String?, rawAvatarUrl: String?, videoTitle: String? = null): BrandLogoInfo {
        if (!rawAvatarUrl.isNullOrEmpty() && (rawAvatarUrl.startsWith("http://") || rawAvatarUrl.startsWith("https://"))) {
            return BrandLogoInfo(
                logoUrls = listOf(rawAvatarUrl),
                brandName = uploaderName ?: "Channel",
                brandShortText = (uploaderName ?: "C").take(2).uppercase(),
                backgroundColor = Color(0xFF1E212A),
                textColor = Color.White
            )
        }

        val name = uploaderName?.lowercase()?.trim() ?: ""
        val title = videoTitle?.lowercase()?.trim() ?: ""
        val combined = "$name $title"

        return when {
            combined.contains("house of the dragon") || combined.contains("game of thrones") || combined.contains("last of us") || combined.contains("hbo") || combined.contains("euphoria") || combined.contains("succession") || combined.contains("white lotus") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/HBO_logo.svg/200px-HBO_logo.svg.png",
                    "https://image.tmdb.org/t/p/w200/qq2330a108a.png"
                ),
                brandName = "HBO",
                brandShortText = "HBO",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "48.5M subscribers"
            )

            combined.contains("chernin") || combined.contains("last house") || combined.contains("planet of the apes") || combined.contains("ford v ferrari") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://image.tmdb.org/t/p/w200/42133.png",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f0/Universal_Pictures_logo_2012.svg/200px-Universal_Pictures_logo_2012.svg.png"
                ),
                brandName = "Chernin Entertainment",
                brandShortText = "CHERNIN",
                backgroundColor = Color(0xFF12141A),
                textColor = Color.White,
                subscriberCountText = "12.4M subscribers"
            )

            combined.contains("evil dead") || combined.contains("new line") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Warner_Bros_logo.svg/200px-Warner_Bros_logo.svg.png"
                ),
                brandName = "New Line Cinema",
                brandShortText = "NEW LINE",
                backgroundColor = Color(0xFF002B49),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "18.2M subscribers"
            )

            combined.contains("marvel") || combined.contains("avengers") || combined.contains("spider-man") || combined.contains("loki") || combined.contains("wandavision") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Marvel_Logo.svg/200px-Marvel_Logo.svg.png",
                    "https://image.tmdb.org/t/p/w200/420.png"
                ),
                brandName = "Marvel Studios",
                brandShortText = "MARVEL",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "34.1M subscribers"
            )

            combined.contains("pixar") || combined.contains("toy story") || combined.contains("inside out") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Pixar_Animation_Studios_logo.svg/200px-Pixar_Animation_Studios_logo.svg.png"
                ),
                brandName = "Pixar",
                brandShortText = "PIXAR",
                backgroundColor = Color(0xFF003366),
                textColor = Color.White,
                subscriberCountText = "15.8M subscribers"
            )

            combined.contains("dc studios") || combined.contains("dc comics") || combined.contains("batman") || combined.contains("superman") || combined.contains("joker") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1c/DC_Comics_logo.svg/200px-DC_Comics_logo.svg.png"
                ),
                brandName = "DC Comics",
                brandShortText = "DC",
                backgroundColor = Color(0xFF0078D4),
                textColor = Color.White,
                subscriberCountText = "22.3M subscribers"
            )

            combined.contains("disney") || combined.contains("mandalorian") || combined.contains("star wars") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Disney%2B_logo.svg/200px-Disney%2B_logo.svg.png"
                ),
                brandName = "Disney+",
                brandShortText = "DISNEY",
                backgroundColor = Color(0xFF113CCF),
                textColor = Color.White,
                subscriberCountText = "52.0M subscribers"
            )

            combined.contains("universal") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f0/Universal_Pictures_logo_2012.svg/200px-Universal_Pictures_logo_2012.svg.png"
                ),
                brandName = "Universal Pictures",
                brandShortText = "UNI",
                backgroundColor = Color(0xFF0D1B2A),
                textColor = Color.White,
                subscriberCountText = "19.5M subscribers"
            )

            combined.contains("warner") || combined.contains("wb") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Warner_Bros_logo.svg/200px-Warner_Bros_logo.svg.png"
                ),
                brandName = "Warner Bros",
                brandShortText = "WB",
                backgroundColor = Color(0xFF002B49),
                textColor = Color(0xFFFFD700),
                subscriberCountText = "28.9M subscribers"
            )

            combined.contains("sony") || combined.contains("columbia") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Sony_Pictures_Entertainment_logo.svg/200px-Sony_Pictures_Entertainment_logo.svg.png"
                ),
                brandName = "Sony Pictures",
                brandShortText = "SONY",
                backgroundColor = Color(0xFF000000),
                textColor = Color.White,
                subscriberCountText = "14.2M subscribers"
            )

            combined.contains("paramount") || combined.contains("yellowstone") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/Paramount_Pictures_2022.svg/200px-Paramount_Pictures_2022.svg.png"
                ),
                brandName = "Paramount+",
                brandShortText = "PARA",
                backgroundColor = Color(0xFF0033A0),
                textColor = Color.White,
                subscriberCountText = "16.7M subscribers"
            )

            combined.contains("netflix") || combined.contains("stranger things") || combined.contains("squid game") || combined.contains("wednesday") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Netflix_2015_logo.svg/200px-Netflix_2015_logo.svg.png"
                ),
                brandName = "Netflix",
                brandShortText = "NETFLIX",
                backgroundColor = Color(0xFFE50914),
                textColor = Color.White,
                subscriberCountText = "68.2M subscribers"
            )

            combined.contains("apple tv") || combined.contains("ted lasso") || combined.contains("severance") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/28/Apple_TV_Plus_Logo.svg/200px-Apple_TV_Plus_Logo.svg.png"
                ),
                brandName = "Apple TV+",
                brandShortText = "APPLE",
                backgroundColor = Color(0xFF222222),
                textColor = Color.White,
                subscriberCountText = "11.1M subscribers"
            )

            combined.contains("amazon") || combined.contains("the boys") || combined.contains("reacher") || combined.contains("fallout") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Prime_Video.png/200px-Prime_Video.png"
                ),
                brandName = "Amazon MGM Studios",
                brandShortText = "PRIME",
                backgroundColor = Color(0xFF00A8E1),
                textColor = Color.White,
                subscriberCountText = "25.3M subscribers"
            )

            combined.contains("amc") || combined.contains("breaking bad") || combined.contains("better call saul") || combined.contains("walking dead") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/AMC_logo.svg/200px-AMC_logo.svg.png"
                ),
                brandName = "AMC Networks",
                brandShortText = "AMC",
                backgroundColor = Color(0xFF222222),
                textColor = Color.White,
                subscriberCountText = "15.0M subscribers"
            )

            combined.contains("flex x cop") || combined.contains("sbs") || combined.contains("k drama") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/41/BBC_Logo_2021.svg/200px-BBC_Logo_2021.svg.png"
                ),
                brandName = "SBS Drama",
                brandShortText = "SBS",
                backgroundColor = Color(0xFF005BAC),
                textColor = Color.White,
                subscriberCountText = "8.9M subscribers"
            )

            combined.contains("white fox") || combined.contains("ghibli") || combined.contains("toei") ||
            combined.contains("mappa") || combined.contains("aniplex") || combined.contains("madhouse") ||
            combined.contains("anime") || combined.contains("jikan") || combined.contains("nyaa") ||
            combined.contains("gintama") || combined.contains("naruto") || combined.contains("jujutsu") -> BrandLogoInfo(
                logoUrls = listOf(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/eb/Studio_Ghibli_logo.svg/200px-Studio_Ghibli_logo.svg.png"
                ),
                brandName = if (uploaderName.isNullOrBlank() || uploaderName.lowercase().contains("tv network")) "Tokyo TV / Anime" else uploaderName,
                brandShortText = "ANIME",
                backgroundColor = Color(0xFF673AB7),
                textColor = Color.White,
                subscriberCountText = "21.4M subscribers"
            )

            else -> {
                val displayName = if (uploaderName.isNullOrBlank() || uploaderName.lowercase().contains("tv network")) "HBO / Warner Studios" else uploaderName
                BrandLogoInfo(
                    logoUrls = listOf(
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/HBO_logo.svg/200px-HBO_logo.svg.png"
                    ),
                    brandName = displayName,
                    brandShortText = displayName.take(3).uppercase(),
                    backgroundColor = Color(0xFF1E212A),
                    textColor = Color(0xFFFFC107),
                    subscriberCountText = "14.5M subscribers"
                )
            }
        }
    }
}
