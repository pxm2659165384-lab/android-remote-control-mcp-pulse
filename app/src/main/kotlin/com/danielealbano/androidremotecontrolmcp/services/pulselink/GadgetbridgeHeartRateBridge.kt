package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class GadgetbridgeHeartRateBridge(
    private val context: Context,
    private val collector: BiometricCollector,
    private val scope: CoroutineScope,
) {
    private var registered = false
    private var pollJob: Job? = null

    fun start() {
        tryStartBroadcastReceiver()
        pollJob = scope.launch { pollExportedDatabase() }
    }

    fun stop() {
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
        pollJob?.cancel()
        pollJob = null
    }

    private fun tryStartBroadcastReceiver() {
        runCatching {
            val filter =
                IntentFilter().apply {
                    CANDIDATE_ACTIONS.forEach { addAction(it) }
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            registered = true
            PulseLogger.i("Gadgetbridge broadcast probe registered")
        }.onFailure {
            PulseLogger.w("Gadgetbridge broadcast probe unavailable: ${it.message}")
        }
    }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                val bpm =
                    HEART_RATE_EXTRA_KEYS
                        .firstNotNullOfOrNull { key -> intent?.readIntExtra(key) }
                        ?: return
                scope.launch { collector.pushHeartRate(bpm) }
                PulseLogger.i("Gadgetbridge broadcast heart rate: $bpm BPM")
            }
        }

    private suspend fun pollExportedDatabase() {
        var lastTimestamp = 0L
        while (scope.isActive) {
            runCatching {
                readLatestHeartRate()?.let { sample ->
                    if (sample.timestamp > lastTimestamp) {
                        lastTimestamp = sample.timestamp
                        collector.pushHeartRate(sample.bpm)
                        PulseLogger.d("Gadgetbridge DB heart rate: ${sample.bpm} BPM")
                    }
                }
            }.onFailure {
                PulseLogger.d("Gadgetbridge DB poll skipped: ${it.message}")
            }
            delay(DB_POLL_INTERVAL_MS)
        }
    }

    private fun readLatestHeartRate(): HeartRateSample? {
        val path =
            possibleDbPaths().firstOrNull { candidate ->
                File(candidate).let { file -> file.exists() && file.length() > 0 }
            } ?: return null
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            readLatestHuawei(db) ?: readLatestXiaomi(db)
        } finally {
            db.close()
        }
    }

    private fun readLatestHuawei(db: SQLiteDatabase): HeartRateSample? =
        runCatching {
            db.query(
                "HUAWEI_ACTIVITY_SAMPLE",
                arrayOf("TIMESTAMP", "HEART_RATE"),
                "TIMESTAMP <= OTHER_TIMESTAMP AND HEART_RATE > 0",
                null,
                null,
                null,
                "TIMESTAMP DESC",
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    HeartRateSample(timestamp = cursor.getLong(0) * 1000L, bpm = cursor.getInt(1))
                } else {
                    null
                }
            }
        }.getOrNull()

    private fun readLatestXiaomi(db: SQLiteDatabase): HeartRateSample? =
        runCatching {
            db.query(
                "XIAOMI_ACTIVITY_SAMPLE",
                arrayOf("TIMESTAMP", "HEART_RATE"),
                "HEART_RATE IS NOT NULL AND HEART_RATE > 0",
                null,
                null,
                null,
                "TIMESTAMP DESC",
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    HeartRateSample(timestamp = cursor.getLong(0), bpm = cursor.getInt(1))
                } else {
                    null
                }
            }
        }.getOrNull()

    private fun possibleDbPaths(): List<String> {
        val root = Environment.getExternalStorageDirectory()
        val basePaths =
            listOf(
                File(root, "Download/Gadgetbridge.db").absolutePath,
                File(root, "Download/Gadgetbridge/Gadgetbridge.db").absolutePath,
                File(root, "Download/手环/Gadgetbridge.db").absolutePath,
                File(root, "Documents/Gadgetbridge/Gadgetbridge.db").absolutePath,
                File(root, "下载/手环/Gadgetbridge.db").absolutePath,
                "/sdcard/Download/Gadgetbridge.db",
                "/storage/emulated/0/Download/Gadgetbridge.db",
                "/sdcard/Download/Gadgetbridge/Gadgetbridge.db",
                "/storage/emulated/0/Download/Gadgetbridge/Gadgetbridge.db",
                "/sdcard/Download/手环/Gadgetbridge.db",
                "/storage/emulated/0/Download/手环/Gadgetbridge.db",
                "/sdcard/下载/手环/Gadgetbridge.db",
                "/storage/emulated/0/下载/手环/Gadgetbridge.db",
            )
        return basePaths + basePaths.map { it.removeSuffix(".db") + ".sqlite3" }
    }

    private fun Intent.readIntExtra(key: String): Int? {
        if (!hasExtra(key)) return null
        return when (val value = extras?.get(key)) {
            is Int -> value
            is Long -> value.toInt()
            is Float -> value.toInt()
            is Double -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }?.takeIf { it > 0 }
    }

    private data class HeartRateSample(
        val timestamp: Long,
        val bpm: Int,
    )

    companion object {
        private const val DB_POLL_INTERVAL_MS = 5_000L

        private val CANDIDATE_ACTIONS =
            listOf(
                "nodomain.freeyourgadget.gadgetbridge.REALTIME_SAMPLES",
                "nodomain.freeyourgadget.gadgetbridge.HEALTH_DATA",
                "nodomain.freeyourgadget.gadgetbridge.action.HEART_RATE",
            )

        private val HEART_RATE_EXTRA_KEYS =
            listOf(
                "heart_rate",
                "heartrate",
                "bpm",
                "hr",
                "value",
            )
    }
}
