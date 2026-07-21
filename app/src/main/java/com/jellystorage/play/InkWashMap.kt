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
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 古画 / 水墨卷轴风地图布景。
 * 分层：宣纸底 → 远山淡墨 → 中景 → 近景雾气 → 朱印点缀。
 */
fun DrawScope.drawInkScrollBackdrop(stage: StageDef, w: Float, h: Float, pulse: Float, inkRank: Int = 0) {
    // 宣纸/绢本底色（偏暖，像旧画）
    val paper = listOf(
        Color(0xFFF3E9D2),
        Color(0xFFE8D9B8),
        Color(0xFFD9C7A0)
    )
    drawRect(Brush.verticalGradient(paper), size = Size(w, h))

    // 细纤维纹理
    val rng = Random(stage.id * 97L + inkRank * 13L)
    for (i in 0..40) {
        val x = rng.nextFloat() * w
        val y = rng.nextFloat() * h
        drawLine(
            Color(0x14A16207),
            Offset(x, y),
            Offset(x + rng.nextFloat() * 28f - 8f, y + rng.nextFloat() * 6f - 3f),
            1.2f
        )
    }

    when (stage.chapterIndex % 5) {
        0 -> drawInkSpringHills(w, h, pulse, rng)      // 青绿春山
        1 -> drawInkAutumnGorge(w, h, pulse, rng)     // 赭墨秋壑
        2 -> drawInkNightSnow(w, h, pulse, rng)       // 雪夜乌金
        3 -> drawInkMistSea(w, h, pulse, rng)         // 墨海云涛
        else -> drawInkPeak(w, h, pulse, rng)         // 空翠奇峰
    }

    // 卷轴上下暗边
    drawRect(
        Brush.verticalGradient(listOf(Color(0x552E1A0A), Color.Transparent)),
        size = Size(w, h * 0.10f)
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x663D2914))),
        topLeft = Offset(0f, h * 0.78f),
        size = Size(w, h * 0.22f)
    )
    // 左右轴杆
    drawRoundRect(Color(0xFF5C3A1E), Offset(-6f, h * 0.05f), Size(14f, h * 0.9f), CornerRadius(6f))
    drawRoundRect(Color(0xFF5C3A1E), Offset(w - 8f, h * 0.05f), Size(14f, h * 0.9f), CornerRadius(6f))
    drawCircle(Color(0xFF8B5A2B), 10f, Offset(4f, h * 0.08f))
    drawCircle(Color(0xFF8B5A2B), 10f, Offset(w - 4f, h * 0.08f))

    // 朱文印
    drawSealStamp(w * 0.90f, h * 0.22f, 22f + (inkRank % 3) * 2f, "果")
    if (inkRank > 0) {
        drawSealStamp(w * 0.86f, h * 0.32f, 16f, "墨$inkRank")
    }
}

private fun DrawScope.drawSealStamp(cx: Float, cy: Float, s: Float, textHint: String) {
    drawRoundRect(
        Color(0xBBB91C1C),
        Offset(cx - s, cy - s),
        Size(s * 2f, s * 2f),
        CornerRadius(3f),
        style = Stroke(2.5f)
    )
    drawRoundRect(
        Color(0x33B91C1C),
        Offset(cx - s * 0.85f, cy - s * 0.85f),
        Size(s * 1.7f, s * 1.7f),
        CornerRadius(2f)
    )
    // 印文用抽象笔触代替字体（Canvas 无字库保证）
    drawLine(Color(0xDDB91C1C), Offset(cx - s * 0.45f, cy - s * 0.3f), Offset(cx + s * 0.4f, cy - s * 0.35f), 2.5f)
    drawLine(Color(0xDDB91C1C), Offset(cx - s * 0.35f, cy), Offset(cx + s * 0.35f, cy + s * 0.05f), 2.5f)
    drawLine(Color(0xDDB91C1C), Offset(cx - s * 0.25f, cy + s * 0.35f), Offset(cx + s * 0.3f, cy + s * 0.3f), 2.2f)
    @Suppress("UNUSED_VARIABLE")
    val keep = textHint
}

/** 第一章：青绿春山 */
private fun DrawScope.drawInkSpringHills(w: Float, h: Float, pulse: Float, rng: Random) {
    // 远山三层
    drawMountainLayer(w, h * 0.38f, h * 0.28f, Color(0x55334E3B), 0.15f, pulse * 0.1f)
    drawMountainLayer(w, h * 0.48f, h * 0.32f, Color(0x663D5C45), 0.28f, pulse * 0.15f + 1f)
    drawMountainLayer(w, h * 0.58f, h * 0.35f, Color(0x774A6741), 0.45f, pulse * 0.12f + 2f)
    // 雾带
    drawOval(Color(0x55F5F0E1), topLeft = Offset(-w * 0.05f, h * 0.42f), size = Size(w * 1.1f, h * 0.12f))
    drawOval(Color(0x44E8DFC8), topLeft = Offset(w * 0.1f, h * 0.55f), size = Size(w * 0.9f, h * 0.10f))
    // 近树剪影
    for (i in 0..6) {
        val x = w * (0.06f + i * 0.12f)
        val y = h * (0.62f + 0.02f * sin(pulse + i))
        drawInkPine(x, y, 18f + (i % 3) * 4f, Color(0xAA1F3D2A))
    }
    // 水墨太阳（淡朱）
    drawCircle(Color(0x33C2410C), 36f + sin(pulse) * 3f, Offset(w * 0.82f, h * 0.24f))
    drawCircle(Color(0x55B45309), 18f, Offset(w * 0.82f, h * 0.24f))
}

/** 第二章：秋壑矿脉 — 赭石+黛墨 */
private fun DrawScope.drawInkAutumnGorge(w: Float, h: Float, pulse: Float, rng: Random) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE7D3B0), Color(0xFFC4A574))), size = Size(w, h))
    drawMountainLayer(w, h * 0.40f, h * 0.30f, Color(0x665C4033), 0.2f, 0f)
    drawMountainLayer(w, h * 0.52f, h * 0.34f, Color(0x77422A1A), 0.35f, 1.2f)
    // 峡谷裂缝
    val path = Path().apply {
        moveTo(w * 0.45f, h * 0.25f)
        cubicTo(w * 0.48f, h * 0.4f, w * 0.42f, h * 0.55f, w * 0.5f, h * 0.72f)
        lineTo(w * 0.55f, h * 0.72f)
        cubicTo(w * 0.5f, h * 0.55f, w * 0.55f, h * 0.4f, w * 0.52f, h * 0.25f)
        close()
    }
    drawPath(path, Color(0x442E1A0A))
    // 矿光（像矿脉的暖点）
    for (i in 0..5) {
        val px = w * (0.2f + i * 0.12f)
        val py = h * (0.5f + 0.05f * sin(pulse * 2f + i))
        drawCircle(Color(0x55D97706), 8f + (i % 2) * 3f, Offset(px, py))
    }
    drawOval(Color(0x44E8DFC8), topLeft = Offset(0f, h * 0.48f), size = Size(w, h * 0.14f))
}

/** 第三章：雪夜乌金 */
private fun DrawScope.drawInkNightSnow(w: Float, h: Float, pulse: Float, rng: Random) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2C3340), Color(0xFF1A1F28), Color(0xFF0F1419))), size = Size(w, h))
    drawMountainLayer(w, h * 0.42f, h * 0.3f, Color(0x55333B4A), 0.25f, 0f)
    drawMountainLayer(w, h * 0.55f, h * 0.32f, Color(0x66202835), 0.4f, 1f)
    // 月
    drawCircle(Color(0x33E2E8F0), 42f, Offset(w * 0.86f, h * 0.22f))
    drawCircle(Color(0xAAE2E8F0), 20f, Offset(w * 0.86f, h * 0.22f))
    // 雪点
    for (i in 0..28) {
        val px = (i * 97f + pulse * 12f) % w
        val py = (i * 53f + pulse * 18f) % (h * 0.7f)
        drawCircle(Color(0x88F8FAFC), 1.5f + (i % 3), Offset(px, py))
    }
    // 飞檐剪影
    for (i in 0..3) {
        val tx = w * (0.12f + i * 0.2f)
        drawRoundRect(Color(0x66334155), Offset(tx, h * 0.48f), Size(w * 0.08f, h * 0.2f), CornerRadius(2f))
        drawLine(Color(0x88CBD5E1), Offset(tx - 6f, h * 0.48f), Offset(tx + w * 0.08f + 6f, h * 0.48f), 3f)
    }
}

/** 第四章：墨海 */
private fun DrawScope.drawInkMistSea(w: Float, h: Float, pulse: Float, rng: Random) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFEDE6D6), Color(0xFFC9C2B0), Color(0xFF9A9588))), size = Size(w, h))
    // 云涛层
    for (i in 0..4) {
        val y = h * (0.35f + i * 0.08f)
        drawOval(
            Color(0x22FFFFFF).copy(alpha = 0.12f + i * 0.04f),
            topLeft = Offset(-w * 0.1f + sin(pulse + i) * 20f, y),
            size = Size(w * 1.2f, h * 0.1f)
        )
    }
    drawMountainLayer(w, h * 0.55f, h * 0.28f, Color(0x553F3F46), 0.5f, pulse)
    // 孤舟点
    drawLine(Color(0xFF3F3F46), Offset(w * 0.7f, h * 0.58f), Offset(w * 0.78f, h * 0.58f), 3f)
    drawLine(Color(0xFF52525B), Offset(w * 0.74f, h * 0.58f), Offset(w * 0.74f, h * 0.52f), 2f)
}

/** 第五章：空翠奇峰 */
private fun DrawScope.drawInkPeak(w: Float, h: Float, pulse: Float, rng: Random) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE8F0E8), Color(0xFFC5D5C0), Color(0xFF8FA88C))), size = Size(w, h))
    drawMountainLayer(w, h * 0.36f, h * 0.4f, Color(0x442F4F3A), 0.35f, 0f)
    // 主峰
    val peak = Path().apply {
        moveTo(w * 0.55f, h * 0.22f)
        lineTo(w * 0.35f, h * 0.65f)
        lineTo(w * 0.75f, h * 0.65f)
        close()
    }
    drawPath(peak, Color(0x55364B3A))
    drawPath(peak, Color(0x88364B3A), style = Stroke(2f))
    // 飞瀑
    drawLine(Color(0x66E0F2FE), Offset(w * 0.55f, h * 0.30f), Offset(w * 0.52f, h * 0.62f), 6f)
    drawLine(Color(0x44FFFFFF), Offset(w * 0.57f, h * 0.32f), Offset(w * 0.54f, h * 0.60f), 3f)
    drawOval(Color(0x33F8FAFC), topLeft = Offset(w * 0.2f, h * 0.5f), size = Size(w * 0.6f, h * 0.12f))
}

private fun DrawScope.drawMountainLayer(
    w: Float,
    baseY: Float,
    height: Float,
    color: Color,
    roughness: Float,
    phase: Float
) {
    val path = Path()
    path.moveTo(0f, baseY + height)
    path.lineTo(0f, baseY + height * 0.6f)
    var x = 0f
    var i = 0
    while (x < w) {
        val y = baseY + sin(x * 0.008f + phase + i * 0.7f) * height * roughness +
            cos(x * 0.015f + phase) * height * 0.15f
        path.lineTo(x, y.coerceIn(baseY - height * 0.2f, baseY + height))
        x += w * 0.04f
        i++
    }
    path.lineTo(w, baseY + height)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawInkPine(cx: Float, baseY: Float, s: Float, col: Color) {
    drawLine(col, Offset(cx, baseY), Offset(cx, baseY - s * 1.4f), 2.5f, StrokeCap.Round)
    for (i in 0..3) {
        val y = baseY - s * (0.4f + i * 0.25f)
        val spread = s * (0.55f - i * 0.08f)
        drawLine(col, Offset(cx, y), Offset(cx - spread, y + s * 0.15f), 2f, StrokeCap.Round)
        drawLine(col, Offset(cx, y), Offset(cx + spread, y + s * 0.15f), 2f, StrokeCap.Round)
    }
}

/** 水墨风节点：朱圈 / 墨点 / 印泥高亮 */
fun DrawScope.drawInkNodeIcon(
    c: Offset,
    r: Float,
    type: NodeType,
    el: WuXing?,
    lit: Boolean,
    pulse: Float
) {
    val ink = Color(0xFF2C2416)
    val cinnabar = Color(0xFFB91C1C)
    val jade = Color(0xFF3F6212)
    if (lit) {
        drawCircle(cinnabar.copy(alpha = 0.18f + 0.08f * pulse), r + 12f, c)
        drawCircle(cinnabar.copy(alpha = 0.55f), r + 5f, c, style = Stroke(2.2f))
    }
    // 宣纸圆底
    drawCircle(Color(0xFFF5EBD4), r, c)
    drawCircle(ink.copy(alpha = 0.75f), r, c, style = Stroke(if (lit) 2.8f else 1.8f))
    if (el != null && (type == NodeType.MOB || type == NodeType.ELITE || type == NodeType.BOSS)) {
        drawCircle(el.color.copy(alpha = 0.45f), r * 0.78f, c, style = Stroke(2.2f))
    }
    when (type) {
        NodeType.MOB -> {
            drawCircle(jade.copy(alpha = 0.85f), r * 0.38f, Offset(c.x, c.y + r * 0.05f))
            drawCircle(Color(0x33FFFFFF), r * 0.12f, Offset(c.x - r * 0.12f, c.y))
        }
        NodeType.ELITE -> {
            drawCircle(Color(0xFFB45309), r * 0.4f, c)
            drawCircle(cinnabar, r * 0.12f, Offset(c.x, c.y - r * 0.55f))
        }
        NodeType.BOSS -> {
            drawCircle(Color(0xFF44403C), r * 0.42f, c)
            drawCircle(cinnabar, r * 0.14f, Offset(c.x - r * 0.15f, c.y - r * 0.08f))
            drawCircle(cinnabar, r * 0.14f, Offset(c.x + r * 0.18f, c.y - r * 0.08f))
        }
        NodeType.SHOP -> {
            drawRoundRect(Color(0xFF78716C), Offset(c.x - r * 0.35f, c.y - r * 0.08f), Size(r * 0.7f, r * 0.3f), CornerRadius(3f))
            drawCircle(Color(0xFFD97706), r * 0.12f, Offset(c.x + r * 0.22f, c.y - r * 0.22f))
        }
        NodeType.GOLD -> drawCircle(Color(0xFFCA8A04), r * 0.36f, c)
        NodeType.HEAL, NodeType.REST -> {
            drawCircle(Color(0xFF4D7C0F), r * 0.34f, c)
            drawLine(Color(0xFFF5EBD4), Offset(c.x, c.y - r * 0.2f), Offset(c.x, c.y + r * 0.2f), 3f, StrokeCap.Round)
            drawLine(Color(0xFFF5EBD4), Offset(c.x - r * 0.2f, c.y), Offset(c.x + r * 0.2f, c.y), 3f, StrokeCap.Round)
        }
        NodeType.TRAP -> {
            drawCircle(cinnabar, r * 0.3f, c)
            for (i in 0..3) {
                val a = i * 1.5f
                drawLine(Color(0xFFFECACA), c, Offset(c.x + cos(a) * r * 0.42f, c.y + sin(a) * r * 0.42f), 2.5f)
            }
        }
        NodeType.EVENT -> {
            drawCircle(Color(0xFF9F1239), r * 0.34f, c)
            drawCircle(Color(0xFFF5EBD4), r * 0.1f, Offset(c.x, c.y - r * 0.06f))
        }
        NodeType.START -> drawCircle(Color(0xFF78716C), r * 0.28f, c)
        NodeType.EXIT -> {
            drawRoundRect(Color(0xFF0F766E), Offset(c.x - r * 0.32f, c.y - r * 0.38f), Size(r * 0.64f, r * 0.72f), CornerRadius(4f))
            drawCircle(Color(0xFFF5EBD4), r * 0.08f, Offset(c.x + r * 0.12f, c.y))
        }
    }
}

/** 地图路线：中锋笔触（主墨 + 侧锋淡墨） */
fun DrawScope.drawInkPathStroke(
    a: Offset,
    b: Offset,
    active: Boolean,
    visited: Boolean
) {
    val mid = Offset((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
    // 轻微弧线，像毛笔走线
    val dx = b.x - a.x
    val dy = b.y - a.y
    val nx = -dy * 0.08f
    val ny = dx * 0.08f
    val c1 = Offset(a.x + dx * 0.35f + nx, a.y + dy * 0.35f + ny)
    val c2 = Offset(a.x + dx * 0.65f - nx * 0.6f, a.y + dy * 0.65f - ny * 0.6f)
    val path = Path().apply {
        moveTo(a.x, a.y)
        cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
    }
    val soft = when {
        active -> Color(0x55B45309)
        visited -> Color(0x332F4F3A)
        else -> Color(0x221C1917)
    }
    val main = when {
        active -> Color(0xDDB91C1C)
        visited -> Color(0x884A6741)
        else -> Color(0x553F3F46)
    }
    val wMain = if (active) 4.2f else if (visited) 2.8f else 1.8f
    drawPath(path, soft, style = Stroke(width = wMain + 4f, cap = StrokeCap.Round))
    drawPath(path, main, style = Stroke(width = wMain, cap = StrokeCap.Round))
    if (active) {
        drawCircle(Color(0x88B91C1C), 3.5f, mid)
    }
}

/** 轻量宣纸底（菜单/商店/结算，不画完整卷轴轴杆） */
fun DrawScope.drawInkPaperBackdrop(w: Float, h: Float, pulse: Float = 0f, chapterHint: Int = 0) {
    val paper = when (chapterHint % 5) {
        1 -> listOf(Color(0xFFE7D3B0), Color(0xFFD4B896), Color(0xFFC4A574))
        2 -> listOf(Color(0xFF2C3340), Color(0xFF1A1F28), Color(0xFF12161C))
        3 -> listOf(Color(0xFFEDE6D6), Color(0xFFC9C2B0), Color(0xFFA8A193))
        4 -> listOf(Color(0xFFE8F0E8), Color(0xFFC5D5C0), Color(0xFFA3B89E))
        else -> listOf(Color(0xFFF3E9D2), Color(0xFFE8D9B8), Color(0xFFD9C7A0))
    }
    drawRect(Brush.verticalGradient(paper), size = Size(w, h))
    // 纤维
    val seed = chapterHint * 41 + 7
    for (i in 0..28) {
        val x = ((seed * 17 + i * 97) % 1000) / 1000f * w
        val y = ((seed * 31 + i * 53) % 1000) / 1000f * h
        drawLine(
            Color(0x12A16207),
            Offset(x, y),
            Offset(x + 18f + (i % 5), y + (i % 3) - 1f),
            1.1f
        )
    }
    // 远山剪影（淡）
    val ink = if (chapterHint % 5 == 2) Color(0x33202835) else Color(0x22364B3A)
    drawMountainLayer(w, h * 0.55f, h * 0.28f, ink, 0.35f, pulse * 0.2f)
    drawOval(
        if (chapterHint % 5 == 2) Color(0x18E2E8F0) else Color(0x28F5F0E1),
        topLeft = Offset(-w * 0.05f, h * 0.62f),
        size = Size(w * 1.1f, h * 0.14f)
    )
    // 上下暗角
    drawRect(
        Brush.verticalGradient(listOf(Color(0x332E1A0A), Color.Transparent)),
        size = Size(w, h * 0.08f)
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x443D2914))),
        topLeft = Offset(0f, h * 0.88f),
        size = Size(w, h * 0.12f)
    )
}

/** 绢本面板：暖纸底 + 赭边 */
fun DrawScope.drawParchmentPanel(
    x: Float,
    y: Float,
    pw: Float,
    ph: Float,
    radius: Float = 14f,
    strokeCol: Color = Color(0xAA5C4033),
    fill: Color = Color(0xEEF5EBD4)
) {
    drawRoundRect(Color(0x33000000), Offset(x + 3f, y + 4f), Size(pw, ph), CornerRadius(radius))
    drawRoundRect(fill, Offset(x, y), Size(pw, ph), CornerRadius(radius))
    drawRoundRect(strokeCol, Offset(x, y), Size(pw, ph), CornerRadius(radius), style = Stroke(1.8f))
    // 内细线
    drawRoundRect(
        Color(0x33B91C1C),
        Offset(x + 5f, y + 5f),
        Size(pw - 10f, ph - 10f),
        CornerRadius((radius - 4f).coerceAtLeast(4f)),
        style = Stroke(1f)
    )
}

/** 木纹顶/底条 */
fun DrawScope.drawInkWoodBar(x: Float, y: Float, bw: Float, bh: Float, radius: Float = 12f) {
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xEE3D2914), Color(0xDD5C4033), Color(0xEE3D2914))),
        Offset(x, y), Size(bw, bh), CornerRadius(radius)
    )
    drawRoundRect(
        Color(0x66B91C1C),
        Offset(x, y), Size(bw, bh), CornerRadius(radius),
        style = Stroke(1.4f)
    )
}

/** 朱/墨按钮底 */
fun DrawScope.drawInkButton(
    x: Float,
    y: Float,
    bw: Float,
    bh: Float,
    accent: Color = Color(0xFFB45309),
    radius: Float = 12f
) {
    drawRoundRect(Color(0xEE2C1810), Offset(x, y), Size(bw, bh), CornerRadius(radius))
    drawRoundRect(
        Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Color(0xEE2C1810))),
        Offset(x, y), Size(bw, bh), CornerRadius(radius)
    )
    drawRoundRect(accent, Offset(x, y), Size(bw, bh), CornerRadius(radius), style = Stroke(2f))
}

/**
 * 本局缩放后的关卡表：墨阶越高越难，种子让每次分岔细节不同。
 */
fun stagesForRun(seed: Long, inkRank: Int): List<StageDef> {
    val base = stageDefs()
    val mul = 1f + inkRank * 0.20f + ((seed % 5).toInt()) * 0.03f
    return base.mapIndexed { idx, st ->
        scaleStage(st, mul, seed xor (idx * 31L), inkRank)
    }
}

private fun scaleStage(st: StageDef, mul: Float, seed: Long, inkRank: Int): StageDef {
    val rng = Random(seed)
    val title = if (inkRank > 0) "${st.title} · 墨$inkRank" else st.title
    return st.copy(
        title = title,
        tip = if (inkRank > 0) "墨阶$inkRank · 敌人更强 · 掉落更丰" else st.tip,
        nodes = st.nodes.map { n ->
            val gMul = 0.92f + rng.nextFloat() * 0.22f + inkRank * 0.04f
            n.copy(
                goldDrop = (n.goldDrop * gMul).toInt().coerceAtLeast(if (n.goldDrop > 0) 1 else 0),
                trapDmg = n.trapDmg * (1f + inkRank * 0.12f),
                waves = n.waves.map { w ->
                    WaveDef(w.enemies.map { e ->
                        e.copy(
                            hp = e.hp * mul * (0.96f + rng.nextFloat() * 0.08f),
                            atk = e.atk * (1f + inkRank * 0.16f) * (0.97f + rng.nextFloat() * 0.06f)
                        )
                    })
                },
                // 小概率把非战斗房换成另一类，增加重开新鲜感
                type = maybeSwapNode(n, rng, inkRank),
                eventId = if (n.type == NodeType.EVENT && n.eventId.isNotBlank() && rng.nextFloat() < 0.35f) {
                    listOf("merchant", "bard", "stele", "archive").random(rng)
                } else n.eventId
            )
        }
    )
}

private fun maybeSwapNode(n: MapNode, rng: Random, inkRank: Int): NodeType {
    if (n.type == NodeType.MOB || n.type == NodeType.ELITE || n.type == NodeType.BOSS ||
        n.type == NodeType.START || n.type == NodeType.EXIT
    ) return n.type
    if (inkRank <= 0 || rng.nextFloat() > 0.25f) return n.type
    return listOf(NodeType.GOLD, NodeType.HEAL, NodeType.REST, NodeType.EVENT, NodeType.SHOP).random(rng)
}
