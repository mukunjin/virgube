package com.mukunjin.virgube.scramble

import com.mukunjin.virgube.scramble.rng.IsaacRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 333 打乱一致性测试。
 *
 * 金标准比对的是每条打乱的**目标态**（送入求解器前的 54 字符 facelet 串），
 * 而非最终打乱字符串。原因：
 * - 目标态在调用 min2phase 求解**之前**就已确定，只依赖 RNG 流与 `fixOri`/`fixPerm`，
 *   与 `Util` 剪枝表的全局（增量）初始化进度无关，因此**稳定、与测试执行顺序无关**；
 * - 最终字符串由求解器给出，同一态在不同初始化进度下会返回**不同但等价**的最优解，
 *   逐字符比对会把“求解器内部状态”误当成契约，测试会随无关测试的执行顺序而翻。
 *
 * 如何校验打乱串本身：csTimer 的 333 打乱是随机态的**逆解**，把打乱作用到复原态即得目标态。
 * 因此断言 `faceletsOf(打乱串) == 目标态`，即可同时覆盖“随机态构造正确”与“打乱串正确编码该态”
 * （move 编码、逆解语义等），且不依赖求解器选了哪个等价解。
 *
 * 资源由 `tools/gen-golden.js` 从 csTimer 原始 JS 生成，覆盖默认种子与三类边缘种子。
 */
class Scramble333GoldenTest {

    @Test
    fun scrambleReproducesCsTimerTargetState() {
        for (resource in RESOURCES) {
            val lines = readLines(resource)
            val seed = parseSeed(lines.first())
            assertEquals("$resource 种子长度应为 256", 256, seed.size)

            val expected = lines.drop(1).map { it.trim() }
            assertTrue("$resource 金标准向量数量过少", expected.size >= 10)

            val rng = IsaacRandom()
            rng.seedWithPreAdvance(seed)
            val generator = Scramble333(rng)

            for (i in expected.indices) {
                val scramble = generator.getRandomScramble()
                assertEquals(
                    "$resource 第 $i 条打乱的目标态与 csTimer 不一致",
                    expected[i],
                    CubeState.faceletsOf(scramble),
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
            "scramble333_state_golden.txt",
            "scramble333_state_golden_zeros.txt",
            "scramble333_state_golden_ones.txt",
            "scramble333_state_golden_sparse.txt",
        )
    }
}
