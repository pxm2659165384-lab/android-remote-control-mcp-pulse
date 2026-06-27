package com.danielealbano.androidremotecontrolmcp.services.sharing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [EphemeralFileLinkService].
 *
 * All state — including the blob bytes — lives in a [Mutex]-guarded insertion-ordered map in RAM.
 * Nothing is written to disk and nothing is persisted across process death. Tokens/URLs are never logged.
 */
@Singleton
class EphemeralFileLinkServiceImpl
    @Inject
    constructor() : EphemeralFileLinkService {
        /** Test seam for the clock; defaults to the wall clock. */
        internal var clockMs: () -> Long = { System.currentTimeMillis() }

        private val mutex = Mutex()
        private val entries = LinkedHashMap<String, LinkEntry>()

        override suspend fun register(
            bytes: ByteArray,
            mimeType: String,
            fileName: String,
        ): String =
            mutex.withLock {
                purgeExpiredLocked()
                val token = CapabilityToken.generate()
                while (entries.size >= EphemeralFileLinkService.MAX_LINKS) {
                    entries.remove(
                        entries.entries
                            .iterator()
                            .next()
                            .key,
                    )
                }
                entries[token] =
                    LinkEntry(
                        token = token,
                        bytes = bytes,
                        mimeType = mimeType,
                        fileName = fileName,
                        expiresAtMs = clockMs() + EphemeralFileLinkService.TTL_MS,
                    )
                token
            }

        override suspend fun resolve(token: String): LinkEntry? =
            mutex.withLock {
                purgeExpiredLocked()
                entries[token]?.takeIf { clockMs() < it.expiresAtMs }
            }

        override suspend fun purgeExpired() = mutex.withLock { purgeExpiredLocked() }

        override fun pathFor(token: String): String = EphemeralFileLinkService.PATH_PREFIX + token

        private fun purgeExpiredLocked() {
            val now = clockMs()
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                if (now >= iterator.next().value.expiresAtMs) {
                    iterator.remove()
                }
            }
        }
    }
