package com.jellystorage.play

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class BossMove(val title: String) {
    SYRUP_METEORS("脓液坠落"),
    CORE_RING("炎症环爆"),
    MINECART_RIFT("气流裂道"),
    CRYSTAL_CROSS("缺氧十字"),
    ROYAL_SEAL("胃酸喷涌"),
    EMPTY_ECHO("菌群回响"),
    TIDAL_LANES("胆汁潮线"),
    SEA_VORTEX("毒素涡流"),
    FIVE_STROKES("五向血流"),
    FINAL_SIGNATURE("心室重压")
}

/** One chapter-final identity, independent from the two reusable enemy body sprites. */
enum class BossEncounter(
    val stageId: Int,
    val title: String,
    val phaseOne: String,
    val phaseTwo: String,
    val moveA: BossMove,
    val moveB: BossMove,
    val accent: Long
) {
    PRAIRIE(
        1, "创口感染核心", "脓液增殖", "抗原暴露",
        BossMove.SYRUP_METEORS, BossMove.CORE_RING, 0xFFC084FC
    ),
    MINE(
        2, "肺部感染核心", "气道痉挛", "缺氧过载",
        BossMove.MINECART_RIFT, BossMove.CRYSTAL_CROSS, 0xFFF59E0B
    ),
    KING(
        3, "肠道感染核心", "胃酸失衡", "菌群暴走",
        BossMove.ROYAL_SEAL, BossMove.EMPTY_ECHO, 0xFFE11D48
    ),
    SEA(
        4, "肝部感染核心", "胆汁逆流", "毒素倒灌",
        BossMove.TIDAL_LANES, BossMove.SEA_VORTEX, 0xFF0284C7
    ),
    SOUL(
        5, "循环变异母体", "血流汇聚", "心室超压",
        BossMove.FIVE_STROKES, BossMove.FINAL_SIGNATURE, 0xFF7C3AED
    )
}

fun bossEncounterForStage(stageId: Int): BossEncounter? =
    BossEncounter.entries.firstOrNull { it.stageId == stageId }

enum class BossHazardShape { CIRCLE, RING, LINE }

/**
 * A delayed, fully readable boss attack. `life` is the remaining warning window;
 * the hit is resolved once at zero and the object is then removed.
 */
data class BossHazard(
    val shape: BossHazardShape,
    val x0: Float,
    val y0: Float,
    val x1: Float = x0,
    val y1: Float = y0,
    val outerRadius: Float = 0f,
    val innerRadius: Float = 0f,
    val width: Float = 0f,
    var life: Float,
    val maxLife: Float = life,
    val damage: Float,
    val color: Long,
    val label: String = "",
    val slowOnHit: Boolean = false
) {
    fun warningProgress(): Float = (1f - life / maxLife.coerceAtLeast(0.001f)).coerceIn(0f, 1f)

    fun contains(px: Float, py: Float, playerRadius: Float = 0f): Boolean = when (shape) {
        BossHazardShape.CIRCLE -> distance(px, py, x0, y0) <= outerRadius + playerRadius
        BossHazardShape.RING -> {
            val d = distance(px, py, x0, y0)
            d >= max(0f, innerRadius - playerRadius) && d <= outerRadius + playerRadius
        }
        BossHazardShape.LINE ->
            pointSegmentDistance(px, py, x0, y0, x1, y1) <= width * 0.5f + playerRadius
    }
}

private fun distance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x1 - x0
    val dy = y1 - y0
    return sqrt(dx * dx + dy * dy)
}

internal fun pointSegmentDistance(
    px: Float,
    py: Float,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float
): Float {
    val dx = x1 - x0
    val dy = y1 - y0
    val lenSq = dx * dx + dy * dy
    if (lenSq <= 0.0001f) return distance(px, py, x0, y0)
    val t = ((px - x0) * dx + (py - y0) * dy) / lenSq
    val clamped = min(1f, max(0f, t))
    return distance(px, py, x0 + dx * clamped, y0 + dy * clamped)
}
