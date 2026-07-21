package com.jellystorage.run

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellystorage.engine.battle.BattleSimulator
import com.jellystorage.engine.battle.BattleStatus
import com.jellystorage.engine.battle.BattleViewState
import com.jellystorage.engine.model.Weapon
import com.jellystorage.engine.model.evaluateSynergies
import com.jellystorage.ui.canvas.JellyArenaCanvas
import kotlin.math.sin

private const val MAX_WEAPONS = 3

class RunGameState {
    var stageIndex: Int = 0
    var roomId: Int = 0
    var gold: Int = 20
    var playerHp: Float = 200f
    var playerMaxHp: Float = 200f
    var loadout: MutableList<Weapon> = defaultLoadout()
    var visited: MutableSet<Int> = mutableSetOf(0)
    var toast: String = ""
    var toastT: Float = 0f
    var eventTitle: String = ""
    var eventBody: String = ""
    var pulse: Float = 0f
    var battleSim: BattleSimulator? = null
    var battleView: BattleViewState? = null
    var battleSession: Int = 0
    var shopOffers: List<ShopOffer> = emptyList()
    var mapSize: IntSize = IntSize.Zero
    var pathAnim: Float = 0f
}

@Composable
fun StageRunScreen(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val state = remember { RunGameState() }
    var phase by remember { mutableStateOf(RunPhase.MAP) }
    var frame by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var battleKey by remember { mutableIntStateOf(0) }

    fun setPhase(p: RunPhase) {
        phase = p
    }

    LaunchedEffect(size, battleKey, phase) {
        if (size.width <= 0) return@LaunchedEffect
        state.mapSize = size

        if (phase == RunPhase.BATTLE) {
            val room = currentRoom(state) ?: return@LaunchedEffect
            val sim = BattleSimulator(
                arenaWidth = size.width.toFloat(),
                arenaHeight = size.height.toFloat() * 0.72f,
                playerWeapons = state.loadout.toList(),
                enemyBaseDamage = room.enemyAtk,
                enemyFireRate = if (room.type == RoomType.BOSS) 1.15f else 0.95f,
                playerMaxHp = state.playerMaxHp,
                enemyMaxHp = room.enemyHp
            )
            // Sync HP into sim by... BattleSimulator doesn't take current HP.
            // Approximate: scale if damaged — for simplicity full heal per fight entry from run HP ratio
            state.battleSim = sim
            state.battleView = sim.viewState
            var prev = 0L
            var resolved = false
            while (true) {
                withFrameNanos { now ->
                    if (prev == 0L) {
                        prev = now
                        return@withFrameNanos
                    }
                    val ms = ((now - prev) / 1_000_000L).coerceIn(0L, 50L)
                    prev = now
                    if (!resolved) {
                        sim.tick(ms)
                        state.battleView = sim.viewState
                        when (sim.getStatus()) {
                            BattleStatus.VICTORY -> {
                                resolved = true
                                onBattleWin(state, room) { setPhase(it) }
                            }
                            BattleStatus.DEFEAT -> {
                                resolved = true
                                state.playerHp = 0f
                                setPhase(RunPhase.RUN_OVER)
                                state.toast = "战败…再试一局"
                                state.toastT = 3f
                            }
                            else -> Unit
                        }
                    }
                    state.pulse += ms / 1000f
                    if (state.toastT > 0f) state.toastT -= ms / 1000f
                    frame = now.toFloat()
                }
                if (phase != RunPhase.BATTLE) break
            }
        } else {
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
                    state.pathAnim += dt
                    if (state.toastT > 0f) state.toastT -= dt
                    frame = now.toFloat()
                }
                if (phase == RunPhase.BATTLE) break
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(phase, state.roomId, battleKey) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var up = down.position
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (ch.changedToUp()) {
                            up = ch.position
                            ch.consume()
                            break
                        }
                    }
                    handleTap(
                        state,
                        up,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        phase,
                        setPhase = { p -> phase = p },
                        bumpBattle = { battleKey++ }
                    )
                }
            }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frame

        when (phase) {
            RunPhase.MAP, RunPhase.EVENT, RunPhase.STAGE_CLEAR, RunPhase.RUN_OVER -> {
                Canvas(Modifier.fillMaxSize()) {
                    drawMapScreen(state, phase, tm, size.width.toFloat(), size.height.toFloat())
                }
            }
            RunPhase.SHOP -> {
                Canvas(Modifier.fillMaxSize()) {
                    drawShopScreen(state, tm, size.width.toFloat(), size.height.toFloat())
                }
            }
            RunPhase.BATTLE -> {
                val vs = state.battleView
                val room = currentRoom(state)
                Box(Modifier.fillMaxSize().background(Color(0xFF0B1020))) {
                    if (vs != null && room != null) {
                        JellyArenaCanvas(
                            state = vs,
                            modifier = Modifier.fillMaxSize(),
                            enemyRim = Color(room.enemyColor),
                            enemyFill = Color(room.enemyColor).copy(alpha = 0.55f)
                        )
                    }
                    Text(
                        text = "遭遇战 · ${room?.enemyName ?: ""}  ·  金币 ${state.gold}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 44.dp)
                    )
                }
            }
        }
    }
}

private fun currentRoom(state: RunGameState): RoomDef? {
    val map = allStageMaps().getOrNull(state.stageIndex) ?: return null
    return map.rooms.find { it.id == state.roomId }
}

private fun currentMap(state: RunGameState): StageMap =
    allStageMaps()[state.stageIndex.coerceIn(0, allStageMaps().lastIndex)]

private fun onBattleWin(state: RunGameState, room: RoomDef, setPhase: (RunPhase) -> Unit) {
    state.gold += room.goldReward
    val vs = state.battleView
    if (vs != null && vs.playerMaxHp > 0f) {
        val ratio = (vs.playerHp / vs.playerMaxHp).coerceIn(0.15f, 1f)
        state.playerHp = (state.playerMaxHp * ratio).coerceAtLeast(1f)
    }
    state.toast = "胜利！+${room.goldReward} 金"
    state.toastT = 1.8f
    state.battleSim = null
    setPhase(RunPhase.MAP)
}

private fun handleTap(
    state: RunGameState,
    pos: Offset,
    w: Float,
    h: Float,
    phase: RunPhase,
    setPhase: (RunPhase) -> Unit,
    bumpBattle: () -> Unit
) {
    when (phase) {
        RunPhase.RUN_OVER -> {
            restartRun(state, setPhase)
        }
        RunPhase.STAGE_CLEAR -> {
            if (state.stageIndex + 1 < allStageMaps().size) {
                state.stageIndex++
                state.roomId = 0
                state.visited = mutableSetOf(0)
                setPhase(RunPhase.MAP)
                state.toast = "进入 ${currentMap(state).title}"
                state.toastT = 2f
            } else {
                state.toast = "全部关卡通关！"
                state.toastT = 3f
                setPhase(RunPhase.RUN_OVER)
            }
        }
        RunPhase.EVENT -> {
            val room = currentRoom(state)
            if (room?.type == RoomType.TRAP &&
                room.trapGoldCost > 0 &&
                state.gold >= room.trapGoldCost &&
                state.eventBody.contains("排除")
            ) {
                if (pos.x < w * 0.5f) {
                    state.playerHp = (state.playerHp - room.trapDamage).coerceAtLeast(0f)
                    state.toast = "硬抗陷阱 -${room.trapDamage.toInt()} HP"
                    if (state.playerHp <= 0f) {
                        setPhase(RunPhase.RUN_OVER)
                        return
                    }
                } else {
                    state.gold -= room.trapGoldCost
                    state.toast = "花费 ${room.trapGoldCost} 金排除陷阱"
                }
                state.toastT = 1.5f
            }
            setPhase(RunPhase.MAP)
        }
        RunPhase.SHOP -> {
            handleShopTap(state, pos, w, h, setPhase)
        }
        RunPhase.MAP -> {
            handleMapTap(state, pos, w, h, setPhase, bumpBattle)
        }
        RunPhase.BATTLE -> {
            // no-op during fight
        }
    }
}

private fun restartRun(state: RunGameState, setPhase: (RunPhase) -> Unit) {
    setPhase(RunPhase.MAP)
    state.stageIndex = 0
    state.roomId = 0
    state.gold = 20
    state.playerHp = 200f
    state.playerMaxHp = 200f
    state.loadout = defaultLoadout()
    state.visited = mutableSetOf(0)
    state.battleSim = null
    state.battleView = null
    state.toast = "新的征程开始"
    state.toastT = 1.5f
}

private fun handleMapTap(
    state: RunGameState,
    pos: Offset,
    w: Float,
    h: Float,
    setPhase: (RunPhase) -> Unit,
    bumpBattle: () -> Unit
) {
    val map = currentMap(state)
    val room = currentRoom(state) ?: return
    val nextIds = room.nextIds
    if (nextIds.isEmpty()) {
        if (room.type == RoomType.EXIT) {
            setPhase(RunPhase.STAGE_CLEAR)
            state.toast = "本关通关！"
            state.toastT = 2f
        }
        return
    }

    // Generous hit radius for fat-finger / high-dpi phones
    for (nid in nextIds) {
        val n = map.rooms.find { it.id == nid } ?: continue
        val c = roomCenter(n, w, h)
        val r = 90f
        val dx = pos.x - c.x
        val dy = pos.y - c.y
        if (dx * dx + dy * dy <= r * r) {
            enterRoom(state, n, setPhase, bumpBattle)
            return
        }
    }

    if (room.type == RoomType.EXIT) {
        setPhase(RunPhase.STAGE_CLEAR)
    }
}

private fun enterRoom(
    state: RunGameState,
    room: RoomDef,
    setPhase: (RunPhase) -> Unit,
    bumpBattle: () -> Unit
) {
    state.roomId = room.id
    state.visited.add(room.id)
    when (room.type) {
        RoomType.START -> setPhase(RunPhase.MAP)
        RoomType.MOB, RoomType.ELITE, RoomType.BOSS -> {
            state.battleSession++
            bumpBattle()
            setPhase(RunPhase.BATTLE)
            state.toast = "遭遇 ${room.enemyName}！"
            state.toastT = 1.2f
        }
        RoomType.RESOURCE -> {
            state.gold += room.goldReward
            state.playerHp = (state.playerHp + room.healReward).coerceAtMost(state.playerMaxHp)
            state.eventTitle = "发现资源：${room.label}"
            state.eventBody = "+${room.goldReward} 金币，恢复 ${room.healReward.toInt()} HP"
            state.toast = state.eventBody
            state.toastT = 2f
            setPhase(RunPhase.EVENT)
        }
        RoomType.TRAP -> {
            if (state.gold >= room.trapGoldCost && room.trapGoldCost > 0) {
                state.eventTitle = "陷阱：${room.label}"
                state.eventBody = "承受 ${room.trapDamage.toInt()} 伤害，或花 ${room.trapGoldCost} 金排除"
                setPhase(RunPhase.EVENT)
                state.toast = "点左侧硬抗 / 右侧花钱排除"
                state.toastT = 2.5f
            } else {
                state.playerHp = (state.playerHp - room.trapDamage).coerceAtLeast(0f)
                state.toast = "踩中陷阱！-${room.trapDamage.toInt()} HP"
                state.toastT = 2f
                if (state.playerHp <= 0f) {
                    setPhase(RunPhase.RUN_OVER)
                } else {
                    setPhase(RunPhase.EVENT)
                    state.eventTitle = "陷阱：${room.label}"
                    state.eventBody = "损失 ${room.trapDamage.toInt()} HP"
                }
            }
        }
        RoomType.SHOP -> {
            state.shopOffers = shopCatalog(currentMap(state).stageId, state.gold)
            setPhase(RunPhase.SHOP)
            state.toast = "欢迎光临商店"
            state.toastT = 1.2f
        }
        RoomType.EXIT -> {
            setPhase(RunPhase.STAGE_CLEAR)
            state.toast = "抵达出口！"
            state.toastT = 2f
        }
    }
}

private fun handleShopTap(
    state: RunGameState,
    pos: Offset,
    w: Float,
    h: Float,
    setPhase: (RunPhase) -> Unit
) {
    if (pos.y in h * 0.86f..h * 0.96f) {
        setPhase(RunPhase.MAP)
        state.toast = "离开商店"
        state.toastT = 1f
        return
    }
    // Offer cards
    val offers = state.shopOffers
    if (offers.isEmpty()) return
    val cardW = w * 0.28f
    val gap = w * 0.04f
    val total = offers.size * cardW + (offers.size - 1) * gap
    var x0 = (w - total) / 2f
    val y0 = h * 0.42f
    val cardH = h * 0.28f
    offers.forEachIndexed { i, offer ->
        if (pos.x in x0..(x0 + cardW) && pos.y in y0..(y0 + cardH)) {
            buyOffer(state, offer)
            return
        }
        x0 += cardW + gap
    }
}

private fun buyOffer(state: RunGameState, offer: ShopOffer) {
    if (state.gold < offer.price) {
        state.toast = "金币不足"
        state.toastT = 1.2f
        return
    }
    if (state.loadout.any { it.id == offer.weapon.id }) {
        state.toast = "已拥有该武器"
        state.toastT = 1.2f
        return
    }
    if (state.loadout.size >= MAX_WEAPONS) {
        // replace last
        state.loadout.removeAt(state.loadout.lastIndex)
    }
    state.gold -= offer.price
    state.loadout.add(offer.weapon)
    state.toast = "购入 ${offer.weapon.name}"
    state.toastT = 1.5f
}

// ─── Drawing ─────────────────────────────────────────────────────────────

private fun roomCenter(room: RoomDef, w: Float, h: Float): Offset {
    val mapL = w * 0.04f
    val mapT = h * 0.22f
    val mapW = w * 0.92f
    val mapH = h * 0.48f
    return Offset(mapL + room.nx * mapW, mapT + room.ny * mapH)
}

private fun DrawScope.drawMapScreen(
    state: RunGameState,
    phase: RunPhase,
    tm: TextMeasurer,
    w: Float,
    h: Float
) {
    val map = currentMap(state)
    drawRect(
        Brush.verticalGradient(listOf(Color(map.bgTop), Color(map.bgBot))),
        size = Size(w, h)
    )

    // Top HUD
    drawRoundRect(Color(0xCC0F172A), Offset(12f, h * 0.04f), Size(w - 24f, h * 0.12f), CornerRadius(14f))
    banner(tm, map.title, w * 0.5f, h * 0.05f, Color.White, 18.sp)
    banner(tm, map.subtitle, w * 0.5f, h * 0.085f, Color(0xFF94A3B8), 11.sp)
    banner(
        tm,
        "金币 ${state.gold}   HP ${state.playerHp.toInt()}/${state.playerMaxHp.toInt()}   武器 ${state.loadout.size}/$MAX_WEAPONS",
        w * 0.5f,
        h * 0.118f,
        Color(0xFFFFD700),
        12.sp
    )

    // Map panel
    val mapL = w * 0.04f
    val mapT = h * 0.22f
    val mapW = w * 0.92f
    val mapH = h * 0.48f
    drawRoundRect(Color(0x66000000), Offset(mapL, mapT), Size(mapW, mapH), CornerRadius(18f))

    // Decorative terrain stripes
    var sy = mapT + 20f
    while (sy < mapT + mapH) {
        drawLine(Color(0x14FFFFFF), Offset(mapL + 8f, sy), Offset(mapL + mapW - 8f, sy), 1f)
        sy += 28f
    }

    // Edges
    for (room in map.rooms) {
        val from = roomCenter(room, w, h)
        for (nid in room.nextIds) {
            val toRoom = map.rooms.find { it.id == nid } ?: continue
            val to = roomCenter(toRoom, w, h)
            val active = room.id == state.roomId || state.visited.contains(room.id)
            drawLine(
                if (active) Color(0x88F8FAFC) else Color(0x33F8FAFC),
                from,
                to,
                strokeWidth = if (active) 5f else 3f,
                cap = StrokeCap.Round
            )
            // small resource/trap markers mid-edge for flavor
            if (toRoom.type == RoomType.TRAP || toRoom.type == RoomType.RESOURCE) {
                val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
                drawCircle(
                    if (toRoom.type == RoomType.TRAP) Color(0x66EF4444) else Color(0x66FACC15),
                    6f,
                    mid
                )
            }
        }
    }

    // Nodes
    val pulse = 0.5f + 0.5f * sin(state.pulse * 3f)
    for (room in map.rooms) {
        val c = roomCenter(room, w, h)
        val col = roomColor(room.type)
        val isHere = room.id == state.roomId
        val isNext = currentRoom(state)?.nextIds?.contains(room.id) == true
        val r = when {
            isHere -> 36f + 4f * pulse
            isNext -> 32f
            else -> 26f
        }
        if (isNext) {
            drawCircle(col.copy(alpha = 0.25f + 0.2f * pulse), r + 14f, c)
        }
        drawCircle(Color(0xFF0F172A), r, c)
        drawCircle(col, r, c, style = Stroke(if (isHere) 5f else 3f))
        if (state.visited.contains(room.id) && !isHere) {
            drawCircle(col.copy(alpha = 0.35f), r * 0.55f, c)
        }
        // icon letter
        val icon = roomIcon(room.type)
        val layout = tm.measure(
            icon,
            TextStyle(color = Color.White, fontSize = if (room.type == RoomType.BOSS) 11.sp else 13.sp, fontWeight = FontWeight.Bold)
        )
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))

        // label under node
        val lab = tm.measure(
            room.label,
            TextStyle(color = Color(0xFFCBD5E1), fontSize = 10.sp)
        )
        drawText(lab, topLeft = Offset(c.x - lab.size.width / 2f, c.y + r + 6f))
    }

    // Player token on current room
    currentRoom(state)?.let { cur ->
        val c = roomCenter(cur, w, h)
        val bob = sin(state.pulse * 4f) * 4f
        drawCircle(Color(0xFFFF6B8A), 12f, Offset(c.x, c.y - 48f + bob))
        drawCircle(Color.White, 4f, Offset(c.x - 3f, c.y - 50f + bob))
    }

    // Bottom loadout + help
    drawRoundRect(Color(0xCC0F172A), Offset(12f, h * 0.74f), Size(w - 24f, h * 0.22f), CornerRadius(14f))
    banner(tm, "点亮圈节点前进 · 分支自选路线", w * 0.5f, h * 0.755f, Color(0xFF94A3B8), 12.sp)
    val syn = evaluateSynergies(state.loadout)
    val loadTxt = "装备: " + state.loadout.joinToString(" / ") { it.name }
    banner(tm, loadTxt, w * 0.5f, h * 0.80f, Color.White, 13.sp)
    if (syn.isNotEmpty()) {
        banner(tm, "协同: " + syn.map { it.name }.distinct().joinToString(" · "), w * 0.5f, h * 0.845f, Color(0xFFFFD700), 12.sp)
    } else {
        banner(tm, "协同: 无（商店可买武器组合）", w * 0.5f, h * 0.845f, Color(0xFF64748B), 12.sp)
    }
    val cur = currentRoom(state)
    if (cur != null && cur.nextIds.isNotEmpty()) {
        banner(tm, "可选: " + cur.nextIds.mapNotNull { id -> map.rooms.find { it.id == id }?.label }.joinToString(" / "),
            w * 0.5f, h * 0.89f, Color(0xFF4ADE80), 12.sp)
    }

    if (phase == RunPhase.EVENT) {
        drawEventOverlay(state, tm, w, h)
    }
    if (phase == RunPhase.STAGE_CLEAR) {
        drawDim(w, h)
        banner(tm, "关卡完成！", w * 0.5f, h * 0.40f, Color(0xFF4ADE80), 28.sp)
        banner(tm, "点屏幕进入下一关", w * 0.5f, h * 0.48f, Color.White, 14.sp)
    }
    if (phase == RunPhase.RUN_OVER) {
        drawDim(w, h)
        banner(tm, if (state.playerHp <= 0f) "征程失败" else "全部通关！", w * 0.5f, h * 0.40f, Color(0xFFFF6B8A), 28.sp)
        banner(tm, "点屏幕重新开始", w * 0.5f, h * 0.48f, Color.White, 14.sp)
    }
    if (state.toastT > 0f && phase == RunPhase.MAP) {
        banner(tm, state.toast, w * 0.5f, h * 0.18f, Color(0xFFFFD700), 13.sp)
    }
}

private fun DrawScope.drawEventOverlay(state: RunGameState, tm: TextMeasurer, w: Float, h: Float) {
    drawDim(w, h)
    drawRoundRect(Color(0xF01E293B), Offset(w * 0.1f, h * 0.32f), Size(w * 0.8f, h * 0.30f), CornerRadius(18f))
    banner(tm, state.eventTitle, w * 0.5f, h * 0.36f, Color(0xFFFFD700), 18.sp)
    banner(tm, state.eventBody, w * 0.5f, h * 0.42f, Color.White, 13.sp)

    val room = currentRoom(state)
    if (room?.type == RoomType.TRAP && state.gold >= room.trapGoldCost && room.trapGoldCost > 0 &&
        state.eventBody.contains("排除")
    ) {
        // two choices
        drawRoundRect(Color(0xFF7F1D1D), Offset(w * 0.15f, h * 0.50f), Size(w * 0.3f, h * 0.07f), CornerRadius(12f))
        drawRoundRect(Color(0xFF1D4ED8), Offset(w * 0.55f, h * 0.50f), Size(w * 0.3f, h * 0.07f), CornerRadius(12f))
        banner(tm, "硬抗 -${room.trapDamage.toInt()}", w * 0.30f, h * 0.515f, Color.White, 12.sp)
        banner(tm, "花${room.trapGoldCost}金排除", w * 0.70f, h * 0.515f, Color.White, 12.sp)
    } else {
        banner(tm, "点任意处继续", w * 0.5f, h * 0.52f, Color(0xFF94A3B8), 12.sp)
    }
}

private fun DrawScope.drawShopScreen(state: RunGameState, tm: TextMeasurer, w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0C4A6E), Color(0xFF0F172A))), size = Size(w, h))
    banner(tm, "流浪商店", w * 0.5f, h * 0.08f, Color(0xFF7DD3FC), 26.sp)
    banner(tm, "金币 ${state.gold}  ·  已装备 ${state.loadout.size}/$MAX_WEAPONS", w * 0.5f, h * 0.14f, Color(0xFFFFD700), 14.sp)
    banner(tm, "点卡片购买（满 3 件会替换最后一件）", w * 0.5f, h * 0.19f, Color(0xFF94A3B8), 12.sp)

    val offers = state.shopOffers
    val cardW = w * 0.28f
    val gap = w * 0.04f
    val total = offers.size * cardW + (offers.size - 1).coerceAtLeast(0) * gap
    var x0 = (w - total) / 2f
    val y0 = h * 0.42f
    val cardH = h * 0.28f
    for (offer in offers) {
        val affordable = state.gold >= offer.price
        drawRoundRect(
            if (affordable) Color(0xFF1E293B) else Color(0xFF111827),
            Offset(x0, y0), Size(cardW, cardH), CornerRadius(14f)
        )
        drawRoundRect(
            if (affordable) Color(0xFF38BDF8) else Color(0xFF475569),
            Offset(x0, y0), Size(cardW, cardH), CornerRadius(14f), style = Stroke(3f)
        )
        banner(tm, offer.weapon.name, x0 + cardW / 2f, y0 + 24f, Color.White, 14.sp)
        val tags = offer.weapon.tags.joinToString(" ") { it.name.take(2) }
        banner(tm, tags, x0 + cardW / 2f, y0 + 70f, Color(0xFFCBD5E1), 11.sp)
        banner(tm, "攻 ${offer.weapon.baseDamage.toInt()}", x0 + cardW / 2f, y0 + 110f, Color(0xFF94A3B8), 12.sp)
        banner(tm, "${offer.price} 金", x0 + cardW / 2f, y0 + cardH - 48f, Color(0xFFFFD700), 16.sp)
        x0 += cardW + gap
    }

    // loadout
    banner(tm, "当前: " + state.loadout.joinToString(" / ") { it.name }, w * 0.5f, h * 0.78f, Color.White, 12.sp)
    val syn = evaluateSynergies(state.loadout)
    if (syn.isNotEmpty()) {
        banner(tm, "协同 " + syn.map { it.name }.distinct().joinToString(" · "), w * 0.5f, h * 0.82f, Color(0xFFFFD700), 12.sp)
    }

    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B))),
        Offset(w * 0.2f, h * 0.88f), Size(w * 0.6f, 56f), CornerRadius(16f)
    )
    banner(tm, "离开商店", w * 0.5f, h * 0.895f, Color.White, 16.sp)
}

private fun DrawScope.drawDim(w: Float, h: Float) {
    drawRect(Color(0xAA020617), size = Size(w, h))
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

// Fix EVENT trap choice on tap — need special handling in handleTap for EVENT
