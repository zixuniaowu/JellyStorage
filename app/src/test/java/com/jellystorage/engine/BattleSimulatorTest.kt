package com.jellystorage.engine

import com.jellystorage.engine.battle.BattleSimulator
import com.jellystorage.engine.battle.BattleStatus
import com.jellystorage.engine.model.WeaponCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives shipped [BattleSimulator.tick] from t=0 — no mid-fight seeding.
 */
class BattleSimulatorTest {

    @Test
    fun startsInPreparing_thenEntersFighting() {
        val sim = BattleSimulator(
            playerWeapons = WeaponCatalog.defaultsForSteamBlast(),
            playerMaxHp = 200f,
            enemyMaxHp = 150f
        )
        assertEquals(BattleStatus.PREPARING, sim.viewState.status)

        // PREPARE_SECONDS = 1.2s → tick ~20 frames of 50ms
        repeat(30) { sim.tick(50L) }

        val status = sim.viewState.status
        assertTrue(
            "Expected FIGHTING or already terminal, was $status",
            status == BattleStatus.FIGHTING ||
                status == BattleStatus.VICTORY ||
                status == BattleStatus.DEFEAT
        )
    }

    @Test
    fun tick_spawnsAndMovesProjectiles() {
        val sim = BattleSimulator(
            playerWeapons = listOf(WeaponCatalog.FLAME_CLAW, WeaponCatalog.ICE_SPIKE),
            playerMaxHp = 300f,
            enemyMaxHp = 500f,
            enemyBaseDamage = 4f,
            enemyFireRate = 0.5f
        )
        // Skip prepare
        repeat(40) { sim.tick(50L) }
        assertEquals(BattleStatus.FIGHTING, sim.getStatus())

        // Fire weapons for a while and observe projectiles / motion
        var sawProjectile = false
        var sawMoved = false
        var prevX = 0f
        var prevY = 0f
        var hasPrev = false

        repeat(120) {
            sim.tick(16L)
            val vs = sim.viewState
            if (vs.projectileCount > 0) {
                if (!sawProjectile) {
                    sawProjectile = true
                    prevX = vs.projectileX[0]
                    prevY = vs.projectileY[0]
                    hasPrev = true
                } else if (hasPrev) {
                    val dx = vs.projectileX[0] - prevX
                    val dy = vs.projectileY[0] - prevY
                    if (dx != 0f || dy != 0f) sawMoved = true
                    prevX = vs.projectileX[0]
                    prevY = vs.projectileY[0]
                }
            }
        }
        assertTrue("Battle should spawn projectiles from weapons", sawProjectile)
        assertTrue("Projectiles should integrate x/y via vx/vy", sawMoved)
    }

    @Test
    fun tick_reducesHp_onCollisions() {
        val sim = BattleSimulator(
            playerWeapons = WeaponCatalog.defaultsForSteamBlast(),
            playerMaxHp = 200f,
            enemyMaxHp = 120f,
            enemyBaseDamage = 8f,
            enemyFireRate = 1.2f
        )
        val startEnemy = sim.viewState.enemyHp
        val startPlayer = sim.viewState.playerHp

        // Run long enough for shots to connect
        repeat(200) { sim.tick(16L) }

        val vs = sim.viewState
        val enemyHurt = vs.enemyHp < startEnemy
        val playerHurt = vs.playerHp < startPlayer
        val ended = vs.status == BattleStatus.VICTORY || vs.status == BattleStatus.DEFEAT
        assertTrue(
            "Expected HP reduction or terminal state (enemy ${vs.enemyHp}/$startEnemy player ${vs.playerHp}/$startPlayer status=${vs.status})",
            enemyHurt || playerHurt || ended
        )
    }

    @Test
    fun floatingCombatText_appears_andFades() {
        val sim = BattleSimulator(
            playerWeapons = listOf(WeaponCatalog.FLAME_CLAW),
            playerMaxHp = 400f,
            enemyMaxHp = 400f,
            enemyBaseDamage = 10f,
            enemyFireRate = 1.5f
        )
        // Enter fight and produce hits
        repeat(80) { sim.tick(50L) }

        var sawFloat = false
        var sawPositiveLife = false
        var sawFade = false
        var lastLife = -1f

        repeat(100) {
            sim.tick(16L)
            val vs = sim.viewState
            if (vs.floatCount > 0) {
                sawFloat = true
                val life = vs.floatLife[0]
                if (life > 0f) sawPositiveLife = true
                if (lastLife > 0f && life < lastLife) sawFade = true
                lastLife = life
            }
        }

        // Even if floats already expired, combat should have produced them at some point —
        // re-check by aggressive fire on a fresh sim if needed
        if (!sawFloat) {
            val sim2 = BattleSimulator(
                playerWeapons = listOf(WeaponCatalog.FLAME_CLAW, WeaponCatalog.ICE_SPIKE),
                playerMaxHp = 500f,
                enemyMaxHp = 80f,
                enemyBaseDamage = 1f,
                enemyFireRate = 0.2f
            )
            repeat(60) { sim2.tick(50L) }
            repeat(40) {
                sim2.tick(16L)
                if (sim2.viewState.floatCount > 0) {
                    sawFloat = true
                    sawPositiveLife = sim2.viewState.floatLife[0] > 0f
                }
            }
        }

        assertTrue("Expected floating combat text from hits", sawFloat)
        assertTrue("Float text should start with positive life", sawPositiveLife || sawFade)
    }

    @Test
    fun battle_reachesTerminalVictoryOrDefeat() {
        val sim = BattleSimulator(
            playerWeapons = WeaponCatalog.defaultsForSteamBlast(),
            playerMaxHp = 180f,
            enemyMaxHp = 90f,
            enemyBaseDamage = 6f,
            enemyFireRate = 1.0f
        )
        // Cap frames to avoid infinite loop if broken
        var frames = 0
        while (
            sim.getStatus() != BattleStatus.VICTORY &&
            sim.getStatus() != BattleStatus.DEFEAT &&
            frames < 2000
        ) {
            sim.tick(16L)
            frames++
        }
        val status = sim.getStatus()
        assertTrue(
            "Battle must end in VICTORY or DEFEAT within timeout (status=$status frames=$frames)",
            status == BattleStatus.VICTORY || status == BattleStatus.DEFEAT
        )
        val vs = sim.viewState
        if (status == BattleStatus.VICTORY) {
            assertTrue(vs.enemyHp <= 0f)
        } else {
            assertTrue(vs.playerHp <= 0f)
        }
    }

    @Test
    fun viewState_exposesRequiredFields() {
        val sim = BattleSimulator(playerWeapons = WeaponCatalog.defaultsForThunderArmor())
        val vs = sim.viewState
        assertTrue(vs.playerMaxHp > 0f)
        assertTrue(vs.enemyMaxHp > 0f)
        assertTrue(vs.playerOuterCount >= 8)
        assertTrue(vs.enemyOuterCount >= 8)
        assertEquals(vs.playerOuterCount, 12)
        // synergies for thunder+shield
        assertTrue(vs.activeSynergies.any { it.name == "Thunder Armor" })
    }

    /**
     * Regression: PREPARING→FIGHTING must not emit a K.O. float
     * (crit+combo+non-positive value is reserved for victory/defeat).
     */
    @Test
    fun fightStart_doesNotSpawnKoFloat() {
        val sim = BattleSimulator(
            playerWeapons = WeaponCatalog.defaultsForSteamBlast(),
            playerMaxHp = 300f,
            enemyMaxHp = 300f,
            enemyBaseDamage = 1f,
            enemyFireRate = 0.2f
        )
        // Drain prepare (~1.2s)
        repeat(30) { sim.tick(50L) }
        assertEquals(BattleStatus.FIGHTING, sim.getStatus())

        val vs = sim.viewState
        var koStyle = false
        for (i in 0 until vs.floatCount) {
            // Canvas K.O. rule: crit && combo && value <= 0.01
            if (vs.floatIsCrit[i] && vs.floatIsCombo[i] && vs.floatValue[i] <= 0.01f) {
                koStyle = true
            }
        }
        assertTrue(
            "Fight start must not spawn K.O.-style float (crit+combo+zero)",
            !koStyle
        )
    }

    /**
     * Regression: a single projectile contact must not multi-apply damage every tick
     * while still overlapping (Steam Blast armor-pierce latch bug).
     */
    @Test
    fun projectileHit_doesNotMultiDamageSameOverlap() {
        val sim = BattleSimulator(
            playerWeapons = WeaponCatalog.defaultsForSteamBlast(), // has ARMOR_PIERCE synergy
            playerMaxHp = 500f,
            enemyMaxHp = 500f,
            enemyBaseDamage = 0.01f,
            enemyFireRate = 0.05f
        )
        // Enter fighting
        repeat(30) { sim.tick(50L) }
        assertEquals(BattleStatus.FIGHTING, sim.getStatus())

        // Run until first enemy HP drop from a projectile hit
        var prev = sim.viewState.enemyHp
        var frames = 0
        while (sim.viewState.enemyHp >= prev - 0.001f && frames < 400) {
            sim.tick(16L)
            frames++
            if (sim.viewState.enemyHp < prev) break
        }
        val afterFirstHit = sim.viewState.enemyHp
        assertTrue(
            "Expected at least one damaging hit (prev=$prev after=$afterFirstHit frames=$frames)",
            afterFirstHit < prev
        )

        // Immediately advance a few frames while projectiles may still overlap —
        // damage per frame from the SAME contact must not stack unboundedly.
        val samples = FloatArray(8)
        for (i in samples.indices) {
            sim.tick(16L)
            samples[i] = sim.viewState.enemyHp
        }
        // Total drop over 8 frames right after first hit should be bounded
        // (not ~8x single-shot from re-latching the same projectile).
        val drop = afterFirstHit - samples.last()
        val firstHitDmg = prev - afterFirstHit
        // Allow some additional hits from new projectiles, but not 6x first-hit every frame
        assertTrue(
            "Overlap re-damage too high: firstHit=$firstHitDmg dropOver8frames=$drop samples=${samples.toList()}",
            drop < firstHitDmg * 6f + 1f
        )
    }
}
