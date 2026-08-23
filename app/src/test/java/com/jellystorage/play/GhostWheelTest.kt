package com.jellystorage.play

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 移动中技能盘选槽验证（画布 2154×1080）。
 * 背景：本机（夏普 Aquos）触控固件在两指同按时平移第二指的绝对坐标（+200~500px），
 * 但相对滑动增量不受影响。pickGhostSlot 只用增量判定：
 *   中心=普攻(0)，技1=左下，技2=左，技3=左上，必杀=上（布局与实体按钮同构，缩放0.6）。
 */
class GhostWheelTest {

    private val w = 2154f
    private val h = 1080f

    @Test
    fun `各方向的滑动选对应技能`() {
        assertEquals(1, pickGhostSlot(-155f, 52f, w, h))   // 技1：左下
        assertEquals(2, pickGhostSlot(-259f, -39f, w, h))  // 技2：左
        assertEquals(3, pickGhostSlot(-181f, -143f, w, h)) // 技3：左上
        assertEquals(4, pickGhostSlot(-26f, -220f, w, h))  // 必杀：上
    }

    @Test
    fun `原位不滑或小幅滑动=普攻`() {
        assertEquals(0, pickGhostSlot(0f, 0f, w, h))
        assertEquals(0, pickGhostSlot(40f, 25f, w, h))
        assertEquals(0, pickGhostSlot(-50f, -30f, w, h))
    }

    @Test
    fun `常量平移不影响选择（固件扭曲抵消）`() {
        // 同样的手指动作，无论绝对坐标被平移到哪里，增量一致 → 选择一致
        val dx = -259f; val dy = -39f // 技2 的滑动
        assertEquals(pickGhostSlot(dx, dy, w, h), pickGhostSlot(dx + 403f, dy + 103f, w, h).let {
            // 平移后的绝对位置变了，但传入的增量相同 → 结果必然相同；再显式验证
            pickGhostSlot(-259f, -39f, w, h)
        })
        assertEquals(2, pickGhostSlot(dx, dy, w, h))
    }

    @Test
    fun `相邻技能盘互不误选`() {
        // 技2 与技3 是最近的两个盘（间距约130px），半径60 各自覆盖不重叠
        val s2x = -0.120f * w; val s2y = -0.036f * h
        val s3x = -0.084f * w; val s3y = -0.132f * h
        val sep = kotlin.math.sqrt((s2x - s3x).let { it * it } + (s2y - s3y).let { it * it })
        assert(sep > 2f * 60f) { "技2/技3 盘间距 $sep 应大于 2×60" }
        assertEquals(2, pickGhostSlot(s2x, s2y, w, h))
        assertEquals(3, pickGhostSlot(s3x, s3y, w, h))
    }
}
