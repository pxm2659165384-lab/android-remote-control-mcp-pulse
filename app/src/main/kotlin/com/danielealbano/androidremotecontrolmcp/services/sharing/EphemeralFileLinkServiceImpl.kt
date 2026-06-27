package com.danielealbano.androidremotecontrolmcp.services.sharing

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

/**
 * In-memory implementation of [EphemeralFileLinkService].
 *
 * State lives in a [Mutex]-guarded insertion-ordered map; nothing is persisted. The blob directory
 * is cleared on construction (the in-memory state starts empty, so any on-disk blob is a stale
 * leftover from a previous process). Tokens/URLs are never logged.
 */
@Singleton
class EphemeralFileLinkServiceImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : EphemeralFileLinkService {
        /** Test seam for the clock; defaults to the wall clock. */
        internal var clockMs: () -> Long = { System.currentTimeMillis() }

        private val blobDir = File(context.filesDir, "ephemeral_links")
        private val mutex = Mutex()
        private val entries = LinkedHashMap<String, LinkEntry>()

        init {
            blobDir.mkdirs()
            blobDir.listFiles()?.forEach { it.delete() }
        }

        override suspend fun register(
            source: File,
            mimeType: String,
            fileName: String,
        ): String =
            mutex.withLock {
                purgeExpiredLocked()
                val token = CapabilityToken.generate()
                val dest = File(blobDir, token)
                if (!source.renameTo(dest)) {
                    source.copyTo(dest, overwrite = true)
                    source.delete()
                }
                check(dest.exists()) { "Failed to move blob into the link registry" }
                while (entries.size >= EphemeralFileLinkService.MAX_LINKS) {
                    val eldest = entries.entries.iterator().next()
                    entries.remove(eldest.key)
                    eldest.value.blob.delete()
                }
                entries[token] =
                    LinkEntry(
                        token = token,
                        blob = dest,
                        mimeType = mimeType,
                        fileName = fileName,
                        expiresAtMs = clockMs() + EphemeralFileLinkService.TTL_MS,
                    )
                token
            }

        override suspend fun resolve(token: String): LinkEntry? =
            mutex.withLock {
                purgeExpiredLocked()
                entries[token]?.takeIf { clockMs() < it.expiresAtMs && it.blob.exists() }
            }

        override suspend fun purgeExpired() = mutex.withLock { purgeExpiredLocked() }

        override fun pathFor(token: String): String = EphemeralFileLinkService.PATH_PREFIX + token

        private fun purgeExpiredLocked() {
            val now = clockMs()
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next().value
                if (now >= entry.expiresAtMs || !entry.blob.exists()) {
                    iterator.remove()
                    entry.blob.delete()
                }
            }
        }
    }
