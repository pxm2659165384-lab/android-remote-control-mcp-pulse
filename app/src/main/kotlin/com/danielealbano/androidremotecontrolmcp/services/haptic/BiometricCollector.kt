package com.danielealbano.androidremotecontrolmcp.services.haptic

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 生物特征数据采集器（心率）
 *
 * 数据源：Gadgetbridge 导出的数据库文件
 * 采集策略：轮询模式，每30秒读取一次数据库文件
 *
 * 实现方案（基于 orangechat 源码分析）：
 *   1. 读取用户导出的数据库文件（推荐，无需特殊权限）
 *      路径：/sdcard/Download/手环/Gadgetbridge.db
 *   2. Root访问内部数据库（备选，需要Root）
 *      路径：/data/data/nodomain.freeyourgadget.gadgetbridge/databases/Gadgetbridge.db
 *
 * 数据存储：
 * - 内存环形缓冲区，最多保存 600 个样本（约 10 分钟）
 * - 支持实时查询和历史统计
 *
 * 厂商兼容：
 * - 小米手环：XIAOMI_ACTIVITY_SAMPLE 表
 * - 华为手环：HUAWEI_ACTIVITY_SAMPLE 表（需过滤占位行）
 */
class BiometricCollector(private val context: Context) {

    private val biometricBuffer = ArrayDeque<HeartRateData>(600)
    private val collectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var manufacturer: Manufacturer = Manufacturer.UNKNOWN

    /**
     * 心率数据点
     */
    data class HeartRateData(val timestamp: Long, val bpm: Int)

    /**
     * 设备厂商
     */
    private enum class Manufacturer {
        XIAOMI,
        HUAWEI,
        UNKNOWN
    }

    /**
     * 可能的数据库文件路径（按优先级排序）
     */
    private val possibleDbPaths = listOf(
        // 用户导出路径（推荐）
        File(Environment.getExternalStorageDirectory(), "Download/手环/Gadgetbridge.db").absolutePath,
        "/sdcard/Download/手环/Gadgetbridge.db",
        "/storage/emulated/0/Download/手环/Gadgetbridge.db",
        "/sdcard/下载/手环/Gadgetbridge.db",
        "/storage/emulated/0/下载/手环/Gadgetbridge.db",
        // SQLite3 变体
        File(Environment.getExternalStorageDirectory(), "Download/手环/Gadgetbridge.sqlite3").absolutePath,
        "/sdcard/Download/手环/Gadgetbridge.sqlite3",
        // Root访问路径（备选）
        "/data/data/nodomain.freeyourgadget.gadgetbridge/databases/Gadgetbridge.db"
    )

    private var cachedDbPath: String? = null

    /**
     * 启动数据采集（轮询模式）
     */
    fun start() {
        pollingJob = collectorScope.launch {
            while (isActive) {
                try {
                    pollHeartRateData()
                } catch (e: Exception) {
                    Log.e(TAG, "Heart rate polling error: ${e.message}")
                }
                delay(30000) // 每30秒轮询一次
            }
        }
        Log.i(TAG, "BiometricCollector started (polling every 30s)")
    }

    /**
     * 停止数据采集
     */
    fun stop() {
        pollingJob?.cancel()
        collectorScope.cancel()
        Log.i(TAG, "BiometricCollector stopped")
    }

    /**
     * 轮询心率数据
     */
    private fun pollHeartRateData() {
        val dbPath = findDbPath() ?: run {
            Log.d(TAG, "Gadgetbridge database not found. User may need to export it.")
            return
        }

        try {
            val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                // 首次检测厂商
                if (manufacturer == Manufacturer.UNKNOWN) {
                    manufacturer = detectManufacturer(db)
                    Log.i(TAG, "Detected manufacturer: $manufacturer")
                }

                // 根据厂商查询心率数据
                val heartRate = when (manufacturer) {
                    Manufacturer.XIAOMI -> queryXiaomiHeartRate(db)
                    Manufacturer.HUAWEI -> queryHuaweiHeartRate(db)
                    Manufacturer.UNKNOWN -> null
                }

                heartRate?.let { bpm ->
                    addHeartRateData(bpm)
                }
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query heart rate from $dbPath: ${e.message}")
        }
    }

    /**
     * 查找可用的数据库文件路径
     */
    private fun findDbPath(): String? {
        // 如果有缓存路径且文件仍存在，直接返回
        cachedDbPath?.let { cached ->
            if (File(cached).exists() && File(cached).length() > 0) {
                return cached
            }
            cachedDbPath = null
        }

        // 遍历所有可能的路径
        for (path in possibleDbPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "Found Gadgetbridge database: $path")
                    cachedDbPath = path
                    return path
                }
            } catch (e: Exception) {
                // 忽略无法访问的路径
            }
        }

        return null
    }

    /**
     * 检测手环厂商
     */
    private fun detectManufacturer(db: SQLiteDatabase): Manufacturer {
        return try {
            val cursor = db.query(
                "DEVICE",
                arrayOf("MANUFACTURER"),
                null, null, null, null,
                "_id DESC", "1"
            )
            cursor.use {
                if (!it.moveToFirst()) {
                    Manufacturer.XIAOMI // 默认小米
                } else {
                    val mfr = it.getString(0)?.lowercase()?.trim().orEmpty()
                    when {
                        mfr.contains("huawei") -> Manufacturer.HUAWEI
                        else -> Manufacturer.XIAOMI
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect manufacturer, defaulting to Xiaomi", e)
            Manufacturer.XIAOMI
        }
    }

    /**
     * 查询小米手环心率（最新值）
     */
    private fun queryXiaomiHeartRate(db: SQLiteDatabase): Int? {
        return try {
            val cursor = db.query(
                "XIAOMI_ACTIVITY_SAMPLE",
                arrayOf("HEART_RATE"),
                "HEART_RATE > 0",
                null, null, null,
                "TIMESTAMP DESC", "1"
            )
            cursor.use {
                if (it.moveToFirst() && !it.isNull(0)) {
                    it.getInt(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query Xiaomi heart rate", e)
            null
        }
    }

    /**
     * 查询华为手环心率（最新值，需过滤占位行）
     */
    private fun queryHuaweiHeartRate(db: SQLiteDatabase): Int? {
        return try {
            val cursor = db.query(
                "HUAWEI_ACTIVITY_SAMPLE",
                arrayOf("HEART_RATE"),
                "TIMESTAMP <= OTHER_TIMESTAMP AND HEART_RATE > 0",
                null, null, null,
                "TIMESTAMP DESC", "1"
            )
            cursor.use {
                if (it.moveToFirst() && !it.isNull(0)) {
                    it.getInt(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query Huawei heart rate", e)
            null
        }
    }

    /**
     * 添加心率数据点到缓冲区
     */
    private fun addHeartRateData(bpm: Int) {
        synchronized(biometricBuffer) {
            biometricBuffer.addLast(HeartRateData(System.currentTimeMillis(), bpm))
            // 保持最多 600 个样本（约 10 分钟）
            while (biometricBuffer.size > 600) {
                biometricBuffer.removeFirst()
            }
        }
        Log.d(TAG, "Heart rate updated: $bpm bpm")
    }

    /**
     * 检查是否有数据可用
     */
    fun isAvailable(): Boolean {
        synchronized(biometricBuffer) {
            return biometricBuffer.isNotEmpty()
        }
    }

    /**
     * 获取当前最新心率（无 duration 参数）
     */
    fun getCurrentData(): BiometricResponse {
        synchronized(biometricBuffer) {
            val latest = biometricBuffer.lastOrNull()
                ?: return BiometricResponse(success = false, error = "No HR data available")

            return BiometricResponse(
                success = true,
                current = latest.bpm,
                avg = latest.bpm,
                max = latest.bpm,
                stddev = 0.0,
                sampleCount = 1
            )
        }
    }

    /**
     * 获取近 duration 秒内的心率统计
     */
    fun getHistoricalData(duration: Int): BiometricResponse {
        synchronized(biometricBuffer) {
            val since = System.currentTimeMillis() - duration * 1000L
            val samples = biometricBuffer.filter { it.timestamp >= since }.map { it.bpm }

            if (samples.isEmpty()) {
                return BiometricResponse(success = false, error = "No HR data in last ${duration}s")
            }

            val avg = samples.average().toInt()
            val max = samples.max()
            val stdDev = calcStdDev(samples)

            return BiometricResponse(
                success = true,
                current = biometricBuffer.lastOrNull()?.bpm ?: 0,
                avg = avg,
                max = max,
                stddev = stdDev,
                sampleCount = samples.size
            )
        }
    }

    /**
     * 计算标准差
     */
    private fun calcStdDev(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean).pow(2) } / values.size
        return sqrt(variance)
    }

    companion object {
        private const val TAG = "BiometricCollector"
    }
}

/**
 * 生物特征数据响应（序列化支持，用于 Ktor JSON 响应）
 */
@Serializable
data class BiometricResponse(
    val success: Boolean,
    val current: Int = 0,
    val avg: Int = 0,
    val max: Int = 0,
    val stddev: Double = 0.0,
    val sampleCount: Int = 0,
    val error: String? = null
)
