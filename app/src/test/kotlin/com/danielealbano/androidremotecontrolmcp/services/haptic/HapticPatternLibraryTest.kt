package com.danielealbano.androidremotecontrolmcp.services.haptic

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * HapticPatternLibrary 单元测试
 *
 * 测试范围：
 * - 数据完整性验证（23种模式 × 5个强度）
 * - 模式名称查询（中英文）
 * - 边界条件处理
 * - Timing 数组有效性
 */
@DisplayName("触觉模式库测试")
class HapticPatternLibraryTest {

    @Nested
    @DisplayName("数据完整性测试")
    inner class DataIntegrityTests {

        @Test
        @DisplayName("应包含23种模式")
        fun shouldContain23Patterns() {
            assertEquals(23, HapticPatternLibrary.PATTERNS.size, "模式数量应为23")
        }

        @Test
        @DisplayName("每种模式应包含5个强度等级")
        fun eachPatternShouldHave5Levels() {
            HapticPatternLibrary.PATTERNS.forEach { (modeName, levels) ->
                assertEquals(5, levels.size, "$modeName 应包含5个强度等级")
            }
        }

        @Test
        @DisplayName("所有模式名称应符合命名规范")
        fun allPatternNamesShouldFollowConvention() {
            val validNames = (1..23).map { "模式$it" }.toSet()
            val actualNames = HapticPatternLibrary.PATTERNS.keys

            assertTrue(actualNames.all { it in validNames },
                "所有模式名称应为'模式1'到'模式23'")
        }

        @Test
        @DisplayName("Timing数组不应为空")
        fun timingArraysShouldNotBeEmpty() {
            HapticPatternLibrary.PATTERNS.forEach { (modeName, levels) ->
                levels.forEachIndexed { levelIndex, timing ->
                    assertTrue(timing.isNotEmpty(),
                        "$modeName 等级${levelIndex + 1} 的timing数组不应为空")
                }
            }
        }

        @Test
        @DisplayName("Timing数组长度应为偶数（停顿-震动配对）")
        fun timingArraysShouldHaveEvenLength() {
            HapticPatternLibrary.PATTERNS.forEach { (modeName, levels) ->
                levels.forEachIndexed { levelIndex, timing ->
                    if (timing.isNotEmpty()) {
                        assertTrue(timing.size % 2 == 0,
                            "$modeName 等级${levelIndex + 1} 的timing数组长度应为偶数")
                    }
                }
            }
        }

        @Test
        @DisplayName("Timing值应全部非负")
        fun timingValuesShouldBeNonNegative() {
            HapticPatternLibrary.PATTERNS.forEach { (modeName, levels) ->
                levels.forEachIndexed { levelIndex, timing ->
                    timing.forEachIndexed { index, value ->
                        assertTrue(value >= 0,
                            "$modeName 等级${levelIndex + 1} 索引$index 的值应非负，实际为$value")
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("模式查询测试")
    inner class QueryTests {

        @Test
        @DisplayName("应支持中文模式名查询")
        fun shouldSupportChineseModeName() {
            val result = HapticPatternLibrary.get("模式1", 3)
            assertNotNull(result, "应能查询到'模式1'")
        }

        @Test
        @DisplayName("应支持英文模式名查询")
        fun shouldSupportEnglishModeName() {
            val result = HapticPatternLibrary.get("mode_1", 3)
            assertNotNull(result, "应能查询到'mode_1'")
        }

        @Test
        @DisplayName("中英文模式名应返回相同数据")
        fun chineseAndEnglishNamesShouldReturnSameData() {
            for (i in 1..23) {
                val chineseResult = HapticPatternLibrary.get("模式$i", 3)
                val englishResult = HapticPatternLibrary.get("mode_$i", 3)

                assertNotNull(chineseResult)
                assertNotNull(englishResult)
                assertArrayEquals(chineseResult, englishResult,
                    "模式$i 的中英文查询应返回相同数据")
            }
        }

        @Test
        @DisplayName("不存在的模式应返回null")
        fun nonExistentPatternShouldReturnNull() {
            assertNull(HapticPatternLibrary.get("不存在的模式", 3))
            assertNull(HapticPatternLibrary.get("mode_99", 3))
        }

        @Test
        @DisplayName("应支持所有强度等级（1-5）")
        fun shouldSupportAllIntensityLevels() {
            for (level in 1..5) {
                val result = HapticPatternLibrary.get("模式1", level)
                assertNotNull(result, "应支持强度等级$level")
            }
        }

        @Test
        @DisplayName("超出范围的强度等级应被限制到有效范围")
        fun outOfRangeLevelShouldBeCoerced() {
            val level0 = HapticPatternLibrary.get("模式1", 0)
            val level1 = HapticPatternLibrary.get("模式1", 1)
            assertArrayEquals(level1, level0, "等级0应被限制为等级1")

            val level6 = HapticPatternLibrary.get("模式1", 6)
            val level5 = HapticPatternLibrary.get("模式1", 5)
            assertArrayEquals(level5, level6, "等级6应被限制为等级5")
        }
    }

    @Nested
    @DisplayName("强度等级测试")
    inner class IntensityLevelTests {

        @Test
        @DisplayName("高强度等级的总震动时间应多于低强度")
        fun higherLevelShouldHaveMoreVibrationTime() {
            // 对大部分模式验证（模式7持续震动除外）
            val testModes = listOf("模式1", "模式2", "模式3", "模式4", "模式5")

            testModes.forEach { modeName ->
                val level1 = HapticPatternLibrary.get(modeName, 1)!!
                val level5 = HapticPatternLibrary.get(modeName, 5)!!

                val vibrationTime1 = calculateVibrationTime(level1)
                val vibrationTime5 = calculateVibrationTime(level5)

                assertTrue(vibrationTime5 >= vibrationTime1,
                    "$modeName 的等级5震动时间应大于等于等级1")
            }
        }

        private fun calculateVibrationTime(timing: LongArray): Long {
            return timing.filterIndexed { index, _ -> index % 2 == 1 }.sum()
        }
    }

    @Nested
    @DisplayName("辅助方法测试")
    inner class HelperMethodTests {

        @Test
        @DisplayName("exists方法应正确判断模式是否存在")
        fun existsShouldCorrectlyCheckPatternExistence() {
            assertTrue(HapticPatternLibrary.exists("模式1"))
            assertTrue(HapticPatternLibrary.exists("mode_1"))
            assertFalse(HapticPatternLibrary.exists("不存在"))
        }

        @Test
        @DisplayName("getAllModeNames应返回所有模式名称（中英文）")
        fun getAllModeNamesShouldReturnAllNames() {
            val allNames = HapticPatternLibrary.getAllModeNames()

            // 应包含23个中文名
            for (i in 1..23) {
                assertTrue(allNames.contains("模式$i"),
                    "应包含'模式$i'")
            }

            // 应包含23个英文名
            for (i in 1..23) {
                assertTrue(allNames.contains("mode_$i"),
                    "应包含'mode_$i'")
            }

            // 总数应为46（23中文 + 23英文）
            assertEquals(46, allNames.size, "应返回46个模式名称")
        }
    }

    @Nested
    @DisplayName("特殊模式测试")
    inner class SpecialPatternTests {

        @Test
        @DisplayName("模式7（持续震动）应有特殊处理")
        fun mode7ShouldBeHandledSpecially() {
            val mode7 = HapticPatternLibrary.get("模式7", 3)
            assertNotNull(mode7)

            // 持续震动模式的timing应该较长
            val totalTime = mode7!!.sum()
            assertTrue(totalTime >= 5000,
                "模式7的总时长应较长（持续震动），实际为${totalTime}ms")
        }

        @Test
        @DisplayName("模式23应只有单一强度级别或所有等级相同")
        fun mode23ShouldHaveUniformLevels() {
            val level1 = HapticPatternLibrary.get("模式23", 1)!!
            val level5 = HapticPatternLibrary.get("模式23", 5)!!

            assertArrayEquals(level1, level5,
                "模式23的所有强度等级应相同（单一强度）")
        }
    }

    @Nested
    @DisplayName("性能测试")
    inner class PerformanceTests {

        @Test
        @DisplayName("查询操作应快速完成")
        fun queryShouldBeFast() {
            val startTime = System.nanoTime()

            // 执行1000次查询
            repeat(1000) {
                HapticPatternLibrary.get("模式1", 3)
            }

            val duration = (System.nanoTime() - startTime) / 1_000_000 // 转换为毫秒

            assertTrue(duration < 100,
                "1000次查询应在100ms内完成，实际耗时${duration}ms")
        }
    }

    @Nested
    @DisplayName("数据一致性测试")
    inner class ConsistencyTests {

        @Test
        @DisplayName("相同查询应返回相同对象引用")
        fun sameQueryShouldReturnSameReference() {
            val result1 = HapticPatternLibrary.get("模式1", 3)
            val result2 = HapticPatternLibrary.get("模式1", 3)

            assertSame(result1, result2,
                "相同的查询应返回相同的对象引用（性能优化）")
        }

        @Test
        @DisplayName("所有模式的所有等级应可正常访问")
        fun allPatternsAndLevelsShouldBeAccessible() {
            for (i in 1..23) {
                for (level in 1..5) {
                    val result = HapticPatternLibrary.get("模式$i", level)
                    assertNotNull(result,
                        "模式$i 等级$level 应可访问")
                }
            }
        }
    }
}
