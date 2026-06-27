package com.danielealbano.androidremotecontrolmcp.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.danielealbano.androidremotecontrolmcp.services.sharing.SharedContentClassifier
import com.danielealbano.androidremotecontrolmcp.services.sharing.SharedContentInbox
import com.danielealbano.androidremotecontrolmcp.services.sharing.SharedItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Invisible activity registered as an Android share target (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`,
 * any MIME type). It captures the shared payload into the in-memory [SharedContentInbox] and finishes
 * with no visible UI.
 *
 * Textual `ACTION_SEND` with `EXTRA_TEXT` is stored as a text item. Otherwise each `EXTRA_STREAM` URI
 * is streamed into the inbox blob directory while counting bytes, aborting (and deleting the partial
 * file) when the per-file cap is exceeded — the provider-reported size ([OpenableColumns.SIZE]) is NOT
 * trusted (it may be -1/null). All copying happens before [finish] because the URI read grants are tied
 * to this activity's lifetime.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    @Inject
    lateinit var inbox: SharedContentInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        process(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        process(intent)
    }

    /** Streams/copies the payload off the main thread (the read grant is tied to this activity), then finishes. */
    private fun process(intent: Intent) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { handleIntent(intent) }
            finish()
        }
    }

    private suspend fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                val type = intent.type
                if (text != null && type != null && SharedContentClassifier.isTextual(type)) {
                    addTextItem(text.toString(), type)
                } else {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    if (uri != null) streamToInbox(uri)?.let { inbox.add(it) }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                uris?.forEach { uri -> streamToInbox(uri)?.let { inbox.add(it) } }
            }
        }
    }

    private suspend fun addTextItem(
        text: String,
        type: String,
    ) {
        val now = System.currentTimeMillis()
        inbox.add(
            SharedItem(
                id = UUID.randomUUID().toString(),
                kind = SharedItem.Kind.TEXT,
                mimeType = type,
                fileName = null,
                text = text,
                blob = null,
                sizeBytes = text.toByteArray().size.toLong(),
                createdAtMs = now,
                expiresAtMs = now + SharedContentInbox.TTL_MS,
            ),
        )
    }

    /**
     * Streams [uri] into a fresh file in the inbox blob directory, enforcing [SharedContentInbox.MAX_FILE_BYTES]
     * during the copy. Returns the built [SharedItem], or null when the stream cannot be opened, exceeds the cap,
     * or fails to read (the partial file is deleted in those cases).
     */
    private fun streamToInbox(uri: Uri): SharedItem? {
        val mimeType = contentResolver.getType(uri) ?: intent.type ?: "application/octet-stream"
        val displayName = queryDisplayName(uri)
        val id = UUID.randomUUID().toString()
        val dest = File(inbox.blobDir, id)
        var count = 0L
        try {
            val input = contentResolver.openInputStream(uri)
            if (input == null) {
                dest.delete()
                return null
            }
            input.use { source ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > SharedContentInbox.MAX_FILE_BYTES) {
                            dest.delete()
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read shared stream", e)
            dest.delete()
            return null
        }
        val now = System.currentTimeMillis()
        return SharedItem(
            id = id,
            kind = SharedItem.Kind.BLOB,
            mimeType = mimeType,
            fileName = displayName,
            text = null,
            blob = dest,
            sizeBytes = count,
            createdAtMs = now,
            expiresAtMs = now + SharedContentInbox.TTL_MS,
        )
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()

    companion object {
        private const val TAG = "MCP:ShareReceiver"
        private const val BUFFER_SIZE = 8 * 1024
    }
}
