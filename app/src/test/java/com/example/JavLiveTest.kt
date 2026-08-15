package com.example

import com.example.plugin.jav.orchestrator.UnifiedJavOrchestrator
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JavLiveTest {

    @Test
    fun `test live resolution with real JAV IDs`() = runBlocking {
        val testIds = listOf("IPX-800", "SSIS-001", "STARS-100", "JUL-200")
        println("==================================================")
        println("LIVE JAV PROVIDER NETWORK AUDIT TEST RUN")
        println("==================================================")

        for (javId in testIds) {
            println("\n--- Testing JAV ID: $javId ---")
            val result = UnifiedJavOrchestrator.resolveJav(javId)
            println("Metadata Found: ${result.metadata != null}")
            if (result.metadata != null) {
                println("  Title: ${result.metadata.title}")
                println("  Cover URL: ${result.metadata.coverUrl}")
                println("  Studio: ${result.metadata.studio}")
                println("  Actors: ${result.metadata.actors}")
                println("  Provider Scores: ${result.metadata.providerScores}")
            }
            println("Streams Found (${result.streams.size}):")
            for (stream in result.streams) {
                println("  [${stream.providerName}] Title: ${stream.title} | URL: ${stream.url.take(60)}...")
            }
            println("Trailers Found (${result.trailers.size}):")
            for (trailer in result.trailers) {
                println("  [${trailer.providerName}] Title: ${trailer.title} | Video URL: ${trailer.videoUrl}")
            }
            println("Subtitles Found (${result.subtitles.size}):")
            for (sub in result.subtitles) {
                println("  [${sub.providerId}] Lang: ${sub.language} | URL: ${sub.url}")
            }
        }
        println("==================================================")
    }
}
