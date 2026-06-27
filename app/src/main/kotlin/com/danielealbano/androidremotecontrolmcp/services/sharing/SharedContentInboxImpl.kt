package com.danielealbano.androidremotecontrolmcp.services.sharing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [SharedContentInbox].
 *
 * State lives in a [Mutex]-guarded insertion-ordered map; nothing is persisted. The blob directory is
 * cleared on construction (the in-memory state starts empty, so any on-disk blob is a stale leftover
 * from a previous process).
 */
@Singleton
class SharedContentInboxImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SharedContentInbox {
        /** Test seam for the clock; defaults to the wall clock. */
        internal var clockMs: () -> Long = { System.currentTimeMillis() }

        /** Directory holding inbound shared blobs; exposed so the share receiver can write into it. */
        override val blobDir: File = File(context.filesDir, "shared_inbox")
        private val mutex = Mutex()
        private val items = LinkedHashMap<String, SharedItem>()

        init {
            blobDir.mkdirs()
            blobDir.listFiles()?.forEach { it.delete() }
        }

        override suspend fun add(item: SharedItem): Boolean {
            if (item.sizeBytes > SharedContentInbox.MAX_FILE_BYTES) {
                item.blob?.delete()
                return false
            }
            mutex.withLock {
                purgeExpiredLocked()
                // Terminates: a single item is ≤ 10 MB < 50 MB total, so evicting to empty always admits it.
                while (items.size >= SharedContentInbox.MAX_ITEMS ||
                    currentTotalBytesLocked() + item.sizeBytes > SharedContentInbox.MAX_TOTAL_BYTES
                ) {
                    if (items.isEmpty()) break
                    val eldest = items.entries.iterator().next()
                    items.remove(eldest.key)
                    eldest.value.blob?.delete()
                }
                items[item.id] = item
            }
            return true
        }

        override suspend fun drainAll(): List<SharedItem> =
            mutex.withLock {
                purgeExpiredLocked()
                val snapshot = items.values.toList()
                items.clear()
                snapshot
            }

        override suspend fun purgeExpired() = mutex.withLock { purgeExpiredLocked() }

        private fun currentTotalBytesLocked(): Long = items.values.sumOf { it.sizeBytes }

        private fun purgeExpiredLocked() {
            val now = clockMs()
            val iterator = items.entries.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next().value
                if (now >= item.expiresAtMs) {
                    iterator.remove()
                    item.blob?.delete()
                }
            }
        }
    }
