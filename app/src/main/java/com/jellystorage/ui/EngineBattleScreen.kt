package com.jellystorage.ui

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellystorage.engine.battle.BattleSimulator
import com.jellystorage.engine.battle.BattleStatus
import com.jellystorage.engine.battle.BattleViewState
import com.jellystorage.engine.model.WeaponCatalog
import com.jellystorage.ui.canvas.JellyArenaCanvas

/**
 * Hosts the production [BattleSimulator] + [JellyArenaCanvas] loop.
 * Tap the screen after victory/defeat to restart with Steam Blast loadout.
 */
@Composable
fun EngineBattleScreen(modifier: Modifier = Modifier) {
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var session by remember { mutableIntStateOf(0) }
    var viewState by remember { mutableStateOf<BattleViewState?>(null) }
    var status by remember { mutableStateOf(BattleStatus.PREPARING) }

    LaunchedEffect(layoutSize.width, layoutSize.height, session) {
        val w = layoutSize.width
        val h = layoutSize.height
        if (w <= 0 || h <= 0) return@LaunchedEffect

        val sim = BattleSimulator(
            arenaWidth = w.toFloat(),
            arenaHeight = h.toFloat(),
            playerWeapons = WeaponCatalog.defaultsForSteamBlast(),
            enemyBaseDamage = 10f,
            enemyFireRate = 0.95f,
            playerMaxHp = 220f,
            enemyMaxHp = 160f
        )
        viewState = sim.viewState
        status = sim.getStatus()

        var prevNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (prevNs == 0L) {
                    prevNs = now
                    return@withFrameNanos
                }
                val elapsedMs = ((now - prevNs) / 1_000_000L).coerceIn(0L, 50L)
                prevNs = now
                sim.tick(elapsedMs)
                viewState = sim.viewState
                status = sim.getStatus()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020))
            .onSizeChanged { layoutSize = it }
            .pointerInput(status) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) {
                            change.consume()
                            if (status == BattleStatus.VICTORY || status == BattleStatus.DEFEAT) {
                                session++
                            }
                            break
                        }
                    }
                }
            }
    ) {
        val vs = viewState
        if (vs != null) {
            JellyArenaCanvas(
                state = vs,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay HUD chrome (not in canvas hot path)
        Text(
            text = "果冻引擎战场 · Steam Blast 验证",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )
        if (status == BattleStatus.VICTORY || status == BattleStatus.DEFEAT) {
            Text(
                text = "点屏幕再来一局",
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }
    }
}
