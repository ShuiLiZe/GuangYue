package com.guangyue.core.player

/**
 * Anything that can turn a plugin payload into a playable stream url.
 *
 * :core:data plugs in the real implementation backed by MusicPluginBridge; :core:player only
 * depends on this interface so the two stay independent (no compile cycle).
 */
interface MediaSourceResolver {

    /**
     * Resolve a playable url.
     *
     * @param origin item origin (plugin + quality + opaque payload)
     * @throws ResolutionException when the plugin cannot produce a url
     */
    suspend fun resolve(origin: MediaOrigin): ResolvedSource
}

data class ResolvedSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** true when the url is known to expire quickly and should be re-resolved on retry */
    val volatile: Boolean = true,
)

class ResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)
