package com.jellystorage.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmuneLoopTest {
    @Test
    fun memoriesUnlockGraduallyAcrossMutationGenerations() {
        assertEquals(listOf(ImmuneMemory.CLOTTING_BARRIER), ImmuneMemory.unlocked(0))
        assertTrue(ImmuneMemory.ADAPTIVE_RECEPTOR !in ImmuneMemory.unlocked(4))
        assertTrue(ImmuneMemory.ADAPTIVE_RECEPTOR in ImmuneMemory.unlocked(5))
    }

    @Test
    fun selectedMemoryChangesRunOpeningWithoutSurvivingAsEquipment() {
        val meta = RunMeta()
        meta.immuneMemoryId = ImmuneMemory.ENERGY_RESERVE.id
        meta.resetRun(HeroClass.WARRIOR, rank = 2, forcedSeed = 42L)

        assertEquals(ImmuneMemory.ENERGY_RESERVE, meta.immuneMemory())
        assertTrue(meta.gold >= 18)
        assertEquals(1, meta.ownedWeapons.size)
    }

    @Test
    fun oldInkRankMapsToMutationGenerationLabel() {
        assertEquals("原始毒株", mutationGenerationLabel(0))
        assertEquals("变异第4代", mutationGenerationLabel(4))
    }

    @Test
    fun lateGenerationsUseSoftScalingAndRemainPlayable() {
        assertEquals(2.4f, mutationHpScale(7), 0.001f)
        assertEquals(2.5f, mutationHpScale(8), 0.001f)
        assertTrue(mutationHpScale(30) < 5f)
        assertTrue(mutationAtkScale(30) < mutationHpScale(30))
    }
}
