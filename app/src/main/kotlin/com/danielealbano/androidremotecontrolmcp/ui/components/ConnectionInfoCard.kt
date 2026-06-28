@file:Suppress("FunctionNaming", "UnusedPrivateMember", "LongMethod", "LongParameterList")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelProviderType
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelStatus
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme

private const val TOKEN_MASK = "********-****-****-****-************"

/** Visual state of the Public URL row in [ConnectionInfoCard]. */
internal sealed interface TunnelRowState {
    /** Row hidden: remote access off, or server not Starting/Running. */
    data object Hidden : TunnelRowState

    /** Server started and remote access enabled, address not yet available — show a spinner. */
    data object Loading : TunnelRowState

    /** Tunnel connected — show the public address. */
    data class Connected(val url: String) : TunnelRowState

    /** Tunnel failed — show the error message in red. */
    data class Failed(val message: String) : TunnelRowState
}

/**
 * Computes the Public URL row state from the combined server + tunnel state.
 *
 * The row becomes visible as soon as the server is Starting or Running and
 * remote access is enabled, showing [TunnelRowState.Loading] (a spinner) until
 * the tunnel reports [TunnelStatus.Connected] or [TunnelStatus.Error].
 */
internal fun tunnelRowState(
    tunnelEnabled: Boolean,
    serverStatus: ServerStatus,
    tunnelStatus: TunnelStatus,
): TunnelRowState {
    val serverActive =
        serverStatus is ServerStatus.Running || serverStatus is ServerStatus.Starting
    if (!tunnelEnabled || !serverActive) return TunnelRowState.Hidden
    return when (tunnelStatus) {
        is TunnelStatus.Connected -> TunnelRowState.Connected(tunnelStatus.url)
        is TunnelStatus.Error -> TunnelRowState.Failed(tunnelStatus.message)
        TunnelStatus.Connecting, TunnelStatus.Disconnected -> TunnelRowState.Loading
    }
}

/**
 * Builds the Copy-all / Share connection string. The tunnel line is included
 * only when [tunnelUrl] is non-null (i.e. the tunnel is connected); the bearer
 * token line is included only when [bearerToken] is non-empty.
 */
internal fun buildConnectionString(
    serverUrl: String,
    tunnelUrl: String?,
    bearerToken: String,
): String =
    buildString {
        append("URL: $serverUrl")
        tunnelUrl?.let { append("\nTunnel: $it/mcp") }
        if (bearerToken.isNotEmpty()) {
            append("\nBearer Token: $bearerToken")
        }
    }

@Composable
fun ConnectionInfoCard(
    bindingAddress: BindingAddress,
    ipAddress: String,
    port: Int,
    httpsEnabled: Boolean,
    bearerToken: String,
    onCopyAll: (String) -> Unit,
    tunnelEnabled: Boolean = false,
    serverStatus: ServerStatus = ServerStatus.Stopped,
    tunnelStatus: TunnelStatus = TunnelStatus.Disconnected,
    onShare: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showToken by remember { mutableStateOf(false) }

    val displayAddress =
        when (bindingAddress) {
            BindingAddress.LOCALHOST -> "127.0.0.1"
            BindingAddress.NETWORK -> ipAddress
        }
    val scheme = if (httpsEnabled) "https" else "http"
    val serverUrl = "$scheme://$displayAddress:$port/mcp"
    val displayToken = if (showToken) bearerToken else TOKEN_MASK

    val rowState = tunnelRowState(tunnelEnabled, serverStatus, tunnelStatus)

    val labelStyle = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val ipLabel = stringResource(R.string.connection_info_ip)
    val portLabel = stringResource(R.string.connection_info_port)
    val urlLabel = stringResource(R.string.connection_info_url)
    val publicUrlLabel = stringResource(R.string.remote_access_public_url_label)
    val tokenLabel = stringResource(R.string.connection_info_token)

    val visibleLabels =
        buildList {
            add(ipLabel)
            add(portLabel)
            add(urlLabel)
            if (rowState != TunnelRowState.Hidden) add(publicUrlLabel)
            if (bearerToken.isNotEmpty()) add(tokenLabel)
        }
    val labelColumnWidth: Dp =
        remember(visibleLabels, labelStyle, density) {
            with(density) {
                visibleLabels.maxOf { measurer.measure(it, labelStyle).size.width }.toDp()
            }
        }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.connection_info_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(12.dp))

            ConnectionInfoRow(label = ipLabel, labelWidth = labelColumnWidth) {
                Text(text = displayAddress, style = MaterialTheme.typography.bodyMedium)
            }
            ConnectionInfoRow(label = portLabel, labelWidth = labelColumnWidth) {
                Text(text = port.toString(), style = MaterialTheme.typography.bodyMedium)
            }
            ConnectionInfoRow(label = urlLabel, labelWidth = labelColumnWidth) {
                Text(text = serverUrl, style = MaterialTheme.typography.bodyMedium)
            }
            if (rowState != TunnelRowState.Hidden) {
                ConnectionInfoRow(label = publicUrlLabel, labelWidth = labelColumnWidth) {
                    when (rowState) {
                        TunnelRowState.Loading ->
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )

                        is TunnelRowState.Connected ->
                            Text(
                                text = "${rowState.url}/mcp",
                                style = MaterialTheme.typography.bodyMedium,
                            )

                        is TunnelRowState.Failed ->
                            Text(
                                text = stringResource(R.string.remote_access_status_error, rowState.message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )

                        TunnelRowState.Hidden -> Unit
                    }
                }
            }

            if (bearerToken.isNotEmpty()) {
                ConnectionInfoRow(label = tokenLabel, labelWidth = labelColumnWidth) {
                    Text(
                        text = displayToken,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector =
                                if (showToken) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                            contentDescription =
                                if (showToken) {
                                    stringResource(R.string.config_token_hide)
                                } else {
                                    stringResource(R.string.config_token_show)
                                },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val connectionString =
                buildConnectionString(
                    serverUrl = serverUrl,
                    tunnelUrl = (rowState as? TunnelRowState.Connected)?.url,
                    bearerToken = bearerToken,
                )
            Row(
                modifier = Modifier.align(Alignment.End),
            ) {
                TextButton(onClick = { onCopyAll(connectionString) }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.connection_info_copy_all),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                TextButton(onClick = { onShare(connectionString) }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.connection_info_share),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionInfoRow(
    label: String,
    labelWidth: Dp,
    modifier: Modifier = Modifier,
    value: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        value()
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionInfoCardPreview() {
    AndroidRemoteControlMcpTheme {
        ConnectionInfoCard(
            bindingAddress = BindingAddress.LOCALHOST,
            ipAddress = "192.168.1.100",
            port = 8080,
            httpsEnabled = false,
            bearerToken = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            tunnelEnabled = true,
            serverStatus = ServerStatus.Running(port = 8080, bindingAddress = "127.0.0.1"),
            tunnelStatus =
                TunnelStatus.Connected(
                    url = "https://random-words.trycloudflare.com",
                    providerType = TunnelProviderType.CLOUDFLARE,
                ),
            onCopyAll = {},
            onShare = {},
        )
    }
}
