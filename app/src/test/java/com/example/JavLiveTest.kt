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
        val testIds = listOf("IPX-800", "SSIS-001", "STARS-100", "JUL-200", "MIDE-900")
        println("==================================================")
        println("LIVE JAV PROVIDER NETWORK AUDIT TEST RUN (5 REAL JAV IDs)")
        println("==================================================")

        for (javId in testIds) {
            println("\n--- Testing JAV ID: $javId ---")
            val result = UnifiedJavOrchestrator.resolveJav(javId)
            val diagnostics = UnifiedJavOrchestrator.runDiagnostics(javId)

            println("Metadata Result: ${result.metadata?.title ?: "NO_RESULT"}")
            println("Streams Count: ${result.streams.size}")
            println("Trailers Count: ${result.trailers.size}")
            println("Subtitles Count: ${result.subtitles.size}")

            println("Provider Diagnostic Summary:")
            for (diag in diagnostics) {
                println("  - [${diag.capability}] ${diag.providerName} (${diag.providerId}): ${diag.status} (${diag.responseTimeMs}ms) - ${diag.detailMessage}")
            }
        }
        println("==================================================")
    }
}
