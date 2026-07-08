package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable

@Serializable
data class MediaTransitionConfig(
    val fileUri: String = "",
    val displayName: String = "",
    val mimeType: String = "video/*",
    val startPositionMs: Long = 0L,
    val previewLabel: String = "",
)

object MediaTransitionManager {
    var config by mutableStateOf(MediaTransitionConfig())
        private set
    var coverPreview by mutableStateOf<Bitmap?>(null)
        private set

    fun load(context: Context) {
        val prefs = prefs(context)
        config =
            MediaTransitionConfig(
                fileUri = prefs.getString(KEY_URI, "") ?: "",
                displayName = prefs.getString(KEY_NAME, "") ?: "",
                mimeType = prefs.getString(KEY_MIME, "video/*") ?: "video/*",
                startPositionMs = prefs.getLong(KEY_START_MS, 0L),
                previewLabel = prefs.getString(KEY_PREVIEW_LABEL, "") ?: "",
            )
        refreshCoverPreview(context)
    }

    fun save(context: Context) {
        prefs(context)
            .edit()
            .putString(KEY_URI, config.fileUri)
            .putString(KEY_NAME, config.displayName)
            .putString(KEY_MIME, config.mimeType)
            .putLong(KEY_START_MS, config.startPositionMs)
            .putString(KEY_PREVIEW_LABEL, config.previewLabel)
            .apply()
    }

    fun setStartPosition(
        context: Context,
        startPositionMs: Long,
    ) {
        config = config.copy(startPositionMs = startPositionMs.coerceAtLeast(0L))
        save(context)
    }

    fun setFile(
        context: Context,
        uri: Uri,
    ) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure {
            PulseLogger.w("Unable to persist media URI permission: ${it.message}")
        }

        config =
            config.copy(
                fileUri = uri.toString(),
                displayName = displayName(context, uri),
                mimeType = context.contentResolver.getType(uri) ?: "video/*",
                previewLabel = "已导入，封面预览可用",
            )
        refreshCoverPreview(context)
        save(context)
    }

    fun clear(context: Context) {
        config = MediaTransitionConfig()
        coverPreview = null
        save(context)
    }

    fun play(context: Context) {
        val cfg = config
        if (cfg.fileUri.isBlank()) {
            throw IllegalStateException("no_media_configured")
        }

        val uri = Uri.parse(cfg.fileUri)
        val intent =
            Intent(context, PulseMediaPlayerActivity::class.java).apply {
                setDataAndType(uri, cfg.mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(PulseMediaPlayerActivity.EXTRA_START_POSITION_MS, cfg.startPositionMs)
                putExtra(PulseMediaPlayerActivity.EXTRA_DISPLAY_NAME, cfg.displayName)
            }
        context.startActivity(intent)
    }

    fun refreshCoverPreview(context: Context) {
        val cfg = config
        coverPreview = null
        if (cfg.fileUri.isBlank()) return
        val uri = Uri.parse(cfg.fileUri)
        coverPreview =
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, uri)
                    val embedded = retriever.embeddedPicture?.let { bytes ->
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    val frame =
                        runCatching {
                            retriever.getFrameAtTime(
                                cfg.startPositionMs.coerceAtLeast(0L) * MICROSECONDS_PER_MILLISECOND,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            )
                        }.getOrNull()
                    frame ?: embedded
                }
            }.getOrElse {
                PulseLogger.w("Media cover preview unavailable: ${it.message}")
                null
            }
    }

    fun abandonAudioFocus(
        audioManager: AudioManager,
        request: AudioFocusRequest?,
    ) {
        if (request != null) {
            audioManager.abandonAudioFocusRequest(request)
        }
        PulseLogger.i("Audio focus released")
    }

    private fun displayName(
        context: Context,
        uri: Uri,
    ): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else uri.lastPathSegment
            } else {
                uri.lastPathSegment
            }
        } ?: uri.lastPathSegment ?: "Selected media"
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "pulse_link_media"
    private const val KEY_URI = "media_uri"
    private const val KEY_NAME = "media_name"
    private const val KEY_MIME = "media_mime"
    private const val KEY_START_MS = "media_start_ms"
    private const val KEY_PREVIEW_LABEL = "media_preview_label"
    private const val MICROSECONDS_PER_MILLISECOND = 1_000L
}
