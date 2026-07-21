package com.jellystorage.softbody.levels

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
import androidx.compose.ui.unit.sp
import com.jellystorage.softbody.rememberHapticAudioManager
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import org.json.JSONObject

private const val PREFS = "jelly_levels_prefs"
private const val KEY_UNLOCK = "unlocked"
private const val KEY_STARS = "stars_json"

@Composable
fun LevelGame(
    modifier: Modifier = Modifier,
    textMeasurer: TextMeasurer = rememberTextMeasurer()
) {
    val context = LocalContext.current
    val haptics = rememberHapticAudioManager()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val state = remember {
        LevelState(
            unlocked = prefs.getInt(KEY_UNLOCK, 1).coerceAtLeast(1),
            stars = loadStars(prefs.getString(KEY_STARS, "{}") ?: "{}")
        )
    }
    var frame by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var lastPhase by remember { mutableStateOf(state.phase) }

    DisposableEffect(Unit) { onDispose { haptics.release() } }

    fun persist() {
        prefs.edit()
            .putInt(KEY_UNLOCK, state.unlocked)
            .putString(KEY_STARS, saveStars(state.stars))
            .apply()
    }

    LaunchedEffect(size) {
        if (size.width <= 0) return@LaunchedEffect
        layoutPlayfield(state, size.width.toFloat(), size.height.toFloat())
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                if (prev == 0L) {
                    prev = now
                    return@withFrameNanos
                }
                val dt = ((now - prev) / 1e9f).coerceIn(0f, 0.05f)
                prev = now
                updateLevel(state, dt)
                if (state.phase != lastPhase) {
                    when (state.phase) {
                        LevelPhase.WIN -> {
                            haptics.onPackSuccess()
                            persist()
                        }
                        LevelPhase.LOSE -> haptics.thud()
                        else -> {}
                    }
                    lastPhase = state.phase
                }
                frame = now.toFloat()
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                size = it
                if (it.width > 0) layoutPlayfield(state, it.width.toFloat(), it.height.toFloat())
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val start = down.position
                    val id = down.id
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val phase = state.phase

                    when (phase) {
                        LevelPhase.MENU -> {
                            waitUp(id)
                            state.phase = LevelPhase.SELECT
                            haptics.squish()
                        }

                        LevelPhase.SELECT -> {
                            val up = waitUp(id) ?: start
                            val hit = hitLevelCell(up, w, h, allLevels().size)
                            if (hit != null) {
                                val levelId = hit + 1
                                if (levelId <= state.unlocked) {
                                    layoutPlayfield(state, w, h)
                                    startLevel(state, hit)
                                    haptics.squish()
                                }
                            } else if (up.y > h * 0.88f) {
                                state.phase = LevelPhase.MENU
                            }
                        }

                        LevelPhase.PLAYING -> {
                            val grabbed = tryBeginAim(state, start)
                            if (grabbed) haptics.rustle()
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == id } ?: break
                                if (ch.changedToUp()) {
                                    ch.consume()
                                    if (state.aiming) {
                                        val ok = releaseAim(state)
                                        if (ok) haptics.thud() else cancelAim(state)
                                    }
                                    break
                                }
                                if (ch.pressed) {
                                    if (state.aiming) {
                                        updateAim(state, ch.position)
                                    } else if (!grabbed && tryBeginAim(state, ch.position)) {
                                        haptics.rustle()
                                    }
                                    ch.consume()
                                }
                            }
                        }

                        LevelPhase.WIN -> {
                            val up = waitUp(id) ?: start
                            when {
                                up.y in h * 0.58f..h * 0.70f -> {
                                    val next = state.levelIndex + 1
                                    if (next < allLevels().size && next + 1 <= state.unlocked) {
                                        startLevel(state, next)
                                    } else {
                                        state.phase = LevelPhase.SELECT
                                    }
                                    haptics.squish()
                                }
                                up.y in h * 0.72f..h * 0.84f -> {
                                    startLevel(state, state.levelIndex)
                                    haptics.rustle()
                                }
                                else -> state.phase = LevelPhase.SELECT
                            }
                        }

                        LevelPhase.LOSE -> {
                            val up = waitUp(id) ?: start
                            when {
                                up.y in h * 0.58f..h * 0.72f -> {
                                    startLevel(state, state.levelIndex)
                                    haptics.squish()
                                }
                                else -> state.phase = LevelPhase.SELECT
                            }
                        }
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frame
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)

        drawBackground(w, h)
        when (state.phase) {
            LevelPhase.MENU -> drawMenu(state, textMeasurer, w, h)
            LevelPhase.SELECT -> drawSelect(state, textMeasurer, w, h)
            LevelPhase.PLAYING, LevelPhase.WIN, LevelPhase.LOSE -> {
                drawPlayfield(state, textMeasurer, w, h)
                if (state.phase == LevelPhase.WIN) drawWin(state, textMeasurer, w, h)
                if (state.phase == LevelPhase.LOSE) drawLose(state, textMeasurer, w, h)
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitUp(
    id: androidx.compose.ui.input.pointer.PointerId
): Offset? {
    while (true) {
        val event = awaitPointerEvent()
        val ch = event.changes.firstOrNull { it.id == id } ?: return null
        if (ch.positionChange() != Offset.Zero) ch.consume()
        if (ch.changedToUp()) {
            ch.consume()
            return ch.position
        }
    }
}

private fun loadStars(json: String): MutableMap<Int, Int> {
    val map = mutableMapOf<Int, Int>()
    try {
        val o = JSONObject(json)
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k.toInt()] = o.getInt(k)
        }
    } catch (_: Exception) {}
    return map
}

private fun saveStars(map: Map<Int, Int>): String {
    val o = JSONObject()
    map.forEach { (k, v) -> o.put(k.toString(), v) }
    return o.toString()
}

private fun hitLevelCell(pos: Offset, w: Float, h: Float, count: Int): Int? {
    val cols = 4
    val rows = (count + cols - 1) / cols
    val gridTop = h * 0.28f
    val gridH = h * 0.52f
    val cellW = w * 0.2f
    val cellH = gridH / rows
    val gapX = w * 0.04f
    val totalW = cols * cellW + (cols - 1) * gapX
    val startX = (w - totalW) / 2f
    for (i in 0 until count) {
        val col = i % cols
        val row = i / cols
        val x = startX + col * (cellW + gapX)
        val y = gridTop + row * cellH
        if (pos.x in x..(x + cellW) && pos.y in y..(y + cellH * 0.85f)) return i
    }
    return null
}

private fun DrawScope.drawBackground(w: Float, h: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF0F3460))
        ),
        size = Size(w, h)
    )
}

private fun DrawScope.drawMenu(state: LevelState, tm: TextMeasurer, w: Float, h: Float) {
    banner(tm, "果冻弹弹", w * 0.5f, h * 0.22f, Color(0xFFFF6B8A), 40.sp)
    banner(tm, "软体弹射闯关", w * 0.5f, h * 0.30f, Color(0xFF94A3B8), 16.sp)

    val cardW = w * 0.84f
    val cardH = h * 0.30f
    val cx = (w - cardW) / 2f
    val cy = h * 0.36f
    drawRoundRect(Color(0xEE1E293B), Offset(cx, cy), Size(cardW, cardH), CornerRadius(20f, 20f))
    banner(tm, "怎么玩", w * 0.5f, cy + 22f, Color(0xFFFFD700), 18.sp)
    val lines = listOf(
        "1. 按住果冻，向后拉蓄力",
        "2. 松手发射，飞进发光圈",
        "3. 发射次数少 = 更多星星",
        "4. 过关解锁下一关"
    )
    lines.forEachIndexed { i, s ->
        val layout = tm.measure(s, TextStyle(color = Color(0xFFE2E8F0), fontSize = 15.sp))
        drawText(layout, topLeft = Offset(cx + 40f, cy + 72f + i * 42f))
    }

    val bw = w * 0.55f
    val bh = 70f
    val bx = (w - bw) / 2f
    val by = h * 0.72f
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(Color(0xFFFF6B8A), Color(0xFFFFA94D))),
        topLeft = Offset(bx, by),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(22f, 22f)
    )
    banner(tm, "开始闯关", w * 0.5f, by + 18f, Color.White, 24.sp)
}

private fun DrawScope.drawSelect(state: LevelState, tm: TextMeasurer, w: Float, h: Float) {
    banner(tm, "选择关卡", w * 0.5f, h * 0.14f, Color.White, 28.sp)
    banner(tm, "已解锁 ${state.unlocked}/${allLevels().size}", w * 0.5f, h * 0.20f, Color(0xFF94A3B8), 14.sp)

    val levels = allLevels()
    val cols = 4
    val rows = (levels.size + cols - 1) / cols
    val gridTop = h * 0.28f
    val gridH = h * 0.52f
    val cellW = w * 0.2f
    val cellH = gridH / rows
    val gapX = w * 0.04f
    val totalW = cols * cellW + (cols - 1) * gapX
    val startX = (w - totalW) / 2f

    levels.forEachIndexed { i, def ->
        val col = i % cols
        val row = i / cols
        val x = startX + col * (cellW + gapX)
        val y = gridTop + row * cellH
        val unlocked = def.id <= state.unlocked
        val star = state.stars[def.id] ?: 0
        drawRoundRect(
            color = if (unlocked) Color(0xFF1E293B) else Color(0xFF0F172A),
            topLeft = Offset(x, y),
            size = Size(cellW, cellH * 0.8f),
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawRoundRect(
            color = if (unlocked) Color(0xFF4ECDC4) else Color(0xFF334155),
            topLeft = Offset(x, y),
            size = Size(cellW, cellH * 0.8f),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(3f)
        )
        banner(
            tm,
            if (unlocked) "${def.id}" else "锁",
            x + cellW / 2f,
            y + cellH * 0.18f,
            if (unlocked) Color.White else Color(0xFF64748B),
            22.sp
        )
        if (unlocked && star > 0) {
            banner(tm, "★".repeat(star), x + cellW / 2f, y + cellH * 0.48f, Color(0xFFFFD700), 12.sp)
        }
    }
    banner(tm, "返回标题", w * 0.5f, h * 0.90f, Color(0xFF94A3B8), 14.sp)
}

private fun DrawScope.drawPlayfield(state: LevelState, tm: TextMeasurer, w: Float, h: Float) {
    val def = allLevels()[state.levelIndex]
    val play = state.play

    // play panel
    drawRoundRect(
        color = Color(0xFF1A1528),
        topLeft = Offset(play.left, play.top),
        size = Size(play.width, play.height),
        cornerRadius = CornerRadius(18f, 18f)
    )

    // walls
    for (wn in def.walls) {
        val a = Offset(play.left + wn.left * play.width, play.top + wn.top * play.height)
        val b = Offset(play.left + wn.right * play.width, play.top + wn.bottom * play.height)
        val left = min(a.x, b.x)
        val top = min(a.y, b.y)
        val rw = kotlin.math.abs(b.x - a.x)
        val rh = kotlin.math.abs(b.y - a.y)
        drawRoundRect(wn.color, Offset(left, top), Size(rw, rh), CornerRadius(8f, 8f))
        if (wn.bounce > 0.8f) {
            // bounce pad stripes
            drawRoundRect(
                Color(0x6622C55E),
                Offset(left, top),
                Size(rw, rh),
                CornerRadius(8f, 8f),
                style = Stroke(4f)
            )
        }
    }

    // goal
    val gc = Offset(play.left + def.goal.cx * play.width, play.top + def.goal.cy * play.height)
    val gr = def.goal.r * min(play.width, play.height)
    val pulse = 0.75f + 0.25f * sin(state.pulse * 3f)
    drawCircle(Color(0x334ECDC4), gr * 1.25f * pulse, gc)
    drawCircle(Color(0xFF0B1020), gr * 0.92f, gc)
    drawCircle(Color(0xFF4ECDC4).copy(alpha = 0.9f), gr, gc, style = Stroke(5f * pulse))
    banner(tm, "GOAL", gc.x, gc.y - 10f, Color(0xFF4ECDC4), 12.sp)

    // trajectory
    if (state.aiming) {
        val pts = aimTrajectory(state)
        pts.forEachIndexed { i, p ->
            drawCircle(Color.White.copy(alpha = 0.55f - i * 0.03f), 5f - i * 0.15f, p)
        }
        val pull = launchImpulseVector(state)
        drawLine(
            Color(0xFFFFD700),
            state.bodyCenter,
            state.bodyCenter - pull,
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }

    // jelly soft body
    drawJelly(state)

    // particles
    for (p in state.particles) {
        val a = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(p.color.copy(alpha = a), p.size, Offset(p.x, p.y))
    }

    // HUD
    val hudY = h * 0.055f
    drawRoundRect(Color(0xCC0B1020), Offset(16f, hudY), Size(w - 32f, 78f), CornerRadius(14f, 14f))
    banner(tm, "第${def.id}关 · ${def.title}", w * 0.5f, hudY + 12f, Color.White, 17.sp)
    banner(
        tm,
        "剩余 ${state.shotsLeft} 次发射   三星标准 ${def.par} 次",
        w * 0.5f,
        hudY + 44f,
        Color(0xFFFFD700),
        13.sp
    )

    if (state.messageT > 0f && state.phase == LevelPhase.PLAYING) {
        banner(tm, state.message, w * 0.5f, play.top + 24f, Color.White, 15.sp)
    }

    if (state.canAim && !state.aiming && state.phase == LevelPhase.PLAYING) {
        banner(tm, "按住果冻拉一下", state.bodyCenter.x, state.bodyCenter.y - state.bodyRadius - 28f, Color(0xAAFFFFFF), 12.sp)
    }
}

private fun DrawScope.drawJelly(state: LevelState) {
    val verts = state.softVerts
    if (verts.size < 3) {
        drawCircle(Color(0xFFFF6B8A), state.bodyRadius, state.bodyCenter)
        return
    }
    val c = state.bodyCenter
    val squash = 1f - state.squash * 0.25f
    val stretch = 1f + state.squash * 0.2f
    val path = Path()
    fun map(p: Offset): Offset {
        val d = p - c
        return c + Offset(d.x * stretch, d.y * squash)
    }
    val p0 = map(verts[0])
    path.moveTo(p0.x, p0.y)
    for (i in 1 until verts.size) {
        val p = map(verts[i])
        path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, Color(0xFFFFD0DA))
    drawPath(path, Color(0xFFFF6B8A), style = Stroke(5f))
    drawCircle(Color.White.copy(alpha = 0.4f), state.bodyRadius * 0.22f, c + Offset(-state.bodyRadius * 0.25f, -state.bodyRadius * 0.28f))
    if (state.aiming) {
        drawCircle(Color.White.copy(alpha = 0.35f), state.bodyRadius * 1.35f, c, style = Stroke(3f))
    }
}

private fun DrawScope.drawWin(state: LevelState, tm: TextMeasurer, w: Float, h: Float) {
    drawRect(Color(0xBB020617))
    banner(tm, "过关！", w * 0.5f, h * 0.28f, Color(0xFFFFD700), 36.sp)
    banner(tm, "★".repeat(state.winStars.coerceIn(1, 3)), w * 0.5f, h * 0.38f, Color(0xFFFFD700), 32.sp)
    banner(tm, "用了 ${state.shotsUsed} 次发射", w * 0.5f, h * 0.48f, Color.White, 16.sp)

    button(tm, "下一关 / 选关", w, h * 0.60f, true)
    button(tm, "再玩一次", w, h * 0.74f, false)
}

private fun DrawScope.drawLose(state: LevelState, tm: TextMeasurer, w: Float, h: Float) {
    drawRect(Color(0xBB020617))
    banner(tm, "失败", w * 0.5f, h * 0.32f, Color(0xFFFF6B8A), 34.sp)
    banner(tm, state.message, w * 0.5f, h * 0.42f, Color.White, 16.sp)
    button(tm, "重试本关", w, h * 0.60f, true)
    button(tm, "关卡列表", w, h * 0.74f, false)
}

private fun DrawScope.button(tm: TextMeasurer, text: String, w: Float, y: Float, primary: Boolean) {
    val bw = w * 0.55f
    val bh = 60f
    val bx = (w - bw) / 2f
    if (primary) {
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFFFF6B8A), Color(0xFFFFA94D))),
            topLeft = Offset(bx, y),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(18f, 18f)
        )
    } else {
        drawRoundRect(Color(0xFF334155), Offset(bx, y), Size(bw, bh), CornerRadius(18f, 18f))
    }
    banner(tm, text, w * 0.5f, y + 14f, Color.White, 18.sp)
}

private fun DrawScope.banner(
    tm: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit
) {
    val layout = tm.measure(text, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y))
}
