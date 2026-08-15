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
    fun `test repository provider integrations against IPX-800, SSIS-001, and STARS-100`() = runBlocking {
        val testIds = listOf("IPX-800", "SSIS-001", "STARS-100")
        println("==================================================")
        println("REPOSITORIES INTEGRATION TEST RUN (IPX-800, SSIS-001, STARS-100)")
        println("==================================================")

        for (javId in testIds) {
            println("\n--------------------------------------------------")
            println("Testing JAV ID: $javId")
            println("--------------------------------------------------")
            val diagnostics = UnifiedJavOrchestrator.runDiagnostics(javId)

            for (diag in diagnostics) {
                if (listOf("javinizer_go", "avm_engine", "javdex", "openaver", "javpy_resolver", "mdcx", "fss").contains(diag.providerId)) {
                    println("  - [${diag.providerName} (${diag.providerId})] Status: ${diag.status} (${diag.responseTimeMs}ms) | Details: ${diag.detailMessage}")
                }
            }
        }
        println("==================================================")
    }
}
