package com.danielealbano.androidremotecontrolmcp.services.pulselink

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PulseMediaStartPositionTest {
    @Test
    fun `negative requested position resolves to start`() {
        assertEquals(0, PulseMediaStartPosition.resolve(-1000, 10_000))
    }

    @Test
    fun `requested position inside duration is preserved`() {
        assertEquals(12_000, PulseMediaStartPosition.resolve(12_000, 30_000))
    }

    @Test
    fun `requested position beyond duration is clamped before the end`() {
        assertEquals(29_750, PulseMediaStartPosition.resolve(60_000, 30_000))
    }

    @Test
    fun `unknown duration keeps requested position`() {
        assertEquals(45_000, PulseMediaStartPosition.resolve(45_000, 0))
    }
}
