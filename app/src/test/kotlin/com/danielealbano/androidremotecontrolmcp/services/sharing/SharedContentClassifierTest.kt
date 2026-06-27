package com.danielealbano.androidremotecontrolmcp.services.sharing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.OutputStream

@DisplayName("SharedContentClassifier")
class SharedContentClassifierTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    @DisplayName("textual mimes map to text; binary mimes do not")
    fun textualMimesMapToText() {
        listOf(
            "application/json",
            "application/xml",
            "application/xhtml+xml",
            "application/javascript",
            "application/ld+json",
            "application/yaml",
            "text/yaml",
            "text/csv",
            "text/plain; charset=utf-8",
        ).forEach { mime ->
            assertTrue(SharedContentClassifier.isTextual(mime), "$mime must be textual")
        }
        assertFalse(SharedContentClassifier.isTextual("application/pdf"))
        assertFalse(SharedContentClassifier.isTextual("application/zip"))
    }

    @Test
    @DisplayName("image mimes are detected")
    fun imageMimesDetected() {
        assertTrue(SharedContentClassifier.isImage("image/png"))
        assertTrue(SharedContentClassifier.isImage("image/jpeg"))
        assertFalse(SharedContentClassifier.isImage("application/pdf"))
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(BitmapFactory::class, Bitmap::class, Base64::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(BitmapFactory::class, Bitmap::class, Base64::class)
    }

    private fun imageBlob(name: String): File = File(tempDir, name).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

    @Test
    @DisplayName("large image is downscaled to 800px and re-encoded as JPEG")
    fun largeImageReencodedToJpeg() {
        val blob = imageBlob("large.png")
        every { BitmapFactory.decodeFile(any<String>(), any<BitmapFactory.Options>()) } answers {
            secondArg<BitmapFactory.Options>().apply {
                outWidth = 1600
                outHeight = 1200
            }
            null
        }
        val source =
            mockk<Bitmap>(relaxed = true) {
                every { width } returns 1600
                every { height } returns 1200
            }
        every { BitmapFactory.decodeFile(any<String>()) } returns source
        val resized =
            mockk<Bitmap>(relaxed = true) {
                every { compress(any(), any(), any<OutputStream>()) } returns true
            }
        every { Bitmap.createScaledBitmap(source, 800, 600, true) } returns resized
        every { Base64.encodeToString(any<ByteArray>(), Base64.NO_WRAP) } returns "JPEGB64"

        val result = SharedContentClassifier.downscaleToInline(blob, "image/png")

        assertEquals("image/jpeg", result.mimeType)
        assertEquals("JPEGB64", result.base64)
        verify { Bitmap.createScaledBitmap(source, 800, 600, true) }
    }

    @Test
    @DisplayName("small image keeps original bytes and MIME (no upscale)")
    fun smallImageKept() {
        val blob = imageBlob("small.png")
        every { BitmapFactory.decodeFile(any<String>(), any<BitmapFactory.Options>()) } answers {
            secondArg<BitmapFactory.Options>().apply {
                outWidth = 400
                outHeight = 300
            }
            null
        }
        every { Base64.encodeToString(any<ByteArray>(), Base64.NO_WRAP) } returns "ORIGB64"

        val result = SharedContentClassifier.downscaleToInline(blob, "image/png")

        assertEquals("image/png", result.mimeType)
        assertEquals("ORIGB64", result.base64)
        verify(exactly = 0) { Bitmap.createScaledBitmap(any(), any(), any(), any()) }
    }
}
