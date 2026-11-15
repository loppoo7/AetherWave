package com.aetherwave.playback

/** A direct stream URL and the HTTP headers it needs. Some clients require their own User-Agent. */
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)
