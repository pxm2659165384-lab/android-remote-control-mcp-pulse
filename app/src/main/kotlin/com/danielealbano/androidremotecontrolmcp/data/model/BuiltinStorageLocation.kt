package com.danielealbano.androidremotecontrolmcp.data.model

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException

/**
 * Defines the built-in storage locations backed by MediaStore.
 *
 * These are always available without user setup (no SAF picker needed).
 * The app can write to these locations without any runtime permissions.
 * Reading non-owned files requires the corresponding [readMediaPermission].
 *
 * The [collectionUri] is resolved lazily to avoid loading Android framework
 * classes during enum initialization (which would fail in JVM unit tests).
 *
 * @property locationId Stable identifier used in MCP tool calls.
 * @property displayNameOwned Display name when only owned files are visible.
 * @property displayNameAll Display name when all files are visible (READ_MEDIA_* granted).
 * @property baseRelativePath The MediaStore RELATIVE_PATH prefix (e.g., "Download/").
 * @property readMediaPermission Runtime permission for "all files" access, or null if unavailable.
 */
enum class BuiltinStorageLocation(
    val locationId: String,
    val displayNameOwned: String,
    val displayNameAll: String,
    private val collectionUriProvider: () -> Uri,
    val baseRelativePath: String,
    val readMediaPermission: String?,
) {
    DOWNLOADS(
        locationId = "builtin:downloads",
        displayNameOwned = "Downloads - Only owned files",
        displayNameAll = "Downloads - Only owned files",
        collectionUriProvider = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }
        },
        baseRelativePath = "Download/",
        readMediaPermission = null,
    ),
    PICTURES(
        locationId = "builtin:pictures",
        displayNameOwned = "Pictures - Only owned files",
        displayNameAll = "Pictures - All files",
        collectionUriProvider = { MediaStore.Images.Media.EXTERNAL_CONTENT_URI },
        baseRelativePath = "Pictures/",
        readMediaPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                null
            },
    ),
    MOVIES(
        locationId = "builtin:movies",
        displayNameOwned = "Movies - Only owned files",
        displayNameAll = "Movies - All files",
        collectionUriProvider = { MediaStore.Video.Media.EXTERNAL_CONTENT_URI },
        baseRelativePath = "Movies/",
        readMediaPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_VIDEO
            } else {
                null
            },
    ),
    MUSIC(
        locationId = "builtin:music",
        displayNameOwned = "Music - Only owned files",
        displayNameAll = "Music - All files",
        collectionUriProvider = { MediaStore.Audio.Media.EXTERNAL_CONTENT_URI },
        baseRelativePath = "Music/",
        readMediaPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_AUDIO
            } else {
                null
            },
    ),
    ;

    /** MediaStore collection content URI for queries/inserts. Resolved lazily. */
    val collectionUri: Uri by lazy { collectionUriProvider() }

    companion object {
        /** Prefix for all built-in location IDs. */
        const val ID_PREFIX = "builtin:"

        /** Returns the [BuiltinStorageLocation] for a given location ID, or null. */
        fun fromLocationId(locationId: String): BuiltinStorageLocation? = entries.find { it.locationId == locationId }

        /** Returns true if the given location ID is a built-in location. */
        fun isBuiltinId(locationId: String): Boolean = locationId.startsWith(ID_PREFIX)

        /**
         * Validates a relative path for use with built-in locations.
         * Rejects path traversal attempts (`..`), absolute paths, and control characters.
         *
         * @throws McpToolException.InvalidParams if the path is invalid.
         */
        fun validatePath(path: String) {
            val error = findPathValidationError(path)
            if (error != null) {
                throw McpToolException.InvalidParams(error)
            }
        }

        private fun findPathValidationError(path: String): String? =
            when {
                path.startsWith("/") -> {
                    "Path must be relative, not absolute"
                }

                else -> {
                    path.split("/").filter { it.isNotEmpty() }.firstNotNullOfOrNull { segment ->
                        when {
                            segment == ".." -> {
                                "Path must not contain '..' segments"
                            }

                            segment == "." -> {
                                "Path must not contain '.' segments"
                            }

                            CONTROL_CHAR_REGEX.containsMatchIn(segment) -> {
                                "Path must not contain control characters"
                            }

                            else -> {
                                null
                            }
                        }
                    }
                }
            }

        private val CONTROL_CHAR_REGEX = Regex("[\\p{Cntrl}]")
    }
}
