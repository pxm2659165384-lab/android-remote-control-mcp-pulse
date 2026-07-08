package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

data class HapticModeDefinition(
    val id: String,
    val label: String,
    val builtIn: Boolean = true,
    val deprecated: Boolean = false,
)

data class CustomHapticPattern(
    val id: String,
    val label: String,
    val timing: LongArray,
) {
    fun exportCode(): String = "${label.toPulseExportLabel()}|${timing.joinToString(",")}"
}

object HapticPatternLibrary {
    private const val MODE_PREFIX = "\u6a21\u5f0f"
    private val mode23 = longArrayOf(0, 2000, 30, 2000, 30, 2000, 30, 2000, 30, 0)
    private var customPatterns by mutableStateOf<List<CustomHapticPattern>>(emptyList())
    var favoriteModeIds by mutableStateOf<Set<String>>(emptySet())
        private set

    val builtInDefinitions =
        listOf(
            HapticModeDefinition("mode_1", "瀑布模式"),
            HapticModeDefinition("mode_2", "漩涡模式"),
            HapticModeDefinition("mode_3", "星点模式"),
            HapticModeDefinition("mode_4", "海浪模式"),
            HapticModeDefinition("mode_5", "闪电模式"),
            HapticModeDefinition("mode_6", "冲刺模式"),
            HapticModeDefinition("mode_7", "随机模式", deprecated = true),
            HapticModeDefinition("mode_8", "居家模式"),
            HapticModeDefinition("mode_9", "火焰模式"),
            HapticModeDefinition("mode_10", "蜂振模式"),
            HapticModeDefinition("mode_11", "云涌模式"),
            HapticModeDefinition("mode_12", "冰峰模式"),
            HapticModeDefinition("mode_13", "微风模式"),
            HapticModeDefinition("mode_14", "火山模式"),
            HapticModeDefinition("mode_15", "潮汐模式"),
            HapticModeDefinition("mode_16", "雷雨模式"),
            HapticModeDefinition("mode_17", "轻羽模式"),
            HapticModeDefinition("mode_18", "心跳模式"),
            HapticModeDefinition("mode_19", "点按模式"),
            HapticModeDefinition("mode_20", "星轨模式"),
            HapticModeDefinition("mode_21", "安眠模式"),
            HapticModeDefinition("mode_22", "旋风模式"),
            HapticModeDefinition("mode_23", "循环模式"),
        )

    val patterns: Map<String, List<LongArray>> =
        buildMap {
            putPattern(
                1,
                longArrayOf(0, 1000, 5500, 1000, 5500, 1000, 5500, 1000, 5500, 1000),
                longArrayOf(0, 1000, 2500, 1000, 2500, 1000, 2500, 1000, 2500, 1000),
                longArrayOf(0, 1000, 1500, 1000, 1500, 1000, 1500, 1000, 1500, 1000),
                longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000),
                longArrayOf(100, 1000, 100, 1000, 100, 1000, 100, 1000, 100, 1000),
            )
            putPattern(
                2,
                longArrayOf(0, 500, 1100, 500, 1100, 500, 1100, 500, 1100, 500),
                longArrayOf(0, 500, 600, 500, 600, 500, 600, 500, 600, 500),
                longArrayOf(0, 500, 100, 500, 100, 500, 100, 500, 100, 500),
                longArrayOf(0, 500, 10, 500, 10, 500, 10, 500, 10, 500),
                longArrayOf(0, 500, 3, 500, 3, 500, 3, 500, 3, 500),
            )
            putPattern(
                3,
                longArrayOf(0, 120, 600, 120, 600, 120, 600, 120, 600, 120),
                longArrayOf(0, 120, 400, 120, 400, 120, 400, 120, 400, 120),
                longArrayOf(0, 120, 320, 120, 320, 120, 320, 120, 320, 120),
                longArrayOf(0, 120, 200, 120, 200, 120, 200, 120, 200, 120),
                longArrayOf(0, 120, 80, 120, 80, 120, 80, 120, 80, 120),
            )
            putPattern(
                4,
                longArrayOf(0, 100, 500, 300, 600, 500, 700, 700, 900, 900),
                longArrayOf(0, 100, 400, 300, 500, 500, 600, 700, 700, 900),
                longArrayOf(0, 100, 300, 300, 400, 500, 500, 700, 600, 900),
                longArrayOf(0, 100, 200, 300, 300, 500, 400, 700, 500, 900),
                longArrayOf(0, 100, 100, 300, 200, 500, 300, 700, 400, 900),
            )
            putPattern(
                5,
                longArrayOf(0, 300, 1200, 200, 1000, 100, 800, 1200, 400, 300),
                longArrayOf(0, 300, 1100, 200, 900, 100, 700, 1200, 300, 300),
                longArrayOf(0, 300, 900, 200, 700, 100, 500, 1200, 200, 300),
                longArrayOf(0, 300, 700, 200, 500, 100, 300, 1200, 200, 300),
                longArrayOf(0, 300, 500, 200, 300, 100, 100, 1200, 50, 300),
            )
            putPattern(
                6,
                longArrayOf(0, 10, 900, 80, 700, 60, 500, 40, 300, 20),
                longArrayOf(0, 10, 850, 80, 650, 60, 450, 40, 250, 20),
                longArrayOf(0, 10, 800, 80, 600, 60, 400, 40, 200, 20),
                longArrayOf(0, 10, 750, 80, 550, 60, 350, 40, 150, 20),
                longArrayOf(0, 10, 700, 80, 500, 60, 300, 40, 100, 20),
            )
            putPattern(
                7,
                longArrayOf(0, 4000),
                longArrayOf(0, 4000),
                longArrayOf(0, 4000),
                longArrayOf(0, 4000),
                longArrayOf(0, 4000),
            )
            putPattern(
                8,
                longArrayOf(0, 500, 600, 500, 500, 500, 400, 500, 600, 800),
                longArrayOf(0, 500, 500, 500, 400, 500, 300, 500, 500, 800),
                longArrayOf(0, 500, 400, 500, 300, 500, 200, 500, 400, 800),
                longArrayOf(0, 500, 300, 500, 200, 500, 100, 500, 300, 800),
                longArrayOf(0, 500, 200, 500, 100, 500, 50, 500, 200, 800),
            )
            putPattern(
                9,
                longArrayOf(0, 100, 200, 100, 200, 100, 200, 100, 1),
                longArrayOf(0, 100, 180, 100, 180, 100, 180, 100, 1),
                longArrayOf(0, 100, 160, 100, 160, 100, 160, 100, 1),
                longArrayOf(0, 100, 140, 100, 140, 100, 140, 100, 1),
                longArrayOf(0, 100, 120, 100, 120, 100, 120, 100, 1),
            )
            putPattern(
                10,
                longArrayOf(0, 600, 800, 600, 800, 600, 800, 600, 800, 600),
                longArrayOf(0, 600, 700, 600, 700, 600, 700, 600, 700, 600),
                longArrayOf(0, 600, 600, 600, 600, 600, 600, 600, 600, 600),
                longArrayOf(0, 600, 500, 600, 500, 600, 500, 600, 500, 600),
                longArrayOf(0, 600, 300, 600, 300, 600, 300, 600, 300, 600),
            )
            putPattern(
                11,
                longArrayOf(0, 100, 250, 100, 250, 100, 250, 100, 250, 100),
                longArrayOf(0, 100, 230, 100, 230, 100, 230, 100, 230, 100),
                longArrayOf(0, 100, 200, 100, 200, 100, 200, 100, 200, 100),
                longArrayOf(0, 100, 180, 100, 180, 100, 180, 100, 180, 100),
                longArrayOf(0, 100, 150, 100, 150, 100, 150, 100, 150, 100),
            )
            putPattern(
                12,
                longArrayOf(0, 1200, 1200, 120, 1000, 1200, 800, 1200, 600, 1200),
                longArrayOf(0, 1200, 1100, 120, 900, 1200, 700, 1200, 500, 1200),
                longArrayOf(0, 1200, 1000, 120, 800, 1200, 600, 1200, 400, 1200),
                longArrayOf(0, 1200, 900, 120, 700, 1200, 500, 1200, 300, 1200),
                longArrayOf(0, 1200, 700, 120, 500, 1200, 300, 1200, 100, 1200),
            )
            putPattern(
                13,
                longArrayOf(0, 300, 1550, 300, 1550, 300, 1550, 300, 1550, 300),
                longArrayOf(0, 300, 1050, 300, 1050, 300, 1050, 300, 1050, 300),
                longArrayOf(0, 300, 550, 300, 550, 300, 550, 300, 550, 300),
                longArrayOf(0, 300, 10, 300, 10, 300, 10, 300, 10, 300),
                longArrayOf(0, 300, 1, 300, 1, 300, 1, 300, 1, 300),
            )
            putPattern(
                14,
                longArrayOf(0, 500, 1380, 500, 1380, 200, 1380, 60, 1380, 200),
                longArrayOf(0, 500, 1230, 500, 1230, 200, 1230, 60, 1230, 200),
                longArrayOf(0, 500, 780, 500, 780, 200, 780, 60, 780, 200),
                longArrayOf(0, 500, 30, 500, 30, 200, 30, 60, 30, 200),
                longArrayOf(0, 500, 3, 500, 3, 200, 3, 60, 3, 200),
            )
            putPattern(
                15,
                longArrayOf(0, 50, 1230, 70, 1230, 90, 1230, 110, 1230, 130),
                longArrayOf(0, 50, 630, 70, 630, 90, 630, 110, 630, 130),
                longArrayOf(0, 50, 330, 70, 330, 90, 330, 110, 330, 130),
                longArrayOf(0, 50, 30, 70, 30, 90, 30, 110, 30, 130),
                longArrayOf(0, 50, 1, 70, 1, 90, 1, 110, 1, 130),
            )
            putPattern(
                16,
                longArrayOf(0, 200, 2500, 200, 2500, 200, 2500, 200, 2500, 200),
                longArrayOf(0, 200, 1750, 200, 1750, 200, 1750, 200, 1750, 200),
                longArrayOf(0, 200, 1000, 200, 1000, 200, 1000, 200, 1000, 200),
                longArrayOf(0, 200, 250, 200, 250, 200, 250, 200, 250, 200),
                longArrayOf(0, 200, 30, 200, 30, 200, 30, 200, 30, 200),
            )
            putPattern(
                17,
                longArrayOf(0, 100, 1000, 100, 1000, 100, 1000),
                longArrayOf(0, 100, 900, 100, 900, 100, 900),
                longArrayOf(0, 100, 800, 100, 800, 100, 800),
                longArrayOf(0, 100, 700, 100, 700, 100, 700),
                longArrayOf(0, 100, 600, 100, 600, 100, 600),
            )
            putPattern(
                18,
                longArrayOf(0, 150, 1850, 150, 1850, 150, 1850, 150, 1850, 150),
                longArrayOf(0, 150, 1350, 150, 1350, 150, 1350, 150, 1350, 150),
                longArrayOf(0, 150, 850, 150, 850, 100, 850, 150, 850, 150),
                longArrayOf(0, 150, 600, 150, 600, 100, 600, 150, 600, 150),
                longArrayOf(0, 150, 100, 150, 100, 150, 100, 150, 100, 150),
            )
            putPattern(
                19,
                longArrayOf(0, 150, 3500, 150, 3500, 150, 3500, 150, 3500, 150),
                longArrayOf(0, 150, 1300, 150, 1300, 150, 1300, 150, 1300, 150),
                longArrayOf(0, 150, 700, 150, 700, 150, 700, 150, 700, 150),
                longArrayOf(0, 150, 400, 150, 400, 150, 400, 150, 400, 150),
                longArrayOf(0, 150, 100, 150, 100, 150, 100, 150, 100, 150),
            )
            putPattern(
                20,
                longArrayOf(0, 200, 510, 200, 510, 100, 510, 200, 510, 500),
                longArrayOf(0, 200, 360, 200, 360, 100, 360, 200, 360, 500),
                longArrayOf(0, 200, 210, 200, 210, 100, 210, 200, 210, 500),
                longArrayOf(0, 200, 10, 200, 10, 100, 10, 200, 10, 500),
                longArrayOf(0, 200, 1, 200, 1, 100, 1, 200, 1, 500),
            )
            putPattern(
                21,
                longArrayOf(0, 1000, 3600, 800, 2800, 600, 2000, 400, 1200, 200),
                longArrayOf(0, 1000, 1800, 800, 1400, 600, 1000, 400, 600, 200),
                longArrayOf(0, 1000, 900, 800, 700, 600, 500, 400, 300, 200),
                longArrayOf(0, 1000, 450, 800, 350, 600, 250, 400, 150, 200),
                longArrayOf(0, 1000, 180, 800, 140, 600, 100, 400, 60, 200),
            )
            putPattern(
                22,
                longArrayOf(0, 50, 930, 90, 930, 120, 930, 150, 930, 200),
                longArrayOf(0, 50, 480, 90, 480, 120, 480, 150, 480, 200),
                longArrayOf(0, 50, 330, 90, 330, 120, 330, 150, 330, 200),
                longArrayOf(0, 50, 30, 90, 30, 120, 30, 150, 30, 200),
                longArrayOf(0, 50, 3, 90, 3, 120, 3, 150, 3, 200),
            )
            put("${MODE_PREFIX}23", listOf(mode23, mode23, mode23, mode23, mode23))
        }

    private val aliases =
        buildMap {
            for (index in 1..23) {
                val mode = "$MODE_PREFIX$index"
                val id = "mode_$index"
                val label = builtInDefinitions.firstOrNull { it.id == id }?.label
                put(id, mode)
                put("mode$index", mode)
                put(mode, mode)
                put("模式$index", mode)
                if (!label.isNullOrBlank()) {
                    put(label, mode)
                    put(label.removeSuffix("模式"), mode)
                }
            }
            put("gentle", "${MODE_PREFIX}1")
            put("heartbeat", "${MODE_PREFIX}3")
            put("pulse", "${MODE_PREFIX}2")
            put("wave", "${MODE_PREFIX}10")
            put("impact", "${MODE_PREFIX}5")
            put("${MODE_PREFIX}7_\u6301\u7eed\u9707\u52a8", "${MODE_PREFIX}7")
        }

    fun get(
        modeName: String,
        level: Int,
    ): LongArray? {
        customPattern(modeName)?.let { return it.timing.copyOf() }
        val key = canonical(modeName)
        val levelIndex = (level - 1).coerceIn(0, 4)
        return patterns[key]?.getOrNull(levelIndex)?.copyOf()
    }

    fun hasMode(modeName: String): Boolean = customPattern(modeName) != null || patterns.containsKey(canonical(modeName))

    fun isContinuous(modeName: String): Boolean = canonical(modeName) == "${MODE_PREFIX}7"

    fun loadUserModes(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        customPatterns =
            prefs.getString(KEY_CUSTOM_PATTERNS, "")
                .orEmpty()
                .lineSequence()
                .mapNotNull { decodeCustomPattern(it) }
                .toList()
        favoriteModeIds =
            prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
                .filter { id -> builtInDefinitions.any { it.id == id } || customPatterns.any { it.id == id } }
                .toSet()
    }

    fun modeDefinitions(favoritesFirst: Boolean = true): List<HapticModeDefinition> {
        val definitions =
            builtInDefinitions +
                customPatterns.map { pattern ->
                    HapticModeDefinition(id = pattern.id, label = pattern.label, builtIn = false)
                }
        if (!favoritesFirst) return definitions
        return definitions.sortedWith(
            compareByDescending<HapticModeDefinition> { favoriteModeIds.contains(it.id) }
                .thenBy { if (it.builtIn) 0 else 1 }
                .thenBy { it.id.modeSortIndex() },
        )
    }

    fun labelOf(modeName: String): String {
        val trimmed = modeName.trim()
        customPattern(trimmed)?.let { return it.label }
        val id = idOf(trimmed)
        return builtInDefinitions.firstOrNull { it.id == id }?.label ?: trimmed
    }

    fun idOf(modeName: String): String {
        val trimmed = modeName.trim()
        customPattern(trimmed)?.let { return it.id }
        val canonical = canonical(trimmed)
        val builtInIndex = Regex("""\d+""").find(canonical)?.value?.toIntOrNull()
        return if (builtInIndex != null) "mode_$builtInIndex" else trimmed
    }

    fun isCustomMode(modeName: String): Boolean = customPattern(modeName) != null

    fun isFavorite(modeName: String): Boolean = favoriteModeIds.contains(idOf(modeName))

    fun toggleFavorite(
        context: Context,
        modeName: String,
    ) {
        val id = idOf(modeName)
        favoriteModeIds =
            if (favoriteModeIds.contains(id)) {
                favoriteModeIds - id
            } else {
                favoriteModeIds + id
            }
        saveFavorites(context)
    }

    fun saveCustomPattern(
        context: Context,
        label: String,
        rawCurve: String,
    ): CustomHapticPattern {
        val parsed = parseSharedCurve(rawCurve)
        val importedLabel = parsed.first ?: label
        val timing = parsed.second ?: throw IllegalArgumentException("invalid_curve")
        val safeLabel = importedLabel.ifBlank { "自定义曲线 ${customPatterns.size + 1}" }.take(24)
        val mode =
            CustomHapticPattern(
                id = "custom_${System.currentTimeMillis()}",
                label = safeLabel,
                timing = timing,
            )
        customPatterns = (customPatterns + mode).takeLast(MAX_CUSTOM_PATTERNS)
        saveCustomPatterns(context)
        return mode
    }

    fun deleteCustomPattern(
        context: Context,
        modeName: String,
    ): Boolean {
        val id = idOf(modeName)
        val next = customPatterns.filterNot { it.id == id }
        if (next.size == customPatterns.size) return false
        customPatterns = next
        favoriteModeIds = favoriteModeIds - id
        saveCustomPatterns(context)
        saveFavorites(context)
        return true
    }

    fun exportCode(
        modeName: String,
        level: Int,
    ): String? {
        customPattern(modeName)?.let { return it.exportCode() }
        val timing = get(modeName, level) ?: return null
        return "${labelOf(modeName).toPulseExportLabel()}|${timing.joinToString(",")}"
    }

    fun patternDuration(
        modeName: String,
        level: Int,
    ): Long = get(modeName, level)?.sum()?.coerceAtLeast(400L) ?: 600L

    fun previewPoints(
        modeName: String,
        level: Int,
    ): List<Float> {
        val timing = get(modeName, level) ?: return emptyList()
        val max = timing.maxOrNull()?.coerceAtLeast(1L)?.toFloat() ?: 1f
        return timing.mapIndexed { index, value ->
            if (index % 2 == 0) 0f else (value / max).coerceIn(0f, 1f)
        }
    }

    private fun canonical(modeName: String): String = aliases[modeName.trim()] ?: modeName.trim()

    private fun MutableMap<String, List<LongArray>>.putPattern(
        index: Int,
        vararg levels: LongArray,
    ) {
        put("$MODE_PREFIX$index", levels.toList())
    }

    private fun customPattern(modeName: String): CustomHapticPattern? {
        val trimmed = modeName.trim()
        return customPatterns.firstOrNull {
            it.id == trimmed || it.label == trimmed || it.label.removeSuffix("模式") == trimmed
        }
    }

    private fun parseSharedCurve(raw: String): Pair<String?, LongArray?> {
        val source = raw.trim()
        if (source.isBlank()) return null to null
        val label =
            source.substringBefore("|", missingDelimiterValue = "")
                .takeIf { it.isNotBlank() && it.any { ch -> !ch.isDigit() && ch != ',' && !ch.isWhitespace() } }
        val numberPart = if (label != null && source.contains("|")) source.substringAfter("|") else source
        val values =
            Regex("""\d+""")
                .findAll(numberPart)
                .mapNotNull { it.value.toLongOrNull() }
                .map { it.coerceIn(0L, MAX_SEGMENT_MS) }
                .take(MAX_SEGMENTS)
                .toList()
        if (values.size < 2) return label to null
        val normalized =
            if (values.first() == 0L) {
                values
            } else {
                listOf(0L) + values
            }
        return label to normalized.toLongArray()
    }

    private fun saveCustomPatterns(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_PATTERNS, customPatterns.joinToString("\n") { encodeCustomPattern(it) })
            .apply()
    }

    private fun saveFavorites(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_FAVORITES, favoriteModeIds)
            .apply()
    }

    private fun encodeCustomPattern(pattern: CustomHapticPattern): String =
        listOf(
            pattern.id,
            URLEncoder.encode(pattern.label, StandardCharsets.UTF_8.name()),
            pattern.timing.joinToString(","),
        ).joinToString("|")

    private fun decodeCustomPattern(line: String): CustomHapticPattern? {
        val parts = line.split("|", limit = 3)
        if (parts.size != 3) return null
        val timing = parseSharedCurve(parts[2]).second ?: return null
        return CustomHapticPattern(
            id = parts[0],
            label = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name()),
            timing = timing,
        )
    }

    private fun String.modeSortIndex(): Int {
        val numeric = Regex("""\d+""").find(this)?.value?.toIntOrNull()
        if (numeric != null) return numeric
        return 10_000 + abs(hashCode() % 1_000)
    }

    private const val PREFS_NAME = "pulse_link_haptic_modes"
    private const val KEY_CUSTOM_PATTERNS = "custom_patterns"
    private const val KEY_FAVORITES = "favorite_mode_ids"
    private const val MAX_CUSTOM_PATTERNS = 32
    private const val MAX_SEGMENTS = 64
    private const val MAX_SEGMENT_MS = 60_000L
}

private fun String.toPulseExportLabel(): String =
    trim()
        .replace("|", " ")
        .replace(Regex("""\s+"""), " ")
        .ifBlank { "自定义曲线" }
        .take(24)
