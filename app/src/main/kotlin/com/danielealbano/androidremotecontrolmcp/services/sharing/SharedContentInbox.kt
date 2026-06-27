package com.danielealbano.androidremotecontrolmcp.services.sharing

import java.io.File

/**
 * In-memory queue of content shared INTO the app via the Android share sheet, drained (consumed) by
 * the `get_shared_content` MCP tool.
 *
 * IN-MEMORY ONLY (no persistence across process death): at most [MAX_ITEMS] items and [MAX_TOTAL_BYTES]
 * total; each item ≤ [MAX_FILE_BYTES] (larger ones are rejected); items expire after [TTL_MS]. Stale
 * blobs are cleared on init.
 */
interface SharedContentInbox {
    /** Directory the share receiver writes inbound blobs into before calling [add]. */
    val blobDir: File

    /**
     * Adds [item]. Returns false (deleting any blob) when `sizeBytes > MAX_FILE_BYTES`. Otherwise
     * evicts the oldest items (deleting their blobs) to honor [MAX_ITEMS] / [MAX_TOTAL_BYTES], inserts,
     * and returns true.
     */
    suspend fun add(item: SharedItem): Boolean

    /** Returns all non-expired items in insertion order AND clears the queue (consume-on-read). */
    suspend fun drainAll(): List<SharedItem>

    /** Deletes expired items and their blobs. */
    suspend fun purgeExpired()

    companion object {
        const val MAX_ITEMS = 5
        const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
        const val MAX_FILE_BYTES = 10L * 1024 * 1024
        const val TTL_MS = 60L * 60L * 1000L
    }
}

/**
 * A shared item.
 *
 * @property kind [Kind.TEXT] carries [text]; [Kind.BLOB] carries [blob] (a file in the inbox dir).
 */
data class SharedItem(
    val id: String,
    val kind: Kind,
    val mimeType: String,
    val fileName: String?,
    val text: String?,
    val blob: File?,
    val sizeBytes: Long,
    val createdAtMs: Long,
    val expiresAtMs: Long,
) {
    enum class Kind { TEXT, BLOB }
}
