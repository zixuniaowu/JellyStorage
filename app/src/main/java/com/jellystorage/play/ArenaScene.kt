package com.jellystorage.play

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Top-down arena painting — 古画/水墨地席，与地图章节同色系。
 * Ground lives in **world space** so characters never float over a fake horizon.
 */
fun DrawScope.drawArenaBackdrop(
    chapterIndex: Int,
    screenW: Float,
    screenH: Float,
    camX: Float,
    camY: Float,
    time: Float,
    worldW: Float,
    worldH: Float,
    wx: (Float) -> Float,
    wy: (Float) -> Float,
    wr: (Float) -> Float
) {
    val ch = chapterIndex.coerceAtLeast(0) % 5
    val palette = arenaPalette(ch)

    // 画外虚空：深墨/夜色，像卷轴外
    drawRect(Brush.verticalGradient(listOf(palette.voidTop, palette.voidBot)), size = Size(screenW, screenH))

    // 远景晕染圆
    drawCircle(
        palette.haze.copy(alpha = 0.14f),
        wr(worldW * 0.55f),
        Offset(wx(worldW * 0.5f), wy(worldH * 0.5f))
    )

    val fieldTL = Offset(wx(0f), wy(0f))
    val fieldBR = Offset(wx(worldW), wy(worldH))
    val fieldW = (fieldBR.x - fieldTL.x).coerceAtLeast(1f)
    val fieldH = (fieldBR.y - fieldTL.y).coerceAtLeast(1f)

    // 宣纸/绢本戏台
    drawRoundRect(
        Brush.verticalGradient(listOf(palette.fieldTop, palette.fieldBot)),
        topLeft = fieldTL,
        size = Size(fieldW, fieldH),
        cornerRadius = CornerRadius(wr(26f), wr(26f))
    )
    // 赭/朱边框
    drawRoundRect(
        palette.rim.copy(alpha = 0.55f),
        topLeft = fieldTL,
        size = Size(fieldW, fieldH),
        cornerRadius = CornerRadius(wr(26f), wr(26f)),
        style = Stroke(wr(3.2f))
    )
    drawRoundRect(
        Color(0x22000000),
        topLeft = Offset(fieldTL.x + wr(6f), fieldTL.y + wr(6f)),
        size = Size(fieldW - wr(12f), fieldH - wr(12f)),
        cornerRadius = CornerRadius(wr(20f), wr(20f)),
        style = Stroke(wr(8f))
    )

    // 纸纹方格（淡）
    val tile = 72f
    var gy = 0f
    var row = 0
    while (gy < worldH) {
        var gx = 0f
        var col = 0
        while (gx < worldW) {
            if (((row + col) and 1) == 0) {
                drawRoundRect(
                    palette.patch.copy(alpha = 0.045f),
                    topLeft = Offset(wx(gx + 5f), wy(gy + 5f)),
                    size = Size(wr(tile - 12f), wr(tile - 12f)),
                    cornerRadius = CornerRadius(wr(8f), wr(8f))
                )
            }
            if ((col * 13 + row * 7) % 6 == 0) {
                drawCircle(
                    Color(0x14000000),
                    wr(2.5f + (col % 3)),
                    Offset(wx(gx + 20f + (row % 4) * 5f), wy(gy + 16f + (col % 3) * 4f))
                )
            }
            gx += tile
            col++
        }
        gy += tile
        row++
    }

    // 墨道（中锋蜿蜒）
    for (i in 0..20) {
        val t = i / 20f
        val px = worldW * (0.10f + t * 0.80f)
        val py = worldH * (0.40f + sin(t * 3.0f) * 0.11f + cos(t * 1.6f) * 0.05f)
        drawOval(
            palette.path.copy(alpha = 0.22f),
            topLeft = Offset(wx(px - 44f), wy(py - 18f)),
            size = Size(wr(88f), wr(36f))
        )
        if (i % 3 == 0) {
            drawCircle(Color(0x18000000), wr(3.5f), Offset(wx(px + 8f), wy(py + 3f)))
        }
    }

    drawInkFieldProps(ch, palette, worldW, worldH, time, wx, wy, wr)

    // 草/雪/墨点装饰（按章）
    val bladeCount = 70
    for (i in 0 until bladeCount) {
        val seed = i * 97 + 13
        val bx = ((seed * 53) % 1000) / 1000f * worldW
        val by = ((seed * 91) % 1000) / 1000f * worldH
        val sway = sin(time * 2.0f + i * 0.7f) * 2.2f
        val sx = wx(bx)
        val sy = wy(by)
        if (sx < -20f || sx > screenW + 20f || sy < -20f || sy > screenH + 20f) continue
        when (ch) {
            2 -> { // 雪点
                drawCircle(Color(0x88F8FAFC), wr(1.4f + (i % 3) * 0.5f), Offset(sx, sy))
            }
            3 -> { // 墨点
                drawCircle(Color(0x223F3F46), wr(2f + (i % 2)), Offset(sx + sway * 0.3f, sy))
            }
            else -> {
                val gh = wr(6f + (i % 5))
                drawLine(
                    palette.blade.copy(alpha = 0.45f),
                    Offset(sx, sy),
                    Offset(sx + sway, sy - gh),
                    strokeWidth = wr(1.6f),
                    cap = StrokeCap.Round
                )
                if (i % 7 == 0 && ch == 0) {
                    drawCircle(Color(0x88B91C1C).copy(alpha = 0.35f), wr(2.2f), Offset(sx + sway, sy - gh))
                }
            }
        }
    }

    // 花粉 / 萤火 / 金尘
    for (i in 0 until 14) {
        val px = (sin(time * 0.35f + i * 1.7f) * 0.45f + 0.5f) * worldW
        val py = (cos(time * 0.48f + i * 1.3f) * 0.4f + 0.5f) * worldH
        val pulse = 0.3f + 0.3f * sin(time * 2.6f + i)
        drawCircle(palette.mote.copy(alpha = pulse * 0.5f), wr(2.2f + (i % 3) * 0.4f), Offset(wx(px), wy(py)))
    }

    // 场地中心光晕
    drawCircle(
        palette.glow.copy(alpha = 0.10f),
        wr(200f),
        Offset(wx(worldW * 0.5f), wy(worldH * 0.48f))
    )

    // 夜章：淡淡阵纹
    if (ch == 2 || ch == 3) {
        for (i in 0 until 2) {
            val rr = 90f + i * 85f
            drawCircle(
                palette.rim.copy(alpha = 0.12f),
                wr(rr),
                Offset(wx(worldW * 0.5f), wy(worldH * 0.5f)),
                style = Stroke(wr(1.8f))
            )
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val keepCam = camX + camY
}

private data class ArenaPalette(
    val voidTop: Color,
    val voidBot: Color,
    val fieldTop: Color,
    val fieldBot: Color,
    val patch: Color,
    val path: Color,
    val rim: Color,
    val haze: Color,
    val blade: Color,
    val mote: Color,
    val glow: Color,
    val prop: Color
)

private fun arenaPalette(ch: Int): ArenaPalette = when (ch) {
    1 -> ArenaPalette( // 秋壑
        voidTop = Color(0xFF1A120C), voidBot = Color(0xFF2A1C12),
        fieldTop = Color(0xFFC4A574), fieldBot = Color(0xFF9A7348),
        patch = Color(0xFF8B5E34), path = Color(0xFF5C4033),
        rim = Color(0xFFB45309), haze = Color(0xFFD97706),
        blade = Color(0xFFA16207), mote = Color(0xFFFBBF24),
        glow = Color(0xFFF59E0B), prop = Color(0xFF5C4033)
    )
    2 -> ArenaPalette( // 雪夜
        voidTop = Color(0xFF0A0E14), voidBot = Color(0xFF141A24),
        fieldTop = Color(0xFF3A4558), fieldBot = Color(0xFF252C3A),
        patch = Color(0xFF4B5568), path = Color(0xFF64748B),
        rim = Color(0xFF94A3B8), haze = Color(0xFFE2E8F0),
        blade = Color(0xFFCBD5E1), mote = Color(0xFFF8FAFC),
        glow = Color(0xFF7DD3FC), prop = Color(0xFF475569)
    )
    3 -> ArenaPalette( // 墨海
        voidTop = Color(0xFF12110F), voidBot = Color(0xFF1C1B18),
        fieldTop = Color(0xFFB8B0A0), fieldBot = Color(0xFF8A8478),
        patch = Color(0xFF6B655C), path = Color(0xFF3F3F46),
        rim = Color(0xFF52525B), haze = Color(0xFFE7E5E4),
        blade = Color(0xFF57534E), mote = Color(0xFFD6D3D1),
        glow = Color(0xFFA8A29E), prop = Color(0xFF44403C)
    )
    4 -> ArenaPalette( // 空翠峰
        voidTop = Color(0xFF0C1410), voidBot = Color(0xFF152018),
        fieldTop = Color(0xFFA3B89E), fieldBot = Color(0xFF6F8F72),
        patch = Color(0xFF4A6741), path = Color(0xFF3F6212),
        rim = Color(0xFF365B37), haze = Color(0xFF86EFAC),
        blade = Color(0xFF4D7C0F), mote = Color(0xFFBBF7D0),
        glow = Color(0xFF4ADE80), prop = Color(0xFF365B37)
    )
    else -> ArenaPalette( // 春山
        voidTop = Color(0xFF0E1612), voidBot = Color(0xFF1A261C),
        fieldTop = Color(0xFFD4C4A0), fieldBot = Color(0xFFA8B88A),
        patch = Color(0xFF6B8F5A), path = Color(0xFF78716C),
        rim = Color(0xFF5C4033), haze = Color(0xFF86EFAC),
        blade = Color(0xFF3F6212), mote = Color(0xFFFDE68A),
        glow = Color(0xFFB45309), prop = Color(0xFF1F3D2A)
    )
}

private fun DrawScope.drawInkFieldProps(
    ch: Int,
    palette: ArenaPalette,
    worldW: Float,
    worldH: Float,
    time: Float,
    wx: (Float) -> Float,
    wy: (Float) -> Float,
    wr: (Float) -> Float
) {
    val landmarks = listOf(
        worldW * 0.12f to worldH * 0.18f,
        worldW * 0.88f to worldH * 0.20f,
        worldW * 0.15f to worldH * 0.82f,
        worldW * 0.86f to worldH * 0.80f,
        worldW * 0.50f to worldH * 0.12f,
        worldW * 0.50f to worldH * 0.88f,
        worldW * 0.28f to worldH * 0.30f,
        worldW * 0.72f to worldH * 0.72f
    )
    landmarks.forEachIndexed { idx, (x, y) ->
        when (ch) {
            2 -> drawInkCrystal(wx(x), wy(y), wr(22f + idx * 1.5f), time + idx, palette)
            1 -> drawInkRock(wx(x), wy(y), wr(18f + idx % 4 * 3f), palette, angular = true)
            3 -> drawInkRock(wx(x), wy(y), wr(16f + idx % 3 * 4f), palette, angular = false)
            else -> drawInkPineTop(wx(x), wy(y), wr(26f + (idx % 3) * 5f), palette)
        }
    }

    for (i in 0 until 14) {
        val rx = ((i * 173 + 41) % 1000) / 1000f * (worldW - 160f) + 80f
        val ry = ((i * 97 + 19) % 1000) / 1000f * (worldH - 160f) + 80f
        if (rx in worldW * 0.42f..worldW * 0.58f && ry in worldH * 0.40f..worldH * 0.60f) continue
        drawInkRock(wx(rx), wy(ry), wr(9f + i % 6), palette, angular = ch == 1)
    }

    for (i in 0 until 12) {
        val ang = i / 12f * 6.28318f
        val bx = worldW * 0.5f + cos(ang) * worldW * 0.36f
        val by = worldH * 0.5f + sin(ang) * worldH * 0.34f
        drawInkBush(wx(bx), wy(by), wr(14f + i % 5), palette)
    }
}

private fun DrawScope.drawInkPineTop(cx: Float, cy: Float, s: Float, palette: ArenaPalette) {
    drawOval(Color(0x40000000), topLeft = Offset(cx - s * 0.7f, cy + s * 0.35f), size = Size(s * 1.4f, s * 0.32f))
    drawLine(palette.prop, Offset(cx, cy + s * 0.35f), Offset(cx, cy - s * 0.9f), s * 0.08f, StrokeCap.Round)
    for (i in 0..3) {
        val y = cy - s * (0.15f + i * 0.22f)
        val spread = s * (0.55f - i * 0.08f)
        drawLine(palette.prop.copy(alpha = 0.9f), Offset(cx, y), Offset(cx - spread, y + s * 0.12f), s * 0.07f, StrokeCap.Round)
        drawLine(palette.prop.copy(alpha = 0.9f), Offset(cx, y), Offset(cx + spread, y + s * 0.12f), s * 0.07f, StrokeCap.Round)
    }
}

private fun DrawScope.drawInkCrystal(cx: Float, cy: Float, s: Float, t: Float, palette: ArenaPalette) {
    drawOval(Color(0x45000000), topLeft = Offset(cx - s * 0.65f, cy + s * 0.35f), size = Size(s * 1.3f, s * 0.3f))
    val path = Path()
    path.moveTo(cx, cy - s * 1.1f)
    path.lineTo(cx + s * 0.5f, cy + s * 0.15f)
    path.lineTo(cx, cy + s * 0.5f)
    path.lineTo(cx - s * 0.5f, cy + s * 0.15f)
    path.close()
    drawPath(path, palette.prop.copy(alpha = 0.75f))
    drawPath(path, palette.rim.copy(alpha = 0.55f), style = Stroke(s * 0.07f))
    val pulse = 0.2f + 0.15f * sin(t * 2.2f)
    drawCircle(palette.mote.copy(alpha = pulse), s * 0.28f, Offset(cx, cy - s * 0.15f))
}

private fun DrawScope.drawInkRock(cx: Float, cy: Float, s: Float, palette: ArenaPalette, angular: Boolean) {
    drawOval(Color(0x38000000), topLeft = Offset(cx - s * 0.85f, cy + s * 0.2f), size = Size(s * 1.7f, s * 0.4f))
    if (angular) {
        val path = Path()
        path.moveTo(cx - s, cy)
        path.lineTo(cx - s * 0.3f, cy - s * 0.7f)
        path.lineTo(cx + s * 0.6f, cy - s * 0.45f)
        path.lineTo(cx + s, cy + s * 0.15f)
        path.lineTo(cx - s * 0.2f, cy + s * 0.35f)
        path.close()
        drawPath(path, palette.prop.copy(alpha = 0.7f))
        drawPath(path, Color.White.copy(alpha = 0.08f), style = Stroke(1.5f))
    } else {
        drawOval(palette.prop.copy(alpha = 0.65f), topLeft = Offset(cx - s, cy - s * 0.5f), size = Size(s * 2f, s * 1.1f))
        drawOval(Color.White.copy(alpha = 0.08f), topLeft = Offset(cx - s * 0.5f, cy - s * 0.45f), size = Size(s * 0.65f, s * 0.3f))
    }
}

private fun DrawScope.drawInkBush(cx: Float, cy: Float, s: Float, palette: ArenaPalette) {
    drawOval(Color(0x35000000), topLeft = Offset(cx - s * 1.0f, cy + s * 0.15f), size = Size(s * 2.0f, s * 0.4f))
    drawCircle(palette.prop.copy(alpha = 0.55f), s * 0.55f, Offset(cx - s * 0.35f, cy))
    drawCircle(palette.prop.copy(alpha = 0.55f), s * 0.55f, Offset(cx + s * 0.35f, cy))
    drawCircle(palette.blade.copy(alpha = 0.45f), s * 0.5f, Offset(cx, cy - s * 0.2f))
}

fun DrawScope.drawArenaVignette(w: Float, h: Float) {
    drawRect(
        Brush.verticalGradient(listOf(Color(0x552E1A0A), Color.Transparent)),
        topLeft = Offset(0f, 0f),
        size = Size(w, h * 0.12f)
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x663D2914))),
        topLeft = Offset(0f, h * 0.78f),
        size = Size(w, h * 0.22f)
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color(0x442E1A0A), Color.Transparent)),
        topLeft = Offset(0f, 0f),
        size = Size(w * 0.1f, h)
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, Color(0x442E1A0A))),
        topLeft = Offset(w * 0.9f, 0f),
        size = Size(w * 0.1f, h)
    )
}

fun DrawScope.drawShadowDisk(cx: Float, cy: Float, r: Float) {
    drawOval(
        Color(0x55000000),
        topLeft = Offset(cx - r * 0.85f, cy + r * 0.55f),
        size = Size(r * 1.7f, r * 0.38f)
    )
}

/** 技能钮：墨钮 + 朱/彩釉，偏卷轴风 */
fun DrawScope.drawSkillChrome(
    cx: Float,
    cy: Float,
    r: Float,
    fill: Color,
    held: Boolean,
    ready: Boolean
) {
    drawCircle(Color(0x66000000), r + 4f, Offset(cx + 2f, cy + 5f))
    // 外圈朱/墨环
    drawCircle(Color(0xFF2C1810), r + 5f, Offset(cx, cy))
    drawCircle(
        if (ready) fill.copy(alpha = 0.85f) else Color(0xFF57534E),
        r + 5f,
        Offset(cx, cy),
        style = Stroke(if (held) 3.5f else 2.2f)
    )
    val body = if (!ready) Color(0xFF292524) else fill
    drawCircle(
        Brush.radialGradient(
            listOf(
                body.copy(
                    red = (body.red * 1.12f).coerceIn(0f, 1f),
                    green = (body.green * 1.12f).coerceIn(0f, 1f),
                    blue = (body.blue * 1.12f).coerceIn(0f, 1f)
                ),
                body,
                body.copy(
                    red = body.red * 0.55f,
                    green = body.green * 0.55f,
                    blue = body.blue * 0.55f
                )
            ),
            center = Offset(cx - r * 0.22f, cy - r * 0.28f),
            radius = r * 1.25f
        ),
        r,
        Offset(cx, cy)
    )
    // 宣纸高光
    drawOval(
        Color(0xFFF5EBD4).copy(alpha = if (held) 0.28f else 0.14f),
        topLeft = Offset(cx - r * 0.5f, cy - r * 0.65f),
        size = Size(r * 1.0f, r * 0.48f)
    )
    if (!ready) {
        drawCircle(Color(0x88000000), r, Offset(cx, cy))
    }
}
