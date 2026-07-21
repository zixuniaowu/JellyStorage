package com.jellystorage.ui.canvas

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.jellystorage.engine.battle.BattleStatus
import com.jellystorage.engine.battle.BattleViewState
import com.jellystorage.engine.battle.ProjectileType
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * High-performance arena renderer.
 *
 * Alloc-free frame rules:
 * - Single pre-allocated [Path] per jelly, [rewind] before rebuild
 * - Outer contour uses midpoint quadratic Beziers ([quadTo])
 * - No `Path()` / Compose `Offset()` construction inside the draw lambda
 * - Circles/text via reused [Paint] + nativeCanvas primitive draws
 */
@Composable
fun JellyArenaCanvas(
    state: BattleViewState,
    modifier: Modifier = Modifier,
    backgroundTop: ComposeColor = ComposeColor(0xFF0F172A),
    backgroundBottom: ComposeColor = ComposeColor(0xFF1E1B4B),
    playerRim: ComposeColor = ComposeColor(0xFFFF6B8A),
    playerFill: ComposeColor = ComposeColor(0xFFFFD0DA),
    enemyRim: ComposeColor = ComposeColor(0xFF4ADE80),
    enemyFill: ComposeColor = ComposeColor(0xFFBBF7D0)
) {
    val playerPath = remember { Path() }
    val enemyPath = remember { Path() }
    val fillPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    }
    val strokePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }
    val projPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 36f
            isFakeBoldText = true
        }
    }
    val hpBackPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF334155.toInt()
        }
    }
    val hpPlayerPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF4ADE80.toInt()
        }
    }
    val hpEnemyPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFB7185.toInt()
        }
    }
    val groundPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0x55FFFFFF
        }
    }
    val bgTopArgb = backgroundTop.toArgb()
    val bgBotArgb = backgroundBottom.toArgb()
    val playerRimArgb = playerRim.toArgb()
    val playerFillArgb = playerFill.toArgb()
    val enemyRimArgb = enemyRim.toArgb()
    val enemyFillArgb = enemyFill.toArgb()

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas

            // Background gradient (two rects — no shader alloc each frame if we use solid blend)
            fillPaint.color = bgTopArgb
            nc.drawRect(0f, 0f, w, h * 0.55f, fillPaint)
            fillPaint.color = bgBotArgb
            nc.drawRect(0f, h * 0.45f, w, h, fillPaint)

            // Ground line under fighters
            val groundY = (state.playerY + state.playerRadius).coerceAtMost(h * 0.85f)
            nc.drawLine(0f, groundY, w, groundY, groundPaint)

            // HP bars
            val barW = w * 0.40f
            val barH = 14f
            val barY = h * 0.06f
            nc.drawRoundRect(w * 0.05f, barY, w * 0.05f + barW, barY + barH, 8f, 8f, hpBackPaint)
            nc.drawRoundRect(w * 0.55f, barY, w * 0.55f + barW, barY + barH, 8f, 8f, hpBackPaint)
            val pFrac = (state.playerHp / state.playerMaxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val eFrac = (state.enemyHp / state.enemyMaxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
            nc.drawRoundRect(
                w * 0.05f, barY,
                w * 0.05f + barW * pFrac, barY + barH,
                8f, 8f, hpPlayerPaint
            )
            nc.drawRoundRect(
                w * 0.55f, barY,
                w * 0.55f + barW * eFrac, barY + barH,
                8f, 8f, hpEnemyPaint
            )

            textPaint.textSize = 28f
            textPaint.color = 0xFFFFFFFF.toInt()
            nc.drawText(
                "HP ${state.playerHp.toInt()}/${state.playerMaxHp.toInt()}",
                w * 0.25f,
                barY - 10f,
                textPaint
            )
            nc.drawText(
                "HP ${state.enemyHp.toInt()}/${state.enemyMaxHp.toInt()}",
                w * 0.75f,
                barY - 10f,
                textPaint
            )

            // Status banner
            textPaint.textSize = 40f
            textPaint.color = 0xFFFFD700.toInt()
            when (state.status) {
                BattleStatus.PREPARING -> {
                    textPaint.color = 0xFFFFFFFF.toInt()
                    nc.drawText("GET READY", w * 0.5f, h * 0.22f, textPaint)
                }
                BattleStatus.VICTORY -> {
                    textPaint.color = 0xFF4ADE80.toInt()
                    nc.drawText("VICTORY", w * 0.5f, h * 0.22f, textPaint)
                }
                BattleStatus.DEFEAT -> {
                    textPaint.color = 0xFFFB7185.toInt()
                    nc.drawText("DEFEAT", w * 0.5f, h * 0.22f, textPaint)
                }
                BattleStatus.FIGHTING -> {
                    if (state.comboCount >= 3) {
                        textPaint.color = 0xFFFF4500.toInt()
                        nc.drawText("COMBO x${state.comboCount}", w * 0.5f, h * 0.18f, textPaint)
                    }
                }
            }

            // Jellies — reused paths + quadratic midpoints
            buildSmoothJellyPath(
                path = playerPath,
                outerX = state.playerOuterX,
                outerY = state.playerOuterY,
                count = state.playerOuterCount
            )
            fillPaint.color = playerFillArgb
            nc.drawPath(playerPath, fillPaint)
            strokePaint.color = playerRimArgb
            strokePaint.strokeWidth = 5f
            nc.drawPath(playerPath, strokePaint)
            drawEyes(nc, fillPaint, state.playerX, state.playerY, state.playerRadius)

            buildSmoothJellyPath(
                path = enemyPath,
                outerX = state.enemyOuterX,
                outerY = state.enemyOuterY,
                count = state.enemyOuterCount
            )
            fillPaint.color = enemyFillArgb
            nc.drawPath(enemyPath, fillPaint)
            strokePaint.color = enemyRimArgb
            nc.drawPath(enemyPath, strokePaint)
            drawEyes(nc, fillPaint, state.enemyX, state.enemyY, state.enemyRadius)

            // Projectiles — primitive iteration, no Offset wrappers
            val pc = state.projectileCount
            var i = 0
            while (i < pc) {
                projPaint.color = projectileColor(state.projectileType[i])
                val pr = state.projectileRadius[i]
                val px = state.projectileX[i]
                val py = state.projectileY[i]
                nc.drawCircle(px, py, pr, projPaint)
                // trail hint
                projPaint.alpha = 90
                nc.drawCircle(
                    px - state.projectileVx[i] * 0.012f,
                    py - state.projectileVy[i] * 0.012f,
                    pr * 0.65f,
                    projPaint
                )
                projPaint.alpha = 255
                i++
            }

            // Floating combat text
            val fc = state.floatCount
            i = 0
            while (i < fc) {
                val life = state.floatLife[i]
                val maxL = state.floatMaxLife[i].coerceAtLeast(0.001f)
                val a = (life / maxL).coerceIn(0f, 1f)
                val alpha = (a * 255f).toInt().coerceIn(0, 255)
                val crit = state.floatIsCrit[i]
                val combo = state.floatIsCombo[i]
                val value = state.floatValue[i]
                // K.O. only when both crit+combo flags set with non-positive value (victory/defeat only)
                val isKo = crit && combo && value <= 0.01f
                if (isKo) {
                    textPaint.textSize = 48f
                    textPaint.color = argb(alpha, 255, 215, 0)
                    nc.drawText("K.O.", state.floatX[i], state.floatY[i], textPaint)
                } else if (value < 0f) {
                    // Non-damage banner (e.g. fight start) — never render as K.O.
                    textPaint.textSize = 40f
                    textPaint.color = argb(alpha, 255, 255, 255)
                    nc.drawText("FIGHT!", state.floatX[i], state.floatY[i], textPaint)
                } else {
                    textPaint.textSize = if (crit) 44f else 32f
                    textPaint.color = if (crit) {
                        argb(alpha, 255, 215, 0)
                    } else {
                        argb(alpha, 255, 255, 255)
                    }
                    val label = if (crit) {
                        "${value.toInt()}!"
                    } else {
                        "${value.toInt()}"
                    }
                    nc.drawText(label, state.floatX[i], state.floatY[i], textPaint)
                }
                i++
            }

            // Synergy strip
            if (state.activeSynergies.isNotEmpty()) {
                textPaint.textSize = 26f
                textPaint.color = argb(220, 255, 215, 0)
                val names = buildSynergyLabel(state)
                nc.drawText(names, w * 0.5f, h * 0.12f, textPaint)
            }
        }
    }
}

/**
 * Rebuild [path] as a closed smooth jelly contour using midpoint quadratic Beziers.
 * Path is rewound — never reallocated.
 */
internal fun buildSmoothJellyPath(
    path: Path,
    outerX: FloatArray,
    outerY: FloatArray,
    count: Int
) {
    path.rewind()
    if (count < 3) return

    // Midpoint between last and first as start
    val last = count - 1
    var midX = (outerX[last] + outerX[0]) * 0.5f
    var midY = (outerY[last] + outerY[0]) * 0.5f
    path.moveTo(midX, midY)

    var i = 0
    while (i < count) {
        val next = if (i + 1 < count) i + 1 else 0
        val cx = outerX[i]
        val cy = outerY[i]
        midX = (outerX[i] + outerX[next]) * 0.5f
        midY = (outerY[i] + outerY[next]) * 0.5f
        path.quadTo(cx, cy, midX, midY)
        i++
    }
    path.close()
}

private fun drawEyes(
    nc: android.graphics.Canvas,
    paint: Paint,
    cx: Float,
    cy: Float,
    radius: Float
) {
    paint.color = argb(255, 15, 23, 42)
    val eyeY = cy - radius * 0.12f
    val eyeR = radius * 0.11f
    nc.drawCircle(cx - radius * 0.22f, eyeY, eyeR, paint)
    nc.drawCircle(cx + radius * 0.22f, eyeY, eyeR, paint)
    paint.color = argb(255, 255, 255, 255)
    nc.drawCircle(cx - radius * 0.18f, eyeY - radius * 0.03f, eyeR * 0.35f, paint)
    nc.drawCircle(cx + radius * 0.26f, eyeY - radius * 0.03f, eyeR * 0.35f, paint)
}

private fun projectileColor(typeOrdinal: Int): Int {
    return when (typeOrdinal) {
        ProjectileType.FIRE.ordinal -> argb(255, 255, 107, 53)
        ProjectileType.ICE.ordinal -> argb(255, 103, 232, 249)
        ProjectileType.THUNDER.ordinal -> argb(255, 250, 204, 21)
        ProjectileType.POISON.ordinal -> argb(255, 74, 222, 128)
        ProjectileType.STEAM.ordinal -> argb(230, 224, 242, 254)
        ProjectileType.HEAVY.ordinal -> argb(255, 168, 162, 158)
        else -> argb(255, 255, 255, 255)
    }
}

private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
    return (a and 0xFF shl 24) or (r and 0xFF shl 16) or (g and 0xFF shl 8) or (b and 0xFF)
}

/**
 * Builds synergy label without per-frame StringBuilder growth explosion:
 * uses a small local builder once per frame (acceptable; not Path/Offset).
 */
private fun buildSynergyLabel(state: BattleViewState): String {
    val list = state.activeSynergies
    if (list.isEmpty()) return ""
    val sb = StringBuilder(64)
    var i = 0
    val n = list.size.coerceAtMost(4)
    while (i < n) {
        if (i > 0) sb.append(" · ")
        sb.append(list[i].name)
        i++
    }
    return sb.toString()
}
