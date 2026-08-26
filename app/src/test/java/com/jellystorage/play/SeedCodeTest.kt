package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class SeedCodeTest {

    @Test
    fun `编码解码往返无损`() {
        val cases = listOf(
            0L to 0,
            123456789L to 3,
            System.nanoTime() to 5,
            -987654321L to 1
        )
        cases.forEach { (seed, rank) ->
            val (s, r) = SeedCode.decode(SeedCode.encode(seed, rank))!!
            assertEquals(seed, s)
            assertEquals(rank, r)
        }
    }

    @Test
    fun `非法输入返回null`() {
        assertNull(SeedCode.decode(""))
        assertNull(SeedCode.decode("abc"))
        assertNull(SeedCode.decode("abc_"))
        assertNull(SeedCode.decode("_2"))
        assertNull(SeedCode.decode("abc_x"))
        assertNull(SeedCode.decode("abc_2_3"))
        assertNull(SeedCode.decode("zzzzzzzzzzzzzzzzzz_1"), "seed 溢出")
        assertNull(SeedCode.decode("abc_100"), "rank 越界")
        assertNull(SeedCode.decode("abc_-1"), "rank 负数")
    }

    @Test
    fun `解码容忍首尾空白`() {
        val (s, r) = SeedCode.decode("  3vk7d2_2 \n")!!
        assertEquals("3vk7d2".toLong(36), s)
        assertEquals(2, r)
    }

    @Test
    fun `不同种子或墨阶产生不同码`() {
        assertNotEquals(SeedCode.encode(1L, 0), SeedCode.encode(2L, 0))
        assertNotEquals(SeedCode.encode(1L, 0), SeedCode.encode(1L, 1))
    }

    @Test
    fun `开局校验区分非法格式和未解锁墨阶`() {
        assertIs<SeedCode.LaunchValidation.InvalidFormat>(
            SeedCode.validateForLaunch("not-a-code", unlockedRank = 4)
        )

        val locked = assertIs<SeedCode.LaunchValidation.LockedRank>(
            SeedCode.validateForLaunch(SeedCode.encode(42L, 5), unlockedRank = 4)
        )
        assertEquals(5, locked.required)
        assertEquals(4, locked.unlocked)

        val valid = assertIs<SeedCode.LaunchValidation.Valid>(
            SeedCode.validateForLaunch(SeedCode.encode(42L, 4), unlockedRank = 4)
        )
        assertEquals(42L, valid.seed)
        assertEquals(4, valid.inkRank)
    }

    @Test
    fun `当前远征码只在有进度时给出`() {
        val fresh = RunMeta()
        fresh.resetRun(HeroClass.WARRIOR, 0)
        assertNull(SeedCode.currentRunCode(fresh))
        fresh.kills = 3
        val code = SeedCode.currentRunCode(fresh)
        assertTrue(code != null && code.endsWith("_0"))
        val (seed, rank) = SeedCode.decode(code)!!
        assertEquals(fresh.runSeed, seed)
        assertEquals(fresh.inkRank, rank)
    }
}
