package com.example.plugin.jav

interface BaseJavProvider {
    val id: String
    val name: String
    val timeoutMs: Long get() = 8000L
    var isEnabled: Boolean
}

interface MetadataProvider : BaseJavProvider {
    suspend fun fetchMetadata(javId: String): JavMetadata?
}

interface StreamProvider : BaseJavProvider {
    suspend fun resolveStreams(javId: String, title: String): List<JavStream>
}

interface TrailerProvider : BaseJavProvider {
    suspend fun fetchTrailers(javId: String): List<JavTrailer>
}

interface SubtitleProvider : BaseJavProvider {
    suspend fun searchSubtitles(javId: String, title: String): List<JavSubtitle>
}
