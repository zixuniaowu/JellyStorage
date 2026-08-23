package com.jellystorage.play

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class ArenaControlLayoutTest {

    @Test
    fun `键位在不同横屏比例下保持相同拇指距离`() {
        val screens = listOf(
            1920f to 1080f, // 16:9
            2400f to 1080f, // 20:9
            1280f to 720f,  // 小尺寸 16:9
            2560f to 1600f  // 平板 16:10
        )

        for ((w, h) in screens) {
            val u = min(w, h)
            val c = arenaControlLayout(w, h)

            assertEquals(u * 0.18f, w - c.attackX, 0.01f)
            assertEquals(u * 0.18f, c.attackX - c.skill1X, 0.01f)
            assertEquals(u * 0.27f, c.attackX - c.skill2X, 0.01f)
            assertEquals(u * 0.20f, c.attackX - c.skill3X, 0.01f)
            assertEquals(u * 0.04f, c.attackX - c.ultX, 0.01f)
            assertEquals(u * 0.08f, w - c.potionX, 0.01f)
        }
    }
}
