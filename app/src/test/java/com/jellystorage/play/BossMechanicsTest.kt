package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BossMechanicsTest {
    @Test
    fun `five chapters map to five identities and ten signature moves`() {
        val encounters = (1..5).map { stage -> assertNotNull(bossEncounterForStage(stage)) }
        assertEquals(5, encounters.map { it.title }.toSet().size)
        assertEquals(10, encounters.flatMap { listOf(it.moveA, it.moveB) }.toSet().size)
        assertEquals(null, bossEncounterForStage(6))
    }

    @Test
    fun `circle ring and line warnings use fair geometric boundaries`() {
        val circle = BossHazard(
            BossHazardShape.CIRCLE, 100f, 100f,
            outerRadius = 50f, life = 1f, damage = 10f, color = 0L
        )
        assertTrue(circle.contains(151f, 100f, playerRadius = 2f))
        assertFalse(circle.contains(154f, 100f, playerRadius = 2f))

        val ring = BossHazard(
            BossHazardShape.RING, 100f, 100f,
            outerRadius = 80f, innerRadius = 40f, life = 1f, damage = 10f, color = 0L
        )
        assertFalse(ring.contains(100f, 100f, playerRadius = 5f))
        assertTrue(ring.contains(145f, 100f, playerRadius = 5f))
        assertFalse(ring.contains(188f, 100f, playerRadius = 5f))

        val line = BossHazard(
            BossHazardShape.LINE, 0f, 0f, 100f, 0f,
            width = 20f, life = 1f, damage = 10f, color = 0L
        )
        assertTrue(line.contains(50f, 12f, playerRadius = 3f))
        assertFalse(line.contains(50f, 14f, playerRadius = 3f))
        assertFalse(line.contains(120f, 0f, playerRadius = 3f))
    }

    @Test
    fun `chapter boss executes both telegraphed signature attacks after phase change`() {
        val sim = ArenaSim(
            width = 1080f,
            height = 700f,
            hero = HeroClass.WARRIOR,
            weaponLevel = 0,
            armorLevel = 4,
            passives = emptySet(),
            startHp = 500f,
            startMp = 0f,
            waves = listOf(
                WaveDef(listOf(WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 500f, 5f, 80f)))
            ),
            goldPerKill = 1,
            bossEncounter = BossEncounter.PRAIRIE
        )

        // Enter phase two immediately; pattern 0 and pattern 2 must become A and B.
        sim.enemies.first().hp = sim.enemies.first().maxHp * 0.6f
        val observedLabels = linkedSetOf<String>()
        var longestWarning = 0f
        repeat(180) {
            sim.update(0.05f, 0f, 0f, basic = false, s1 = false, s2 = false)
            sim.bossHazards.forEach { hazard ->
                if (hazard.label.isNotEmpty()) observedLabels += hazard.label
                longestWarning = maxOf(longestWarning, hazard.maxLife)
            }
        }

        assertTrue(longestWarning >= 0.8f)
        assertTrue(BossMove.SYRUP_METEORS.title in observedLabels)
        assertTrue(BossMove.CORE_RING.title in observedLabels)
    }
}
