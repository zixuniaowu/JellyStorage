package com.jellystorage.play

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.atan2
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
    bob: Float = 0f,
    drawPlaceholderWeapon: Boolean = true,
    hero: HeroClass? = null
) {
    val s = bodyR * 0.95f
    val y = cy + bob
    val dir = if (facing >= 0f) 1f else -1f
    val flash = hitFlash > 0.05f
    val outfit = skin.outfit
    val body = if (flash) PaperLite else Color(
        red = outfit.red * 0.55f + 0.15f,
        green = outfit.green * 0.55f + 0.12f,
        blue = outfit.blue * 0.55f + 0.10f
    )
    val ink = if (flash) Color.White else InkDark

    // 墨影
    drawOval(Color(0x45000000), Offset(cx - s * 0.7f, y + s * 0.55f), Size(s * 1.4f, s * 0.32f))
    // 职业骨架先决定轮廓，装备再叠加其上；不再让三职业共用一个圆躯干。
    when (hero) {
        HeroClass.WARRIOR -> {
            val torso = Path().apply {
                moveTo(cx - s * 0.68f, y - s * 0.22f)
                lineTo(cx - s * 0.56f, y + s * 0.46f)
                lineTo(cx - s * 0.28f, y + s * 0.7f)
                lineTo(cx + s * 0.28f, y + s * 0.7f)
                lineTo(cx + s * 0.56f, y + s * 0.46f)
                lineTo(cx + s * 0.68f, y - s * 0.22f)
                quadraticTo(cx, y - s * 0.48f, cx - s * 0.68f, y - s * 0.22f)
                close()
            }
            drawPath(torso, Brush.verticalGradient(listOf(body, body.copy(alpha = 0.72f))))
            drawPath(torso, ink.copy(alpha = 0.76f), style = Stroke(s * 0.075f))
            // 分腿站姿与握武器前臂。
            drawLine(ink.copy(alpha = 0.78f), Offset(cx - s * 0.24f, y + s * 0.55f), Offset(cx - s * 0.42f, y + s * 0.9f), s * 0.2f, StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.78f), Offset(cx + s * 0.24f, y + s * 0.55f), Offset(cx + s * 0.42f, y + s * 0.9f), s * 0.2f, StrokeCap.Round)
            drawLine(body, Offset(cx + dir * s * 0.48f, y - s * 0.02f), Offset(cx + dir * s * 0.82f, y + s * 0.18f), s * 0.25f, StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.7f), Offset(cx - s * 0.54f, y + s * 0.28f), Offset(cx + s * 0.54f, y + s * 0.3f), s * 0.11f, StrokeCap.Round)
        }
        HeroClass.MAGE -> {
            // 披风在后，窄肩长袍在前，移动时像一枚飘动水滴。
            val cape = Path().apply {
                moveTo(cx - s * 0.5f, y - s * 0.3f)
                quadraticTo(cx - s * 0.85f, y + s * 0.3f, cx - s * 0.7f, y + s * 0.92f)
                quadraticTo(cx, y + s * 0.72f, cx + s * 0.7f, y + s * 0.92f)
                quadraticTo(cx + s * 0.85f, y + s * 0.3f, cx + s * 0.5f, y - s * 0.3f)
                close()
            }
            drawPath(cape, body.copy(alpha = 0.58f))
            drawPath(cape, ink.copy(alpha = 0.52f), style = Stroke(s * 0.065f))
            val robe = Path().apply {
                moveTo(cx - s * 0.4f, y - s * 0.18f)
                lineTo(cx - s * 0.52f, y + s * 0.68f)
                lineTo(cx, y + s * 0.84f)
                lineTo(cx + s * 0.52f, y + s * 0.68f)
                lineTo(cx + s * 0.4f, y - s * 0.18f)
                close()
            }
            drawPath(robe, Brush.verticalGradient(listOf(body, body.copy(alpha = 0.68f))))
            drawPath(robe, ink.copy(alpha = 0.7f), style = Stroke(s * 0.065f))
            drawLine(body, Offset(cx + dir * s * 0.32f, y), Offset(cx + dir * s * 0.74f, y - s * 0.08f), s * 0.18f, StrokeCap.Round)
            drawLine(skin.accent.copy(alpha = 0.78f), Offset(cx - s * 0.34f, y + s * 0.3f), Offset(cx + s * 0.34f, y + s * 0.3f), s * 0.075f, StrokeCap.Round)
        }
        HeroClass.TAOIST -> {
            // 宽袖、直身、开衩道袍，和法师的披风轮廓区分。
            val robe = Path().apply {
                moveTo(cx - s * 0.5f, y - s * 0.24f)
                lineTo(cx - s * 0.62f, y + s * 0.7f)
                lineTo(cx - s * 0.12f, y + s * 0.82f)
                lineTo(cx, y + s * 0.34f)
                lineTo(cx + s * 0.12f, y + s * 0.82f)
                lineTo(cx + s * 0.62f, y + s * 0.7f)
                lineTo(cx + s * 0.5f, y - s * 0.24f)
                close()
            }
            drawPath(robe, Brush.verticalGradient(listOf(body, body.copy(alpha = 0.66f))))
            drawPath(robe, ink.copy(alpha = 0.72f), style = Stroke(s * 0.07f))
            for (side in -1..1 step 2) {
                drawLine(body, Offset(cx + side * s * 0.38f, y - s * 0.02f), Offset(cx + side * s * 0.86f, y + s * 0.18f), s * 0.27f, StrokeCap.Round)
                drawLine(ink.copy(alpha = 0.5f), Offset(cx + side * s * 0.42f, y), Offset(cx + side * s * 0.84f, y + s * 0.18f), s * 0.045f, StrokeCap.Round)
            }
            drawLine(skin.accent.copy(alpha = 0.8f), Offset(cx - s * 0.42f, y + s * 0.32f), Offset(cx + s * 0.42f, y + s * 0.32f), s * 0.075f, StrokeCap.Round)
        }
        null -> {
            drawCircle(
                Brush.radialGradient(
                    listOf(body.copy(alpha = 0.96f), body.copy(alpha = 0.82f), body.copy(alpha = 0.5f)),
                    center = Offset(cx - s * 0.2f, y - s * 0.1f), radius = s * 0.95f
                ),
                s * 0.72f, Offset(cx, y + s * 0.05f)
            )
            drawCircle(ink.copy(alpha = 0.75f), s * 0.72f, Offset(cx, y + s * 0.05f), style = Stroke(s * 0.07f))
            drawLine(ink.copy(alpha = 0.5f), Offset(cx - s * 0.5f, y + s * 0.3f), Offset(cx + s * 0.5f, y + s * 0.32f), s * 0.09f, StrokeCap.Round)
        }
    }
    // 头：宣纸色 + 体积渐变
    drawCircle(
        Brush.radialGradient(
            listOf(if (flash) PaperLite else skin.skinTone, if (flash) Color.White else skin.skinTone.copy(alpha = 0.82f)),
            center = Offset(cx - s * 0.12f, y - s * 0.65f), radius = s * 0.55f
        ),
        s * 0.48f, Offset(cx, y - s * 0.55f)
    )
    drawCircle(ink.copy(alpha = 0.65f), s * 0.48f, Offset(cx, y - s * 0.55f), style = Stroke(s * 0.07f))
    // 发（侧锋两笔）
    drawOval(skin.hair.copy(alpha = 0.88f), Offset(cx - s * 0.48f, y - s * 1.05f), Size(s * 0.96f, s * 0.55f))
    drawOval(skin.hair.copy(alpha = 0.5f), Offset(cx - s * 0.3f, y - s * 1.12f), Size(s * 0.6f, s * 0.35f))
    when (hero) {
        HeroClass.WARRIOR -> {
            drawArc(skin.accent, 195f, 150f, false, Offset(cx - s * 0.5f, y - s * 1.04f), Size(s, s * 0.55f), style = Stroke(s * 0.11f, cap = StrokeCap.Round))
            drawLine(skin.accent, Offset(cx, y - s * 1.06f), Offset(cx + dir * s * 0.18f, y - s * 1.35f), s * 0.1f, StrokeCap.Round)
        }
        HeroClass.MAGE -> {
            val hat = Path().apply {
                moveTo(cx - s * 0.62f, y - s * 0.92f)
                lineTo(cx + s * 0.62f, y - s * 0.92f)
                lineTo(cx + dir * s * 0.12f, y - s * 1.72f)
                close()
            }
            drawPath(hat, body.copy(alpha = 0.94f)); drawPath(hat, ink.copy(alpha = 0.72f), style = Stroke(s * 0.065f))
            drawLine(skin.accent, Offset(cx - s * 0.66f, y - s * 0.9f), Offset(cx + s * 0.66f, y - s * 0.9f), s * 0.09f, StrokeCap.Round)
        }
        HeroClass.TAOIST -> {
            drawCircle(skin.hair, s * 0.2f, Offset(cx, y - s * 1.15f))
            drawLine(skin.accent, Offset(cx - s * 0.36f, y - s * 0.9f), Offset(cx + s * 0.36f, y - s * 0.9f), s * 0.075f, StrokeCap.Round)
            drawLine(Cinnabar.copy(alpha = 0.85f), Offset(cx + dir * s * 0.38f, y - s * 0.78f), Offset(cx + dir * s * 0.52f, y - s * 0.45f), s * 0.05f, StrokeCap.Round)
        }
        null -> Unit
    }
    // 眼（两点墨，随朝向）
    val eye = if (flash) ink else skin.eye
    drawCircle(eye, s * 0.07f, Offset(cx - s * 0.14f * dir, y - s * 0.55f))
    drawCircle(eye, s * 0.07f, Offset(cx + s * 0.18f * dir, y - s * 0.55f))
    // 腮红一点朱
    drawCircle(Cinnabar.copy(alpha = 0.3f), s * 0.05f, Offset(cx + dir * s * 0.3f, y - s * 0.46f))
    if (drawPlaceholderWeapon) {
        // 非战斗预览的通用兵器；战斗中由真实装备图谱替代。
        val wx0 = cx + dir * s * 0.55f
        val wy0 = y - s * 0.1f
        val tipX = wx0 + dir * s * 0.95f
        val tipY = wy0 - s * 0.55f
        drawLine(ink, Offset(wx0, wy0), Offset(tipX, tipY), s * 0.13f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(wx0 + dir * s * 0.1f, wy0 - s * 0.08f), Offset(tipX - dir * s * 0.1f, tipY + s * 0.08f), s * 0.035f, StrokeCap.Round)
        drawLine(Cinnabar.copy(alpha = 0.55f), Offset(wx0 + dir * s * 0.2f, wy0 - s * 0.05f), Offset(wx0 + dir * s * 0.9f, wy0 - s * 0.5f), s * 0.05f, StrokeCap.Round)
        drawLine(ink, Offset(wx0, wy0 - s * 0.14f), Offset(wx0 + dir * s * 0.1f, wy0 + s * 0.12f), s * 0.08f, StrokeCap.Round)
    }
}

// ══ 敌人单位形状缓存（单位坐标 r=1、圆心在原点）：绘制时 translate+scale，零每帧分配 ══
private val SlimeDomePath = Path().apply {
    moveTo(-0.95f, 0.35f)
    cubicTo(-1.05f, -0.7f, -0.4f, -1.05f, 0f, -1.0f)
    cubicTo(0.4f, -1.05f, 1.05f, -0.7f, 0.95f, 0.35f)
    cubicTo(0.6f, 0.55f, -0.6f, 0.55f, -0.95f, 0.35f)
    close()
}
private val BeetleShellPath = Path().apply {
    moveTo(-0.95f, 0.25f)
    cubicTo(-1.0f, -0.8f, 1.0f, -0.8f, 0.95f, 0.25f)
    close()
}
private val BatWingPath = Path().apply {
    moveTo(0.3f, -0.1f)
    cubicTo(1.1f, -0.7f, 1.5f, 0.1f, 1.2f, 0.45f)
    cubicTo(0.8f, 0.2f, 0.5f, 0.15f, 0.3f, 0.15f)
    close()
}
private val RatBodyPath = Path().apply {
    moveTo(-1.1f, 0.15f)
    quadraticTo(-0.4f, -0.55f, 0.5f, -0.25f)
    quadraticTo(1.05f, -0.05f, 0.9f, 0.3f)
    quadraticTo(0f, 0.6f, -1.1f, 0.15f)
    close()
}
private val GoblinEarsPath = Path().apply {
    moveTo(-0.5f, -0.45f); lineTo(-1.0f, -0.8f); lineTo(-0.45f, -0.7f); close()
    moveTo(0.5f, -0.45f); lineTo(1.0f, -0.8f); lineTo(0.45f, -0.7f); close()
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
        EnemyKind.PINK_SLIME -> Color(0xFF6D5A54)
        else -> Color(0xFF3F3F46)
    }
    val fill = if (flash) PaperLite else base
    val el = kind.element().color
    val dir = if (facing >= 0f) 1f else -1f
    // 单圈墨晕
    drawCircle(fill.copy(alpha = 0.2f), r * 1.38f, Offset(cx, y))

    // 单位坐标系内绘制（translate 到位置，scale 到半径；描边宽度用单位值）
    translate(cx, y) {
        scale(r, r, pivot = Offset.Zero) {
            when (kind) {
                EnemyKind.MINI_SLIME, EnemyKind.SLIME, EnemyKind.PINK_SLIME -> {
                    drawPath(
                        SlimeDomePath,
                        Brush.verticalGradient(listOf(fill.copy(alpha = 0.95f), fill.copy(alpha = 0.62f)), startY = -1f, endY = 0.5f)
                    )
                    drawPath(SlimeDomePath, InkDark.copy(alpha = 0.6f), style = Stroke(0.1f))
                    drawOval(fill.copy(alpha = 0.75f), Offset(0.43f, 0.3f), Size(0.24f, 0.4f))
                    if (kind == EnemyKind.PINK_SLIME) {
                        drawCircle(Color(0xFFF9A8D4).copy(alpha = 0.3f), 0.2f, Offset(-0.25f, -0.35f))
                        // 桃心触须，和普通史莱姆形成不同剪影。
                        drawLine(Color(0xFFF9A8D4), Offset(-0.16f, -0.88f), Offset(-0.34f, -1.25f), 0.08f, StrokeCap.Round)
                        drawLine(Color(0xFFF9A8D4), Offset(0.16f, -0.88f), Offset(0.34f, -1.25f), 0.08f, StrokeCap.Round)
                        drawCircle(Color(0xFFF472B6), 0.12f, Offset(-0.36f, -1.28f))
                        drawCircle(Color(0xFFF472B6), 0.12f, Offset(0.36f, -1.28f))
                    } else {
                        // 叶芽冠：让最基础敌人也有明确生态特征。
                        drawLine(Jade, Offset(0f, -0.94f), Offset(0.08f, -1.3f), 0.08f, StrokeCap.Round)
                        drawOval(Color(0xFF86EFAC), Offset(0.03f, -1.38f), Size(0.34f, 0.18f))
                    }
                }
                EnemyKind.SPIKE_SLIME -> {
                    drawCircle(fill.copy(alpha = 0.9f), 0.95f, Offset.Zero)
                    for (i in 0..7) {
                        val a = i * 0.785f
                        drawLine(
                            InkDark.copy(alpha = 0.75f),
                            Offset(cos(a) * 0.7f, sin(a) * 0.7f),
                            Offset(cos(a) * 1.35f, sin(a) * 1.35f),
                            0.12f, StrokeCap.Round
                        )
                    }
                    drawCircle(InkDark.copy(alpha = 0.65f), 0.95f, Offset.Zero, style = Stroke(0.09f))
                }
                EnemyKind.BEETLE -> {
                    drawPath(
                        BeetleShellPath,
                        Brush.verticalGradient(listOf(fill.copy(alpha = 0.95f), InkDark.copy(alpha = 0.55f)), startY = -0.8f, endY = 0.25f)
                    )
                    drawPath(BeetleShellPath, InkDark.copy(alpha = 0.75f), style = Stroke(0.1f))
                    drawLine(InkDark.copy(alpha = 0.55f), Offset(0f, -0.85f), Offset(0f, 0.25f), 0.08f)
                    drawLine(InkDark.copy(alpha = 0.55f), Offset(-0.5f, -0.15f), Offset(0.5f, -0.15f), 0.05f)
                    // 双角
                    drawLine(InkDark, Offset(-0.2f, -0.75f), Offset(-0.45f, -1.25f), 0.09f, StrokeCap.Round)
                    drawLine(InkDark, Offset(0.2f, -0.75f), Offset(0.45f, -1.25f), 0.09f, StrokeCap.Round)
                    // 六足和钳角，强化“重甲冲锋”轮廓。
                    for (i in -1..1) {
                        val ly = -0.28f + i * 0.3f
                        drawLine(InkDark, Offset(-0.62f, ly), Offset(-1.12f, ly + i * 0.12f), 0.09f, StrokeCap.Round)
                        drawLine(InkDark, Offset(0.62f, ly), Offset(1.12f, ly + i * 0.12f), 0.09f, StrokeCap.Round)
                    }
                    drawArc(el.copy(alpha = 0.6f), 205f, 130f, false, Offset(-0.42f, -0.62f), Size(0.84f, 0.84f), style = Stroke(0.08f))
                }
                EnemyKind.BAT -> {
                    val flap = (bob * 0.25f).coerceIn(-1f, 1f)
                    drawCircle(InkDark.copy(alpha = 0.9f), 0.5f, Offset.Zero)
                    // 右翼：绕肩点旋转模拟振翅
                    rotate(degrees = flap * 22f, pivot = Offset(0.3f, -0.1f)) {
                        drawPath(BatWingPath, fill.copy(alpha = 0.85f))
                        drawPath(BatWingPath, InkDark.copy(alpha = 0.6f), style = Stroke(0.07f))
                    }
                    scale(-1f, 1f, pivot = Offset.Zero) {
                        rotate(degrees = flap * 22f, pivot = Offset(0.3f, -0.1f)) {
                            drawPath(BatWingPath, fill.copy(alpha = 0.85f))
                            drawPath(BatWingPath, InkDark.copy(alpha = 0.6f), style = Stroke(0.07f))
                        }
                    }
                    drawCircle(el.copy(alpha = 0.5f), 0.16f, Offset(0f, -0.1f))
                    drawLine(InkDark, Offset(-0.22f, -0.36f), Offset(-0.38f, -0.78f), 0.1f, StrokeCap.Round)
                    drawLine(InkDark, Offset(0.22f, -0.36f), Offset(0.38f, -0.78f), 0.1f, StrokeCap.Round)
                    drawLine(PaperLite, Offset(-0.12f, 0.2f), Offset(-0.05f, 0.42f), 0.07f, StrokeCap.Round)
                    drawLine(PaperLite, Offset(0.12f, 0.2f), Offset(0.05f, 0.42f), 0.07f, StrokeCap.Round)
                }
                EnemyKind.SKELETON -> {
                    drawCircle(if (flash) PaperLite else Color(0xFFD6D3D1), 0.72f, Offset(0f, -0.35f))
                    drawCircle(InkDark.copy(alpha = 0.8f), 0.72f, Offset(0f, -0.35f), style = Stroke(0.1f))
                    drawCircle(InkDark, 0.16f, Offset(-0.26f, -0.42f))
                    drawCircle(InkDark, 0.16f, Offset(0.26f, -0.42f))
                    drawLine(Color(0xFFD6D3D1).copy(alpha = 0.9f), Offset(0f, 0.1f), Offset(0f, 0.7f), 0.16f, StrokeCap.Round)
                    for (i in 0..2) {
                        drawLine(Color(0xFFD6D3D1).copy(alpha = 0.7f), Offset(-0.4f, 0.2f + i * 0.2f), Offset(0.4f, 0.2f + i * 0.2f), 0.06f)
                    }
                    // 骨刃和持刀臂，与横扫招式对应。
                    drawLine(Color(0xFFD6D3D1), Offset(dir * 0.25f, 0.15f), Offset(dir * 0.78f, 0.42f), 0.1f, StrokeCap.Round)
                    drawLine(Color(0xFFCBD5E1), Offset(dir * 0.7f, 0.42f), Offset(dir * 1.28f, -0.18f), 0.14f, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.65f), Offset(dir * 0.76f, 0.34f), Offset(dir * 1.24f, -0.16f), 0.035f, StrokeCap.Round)
                }
                EnemyKind.GOBLIN -> {
                    drawOval(fill.copy(alpha = 0.92f), Offset(-0.6f, -0.3f), Size(1.2f, 1.25f))
                    drawOval(InkDark.copy(alpha = 0.6f), Offset(-0.6f, -0.3f), Size(1.2f, 1.25f), style = Stroke(0.09f))
                    drawPath(GoblinEarsPath, fill.copy(alpha = 0.95f))
                    drawLine(InkDark, Offset(dir * 0.5f, 0f), Offset(dir * 1.1f, -0.5f), 0.12f, StrokeCap.Round)
                    drawCircle(Color(0xFFD97706).copy(alpha = 0.8f), 0.14f, Offset(dir * 1.1f, -0.5f))
                    // 背鼓与火罐：轮廓直接提示其鼓舞/投掷职责。
                    drawCircle(Color(0xFF9A3412), 0.42f, Offset(-dir * 0.52f, 0.36f))
                    drawCircle(Color(0xFFFDBA74), 0.34f, Offset(-dir * 0.52f, 0.36f), style = Stroke(0.08f))
                    drawCircle(Color(0xFFFB923C), 0.18f, Offset(dir * 0.68f, 0.22f))
                }
                EnemyKind.RAT -> {
                    drawPath(RatBodyPath, fill.copy(alpha = 0.92f))
                    drawPath(RatBodyPath, InkDark.copy(alpha = 0.65f), style = Stroke(0.08f))
                    // 尾（一笔回锋）
                    val wag = (bob * 0.12f).coerceIn(-0.3f, 0.3f)
                    drawLine(InkDark.copy(alpha = 0.7f), Offset(-1.05f, 0.2f), Offset(-1.7f, -0.2f + wag), 0.08f, StrokeCap.Round)
                    drawCircle(fill, 0.22f, Offset(0.35f, -0.5f))
                    drawCircle(InkDark.copy(alpha = 0.6f), 0.1f, Offset(dir * 0.75f, -0.1f))
                    drawCircle(fill, 0.2f, Offset(-0.28f, -0.53f))
                    drawCircle(fill, 0.2f, Offset(0.18f, -0.55f))
                    drawLine(PaperLite, Offset(dir * 0.73f, 0.02f), Offset(dir * 0.92f, 0.16f), 0.06f, StrokeCap.Round)
                    for (i in -1..1) drawLine(InkDark.copy(alpha = 0.5f), Offset(dir * 0.55f, 0.02f + i * 0.08f), Offset(dir * 1.15f, -0.02f + i * 0.14f), 0.025f)
                }
                EnemyKind.WISP -> {
                    drawCircle(el.copy(alpha = 0.15f), 1.5f, Offset.Zero)
                    drawCircle(el.copy(alpha = 0.3f), 0.95f, Offset.Zero)
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xFFFEF9C3), el), center = Offset(0f, -0.1f), radius = 0.7f),
                        0.5f, Offset.Zero
                    )
                    for (i in 0..2) {
                        drawOval(el.copy(alpha = 0.3f - i * 0.08f), Offset(-0.14f, 0.3f + i * 0.25f), Size(0.28f, 0.42f))
                    }
                    // 环绕魂灯碎片，呼应轮射与治疗定位。
                    for (i in 0..3) {
                        val a = i * 1.57f + bob * 0.08f
                        drawCircle(el.copy(alpha = 0.72f), 0.09f, Offset(cos(a) * 0.82f, sin(a) * 0.58f))
                    }
                }
                EnemyKind.BOSS_SLIME, EnemyKind.BOSS_ORE -> {
                    drawCircle(fill.copy(alpha = 0.92f), 0.95f, Offset.Zero)
                    drawCircle(el.copy(alpha = 0.2f), 1.15f, Offset.Zero)
                    drawCircle(InkDark.copy(alpha = 0.55f), 0.95f, Offset.Zero, style = Stroke(0.16f))
                    drawCircle(InkDark.copy(alpha = 0.8f), 0.95f, Offset.Zero, style = Stroke(0.07f))
                    if (kind == EnemyKind.BOSS_SLIME) {
                        for (i in -1..1) {
                            drawLine(Color(0xFFFBBF24).copy(alpha = 0.9f), Offset(i * 0.3f, -0.8f), Offset(i * 0.4f, -1.3f), 0.14f, StrokeCap.Round)
                        }
                    } else {
                        for (i in -1..1) {
                            val gemH = if (i == 0) 0.75f else 0.5f
                            drawLine(el.copy(alpha = 0.85f), Offset(i * 0.4f - 0.1f, -0.7f), Offset(i * 0.4f, -0.7f - gemH), 0.18f, StrokeCap.Round)
                            drawLine(el.copy(alpha = 0.85f), Offset(i * 0.4f, -0.7f - gemH), Offset(i * 0.4f + 0.1f, -0.7f), 0.18f, StrokeCap.Round)
                        }
                    }
                }
            }
            // 通用：眼（无自带眼的种类）
            if (kind !in listOf(EnemyKind.SKELETON, EnemyKind.BAT, EnemyKind.RAT, EnemyKind.WISP, EnemyKind.GOBLIN)) {
                val eyeCol = if (flash) InkDark else PaperLite
                drawCircle(eyeCol, 0.11f, Offset(-0.25f, -0.15f))
                drawCircle(eyeCol, 0.11f, Offset(0.22f, -0.15f))
            }
            // 五行胸章
            drawCircle(el.copy(alpha = 0.55f), 0.22f, Offset(facing * 0.3f, 0.25f))
        }
    }
    if (elite) drawCircle(Cinnabar, r * 1.12f, Offset(cx, y), style = Stroke(r * 0.1f))
    if (enraged) drawCircle(Cinnabar.copy(alpha = 0.35f), r * 1.28f, Offset(cx, y), style = Stroke(r * 0.08f))
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
        7 -> Color(0xFFA78BFA) // 蝙蝠声波
        8 -> Color(0xFFCBD5E1) // 棘刺
        10 -> Color(0xFFFB923C) // 哥布林火罐
        11 -> Color(0xFFF472B6) // 桃心散弹
        12 -> Color(0xFF7C3AED) // 幽火轮射
        else -> InkMid
    }
    // 主笔：前粗后细感用两笔
    drawLine(InkDark.copy(alpha = 0.4f * alpha), Offset(prevX, prevY), Offset(x, y), radius * 2.2f, StrokeCap.Round)
    when (style) {
        1 -> {
            // 法师火球：彗星——焰尾渐宽 + 跳动大弹头
            val ang = atan2(y - prevY, x - prevX)
            val flick = 0.85f + 0.3f * sin(x * 0.11f + y * 0.07f)
            drawLine(Color(0xFFFFB25E).copy(alpha = 0.5f * alpha), Offset(prevX, prevY), Offset(x, y), radius * 1.5f * flick, StrokeCap.Round)
            drawCircle(core.copy(alpha = 0.95f * alpha), radius * 0.6f * flick, Offset(x, y))
            drawCircle(Color(0xFFFFE0A3).copy(alpha = 0.9f * alpha), radius * 0.3f * flick, Offset(x, y))
        }
        2 -> {
            // 道士灵符：旋转的符纸方片 + 朱砂符文（与法师圆球完全区分）
            val ang = atan2(y - prevY, x - prevX) + 9f * x * 0.05f
            rotate(degrees = ang * 57.29578f, pivot = Offset(x, y)) {
                drawRoundRect(
                    Color(0xFFF5EBD4).copy(alpha = 0.95f * alpha),
                    Offset(x - radius * 0.55f, y - radius * 0.75f),
                    Size(radius * 1.1f, radius * 1.5f),
                    CornerRadius(1.5f)
                )
                drawRoundRect(
                    Cinnabar.copy(alpha = 0.85f * alpha),
                    Offset(x - radius * 0.55f, y - radius * 0.75f),
                    Size(radius * 1.1f, radius * 1.5f),
                    CornerRadius(1.5f),
                    style = Stroke(1.4f)
                )
                drawLine(Cinnabar.copy(alpha = 0.8f * alpha), Offset(x, y - radius * 0.5f), Offset(x, y + radius * 0.45f), 1.6f, StrokeCap.Round)
                drawLine(Cinnabar.copy(alpha = 0.7f * alpha), Offset(x - radius * 0.32f, y - radius * 0.1f), Offset(x + radius * 0.32f, y - radius * 0.1f), 1.4f, StrokeCap.Round)
            }
            drawLine(Color(0xFF86EFAC).copy(alpha = 0.5f * alpha), Offset(prevX, prevY), Offset(x, y), radius * 0.8f, StrokeCap.Round)
        }
        else -> {
            drawLine(core.copy(alpha = 0.75f * alpha), Offset(prevX, prevY), Offset(x, y), radius * 1.1f, StrokeCap.Round)
            drawCircle(core.copy(alpha = 0.9f * alpha), radius * 0.45f, Offset(x, y))
        }
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
