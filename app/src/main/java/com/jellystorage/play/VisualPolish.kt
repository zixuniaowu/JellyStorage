package com.jellystorage.play

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 每把武器专属配图（按 id 分支，再 fallback 职业轮廓）。
 */
fun DrawScope.drawWeaponArt(
    cx: Float,
    cy: Float,
    size: Float,
    gear: GearWeapon,
    pulse: Float = 0f
) {
    val s = size
    val el = gear.element.color
    val shine = Color.White.copy(alpha = 0.35f + 0.15f * ((sin(pulse * 4f) + 1f) * 0.5f))
    drawCircle(el.copy(alpha = 0.16f), s * 0.72f, Offset(cx, cy + s * 0.12f))
    drawOval(Color(0x55000000), topLeft = Offset(cx - s * 0.55f, cy + s * 0.42f), size = Size(s * 1.1f, s * 0.22f))
    val rim = when (gear.rarity) {
        2 -> Color(0xFFFBBF24)
        1 -> Color(0xFFA78BFA)
        else -> Color(0xFF64748B)
    }
    drawCircle(rim.copy(alpha = 0.4f), s * 0.62f, Offset(cx, cy), style = Stroke(2.5f))

    when (gear.id) {
        // ── 战士 ──
        "w_iron" -> drawSwordBase(cx, cy, s, Color(0xFF94A3B8), shine, short = true, rust = true)
        "w_steel" -> drawSwordBase(cx, cy, s, Color(0xFFE2E8F0), shine, long = true)
        "w_blood" -> {
            drawSwordBase(cx, cy, s, Color(0xFFEF4444), shine, long = true)
            drawCircle(Color(0x88DC2626), s * 0.12f, Offset(cx + s * 0.22f, cy - s * 0.1f))
            drawLine(Color(0xFF7F1D1D), Offset(cx - s * 0.02f, cy - s * 0.4f), Offset(cx + s * 0.08f, cy + s * 0.05f), 2f)
        }
        "w_quake" -> drawAxeArt(cx, cy, s, Color(0xFFD97706), shine)
        "w_gold_spear" -> drawSpearArt(cx, cy, s, Color(0xFFFBBF24), shine)
        "w_flame" -> {
            drawSwordBase(cx, cy, s, Color(0xFFFF6B35), shine, long = true, wide = true)
            for (i in 0..3) {
                val a = pulse * 3f + i
                drawCircle(Color(0xAAFBBF24), s * 0.06f, Offset(cx + cos(a) * s * 0.2f, cy - s * 0.35f + sin(a) * s * 0.1f))
            }
        }
        "w_thund_edge" -> {
            drawSwordBase(cx - s * 0.12f, cy, s * 0.85f, Color(0xFF38BDF8), shine)
            drawSwordBase(cx + s * 0.12f, cy, s * 0.85f, Color(0xFFA78BFA), shine)
            drawLine(Color(0xFFFDE68A), Offset(cx, cy - s * 0.5f), Offset(cx, cy + s * 0.2f), 2f)
        }
        "w_guard_blade" -> {
            drawSwordBase(cx, cy, s, Color(0xFF86EFAC), shine)
            drawRoundRect(Color(0xFF64748B), Offset(cx - s * 0.35f, cy + s * 0.05f), Size(s * 0.7f, s * 0.12f), CornerRadius(3f))
        }
        // ── 法师 ──
        "w_staff", "m_flame" -> drawStaffBase(cx, cy, s, el, shine, orbScale = 1f, flame = gear.id == "m_flame")
        "m_ice" -> {
            drawStaffBase(cx, cy, s, el, shine, orbScale = 1.15f)
            drawCircle(Color(0xAAE0F2FE), s * 0.2f, Offset(cx, cy - s * 0.35f), style = Stroke(2f))
            drawLine(Color(0xFF7DD3FC), Offset(cx - s * 0.2f, cy - s * 0.5f), Offset(cx + s * 0.2f, cy - s * 0.2f), 2f)
        }
        "m_thunder" -> {
            drawStaffBase(cx, cy, s, el, shine, tall = true)
            drawLine(Color(0xFFFDE68A), Offset(cx - s * 0.15f, cy - s * 0.55f), Offset(cx + s * 0.05f, cy - s * 0.25f), 3f)
            drawLine(Color(0xFFFDE68A), Offset(cx + s * 0.05f, cy - s * 0.25f), Offset(cx - s * 0.08f, cy - s * 0.05f), 3f)
        }
        "m_wood_orb" -> {
            drawCircle(Color(0xFF166534), s * 0.28f, Offset(cx, cy))
            drawCircle(el, s * 0.22f, Offset(cx, cy))
            drawCircle(shine, s * 0.08f, Offset(cx - s * 0.08f, cy - s * 0.08f))
            drawOval(Color(0xFF4ADE80), topLeft = Offset(cx + s * 0.1f, cy - s * 0.35f), size = Size(s * 0.2f, s * 0.28f))
        }
        "m_crown" -> {
            drawStaffBase(cx, cy, s, el, shine, orbScale = 1.25f, flame = true)
            // crown spikes
            for (i in -2..2) {
                drawLine(Color(0xFFFBBF24), Offset(cx + i * s * 0.1f, cy - s * 0.55f), Offset(cx + i * s * 0.1f, cy - s * 0.72f), 3f, StrokeCap.Round)
            }
        }
        "m_abyss" -> {
            drawRoundRect(Color(0xFF1E3A5F), Offset(cx - s * 0.22f, cy - s * 0.4f), Size(s * 0.44f, s * 0.7f), CornerRadius(4f))
            drawRoundRect(el.copy(alpha = 0.7f), Offset(cx - s * 0.16f, cy - s * 0.32f), Size(s * 0.32f, s * 0.55f), CornerRadius(3f))
            drawCircle(Color(0xFF7DD3FC), s * 0.1f, Offset(cx, cy - s * 0.1f))
        }
        "m_earth_tome" -> {
            drawRoundRect(Color(0xFF78350F), Offset(cx - s * 0.25f, cy - s * 0.35f), Size(s * 0.5f, s * 0.65f), CornerRadius(4f))
            drawRoundRect(Color(0xFFD97706), Offset(cx - s * 0.18f, cy - s * 0.28f), Size(s * 0.36f, s * 0.5f), CornerRadius(3f))
            drawLine(Color(0xFFFEF3C7), Offset(cx - s * 0.1f, cy - s * 0.1f), Offset(cx + s * 0.1f, cy - s * 0.1f), 2f)
            drawLine(Color(0xFFFEF3C7), Offset(cx - s * 0.1f, cy + s * 0.05f), Offset(cx + s * 0.08f, cy + s * 0.05f), 2f)
        }
        // ── 道士 ──
        "w_peach", "t_talisman" -> drawTalismanBase(cx, cy, s, el, shine, peach = gear.id == "w_peach")
        "t_dust" -> {
            drawRoundRect(Color(0xFFA16207), Offset(cx - s * 0.04f, cy - s * 0.15f), Size(s * 0.08f, s * 0.55f), CornerRadius(3f))
            for (i in 0..5) {
                val a = -0.6f + i * 0.25f
                drawLine(el, Offset(cx, cy - s * 0.2f), Offset(cx + cos(a) * s * 0.45f, cy - s * 0.55f + sin(a) * s * 0.2f), 2.5f, StrokeCap.Round)
            }
        }
        "t_seal" -> {
            drawRoundRect(Color(0xFF7F1D1D), Offset(cx - s * 0.28f, cy - s * 0.28f), Size(s * 0.56f, s * 0.56f), CornerRadius(6f))
            drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.18f, cy - s * 0.18f), Size(s * 0.36f, s * 0.36f), CornerRadius(4f))
            drawCircle(Color(0xFFFEF3C7), s * 0.08f, Offset(cx, cy))
        }
        "t_fire_charm" -> {
            drawTalismanBase(cx, cy, s, Color(0xFFEF4444), shine)
            drawCircle(Color(0xAAFBBF24), s * 0.15f, Offset(cx + s * 0.25f, cy - s * 0.25f))
        }
        "t_gourd" -> {
            drawOval(Color(0xFF7C3AED), topLeft = Offset(cx - s * 0.22f, cy - s * 0.05f), size = Size(s * 0.44f, s * 0.5f))
            drawOval(Color(0xFFA78BFA), topLeft = Offset(cx - s * 0.16f, cy - s * 0.35f), size = Size(s * 0.32f, s * 0.28f))
            drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.06f, cy - s * 0.45f), Size(s * 0.12f, s * 0.12f), CornerRadius(2f))
            drawCircle(shine, s * 0.06f, Offset(cx - s * 0.06f, cy + s * 0.05f))
        }
        "t_poison_bell" -> {
            drawOval(Color(0xFF4D7C0F), topLeft = Offset(cx - s * 0.25f, cy - s * 0.15f), size = Size(s * 0.5f, s * 0.45f))
            drawCircle(Color(0xFFA3E635), s * 0.12f, Offset(cx, cy - s * 0.35f))
            drawLine(Color(0xFFCA8A04), Offset(cx, cy - s * 0.45f), Offset(cx, cy - s * 0.55f), 3f)
            drawCircle(Color(0x66A3E635), s * 0.2f, Offset(cx + s * 0.25f, cy + s * 0.1f))
        }
        "t_jade" -> {
            drawRoundRect(Color(0xFF14B8A6), Offset(cx - s * 0.08f, cy - s * 0.4f), Size(s * 0.16f, s * 0.7f), CornerRadius(s * 0.08f))
            drawCircle(Color(0xFF5EEAD4), s * 0.18f, Offset(cx, cy - s * 0.35f))
            drawCircle(shine, s * 0.06f, Offset(cx - s * 0.05f, cy - s * 0.4f))
        }
        // ── 通用 ──
        "u_water_blade" -> drawSwordBase(cx, cy, s, Color(0xFF38BDF8), shine, short = true)
        "u_earth_hammer" -> drawHammerArt(cx, cy, s, Color(0xFFD97706), shine)
        "u_wood_bow" -> drawBowArt(cx, cy, s, Color(0xFF4ADE80), shine)
        "u_penta" -> drawPentaWheel(cx, cy, s, pulse)
        "u_spark" -> {
            drawCircle(Color(0xFFEF4444), s * 0.2f, Offset(cx, cy))
            drawCircle(Color(0xFFFBBF24), s * 0.12f, Offset(cx, cy))
            for (i in 0..4) {
                val a = i * 1.25f + pulse
                drawLine(Color(0xFFFDE68A), Offset(cx, cy), Offset(cx + cos(a) * s * 0.4f, cy + sin(a) * s * 0.4f), 2f)
            }
        }
        else -> {
            val style = gear.hero ?: HeroClass.WARRIOR
            when (style) {
                HeroClass.WARRIOR -> drawSwordBase(cx, cy, s, el, shine)
                HeroClass.MAGE -> drawStaffBase(cx, cy, s, el, shine)
                HeroClass.TAOIST -> drawTalismanBase(cx, cy, s, el, shine)
            }
        }
    }
    drawCircle(Color(0xFF0F172A), s * 0.12f, Offset(cx + s * 0.38f, cy - s * 0.38f))
    drawCircle(el, s * 0.09f, Offset(cx + s * 0.38f, cy - s * 0.38f))
}

private fun DrawScope.drawSwordBase(
    cx: Float, cy: Float, s: Float, el: Color, shine: Color,
    short: Boolean = false, long: Boolean = false, wide: Boolean = false, rust: Boolean = false
) {
    val h = s * (if (long) 0.8f else if (short) 0.55f else 0.7f)
    val bw = s * (if (wide) 0.2f else 0.14f)
    val blade = if (rust) Color(0xFF78716C) else el
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFE2E8F0), blade, Color(0xFF64748B))),
        topLeft = Offset(cx - bw * 0.5f, cy - h * 0.75f),
        size = Size(bw, h),
        cornerRadius = CornerRadius(s * 0.05f, s * 0.05f)
    )
    drawLine(shine, Offset(cx - bw * 0.15f, cy - h * 0.65f), Offset(cx - bw * 0.15f, cy - h * 0.1f), s * 0.025f)
    drawCircle(blade, bw * 0.45f, Offset(cx, cy - h * 0.78f))
    drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.28f, cy + h * 0.12f), Size(s * 0.56f, s * 0.09f), CornerRadius(3f))
    drawRoundRect(Color(0xFF7C2D12), Offset(cx - s * 0.06f, cy + h * 0.2f), Size(s * 0.12f, s * 0.26f), CornerRadius(3f))
    drawCircle(Color(0xFFFBBF24), s * 0.07f, Offset(cx, cy + h * 0.48f))
}

private fun DrawScope.drawAxeArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color) {
    drawRoundRect(Color(0xFF78350F), Offset(cx - s * 0.05f, cy - s * 0.2f), Size(s * 0.1f, s * 0.65f), CornerRadius(3f))
    drawOval(el, topLeft = Offset(cx - s * 0.45f, cy - s * 0.45f), size = Size(s * 0.55f, s * 0.4f))
    drawOval(el.copy(alpha = 0.85f), topLeft = Offset(cx - s * 0.05f, cy - s * 0.42f), size = Size(s * 0.4f, s * 0.35f))
    drawCircle(shine, s * 0.06f, Offset(cx - s * 0.2f, cy - s * 0.32f))
}

private fun DrawScope.drawSpearArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color) {
    drawRoundRect(Color(0xFFA16207), Offset(cx - s * 0.035f, cy - s * 0.35f), Size(s * 0.07f, s * 0.85f), CornerRadius(2f))
    // spear head
    drawRoundRect(el, Offset(cx - s * 0.1f, cy - s * 0.55f), Size(s * 0.2f, s * 0.28f), CornerRadius(s * 0.1f))
    drawCircle(shine, s * 0.05f, Offset(cx - s * 0.03f, cy - s * 0.48f))
    drawCircle(Color(0xFFFBBF24), s * 0.06f, Offset(cx, cy + s * 0.45f))
}

private fun DrawScope.drawHammerArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color) {
    drawRoundRect(Color(0xFF78350F), Offset(cx - s * 0.05f, cy - s * 0.1f), Size(s * 0.1f, s * 0.55f), CornerRadius(3f))
    drawRoundRect(el, Offset(cx - s * 0.35f, cy - s * 0.45f), Size(s * 0.7f, s * 0.32f), CornerRadius(6f))
    drawCircle(shine, s * 0.06f, Offset(cx - s * 0.15f, cy - s * 0.35f))
}

private fun DrawScope.drawBowArt(cx: Float, cy: Float, s: Float, el: Color, shine: Color) {
    drawCircle(el, s * 0.42f, Offset(cx, cy), style = Stroke(s * 0.08f))
    drawLine(Color(0xFFE2E8F0), Offset(cx, cy - s * 0.4f), Offset(cx, cy + s * 0.4f), 2f)
    drawLine(el, Offset(cx, cy), Offset(cx + s * 0.45f, cy - s * 0.05f), 3f, StrokeCap.Round)
    drawCircle(shine, s * 0.05f, Offset(cx - s * 0.25f, cy - s * 0.15f))
}

private fun DrawScope.drawPentaWheel(cx: Float, cy: Float, s: Float, pulse: Float) {
    val cols = listOf(Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFFD97706), Color(0xFF38BDF8), Color(0xFFEF4444))
    drawCircle(Color(0xFF1E293B), s * 0.42f, Offset(cx, cy))
    for (i in 0..4) {
        val a = -Math.PI.toFloat() / 2f + i * (2f * Math.PI.toFloat() / 5f) + pulse * 0.4f
        val p = Offset(cx + cos(a) * s * 0.28f, cy + sin(a) * s * 0.28f)
        drawCircle(cols[i], s * 0.1f, p)
        drawLine(Color(0x88E2E8F0), Offset(cx, cy), p, 2f)
    }
    drawCircle(Color(0xFFFDE68A), s * 0.08f, Offset(cx, cy))
}

private fun DrawScope.drawStaffBase(
    cx: Float, cy: Float, s: Float, el: Color, shine: Color,
    orbScale: Float = 1f, tall: Boolean = false, flame: Boolean = false
) {
    val shaftH = s * (if (tall) 0.8f else 0.65f)
    drawRoundRect(Color(0xFF78350F), Offset(cx - s * 0.05f, cy - s * 0.1f), Size(s * 0.1f, shaftH), CornerRadius(4f))
    val oy = cy - s * 0.35f
    drawCircle(el.copy(alpha = 0.35f), s * 0.32f * orbScale, Offset(cx, oy))
    drawCircle(el, s * 0.22f * orbScale, Offset(cx, oy))
    drawCircle(shine, s * 0.08f, Offset(cx - s * 0.07f, oy - s * 0.08f))
    if (flame) {
        drawCircle(Color(0xAAFBBF24), s * 0.1f, Offset(cx + s * 0.12f, oy - s * 0.15f))
        drawCircle(Color(0xAAEF4444), s * 0.07f, Offset(cx - s * 0.1f, oy - s * 0.18f))
    }
    drawCircle(Color(0xFFFBBF24), s * 0.15f, Offset(cx, cy - s * 0.05f), style = Stroke(s * 0.035f))
}

private fun DrawScope.drawTalismanBase(
    cx: Float, cy: Float, s: Float, el: Color, shine: Color, peach: Boolean = false
) {
    val wood = if (peach) Color(0xFFD97706) else Color(0xFFA16207)
    drawRoundRect(wood, Offset(cx - s * 0.07f, cy - s * 0.45f), Size(s * 0.14f, s * 0.7f), CornerRadius(s * 0.05f))
    drawRoundRect(el.copy(alpha = 0.55f), Offset(cx - s * 0.04f, cy - s * 0.4f), Size(s * 0.08f, s * 0.55f), CornerRadius(3f))
    drawRoundRect(Color(0xFFFEF3C7), Offset(cx + s * 0.12f, cy - s * 0.35f), Size(s * 0.3f, s * 0.45f), CornerRadius(4f))
    drawLine(el, Offset(cx + s * 0.18f, cy - s * 0.22f), Offset(cx + s * 0.36f, cy - s * 0.22f), 2.5f)
    drawLine(el, Offset(cx + s * 0.18f, cy - s * 0.08f), Offset(cx + s * 0.34f, cy - s * 0.08f), 2.5f)
    drawCircle(shine, s * 0.05f, Offset(cx + s * 0.26f, cy - s * 0.3f))
}

/** 防具专属配图 */
fun DrawScope.drawArmorArt(cx: Float, cy: Float, size: Float, armor: GearArmor, pulse: Float = 0f) {
    val s = size
    val el = armor.element?.color ?: when (armor.hero) {
        HeroClass.WARRIOR -> Color(0xFFEF4444)
        HeroClass.MAGE -> Color(0xFF60A5FA)
        HeroClass.TAOIST -> Color(0xFF4ADE80)
        null -> Color(0xFF94A3B8)
    }
    val shine = Color.White.copy(alpha = 0.3f)
    drawCircle(el.copy(alpha = 0.15f), s * 0.7f, Offset(cx, cy + s * 0.1f))
    drawOval(Color(0x55000000), topLeft = Offset(cx - s * 0.5f, cy + s * 0.4f), size = Size(s, s * 0.2f))
    when (armor.id) {
        "a_cloth" -> {
            drawRoundRect(Color(0xFFCBD5E1), Offset(cx - s * 0.28f, cy - s * 0.2f), Size(s * 0.56f, s * 0.55f), CornerRadius(s * 0.15f))
            drawCircle(Color(0xFFFFE0BD), s * 0.16f, Offset(cx, cy - s * 0.35f))
        }
        "a_w_leather", "a_w_iron", "a_w_blood", "a_w_quake", "a_w_flame" -> {
            val body = when (armor.id) {
                "a_w_leather" -> Color(0xFF92400E)
                "a_w_iron" -> Color(0xFF94A3B8)
                "a_w_blood" -> Color(0xFFB91C1C)
                "a_w_quake" -> Color(0xFFA16207)
                else -> Color(0xFFEA580C)
            }
            drawRoundRect(body, Offset(cx - s * 0.32f, cy - s * 0.15f), Size(s * 0.64f, s * 0.55f), CornerRadius(s * 0.12f))
            drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.2f, cy + s * 0.05f), Size(s * 0.4f, s * 0.08f), CornerRadius(2f))
            drawCircle(Color(0xFFFFE0BD), s * 0.15f, Offset(cx, cy - s * 0.32f))
            if (armor.id == "a_w_flame") {
                drawCircle(Color(0xAAFBBF24), s * 0.1f, Offset(cx + s * 0.25f, cy - s * 0.1f + sin(pulse * 4f) * 3f))
            }
            if (armor.id == "a_w_iron" || armor.id == "a_w_quake") {
                drawCircle(Color(0xFF64748B), s * 0.12f, Offset(cx - s * 0.35f, cy))
                drawCircle(Color(0xFF64748B), s * 0.12f, Offset(cx + s * 0.35f, cy))
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
            drawOval(robe, topLeft = Offset(cx - s * 0.35f, cy - s * 0.1f), size = Size(s * 0.7f, s * 0.65f))
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.28f))
            // hood / hat
            drawOval(robe, topLeft = Offset(cx - s * 0.22f, cy - s * 0.5f), size = Size(s * 0.44f, s * 0.28f))
            if (armor.id == "a_m_crown") {
                for (i in -1..1) drawLine(Color(0xFFFBBF24), Offset(cx + i * s * 0.1f, cy - s * 0.48f), Offset(cx + i * s * 0.1f, cy - s * 0.62f), 3f)
            }
            if (armor.id == "a_m_storm") {
                drawLine(Color(0xFFFDE68A), Offset(cx - s * 0.2f, cy), Offset(cx + s * 0.15f, cy + s * 0.15f), 2f)
            }
            drawCircle(shine, s * 0.05f, Offset(cx - s * 0.1f, cy))
        }
        "a_t_cloth", "a_t_jade", "a_t_seal", "a_t_poison", "a_t_gourd" -> {
            val robe = when (armor.id) {
                "a_t_cloth" -> Color(0xFF22C55E)
                "a_t_jade" -> Color(0xFF14B8A6)
                "a_t_seal" -> Color(0xFF0F766E)
                "a_t_poison" -> Color(0xFF65A30D)
                else -> Color(0xFF7C3AED)
            }
            drawRoundRect(robe, Offset(cx - s * 0.3f, cy - s * 0.12f), Size(s * 0.6f, s * 0.55f), CornerRadius(s * 0.2f))
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.3f))
            // sash
            drawRoundRect(Color(0xFFFBBF24), Offset(cx - s * 0.25f, cy + s * 0.08f), Size(s * 0.5f, s * 0.08f), CornerRadius(2f))
            if (armor.id == "a_t_seal") drawCircle(Color(0xFFEF4444), s * 0.08f, Offset(cx + s * 0.2f, cy - s * 0.05f))
            if (armor.id == "a_t_gourd") drawOval(Color(0xFFA78BFA), topLeft = Offset(cx + s * 0.15f, cy + s * 0.15f), size = Size(s * 0.2f, s * 0.25f))
            if (armor.id == "a_t_poison") drawCircle(Color(0x88A3E635), s * 0.12f, Offset(cx - s * 0.25f, cy + s * 0.1f))
        }
        "a_u_chain" -> {
            drawRoundRect(Color(0xFF94A3B8), Offset(cx - s * 0.3f, cy - s * 0.15f), Size(s * 0.6f, s * 0.5f), CornerRadius(8f))
            for (i in 0..2) drawLine(Color(0xFF64748B), Offset(cx - s * 0.22f, cy - s * 0.05f + i * s * 0.12f), Offset(cx + s * 0.22f, cy - s * 0.05f + i * s * 0.12f), 2f)
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.3f))
        }
        "a_u_scale" -> {
            drawRoundRect(Color(0xFF0F766E), Offset(cx - s * 0.3f, cy - s * 0.12f), Size(s * 0.6f, s * 0.52f), CornerRadius(10f))
            val cols = listOf(Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFF38BDF8), Color(0xFFEF4444))
            cols.forEachIndexed { i, c -> drawCircle(c, s * 0.07f, Offset(cx - s * 0.18f + i * s * 0.12f, cy + s * 0.05f)) }
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.3f))
        }
        else -> {
            drawRoundRect(el, Offset(cx - s * 0.28f, cy - s * 0.15f), Size(s * 0.56f, s * 0.5f), CornerRadius(10f))
            drawCircle(Color(0xFFFFE0BD), s * 0.14f, Offset(cx, cy - s * 0.3f))
        }
    }
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

/** 戒指/鞋子图标 */
fun DrawScope.drawAccessoryArt(
    cx: Float,
    cy: Float,
    size: Float,
    acc: GearAccessory,
    pulse: Float = 0f,
    owned: Boolean = true
) {
    val s = size
    val el = acc.element?.color ?: Color(0xFFA78BFA)
    if (!owned) {
        drawCircle(Color(0xFF1E293B), s * 0.55f, Offset(cx, cy))
        drawCircle(Color(0xFF475569), s * 0.4f, Offset(cx, cy), style = Stroke(2f))
        return
    }
    drawCircle(el.copy(alpha = 0.2f), s * 0.65f, Offset(cx, cy))
    when (acc.slot) {
        AccSlot.RING -> {
            drawCircle(Color(0xFF78350F), s * 0.42f, Offset(cx, cy), style = Stroke(s * 0.12f))
            drawCircle(el, s * 0.22f, Offset(cx, cy - s * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.5f), s * 0.08f, Offset(cx - s * 0.06f, cy - s * 0.1f))
            if (acc.id == "r_spark" || acc.id == "r_penta") {
                for (i in 0..4) {
                    val a = pulse * 2f + i * 1.2f
                    drawCircle(el, 3f, Offset(cx + cos(a) * s * 0.5f, cy + sin(a) * s * 0.5f))
                }
            }
        }
        AccSlot.BOOTS -> {
            drawRoundRect(el, Offset(cx - s * 0.35f, cy - s * 0.15f), Size(s * 0.7f, s * 0.45f), CornerRadius(s * 0.12f))
            drawRoundRect(Color(0xFF1E293B), Offset(cx - s * 0.32f, cy + s * 0.18f), Size(s * 0.64f, s * 0.18f), CornerRadius(4f))
            drawCircle(Color.White.copy(alpha = 0.35f), s * 0.08f, Offset(cx - s * 0.1f, cy - s * 0.05f))
            if (acc.id == "b_wind" || acc.id == "b_star") {
                drawLine(Color(0xAAFDE68A), Offset(cx + s * 0.2f, cy), Offset(cx + s * 0.55f, cy - s * 0.15f), 3f)
                drawLine(Color(0xAAFDE68A), Offset(cx + s * 0.2f, cy + s * 0.1f), Offset(cx + s * 0.5f, cy + s * 0.05f), 2f)
            }
        }
    }
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
