package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnemyTacticsTest {
    @Test
    fun `support enemy silhouettes have stable readable roles`() {
        assertEquals(EnemyTacticalRole.HEALER, EnemyKind.WISP.tacticalRole())
        assertEquals(EnemyTacticalRole.DRUMMER, EnemyKind.GOBLIN.tacticalRole())
        assertEquals(EnemyTacticalRole.GUARD, EnemyKind.BEETLE.tacticalRole())
        assertEquals(EnemyTacticalRole.NONE, EnemyKind.SLIME.tacticalRole())
        assertEquals(0.82f, guardDamageMultiplier(elite = false))
        assertEquals(0.76f, guardDamageMultiplier(elite = true))
    }

    @Test
    fun `common enemy families have distinct signature attacks`() {
        val combatKinds = listOf(
            EnemyKind.SLIME, EnemyKind.PINK_SLIME, EnemyKind.SPIKE_SLIME,
            EnemyKind.BAT, EnemyKind.SKELETON, EnemyKind.GOBLIN,
            EnemyKind.RAT, EnemyKind.WISP
        )
        val attacks = combatKinds.map { it.signatureAttack() }
        assertTrue(attacks.none { it == EnemySignatureAttack.NONE })
        assertEquals(attacks.size, attacks.distinct().size)
        assertEquals(EnemySignatureAttack.NONE, EnemyKind.BEETLE.signatureAttack())
        assertEquals(EnemySignatureAttack.NONE, EnemyKind.BOSS_SLIME.signatureAttack())
    }

    @Test
    fun `signature attacks telegraph before producing a combat effect`() {
        val combatKinds = listOf(
            EnemyKind.SLIME, EnemyKind.PINK_SLIME, EnemyKind.SPIKE_SLIME,
            EnemyKind.BAT, EnemyKind.SKELETON, EnemyKind.GOBLIN,
            EnemyKind.RAT, EnemyKind.WISP
        )
        combatKinds.forEach { kind ->
            val ai = when (kind) {
                EnemyKind.BAT, EnemyKind.WISP -> EnemyAi.RANGED
                else -> EnemyAi.CHASE
            }
            val sim = ArenaSim(
                width = 900f,
                height = 650f,
                hero = HeroClass.WARRIOR,
                weaponLevel = 0,
                armorLevel = 4,
                passives = emptySet(),
                startHp = 500f,
                startMp = 0f,
                waves = listOf(WaveDef(listOf(WaveEnemy(kind, ai, 200f, 1f)))),
                goldPerKill = 1
            )
            val enemy = sim.enemies.single()
            enemy.x = sim.player.x + 100f
            enemy.y = sim.player.y
            enemy.specialCd = 0f
            enemy.supportCd = 99f
            enemy.attackCd = 99f
            val startX = enemy.x
            val startShots = sim.shots.size
            val startRings = sim.rings.size

            sim.update(0.01f, 0f, 0f, basic = false, s1 = false, s2 = false)
            val expected = kind.signatureAttack()
            assertEquals(expected, enemy.specialAttack, "$kind should visibly telegraph")

            repeat(((expected.windup + 0.08f) / 0.04f).toInt()) {
                sim.update(0.04f, 0f, 0f, basic = false, s1 = false, s2 = false)
            }
            assertEquals(EnemySignatureAttack.NONE, enemy.specialAttack)
            assertTrue(
                sim.shots.size > startShots || sim.rings.size > startRings || enemy.x != startX,
                "$kind signature should change the battlefield"
            )
        }
    }

    @Test
    fun `healer restores a wounded ally and drummer grants haste`() {
        val sim = ArenaSim(
            width = 900f,
            height = 650f,
            hero = HeroClass.WARRIOR,
            weaponLevel = 0,
            armorLevel = 4,
            passives = emptySet(),
            startHp = 500f,
            startMp = 0f,
            waves = listOf(
                WaveDef(
                    listOf(
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 80f, 2f),
                        WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, 100f, 2f),
                        WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, 120f, 2f)
                    )
                )
            ),
            goldPerKill = 1
        )
        val healer = sim.enemies.first { it.kind == EnemyKind.WISP }
        val drummer = sim.enemies.first { it.kind == EnemyKind.GOBLIN }
        val slime = sim.enemies.first { it.kind == EnemyKind.SLIME }
        healer.x = 500f; healer.y = 320f; healer.supportCd = 0f
        drummer.x = 530f; drummer.y = 320f; drummer.supportCd = 0f
        slime.x = 560f; slime.y = 320f; slime.hp = slime.maxHp * 0.5f
        val woundedHp = slime.hp

        sim.update(0.05f, 0f, 0f, basic = false, s1 = false, s2 = false)

        assertTrue(slime.hp > woundedHp)
        assertTrue(slime.has(StatusType.RAGE))
        assertTrue(sim.bolts.any { it.color == 0xFF86EFAC })
    }
}
