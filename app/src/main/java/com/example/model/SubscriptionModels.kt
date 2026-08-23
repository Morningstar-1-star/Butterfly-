package com.example.model

data class SubscribedChannel(
    val id: String,
    val name: String,
    val handle: String = "",
    val avatarUrl: String? = null,
    val subscriberCount: String = "1.2M subscribers",
    val hasUnreadUpdates: Boolean = true,
    val notificationEnabled: Boolean = true,
    val description: String = "",
    val bannerUrl: String? = null
)

object DefaultSuggestedChannels {
    val list = listOf(
        SubscribedChannel(
            id = "indiainpixels",
            name = "India in Pixels by Ashris",
            handle = "@indiainpixels",
            avatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_md5_0X_T8=s176-c-k-c0x00ffffff-no-rj",
            subscriberCount = "775K subscribers",
            description = "Visualizing data, maps, history, culture and stories from India."
        ),
        SubscribedChannel(
            id = "mkbhd",
            name = "Marques Brownlee",
            handle = "@mkbhd",
            avatarUrl = "https://yt3.googleusercontent.com/lkH37D712tiyphnu0Id0D5MwwQ7IRuwgQLVD05iMXlDWO-kDHqqd1m552P2lEgsKoBtN-ivcxQ=s176-c-k-c0x00ffffff-no-rj",
            subscriberCount = "19.1M subscribers",
            description = "Quality Tech Videos | YouTuber | Geek | Consumer Electronics"
        ),
        SubscribedChannel(
            id = "veritasium",
            name = "Veritasium",
            handle = "@veritasium",
            avatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_k4m1K9w921F7sEw73a1197908=s176-c-k-c0x00ffffff-no-rj",
            subscriberCount = "15.8M subscribers",
            description = "An element of truth - videos about science, education, and anything interesting."
        ),
        SubscribedChannel(
            id = "kurzgesagt",
            name = "Kurzgesagt – In a Nutshell",
            handle = "@kurzgesagt",
            avatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_ktz_a9P3QvG-qXG7G1w=s176-c-k-c0x00ffffff-no-rj",
            subscriberCount = "22.4M subscribers",
            description = "Videos explaining things with optimistic nihilism. Science, space, humanity."
        ),
        SubscribedChannel(
            id = "mrbeast",
            name = "MrBeast",
            handle = "@mrbeast",
            avatarUrl = "https://yt3.googleusercontent.com/fxGKYucJAVme-YzgnGQruSbgReR_TH_fixufpqomYphxoeo1T7pnWHPgdfQC3fYMGQIKYZZRiQ=s176-c-k-c0x00ffffff-no-rj",
            subscriberCount = "320M subscribers",
            description = "I want to make the world a better place before I die."
        ),
        SubscribedChannel(
            id = "ted",
            name = "TED",
            handle = "@ted",
            avatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_n8c_7X_T8=s176-c-k-c0x00ffffff-no-rj",
            subscriberCount = "24.1M subscribers",
            description = "Ideas worth spreading. TED Talks on science, tech, culture, design."
        )
    )
}

