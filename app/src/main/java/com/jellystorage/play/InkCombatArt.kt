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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 水墨战斗：人物/敌人/条/摇杆 — 偏写意，不抢戏 */

private val InkDark = Color(0xFF1C1410)
private val InkMid = Color(0xFF3F3F46)
private val PaperLite = Color(0xFFF5EBD4)
private val Cinnabar = Color(0xFFB91C1C)
private val Indigo = Color(0xFF1E3A5F)
private val Jade = Color(0xFF3F6212)

fun DrawScope.drawInkHero(
    cx: Float,
    cy: Float,
    bodyR: Float,
    facing: Float,
    skin: CharacterSkin,
    hitFlash: Float = 0f,
    bob: Float = 0f
) {
    val s = bodyR * 0.95f
    val y = cy + bob
    val dir = if (facing >= 0f) 1f else -1f
    val flash = hitFlash > 0.05f
    val body = if (flash) PaperLite else skin.outfit.copy(
        red = skin.outfit.red * 0.55f + 0.15f,
        green = skin.outfit.green * 0.55f + 0.12f,
        blue = skin.outfit.blue * 0.55f + 0.10f
    )
    val ink = if (flash) Color.White else InkDark

    // 墨影
    drawOval(Color(0x45000000), Offset(cx - s * 0.7f, y + s * 0.55f), Size(s * 1.4f, s * 0.32f))
    // 写意躯干（一笔圆）
    drawCircle(body.copy(alpha = 0.92f), s * 0.72f, Offset(cx, y + s * 0.05f))
    drawCircle(ink.copy(alpha = 0.55f), s * 0.72f, Offset(cx, y + s * 0.05f), style = Stroke(s * 0.08f))
    // 头
    drawCircle(if (flash) PaperLite else Color(0xFFE8D9B8), s * 0.48f, Offset(cx, y - s * 0.55f))
    drawCircle(ink.copy(alpha = 0.65f), s * 0.48f, Offset(cx, y - s * 0.55f), style = Stroke(s * 0.07f))
    // 发（侧锋）
    drawOval(skin.hair.copy(alpha = 0.85f), Offset(cx - s * 0.48f, y - s * 1.05f), Size(s * 0.96f, s * 0.55f))
    // 眼（两点墨）
    drawCircle(ink, s * 0.07f, Offset(cx - s * 0.14f * dir, y - s * 0.55f))
    drawCircle(ink, s * 0.07f, Offset(cx + s * 0.18f * dir, y - s * 0.55f))
    // 武器：一笔锋
    val wx0 = cx + dir * s * 0.55f
    val wy0 = y - s * 0.1f
    drawLine(ink, Offset(wx0, wy0), Offset(wx0 + dir * s * 0.95f, wy0 - s * 0.55f), s * 0.12f, StrokeCap.Round)
    drawLine(Cinnabar.copy(alpha = 0.55f), Offset(wx0 + dir * s * 0.2f, wy0 - s * 0.05f), Offset(wx0 + dir * s * 0.9f, wy0 - s * 0.5f), s * 0.05f, StrokeCap.Round)
}

fun DrawScope.drawInkEnemy(
    cx: Float,
    cy: Float,
    r: Float,
    kind: EnemyKind,
    hitFlash: Float,
    bob: Float,
    facing: Float,
    elite: Boolean = false,
    enraged: Boolean = false
) {
    val y = cy + bob
    val flash = hitFlash > 0.05f
    val base = when (kind) {
        EnemyKind.BOSS_SLIME, EnemyKind.BOSS_ORE -> Color(0xFF44403C)
        EnemyKind.BAT, EnemyKind.WISP -> Color(0xFF57534E)
        EnemyKind.SPIKE_SLIME -> Color(0xFF5C4033)
        else -> Color(0xFF3F3F46)
    }
    val fill = if (flash) PaperLite else base
    val el = kind.element().color
    // 墨晕
    drawCircle(fill.copy(alpha = 0.25f), r * 1.35f, Offset(cx, y))
    drawCircle(fill.copy(alpha = 0.85f), r * 0.95f, Offset(cx, y))
    drawCircle(InkDark.copy(alpha = 0.7f), r * 0.95f, Offset(cx, y), style = Stroke(r * 0.12f))
    // 五行一点朱/彩
    drawCircle(el.copy(alpha = 0.55f), r * 0.28f, Offset(cx + facing * r * 0.15f, y - r * 0.1f))
    if (elite) drawCircle(Cinnabar, r * 1.08f, Offset(cx, y), style = Stroke(r * 0.1f))
    if (enraged) drawCircle(Cinnabar.copy(alpha = 0.35f), r * 1.25f, Offset(cx, y), style = Stroke(r * 0.08f))
    // 眼
    drawCircle(if (flash) InkDark else PaperLite, r * 0.12f, Offset(cx - r * 0.25f, y - r * 0.15f))
    drawCircle(if (flash) InkDark else PaperLite, r * 0.12f, Offset(cx + r * 0.22f, y - r * 0.15f))
}

/**
 * 笔锋斩：一笔弧（起笔细→行笔粗→收笔飞白），
 * 不是直线激光，也不是金星特效。
 */
fun DrawScope.drawInkSlash(
    px: Float,
    py: Float,
    ang: Float,
    r: Float,
    alpha: Float,
    width: Float
) {
    val a = alpha.coerceIn(0f, 1f)
    val mid = r * 0.55f
    val cx = px + cos(ang) * mid * 0.35f
    val cy = py + sin(ang) * mid * 0.35f
    val span = 0.95f + width * 0.15f
    val steps = 14
    for (i in 0 until steps) {
        val t0 = i / steps.toFloat()
        val t1 = (i + 1) / steps.toFloat()
        val a0 = ang - span * 0.5f + span * t0
        val a1 = ang - span * 0.5f + span * t1
        val thick = when {
            t0 < 0.15f -> 2.5f + t0 * 40f
            t0 > 0.75f -> 14f * (1f - (t0 - 0.75f) / 0.25f) + 3f
            else -> 12f + width * 6f
        }
        val rad0 = r * (0.25f + t0 * 0.75f)
        val rad1 = r * (0.25f + t1 * 0.75f)
        val p0 = Offset(cx + cos(a0) * rad0, cy + sin(a0) * rad0)
        val p1 = Offset(cx + cos(a1) * rad1, cy + sin(a1) * rad1)
        drawLine(InkDark.copy(alpha = a * 0.28f), p0, p1, thick * 1.35f, StrokeCap.Round)
        drawLine(Cinnabar.copy(alpha = a * 0.72f), p0, p1, thick * 0.55f, StrokeCap.Round)
        if (t0 in 0.2f..0.7f) {
            drawLine(PaperLite.copy(alpha = a * 0.35f), p0, p1, thick * 0.18f, StrokeCap.Round)
        }
    }
    val tipAng = ang + span * 0.42f
    val tipR = r * 0.92f
    val tip = Offset(cx + cos(tipAng) * tipR, cy + sin(tipAng) * tipR)
    for (k in 0..5) {
        val off = (k - 2.5f) * 4.5f
        val ox = tip.x + cos(tipAng + 1.2f) * off
        val oy = tip.y + sin(tipAng + 1.2f) * off
        drawCircle(InkDark.copy(alpha = a * (0.35f - k * 0.04f).coerceAtLeast(0.05f)), 2.2f + (k % 2), Offset(ox, oy))
    }
    val start = Offset(cx + cos(ang - span * 0.48f) * r * 0.28f, cy + sin(ang - span * 0.48f) * r * 0.28f)
    drawCircle(Cinnabar.copy(alpha = a * 0.55f), 3.5f * width, start)
}

/**
 * 法术/投射：以笔锋段为主，尖端极小，避免“一串球”。
 * 真正的墨迹在场上 InkMark 残留层。
 */
fun DrawScope.drawInkProjectile(
    x: Float,
    y: Float,
    prevX: Float,
    prevY: Float,
    radius: Float,
    style: Int,
    alpha: Float = 1f
) {
    val core = when (style) {
        1 -> Cinnabar
        2 -> Jade
        3 -> Color(0xFF4A6741)
        5 -> Color(0xFF5C4033)
        6 -> Color(0xFF44403C)
        else -> InkMid
    }
    // 主笔：前粗后细感用两笔
    drawLine(InkDark.copy(alpha = 0.4f * alpha), Offset(prevX, prevY), Offset(x, y), radius * 2.2f, StrokeCap.Round)
    drawLine(core.copy(alpha = 0.75f * alpha), Offset(prevX, prevY), Offset(x, y), radius * 1.1f, StrokeCap.Round)
    // 尖端只一点，不要大球
    drawCircle(core.copy(alpha = 0.9f * alpha), radius * 0.45f, Offset(x, y))
    if (style == 2) {
        // 符：短竖笔+一横
        drawLine(Cinnabar.copy(alpha = 0.8f * alpha), Offset(x, y - radius), Offset(x, y + radius * 0.6f), 2.2f, StrokeCap.Round)
        drawLine(Cinnabar.copy(alpha = 0.7f * alpha), Offset(x - radius * 0.5f, y - radius * 0.2f), Offset(x + radius * 0.5f, y - radius * 0.2f), 1.8f, StrokeCap.Round)
    }
}

/**
 * 全屏墨马：写意剪影，从一侧奔到另一侧。
 * progress 0..1；dir >0 向右。
 */
fun DrawScope.drawInkHorseCharge(
    progress: Float,
    dir: Float,
    screenW: Float,
    screenH: Float
) {
    val p = progress.coerceIn(0f, 1f)
    // 缓入缓出
    val ease = if (p < 0.5f) 2f * p * p else 1f - (-2f * p + 2f) * (-2f * p + 2f) / 2f
    val from = if (dir >= 0f) -screenW * 0.25f else screenW * 1.25f
    val to = if (dir >= 0f) screenW * 1.25f else -screenW * 0.25f
    val hx = from + (to - from) * ease
    val hy = screenH * (0.42f + sin(p * 12f) * 0.02f)
    val s = min(screenW, screenH) * 0.14f
    val face = if (dir >= 0f) 1f else -1f
    val a = (0.95f - abs(p - 0.5f) * 0.3f).coerceIn(0.4f, 0.95f)

    // 烟尘墨道
    for (i in 0..5) {
        val tx = hx - face * s * (0.8f + i * 0.55f)
        val ty = hy + s * 0.35f + (i % 2) * 6f
        drawOval(
            InkDark.copy(alpha = a * (0.18f - i * 0.02f)),
            Offset(tx - s * 0.5f, ty),
            Size(s * (1.1f - i * 0.08f), s * 0.28f)
        )
    }
    // 躯干
    drawOval(InkDark.copy(alpha = a * 0.85f), Offset(hx - s * 0.7f * face * 0f - s * 0.75f, hy - s * 0.25f), Size(s * 1.5f, s * 0.7f))
    // 用路径画更利落的剪影
    val body = Path().apply {
        // 头
        moveTo(hx + face * s * 0.55f, hy - s * 0.15f)
        lineTo(hx + face * s * 0.95f, hy - s * 0.35f)
        lineTo(hx + face * s * 1.05f, hy - s * 0.1f)
        lineTo(hx + face * s * 0.7f, hy + s * 0.05f)
        // 颈到背
        lineTo(hx + face * s * 0.2f, hy - s * 0.35f)
        lineTo(hx - face * s * 0.55f, hy - s * 0.28f)
        // 臀
        lineTo(hx - face * s * 0.85f, hy + s * 0.05f)
        lineTo(hx - face * s * 0.65f, hy + s * 0.35f)
        // 腹
        lineTo(hx + face * s * 0.15f, hy + s * 0.38f)
        close()
    }
    drawPath(body, InkDark.copy(alpha = a))
    // 四蹄（奔姿交错）
    val gait = sin(p * 28f)
    fun leg(bx: Float, by: Float, swing: Float) {
        drawLine(
            InkDark.copy(alpha = a * 0.9f),
            Offset(bx, by),
            Offset(bx + face * s * 0.08f + swing, by + s * 0.55f),
            s * 0.1f,
            StrokeCap.Round
        )
    }
    leg(hx - face * s * 0.35f, hy + s * 0.25f, gait * s * 0.25f)
    leg(hx - face * s * 0.15f, hy + s * 0.25f, -gait * s * 0.22f)
    leg(hx + face * s * 0.15f, hy + s * 0.22f, gait * s * 0.2f)
    leg(hx + face * s * 0.35f, hy + s * 0.22f, -gait * s * 0.24f)
    // 鬃毛几笔
    for (i in 0..3) {
        val mx = hx - face * s * (0.1f + i * 0.12f)
        val my = hy - s * (0.35f + i * 0.02f)
        drawLine(
            InkDark.copy(alpha = a * 0.7f),
            Offset(mx, my),
            Offset(mx - face * s * 0.35f, my - s * 0.25f + i * 3f),
            s * 0.07f,
            StrokeCap.Round
        )
    }
    // 尾
    drawLine(
        InkDark.copy(alpha = a * 0.75f),
        Offset(hx - face * s * 0.75f, hy - s * 0.05f),
        Offset(hx - face * s * 1.25f, hy - s * 0.45f + sin(p * 20f) * s * 0.1f),
        s * 0.09f,
        StrokeCap.Round
    )
    // 朱砂一点眼
    drawCircle(Cinnabar.copy(alpha = a), s * 0.06f, Offset(hx + face * s * 0.88f, hy - s * 0.22f))
    // 蹄下飞白
    for (i in 0..4) {
        drawCircle(
            InkDark.copy(alpha = a * 0.25f),
            2f + i % 2,
            Offset(hx - face * s * (0.2f + i * 0.15f), hy + s * 0.55f + (i % 3) * 2f)
        )
    }
}

/** 绘制场上全部墨迹残留（世界→屏幕由调用方变换） */
fun DrawScope.drawInkMarkLayer(
    marks: List<InkMark>,
    settled: Boolean,
    settleT: Float,
    wx: (Float) -> Float,
    wy: (Float) -> Float,
    wr: (Float) -> Float
) {
    val settleBoost = if (settled) (0.15f + 0.1f * sin(settleT * 0.8f)).coerceIn(0f, 0.25f) else 0f
    for (m in marks) {
        val a = ((m.life / m.maxLife).coerceIn(0.08f, 1f) * 0.85f + settleBoost).coerceIn(0f, 0.95f)
        val col = Color(m.color).copy(alpha = Color(m.color).alpha * a)
        when (m.kind) {
            1 -> {
                // 墨晕
                val rr = wr(m.r.coerceAtLeast(4f))
                drawOval(
                    col,
                    Offset(wx(m.x0) - rr, wy(m.y0) - rr * 0.55f),
                    Size(rr * 2f, rr * 1.1f)
                )
            }
            2 -> {
                drawCircle(col, wr(m.r.coerceAtLeast(1.2f)), Offset(wx(m.x0), wy(m.y0)))
            }
            else -> {
                // 笔锋线
                drawLine(
                    col,
                    Offset(wx(m.x0), wy(m.y0)),
                    Offset(wx(m.x1), wy(m.y1)),
                    wr(m.thick.coerceAtLeast(1.2f)),
                    StrokeCap.Round
                )
            }
        }
    }
    if (settled && settleT > 0.2f) {
        // 落款后淡淡宣纸光，提示“画成了”
        // 调用方可选再叠字
    }
}

fun DrawScope.drawInkBar(
    x: Float,
    y: Float,
    bw: Float,
    bh: Float,
    ratio: Float,
    fill: Color,
    label: String = ""
) {
    // 宣纸槽
    drawRoundRect(Color(0x332C1810), Offset(x, y), Size(bw, bh), CornerRadius(bh / 2f))
    drawRoundRect(InkMid.copy(alpha = 0.35f), Offset(x, y), Size(bw, bh), CornerRadius(bh / 2f), style = Stroke(1.2f))
    val w = (bw * ratio.coerceIn(0f, 1f)).coerceAtLeast(if (ratio > 0f) bh else 0f)
    if (w > 0f) {
        drawRoundRect(
            Brush.horizontalGradient(listOf(fill.copy(alpha = 0.95f), fill.copy(alpha = 0.65f))),
            Offset(x, y), Size(w, bh), CornerRadius(bh / 2f)
        )
    }
}

fun DrawScope.drawInkJoystick(cx: Float, cy: Float, r: Float, active: Boolean, knobX: Float, knobY: Float) {
    drawCircle(Color(0x33000000), r + 3f, Offset(cx + 2f, cy + 3f))
    drawCircle(PaperLite.copy(alpha = 0.22f), r, Offset(cx, cy))
    drawCircle(InkDark.copy(alpha = 0.55f), r, Offset(cx, cy), style = Stroke(2.5f))
    // 朱印细环
    drawCircle(Cinnabar.copy(alpha = 0.35f), r * 0.92f, Offset(cx, cy), style = Stroke(1.5f))
    if (active) {
        drawCircle(PaperLite.copy(alpha = 0.85f), r * 0.36f, Offset(knobX, knobY))
        drawCircle(InkDark.copy(alpha = 0.75f), r * 0.36f, Offset(knobX, knobY), style = Stroke(2f))
    } else {
        drawCircle(InkMid.copy(alpha = 0.45f), r * 0.3f, Offset(cx, cy))
    }
}

fun DrawScope.drawInkSkillSeal(
    cx: Float,
    cy: Float,
    r: Float,
    ready: Boolean,
    held: Boolean,
    accent: Color
) {
    drawCircle(Color(0x44000000), r + 3f, Offset(cx + 1f, cy + 2f))
    val body = if (!ready) Color(0xFF292524) else accent.copy(
        red = accent.red * 0.45f + 0.1f,
        green = accent.green * 0.4f + 0.08f,
        blue = accent.blue * 0.35f + 0.08f
    )
    drawCircle(body, r, Offset(cx, cy))
    drawCircle(
        if (ready) Cinnabar.copy(alpha = if (held) 0.95f else 0.7f) else InkMid,
        r, Offset(cx, cy), style = Stroke(if (held) 3.5f else 2.2f)
    )
    // 宣纸高光
    drawOval(PaperLite.copy(alpha = 0.12f), Offset(cx - r * 0.45f, cy - r * 0.55f), Size(r * 0.9f, r * 0.45f))
    if (!ready) drawCircle(Color(0x77000000), r, Offset(cx, cy))
}

fun DrawScope.drawInkCombatHudFrame(x: Float, y: Float, bw: Float, bh: Float) {
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xDD2C1810), Color(0xCC3D2914), Color(0xDD2C1810))),
        Offset(x, y), Size(bw, bh), CornerRadius(12f)
    )
    drawRoundRect(Cinnabar.copy(alpha = 0.35f), Offset(x, y), Size(bw, bh), CornerRadius(12f), style = Stroke(1.3f))
}
