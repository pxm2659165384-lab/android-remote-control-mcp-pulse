package com.danielealbano.androidremotecontrolmcp.data.model

/**
 * Represents the current state of the tunnel connection.
 *
 * Used as [kotlinx.coroutines.flow.StateFlow] value, observed by the UI
 * to display tunnel status and public URL.
 */
sealed class TunnelStatus {
    /** Tunnel is not active. */
    data object Disconnected : TunnelStatus()

    /** Tunnel is establishing a connection. */
    data object Connecting : TunnelStatus()

    /**
     * Tunnel is connected and serving traffic.
     *
     * @property urls The public HTTPS URL(s) (e.g., ["https://xxx.trycloudflare.com"] for Free/ngrok,
     *   or one entry per public hostname for a Cloudflare token tunnel).
     * @property providerType The provider that created this tunnel.
     */
    data class Connected(
        val urls: List<String>,
        val providerType: TunnelProviderType,
    ) : TunnelStatus()

    /**
     * Tunnel encountered an error.
     *
     * @property message A human-readable error description.
     */
    data class Error(
        val message: String,
    ) : TunnelStatus()
}
