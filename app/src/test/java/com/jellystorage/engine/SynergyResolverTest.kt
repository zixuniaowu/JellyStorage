package com.jellystorage.engine

import com.jellystorage.engine.model.EffectType
import com.jellystorage.engine.model.Tag
import com.jellystorage.engine.model.Weapon
import com.jellystorage.engine.model.WeaponCatalog
import com.jellystorage.engine.model.evaluateSynergies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the shipped [evaluateSynergies] entry point — no reimplementation.
 */
class SynergyResolverTest {

    @Test
    fun steamBlast_firePlusIce_damageScaleAndArmorPierce() {
        val loadout = WeaponCatalog.defaultsForSteamBlast()
        val effects = evaluateSynergies(loadout)

        assertTrue(
            "Expected Steam Blast damage scale 1.5x",
            effects.any {
                it.name == "Steam Blast" &&
                    it.effectType == EffectType.DAMAGE_SCALE &&
                    it.multiplier == 1.5f
            }
        )
        assertTrue(
            "Expected Steam Blast armor pierce",
            effects.any {
                it.name == "Steam Blast" &&
                    it.effectType == EffectType.ARMOR_PIERCE
            }
        )
    }

    @Test
    fun thunderArmor_thunderPlusShield_percentageThorns() {
        val loadout = WeaponCatalog.defaultsForThunderArmor()
        val effects = evaluateSynergies(loadout)

        val thorns = effects.filter {
            it.name == "Thunder Armor" && it.effectType == EffectType.THORNS
        }
        assertTrue("Expected Thunder Armor thorns effect", thorns.isNotEmpty())
        assertTrue(
            "Thorns multiplier should be a positive percentage fraction",
            thorns.first().multiplier > 0f && thorns.first().multiplier <= 1f
        )
    }

    @Test
    fun frozenShatter_icePlusHeavy_critChance() {
        // ICE_SPIKE (ICE) + FLAME_CLAW (HEAVY) provides ICE+HEAVY
        val loadout = listOf(WeaponCatalog.ICE_SPIKE, WeaponCatalog.FLAME_CLAW)
        val effects = evaluateSynergies(loadout)

        assertTrue(
            "Expected Frozen Shatter crit chance",
            effects.any {
                it.name == "Frozen Shatter" &&
                    it.effectType == EffectType.CRIT_CHANCE &&
                    it.multiplier >= 0.3f
            }
        )
    }

    @Test
    fun negativeControl_missingTags_noContractSynergies() {
        // Only POISON+BLAST — should not unlock Steam / Thunder / Frozen
        val loadout = listOf(WeaponCatalog.POISON_SAC)
        val effects = evaluateSynergies(loadout)
        assertFalse(effects.any { it.name == "Steam Blast" })
        assertFalse(effects.any { it.name == "Thunder Armor" })
        assertFalse(effects.any { it.name == "Frozen Shatter" })
    }

    @Test
    fun determinism_sameInput_sameOrderedEffects() {
        val loadout = listOf(
            WeaponCatalog.FLAME_CLAW,
            WeaponCatalog.ICE_SPIKE,
            WeaponCatalog.SHELL_GUARD
        )
        val a = evaluateSynergies(loadout)
        val b = evaluateSynergies(loadout)
        assertEquals(a, b)
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun emptyLoadout_returnsEmpty() {
        assertTrue(evaluateSynergies(emptyList()).isEmpty())
    }

    @Test
    fun tagEnum_containsRequiredNames() {
        val names = Tag.entries.map { it.name }.toSet()
        listOf(
            "FIRE", "ICE", "THUNDER", "POISON", "SHIELD",
            "PIERCE", "BLAST", "LIFESTEAL", "AGILE", "HEAVY"
        ).forEach { required ->
            assertTrue("Missing Tag.$required", required in names)
        }
        assertEquals(10, Tag.entries.size)
    }

    @Test
    fun weapon_fields_matchContract() {
        val w = Weapon("id1", "Name", 10f, 1.5f, setOf(Tag.FIRE))
        assertEquals("id1", w.id)
        assertEquals("Name", w.name)
        assertEquals(10f, w.baseDamage, 0f)
        assertEquals(1.5f, w.fireRate, 0f)
        assertTrue(Tag.FIRE in w.tags)
    }
}
