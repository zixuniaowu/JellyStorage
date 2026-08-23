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
    environment: ArenaEnvironment,
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
    val tile = 66f + environment.variant * 9f
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
        val phase = (environment.visualSeed % 17) * 0.11f
        val py = worldH * (0.40f + sin(t * (2.7f + environment.variant * 0.22f) + phase) * 0.11f + cos(t * 1.6f + phase) * 0.05f)
        drawOval(
            palette.path.copy(alpha = 0.22f),
            topLeft = Offset(wx(px - 44f), wy(py - 18f)),
            size = Size(wr(88f), wr(36f))
        )
        if (i % 3 == 0) {
            drawCircle(Color(0x18000000), wr(3.5f), Offset(wx(px + 8f), wy(py + 3f)))
        }
    }

    drawOrganFieldProps(ch, palette, worldW, worldH, time, environment.visualSeed, wx, wy, wr)
    drawRoomIdentityMark(environment, palette, worldW, worldH, wx, wy, wr)

    // 细胞碎屑与纤维，密度较低，避免盖过攻击预警。
    val particleCount = 42
    for (i in 0 until particleCount) {
        val seed = i * 97 + 13
        val bx = ((seed * 53) % 1000) / 1000f * worldW
        val by = ((seed * 91) % 1000) / 1000f * worldH
        val sway = sin(time * 2.0f + i * 0.7f) * 2.2f
        val sx = wx(bx)
        val sy = wy(by)
        if (sx < -20f || sx > screenW + 20f || sy < -20f || sy > screenH + 20f) continue
        val gh = wr(4f + (i % 4))
        drawLine(
            palette.blade.copy(alpha = 0.22f),
            Offset(sx - sway * 0.2f, sy + gh),
            Offset(sx + sway * 0.4f, sy - gh),
            strokeWidth = wr(1.4f),
            cap = StrokeCap.Round
        )
        if (i % 5 == 0) drawCircle(palette.mote.copy(alpha = 0.20f), wr(2.4f), Offset(sx, sy))
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
    1 -> ArenaPalette( // 肺泡：冷蓝气流与淡粉组织
        voidTop = Color(0xFF101827), voidBot = Color(0xFF1E293B),
        fieldTop = Color(0xFFB9D8DE), fieldBot = Color(0xFF7FA9B5),
        patch = Color(0xFF67A6B4), path = Color(0xFFE9A8AE),
        rim = Color(0xFF5B8FA3), haze = Color(0xFFBAE6FD),
        blade = Color(0xFF7DD3FC), mote = Color(0xFFF0F9FF),
        glow = Color(0xFF38BDF8), prop = Color(0xFF4F8191)
    )
    2 -> ArenaPalette( // 胃肠：酸性紫红与菌群荧光
        voidTop = Color(0xFF24101F), voidBot = Color(0xFF3B1630),
        fieldTop = Color(0xFFC9849F), fieldBot = Color(0xFF8F526F),
        patch = Color(0xFFA8557A), path = Color(0xFFF2A65A),
        rim = Color(0xFF9F365F), haze = Color(0xFFF9A8D4),
        blade = Color(0xFFF0ABFC), mote = Color(0xFFFDE68A),
        glow = Color(0xFFFB7185), prop = Color(0xFF7E3155)
    )
    3 -> ArenaPalette( // 肝脏：深赭肝小叶与胆汁色
        voidTop = Color(0xFF1F1110), voidBot = Color(0xFF351815),
        fieldTop = Color(0xFF9E5B4E), fieldBot = Color(0xFF6F382F),
        patch = Color(0xFF7F463A), path = Color(0xFFB8A34A),
        rim = Color(0xFF6B2F28), haze = Color(0xFFF59E8B),
        blade = Color(0xFFD6B85A), mote = Color(0xFFFDE68A),
        glow = Color(0xFFEF7A65), prop = Color(0xFF5A2C27)
    )
    4 -> ArenaPalette( // 心脏：深红心肌与高速血流
        voidTop = Color(0xFF20090D), voidBot = Color(0xFF3F0D17),
        fieldTop = Color(0xFF9F344A), fieldBot = Color(0xFF641E31),
        patch = Color(0xFF7F2440), path = Color(0xFFE76B75),
        rim = Color(0xFF7F1D2D), haze = Color(0xFFFB7185),
        blade = Color(0xFFF43F5E), mote = Color(0xFFFFB4B8),
        glow = Color(0xFFEF4444), prop = Color(0xFF581527)
    )
    else -> ArenaPalette( // 皮肤：暖色表皮与创口组织液
        voidTop = Color(0xFF241514), voidBot = Color(0xFF3A201D),
        fieldTop = Color(0xFFE0B59D), fieldBot = Color(0xFFB97868),
        patch = Color(0xFFC98675), path = Color(0xFF9B3D48),
        rim = Color(0xFF7E4037), haze = Color(0xFFFCA5A5),
        blade = Color(0xFFBE6B61), mote = Color(0xFFFFD1C7),
        glow = Color(0xFFEF6C68), prop = Color(0xFF713B35)
    )
}

/** 每章使用器官结构作地标，保留水墨轮廓但不再出现山石树木。 */
private fun DrawScope.drawOrganFieldProps(
    ch: Int,
    palette: ArenaPalette,
    worldW: Float,
    worldH: Float,
    time: Float,
    roomSeed: Int,
    wx: (Float) -> Float,
    wy: (Float) -> Float,
    wr: (Float) -> Float
) {
    for (i in 0 until 18) {
        val sx = ((i * 173 + 71 + roomSeed * 11) % 1000).let { if (it < 0) it + 1000 else it } / 1000f
        val sy = ((i * 97 + 31 + roomSeed * 7) % 1000).let { if (it < 0) it + 1000 else it } / 1000f
        val x = worldW * (0.07f + sx * 0.86f)
        val y = worldH * (0.08f + sy * 0.84f)
        val p = Offset(wx(x), wy(y))
        when (ch) {
            0 -> { // 表皮细胞与凝血点
                val r = wr(15f + i % 4 * 3f)
                drawCircle(palette.prop.copy(alpha = 0.10f), r, p)
                drawCircle(palette.rim.copy(alpha = 0.30f), r, p, style = Stroke(wr(1.8f)))
                if (i % 3 == 0) drawCircle(Color(0x88FDE68A), wr(4f), p)
            }
            1 -> { // 肺泡簇
                val breath = 1f + sin(time * 1.4f + i) * 0.06f
                for (a in 0 until 4) {
                    val ang = a * 1.5708f + i * 0.2f
                    val c = Offset(p.x + cos(ang) * wr(12f), p.y + sin(ang) * wr(10f))
                    drawCircle(palette.haze.copy(alpha = 0.10f), wr((10f + a) * breath), c)
                    drawCircle(palette.rim.copy(alpha = 0.26f), wr((10f + a) * breath), c, style = Stroke(wr(1.5f)))
                }
            }
            2 -> { // 肠绒毛
                val hh = wr(24f + i % 5 * 4f)
                val sway = wr(sin(time * 1.8f + i) * 3f)
                drawLine(palette.rim.copy(alpha = 0.32f), p, Offset(p.x + sway, p.y - hh), wr(6f), StrokeCap.Round)
                drawCircle(palette.mote.copy(alpha = 0.24f), wr(5f), Offset(p.x + sway, p.y - hh))
            }
            3 -> { // 肝小叶六角环
                val path = Path()
                val r = wr(19f + i % 3 * 4f)
                for (k in 0..6) {
                    val ang = k * 1.0472f
                    val q = Offset(p.x + cos(ang) * r, p.y + sin(ang) * r)
                    if (k == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                }
                drawPath(path, palette.rim.copy(alpha = 0.28f), style = Stroke(wr(1.8f)))
                drawCircle(palette.mote.copy(alpha = 0.18f), wr(4f), p)
            }
            else -> { // 红细胞随心搏流动
                val beat = sin(time * 4.2f + i * 0.5f) * wr(2f)
                drawOval(
                    palette.mote.copy(alpha = 0.22f),
                    topLeft = Offset(p.x - wr(12f) + beat, p.y - wr(6f)),
                    size = Size(wr(24f), wr(12f))
                )
                drawOval(
                    palette.rim.copy(alpha = 0.34f),
                    topLeft = Offset(p.x - wr(12f) + beat, p.y - wr(6f)),
                    size = Size(wr(24f), wr(12f)),
                    style = Stroke(wr(1.6f))
                )
            }
        }
    }
}

private fun DrawScope.drawInkFieldProps(
    ch: Int,
    palette: ArenaPalette,
    worldW: Float,
    worldH: Float,
    time: Float,
    roomSeed: Int,
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
    val rotated = if (landmarks.isEmpty()) landmarks else {
        val shift = (roomSeed and Int.MAX_VALUE) % landmarks.size
        landmarks.drop(shift) + landmarks.take(shift)
    }
    rotated.forEachIndexed { idx, (x, y) ->
        val jitterX = (((roomSeed + idx * 37) % 9) - 4) * worldW * 0.006f
        val jitterY = (((roomSeed + idx * 53) % 9) - 4) * worldH * 0.006f
        when (ch) {
            2 -> drawInkCrystal(wx(x + jitterX), wy(y + jitterY), wr(22f + idx * 1.5f), time + idx, palette)
            1 -> drawInkRock(wx(x + jitterX), wy(y + jitterY), wr(18f + idx % 4 * 3f), palette, angular = true)
            3 -> drawInkRock(wx(x + jitterX), wy(y + jitterY), wr(16f + idx % 3 * 4f), palette, angular = false)
            else -> drawInkPineTop(wx(x + jitterX), wy(y + jitterY), wr(26f + (idx % 3) * 5f), palette)
        }
    }

    for (i in 0 until 14) {
        val rx = ((i * 173 + 41 + roomSeed * 11) % 1000).let { if (it < 0) it + 1000 else it } / 1000f * (worldW - 160f) + 80f
        val ry = ((i * 97 + 19 + roomSeed * 7) % 1000).let { if (it < 0) it + 1000 else it } / 1000f * (worldH - 160f) + 80f
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

/** 大型地面构图与环境技使用同一图形语言，玩家一进房就能认出房间规则。 */
private fun DrawScope.drawRoomIdentityMark(
    environment: ArenaEnvironment,
    palette: ArenaPalette,
    worldW: Float,
    worldH: Float,
    wx: (Float) -> Float,
    wy: (Float) -> Float,
    wr: (Float) -> Float
) {
    if (!environment.active) return
    val color = Color(environment.color)
    val center = Offset(
        wx(worldW * (0.44f + environment.variant * 0.06f)),
        wy(worldH * (0.46f + ((environment.visualSeed % 3) - 1) * 0.06f))
    )
    when (environment.pattern) {
        ArenaHazardPattern.CIRCLE -> {
            val spots = listOf(-0.24f to -0.12f, 0.18f to -0.18f, 0.06f to 0.22f)
            spots.forEachIndexed { index, (ox, oy) ->
                val c = Offset(wx(worldW * (0.5f + ox)), wy(worldH * (0.5f + oy)))
                drawCircle(color.copy(alpha = 0.07f), wr(58f + index * 9f), c)
                drawCircle(color.copy(alpha = 0.22f), wr(45f + index * 8f), c, style = Stroke(wr(2f)))
                drawCircle(palette.rim.copy(alpha = 0.14f), wr(12f), c)
            }
        }
        ArenaHazardPattern.RING -> {
            repeat(3) { index ->
                drawCircle(
                    color.copy(alpha = 0.18f - index * 0.035f),
                    wr(78f + index * 58f), center,
                    style = Stroke(wr(2.2f + index * 0.5f))
                )
            }
            repeat(8) { index ->
                val a = index * 0.785f
                val px = center.x + cos(a) * wr(190f)
                val py = center.y + sin(a) * wr(190f)
                drawCircle(color.copy(alpha = 0.32f), wr(5f), Offset(px, py))
            }
        }
        ArenaHazardPattern.LINE -> {
            val horizontal = (environment.visualSeed and 1) == 0
            repeat(3) { index ->
                val offset = (index - 1) * 94f
                if (horizontal) {
                    val y = wy(worldH * 0.5f + offset)
                    drawLine(color.copy(alpha = 0.16f), Offset(wx(55f), y), Offset(wx(worldW - 55f), y), wr(13f), StrokeCap.Round)
                    drawLine(palette.rim.copy(alpha = 0.28f), Offset(wx(55f), y), Offset(wx(worldW - 55f), y), wr(2f))
                } else {
                    val x = wx(worldW * 0.5f + offset)
                    drawLine(color.copy(alpha = 0.16f), Offset(x, wy(55f)), Offset(x, wy(worldH - 55f)), wr(13f), StrokeCap.Round)
                    drawLine(palette.rim.copy(alpha = 0.28f), Offset(x, wy(55f)), Offset(x, wy(worldH - 55f)), wr(2f))
                }
            }
        }
        ArenaHazardPattern.NONE -> Unit
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
