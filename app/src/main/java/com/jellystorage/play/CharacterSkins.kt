package com.jellystorage.play

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Palette for a chibi character. Swap skins later without changing draw code.
 */
data class CharacterSkin(
    val id: String,
    val displayName: String,
    val skinTone: Color,
    val hair: Color,
    val outfit: Color,
    val accent: Color,
    val cheek: Color,
    val eye: Color = Color(0xFF1E293B),
    val accessory: AccessoryKind
)

enum class AccessoryKind {
    SWORD, STAFF, TALISMAN, CROWN, NONE
}

/** Built-in skins. Add new rows here for future shop unlocks. */
object SkinCatalog {
    val warriorDefault = CharacterSkin(
        id = "warrior_default",
        displayName = "小红盔",
        skinTone = Color(0xFFFFE0BD),
        hair = Color(0xFF7C2D12),
        outfit = Color(0xFFEF4444),
        accent = Color(0xFFFBBF24),
        cheek = Color(0xFFFF8FAB),
        accessory = AccessoryKind.SWORD
    )
    val warriorPink = CharacterSkin(
        id = "warrior_sakura",
        displayName = "樱花武士",
        skinTone = Color(0xFFFFE4D6),
        hair = Color(0xFFF472B6),
        outfit = Color(0xFFFDA4AF),
        accent = Color(0xFFFCE7F3),
        cheek = Color(0xFFFF6B9D),
        accessory = AccessoryKind.SWORD
    )
    val mageDefault = CharacterSkin(
        id = "mage_default",
        displayName = "小蓝帽",
        skinTone = Color(0xFFFFE0BD),
        hair = Color(0xFF1E3A8A),
        outfit = Color(0xFF3B82F6),
        accent = Color(0xFFA78BFA),
        cheek = Color(0xFFFF9EB5),
        accessory = AccessoryKind.STAFF
    )
    val mageStar = CharacterSkin(
        id = "mage_star",
        displayName = "星尘法师",
        skinTone = Color(0xFFFFE8D0),
        hair = Color(0xFF6366F1),
        outfit = Color(0xFF8B5CF6),
        accent = Color(0xFFFDE68A),
        cheek = Color(0xFFFFB4C8),
        accessory = AccessoryKind.STAFF
    )
    val taoistDefault = CharacterSkin(
        id = "taoist_default",
        displayName = "小青符",
        skinTone = Color(0xFFFFE0BD),
        hair = Color(0xFF14532D),
        outfit = Color(0xFF22C55E),
        accent = Color(0xFFFACC15),
        cheek = Color(0xFFFF9EB5),
        accessory = AccessoryKind.TALISMAN
    )
    val taoistCloud = CharacterSkin(
        id = "taoist_cloud",
        displayName = "云游道童",
        skinTone = Color(0xFFFFE4D6),
        hair = Color(0xFF0F766E),
        outfit = Color(0xFF14B8A6),
        accent = Color(0xFFFEF3C7),
        cheek = Color(0xFFFF8FAB),
        accessory = AccessoryKind.TALISMAN
    )

    fun defaultFor(hero: HeroClass): CharacterSkin = when (hero) {
        HeroClass.WARRIOR -> warriorDefault
        HeroClass.MAGE -> mageDefault
        HeroClass.TAOIST -> taoistDefault
    }

    fun altsFor(hero: HeroClass): List<CharacterSkin> = when (hero) {
        HeroClass.WARRIOR -> listOf(warriorDefault, warriorPink)
        HeroClass.MAGE -> listOf(mageDefault, mageStar)
        HeroClass.TAOIST -> listOf(taoistDefault, taoistCloud)
    }

    fun byId(id: String): CharacterSkin? = listOf(
        warriorDefault, warriorPink, mageDefault, mageStar, taoistDefault, taoistCloud
    ).find { it.id == id }
}

/** Distinct monster roster — each has its own silhouette. */
enum class EnemyKind {
    SLIME, PINK_SLIME, SPIKE_SLIME,
    BEETLE, BAT, SKELETON, GOBLIN, RAT, WISP,
    BOSS_SLIME, BOSS_ORE
}

/**
 * Compact chibi — detail over bulk. [bodyR] ≈ collision radius.
 */
fun DrawScope.drawCuteHero(
    cx: Float,
    cy: Float,
    bodyR: Float,
    facing: Float,
    skin: CharacterSkin,
    hitFlash: Float = 0f,
    bob: Float = 0f,
    attackFlash: Float = 0f
) {
    val flash = hitFlash > 0f
    val s = bodyR * 0.92f
    val face = if (flash) Color.White else skin.skinTone
    val outfit = if (flash) Color.White else skin.outfit
    val hair = if (flash) Color(0xFFE2E8F0) else skin.hair
    val accent = if (flash) Color.White else skin.accent
    val dir = if (facing >= 0f) 1f else -1f
    val y = cy + bob
    val outfitDark = outfit.copy(
        red = (outfit.red * 0.72f).coerceIn(0f, 1f),
        green = (outfit.green * 0.72f).coerceIn(0f, 1f),
        blue = (outfit.blue * 0.72f).coerceIn(0f, 1f)
    )

    // soft contact shadow
    drawOval(
        Color(0x40000000),
        topLeft = Offset(cx - s * 0.62f, y + s * 0.72f),
        size = Size(s * 1.24f, s * 0.28f)
    )

    // legs (two little ovals)
    drawOval(outfitDark, topLeft = Offset(cx - s * 0.42f, y + s * 0.42f), size = Size(s * 0.32f, s * 0.38f))
    drawOval(outfitDark, topLeft = Offset(cx + s * 0.10f, y + s * 0.42f), size = Size(s * 0.32f, s * 0.38f))
    // shoes
    drawOval(Color(0xFF1E293B), topLeft = Offset(cx - s * 0.48f, y + s * 0.68f), size = Size(s * 0.36f, s * 0.16f))
    drawOval(Color(0xFF1E293B), topLeft = Offset(cx + s * 0.12f, y + s * 0.68f), size = Size(s * 0.36f, s * 0.16f))

    // torso
    drawRoundRect(
        outfit,
        topLeft = Offset(cx - s * 0.48f, y - s * 0.12f),
        size = Size(s * 0.96f, s * 0.72f),
        cornerRadius = CornerRadius(s * 0.35f, s * 0.35f)
    )
    // belt
    drawRoundRect(
        accent,
        topLeft = Offset(cx - s * 0.42f, y + s * 0.32f),
        size = Size(s * 0.84f, s * 0.10f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawCircle(accent, s * 0.08f, Offset(cx, y + s * 0.37f))
    // collar / neck
    drawCircle(face, s * 0.14f, Offset(cx, y - s * 0.05f))
    // belly sheen
    drawOval(
        Color.White.copy(alpha = 0.18f),
        topLeft = Offset(cx - s * 0.22f, y + s * 0.02f),
        size = Size(s * 0.36f, s * 0.28f)
    )

    // arms
    drawOval(face, topLeft = Offset(cx - s * 0.72f, y + s * 0.02f), size = Size(s * 0.28f, s * 0.36f))
    drawOval(face, topLeft = Offset(cx + s * 0.44f, y + s * 0.02f), size = Size(s * 0.28f, s * 0.36f))

    // head
    val headR = s * 0.58f
    val headY = y - s * 0.52f
    drawCircle(face, headR, Offset(cx, headY))
    // ear dots
    drawCircle(face, headR * 0.22f, Offset(cx - headR * 0.92f, headY + headR * 0.05f))
    drawCircle(face, headR * 0.22f, Offset(cx + headR * 0.92f, headY + headR * 0.05f))
    // forehead highlight
    drawCircle(Color.White.copy(alpha = 0.28f), headR * 0.28f, Offset(cx - headR * 0.22f, headY - headR * 0.28f))

    // hair bowl
    drawOval(
        hair,
        topLeft = Offset(cx - headR * 0.98f, headY - headR * 1.05f),
        size = Size(headR * 1.96f, headR * 1.05f)
    )
    drawCircle(hair, headR * 0.30f, Offset(cx - headR * 0.42f, headY - headR * 0.28f))
    drawCircle(hair, headR * 0.26f, Offset(cx + headR * 0.28f, headY - headR * 0.32f))
    drawCircle(hair, headR * 0.18f, Offset(cx + dir * headR * 0.55f, headY - headR * 0.05f))

    // accessories (compact)
    when (skin.accessory) {
        AccessoryKind.SWORD, AccessoryKind.NONE -> {
            drawRoundRect(
                accent,
                topLeft = Offset(cx - headR * 0.62f, headY - headR * 0.88f),
                size = Size(headR * 1.24f, headR * 0.20f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // helmet knob
            drawCircle(accent, headR * 0.16f, Offset(cx, headY - headR * 1.05f))
            drawCircle(Color.White.copy(alpha = 0.4f), headR * 0.07f, Offset(cx - headR * 0.04f, headY - headR * 1.08f))
        }
        AccessoryKind.STAFF -> {
            drawOval(
                skin.outfit,
                topLeft = Offset(cx - headR * 0.85f, headY - headR * 1.15f),
                size = Size(headR * 1.7f, headR * 0.55f)
            )
            drawCircle(skin.outfit, headR * 0.34f, Offset(cx, headY - headR * 1.22f))
            drawCircle(skin.outfit, headR * 0.20f, Offset(cx + dir * headR * 0.06f, headY - headR * 1.48f))
            drawCircle(accent, headR * 0.10f, Offset(cx + dir * headR * 0.06f, headY - headR * 1.62f))
            // star on brim
            drawCircle(accent, headR * 0.08f, Offset(cx - headR * 0.25f, headY - headR * 0.95f))
        }
        AccessoryKind.TALISMAN -> {
            drawCircle(hair, headR * 0.22f, Offset(cx, headY - headR * 0.95f))
            drawCircle(accent, headR * 0.09f, Offset(cx, headY - headR * 1.12f))
            // cheek talisman
            drawRoundRect(
                accent,
                topLeft = Offset(cx + headR * 0.48f * dir, headY - headR * 0.05f),
                size = Size(headR * 0.28f, headR * 0.42f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawLine(
                skin.outfit,
                Offset(cx + headR * 0.62f * dir, headY + headR * 0.05f),
                Offset(cx + headR * 0.62f * dir, headY + headR * 0.22f),
                strokeWidth = headR * 0.06f
            )
        }
        AccessoryKind.CROWN -> {
            drawRoundRect(
                accent,
                topLeft = Offset(cx - headR * 0.48f, headY - headR * 1.0f),
                size = Size(headR * 0.96f, headR * 0.22f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }

    // face details
    val eyeOffX = dir * headR * 0.06f
    val eyeY = headY + headR * 0.02f
    val eyeDx = headR * 0.26f
    val eyeR = headR * 0.11f
    // brows
    drawLine(
        hair,
        Offset(cx - eyeDx + eyeOffX - eyeR, eyeY - eyeR * 1.4f),
        Offset(cx - eyeDx + eyeOffX + eyeR, eyeY - eyeR * 1.1f),
        strokeWidth = headR * 0.06f,
        cap = StrokeCap.Round
    )
    drawLine(
        hair,
        Offset(cx + eyeDx + eyeOffX - eyeR, eyeY - eyeR * 1.1f),
        Offset(cx + eyeDx + eyeOffX + eyeR, eyeY - eyeR * 1.4f),
        strokeWidth = headR * 0.06f,
        cap = StrokeCap.Round
    )
    drawCircle(skin.eye, eyeR, Offset(cx - eyeDx + eyeOffX, eyeY))
    drawCircle(skin.eye, eyeR, Offset(cx + eyeDx + eyeOffX, eyeY))
    drawCircle(Color.White, eyeR * 0.38f, Offset(cx - eyeDx + eyeOffX + eyeR * 0.22f, eyeY - eyeR * 0.22f))
    drawCircle(Color.White, eyeR * 0.38f, Offset(cx + eyeDx + eyeOffX + eyeR * 0.22f, eyeY - eyeR * 0.22f))
    // cheeks
    drawCircle(skin.cheek.copy(alpha = 0.5f), headR * 0.12f, Offset(cx - headR * 0.48f + eyeOffX, headY + headR * 0.28f))
    drawCircle(skin.cheek.copy(alpha = 0.5f), headR * 0.12f, Offset(cx + headR * 0.48f + eyeOffX, headY + headR * 0.28f))
    // smile
    drawLine(
        skin.eye.copy(alpha = 0.65f),
        Offset(cx - headR * 0.10f + eyeOffX, headY + headR * 0.36f),
        Offset(cx + headR * 0.10f + eyeOffX, headY + headR * 0.36f),
        strokeWidth = headR * 0.07f,
        cap = StrokeCap.Round
    )

    // weapon (compact)
    val handX = cx + dir * s * 0.70f
    val handY = y + s * 0.12f
    when (skin.accessory) {
        AccessoryKind.SWORD -> {
            val ang = if (attackFlash > 0f) -55f * dir else -18f * dir
            rotate(ang, pivot = Offset(handX, handY)) {
                drawRoundRect(
                    Color(0xFFCBD5E1),
                    topLeft = Offset(handX - s * 0.06f, handY - s * 0.85f),
                    size = Size(s * 0.12f, s * 0.85f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                drawLine(
                    Color.White.copy(alpha = 0.5f),
                    Offset(handX, handY - s * 0.8f),
                    Offset(handX, handY - s * 0.2f),
                    strokeWidth = s * 0.03f
                )
                drawRoundRect(
                    accent,
                    topLeft = Offset(handX - s * 0.18f, handY - s * 0.12f),
                    size = Size(s * 0.36f, s * 0.11f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                drawRoundRect(
                    Color(0xFF78350F),
                    topLeft = Offset(handX - s * 0.07f, handY - s * 0.02f),
                    size = Size(s * 0.14f, s * 0.22f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
        AccessoryKind.STAFF -> {
            drawLine(
                Color(0xFFA16207),
                Offset(handX, handY + s * 0.38f),
                Offset(handX + dir * s * 0.08f, handY - s * 0.72f),
                strokeWidth = s * 0.09f,
                cap = StrokeCap.Round
            )
            drawCircle(accent, s * 0.16f, Offset(handX + dir * s * 0.08f, handY - s * 0.82f))
            drawCircle(Color.White.copy(alpha = 0.55f), s * 0.07f, Offset(handX + dir * s * 0.04f, handY - s * 0.86f))
            // orbit spark
            drawCircle(accent.copy(alpha = 0.5f), s * 0.05f, Offset(handX + dir * s * 0.22f, handY - s * 0.7f))
        }
        AccessoryKind.TALISMAN -> {
            drawRoundRect(
                accent,
                topLeft = Offset(handX - s * 0.14f, handY - s * 0.2f),
                size = Size(s * 0.28f, s * 0.4f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawLine(skin.outfit, Offset(handX, handY - s * 0.08f), Offset(handX, handY + s * 0.1f), strokeWidth = s * 0.05f)
            drawCircle(skin.outfit, s * 0.05f, Offset(handX, handY + s * 0.02f))
        }
        else -> Unit
    }
}

/**
 * Distinct enemy silhouettes — not one blob with tint.
 */
fun DrawScope.drawCuteEnemy(
    cx: Float,
    cy: Float,
    r: Float,
    kind: EnemyKind,
    hitFlash: Float = 0f,
    bob: Float = 0f,
    facing: Float = 1f
) {
    val flash = hitFlash > 0f
    val y = cy + bob
    val dir = if (facing >= 0f) 1f else -1f
    fun col(c: Color) = if (flash) Color.White else c
    // contact shadow
    drawOval(Color(0x3A000000), topLeft = Offset(cx - r * 0.8f, y + r * 0.5f), size = Size(r * 1.6f, r * 0.32f))

    when (kind) {
        EnemyKind.SLIME -> drawSlimeBody(cx, y, r, col(Color(0xFF4ADE80)), Color(0xFF166534), dir, crown = false, spikes = false)
        EnemyKind.PINK_SLIME -> drawSlimeBody(cx, y, r, col(Color(0xFFF472B6)), Color(0xFF9D174D), dir, crown = false, spikes = false, heart = true)
        EnemyKind.SPIKE_SLIME -> drawSlimeBody(cx, y, r, col(Color(0xFF94A3B8)), Color(0xFF334155), dir, crown = false, spikes = true)
        EnemyKind.BEETLE -> drawBeetle(cx, y, r, col(Color(0xFFFB923C)), Color(0xFF9A3412), dir)
        EnemyKind.BAT -> drawBat(cx, y, r, col(Color(0xFF64748B)), Color(0xFF1E293B), dir)
        EnemyKind.SKELETON -> drawSkeleton(cx, y, r, col(Color(0xFFE2E8F0)), Color(0xFF64748B), dir)
        EnemyKind.GOBLIN -> drawGoblin(cx, y, r, col(Color(0xFF86EFAC)), Color(0xFF166534), dir)
        EnemyKind.RAT -> drawRat(cx, y, r, col(Color(0xFFA8A29E)), Color(0xFF44403C), dir)
        EnemyKind.WISP -> drawWisp(cx, y, r, col(Color(0xFFA78BFA)), Color(0xFF5B21B6), dir)
        EnemyKind.BOSS_SLIME -> drawSlimeBody(cx, y, r, col(Color(0xFFC084FC)), Color(0xFF6B21A8), dir, crown = true, spikes = false)
        EnemyKind.BOSS_ORE -> drawOreBoss(cx, y, r, col(Color(0xFFFBBF24)), Color(0xFF92400E), dir)
    }
}

private fun DrawScope.drawFace(
    cx: Float, y: Float, r: Float, dir: Float,
    eyeYOff: Float = -0.12f, eyeDx: Float = 0.28f, eyeR: Float = 0.13f,
    mouthY: Float = 0.22f, angry: Boolean = false
) {
    val eyeY = y + r * eyeYOff
    val ex = dir * r * 0.06f
    val er = r * eyeR
    drawCircle(Color(0xFF0F172A), er, Offset(cx - r * eyeDx + ex, eyeY))
    drawCircle(Color(0xFF0F172A), er, Offset(cx + r * eyeDx + ex, eyeY))
    drawCircle(Color.White, er * 0.35f, Offset(cx - r * eyeDx + ex + er * 0.2f, eyeY - er * 0.2f))
    drawCircle(Color.White, er * 0.35f, Offset(cx + r * eyeDx + ex + er * 0.2f, eyeY - er * 0.2f))
    if (angry) {
        drawLine(Color(0xFF0F172A), Offset(cx - r * 0.42f + ex, eyeY - er * 1.2f), Offset(cx - r * 0.15f + ex, eyeY - er * 0.4f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        drawLine(Color(0xFF0F172A), Offset(cx + r * 0.42f + ex, eyeY - er * 1.2f), Offset(cx + r * 0.15f + ex, eyeY - er * 0.4f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
    }
    drawCircle(Color(0xFFFF8FAB).copy(alpha = 0.45f), r * 0.09f, Offset(cx - r * 0.48f, y + r * 0.1f))
    drawCircle(Color(0xFFFF8FAB).copy(alpha = 0.45f), r * 0.09f, Offset(cx + r * 0.48f, y + r * 0.1f))
    drawLine(
        Color(0xFF0F172A).copy(alpha = 0.55f),
        Offset(cx - r * 0.12f + ex, y + r * mouthY),
        Offset(cx + r * 0.12f + ex, y + r * mouthY),
        strokeWidth = r * 0.07f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawSlimeBody(
    cx: Float, y: Float, r: Float, body: Color, dark: Color, dir: Float,
    crown: Boolean, spikes: Boolean, heart: Boolean = false
) {
    if (spikes) {
        for (i in -2..2) {
            val sx = cx + i * r * 0.28f
            drawLine(dark, Offset(sx, y - r * 0.55f), Offset(sx, y - r * 1.05f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
            drawCircle(body, r * 0.1f, Offset(sx, y - r * 1.05f))
        }
    }
    drawOval(body, topLeft = Offset(cx - r * 0.95f, y - r * 0.7f), size = Size(r * 1.9f, r * 1.55f))
    drawOval(Color.White.copy(alpha = 0.28f), topLeft = Offset(cx - r * 0.55f, y - r * 0.55f), size = Size(r * 0.55f, r * 0.35f))
    drawOval(body, topLeft = Offset(cx + r * 0.25f, y + r * 0.35f), size = Size(r * 0.28f, r * 0.35f))
    drawOval(body, topLeft = Offset(cx - r * 0.5f, y + r * 0.4f), size = Size(r * 0.22f, r * 0.28f))
    if (heart) {
        drawCircle(Color(0xFFFFF1F2).copy(alpha = 0.5f), r * 0.12f, Offset(cx - r * 0.1f, y + r * 0.05f))
        drawCircle(Color(0xFFFFF1F2).copy(alpha = 0.5f), r * 0.12f, Offset(cx + r * 0.12f, y + r * 0.05f))
        drawCircle(Color(0xFFFFF1F2).copy(alpha = 0.45f), r * 0.14f, Offset(cx, y + r * 0.18f))
    }
    if (crown) {
        drawRoundRect(Color(0xFFFACC15), Offset(cx - r * 0.42f, y - r * 1.08f), Size(r * 0.84f, r * 0.22f), CornerRadius(2f, 2f))
        drawCircle(Color(0xFFFACC15), r * 0.12f, Offset(cx - r * 0.28f, y - r * 1.15f))
        drawCircle(Color(0xFFFACC15), r * 0.14f, Offset(cx, y - r * 1.22f))
        drawCircle(Color(0xFFFACC15), r * 0.12f, Offset(cx + r * 0.28f, y - r * 1.15f))
        drawLine(dark.copy(alpha = 0.45f), Offset(cx + r * 0.1f, y - r * 0.15f), Offset(cx + r * 0.4f, y + r * 0.15f), strokeWidth = r * 0.06f)
    }
    drawFace(cx, y, r, dir, angry = crown)
}

private fun DrawScope.drawBeetle(cx: Float, y: Float, r: Float, body: Color, dark: Color, dir: Float) {
    // legs
    for (i in 0..2) {
        val ly = y + r * (-0.15f + i * 0.22f)
        drawLine(dark, Offset(cx - r * 0.55f, ly), Offset(cx - r * 1.05f, ly + r * 0.12f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
        drawLine(dark, Offset(cx + r * 0.55f, ly), Offset(cx + r * 1.05f, ly + r * 0.12f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
    }
    // shell
    drawOval(dark, topLeft = Offset(cx - r * 0.85f, y - r * 0.55f), size = Size(r * 1.7f, r * 1.35f))
    drawOval(body, topLeft = Offset(cx - r * 0.75f, y - r * 0.5f), size = Size(r * 1.5f, r * 1.15f))
    // split line
    drawLine(dark, Offset(cx, y - r * 0.4f), Offset(cx, y + r * 0.45f), strokeWidth = r * 0.06f)
    // wing gloss
    drawOval(Color.White.copy(alpha = 0.2f), topLeft = Offset(cx - r * 0.55f, y - r * 0.35f), size = Size(r * 0.4f, r * 0.55f))
    drawOval(Color.White.copy(alpha = 0.2f), topLeft = Offset(cx + r * 0.15f, y - r * 0.35f), size = Size(r * 0.4f, r * 0.55f))
    // head
    drawCircle(body, r * 0.38f, Offset(cx + dir * r * 0.55f, y - r * 0.05f))
    // antenna
    drawLine(dark, Offset(cx + dir * r * 0.45f, y - r * 0.35f), Offset(cx + dir * r * 0.7f, y - r * 0.95f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
    drawLine(dark, Offset(cx + dir * r * 0.55f, y - r * 0.3f), Offset(cx + dir * r * 0.95f, y - r * 0.85f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
    drawCircle(dark, r * 0.08f, Offset(cx + dir * r * 0.7f, y - r * 0.95f))
    drawCircle(dark, r * 0.08f, Offset(cx + dir * r * 0.95f, y - r * 0.85f))
    // eyes
    drawCircle(Color(0xFF0F172A), r * 0.1f, Offset(cx + dir * r * 0.65f, y - r * 0.08f))
    drawCircle(Color.White, r * 0.04f, Offset(cx + dir * r * 0.68f, y - r * 0.11f))
}

private fun DrawScope.drawBat(cx: Float, y: Float, r: Float, body: Color, dark: Color, dir: Float) {
    // wings
    drawOval(body.copy(alpha = 0.85f), topLeft = Offset(cx - r * 1.55f, y - r * 0.45f), size = Size(r * 1.1f, r * 0.75f))
    drawOval(body.copy(alpha = 0.85f), topLeft = Offset(cx + r * 0.45f, y - r * 0.45f), size = Size(r * 1.1f, r * 0.75f))
    drawOval(dark.copy(alpha = 0.35f), topLeft = Offset(cx - r * 1.4f, y - r * 0.3f), size = Size(r * 0.7f, r * 0.4f))
    drawOval(dark.copy(alpha = 0.35f), topLeft = Offset(cx + r * 0.7f, y - r * 0.3f), size = Size(r * 0.7f, r * 0.4f))
    // body
    drawOval(body, topLeft = Offset(cx - r * 0.55f, y - r * 0.45f), size = Size(r * 1.1f, r * 1.1f))
    // ears
    drawCircle(body, r * 0.22f, Offset(cx - r * 0.35f, y - r * 0.55f))
    drawCircle(body, r * 0.22f, Offset(cx + r * 0.35f, y - r * 0.55f))
    drawCircle(Color(0xFFF9A8D4).copy(alpha = 0.5f), r * 0.1f, Offset(cx - r * 0.35f, y - r * 0.55f))
    drawCircle(Color(0xFFF9A8D4).copy(alpha = 0.5f), r * 0.1f, Offset(cx + r * 0.35f, y - r * 0.55f))
    // fangs
    drawLine(Color.White, Offset(cx - r * 0.1f, y + r * 0.15f), Offset(cx - r * 0.06f, y + r * 0.32f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(cx + r * 0.1f, y + r * 0.15f), Offset(cx + r * 0.06f, y + r * 0.32f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
    drawFace(cx, y, r, dir, eyeYOff = -0.08f, eyeDx = 0.22f, eyeR = 0.12f, mouthY = 0.12f)
}

private fun DrawScope.drawSkeleton(cx: Float, y: Float, r: Float, bone: Color, dark: Color, dir: Float) {
    // legs
    drawRoundRect(bone, Offset(cx - r * 0.35f, y + r * 0.25f), Size(r * 0.22f, r * 0.55f), CornerRadius(r * 0.08f, r * 0.08f))
    drawRoundRect(bone, Offset(cx + r * 0.12f, y + r * 0.25f), Size(r * 0.22f, r * 0.55f), CornerRadius(r * 0.08f, r * 0.08f))
    // ribs torso
    drawRoundRect(bone, Offset(cx - r * 0.45f, y - r * 0.25f), Size(r * 0.9f, r * 0.65f), CornerRadius(r * 0.15f, r * 0.15f))
    drawLine(dark, Offset(cx - r * 0.25f, y - r * 0.05f), Offset(cx + r * 0.25f, y - r * 0.05f), strokeWidth = r * 0.04f)
    drawLine(dark, Offset(cx - r * 0.25f, y + r * 0.12f), Offset(cx + r * 0.25f, y + r * 0.12f), strokeWidth = r * 0.04f)
    // arms + bone sword
    drawRoundRect(bone, Offset(cx - r * 0.75f, y - r * 0.1f), Size(r * 0.28f, r * 0.45f), CornerRadius(r * 0.1f, r * 0.1f))
    drawRoundRect(bone, Offset(cx + r * 0.48f, y - r * 0.1f), Size(r * 0.28f, r * 0.45f), CornerRadius(r * 0.1f, r * 0.1f))
    val sx = cx + dir * r * 0.85f
    drawLine(Color(0xFFCBD5E1), Offset(sx, y - r * 0.55f), Offset(sx, y + r * 0.45f), strokeWidth = r * 0.1f, cap = StrokeCap.Round)
    drawLine(Color(0xFF94A3B8), Offset(sx - r * 0.15f, y - r * 0.35f), Offset(sx + r * 0.15f, y - r * 0.35f), strokeWidth = r * 0.08f, cap = StrokeCap.Round)
    // skull
    drawCircle(bone, r * 0.52f, Offset(cx, y - r * 0.55f))
    drawCircle(Color(0xFF0F172A), r * 0.14f, Offset(cx - r * 0.18f + dir * r * 0.05f, y - r * 0.58f))
    drawCircle(Color(0xFF0F172A), r * 0.14f, Offset(cx + r * 0.18f + dir * r * 0.05f, y - r * 0.58f))
    drawCircle(Color(0xFFEF4444).copy(alpha = 0.7f), r * 0.05f, Offset(cx - r * 0.15f + dir * r * 0.05f, y - r * 0.58f))
    drawCircle(Color(0xFFEF4444).copy(alpha = 0.7f), r * 0.05f, Offset(cx + r * 0.21f + dir * r * 0.05f, y - r * 0.58f))
    // jaw
    drawRoundRect(bone, Offset(cx - r * 0.22f, y - r * 0.28f), Size(r * 0.44f, r * 0.16f), CornerRadius(2f, 2f))
    for (i in 0..3) {
        drawLine(dark, Offset(cx - r * 0.15f + i * r * 0.1f, y - r * 0.28f), Offset(cx - r * 0.15f + i * r * 0.1f, y - r * 0.14f), strokeWidth = r * 0.03f)
    }
}

private fun DrawScope.drawGoblin(cx: Float, y: Float, r: Float, skin: Color, dark: Color, dir: Float) {
    // legs
    drawOval(dark, topLeft = Offset(cx - r * 0.42f, y + r * 0.35f), size = Size(r * 0.32f, r * 0.4f))
    drawOval(dark, topLeft = Offset(cx + r * 0.1f, y + r * 0.35f), size = Size(r * 0.32f, r * 0.4f))
    // tunic
    drawRoundRect(Color(0xFF3F6212), Offset(cx - r * 0.5f, y - r * 0.15f), Size(r * 1.0f, r * 0.7f), CornerRadius(r * 0.2f, r * 0.2f))
    drawRoundRect(Color(0xFF713F12), Offset(cx - r * 0.45f, y + r * 0.28f), Size(r * 0.9f, r * 0.12f), CornerRadius(2f, 2f))
    // arms
    drawOval(skin, topLeft = Offset(cx - r * 0.72f, y + r * 0.0f), size = Size(r * 0.28f, r * 0.4f))
    drawOval(skin, topLeft = Offset(cx + r * 0.44f, y + r * 0.0f), size = Size(r * 0.28f, r * 0.4f))
    // club
    val hx = cx + dir * r * 0.85f
    drawLine(Color(0xFF78350F), Offset(hx, y - r * 0.1f), Offset(hx + dir * r * 0.15f, y + r * 0.55f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
    drawCircle(Color(0xFFA16207), r * 0.22f, Offset(hx + dir * r * 0.18f, y + r * 0.6f))
    // head
    drawCircle(skin, r * 0.55f, Offset(cx, y - r * 0.5f))
    // long ears
    drawOval(skin, topLeft = Offset(cx - r * 0.95f, y - r * 0.75f), size = Size(r * 0.4f, r * 0.28f))
    drawOval(skin, topLeft = Offset(cx + r * 0.55f, y - r * 0.75f), size = Size(r * 0.4f, r * 0.28f))
    // hair tuft
    drawCircle(Color(0xFF14532D), r * 0.2f, Offset(cx - r * 0.1f, y - r * 0.95f))
    drawFace(cx, y - r * 0.05f, r * 0.95f, dir, eyeYOff = -0.45f, angry = true)
}

private fun DrawScope.drawRat(cx: Float, y: Float, r: Float, fur: Color, dark: Color, dir: Float) {
    // tail
    drawLine(Color(0xFFF9A8D4), Offset(cx - dir * r * 0.4f, y + r * 0.25f), Offset(cx - dir * r * 1.3f, y + r * 0.55f), strokeWidth = r * 0.1f, cap = StrokeCap.Round)
    // body
    drawOval(fur, topLeft = Offset(cx - r * 0.85f, y - r * 0.35f), size = Size(r * 1.5f, r * 1.0f))
    // head
    drawOval(fur, topLeft = Offset(cx + dir * r * 0.15f - r * 0.35f, y - r * 0.55f), size = Size(r * 0.95f, r * 0.85f))
    // ears
    drawCircle(fur, r * 0.22f, Offset(cx + dir * r * 0.05f, y - r * 0.7f))
    drawCircle(fur, r * 0.22f, Offset(cx + dir * r * 0.45f, y - r * 0.65f))
    drawCircle(Color(0xFFF9A8D4), r * 0.1f, Offset(cx + dir * r * 0.05f, y - r * 0.7f))
    drawCircle(Color(0xFFF9A8D4), r * 0.1f, Offset(cx + dir * r * 0.45f, y - r * 0.65f))
    // snout
    drawOval(Color(0xFFFECACA), topLeft = Offset(cx + dir * r * 0.45f - r * 0.15f, y - r * 0.15f), size = Size(r * 0.45f, r * 0.3f))
    drawCircle(Color(0xFF0F172A), r * 0.06f, Offset(cx + dir * r * 0.7f, y - r * 0.05f))
    // eyes
    drawCircle(Color(0xFF0F172A), r * 0.1f, Offset(cx + dir * r * 0.2f, y - r * 0.25f))
    drawCircle(Color.White, r * 0.04f, Offset(cx + dir * r * 0.22f, y - r * 0.28f))
    // whiskers
    drawLine(dark, Offset(cx + dir * r * 0.5f, y), Offset(cx + dir * r * 0.95f, y - r * 0.12f), strokeWidth = r * 0.03f)
    drawLine(dark, Offset(cx + dir * r * 0.5f, y + r * 0.05f), Offset(cx + dir * r * 0.95f, y + r * 0.1f), strokeWidth = r * 0.03f)
}

private fun DrawScope.drawWisp(cx: Float, y: Float, r: Float, body: Color, dark: Color, dir: Float) {
    // glow aura
    drawCircle(body.copy(alpha = 0.22f), r * 1.35f, Offset(cx, y))
    drawCircle(body.copy(alpha = 0.35f), r * 0.95f, Offset(cx, y))
    // flame body
    drawOval(body, topLeft = Offset(cx - r * 0.55f, y - r * 0.7f), size = Size(r * 1.1f, r * 1.4f))
    drawOval(Color.White.copy(alpha = 0.45f), topLeft = Offset(cx - r * 0.28f, y - r * 0.45f), size = Size(r * 0.4f, r * 0.55f))
    // tail wisps
    drawOval(body.copy(alpha = 0.6f), topLeft = Offset(cx - r * 0.35f, y + r * 0.45f), size = Size(r * 0.25f, r * 0.4f))
    drawOval(body.copy(alpha = 0.5f), topLeft = Offset(cx + r * 0.1f, y + r * 0.5f), size = Size(r * 0.22f, r * 0.35f))
    // hollow eyes
    drawCircle(Color(0xFF0F172A), r * 0.14f, Offset(cx - r * 0.2f + dir * r * 0.05f, y - r * 0.15f))
    drawCircle(Color(0xFF0F172A), r * 0.14f, Offset(cx + r * 0.2f + dir * r * 0.05f, y - r * 0.15f))
    drawCircle(Color(0xFFE9D5FF), r * 0.05f, Offset(cx - r * 0.17f + dir * r * 0.05f, y - r * 0.18f))
    drawCircle(Color(0xFFE9D5FF), r * 0.05f, Offset(cx + r * 0.23f + dir * r * 0.05f, y - r * 0.18f))
    // spark ring
    drawCircle(Color.White.copy(alpha = 0.25f), r * 0.7f, Offset(cx, y), style = Stroke(r * 0.06f))
}

private fun DrawScope.drawOreBoss(cx: Float, y: Float, r: Float, ore: Color, dark: Color, dir: Float) {
    // rocky body chunks
    drawRoundRect(dark, Offset(cx - r * 0.85f, y - r * 0.35f), Size(r * 1.7f, r * 1.2f), CornerRadius(r * 0.25f, r * 0.25f))
    drawRoundRect(ore, Offset(cx - r * 0.75f, y - r * 0.45f), Size(r * 1.5f, r * 1.15f), CornerRadius(r * 0.22f, r * 0.22f))
    // crystal spikes
    for (i in -2..2) {
        val sx = cx + i * r * 0.28f
        drawLine(Color(0xFF67E8F9), Offset(sx, y - r * 0.35f), Offset(sx + i * r * 0.05f, y - r * 1.15f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFA5F3FC), r * 0.1f, Offset(sx + i * r * 0.05f, y - r * 1.15f))
    }
    // gold veins
    drawLine(Color(0xFFFDE68A), Offset(cx - r * 0.4f, y - r * 0.1f), Offset(cx + r * 0.35f, y + r * 0.35f), strokeWidth = r * 0.08f)
    drawLine(Color(0xFFFDE68A), Offset(cx + r * 0.2f, y - r * 0.2f), Offset(cx - r * 0.15f, y + r * 0.4f), strokeWidth = r * 0.06f)
    // face plate
    drawCircle(Color(0xFF1C1917), r * 0.42f, Offset(cx, y + r * 0.05f))
    drawCircle(Color(0xFFEF4444), r * 0.12f, Offset(cx - r * 0.15f + dir * r * 0.04f, y))
    drawCircle(Color(0xFFEF4444), r * 0.12f, Offset(cx + r * 0.15f + dir * r * 0.04f, y))
    drawCircle(Color.White, r * 0.04f, Offset(cx - r * 0.12f + dir * r * 0.04f, y - r * 0.03f))
    drawCircle(Color.White, r * 0.04f, Offset(cx + r * 0.18f + dir * r * 0.04f, y - r * 0.03f))
    // jaw grate
    drawRoundRect(Color(0xFF44403C), Offset(cx - r * 0.28f, y + r * 0.2f), Size(r * 0.56f, r * 0.18f), CornerRadius(2f, 2f))
    for (i in 0..3) {
        drawLine(Color(0xFF78716C), Offset(cx - r * 0.2f + i * r * 0.12f, y + r * 0.2f), Offset(cx - r * 0.2f + i * r * 0.12f, y + r * 0.36f), strokeWidth = r * 0.04f)
    }
}

fun DrawScope.drawCuteHeroMini(
    cx: Float,
    cy: Float,
    size: Float,
    skin: CharacterSkin
) {
    drawCuteHero(cx, cy, size, facing = 1f, skin = skin, bob = 0f)
}

/** Tiny decorative ground flecks for arena detail. */
fun DrawScope.drawGroundFlecks(camX: Float, camY: Float, w: Float, h: Float, t: Float) {
    val seed = 17
    var i = 0
    while (i < 40) {
        val gx = ((i * 97 + seed) % 200) / 200f * w * 1.4f - camX * 0.15f
        val gy = ((i * 53 + 31) % 200) / 200f * h * 1.2f - camY * 0.1f
        val xx = ((gx % w) + w) % w
        val yy = ((gy % h) + h) % h
        val a = 0.08f + ((i % 5) * 0.02f)
        drawCircle(Color.White.copy(alpha = a), 1.5f + (i % 3), Offset(xx, yy + sin(t + i) * 0.5f))
        i++
    }
}
