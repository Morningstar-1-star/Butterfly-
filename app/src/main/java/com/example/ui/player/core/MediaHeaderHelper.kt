package com.example.ui.player.core

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Handles provider-specific media stream request headers (Referer, Origin, Cookies, User-Agent)
 * and stream segment repair (such as BigO MPEG-TS packet repair).
 */
object MediaHeaderHelper {

    private val bigoSeedMap = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile
    private var lastBigoSeed: Long = -1L

    val mediaHeaderInterceptor: Interceptor = Interceptor { chain ->
        var request = chain.request()
        val urlStr = request.url.toString().lowercase()
        val builder = request.newBuilder()

        // Default Desktop Chrome User-Agent if missing or generic okhttp
        val existingUa = request.header("User-Agent")
        if (existingUa.isNullOrBlank() || existingUa.startsWith("okhttp", ignoreCase = true)) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
        }

        when {
            urlStr.contains("googlevideo.com") || urlStr.contains("youtube.com") || urlStr.contains("youtu.be") || urlStr.contains("ytimg.com") ||
            urlStr.contains("googleapis.com") || urlStr.contains("storage.googleapis") || urlStr.contains("commondatastorage") || urlStr.contains("w3schools") || urlStr.contains("githubusercontent") -> {
                builder.removeHeader("Referer")
                builder.removeHeader("referer")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                builder.removeHeader("Cookie")
                builder.removeHeader("cookie")
            }
            urlStr.contains("vk.com") || urlStr.contains("vkvideo") || urlStr.contains("vkuser") || urlStr.contains("mycdn") || urlStr.contains("vk-cdn") || urlStr.contains("userapi") || urlStr.contains("vkuservideo") || urlStr.contains("vk.me") || urlStr.contains("mvk.com") || urlStr.contains("ok.ru") || urlStr.contains("odnoklassniki") -> {
                builder.header("Referer", "https://vk.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                builder.removeHeader("Cookie")
                builder.removeHeader("cookie")
            }
            urlStr.contains("bilibili") || urlStr.contains("bilivideo") || urlStr.contains("biliapi") ||
                    urlStr.contains("hdslb") || urlStr.contains("szbdyd") || urlStr.contains("mcdn") ||
                    urlStr.contains("acgvideo") || urlStr.contains("upgcxcode") || urlStr.contains("upos") ||
                    urlStr.contains("akamaized") || urlStr.contains("bcache") || urlStr.contains("mirrorali") ||
                    urlStr.contains("mirrorcos") || urlStr.contains("mirrorhw") || urlStr.contains("mirrorbos") ||
                    urlStr.contains("mirror08c") || urlStr.contains("mirrorakam") || urlStr.contains("bstar") ||
                    urlStr.contains("biliintl") -> {
                val isBiliCdn = urlStr.contains("bilivideo") || urlStr.contains("szbdyd") || urlStr.contains("mcdn") ||
                        urlStr.contains("upos") || urlStr.contains("upgcxcode") || urlStr.contains("acgvideo")

                val biliReferer = if (urlStr.contains("live") || urlStr.contains("gotcha") || urlStr.contains("xlive")) "https://live.bilibili.com/" else "https://www.bilibili.com/"
                if (request.header("Referer").isNullOrBlank()) {
                    builder.header("Referer", biliReferer)
                }
                if (request.header("User-Agent").isNullOrBlank()) {
                    builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                }
                builder.header("Accept", "*/*")
                builder.header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
                builder.removeHeader("Sec-Fetch-Mode")
                builder.removeHeader("Sec-Fetch-Site")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")

                if (!isBiliCdn) {
                    val cookie = com.example.extractor.BilibiliProvider.getBilibiliCookie()
                    if (cookie.isNotBlank() && request.header("Cookie") == null) {
                        builder.header("Cookie", cookie)
                    }
                } else {
                    builder.removeHeader("Cookie")
                    builder.removeHeader("cookie")
                }
            }
            urlStr.contains("eporner.com") || urlStr.contains("eporner") || urlStr.contains("static-cluster") || urlStr.contains("eporner-cdn") -> {
                builder.header("Referer", "https://www.eporner.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
            }
            urlStr.contains("hqporner.com") || urlStr.contains("hqporner.tv") || urlStr.contains("hqporner") || urlStr.contains("hqplayer") || urlStr.contains("cdn.hqporner") -> {
                builder.header("Referer", "https://hqporner.com/")
                builder.header("Origin", "https://hqporner.com")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; country=US; consent=1")
            }
            urlStr.contains("spankbang.com") || urlStr.contains("sb-cd.com") || urlStr.contains("spankcdn") || urlStr.contains("spankbang.party") || urlStr.contains("spankbang") -> {
                builder.header("Referer", "https://spankbang.com/")
                builder.header("Origin", "https://spankbang.com")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_confirmed=1; country=US; platform=pc; ft_mature=1; consent=1")
            }
            urlStr.contains("motherless.com") || urlStr.contains("motherlessmedia") || urlStr.contains("cdn.motherless") -> {
                builder.header("Referer", "https://motherless.com/")
                builder.header("Origin", "https://motherless.com")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                if (request.header("Cookie") == null) builder.header("Cookie", "content_filter=0; member=1; age_verified=1; country=US; consent=1")
            }
            urlStr.contains("dailymotion") || urlStr.contains("dmcdn") || urlStr.contains("dai.ly") || urlStr.contains("dm-event") -> {
                builder.header("Referer", "https://www.dailymotion.com/")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.header("Accept", "*/*")
                if (urlStr.contains("dmcdn.net")) {
                    builder.removeHeader("Cookie")
                    builder.removeHeader("cookie")
                } else {
                    val dmCookie = com.example.extractor.DailymotionProvider.lastDmCookies
                    if (dmCookie.isNotBlank()) {
                        builder.header("Cookie", dmCookie)
                    }
                }
                if (request.method.equals("HEAD", ignoreCase = true)) {
                    val getReq = builder.get().build()
                    val resp = chain.proceed(getReq)
                    val cType = resp.body?.contentType()
                    val cLen = resp.body?.contentLength() ?: -1L
                    try { resp.body?.string() } catch (_: Throwable) {}
                    val emptyBody = "".toResponseBody(cType)
                    val newResp = resp.newBuilder().body(emptyBody)
                    if (cLen > 0) newResp.header("Content-Length", cLen.toString())
                    return@Interceptor newResp.build()
                }
            }
            urlStr.contains("archive.org") || urlStr.contains("us.archive.org") || urlStr.contains("ia60") || urlStr.contains("ia80") || urlStr.contains("ia90") -> {
                if (request.header("Referer") == null) {
                    builder.header("Referer", "https://archive.org/")
                }
            }
            urlStr.contains("pornhub.com") || urlStr.contains("phncdn.com") -> {
                builder.header("Referer", "https://www.pornhub.com/")
                builder.header("Origin", "https://www.pornhub.com")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; ip_country=US; has_consent=1; expired_cookies=1; il=en")
            }
            urlStr.contains("4tube.com") || urlStr.contains("ttcache.com") || urlStr.contains("f-cdn.com") || urlStr.contains("foursex.com") || urlStr.contains("pornerbros.com") || urlStr.contains("fux.com") -> {
                builder.header("Referer", "https://www.4tube.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc; ft_mature=1; consent=1; has_consent=1")
            }
            urlStr.contains("beeg.com") || urlStr.contains("externulls.com") -> {
                builder.header("Referer", "https://beeg.com/")
                builder.header("Origin", "https://beeg.com")
            }
            urlStr.contains("xvideos.com") || urlStr.contains("xv-cdn.com") -> {
                builder.header("Referer", "https://www.xvideos.com/")
            }
            urlStr.contains("youporn.com") || urlStr.contains("ypncdn.com") -> {
                builder.header("Referer", "https://www.youporn.com/")
                builder.header("Origin", "https://www.youporn.com")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc")
            }
            urlStr.contains("xhamster.com") || urlStr.contains("xhcdn.com") -> {
                builder.header("Referer", "https://xhamster.com/")
                builder.header("Origin", "https://xhamster.com")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc")
            }
            urlStr.contains("vimeo.com") || urlStr.contains("vimeocdn.com") || (urlStr.contains("vimeo") && !urlStr.contains("bili")) -> {
                builder.header("Referer", "https://vimeo.com/")
                builder.header("Origin", "https://vimeo.com")
            }
            urlStr.contains("hotstar.com") || urlStr.contains("hotstar-cdn") || urlStr.contains("jiohotstar") || urlStr.contains("starott.com") || urlStr.contains("hs-cdn") -> {
                builder.header("Referer", "https://www.hotstar.com/")
                builder.header("Origin", "https://www.hotstar.com")
            }
            urlStr.contains("amazon.in") || urlStr.contains("minitv") || urlStr.contains("aiv-cdn") || urlStr.contains("amazonvideo") -> {
                builder.header("Referer", "https://www.amazon.in/")
                builder.header("Origin", "https://www.amazon.in")
            }
            urlStr.contains("cam4.com") || urlStr.contains("stream.cam4.com") -> {
                builder.header("Referer", "https://www.cam4.com/")
                builder.header("Origin", "https://www.cam4.com")
            }
            urlStr.contains("bigo.tv") || urlStr.contains("bigolive.tv") || urlStr.contains("bigocdn.com") || urlStr.contains("live.bigo.tv") || urlStr.contains("cubetecn.com") || urlStr.contains("bigo.sg") -> {
                builder.header("Referer", "https://www.bigo.tv/")
                builder.header("Origin", "https://www.bigo.tv")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            urlStr.contains("cammodels.com") || urlStr.contains("stripchat.com") || urlStr.contains("doppiocdn.com") || urlStr.contains("strpst.com") || urlStr.contains("bongacams.com") || urlStr.contains("bngp.net") -> {
                builder.header("Referer", "https://cammodels.com/")
                builder.header("Origin", "https://cammodels.com")
            }
            urlStr.contains("chaturbate.com") || urlStr.contains("highwebmedia.com") -> {
                builder.header("Referer", "https://chaturbate.com/")
                builder.header("Origin", "https://chaturbate.com")
            }
            urlStr.contains("discoveryplus") -> {
                builder.header("Referer", "https://www.discoveryplus.in/")
                builder.header("Origin", "https://www.discoveryplus.in")
            }
            urlStr.contains("disneyplus") -> {
                builder.header("Referer", "https://www.disneyplus.com/")
                builder.header("Origin", "https://www.disneyplus.com")
            }
            urlStr.contains("max.com") || urlStr.contains("hbo.com") || urlStr.contains("hbomax.com") -> {
                builder.header("Referer", "https://play.max.com/")
                builder.header("Origin", "https://play.max.com")
            }
            urlStr.contains("curiositystream") -> {
                builder.header("Referer", "https://curiositystream.com/")
                builder.header("Origin", "https://curiositystream.com")
            }
            urlStr.contains("drive.google.com") || urlStr.contains("googleusercontent.com") || urlStr.contains("drive.usercontent.google.com") -> {
                builder.header("Referer", "https://drive.google.com/")
            }
            urlStr.contains("mxplayer.in") || urlStr.contains("mxplay.com") -> {
                builder.header("Referer", "https://www.mxplayer.in/")
                builder.header("Origin", "https://www.mxplayer.in")
            }
            urlStr.contains("imdb.com") || urlStr.contains("media-amazon.com") -> {
                builder.header("Referer", "https://www.imdb.com/")
                builder.header("Origin", "https://www.imdb.com")
            }
            urlStr.contains("hanime1") || urlStr.contains("hanime.tv") || urlStr.contains("hanime") || urlStr.contains("hembed.com") || urlStr.contains("vdownload") -> {
                val ref = if (urlStr.contains("hanime.tv")) "https://hanime.tv/" else "https://hanime1.me/"
                val orig = if (urlStr.contains("hanime.tv")) "https://hanime.tv" else "https://hanime1.me"
                builder.header("Referer", ref)
                builder.header("Origin", orig)
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; country=US; language=en; ft_mature=1; consent=1")
            }
            urlStr.contains("noodlemagazine") || urlStr.contains("noodlemag") -> {
                builder.header("Referer", "https://noodlemagazine.com/")
                builder.header("Origin", "https://noodlemagazine.com")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                if (request.header("Cookie") == null) builder.header("Cookie", "lang=en; hl=en; language=en; remixlang=3; age_verified=1; platform=pc; ft_mature=1; consent=1")
            }
            urlStr.contains("vk.com") || urlStr.contains("vkvideo.ru") || urlStr.contains("vkuser") || urlStr.contains("mycdn.me") || urlStr.contains("userapi.com") || urlStr.contains("ok.ru") || urlStr.contains("odnoklassniki.ru") -> {
                builder.header("Referer", "https://vk.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("Cookie")
            }
            urlStr.contains("popcorntime") -> {
                builder.header("Referer", "https://popcorntime.pro/")
                builder.header("Origin", "https://popcorntime.pro")
            }
            urlStr.contains("sonyliv") || urlStr.contains("setindia") || urlStr.contains("sonypicturesnetworks") -> {
                builder.header("Referer", "https://www.sonyliv.com/")
                builder.header("Origin", "https://www.sonyliv.com")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            urlStr.contains("thisvid.com") || (urlStr.contains("thisvid") && !urlStr.contains("gtv-videos-bucket")) -> {
                builder.header("Referer", "https://thisvid.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc; has_consent=1; kt_ips=1; kt_is_visited=1; kt_disclaimer=1")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
            }
            urlStr.contains("txxx") || urlStr.contains("txxx.com") || urlStr.contains("txxx.tube") || urlStr.contains("tubecdn.com") -> {
                builder.header("Referer", "https://www.txxx.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc; country=US; ft_mature=1; consent=1")
            }
            urlStr.contains("4tube") || urlStr.contains("4tube.com") || urlStr.contains("fivetube.com") -> {
                builder.header("Referer", "https://www.4tube.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc; country=US; ft_mature=1; consent=1")
            }
            urlStr.contains("spankbang") || urlStr.contains("spankbang.com") || urlStr.contains("spankcdn") || urlStr.contains("sb-cd.com") -> {
                builder.header("Referer", "https://spankbang.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_confirmed=1; country=US")
            }
            urlStr.contains("motherless.com") || urlStr.contains("motherlessmedia") || urlStr.contains("cdn.motherless") -> {
                builder.header("Referer", "https://motherless.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                if (request.header("Cookie") == null) builder.header("Cookie", "content_filter=0; member=1")
            }
            urlStr.contains("playvid") || urlStr.contains("playvid.com") -> {
                builder.header("Referer", "https://www.playvid.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_confirmed=1")
            }
            urlStr.contains("tnaflix") || urlStr.contains("tnaflix.com") || urlStr.contains("tnaflixcdn") -> {
                builder.header("Referer", "https://www.tnaflix.com/")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.removeHeader("Origin")
                builder.removeHeader("origin")
                if (request.header("Cookie") == null) builder.header("Cookie", "age_verified=1; platform=pc; has_consent=1")
            }
            urlStr.contains("crunchyroll") || urlStr.contains("vrv.co") || (urlStr.contains("akamaized.net") && urlStr.contains("crunchyroll")) -> {
                builder.header("Referer", "https://www.crunchyroll.com/")
                builder.header("Origin", "https://www.crunchyroll.com")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            urlStr.contains("hianime") || urlStr.contains("megacloud") || urlStr.contains("rapid-cloud") || urlStr.contains("kaido") || urlStr.contains("aniwatch") || urlStr.contains("animepahe") || urlStr.contains("gogoanime") -> {
                builder.header("Referer", "https://hianime.to/")
                builder.header("Origin", "https://hianime.to")
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            urlStr.contains("stbturbo") || urlStr.contains("streamtb") || urlStr.contains("stb") || urlStr.contains("sextb") -> {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                builder.header("Referer", "https://stbturbo.xyz/")
                builder.header("Origin", "https://stbturbo.xyz")
            }
            urlStr.contains("javplayer") || urlStr.contains("123av") || urlStr.contains("icdn.123av") -> {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                builder.header("Referer", "https://javplayer.cc/")
                builder.header("Origin", "https://javplayer.cc")
            }
            urlStr.contains("javtiful") || urlStr.contains("fast-stream.jav.si") -> {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                builder.header("Referer", "https://javtiful.com/")
                builder.header("Origin", "https://javtiful.com")
            }
            urlStr.contains("supjav") || urlStr.contains("tvlogy") || urlStr.contains("supplayer") || urlStr.contains("streamwish") || urlStr.contains("wishembed") || urlStr.contains("awish") || urlStr.contains("dwish") || urlStr.contains("strwish") || urlStr.contains("cdnwish") || urlStr.contains("embedwish") || urlStr.contains("sfastwish") || urlStr.contains("filelions") || urlStr.contains("voe") || urlStr.contains("audaciousdefaulthouse") || urlStr.contains("dood") || urlStr.contains("ds2play") || urlStr.contains("streamtape") || urlStr.contains("tapecontent") || urlStr.contains("stbturbo") || urlStr.contains("streamtb") -> {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                val (ref, orig) = when {
                    urlStr.contains("tvlogy") || urlStr.contains("supplayer") -> "https://tvlogy.to/" to "https://tvlogy.to"
                    urlStr.contains("streamwish") || urlStr.contains("wishembed") || urlStr.contains("awish") || urlStr.contains("dwish") || urlStr.contains("strwish") || urlStr.contains("cdnwish") || urlStr.contains("embedwish") || urlStr.contains("sfastwish") || urlStr.contains("filelions") -> "https://streamwish.to/" to "https://streamwish.to"
                    urlStr.contains("voe") || urlStr.contains("audaciousdefaulthouse") -> "https://voe.sx/" to "https://voe.sx"
                    urlStr.contains("dood") || urlStr.contains("ds2play") -> "https://dood.to/" to "https://dood.to"
                    urlStr.contains("streamtape") || urlStr.contains("tapecontent") -> "https://streamtape.com/" to "https://streamtape.com"
                    urlStr.contains("stbturbo") || urlStr.contains("streamtb") -> "https://stbturbo.xyz/" to "https://stbturbo.xyz"
                    else -> "https://supjav.com/" to "https://supjav.com"
                }
                if (request.header("Referer") == null) {
                    builder.header("Referer", ref)
                }
                if (request.header("Origin") == null) {
                    builder.header("Origin", orig)
                }
            }
            urlStr.contains("vidsrc") || urlStr.contains("cloudorchestranova") || urlStr.contains("vsembed") ||
            urlStr.contains("vidlink") || urlStr.contains("autoembed") || urlStr.contains("smashystream") ||
            urlStr.contains("2embed") || urlStr.contains("multiembed") || urlStr.contains("vidrock") ||
            urlStr.contains("embed.su") || urlStr.contains("rive.stream") || urlStr.contains("decryptor") ||
            urlStr.contains("vidhide") || urlStr.contains("turboviplay") || urlStr.contains("luluvdo") ||
            urlStr.contains("lulustream") || urlStr.contains("vidara") || urlStr.contains("streamhide") ||
            urlStr.contains("vidmoly") || urlStr.contains("nxsha") || urlStr.contains("onrender.com") -> {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                val ref = when {
                    urlStr.contains("cloudorchestranova") -> "https://cloudorchestranova.com/"
                    urlStr.contains("vidsrc.to") -> "https://vidsrc.to/"
                    urlStr.contains("vidsrc.net") -> "https://vidsrc.net/"
                    urlStr.contains("vidsrc.pm") -> "https://vidsrc.pm/"
                    urlStr.contains("vidsrc.xyz") -> "https://vidsrc.xyz/"
                    urlStr.contains("vidsrc.cc") -> "https://vidsrc.cc/"
                    urlStr.contains("vidsrc.vip") -> "https://vidsrc.vip/"
                    urlStr.contains("vidsrc.icu") -> "https://vidsrc.icu/"
                    urlStr.contains("vidsrc.in") -> "https://vidsrc.in/"
                    urlStr.contains("vidlink.pro") -> "https://vidlink.pro/"
                    urlStr.contains("autoembed.cc") -> "https://player.autoembed.cc/"
                    urlStr.contains("smashystream") -> "https://embed.smashystream.com/"
                    urlStr.contains("2embed") -> "https://www.2embed.cc/"
                    urlStr.contains("multiembed") -> "https://multiembed.mov/"
                    urlStr.contains("embed.su") -> "https://embed.su/"
                    urlStr.contains("rive.stream") -> "https://rive.stream/"
                    urlStr.contains("vidhide") -> "https://vidhidepro.com/"
                    urlStr.contains("turboviplay") -> "https://turboviplay.com/"
                    urlStr.contains("luluvdo") || urlStr.contains("lulustream") -> "https://luluvdo.com/"
                    urlStr.contains("vidara") -> "https://vidara.org/"
                    else -> "https://cloudorchestranova.com/"
                }
                if (request.header("Referer") == null) {
                    builder.header("Referer", ref)
                }
                if (request.header("Origin") == null) {
                    builder.header("Origin", ref.trimEnd('/'))
                }
            }
        }

        val response = chain.proceed(builder.build())
        if (!response.isSuccessful) return@Interceptor response

        val isBigoStream = urlStr.contains("cubetecn.com") || urlStr.contains("bigo.tv") || urlStr.contains("bigolive.tv") || urlStr.contains("bigo.sg") || urlStr.contains("bigocdn.com") || urlStr.contains("da7akni.net") || urlStr.contains("piojm.tech")

        if (isBigoStream) {
            val responseBody = response.body ?: return@Interceptor response
            val mediaType = responseBody.contentType()
            val mediaTypeStr = mediaType?.toString()?.lowercase() ?: ""
            val isM3u8 = urlStr.contains(".m3u8") || mediaTypeStr.contains("mpegurl") || mediaTypeStr.contains("vnd.apple.mpegurl")

            if (isM3u8) {
                val rawText = responseBody.string()
                val seedMatch = Regex("""#EXT-X-BIGO-WEB-PROTECTION:[^,\r\n]*SEED=(\d+)""").find(rawText)
                if (seedMatch != null) {
                    val seedVal = seedMatch.groupValues[1].toLongOrNull() ?: -1L
                    if (seedVal > 0L) {
                        lastBigoSeed = seedVal
                        val pathKey = request.url.encodedPath.substringBeforeLast('/')
                        bigoSeedMap[pathKey] = seedVal
                    }
                }
                if (rawText.contains("#EXT-X-BIGO-WEB-PROTECTION") || rawText.contains("#EXTM3U")) {
                    val cleanText = rawText.replace(Regex("""#EXT-X-BIGO-WEB-PROTECTION:[^\r\n]*\r?\n?"""), "")
                    val newBody = cleanText.toResponseBody(mediaType)
                    return@Interceptor response.newBuilder().body(newBody).build()
                } else {
                    val newBody = rawText.toResponseBody(mediaType)
                    return@Interceptor response.newBuilder().body(newBody).build()
                }
            } else {
                val bytes = responseBody.bytes()
                if (bytes.size >= 376 && bytes[0] != 0x47.toByte()) {
                    val pathKey = request.url.encodedPath.substringBeforeLast('/')
                    val seed = bigoSeedMap[pathKey] ?: lastBigoSeed
                    val repaired = if (seed > 0L) {
                        repairBigoTsSegmentWithSeed(bytes, seed)
                    } else {
                        repairBigoTsSegment(bytes)
                    }
                    val finalBytes = if (repaired.size >= 376 && repaired[0] != 0x47.toByte()) {
                        repairBigoTsSegment(repaired)
                    } else {
                        repaired
                    }
                    val newBody = finalBytes.toResponseBody(mediaType)
                    return@Interceptor response.newBuilder().body(newBody).build()
                } else {
                    val newBody = bytes.toResponseBody(mediaType)
                    return@Interceptor response.newBuilder().body(newBody).build()
                }
            }
        }

        response
    }

    private fun repairBigoTsSegmentWithSeed(rawBytes: ByteArray, seed: Long): ByteArray {
        if (rawBytes.size < 376 || rawBytes[0] == 0x47.toByte()) {
            return rawBytes
        }
        val fixed = rawBytes.clone()
        for (num in 0 until 2) {
            val imul = ((num + 1) * 2654435769L) and 0xFFFFFFFFL
            var r = (seed xor imul) and 0xFFFFFFFFL
            if (r == 0L) {
                r = 1831565813L
            }
            val packetOffset = 188 * num
            for (offset in 0 until 16) {
                r = (r xor ((r shl 13) and 0xFFFFFFFFL)) and 0xFFFFFFFFL
                r = (r xor (r ushr 17)) and 0xFFFFFFFFL
                r = (r xor ((r shl 5) and 0xFFFFFFFFL)) and 0xFFFFFFFFL
                var mask = (r and 0xFFL).toInt()
                if (mask == 0) {
                    mask = 165
                }
                if (packetOffset + offset < fixed.size) {
                    fixed[packetOffset + offset] = (fixed[packetOffset + offset].toInt() xor mask).toByte()
                }
            }
        }
        return fixed
    }

    private fun repairBigoTsSegment(rawBytes: ByteArray): ByteArray {
        if (rawBytes.size < 376 || rawBytes[0] == 0x47.toByte()) {
            return rawBytes
        }
        val fixed = rawBytes.clone()

        // 1. Reconstruct Standard MPEG-TS PAT packet (188 bytes, PID 0x0000, Program 1 -> PMT PID 0x1000)
        for (i in 0 until 188) fixed[i] = 0xFF.toByte()
        val patHeader = byteArrayOf(
            0x47.toByte(), 0x40.toByte(), 0x00.toByte(), 0x10.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xb0.toByte(), 0x0d.toByte(),
            0x00.toByte(), 0x01.toByte(), 0xc1.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0xf0.toByte(),
            0x00.toByte(), 0x2a.toByte(), 0xb1.toByte(), 0x04.toByte(),
            0xb2.toByte()
        )
        System.arraycopy(patHeader, 0, fixed, 0, patHeader.size)

        // 2. Reconstruct Standard MPEG-TS PMT packet (188 bytes, PID 0x1000, PCR PID 0x100, Stream 1: AAC Audio PID 0x101, Stream 2: AVC Video PID 0x100)
        for (i in 188 until 376) fixed[i] = 0xFF.toByte()
        val pmtHeader = byteArrayOf(
            0x47.toByte(), 0x50.toByte(), 0x00.toByte(), 0x10.toByte(),
            0x00.toByte(), 0x02.toByte(), 0xb0.toByte(), 0x17.toByte(),
            0x00.toByte(), 0x01.toByte(), 0xc1.toByte(), 0x00.toByte(),
            0x00.toByte(), 0xe1.toByte(), 0x00.toByte(), 0xf0.toByte(),
            0x00.toByte(), 0x0f.toByte(), 0xe1.toByte(), 0x01.toByte(),
            0xf0.toByte(), 0x00.toByte(), 0x1b.toByte(), 0xe1.toByte(),
            0x00.toByte(), 0xf0.toByte(), 0x00.toByte(), 0xf2.toByte(),
            0xd9.toByte(), 0x15.toByte(), 0x63.toByte()
        )
        System.arraycopy(pmtHeader, 0, fixed, 188, pmtHeader.size)

        return fixed
    }

    val networkHeaderInterceptor = Interceptor { chain ->
        val request = chain.request()
        val urlStr = request.url.toString()
        val builder = request.newBuilder()

        val isBili = urlStr.contains("bilibili") || urlStr.contains("bilivideo") || urlStr.contains("biliapi") ||
                urlStr.contains("hdslb") || urlStr.contains("szbdyd") || urlStr.contains("mcdn") ||
                urlStr.contains("acgvideo") || urlStr.contains("upgcxcode") || urlStr.contains("upos") ||
                urlStr.contains("akamaized") || urlStr.contains("bcache") || urlStr.contains("mirrorali") ||
                urlStr.contains("mirrorcos") || urlStr.contains("mirrorhw") || urlStr.contains("mirrorbos") ||
                urlStr.contains("mirror08c") || urlStr.contains("mirrorakam") || urlStr.contains("bstar") ||
                urlStr.contains("biliintl")

        val isDm = urlStr.contains("dailymotion") || urlStr.contains("dmcdn") || urlStr.contains("dai.ly") || urlStr.contains("dm-event")

        if (isBili) {
            val isBiliCdn = urlStr.contains("bilivideo") || urlStr.contains("szbdyd") || urlStr.contains("mcdn") ||
                    urlStr.contains("upos") || urlStr.contains("upgcxcode") || urlStr.contains("acgvideo")

            val biliReferer = if (urlStr.contains("live") || urlStr.contains("gotcha") || urlStr.contains("xlive")) "https://live.bilibili.com/" else "https://www.bilibili.com/"
            if (request.header("Referer").isNullOrBlank()) {
                builder.header("Referer", biliReferer)
            }
            if (request.header("User-Agent").isNullOrBlank()) {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            builder.header("Accept", "*/*")
            builder.removeHeader("Origin")
            builder.removeHeader("origin")
            if (isBiliCdn) {
                builder.removeHeader("Cookie")
                builder.removeHeader("cookie")
            }
        } else if (isDm) {
            builder.header("Referer", "https://www.dailymotion.com/")
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            builder.header("Accept", "*/*")
            builder.removeHeader("Origin")
            builder.removeHeader("origin")
            if (urlStr.contains("dmcdn.net")) {
                builder.removeHeader("Cookie")
                builder.removeHeader("cookie")
            }
        }

        chain.proceed(builder.build())
    }
}
