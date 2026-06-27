package com.danielealbano.androidremotecontrolmcp.services.sharing

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@DisplayName("EphemeralFileLinkServiceImpl")
class EphemeralFileLinkServiceImplTest {
    @TempDir
    lateinit var tempDir: File

    private fun newService(): EphemeralFileLinkServiceImpl {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        return EphemeralFileLinkServiceImpl(context)
    }

    private fun blobDir(): File = File(tempDir, "ephemeral_links")

    private fun sourceFile(
        name: String,
        content: ByteArray = name.toByteArray(),
    ): File {
        val dir = File(tempDir, "sources").also { it.mkdirs() }
        return File(dir, name).apply { writeBytes(content) }
    }

    @Test
    @DisplayName("register returns a 64-hex token, moves the source, and resolve finds it")
    fun registerAndResolve() =
        runTest {
            val service = newService()
            val source = sourceFile("a.bin", byteArrayOf(1, 2, 3))
            val token = service.register(source, "application/octet-stream", "a.bin")

            assertTrue(token.matches(Regex("[0-9a-f]{64}")), "token must be 64-hex")
            assertEquals("/s/$token", service.pathFor(token))
            assertFalse(source.exists(), "source must be moved out of its original location")

            val entry = service.resolve(token)
            assertNotNull(entry)
            assertEquals("application/octet-stream", entry!!.mimeType)
            assertEquals("a.bin", entry.fileName)
            assertTrue(entry.blob.exists() && entry.blob.parentFile == blobDir())
            assertArrayEqualsContent(byteArrayOf(1, 2, 3), entry.blob.readBytes())
        }

    @Test
    @DisplayName("resolve returns null for an unknown token")
    fun resolveUnknown() =
        runTest {
            assertNull(newService().resolve("deadbeef"))
        }

    @Test
    @DisplayName("resolve returns null after the TTL has elapsed")
    fun resolveExpired() =
        runTest {
            val service = newService()
            var now = 1_000L
            service.clockMs = { now }
            val token = service.register(sourceFile("b.bin"), "text/plain", "b.bin")

            now += EphemeralFileLinkService.TTL_MS + 1
            assertNull(service.resolve(token), "expired link must not resolve")
        }

    @Test
    @DisplayName("adding the 21st link evicts the oldest, deletes its blob, and caps at 20")
    fun evictsOldest() =
        runTest {
            val service = newService()
            val firstToken = service.register(sourceFile("first.bin"), "text/plain", "first.bin")
            val firstBlob = service.resolve(firstToken)!!.blob
            repeat(EphemeralFileLinkService.MAX_LINKS) { i ->
                service.register(sourceFile("f$i.bin"), "text/plain", "f$i.bin")
            }

            assertNull(service.resolve(firstToken), "oldest link evicted")
            assertFalse(firstBlob.exists(), "evicted blob deleted")
            assertEquals(EphemeralFileLinkService.MAX_LINKS, blobDir().listFiles()?.size)
        }

    @Test
    @DisplayName("init clears stale blobs from a previous process")
    fun initClearsStaleBlobs() {
        blobDir().mkdirs()
        val stale = File(blobDir(), "stale").apply { writeBytes(byteArrayOf(9)) }
        assertTrue(stale.exists())

        newService() // constructing clears the dir

        assertFalse(stale.exists(), "stale blob must be deleted on init")
    }

    private fun assertArrayEqualsContent(
        expected: ByteArray,
        actual: ByteArray,
    ) {
        assertTrue(expected.contentEquals(actual), "blob content mismatch")
    }
}
