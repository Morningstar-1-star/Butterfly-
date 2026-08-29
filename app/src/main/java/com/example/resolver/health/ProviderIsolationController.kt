package com.example.resolver.health

import android.util.Log
import com.example.model.MediaIdentity
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Provider Isolation & Resilience Controller (Adapted from Cauldron concurrency & scraper isolation).
 *
 * Guarantees that:
 * 1. Every provider executes in strict fault isolation: one provider crash/block NEVER impacts others.
 * 2. Strict concurrency limiting per provider via [Semaphore].
 * 3. Individual timeout enforcement per provider.
 * 4. Circuit Breaker protection: automatically skips quarantined providers unless in probing state.
 * 5. Automatic latency and health metrics tracking.
 */
object ProviderIsolationController {
    private const val TAG = "ProviderIsolation"

    private val providerSemaphores = ConcurrentHashMap<String, Semaphore>()

    private fun getSemaphore(providerId: String, maxConcurrent: Int = 3): Semaphore {
        return providerSemaphores.getOrPut(providerId.lowercase()) {
            Semaphore(maxConcurrent)
        }
    }

    /**
     * Executes [SourceProvider.searchSources] with complete concurrency, timeout,
     * and circuit breaker isolation.
     */
    fun executeIsolated(
        provider: SourceProvider,
        identity: MediaIdentity
    ): Flow<List<SourceCandidate>> = flow {
        val pid = provider.id.lowercase()

        // 1. Circuit Breaker Check
        if (ProviderHealthManager.isQuarantined(pid)) {
            Log.d(TAG, "Skipping quarantined provider: $pid")
            emit(emptyList())
            return@flow
        }

        val semaphore = getSemaphore(pid, provider.maxConcurrentRequests)
        val startTime = System.currentTimeMillis()

        try {
            semaphore.withPermit {
                val timeout = provider.timeoutMs
                val resultList = withTimeoutOrNull(timeout) {
                    val accumulated = mutableListOf<SourceCandidate>()
                    provider.searchSources(identity)
                        .catch { e ->
                            Log.w(TAG, "Provider $pid threw caught exception in flow: ${e.message}")
                            ProviderHealthManager.recordFailure(
                                providerId = pid,
                                failureType = FailureType.EXTRACTION_FAILED,
                                errorMessage = e.message
                            )
                        }
                        .collect { candidates ->
                            if (candidates.isNotEmpty()) {
                                accumulated.clear()
                                accumulated.addAll(candidates)
                                emit(candidates)
                            }
                        }
                    accumulated
                }

                val latency = System.currentTimeMillis() - startTime

                if (resultList == null) {
                    // Timed out
                    Log.w(TAG, "Provider $pid timed out after ${timeout}ms")
                    ProviderHealthManager.recordFailure(
                        providerId = pid,
                        failureType = FailureType.TIMEOUT,
                        errorMessage = "Execution timed out after ${timeout}ms",
                        latencyMs = latency
                    )
                } else if (resultList.isNotEmpty()) {
                    // Success
                    ProviderHealthManager.recordSuccess(
                        providerId = pid,
                        latencyMs = latency
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "Isolated execution failed for $pid: ${e.message}")
            ProviderHealthManager.recordFailure(
                providerId = pid,
                failureType = FailureType.UNKNOWN,
                errorMessage = e.message,
                latencyMs = latency
            )
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}
