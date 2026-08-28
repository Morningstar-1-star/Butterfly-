package com.example.metadata.person

import com.example.metadata.JavActor

/**
 * Universal interface for Actress / Person enrichment.
 */
interface PersonProvider {
    val id: String
    val name: String

    /**
     * Enriches an actor with high-res portrait avatar and biographical details.
     */
    suspend fun enrichActor(actor: JavActor): JavActor

    /**
     * Looks up an actor by exact or phonetic/kanji name.
     */
    suspend fun getActorDetails(name: String): JavActor?
}
