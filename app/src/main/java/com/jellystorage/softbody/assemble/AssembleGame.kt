package com.jellystorage.softbody.assemble

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.jellystorage.softbody.rememberHapticAudioManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val PREFS = "jelly_arena_v2"
private const val KEY_CLEAR = "cleared"
private const val MAX_SLOTS = 3

enum class Phase { MENU, MAP, BUILD, FIGHT, RESULT }

class AppState {
    var phase = Phase.MENU
    var stageIndex = 0
    var cleared = 0
    var loadout = mutableListOf<String>()
    var arena: ArenaState? = null
    var resultWin = false
    var pulse = 0f
    var toast = ""
    var toastT = 0f
    var buildPreviewPhase = 0f
}

@Composable
fun AssembleGame(
    modifier: Modifier = Modifier,
    textMeasurer: TextMeasurer = rememberTextMeasurer()
) {
    val context = LocalContext.current
    val haptics = rememberHapticAudioManager()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val state = remember { AppState().also { it.cleared = prefs.getInt(KEY_CLEAR, 0) } }
    var frame by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(Unit) { onDispose { haptics.release() } }

    fun save() = prefs.edit().putInt(KEY_CLEAR, state.cleared).apply()

    LaunchedEffect(size) {
        if (size.width <= 0) return@LaunchedEffect
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                if (prev == 0L) {
                    prev = now
                    return@withFrameNanos
                }
                val dt = ((now - prev) / 1e9f).coerceIn(0f, 0.05f)
                prev = now
                state.pulse += dt
                state.buildPreviewPhase += dt
                if (state.toastT > 0f) state.toastT -= dt

                val arena = state.arena
                if (state.phase == Phase.FIGHT && arena != null) {
                    val was = arena.finished
                    updateArena(arena, dt)
                    if (!was && arena.finished) {
                        state.phase = Phase.RESULT
                        state.resultWin = arena.playerWon
                        if (arena.playerWon) {
                            val id = allStages()[state.stageIndex].id
                            if (id > state.cleared) {
                                state.cleared = id
                                save()
                            }
                            haptics.onPackSuccess()
                        } else haptics.thud()
                    }
                }
                frame = now.toFloat()
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val pid = down.id
                    var up = down.position
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == pid } ?: break
                        if (ch.positionChange() != Offset.Zero) ch.consume()
                        if (ch.changedToUp()) {
                            up = ch.position
                            ch.consume()
                            break
                        }
                    }
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w < 2f) return@awaitEachGesture
                    onTap(state, up, w, h, haptics, ::save)
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frame
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)

        when (state.phase) {
            Phase.MENU -> drawMenu(textMeasurer, w, h, state.pulse)
            Phase.MAP -> drawMap(state, textMeasurer, w, h)
            Phase.BUILD -> drawBuild(state, textMeasurer, w, h)
            Phase.FIGHT, Phase.RESULT -> {
                drawFight(state, textMeasurer, w, h)
                if (state.phase == Phase.RESULT) drawResultOverlay(state, textMeasurer, w, h)
            }
        }
        if (state.toastT > 0f) {
            drawChip(textMeasurer, state.toast, w * 0.5f, h * 0.14f, Color(0xFFFFD700))
        }
    }
}

private fun onTap(
    state: AppState,
    pos: Offset,
    w: Float,
    h: Float,
    haptics: com.jellystorage.softbody.HapticAudioManager,
    save: () -> Unit
) {
    when (state.phase) {
        Phase.MENU -> {
            state.phase = Phase.MAP
            haptics.squish()
        }
        Phase.MAP -> {
            if (pos.y > h * 0.92f) {
                state.phase = Phase.MENU
                return
            }
            val idx = hitMapRow(pos, w, h)
            if (idx != null) {
                val st = allStages()[idx]
                if (st.id <= state.cleared + 1) {
                    state.stageIndex = idx
                    state.loadout.clear()
                    // auto equip flame for stage 1 tutorial if empty
                    state.phase = Phase.BUILD
                    haptics.squish()
                } else {
                    state.toast = "先通关前一关"
                    state.toastT = 1.5f
                }
            }
        }
        Phase.BUILD -> {
            val stage = allStages()[state.stageIndex]
            val weapons = ALL_WEAPONS.take(stage.unlockWeapons)
            hitWeaponCell(pos, w, h, weapons.size)?.let { i ->
                val id = weapons[i].id
                if (id in state.loadout) {
                    state.loadout.remove(id)
                    haptics.rustle()
                } else if (state.loadout.size < MAX_SLOTS) {
                    state.loadout.add(id)
                    haptics.rustle()
                    val syn = activeSynergies(state.loadout)
                    if (syn.isNotEmpty()) {
                        state.toast = "组合：" + syn.joinToString("、") { it.name }
                        state.toastT = 1.6f
                        haptics.vibrateSnap()
                    }
                } else {
                    state.toast = "最多 3 件武器"
                    state.toastT = 1.2f
                }
                return
            }
            if (pos.y in h * 0.88f..h * 0.97f) {
                if (state.loadout.isEmpty()) {
                    state.toast = "先点选至少 1 件武器"
                    state.toastT = 1.4f
                    return
                }
                val arenaH = h * 0.62f
                val arenaW = w
                state.arena = createArena(state.loadout.toList(), stage, arenaW, arenaH)
                state.phase = Phase.FIGHT
                haptics.thud()
                return
            }
            if (pos.y < h * 0.09f && pos.x < w * 0.22f) {
                state.phase = Phase.MAP
            }
        }
        Phase.FIGHT -> {
            // tap: small shake only, fight is auto visual
        }
        Phase.RESULT -> {
            when {
                pos.y in h * 0.62f..h * 0.73f -> {
                    if (state.resultWin) {
                        val next = state.stageIndex + 1
                        if (next < allStages().size) {
                            state.stageIndex = next
                            state.loadout.clear()
                            state.phase = Phase.BUILD
                        } else state.phase = Phase.MAP
                    } else {
                        state.phase = Phase.BUILD
                    }
                    state.arena = null
                    haptics.squish()
                }
                pos.y in h * 0.75f..h * 0.86f -> {
                    state.arena = null
                    state.phase = Phase.MAP
                }
            }
        }
    }
}

private fun hitMapRow(pos: Offset, w: Float, h: Float): Int? {
    val top = h * 0.18f
    val row = h * 0.085f
    val n = allStages().size
    for (i in 0 until n) {
        val y = top + i * row
        if (pos.y in y..(y + row * 0.88f) && pos.x in (w * 0.06f)..(w * 0.94f)) return i
    }
    return null
}

private fun hitWeaponCell(pos: Offset, w: Float, h: Float, count: Int): Int? {
    val cols = 3
    val top = h * 0.42f
    val cellW = w * 0.29f
    val cellH = h * 0.10f
    val gapX = w * 0.025f
    val startX = (w - (cols * cellW + (cols - 1) * gapX)) / 2f
    for (i in 0 until count) {
        val c = i % cols
        val r = i / cols
        val x = startX + c * (cellW + gapX)
        val y = top + r * (cellH + 8f)
        if (pos.x in x..(x + cellW) && pos.y in y..(y + cellH)) return i
    }
    return null
}

// ═══════════════════════ DRAW ═══════════════════════

private fun DrawScope.drawMenu(tm: TextMeasurer, w: Float, h: Float, pulse: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1020), Color(0xFF1E1B4B), Color(0xFF0F2744))), size = Size(w, h))
    // ambient blobs
    drawMonster(Offset(w * 0.22f, h * 0.55f), 48f, Color(0xFFFF6B8A), Color(0xFFFFD0DA), pulse, 0f, 0f)
    drawMonster(Offset(w * 0.78f, h * 0.52f), 56f, Color(0xFF4ECDC4), Color(0xFFC8F5F0), pulse + 1f, 0f, 0f)

    title(tm, "果冻拼装乱斗", w * 0.5f, h * 0.14f, Color(0xFFFF6B8A), 34.sp)
    title(tm, "组合同步技 · 打出华丽连段", w * 0.5f, h * 0.21f, Color(0xFF94A3B8), 14.sp)

    val card = RectF(w * 0.08f, h * 0.28f, w * 0.84f, h * 0.26f)
    round(card, Color(0xEE1E293B), 20f)
    title(tm, "这是一款拼装对战", w * 0.5f, card.t + 22f, Color(0xFFFFD700), 16.sp)
    val tips = listOf(
        "为果冻装上最多 3 件武器",
        "标签凑对触发「奇妙组合」特效",
        "每关 AI 有克制，装错几乎打不动",
        "战场上会飞弹、爆炸、连击数字"
    )
    tips.forEachIndexed { i, s ->
        label(tm, s, w * 0.14f, card.t + 70f + i * 38f, Color(0xFFE2E8F0), 14.sp)
    }

    primaryBtn(tm, "进入征程", w, h * 0.72f)
}

private fun DrawScope.drawMap(state: AppState, tm: TextMeasurer, w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))), size = Size(w, h))
    title(tm, "征程地图", w * 0.5f, h * 0.08f, Color.White, 26.sp)
    title(tm, "已征服 ${state.cleared}/${allStages().size} 关", w * 0.5f, h * 0.13f, Color(0xFF94A3B8), 13.sp)

    val top = h * 0.18f
    val row = h * 0.085f
    allStages().forEachIndexed { i, st ->
        val y = top + i * row
        val open = st.id <= state.cleared + 1
        val done = st.id <= state.cleared
        round(RectF(w * 0.06f, y, w * 0.88f, row * 0.82f), if (open) Color(0xFF1E293B) else Color(0xFF0F172A), 14f)
        // left color pip
        drawCircle(st.enemy.color.copy(alpha = if (open) 1f else 0.3f), 10f, Offset(w * 0.12f, y + row * 0.4f))
        val name = if (open) "${st.id}  ${st.enemy.name}" else "${st.id}  ？？？"
        label(tm, name, w * 0.18f, y + 12f, when {
            done -> Color(0xFF4ADE80)
            open -> Color.White
            else -> Color(0xFF64748B)
        }, 15.sp)
        label(tm, if (open) st.chapter else "未解锁", w * 0.18f, y + 38f, Color(0xFF94A3B8), 11.sp)
        if (done) label(tm, "CLEAR", w * 0.78f, y + 22f, Color(0xFF4ADE80), 12.sp)
        else if (open) label(tm, "可挑战", w * 0.76f, y + 22f, Color(0xFFFFD700), 12.sp)
    }
    title(tm, "返回标题", w * 0.5f, h * 0.94f, Color(0xFF64748B), 13.sp)
}

private fun DrawScope.drawBuild(state: AppState, tm: TextMeasurer, w: Float, h: Float) {
    val stage = allStages()[state.stageIndex]
    drawRect(Brush.verticalGradient(listOf(stage.bgTop, stage.bgBot)), size = Size(w, h))

    title(tm, "拼装工坊", w * 0.5f, h * 0.045f, Color.White, 18.sp)
    label(tm, "← 地图", w * 0.06f, h * 0.05f, Color(0xFF94A3B8), 12.sp)

    // enemy info
    round(RectF(w * 0.05f, h * 0.095f, w * 0.9f, h * 0.13f), Color(0xCC0F172A), 14f)
    title(tm, "敌 ${stage.enemy.name}", w * 0.5f, h * 0.105f, stage.enemy.color, 15.sp)
    title(tm, stage.enemy.hint, w * 0.5f, h * 0.145f, Color(0xFFFFD700), 11.sp)
    title(tm, stage.enemy.flavor, w * 0.5f, h * 0.175f, Color(0xFF94A3B8), 11.sp)

    // preview monster
    val cx = w * 0.5f
    val cy = h * 0.30f
    drawMonster(Offset(cx, cy), 44f, Color(0xFFFF6B8A), Color(0xFFFFD0DA), state.buildPreviewPhase, 0f, 0f)
    // orbit weapons
    state.loadout.forEachIndexed { i, id ->
        val wpn = weaponById(id) ?: return@forEachIndexed
        val a = state.buildPreviewPhase * 1.5f + i * (2f * PI.toFloat() / 3f)
        val ox = cx + cos(a) * 70f
        val oy = cy + sin(a) * 36f
        drawCircle(wpn.color.copy(alpha = 0.9f), 14f, Offset(ox, oy))
        drawCircle(Color.White.copy(alpha = 0.5f), 14f, Offset(ox, oy), style = Stroke(2f))
    }

    val syn = activeSynergies(state.loadout)
    if (syn.isNotEmpty()) {
        title(tm, "✦ " + syn.joinToString("  ") { it.name }, w * 0.5f, h * 0.37f, Color(0xFFFFD700), 13.sp)
    } else {
        title(tm, "未激活奇妙组合", w * 0.5f, h * 0.37f, Color(0xFF64748B), 12.sp)
    }

    // slots text
    title(tm, "装备 ${state.loadout.size}/3  ·  点武器装卸", w * 0.5f, h * 0.395f, Color(0xFFCBD5E1), 12.sp)

    val weapons = ALL_WEAPONS.take(stage.unlockWeapons)
    val cols = 3
    val top = h * 0.42f
    val cellW = w * 0.29f
    val cellH = h * 0.10f
    val gapX = w * 0.025f
    val startX = (w - (cols * cellW + (cols - 1) * gapX)) / 2f
    weapons.forEachIndexed { i, wep ->
        val c = i % cols
        val r = i / cols
        val x = startX + c * (cellW + gapX)
        val y = top + r * (cellH + 8f)
        val sel = wep.id in state.loadout
        round(RectF(x, y, cellW, cellH), if (sel) wep.color.copy(alpha = 0.35f) else Color(0xFF1E293B), 12f)
        drawRoundRect(
            if (sel) Color(0xFFFFD700) else wep.color.copy(alpha = 0.75f),
            Offset(x, y), Size(cellW, cellH), CornerRadius(12f), style = Stroke(if (sel) 3f else 2f)
        )
        title(tm, wep.name, x + cellW / 2f, y + 6f, Color.White, 12.sp)
        title(tm, wep.tags.joinToString("") { "[${it.label}]" }, x + cellW / 2f, y + cellH * 0.38f, Color(0xFFCBD5E1), 10.sp)
        title(tm, "攻${wep.atk}" + if (wep.def > 0) " 防${wep.def}" else "", x + cellW / 2f, y + cellH * 0.65f, Color(0xFF94A3B8), 10.sp)
    }

    primaryBtn(tm, "进入战场！", w, h * 0.90f)
}

private fun DrawScope.drawFight(state: AppState, tm: TextMeasurer, w: Float, h: Float) {
    val stage = allStages()[state.stageIndex]
    val arena = state.arena ?: return

    // full bg
    drawRect(Brush.verticalGradient(listOf(stage.bgTop, stage.bgBot)), size = Size(w, h))

    // arena panel
    val topPad = h * 0.11f
    val arenaH = h * 0.62f
    withTransform({
        translate(0f, topPad)
        if (arena.shake > 0f) {
            translate(
                (Random.nextFloat() - 0.5f) * arena.shake,
                (Random.nextFloat() - 0.5f) * arena.shake
            )
        }
    }) {
        // ground
        drawRect(Color(0x33000000), Offset(0f, 0f), Size(w, arenaH))
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0x44000000))),
            Offset(0f, arena.groundY - 40f), Size(w, arenaH - arena.groundY + 40f)
        )
        drawLine(Color(0x44FFFFFF), Offset(0f, arena.groundY), Offset(w, arena.groundY), 3f)

        // intro banners
        if (arena.introT > 0f) {
            title(tm, "VS", w * 0.5f, arenaH * 0.35f, Color.White, 40.sp)
            title(tm, stage.enemy.name, w * 0.5f, arenaH * 0.48f, stage.enemy.color, 22.sp)
        }

        // projectiles
        for (p in arena.projectiles) {
            when (p.style) {
                AttackStyle.SPIKE, AttackStyle.DRILL -> {
                    drawCircle(p.color, p.r * 0.7f, Offset(p.x, p.y))
                    drawLine(p.color, Offset(p.x - p.vx * 0.02f, p.y - p.vy * 0.02f), Offset(p.x, p.y), 4f, StrokeCap.Round)
                }
                AttackStyle.MIST -> drawCircle(p.color.copy(alpha = 0.45f), p.r * 1.4f, Offset(p.x, p.y))
                else -> {
                    drawCircle(p.color.copy(alpha = 0.9f), p.r, Offset(p.x, p.y))
                    drawCircle(Color.White.copy(alpha = 0.35f), p.r * 0.4f, Offset(p.x - 2f, p.y - 2f))
                }
            }
        }

        // fighters
        drawArenaFighter(arena.player, state.pulse)
        drawArenaFighter(arena.enemy, state.pulse + 1f)

        // sparks
        for (s in arena.sparks) {
            val a = (s.life / 0.7f).coerceIn(0f, 1f)
            drawCircle(s.color.copy(alpha = a), s.size, Offset(s.x, s.y))
        }
        // floats
        for (f in arena.floats) {
            val a = (f.life / 0.85f).coerceIn(0f, 1f)
            val sz = if (f.crit) 20.sp else 15.sp
            title(tm, f.text, f.x, f.y, f.color.copy(alpha = a), sz)
        }

        if (arena.combo >= 3) {
            title(tm, "COMBO x${arena.combo}", w * 0.5f, 40f, Color(0xFFFF4500), 18.sp)
        }
        if (arena.bannerT > 0f && arena.introT <= 0f) {
            title(tm, arena.banner, w * 0.5f, 70f, Color(0xFFFFD700), 14.sp)
        }
    }

    // HUD outside arena transform
    // HP bars top
    round(RectF(w * 0.04f, h * 0.035f, w * 0.42f, h * 0.055f), Color(0xCC0F172A), 10f)
    round(RectF(w * 0.54f, h * 0.035f, w * 0.42f, h * 0.055f), Color(0xCC0F172A), 10f)
    hpFill(w * 0.05f, h * 0.05f, w * 0.40f, 12f, arena.player.hp / arena.player.maxHp, Color(0xFF4ADE80))
    hpFill(w * 0.55f, h * 0.05f, w * 0.40f, 12f, arena.enemy.hp / arena.enemy.maxHp, stage.enemy.color)
    label(tm, "你  ${arena.player.hp.toInt()}", w * 0.06f, h * 0.038f, Color.White, 10.sp)
    label(tm, "${stage.enemy.name}  ${arena.enemy.hp.toInt()}", w * 0.56f, h * 0.038f, Color.White, 10.sp)

    // synergy strip
    if (arena.synergies.isNotEmpty()) {
        title(
            tm,
            arena.synergies.joinToString("  ") { "【${it.name}】" },
            w * 0.5f, h * 0.095f, Color(0xFFFFD700), 11.sp
        )
    } else if (!arena.requiredMet) {
        title(tm, "⚠ 未满足克制组合 · 输出被压制", w * 0.5f, h * 0.095f, Color(0xFFFB7185), 11.sp)
    }

    // bottom log / tips
    round(RectF(w * 0.05f, h * 0.76f, w * 0.9f, h * 0.12f), Color(0xCC0F172A), 14f)
    val lines = buildList {
        if (arena.synergies.isNotEmpty()) add("协同：" + arena.synergies.joinToString("、") { it.name })
        add(stage.enemy.hint)
        if (!arena.requiredMet) add("当前组合无法打穿这关！")
        else add("看弹幕与连击，自动作战中…")
    }
    lines.take(3).forEachIndexed { i, s ->
        title(tm, s, w * 0.5f, h * 0.78f + i * 28f, Color(0xFFCBD5E1), 11.sp)
    }
}

private fun DrawScope.drawResultOverlay(state: AppState, tm: TextMeasurer, w: Float, h: Float) {
    drawRect(Color(0xAA020617), size = Size(w, h))
    if (state.resultWin) {
        title(tm, "胜利！", w * 0.5f, h * 0.32f, Color(0xFF4ADE80), 40.sp)
        title(tm, "奇妙组合撕碎了敌人", w * 0.5f, h * 0.42f, Color.White, 15.sp)
        val next = state.stageIndex + 1 < allStages().size
        primaryBtn(tm, if (next) "下一关拼装" else "返回地图", w, h * 0.64f)
        secondaryBtn(tm, "征程地图", w, h * 0.77f)
    } else {
        title(tm, "战败", w * 0.5f, h * 0.30f, Color(0xFFFF6B8A), 40.sp)
        val stage = allStages()[state.stageIndex]
        title(tm, "换一套武器组合再战", w * 0.5f, h * 0.40f, Color.White, 14.sp)
        title(tm, stage.enemy.hint, w * 0.5f, h * 0.48f, Color(0xFFFFD700), 12.sp)
        primaryBtn(tm, "重新拼装", w, h * 0.64f)
        secondaryBtn(tm, "征程地图", w, h * 0.77f)
    }
}

private fun DrawScope.drawArenaFighter(f: ArenaFighter, pulse: Float) {
    val squashY = 1f - f.squash * 0.35f
    val squashX = 1f + f.squash * 0.3f
    val attackNudge = f.facing * f.attackAnim * 18f
    val c = Offset(f.x + attackNudge, f.y)
    val flash = f.hitFlash > 0f
    val rim = if (flash) Color.White else f.color
    val fill = if (flash) Color.White.copy(alpha = 0.9f) else f.bodyColor
    drawMonster(c, f.radius, rim, fill, pulse, squashX, squashY)
    // shield ring
    if (f.def > 12f) {
        drawCircle(Color(0x5560A5FA), f.radius * 1.25f, c, style = Stroke(3f))
    }
    // weapon ornaments
    f.weapons.take(3).forEachIndexed { i, w ->
        val a = -0.8f + i * 0.8f
        val ox = c.x + cos(a) * f.radius * 0.9f * f.facing
        val oy = c.y + sin(a) * f.radius * 0.5f
        drawCircle(w.color, 7f, Offset(ox, oy))
    }
}

private fun DrawScope.drawMonster(
    center: Offset, r: Float, rim: Color, fill: Color,
    phase: Float, sx: Float, sy: Float
) {
    val scaleX = if (sx == 0f) 1f else sx
    val scaleY = if (sy == 0f) 1f else sy
    val n = 14
    val path = Path()
    for (i in 0 until n) {
        val a = (2 * PI * i / n).toFloat()
        val wob = 1f + 0.07f * sin(phase * 3.2f + a * 2.5f)
        val p = Offset(
            center.x + cos(a) * r * scaleX * wob,
            center.y + sin(a) * r * scaleY * wob
        )
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, fill.copy(alpha = 0.92f))
    drawPath(path, rim, style = Stroke(4.5f))
    // eyes
    val eyeY = center.y - r * 0.15f
    drawCircle(Color(0xFF0F172A), r * 0.12f, Offset(center.x - r * 0.22f, eyeY))
    drawCircle(Color(0xFF0F172A), r * 0.12f, Offset(center.x + r * 0.22f, eyeY))
    drawCircle(Color.White, r * 0.05f, Offset(center.x - r * 0.18f, eyeY - r * 0.03f))
    drawCircle(Color.White, r * 0.05f, Offset(center.x + r * 0.26f, eyeY - r * 0.03f))
    // shine
    drawCircle(Color.White.copy(alpha = 0.28f), r * 0.18f, Offset(center.x - r * 0.3f, center.y - r * 0.35f))
}

// helpers
private data class RectF(val l: Float, val t: Float, val w: Float, val h: Float)

private fun DrawScope.round(r: RectF, color: Color, rad: Float) {
    drawRoundRect(color, Offset(r.l, r.t), Size(r.w, r.h), CornerRadius(rad, rad))
}

private fun DrawScope.hpFill(x: Float, y: Float, w: Float, h: Float, frac: Float, color: Color) {
    drawRoundRect(Color(0xFF334155), Offset(x, y), Size(w, h), CornerRadius(6f))
    drawRoundRect(color, Offset(x, y), Size(w * frac.coerceIn(0f, 1f), h), CornerRadius(6f))
}

private fun DrawScope.primaryBtn(tm: TextMeasurer, text: String, w: Float, y: Float) {
    val bw = w * 0.58f
    val bx = (w - bw) / 2f
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFFFF6B8A), Color(0xFFFFA94D))),
        Offset(bx, y), Size(bw, 58f), CornerRadius(18f)
    )
    title(tm, text, w * 0.5f, y + 14f, Color.White, 18.sp)
}

private fun DrawScope.secondaryBtn(tm: TextMeasurer, text: String, w: Float, y: Float) {
    val bw = w * 0.58f
    val bx = (w - bw) / 2f
    drawRoundRect(Color(0xFF334155), Offset(bx, y), Size(bw, 54f), CornerRadius(16f))
    title(tm, text, w * 0.5f, y + 14f, Color.White, 16.sp)
}

private fun DrawScope.drawChip(tm: TextMeasurer, text: String, x: Float, y: Float, color: Color) {
    val layout = tm.measure(text, TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    val pad = 16f
    drawRoundRect(
        Color(0xEE000000),
        Offset(x - layout.size.width / 2f - pad, y - 6f),
        Size(layout.size.width + pad * 2, layout.size.height + 12f),
        CornerRadius(12f)
    )
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y))
}

private fun DrawScope.title(tm: TextMeasurer, text: String, x: Float, y: Float, color: Color, size: TextUnit) {
    val layout = tm.measure(text, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y))
}

private fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, y: Float, color: Color, size: TextUnit) {
    val layout = tm.measure(text, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Medium))
    drawText(layout, topLeft = Offset(x, y))
}
