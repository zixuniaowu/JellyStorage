package com.jellystorage.softbody.assemble

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class ArenaFighter(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var radius: Float,
    var hp: Float,
    val maxHp: Float,
    var atk: Float,
    var def: Float,
    var color: Color,
    var bodyColor: Color,
    var attackCd: Float = 0.4f,
    var attackInterval: Float = 1f,
    var attackAnim: Float = 0f, // 0..1 punch flash
    var hitFlash: Float = 0f,
    var squash: Float = 0f,
    var facing: Float = 1f, // 1 right, -1 left
    var lifesteal: Float = 0f,
    var thorns: Float = 0f,
    var burnDps: Float = 0f,
    var burnTimer: Float = 0f,
    var shieldPulse: Float = 0f,
    val isPlayer: Boolean,
    val name: String,
    var weapons: List<WeaponMod> = emptyList(),
    var multiShot: Int = 0,
    var projScale: Float = 1f,
    var critChance: Float = 0.1f,
    var damageMult: Float = 1f,
    var moveStyle: Int = 0
)

data class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    var r: Float,
    val dmg: Float,
    val color: Color,
    val fromPlayer: Boolean,
    val style: AttackStyle,
    var pierce: Int = 0,
    var homing: Float = 0f
)

data class FloatNum(
    var x: Float, var y: Float,
    val text: String,
    val color: Color,
    var life: Float = 0.85f,
    val crit: Boolean = false
)

data class Spark(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float,
    val color: Color,
    var size: Float
)

data class ArenaState(
    var player: ArenaFighter,
    var enemy: ArenaFighter,
    var projectiles: MutableList<Projectile> = mutableListOf(),
    var floats: MutableList<FloatNum> = mutableListOf(),
    var sparks: MutableList<Spark> = mutableListOf(),
    var time: Float = 0f,
    var finished: Boolean = false,
    var playerWon: Boolean = false,
    var introT: Float = 1.6f,
    var outroT: Float = 0f,
    var synergies: List<Synergy> = emptyList(),
    var requiredMet: Boolean = true,
    var banner: String = "",
    var bannerT: Float = 0f,
    var shake: Float = 0f,
    var arenaW: Float = 1080f,
    var arenaH: Float = 900f,
    var groundY: Float = 700f,
    var combo: Int = 0,
    var comboT: Float = 0f
)

fun createArena(
    loadout: List<String>,
    stage: StageDef,
    arenaW: Float,
    arenaH: Float
): ArenaState {
    val weapons = loadout.mapNotNull { weaponById(it) }
    val syn = activeSynergies(loadout)
    val tags = tagsFromLoadout(loadout)
    val e = stage.enemy

    var atk = 12f + weapons.sumOf { it.atk }
    var def = 3f + weapons.sumOf { it.def }
    var hp = 140f + weapons.sumOf { it.hp }
    var lifesteal = 0f
    var thorns = 0f
    var burn = 0f
    var multi = 0
    var scale = 1f
    var crit = 0.1f
    var atkM = 1f
    var defM = 1f

    for (s in syn) {
        atkM *= s.atkMult
        defM *= s.defMult
        atk += s.bonusAtk
        def += s.bonusDef
        lifesteal += s.lifesteal
        thorns += s.thorns
        burn += s.burnDps
        multi += s.multiShot
        scale *= s.projectileScale
        if (s.special == "crit") crit += 0.35f
        if (s.special == "fortress") hp += 50f
        if (s.special == "steam") atk += 10f
    }

    val weak = e.weakTo.count { it in tags }
    val resist = e.resist.count { it in tags }
    atkM *= 1f + weak * 0.2f
    atkM *= (1f - resist * 0.25f).coerceAtLeast(0.3f)

    val req = e.requireSynergy
    val requiredMet = req.isEmpty() || syn.any { it.id in req }
    if (!requiredMet) {
        atkM *= 0.28f
        defM *= 0.65f
    } else if (req.isNotEmpty()) {
        atkM *= 1.3f
    }

    atk = (atk * atkM).coerceAtLeast(4f)
    def = (def * defM).coerceAtLeast(0f)

    val ground = arenaH * 0.78f
    val pr = minOf(arenaW, arenaH) * 0.07f
    val er = minOf(arenaW, arenaH) * 0.08f

    val player = ArenaFighter(
        x = arenaW * 0.22f, y = ground - pr,
        radius = pr, hp = hp, maxHp = hp, atk = atk, def = def,
        color = Color(0xFFFF6B8A), bodyColor = Color(0xFFFFD0DA),
        attackInterval = 0.72f - multi * 0.06f,
        lifesteal = lifesteal.coerceAtMost(0.55f),
        thorns = thorns.coerceAtMost(0.65f),
        burnDps = burn,
        isPlayer = true, name = "拼装果冻",
        weapons = weapons, multiShot = multi, projScale = scale,
        critChance = crit.coerceAtMost(0.55f),
        damageMult = if (requiredMet) 1f else 0.28f,
        facing = 1f
    )
    val enemy = ArenaFighter(
        x = arenaW * 0.78f, y = ground - er,
        radius = er, hp = e.hp.toFloat(), maxHp = e.hp.toFloat(),
        atk = e.atk.toFloat(), def = e.def.toFloat(),
        color = e.color, bodyColor = e.bodyColor,
        attackInterval = e.attackInterval,
        isPlayer = false, name = e.name,
        facing = -1f, moveStyle = e.moveStyle,
        burnDps = if (stage.id == 3 || stage.id == 5) 3f else 0f
    )

    val banner = when {
        !requiredMet && req.isNotEmpty() -> "组合不对！伤害被大幅压制"
        syn.isNotEmpty() -> "协同发动：${syn.joinToString("·") { it.name }}"
        else -> "没有奇妙组合…"
    }

    return ArenaState(
        player = player, enemy = enemy, synergies = syn, requiredMet = requiredMet,
        banner = banner, bannerT = 2.2f,
        arenaW = arenaW, arenaH = arenaH, groundY = ground,
        introT = 1.4f
    )
}

fun updateArena(state: ArenaState, dt: Float) {
    if (state.finished) {
        state.outroT += dt
        updateFx(state, dt)
        return
    }

    val raw = dt.coerceIn(0f, 0.05f)
    state.time += raw
    if (state.shake > 0f) state.shake = (state.shake - raw * 40f).coerceAtLeast(0f)
    if (state.bannerT > 0f) state.bannerT -= raw
    if (state.comboT > 0f) {
        state.comboT -= raw
        if (state.comboT <= 0f) state.combo = 0
    }

    if (state.introT > 0f) {
        state.introT -= raw
        // run-in
        val p = state.player
        val e = state.enemy
        p.x += (state.arenaW * 0.22f - p.x) * 4f * raw
        e.x += (state.arenaW * 0.78f - e.x) * 4f * raw
        updateFx(state, raw)
        return
    }

    // burn ticks
    applyBurn(state.player, state.enemy, raw, state)
    applyBurn(state.enemy, state.player, raw, state)

    aiMove(state.player, state.enemy, raw, true)
    aiMove(state.enemy, state.player, raw, false)

    // attacks
    tryAttack(state, state.player, state.enemy, raw)
    tryAttack(state, state.enemy, state.player, raw)

    // projectiles
    updateProjectiles(state, raw)

    // anim decay
    decayFighter(state.player, raw)
    decayFighter(state.enemy, raw)
    updateFx(state, raw)

    if (state.enemy.hp <= 0f) {
        state.finished = true
        state.playerWon = true
        state.banner = "胜利！"
        state.bannerT = 3f
        state.shake = 14f
        burst(state, state.enemy.x, state.enemy.y, state.enemy.color, 40)
        float(state, state.enemy.x, state.enemy.y - 40f, "K.O.", Color(0xFFFFD700), true)
    } else if (state.player.hp <= 0f) {
        state.finished = true
        state.playerWon = false
        state.banner = if (!state.requiredMet) "组合错误 · 重新拼装" else "战败 · 调整武器"
        state.bannerT = 3f
        state.shake = 12f
        burst(state, state.player.x, state.player.y, state.player.color, 30)
    }
}

private fun applyBurn(from: ArenaFighter, to: ArenaFighter, dt: Float, state: ArenaState) {
    if (from.burnDps <= 0f || to.hp <= 0f) return
    // continuous burn from synergies applied by player only each frame if from is player... 
    // Actually burnDps on fighter is "I apply burn" - tick damage on enemy
    if (from.isPlayer) {
        to.burnTimer = 2f
    }
    if (to.burnTimer > 0f) {
        to.burnTimer -= dt
        val dmg = from.burnDps * dt
        if (dmg > 0 && from.isPlayer) {
            to.hp = (to.hp - dmg).coerceAtLeast(0f)
        }
    }
}

private fun aiMove(self: ArenaFighter, other: ArenaFighter, dt: Float, isPlayerSide: Boolean) {
    val ground = self.y // keep y fixed mostly
    val preferredDist = self.radius + other.radius + 80f
    val dx = other.x - self.x
    val dist = abs(dx)

    when (self.moveStyle) {
        1 -> { // charge
            if (dist > preferredDist) self.vx += self.facing * 220f * dt
            else self.vx *= 0.9f
        }
        2 -> { // kite
            if (dist < preferredDist + 40f) self.vx -= self.facing * 180f * dt
            else if (dist > preferredDist + 120f) self.vx += self.facing * 160f * dt
            else self.vx *= 0.92f
        }
        else -> {
            // gentle bob toward mid spacing
            val target = if (isPlayerSide) other.x - preferredDist else other.x + preferredDist
            self.vx += (target - self.x) * 1.8f * dt
            self.vx *= 0.92f
        }
    }
    self.x += self.vx * dt
    self.facing = if (other.x >= self.x) 1f else -1f
    // clamp
    // soft separation
    if (dist < self.radius + other.radius * 0.85f && dist > 1f) {
        val push = (self.radius + other.radius * 0.85f - dist) * 0.5f
        val n = dx / dist
        self.x -= n * push * if (isPlayerSide) 1f else -1f * 0f
        // simpler: push both handled per call
        if (isPlayerSide) self.x -= push
        else self.x += push
    }
}

private fun tryAttack(state: ArenaState, attacker: ArenaFighter, target: ArenaFighter, dt: Float) {
    if (attacker.hp <= 0f || target.hp <= 0f) return
    attacker.attackCd -= dt
    if (attacker.attackAnim > 0f) attacker.attackAnim = (attacker.attackAnim - dt * 3f).coerceAtLeast(0f)
    if (attacker.attackCd > 0f) return
    attacker.attackCd = attacker.attackInterval * (0.9f + Random.nextFloat() * 0.2f)
    attacker.attackAnim = 1f

    if (attacker.isPlayer && attacker.weapons.isNotEmpty()) {
        // fire each weapon style
        val shots = 1 + attacker.multiShot
        attacker.weapons.forEachIndexed { wi, w ->
            repeat(shots) { s ->
                val spread = (s - (shots - 1) / 2f) * 0.18f
                spawnShot(state, attacker, target, w, spread)
            }
        }
        // synergy steam: big orb
        if (state.synergies.any { it.special == "steam" }) {
            spawnBig(state, attacker, target, Color(0xFFE0F2FE), attacker.atk * 0.55f, 1.6f)
        }
    } else {
        // enemy simple projectile
        val col = attacker.color
        val dir = if (target.x > attacker.x) 1f else -1f
        state.projectiles.add(
            Projectile(
                attacker.x + dir * attacker.radius,
                attacker.y - 10f,
                dir * 420f, (Random.nextFloat() - 0.5f) * 60f,
                life = 1.6f, maxLife = 1.6f,
                r = 12f, dmg = attacker.atk,
                color = col, fromPlayer = false, style = AttackStyle.CORE
            )
        )
        // melee if close
        val dist = abs(target.x - attacker.x)
        if (dist < attacker.radius + target.radius + 30f) {
            applyHit(state, attacker, target, attacker.atk * 0.85f, false)
        }
    }
}

private fun spawnShot(state: ArenaState, a: ArenaFighter, t: ArenaFighter, w: WeaponMod, spread: Float) {
    val dir = a.facing
    val baseSpeed = when (w.style) {
        AttackStyle.SPIKE, AttackStyle.DRILL -> 620f
        AttackStyle.WHIP, AttackStyle.TENDRIL -> 480f
        AttackStyle.HAMMER, AttackStyle.CLAW -> 380f
        AttackStyle.MIST -> 280f
        AttackStyle.BOUNCE -> 450f
        AttackStyle.SHELL -> 200f
        AttackStyle.CORE -> 500f
    }
    val ang = spread
    val vx = dir * baseSpeed * cos(ang)
    val vy = baseSpeed * sin(ang) * 0.35f - 40f
    val dmg = (a.atk * 0.45f + w.atk * 0.55f) * a.damageMult
    val r = (9f + w.atk * 0.25f) * a.projScale
    state.projectiles.add(
        Projectile(
            a.x + dir * a.radius * 0.8f,
            a.y - a.radius * 0.15f,
            vx, vy, life = 1.8f, maxLife = 1.8f,
            r = r, dmg = dmg, color = w.color, fromPlayer = true,
            style = w.style,
            pierce = if (Tag.PIERCE in w.tags) 1 else 0,
            homing = if (Tag.SWIFT in w.tags) 0.8f else 0f
        )
    )
}

private fun spawnBig(state: ArenaState, a: ArenaFighter, t: ArenaFighter, color: Color, dmg: Float, scale: Float) {
    val dir = a.facing
    state.projectiles.add(
        Projectile(
            a.x + dir * a.radius, a.y,
            dir * 360f, -20f, life = 2f, maxLife = 2f,
            r = 22f * scale, dmg = dmg * a.damageMult, color = color,
            fromPlayer = true, style = AttackStyle.CORE, pierce = 2
        )
    )
}

private fun updateProjectiles(state: ArenaState, dt: Float) {
    var i = 0
    while (i < state.projectiles.size) {
        val p = state.projectiles[i]
        p.life -= dt
        // gravity light
        p.vy += 120f * dt
        if (p.homing > 0f) {
            val target = if (p.fromPlayer) state.enemy else state.player
            val dx = target.x - p.x
            val dy = target.y - p.y
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            p.vx += dx / d * 500f * p.homing * dt
            p.vy += dy / d * 500f * p.homing * dt
        }
        if (p.style == AttackStyle.BOUNCE && p.y > state.groundY - 8f && p.vy > 0f) {
            p.vy = -abs(p.vy) * 0.75f
            p.y = state.groundY - 8f
        }
        p.x += p.vx * dt
        p.y += p.vy * dt

        val target = if (p.fromPlayer) state.enemy else state.player
        val attacker = if (p.fromPlayer) state.player else state.enemy
        val dx = p.x - target.x
        val dy = p.y - target.y
        val hitR = target.radius + p.r
        if (dx * dx + dy * dy < hitR * hitR && target.hp > 0f) {
            var dmg = p.dmg
            val crit = p.fromPlayer && Random.nextFloat() < attacker.critChance
            if (crit) dmg *= 1.85f
            applyHit(state, attacker, target, dmg, crit)
            burst(state, p.x, p.y, p.color, 8)
            if (p.pierce > 0) {
                p.pierce--
                p.vx *= 0.85f
            } else {
                state.projectiles.removeAt(i)
                continue
            }
        }

        if (p.life <= 0f || p.x < -50f || p.x > state.arenaW + 50f || p.y > state.arenaH + 50f) {
            state.projectiles.removeAt(i)
        } else i++
    }
}

private fun applyHit(state: ArenaState, attacker: ArenaFighter, target: ArenaFighter, raw: Float, crit: Boolean) {
    val dmg = max(1f, raw - target.def * 0.45f)
    target.hp = (target.hp - dmg).coerceAtLeast(0f)
    target.hitFlash = 0.18f
    target.squash = 0.55f
    state.shake = max(state.shake, if (crit) 10f else 5f)

    if (attacker.isPlayer) {
        state.combo++
        state.comboT = 1.4f
    }

    val col = if (crit) Color(0xFFFFD700) else if (attacker.isPlayer) Color.White else Color(0xFFFF8A8A)
    float(state, target.x, target.y - target.radius, "${dmg.toInt()}${if (crit) "!" else ""}", col, crit)

    if (attacker.lifesteal > 0f && attacker.isPlayer) {
        val heal = dmg * attacker.lifesteal
        attacker.hp = (attacker.hp + heal).coerceAtMost(attacker.maxHp)
        float(state, attacker.x, attacker.y - attacker.radius - 20f, "+${heal.toInt()}", Color(0xFF4ADE80), false)
    }
    if (target.thorns > 0f && !attacker.isPlayer) {
        // player has thorns when hit by enemy - thorns on player
    }
    if (target.isPlayer && target.thorns > 0f) {
        val th = dmg * target.thorns
        // wrong - when player is target and has thorns
        attacker.hp = (attacker.hp - th).coerceAtLeast(0f)
        float(state, attacker.x, attacker.y - 30f, "反${th.toInt()}", Color(0xFFFACC15), false)
    }
    // enemy hit player: if player has thorns (on player fighter)
    if (!attacker.isPlayer && state.player.thorns > 0f && target.isPlayer) {
        // already handled above when target.isPlayer && target.thorns
    }
}

private fun decayFighter(f: ArenaFighter, dt: Float) {
    if (f.hitFlash > 0f) f.hitFlash -= dt
    if (f.squash > 0f) f.squash = (f.squash - dt * 2.8f).coerceAtLeast(0f)
    if (f.shieldPulse > 0f) f.shieldPulse -= dt
}

private fun float(state: ArenaState, x: Float, y: Float, text: String, color: Color, crit: Boolean) {
    state.floats.add(FloatNum(x + Random.nextFloat() * 20f - 10f, y, text, color, crit = crit))
}

private fun burst(state: ArenaState, x: Float, y: Float, color: Color, n: Int) {
    repeat(n) {
        val a = Random.nextFloat() * 2f * PI.toFloat()
        val s = Random.nextFloat() * 260f + 60f
        state.sparks.add(
            Spark(x, y, cos(a) * s, sin(a) * s - 40f, Random.nextFloat() * 0.45f + 0.25f, color, Random.nextFloat() * 6f + 2f)
        )
    }
}

private fun updateFx(state: ArenaState, dt: Float) {
    var i = 0
    while (i < state.floats.size) {
        val f = state.floats[i]
        f.life -= dt
        f.y -= 50f * dt
        if (f.life <= 0f) state.floats.removeAt(i) else i++
    }
    i = 0
    while (i < state.sparks.size) {
        val s = state.sparks[i]
        s.life -= dt
        s.vy += 400f * dt
        s.x += s.vx * dt
        s.y += s.vy * dt
        s.size *= 0.98f
        if (s.life <= 0f) state.sparks.removeAt(i) else i++
    }
}
