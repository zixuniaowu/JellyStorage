package com.jellystorage.play

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
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

/** 五器官路线图快照，防止后续又退回通用山水底图。 */
@RunWith(AndroidJUnit4::class)
class OrganMapRenderTest {
    @Test
    fun renderFiveOrganRouteMaps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cellW = 400f
        val cellH = 260f
        val bitmap = Bitmap.createBitmap((cellW * 5).toInt(), cellH.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap.asImageBitmap())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(cellW * 5, cellH)) {
            stageDefs().forEachIndexed { chapter, stage ->
                translate(chapter * cellW, 0f) {
                    clipRect(0f, 0f, cellW, cellH) {
                        drawInkScrollBackdrop(stage, cellW, cellH, 1.4f, chapter)
                        fun center(n: MapNode) = Offset(
                            cellW * (0.06f + n.nx * 0.88f),
                            cellH * (0.18f + n.ny * 0.54f)
                        )
                        stage.nodes.forEach { node ->
                            node.next.forEach { id ->
                                val next = stage.nodes.find { it.id == id } ?: return@forEach
                                drawInkPathStroke(center(node), center(next), node.id <= 1, false, chapter)
                            }
                        }
                        stage.nodes.forEach { node ->
                            drawInkNodeIcon(
                                center(node), if (node.type == NodeType.BOSS) 7f else 5f,
                                node.type, node.resolvedElement(), node.id <= 1, 0.7f, chapter
                            )
                        }
                    }
                }
            }
        }
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "organ_route_maps.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        check(output.length() > 50_000L)
    }
}
