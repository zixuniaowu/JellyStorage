package com.jellystorage.play

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/** 渲染五章×三种房间构型，防止场景变化只停留在数据层。 */
@RunWith(AndroidJUnit4::class)
class ArenaEnvironmentRenderTest {
    @Test
    fun renderEnvironmentAtlas() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(1500, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap.asImageBitmap())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(1500f, 900f)) {
            val cellW = 300f
            val cellH = 300f
            val worldW = 1080f
            val worldH = 650f
            (0..4).forEach { chapter ->
                (0..2).forEach { node ->
                    val environment = arenaEnvironmentFor(chapter, node, NodeType.MOB)
                    translate(chapter * cellW, node * cellH) {
                        clipRect(0f, 0f, cellW, cellH) {
                            val sx = cellW / worldW
                            val sy = cellH / worldH
                            drawArenaBackdrop(
                                chapter, environment, cellW, cellH, 0f, 0f, 1.2f,
                                worldW, worldH,
                                { x -> x * sx }, { y -> y * sy }, { radius -> radius * sx }
                            )
                        }
                    }
                }
            }
        }
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "arena_environments.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        check(output.length() > 80_000L)
    }
}
