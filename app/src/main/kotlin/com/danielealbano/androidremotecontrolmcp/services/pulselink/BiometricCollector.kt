package com.danielealbano.androidremotecontrolmcp.services.pulselink

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

@Serializable
data class HeartRateData(
    val bpm: Int,
    val timestamp: Long,
)

@Serializable
data class BiometricResponse(
    val success: Boolean,
    val current: Int = 0,
    val avg: Int = 0,
    val max: Int = 0,
    val stddev: Double = 0.0,
    val sampleCount: Int = 0,
    val error: String? = null,
)

class BiometricCollector {
    private val buffer = ArrayDeque<HeartRateData>(MAX_SAMPLES)
    private val bufferLock = Mutex()
    private val snapshotLock = Any()

    val isAvailable: Boolean
        get() = synchronized(snapshotLock) { buffer.isNotEmpty() }

    fun getCurrentData(): BiometricResponse {
        val latest = synchronized(snapshotLock) { buffer.lastOrNull() }
        return if (latest != null) {
            BiometricResponse(success = true, current = latest.bpm, sampleCount = 1)
        } else {
            BiometricResponse(success = false, error = "no_data")
        }
    }

    suspend fun getHistoricalData(durationSeconds: Int): BiometricResponse =
        bufferLock.withLock {
            val cutoff = System.currentTimeMillis() - durationSeconds.coerceAtLeast(1) * 1000L
            val samples = synchronized(snapshotLock) {
                buffer.filter { it.timestamp >= cutoff }
            }
            if (samples.isEmpty()) {
                BiometricResponse(success = false, error = "no_data_in_range")
            } else {
                val bpms = samples.map { it.bpm }
                BiometricResponse(
                    success = true,
                    current = samples.last().bpm,
                    avg = bpms.average().toInt(),
                    max = bpms.maxOrNull() ?: 0,
                    stddev = stddev(bpms),
                    sampleCount = samples.size,
                )
            }
        }

    suspend fun pushHeartRate(bpm: Int) {
        if (bpm !in MIN_HEART_RATE..MAX_HEART_RATE) {
            PulseLogger.w("Ignoring implausible heart rate sample: $bpm")
            return
        }
        bufferLock.withLock {
            synchronized(snapshotLock) {
                if (buffer.size >= MAX_SAMPLES) {
                    buffer.removeFirst()
                }
                buffer.addLast(HeartRateData(bpm, System.currentTimeMillis()))
            }
        }
    }

    private fun stddev(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { bpm -> (bpm - mean) * (bpm - mean) } / values.size)
    }

    private companion object {
        private const val MAX_SAMPLES = 600
        private const val MIN_HEART_RATE = 25
        private const val MAX_HEART_RATE = 240
    }
}
