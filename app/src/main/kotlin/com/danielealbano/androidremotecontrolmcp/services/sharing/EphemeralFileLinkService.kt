package com.danielealbano.androidremotecontrolmcp.services.sharing

import java.io.File

/**
 * Holds short-lived capability links that serve a blob over `/s/<token>` for a limited time.
 *
 * IN-MEMORY ONLY (no persistence across process death): at most [MAX_LINKS] live links; each expires
 * after [TTL_MS]; adding beyond [MAX_LINKS] evicts the oldest (FIFO) and deletes its blob. Deletion
 * is TTL/eviction only — links are NOT removed on fetch and may be fetched multiple times within the
 * TTL. The token and full URL are credentials and MUST NOT be logged.
 */
interface EphemeralFileLinkService {
    /**
     * Moves [source] into the managed dir under a fresh token, registers it, and returns the token.
     * Evicts the oldest link when the registry is full.
     */
    suspend fun register(
        source: File,
        mimeType: String,
        fileName: String,
    ): String

    /** Live entry for [token], or null if missing/expired (expired entries are purged on lookup). */
    suspend fun resolve(token: String): LinkEntry?

    /** Deletes expired entries and their blobs. */
    suspend fun purgeExpired()

    /** Returns the route path for [token], e.g. `/s/<token>`. */
    fun pathFor(token: String): String

    companion object {
        const val PATH_PREFIX = "/s/"
        const val MAX_LINKS = 20
        const val TTL_MS = 60L * 60L * 1000L
    }
}

/**
 * A live capability link.
 *
 * @property token The secret URL segment.
 * @property blob The on-disk file served for this link.
 * @property mimeType The content-type served.
 * @property fileName The original file name (for callers' metadata).
 * @property expiresAtMs Absolute expiry time in epoch millis.
 */
data class LinkEntry(
    val token: String,
    val blob: File,
    val mimeType: String,
    val fileName: String,
    val expiresAtMs: Long,
)
