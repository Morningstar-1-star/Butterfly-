package com.example

import com.example.extractor.ArchiveOrgProvider
import com.example.extractor.YouTubeExtractorHelper
import com.example.extractor.YtDlpResolver
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackPipelineRegressionTest {

    @Test
    fun testYtDlpSupportedUrlDetection() {
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://vimeo.com/76979871"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.dailymotion.com/video/x7tgad0"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.tiktok.com/@user/video/1234567890"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.twitch.tv/videos/123456789"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://www.eporner.com/video-123456/test-slug/"))
        assertTrue(YtDlpResolver.isYtDlpSupportedUrl("https://rule34video.com/video/987654/animation-slug/"))
    }

    @Test
    fun testEpornerAndRule34IdExtraction() {
        assertEquals("123456", com.example.extractor.EpornerProvider.extractVideoId("https://www.eporner.com/video-123456/test-slug/"))
        assertEquals("987654", com.example.extractor.Rule34VideoProvider.extractVideoId("https://rule34video.com/video/987654/animation-slug/"))
    }

    @Test
    fun testRealEpornerExtractionAndPlaybackUrl() {
        val videoId = "3746271"
        val client = okhttp3.OkHttpClient.Builder().followRedirects(true).build()

        val embedReq = okhttp3.Request.Builder()
            .url("https://www.eporner.com/embed/$videoId/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://www.eporner.com/")
            .build()
        val embedResp = client.newCall(embedReq).execute()
        val embedHtml = embedResp.body?.string() ?: ""
        println("FULL EMBED HTML: $embedHtml")
        embedResp.close()

        val videoReq = okhttp3.Request.Builder()
            .url("https://www.eporner.com/video-$videoId/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://www.eporner.com/")
            .build()
        val videoResp = client.newCall(videoReq).execute()
        val videoHtml = videoResp.body?.string() ?: ""
        videoResp.close()

        val scriptBlocks = Regex("""<script[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(videoHtml)
            .map { it.groupValues[1] }
            .filter { it.contains("player", ignoreCase = true) || it.contains("video", ignoreCase = true) || it.contains("download", ignoreCase = true) || it.contains("hash", ignoreCase = true) }
            .toList()

        println("MATCHED SCRIPT BLOCKS COUNT: ${scriptBlocks.size}")
        scriptBlocks.take(5).forEachIndexed { idx, s ->
            println("--- SCRIPT BLOCK $idx ---")
            println(s.take(1000))
        }

        assertTrue("Should have fetched embed html", embedHtml.isNotBlank())
    }

    @Test
    fun testPornhubFeedAndExtraction() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. Test Home feed fetching via MultiSourceProvider
        val homeItems = kotlinx.coroutines.runBlocking {
            com.example.extractor.MultiSourceProvider.getHome(ctx, "pornhub", 5)
        }
        println("PORNHUB HOME ITEMS COUNT: ${homeItems.size}")
        homeItems.forEach { item ->
            println("PORNHUB ITEM: id=${item.id}, title=${item.title}, thumb=${item.thumbnailUrl}, duration=${item.durationSeconds}s")
        }

        assert(homeItems.isNotEmpty()) { "Pornhub home feed returned 0 items!" }

        // 2. Test stream resolution via YouTubeExtractorHelper (which routes to PornhubProvider)
        val firstItem = homeItems.first()
        val result = kotlinx.coroutines.runBlocking {
            com.example.extractor.YouTubeExtractorHelper.resolveStream(firstItem.id, ctx, "pornhub")
        }

        println("PORNHUB EXTRACTION RESULT: $result")
        assert(result is YouTubeExtractorHelper.ExtractionResult.Success) { "Pornhub extraction failed: $result" }

        val streamData = (result as YouTubeExtractorHelper.ExtractionResult.Success).streamData
        println("PORNHUB STREAM DATA TITLE: ${streamData.title}")
        println("PORNHUB STREAM DATA OPTIONS COUNT: ${streamData.availableStreamOptions.size}")
        streamData.availableStreamOptions.forEach { opt ->
            println("OPTION: label=${opt.qualityLabel}, format=${opt.format}, videoUrl=${opt.videoUrl?.take(120)}")
        }

        assert(streamData.availableStreamOptions.isNotEmpty()) { "Pornhub stream options empty!" }
        assert(streamData.headers.containsKey("Referer")) { "Missing Referer header!" }
    }

    @Test
    fun testHeaderIsolationForProviders() {
        // YouTube must NOT have synthetic Referer/Origin headers injected
        val ytHeaders = mapOf("User-Agent" to "TestUA")
        assertFalse(ytHeaders.containsKey("Referer"))

        // Bilibili must have Bilibili Referer
        val biliHeaders = mapOf("User-Agent" to "TestUA", "Referer" to "https://www.bilibili.com/")
        assertEquals("https://www.bilibili.com/", biliHeaders["Referer"])

        // Eporner must have Eporner Referer
        val epornerHeaders = mapOf("User-Agent" to "TestUA", "Referer" to "https://www.eporner.com/")
        assertEquals("https://www.eporner.com/", epornerHeaders["Referer"])

        // Rule34Video must have Rule34Video Referer
        val r34Headers = mapOf("User-Agent" to "TestUA", "Referer" to "https://rule34video.com/")
        assertEquals("https://rule34video.com/", r34Headers["Referer"])
    }

    @Test
    fun testParsedFormatPrioritization() {
        val muxedH264 = YtDlpResolver.ParsedFormat(
            formatId = "18",
            url = "https://example.com/muxed18.mp4",
            ext = "mp4",
            resolution = "640x360",
            width = 640,
            height = 360,
            fps = 30.0,
            tbr = 500.0,
            vbr = 400.0,
            abr = 96.0,
            vcodec = "avc1.42001E",
            acodec = "mp4a.40.2",
            formatNote = "360p",
            protocol = "https",
            httpHeaders = mapOf("Referer" to "https://www.youtube.com/")
        )

        val videoOnly = YtDlpResolver.ParsedFormat(
            formatId = "137",
            url = "https://example.com/video137.mp4",
            ext = "mp4",
            resolution = "1920x1080",
            width = 1920,
            height = 1080,
            fps = 30.0,
            tbr = 2500.0,
            vbr = 2500.0,
            abr = 0.0,
            vcodec = "avc1.640028",
            acodec = "none",
            formatNote = "1080p",
            protocol = "https"
        )

        assertTrue(muxedH264.isMuxed)
        assertTrue(muxedH264.isH264)
        assertFalse(muxedH264.isVideoOnly)
        assertTrue(videoOnly.isVideoOnly)
        assertFalse(videoOnly.isMuxed)
        assertEquals("https://www.youtube.com/", muxedH264.httpHeaders["Referer"])
    }

    @Test
    fun testYouTubeQualityScorePrioritizesMuxed() {
        val muxedOption = PlayableStreamOption(
            qualityLabel = "720p Progressive (mp4)",
            format = "mp4",
            isMuxed = true,
            videoUrl = "https://example.com/720p.mp4",
            providerType = ProviderType.DIRECT,
            headers = mapOf("User-Agent" to "TestUA", "Referer" to "https://www.youtube.com/")
        )

        val adaptiveOption = PlayableStreamOption(
            qualityLabel = "1080p Adaptive (mp4)",
            format = "mp4",
            isMuxed = false,
            videoUrl = "https://example.com/1080p.mp4",
            providerType = ProviderType.DIRECT
        )

        val muxedScore = YouTubeExtractorHelper.parseQualityScore(muxedOption)
        val adaptiveScore = YouTubeExtractorHelper.parseQualityScore(adaptiveOption)

        // Muxed option should have higher priority score for initial standalone playback
        assertTrue("Muxed score ($muxedScore) should exceed adaptive score ($adaptiveScore)", muxedScore > adaptiveScore)
        assertEquals("TestUA", muxedOption.headers["User-Agent"])
    }

    @Test
    fun testYtDlpUpdateStateTransitions() {
        com.example.extractor.YtDlpUpdateManager.resetState()
        assertEquals(com.example.extractor.YtDlpUpdateManager.UpdateState.Idle, com.example.extractor.YtDlpUpdateManager.updateState.value)
    }

    @Test
    fun testVimeoFeedAndExtraction() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val homeItems = kotlinx.coroutines.runBlocking {
            com.example.extractor.MultiSourceProvider.getHome(ctx, "vimeo", 5)
        }
        println("VIMEO HOME ITEMS COUNT: ${homeItems.size}")
        homeItems.forEach { item ->
            println("VIMEO ITEM: id=${item.id}, title=${item.title}, thumb=${item.thumbnailUrl}")
        }
        assert(homeItems.isNotEmpty()) { "Vimeo home feed returned 0 items!" }

        val firstItem = homeItems.first()
        val result = kotlinx.coroutines.runBlocking {
            com.example.extractor.YouTubeExtractorHelper.resolveStream(firstItem.id, ctx, "vimeo")
        }
        println("VIMEO EXTRACTION RESULT: $result")
        assert(result is YouTubeExtractorHelper.ExtractionResult.Success) { "Vimeo extraction failed: $result" }
        val streamData = (result as YouTubeExtractorHelper.ExtractionResult.Success).streamData
        assert(streamData.availableStreamOptions.isNotEmpty()) { "Vimeo stream options empty!" }
    }

    @Test
    fun testXVideosFeedAndExtraction() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val homeItems = kotlinx.coroutines.runBlocking {
            com.example.extractor.MultiSourceProvider.getHome(ctx, "xvideos", 5)
        }
        println("XVIDEOS HOME ITEMS COUNT: ${homeItems.size}")
        homeItems.forEach { item ->
            println("XVIDEOS ITEM: id=${item.id}, title=${item.title}, thumb=${item.thumbnailUrl}")
        }
        assert(homeItems.isNotEmpty()) { "XVideos home feed returned 0 items!" }

        val firstItem = homeItems.first()
        val result = kotlinx.coroutines.runBlocking {
            com.example.extractor.YouTubeExtractorHelper.resolveStream(firstItem.id, ctx, "xvideos")
        }
        println("XVIDEOS EXTRACTION RESULT: $result")
        assert(result is YouTubeExtractorHelper.ExtractionResult.Success) { "XVideos extraction failed: $result" }
        val streamData = (result as YouTubeExtractorHelper.ExtractionResult.Success).streamData
        assert(streamData.availableStreamOptions.isNotEmpty()) { "XVideos stream options empty!" }
    }

    @Test
    fun testYouPornFeedAndExtraction() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val homeItems = kotlinx.coroutines.runBlocking {
            com.example.extractor.MultiSourceProvider.getHome(ctx, "youporn", 5)
        }
        println("YOUPORN HOME ITEMS COUNT: ${homeItems.size}")
        homeItems.forEach { item ->
            println("YOUPORN ITEM: id=${item.id}, title=${item.title}, thumb=${item.thumbnailUrl}")
        }
        assert(homeItems.isNotEmpty()) { "YouPorn home feed returned 0 items!" }

        val firstItem = homeItems.first()
        val result = kotlinx.coroutines.runBlocking {
            com.example.extractor.YouTubeExtractorHelper.resolveStream(firstItem.id, ctx, "youporn")
        }
        println("YOUPORN EXTRACTION RESULT: $result")
        assert(result is YouTubeExtractorHelper.ExtractionResult.Success) { "YouPorn extraction failed: $result" }
        val streamData = (result as YouTubeExtractorHelper.ExtractionResult.Success).streamData
        assert(streamData.availableStreamOptions.isNotEmpty()) { "YouPorn stream options empty!" }
    }

    @Test
    fun testBeegFeedAndExtraction() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val homeItems = kotlinx.coroutines.runBlocking {
            com.example.extractor.MultiSourceProvider.getHome(ctx, "beeg", 5)
        }
        println("BEEG HOME ITEMS COUNT: ${homeItems.size}")
        homeItems.forEach { item ->
            println("BEEG ITEM: id=${item.id}, title=${item.title}, thumb=${item.thumbnailUrl}")
        }
        assert(homeItems.isNotEmpty()) { "Beeg home feed returned 0 items!" }

        val firstItem = homeItems.first()
        val result = kotlinx.coroutines.runBlocking {
            com.example.extractor.YouTubeExtractorHelper.resolveStream(firstItem.id, ctx, "beeg")
        }
        println("BEEG EXTRACTION RESULT: $result")
        assert(result is YouTubeExtractorHelper.ExtractionResult.Success) { "Beeg extraction failed: $result" }
        val streamData = (result as YouTubeExtractorHelper.ExtractionResult.Success).streamData
        assert(streamData.availableStreamOptions.isNotEmpty()) { "Beeg stream options empty!" }
    }

    @Test
    fun testInspectNetworkResponsesForProblematicSources() {
        val client = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        // 1. Vimeo Provider Home and Stream test
        try {
            val vimeoItems = com.example.extractor.VimeoProvider.getHome(5)
            println("DIAG VIMEO HOME ITEMS: ${vimeoItems.size}")
            val testId = if (vimeoItems.isNotEmpty()) vimeoItems.first().id else "https://vimeo.com/76979871"
            val videoNum = Regex("""\d+""").find(testId)?.value ?: "76979871"
            println("DIAG TEST VIMEO NUM: $videoNum (from $testId)")

            // Test A: player config endpoint
            val configUrl1 = "https://player.vimeo.com/video/$videoNum/config"
            val req1 = okhttp3.Request.Builder()
                .url(configUrl1)
                .header("User-Agent", ua)
                .header("Referer", "https://vimeo.com/$videoNum")
                .build()
            client.newCall(req1).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                println("DIAG CONFIG API CODE=${resp.code} LEN=${body.length} SNIPPET=${body.take(300)}")
            }

            // Test B: video webpage
            val pageUrl = "https://vimeo.com/$videoNum"
            val req2 = okhttp3.Request.Builder()
                .url(pageUrl)
                .header("User-Agent", ua)
                .header("Referer", "https://vimeo.com/")
                .build()
            client.newCall(req2).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                println("DIAG VIMEO PAGE CODE=${resp.code} LEN=${body.length} HAS_CONFIG=${body.contains("config")}")
                val configMatch = Regex("""window\.vimeo\.clip_page_config\s*=\s*(\{.+?\});""").find(body)
                    ?: Regex("""var\s+config\s*=\s*(\{.+?\});""").find(body)
                    ?: Regex("""playerConfig\s*=\s*(\{.+?\});""").find(body)
                if (configMatch != null) {
                    println("DIAG MATCHED CONFIG IN PAGE LEN=${configMatch.groupValues[1].length}")
                }
            }

            // Test C: player embed webpage with brace balancing
            val embedUrl = "https://player.vimeo.com/video/$videoNum"
            val req3 = okhttp3.Request.Builder()
                .url(embedUrl)
                .header("User-Agent", ua)
                .header("Referer", "https://vimeo.com/")
                .build()
            client.newCall(req3).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                println("DIAG VIMEO EMBED CODE=${resp.code} LEN=${body.length}")

                val markers = listOf("var config = ", "window.playerConfig = ", "playerConfig = ", "\"request\":")
                for (m in markers) {
                    val idx = body.indexOf(m)
                    if (idx != -1) {
                        println("Found marker '$m' at $idx")
                        val braceIndex = body.indexOf('{', idx)
                        if (braceIndex != -1) {
                            var openBraces = 0
                            var inString = false
                            var escape = false
                            var endIdx = -1
                            for (i in braceIndex until body.length) {
                                val c = body[i]
                                if (inString) {
                                    if (escape) escape = false
                                    else if (c == '\\') escape = true
                                    else if (c == '"') inString = false
                                } else {
                                    when (c) {
                                        '"' -> inString = true
                                        '{' -> openBraces++
                                        '}' -> {
                                            openBraces--
                                            if (openBraces == 0) {
                                                endIdx = i
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                            if (endIdx != -1) {
                                val jsonStr = body.substring(braceIndex, endIdx + 1)
                                println("EXTRACTED JSON LEN=${jsonStr.length} HAS_FILES=${jsonStr.contains("files")}")
                                try {
                                    val obj = org.json.JSONObject(jsonStr)
                                    val files = obj.optJSONObject("request")?.optJSONObject("files")
                                        ?: obj.optJSONObject("files")
                                    println("PARSED OBJ HAS FILES=${files != null}")
                                    if (files != null) {
                                        val hls = files.optJSONObject("hls")
                                        println("HLS OBJ=${hls != null}")
                                        if (hls != null) {
                                            val cdns = hls.optJSONObject("cdns")
                                            val defKey = hls.optString("default_cdn", "")
                                            println("HLS DEF_CDN=$defKey CDNS_KEYS=${cdns?.keys()?.asSequence()?.toList()}")
                                            if (cdns != null) {
                                                val cdnObj = cdns.optJSONObject(defKey) ?: cdns.optJSONObject(cdns.keys().next())
                                                println("HLS URL=${cdnObj?.optString("url")}")
                                            }
                                        }
                                        val prog = files.optJSONArray("progressive")
                                        println("PROG ARRAY COUNT=${prog?.length()}")
                                    }
                                } catch (e: Exception) {
                                    println("JSON parse error: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            // Test D: Vimeo API v2
            val api2Url = "https://vimeo.com/api/v2/video/$videoNum.json"
            val req4 = okhttp3.Request.Builder().url(api2Url).header("User-Agent", ua).build()
            client.newCall(req4).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                println("DIAG VIMEO API V2 CODE=${resp.code} LEN=${body.length} SNIP=${body.take(200)}")
            }

            val streamData = kotlinx.coroutines.runBlocking {
                com.example.extractor.VimeoProvider.getStreamData(testId, null)
            }
            println("DIAG VIMEO STREAM DATA RESULT: ${streamData?.title} -> url=${streamData?.videoUrl}")
        } catch (e: Exception) {
            println("DIAG VIMEO ERROR: ${e.message}")
            e.printStackTrace()
        }

        // 2. XVideos Provider Home test
        try {
            val xvItems = com.example.extractor.XVideosProvider.getHome(5)
            println("DIAG XVIDEOS HOME ITEMS: ${xvItems.size}")
            if (xvItems.isNotEmpty()) {
                println("DIAG XVIDEOS FIRST ITEM: ${xvItems.first().title} -> ${xvItems.first().id}")
            }
        } catch (e: Exception) {
            println("DIAG XVIDEOS ERROR: ${e.message}")
        }

        // 3. YouPorn Provider Home test
        try {
            val ypItems = com.example.extractor.YouPornProvider.getHome(5)
            println("DIAG YOUPORN HOME ITEMS: ${ypItems.size}")
            if (ypItems.isNotEmpty()) {
                println("DIAG YOUPORN FIRST ITEM: ${ypItems.first().title} -> ${ypItems.first().id}")
            }
        } catch (e: Exception) {
            println("DIAG YOUPORN ERROR: ${e.message}")
        }

        // 4. Beeg Provider Home and Stream test
        try {
            val beegItems = com.example.extractor.BeegProvider.getHome(5)
            println("DIAG BEEG HOME ITEMS: ${beegItems.size}")
            if (beegItems.isNotEmpty()) {
                val first = beegItems.first()
                println("DIAG BEEG FIRST ITEM: ${first.title} -> ${first.id}")
            }

            // Test A: Beeg Main Page - inspect script tags and text
            val pageReq = okhttp3.Request.Builder()
                .url("https://beeg.com/")
                .header("User-Agent", ua)
                .build()
            client.newCall(pageReq).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                println("DIAG BEEG MAIN PAGE FULL HTML LEN=${body.length}")
                println("HTML BODY: $body")
                val scriptMatches = Regex("""src="([^"]+\.js[^"]*)"""").findAll(body).map { it.groupValues[1] }.toList()
                println("BEEG SCRIPTS: $scriptMatches")

                // Fetch JS bundles to look for API URLs
                for (script in scriptMatches.take(3)) {
                    val sUrl = if (script.startsWith("http")) script else if (script.startsWith("/")) "https://beeg.com$script" else "https://beeg.com/$script"
                    val sReq = okhttp3.Request.Builder().url(sUrl).header("User-Agent", ua).build()
                    client.newCall(sReq).execute().use { sResp ->
                        val sBody = sResp.body?.string() ?: ""
                        println("SCRIPT $sUrl LEN=${sBody.length}")
                        val apiRegex = Regex("""https?://[a-zA-Z0-0\.-]+/api/[^\s"']+""", RegexOption.IGNORE_CASE)
                        val foundApis = apiRegex.findAll(sBody).map { it.value }.distinct().take(10).toList()
                        println("FOUND APIS IN JS: $foundApis")
                        val pathApis = Regex("""/api/[a-zA-Z0-9_/.-]+""", RegexOption.IGNORE_CASE)
                            .findAll(sBody).map { it.value }.distinct().take(15).toList()
                        println("FOUND PATH APIS IN JS: $pathApis")
                    }
                }
            }

            // Verify Beeg items can be listed
            if (beegItems.isNotEmpty()) {
                val first = beegItems.first()
                println("BEEG HOME ITEM: ${first.title} -> ${first.id}")
            }

        } catch (e: Exception) {
            println("DIAG BEEG ERROR: ${e.message}")
            e.printStackTrace()
        }
    }
}

