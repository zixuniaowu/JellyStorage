package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetFeedbackTest {
    @Test
    fun `equipping the completing piece starts one awakening presentation`() {
        val meta = RunMeta()
        meta.resetRun(HeroClass.WARRIOR, forcedSeed = 42L)
        meta.ownedWeapons += "w_steel"
        meta.ownedArmors += "a_w_leather"
        meta.equipArmor("a_w_leather")

        assertTrue(meta.equipWeapon("w_steel"))
        assertEquals("set_w_iron", meta.activeSet()?.id)
        assertEquals("set_w_iron", meta.setAwakenedId)
        assertTrue(meta.setAwakenedT > 3f)
        assertTrue(meta.setAwakenedHapticPending)

        meta.setAwakenedT = 0f
        meta.equipWeapon("w_steel")
        assertEquals(0f, meta.setAwakenedT, "re-equipping the same active set must not replay the reveal")
    }

    @Test
    fun `active procs have cooldowns while passive bonuses remain constant`() {
        assertTrue(GearProc.BURN.cooldownSeconds() > 0f)
        assertTrue(GearProc.CHAIN.cooldownSeconds() > 0f)
        assertEquals(0f, GearProc.WUXING_AMP.cooldownSeconds())
        assertEquals(0f, GearProc.HEAL_AMP.cooldownSeconds())
        assertEquals(0f, GearProc.THORNS.cooldownSeconds())
    }

    @Test
    fun `burn proc trigger starts the exact cooldown exposed to HUD`() {
        val sim = ArenaSim(
            width = 900f,
            height = 650f,
            hero = HeroClass.WARRIOR,
            weaponLevel = 0,
            armorLevel = 0,
            passives = emptySet(),
            startHp = 300f,
            startMp = 0f,
            waves = listOf(WaveDef(listOf(WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, 900f, 1f, 20f)))),
            goldPerKill = 1,
            overrideProc = GearProc.BURN,
            overrideProcPower = 0.28f
        )
        val enemy = sim.enemies.first()
        enemy.x = sim.player.x + 35f
        enemy.y = sim.player.y

        sim.update(0.05f, 0f, 0f, basic = true, s1 = false, s2 = false)

        assertEquals(1, sim.gearProcTriggerCount)
        assertTrue(enemy.has(StatusType.BURN))
        assertTrue(sim.gearProcCooldown > 0f)
        assertTrue(sim.gearProcPulse > 0f)
        val before = sim.gearProcCooldown
        sim.update(0.1f, 0f, 0f, basic = false, s1 = false, s2 = false)
        assertTrue(sim.gearProcCooldown < before)
    }
}
