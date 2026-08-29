package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreInkTest {
    @Test
    fun `each hero has three mechanism choices and upgrades cap at five`() {
        HeroClass.entries.forEach { hero ->
            val choices = coreInkChoices(hero)
            assertEquals(3, choices.size)
            assertTrue(choices.all { it.hero == hero })
        }
        val ranks = linkedMapOf<CoreInkId, Int>()
        repeat(8) { ranks.upgrade(CoreInkId.MAGE_TWIN_FROST) }
        assertEquals(5, ranks.rankOf(CoreInkId.MAGE_TWIN_FROST))
    }

    @Test
    fun `core ranks survive save format and reject another class`() {
        val source = linkedMapOf(
            CoreInkId.MAGE_TWIN_FROST to 2,
            CoreInkId.MAGE_CINDER_FIELD to 4
        )
        val raw = encodeCoreInkRanks(source) + ",BROKEN:9,WARRIOR_FIRE_TRAIL:3"
        assertEquals(source, decodeCoreInkRanks(raw, HeroClass.MAGE))
    }

    @Test
    fun `warrior fire trail changes dash into persistent burning zones`() {
        val sim = simFor(HeroClass.WARRIOR, CoreInkId.WARRIOR_FIRE_TRAIL, rank = 2)
        sim.update(0.05f, 0f, 0f, basic = false, s1 = true, s2 = false)
        assertTrue(sim.fields.count { it.kind == 2 } >= 3)
    }

    @Test
    fun `warrior bastion damages nearby foes and whirlwind schedules return stroke`() {
        val bastion = simFor(HeroClass.WARRIOR, CoreInkId.WARRIOR_BASTION_BURST, rank = 2)
        val foe = bastion.enemies.first()
        foe.x = bastion.player.x + 35f; foe.y = bastion.player.y
        val before = foe.hp
        bastion.update(0.05f, 0f, 0f, basic = false, s1 = false, s2 = true)
        assertTrue(foe.hp < before)

        val whirlwind = simFor(HeroClass.WARRIOR, CoreInkId.WARRIOR_WHIRL_ECHO, rank = 2)
        whirlwind.update(0.05f, 0f, 0f, basic = false, s1 = false, s2 = false, s3 = true)
        assertTrue(whirlwind.echoPulses.any { it.glyph == "回" })
    }

    @Test
    fun `mage twin frost shield persists and scales with rank`() {
        val sim = simFor(HeroClass.MAGE, CoreInkId.MAGE_TWIN_FROST, rank = 2)
        sim.update(0.05f, 0f, 0f, basic = false, s1 = true, s2 = false)
        // rank2: 5s + 2*0.6s = 6.2s，减去一帧
        assertTrue(sim.manaShieldT > 5.5f, "manaShieldT = ${sim.manaShieldT}")
    }

    @Test
    fun `mage storm branch reaches seven targets and cinder leaves a field`() {
        val storm = ArenaSim(
            width = 1200f,
            height = 700f,
            hero = HeroClass.MAGE,
            weaponLevel = 0,
            armorLevel = 4,
            passives = emptySet(),
            startHp = 500f,
            startMp = 200f,
            waves = listOf(WaveDef(List(7) { WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, 900f, 1f, 20f) })),
            goldPerKill = 1,
            heroLevel = skillsFor(HeroClass.MAGE).maxOf { it.unlockLevel },
            coreInkRanks = mapOf(CoreInkId.MAGE_STORM_BRANCH to 2)
        )
        storm.enemies.forEachIndexed { index, enemy ->
            enemy.x = 420f + index * 90f
            enemy.y = 350f
        }
        storm.update(0.05f, 0f, 0f, basic = false, s1 = false, s2 = true)
        assertTrue(storm.bolts.size >= 7)

        val cinder = simFor(HeroClass.MAGE, CoreInkId.MAGE_CINDER_FIELD, rank = 2)
        cinder.update(0.05f, 0f, 0f, basic = false, s1 = false, s2 = false, s3 = true)
        assertTrue(cinder.fields.any { it.kind == 2 })
    }

    @Test
    fun `taoist wandering mist creates a following poison field`() {
        val sim = simFor(HeroClass.TAOIST, CoreInkId.TAOIST_WANDERING_MIST, rank = 2)
        sim.update(0.05f, 0f, 0f, basic = false, s1 = true, s2 = false)
        assertTrue(sim.fields.any { it.kind == 3 })
    }

    @Test
    fun `taoist overflow becomes shield and seal schedules return talisman`() {
        val spring = simFor(HeroClass.TAOIST, CoreInkId.TAOIST_SPRING_OVERFLOW, rank = 2)
        spring.update(0.05f, 0f, 0f, basic = false, s1 = false, s2 = true)
        assertTrue(spring.player.powerOf(StatusType.SHIELD) > spring.player.maxHp * 0.08f)

        val seal = simFor(HeroClass.TAOIST, CoreInkId.TAOIST_SEAL_ECHO, rank = 2)
        seal.update(0.05f, 0f, 0f, basic = false, s1 = false, s2 = false, s3 = true)
        assertTrue(seal.echoPulses.any { it.glyph == "镇" })
    }

    private fun simFor(hero: HeroClass, ink: CoreInkId, rank: Int): ArenaSim = ArenaSim(
        width = 900f,
        height = 650f,
        hero = hero,
        weaponLevel = 0,
        armorLevel = 4,
        passives = emptySet(),
        startHp = 500f,
        startMp = 200f,
        waves = listOf(WaveDef(listOf(WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, 500f, 2f, 50f)))),
        goldPerKill = 1,
        heroLevel = skillsFor(hero).maxOf { it.unlockLevel },
        coreInkRanks = mapOf(ink to rank)
    )
}
