package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressionBalanceTest {

    @Test
    fun `一次战斗结算最多提升一级并保留溢出经验`() {
        val meta = RunMeta()

        assertTrue(meta.addXp(500))
        assertEquals(2, meta.level)
        assertEquals(500 - xpRequirementForLevel(1), meta.xp)
        assertEquals(xpRequirementForLevel(2), meta.xpToLevel)

        assertTrue(meta.addXp(0))
        assertEquals(3, meta.level)
    }

    @Test
    fun `五个技能分布在多个战斗节点解锁`() {
        for (hero in HeroClass.entries) {
            // 法师 S1 魔法盾 1 级即解锁（前期保命）
            val expected = if (hero == HeroClass.MAGE) listOf(1, 1, 4, 6, 8) else listOf(1, 2, 4, 6, 8)
            assertEquals(expected, skillsFor(hero).map { it.unlockLevel })
        }
    }

    @Test
    fun `难度逐步增加且速度与精英率有上限`() {
        val values = (0..25).map { arenaThreatMultiplier(it) }
        assertTrue(values.zipWithNext().all { (a, b) -> b > a })
        assertTrue(enemySpeedThreatMultiplier(30) <= 1.25f)
        assertTrue(eliteSpawnChance(30) <= 0.38f)
    }

    @Test
    fun `装备品质按章节威胁逐步开放`() {
        assertEquals(0, maxGearRarityForThreat(1))
        assertEquals(1, maxGearRarityForThreat(6))
        assertEquals(2, maxGearRarityForThreat(16))
        assertEquals(1, maxGearTierForThreat(1))
        assertEquals(3, maxGearTierForThreat(10))
        assertEquals(5, maxGearTierForThreat(24))
    }
}
