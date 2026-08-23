package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArenaRawTouchTest {

    @Test
    fun `staleIds 只回收超时手指且不重复返回`() {
        val s = ArenaRawTouchState()
        s.lastSeen[1] = 125_000L // 5s 前活跃
        s.lastSeen[2] = 100_000L // 30s 前活跃

        val stale = s.staleIds(nowMs = 130_001L, timeoutMs = 30_000L)
        assertEquals(listOf(2), stale)
        assertTrue(s.lastSeen.containsKey(1))
        assertFalse(s.lastSeen.containsKey(2))

        // 已回收的手指不会重复返回；仍未超时的手指保持登记
        assertTrue(s.staleIds(nowMs = 130_002L, timeoutMs = 30_000L).isEmpty())
    }

    @Test
    fun `reset 清空全部登记`() {
        val s = ArenaRawTouchState()
        s.lastSeen[3] = 42L
        s.reset()
        assertTrue(s.lastSeen.isEmpty())
        assertEquals(-1, s.joyId)
        assertEquals(-1, s.ultId)
    }

    @Test
    fun `命中区系数不会让普攻吞掉技能1的视觉圆`() {
        // 布局：技能1 中心距普攻 0.193u；普攻视觉半径 0.10u、命中 0.12u；技能1 视觉 0.072u
        val centers = 0.1931f
        val atkHit = 0.10f * ARENA_HIT_SLOP_ATTACK
        val s1Visual = 0.072f
        // 技能1 视觉圆近端边缘到普攻中心的距离必须大于普攻命中半径
        assertTrue(centers - s1Visual > atkHit, "centers-s1visual=${centers - s1Visual} atkHit=$atkHit")
        // 必杀命中区不得侵入普攻视觉圆：中心距 0.2532u
        val ultDist = 0.2532f
        val ultHit = 0.088f * ARENA_HIT_SLOP
        val atkVisual = 0.10f
        assertTrue(ultDist - ultHit > atkVisual, "ultDist-ultHit=${ultDist - ultHit} atkVisual=$atkVisual")
    }
}
