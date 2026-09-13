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

/** 器官路线扫描图。函数名保留，避免破坏旧调用与存档兼容。 */
fun DrawScope.drawInkScrollBackdrop(stage: StageDef, w: Float, h: Float, pulse: Float, inkRank: Int = 0) {
    val ch = stage.chapterIndex.coerceAtLeast(0) % 5
    val colors = organMapColors(ch)
    drawRect(Brush.verticalGradient(listOf(colors.first, colors.second)), size = Size(w, h))

    // 微弱细胞基质纹理，确定性生成，不影响路线辨识。
    val rng = Random(stage.id * 97L + inkRank * 13L)
    for (i in 0..34) {
        val x = rng.nextFloat() * w
        val y = rng.nextFloat() * h
        val r = 2f + rng.nextFloat() * 7f
        drawCircle(Color.White.copy(alpha = 0.035f), r, Offset(x, y))
        if (i % 4 == 0) drawCircle(colors.third.copy(alpha = 0.08f), r * 0.35f, Offset(x, y))
    }

    drawOrganMapAnatomy(ch, w, h, pulse, colors.third)

    // 医疗扫描边框与角标。
    drawRoundRect(
        Color.White.copy(alpha = 0.12f), Offset(w * 0.015f, h * 0.015f),
        Size(w * 0.97f, h * 0.95f), CornerRadius(18f), style = Stroke(2f)
    )
    val corner = Color.White.copy(alpha = 0.32f)
    listOf(
        Offset(w * 0.035f, h * 0.14f), Offset(w * 0.965f, h * 0.14f),
        Offset(w * 0.035f, h * 0.69f), Offset(w * 0.965f, h * 0.69f)
    ).forEach { p ->
        drawLine(corner, Offset(p.x - 10f, p.y), Offset(p.x + 10f, p.y), 2f)
        drawLine(corner, Offset(p.x, p.y - 10f), Offset(p.x, p.y + 10f), 2f)
    }

    // 上下暗角，底部选路栏仍能自然衔接。
    drawRect(Brush.verticalGradient(listOf(Color(0x44000000), Color.Transparent)), size = Size(w, h * 0.11f))
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000))),
        topLeft = Offset(0f, h * 0.69f), size = Size(w, h * 0.31f)
    )
}

private fun organMapColors(ch: Int): Triple<Color, Color, Color> = when (ch) {
    1 -> Triple(Color(0xFFB9DDE5), Color(0xFF4E7E91), Color(0xFF2E7089))
    2 -> Triple(Color(0xFFD796AC), Color(0xFF71324F), Color(0xFFF0A45D))
    3 -> Triple(Color(0xFFB66D5D), Color(0xFF4E2527), Color(0xFFD8B64D))
    4 -> Triple(Color(0xFFB83D55), Color(0xFF390B19), Color(0xFFFF8A92))
    else -> Triple(Color(0xFFF0C4B4), Color(0xFF984E5A), Color(0xFFC43D55))
}

private fun DrawScope.drawOrganMapAnatomy(ch: Int, w: Float, h: Float, pulse: Float, accent: Color) {
    val breathe = 1f + sin(pulse * 2.2f) * 0.015f

    when (ch) {
        0 -> { // ── 皮肤：表皮分层横切面 + 毛囊 + 汗腺 ──
            // 皮肤各层用不同色带（角质层→透明层→颗粒层→棘层→基底层）
            val layers = listOf(
                Triple(0.10f, 0.20f, Color(0xFFFFE8D6)),  // 角质层
                Triple(0.20f, 0.34f, Color(0xFFFFDCC5)),  // 透明层
                Triple(0.34f, 0.52f, Color(0xFFF5C9B0)),  // 颗粒层
                Triple(0.52f, 0.74f, Color(0xFFEBA890)),  // 棘层
                Triple(0.74f, 0.88f, Color(0xFFD4927A))   // 基底层
            )
            layers.forEach { (top, bot, col) ->
                drawRect(col.copy(alpha = 0.25f), Offset(0f, h * top), Size(w, h * (bot - top)))
                // 层间分界线
                drawLine(
                    Color(0x33BE123C), Offset(0f, h * top), Offset(w, h * top), 1.5f,
                    cap = StrokeCap.Round
                )
            }
            // 毛囊（斜向管道 + 毛干）
            for (i in 0 until 8) {
                val fx = w * (0.08f + i * 0.12f)
                val fy = h * 0.10f
                val depth = h * (0.30f + (i % 3) * 0.12f)
                val tilt = if (i % 2 == 0) 0.15f else -0.12f
                // 毛囊管道
                drawLine(
                    Color(0x44BE123C), Offset(fx, fy),
                    Offset(fx + tilt * depth, fy + depth), 6f, StrokeCap.Round
                )
                // 毛干
                drawLine(
                    Color(0xFF7A5040).copy(alpha = 0.5f), Offset(fx + tilt * depth, fy + depth),
                    Offset(fx + tilt * depth * 1.15f, fy + depth * 1.12f), 3f, StrokeCap.Round
                )
                // 毛囊根部（毛球）
                drawCircle(Color(0x33BE123C), 5f, Offset(fx + tilt * depth, fy + depth))
            }
            // 汗腺（螺旋卷曲）
            for (i in 0 until 4) {
                val sx = w * (0.18f + i * 0.22f)
                val sy = h * 0.82f
                val spiral = Path()
                for (k in 0..20) {
                    val t = k / 20f
                    val a = t * 4f * 3.14159f
                    val r = t * 12f
                    val px = sx + cos(a) * r
                    val py = sy + sin(a) * r * 0.6f
                    if (k == 0) spiral.moveTo(px, py) else spiral.lineTo(px, py)
                }
                drawPath(spiral, Color(0x444A6741), style = Stroke(2f))
            }
            // 创口裂痕
            val wound = Path().apply {
                moveTo(w * 0.48f, h * 0.12f)
                lineTo(w * 0.46f, h * 0.24f); lineTo(w * 0.52f, h * 0.36f)
                lineTo(w * 0.47f, h * 0.50f); lineTo(w * 0.51f, h * 0.62f)
                lineTo(w * 0.48f, h * 0.74f)
            }
            drawPath(wound, Color(0x55BE123C), style = Stroke(8f, cap = StrokeCap.Round))
            drawPath(wound, Color(0x22FFFFFF), style = Stroke(14f, cap = StrokeCap.Round))
        }

        1 -> { // ── 肺部：左右肺叶 + 气管支气管树 + 肺泡纹理 ──
            // 气管
            drawRoundRect(
                Color(0x44E0F2FE), Offset(w * 0.475f, h * 0.06f), Size(w * 0.05f, h * 0.14f),
                CornerRadius(4f)
            )
            // 支气管分叉
            val bronchus = Path().apply {
                moveTo(w * 0.50f, h * 0.18f)
                cubicTo(w * 0.48f, h * 0.22f, w * 0.38f, h * 0.25f, w * 0.32f, h * 0.28f)
                moveTo(w * 0.50f, h * 0.18f)
                cubicTo(w * 0.52f, h * 0.22f, w * 0.62f, h * 0.25f, w * 0.68f, h * 0.28f)
            }
            drawPath(bronchus, Color(0x66BAE6FD), style = Stroke(8f, cap = StrokeCap.Round))
            // 左肺（画面左侧）
            val leftLung = Path().apply {
                moveTo(w * 0.34f, h * 0.26f)
                cubicTo(w * 0.18f, h * 0.22f, w * 0.06f, h * 0.32f, w * 0.05f, h * 0.52f)
                cubicTo(w * 0.04f, h * 0.66f, w * 0.10f, h * 0.78f, w * 0.22f, h * 0.78f)
                cubicTo(w * 0.34f, h * 0.78f, w * 0.38f, h * 0.62f, w * 0.38f, h * 0.48f)
                cubicTo(w * 0.38f, h * 0.38f, w * 0.38f, h * 0.30f, w * 0.34f, h * 0.26f)
                close()
            }
            drawPath(leftLung, Color(0x22BAE6FD))
            drawPath(leftLung, Color(0x5538BDF8), style = Stroke(3f))
            // 右肺（画面右侧，稍大因为有三叶）
            val rightLung = Path().apply {
                moveTo(w * 0.66f, h * 0.26f)
                cubicTo(w * 0.82f, h * 0.22f, w * 0.94f, h * 0.32f, w * 0.95f, h * 0.52f)
                cubicTo(w * 0.96f, h * 0.66f, w * 0.90f, h * 0.78f, w * 0.78f, h * 0.78f)
                cubicTo(w * 0.66f, h * 0.78f, w * 0.62f, h * 0.62f, w * 0.62f, h * 0.48f)
                cubicTo(w * 0.62f, h * 0.38f, w * 0.62f, h * 0.30f, w * 0.66f, h * 0.26f)
                close()
            }
            drawPath(rightLung, Color(0x22BAE6FD))
            drawPath(rightLung, Color(0x5538BDF8), style = Stroke(3f))
            // 肺泡纹理：两肺内部散布圆形肺泡
            val alveoliRng = Random(42)
            for (i in 0 until 36) {
                val side = if (i % 2 == 0) 0.08f..0.34f else 0.66f..0.92f
                val ax = w * (side.start + alveoliRng.nextFloat() * (side.endInclusive - side.start))
                val ay = h * (0.30f + alveoliRng.nextFloat() * 0.42f)
                val ar = 6f + alveoliRng.nextFloat() * 10f + sin(pulse + i) * 1.5f
                drawCircle(Color.White.copy(alpha = 0.10f), ar, Offset(ax, ay))
                drawCircle(Color(0x337DD3FC), ar, Offset(ax, ay), style = Stroke(1.2f))
            }
            // 膈肌弧线（肺底部）
            val diaphragm = Path().apply {
                moveTo(w * 0.04f, h * 0.78f)
                quadraticBezierTo(w * 0.50f, h * 0.84f, w * 0.96f, h * 0.78f)
            }
            drawPath(diaphragm, Color(0x448899AA), style = Stroke(4f, cap = StrokeCap.Round))
        }

        2 -> { // ── 胃肠：胃袋 J 形 + 小肠盘曲 + 大肠边框 ──
            // 胃袋（J 形囊袋，画面中偏左上）
            val stomach = Path().apply {
                moveTo(w * 0.28f, h * 0.14f)
                cubicTo(w * 0.22f, h * 0.10f, w * 0.14f, h * 0.14f, w * 0.13f, h * 0.24f)
                cubicTo(w * 0.12f, h * 0.38f, w * 0.18f, h * 0.54f, w * 0.32f, h * 0.58f)
                cubicTo(w * 0.42f, h * 0.61f, w * 0.52f, h * 0.55f, w * 0.52f, h * 0.46f)
                cubicTo(w * 0.52f, h * 0.36f, w * 0.44f, h * 0.30f, w * 0.40f, h * 0.22f)
                cubicTo(w * 0.37f, h * 0.16f, w * 0.33f, h * 0.14f, w * 0.28f, h * 0.14f)
                close()
            }
            drawPath(stomach, Color(0x22F5C9B0))
            drawPath(stomach, Color(0x55D4927A), style = Stroke(3.5f))
            // 胃内皱褶线
            for (i in 0..2) {
                val fold = Path().apply {
                    moveTo(w * 0.18f, h * (0.22f + i * 0.08f))
                    quadraticBezierTo(w * 0.28f, h * (0.28f + i * 0.08f), w * 0.38f, h * (0.22f + i * 0.08f))
                }
                drawPath(fold, Color(0x33BE123C), style = Stroke(2f, cap = StrokeCap.Round))
            }
            // 幽门到十二指肠
            drawLine(
                Color(0x66D4927A), Offset(w * 0.50f, h * 0.46f), Offset(w * 0.58f, h * 0.52f), 8f, StrokeCap.Round
            )
            // 小肠盘曲（六段弯折管道）
            val smallIntestine = Path().apply {
                moveTo(w * 0.58f, h * 0.52f)
                cubicTo(w * 0.70f, h * 0.50f, w * 0.78f, h * 0.56f, w * 0.72f, h * 0.64f)
                cubicTo(w * 0.66f, h * 0.72f, w * 0.50f, h * 0.68f, w * 0.44f, h * 0.72f)
                cubicTo(w * 0.38f, h * 0.76f, w * 0.52f, h * 0.82f, w * 0.64f, h * 0.80f)
                cubicTo(w * 0.76f, h * 0.78f, w * 0.82f, h * 0.72f, w * 0.80f, h * 0.66f)
            }
            drawPath(smallIntestine, Color(0x22C4B5FD), style = Stroke(26f, cap = StrokeCap.Round))
            drawPath(smallIntestine, Color(0x44A78BFA), style = Stroke(3.5f, cap = StrokeCap.Round))
            // 大肠框边（从右下→上→左→下）
            val largeIntestine = Path().apply {
                moveTo(w * 0.82f, h * 0.66f)
                lineTo(w * 0.84f, h * 0.40f)
                cubicTo(w * 0.84f, h * 0.32f, w * 0.78f, h * 0.28f, w * 0.72f, h * 0.30f)
                lineTo(w * 0.16f, h * 0.32f)
                cubicTo(w * 0.10f, h * 0.32f, w * 0.08f, h * 0.38f, w * 0.10f, h * 0.48f)
                lineTo(w * 0.12f, h * 0.62f)
            }
            drawPath(largeIntestine, Color(0x18A3E635), style = Stroke(20f, cap = StrokeCap.Round))
            drawPath(largeIntestine, Color(0x33A3E635), style = Stroke(3f, cap = StrokeCap.Round))
        }

        3 -> { // ── 肝脏：大体肝脏楔形 + 胆囊 + 肝门血管 ──
            // 肝脏主体（大型楔形，右上腹占位）
            val liver = Path().apply {
                moveTo(w * 0.10f, h * 0.20f)
                cubicTo(w * 0.08f, h * 0.14f, w * 0.20f, h * 0.08f, w * 0.40f, h * 0.08f)
                cubicTo(w * 0.62f, h * 0.08f, w * 0.84f, h * 0.12f, w * 0.90f, h * 0.24f)
                cubicTo(w * 0.94f, h * 0.34f, w * 0.88f, h * 0.48f, w * 0.72f, h * 0.56f)
                cubicTo(w * 0.58f, h * 0.63f, w * 0.36f, h * 0.64f, w * 0.22f, h * 0.58f)
                cubicTo(w * 0.12f, h * 0.53f, w * 0.08f, h * 0.42f, w * 0.10f, h * 0.28f)
                close()
            }
            drawPath(liver, Color(0x22D4A574))
            drawPath(liver, Color(0x55B45309), style = Stroke(3.5f))
            // 镰状韧带分界线（左右肝分界）
            drawLine(
                Color(0x44B45309), Offset(w * 0.42f, h * 0.10f), Offset(w * 0.44f, h * 0.52f), 3f,
                cap = StrokeCap.Round
            )
            // 胆囊（梨形，肝下缘）
            val gallbladder = Path().apply {
                moveTo(w * 0.52f, h * 0.54f)
                cubicTo(w * 0.50f, h * 0.60f, w * 0.52f, h * 0.66f, w * 0.58f, h * 0.68f)
                cubicTo(w * 0.63f, h * 0.69f, w * 0.66f, h * 0.64f, w * 0.63f, h * 0.58f)
                cubicTo(w * 0.61f, h * 0.54f, w * 0.56f, h * 0.52f, w * 0.52f, h * 0.54f)
                close()
            }
            drawPath(gallbladder, Color(0x33A3E635))
            drawPath(gallbladder, Color(0x5565A30D), style = Stroke(2f))
            // 肝门血管（门静脉+肝动脉分叉）
            val portalVein = Path().apply {
                moveTo(w * 0.48f, h * 0.56f)
                cubicTo(w * 0.48f, h * 0.48f, w * 0.42f, h * 0.42f, w * 0.36f, h * 0.38f)
                moveTo(w * 0.48f, h * 0.52f)
                cubicTo(w * 0.54f, h * 0.46f, w * 0.62f, h * 0.42f, w * 0.68f, h * 0.38f)
            }
            drawPath(portalVein, Color(0x44885B6B), style = Stroke(6f, cap = StrokeCap.Round))
            // 肝小叶六角纹理
            val hepRng = Random(77)
            for (i in 0 until 22) {
                val hx = w * (0.14f + hepRng.nextFloat() * 0.72f)
                val hy = h * (0.14f + hepRng.nextFloat() * 0.42f)
                if (hx < w * 0.06f || hx > w * 0.94f) continue
                val hexR = 10f + hepRng.nextFloat() * 8f
                val hex = Path()
                for (k in 0..6) {
                    val a = k * 1.0472f + 0.5f
                    val p = Offset(hx + cos(a) * hexR, hy + sin(a) * hexR * 0.85f)
                    if (k == 0) hex.moveTo(p.x, p.y) else hex.lineTo(p.x, p.y)
                }
                drawPath(hex, Color(0x22B45309), style = Stroke(1.2f))
            }
        }

        else -> { // ── 心脏：四腔心 + 主动脉弓 + 肺动脉 + 冠状动脉 ──
            val beat = 1f + sin(pulse * 3.2f) * 0.03f
            // 心外膜（心包脂肪层）
            val pericardium = Path().apply {
                moveTo(w * 0.50f, h * 0.66f)
                cubicTo(w * 0.18f, h * 0.52f, w * 0.14f, h * 0.28f, w * 0.26f, h * 0.16f)
                cubicTo(w * 0.36f, h * 0.06f, w * 0.48f, h * 0.10f, w * 0.50f, h * 0.22f)
                cubicTo(w * 0.52f, h * 0.10f, w * 0.66f, h * 0.06f, w * 0.76f, h * 0.16f)
                cubicTo(w * 0.88f, h * 0.28f, w * 0.84f, h * 0.52f, w * 0.50f, h * 0.66f)
                close()
            }
            drawPath(pericardium, Color(0x18FF8A92))
            drawPath(pericardium, Color(0x33FF8A92), style = Stroke(2f))
            // 心脏主体（略偏左，心尖朝左下）
            val heart = Path().apply {
                moveTo(w * 0.46f, h * 0.62f)
                cubicTo(w * (0.20f / beat), h * 0.48f, w * 0.16f, h * 0.28f, w * 0.28f, h * 0.20f)
                cubicTo(w * 0.36f, h * 0.14f, w * 0.46f, h * 0.16f, w * 0.48f, h * 0.26f)
                cubicTo(w * 0.50f, h * 0.16f, w * 0.62f, h * 0.14f, w * 0.72f, h * 0.20f)
                cubicTo(w * 0.84f, h * 0.28f, w * 0.80f, h * 0.48f, w * 0.54f, h * 0.62f)
                close()
            }
            drawPath(heart, Color(0x22FF6B6B))
            drawPath(heart, Color(0x66FF8A92), style = Stroke(3.5f))
            // 室间隔（左右心室分界）
            drawLine(
                Color(0x55FFB4B4), Offset(w * 0.50f, h * 0.28f), Offset(w * 0.48f, h * 0.60f), 3f,
                cap = StrokeCap.Round
            )
            // 主动脉弓（从心底部上弯再下行）
            val aorta = Path().apply {
                moveTo(w * 0.48f, h * 0.26f)
                cubicTo(w * 0.48f, h * 0.14f, w * 0.52f, h * 0.06f, w * 0.62f, h * 0.06f)
                cubicTo(w * 0.72f, h * 0.06f, w * 0.78f, h * 0.12f, w * 0.76f, h * 0.22f)
                cubicTo(w * 0.74f, h * 0.30f, w * 0.70f, h * 0.36f, w * 0.68f, h * 0.40f)
            }
            drawPath(aorta, Color(0x55FBBF24), style = Stroke(9f, cap = StrokeCap.Round))
            // 肺动脉（从右心室向左弯）
            val pulmArt = Path().apply {
                moveTo(w * 0.50f, h * 0.24f)
                cubicTo(w * 0.48f, h * 0.14f, w * 0.36f, h * 0.08f, w * 0.26f, h * 0.10f)
            }
            drawPath(pulmArt, Color(0x447DD3FC), style = Stroke(7f, cap = StrokeCap.Round))
            // 冠状动脉（心脏表面的血管网络）
            val coronary = Path().apply {
                moveTo(w * 0.44f, h * 0.28f)
                cubicTo(w * 0.38f, h * 0.34f, w * 0.32f, h * 0.42f, w * 0.36f, h * 0.50f)
                moveTo(w * 0.56f, h * 0.28f)
                cubicTo(w * 0.62f, h * 0.34f, w * 0.66f, h * 0.42f, w * 0.60f, h * 0.52f)
            }
            drawPath(coronary, Color(0x44EF4444), style = Stroke(2.5f, cap = StrokeCap.Round))
            // 心房纹理线
            for (i in 0..2) {
                drawLine(
                    Color(0x22FFB4B4),
                    Offset(w * (0.28f + i * 0.06f), h * 0.20f),
                    Offset(w * (0.30f + i * 0.06f), h * 0.32f), 1.5f
                )
            }
        }
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
    pulse: Float,
    chapterIndex: Int = 0
) {
    val ch = chapterIndex.coerceAtLeast(0) % 5
    val accent = organMapColors(ch).third
    val ink = Color(0xFF281A22)
    val cinnabar = accent
    val jade = if (ch == 1) Color(0xFF0E7490) else Color(0xFF3F7653)
    if (lit) {
        drawCircle(cinnabar.copy(alpha = 0.18f + 0.08f * pulse), r + 12f, c)
        drawCircle(cinnabar.copy(alpha = 0.55f), r + 5f, c, style = Stroke(2.2f))
    }
    // 细胞膜圆底
    val membrane = when (ch) {
        1 -> Color(0xFFE8F7FA)
        2 -> Color(0xFFFFE1E8)
        3 -> Color(0xFFF4D5C7)
        4 -> Color(0xFFFFD8DE)
        else -> Color(0xFFFFE8DF)
    }
    drawCircle(membrane, r, c)
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
        NodeType.TREASURE -> {
            // 小宝箱：箱体 + 锁扣
            drawRoundRect(Color(0xFFB45309), Offset(c.x - r * 0.38f, c.y - r * 0.18f), Size(r * 0.76f, r * 0.42f), CornerRadius(3f))
            drawRoundRect(Color(0xFFFBBF24), Offset(c.x - r * 0.38f, c.y - r * 0.28f), Size(r * 0.76f, r * 0.2f), CornerRadius(3f))
            drawCircle(Color(0xFFF5EBD4), r * 0.09f, Offset(c.x, c.y - r * 0.02f))
        }
        NodeType.CHALLENGE -> {
            // 骷髅印记：高危但诱人
            drawCircle(Color(0xFF44403C), r * 0.4f, c)
            drawCircle(Color(0xFFF5EBD4), r * 0.1f, Offset(c.x - r * 0.14f, c.y - r * 0.1f))
            drawCircle(Color(0xFFF5EBD4), r * 0.1f, Offset(c.x + r * 0.14f, c.y - r * 0.1f))
            drawRect(Color(0xFFF5EBD4), Offset(c.x - r * 0.05f, c.y + r * 0.05f), Size(r * 0.1f, r * 0.16f))
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
    visited: Boolean,
    chapterIndex: Int = 0
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
    val organAccent = organMapColors(chapterIndex.coerceAtLeast(0) % 5).third
    val soft = when {
        active -> organAccent.copy(alpha = 0.34f)
        visited -> Color(0x3347A58B)
        else -> Color(0x221C1917)
    }
    val main = when {
        active -> organAccent.copy(alpha = 0.92f)
        visited -> Color(0xAA47A58B)
        else -> Color(0x553F3F46)
    }
    val wMain = if (active) 4.2f else if (visited) 2.8f else 1.8f
    drawPath(path, soft, style = Stroke(width = wMain + 4f, cap = StrokeCap.Round))
    drawPath(path, main, style = Stroke(width = wMain, cap = StrokeCap.Round))
    if (active) {
        drawCircle(organAccent.copy(alpha = 0.72f), 3.5f, mid)
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
    val mul = mutationHpScale(inkRank) + ((seed % 5).toInt()) * 0.03f
    return base.mapIndexed { idx, st ->
        scaleStage(st, mul, seed xor (idx * 31L), inkRank)
    }
}

private fun scaleStage(st: StageDef, mul: Float, seed: Long, inkRank: Int): StageDef {
    val rng = Random(seed)
    val title = if (inkRank > 0) "${st.title} · 变异$inkRank" else st.title
    // 前两章 mercy 曲线：头两关是新手第一印象，压低敌人数值
    val mercyHp = when (st.id) { 0 -> 0.8f; 1 -> 0.9f; else -> 1f }
    val mercyAtk = when (st.id) { 0 -> 0.85f; 1 -> 0.92f; else -> 1f }
    return st.copy(
        title = title,
        tip = if (inkRank > 0) "变异第${inkRank}代 · 敌人更强 · 掉落更丰" else st.tip,
        nodes = st.nodes.mapIndexed { ni, n ->
            val gMul = 0.92f + rng.nextFloat() * 0.22f + inkRank.coerceAtMost(15) * 0.04f
            // 章节节点多样化：中途一座宝物房；第二章起后半一座挑战房（确定性注入）
            val typeOverride = when {
                ni == st.nodes.size / 2 && n.type == NodeType.MOB -> NodeType.TREASURE
                st.id >= 1 && ni == (st.nodes.size * 3) / 4 && n.type == NodeType.MOB -> NodeType.CHALLENGE
                else -> null
            }
            n.copy(
                goldDrop = (n.goldDrop * gMul).toInt().coerceAtLeast(if (n.goldDrop > 0) 1 else 0),
                trapDmg = n.trapDmg * mutationTrapScale(inkRank),
                // 前两章节奏收紧：每场战斗至多 3/4 波（战斗疲劳是大问题）
                waves = n.waves.take(if (st.id == 0) 3 else if (st.id == 1) 4 else n.waves.size).map { w ->
                    WaveDef(w.enemies.map { e ->
                        e.copy(
                            hp = e.hp * mul * mercyHp * (0.96f + rng.nextFloat() * 0.08f),
                            atk = e.atk * mutationAtkScale(inkRank) * mercyAtk * (0.97f + rng.nextFloat() * 0.06f)
                        )
                    })
                },
                // 小概率把非战斗房换成另一类，增加重开新鲜感
                type = typeOverride ?: maybeSwapNode(n, rng, inkRank),
                eventId = if (n.type == NodeType.EVENT && n.eventId.isNotBlank() && rng.nextFloat() < 0.35f) {
                    listOf("merchant", "bard", "stele", "archive", "inkwell", "spirit_forge").random(rng)
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
