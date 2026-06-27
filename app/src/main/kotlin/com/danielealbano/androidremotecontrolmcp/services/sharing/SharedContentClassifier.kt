package com.danielealbano.androidremotecontrolmcp.services.sharing

import android.graphics.BitmapFactory
import com.danielealbano.androidremotecontrolmcp.services.screencapture.ScreenshotEncoder
import java.io.File

/** A downscaled inline image plus the MIME type that actually describes [base64]. */
data class InlineImage(val base64: String, val mimeType: String)

/**
 * Classifies shared MIME types and downscales images for inline display.
 *
 * [isTextual] / [isImage] are pure. [downscaleToInline] decodes an image file and produces a
 * base64 view whose longest side is ≤ [IMAGE_MAX_DIMENSION], reusing [ScreenshotEncoder].
 */
object SharedContentClassifier {
    const val IMAGE_MAX_DIMENSION = 800
    private const val JPEG_QUALITY = 80

    private val TEXTUAL_MIMES =
        setOf(
            "application/json",
            "application/xml",
            "application/xhtml+xml",
            "application/javascript",
            "application/ld+json",
            "application/yaml",
            "text/yaml",
        )

    private val encoder = ScreenshotEncoder()

    /** True for `text/*` plus the explicit textual allowlist (ignores any `; charset=` suffix). */
    fun isTextual(mimeType: String): Boolean {
        val mime = mimeType.substringBefore(';').trim().lowercase()
        return mime.startsWith("text/") || mime in TEXTUAL_MIMES
    }

    /** True for `image/*`. */
    fun isImage(mimeType: String): Boolean = mimeType.trim().lowercase().startsWith("image/")

    /**
     * Returns a base64 inline view of the image in [blob].
     *
     * When the image already fits within [IMAGE_MAX_DIMENSION], the original bytes (and
     * [originalMime]) are kept; otherwise it is scaled so the longest side is [IMAGE_MAX_DIMENSION]
     * and re-encoded as JPEG (so the returned MIME becomes `image/jpeg`).
     */
    fun downscaleToInline(
        blob: File,
        originalMime: String,
    ): InlineImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(blob.absolutePath, bounds)
        val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longestSide in 1..IMAGE_MAX_DIMENSION) {
            return InlineImage(encoder.encodeToBase64(blob.readBytes()), originalMime)
        }

        val bitmap =
            BitmapFactory.decodeFile(blob.absolutePath)
                ?: error("Shared image could not be decoded: ${blob.name}")
        val resized = encoder.resizeBitmapProportional(bitmap, IMAGE_MAX_DIMENSION, IMAGE_MAX_DIMENSION)
        return try {
            InlineImage(encoder.encodeToBase64(encoder.encodeBitmapToJpeg(resized, JPEG_QUALITY)), "image/jpeg")
        } finally {
            if (resized != bitmap) resized.recycle()
            bitmap.recycle()
        }
    }
}
