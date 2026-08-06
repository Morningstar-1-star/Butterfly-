package com.example

import com.example.extractor.DownloaderImpl
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.Locale

class ExampleUnitTest {

    @Before
    fun setup() {
        NewPipe.init(
            DownloaderImpl.getInstance(),
            Localization.fromLocale(Locale.getDefault()),
            ContentCountry(Locale.getDefault().country)
        )
    }

    @Test
    fun testUrlParserVariants() {
        val helper = com.example.extractor.YouTubeExtractorHelper

        // 1. watch?v=
        val watchRes = helper.parseYouTubeInput("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared&t=10s")
        assertTrue(watchRes is com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (watchRes as com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)

        // 2. youtu.be/
        val shortRes = helper.parseYouTubeInput("https://youtu.be/dQw4w9WgXcQ?si=abcdef123")
        assertTrue(shortRes is com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (shortRes as com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)

        // 3. m.youtube.com
        val mobileRes = helper.parseYouTubeInput("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(mobileRes is com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (mobileRes as com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)

        // 4. youtube.com/shorts/
        val shortsRes = helper.parseYouTubeInput("https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share")
        assertTrue(shortsRes is com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (shortsRes as com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)

        // 5. youtube.com/live/
        val liveRes = helper.parseYouTubeInput("https://www.youtube.com/live/dQw4w9WgXcQ")
        assertTrue(liveRes is com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (liveRes as com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)

        // 6. Raw 11-char ID
        val rawRes = helper.parseYouTubeInput("dQw4w9WgXcQ")
        assertTrue(rawRes is com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId)
        assertEquals("dQw4w9WgXcQ", (rawRes as com.example.extractor.YouTubeExtractorHelper.UrlParseResult.ValidVideoId).videoId)

        // 7. Invalid YouTube URL attempt
        val invalidRes = helper.parseYouTubeInput("https://www.youtube.com/watch?v=invalid_short")
        assertTrue(invalidRes is com.example.extractor.YouTubeExtractorHelper.UrlParseResult.InvalidUrl)
        assertEquals("Invalid YouTube URL", (invalidRes as com.example.extractor.YouTubeExtractorHelper.UrlParseResult.InvalidUrl).message)

        // 8. Search query
        val searchRes = helper.parseYouTubeInput("classical piano music")
        assertTrue(searchRes is com.example.extractor.YouTubeExtractorHelper.UrlParseResult.SearchQuery)
    }

    @Test
    fun testStreamInfoForKnownVideo() {
        val service = ServiceList.YouTube
        val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(info.name)
        assertEquals("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", info.name)
        assertTrue(info.audioStreams.size > 0)
    }
}


