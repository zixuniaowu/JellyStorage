package com.jellystorage.softbody.storage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.jellystorage.softbody.PhysicsParams
import com.jellystorage.softbody.SoftBodyObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private fun kindForWave(wave: Int): JellyKind {
    val pool = buildList {
        add(JellyKind.NORMAL)
        if (wave >= 1) add(JellyKind.TINY)
        if (wave >= 2) add(JellyKind.STICKY)
        if (wave >= 3) add(JellyKind.BOUNCY)
        if (wave >= 4) add(JellyKind.HEAVY)
        if (wave >= 6) {
            add(JellyKind.HEAVY)
            add(JellyKind.STICKY)
        }
    }
    val total = pool.sumOf { it.spawnWeight.toDouble() }.toFloat()
    var r = Random.nextFloat() * total
    for (k in pool) {
        r -= k.spawnWeight
        if (r <= 0f) return k
    }
    return JellyKind.NORMAL
}

fun waveGoalFor(wave: Int): Int = (7 + wave * 2).coerceAtMost(24)

fun waveTimeFor(wave: Int): Float = (52f - wave * 1.5f).coerceIn(28f, 55f)

fun chaosCapFor(wave: Int, mods: RunMods): Int =
    (mods.chaosCap + 2 + wave / 3).coerceAtMost(20)

fun layoutSlots(state: StorageState, w: Float, h: Float) {
    val margin = w * 0.06f
    val usable = w - margin * 2f
    val kinds = listOf(SlotKind.WIDE, SlotKind.ROUND, SlotKind.NARROW)
    // Desk floor + bin mouths must overlap so jellies can actually enter.
    state.deskW = w
    state.deskH = h
    // Design was tuned ~1080px wide; keep jellies readable on xxhdpi/xxxhdpi.
    state.unit = (w / 1080f).coerceIn(0.85f, 2.2f)
    // Clear status bar / camera cutout
    state.playY0 = h * 0.14f
    state.playY1 = h * 0.68f
    val y = state.playY1 - state.u(6f)
    state.slots.clear()
    for (i in kinds.indices) {
        val x = margin + usable * ((i + 0.5f) / kinds.size)
        state.slots.add(StorageSlot(kind = kinds[i], mouth = Offset(x, y)))
    }
}

fun startRun(state: StorageState, best: Int, w: Float, h: Float) {
    state.phase = GamePhase.PLAYING
    state.jellies.clear()
    state.particles.clear()
    state.floatTexts.clear()
    state.wave = 1
    state.packedThisWave = 0
    state.waveGoal = waveGoalFor(1)
    state.waveTimeMax = waveTimeFor(1)
    state.waveTimeLeft = state.waveTimeMax
    state.score = 0
    state.bestScore = best
    state.combo = 0
    state.comboTimer = 0f
    state.totalPacked = 0
    state.grabId = -1
    state.spawnTimer = 2.5f
    state.nextJellyId = 1
    state.mods = RunMods(softCatch = 0.8f) // easier first run catch
    state.upgradeChoices = emptyList()
    state.shake = 0f
    state.announce = "订单 #1"
    state.announceT = 1.6f
    state.failedReason = ""
    state.tutorialStep = 0
    state.tutorialHint = "① 用手指按住彩色果冻"
    state.tutorialT = 99f
    state.tutorialPauseSpawn = true
    state.pulseT = 0f
    layoutSlots(state, w, h)
    // One easy starter jelly above the Wide bin so the first action is obvious
    spawnTutorialJelly(state)
}

/** Big normal jelly parked above Wide Bin for the first drag. */
fun spawnTutorialJelly(state: StorageState) {
    val wide = state.slots.firstOrNull() ?: return
    val radius = state.u(JellyKind.NORMAL.radius * 1.15f)
    val x = wide.mouth.x
    val y = state.playY0 + (state.playY1 - state.playY0) * 0.42f
    val body = SoftBodyObject.create(center = Offset(x, y), numVertices = 12, radius = radius)
    state.jellies.add(
        StorageJelly(
            id = state.nextJellyId++,
            kind = JellyKind.NORMAL,
            colorIdx = 0,
            body = body
        )
    )
}

fun spawnJelly(state: StorageState, burst: Boolean = false) {
    val cap = chaosCapFor(state.wave, state.mods)
    val live = state.jellies.count { !it.packing }
    if (live >= cap) return

    val kind = kindForWave(state.wave)
    val radius = state.u(kind.radius)
    val margin = radius + state.u(16f)
    val x = Random.nextFloat() * (state.deskW - margin * 2f) + margin
    val y = if (burst) {
        state.playY0 + Random.nextFloat() * (state.playY1 - state.playY0) * 0.45f
    } else {
        state.playY0 + radius + Random.nextFloat() * state.u(40f)
    }
    val body = SoftBodyObject.create(
        center = Offset(x, y),
        numVertices = 12,
        radius = radius
    ).withVelocity(
        Offset(
            (Random.nextFloat() - 0.5f) * state.u(80f),
            if (burst) (Random.nextFloat() - 0.3f) * state.u(60f)
            else Random.nextFloat() * state.u(40f) + state.u(20f)
        )
    )
    state.jellies.add(
        StorageJelly(
            id = state.nextJellyId++,
            kind = kind,
            colorIdx = Random.nextInt(jellyPalette.size),
            body = body,
            wobble = Random.nextFloat() * (2f * PI.toFloat())
        )
    )
}

fun physicsFor(kind: JellyKind): PhysicsParams =
    PhysicsParams(
        stiffness = kind.stiffness,
        edgeStiffness = kind.stiffness * 0.75f,
        damping = kind.damping,
        pressure = 3.2f
    )

fun pickUpgrades(): List<UpgradeDef> = ALL_UPGRADES.shuffled().take(3)

fun applyUpgrade(state: StorageState, id: String) {
    when (id) {
        "magnet" -> state.mods.magnet = (state.mods.magnet + 0.55f).coerceAtMost(2.2f)
        "throw" -> state.mods.throwPower = (state.mods.throwPower + 0.25f).coerceAtMost(2.2f)
        "widen" -> state.mods.slotWiden = (state.mods.slotWiden + 0.12f).coerceAtMost(1.55f)
        "chaos" -> state.mods.chaosCap += 2
        "score" -> state.mods.scoreMult = (state.mods.scoreMult + 0.2f).coerceAtMost(2.5f)
        "calm" -> state.mods.spawnSlow = (state.mods.spawnSlow * 1.15f).coerceAtMost(1.8f)
        "catch" -> state.mods.softCatch = (state.mods.softCatch + 0.35f).coerceAtMost(1.2f)
        "time" -> state.waveTimeLeft += 12f
    }
}

fun beginNextWave(state: StorageState) {
    state.wave += 1
    state.packedThisWave = 0
    state.waveGoal = waveGoalFor(state.wave)
    state.waveTimeMax = waveTimeFor(state.wave)
    state.waveTimeLeft = state.waveTimeMax
    state.phase = GamePhase.PLAYING
    state.upgradeChoices = emptyList()
    state.announce = "Order #${state.wave}"
    state.announceT = 2f
    state.spawnTimer = 0.4f
}

private fun burstParticles(x: Float, y: Float, color: Color, n: Int): List<Particle> =
    List(n) {
        val a = Random.nextFloat() * 2f * PI.toFloat()
        val s = Random.nextFloat() * 220f + 90f
        Particle(
            x, y,
            cos(a) * s, sin(a) * s - 40f,
            life = Random.nextFloat() * 0.45f + 0.35f,
            maxLife = 0.9f,
            size = Random.nextFloat() * 7f + 3f,
            color = color,
            gravity = 320f
        )
    }

private fun softCollide(a: StorageJelly, b: StorageJelly, unit: Float) {
    if (a.packing || b.packing) return
    if (a.id == b.id) return
    val d = b.body.center - a.body.center
    val dist = d.getDistance()
    val ra = a.body.restRadius().coerceAtLeast(a.kind.radius * unit)
    val rb = b.body.restRadius().coerceAtLeast(b.kind.radius * unit)
    val minDist = (ra + rb) * 0.82f
    if (dist < 1e-3f || dist >= minDist) return
    val n = d / dist
    val overlap = minDist - dist
    val invA = 1f / a.kind.mass
    val invB = 1f / b.kind.mass
    val invSum = invA + invB
    val push = n * (overlap / invSum)
    a.body = a.body.copy(center = a.body.center - push * invA)
    b.body = b.body.copy(center = b.body.center + push * invB)
    // Mild velocity exchange
    val rel = b.body.velocity - a.body.velocity
    val vn = rel.x * n.x + rel.y * n.y
    if (vn < 0f) {
        val bounce = 0.35f
        val j = -(1f + bounce) * vn / invSum
        val impulse = n * j
        a.body = a.body.withVelocity(a.body.velocity - impulse * invA)
        b.body = b.body.withVelocity(b.body.velocity + impulse * invB)
    }
}

fun tryGrab(state: StorageState, pos: Offset): Boolean {
    if (state.phase != GamePhase.PLAYING) return false
    var best: StorageJelly? = null
    // Generous grab radius — first-time players miss small hitboxes
    var bestD = state.u(160f)
    for (j in state.jellies) {
        if (j.packing) continue
        val r = j.body.restRadius().coerceAtLeast(state.u(j.kind.radius))
        val hit = j.body.containsPoint(pos) ||
            (j.body.center - pos).getDistance() < r * 1.85f
        if (!hit) continue
        val d = (j.body.center - pos).getDistance()
        if (d < bestD) {
            bestD = d
            best = j
        }
    }
    val jelly = best ?: return false
    state.grabId = jelly.id
    state.grabOffset = jelly.body.center - pos
    state.lastPointer = pos
    state.pointerVel = Offset.Zero
    jelly.grabScale = 1.12f
    if (state.tutorialStep == 0) {
        state.tutorialStep = 1
        state.tutorialHint = "② 拖到底部绿色「宽口箱」，对准洞口"
    }
    return true
}

fun moveGrab(state: StorageState, pos: Offset, dt: Float) {
    if (state.grabId < 0) return
    val jelly = state.jellies.firstOrNull { it.id == state.grabId } ?: return
    val dtSafe = dt.coerceAtLeast(1f / 120f)
    val rawVel = (pos - state.lastPointer) / dtSafe
    // Smooth pointer velocity for fling
    state.pointerVel = state.pointerVel * 0.55f + rawVel * 0.45f
    state.lastPointer = pos

    var target = pos + state.grabOffset
    // Sticky jellies lag behind the finger
    if (jelly.kind.sticky) {
        val cur = jelly.body.center
        target = cur + (target - cur) * 0.55f
    }
    val pad = state.u(jelly.kind.radius) * 0.6f
    target = Offset(
        target.x.coerceIn(pad, state.deskW - pad),
        // Allow dragging slightly into bin mouths (below nominal floor)
        target.y.coerceIn(state.playY0 + pad * 0.3f, state.playY1 + state.u(80f))
    )
    jelly.body = jelly.body.applyDrag(target)
}

fun releaseGrab(state: StorageState): Boolean {
    if (state.grabId < 0) return false
    val jelly = state.jellies.firstOrNull { it.id == state.grabId }
    val wasId = state.grabId
    state.grabId = -1
    if (jelly == null) return false
    jelly.grabScale = 1f
    val power = state.mods.throwPower * (if (jelly.kind.sticky) 0.65f else 1f)
    var vx = state.pointerVel.x * 0.55f * power
    var vy = state.pointerVel.y * 0.55f * power
    // Cap fling so physics stays readable
    val max = state.u(1100f) * power
    val speed = sqrt(vx * vx + vy * vy)
    if (speed > max) {
        val s = max / speed
        vx *= s
        vy *= s
    }
    // Heavy is slower to throw
    if (jelly.kind == JellyKind.HEAVY) {
        vx *= 0.7f
        vy *= 0.7f
    }
    if (jelly.kind == JellyKind.BOUNCY) {
        vx *= 1.15f
        vy *= 1.15f
    }
    jelly.body = jelly.body.withVelocity(Offset(vx, vy))
    state.pointerVel = Offset.Zero

    // Dropping on a bin mouth = pack (forgiving). Don't require slow settle.
    if (!jelly.packing && tryStartPack(state, jelly, force = true)) {
        return true
    }
    // restore id only if not packed — already cleared
    if (state.jellies.none { it.id == wasId && it.packing }) {
        // ok
    }
    if (state.tutorialStep == 1) {
        state.tutorialHint = "② 把果冻拖到绿色箱子洞口上方再松手"
    }
    return speed > state.u(180f)
}

private fun updatePacking(state: StorageState, dt: Float) {
    var i = 0
    while (i < state.jellies.size) {
        val j = state.jellies[i]
        if (!j.packing) {
            i++
            continue
        }
        j.packT += dt / 0.42f
        val t = j.packT.coerceIn(0f, 1f)
        // Shrink toward slot mouth
        val slot = state.slots.getOrNull(j.packSlot)
        if (slot != null) {
            val c = j.body.center
            val m = slot.mouth
            val nc = Offset(c.x + (m.x - c.x) * 0.18f, c.y + (m.y - c.y) * 0.18f)
            j.body = j.body.applyDrag(nc)
            // Scale rest radius down via re-create-ish: pull verts by moving center only;
            // visual scale handled in renderer via packT
        }
        if (t >= 1f) {
            state.jellies.removeAt(i)
            continue
        }
        i++
    }
}

private fun tryStartPack(state: StorageState, jelly: StorageJelly, force: Boolean = false): Boolean {
    if (jelly.packing) return false
    // Allow pack while still "grabbed" only when force (on release)
    if (!force && jelly.id == state.grabId) return false
    val r = jelly.body.restRadius().coerceAtLeast(state.u(jelly.kind.radius * 0.85f))
    val speed = jelly.body.velocity.getDistance()
    val soft = state.u(380f) + state.mods.softCatch * state.u(280f)
    val widen = state.mods.slotWiden

    for ((idx, slot) in state.slots.withIndex()) {
        val rect = slot.mouthRect(widen * 1.15f, state.unit) // slightly larger hit box
        val c = jelly.body.center
        val pad = state.u(if (force) 48f else 24f)
        if (c.x < rect.left - pad * 0.2f || c.x > rect.right + pad * 0.2f ||
            c.y < rect.top - pad || c.y > rect.bottom + pad
        ) {
            continue
        }
        if (!slot.accepts(r, widen, state.unit)) {
            // Reject: bounce up
            if (force || speed < state.u(80f) || c.y > slot.mouth.y - state.u(20f)) {
                jelly.body = jelly.body.withVelocity(
                    Offset(
                        jelly.body.velocity.x * 0.4f + (if (c.x < slot.mouth.x) -state.u(120f) else state.u(120f)),
                        -min(state.u(320f), state.u(180f) + r)
                    )
                )
                state.floatTexts.add(
                    FloatText(c.x, c.y - state.u(30f), "太大了！换宽口", Color(0xFFFF6B8A), life = 0.9f)
                )
                state.shake = maxOf(state.shake, 4f)
            }
            continue
        }
        // Must not be flying too hard — soft catch / force release ignores
        if (!force && speed > soft) {
            jelly.body = jelly.body.withVelocity(jelly.body.velocity * 0.72f)
            if (c.y < slot.mouth.y - state.u(12f)) continue
        }
        // Success
        jelly.packing = true
        jelly.packT = 0f
        jelly.packSlot = idx
        slot.flash = 1f
        slot.filled += 1

        val comboBonus = 1f + state.combo * 0.12f
        val pts = (jelly.kind.points * slot.kind.scoreMult * state.mods.scoreMult * comboBonus).toInt()
        state.score += pts
        state.totalPacked += 1
        state.packedThisWave += 1
        state.combo += 1
        state.comboTimer = 2.4f
        if (state.score > state.bestScore) state.bestScore = state.score

        val col = jellyPalette[jelly.colorIdx % jellyPalette.size]
        state.particles.addAll(burstParticles(c.x, c.y, col, 18))
        state.particles.addAll(burstParticles(slot.mouth.x, slot.mouth.y, slot.kind.color, 10))
        val comboTxt = if (state.combo >= 2) "  x${state.combo}" else ""
        state.floatTexts.add(
            FloatText(c.x, c.y - state.u(40f), "+$pts$comboTxt", Color(0xFFFFD700), life = 1f)
        )
        state.shake = maxOf(state.shake, if (state.combo >= 5) 10f else 5f)

        if (state.tutorialStep < 2) {
            state.tutorialStep = 2
            state.tutorialHint = "太棒了！继续塞满订单进度"
            state.tutorialPauseSpawn = false
            state.spawnTimer = 0.4f
            // Add a couple more easy jellies
            repeat(2) { spawnJelly(state, burst = true) }
        }
        if (state.totalPacked >= 3 && state.tutorialStep < 3) {
            state.tutorialStep = 3
            state.tutorialHint = "红条满了会失败 · 顶部是倒计时"
            state.tutorialT = 5f
        }
        return true
    }
    return false
}

private fun applyMagnet(state: StorageState, jelly: StorageJelly, dt: Float) {
    val m = state.mods.magnet
    if (m <= 0.01f || jelly.packing || jelly.id == state.grabId) return
    val widen = state.mods.slotWiden
    var best: StorageSlot? = null
    var bestD = state.u(160f) + m * state.u(90f)
    for (slot in state.slots) {
        if (!slot.accepts(jelly.body.restRadius(), widen, state.unit)) continue
        val d = (slot.mouth - jelly.body.center).getDistance()
        if (d < bestD) {
            bestD = d
            best = slot
        }
    }
    val slot = best ?: return
    val dir = slot.mouth - jelly.body.center
    val dist = dir.getDistance().coerceAtLeast(1f)
    val range = state.u(160f) + m * state.u(90f)
    val pull = (m * state.u(140f)) * (1f - (dist / range).coerceIn(0f, 1f))
    val n = dir / dist
    jelly.body = jelly.body.withVelocity(jelly.body.velocity + n * pull * dt)
}

fun updateStorage(state: StorageState, dt: Float) {
    if (state.phase != GamePhase.PLAYING) {
        // Still animate particles on menus
        updateFx(state, dt)
        return
    }

    val w = state.deskW
    val h = state.deskH
    val rawDt = dt.coerceIn(0f, 0.05f)

    if (state.shake > 0f) state.shake = (state.shake - rawDt * 28f).coerceAtLeast(0f)
    if (state.announceT > 0f) state.announceT -= rawDt
    if (state.tutorialT > 0f) state.tutorialT -= rawDt
    if (state.comboTimer > 0f) {
        state.comboTimer -= rawDt
        if (state.comboTimer <= 0f) state.combo = 0
    }
    for (slot in state.slots) {
        if (slot.flash > 0f) slot.flash = (slot.flash - rawDt * 2.8f).coerceAtLeast(0f)
    }

    // Don't punish players during the first-pack tutorial
    if (!state.tutorialPauseSpawn) {
        state.waveTimeLeft -= rawDt
        if (state.waveTimeLeft <= 0f) {
            failRun(state, "订单超时了！")
            return
        }
    }

    state.pulseT += rawDt

    // Spawn (paused until first successful pack in tutorial)
    if (!state.tutorialPauseSpawn) {
        state.spawnTimer -= rawDt
        val spawnEvery = (1.85f - state.wave * 0.07f).coerceIn(0.7f, 2.0f) * state.mods.spawnSlow
        if (state.spawnTimer <= 0f) {
            val live = state.jellies.count { !it.packing }
            val cap = chaosCapFor(state.wave, state.mods)
            if (live >= cap) {
                failRun(state, "桌子塞满了！果冻溢出")
                return
            }
            spawnJelly(state)
            state.spawnTimer = spawnEvery * (0.85f + Random.nextFloat() * 0.3f)
        }
    }

    // Physics substeps for stability
    val steps = 2
    val sdt = rawDt / steps
    repeat(steps) {
        for (jelly in state.jellies) {
            if (jelly.packing) continue
            val dragging = jelly.id == state.grabId
            if (!dragging) {
                applyMagnet(state, jelly, sdt)
                // light gravity (scale with unit so fall time feels similar across densities)
                val g = state.u(420f) * jelly.kind.mass * 0.35f
                jelly.body = jelly.body.withVelocity(
                    jelly.body.velocity + Offset(0f, g * sdt)
                )
            }
            jelly.body = jelly.body
                .update(sdt, physicsFor(jelly.kind), isDragging = dragging)
            // Custom playfield bounds; mouths can swallow jellies below the desk lip
            jelly.body = constrainPlayfield(
                jelly.body, w, state.playY0, state.playY1, jelly.kind, state
            )
            jelly.wobble += sdt * 3f
            if (jelly.grabScale > 1f && !dragging) {
                jelly.grabScale = (jelly.grabScale - sdt * 1.5f).coerceAtLeast(1f)
            }
        }
        // Soft body-body collisions (few jellies — O(n^2) fine)
        val list = state.jellies
        for (a in 0 until list.size) {
            for (b in a + 1 until list.size) {
                softCollide(list[a], list[b], state.unit)
            }
        }
    }

    // Packing checks
    for (jelly in state.jellies.toList()) {
        if (!jelly.packing) tryStartPack(state, jelly, force = false)
    }
    updatePacking(state, rawDt)

    // Wave clear
    if (state.packedThisWave >= state.waveGoal) {
        state.phase = GamePhase.UPGRADE
        state.upgradeChoices = pickUpgrades()
        state.announce = "订单完成！"
        state.announceT = 1.5f
        state.grabId = -1
        state.particles.addAll(
            burstParticles(w * 0.5f, h * 0.4f, Color(0xFFFFD700), 28)
        )
    }

    updateFx(state, rawDt)
}

private fun overOpenMouth(state: StorageState, body: SoftBodyObject, kind: JellyKind): Boolean {
    val widen = state.mods.slotWiden
    val r = body.restRadius().let { if (it <= 1f) kind.radius else it }
    for (slot in state.slots) {
        if (!slot.accepts(r, widen, state.unit)) continue
        val rect = slot.mouthRect(widen, state.unit)
        if (body.center.x in rect.left..rect.right) return true
    }
    return false
}

private fun constrainPlayfield(
    body: SoftBodyObject,
    width: Float,
    y0: Float,
    y1: Float,
    kind: JellyKind,
    state: StorageState
): SoftBodyObject {
    val bounce = if (kind == JellyKind.BOUNCY) 0.55f else 0.28f
    val inMouth = overOpenMouth(state, body, kind)
    // Let jellies sink into bin mouths instead of bouncing on the floor lip.
    val floorY = if (inMouth) y1 + state.u(55f) else y1
    val newVertices = body.boundaryVertices.map { v ->
        var pos = v.absolutePosition
        var vel = v.velocity
        if (pos.x < 0f) {
            pos = pos.copy(x = 0f); vel = vel.copy(x = -vel.x * bounce)
        }
        if (pos.x > width) {
            pos = pos.copy(x = width); vel = vel.copy(x = -vel.x * bounce)
        }
        if (pos.y < y0) {
            pos = pos.copy(y = y0); vel = vel.copy(y = -vel.y * bounce)
        }
        if (pos.y > floorY) {
            pos = pos.copy(y = floorY)
            vel = if (inMouth) vel.copy(y = vel.y * 0.5f) else vel.copy(y = -vel.y * bounce)
        }
        v.copy(absolutePosition = pos, velocity = vel)
    }
    var cx = body.center.x.coerceIn(0f, width)
    var cy = body.center.y.coerceIn(y0, floorY)
    var cv = body.velocity
    if (!inMouth && body.center.y >= y1 - 1f && cv.y > 0f) cv = cv.copy(y = -cv.y * bounce)
    if (body.center.y <= y0 + 1f && cv.y < 0f) cv = cv.copy(y = -cv.y * bounce)
    if (body.center.x <= 1f && cv.x < 0f) cv = cv.copy(x = -cv.x * bounce)
    if (body.center.x >= width - 1f && cv.x > 0f) cv = cv.copy(x = -cv.x * bounce)
    if (!inMouth && abs(cy - y1) < 2f) cv = cv.copy(x = cv.x * 0.92f)
    return body.copy(center = Offset(cx, cy), velocity = cv, boundaryVertices = newVertices)
}

private fun failRun(state: StorageState, reason: String) {
    state.phase = GamePhase.GAME_OVER
    state.failedReason = reason
    state.grabId = -1
    state.shake = 14f
    state.announce = reason
    state.announceT = 2f
}

fun updateFx(state: StorageState, dt: Float) {
    var i = 0
    while (i < state.particles.size) {
        val p = state.particles[i]
        p.life -= dt
        p.vy += p.gravity * dt
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.vx *= 0.99f
        p.size = (p.size - p.shrink * dt * p.size).coerceAtLeast(0.5f)
        if (p.life <= 0f) state.particles.removeAt(i) else i++
    }
    i = 0
    while (i < state.floatTexts.size) {
        val t = state.floatTexts[i]
        t.life -= dt
        t.y -= 42f * dt
        if (t.life <= 0f) state.floatTexts.removeAt(i) else i++
    }
}
