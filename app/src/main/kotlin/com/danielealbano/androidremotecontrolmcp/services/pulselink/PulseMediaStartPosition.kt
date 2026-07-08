package com.danielealbano.androidremotecontrolmcp.services.pulselink

object PulseMediaStartPosition {
    fun resolve(
        requestedMs: Long,
        durationMs: Int,
    ): Int {
        val requested = requestedMs.coerceAtLeast(0L)
        val duration = durationMs.coerceAtLeast(0)
        val target =
            if (duration > 0) {
                requested.coerceAtMost((duration - END_GUARD_MS).coerceAtLeast(0).toLong())
            } else {
                requested
            }
        return target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private const val END_GUARD_MS = 250
}
