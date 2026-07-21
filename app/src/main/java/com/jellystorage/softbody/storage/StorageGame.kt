package com.jellystorage.softbody.storage

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
import androidx.compose.ui.unit.sp
import com.jellystorage.softbody.rememberHapticAudioManager
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val PREFS = "jelly_storage_prefs"
private const val KEY_BEST = "best_score"

@Composable
fun StorageGame(
    modifier: Modifier = Modifier,
    textMeasurer: TextMeasurer = rememberTextMeasurer()
) {
    val context = LocalContext.current
    val haptics = rememberHapticAudioManager()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val state = remember {
        StorageState(bestScore = prefs.getInt(KEY_BEST, 0))
    }
    var frame by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var lastPackTotal by remember { mutableStateOf(0) }
    var lastPhase by remember { mutableStateOf(state.phase) }

    DisposableEffect(Unit) {
        onDispose { haptics.release() }
    }

    LaunchedEffect(size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                if (prev == 0L) {
                    prev = now
                    return@withFrameNanos
                }
                val dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.05f)
                prev = now
                updateStorage(state, dt)
                // Haptics / feedback hooks
                if (state.totalPacked > lastPackTotal) {
                    haptics.onPackSuccess()
                    lastPackTotal = state.totalPacked
                    prefs.edit().putInt(KEY_BEST, state.bestScore).apply()
                }
                if (state.phase != lastPhase) {
                    when (state.phase) {
                        GamePhase.UPGRADE -> haptics.vibrateSnap()
                        GamePhase.GAME_OVER -> haptics.thud()
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
            .onSizeChanged { newSize ->
                size = newSize
                if (newSize.width > 0 && newSize.height > 0) {
                    layoutSlots(state, newSize.width.toFloat(), newSize.height.toFloat())
                }
            }
            // Single gesture pipeline — tap + drag must not fight each other
            // (detectDragGestures was swallowing menu taps).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startPos = down.position
                    val pointerId = down.id
                    val phaseAtDown = state.phase
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()

                    when (phaseAtDown) {
                        GamePhase.MENU -> {
                            // Any press+release starts the game
                            var upPos = startPos
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (change.positionChange() != Offset.Zero) change.consume()
                                if (change.changedToUp()) {
                                    upPos = change.position
                                    change.consume()
                                    break
                                }
                            }
                            if (w > 1f && h > 1f) {
                                startRun(state, prefs.getInt(KEY_BEST, 0), w, h)
                                lastPackTotal = 0
                                haptics.squish()
                            }
                        }

                        GamePhase.GAME_OVER -> {
                            var upPos = startPos
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (change.changedToUp()) {
                                    upPos = change.position
                                    change.consume()
                                    break
                                }
                            }
                            if (w <= 1f || h <= 1f) return@awaitEachGesture
                            when {
                                upPos.y in h * 0.60f..h * 0.73f &&
                                    upPos.x in w * 0.20f..w * 0.80f -> {
                                    startRun(state, prefs.getInt(KEY_BEST, 0), w, h)
                                    lastPackTotal = 0
                                    haptics.squish()
                                }
                                upPos.y in h * 0.73f..h * 0.88f &&
                                    upPos.x in w * 0.20f..w * 0.80f -> {
                                    state.phase = GamePhase.MENU
                                    haptics.rustle()
                                }
                            }
                        }

                        GamePhase.UPGRADE -> {
                            var upPos = startPos
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (change.changedToUp()) {
                                    upPos = change.position
                                    change.consume()
                                    break
                                }
                            }
                            val choice = hitUpgradeCard(state, upPos, w, h)
                                ?: hitUpgradeCard(state, startPos, w, h)
                            if (choice != null) {
                                applyUpgrade(state, choice)
                                beginNextWave(state)
                                haptics.vibrateSnap()
                            }
                        }

                        GamePhase.PLAYING -> {
                            val grabbed = tryGrab(state, startPos)
                            if (grabbed) haptics.rustle()
                            var lastNanos = System.nanoTime()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (change.changedToUp()) {
                                    change.consume()
                                    if (state.grabId >= 0) {
                                        val flung = releaseGrab(state)
                                        if (flung) haptics.thud() else haptics.squish()
                                    }
                                    break
                                }
                                if (change.pressed && state.grabId >= 0) {
                                    val now = System.nanoTime()
                                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                                    lastNanos = now
                                    moveGrab(state, change.position, dt)
                                    change.consume()
                                } else if (change.pressed && !grabbed) {
                                    // Try grab again if finger slid onto a jelly
                                    if (tryGrab(state, change.position)) {
                                        haptics.rustle()
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // force recompose dependency
        @Suppress("UNUSED_EXPRESSION")
        frame

        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        val shakeOff = if (state.shake > 0f) {
            Offset(
                (Random.nextFloat() - 0.5f) * state.shake,
                (Random.nextFloat() - 0.5f) * state.shake
            )
        } else Offset.Zero

        withTransform({
            translate(shakeOff.x, shakeOff.y)
        }) {
            drawDesk(w, h, state)
            drawSlots(state)
            drawJellies(state)
            drawParticles(state)
            drawFloatTexts(state, textMeasurer)
        }
        drawHud(state, textMeasurer, w, h)

        when (state.phase) {
            GamePhase.MENU -> drawMenu(state, textMeasurer, w, h)
            GamePhase.UPGRADE -> drawUpgrade(state, textMeasurer, w, h)
            GamePhase.GAME_OVER -> drawGameOver(state, textMeasurer, w, h)
            GamePhase.PLAYING -> {
                drawTutorialCoach(state, textMeasurer, w, h)
                if (state.announceT > 0f && state.tutorialStep >= 2) {
                    drawCenteredBanner(
                        textMeasurer,
                        state.announce,
                        w * 0.5f,
                        h * 0.20f,
                        Color(0xFFFFD700),
                        26.sp
                    )
                }
            }
        }
    }
}

private fun hitUpgradeCard(state: StorageState, pos: Offset, w: Float, h: Float): String? {
    val cards = state.upgradeChoices
    if (cards.isEmpty()) return null
    val cardW = min(w * 0.28f, 220f)
    val cardH = h * 0.28f
    val gap = w * 0.03f
    val total = cards.size * cardW + (cards.size - 1) * gap
    var x0 = (w - total) * 0.5f
    val y0 = h * 0.38f
    for (c in cards) {
        if (pos.x in x0..(x0 + cardW) && pos.y in y0..(y0 + cardH)) return c.id
        x0 += cardW + gap
    }
    return null
}

private fun DrawScope.drawDesk(w: Float, h: Float, state: StorageState) {
    // Background gradient
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
        ),
        size = Size(w, h)
    )
    // Desk surface
    val deskTop = state.playY0
    val deskBot = state.playY1
    drawRoundRect(
        color = Color(0xFF2A1F18),
        topLeft = Offset(0f, deskTop - 8f),
        size = Size(w, deskBot - deskTop + 16f),
        cornerRadius = CornerRadius(18f, 18f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF5C4033), Color(0xFF3E2723))
        ),
        topLeft = Offset(8f, deskTop),
        size = Size(w - 16f, deskBot - deskTop),
        cornerRadius = CornerRadius(14f, 14f)
    )
    // subtle wood grain lines
    var gy = deskTop + 24f
    while (gy < deskBot) {
        drawLine(
            color = Color(0x22FFFFFF),
            start = Offset(20f, gy),
            end = Offset(w - 20f, gy),
            strokeWidth = 1.2f
        )
        gy += 28f
    }
    // Shelf for bins
    drawRoundRect(
        color = Color(0xFF1B1410),
        topLeft = Offset(0f, deskBot + 4f),
        size = Size(w, h - deskBot),
        cornerRadius = CornerRadius(0f, 0f)
    )
}

private fun DrawScope.drawSlots(state: StorageState) {
    val widen = state.mods.slotWiden
    val u = state.unit
    for (slot in state.slots) {
        val mouth = slot.mouth
        val hw = slot.kind.mouthHalfWidth * widen * u
        val jarH = 110f * u
        val jarW = hw * 2.1f
        val left = mouth.x - jarW / 2f
        val top = mouth.y
        val flash = slot.flash
        val base = slot.kind.color
        val bodyColor = base.copy(alpha = 0.35f + flash * 0.35f)

        // Jar body
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(left, top),
            size = Size(jarW, jarH),
            cornerRadius = CornerRadius(16f * u, 16f * u)
        )
        drawRoundRect(
            color = base.copy(alpha = 0.85f),
            topLeft = Offset(left, top),
            size = Size(jarW, jarH),
            cornerRadius = CornerRadius(16f * u, 16f * u),
            style = Stroke(width = 3.5f * u)
        )
        // Mouth opening
        drawOval(
            color = Color(0xFF0B0B14).copy(alpha = 0.75f),
            topLeft = Offset(mouth.x - hw, mouth.y - 10f * u),
            size = Size(hw * 2f, 22f * u)
        )
        drawOval(
            color = base,
            topLeft = Offset(mouth.x - hw, mouth.y - 10f * u),
            size = Size(hw * 2f, 22f * u),
            style = Stroke(width = 3f * u)
        )
        // Fill level dots
        val fillFrac = (slot.filled % 8) / 8f
        if (fillFrac > 0f) {
            drawRoundRect(
                color = base.copy(alpha = 0.45f),
                topLeft = Offset(left + 10f * u, top + jarH - 14f * u - fillFrac * (jarH - 30f * u)),
                size = Size(jarW - 20f * u, fillFrac * (jarH - 30f * u) + 8f * u),
                cornerRadius = CornerRadius(10f * u, 10f * u)
            )
        }
        // Accept radius hint ring
        val r = slot.kind.maxRadius * widen * u
        drawCircle(
            color = base.copy(alpha = 0.12f + flash * 0.2f),
            radius = r,
            center = Offset(mouth.x, mouth.y - 36f * u)
        )
    }
}

private fun DrawScope.drawJellies(state: StorageState) {
    for (jelly in state.jellies) {
        val rim = jellyPalette[jelly.colorIdx % jellyPalette.size]
        val fill = jellyBodyPalette[jelly.colorIdx % jellyBodyPalette.size]
        val pack = jelly.packT.coerceIn(0f, 1f)
        val scale = (if (jelly.packing) (1f - pack) * 0.85f + 0.15f else 1f) * jelly.grabScale
        val alpha = if (jelly.packing) (1f - pack * 0.85f) else 1f
        val path = Path()
        val verts = jelly.body.boundaryPositions
        if (verts.isEmpty()) continue
        val c = jelly.body.center
        val baseR = jelly.body.restRadius().coerceAtLeast(state.u(jelly.kind.radius))
        fun scaled(p: Offset): Offset {
            val d = p - c
            return c + d * scale
        }
        val s0 = scaled(verts[0])
        path.moveTo(s0.x, s0.y)
        for (i in 1 until verts.size) {
            val s = scaled(verts[i])
            path.lineTo(s.x, s.y)
        }
        path.close()

        drawPath(path, fill.copy(alpha = 0.92f * alpha))
        drawPath(path, rim.copy(alpha = 0.95f * alpha), style = Stroke(width = 4f * scale * state.unit.coerceAtLeast(1f)))

        // Highlight blob
        val hl = c + Offset(-baseR * 0.28f * scale, -baseR * 0.3f * scale)
        drawCircle(
            color = Color.White.copy(alpha = 0.35f * alpha),
            radius = baseR * 0.22f * scale,
            center = hl
        )
        // Kind mark
        when (jelly.kind) {
            JellyKind.STICKY -> {
                drawCircle(Color(0x8822C55E), baseR * 0.12f * scale, c + Offset(10f * state.unit, 8f * state.unit))
                drawCircle(Color(0x8822C55E), baseR * 0.09f * scale, c + Offset(-12f * state.unit, 14f * state.unit))
            }
            JellyKind.HEAVY -> {
                drawCircle(Color.Black.copy(alpha = 0.12f * alpha), baseR * 0.35f * scale, c + Offset(0f, 6f * state.unit))
            }
            JellyKind.BOUNCY -> {
                val wob = jelly.wobble
                val p2 = c + Offset(cos(wob) * 10f * state.unit, sin(wob * 1.3f) * 6f * state.unit)
                drawCircle(rim.copy(alpha = 0.5f * alpha), 5f * scale * state.unit, p2)
            }
            JellyKind.TINY -> {
                drawCircle(rim.copy(alpha = 0.7f * alpha), 3.5f * scale * state.unit, c)
            }
            JellyKind.NORMAL -> {}
        }

        // Grab ring
        if (jelly.id == state.grabId) {
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = baseR * 1.25f,
                center = c,
                style = Stroke(width = 3f * state.unit)
            )
        }
    }
}

private fun DrawScope.drawParticles(state: StorageState) {
    for (p in state.particles) {
        val a = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(p.color.copy(alpha = a), p.size, Offset(p.x, p.y))
    }
}

private fun DrawScope.drawFloatTexts(state: StorageState, tm: TextMeasurer) {
    for (t in state.floatTexts) {
        val a = (t.life / t.maxLife).coerceIn(0f, 1f)
        val layout = tm.measure(
            t.text,
            style = TextStyle(
                color = t.color.copy(alpha = a),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        drawText(layout, topLeft = Offset(t.x - layout.size.width / 2f, t.y))
    }
}

private fun DrawScope.drawHud(state: StorageState, tm: TextMeasurer, w: Float, h: Float) {
    if (state.phase == GamePhase.MENU) return

    // Top bar — sit below status bar / cutout
    val hudTop = h * 0.055f
    drawRoundRect(
        color = Color(0xCC0B1020),
        topLeft = Offset(10f, hudTop),
        size = Size(w - 20f, 78f),
        cornerRadius = CornerRadius(16f, 16f)
    )

    fun label(text: String, x: Float, y: Float, color: Color = Color.White, sizeSp: Float = 15f) {
        val layout = tm.measure(
            text,
            style = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.SemiBold)
        )
        drawText(layout, topLeft = Offset(x, y))
    }

    label("得分 ${state.score}", 24f, hudTop + 14f, Color(0xFFFFD700), 18f)
    label("最高 ${state.bestScore}", 24f, hudTop + 42f, Color(0xFFAAAAAA), 13f)

    val order = "订单 ${state.wave}  ${state.packedThisWave}/${state.waveGoal}"
    val orderLayout = tm.measure(
        order,
        style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    )
    drawText(orderLayout, topLeft = Offset((w - orderLayout.size.width) / 2f, hudTop + 16f))

    // Timer bar
    val tFrac = (state.waveTimeLeft / state.waveTimeMax).coerceIn(0f, 1f)
    val barW = w * 0.34f
    val barX = w - barW - 24f
    drawRoundRect(Color(0xFF333344), Offset(barX, hudTop + 20f), Size(barW, 14f), CornerRadius(8f, 8f))
    val tColor = when {
        tFrac < 0.25f -> Color(0xFFEF4444)
        tFrac < 0.5f -> Color(0xFFFFA94D)
        else -> Color(0xFF22C55E)
    }
    drawRoundRect(tColor, Offset(barX, hudTop + 20f), Size(barW * tFrac, 14f), CornerRadius(8f, 8f))
    label("${state.waveTimeLeft.toInt()}s", barX + barW * 0.5f - 12f, hudTop + 42f, Color(0xFFCCCCCC), 12f)

    // Chaos capacity
    if (state.phase == GamePhase.PLAYING) {
        val live = state.jellies.count { !it.packing }
        val cap = chaosCapFor(state.wave, state.mods)
        val chaosFrac = live.toFloat() / cap.toFloat()
        val cw = w * 0.5f
        val cx = (w - cw) / 2f
        val cy = state.playY0 - 18f
        drawRoundRect(Color(0x55331111), Offset(cx, cy), Size(cw, 8f), CornerRadius(4f, 4f))
        drawRoundRect(
            if (chaosFrac > 0.8f) Color(0xFFEF4444) else Color(0xFFFF6B8A),
            Offset(cx, cy),
            Size(cw * chaosFrac, 8f),
            CornerRadius(4f, 4f)
        )
        if (state.combo >= 2) {
            label("连击 x${state.combo}", w * 0.5f - 48f, state.playY0 + 8f, Color(0xFFFF4500), 16f)
        }
        label("桌面 $live/$cap", cx + cw + 8f, cy - 4f, Color(0xFFCCAAAA), 11f)
    }

    // Slot labels
    for (slot in state.slots) {
        val layout = tm.measure(
            slot.kind.label,
            style = TextStyle(color = slot.kind.color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        )
        drawText(
            layout,
            topLeft = Offset(slot.mouth.x - layout.size.width / 2f, slot.mouth.y + state.u(118f))
        )
        val fit = "≤${(slot.kind.maxRadius * state.mods.slotWiden).toInt()}"
        val fl = tm.measure(
            fit,
            style = TextStyle(color = Color(0xFF888899), fontSize = 10.sp)
        )
        drawText(fl, topLeft = Offset(slot.mouth.x - fl.size.width / 2f, slot.mouth.y + state.u(134f)))
    }
}

private fun DrawScope.drawCenteredBanner(
    tm: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit
) {
    val layout = tm.measure(
        text,
        style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold)
    )
    val pad = 16f
    drawRoundRect(
        color = Color(0xAA000000),
        topLeft = Offset(x - layout.size.width / 2f - pad, y - pad * 0.5f),
        size = Size(layout.size.width + pad * 2f, layout.size.height + pad),
        cornerRadius = CornerRadius(14f, 14f)
    )
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y))
}

private fun DrawScope.drawMenu(state: StorageState, tm: TextMeasurer, w: Float, h: Float) {
    drawRect(Color(0xCC0A0A14))
    drawCenteredBanner(tm, "果冻收纳", w * 0.5f, h * 0.16f, Color(0xFFFF6B8A), 36.sp)
    drawCenteredBanner(tm, "Jelly Storage", w * 0.5f, h * 0.22f, Color(0xFF99AABC), 14.sp)

    // How to play card
    val cardW = w * 0.86f
    val cardH = h * 0.28f
    val cardX = (w - cardW) / 2f
    val cardY = h * 0.28f
    drawRoundRect(
        color = Color(0xEE1E293B),
        topLeft = Offset(cardX, cardY),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(18f, 18f)
    )
    drawCenteredBanner(tm, "怎么玩（超简单）", w * 0.5f, cardY + cardH * 0.12f, Color(0xFFFFD700), 18.sp)
    val steps = listOf(
        "1. 手指按住桌子上的彩色果冻",
        "2. 拖到底部绿色「宽口箱」洞口",
        "3. 松手塞进去 → 得分！",
        "4. 塞够数量过关；桌面满了/超时就失败"
    )
    steps.forEachIndexed { i, line ->
        val layout = tm.measure(
            line,
            style = TextStyle(color = Color(0xFFE2E8F0), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
        drawText(
            layout,
            topLeft = Offset(cardX + cardW * 0.08f, cardY + cardH * (0.32f + i * 0.15f))
        )
    }

    drawCenteredBanner(tm, "最高分  ${state.bestScore}", w * 0.5f, h * 0.60f, Color(0xFFFFD700), 18.sp)

    val bw = w * 0.55f
    val bh = 64f
    val bx = (w - bw) / 2f
    val by = h * 0.66f
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(Color(0xFFFF6B8A), Color(0xFFFFA94D))),
        topLeft = Offset(bx, by),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(20f, 20f)
    )
    val play = tm.measure(
        "点这里开始",
        style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    )
    drawText(play, topLeft = Offset((w - play.size.width) / 2f, by + 16f))

    drawCenteredBanner(
        tm,
        "宽口=最好装  ·  中罐=一般  ·  窄槽=只装小果冻",
        w * 0.5f,
        h * 0.80f,
        Color(0xFF99AABC),
        12.sp
    )
}

private fun DrawScope.drawUpgrade(state: StorageState, tm: TextMeasurer, w: Float, h: Float) {
    drawRect(Color(0xBB050510))
    drawCenteredBanner(tm, "班次升级", w * 0.5f, h * 0.18f, Color(0xFFFFD700), 28.sp)
    drawCenteredBanner(tm, "选一个强化，点卡片继续", w * 0.5f, h * 0.26f, Color(0xFFCCCCDD), 14.sp)

    val cards = state.upgradeChoices
    val cardW = min(w * 0.28f, 220f)
    val cardH = h * 0.28f
    val gap = w * 0.03f
    val total = cards.size * cardW + (cards.size - 1).coerceAtLeast(0) * gap
    var x0 = (w - total) * 0.5f
    val y0 = h * 0.38f
    for (c in cards) {
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(x0, y0),
            size = Size(cardW, cardH),
            cornerRadius = CornerRadius(18f, 18f)
        )
        drawRoundRect(
            color = Color(0xFF4ECDC4),
            topLeft = Offset(x0, y0),
            size = Size(cardW, cardH),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 3f)
        )
        val title = tm.measure(
            c.title,
            style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
        drawText(title, topLeft = Offset(x0 + (cardW - title.size.width) / 2f, y0 + 28f))
        val desc = tm.measure(
            c.desc,
            style = TextStyle(color = Color(0xFFBBCCDD), fontSize = 12.sp)
        )
        drawText(desc, topLeft = Offset(x0 + 12f, y0 + 70f))
        x0 += cardW + gap
    }
}

private fun DrawScope.drawGameOver(state: StorageState, tm: TextMeasurer, w: Float, h: Float) {
    drawRect(Color(0xCC0A0A14))
    drawCenteredBanner(tm, "本班结束", w * 0.5f, h * 0.22f, Color(0xFFFF6B8A), 32.sp)
    drawCenteredBanner(tm, state.failedReason, w * 0.5f, h * 0.32f, Color(0xFFEEEEFF), 16.sp)
    drawCenteredBanner(tm, "得分  ${state.score}", w * 0.5f, h * 0.42f, Color(0xFFFFD700), 26.sp)
    drawCenteredBanner(
        tm,
        "装箱 ${state.totalPacked}  ·  订单 ${state.wave}",
        w * 0.5f,
        h * 0.50f,
        Color(0xFFAABBCC),
        14.sp
    )
    drawCenteredBanner(tm, "最高  ${state.bestScore}", w * 0.5f, h * 0.56f, Color(0xFF88AACC), 14.sp)

    val bw = w * 0.5f
    val bh = 56f
    val bx = (w - bw) / 2f
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(Color(0xFFFF6B8A), Color(0xFFFFA94D))),
        topLeft = Offset(bx, h * 0.64f),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(18f, 18f)
    )
    val retry = tm.measure(
        "再来一局",
        style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    )
    drawText(retry, topLeft = Offset((w - retry.size.width) / 2f, h * 0.64f + 14f))

    drawRoundRect(
        color = Color(0xFF334155),
        topLeft = Offset(bx, h * 0.76f),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(18f, 18f)
    )
    val menu = tm.measure(
        "回菜单",
        style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    )
    drawText(menu, topLeft = Offset((w - menu.size.width) / 2f, h * 0.76f + 15f))
}

/** Finger coach: hint text + pulsing arrow toward Wide bin for first pack. */
private fun DrawScope.drawTutorialCoach(state: StorageState, tm: TextMeasurer, w: Float, h: Float) {
    if (state.tutorialStep >= 3 && state.tutorialT <= 0f) return
    if (state.tutorialHint.isBlank()) return

    val show = state.tutorialStep < 3 || state.tutorialT > 0f
    if (!show) return

    drawCenteredBanner(
        tm,
        state.tutorialHint,
        w * 0.5f,
        h * 0.24f,
        Color.White.copy(alpha = 0.95f),
        17.sp
    )

    // Arrow from first jelly (or desk center) down to Wide bin while learning
    if (state.tutorialStep <= 1) {
        val jelly = state.jellies.firstOrNull { !it.packing }
        val wide = state.slots.firstOrNull()
        if (jelly != null && wide != null) {
            val from = jelly.body.center
            val to = wide.mouth + Offset(0f, state.u(20f))
            val pulse = 0.55f + 0.45f * sin(state.pulseT * 4f)
            drawLine(
                color = Color(0xFFFFD700).copy(alpha = 0.35f + 0.45f * pulse),
                start = from,
                end = to,
                strokeWidth = 6f * state.unit,
                cap = StrokeCap.Round
            )
            // arrow head
            val dir = to - from
            val len = dir.getDistance().coerceAtLeast(1f)
            val n = dir / len
            val left = Offset(-n.y, n.x)
            val tip = to
            val base = to - n * state.u(28f)
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(base.x + left.x * state.u(14f), base.y + left.y * state.u(14f))
                lineTo(base.x - left.x * state.u(14f), base.y - left.y * state.u(14f))
                close()
            }
            drawPath(path, Color(0xFFFFD700).copy(alpha = 0.5f + 0.5f * pulse))

            // Pulse ring on wide bin
            drawCircle(
                color = Color(0xFF4ECDC4).copy(alpha = 0.25f + 0.35f * pulse),
                radius = state.u(70f) * (0.9f + 0.15f * pulse),
                center = wide.mouth,
                style = Stroke(width = 4f * state.unit)
            )
            drawCenteredBanner(
                tm,
                "松手进洞",
                wide.mouth.x,
                wide.mouth.y - state.u(70f),
                Color(0xFF4ECDC4),
                13.sp
            )
        }
    }
}
