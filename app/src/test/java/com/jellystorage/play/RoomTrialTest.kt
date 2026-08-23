package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomTrialTest {
    @Test
    fun `trial roll is stable for the same run and node`() {
        val node = MapNode(7, NodeType.ELITE, 0.5f, 0.5f, "试炼", emptyList())
        assertEquals(roomTrialFor(123456L, 2, node), roomTrialFor(123456L, 2, node))
    }

    @Test
    fun `non combat nodes never receive a trial`() {
        val shop = MapNode(2, NodeType.SHOP, 0.5f, 0.5f, "商店", emptyList())
        assertNull(roomTrialFor(1L, 0, shop))
    }

    @Test
    fun `different combat nodes produce a useful mix`() {
        val trials = (1..24).mapNotNull { id ->
            roomTrialFor(987654321L, 1, MapNode(id, NodeType.MOB, 0f, 0f, "战", emptyList()))
        }.toSet()
        assertTrue(trials.size >= 3)
    }

    @Test
    fun `objectives honor their pass and fail boundaries`() {
        assertTrue(RoomTrial.RUSH.succeeded(4, 72f, 0, 0, 0f, 100f))
        assertFalse(RoomTrial.RUSH.succeeded(4, 72.1f, 0, 0, 0f, 100f))
        assertTrue(RoomTrial.COMBO.succeeded(4, 0f, 16, 0, 0f, 100f))
        assertFalse(RoomTrial.ARCANE.succeeded(4, 0f, 0, 7, 0f, 100f))
        assertTrue(RoomTrial.GUARD.succeeded(4, 0f, 0, 0, 28f, 100f))
        assertFalse(RoomTrial.GUARD.succeeded(4, 0f, 0, 0, 28.1f, 100f))
    }
}
