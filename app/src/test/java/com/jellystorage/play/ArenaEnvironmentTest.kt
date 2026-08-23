package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArenaEnvironmentTest {
    @Test
    fun `each chapter exposes three stable room identities`() {
        (0..4).forEach { chapter ->
            val first = (0..2).map { arenaEnvironmentFor(chapter, it, NodeType.MOB) }
            val again = (0..2).map { arenaEnvironmentFor(chapter, it, NodeType.MOB) }
            assertEquals(first, again)
            assertEquals(3, first.map { it.id }.distinct().size)
            assertEquals(3, first.map { it.pattern }.distinct().size)
            assertTrue(first.all { it.active && it.chapter == chapter })
        }
    }

    @Test
    fun `boss keeps its authored mechanics without extra room hazard`() {
        assertFalse(arenaEnvironmentFor(3, 12, NodeType.BOSS).active)
        assertFalse(arenaEnvironmentFor(1, 7, NodeType.SHOP).active)
    }

    @Test
    fun `active room environment creates a readable delayed hazard`() {
        val environment = arenaEnvironmentFor(0, 0, NodeType.MOB)
        val sim = ArenaSim(
            width = 900f,
            height = 650f,
            hero = HeroClass.WARRIOR,
            weaponLevel = 0,
            armorLevel = 4,
            passives = emptySet(),
            startHp = 500f,
            startMp = 0f,
            waves = listOf(WaveDef(listOf(WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, 999f, 0f, 0f)))),
            goldPerKill = 1,
            environment = environment
        )
        sim.enemies.single().apply {
            speed = 0f
            attackCd = 99f
            specialCd = 99f
        }
        repeat(112) { sim.update(0.04f, 0f, 0f, basic = false, s1 = false, s2 = false) }
        assertTrue(sim.bossHazards.any { it.label == environment.title && it.life > 0f })
    }
}
