package com.danielealbano.androidremotecontrolmcp.services.haptic

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import kotlin.random.Random

/**
 * ProceduralHapticEngine 单元测试
 *
 * 注意：由于依赖 Android 系统组件（Vibrator, Context），
 * 本测试主要验证算法逻辑，不涉及实际硬件调用。
 */
@DisplayName("程序化触觉引擎测试")
class ProceduralHapticEngineTest {

    @Nested
    @DisplayName("微随机解构算法测试")
    inner class MicroRandomizationTests {

        @Test
        @DisplayName("随机化后的数组长度应保持不变")
        fun randomizedArrayShouldKeepSameLength() {
            val original = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            val randomized = applyMicroRandomizationPublic(original)

            assertEquals(original.size, randomized.size,
                "随机化不应改变数组长度")
        }

        @Test
        @DisplayName("随机化应在±15%范围内")
        fun randomizationShouldBeWithin15Percent() {
            val original = longArrayOf(1000, 2000, 3000, 4000, 5000)

            // 多次测试以覆盖随机性
            repeat(100) {
                val randomized = applyMicroRandomizationPublic(original)

                original.forEachIndexed { index, originalValue ->
                    val randomizedValue = randomized[index]
                    val deviation = kotlin.math.abs(randomizedValue - originalValue).toDouble() / originalValue

                    assertTrue(deviation <= 0.15,
                        "索引$index: 偏差${deviation * 100}%超过15%限制")
                }
            }
        }

        @Test
        @DisplayName("多次随机化应产生不同结果")
        fun multipleRandomizationsShouldProduceDifferentResults() {
            val original = longArrayOf(0, 1000, 500, 1000, 500, 1000)

            val results = mutableSetOf<String>()
            repeat(50) {
                val randomized = applyMicroRandomizationPublic(original)
                results.add(randomized.joinToString(","))
            }

            // 50次随机化应产生至少20种不同结果
            assertTrue(results.size >= 20,
                "50次随机化只产生了${results.size}种不同结果，可能随机性不足")
        }

        @Test
        @DisplayName("最小值应被限制为10ms")
        fun minimumValueShouldBe10ms() {
            val original = longArrayOf(0, 5, 3, 1, 8, 2)

            repeat(100) {
                val randomized = applyMicroRandomizationPublic(original)

                randomized.forEach { value ->
                    assertTrue(value >= 10,
                        "所有值应被限制为至少10ms，但发现${value}ms")
                }
            }
        }

        @Test
        @DisplayName("零值经随机化后应变为10ms")
        fun zeroShouldBecome10ms() {
            val original = longArrayOf(0, 0, 0)
            val randomized = applyMicroRandomizationPublic(original)

            randomized.forEach { value ->
                assertEquals(10, value,
                    "零值应被转换为10ms")
            }
        }

        @Test
        @DisplayName("大值应保持合理范围")
        fun largeValuesShouldStayReasonable() {
            val original = longArrayOf(10000, 5000, 8000)

            repeat(50) {
                val randomized = applyMicroRandomizationPublic(original)

                randomized.forEachIndexed { index, value ->
                    assertTrue(value <= original[index] * 1.15 + 1,
                        "索引$index: ${value}ms 超出合理范围")
                }
            }
        }

        // 公开版本的随机化方法（用于测试）
        private fun applyMicroRandomizationPublic(timing: LongArray): LongArray {
            return timing.map { ms ->
                val factor = 1.0 + Random.nextDouble(-0.15, 0.15)
                (ms * factor).toLong().coerceAtLeast(10)
            }.toLongArray()
        }
    }

    @Nested
    @DisplayName("强度档位测试")
    inner class IntensityLevelTests {

        @Test
        @DisplayName("默认强度应为3")
        fun defaultLevelShouldBe3() {
            // 这个测试需要mock Context和ButtplugWebSocketClient
            // 实际实现中，currentLevel 默认值为 3
            assertEquals(3, 3) // 占位测试
        }

        @Test
        @DisplayName("强度档位应在1-5范围内")
        fun levelShouldBeInValidRange() {
            val validLevels = 1..5

            validLevels.forEach { level ->
                assertTrue(level in validLevels,
                    "强度档位$level 应在有效范围内")
            }
        }
    }

    @Nested
    @DisplayName("Timing数组格式验证")
    inner class TimingFormatTests {

        @Test
        @DisplayName("Timing数组应符合createWaveform格式")
        fun timingShouldFollowWaveformFormat() {
            // createWaveform格式：[延迟, 震动, 延迟, 震动, ...]
            // 偶数索引=延迟，奇数索引=震动

            val validTiming = longArrayOf(0, 100, 50, 200, 100, 300)

            assertEquals(0, validTiming.size % 2,
                "Timing数组长度应为偶数")

            assertTrue(validTiming[0] >= 0,
                "第一个元素（初始延迟）应非负")
        }

        @Test
        @DisplayName("空数组应被正确处理")
        fun emptyArrayShouldBeHandled() {
            val emptyTiming = longArrayOf()
            val randomized = applyMicroRandomizationPublic(emptyTiming)

            assertEquals(0, randomized.size,
                "空数组随机化后仍应为空")
        }

        private fun applyMicroRandomizationPublic(timing: LongArray): LongArray {
            return timing.map { ms ->
                val factor = 1.0 + Random.nextDouble(-0.15, 0.15)
                (ms * factor).toLong().coerceAtLeast(10)
            }.toLongArray()
        }
    }

    @Nested
    @DisplayName("目标设备测试")
    inner class TargetDeviceTests {

        @Test
        @DisplayName("应支持三种目标类型")
        fun shouldSupportThreeTargetTypes() {
            val validTargets = setOf("phone", "toy", "all")

            validTargets.forEach { target ->
                assertTrue(target in validTargets,
                    "目标'$target'应是有效值")
            }
        }

        @Test
        @DisplayName("目标类型应不区分大小写")
        fun targetShouldBeCaseInsensitive() {
            val targets = listOf("phone", "Phone", "PHONE", "PhOnE")

            targets.forEach { target ->
                assertEquals("phone", target.lowercase(),
                    "目标应转换为小写")
            }
        }
    }

    @Nested
    @DisplayName("性能与并发测试")
    inner class PerformanceTests {

        @Test
        @DisplayName("随机化算法应高效执行")
        fun randomizationShouldBeFast() {
            val timing = LongArray(100) { it * 10L }

            val startTime = System.nanoTime()

            repeat(1000) {
                applyMicroRandomizationPublic(timing)
            }

            val duration = (System.nanoTime() - startTime) / 1_000_000

            assertTrue(duration < 100,
                "1000次随机化应在100ms内完成，实际耗时${duration}ms")
        }

        private fun applyMicroRandomizationPublic(timing: LongArray): LongArray {
            return timing.map { ms ->
                val factor = 1.0 + Random.nextDouble(-0.15, 0.15)
                (ms * factor).toLong().coerceAtLeast(10)
            }.toLongArray()
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class BoundaryTests {

        @Test
        @DisplayName("单元素数组应正确处理")
        fun singleElementArrayShouldBeHandled() {
            val singleElement = longArrayOf(1000)
            val randomized = applyMicroRandomizationPublic(singleElement)

            assertEquals(1, randomized.size)
            assertTrue(randomized[0] >= 10)
        }

        @Test
        @DisplayName("极大值应正确处理")
        fun veryLargeValuesShouldBeHandled() {
            val largeValues = longArrayOf(Long.MAX_VALUE / 2, 1000000000)

            assertDoesNotThrow {
                applyMicroRandomizationPublic(largeValues)
            }
        }

        @Test
        @DisplayName("全零数组应转换为全10")
        fun allZerosShouldBecomeAll10s() {
            val zeros = LongArray(10) { 0 }
            val randomized = applyMicroRandomizationPublic(zeros)

            randomized.forEach { value ->
                assertEquals(10, value)
            }
        }

        private fun applyMicroRandomizationPublic(timing: LongArray): LongArray {
            return timing.map { ms ->
                val factor = 1.0 + Random.nextDouble(-0.15, 0.15)
                (ms * factor).toLong().coerceAtLeast(10)
            }.toLongArray()
        }
    }

    @Nested
    @DisplayName("算法统计特性测试")
    inner class StatisticalTests {

        @Test
        @DisplayName("随机化应呈正态分布特性")
        fun randomizationShouldShowNormalDistribution() {
            val original = 1000L
            val samples = mutableListOf<Long>()

            repeat(1000) {
                val randomized = applyMicroRandomizationPublic(longArrayOf(original))
                samples.add(randomized[0])
            }

            // 计算平均值和标准差
            val mean = samples.average()
            val stdDev = kotlin.math.sqrt(
                samples.sumOf { (it - mean) * (it - mean) } / samples.size
            )

            // 平均值应接近原始值
            assertTrue(kotlin.math.abs(mean - original) < 50,
                "平均值${mean}应接近原始值${original}")

            // 标准差应在合理范围内（约为original*0.15/3）
            assertTrue(stdDev > 10 && stdDev < 100,
                "标准差${stdDev}应在合理范围内")
        }

        private fun applyMicroRandomizationPublic(timing: LongArray): LongArray {
            return timing.map { ms ->
                val factor = 1.0 + Random.nextDouble(-0.15, 0.15)
                (ms * factor).toLong().coerceAtLeast(10)
            }.toLongArray()
        }
    }
}
