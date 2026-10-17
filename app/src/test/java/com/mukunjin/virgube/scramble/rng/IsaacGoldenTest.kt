package com.mukunjin.virgube.scramble.rng

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ISAAC 随机数金标准测试：验证 RNG 与 csTimer 逐位一致。
 *
 * `isaac.random()` 是**纯函数**（种子 -> 随机数流，无全局可变状态），因此该测试
 * 完全确定性、与执行顺序无关。除默认种子外，另覆盖全 0、全 0xFFFF、稀疏三种
 * 边缘种子，以稳定捕获播种、32 位回绕与缓冲区重填（每 256 次 `rand()` 重填）的偏差。
 *
 * 资源由 `tools/gen-golden.js` 从 csTimer 原始 JS 生成。
 */
class IsaacGoldenTest {

    @Test
    fun matchesCsTimerRandomStream() {
        for (resource in RESOURCES) {
            val lines = readLines(resource)
            val seed = parseSeed(lines.first())
            assertEquals("$resource 种子长度应为 256", 256, seed.size)

            val expected = lines.drop(1).map { it.trim() }
            assertEquals("$resource 金标准向量数量过少", 20, expected.size)

            val rng = IsaacRandom()
            rng.seedWithPreAdvance(seed)

            for (i in expected.indices) {
                assertEquals(
                    "$resource 第 $i 个随机数与 csTimer 不一致",
                    expected[i].toDouble(),
                    rng.random(),
                    0.0,
                )
            }
        }
    }

    private fun parseSeed(header: String): IntArray {
        check(header.startsWith("SEED ")) { "金标准首行应为 SEED 头：$header" }
        return header.removePrefix("SEED ").split(",").map { it.trim().toInt() }.toIntArray()
    }

    private fun readLines(name: String): List<String> {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: error("找不到金标准资源 $name")
        return stream.bufferedReader(Charsets.UTF_8).readText().trimEnd('\n').split("\n")
    }

    private companion object {
        /** 默认种子 + 三类边缘种子。 */
        val RESOURCES = listOf(
            "isaac_golden.txt",
            "isaac_golden_zeros.txt",
            "isaac_golden_ones.txt",
            "isaac_golden_sparse.txt",
        )
    }
}
