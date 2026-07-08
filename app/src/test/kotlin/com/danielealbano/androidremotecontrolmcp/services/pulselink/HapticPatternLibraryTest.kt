package com.danielealbano.androidremotecontrolmcp.services.pulselink

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HapticPatternLibraryTest {
    @Test
    fun `all configured modes resolve for every level`() {
        for (mode in 1..23) {
            for (level in 1..5) {
                assertNotNull(HapticPatternLibrary.get("mode_$mode", level))
            }
        }
    }

    @Test
    fun `numeric aliases resolve to the same pattern as mode names`() {
        assertArrayEquals(
            HapticPatternLibrary.get("\u6a21\u5f0f1", 3),
            HapticPatternLibrary.get("mode_1", 3),
        )
    }

    @Test
    fun `continuous mode alias is recognized`() {
        assertTrue(HapticPatternLibrary.isContinuous("mode_7"))
        assertTrue(HapticPatternLibrary.isContinuous("\u6a21\u5f0f7_\u6301\u7eed\u9707\u52a8"))
    }

    @Test
    fun `semantic prompt modes resolve to supported patterns`() {
        assertArrayEquals(HapticPatternLibrary.get("mode_1", 3), HapticPatternLibrary.get("gentle", 3))
        assertArrayEquals(HapticPatternLibrary.get("mode_3", 3), HapticPatternLibrary.get("heartbeat", 3))
        assertArrayEquals(HapticPatternLibrary.get("mode_2", 3), HapticPatternLibrary.get("pulse", 3))
        assertArrayEquals(HapticPatternLibrary.get("mode_10", 3), HapticPatternLibrary.get("wave", 3))
        assertArrayEquals(HapticPatternLibrary.get("mode_5", 3), HapticPatternLibrary.get("impact", 3))
    }
}
