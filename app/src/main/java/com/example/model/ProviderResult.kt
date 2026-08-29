package com.example.model

/**
 * Standard telemetry and result encapsulation for all provider queries.
 * Ensures fault isolation so that if one provider fails or times out,
 * all other providers continue and return clean, typed outcomes.
 */
sealed class ProviderResult<out T> {

    data class Success<out T>(
        val providerId: String,
        val providerName: String,
        val data: T,
        val responseTimeMs: Long = 0L,
        val count: Int = 0
    ) : ProviderResult<T>()

    data class Empty(
        val providerId: String,
        val providerName: String,
        val responseTimeMs: Long = 0L,
        val message: String = "No results found"
    ) : ProviderResult<Nothing>()

    data class Failure(
        val providerId: String,
        val providerName: String,
        val errorMessage: String,
        val throwable: Throwable? = null,
        val responseTimeMs: Long = 0L,
        val isTimeout: Boolean = false
    ) : ProviderResult<Nothing>()

    val isSuccessful: Boolean
        get() = this is Success

    val providerIdentifier: String
        get() = when (this) {
            is Success -> providerId
            is Empty -> providerId
            is Failure -> providerId
        }
}
