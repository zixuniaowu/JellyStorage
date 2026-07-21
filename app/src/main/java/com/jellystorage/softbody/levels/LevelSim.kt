package com.jellystorage.softbody.levels

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val STIFF = 38f
private const val EDGE_STIFF = 28f
private const val DAMP = 6.5f
private const val PRESSURE = 22f
private const val GRAVITY = 980f
private const val AIR = 0.998f
private const val MAX_PULL = 0.28f // of min play size
private const val LAUNCH_POWER = 7.2f

fun layoutPlayfield(state: LevelState, w: Float, h: Float) {
    state.screenW = w
    state.screenH = h
    val top = h * 0.12f
    val bottom = h * 0.90f
    val side = w * 0.04f
    state.play = Rect(side, top, w - side, bottom)
    state.unit = minOf(state.play.width, state.play.height) / 600f
}

private fun LevelState.px(nx: Float, ny: Float): Offset {
    val p = play
    return Offset(p.left + nx * p.width, p.top + ny * p.height)
}

private fun LevelState.wallRect(wn: WallN): Rect {
    val a = px(wn.left, wn.top)
    val b = px(wn.right, wn.bottom)
    return Rect(
        minOf(a.x, b.x), minOf(a.y, b.y),
        maxOf(a.x, b.x), maxOf(a.y, b.y)
    )
}

private fun LevelState.goalCenter(g: GoalN): Offset = px(g.cx, g.cy)
private fun LevelState.goalRadius(g: GoalN): Float =
    g.r * minOf(play.width, play.height)

fun currentLevel(): List<LevelDef> = allLevels()

fun startLevel(state: LevelState, index: Int) {
    val levels = allLevels()
    val i = index.coerceIn(0, levels.lastIndex)
    val def = levels[i]
    state.levelIndex = i
    state.phase = LevelPhase.PLAYING
    state.shotsLeft = def.maxShots
    state.shotsUsed = 0
    state.settledTimer = 0f
    state.inGoalTimer = 0f
    state.winStars = 0
    state.aiming = false
    state.launched = false
    state.canAim = true
    state.particles.clear()
    state.message = def.hint
    state.messageT = 3.5f
    state.squash = 0f

    val r = def.jellyRadius * minOf(state.play.width, state.play.height)
    state.bodyRadius = r
    state.bodyCenter = state.px(def.start.x, def.start.y)
    state.bodyVel = Offset.Zero
    rebuildSoftBody(state, r)
}

private fun rebuildSoftBody(state: LevelState, r: Float) {
    val n = 12
    val step = (2f * PI.toFloat()) / n
    val rel = List(n) { i ->
        val a = step * i
        Offset(cos(a) * r, sin(a) * r)
    }
    state.softRel = rel
    state.softVerts = rel.map { state.bodyCenter + it }
    state.softVels = List(n) { Offset.Zero }
    // rest area of regular polygon
    state.restArea = 0.5f * n * r * r * sin(step)
}

fun tryBeginAim(state: LevelState, pos: Offset): Boolean {
    if (state.phase != LevelPhase.PLAYING || !state.canAim || state.shotsLeft <= 0) return false
    val d = (pos - state.bodyCenter).getDistance()
    if (d > state.bodyRadius * 2.2f) return false
    // must be relatively still
    if (state.bodyVel.getDistance() > 90f) return false
    state.aiming = true
    state.aimFrom = state.bodyCenter
    state.aimTo = pos
    return true
}

fun updateAim(state: LevelState, pos: Offset) {
    if (!state.aiming) return
    state.aimTo = pos
}

fun releaseAim(state: LevelState): Boolean {
    if (!state.aiming) return false
    state.aiming = false
    val pull = state.aimFrom - state.aimTo // drag back → launch forward
    val maxPull = MAX_PULL * minOf(state.play.width, state.play.height)
    var dist = pull.getDistance()
    if (dist < 12f) return false // cancel
    if (dist > maxPull) {
        val s = maxPull / dist
        dist = maxPull
        val p = pull * s
        applyLaunch(state, p, dist, maxPull)
    } else {
        applyLaunch(state, pull, dist, maxPull)
    }
    return true
}

private fun applyLaunch(state: LevelState, pull: Offset, dist: Float, maxPull: Float) {
    val power = (dist / maxPull).coerceIn(0.15f, 1f) * LAUNCH_POWER
    val dir = pull / dist.coerceAtLeast(1f)
    // velocity in px/s scaled by play size
    val speed = power * minOf(state.play.width, state.play.height)
    state.bodyVel = dir * speed
    state.shotsLeft -= 1
    state.shotsUsed += 1
    state.launched = true
    state.canAim = false
    state.settledTimer = 0f
    state.squash = 0.35f
    // burst
    burst(state, state.bodyCenter, Color(0xFFFF6B8A), 10)
}

fun cancelAim(state: LevelState) {
    state.aiming = false
}

fun updateLevel(state: LevelState, dt: Float) {
    val raw = dt.coerceIn(0f, 0.05f)
    state.pulse += raw
    if (state.messageT > 0f) state.messageT -= raw
    if (state.squash > 0f) state.squash = (state.squash - raw * 2.5f).coerceAtLeast(0f)

    updateParticles(state, raw)

    if (state.phase != LevelPhase.PLAYING) return
    if (state.aiming) return // freeze while aiming for readability

    val def = allLevels()[state.levelIndex]
    val steps = 3
    val sdt = raw / steps
    repeat(steps) {
        integrateSoft(state, sdt)
        collideWalls(state, def, sdt)
        containPlay(state)
    }

    // Goal check
    val g = def.goal
    val gc = state.goalCenter(g)
    val gr = state.goalRadius(g)
    val inGoal = (state.bodyCenter - gc).getDistance() < gr * 0.85f
    if (inGoal) {
        state.inGoalTimer += raw
        if (state.inGoalTimer > 0.35f) {
            winLevel(state, def)
            return
        }
    } else {
        state.inGoalTimer = 0f
    }

    // Settled → can aim again
    val speed = state.bodyVel.getDistance()
    if (speed < 55f && !state.aiming) {
        state.settledTimer += raw
        if (state.settledTimer > 0.35f) {
            state.canAim = state.shotsLeft > 0
            if (state.shotsLeft <= 0 && !inGoal) {
                // give a moment then lose
                if (state.settledTimer > 1.0f) loseLevel(state, "次数用完了")
            }
        }
    } else {
        state.settledTimer = 0f
        if (speed > 80f) state.canAim = false
    }

    // Fall out of play (below)
    if (state.bodyCenter.y > state.play.bottom + state.bodyRadius * 2f) {
        loseLevel(state, "掉下去了！")
    }
}

private fun winLevel(state: LevelState, def: LevelDef) {
    state.phase = LevelPhase.WIN
    state.aiming = false
    state.winStars = starsFor(state.shotsUsed, def.par, def.maxShots)
    val prev = state.stars[def.id] ?: 0
    if (state.winStars > prev) state.stars[def.id] = state.winStars
    // unlock next
    val nextId = def.id + 1
    if (nextId > state.unlocked && nextId <= allLevels().size) {
        state.unlocked = nextId
    }
    burst(state, state.bodyCenter, Color(0xFFFFD700), 28)
    burst(state, state.goalCenter(def.goal), Color(0xFF4ECDC4), 16)
    state.message = "过关！"
    state.messageT = 2f
}

private fun loseLevel(state: LevelState, reason: String) {
    if (state.phase != LevelPhase.PLAYING) return
    state.phase = LevelPhase.LOSE
    state.aiming = false
    state.message = reason
    state.messageT = 2f
    burst(state, state.bodyCenter, Color(0xFFEF4444), 14)
}

private fun integrateSoft(state: LevelState, dt: Float) {
    val n = state.softVerts.size
    if (n < 3) return
    val c = state.bodyCenter
    val forces = MutableList(n) { Offset.Zero }

    // radial springs
    for (i in 0 until n) {
        val p = state.softVerts[i]
        val rel0 = state.softRel[i]
        val diff = p - c
        val dist = diff.getDistance().coerceAtLeast(1e-3f)
        val rest = rel0.getDistance()
        val dir = diff / dist
        forces[i] = forces[i] - dir * STIFF * (dist - rest)
    }
    // edge springs
    for (i in 0 until n) {
        val j = (i + 1) % n
        val pi = state.softVerts[i]
        val pj = state.softVerts[j]
        val diff = pi - pj
        val dist = diff.getDistance().coerceAtLeast(1e-3f)
        val rest = (state.softRel[i] - state.softRel[j]).getDistance()
        val dir = diff / dist
        val f = dir * EDGE_STIFF * (dist - rest)
        forces[i] = forces[i] - f
        forces[j] = forces[j] + f
    }
    // damping + pressure
    val area = polygonArea(state.softVerts)
    val pMag = if (state.restArea > 1f && area > 1f) PRESSURE * (1f - area / state.restArea) else 0f
    for (i in 0 until n) {
        forces[i] = forces[i] - state.softVels[i] * DAMP
        val diff = state.softVerts[i] - c
        val dist = diff.getDistance().coerceAtLeast(1e-3f)
        forces[i] = forces[i] + (diff / dist) * pMag
    }

    val newVels = MutableList(n) { Offset.Zero }
    val newVerts = MutableList(n) { Offset.Zero }
    for (i in 0 until n) {
        var v = (state.softVels[i] + forces[i] * dt) * 0.995f
        var p = state.softVerts[i] + v * dt
        newVels[i] = v
        newVerts[i] = p
    }
    state.softVels = newVels
    state.softVerts = newVerts

    // center motion
    var vel = state.bodyVel
    vel = Offset(vel.x * AIR, vel.y * AIR + GRAVITY * dt)
    var center = state.bodyCenter + vel * dt
    // move verts with center delta
    val dC = center - state.bodyCenter
    state.softVerts = state.softVerts.map { it + dC }
    state.bodyCenter = center
    state.bodyVel = vel
}

private fun collideWalls(state: LevelState, def: LevelDef, dt: Float) {
    val r = state.bodyRadius
    var c = state.bodyCenter
    var v = state.bodyVel
    for (wn in def.walls) {
        val rect = state.wallRect(wn)
        // expand rect by radius → circle vs AABB
        val nearest = Offset(
            c.x.coerceIn(rect.left, rect.right),
            c.y.coerceIn(rect.top, rect.bottom)
        )
        val delta = c - nearest
        val dist = delta.getDistance()
        if (dist < r && dist > 1e-4f) {
            val n = delta / dist
            val pen = r - dist
            c += n * pen
            val vn = v.x * n.x + v.y * n.y
            if (vn < 0f) {
                v -= n * vn * (1f + wn.bounce)
                if (abs(vn) > 120f) {
                    state.squash = 0.5f
                    if (Random.nextFloat() < 0.3f) {
                        burst(state, c, Color(0x88FFFFFF), 4)
                    }
                }
            }
        } else if (dist <= 1e-4f && rect.contains(c)) {
            // deep inside: push out via min axis
            val dl = c.x - rect.left
            val dr = rect.right - c.x
            val dt2 = c.y - rect.top
            val db = rect.bottom - c.y
            val m = minOf(dl, dr, dt2, db)
            when (m) {
                dl -> { c = Offset(rect.left - r, c.y); if (v.x > 0) v = v.copy(x = -v.x * wn.bounce) }
                dr -> { c = Offset(rect.right + r, c.y); if (v.x < 0) v = v.copy(x = -v.x * wn.bounce) }
                dt2 -> { c = Offset(c.x, rect.top - r); if (v.y > 0) v = v.copy(y = -v.y * wn.bounce) }
                else -> { c = Offset(c.x, rect.bottom + r); if (v.y < 0) v = v.copy(y = -v.y * wn.bounce) }
            }
        }
    }
    val dC = c - state.bodyCenter
    if (dC != Offset.Zero) {
        state.softVerts = state.softVerts.map { it + dC }
        state.bodyCenter = c
    }
    state.bodyVel = v
}

private fun containPlay(state: LevelState) {
    val p = state.play
    val r = state.bodyRadius * 0.9f
    var c = state.bodyCenter
    var v = state.bodyVel
    var hit = false
    if (c.x < p.left + r) { c = c.copy(x = p.left + r); v = v.copy(x = abs(v.x) * 0.4f); hit = true }
    if (c.x > p.right - r) { c = c.copy(x = p.right - r); v = v.copy(x = -abs(v.x) * 0.4f); hit = true }
    if (c.y < p.top + r) { c = c.copy(y = p.top + r); v = v.copy(y = abs(v.y) * 0.4f); hit = true }
    // bottom: only soft containment if floor walls missing — allow fall for cliff levels
    if (c.y > p.bottom + r * 3f) {
        // free fall handled by lose
    }
    if (hit) {
        val dC = c - state.bodyCenter
        state.softVerts = state.softVerts.map { it + dC }
        state.bodyCenter = c
        state.bodyVel = v
    }
}

private fun polygonArea(verts: List<Offset>): Float {
    var a = 0f
    val n = verts.size
    for (i in 0 until n) {
        val j = (i + 1) % n
        a += verts[i].x * verts[j].y - verts[j].x * verts[i].y
    }
    return abs(a) * 0.5f
}

private fun burst(state: LevelState, at: Offset, color: Color, n: Int) {
    repeat(n) {
        val a = Random.nextFloat() * 2f * PI.toFloat()
        val s = Random.nextFloat() * 280f + 80f
        state.particles.add(
            Particle(
                at.x, at.y,
                cos(a) * s, sin(a) * s - 40f,
                life = Random.nextFloat() * 0.4f + 0.3f,
                maxLife = 0.8f,
                size = Random.nextFloat() * 8f + 3f,
                color = color
            )
        )
    }
}

private fun updateParticles(state: LevelState, dt: Float) {
    var i = 0
    while (i < state.particles.size) {
        val p = state.particles[i]
        p.life -= dt
        p.vy += p.gravity * dt
        p.x += p.vx * dt
        p.y += p.vy * dt
        if (p.life <= 0f) state.particles.removeAt(i) else i++
    }
}

/** Predicted trajectory points for aim UI (simple ballistic, no soft-body). */
fun aimTrajectory(state: LevelState, steps: Int = 16): List<Offset> {
    if (!state.aiming) return emptyList()
    val pull = state.aimFrom - state.aimTo
    val maxPull = MAX_PULL * minOf(state.play.width, state.play.height)
    var dist = pull.getDistance()
    if (dist < 8f) return emptyList()
    val clamped = if (dist > maxPull) pull * (maxPull / dist) else pull
    dist = clamped.getDistance()
    val power = (dist / maxPull).coerceIn(0.15f, 1f) * LAUNCH_POWER
    val dir = clamped / dist
    val speed = power * minOf(state.play.width, state.play.height)
    var pos = state.bodyCenter
    var vel = dir * speed
    val dt = 0.045f
    val pts = mutableListOf<Offset>()
    repeat(steps) {
        vel = Offset(vel.x * AIR, vel.y * AIR + GRAVITY * dt)
        pos += vel * dt
        pts.add(pos)
    }
    return pts
}

fun launchImpulseVector(state: LevelState): Offset {
    if (!state.aiming) return Offset.Zero
    val pull = state.aimFrom - state.aimTo
    val maxPull = MAX_PULL * minOf(state.play.width, state.play.height)
    var dist = pull.getDistance()
    if (dist < 1f) return Offset.Zero
    val p = if (dist > maxPull) pull * (maxPull / dist) else pull
    return p
}
