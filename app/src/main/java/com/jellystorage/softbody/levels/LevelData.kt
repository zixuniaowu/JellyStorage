package com.jellystorage.softbody.levels

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

enum class LevelPhase { MENU, SELECT, PLAYING, WIN, LOSE }

/** Axis-aligned solid in normalized playfield coords (0..1). */
data class WallN(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val bounce: Float = 0.55f,
    val color: Color = Color(0xFF334155)
)

data class GoalN(
    val cx: Float,
    val cy: Float,
    val r: Float
)

data class LevelDef(
    val id: Int,
    val title: String,
    val hint: String,
    val start: Offset, // normalized
    val goal: GoalN,
    val walls: List<WallN> = emptyList(),
    val maxShots: Int = 5,
    val par: Int = 2, // shots for 3 stars
    val jellyRadius: Float = 0.055f // of min(w,h)
)

/**
 * Hand-authored levels — short, readable, increasing skill.
 * Coords are fractions of the play rectangle (not full screen).
 */
fun allLevels(): List<LevelDef> = listOf(
    LevelDef(
        id = 1,
        title = "第一弹",
        hint = "按住果冻向后拉，松手发射进圈",
        start = Offset(0.18f, 0.72f),
        goal = GoalN(0.78f, 0.72f, 0.09f),
        walls = listOf(
            WallN(0f, 0.88f, 1f, 1f, bounce = 0.25f, color = Color(0xFF3F2E22)) // floor
        ),
        maxShots = 4,
        par = 1
    ),
    LevelDef(
        id = 2,
        title = "过山丘",
        hint = "拉高一点，飞过土坡",
        start = Offset(0.14f, 0.75f),
        goal = GoalN(0.82f, 0.70f, 0.085f),
        walls = listOf(
            WallN(0f, 0.88f, 1f, 1f, 0.25f, Color(0xFF3F2E22)),
            WallN(0.38f, 0.62f, 0.58f, 0.88f, 0.4f, Color(0xFF475569))
        ),
        maxShots = 5,
        par = 2
    ),
    LevelDef(
        id = 3,
        title = "窄门",
        hint = "对准中间的缝",
        start = Offset(0.16f, 0.55f),
        goal = GoalN(0.84f, 0.55f, 0.08f),
        walls = listOf(
            WallN(0f, 0.88f, 1f, 1f, 0.25f, Color(0xFF3F2E22)),
            WallN(0.42f, 0.20f, 0.52f, 0.48f, 0.5f, Color(0xFF64748B)),
            WallN(0.42f, 0.62f, 0.52f, 0.88f, 0.5f, Color(0xFF64748B))
        ),
        maxShots = 5,
        par = 2
    ),
    LevelDef(
        id = 4,
        title = "上高台",
        hint = "借力弹上右边平台",
        start = Offset(0.15f, 0.78f),
        goal = GoalN(0.78f, 0.38f, 0.08f),
        walls = listOf(
            WallN(0f, 0.88f, 1f, 1f, 0.25f, Color(0xFF3F2E22)),
            WallN(0.55f, 0.48f, 0.95f, 0.56f, 0.45f, Color(0xFF475569)),
            WallN(0.30f, 0.65f, 0.42f, 0.88f, 0.55f, Color(0xFF64748B))
        ),
        maxShots = 6,
        par = 2
    ),
    LevelDef(
        id = 5,
        title = "弹床",
        hint = "踩高弹地板，飞得更高",
        start = Offset(0.18f, 0.70f),
        goal = GoalN(0.80f, 0.28f, 0.08f),
        walls = listOf(
            WallN(0f, 0.88f, 0.45f, 1f, 0.95f, Color(0xFF22C55E)), // bouncy
            WallN(0.45f, 0.88f, 1f, 1f, 0.25f, Color(0xFF3F2E22)),
            WallN(0.60f, 0.40f, 0.95f, 0.48f, 0.4f, Color(0xFF475569))
        ),
        maxShots = 6,
        par = 2
    ),
    LevelDef(
        id = 6,
        title = "迷宫一角",
        hint = "先弹左边再拐进去",
        start = Offset(0.15f, 0.75f),
        goal = GoalN(0.82f, 0.55f, 0.075f),
        walls = listOf(
            WallN(0f, 0.88f, 1f, 1f, 0.25f, Color(0xFF3F2E22)),
            WallN(0.35f, 0.35f, 0.45f, 0.88f, 0.5f, Color(0xFF64748B)),
            WallN(0.45f, 0.35f, 0.78f, 0.45f, 0.5f, Color(0xFF64748B)),
            WallN(0.68f, 0.45f, 0.78f, 0.70f, 0.5f, Color(0xFF64748B))
        ),
        maxShots = 7,
        par = 3
    ),
    LevelDef(
        id = 7,
        title = "悬崖",
        hint = "别掉出屏幕下方",
        start = Offset(0.18f, 0.40f),
        goal = GoalN(0.78f, 0.72f, 0.08f),
        walls = listOf(
            WallN(0.05f, 0.50f, 0.35f, 0.58f, 0.45f, Color(0xFF475569)),
            WallN(0.55f, 0.80f, 0.95f, 0.88f, 0.3f, Color(0xFF3F2E22))
        ),
        maxShots = 6,
        par = 2,
        jellyRadius = 0.05f
    ),
    LevelDef(
        id = 8,
        title = "终极缝",
        hint = "精准一击",
        start = Offset(0.14f, 0.72f),
        goal = GoalN(0.86f, 0.35f, 0.07f),
        walls = listOf(
            WallN(0f, 0.88f, 1f, 1f, 0.25f, Color(0xFF3F2E22)),
            WallN(0.40f, 0.15f, 0.50f, 0.55f, 0.5f, Color(0xFF64748B)),
            WallN(0.50f, 0.55f, 0.72f, 0.65f, 0.5f, Color(0xFF64748B)),
            WallN(0.62f, 0.20f, 0.95f, 0.28f, 0.4f, Color(0xFF475569))
        ),
        maxShots = 7,
        par = 3
    )
)

data class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float,
    val maxLife: Float,
    var size: Float,
    val color: Color,
    val gravity: Float = 400f
)

data class LevelState(
    var phase: LevelPhase = LevelPhase.MENU,
    var levelIndex: Int = 0,
    var unlocked: Int = 1, // highest unlocked level id
    var stars: MutableMap<Int, Int> = mutableMapOf(), // levelId -> 0..3
    var bodyCenter: Offset = Offset.Zero,
    var bodyVel: Offset = Offset.Zero,
    var bodyRadius: Float = 48f,
    var softVerts: List<Offset> = emptyList(), // absolute positions for draw
    var softRel: List<Offset> = emptyList(),
    var softVels: List<Offset> = emptyList(),
    var restArea: Float = 1f,
    var aiming: Boolean = false,
    var aimFrom: Offset = Offset.Zero,
    var aimTo: Offset = Offset.Zero,
    var shotsLeft: Int = 0,
    var shotsUsed: Int = 0,
    var settledTimer: Float = 0f,
    var inGoalTimer: Float = 0f,
    var winStars: Int = 0,
    var particles: MutableList<Particle> = mutableListOf(),
    var message: String = "",
    var messageT: Float = 0f,
    var play: Rect = Rect.Zero, // absolute playfield
    var unit: Float = 1f,
    var screenW: Float = 1080f,
    var screenH: Float = 1920f,
    var launched: Boolean = false,
    var canAim: Boolean = true,
    var squash: Float = 0f, // visual juice 0..1
    var pulse: Float = 0f
)

fun starsFor(shotsUsed: Int, par: Int, maxShots: Int): Int {
    if (shotsUsed <= 0) return 1
    return when {
        shotsUsed <= par -> 3
        shotsUsed <= par + 1 -> 2
        shotsUsed <= maxShots -> 1
        else -> 1
    }
}
