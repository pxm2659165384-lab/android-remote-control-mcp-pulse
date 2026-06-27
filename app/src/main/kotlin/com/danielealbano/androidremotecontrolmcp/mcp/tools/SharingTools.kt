@file:Suppress("MatchingDeclarationName")

package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.services.sharing.EphemeralFileLinkService
import com.danielealbano.androidremotecontrolmcp.services.sharing.SharedContentClassifier
import com.danielealbano.androidremotecontrolmcp.services.sharing.SharedContentInbox
import com.danielealbano.androidremotecontrolmcp.services.sharing.SharedItem
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject

// ─────────────────────────────────────────────────────────────────────────────
// get_shared_content
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MCP tool handler for `get_shared_content`.
 *
 * Drains the [SharedContentInbox] (consume-on-read) and returns each item: text inline; images as a
 * downscaled [ImageContent] plus a full-res capability URL; other files as a capability URL. The
 * untrusted-content warning is the first content block (device-derived content).
 */
class GetSharedContentHandler(
    private val inbox: SharedContentInbox,
    private val linkService: EphemeralFileLinkService,
    private val baseUrlProvider: () -> String,
    private val tunnelConnected: () -> Boolean,
) {
    suspend fun execute(): CallToolResult {
        val items = inbox.drainAll()
        if (items.isEmpty()) {
            return McpToolUtils.untrustedTextResult(
                "note:no shared content available. Share text/an image/a file to this app, then call this tool.",
            )
        }

        val content = mutableListOf<ContentBlock>()
        var linkProduced = false
        for (item in items) {
            when {
                item.kind == SharedItem.Kind.TEXT -> content += TextContent(text = item.text.orEmpty())

                SharedContentClassifier.isImage(item.mimeType) && item.blob != null -> {
                    val name = item.fileName ?: "image"
                    // Read/encode the image BEFORE register (which moves the blob out of the inbox dir).
                    val inline = runCatching { SharedContentClassifier.downscaleToInline(item.blob, item.mimeType) }.getOrNull()
                    val token = linkService.register(item.blob, item.mimeType, name)
                    val url = baseUrlProvider() + linkService.pathFor(token)
                    linkProduced = true
                    if (inline != null) {
                        content += ImageContent(data = inline.base64, mimeType = inline.mimeType)
                        content += TextContent(
                            text =
                                "Image '$name' (${item.sizeBytes} bytes). Original (full-res) at $url " +
                                    "(expires 1h). Only share this URL with the user if they ask for the original.",
                        )
                    } else {
                        content += TextContent(
                            text =
                                "Image '$name' ${item.mimeType} (${item.sizeBytes} bytes) could not be decoded; " +
                                    "download it at $url (expires 1h).",
                        )
                    }
                }

                item.blob != null -> {
                    val name = item.fileName ?: "file"
                    val token = linkService.register(item.blob, item.mimeType, name)
                    val url = baseUrlProvider() + linkService.pathFor(token)
                    linkProduced = true
                    content += TextContent(
                        text =
                            "File '$name' ${item.mimeType} (${item.sizeBytes} bytes) at $url (expires 1h). " +
                                "You can web_fetch text/PDF URLs to read them; other types are download-only.",
                    )
                }
            }
        }

        if (linkProduced && !tunnelConnected()) {
            content += TextContent(
                text =
                    "note:download URLs use the device's local network address; a remote client " +
                        "(Claude.ai / Claude Desktop) can only fetch them when a tunnel is active.",
            )
        }

        return McpToolUtils.untrustedResult(content)
    }

    fun register(
        server: Server,
        toolNamePrefix: String,
    ) {
        server.addTool(
            name = "$toolNamePrefix$TOOL_NAME",
            description =
                "Returns and clears items shared to this app via Android Share (text, images, files); " +
                    "read-once (re-share to read again). Prefer this over the clipboard when content can be " +
                    "shared directly to this app. Images return a downscaled view plus a full-res download " +
                    "URL; files return a fetch URL (web_fetch reads text/PDF).",
            inputSchema = ToolSchema(properties = buildJsonObject { }, required = emptyList()),
        ) { execute() }
    }

    companion object {
        const val TOOL_NAME = "get_shared_content"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Registration
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Registers the sharing tools. The capability-link plumbing is shared by both tools.
 */
@Suppress("LongParameterList")
fun registerSharingTools(
    server: Server,
    inbox: SharedContentInbox,
    linkService: EphemeralFileLinkService,
    fileOperationProvider: com.danielealbano.androidremotecontrolmcp.services.storage.FileOperationProvider,
    fileSizeLimitMb: Int,
    baseUrlProvider: () -> String,
    tunnelConnected: () -> Boolean,
    context: android.content.Context,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) {
    if (perms.isToolEnabled(GetSharedContentHandler.TOOL_NAME)) {
        GetSharedContentHandler(inbox, linkService, baseUrlProvider, tunnelConnected)
            .register(server, toolNamePrefix)
    }
    if (perms.isToolEnabled(ShareFileViaWebHandler.TOOL_NAME)) {
        ShareFileViaWebHandler(fileOperationProvider, linkService, fileSizeLimitMb, baseUrlProvider, tunnelConnected, context)
            .register(server, toolNamePrefix)
    }
}
