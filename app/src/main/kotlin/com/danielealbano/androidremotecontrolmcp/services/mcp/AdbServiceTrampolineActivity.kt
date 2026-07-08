package com.danielealbano.androidremotecontrolmcp.services.mcp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.danielealbano.androidremotecontrolmcp.services.pulselink.HapticMiddlewareService

/**
 * Invisible trampoline [Activity] that starts or stops [McpServerService] from ADB
 * when the app is in the background.
 *
 * On Android 12+ (API 31+), apps cannot call [android.content.Context.startForegroundService]
 * from the background. Using `am start` to launch an Activity gives the app a foreground
 * exemption, allowing it to start the foreground service. This Activity renders no UI
 * (uses `Theme.NoDisplay`), performs the service start/stop in [onCreate], and calls
 * [finish] immediately.
 *
 * For configuration-only changes (no service start/stop), use [AdbConfigReceiver] instead
 * — it works from the background without a foreground exemption.
 *
 * **Usage** (from adb):
 * ```
 * # Start the MCP server
 * adb shell am start \
 *   -n <app-id>/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbServiceTrampolineActivity \
 *   --es action start
 *
 * # Stop the MCP server
 * adb shell am start \
 *   -n <app-id>/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbServiceTrampolineActivity \
 *   --es action stop
 *
 * # Start Pulse Link middleware
 * adb shell am start \
 *   -n <app-id>/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbServiceTrampolineActivity \
 *   --es action pulse_start
 * ```
 *
 * Where `<app-id>` is:
 * - Debug: `com.danielealbano.androidremotecontrolmcp.debug`
 * - Release: `com.danielealbano.androidremotecontrolmcp`
 */
class AdbServiceTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.getStringExtra(EXTRA_ACTION)
        Log.i(TAG, "Trampoline launched with action: $action")

        when (action) {
            ACTION_VALUE_START -> {
                Log.i(TAG, "Starting McpServerService via trampoline")
                val serviceIntent =
                    Intent(this, McpServerService::class.java).apply {
                        this.action = McpServerService.ACTION_START
                    }
                startForegroundService(serviceIntent)
            }

            ACTION_VALUE_STOP -> {
                Log.i(TAG, "Stopping McpServerService via trampoline")
                val serviceIntent =
                    Intent(this, McpServerService::class.java).apply {
                        this.action = McpServerService.ACTION_STOP
                    }
                startForegroundService(serviceIntent)
            }

            ACTION_VALUE_PULSE_START -> {
                Log.i(TAG, "Starting HapticMiddlewareService via trampoline")
                val serviceIntent =
                    Intent(this, HapticMiddlewareService::class.java).apply {
                        this.action = HapticMiddlewareService.ACTION_START
                        if (intent.hasExtra(HapticMiddlewareService.EXTRA_ENABLE_INTIFACE)) {
                            putExtra(
                                HapticMiddlewareService.EXTRA_ENABLE_INTIFACE,
                                intent.getBooleanExtra(HapticMiddlewareService.EXTRA_ENABLE_INTIFACE, true),
                            )
                        }
                        if (intent.hasExtra(HapticMiddlewareService.EXTRA_ENABLE_GADGETBRIDGE)) {
                            putExtra(
                                HapticMiddlewareService.EXTRA_ENABLE_GADGETBRIDGE,
                                intent.getBooleanExtra(HapticMiddlewareService.EXTRA_ENABLE_GADGETBRIDGE, true),
                            )
                        }
                    }
                startForegroundService(serviceIntent)
            }

            ACTION_VALUE_PULSE_STOP -> {
                Log.i(TAG, "Stopping HapticMiddlewareService via trampoline")
                val serviceIntent =
                    Intent(this, HapticMiddlewareService::class.java).apply {
                        this.action = HapticMiddlewareService.ACTION_STOP
                    }
                startForegroundService(serviceIntent)
            }

            else -> {
                Log.w(TAG, "Unknown or missing action: '$action'")
            }
        }

        finish()
    }

    companion object {
        private const val TAG = "MCP:ServiceTrampoline"
        internal const val EXTRA_ACTION = "action"
        internal const val ACTION_VALUE_START = "start"
        internal const val ACTION_VALUE_STOP = "stop"
        internal const val ACTION_VALUE_PULSE_START = "pulse_start"
        internal const val ACTION_VALUE_PULSE_STOP = "pulse_stop"
    }
}
