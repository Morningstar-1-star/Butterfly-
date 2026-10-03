package com.example.ui.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.ui.player.core.MediaSourceFactoryHelper
import com.example.ui.player.queue.Media3QueueManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Media3QueueManagerTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testInitialStreamInitialization() = runTest(testDispatcher) {
        var activeChanged = false
        val queueManager = Media3QueueManager(context, testScope) { streamData, videoItem ->
            activeChanged = true
        }

        val initialStream = StreamData(
            videoId = "vid_1",
            title = "First Video",
            channelName = "Channel 1",
            hlsUrl = "https://example.com/stream1.m3u8"
        )
        val initialOption = PlayableStreamOption(
            qualityLabel = "1080p",
            format = "hls",
            isMuxed = true,
            videoUrl = "https://example.com/stream1.m3u8"
        )
        val mediaSource = MediaSourceFactoryHelper.buildMediaSource(
            context = context,
            streamData = initialStream,
            streamOption = initialOption
        )
        assertNotNull(mediaSource)

        queueManager.setInitialStream(
            streamData = initialStream,
            streamOption = initialOption,
            initialMediaSource = mediaSource!!
        )

        assertEquals(1, queueManager.queueItems.value.size)
        assertEquals("vid_1", queueManager.videoQueue.value.first().id)
        assertEquals(0, queueManager.currentQueueIndex.value)
        assertFalse(queueManager.hasNextItem.value)
        assertFalse(queueManager.hasPreviousItem.value)
        assertEquals(1, queueManager.concatenatingMediaSource.size)
    }

    @Test
    fun testQueueAddAndPlayNextOperations() = runTest(testDispatcher) {
        val queueManager = Media3QueueManager(context, testScope) { _, _ -> }

        val initialStream = StreamData(
            videoId = "vid_1",
            title = "First Video",
            channelName = "Channel 1",
            hlsUrl = "https://example.com/stream1.m3u8"
        )
        val initialOption = PlayableStreamOption(
            qualityLabel = "1080p",
            format = "hls",
            isMuxed = true,
            videoUrl = "https://example.com/stream1.m3u8"
        )
        val mediaSource = MediaSourceFactoryHelper.buildMediaSource(
            context = context,
            streamData = initialStream,
            streamOption = initialOption
        )
        queueManager.setInitialStream(
            streamData = initialStream,
            streamOption = initialOption,
            initialMediaSource = mediaSource!!
        )

        val video2 = VideoItem(id = "vid_2", title = "Second Video", uploaderName = "Channel 2")
        val video3 = VideoItem(id = "vid_3", title = "Third Video", uploaderName = "Channel 3")
        val video4 = VideoItem(id = "vid_4", title = "Play Next Video", uploaderName = "Channel 4")

        // Add to end of queue
        queueManager.addToQueue(video2)
        queueManager.addToQueue(video3)

        assertEquals(3, queueManager.videoQueue.value.size)
        assertEquals(listOf("vid_1", "vid_2", "vid_3"), queueManager.videoQueue.value.map { it.id })
        assertTrue(queueManager.hasNextItem.value)

        // Play Next (inserts right after current index 0)
        queueManager.playNextInQueue(video4)

        assertEquals(4, queueManager.videoQueue.value.size)
        assertEquals(listOf("vid_1", "vid_4", "vid_2", "vid_3"), queueManager.videoQueue.value.map { it.id })

        // Reorder / move
        queueManager.moveQueueItem(fromIndex = 1, toIndex = 3)
        assertEquals(listOf("vid_1", "vid_2", "vid_3", "vid_4"), queueManager.videoQueue.value.map { it.id })

        // Remove item
        queueManager.removeFromQueue(video2)
        assertEquals(listOf("vid_1", "vid_3", "vid_4"), queueManager.videoQueue.value.map { it.id })

        // Clear queue keeps active item
        queueManager.clearQueue()
        assertEquals(1, queueManager.videoQueue.value.size)
        assertEquals("vid_1", queueManager.videoQueue.value.first().id)
        assertFalse(queueManager.hasNextItem.value)
    }
}
