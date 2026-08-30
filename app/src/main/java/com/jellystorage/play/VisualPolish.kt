package com.jellystorage.play

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Canvas 装备图位图缓存：小尺寸场景（列表/商店/穿戴槽）统一使用 288px 一次性
 * 离屏渲染 + FilterQuality.High 缩放，获得接近手绘位图的平滑观感，
 * 同时把每帧的 Path 开销从滚动列表里拿掉。大图预览仍走实时绘制以保留动画。
 */
object GearIconCache {
    private const val RENDER = 288
    private const val CONTENT = 160f
    const val DST_SCALE = RENDER / CONTENT

    private val cache = object : android.util.LruCache<String, ImageBitmap>(36) {}

    val centerX: Float get() = RENDER / 2f
    val centerY: Float get() = RENDER / 2f
    val contentSize: Float get() = CONTENT

    fun bitmap(key: String, render: DrawScope.() -> Unit): ImageBitmap? {
        cache.get(key)?.let { return it }
        return try {
            val bmp = Bitmap.createBitmap(RENDER, RENDER, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp.asImageBitmap())
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, canvas, Size(RENDER.toFloat(), RENDER.toFloat())
            ) {
                render()
            }
            bmp.asImageBitmap().also { cache.put(key, it) }
        } catch (_: Throwable) {
            null
        }
    }
}

/** 缓存位图按内容对齐绘制（艺术内容在源图中心 CONTENT px） */
private fun DrawScope.drawCachedGearIcon(image: ImageBitmap, cx: Float, cy: Float, size: Float) {
    val edge = size * GearIconCache.DST_SCALE
    drawImage(
        image = image,
        dstOffset = IntOffset((cx - edge / 2f).roundToInt(), (cy - edge / 2f).roundToInt()),
        dstSize = IntSize(edge.roundToInt(), edge.roundToInt()),
        filterQuality = FilterQuality.High
    )
}

private fun DrawScope.drawPremiumGearIcon(
    image: ImageBitmap,
    cx: Float,
    cy: Float,
    size: Float
) {
    val edge = (size * 1.5f).roundToInt().coerceAtLeast(1)
    drawImage(
        image = image,
        dstOffset = IntOffset((cx - edge / 2f).roundToInt(), (cy - edge / 2f).roundToInt()),
        dstSize = IntSize(edge, edge),
        filterQuality = FilterQuality.High
    )
}

// ═══════════════ 水墨图标工具箱 ═══════════════

/** 稀有度色环：传奇金 / 史诗紫 / 普通青灰 */
internal fun rarityRing(rarity: Int): Color = when (rarity) {
    2 -> Color(0xFFFBBF24)
    1 -> Color(0xFFA78BFA)
    else -> Color(0xFF64748B)
}

/**
 * 宣纸墨晕底座：落影 + 元素晕染 + 稀有度双笔圈。
 * 所有装备图标的统一第一层，让整货架看起来像画在同一张宣纸上。
 */
private fun DrawScope.drawIconBackplate(
    cx: Float,
    cy: Float,
    s: Float,
    el: Color,
    rarity: Int,
    pulse: Float = 0f
) {
    drawOval(Color(0x3D000000), topLeft = Offset(cx - s * 0.48f, cy + s * 0.4f), size = Size(s * 0.96f, s * 0.22f))
    drawCircle(
        Brush.radialGradient(listOf(el.copy(alpha = 0.2f), el.copy(alpha = 0.07f), Color.Transparent)),
        s * 0.9f,
        Offset(cx, cy)
    )
    val ring = rarityRing(rarity)
    drawCircle(ring.copy(alpha = 0.12f + 0.04f * sin(pulse * 2f)), s * 0.66f, Offset(cx, cy), style = Stroke(s * 0.055f))
    drawCircle(ring.copy(alpha = 0.5f), s * 0.66f, Offset(cx, cy), style = Stroke(1.2f))
}

/** 毛笔双锋描边：外晕一笔 + 内实一笔，模拟宣纸上的墨线（细度有下限，防缩放后消失） */
private fun DrawScope.inkStroke(path: Path, color: Color, width: Float) {
    drawPath(path, color.copy(alpha = 0.28f), style = Stroke((width * 2.1f).coerceAtLeast(2.8f)))
    drawPath(path, color, style = Stroke(width.coerceAtLeast(1.4f)))
}

/** 六面切角宝石：亮面渐变 + 刻面线 + 白高光 */
private fun DrawScope.drawFacetGem(cx: Float, cy: Float, r: Float, col: Color) {
    val pts = Array(6) { i ->
        val a = -Math.PI.toFloat() / 2f + i * (Math.PI.toFloat() / 3f)
        Offset(cx + cos(a) * r, cy + sin(a) * r)
    }
    val gem = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until 6) lineTo(pts[i].x, pts[i].y)
        close()
    }
    drawPath(gem, Brush.verticalGradient(listOf(lerp(col, Color.White, 0.45f), col, lerp(col, Color.Black, 0.45f))))
    drawPath(gem, Color(0x660F172A), style = Stroke(1f))
    // 上半刻面亮线
    drawLine(lerp(col, Color.White, 0.55f), pts[1], pts[5], 1f)
    drawLine(lerp(col, Color.White, 0.3f), pts[0], Offset((pts[1].x + pts[5].x) / 2f, (pts[1].y + pts[5].y) / 2f), 1f)
    drawCircle(Color.White.copy(alpha = 0.75f), r * 0.16f, Offset(cx - r * 0.28f, cy - r * 0.3f))
}

/** 缠绳握柄：木柄 + 斜向绑带 + 墨线勾边 */
private fun DrawScope.drawWrappedGrip(cx: Float, top: Float, w: Float, h: Float, bands: Int, base: Color, wrap: Color) {
    val r = CornerRadius(w * 0.35f, w * 0.35f)
    drawRoundRect(base, Offset(cx - w / 2f, top), Size(w, h), r)
    val step = h / bands
    for (i in 0 until bands) {
        drawLine(
            wrap,
            Offset(cx - w * 0.5f, top + step * i + step * 0.2f),
            Offset(cx + w * 0.5f, top + step * i + step * 0.8f),
            (w * 0.18f).coerceAtLeast(1.2f),
            StrokeCap.Round
        )
    }
    drawRoundRect(Color(0x40000000), Offset(cx - w / 2f, top), Size(w, h), r, style = Stroke(1f))
}

/** 流苏：几缕下垂的丝线，道器和枪头共用 */
private fun DrawScope.drawTassel(cx: Float, top: Float, s: Float, col: Color, sway: Float) {
    for (i in -2..2) {
        val dx = i * s * 0.045f
        val bend = sin(sway + i) * s * 0.04f
        drawLine(
            col.copy(alpha = 0.85f),
            Offset(cx + dx * 0.5f, top),
            Offset(cx + dx * 1.6f + bend, top + s * 0.16f + abs(i) * s * 0.015f),
            s * 0.022f,
            StrokeCap.Round
        )
    }
    drawCircle(col, s * 0.035f, Offset(cx, top + s * 0.01f))
}
/**
 * 每把武器专属配图（按 id 分支，再 fallback 职业轮廓）。
 * 分发顺序：手绘 PNG → 小尺寸位图缓存 → 实时绘制（大图带动画）。
 */
fun DrawScope.drawWeaponArt(
    cx: Float,
    cy: Float,
    size: Float,
    gear: GearWeapon,
    pulse: Float = 0f
) {
    val el = gear.element.color
    // 小展示位用 128px 缩略图：战斗掉落/HUD 不再同步解码 512px 大图
    val premium = if (size <= 120f) GearArtAssets.getThumb(gear.id) ?: GearArtAssets.get(gear.id)
    else GearArtAssets.get(gear.id)
    premium?.let {
        drawPremiumGearIcon(it, cx, cy, size)
        drawCircle(Color(0xFF0F172A), size * 0.12f, Offset(cx + size * 0.38f, cy - size * 0.38f))
        drawCircle(el, size * 0.09f, Offset(cx + size * 0.38f, cy - size * 0.38f))
        return
    }
    if (size <= 130f) {
        val cached = GearIconCache.bitmap("w|${gear.id}") {
            drawWeaponArtLive(GearIconCache.centerX, GearIconCache.centerY, GearIconCache.contentSize, gear, 0f)
        }
        if (cached != null) {
            drawCachedGearIcon(cached, cx, cy, size)
            return
        }
    }
    drawWeaponArtLive(cx, cy, size, gear, pulse)
}

/**
 * 战斗中的真实穿戴武器：直接使用图谱透明素材，不带商店/图鉴背板。
 * 攻击时轻微摆动，让换武器不仅改数字，也立刻改变场上轮廓与属性色。
 */
fun DrawScope.drawBattleWeaponArt(
    cx: Float,
    cy: Float,
    size: Float,
    gear: GearWeapon,
    facing: Float,
    attackPulse: Float
) {
    val color = gear.element.color
    val pulse = attackPulse.coerceIn(0f, 1f)
    drawCircle(color.copy(alpha = 0.10f + pulse * 0.12f), size * (0.56f + pulse * 0.12f), Offset(cx, cy))
    val art = GearArtAssets.getThumb(gear.id) ?: GearArtAssets.get(gear.id)
    rotate(
        degrees = (if (facing >= 0f) 1f else -1f) * (8f + pulse * 24f),
        pivot = Offset(cx, cy)
    ) {
        if (art != null) drawPremiumGearIcon(art, cx, cy, size)
        else drawWeaponArtLive(cx, cy, size, gear, 0f)
    }
    drawCircle(color.copy(alpha = 0.72f), size * 0.48f, Offset(cx, cy), style = Stroke(1.4f))
    repeat(gear.rarity.coerceIn(0, 2) + 1) { index ->
        drawCircle(
            if (gear.rarity >= 2) Color(0xFFFDE68A) else color,
            size * 0.035f,
            Offset(cx - size * 0.09f + index * size * 0.09f, cy + size * 0.48f)
        )
    }
}

/** 防具以脚下护甲纹显示：层数对应阶级，颜色对应五行/职业。 */
fun DrawScope.drawBattleArmorAura(cx: Float, cy: Float, radius: Float, armor: GearArmor, pulse: Float) {
    if (armor.id == "a_cloth") return
    val color = armor.element?.color ?: when (armor.hero) {
        HeroClass.WARRIOR -> Color(0xFFEF4444)
        HeroClass.MAGE -> Color(0xFF60A5FA)
        HeroClass.TAOIST -> Color(0xFF4ADE80)
        null -> Color(0xFF94A3B8)
    }
    val breathe = 1f + sin(pulse * 2.5f) * 0.025f
    val layers = (1 + armor.tier / 2).coerceIn(1, 3)
    repeat(layers) { index ->
        drawCircle(
            color.copy(alpha = 0.24f + armor.rarity * 0.08f),
            radius * breathe * (1.15f + index * 0.16f),
            Offset(cx, cy + radius * 0.45f),
            style = Stroke(1.8f + armor.rarity * 0.7f)
        )
    }
}

/**
 * 战斗人物的真实装备外形。防具改变躯干/肩部轮廓，戒指成为绕身灵珠，鞋子改变足部与移动尾迹。
 * 只叠加装备部件，不遮住皮肤的脸和发型，换装后无需看 HUD 也能辨认。
 */
fun DrawScope.drawBattleEquipmentForm(
    cx: Float,
    cy: Float,
    radius: Float,
    hero: HeroClass,
    armor: GearArmor,
    ring: GearAccessory?,
    boots: GearAccessory,
    facing: Float,
    moving: Boolean,
    pulse: Float
) {
    val dir = if (facing >= 0f) 1f else -1f
    val armorColor = armor.element?.color ?: when (armor.hero ?: hero) {
        HeroClass.WARRIOR -> Color(0xFFB45309)
        HeroClass.MAGE -> Color(0xFF2563EB)
        HeroClass.TAOIST -> Color(0xFF15803D)
    }
    val edge = lerp(armorColor, Color(0xFF1C1410), 0.48f)
    val shine = lerp(armorColor, Color.White, 0.46f)
    val tier = armor.tier.coerceIn(1, 5)

    // 鞋履与移动尾迹；不同靴子从铁重、风轻到流星焰迹有明确差异。
    val footY = cy + radius * 0.58f
    val bootColor = boots.element?.color ?: if (boots.id.contains("iron") || boots.id.contains("quake")) {
        Color(0xFF64748B)
    } else Color(0xFF78350F)
    if (moving && boots.id !in listOf("b_cloth", "b_leather")) {
        val trailCount = if (boots.rarity >= 2) 3 else 2
        repeat(trailCount) { index ->
            val back = dir * radius * (0.48f + index * 0.3f)
            drawLine(
                bootColor.copy(alpha = 0.34f - index * 0.07f),
                Offset(cx - back, footY + (index - 1) * radius * 0.13f),
                Offset(cx - back - dir * radius * 0.44f, footY + (index - 1) * radius * 0.18f),
                2.4f, StrokeCap.Round
            )
        }
    }
    for (side in -1..1 step 2) {
        val fx = cx + side * radius * 0.31f
        drawRoundRect(
            bootColor.copy(alpha = 0.94f),
            Offset(fx - radius * 0.19f, footY - radius * 0.13f),
            Size(radius * 0.42f, radius * 0.24f),
            CornerRadius(radius * 0.1f)
        )
        if (boots.id.contains("iron") || boots.id.contains("quake")) {
            drawLine(Color(0xFFE2E8F0), Offset(fx - radius * 0.15f, footY), Offset(fx + radius * 0.16f, footY), radius * 0.05f)
        }
    }

    if (armor.id != "a_cloth") {
        when (armor.hero ?: hero) {
            HeroClass.WARRIOR -> {
                // 宽肩胸铠；高阶增加肩刺和额甲，整体剪影随阶级变厚重。
                val chest = Path().apply {
                    moveTo(cx - radius * 0.62f, cy - radius * 0.2f)
                    lineTo(cx - radius * 0.48f, cy + radius * 0.48f)
                    quadraticTo(cx, cy + radius * 0.72f, cx + radius * 0.48f, cy + radius * 0.48f)
                    lineTo(cx + radius * 0.62f, cy - radius * 0.2f)
                    quadraticTo(cx, cy - radius * 0.52f, cx - radius * 0.62f, cy - radius * 0.2f)
                    close()
                }
                drawPath(chest, Brush.verticalGradient(listOf(shine, armorColor, edge)))
                drawPath(chest, edge, style = Stroke(radius * 0.07f))
                for (side in -1..1 step 2) {
                    val sx = cx + side * radius * 0.62f
                    drawCircle(armorColor, radius * (0.25f + tier * 0.018f), Offset(sx, cy - radius * 0.12f))
                    drawCircle(shine, radius * 0.18f, Offset(sx - side * radius * 0.04f, cy - radius * 0.18f), style = Stroke(radius * 0.045f))
                    if (tier >= 3) {
                        drawLine(edge, Offset(sx, cy - radius * 0.3f), Offset(sx + side * radius * 0.24f, cy - radius * (0.55f + tier * 0.03f)), radius * 0.1f, StrokeCap.Round)
                    }
                }
                drawLine(shine.copy(alpha = 0.78f), Offset(cx, cy - radius * 0.3f), Offset(cx, cy + radius * 0.54f), radius * 0.055f)
                if (tier >= 4) {
                    drawArc(armorColor, 195f, 150f, false, Offset(cx - radius * 0.46f, cy - radius * 1.02f), Size(radius * 0.92f, radius * 0.48f), style = Stroke(radius * 0.13f, cap = StrokeCap.Round))
                }
            }
            HeroClass.MAGE -> {
                // 长袍双摆和浮动法片，强调轻盈法系轮廓。
                val robe = Path().apply {
                    moveTo(cx - radius * 0.52f, cy - radius * 0.2f)
                    quadraticTo(cx - radius * 0.65f, cy + radius * 0.34f, cx - radius * 0.82f, cy + radius * 0.7f)
                    lineTo(cx - radius * 0.08f, cy + radius * 0.58f)
                    lineTo(cx, cy + radius * 0.1f)
                    lineTo(cx + radius * 0.08f, cy + radius * 0.58f)
                    lineTo(cx + radius * 0.82f, cy + radius * 0.7f)
                    quadraticTo(cx + radius * 0.65f, cy + radius * 0.34f, cx + radius * 0.52f, cy - radius * 0.2f)
                    close()
                }
                drawPath(robe, Brush.verticalGradient(listOf(shine.copy(alpha = 0.92f), armorColor.copy(alpha = 0.9f), edge.copy(alpha = 0.88f))))
                drawPath(robe, edge, style = Stroke(radius * 0.055f))
                drawArc(shine, 15f, 150f, false, Offset(cx - radius * 0.42f, cy - radius * 0.4f), Size(radius * 0.84f, radius * 0.46f), style = Stroke(radius * 0.07f))
                if (tier >= 3) repeat((tier - 1).coerceAtMost(4)) { index ->
                    val a = pulse * 1.35f + index * 1.57f
                    val ox = cos(a) * radius * (0.8f + tier * 0.035f)
                    val oy = sin(a) * radius * 0.42f
                    rotate(45f, Offset(cx + ox, cy + oy)) {
                        drawRect(armorColor.copy(alpha = 0.82f), Offset(cx + ox - radius * 0.08f, cy + oy - radius * 0.08f), Size(radius * 0.16f, radius * 0.16f))
                    }
                }
            }
            HeroClass.TAOIST -> {
                // 交领道袍、垂符和高阶发冠。
                val left = Path().apply {
                    moveTo(cx - radius * 0.55f, cy - radius * 0.25f)
                    lineTo(cx + radius * 0.08f, cy + radius * 0.08f)
                    lineTo(cx - radius * 0.18f, cy + radius * 0.7f)
                    lineTo(cx - radius * 0.65f, cy + radius * 0.48f)
                    close()
                }
                val right = Path().apply {
                    moveTo(cx + radius * 0.55f, cy - radius * 0.25f)
                    lineTo(cx - radius * 0.08f, cy + radius * 0.08f)
                    lineTo(cx + radius * 0.18f, cy + radius * 0.7f)
                    lineTo(cx + radius * 0.65f, cy + radius * 0.48f)
                    close()
                }
                drawPath(left, armorColor.copy(alpha = 0.94f)); drawPath(right, shine.copy(alpha = 0.88f))
                drawPath(left, edge, style = Stroke(radius * 0.05f)); drawPath(right, edge, style = Stroke(radius * 0.05f))
                drawRoundRect(Color(0xFFFDE68A), Offset(cx - radius * 0.13f, cy + radius * 0.12f), Size(radius * 0.26f, radius * 0.46f), CornerRadius(radius * 0.035f))
                drawLine(Color(0xFFB91C1C), Offset(cx, cy + radius * 0.18f), Offset(cx, cy + radius * 0.48f), radius * 0.035f)
                drawLine(Color(0xFFB91C1C), Offset(cx - radius * 0.08f, cy + radius * 0.3f), Offset(cx + radius * 0.08f, cy + radius * 0.3f), radius * 0.035f)
                if (tier >= 3) {
                    drawLine(edge, Offset(cx - radius * 0.28f, cy - radius * 0.88f), Offset(cx + radius * 0.28f, cy - radius * 0.88f), radius * 0.12f, StrokeCap.Round)
                    drawLine(armorColor, Offset(cx, cy - radius * 0.88f), Offset(cx, cy - radius * 1.18f), radius * 0.1f, StrokeCap.Round)
                }
            }
        }
        // 通用鳞甲覆盖各职业基础版式，增加可识别的鳞片纹。
        if (armor.hero == null && armor.id.contains("scale")) {
            for (row in 0..2) for (column in -2..2) {
                val ox = (column + if (row % 2 == 0) 0f else 0.5f) * radius * 0.2f
                drawArc(shine.copy(alpha = 0.78f), 0f, 180f, false, Offset(cx + ox - radius * 0.1f, cy + row * radius * 0.18f), Size(radius * 0.2f, radius * 0.15f), style = Stroke(radius * 0.035f))
            }
        }
    }

    // 戒指转化为绕身灵珠；稀有度决定轨道和副珠数量。
    ring?.let { equipped ->
        val ringColor = equipped.element?.color ?: Color(0xFFFBBF24)
        val orbit = pulse * (1.7f + equipped.rarity * 0.28f)
        val orbitR = radius * (0.82f + equipped.tier * 0.035f)
        drawOval(ringColor.copy(alpha = 0.28f), Offset(cx - orbitR, cy - radius * 0.18f), Size(orbitR * 2f, radius * 0.52f), style = Stroke(1.3f))
        repeat(equipped.rarity + 1) { index ->
            val a = orbit + index * (6.283f / (equipped.rarity + 1))
            val gx = cx + cos(a) * orbitR
            val gy = cy + sin(a) * radius * 0.26f
            drawCircle(ringColor.copy(alpha = 0.18f), radius * 0.16f, Offset(gx, gy))
            drawCircle(lerp(ringColor, Color.White, 0.28f), radius * 0.075f, Offset(gx, gy))
        }
    }
}

private fun DrawScope.drawWeaponArtLive(
    cx: Float,
    cy: Float,
    s: Float,
    gear: GearWeapon,
    pulse: Float
) {
    val el = gear.element.color
    val shine = Color.White.copy(alpha = 0.35f + 0.15f * ((sin(pulse * 4f) + 1f) * 0.5f))
    drawIconBackplate(cx, cy, s, el, gear.rarity, pulse)

    when (gear.id) {
        // ── 战士 ──
        "w_iron" -> drawSwordBase(cx, cy, s, Color(0xFF94A3B8), shine, short = true, rust = true)
        "w_steel" -> drawSwordBase(cx, cy, s, Color(0xFFE2E8F0), shine, long = true)
        "w_blood" -> drawSwordBase(cx, cy, s, Color(0xFFEF4444), shine, long = true, grooved = true)
        "w_quake" -> drawAxeArt(cx, cy, s, Color(0xFFD97706), shine)
        "w_gold_spear" -> drawSpearArt(cx, cy, s, Color(0xFFFBBF24), shine)
        "w_flame" -> drawSwordBase(cx, cy, s, Color(0xFFFF6B35), shine, long = true, wide = true, flame = true, t = pulse)
        "w_thund_edge" -> {
            drawSwordBase(cx - s * 0.13f, cy, s * 0.8f, Color(0xFF38BDF8), shine)
            drawSwordBase(cx + s * 0.13f, cy, s * 0.8f, Color(0xFFA78BFA), shine)
            drawLine(Color(0xFFFDE68A), Offset(cx, cy - s * 0.52f), Offset(cx - s * 0.04f, cy - s * 0.3f), 2f, StrokeCap.Round)
            drawLine(Color(0xFFFDE68A), Offset(cx - s * 0.04f, cy - s * 0.3f), Offset(cx + s * 0.04f, cy + s * 0.1f), 2f, StrokeCap.Round)
            drawCircle(Color(0xAAFDE68A), s * 0.07f, Offset(cx, cy - s * 0.56f))
        }
        "w_guard_blade" -> drawSwordBase(cx, cy, s, Color(0xFF86EFAC), shine, tower = true)
        // ── 法师 ──
        "w_staff", "m_flame" -> drawStaffBase(cx, cy, s, el, shine, orbScale = 1f, flame = gear.id == "m_flame", t = pulse)
        "m_ice" -> drawStaffBase(cx, cy, s, el, shine, orbScale = 1.15f, icy = true, t = pulse)
        "m_thunder" -> drawStaffBase(cx, cy, s, el, shine, tall = true, bolt = true, t = pulse)
        "m_wood_orb" -> drawWoodOrbArt(cx, cy, s, el, shine, pulse)
        "m_crown" -> drawStaffBase(cx, cy, s, el, shine, orbScale = 1.25f, crown = true, t = pulse)
        "m_abyss" -> drawAbyssTomeArt(cx, cy, s, el, shine, pulse)
        "m_earth_tome" -> drawTomeArt(cx, cy, s, el, shine, earth = true)
        // ── 道士 ──
        "w_peach", "t_talisman" -> drawTalismanBase(cx, cy, s, el, shine, peach = gear.id == "w_peach", sway = pulse)
        "t_dust" -> drawWhiskArt(cx, cy, s, el, pulse)
        "t_seal" -> drawSealArt(cx, cy, s, el, shine)
        "t_fire_charm" -> drawTalismanBase(cx, cy, s, Color(0xFFEF4444), shine, fire = true, sway = pulse)
        "t_gourd" -> drawGourdArt(cx, cy, s, el, shine, pulse)
        "t_poison_bell" -> drawBellArt(cx, cy, s, el, shine, pulse)
        "t_jade" -> drawJadeArt(cx, cy, s, el, shine)
        // ── 通用 ──
        "u_water_blade" -> drawWaterBladeArt(cx, cy, s, pulse)
        "u_earth_hammer" -> drawEarthHammerArt(cx, cy, s, pulse)
        "u_wood_bow" -> drawWoodBowArt(cx, cy, s, pulse)
        "u_penta" -> drawPentaWheel(cx, cy, s, pulse)
        "u_spark" -> drawSparkOrbArt(cx, cy, s, pulse)
        else -> {
            val style = gear.hero ?: HeroClass.WARRIOR
            when (style) {
                HeroClass.WARRIOR -> drawSwordBase(cx, cy, s, el, shine)
                HeroClass.MAGE -> drawStaffBase(cx, cy, s, el, shine, t = pulse)
                HeroClass.TAOIST -> drawTalismanBase(cx, cy, s, el, shine, sway = pulse)
            }
        }
    }
    drawCircle(Color(0xFF0F172A), s * 0.12f, Offset(cx + s * 0.38f, cy - s * 0.38f))
    drawCircle(el, s * 0.09f, Offset(cx + s * 0.38f, cy - s * 0.38f))
}

/** 剑：锥形刃身 Path + 血槽 + 翼形护手 + 缠绳柄 + 宝石柄首 */
private fun DrawScope.drawSwordBase(
    cx: Float, cy: Float, s: Float, el: Color, shine: Color,
    short: Boolean = false, long: Boolean = false, wide: Boolean = false, rust: Boolean = false,
    grooved: Boolean = false, tower: Boolean = false, flame: Boolean = false,
    curved: Boolean = false, t: Float = 0f
) {
    val h = s * (if (long) 0.82f else if (short) 0.52f else 0.68f)
    val bw = s * (if (wide) 0.11f else 0.075f)
    val tipY = cy - h * 0.78f
    val guardY = cy + h * 0.08f
    val bend = if (curved) s * 0.14f else 0f

    val blade = Path().apply {
        moveTo(cx, tipY)
        cubicTo(cx - bw * 0.32f, tipY + h * 0.16f, cx - bw + bend * 0.4f, tipY + h * 0.34f, cx - bw + bend * 0.7f, guardY - h * 0.06f)
        lineTo(cx - bw + bend * 0.7f, guardY)
        lineTo(cx + bw + bend * 0.7f, guardY)
        lineTo(cx + bw + bend * 0.7f, guardY - h * 0.06f)
        cubicTo(cx + bw + bend * 0.4f, tipY + h * 0.34f, cx + bw * 0.32f + bend, tipY + h * 0.16f, cx + bend, tipY)
        close()
    }
    val steel = if (rust) {
        listOf(Color(0xFFB7A78A), Color(0xFF8A7A5E), Color(0xFF574B38))
    } else {
        listOf(Color(0xFFF8FAFC), lerp(el, Color(0xFFCBD5E1), 0.5f), Color(0xFF7C8CA5))
    }
    drawPath(blade, Brush.verticalGradient(steel, startY = tipY, endY = guardY))
    inkStroke(blade, Color(0xFF0F172A), 1.1f)
    // 血槽 / 刃脊
    drawLine(
        Color(0xFF334155).copy(alpha = if (grooved) 0.55f else 0.3f),
        Offset(cx + bend * 0.5f, tipY + h * 0.12f), Offset(cx + bend * 0.7f, guardY - h * 0.08f),
        if (grooved) 2.6f else 1.4f
    )
    // 刃口高光
    drawLine(shine, Offset(cx + bw * 0.55f + bend * 0.8f, tipY + h * 0.2f), Offset(cx + bw * 0.85f + bend * 0.7f, guardY - h * 0.1f), 1.6f, StrokeCap.Round)

    // 护手：翼形（或塔盾形）
    if (tower) {
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFF64748B))),
            Offset(cx - s * 0.32f, guardY - s * 0.02f), Size(s * 0.64f, s * 0.14f), CornerRadius(s * 0.05f, s * 0.05f)
        )
        drawRoundRect(Color(0xFF0F172A), Offset(cx - s * 0.32f, guardY - s * 0.02f), Size(s * 0.64f, s * 0.14f), CornerRadius(s * 0.05f, s * 0.05f), style = Stroke(1f))
        drawFacetGem(cx + bend * 0.7f, guardY + s * 0.05f, s * 0.06f, el)
    } else {
        val guard = Path().apply {
            moveTo(cx - s * 0.34f + bend * 0.7f, guardY + s * 0.02f)
            quadraticTo(cx - s * 0.12f + bend * 0.7f, guardY - s * 0.07f, cx + bend * 0.7f, guardY - s * 0.035f)
            quadraticTo(cx + s * 0.12f + bend * 0.7f, guardY - s * 0.07f, cx + s * 0.34f + bend * 0.7f, guardY + s * 0.02f)
            quadraticTo(cx + s * 0.12f + bend * 0.7f, guardY + s * 0.1f, cx + bend * 0.7f, guardY + s * 0.075f)
            quadraticTo(cx - s * 0.12f + bend * 0.7f, guardY + s * 0.1f, cx - s * 0.34f + bend * 0.7f, guardY + s * 0.02f)
            close()
        }
        drawPath(guard, Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFF92400E)), startY = guardY - s * 0.08f, endY = guardY + s * 0.1f))
        inkStroke(guard, Color(0xFF451A03), 0.9f)
    }

    // 柄与柄首
    drawWrappedGrip(cx + bend * 0.7f, guardY + s * 0.09f, s * 0.1f, s * 0.24f, 4, Color(0xFF5C3A21), Color(0xFFD97706))
    drawFacetGem(cx + bend * 0.7f, guardY + s * 0.38f, s * 0.06f, el)

    // 火舌
    if (flame) {
        for (i in 0..2) {
            val fx = cx + (i - 1) * s * 0.14f
            val fl = s * (0.1f + 0.05f * sin(t * 5f + i * 2.1f))
            val tongue = Path().apply {
                moveTo(fx - s * 0.035f, tipY + h * 0.3f)
                quadraticTo(fx - s * 0.05f, tipY + h * 0.3f - fl, fx, tipY + h * 0.3f - fl * 1.7f)
                quadraticTo(fx + s * 0.05f, tipY + h * 0.3f - fl, fx + s * 0.035f, tipY + h * 0.3f)
                close()
            }
            drawPath(tongue, if (i == 1) Color(0xFFFEF3C7) else Color(0x99FBBF24))
        }
    }
}

/** 斧：新月刃 Path + 木纹柄 + 铆钉 */
private fun DrawScope.drawAxeArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color) {
    // 柄
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFB45309), Color(0xFF713F12))),
        Offset(cx - s * 0.032f, cy - s * 0.3f), Size(s * 0.064f, s * 0.74f), CornerRadius(s * 0.03f, s * 0.03f)
    )
    drawLine(Color(0xFF57250B).copy(alpha = 0.5f), Offset(cx - s * 0.012f, cy - s * 0.26f), Offset(cx - s * 0.012f, cy + s * 0.4f), 1f)
    drawLine(Color(0xFF57250B).copy(alpha = 0.5f), Offset(cx + s * 0.015f, cy - s * 0.22f), Offset(cx + s * 0.015f, cy + s * 0.38f), 1f)
    // 月刃
    val headTop = cy - s * 0.36f
    val headBot = cy + s * 0.0f
    val blade = Path().apply {
        moveTo(cx + s * 0.07f, headTop)
        cubicTo(cx + s * 0.46f, headTop - s * 0.08f, cx + s * 0.56f, headBot - s * 0.1f, cx + s * 0.44f, headBot + s * 0.1f)
        quadraticTo(cx + s * 0.18f, headBot + s * 0.02f, cx + s * 0.07f, headBot + s * 0.06f)
        close()
    }
    drawPath(blade, Brush.horizontalGradient(listOf(lerp(el, Color.White, 0.35f), el, lerp(el, Color.Black, 0.4f))))
    inkStroke(blade, Color(0xFF0F172A), 1.1f)
    // 刃口弧光
    drawArc(
        Color.White.copy(alpha = 0.65f), -70f, -60f, false,
        topLeft = Offset(cx + s * 0.06f, headTop - s * 0.12f), size = Size(s * 0.52f, s * 0.52f),
        style = Stroke(2.2f, cap = StrokeCap.Round)
    )
    // 铆钉与缠皮
    drawCircle(Color(0xFFFDE68A), s * 0.035f, Offset(cx + s * 0.08f, headTop + s * 0.05f))
    drawCircle(Color(0xFF78350F), s * 0.028f, Offset(cx + s * 0.08f, headTop + s * 0.05f), style = Stroke(1f))
    drawWrappedGrip(cx, cy + s * 0.18f, s * 0.075f, s * 0.2f, 3, Color(0xFF4A2E17), Color(0xFF92400E))
}

/** 枪：柳叶头 + 红缨 + 木杆 */
private fun DrawScope.drawSpearArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color, sway: Float = 0f) {
    // 杆
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFC2843B), Color(0xFF713F12))),
        Offset(cx - s * 0.026f, cy - s * 0.28f), Size(s * 0.052f, s * 0.92f), CornerRadius(s * 0.025f, s * 0.025f)
    )
    // 枪头
    val tipY = cy - s * 0.62f
    val baseY = cy - s * 0.24f
    val head = Path().apply {
        moveTo(cx, tipY)
        cubicTo(cx - s * 0.1f, tipY + s * 0.16f, cx - s * 0.11f, baseY - s * 0.06f, cx - s * 0.1f, baseY)
        lineTo(cx + s * 0.1f, baseY)
        cubicTo(cx + s * 0.11f, baseY - s * 0.06f, cx + s * 0.1f, tipY + s * 0.16f, cx, tipY)
        close()
    }
    drawPath(head, Brush.verticalGradient(listOf(Color(0xFFFEF3C7), el, lerp(el, Color.Black, 0.45f)), startY = tipY, endY = baseY))
    inkStroke(head, Color(0xFF0F172A), 1f)
    drawLine(Color.White.copy(alpha = 0.6f), Offset(cx, tipY + s * 0.05f), Offset(cx, baseY - s * 0.03f), 1.3f)
    // 红缨
    drawTassel(cx, baseY + s * 0.02f, s * 1.3f, Color(0xFFDC2626), sway)
    // 銎箍与尾镦
    drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.045f, baseY), Size(s * 0.09f, s * 0.05f), CornerRadius(1.5f))
    drawRoundRect(Color(0xFFB45309), Offset(cx - s * 0.04f, cy + s * 0.62f), Size(s * 0.08f, s * 0.06f), CornerRadius(2f))
}

/** 锤：札甲锤头 + 绑带铆钉 */
private fun DrawScope.drawHammerArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color) {
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFB45309), Color(0xFF713F12))),
        Offset(cx - s * 0.032f, cy - s * 0.18f), Size(s * 0.064f, s * 0.62f), CornerRadius(s * 0.03f, s * 0.03f)
    )
    val headTop = cy - s * 0.42f
    val head = Path().apply {
        moveTo(cx - s * 0.3f, headTop + s * 0.06f)
        lineTo(cx - s * 0.24f, headTop)
        lineTo(cx + s * 0.24f, headTop)
        lineTo(cx + s * 0.3f, headTop + s * 0.06f)
        lineTo(cx + s * 0.3f, headTop + s * 0.3f)
        lineTo(cx - s * 0.3f, headTop + s * 0.3f)
        close()
    }
    drawPath(head, Brush.verticalGradient(listOf(lerp(el, Color.White, 0.3f), el, lerp(el, Color.Black, 0.45f))))
    inkStroke(head, Color(0xFF0F172A), 1.1f)
    // 顶面高光 + 绑带
    drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - s * 0.22f, headTop + s * 0.025f), Offset(cx + s * 0.22f, headTop + s * 0.025f), 1.5f)
    for (dx in listOf(-s * 0.19f, s * 0.19f)) {
        drawLine(Color(0xFF57250B), Offset(cx + dx, headTop), Offset(cx + dx, headTop + s * 0.3f), 2.5f)
        drawCircle(Color(0xFFFDE68A), s * 0.025f, Offset(cx + dx, headTop + s * 0.15f))
    }
    drawWrappedGrip(cx, cy + s * 0.2f, s * 0.075f, s * 0.18f, 3, Color(0xFF4A2E17), Color(0xFF92400E))
}

/** 弓：反曲弓臂 + 弦 + 搭箭 */
private fun DrawScope.drawBowArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color) {
    val limb = Path().apply {
        moveTo(cx + s * 0.16f, cy - s * 0.52f)
        cubicTo(cx - s * 0.34f, cy - s * 0.38f, cx - s * 0.34f, cy + s * 0.38f, cx + s * 0.16f, cy + s * 0.52f)
    }
    drawPath(limb, Color(0xFF3F2A15), style = Stroke(s * 0.115f, cap = StrokeCap.Round))
    drawPath(limb, Brush.verticalGradient(listOf(lerp(el, Color.White, 0.3f), el, lerp(el, Color.Black, 0.25f))), style = Stroke(s * 0.08f, cap = StrokeCap.Round))
    // 弓弦
    drawLine(Color(0xFFE7E5E4), Offset(cx + s * 0.17f, cy - s * 0.51f), Offset(cx + s * 0.17f, cy + s * 0.51f), 1.4f)
    // 箭
    drawLine(Color(0xFF78350F), Offset(cx + s * 0.06f, cy - s * 0.3f), Offset(cx + s * 0.44f, cy + s * 0.08f), 2f)
    val atip = Offset(cx + s * 0.5f, cy + s * 0.14f)
    val ahead = Path().apply {
        moveTo(atip.x, atip.y)
        lineTo(atip.x - s * 0.1f, atip.y - s * 0.05f)
        lineTo(atip.x - s * 0.05f, atip.y - s * 0.1f)
        close()
    }
    drawPath(ahead, Color(0xFFCBD5E1))
    for (i in 0..1) {
        drawLine(
            Color(0xFFDC2626), Offset(cx + s * 0.06f + i * s * 0.05f, cy - s * 0.3f + i * s * 0.05f),
            Offset(cx - s * 0.02f + i * s * 0.05f, cy - s * 0.38f + i * s * 0.05f), 2f
        )
    }
    // 握把
    drawWrappedGrip(cx - s * 0.02f, cy - s * 0.09f, s * 0.1f, s * 0.2f, 3, Color(0xFF4A2E17), Color(0xFF92400E))
}

/** 五行轮：墨盘 + 五瓣刃 + 八卦芯 */
private fun DrawScope.drawPentaWheel(cx: Float, cy: Float, s: Float, pulse: Float) {
    val cols = listOf(Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFFD97706), Color(0xFF38BDF8), Color(0xFFEF4444))
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))),
        s * 0.46f, Offset(cx, cy)
    )
    drawCircle(Color(0xFF0F172A), s * 0.46f, Offset(cx, cy), style = Stroke(1.5f))
    rotate(degrees = pulse * 22f, pivot = Offset(cx, cy)) {
        for (i in 0..4) {
            val a = -Math.PI.toFloat() / 2f + i * (2f * Math.PI.toFloat() / 5f)
            val px = cx + cos(a) * s * 0.28f
            val py = cy + sin(a) * s * 0.28f
            drawLine(Color(0x88E2E8F0), Offset(cx, cy), Offset(px, py), 2f)
            val petal = Path().apply {
                moveTo(px + cos(a) * s * 0.1f, py + sin(a) * s * 0.1f)
                cubicTo(
                    px + cos(a + 0.7f) * s * 0.12f, py + sin(a + 0.7f) * s * 0.12f,
                    px + cos(a + 0.5f) * s * 0.16f, py + sin(a + 0.5f) * s * 0.16f,
                    px + cos(a) * s * 0.2f, py + sin(a) * s * 0.2f
                )
                cubicTo(
                    px + cos(a - 0.5f) * s * 0.16f, py + sin(a - 0.5f) * s * 0.16f,
                    px + cos(a - 0.7f) * s * 0.12f, py + sin(a - 0.7f) * s * 0.12f,
                    px + cos(a) * s * 0.1f, py + sin(a) * s * 0.1f
                )
                close()
            }
            drawPath(petal, cols[i])
            drawPath(petal, Color(0x660F172A), style = Stroke(0.8f))
            drawCircle(Color.White.copy(alpha = 0.5f), s * 0.02f, Offset(px + cos(a) * s * 0.13f, py + sin(a) * s * 0.13f))
        }
    }
    drawFacetGem(cx, cy, s * 0.09f, Color(0xFFFDE68A))
}

/** 壬水短刃：曲波刃身 + 涡流水环 + 绕刃水珠 + 寒晶 */
private fun DrawScope.drawWaterBladeArt(cx: Float, cy: Float, s: Float, t: Float) {
    val aqua = Color(0xFF38BDF8)
    val deep = Color(0xFF0369A1)
    // 涡流水环（双层反向旋转）
    for (i in 0..2) {
        val a0 = t * (50f - i * 14f)
        val rr = s * (0.62f + i * 0.09f)
        drawArc(
            aqua.copy(alpha = 0.4f - i * 0.1f), a0, 150f - i * 20f, false,
            topLeft = Offset(cx - rr, cy - rr), size = Size(rr * 2f, rr * 2f),
            style = Stroke(2.2f - i * 0.5f, cap = StrokeCap.Round)
        )
    }
    // 曲波刃身：新月形水流刃
    val tip = Offset(cx + s * 0.5f, cy - s * 0.38f)
    val heel = Offset(cx - s * 0.18f, cy + s * 0.3f)
    val blade = Path().apply {
        moveTo(heel.x, heel.y)
        cubicTo(cx - s * 0.34f, cy - s * 0.1f, cx - s * 0.05f, cy - s * 0.42f, tip.x, tip.y)
        cubicTo(cx + s * 0.22f, cy - s * 0.28f, cx + s * 0.16f, cy + s * 0.02f, cx + s * 0.02f, cy + s * 0.16f)
        quadraticTo(cx - s * 0.08f, cy + s * 0.26f, heel.x, heel.y)
        close()
    }
    drawPath(blade, Brush.verticalGradient(listOf(Color(0xFFE0F2FE), aqua, deep)))
    inkStroke(blade, Color(0xFF082F49), 1.1f)
    // 刃口浪脊高光
    drawLine(Color.White.copy(alpha = 0.7f), Offset(cx - s * 0.05f, cy - s * 0.28f), Offset(tip.x - s * 0.08f, tip.y + s * 0.1f), 1.8f, StrokeCap.Round)
    // 波纹刻线
    for (i in 0..2) {
        drawArc(
            Color(0xFFBAE6FD).copy(alpha = 0.6f), 200f, 100f, false,
            topLeft = Offset(cx - s * 0.16f + i * s * 0.1f, cy - s * 0.06f + i * s * 0.09f),
            size = Size(s * 0.2f, s * 0.14f), style = Stroke(1.1f)
        )
    }
    // 柄与柄首
    drawWrappedGrip(heel.x - s * 0.03f, heel.y - s * 0.06f, s * 0.08f, s * 0.2f, 4, Color(0xFF0C4A6E), Color(0xFF38BDF8))
    drawFacetGem(heel.x - s * 0.03f, heel.y + s * 0.18f, s * 0.055f, aqua)
    // 绕刃水珠
    for (i in 0..4) {
        val a = t * 1.8f + i * 1.26f
        val ox = cx + cos(a) * s * 0.55f
        val oy = cy + sin(a) * s * 0.55f * 0.7f
        drawCircle(Color(0xCCE0F2FE), s * (0.02f + (i % 2) * 0.01f), Offset(ox, oy))
    }
    // 寒晶闪
    val sp = Offset(cx + s * 0.3f, cy - s * 0.5f)
    for (i in 0..3) {
        val a = i * (Math.PI.toFloat() / 2f) + t * 0.6f
        drawLine(Color(0xFFE0F2FE), Offset(sp.x - cos(a) * s * 0.05f, sp.y - sin(a) * s * 0.05f), Offset(sp.x + cos(a) * s * 0.05f, sp.y + sin(a) * s * 0.05f), 1.4f, StrokeCap.Round)
    }
}

/** 大地之锤：符文方锤 + 环绕碎石 + 地裂波纹 */
private fun DrawScope.drawEarthHammerArt(cx: Float, cy: Float, s: Float, t: Float) {
    val ochre = Color(0xFFD97706)
    val stone = Color(0xFF78716C)
    // 地裂冲击波（从锤底扩散）
    for (i in 0..2) {
        val ph = (t * 0.6f + i * 0.33f) % 1f
        drawOval(
            Color(0x66A16207).copy(alpha = 0.4f * (1f - ph)),
            topLeft = Offset(cx - s * (0.3f + ph * 0.5f), cy + s * 0.48f - s * ph * 0.08f),
            size = Size(s * (0.6f + ph), s * (0.16f + ph * 0.2f)),
            style = Stroke(2f)
        )
    }
    // 柄
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFB45309), Color(0xFF57250B))),
        Offset(cx - s * 0.035f, cy - s * 0.34f), Size(s * 0.07f, s * 0.8f), CornerRadius(s * 0.035f, s * 0.035f)
    )
    drawLine(Color(0xFF3F1D0B).copy(alpha = 0.5f), Offset(cx - s * 0.015f, cy - s * 0.3f), Offset(cx - s * 0.015f, cy + s * 0.44f), 1f)
    // 方锤头：六面体剪影
    val headTop = cy - s * 0.52f
    val headH = s * 0.36f
    val headW = s * 0.62f
    val head = Path().apply {
        moveTo(cx - headW / 2f, headTop + s * 0.06f)
        lineTo(cx - headW / 2f + s * 0.1f, headTop)
        lineTo(cx + headW / 2f - s * 0.1f, headTop)
        lineTo(cx + headW / 2f, headTop + s * 0.06f)
        lineTo(cx + headW / 2f, headTop + headH)
        lineTo(cx - headW / 2f, headTop + headH)
        close()
    }
    drawPath(head, Brush.verticalGradient(listOf(lerp(ochre, Color.White, 0.35f), ochre, lerp(ochre, Color.Black, 0.5f))))
    inkStroke(head, Color(0xFF3F1D0B), 1.2f)
    // 顶面亮带 + 侧面暗影
    drawLine(Color.White.copy(alpha = 0.4f), Offset(cx - headW / 2f + s * 0.1f, headTop + s * 0.02f), Offset(cx + headW / 2f - s * 0.1f, headTop + s * 0.02f), 1.6f)
    drawRect(Color(0x33000000), Offset(cx + headW * 0.18f, headTop + s * 0.06f), Size(headW * 0.32f, headH - s * 0.06f))
    // 山字符文（土行）
    drawLine(Color(0xFFFDE68A), Offset(cx - s * 0.09f, headTop + headH * 0.32f), Offset(cx + s * 0.09f, headTop + headH * 0.32f), 2f, StrokeCap.Round)
    drawLine(Color(0xFFFDE68A), Offset(cx - s * 0.09f, headTop + headH * 0.32f), Offset(cx - s * 0.09f, headTop + headH * 0.72f), 2f, StrokeCap.Round)
    drawLine(Color(0xFFFDE68A), Offset(cx + s * 0.09f, headTop + headH * 0.32f), Offset(cx + s * 0.09f, headTop + headH * 0.72f), 2f, StrokeCap.Round)
    drawLine(Color(0xFFFDE68A), Offset(cx, headTop + headH * 0.32f), Offset(cx, headTop + headH * 0.72f), 2f, StrokeCap.Round)
    // 金箍绑带与铆钉
    for (dx in listOf(-headW / 2f + s * 0.08f, headW / 2f - s * 0.08f)) {
        drawLine(Color(0xFFFBBF24), Offset(cx + dx, headTop), Offset(cx + dx, headTop + headH), 3f)
        drawCircle(Color(0xFFFDE68A), s * 0.025f, Offset(cx + dx, headTop + headH * 0.5f))
    }
    // 环绕碎石（轨道浮动）
    for (i in 0..4) {
        val a = t * 0.9f + i * 1.26f
        val ox = cx + cos(a) * s * 0.72f
        val oy = cy - s * 0.1f + sin(a) * s * 0.4f
        val rs = s * (0.03f + (i % 3) * 0.012f)
        rotate(degrees = a * 40f, pivot = Offset(ox, oy)) {
            drawRoundRect(stone.copy(alpha = 0.9f), Offset(ox - rs, oy - rs * 0.8f), Size(rs * 2f, rs * 1.6f), CornerRadius(1.5f))
            drawRoundRect(Color(0x440F172A), Offset(ox - rs, oy - rs * 0.8f), Size(rs * 2f, rs * 1.6f), CornerRadius(1.5f), style = Stroke(0.8f))
        }
    }
    drawWrappedGrip(cx, cy + s * 0.26f, s * 0.08f, s * 0.18f, 3, Color(0xFF4A2E17), Color(0xFF92400E))
}

/** 甲木弓：活木弓臂 + 藤蔓缠绕 + 嫩叶发芽 + 绕行萤光 */
private fun DrawScope.drawWoodBowArt(cx: Float, cy: Float, s: Float, t: Float) {
    val leaf = Color(0xFF4ADE80)
    val bark = Color(0xFF3F2A15)
    // 生命光晕
    drawCircle(
        Brush.radialGradient(listOf(leaf.copy(alpha = 0.18f), Color.Transparent)),
        s * 0.75f, Offset(cx, cy)
    )
    // 活木弓臂（双弧，带枝节）
    val limb = Path().apply {
        moveTo(cx + s * 0.2f, cy - s * 0.52f)
        cubicTo(cx - s * 0.38f, cy - s * 0.36f, cx - s * 0.38f, cy + s * 0.36f, cx + s * 0.2f, cy + s * 0.52f)
    }
    drawPath(limb, bark, style = Stroke(s * 0.14f, cap = StrokeCap.Round))
    drawPath(limb, Brush.verticalGradient(listOf(lerp(leaf, Color.White, 0.25f), Color(0xFF65A30D), bark)), style = Stroke(s * 0.095f, cap = StrokeCap.Round))
    // 枝节瘤
    drawCircle(bark, s * 0.035f, Offset(cx - s * 0.2f, cy - s * 0.22f))
    drawCircle(Color(0xFF65A30D), s * 0.02f, Offset(cx - s * 0.2f, cy - s * 0.22f))
    drawCircle(bark, s * 0.03f, Offset(cx - s * 0.22f, cy + s * 0.18f))
    // 弓弦
    drawLine(Color(0xFFE7E5E4), Offset(cx + s * 0.21f, cy - s * 0.51f), Offset(cx + s * 0.21f, cy + s * 0.51f), 1.4f)
    // 藤蔓缠柄
    for (i in 0..4) {
        val ty = cy - s * 0.08f + i * s * 0.042f
        drawLine(
            Color(0xFF65A30D).copy(alpha = 0.9f),
            Offset(cx - s * 0.05f, ty), Offset(cx + s * 0.02f, ty + s * 0.025f), (s * 0.02f).coerceAtLeast(1.2f), StrokeCap.Round
        )
    }
    // 嫩叶（弓臂两端抽芽）
    for ((lx, ly, dir) in listOf(Triple(cx - s * 0.3f, cy - s * 0.32f, -1), Triple(cx - s * 0.32f, cy + s * 0.3f, 1), Triple(cx - s * 0.1f, cy - s * 0.34f, -1))) {
        val sway = sin(t * 2.5f + dir) * s * 0.015f
        val lp = Path().apply {
            moveTo(lx, ly)
            quadraticTo(lx + dir * s * 0.1f, ly - s * 0.06f + sway, lx + dir * s * 0.17f, ly - s * 0.1f + sway)
            quadraticTo(lx + dir * s * 0.07f, ly + s * 0.02f, lx, ly)
            close()
        }
        drawPath(lp, leaf)
        drawPath(lp, Color(0xFF166534), style = Stroke(0.8f))
    }
    // 萤光绕行
    for (i in 0..4) {
        val a = t * 1.6f + i * 1.26f
        val ox = cx + cos(a) * s * 0.62f
        val oy = cy + sin(a) * s * 0.62f * 0.75f
        drawCircle(Color(0xBBFDE68A), s * 0.018f + (i % 2) * s * 0.008f, Offset(ox, oy))
    }
    // 搭箭（叶羽）
    drawLine(Color(0xFF78350F), Offset(cx + s * 0.05f, cy - s * 0.28f), Offset(cx + s * 0.5f, cy + s * 0.1f), 2f)
    val atip = Offset(cx + s * 0.55f, cy + s * 0.15f)
    drawPath(
        Path().apply {
            moveTo(atip.x, atip.y)
            lineTo(atip.x - s * 0.1f, atip.y - s * 0.045f)
            lineTo(atip.x - s * 0.05f, atip.y - s * 0.1f)
            close()
        }, Color(0xFFCBD5E1)
    )
    for (i in 0..1) {
        drawLine(
            leaf, Offset(cx + s * 0.05f + i * s * 0.05f, cy - s * 0.28f + i * s * 0.05f),
            Offset(cx - s * 0.02f + i * s * 0.05f, cy - s * 0.36f + i * s * 0.05f), 2.2f
        )
    }
}

/** 法杖：虬木 + 抱月爪 + 宝珠（冰环/雷纹/王冠可选） */
private fun DrawScope.drawStaffBase(
    cx: Float, cy: Float, s: Float, el: Color, shine: Color,
    orbScale: Float = 1f, tall: Boolean = false, flame: Boolean = false,
    icy: Boolean = false, bolt: Boolean = false, crown: Boolean = false, t: Float = 0f
) {
    val topY = if (tall) cy - s * 0.5f else cy - s * 0.38f
    // 虬曲杖身
    val shaft = Path().apply {
        moveTo(cx - s * 0.06f, cy + s * 0.46f)
        quadraticTo(cx + s * 0.12f, cy + s * 0.05f, cx - s * 0.04f, topY)
    }
    drawPath(shaft, Color(0xFF3F2A15), style = Stroke(s * 0.085f, cap = StrokeCap.Round))
    drawPath(shaft, Brush.verticalGradient(listOf(Color(0xFFC2843B), Color(0xFF713F12))), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
    // 抱月双爪
    for (d in -1..1) {
        val claw = Path().apply {
            moveTo(cx - s * 0.04f, topY + s * 0.02f)
            quadraticTo(cx + d * s * 0.16f, topY - s * 0.02f, cx + d * s * 0.13f, topY - s * 0.16f)
        }
        drawPath(claw, Color(0xFFB45309), style = Stroke(s * 0.035f, cap = StrokeCap.Round))
    }
    // 金箍
    drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.07f, topY + s * 0.03f), Size(s * 0.1f, s * 0.05f), CornerRadius(1.5f))

    // 宝珠
    val oy = if (tall) topY - s * 0.16f else topY - s * 0.12f
    val r = s * 0.22f * orbScale
    drawCircle(el.copy(alpha = 0.25f), r * 1.55f, Offset(cx, oy))
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(el, Color.White, 0.6f), el, lerp(el, Color.Black, 0.35f)),
            center = Offset(cx - r * 0.3f, oy - r * 0.3f), radius = r * 1.4f
        ),
        r, Offset(cx, oy)
    )
    drawCircle(Color(0x550F172A), r, Offset(cx, oy), style = Stroke(1f))
    drawCircle(Color.White.copy(alpha = 0.75f), r * 0.22f, Offset(cx - r * 0.32f, oy - r * 0.34f))
    drawCircle(el.copy(alpha = 0.5f), r * 1.28f, Offset(cx, oy), style = Stroke(1.2f))

    if (flame) {
        for (i in 0..2) {
            val fx = cx + (i - 1) * r * 0.9f
            val fl = r * (0.5f + 0.25f * sin(t * 5f + i * 2.3f))
            val tongue = Path().apply {
                moveTo(fx - r * 0.16f, oy)
                quadraticTo(fx - r * 0.2f, oy - fl, fx, oy - fl * 1.6f)
                quadraticTo(fx + r * 0.2f, oy - fl, fx + r * 0.16f, oy)
                close()
            }
            drawPath(tongue, if (i == 1) Color(0xCCFEF3C7) else Color(0x88FBBF24))
        }
    }
    if (icy) {
        for (i in 0 until 6) {
            val a = t * 1.2f + i * (Math.PI.toFloat() / 3f)
            val px = cx + cos(a) * r * 1.6f
            val py = oy + sin(a) * r * 1.6f * 0.55f
            rotate(degrees = a * 57.3f, pivot = Offset(px, py)) {
                drawRoundRect(Color(0xCCE0F2FE), Offset(px - r * 0.09f, py - r * 0.035f), Size(r * 0.18f, r * 0.07f), CornerRadius(1f))
            }
        }
    }
    if (bolt) {
        drawLine(Color(0xFFFDE68A), Offset(cx - r * 0.5f, oy - r * 1.1f), Offset(cx + r * 0.15f, oy - r * 0.4f), 2.2f, StrokeCap.Round)
        drawLine(Color(0xFFFDE68A), Offset(cx + r * 0.15f, oy - r * 0.4f), Offset(cx - r * 0.25f, oy + r * 0.2f), 2.2f, StrokeCap.Round)
        drawCircle(Color(0xFFFDE68A), r * 0.14f, Offset(cx - r * 0.5f, oy - r * 1.1f))
    }
    if (crown) {
        val cw = Path().apply {
            moveTo(cx - r * 0.75f, oy + r * 1.05f)
            lineTo(cx - r * 0.75f, oy + r * 0.7f)
            lineTo(cx - r * 0.4f, oy + r * 0.9f)
            lineTo(cx, oy + r * 0.55f)
            lineTo(cx + r * 0.4f, oy + r * 0.9f)
            lineTo(cx + r * 0.75f, oy + r * 0.7f)
            lineTo(cx + r * 0.75f, oy + r * 1.05f)
            close()
        }
        drawPath(cw, Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))))
        drawPath(cw, Color(0xFF78350F), style = Stroke(0.9f))
        drawCircle(Color(0xFFEF4444), r * 0.12f, Offset(cx, oy + r * 0.82f))
    }
}

/** 桃木剑 + 悬符（符纸锯齿尾 + 朱砂印） */
private fun DrawScope.drawTalismanBase(
    cx: Float, cy: Float, s: Float, el: Color, shine: Color,
    peach: Boolean = false, fire: Boolean = false, sway: Float = 0f
) {
    val wood = if (peach) Color(0xFFC2410C) else Color(0xFF8B5A2B)
    // 桃木剑身（短剑形）
    val tipY = cy - s * 0.5f
    val baseY = cy + s * 0.18f
    val sword = Path().apply {
        moveTo(cx - s * 0.02f, tipY)
        cubicTo(cx - s * 0.11f, tipY + s * 0.2f, cx - s * 0.1f, baseY - s * 0.12f, cx - s * 0.08f, baseY)
        lineTo(cx + s * 0.04f, baseY)
        cubicTo(cx + s * 0.06f, baseY - s * 0.12f, cx + s * 0.06f, tipY + s * 0.2f, cx - s * 0.02f, tipY)
        close()
    }
    drawPath(sword, Brush.verticalGradient(listOf(lerp(wood, Color.White, 0.25f), wood, lerp(wood, Color.Black, 0.4f)), startY = tipY, endY = baseY))
    inkStroke(sword, Color(0xFF3F1D0B), 1f)
    drawLine(Color(0xFFFCA5A5).copy(alpha = 0.5f), Offset(cx - s * 0.02f, tipY + s * 0.08f), Offset(cx - s * 0.02f, baseY - s * 0.04f), 1.2f)
    // 柄尾
    drawWrappedGrip(cx - s * 0.02f, baseY, s * 0.08f, s * 0.2f, 3, Color(0xFF5C3A21), Color(0xFFD97706))
    drawTassel(cx - s * 0.02f, baseY + s * 0.21f, s * 1.2f, Color(0xFFDC2626), sway)

    // 悬符
    val chX = cx + s * 0.26f
    val chY = cy - s * 0.28f
    rotate(degrees = sin(sway * 1.4f) * 7f, pivot = Offset(chX, chY - s * 0.1f)) {
        drawLine(Color(0xFFDC2626), Offset(cx + s * 0.04f, cy - s * 0.24f), Offset(chX, chY - s * 0.08f), 1.2f)
        val paper = Path().apply {
            moveTo(chX - s * 0.13f, chY - s * 0.08f)
            lineTo(chX + s * 0.13f, chY - s * 0.08f)
            lineTo(chX + s * 0.13f, chY + s * 0.3f)
            lineTo(chX + s * 0.065f, chY + s * 0.24f)
            lineTo(chX, chY + s * 0.32f)
            lineTo(chX - s * 0.065f, chY + s * 0.24f)
            lineTo(chX - s * 0.13f, chY + s * 0.3f)
            close()
        }
        drawPath(paper, Brush.verticalGradient(listOf(Color(0xFFFEF9E7), Color(0xFFF1E4C0))))
        drawPath(paper, Color(0xFF92765A), style = Stroke(0.9f))
        // 朱砂符文
        drawLine(Color(0xFFDC2626), Offset(chX, chY), Offset(chX, chY + s * 0.16f), 2f, StrokeCap.Round)
        drawLine(Color(0xFFDC2626), Offset(chX - s * 0.055f, chY + s * 0.045f), Offset(chX + s * 0.055f, chY + s * 0.045f), 1.8f, StrokeCap.Round)
        drawCircle(Color(0xFFDC2626), s * 0.028f, Offset(chX, chY + s * 0.075f))
        if (fire) {
            drawCircle(Color(0xCCFBBF24), s * 0.05f, Offset(chX, chY - s * 0.14f))
            drawCircle(Color(0x88EF4444), s * 0.035f, Offset(chX + s * 0.06f, chY - s * 0.1f))
        }
    }
}

/** 木灵珠：活木球 + 嫩芽 + 根须 */
private fun DrawScope.drawWoodOrbArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color, t: Float) {
    val r = s * 0.26f
    // 根须
    for (i in -2..2) {
        val a = 1.35f + i * 0.16f
        drawLine(
            Color(0xFF4D7C0F).copy(alpha = 0.8f),
            Offset(cx + cos(a) * r * 0.8f, cy + sin(a) * r * 0.8f),
            Offset(cx + cos(a) * r * (1.5f + 0.15f * sin(t * 2f + i)), cy + sin(a) * r * (1.5f + 0.15f * sin(t * 2f + i))),
            1.6f, StrokeCap.Round
        )
    }
    // 球体
    drawCircle(el.copy(alpha = 0.25f), r * 1.35f, Offset(cx, cy))
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(el, Color.White, 0.55f), el, Color(0xFF14532D)),
            center = Offset(cx - r * 0.3f, cy - r * 0.3f), radius = r * 1.4f
        ), r, Offset(cx, cy)
    )
    drawCircle(Color(0x550F172A), r, Offset(cx, cy), style = Stroke(1f))
    // 年轮
    drawCircle(Color(0xFF166534).copy(alpha = 0.5f), r * 0.55f, Offset(cx, cy), style = Stroke(1f))
    drawCircle(Color(0xFF166534).copy(alpha = 0.35f), r * 0.28f, Offset(cx, cy), style = Stroke(1f))
    // 嫩芽
    val sprout = cy - r - s * 0.05f
    drawLine(Color(0xFF4D7C0F), Offset(cx, cy - r * 0.7f), Offset(cx, sprout), 2f, StrokeCap.Round)
    for (d in -1..1 step 2) {
        val leaf = Path().apply {
            moveTo(cx, sprout + s * 0.02f)
            quadraticTo(cx + d * s * 0.14f, sprout - s * 0.06f, cx + d * s * 0.17f, sprout - s * 0.14f + sin(t * 3f) * s * 0.01f)
            quadraticTo(cx + d * s * 0.07f, sprout - s * 0.1f, cx, sprout + s * 0.02f)
            close()
        }
        drawPath(leaf, Color(0xFF4ADE80))
        drawPath(leaf, Color(0xFF166534), style = Stroke(0.8f))
    }
    drawCircle(shine, s * 0.05f, Offset(cx - r * 0.3f, cy - r * 0.3f))
}

/** 深渊法典：暗蓝封皮 + 幽光书缝 + 升腾气泡 */
private fun DrawScope.drawAbyssTomeArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color, t: Float) {
    val w = s * 0.52f
    val h = s * 0.72f
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))), Offset(cx - w / 2f, cy - h / 2f), Size(w, h), CornerRadius(s * 0.05f))
    drawRoundRect(Color(0xFF0F172A), Offset(cx - w / 2f, cy - h / 2f), Size(w, h), CornerRadius(s * 0.05f), style = Stroke(1.2f))
    // 书脊幽光
    drawRect(el.copy(alpha = 0.65f), Offset(cx - w * 0.04f, cy - h * 0.42f), Size(w * 0.08f, h * 0.84f))
    // 封面纹章：独眼
    drawCircle(el.copy(alpha = 0.3f), s * 0.13f, Offset(cx + w * 0.16f, cy - s * 0.1f))
    drawCircle(Color(0xFF7DD3FC), s * 0.07f, Offset(cx + w * 0.16f, cy - s * 0.1f))
    drawCircle(Color(0xFF0F172A), s * 0.03f, Offset(cx + w * 0.16f, cy - s * 0.1f))
    // 银角包边
    drawRoundRect(Color(0xFF94A3B8), Offset(cx - w / 2f, cy - h / 2f - s * 0.02f), Size(w, s * 0.04f), CornerRadius(2f))
    // 气泡
    for (i in 0..4) {
        val ph = (t * 0.7f + i * 0.2f) % 1f
        drawCircle(
            Color(0x667DD3FC), s * (0.018f + (i % 3) * 0.008f),
            Offset(cx - w * 0.3f + i * w * 0.16f + sin(t + i) * 3f, cy - h * 0.5f - ph * s * 0.3f)
        )
    }
}

/** 大地法典：皮面 + 金扣 + 朱印 */
private fun DrawScope.drawTomeArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color, earth: Boolean) {
    val w = s * 0.5f
    val h = s * 0.66f
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFA16207), Color(0xFF57250B))), Offset(cx - w / 2f, cy - h / 2f), Size(w, h), CornerRadius(s * 0.04f))
    drawRoundRect(Color(0xFF2C1608), Offset(cx - w / 2f, cy - h / 2f), Size(w, h), CornerRadius(s * 0.04f), style = Stroke(1.1f))
    // 书页侧缝
    drawRect(Color(0xFFFEF3C7), Offset(cx - w / 2f - s * 0.02f, cy - h * 0.4f), Size(s * 0.025f, h * 0.8f))
    drawRect(Color(0xFFD6BC9A), Offset(cx - w / 2f - s * 0.02f, cy - h * 0.4f), Size(s * 0.01f, h * 0.8f))
    // 金扣
    drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.045f, cy - h * 0.06f), Size(s * 0.09f, h * 0.16f), CornerRadius(2f))
    drawCircle(Color(0xFFFDE68A), s * 0.018f, Offset(cx, cy - h * 0.02f))
    // 封面朱印（土行卦）
    drawCircle(Color(0xFFDC2626), s * 0.075f, Offset(cx, cy - h * 0.26f))
    drawLine(Color(0xFFFEF3C7), Offset(cx - s * 0.04f, cy - h * 0.26f), Offset(cx + s * 0.04f, cy - h * 0.26f), 1.6f)
    drawLine(Color(0xFFFEF3C7), Offset(cx, cy - h * 0.3f), Offset(cx, cy - h * 0.22f), 1.6f)
}

/** 拂尘：乌木柄 + 白丝拂丝 */
private fun DrawScope.drawWhiskArt(cx: Float, cy: Float, s: Float, el: Color, t: Float) {
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF1C1917), Color(0xFF0F172A))),
        Offset(cx - s * 0.03f, cy - s * 0.08f), Size(s * 0.06f, s * 0.58f), CornerRadius(s * 0.03f, s * 0.03f)
    )
    drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.04f, cy - s * 0.1f), Size(s * 0.08f, s * 0.05f), CornerRadius(1.5f))
    // 拂丝
    for (i in 0..8) {
        val spread = (i - 4) / 4f
        val swayS = sin(t * 2.2f + i * 0.7f) * s * 0.05f
        drawLine(
            if (i % 2 == 0) Color(0xFFE7E5E4) else Color(0xFFCBD5E1),
            Offset(cx, cy - s * 0.08f),
            Offset(cx + spread * s * 0.42f + swayS, cy - s * 0.5f + abs(spread) * s * 0.08f + (if (abs(spread) > 0.5f) s * 0.1f else 0f)),
            s * 0.026f, StrokeCap.Round
        )
    }
    // 丝间灵光
    drawCircle(el.copy(alpha = 0.55f), s * 0.04f, Offset(cx + sin(t * 2f) * s * 0.12f, cy - s * 0.4f))
    drawCircle(el.copy(alpha = 0.35f), s * 0.025f, Offset(cx - sin(t * 1.6f) * s * 0.15f, cy - s * 0.46f))
}

/** 镇魂印：朱砂方印 + 金钮 + 篆痕 */
private fun DrawScope.drawSealArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color) {
    // 金钮
    val knob = Path().apply {
        moveTo(cx - s * 0.09f, cy - s * 0.3f)
        lineTo(cx - s * 0.07f, cy - s * 0.44f)
        quadraticTo(cx, cy - s * 0.52f, cx + s * 0.07f, cy - s * 0.44f)
        lineTo(cx + s * 0.09f, cy - s * 0.3f)
        close()
    }
    drawPath(knob, Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFB45309))))
    drawPath(knob, Color(0xFF57250B), style = Stroke(0.9f))
    // 印身
    val body = s * 0.52f
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFEF4444), Color(0xFF7F1D1D))),
        Offset(cx - body / 2f, cy - body / 2f), Size(body, body), CornerRadius(s * 0.06f)
    )
    drawRoundRect(Color(0xFF450A0A), Offset(cx - body / 2f, cy - body / 2f), Size(body, body), CornerRadius(s * 0.06f), style = Stroke(1.2f))
    // 金框
    drawRoundRect(Color(0xFFFBBF24), Offset(cx - body * 0.34f, cy - body * 0.34f), Size(body * 0.68f, body * 0.68f), CornerRadius(s * 0.03f), style = Stroke(1.6f))
    // 篆刻痕
    drawLine(Color(0xFFFFF1F2), Offset(cx - body * 0.2f, cy - body * 0.14f), Offset(cx + body * 0.2f, cy - body * 0.14f), 2f, StrokeCap.Round)
    drawLine(Color(0xFFFFF1F2), Offset(cx, cy - body * 0.26f), Offset(cx, cy + body * 0.02f), 2f, StrokeCap.Round)
    drawLine(Color(0xFFFFF1F2), Offset(cx - body * 0.16f, cy + body * 0.18f), Offset(cx + body * 0.16f, cy + body * 0.18f), 2f, StrokeCap.Round)
    // 印底朱泥
    drawCircle(Color(0x33DC2626), s * 0.3f, Offset(cx, cy + s * 0.32f))
}

/** 葫芦：双肚 Path + 塞顶 + 系带 */
private fun DrawScope.drawGourdArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color, t: Float) {
    val rTop = s * 0.14f
    val rBot = s * 0.24f
    val neckY = cy - s * 0.06f
    val gourd = Path().apply {
        moveTo(cx, cy - s * 0.42f)
        cubicTo(cx + rTop * 1.1f, cy - s * 0.36f, cx + rTop * 1.15f, neckY - rTop * 0.4f, cx + rTop * 0.7f, neckY)
        cubicTo(cx + rBot * 1.15f, neckY + s * 0.1f, cx + rBot, cy + s * 0.38f, cx, cy + s * 0.4f)
        cubicTo(cx - rBot, cy + s * 0.38f, cx - rBot * 1.15f, neckY + s * 0.1f, cx - rTop * 0.7f, neckY)
        cubicTo(cx - rTop * 1.15f, neckY - rTop * 0.4f, cx - rTop * 1.1f, cy - s * 0.36f, cx, cy - s * 0.42f)
        close()
    }
    drawPath(gourd, Brush.verticalGradient(listOf(lerp(el, Color.White, 0.4f), el, lerp(el, Color.Black, 0.45f))))
    inkStroke(gourd, Color(0xFF0F172A), 1.1f)
    // 束腰系带
    drawOval(Color(0xFFDC2626), topLeft = Offset(cx - rTop * 0.85f, neckY - s * 0.035f), size = Size(rTop * 1.7f, s * 0.07f))
    drawOval(Color(0xFF7F1D1D), topLeft = Offset(cx - rTop * 0.85f, neckY - s * 0.035f), size = Size(rTop * 1.7f, s * 0.07f), style = Stroke(0.8f))
    // 塞顶
    drawRoundRect(Color(0xFF92400E), Offset(cx - s * 0.05f, cy - s * 0.5f), Size(s * 0.1f, s * 0.09f), CornerRadius(1.5f))
    drawFacetGem(cx, cy - s * 0.52f, s * 0.035f, Color(0xFFFDE68A))
    // 高光与灵气
    drawLine(shine, Offset(cx - rBot * 0.4f, cy + s * 0.08f), Offset(cx - rBot * 0.45f, cy + s * 0.24f), 2.2f, StrokeCap.Round)
    drawCircle(el.copy(alpha = 0.4f), s * 0.035f, Offset(cx + rBot * 0.5f + sin(t * 2f) * 3f, cy - s * 0.2f))
    drawCircle(el.copy(alpha = 0.28f), s * 0.025f, Offset(cx + rBot * 0.3f + sin(t * 2f + 1f) * 4f, cy - s * 0.34f))
}

/** 毒铃：钟形 Path + 铜环 + 冒泡 */
private fun DrawScope.drawBellArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color, t: Float) {
    // 悬环
    drawCircle(Color(0xFFCA8A04), s * 0.05f, Offset(cx, cy - s * 0.44f), style = Stroke(s * 0.028f))
    val bell = Path().apply {
        moveTo(cx - s * 0.05f, cy - s * 0.4f)
        cubicTo(cx - s * 0.28f, cy - s * 0.34f, cx - s * 0.3f, cy - s * 0.02f, cx - s * 0.34f, cy + s * 0.18f)
        lineTo(cx + s * 0.34f, cy + s * 0.18f)
        cubicTo(cx + s * 0.3f, cy - s * 0.02f, cx + s * 0.28f, cy - s * 0.34f, cx + s * 0.05f, cy - s * 0.4f)
        close()
    }
    drawPath(bell, Brush.verticalGradient(listOf(lerp(el, Color.White, 0.35f), el, lerp(el, Color.Black, 0.5f))))
    inkStroke(bell, Color(0xFF1A2E05), 1.1f)
    // 铃口沿
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFCA8A04), Color(0xFF854D0E))),
        Offset(cx - s * 0.37f, cy + s * 0.16f), Size(s * 0.74f, s * 0.07f), CornerRadius(2f)
    )
    // 腰箍与铃舌
    drawLine(Color(0xFF3F6212).copy(alpha = 0.6f), Offset(cx - s * 0.24f, cy - s * 0.08f), Offset(cx + s * 0.24f, cy - s * 0.08f), 1.4f)
    drawLine(Color(0xFF854D0E), Offset(cx, cy + s * 0.2f), Offset(cx, cy + s * 0.3f), 1.6f)
    drawCircle(Color(0xFFCA8A04), s * 0.045f, Offset(cx, cy + s * 0.32f))
    // 毒泡
    for (i in 0..3) {
        val ph = (t * 0.8f + i * 0.25f) % 1f
        drawCircle(
            Color(0x66A3E635), s * (0.02f + (i % 2) * 0.012f),
            Offset(cx + s * (0.15f + i * 0.11f) + sin(t + i) * 2f, cy + s * 0.12f - ph * s * 0.42f)
        )
    }
    drawCircle(shine, s * 0.035f, Offset(cx - s * 0.12f, cy - s * 0.2f))
}

/** 玉佩：玉琮形 + 金顶 + 云纹 */
private fun DrawScope.drawJadeArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color) {
    // 系绳
    drawLine(Color(0xFFDC2626), Offset(cx - s * 0.02f, cy - s * 0.5f), Offset(cx + s * 0.02f, cy - s * 0.44f), 1.2f)
    drawCircle(Color(0xFFDC2626), s * 0.025f, Offset(cx, cy - s * 0.5f))
    // 金顶帽
    drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.07f, cy - s * 0.46f), Size(s * 0.14f, s * 0.08f), CornerRadius(2f))
    // 玉身
    val body = Path().apply {
        moveTo(cx - s * 0.08f, cy - s * 0.38f)
        lineTo(cx + s * 0.08f, cy - s * 0.38f)
        lineTo(cx + s * 0.08f, cy + s * 0.34f)
        quadraticTo(cx, cy + s * 0.44f, cx - s * 0.08f, cy + s * 0.34f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(lerp(el, Color.White, 0.5f), el, lerp(el, Color.Black, 0.25f))))
    drawPath(body, Color(0xFF134E4A), style = Stroke(1f))
    // 透孔
    drawCircle(Color(0xFF0F172A), s * 0.035f, Offset(cx, cy - s * 0.26f))
    // 云纹刻线
    drawArc(Color(0xFFCCFBF1).copy(alpha = 0.8f), 200f, 140f, false, topLeft = Offset(cx - s * 0.06f, cy - s * 0.02f), size = Size(s * 0.12f, s * 0.12f), style = Stroke(1.2f))
    drawArc(Color(0xFFCCFBF1).copy(alpha = 0.6f), 200f, 140f, false, topLeft = Offset(cx - s * 0.06f, cy + s * 0.1f), size = Size(s * 0.12f, s * 0.12f), style = Stroke(1.2f))
    drawLine(shine, Offset(cx - s * 0.045f, cy - s * 0.34f), Offset(cx - s * 0.045f, cy + s * 0.2f), 1.5f, StrokeCap.Round)
}

/** 火灵珠：炽核 + 交叉电芒 + 轨道尘 */
private fun DrawScope.drawSparkOrbArt(cx: Float, cy: Float, s: Float, t: Float) {
    val r = s * 0.22f
    drawCircle(Color(0x33EF4444), r * 2.1f, Offset(cx, cy))
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFEF9C3), Color(0xFFFBBF24), Color(0xFFEF4444)),
            center = Offset(cx - r * 0.2f, cy - r * 0.2f), radius = r * 1.3f
        ), r, Offset(cx, cy)
    )
    drawCircle(Color(0x667F1D1D), r, Offset(cx, cy), style = Stroke(1f))
    for (i in 0..5) {
        val a = i * 1.05f + t
        drawLine(
            Color(0xFFFDE68A), Offset(cx + cos(a) * r * 0.9f, cy + sin(a) * r * 0.9f),
            Offset(cx + cos(a) * r * (1.7f + 0.2f * sin(t * 6f + i)), cy + sin(a) * r * (1.7f + 0.2f * sin(t * 6f + i))),
            1.8f, StrokeCap.Round
        )
    }
    drawCircle(Color.White.copy(alpha = 0.7f), r * 0.18f, Offset(cx - r * 0.3f, cy - r * 0.3f))
}

/** 防具专属配图：衣袍/甲片走 Path 剪影 + 分层渐变 + 墨线勾边。PNG → 缓存 → 实时。 */
fun DrawScope.drawArmorArt(cx: Float, cy: Float, size: Float, armor: GearArmor, pulse: Float = 0f) {
    val premium = if (size <= 120f) GearArtAssets.getThumb(armor.id) ?: GearArtAssets.get(armor.id)
    else GearArtAssets.get(armor.id)
    premium?.let {
        drawPremiumGearIcon(it, cx, cy, size)
        return
    }
    if (size <= 130f) {
        val cached = GearIconCache.bitmap("a|${armor.id}") {
            drawArmorArtLive(GearIconCache.centerX, GearIconCache.centerY, GearIconCache.contentSize, armor, 0f)
        }
        if (cached != null) {
            drawCachedGearIcon(cached, cx, cy, size)
            return
        }
    }
    drawArmorArtLive(cx, cy, size, armor, pulse)
}

private fun DrawScope.drawArmorArtLive(cx: Float, cy: Float, s: Float, armor: GearArmor, pulse: Float) {
    val el = armor.element?.color ?: when (armor.hero) {
        HeroClass.WARRIOR -> Color(0xFFEF4444)
        HeroClass.MAGE -> Color(0xFF60A5FA)
        HeroClass.TAOIST -> Color(0xFF4ADE80)
        null -> Color(0xFF94A3B8)
    }
    drawIconBackplate(cx, cy, s, el, armor.rarity, pulse)

    when (armor.id) {
        "a_cloth" -> {
            val robe = robePath(cx, cy, s, flare = 0.3f)
            drawPath(robe, Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1), Color(0xFF94A3B8))))
            inkStroke(robe, Color(0xFF0F172A), 1f)
            drawCollar(cx, cy, s, Color(0xFF64748B))
            drawBelt(cx, cy, s, Color(0xFF78716C))
            drawCircle(Color(0xFFFFE0BD), s * 0.15f, Offset(cx, cy - s * 0.34f))
        }
        "a_w_leather", "a_w_iron", "a_w_blood", "a_w_quake", "a_w_flame" -> {
            val (body, trim) = when (armor.id) {
                "a_w_leather" -> Color(0xFF92400E) to Color(0xFFD97706)
                "a_w_iron" -> Color(0xFF94A3B8) to Color(0xFFE2E8F0)
                "a_w_blood" -> Color(0xFF991B1B) to Color(0xFFEF4444)
                "a_w_quake" -> Color(0xFFA16207) to Color(0xFFFBBF24)
                else -> Color(0xFFEA580C) to Color(0xFFFDBA74)
            }
            val cuirass = Path().apply {
                moveTo(cx - s * 0.3f, cy - s * 0.22f)
                lineTo(cx + s * 0.3f, cy - s * 0.22f)
                cubicTo(cx + s * 0.34f, cy + s * 0.1f, cx + s * 0.28f, cy + s * 0.26f, cx + s * 0.26f, cy + s * 0.32f)
                lineTo(cx - s * 0.26f, cy + s * 0.32f)
                cubicTo(cx - s * 0.28f, cy + s * 0.26f, cx - s * 0.34f, cy + s * 0.1f, cx - s * 0.3f, cy - s * 0.22f)
                close()
            }
            drawPath(cuirass, Brush.verticalGradient(listOf(lerp(body, Color.White, 0.3f), body, lerp(body, Color.Black, 0.4f))))
            inkStroke(cuirass, Color(0xFF0F172A), 1.1f)
            // 胸甲层叠甲片线
            for (i in 0..2) {
                val ly = cy - s * 0.1f + i * s * 0.14f
                drawLine(trim.copy(alpha = 0.75f), Offset(cx - s * 0.28f, ly), Offset(cx + s * 0.28f, ly - s * 0.02f), 1.6f)
            }
            // 肩甲
            for (d in -1..1 step 2) {
                drawOval(
                    Brush.verticalGradient(listOf(lerp(body, Color.White, 0.4f), lerp(body, Color.Black, 0.25f))),
                    topLeft = Offset(cx + d * s * 0.3f - s * 0.11f, cy - s * 0.3f), size = Size(s * 0.22f, s * 0.16f)
                )
                drawOval(Color(0x660F172A), topLeft = Offset(cx + d * s * 0.3f - s * 0.11f, cy - s * 0.3f), size = Size(s * 0.22f, s * 0.16f), style = Stroke(1f))
                drawCircle(trim, s * 0.025f, Offset(cx + d * s * 0.3f, cy - s * 0.24f))
            }
            drawCollar(cx, cy, s, trim)
            drawBelt(cx, cy + s * 0.06f, s, Color(0xFF57250B))
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.33f))
            when (armor.id) {
                "a_w_flame" -> for (i in 0..2) {
                    val fx = cx - s * 0.12f + i * s * 0.12f
                    drawCircle(Color(0x88FBBF24), s * 0.035f, Offset(fx, cy - s * 0.05f + sin(pulse * 5f + i * 2f) * s * 0.02f))
                }
                "a_w_iron" -> {
                    drawLine(Color(0xFFE2E8F0).copy(alpha = 0.6f), Offset(cx - s * 0.1f, cy - s * 0.2f), Offset(cx - s * 0.16f, cy + s * 0.18f), 1.4f)
                }
                "a_w_quake" -> {
                    drawLine(Color(0xFFFBBF24).copy(alpha = 0.8f), Offset(cx + s * 0.05f, cy - s * 0.2f), Offset(cx + s * 0.12f, cy - s * 0.05f), 1.2f)
                    drawLine(Color(0xFFFBBF24).copy(alpha = 0.8f), Offset(cx + s * 0.12f, cy - s * 0.05f), Offset(cx + s * 0.03f, cy + s * 0.1f), 1.2f)
                }
            }
        }
        "a_m_robe", "a_m_frost", "a_m_storm", "a_m_abyss", "a_m_crown" -> {
            val robe = when (armor.id) {
                "a_m_robe" -> Color(0xFF3B82F6)
                "a_m_frost" -> Color(0xFF38BDF8)
                "a_m_storm" -> Color(0xFF8B5CF6)
                "a_m_abyss" -> Color(0xFF1E3A8A)
                else -> Color(0xFFEF4444)
            }
            val rp = robePath(cx, cy, s, flare = 0.4f)
            drawPath(rp, Brush.verticalGradient(listOf(lerp(robe, Color.White, 0.3f), robe, lerp(robe, Color.Black, 0.35f))))
            inkStroke(rp, Color(0xFF0F172A), 1f)
            // 宽袖
            for (d in -1..1 step 2) {
                val sleeve = Path().apply {
                    moveTo(cx + d * s * 0.14f, cy - s * 0.22f)
                    cubicTo(cx + d * s * 0.42f, cy - s * 0.1f, cx + d * s * 0.4f, cy + s * 0.16f, cx + d * s * 0.3f, cy + s * 0.28f)
                    cubicTo(cx + d * s * 0.22f, cy + s * 0.2f, cx + d * s * 0.2f, cy - s * 0.05f, cx + d * s * 0.1f, cy - s * 0.1f)
                    close()
                }
                drawPath(sleeve, Brush.verticalGradient(listOf(lerp(robe, Color.White, 0.2f), lerp(robe, Color.Black, 0.25f))))
                inkStroke(sleeve, Color(0xFF0F172A), 0.9f)
            }
            // 兜帽
            drawOval(
                Brush.verticalGradient(listOf(lerp(robe, Color.White, 0.35f), robe)),
                topLeft = Offset(cx - s * 0.24f, cy - s * 0.52f), size = Size(s * 0.48f, s * 0.32f)
            )
            drawOval(Color(0x660F172A), topLeft = Offset(cx - s * 0.24f, cy - s * 0.52f), size = Size(s * 0.48f, s * 0.32f), style = Stroke(1f))
            drawArc(Color(0xFF0F172A), 190f, 160f, false, topLeft = Offset(cx - s * 0.14f, cy - s * 0.44f), size = Size(s * 0.28f, s * 0.22f), style = Stroke(2f))
            drawCircle(Color.White.copy(alpha = 0.85f), s * 0.025f, Offset(cx - s * 0.06f, cy - s * 0.34f))
            drawCircle(Color.White.copy(alpha = 0.85f), s * 0.025f, Offset(cx + s * 0.06f, cy - s * 0.34f))
            drawBelt(cx, cy + s * 0.04f, s, Color(0xFFFBBF24))
            when (armor.id) {
                "a_m_crown" -> for (i in -1..1) {
                    drawLine(Color(0xFFFBBF24), Offset(cx + i * s * 0.09f, cy - s * 0.54f), Offset(cx + i * s * 0.09f, cy - s * 0.66f - (if (i == 0) s * 0.04f else 0f)), 2.5f, StrokeCap.Round)
                }
                "a_m_storm" -> {
                    drawLine(Color(0xFFFDE68A), Offset(cx - s * 0.14f, cy - s * 0.05f), Offset(cx + s * 0.04f, cy + s * 0.06f), 2f, StrokeCap.Round)
                    drawLine(Color(0xFFFDE68A), Offset(cx + s * 0.04f, cy + s * 0.06f), Offset(cx - s * 0.06f, cy + s * 0.16f), 2f, StrokeCap.Round)
                }
                "a_m_abyss" -> drawCircle(Color(0xFF7DD3FC), s * 0.05f, Offset(cx, cy - s * 0.1f))
                "a_m_frost" -> for (i in 0..2) {
                    val a = i * 2.1f
                    drawLine(Color(0xFFE0F2FE), Offset(cx - s * 0.1f + i * s * 0.1f, cy - s * 0.12f), Offset(cx - s * 0.1f + i * s * 0.1f + cos(a) * s * 0.04f, cy - s * 0.2f), 1.5f)
                }
            }
        }
        "a_t_cloth", "a_t_jade", "a_t_seal", "a_t_poison", "a_t_gourd" -> {
            val robe = when (armor.id) {
                "a_t_cloth" -> Color(0xFF22C55E)
                "a_t_jade" -> Color(0xFF14B8A6)
                "a_t_seal" -> Color(0xFF0F766E)
                "a_t_poison" -> Color(0xFF65A30D)
                else -> Color(0xFF7C3AED)
            }
            val rp = robePath(cx, cy, s, flare = 0.34f)
            drawPath(rp, Brush.verticalGradient(listOf(lerp(robe, Color.White, 0.28f), robe, lerp(robe, Color.Black, 0.32f))))
            inkStroke(rp, Color(0xFF0F172A), 1f)
            // 道袍领：交领右衽
            drawLine(Color(0xFFFFF7ED).copy(alpha = 0.85f), Offset(cx - s * 0.1f, cy - s * 0.3f), Offset(cx + s * 0.16f, cy + s * 0.02f), 2f, StrokeCap.Round)
            drawLine(Color(0xFFFFF7ED).copy(alpha = 0.85f), Offset(cx + s * 0.1f, cy - s * 0.3f), Offset(cx - s * 0.14f, cy + s * 0.02f), 2f, StrokeCap.Round)
            drawBelt(cx, cy + s * 0.02f, s, Color(0xFFFBBF24))
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.34f))
            // 发髻与簪
            drawCircle(robe.copy(alpha = 0.8f), s * 0.06f, Offset(cx + s * 0.05f, cy - s * 0.48f))
            drawLine(Color(0xFFCA8A04), Offset(cx - s * 0.05f, cy - s * 0.5f), Offset(cx + s * 0.12f, cy - s * 0.46f), 1.5f)
            when (armor.id) {
                "a_t_seal" -> {
                    drawRoundRect(Color(0xFFDC2626), Offset(cx + s * 0.1f, cy - s * 0.06f), Size(s * 0.1f, s * 0.1f), CornerRadius(1.5f))
                    drawRoundRect(Color(0xFFFFF1F2), Offset(cx + s * 0.12f, cy - s * 0.04f), Size(s * 0.06f, s * 0.06f), CornerRadius(1f), style = Stroke(1f))
                }
                "a_t_gourd" -> {
                    drawOval(Color(0xFFA78BFA), topLeft = Offset(cx + s * 0.12f, cy + s * 0.06f), size = Size(s * 0.12f, s * 0.15f))
                    drawOval(Color(0xFF7C3AED), topLeft = Offset(cx + s * 0.14f, cy - s * 0.02f), size = Size(s * 0.08f, s * 0.08f))
                }
                "a_t_poison" -> for (i in 0..2) {
                    val ph = (pulse * 0.7f + i * 0.33f) % 1f
                    drawCircle(Color(0x66A3E635), s * 0.022f, Offset(cx - s * 0.2f + i * s * 0.18f, cy + s * 0.14f - ph * s * 0.28f))
                }
                "a_t_jade" -> {
                    drawRoundRect(Color(0xFF5EEAD4), Offset(cx - s * 0.035f, cy - s * 0.08f), Size(s * 0.07f, s * 0.14f), CornerRadius(s * 0.035f))
                    drawCircle(Color(0xFFCCFBF1), s * 0.02f, Offset(cx, cy - s * 0.05f))
                }
            }
        }
        "a_u_chain" -> {
            val rp = robePath(cx, cy, s, flare = 0.3f)
            drawPath(rp, Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8), Color(0xFF64748B))))
            inkStroke(rp, Color(0xFF0F172A), 1f)
            // 环甲纹
            for (row in 0..3) {
                val ry = cy - s * 0.18f + row * s * 0.12f
                for (col in 0..4) {
                    val rx = cx - s * 0.2f + col * s * 0.1f + (if (row % 2 == 0) 0f else s * 0.05f)
                    drawArc(
                        Color(0xFF475569), 180f, 180f, false,
                        topLeft = Offset(rx - s * 0.035f, ry - s * 0.035f), size = Size(s * 0.07f, s * 0.07f), style = Stroke(1.2f)
                    )
                }
            }
            drawBelt(cx, cy + s * 0.06f, s, Color(0xFF57250B))
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.34f))
        }
        "a_u_scale" -> {
            val rp = robePath(cx, cy, s, flare = 0.3f)
            drawPath(rp, Brush.verticalGradient(listOf(Color(0xFF14B8A6), Color(0xFF0F766E), Color(0xFF134E4A))))
            inkStroke(rp, Color(0xFF0F172A), 1f)
            // 五色鳞片
            val cols = listOf(Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFF38BDF8), Color(0xFFEF4444))
            for (row in 0..2) {
                val ry = cy - s * 0.12f + row * s * 0.14f
                for (col in 0..3) {
                    val rx = cx - s * 0.18f + col * s * 0.12f + (if (row % 2 == 0) 0f else s * 0.06f)
                    drawArc(
                        cols[(row + col) % 4], 180f, 180f, false,
                        topLeft = Offset(rx - s * 0.05f, ry - s * 0.05f), size = Size(s * 0.1f, s * 0.1f), style = Stroke(2f)
                    )
                }
            }
            drawBelt(cx, cy + s * 0.08f, s, Color(0xFFB45309))
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.34f))
        }
        else -> {
            val rp = robePath(cx, cy, s, flare = 0.3f)
            drawPath(rp, Brush.verticalGradient(listOf(lerp(el, Color.White, 0.25f), el, lerp(el, Color.Black, 0.35f))))
            inkStroke(rp, Color(0xFF0F172A), 1f)
            drawBelt(cx, cy + s * 0.04f, s, Color(0xFF57250B))
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.34f))
        }
    }
}

/** 衣袍剪影：肩线 → 裙摆波浪 */
private fun robePath(cx: Float, cy: Float, s: Float, flare: Float): Path = Path().apply {
    moveTo(cx - s * 0.1f, cy - s * 0.3f)
    lineTo(cx - s * (0.14f + flare * 0.5f), cy - s * 0.08f)
    cubicTo(cx - s * (0.3f + flare * 0.3f), cy + s * 0.16f, cx - s * (0.3f + flare), cy + s * 0.3f, cx - s * (0.28f + flare * 0.7f), cy + s * 0.38f)
    lineTo(cx - s * 0.13f, cy + s * 0.33f)
    lineTo(cx, cy + s * 0.39f)
    lineTo(cx + s * 0.13f, cy + s * 0.33f)
    lineTo(cx + s * (0.28f + flare * 0.7f), cy + s * 0.38f)
    cubicTo(cx + s * (0.3f + flare), cy + s * 0.3f, cx + s * (0.3f + flare * 0.3f), cy + s * 0.16f, cx + s * (0.14f + flare * 0.5f), cy - s * 0.08f)
    lineTo(cx + s * 0.1f, cy - s * 0.3f)
    close()
}

/** 腰带：束带 + 绦结 + 下垂双绦 */
private fun DrawScope.drawBelt(cx: Float, cy: Float, s: Float, col: Color) {
    drawRoundRect(col, Offset(cx - s * 0.24f, cy), Size(s * 0.48f, s * 0.08f), CornerRadius(2f))
    drawRoundRect(Color(0x330F172A), Offset(cx - s * 0.24f, cy), Size(s * 0.48f, s * 0.08f), CornerRadius(2f), style = Stroke(0.8f))
    drawCircle(Color(0xFFFFF7ED), s * 0.03f, Offset(cx, cy + s * 0.04f))
    drawLine(col, Offset(cx - s * 0.03f, cy + s * 0.06f), Offset(cx - s * 0.06f, cy + s * 0.2f), s * 0.025f, StrokeCap.Round)
    drawLine(col, Offset(cx + s * 0.03f, cy + s * 0.06f), Offset(cx + s * 0.07f, cy + s * 0.19f), s * 0.025f, StrokeCap.Round)
}

/** 领口 */
private fun DrawScope.drawCollar(cx: Float, cy: Float, s: Float, col: Color) {
    val collar = Path().apply {
        moveTo(cx - s * 0.1f, cy - s * 0.32f)
        lineTo(cx, cy - s * 0.2f)
        lineTo(cx + s * 0.1f, cy - s * 0.32f)
    }
    inkStroke(collar, col, 1.6f)
}

/**
 * 套装穿在身上的持续特效光环（按套装 proc 变色/形态）。
 */
fun DrawScope.drawSetAura(
    cx: Float,
    cy: Float,
    bodyR: Float,
    set: GearSetDef?,
    pulse: Float
) {
    if (set == null) return
    val p = (sin(pulse * 3.2f) + 1f) * 0.5f
    val col = when (set.proc) {
        GearProc.BURN -> Color(0xFFFF6B35)
        GearProc.FREEZE -> Color(0xFF7DD3FC)
        GearProc.POISON -> Color(0xFFA3E635)
        GearProc.SPLASH, GearProc.THORNS -> Color(0xFFFBBF24)
        GearProc.HEAL_AMP -> Color(0xFF4ADE80)
        GearProc.KILL_SHIELD -> Color(0xFFFDE68A)
        GearProc.MP_SIPHON, GearProc.CHAIN -> Color(0xFFA78BFA)
        GearProc.RAGE_ON_HIT, GearProc.EXECUTE -> Color(0xFFEF4444)
        GearProc.WUXING_AMP -> Color(0xFFF472B6)
        else -> set.hero.color
    }
    // outer pulse ring
    drawCircle(col.copy(alpha = 0.12f + 0.1f * p), bodyR * (2.1f + 0.25f * p), Offset(cx, cy))
    drawCircle(col.copy(alpha = 0.35f), bodyR * (1.7f + 0.15f * p), Offset(cx, cy), style = Stroke(2.5f + p))
    // orbit sparks
    val n = 5
    for (i in 0 until n) {
        val a = pulse * 2.2f + i * (2f * Math.PI.toFloat() / n)
        val ox = cx + cos(a) * bodyR * 1.85f
        val oy = cy + sin(a) * bodyR * 0.75f
        drawCircle(col.copy(alpha = 0.75f), 3.5f + p * 2f, Offset(ox, oy))
    }
    // ground rune
    drawOval(
        col.copy(alpha = 0.2f + 0.1f * p),
        topLeft = Offset(cx - bodyR * 1.4f, cy + bodyR * 0.85f),
        size = Size(bodyR * 2.8f, bodyR * 0.55f)
    )
    // floating set name spark above head
    drawCircle(col.copy(alpha = 0.5f), 4f, Offset(cx + bodyR * 0.9f, cy - bodyR * 1.6f - p * 4f))
}

/** 戒指/鞋子图标：多层金属环 + 切角主石 / 靴筒剪影 + 绑带。PNG → 缓存 → 实时。 */
fun DrawScope.drawAccessoryArt(
    cx: Float,
    cy: Float,
    size: Float,
    acc: GearAccessory,
    pulse: Float = 0f,
    owned: Boolean = true
) {
    if (!owned) {
        val s = size
        drawCircle(Color(0xFF1E293B), s * 0.55f, Offset(cx, cy))
        drawCircle(Color(0xFF475569), s * 0.4f, Offset(cx, cy), style = Stroke(2f))
        return
    }
    val premium = if (size <= 120f) GearArtAssets.getThumb(acc.id) ?: GearArtAssets.get(acc.id)
    else GearArtAssets.get(acc.id)
    premium?.let {
        drawPremiumGearIcon(it, cx, cy, size)
        return
    }
    if (size <= 130f) {
        val cached = GearIconCache.bitmap("${acc.slot.name.lowercase()}|${acc.id}") {
            drawAccessoryArtLive(GearIconCache.centerX, GearIconCache.centerY, GearIconCache.contentSize, acc, 0f)
        }
        if (cached != null) {
            drawCachedGearIcon(cached, cx, cy, size)
            return
        }
    }
    drawAccessoryArtLive(cx, cy, size, acc, pulse)
}

private fun DrawScope.drawAccessoryArtLive(cx: Float, cy: Float, s: Float, acc: GearAccessory, pulse: Float) {
    val el = acc.element?.color ?: Color(0xFFA78BFA)
    drawIconBackplate(cx, cy, s, el, acc.rarity, pulse)
    when (acc.slot) {
        AccSlot.RING -> {
            val ry = cy + s * 0.1f
            when (acc.id) {
                "r_wood" -> drawWoodRingArt(cx, ry, s, pulse)
                "r_spark" -> drawSparkRingArt(cx, ry, s, pulse)
                "r_ice" -> drawIceRingArt(cx, ry, s)
                "r_blood" -> drawBloodRingArt(cx, ry, s, pulse)
                "r_jade" -> drawJadeRingArt(cx, ry, s)
                "r_penta" -> drawPentaRingArt(cx, ry, s, pulse)
                else -> drawPlainRingArt(cx, ry, s, el)
            }
        }
        AccSlot.BOOTS -> {
            val boot = Path().apply {
                moveTo(cx - s * 0.16f, cy - s * 0.36f)          // 筒口后
                lineTo(cx + s * 0.1f, cy - s * 0.36f)            // 筒口前
                cubicTo(cx + s * 0.1f, cy - s * 0.05f, cx + s * 0.14f, cy + s * 0.16f, cx + s * 0.34f, cy + s * 0.2f)  // 前踝到脚背
                quadraticTo(cx + s * 0.44f, cy + s * 0.24f, cx + s * 0.4f, cy + s * 0.32f)  // 卷趾
                lineTo(cx - s * 0.18f, cy + s * 0.32f)           // 底沿线
                cubicTo(cx - s * 0.2f, cy + s * 0.05f, cx - s * 0.18f, cy - s * 0.1f, cx - s * 0.16f, cy - s * 0.36f)
                close()
            }
            drawPath(boot, Brush.verticalGradient(listOf(lerp(el, Color.White, 0.3f), el, lerp(el, Color.Black, 0.4f))))
            inkStroke(boot, Color(0xFF0F172A), 1.1f)
            // 靴底
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF44403C), Color(0xFF1C1917))),
                Offset(cx - s * 0.2f, cy + s * 0.28f), Size(s * 0.62f, s * 0.09f), CornerRadius(2f)
            )
            // 筒口翻边
            drawRoundRect(
                Brush.verticalGradient(listOf(lerp(el, Color.White, 0.45f), el)),
                Offset(cx - s * 0.2f, cy - s * 0.4f), Size(s * 0.34f, s * 0.09f), CornerRadius(s * 0.04f)
            )
            drawRoundRect(Color(0x550F172A), Offset(cx - s * 0.2f, cy - s * 0.4f), Size(s * 0.34f, s * 0.09f), CornerRadius(s * 0.04f), style = Stroke(0.8f))
            // 绑带
            for (i in 0..2) {
                val ly = cy - s * 0.24f + i * s * 0.13f
                drawLine(Color(0xFFFDE68A), Offset(cx - s * 0.16f, ly), Offset(cx + s * 0.1f, ly + s * 0.04f), 1.8f, StrokeCap.Round)
            }
            if (acc.id == "b_wind" || acc.id == "b_star") {
                for (i in 0..1) {
                    val wob = sin(pulse * 5f + i) * s * 0.02f
                    drawLine(Color(0xAAFDE68A), Offset(cx + s * 0.16f, cy - s * 0.1f + i * s * 0.12f), Offset(cx + s * 0.5f, cy - s * 0.2f + i * s * 0.16f + wob), 2.2f, StrokeCap.Round)
                }
                if (acc.id == "b_star") {
                    val stX = cx + s * 0.3f
                    val stY = cy - s * 0.34f
                    for (i in 0..4) {
                        val a = -Math.PI.toFloat() / 2f + i * (2f * Math.PI.toFloat() / 5f)
                        drawLine(Color(0xFFFDE68A), Offset(stX, stY), Offset(stX + cos(a) * s * 0.08f, stY + sin(a) * s * 0.08f), 1.6f, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

// ═══════════════ 戒指专属配图（对齐雷纹戒位图水准） ═══════════════

/** 通用金属环底盘：双色金属弧 + 内沿勾线 */
private fun DrawScope.ringBand(cx: Float, cy: Float, s: Float, light: Color, dark: Color, width: Float = 0.11f) {
    drawArc(
        Brush.sweepGradient(listOf(light, dark, light)),
        0f, 180f, false,
        topLeft = Offset(cx - s * 0.32f, cy - s * 0.32f), size = Size(s * 0.64f, s * 0.64f),
        style = Stroke(s * width)
    )
    drawArc(
        Brush.sweepGradient(listOf(dark, light, dark)),
        180f, 180f, false,
        topLeft = Offset(cx - s * 0.32f, cy - s * 0.32f), size = Size(s * 0.64f, s * 0.64f),
        style = Stroke(s * width)
    )
    drawCircle(Color(0x660F172A), s * 0.32f, Offset(cx, cy), style = Stroke(1f))
}

/** 木纹指环：藤木环 + 叶形主石 + 环上抽芽 */
private fun DrawScope.drawWoodRingArt(cx: Float, cy: Float, s: Float, t: Float) {
    val wood = Color(0xFF92400E)
    ringBand(cx, cy, s, Color(0xFFD97706), wood, width = 0.13f)
    // 木纹节疤
    for (i in 0..2) {
        val a = 1.1f + i * 0.6f
        drawCircle(Color(0xFF57250B).copy(alpha = 0.7f), s * 0.02f, Offset(cx + cos(a) * s * 0.32f, cy + sin(a) * s * 0.32f))
    }
    // 叶形主石
    val gy = cy - s * 0.34f
    val leafGem = Path().apply {
        moveTo(cx, gy - s * 0.14f)
        quadraticTo(cx + s * 0.12f, gy, cx, gy + s * 0.14f)
        quadraticTo(cx - s * 0.12f, gy, cx, gy - s * 0.14f)
        close()
    }
    drawPath(leafGem, Brush.verticalGradient(listOf(Color(0xFFBBF7D0), Color(0xFF4ADE80), Color(0xFF166534))))
    drawPath(leafGem, Color(0xFF14532D), style = Stroke(1.1f))
    drawLine(Color(0xFFDCFCE7), Offset(cx, gy - s * 0.1f), Offset(cx, gy + s * 0.1f), 1f)
    // 环侧抽芽
    val sway = sin(t * 2.2f) * s * 0.02f
    drawLine(Color(0xFF4D7C0F), Offset(cx + s * 0.3f, cy + s * 0.12f), Offset(cx + s * 0.36f, cy - s * 0.02f + sway), 1.8f, StrokeCap.Round)
    val lp = Path().apply {
        moveTo(cx + s * 0.35f, cy + s * 0.02f)
        quadraticTo(cx + s * 0.44f, cy - s * 0.04f + sway, cx + s * 0.46f, cy - s * 0.1f + sway)
        quadraticTo(cx + s * 0.38f, cy - s * 0.02f, cx + s * 0.35f, cy + s * 0.02f)
        close()
    }
    drawPath(lp, Color(0xFF4ADE80))
    drawPath(lp, Color(0xFF166534), style = Stroke(0.7f))
}

/** 星火戒：玄铁环 + 焰形主石 + 飞溅火星 */
private fun DrawScope.drawSparkRingArt(cx: Float, cy: Float, s: Float, t: Float) {
    val iron = Color(0xFF334155)
    ringBand(cx, cy, s, Color(0xFF64748B), iron)
    // 焰形主石（菱形火晶）
    val gy = cy - s * 0.32f
    val flame = Path().apply {
        moveTo(cx, gy - s * 0.17f)
        quadraticTo(cx + s * 0.1f, gy - s * 0.05f, cx + s * 0.07f, gy + s * 0.07f)
        quadraticTo(cx + s * 0.03f, gy + s * 0.13f, cx, gy + s * 0.14f)
        quadraticTo(cx - s * 0.03f, gy + s * 0.13f, cx - s * 0.07f, gy + s * 0.07f)
        quadraticTo(cx - s * 0.1f, gy - s * 0.05f, cx, gy - s * 0.17f)
        close()
    }
    drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFEF3C7), Color(0xFFFBBF24), Color(0xFFEA580C))))
    drawPath(flame, Color(0xFF7C2D12), style = Stroke(1.1f))
    drawCircle(Color.White.copy(alpha = 0.8f), s * 0.025f, Offset(cx - s * 0.02f, gy - s * 0.04f))
    // 飞溅火星
    for (i in 0..4) {
        val a = t * 2.4f + i * 1.26f
        val ox = cx + cos(a) * s * 0.52f
        val oy = gy + sin(a) * s * 0.4f
        drawCircle(Color(0xFFFDE68A).copy(alpha = 0.9f), s * (0.015f + (i % 2) * 0.008f), Offset(ox, oy))
    }
}

/** 寒霜戒：白银环 + 六芒雪花主石 + 霜雾 */
private fun DrawScope.drawIceRingArt(cx: Float, cy: Float, s: Float) {
    ringBand(cx, cy, s, Color(0xFFF8FAFC), Color(0xFF94A3B8))
    // 霜雾
    drawCircle(
        Brush.radialGradient(listOf(Color(0x33E0F2FE), Color.Transparent)),
        s * 0.55f, Offset(cx, cy)
    )
    // 六棱雪花主石
    val gy = cy - s * 0.32f
    val r = s * 0.13f
    for (i in 0..5) {
        val a = i * (Math.PI.toFloat() / 3f)
        val ex = cx + cos(a) * r
        val ey = gy + sin(a) * r
        drawLine(Color(0xFFBAE6FD), Offset(cx, gy), Offset(ex, ey), 2f, StrokeCap.Round)
        for (d in -1..1 step 2) {
            drawLine(
                Color(0xFFE0F2FE), Offset(ex, ey),
                Offset(ex + cos(a + d * 0.7f) * r * 0.45f, ey + sin(a + d * 0.7f) * r * 0.45f), 1.2f, StrokeCap.Round
            )
        }
    }
    drawFacetGem(cx, gy, s * 0.075f, Color(0xFF7DD3FC))
}

/** 血玉戒：赤金环 + 血滴弧面玉 + 下垂血珠 */
private fun DrawScope.drawBloodRingArt(cx: Float, cy: Float, s: Float, t: Float) {
    ringBand(cx, cy, s, Color(0xFFFDE68A), Color(0xFFB45309))
    // 血滴主石
    val gy = cy - s * 0.3f
    val drop = Path().apply {
        moveTo(cx, gy - s * 0.18f)
        cubicTo(cx + s * 0.11f, gy - s * 0.04f, cx + s * 0.1f, gy + s * 0.08f, cx, gy + s * 0.12f)
        cubicTo(cx - s * 0.1f, gy + s * 0.08f, cx - s * 0.11f, gy - s * 0.04f, cx, gy - s * 0.18f)
        close()
    }
    drawPath(drop, Brush.verticalGradient(listOf(Color(0xFFFCA5A5), Color(0xFFDC2626), Color(0xFF7F1D1D))))
    drawPath(drop, Color(0xFF450A0A), style = Stroke(1.1f))
    drawCircle(Color.White.copy(alpha = 0.75f), s * 0.022f, Offset(cx - s * 0.03f, gy - s * 0.02f))
    // 环上血珠（沿环游走）
    val a = t * 1.2f
    drawCircle(Color(0xFFDC2626), s * 0.028f, Offset(cx + cos(a) * s * 0.32f, cy + sin(a) * s * 0.32f))
    // 底部垂珠
    drawLine(Color(0xFFB45309), Offset(cx, cy + s * 0.3f), Offset(cx, cy + s * 0.38f), 1.4f)
    drawCircle(Color(0xFFDC2626), s * 0.035f, Offset(cx, cy + s * 0.42f))
    drawCircle(Color.White.copy(alpha = 0.5f), s * 0.012f, Offset(cx - s * 0.012f, cy + s * 0.41f))
}

/** 玉清戒：玉玦（有缺口玉环）+ 云纹 + 系绳 */
private fun DrawScope.drawJadeRingArt(cx: Float, cy: Float, s: Float) {
    val jade = Color(0xFF14B8A6)
    // 系绳结
    drawCircle(Color(0xFFDC2626), s * 0.03f, Offset(cx, cy - s * 0.44f))
    drawLine(Color(0xFFDC2626), Offset(cx - s * 0.03f, cy - s * 0.44f), Offset(cx - s * 0.05f, cy - s * 0.5f), 1.2f)
    drawLine(Color(0xFFDC2626), Offset(cx + s * 0.03f, cy - s * 0.44f), Offset(cx + s * 0.06f, cy - s * 0.49f), 1.2f)
    // 玉玦环身（留缺）
    drawArc(
        Brush.sweepGradient(listOf(Color(0xFF5EEAD4), jade, Color(0xFF0F766E), Color(0xFF5EEAD4))),
        -70f, 315f, false,
        topLeft = Offset(cx - s * 0.3f, cy - s * 0.02f), size = Size(s * 0.6f, s * 0.6f),
        style = Stroke(s * 0.15f, cap = StrokeCap.Round)
    )
    drawCircle(Color(0x550F172A), s * 0.3f, Offset(cx, cy + s * 0.28f), style = Stroke(1f))
    // 玉质高光
    drawArc(
        Color.White.copy(alpha = 0.55f), 150f, 70f, false,
        topLeft = Offset(cx - s * 0.26f, cy + s * 0.02f), size = Size(s * 0.52f, s * 0.52f),
        style = Stroke(1.6f, cap = StrokeCap.Round)
    )
    // 缺口小玉珠
    drawCircle(Color(0xFF5EEAD4), s * 0.045f, Offset(cx + s * 0.26f, cy - s * 0.22f))
    // 云纹刻线
    drawArc(Color(0xFFCCFBF1).copy(alpha = 0.8f), 200f, 120f, false, topLeft = Offset(cx - s * 0.08f, cy + s * 0.18f), size = Size(s * 0.16f, s * 0.16f), style = Stroke(1.1f))
    drawArc(Color(0xFFCCFBF1).copy(alpha = 0.55f), 200f, 120f, false, topLeft = Offset(cx - s * 0.05f, cy + s * 0.3f), size = Size(s * 0.12f, s * 0.12f), style = Stroke(1f))
}

/** 五行宝戒：金环 + 五色宝簇（雷纹戒姊妹款） */
private fun DrawScope.drawPentaRingArt(cx: Float, cy: Float, s: Float, t: Float) {
    ringBand(cx, cy, s, Color(0xFFFDE68A), Color(0xFFB45309))
    val cols = listOf(Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFFD97706), Color(0xFF38BDF8), Color(0xFFEF4444))
    // 中心玉璧 + 五色宝簇绕行
    val gy = cy - s * 0.3f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFEF3C7), Color(0xFFFBBF24), Color(0xFF92400E)),
            center = Offset(cx - s * 0.03f, gy - s * 0.03f), radius = s * 0.2f),
        s * 0.09f, Offset(cx, gy)
    )
    drawCircle(Color(0xFF78350F), s * 0.09f, Offset(cx, gy), style = Stroke(1f))
    rotate(degrees = t * 30f, pivot = Offset(cx, gy)) {
        for (i in 0..4) {
            val a = -Math.PI.toFloat() / 2f + i * (2f * Math.PI.toFloat() / 5f)
            val px = cx + cos(a) * s * 0.17f
            val py = gy + sin(a) * s * 0.17f
            val gem = Path().apply {
                moveTo(px, py - s * 0.045f)
                lineTo(px + s * 0.035f, py)
                lineTo(px, py + s * 0.045f)
                lineTo(px - s * 0.035f, py)
                close()
            }
            drawPath(gem, cols[i])
            drawPath(gem, Color(0x660F172A), style = Stroke(0.7f))
        }
    }
    // 环身五色刻点
    for (i in 0..4) {
        val a = i * 1.26f + 0.6f
        drawCircle(cols[i].copy(alpha = 0.85f), s * 0.018f, Offset(cx + cos(a) * s * 0.32f, cy + sin(a) * s * 0.32f))
    }
}

/** 兜底：经典金环 + 切角主石 */
private fun DrawScope.drawPlainRingArt(cx: Float, cy: Float, s: Float, el: Color) {
    ringBand(cx, cy, s, Color(0xFFFDE68A), Color(0xFFB45309))
    for (d in -1..1) {
        drawLine(Color(0xFFF59E0B), Offset(cx + d * s * 0.06f, cy - s * 0.2f), Offset(cx + d * s * 0.1f, cy - s * 0.3f), 2f, StrokeCap.Round)
    }
    drawFacetGem(cx, cy - s * 0.32f, s * 0.15f, el)
}

/**
 * 图鉴点选预览：大号武器 + 特效演出（燃/冻/毒/溅射…按 proc 与 id 强化）。
 */
fun DrawScope.drawWeaponShowcaseFx(
    cx: Float,
    cy: Float,
    size: Float,
    gear: GearWeapon,
    pulse: Float
) {
    val s = size
    val el = gear.element.color
    val p = (sin(pulse * 4f) + 1f) * 0.5f
    // stage
    drawCircle(el.copy(alpha = 0.12f + 0.08f * p), s * 1.35f, Offset(cx, cy))
    drawCircle(Color(0x33000000), s * 1.1f, Offset(cx, cy + s * 0.15f))
    // big weapon
    drawWeaponArt(cx, cy, s, gear, pulse)
    // proc-based FX layer
    when (gear.proc) {
        GearProc.BURN -> {
            for (i in 0..10) {
                val a = pulse * 5f + i * 0.55f
                val r = s * (0.55f + 0.35f * ((sin(a) + 1f) * 0.5f))
                drawCircle(
                    Color(0x66FF6B35),
                    4f + (i % 3) * 2f,
                    Offset(cx + cos(a) * r * 0.4f, cy - s * 0.35f + sin(a * 1.3f) * r * 0.25f)
                )
            }
            drawCircle(Color(0x44FBBF24), s * (0.5f + 0.15f * p), Offset(cx, cy - s * 0.2f))
        }
        GearProc.FREEZE -> {
            for (i in 0..7) {
                val a = i * 0.8f + pulse * 2f
                drawLine(
                    Color(0xAA7DD3FC),
                    Offset(cx, cy),
                    Offset(cx + cos(a) * s * 0.85f, cy + sin(a) * s * 0.55f),
                    2f
                )
                drawCircle(Color(0xCCE0F2FE), 3f, Offset(cx + cos(a) * s * 0.7f, cy + sin(a) * s * 0.45f))
            }
            drawCircle(Color(0x337DD3FC), s * (0.7f + 0.1f * p), Offset(cx, cy), style = Stroke(3f))
        }
        GearProc.POISON -> {
            for (i in 0..8) {
                val t = (pulse * 1.5f + i * 0.4f)
                val oy = cy + s * 0.3f - ((t % 2.5f) / 2.5f) * s * 1.1f
                val ox = cx + sin(t * 3f + i) * s * 0.35f
                drawCircle(Color(0x88A3E635), 5f + (i % 3), Offset(ox, oy))
            }
            drawCircle(Color(0x33A3E635), s * 0.75f, Offset(cx, cy))
        }
        GearProc.SPLASH -> {
            val ring = s * (0.4f + (pulse * 2.5f % 1f) * 0.7f)
            drawCircle(Color(0x66FBBF24), ring, Offset(cx, cy), style = Stroke(3f))
            drawCircle(Color(0x44FDE68A), ring * 0.65f, Offset(cx, cy), style = Stroke(2f))
            for (i in 0..5) {
                val a = i * 1.05f + pulse
                drawCircle(Color(0xAAFBBF24), 4f, Offset(cx + cos(a) * ring, cy + sin(a) * ring * 0.6f))
            }
        }
        GearProc.MP_SIPHON -> {
            for (i in 0..6) {
                val t = (pulse * 2f + i * 0.35f) % 1.5f
                val r = s * (0.9f - t * 0.5f)
                drawCircle(Color(0x887DD3FC), 3f + t * 2f, Offset(cx + cos(i * 1.1f) * r, cy + sin(i * 1.1f) * r * 0.5f))
            }
            drawCircle(Color(0x4438BDF8), s * 0.55f, Offset(cx, cy), style = Stroke(2.5f))
        }
        GearProc.LIFESTEAL_PROC -> {
            for (i in 0..5) {
                val a = pulse * 3f + i
                drawCircle(Color(0xAAEF4444), 5f, Offset(cx + cos(a) * s * 0.5f, cy + sin(a) * s * 0.35f - s * 0.1f))
            }
            drawCircle(Color(0x33EF4444), s * 0.6f, Offset(cx, cy))
        }
        GearProc.EXECUTE -> {
            val flash = ((sin(pulse * 8f) + 1f) * 0.5f)
            drawCircle(Color(0x55EF4444), s * (0.5f + 0.3f * flash), Offset(cx, cy), style = Stroke(4f))
            drawLine(Color(0xFFF87171), Offset(cx - s * 0.7f, cy - s * 0.5f), Offset(cx + s * 0.7f, cy + s * 0.5f), 3f)
            drawLine(Color(0xFFF87171), Offset(cx + s * 0.7f, cy - s * 0.5f), Offset(cx - s * 0.7f, cy + s * 0.5f), 3f)
        }
        GearProc.RAGE_ON_HIT -> {
            for (i in 0..4) {
                val a = pulse * 6f + i * 1.2f
                drawLine(
                    Color(0xAAF97316),
                    Offset(cx, cy),
                    Offset(cx + cos(a) * s * 0.9f, cy + sin(a) * s * 0.5f),
                    3f, StrokeCap.Round
                )
            }
        }
        GearProc.CHAIN -> {
            var px = cx - s * 0.7f
            var py = cy
            for (i in 0..4) {
                val nx = cx - s * 0.4f + i * s * 0.35f
                val ny = cy + sin(pulse * 5f + i) * s * 0.25f
                drawLine(Color(0xFFA78BFA), Offset(px, py), Offset(nx, ny), 3f)
                drawCircle(Color(0xFFE9D5FF), 5f, Offset(nx, ny))
                px = nx; py = ny
            }
        }
        GearProc.WUXING_AMP -> {
            val cols = listOf(Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFFD97706), Color(0xFF38BDF8), Color(0xFFEF4444))
            for (i in 0..4) {
                val a = -Math.PI.toFloat() / 2f + i * (2f * Math.PI.toFloat() / 5f) + pulse
                drawCircle(cols[i], 8f, Offset(cx + cos(a) * s * 0.75f, cy + sin(a) * s * 0.55f))
                drawLine(cols[i].copy(alpha = 0.5f), Offset(cx, cy), Offset(cx + cos(a) * s * 0.75f, cy + sin(a) * s * 0.55f), 2f)
            }
        }
        GearProc.THORNS -> {
            for (i in 0..7) {
                val a = i * 0.8f + pulse * 0.5f
                drawLine(Color(0xFF86EFAC), Offset(cx, cy), Offset(cx + cos(a) * s * 0.85f, cy + sin(a) * s * 0.55f), 3f, StrokeCap.Round)
            }
        }
        GearProc.HEAL_AMP -> {
            for (i in 0..6) {
                val t = (pulse * 1.2f + i * 0.35f) % 2f
                drawCircle(Color(0x664ADE80), 6f, Offset(cx + sin(i * 1.2f) * s * 0.4f, cy + s * 0.4f - t * s * 0.9f))
            }
            // cross
            drawLine(Color(0xFF86EFAC), Offset(cx, cy - s * 0.25f), Offset(cx, cy + s * 0.25f), 4f)
            drawLine(Color(0xFF86EFAC), Offset(cx - s * 0.2f, cy), Offset(cx + s * 0.2f, cy), 4f)
        }
        GearProc.KILL_SHIELD -> {
            drawCircle(Color(0x66FBBF24), s * (0.65f + 0.1f * p), Offset(cx, cy), style = Stroke(4f))
            drawCircle(Color(0x33FDE68A), s * 0.45f, Offset(cx, cy))
        }
        else -> {
            // 默认元素粒子
            for (i in 0..6) {
                val a = pulse * 2.5f + i
                drawCircle(el.copy(alpha = 0.6f), 4f, Offset(cx + cos(a) * s * 0.7f, cy + sin(a) * s * 0.45f))
            }
        }
    }
    // id-specific extra flair
    when (gear.id) {
        "w_quake", "u_earth_hammer" -> {
            drawLine(Color(0x88D97706), Offset(cx - s, cy + s * 0.55f), Offset(cx + s, cy + s * 0.55f), 4f)
            drawCircle(Color(0x44D97706), s * (0.3f + 0.4f * (pulse % 1f)), Offset(cx, cy + s * 0.55f), style = Stroke(2f))
        }
        "m_crown", "w_flame" -> {
            for (i in 0..4) drawCircle(Color(0xAAFBBF24), 5f, Offset(cx + (i - 2) * s * 0.15f, cy - s * 0.7f - sin(pulse * 6f + i) * 8f))
        }
        "t_gourd" -> {
            for (i in 0..4) drawCircle(Color(0x88A78BFA), 4f, Offset(cx + s * 0.4f + sin(pulse + i) * 10f, cy - s * 0.2f + i * 8f))
        }
        "u_penta" -> { /* wheel already spins in art */ }
        "m_thunder", "w_thund_edge" -> {
            drawLine(Color(0xFFFDE68A), Offset(cx - s * 0.3f, cy - s * 0.6f), Offset(cx + s * 0.1f, cy - s * 0.1f), 3f)
            drawLine(Color(0xFFFDE68A), Offset(cx + s * 0.1f, cy - s * 0.1f), Offset(cx - s * 0.15f, cy + s * 0.3f), 3f)
        }
    }
}

fun DrawScope.drawArmorShowcaseFx(
    cx: Float,
    cy: Float,
    size: Float,
    armor: GearArmor,
    pulse: Float
) {
    val s = size
    val el = armor.element?.color ?: Color(0xFF94A3B8)
    val p = (sin(pulse * 3f) + 1f) * 0.5f
    drawCircle(el.copy(alpha = 0.15f), s * 1.2f, Offset(cx, cy))
    drawArmorArt(cx, cy, s, armor, pulse)
    drawCircle(el.copy(alpha = 0.35f), s * (0.85f + 0.1f * p), Offset(cx, cy), style = Stroke(3f))
    for (i in 0..5) {
        val a = pulse * 2f + i * 1.05f
        drawCircle(el.copy(alpha = 0.7f), 4f, Offset(cx + cos(a) * s * 0.9f, cy + sin(a) * s * 0.55f))
    }
}

/** 地图章节布景：草地 / 矿洞 / 王城 */
fun DrawScope.drawMapBackdrop(stage: StageDef, w: Float, h: Float, pulse: Float) {
    val top = Color(stage.bgTop)
    val bot = Color(stage.bgBot)
    drawRect(Brush.verticalGradient(listOf(top, bot)), size = Size(w, h))
    when (stage.chapterIndex) {
        0 -> drawMeadowDecor(w, h, pulse)
        1 -> drawMineDecor(w, h, pulse)
        else -> drawCapitalDecor(w, h, pulse)
    }
    // vignette
    drawRect(Color(0x33000000), size = Size(w, h * 0.08f))
    drawRect(Color(0x44000000), topLeft = Offset(0f, h * 0.72f), size = Size(w, h * 0.28f))
}

private fun DrawScope.drawMeadowDecor(w: Float, h: Float, pulse: Float) {
    // hills
    drawOval(Color(0xFF166534).copy(alpha = 0.55f), topLeft = Offset(-w * 0.1f, h * 0.42f), size = Size(w * 0.55f, h * 0.45f))
    drawOval(Color(0xFF15803D).copy(alpha = 0.45f), topLeft = Offset(w * 0.35f, h * 0.48f), size = Size(w * 0.7f, h * 0.4f))
    // sun
    val sx = w * 0.88f
    val sy = h * 0.28f
    drawCircle(Color(0x33FDE68A), 48f + sin(pulse) * 4f, Offset(sx, sy))
    drawCircle(Color(0x88FBBF24), 28f, Offset(sx, sy))
    drawCircle(Color(0xFFFDE68A), 16f, Offset(sx, sy))
    // floating jelly blobs
    for (i in 0..5) {
        val px = w * (0.08f + i * 0.12f)
        val py = h * (0.28f + 0.06f * sin(pulse * 2f + i))
        drawCircle(Color(0x554ADE80), 10f + (i % 3) * 3f, Offset(px, py))
        drawCircle(Color(0x88BBF7D0), 4f, Offset(px - 3f, py - 3f))
    }
    // grass tufts
    for (i in 0..14) {
        val gx = w * (0.04f + i * 0.065f)
        val gy = h * 0.68f
        drawLine(Color(0xFF4ADE80), Offset(gx, gy), Offset(gx - 4f, gy - 14f), 2.5f, StrokeCap.Round)
        drawLine(Color(0xFF86EFAC), Offset(gx, gy), Offset(gx + 5f, gy - 12f), 2.5f, StrokeCap.Round)
    }
}

private fun DrawScope.drawMineDecor(w: Float, h: Float, pulse: Float) {
    // cave walls
    drawRoundRect(Color(0xFF1C1917).copy(alpha = 0.7f), Offset(-20f, h * 0.18f), Size(w * 0.18f, h * 0.6f), CornerRadius(40f))
    drawRoundRect(Color(0xFF1C1917).copy(alpha = 0.7f), Offset(w * 0.84f, h * 0.18f), Size(w * 0.2f, h * 0.6f), CornerRadius(40f))
    // crystals
    for (i in 0..4) {
        val cx = w * (0.15f + i * 0.15f)
        val cy = h * (0.55f + 0.04f * sin(pulse + i))
        drawCircle(Color(0x66FBBF24), 14f, Offset(cx, cy))
        drawLine(Color(0xFFFBBF24), Offset(cx, cy - 18f), Offset(cx, cy + 8f), 4f, StrokeCap.Round)
        drawLine(Color(0xFFA78BFA), Offset(cx - 8f, cy - 6f), Offset(cx + 8f, cy + 10f), 3f)
    }
    // torch sparks
    for (i in 0..8) {
        val px = w * (0.1f + (i * 0.09f) % 0.8f)
        val py = h * (0.32f + 0.08f * sin(pulse * 3f + i * 1.7f))
        drawCircle(Color(0x88FB923C), 3f + (i % 2), Offset(px, py))
    }
    // rails
    drawLine(Color(0xFF78716C), Offset(w * 0.05f, h * 0.70f), Offset(w * 0.78f, h * 0.70f), 5f)
    drawLine(Color(0xFF57534E), Offset(w * 0.05f, h * 0.72f), Offset(w * 0.78f, h * 0.72f), 3f)
}

private fun DrawScope.drawCapitalDecor(w: Float, h: Float, pulse: Float) {
    // towers
    for (i in 0..4) {
        val tx = w * (0.08f + i * 0.18f)
        val th = h * (0.22f + (i % 3) * 0.05f)
        drawRoundRect(Color(0xFF4C1D95).copy(alpha = 0.55f), Offset(tx, h * 0.55f - th), Size(w * 0.08f, th), CornerRadius(6f))
        drawCircle(Color(0xFFC084FC).copy(alpha = 0.5f), 10f, Offset(tx + w * 0.04f, h * 0.55f - th))
    }
    // moon
    drawCircle(Color(0x33E2E8F0), 40f, Offset(w * 0.9f, h * 0.26f))
    drawCircle(Color(0xAAE2E8F0), 22f, Offset(w * 0.9f, h * 0.26f))
    // floating shards
    for (i in 0..7) {
        val px = w * (0.12f + i * 0.1f)
        val py = h * (0.35f + 0.05f * sin(pulse * 2.2f + i))
        rotate(degrees = i * 18f + pulse * 20f, pivot = Offset(px, py)) {
            drawRoundRect(Color(0x66F472B6), Offset(px - 6f, py - 10f), Size(12f, 20f), CornerRadius(3f))
        }
    }
}

/** 地图节点图标（战斗/店/泉…） */
fun DrawScope.drawMapNodeIcon(
    c: Offset,
    r: Float,
    type: NodeType,
    el: WuXing?,
    lit: Boolean,
    pulse: Float
) {
    val col = nodeColor(type)
    if (lit) {
        drawCircle(col.copy(alpha = 0.28f), r + 14f + 4f * pulse, c)
        drawCircle(Color(0x55FBBF24), r + 8f + 3f * pulse, c, style = Stroke(3f))
    }
    // platform
    drawOval(Color(0x66000000), topLeft = Offset(c.x - r * 1.1f, c.y + r * 0.55f), size = Size(r * 2.2f, r * 0.55f))
    drawCircle(Color(0xFF0B1220), r, c)
    drawCircle(col, r, c, style = Stroke(if (lit) 5f else 3f))
    if (el != null && (type == NodeType.MOB || type == NodeType.ELITE || type == NodeType.BOSS)) {
        drawCircle(el.color.copy(alpha = 0.55f), r * 0.82f, c, style = Stroke(3f))
    }
    // glyph art
    when (type) {
        NodeType.TREASURE -> {
            drawRoundRect(col, Offset(c.x - r * 0.36f, c.y - r * 0.16f), Size(r * 0.72f, r * 0.4f), CornerRadius(2f))
            drawCircle(Color.White.copy(alpha = 0.7f), r * 0.08f, c)
        }
        NodeType.CHALLENGE -> {
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.3f, c)
            drawCircle(Color(0xFF0B1220), r * 0.11f, Offset(c.x - r * 0.1f, c.y - r * 0.04f))
            drawCircle(Color(0xFF0B1220), r * 0.11f, Offset(c.x + r * 0.1f, c.y - r * 0.04f))
        }
        NodeType.MOB, NodeType.ELITE -> {
            // little slime
            val sc = r * 0.45f
            drawCircle(col, sc, Offset(c.x, c.y + sc * 0.1f))
            drawCircle(Color.White.copy(alpha = 0.35f), sc * 0.28f, Offset(c.x - sc * 0.25f, c.y - sc * 0.15f))
            drawCircle(Color(0xFF0F172A), sc * 0.12f, Offset(c.x - sc * 0.18f, c.y))
            drawCircle(Color(0xFF0F172A), sc * 0.12f, Offset(c.x + sc * 0.22f, c.y))
            if (type == NodeType.ELITE) drawCircle(Color(0xFFFBBF24), sc * 0.15f, Offset(c.x, c.y - sc * 0.7f))
        }
        NodeType.BOSS -> {
            drawCircle(Color(0xFFC084FC), r * 0.42f, c)
            drawCircle(Color(0xFFF472B6), r * 0.18f, Offset(c.x - r * 0.12f, c.y - r * 0.05f))
            drawCircle(Color(0xFFF472B6), r * 0.18f, Offset(c.x + r * 0.18f, c.y - r * 0.05f))
            drawCircle(Color(0xFFFBBF24), r * 0.12f, Offset(c.x, c.y - r * 0.55f))
        }
        NodeType.SHOP -> {
            // anvil-ish
            drawRoundRect(Color(0xFF94A3B8), Offset(c.x - r * 0.4f, c.y - r * 0.1f), Size(r * 0.8f, r * 0.35f), CornerRadius(4f))
            drawRoundRect(Color(0xFF64748B), Offset(c.x - r * 0.15f, c.y + r * 0.15f), Size(r * 0.3f, r * 0.35f), CornerRadius(3f))
            drawCircle(Color(0xFFFBBF24), r * 0.12f, Offset(c.x + r * 0.25f, c.y - r * 0.25f))
        }
        NodeType.GOLD -> {
            drawCircle(Color(0xFFFACC15), r * 0.38f, c)
            drawCircle(Color(0xFFFDE68A), r * 0.18f, Offset(c.x - r * 0.1f, c.y - r * 0.1f))
        }
        NodeType.HEAL, NodeType.REST -> {
            drawCircle(Color(0xFF34D399), r * 0.35f, c)
            drawLine(Color.White, Offset(c.x, c.y - r * 0.22f), Offset(c.x, c.y + r * 0.22f), 4f, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x - r * 0.22f, c.y), Offset(c.x + r * 0.22f, c.y), 4f, StrokeCap.Round)
        }
        NodeType.TRAP -> {
            drawCircle(Color(0xFFEF4444), r * 0.32f, c)
            for (i in 0..4) {
                val a = i * 1.2f
                drawLine(Color(0xFFFCA5A5), c, Offset(c.x + cos(a) * r * 0.45f, c.y + sin(a) * r * 0.45f), 3f)
            }
        }
        NodeType.EVENT -> {
            drawCircle(Color(0xFFF472B6), r * 0.35f, c)
            drawCircle(Color.White, r * 0.12f, Offset(c.x, c.y - r * 0.05f))
            drawRoundRect(Color.White, Offset(c.x - r * 0.06f, c.y + r * 0.08f), Size(r * 0.12f, r * 0.22f), CornerRadius(2f))
        }
        NodeType.START -> drawCircle(Color(0xFF94A3B8), r * 0.28f, c)
        NodeType.EXIT -> {
            drawRoundRect(Color(0xFF2DD4BF), Offset(c.x - r * 0.35f, c.y - r * 0.4f), Size(r * 0.7f, r * 0.8f), CornerRadius(6f))
            drawCircle(Color(0xFF0F172A), r * 0.1f, Offset(c.x + r * 0.15f, c.y))
        }
    }
}

/** 装备栏展示台：角色 + 武器 + 防具 + 套装光环 */
fun DrawScope.drawGearShowcase(
    cx: Float,
    cy: Float,
    heroScale: Float,
    skin: CharacterSkin,
    gear: GearWeapon,
    armor: GearArmor,
    set: GearSetDef?,
    pulse: Float
) {
    drawOval(
        Brush.radialGradient(listOf(gear.element.color.copy(alpha = 0.35f), Color.Transparent)),
        topLeft = Offset(cx - heroScale * 2.2f, cy + heroScale * 0.9f),
        size = Size(heroScale * 4.4f, heroScale * 1.4f)
    )
    drawOval(
        Color(0x66000000),
        topLeft = Offset(cx - heroScale * 1.6f, cy + heroScale * 1.05f),
        size = Size(heroScale * 3.2f, heroScale * 0.7f)
    )
    drawWeaponArt(cx - heroScale * 1.65f, cy + heroScale * 0.05f, heroScale * 1.25f, gear, pulse)
    drawArmorArt(cx + heroScale * 1.55f, cy + heroScale * 0.15f, heroScale * 1.1f, armor, pulse)
    val hx = cx + heroScale * 0.05f
    val hy = cy
    drawSetAura(hx, hy, heroScale, set, pulse)
    drawCuteHero(hx, hy, heroScale, 1f, skin, bob = sin(pulse * 3f) * 3f)
    drawRoundRect(
        Color(0xAA0F172A),
        Offset(cx - heroScale * 1.8f, cy + heroScale * 1.55f),
        Size(heroScale * 3.6f, heroScale * 0.55f),
        CornerRadius(10f)
    )
}

/** 地图顶部角色小卡 */
fun DrawScope.drawMapHeroCard(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    skin: CharacterSkin,
    gear: GearWeapon,
    armor: GearArmor,
    set: GearSetDef?,
    pulse: Float
) {
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xEE1E293B), Color(0xCC0F172A))),
        Offset(x, y),
        Size(w, h),
        CornerRadius(12f)
    )
    drawRoundRect(gear.element.color.copy(alpha = 0.5f), Offset(x, y), Size(w, h), CornerRadius(12f), style = Stroke(2f))
    val scale = min(w, h) * 0.26f
    val hx = x + w * 0.22f
    val hy = y + h * 0.52f
    drawSetAura(hx, hy, scale, set, pulse)
    drawCuteHero(hx, hy, scale, 1f, skin, bob = sin(pulse * 3f) * 2f)
    drawWeaponArt(x + w * 0.58f, y + h * 0.45f, scale * 0.95f, gear, pulse)
    drawArmorArt(x + w * 0.82f, y + h * 0.48f, scale * 0.9f, armor, pulse)
}

/** 底部路点按钮上的迷你图标 */
fun DrawScope.drawMiniNodeBadge(cx: Float, cy: Float, type: NodeType, el: WuXing?) {
    val r = 14f
    drawCircle(nodeColor(type).copy(alpha = 0.9f), r, Offset(cx, cy))
    if (el != null) drawCircle(el.color, r * 0.45f, Offset(cx, cy))
    else drawCircle(Color.White.copy(alpha = 0.85f), r * 0.35f, Offset(cx, cy))
}

// ═══════════════ 战斗状态徽记 ═══════════════

/**
 * 战斗内状态与战术职责的图形徽记。
 * 取代原先的纯文字（冻/毒/燃/弱、疗/鼓/卫）—— 图形在中文/日文下同样可读。
 */
enum class StatusEmblem(val color: Long) {
    FREEZE(0xFF7DD3FC),
    POISON(0xFFA3E635),
    BURN(0xFFFF6B35),
    SLOW(0xFF93C5FD),
    VULN(0xFFF472B6),
    HEALER(0xFF4ADE80),
    DRUMMER(0xFFFB923C),
    GUARD(0xFFFBBF24)
}

/** 徽记底片：墨底圆角芯片 + 色环 */
private fun DrawScope.emblemChip(cx: Float, cy: Float, r: Float, col: Color, glow: Float = 0f) {
    if (glow > 0f) drawCircle(col.copy(alpha = 0.16f * glow), r * 1.65f, Offset(cx, cy))
    drawRoundRect(Color(0xD90F172A), Offset(cx - r, cy - r), Size(r * 2f, r * 2f), CornerRadius(r * 0.38f, r * 0.38f))
    drawRoundRect(col.copy(alpha = 0.95f), Offset(cx - r, cy - r), Size(r * 2f, r * 2f), CornerRadius(r * 0.38f, r * 0.38f), style = Stroke(1.6f))
}

fun DrawScope.drawStatusEmblem(cx: Float, cy: Float, r: Float, emblem: StatusEmblem, t: Float = 0f) {
    val col = Color(emblem.color)
    when (emblem) {
        StatusEmblem.FREEZE -> {
            emblemChip(cx, cy, r, col)
            val k = r * 0.62f
            for (i in 0..2) {
                val a = Math.PI.toFloat() / 3f * i + (Math.PI.toFloat() / 6f)
                drawLine(col, Offset(cx - cos(a) * k, cy - sin(a) * k), Offset(cx + cos(a) * k, cy + sin(a) * k), 1.4f, StrokeCap.Round)
            }
            for (i in 0..5) {
                val a = i * (Math.PI.toFloat() / 3f) + Math.PI.toFloat() / 6f
                val px = cx + cos(a) * k
                val py = cy + sin(a) * k
                for (d in -1..1 step 2) {
                    drawLine(col, Offset(px, py), Offset(px + cos(a + d * 0.6f) * k * 0.4f, py + sin(a + d * 0.6f) * k * 0.4f), 1.1f, StrokeCap.Round)
                }
            }
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.14f, Offset(cx, cy))
        }
        StatusEmblem.POISON -> {
            emblemChip(cx, cy, r, col)
            val drop = Path().apply {
                moveTo(cx, cy - r * 0.55f)
                cubicTo(cx + r * 0.42f, cy - r * 0.1f, cx + r * 0.36f, cy + r * 0.45f, cx, cy + r * 0.5f)
                cubicTo(cx - r * 0.36f, cy + r * 0.45f, cx - r * 0.42f, cy - r * 0.1f, cx, cy - r * 0.55f)
                close()
            }
            drawPath(drop, Brush.verticalGradient(listOf(lerp(col, Color.White, 0.4f), col, lerp(col, Color.Black, 0.35f))))
            drawPath(drop, Color(0xFF1A2E05), style = Stroke(1f))
            drawCircle(Color(0xCCFEF9C3), r * 0.1f, Offset(cx - r * 0.13f, cy + r * 0.1f))
            drawCircle(Color(0x88FEF9C3), r * 0.07f, Offset(cx + r * 0.16f, cy - r * 0.12f + sin(t * 3f) * r * 0.05f))
        }
        StatusEmblem.BURN -> {
            emblemChip(cx, cy, r, col)
            val flick = sin(t * 7f) * r * 0.06f
            val outer = Path().apply {
                moveTo(cx, cy - r * 0.62f + flick)
                cubicTo(cx + r * 0.45f, cy - r * 0.15f, cx + r * 0.3f, cy + r * 0.45f, cx, cy + r * 0.5f)
                cubicTo(cx - r * 0.3f, cy + r * 0.45f, cx - r * 0.45f, cy - r * 0.15f, cx, cy - r * 0.62f + flick)
                close()
            }
            drawPath(outer, Brush.verticalGradient(listOf(Color(0xFFFDE68A), col, Color(0xFFC2410C))))
            val inner = Path().apply {
                moveTo(cx, cy - r * 0.3f + flick * 0.6f)
                cubicTo(cx + r * 0.22f, cy + r * 0.02f, cx + r * 0.16f, cy + r * 0.38f, cx, cy + r * 0.42f)
                cubicTo(cx - r * 0.16f, cy + r * 0.38f, cx - r * 0.22f, cy + r * 0.02f, cx, cy - r * 0.3f + flick * 0.6f)
                close()
            }
            drawPath(inner, Color(0xFFFEF9C3))
        }
        StatusEmblem.SLOW -> {
            emblemChip(cx, cy, r, col)
            drawArc(col, 100f, 260f, false, topLeft = Offset(cx - r * 0.45f, cy - r * 0.45f), size = Size(r * 0.9f, r * 0.9f), style = Stroke(1.8f, cap = StrokeCap.Round))
            drawCircle(col, r * 0.16f, Offset(cx, cy))
            for (i in 0..2) {
                val ly = cy - r * 0.3f + i * r * 0.3f
                drawLine(col.copy(alpha = 0.7f), Offset(cx + r * 0.55f, ly), Offset(cx + r * 0.85f, ly), 1.4f, StrokeCap.Round)
            }
        }
        StatusEmblem.VULN -> {
            emblemChip(cx, cy, r, col)
            val shield = Path().apply {
                moveTo(cx, cy - r * 0.58f)
                lineTo(cx + r * 0.48f, cy - r * 0.38f)
                lineTo(cx + r * 0.42f, cy + r * 0.18f)
                quadraticTo(cx + r * 0.2f, cy + r * 0.5f, cx, cy + r * 0.6f)
                quadraticTo(cx - r * 0.2f, cy + r * 0.5f, cx - r * 0.42f, cy + r * 0.18f)
                lineTo(cx - r * 0.48f, cy - r * 0.38f)
                close()
            }
            drawPath(shield, Color(0x33F472B6))
            drawPath(shield, col, style = Stroke(1.5f))
            drawLine(Color.White, Offset(cx - r * 0.05f, cy - r * 0.4f), Offset(cx + r * 0.12f, cy - r * 0.1f), 1.4f, StrokeCap.Round)
            drawLine(Color.White, Offset(cx + r * 0.12f, cy - r * 0.1f), Offset(cx - r * 0.08f, cy + r * 0.2f), 1.4f, StrokeCap.Round)
            drawLine(Color.White, Offset(cx - r * 0.08f, cy + r * 0.2f), Offset(cx + r * 0.06f, cy + r * 0.45f), 1.4f, StrokeCap.Round)
        }
        StatusEmblem.HEALER -> {
            emblemChip(cx, cy, r, col, glow = 0.5f + 0.5f * sin(t * 3f))
            val k = r * 0.5f
            drawLine(Color(0xFFECFDF5), Offset(cx, cy - k), Offset(cx, cy + k), 2.4f, StrokeCap.Round)
            drawLine(Color(0xFFECFDF5), Offset(cx - k, cy), Offset(cx + k, cy), 2.4f, StrokeCap.Round)
            for (i in 0..3) {
                val a = t * 2.4f + i * (Math.PI.toFloat() / 2f)
                drawCircle(col.copy(alpha = 0.8f), r * 0.12f, Offset(cx + cos(a) * r * 0.82f, cy + sin(a) * r * 0.82f))
            }
        }
        StatusEmblem.DRUMMER -> {
            emblemChip(cx, cy, r, col)
            val beat = 0.5f + 0.5f * sin(t * 6f)
            drawOval(Brush.verticalGradient(listOf(Color(0xFFFDBA74), Color(0xFFC2410C))), topLeft = Offset(cx - r * 0.5f, cy - r * 0.28f), size = Size(r, r * 0.42f))
            drawOval(Color(0xFFFBE8D3), topLeft = Offset(cx - r * 0.5f, cy - r * 0.42f), size = Size(r, r * 0.3f))
            drawOval(Color(0xFF9A3412), topLeft = Offset(cx - r * 0.5f, cy - r * 0.42f), size = Size(r, r * 0.3f), style = Stroke(1f))
            drawLine(Color(0xFF9A3412), Offset(cx - r * 0.35f, cy + r * 0.05f), Offset(cx - r * 0.15f, cy + r * 0.32f), 1f)
            drawLine(Color(0xFF9A3412), Offset(cx + r * 0.15f, cy + r * 0.05f), Offset(cx + r * 0.35f, cy + r * 0.32f), 1f)
            // 鼓槌
            drawLine(Color(0xFFFDE68A), Offset(cx + r * 0.15f, cy - r * 0.62f + beat * r * 0.1f), Offset(cx + r * 0.4f, cy - r * 0.38f), 2f, StrokeCap.Round)
            drawCircle(Color(0xFFFDE68A), r * 0.11f, Offset(cx + r * 0.15f, cy - r * 0.62f + beat * r * 0.1f))
        }
        StatusEmblem.GUARD -> {
            emblemChip(cx, cy, r, col)
            val shield = Path().apply {
                moveTo(cx, cy - r * 0.6f)
                lineTo(cx + r * 0.5f, cy - r * 0.4f)
                lineTo(cx + r * 0.44f, cy + r * 0.15f)
                quadraticTo(cx + r * 0.22f, cy + r * 0.52f, cx, cy + r * 0.62f)
                quadraticTo(cx - r * 0.22f, cy + r * 0.52f, cx - r * 0.44f, cy + r * 0.15f)
                lineTo(cx - r * 0.5f, cy - r * 0.4f)
                close()
            }
            drawPath(shield, Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFB45309))))
            drawPath(shield, Color(0xFF57250B), style = Stroke(1.2f))
            drawLine(Color(0xFF57250B), Offset(cx - r * 0.2f, cy + r * 0.05f), Offset(cx + r * 0.2f, cy + r * 0.05f), 1.6f)
            drawLine(Color(0xFF57250B), Offset(cx - r * 0.2f, cy + r * 0.22f), Offset(cx + r * 0.2f, cy + r * 0.22f), 1.6f)
            drawCircle(Color.White.copy(alpha = 0.7f), r * 0.09f, Offset(cx - r * 0.15f, cy - r * 0.28f))
        }
    }
}
