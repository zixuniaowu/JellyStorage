package com.jellystorage.play

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.jellystorage.BuildConfig
import com.jellystorage.softbody.rememberHapticAudioManager
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class Screen {
    LOGIN, CHAR_SELECT, CREATE_CHAR, TITLE, HOW_TO, SETTINGS, CLASS_SELECT, STORY, MAP, ARENA, SHOP, GEAR, CODEX, EVENT, LEVEL_UP, CORE_INK, STAGE_CLEAR, RESULT, TRIALS
}

/**
 * 战斗键位锚定右下角，间距统一按短边计算：不同宽高比只增加战场宽度，
 * 不会把技能键拉向屏幕中央。绘制和触控必须共用这一份几何。
 */
internal data class ArenaControlLayout(
    val attackX: Float, val attackY: Float,
    val skill1X: Float, val skill1Y: Float,
    val skill2X: Float, val skill2Y: Float,
    val skill3X: Float, val skill3Y: Float,
    val ultX: Float, val ultY: Float,
    val potionX: Float, val potionY: Float
)

internal fun arenaControlLayout(w: Float, h: Float): ArenaControlLayout {
    val u = min(w, h)
    val attackX = w - u * 0.18f
    val attackY = h - u * 0.20f
    return ArenaControlLayout(
        attackX, attackY,
        attackX - u * 0.18f, attackY + u * 0.07f,
        attackX - u * 0.27f, attackY - u * 0.04f,
        attackX - u * 0.20f, attackY - u * 0.17f,
        attackX - u * 0.04f, attackY - u * 0.25f,
        w - u * 0.08f, attackY - u * 0.25f
    )
}

/** 命中区相对绘制半径的统一放大系数。普攻单独收敛到 1.2×，
 *  消除旧版 1.45× 膨胀区吞掉相邻技能键视觉圆的问题 */
internal const val ARENA_HIT_SLOP = 1.25f
internal const val ARENA_HIT_SLOP_ATTACK = 1.2f

/**
 * Compose 的 PointerInputChange 在部分设备上会在“左指先按、右指后按”时错误平移
 * 第二指坐标。战斗改用原始 MotionEvent 后，摇杆和按钮始终使用同一套局部坐标。
 */
internal class ArenaRawTouchState {
    var joyId = -1
    var basicId = -1
    var skill1Id = -1
    var skill2Id = -1
    var skill3Id = -1
    var ultId = -1
    var joyOrigin = Offset.Zero

    /** 已按下手指最近一次事件时刻（uptime ms）。系统手势截胡丢失 UP 时按超时回收，防止角色永久卡住 */
    val lastSeen = HashMap<Int, Long>()

    /** 调查日志节流 */
    var lastJoyLogMs = 0L

    /** 指针当前承担的输入角色（日志用） */
    fun roleOf(id: Int): String = when (id) {
        joyId -> "joy"
        basicId -> "atk"
        skill1Id -> "s1"
        skill2Id -> "s2"
        skill3Id -> "s3"
        ultId -> "ult"
        else -> "?"
    }

    fun reset() {
        joyId = -1
        basicId = -1
        skill1Id = -1
        skill2Id = -1
        skill3Id = -1
        ultId = -1
        joyOrigin = Offset.Zero
        lastSeen.clear()
    }

    /** 返回超过 timeoutMs 未见任何事件的手指并移出登记 */
    fun staleIds(nowMs: Long, timeoutMs: Long = 30_000L): List<Int> {
        val stale = ArrayList<Int>()
        val it = lastSeen.entries.iterator()
        while (it.hasNext()) {
            val (id, t) = it.next()
            if (nowMs - t > timeoutMs) {
                it.remove()
                stale.add(id)
            }
        }
        return stale
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun Context.openPrivacyPolicy(): String? {
    val url = BuildConfig.PRIVACY_POLICY_URL
    if (!url.startsWith("https://")) return "发布版尚未配置隐私政策网址"
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: Throwable) {
        "无法打开隐私政策"
    }
}

private fun afterLoginScreen(progress: ProgressStore): Screen {
    return when {
        progress.listCharacters().isEmpty() -> Screen.CREATE_CHAR
        progress.activeCharacter() == null -> Screen.CHAR_SELECT
        !progress.seenTutorial -> Screen.HOW_TO
        else -> Screen.TITLE
    }
}

@Composable
fun PlayScreen(modifier: Modifier = Modifier) {
    // 文本测量缓存加大：战斗 HUD/技能名/飘字每帧测量，默认 8 条缓存会反复失效，
    // 导致每帧全量重新排版（曾造成 99.7% 掉帧、点按被整帧吞掉）
    val tm = rememberTextMeasurer(cacheSize = 512)
    val context = LocalContext.current
    val activity = context.findActivity()
    val progress = remember { ProgressStore(context) }
    var language by remember { mutableStateOf(progress.language) }
    GameI18n.language = language
    val toggleLanguage = {
        val next = language.toggled()
        language = next
        progress.language = next
        GameI18n.language = next
    }
    val ads = remember { AdsManager(context.applicationContext) }
    val haptics = rememberHapticAudioManager()
    // 同步设置开关 → 音效/震动
    haptics.soundEnabled = progress.soundOn
    haptics.hapticsEnabled = progress.hapticsOn
    val meta = remember { RunMeta() }
    var gearScroll by remember { mutableFloatStateOf(0f) }
    var loginUser by remember { mutableStateOf(progress.localUsername()) }
    var loginPass by remember { mutableStateOf("") }
    var loginMsg by remember { mutableStateOf("") }
    var loginIsRegister by remember { mutableStateOf(!progress.hasLocalAccount()) }
    var guestHint by remember { mutableStateOf("") }
    var privacyMessage by remember { mutableStateOf("") }
    // 种子远征面板：输入好友种子码打同一张图；显示本档种子码供分享
    var seedPanelVisible by remember { mutableStateOf(false) }
    var seedInput by remember { mutableStateOf("") }
    var seedMsg by remember { mutableStateOf("") }
    var seedCurCode by remember { mutableStateOf<String?>(null) }
    val seedRunMeta = remember { RunMeta() }
    LaunchedEffect(seedPanelVisible) {
        if (seedPanelVisible && progress.hasActiveRun() && progress.loadActiveRun(seedRunMeta)) {
            seedCurCode = SeedCode.encode(seedRunMeta.runSeed, seedRunMeta.inkRank)
        } else if (!seedPanelVisible) {
            seedCurCode = null
        }
    }
    var createName by remember {
        mutableStateOf(GameCharacter.autoName(progress.listCharacters().size))
    }
    var createHeroIdx by remember { mutableIntStateOf(0) }
    var charTick by remember { mutableIntStateOf(0) }
    /** 确认弹窗：new_run / delete_char / exit_chapter / abandon / none */
    var confirmKind by remember { mutableStateOf("") }
    var confirmPayload by remember { mutableStateOf("") }
    var screen by remember {
        mutableStateOf(
            when {
                !progress.isSessionLoggedIn() -> Screen.LOGIN
                progress.listCharacters().isEmpty() -> Screen.CREATE_CHAR
                progress.activeCharacter() == null -> Screen.CHAR_SELECT
                progress.seenTutorial -> Screen.TITLE
                else -> Screen.HOW_TO
            }
        )
    }
    // E2E/调试：屏幕切换可观测
    LaunchedEffect(screen) { if (BuildConfig.DEBUG) android.util.Log.d("JellyScreen", screen.name) }
    // 标题屏展示存档中已装备的武器/防具（只读快照，不影响本局 meta）
    val titleRunMeta = remember { RunMeta() }
    var titleGearReady by remember { mutableStateOf(false) }
    LaunchedEffect(screen) {
        titleGearReady = if (screen == Screen.TITLE && progress.hasActiveRun()) {
            progress.loadActiveRun(titleRunMeta)
        } else false
    }
    LaunchedEffect(activity) {
        ads.gatherConsent(activity) { error ->
            if (error != null) privacyMessage = "广告隐私：$error"
        }
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableFloatStateOf(0f) }
    var arenaKey by remember { mutableIntStateOf(0) }
    var arena by remember { mutableStateOf<ArenaSim?>(null) }

    var stickX by remember { mutableFloatStateOf(0f) }
    var stickY by remember { mutableFloatStateOf(0f) }
    var basicHeld by remember { mutableStateOf(false) }
    var s1Held by remember { mutableStateOf(false) }
    var s2Held by remember { mutableStateOf(false) }
    var s3Held by remember { mutableStateOf(false) }
    var s4Held by remember { mutableStateOf(false) }
    // floating joystick visual (screen space)
    var joyOx by remember { mutableFloatStateOf(0f) }
    var joyOy by remember { mutableFloatStateOf(0f) }
    var joyActive by remember { mutableStateOf(false) }
    var joyKnobX by remember { mutableFloatStateOf(0f) }
    var joyKnobY by remember { mutableFloatStateOf(0f) }
    // 移动中技能盘（绘制状态）
    var wheelActive by remember { mutableStateOf(false) }
    var wheelX by remember { mutableFloatStateOf(0f) }
    var wheelY by remember { mutableFloatStateOf(0f) }
    var wheelSlotSel by remember { mutableIntStateOf(0) }
    val arenaRawTouch = remember { ArenaRawTouchState() }
    // 暂停菜单“放弃本局”二次确认：首次点击时间戳，3 秒内再点才执行
    var pendingAbandonT by remember { mutableStateOf(0L) }
    // 性能打点：区分 sim.update（逻辑）与 drawArena（绘制录制）耗时
    var perfDrawMs by remember { mutableFloatStateOf(0f) }
    var perfDrawN by remember { mutableIntStateOf(0) }
    var perfFrameAcc by remember { mutableFloatStateOf(0f) }
    var perfFrameN by remember { mutableIntStateOf(0) }
    var perfFrameMax by remember { mutableFloatStateOf(0f) }
    var perfLogT by remember { mutableFloatStateOf(0f) }
    var uiPerfAcc by remember { mutableFloatStateOf(0f) }
    var uiPerfN by remember { mutableIntStateOf(0) }

    fun releaseArenaTouches() {
        arenaRawTouch.reset()
        stickX = 0f
        stickY = 0f
        joyActive = false
        basicHeld = false
        s1Held = false
        s2Held = false
        s3Held = false
        s4Held = false
        wheelActive = false
    }

    fun releaseArenaPointer(pointerId: Int) {
        if (BuildConfig.DEBUG) {
            val role = arenaRawTouch.roleOf(pointerId)
            if (role != "?") android.util.Log.d("JellyInterop", "RELEASE $role id=$pointerId")
        }
        when (pointerId) {
            arenaRawTouch.joyId -> {
                arenaRawTouch.joyId = -1
                stickX = 0f
                stickY = 0f
                joyActive = false
            }
            arenaRawTouch.basicId -> {
                arenaRawTouch.basicId = -1
                basicHeld = false
            }
            arenaRawTouch.skill1Id -> {
                arenaRawTouch.skill1Id = -1
                s1Held = false
            }
            arenaRawTouch.skill2Id -> {
                arenaRawTouch.skill2Id = -1
                s2Held = false
            }
            arenaRawTouch.skill3Id -> {
                arenaRawTouch.skill3Id = -1
                s3Held = false
            }
            arenaRawTouch.ultId -> {
                arenaRawTouch.ultId = -1
                s4Held = false
            }
        }
    }

    LaunchedEffect(screen, arenaKey) {
        releaseArenaTouches()
    }

    // 切后台/系统回收前兜底存档：远征进行中随时写入（战斗中也会存，读档回到该节点前的地图）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && progress.hasActiveRun()) {
                progress.saveActiveRun(meta)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // auto-save whenever we are on the map (resume-safe)
    LaunchedEffect(screen, meta.nodeId, meta.stageIndex, meta.roomsCleared, meta.level) {
        if (screen == Screen.MAP) {
            progress.saveActiveRun(meta)
        }
    }

    // 仅依赖 screen+arenaKey 创建战斗：尺寸变化不得重置整场战斗
    LaunchedEffect(screen, arenaKey) {
        if (screen == Screen.ARENA) {
            val ready = snapshotFlow { size }.first { it.width > 0 && it.height > 0 }
            val node = currentNode(meta) ?: return@LaunchedEffect
            meta.ensureVitals()
            val threat = meta.stageIndex * 5 + meta.roomsCleared + when (node.type) {
                NodeType.MOB -> 1
                NodeType.ELITE -> 4
                NodeType.BOSS -> 7
                NodeType.CHALLENGE -> 5
                else -> 0
            }
            val sim = ArenaSim(
                width = ready.width.toFloat() * 1.22f,
                height = ready.height.toFloat() * 1.05f,
                hero = meta.hero,
                weaponLevel = meta.weaponLevel,
                armorLevel = meta.armorLevel,
                passives = meta.passives.toSet(),
                startHp = meta.curHp,
                startMp = meta.curMp,
                waves = node.waves,
                goldPerKill = node.goldDrop.coerceAtLeast(4),
                threatLevel = threat.coerceAtLeast(1),
                heroLevel = meta.level,
                playerElement = meta.playerElement(),
                gearAtkBonus = meta.totalAtkBonus(),
                skillPowerBonus = meta.combatSkillPowerBonus(),
                extraDr = meta.buffDr,
                fruitBurn = meta.buffBurn,
                gear = meta.equippedWeapon(),
                extraArmorHp = meta.equippedArmor().hpBonus + (meta.activeSet()?.hpBonus ?: 0f) +
                    (meta.equippedRing()?.hpBonus ?: 0f) + meta.equippedBoots().hpBonus,
                extraMp = meta.equippedArmor().mpBonus + (meta.activeSet()?.mpBonus ?: 0f) +
                    (meta.equippedRing()?.mpBonus ?: 0f) + meta.equippedBoots().mpBonus,
                setDr = meta.equippedArmor().dr + (meta.activeSet()?.dr ?: 0f) +
                    (meta.equippedRing()?.dr ?: 0f) + meta.equippedBoots().dr,
                setCrit = meta.equippedArmor().crit + (meta.activeSet()?.crit ?: 0f) +
                    (meta.equippedRing()?.crit ?: 0f) + meta.equippedBoots().crit,
                setLifeSteal = (meta.activeSet()?.lifeSteal ?: 0f) + (meta.equippedRing()?.lifeSteal ?: 0f),
                setCdr = meta.equippedArmor().cdr + (meta.activeSet()?.cdr ?: 0f) +
                    (meta.equippedRing()?.cdr ?: 0f) + meta.equippedBoots().cdr,
                setSkillAmp = meta.equippedArmor().skillAmp + (meta.activeSet()?.skillAmp ?: 0f) +
                    (meta.equippedRing()?.skillAmp ?: 0f) + meta.equippedBoots().skillAmp,
                extraSpd = meta.equippedBoots().spdBonus + (meta.equippedRing()?.spdBonus ?: 0f),
                overrideProc = meta.setProc().first,
                overrideProcPower = meta.setProc().second,
                mods = meta.combatMods(),
                roomTrial = roomTrialFor(meta.runSeed, meta.stageIndex, node),
                coreInkRanks = meta.coreInkRanks.toMap(),
                bossEncounter = if (node.type == NodeType.BOSS) bossEncounterForStage(meta.stage().id) else null,
                environment = arenaEnvironmentFor(meta.stage().chapterIndex, node.id, node.type),
                spawnTerrain = true
            )
            arena = sim
            val environmentLine = sim.environment.takeIf { it.active }?.let {
                "${GameI18n.tr(it.title)}・${GameI18n.tr(it.rule)}"
            }
            environmentLine?.let {
                meta.arenaBanner = it
                meta.arenaBannerT = 3.6f
            } ?: sim.roomTrial?.let { trial ->
                meta.arenaBanner = "${GameI18n.tr("试炼")}・${GameI18n.tr(trial.title)}：${GameI18n.tr(trial.objective(sim.waveTotal))}"
                meta.arenaBannerT = 3.2f
            }
            var prev = 0L
            var done = false
            // 结算过渡：胜利 1.35s / 失败 0.9s，让最后一击的画面被看见
            var endT = -1f
            while (screen == Screen.ARENA) {
                withFrameNanos { now ->
                    if (prev == 0L) {
                        prev = now
                        return@withFrameNanos
                    }
                    val dt = ((now - prev) / 1e9f).coerceIn(0f, 0.05f)
                    prev = now
                    if (screen == Screen.ARENA) {
                        val fms = dt * 1000f
                        perfFrameAcc += fms
                        if (fms > perfFrameMax) perfFrameMax = fms
                        perfFrameN++
                        perfLogT += dt
                        if (perfLogT >= 1f) {
                            val avg = perfFrameAcc / perfFrameN
                            val dAvg = if (perfDrawN > 0) perfDrawMs / perfDrawN else 0f
                            if (BuildConfig.DEBUG)                             if (BuildConfig.DEBUG) android.util.Log.d(
                                "JellyPerf",
                                "frame avg=${"%.1f".format(avg)}ms max=${"%.1f".format(perfFrameMax)}ms draw=${"%.1f".format(dAvg)}ms stick=(${"%.2f".format(stickX)},${"%.2f".format(stickY)}) (n=${perfFrameN}/${perfDrawN})"
                            )
                            perfFrameAcc = 0f; perfFrameN = 0; perfFrameMax = 0f
                            perfDrawMs = 0f; perfDrawN = 0; perfLogT = 0f
                        }
                    }
                    // 幽灵触点兜底：所有手指 UP 全部丢失（此后无任何事件）时按 60s 超时强制释放；
                    // 常规 30s 事件驱动回收见 handleArenaRawTouch
                    arenaRawTouch.staleIds(SystemClock.uptimeMillis(), 60_000L).forEach { releaseArenaPointer(it) }
                    if (!meta.paused) {
                        // 战斗中：正常推演；结算演出窗口（endT>0）继续 tick，让粒子/飘字活着
                        val killsBefore = sim.enemies.count { it.dead }
                        val waveBefore = sim.waveIndex
                        sim.update(dt, stickX, stickY, basicHeld, s1Held, s2Held, s3Held, s4Held)
                        val killsAfter = sim.enemies.count { it.dead }
                        if (killsAfter > killsBefore) {
                            meta.kills += killsAfter - killsBefore
                            // 连斩里程碑飘到战场 Banner
                            val ct = comboTitle(sim.comboCount)
                            if (sim.comboCount in listOf(5, 10, 15, 20) && ct.isNotEmpty()) {
                                meta.arenaBanner = ct
                                meta.arenaBannerT = 1.4f
                            }
                        }
                        if (sim.waveIndex != waveBefore) {
                            val node = currentNode(meta)
                            val mod = sim.waveMod
                            val modTxt = if (mod != WaveMod.NONE) "【${mod.title}·${mod.desc}】" else ""
                            meta.arenaBanner = modTxt + (StoryBook.combatTaunt(
                                node?.type ?: NodeType.MOB,
                                sim.waveIndex,
                                sim.waveTotal
                            ) ?: "新的一波。")
                            meta.arenaBannerT = 2.4f
                        }
                        val tacticalHint = sim.consumeTacticalHintLine()
                        if (tacticalHint.isNotEmpty()) {
                            meta.arenaBanner = tacticalHint
                            meta.arenaBannerT = 2.8f
                        }
                        val phaseLine = sim.consumeBossPhaseLine()
                        if (phaseLine.isNotEmpty()) {
                            meta.arenaBanner = "Boss · $phaseLine"
                            meta.arenaBannerT = 2.6f
                        }
                        if (meta.arenaBannerT > 0f) meta.arenaBannerT -= dt
                        val pulse = sim.consumeHaptic()
                        if (pulse > 0) {
                            haptics.soundEnabled = progress.soundOn
                            haptics.hapticsEnabled = progress.hapticsOn
                            haptics.combatPulse(pulse)
                        }
                        meta.curHp = sim.player.hp
                        meta.curMp = sim.mp
                        if (sim.finished) {
                            done = true
                            if (endT < 0f) {
                                endT = if (sim.won) 1.35f else 0.9f
                                meta.arenaBanner = if (sim.won) "清场！" else "力竭…"
                                meta.arenaBannerT = endT
                            }
                        }
                    }
                    if (endT >= 0f && screen == Screen.ARENA) {
                        endT -= dt
                    if (endT < 0f) {
                        if (sim.won) {
                            screen = settleVictory(sim, meta, progress)
                        } else if (progress.hasCheckpoint()) {
                            // 章节检查点恢复：死亡不再整局清档，从本章开头重来
                            progress.loadCheckpoint(meta)
                            progress.saveActiveRun(meta)
                            meta.arenaBanner = "免疫记忆重启 · 从本章重新出击"
                            meta.arenaBannerT = 3.2f
                            meta.toast = "免疫记忆重启 · 从本章重新出击"
                            meta.toastT = 3f
                            screen = Screen.MAP
                        } else {
                            meta.fillResult(won = false)
                            progress.recordRunEnd(
                                won = false,
                                stageIndex = meta.stageIndex,
                                level = meta.level,
                                goldEarned = meta.goldEarnedThisRun,
                                kills = meta.kills
                            )
                            screen = Screen.RESULT
                        }
                        arena = null
                        stickX = 0f; stickY = 0f
                        basicHeld = false; s1Held = false; s2Held = false; s3Held = false; s4Held = false
                        joyActive = false
                        meta.paused = false
                    }
                    }
                    meta.pulse += dt
                    if (meta.toastT > 0f) meta.toastT -= dt
                    if (meta.setAwakenedT > 0f) meta.setAwakenedT = max(0f, meta.setAwakenedT - dt)
                    if (meta.setAwakenedHapticPending) {
                        haptics.soundEnabled = progress.soundOn
                        haptics.hapticsEnabled = progress.hapticsOn
                        haptics.combatPulse(6)
                        meta.setAwakenedHapticPending = false
                    }
                    frame = now.toFloat()
                }
            }
        } else {
            // 非战斗：降帧，减轻 Text 测量导致的内存抖动/卡顿
            var prev = 0L
            var acc = 0f
            while (screen != Screen.ARENA) {
                withFrameNanos { now ->
                    if (prev == 0L) {
                        prev = now
                        return@withFrameNanos
                    }
                    val dt = ((now - prev) / 1e9f).coerceIn(0f, 0.05f)
                    prev = now
                    meta.pulse += dt
                    if (meta.toastT > 0f) meta.toastT -= dt
                    if (meta.setAwakenedT > 0f) meta.setAwakenedT = max(0f, meta.setAwakenedT - dt)
                    if (meta.setAwakenedHapticPending) {
                        haptics.soundEnabled = progress.soundOn
                        haptics.hapticsEnabled = progress.hapticsOn
                        haptics.combatPulse(6)
                        meta.setAwakenedHapticPending = false
                    }
                    acc += dt
                    if (acc >= 0.033f) { // ~30fps UI
                        acc = 0f
                        frame = now.toFloat()
                    }
                    uiPerfAcc += dt
                    uiPerfN++
                    if (uiPerfAcc >= 1f) {
                        if (BuildConfig.DEBUG) android.util.Log.d("JellyPerf", "ui screen=$screen avg=${"%.0f".format(uiPerfAcc / uiPerfN * 1000f)}ms n=$uiPerfN")
                        uiPerfAcc = 0f; uiPerfN = 0
                    }
                }
            }
        }
    }

    fun handleArenaRawTouch(event: MotionEvent): Boolean {
        if (screen != Screen.ARENA || size.width <= 0 || size.height <= 0) return false

        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val u = min(w, h)
        val controls = arenaControlLayout(w, h)
        val maxJoy = u * 0.13f
        // 事件驱动回收：30s 内无事件的手指视为 UP 丢失，强制释放（同旧 Compose 输入版语义）
        arenaRawTouch.staleIds(SystemClock.uptimeMillis()).forEach { releaseArenaPointer(it) }

        fun inCircle(p: Offset, cx: Float, cy: Float, radius: Float): Boolean {
            val dx = p.x - cx
            val dy = p.y - cy
            return dx * dx + dy * dy <= radius * radius
        }

        fun updateJoystick(p: Offset) {
            val dx = p.x - arenaRawTouch.joyOrigin.x
            val dy = p.y - arenaRawTouch.joyOrigin.y
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val clamped = min(length, maxJoy)
            stickX = dx / length * (clamped / maxJoy)
            stickY = dy / length * (clamped / maxJoy)
            joyKnobX = arenaRawTouch.joyOrigin.x + dx / length * clamped
            joyKnobY = arenaRawTouch.joyOrigin.y + dy / length * clamped
        }

        fun press(pointerId: Int, p: Offset) {
            if (BuildConfig.DEBUG) android.util.Log.d(
                "JellyInterop",
                "DOWN id=$pointerId pos=(${p.x.toInt()},${p.y.toInt()}) joy=${arenaRawTouch.joyId >= 0}"
            )
            if (p.y < h * 0.12f && p.x < w * 0.18f) {
                meta.paused = !meta.paused
                if (meta.paused) releaseArenaTouches()
                return
            }
            if (meta.paused) {
                // 命中区与暂停菜单绘制矩形（x 0.20w~0.80w）对齐，误触菜单外区域不再触发
                val menuX = p.x in w * 0.20f..w * 0.80f
                when {
                    menuX && p.y in h * 0.42f..h * 0.52f -> {
                        pendingAbandonT = 0L
                        meta.paused = false
                    }
                    menuX && p.y in h * 0.54f..h * 0.64f -> {
                        pendingAbandonT = 0L
                        meta.paused = false
                        meta.curHp = arena?.player?.hp ?: meta.curHp
                        meta.curMp = arena?.mp ?: meta.curMp
                        progress.saveActiveRun(meta)
                        screen = Screen.TITLE
                        arena = null
                    }
                    menuX && p.y in h * 0.66f..h * 0.76f -> {
                        // 放弃为破坏性操作：3 秒内二次点击才执行，按钮同步变红提示
                        val now = SystemClock.uptimeMillis()
                        if (now - pendingAbandonT in 1..3_000L) {
                            pendingAbandonT = 0L
                            meta.paused = false
                            meta.fillResult(false)
                            progress.recordRunEnd(
                                false, meta.stageIndex, meta.level,
                                meta.goldEarnedThisRun, meta.kills
                            )
                            screen = Screen.RESULT
                            arena = null
                        } else {
                            pendingAbandonT = now
                        }
                    }
                }
                return
            }

            val sim = arena
            when {
                inCircle(p, controls.ultX, controls.ultY, u * 0.088f * ARENA_HIT_SLOP) -> {
                    arenaRawTouch.ultId = pointerId
                    s4Held = true
                    if (sim != null && !sim.skillUnlocked(4)) {
                        sim.showHint("Lv${sim.skillUnlockLevel(4)} 解锁必杀")
                    } else {
                        sim?.requestTap(4)
                    }
                }
                inCircle(p, controls.potionX, controls.potionY, u * 0.07f * ARENA_HIT_SLOP) -> {
                    if (sim != null && meta.potions > 0 && sim.tryUsePotion()) {
                        meta.potions--
                        meta.potionsUsedThisRun++
                        meta.curHp = sim.player.hp
                        meta.toast = "用药 +${(sim.player.maxHp * 0.4f).toInt()} HP  剩${meta.potions}瓶"
                        meta.toastT = 1.5f
                    } else {
                        meta.toast = if (meta.potions <= 0) "没有药水（地图商店可买）" else "生命已满"
                        meta.toastT = 1.2f
                    }
                }
                inCircle(p, controls.attackX, controls.attackY, u * 0.10f * ARENA_HIT_SLOP_ATTACK) -> {
                    arenaRawTouch.basicId = pointerId
                    basicHeld = true
                    sim?.requestBasicTap()
                    if (BuildConfig.DEBUG) android.util.Log.d("JellyInterop", "=> ATTACK id=$pointerId")
                }
                inCircle(p, controls.skill1X, controls.skill1Y, u * 0.072f * ARENA_HIT_SLOP) -> {
                    arenaRawTouch.skill1Id = pointerId
                    s1Held = true
                    if (sim != null && !sim.skillUnlocked(1)) {
                        sim.showHint("Lv${sim.skillUnlockLevel(1)} 解锁此技能")
                    } else {
                        sim?.requestTap(1)
                    }
                }
                inCircle(p, controls.skill2X, controls.skill2Y, u * 0.072f * ARENA_HIT_SLOP) -> {
                    arenaRawTouch.skill2Id = pointerId
                    s2Held = true
                    if (sim != null && !sim.skillUnlocked(2)) {
                        sim.showHint("Lv${sim.skillUnlockLevel(2)} 解锁此技能")
                    } else {
                        sim?.requestTap(2)
                    }
                }
                inCircle(p, controls.skill3X, controls.skill3Y, u * 0.072f * ARENA_HIT_SLOP) -> {
                    arenaRawTouch.skill3Id = pointerId
                    s3Held = true
                    if (sim != null && !sim.skillUnlocked(3)) {
                        sim.showHint("Lv${sim.skillUnlockLevel(3)} 解锁此技能")
                    } else {
                        sim?.requestTap(3)
                    }
                }
                p.x < w * 0.48f && arenaRawTouch.joyId < 0 -> {
                    arenaRawTouch.joyId = pointerId
                    arenaRawTouch.joyOrigin = p
                    joyOx = p.x
                    joyOy = p.y
                    joyActive = true
                    updateJoystick(p)
                    if (BuildConfig.DEBUG) android.util.Log.d("JellyInterop", "=> JOYSTICK id=$pointerId")
                }
                // 右侧空白兜底：未推摇杆时点按空白=普攻；推杆移动中不触发，保持按键语义
                arenaRawTouch.joyId < 0 -> {
                    arenaRawTouch.basicId = pointerId
                    basicHeld = true
                    sim?.requestBasicTap()
                    if (BuildConfig.DEBUG) android.util.Log.d("JellyInterop", "=> ATK(fallback) id=$pointerId")
                }
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // 新手势开始（ACTION_DOWN = 此前物理上无任何手指在按）：
                // 上一手势若被系统手势截胡吞掉 UP/CANCEL，残留的 joy/技能 id 全是幽灵——先清场再受理
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    releaseArenaTouches()
                    if (BuildConfig.DEBUG) android.util.Log.d("JellyInterop", "SWEEP fresh gesture (ghost clear)")
                }
                val index = event.actionIndex
                press(event.getPointerId(index), Offset(event.getX(index), event.getY(index)))
            }
            MotionEvent.ACTION_MOVE -> {
                // 幽灵摇杆即时检测：手势进行中事件流里已不含摇杆指针（UP 被吞）→ 立即释放，
                // 不必等 30~60s 的兜底回收；下一次触摸马上能重新接管
                val jId = arenaRawTouch.joyId
                if (jId >= 0 && event.findPointerIndex(jId) < 0) {
                    releaseArenaPointer(jId)
                    arenaRawTouch.lastSeen.remove(jId)
                    if (BuildConfig.DEBUG) android.util.Log.d("JellyInterop", "GHOST-JOY release id=$jId (stream lost the pointer)")
                }
                val joyIndex = event.findPointerIndex(arenaRawTouch.joyId)
                if (joyIndex >= 0) {
                    updateJoystick(Offset(event.getX(joyIndex), event.getY(joyIndex)))
                    val nowMs = SystemClock.uptimeMillis()
                    if (BuildConfig.DEBUG && nowMs - arenaRawTouch.lastJoyLogMs > 400) {
                        arenaRawTouch.lastJoyLogMs = nowMs
                        android.util.Log.d(
                            "JellyInterop",
                            "JOY move id=$jId stick=(${"%.2f".format(stickX)},${"%.2f".format(stickY)})"
                        )
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP ->
                releaseArenaPointer(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> {
                if (BuildConfig.DEBUG) android.util.Log.d("JellyInterop", "CANCEL sweep-all")
                releaseArenaTouches()
            }
        }
        // 刷新所有在按手指的活跃时刻：静止手指靠其他手指的事件批次一并刷新
        val lifted = when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> event.getPointerId(event.actionIndex)
            else -> -1
        }
        for (i in 0 until event.pointerCount) {
            val pid = event.getPointerId(i)
            if (pid != lifted) arenaRawTouch.lastSeen[pid] = SystemClock.uptimeMillis()
        }
        return true
    }

    Box(modifier = modifier.fillMaxSize()) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // 战斗必须直接读取 MotionEvent。SH-M10 在先按摇杆后按攻击时，Compose
            // PointerInputChange 会把第二指向右平移 200~300px；Interop 坐标与绘制一致。
            .pointerInteropFilter { event -> handleArenaRawTouch(event) }
            // rebind when size ready — critical so hitboxes match real pixels
            .pointerInput(screen, arenaKey, size.width, size.height, createName, createHeroIdx, charTick, confirmKind, confirmPayload, language) {
                if (size.width <= 0 || size.height <= 0) return@pointerInput
                if (screen == Screen.LOGIN) return@pointerInput
                if (screen == Screen.ARENA) return@pointerInput
                // 全局确认弹窗优先
                if (confirmKind.isNotEmpty()) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                val up = ch.position
                                ch.consume()
                                val ww = size.width.toFloat()
                                val hh = size.height.toFloat()
                                // 取消
                                if (up.y in hh * 0.62f..hh * 0.74f && up.x in ww * 0.12f..ww * 0.48f) {
                                    confirmKind = ""
                                    confirmPayload = ""
                                }
                                // 确认
                                if (up.y in hh * 0.62f..hh * 0.74f && up.x in ww * 0.52f..ww * 0.88f) {
                                    when (confirmKind) {
                                        "new_run" -> {
                                            confirmKind = ""
                                            startRunWithActiveCharacter(meta, progress) { screen = it }
                                        }
                                        "daily_run" -> {
                                            confirmKind = ""
                                            startRunWithActiveCharacter(meta, progress, daily = true) { screen = it }
                                        }
                                        "seed_run" -> {
                                            val code = confirmPayload
                                            confirmKind = ""
                                            confirmPayload = ""
                                            val err = startRunWithActiveCharacter(meta, progress, seedCode = code) { screen = it }
                                            if (err != null) {
                                                seedInput = code
                                                seedMsg = err
                                                seedPanelVisible = true
                                            }
                                        }
                                        "delete_char" -> {
                                            val id = confirmPayload
                                            confirmKind = ""
                                            confirmPayload = ""
                                            if (id.isNotEmpty()) {
                                                progress.deleteCharacter(id)
                                                charTick++
                                            }
                                        }
                                        "exit_chapter" -> {
                                            confirmKind = ""
                                            screen = Screen.STAGE_CLEAR
                                        }
                                        "abandon" -> {
                                            confirmKind = ""
                                            meta.fillResult(won = false)
                                            progress.recordRunEnd(
                                                false, meta.stageIndex, meta.level,
                                                meta.goldEarnedThisRun, meta.kills
                                            )
                                            screen = Screen.RESULT
                                        }
                                        else -> confirmKind = ""
                                    }
                                }
                                break
                            }
                        }
                    }
                    return@pointerInput
                }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var up = down.position
                        var dragY = 0f
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dy = ch.positionChange().y
                            if (dy != 0f) {
                                dragY += dy
                                // 装备栏：上下拖动滚动列表
                                if (screen == Screen.GEAR) {
                                    val hh = size.height.toFloat()
                                    val maxUp = -hh * 0.55f
                                    gearScroll = (gearScroll + dy).coerceIn(maxUp, 0f)
                                }
                                ch.consume()
                            }
                            if (!ch.pressed) {
                                up = ch.position
                                ch.consume()
                                break
                            }
                        }
                        // 拖动较多则不当作点击
                        if (kotlin.math.abs(dragY) > 18f && screen == Screen.GEAR) return@awaitEachGesture
                        handleUiTap(
                            meta, progress, screen, { screen = it }, up,
                            size.width.toFloat(), size.height.toFloat(),
                            onEnterArena = { arenaKey++ },
                            ads = ads,
                            activity = activity,
                            onCharChanged = { charTick++ },
                            createName = createName,
                            setCreateName = { createName = it },
                            createHeroIdx = createHeroIdx,
                            setCreateHeroIdx = { createHeroIdx = it },
                            requestConfirm = { kind, payload ->
                                confirmKind = kind
                                confirmPayload = payload
                            },
                            onPrivacyPolicy = {
                                privacyMessage = context.openPrivacyPolicy().orEmpty()
                            },
                            onAdPrivacy = {
                                ads.showPrivacyOptions(activity) { result ->
                                    privacyMessage = result.orEmpty()
                                }
                            },
                            onLanguageToggle = toggleLanguage,
                            onOpenSeedPanel = {
                                seedMsg = ""
                                seedPanelVisible = true
                            },
                            gearScroll = gearScroll
                        )
                    }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frame
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        when (screen) {
            Screen.TITLE -> drawTitle(
                tm, w, h, meta.pulse, progress,
                weapon = if (titleGearReady) titleRunMeta.equippedWeapon() else null,
                armor = if (titleGearReady) titleRunMeta.equippedArmor() else null,
                setDef = if (titleGearReady) titleRunMeta.activeSet() else null
            )
            Screen.HOW_TO -> drawHowTo(tm, w, h)
            Screen.SETTINGS -> drawSettings(tm, w, h, progress, privacyMessage)
            Screen.CHAR_SELECT -> drawCharSelect(tm, w, h, progress, charTick, meta.pulse)
            Screen.CREATE_CHAR -> drawCreateChar(tm, w, h, createName, createHeroIdx, meta.pulse)
            Screen.CLASS_SELECT -> drawClassSelect(tm, w, h, meta.pulse)
            Screen.STORY -> drawStory(meta, tm, w, h)
            Screen.MAP, Screen.EVENT, Screen.STAGE_CLEAR ->
                drawMap(meta, screen, tm, w, h)
            Screen.SHOP -> drawShop(meta, tm, w, h)
            Screen.GEAR -> drawGear(meta, tm, w, h, gearScroll)
            Screen.CODEX -> drawCodex(meta, progress, tm, w, h)
            Screen.TRIALS -> drawTrials(meta, progress, tm, w, h)
            Screen.LEVEL_UP -> drawLevelUp(meta, tm, w, h)
            Screen.CORE_INK -> drawCoreInkChoice(meta, tm, w, h)
            Screen.RESULT -> drawResult(meta, tm, w, h, progress)
            Screen.LOGIN -> drawRect(
                Brush.verticalGradient(listOf(Color(0xFF0B1020), Color(0xFF1E1B4B))),
                size = Size(w, h)
            )
            Screen.ARENA -> {
                val sim = arena
                if (sim != null) {
                    val t0 = System.nanoTime()
                    drawArena(
                        sim, meta, tm, w, h, stickX, stickY,
                        joyActive, joyOx, joyOy, joyKnobX, joyKnobY,
                        basicHeld, s1Held, s2Held, s3Held, s4Held,
                        wheelActive, wheelX, wheelY, wheelSlotSel, pendingAbandonT
                    )
                    perfDrawMs += (System.nanoTime() - t0) / 1e6f
                    perfDrawN++
                } else drawRect(Color(0xFF0B1020), size = Size(w, h))
            }
        }
        if (meta.setAwakenedT > 0f && meta.setAwakenedId.isNotBlank()) {
            drawSetAwakenedOverlay(meta, tm, w, h)
        }
        if (confirmKind.isNotEmpty()) {
            drawConfirmLayer(tm, w, h, confirmKind)
        }
    }
    if (seedPanelVisible && screen == Screen.TITLE) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC1C1208)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .background(Color(0xF03D2914), RoundedCornerShape(16.dp))
                    .padding(6.dp)
            ) {
                SeedPanel(
                    input = seedInput,
                    message = seedMsg,
                    currentCode = seedCurCode,
                    language = language,
                    onInputChange = { seedInput = it },
                    onLaunch = {
                        if (SeedCode.decode(seedInput) == null) {
                            seedMsg = "种子码格式不对，示例 1lz9f3_2"
                            return@SeedPanel
                        }
                        if (progress.hasActiveRun()) {
                            seedPanelVisible = false
                            confirmKind = "seed_run"
                            confirmPayload = seedInput
                        } else {
                            val err = startRunWithActiveCharacter(meta, progress, seedCode = seedInput) { screen = it }
                            if (err != null) seedMsg = err else seedPanelVisible = false
                        }
                    },
                    onCancel = { seedPanelVisible = false }
                )
            }
        }
    }
    if (screen == Screen.LOGIN) {
        LoginPanel(
            username = loginUser,
            password = loginPass,
            message = loginMsg,
            isRegister = loginIsRegister,
            hasAccount = progress.hasLocalAccount(),
            guestHint = guestHint,
            language = language,
            onUserChange = { loginUser = it },
            onPassChange = { loginPass = it },
            onToggleMode = {
                loginIsRegister = !loginIsRegister
                loginMsg = ""
            },
            onSubmit = {
                val err = if (loginIsRegister) {
                    progress.registerLocal(loginUser, loginPass, asGuest = false)
                } else {
                    progress.loginLocal(loginUser, loginPass)
                }
                if (err != null) {
                    loginMsg = err
                } else {
                    loginMsg = ""
                    loginPass = ""
                    guestHint = ""
                    screen = afterLoginScreen(progress)
                }
            },
            onGuestEnter = {
                val (u, p) = progress.createGuestAndLogin()
                loginUser = u
                loginPass = p
                guestHint = "已生成：用户 $u  口令 $p（请截图保存）"
                loginMsg = ""
                screen = afterLoginScreen(progress)
            },
            onAutoFill = {
                loginUser = GuestIds.username()
                loginPass = GuestIds.password()
                loginIsRegister = true
                loginMsg = "已填入随机档案，点「注册进入」"
                guestHint = "用户 ${loginUser} / 口令 ${loginPass}"
            },
            onLanguageToggle = toggleLanguage
        )
    }
    }
}

/** 用当前选中角色直接开局（职业固定为创建时选择）。seedCode 非空时按分享种子开局 */
private fun startRunWithActiveCharacter(
    meta: RunMeta,
    progress: ProgressStore,
    daily: Boolean = false,
    seedCode: String? = null,
    setScreen: (Screen) -> Unit
): String? {
    val ch = progress.activeCharacter()
    if (ch == null) {
        setScreen(Screen.CHAR_SELECT)
        return null
    }
    // 必须先完成全部校验再清档；否则锁定代数的合法种子会误删当前远征。
    val parsedSeed = seedCode?.let {
        when (val validation = SeedCode.validateForLaunch(it, progress.inkRankUnlocked)) {
            SeedCode.LaunchValidation.InvalidFormat ->
                return "种子码格式不对，示例 1lz9f3_2"
            is SeedCode.LaunchValidation.LockedRank ->
                return "墨阶不足：该种子需要${validation.required}代（你的上限${validation.unlocked}）"
            is SeedCode.LaunchValidation.Valid -> validation
        }
    }
    progress.clearActiveRun()
    val rank = when {
        parsedSeed != null -> parsedSeed.inkRank
        daily -> progress.preferredInkRank.coerceAtLeast(1).coerceAtMost(progress.inkRankUnlocked.coerceAtLeast(1))
        else -> progress.preferredInkRank
    }
    val seed = when {
        parsedSeed != null -> parsedSeed.seed
        daily -> dailyInkSeed()
        else -> null
    }
    meta.immuneMemoryId = progress.preferredImmuneMemoryId
    meta.resetRun(ch.hero, rank, forcedSeed = seed)
    meta.characterName = ch.name
    // 开局应用职业当前皮肤（试炼解锁后可在角色页更换）
    meta.skinId = progress.classSkin(ch.hero).id
    progress.recordRunStart()
    progress.saveActiveRun(meta)
    progress.saveCheckpoint(meta)
    val storyCh = StoryBook.chapters[0]
    meta.queueStory(
        StoryBook.worldPremise + StoryBook.heroGreeting(ch.hero) + storyCh.intro,
        "MAP"
    )
    val tag = when {
        parsedSeed != null -> "种子远征"
        daily -> "今日毒株"
        else -> "免疫出击"
    }
    meta.addJournal("$tag · ${ch.name}（${ch.hero.displayName}） · ${mutationGenerationLabel(rank)} · ${meta.affixHudLine()}")
    meta.toast = "$tag：${ch.name} · ${meta.affixHudLine()}"
    meta.toastT = 2.2f
    setScreen(Screen.STORY)
    return null
}

private fun DrawScope.drawConfirmLayer(tm: TextMeasurer, w: Float, h: Float, kind: String) {
    drawRect(Color(0xCC020617), size = Size(w, h))
    drawRoundRect(
        Color(0xF01E293B),
        Offset(w * 0.12f, h * 0.28f),
        Size(w * 0.76f, h * 0.48f),
        CornerRadius(18f)
    )
    drawRoundRect(
        Color(0x66FBBF24),
        Offset(w * 0.12f, h * 0.28f),
        Size(w * 0.76f, h * 0.48f),
        CornerRadius(18f),
        style = Stroke(2f)
    )
    val (titleText, body) = when (kind) {
        "new_run" -> "开始新冒险？" to "将覆盖当前存档进度，无法恢复。\n确认后从第一章重新出发。"
        "daily_run" -> "挑战今日毒株？" to "使用今日固定变异种子出击（全天相同）。\n会覆盖当前存档，感染特征见标题左下。"
        "delete_char" -> "删除角色？" to "角色与其存档将永久删除。\n此操作不可撤销。"
        "exit_chapter" -> "进入下一章？" to "确认离开本章，前往下一章节。"
        "abandon" -> "放弃本局？" to "本局结束，进度写入生涯统计。\n可在标题重新出征。"
        else -> "确认？" to "请确认操作"
    }
    title(tm, titleText, w * 0.5f, h * 0.34f, Color(0xFFFDE68A), 20.sp)
    // 多行文案必须整串翻译后再拆行：映射表的键含换行，先拆行会导致片段匹配失败回落中文
    GameI18n.tr(body).lines().forEachIndexed { i, line ->
        title(tm, line, w * 0.5f, h * 0.44f + i * h * 0.055f, Color(0xFFE2E8F0), 14.sp)
    }
    drawRoundRect(Color(0xFF334155), Offset(w * 0.12f, h * 0.62f), Size(w * 0.34f, h * 0.11f), CornerRadius(12f))
    title(tm, "取消", w * 0.29f, h * 0.65f, Color.White, 16.sp)
    drawRoundRect(Color(0xFFB45309), Offset(w * 0.54f, h * 0.62f), Size(w * 0.34f, h * 0.11f), CornerRadius(12f))
    title(tm, "确认", w * 0.71f, h * 0.65f, Color.White, 16.sp)
}

private fun currentNode(meta: RunMeta): MapNode? {
    return try {
        meta.stage().nodes.find { it.id == meta.nodeId }
    } catch (_: Exception) {
        null
    }
}

/** 战斗胜利结算：发奖、记档，返回下一屏（由结算延迟结束时机调用） */
private fun settleVictory(
    sim: ArenaSim,
    meta: RunMeta,
    progress: ProgressStore
): Screen {
    meta.gold += sim.goldEarned
    meta.goldEarnedThisRun += sim.goldEarned
    meta.roomsCleared++
    meta.curHp = sim.player.hp
    meta.curMp = sim.mp
    val lootBits = mutableListOf<String>()
    for (id in sim.lootedWeaponIds) {
        WeaponCatalog.byId(id)?.let { w ->
            if (meta.grantWeapon(w)) {
                lootBits.add("${w.classLabel()}${w.name}[${w.element.short}]")
                if (w.canEquip(meta.hero) && w.atkBonus > meta.equippedWeapon().atkBonus) {
                    meta.equipWeapon(w.id)
                }
            } else lootBits.add("武(已有)")
        }
    }
    for (id in sim.lootedTomeIds) {
        TomeCatalog.byId(id)?.let { t ->
            meta.ownedTomes.add(t.id)
            meta.skillPowerBonus += t.powerBonus
            lootBits.add(t.name)
        }
    }
    for (id in sim.lootedItemIds) {
        ItemCatalog.byId(id)?.let { item ->
            meta.addBagItem(item.id, 1)
            lootBits.add(item.name)
        }
    }
    for (id in sim.lootedArmorIds) {
        ArmorCatalog.byId(id)?.let { a ->
            if (meta.grantArmor(a)) {
                lootBits.add("甲${a.name}")
                if (a.canEquip(meta.hero) && a.hpBonus > meta.equippedArmor().hpBonus) {
                    meta.equipArmor(a.id)
                }
            } else lootBits.add("甲(已有)")
        }
    }
    for (id in sim.lootedRingIds) {
        RingCatalog.byId(id)?.let { r ->
            if (meta.grantRing(r)) {
                lootBits.add("戒${r.name}")
                if (meta.equippedRingId.isBlank()) meta.equipRing(r.id)
            } else lootBits.add("戒(已有)")
        }
    }
    for (id in sim.lootedBootsIds) {
        BootsCatalog.byId(id)?.let { b ->
            if (meta.grantBoots(b)) {
                lootBits.add("鞋${b.name}")
                if (b.spdBonus > meta.equippedBoots().spdBonus ||
                    b.hpBonus > meta.equippedBoots().hpBonus
                ) {
                    meta.equipBoots(b.id)
                }
            } else lootBits.add("鞋(已有)")
        }
    }
    progress.unlockCollection(
        meta.ownedWeapons, meta.ownedArmors,
        meta.ownedRings, meta.ownedBoots
    )
    meta.consumeFightBuffsAfterCombat()
    val stage = meta.stage()
    val node = currentNode(meta)
    meta.addJournal(
        StoryBook.journalLine(stage.title, node?.name ?: "未知", true)
    )
    sim.roomTrial?.let { trial ->
        meta.addJournal("${trial.title}·${if (sim.trialSucceeded) "达成" else "未成"}")
    }
    val leveled = meta.addXp(sim.xpEarned)
    val grade = sim.clearGrade.ifEmpty { "B" }
    val newChallenges = settleChallenges(sim, meta, progress, node?.type == NodeType.BOSS, grade)
    // 挑战房通关奖励：额外金币 + 一件当期品级装备
    if (node?.type == NodeType.CHALLENGE) {
        val tierCap = maxGearTierForThreat(meta.stageIndex * 5 + meta.roomsCleared + 3)
        val pool: List<Any> = WeaponCatalog.all.filter { it.tierLevel() in 1..tierCap } +
            ArmorCatalog.all.filter { it.tier in 1..tierCap }
        when (val gearItem = pool.randomOrNull()) {
            is GearWeapon -> if (meta.grantWeapon(gearItem)) {
                if (gearItem.canEquip(meta.hero) && gearItem.atkBonus > meta.equippedWeapon().atkBonus) meta.equipWeapon(gearItem.id)
                meta.toast += " · 挑战奖励 武·${gearItem.name}"
            }
            is GearArmor -> if (meta.grantArmor(gearItem)) {
                if (gearItem.canEquip(meta.hero) && gearItem.hpBonus > meta.equippedArmor().hpBonus) meta.equipArmor(gearItem.id)
                meta.toast += " · 挑战奖励 甲·${gearItem.name}"
            }
            else -> Unit
        }
        meta.gold += 40
        meta.goldEarnedThisRun += 40
    }
    // 职业试炼累计：统计本场合账数据；皮肤首次解锁时提示
    val (_, skinJustUnlocked) = progress.recordTrialBattle(
        meta.hero,
        TrialBattleReport(
            kills = sim.roomKills,
            maxCombo = sim.maxCombo,
            grade = grade,
            bossKilled = node?.type == NodeType.BOSS,
            skillCasts = sim.nonBasicSkillCasts,
            flawless = sim.playerDamageTaken <= 0f
        )
    )
    meta.toast = buildString {
        append("清场 $grade +${sim.goldEarned}金 +${sim.xpEarned}经验")
        sim.roomTrial?.let { trial ->
            append(" · ${trial.title}${if (sim.trialSucceeded) "✓" else "未达"}")
        }
        if (lootBits.isNotEmpty()) append(" · 掉落 ${lootBits.joinToString()}")
        if (newChallenges.isNotEmpty()) {
            append(" · 挑战达成 ${newChallenges.joinToString("、") { it.title }} +${newChallenges.sumOf { it.rewardGold }}金")
        }
        if (skinJustUnlocked) {
            append(" · 职业试炼完成！解锁皮肤「${GameI18n.tr(HeroTrials.rewardSkinFor(meta.hero).displayName)}」")
        }
    }
    meta.toastT = 2.8f
    meta.arenaBanner = "评价 $grade！"
    meta.arenaBannerT = 1.8f
    if (node?.type == NodeType.BOSS) {
        meta.coreInkChoices = coreInkChoices(meta.hero)
        meta.pendingLevelAfterCore = leveled
        meta.queueStory(
            StoryBook.bossDefeat(stage.id),
            "CORE_INK"
        )
        if (leveled) {
            meta.levelChoices = randomPassives(meta.passives, 3)
            meta.pendingMapAfterLevel = true
        }
        return Screen.STORY
    }
    if (leveled) {
        meta.levelChoices = randomPassives(meta.passives, 3)
        meta.pendingMapAfterLevel = true
        return Screen.LEVEL_UP
    }
    return Screen.MAP
}

/** 结算图鉴挑战：返回本次新达成列表，奖励立即进入当局金币与生涯统计口径 */
private fun settleChallenges(
    sim: ArenaSim,
    meta: RunMeta,
    progress: ProgressStore,
    bossBattle: Boolean,
    grade: String
): List<CodexChallenge> {
    val outcome = BattleOutcome(
        maxCombo = sim.maxCombo,
        fightSeconds = sim.fightTimer,
        damageTaken = sim.playerDamageTaken,
        grade = grade,
        bossBattle = bossBattle,
        potionsUsed = meta.potionsUsedThisRun
    )
    val newly = mutableListOf<CodexChallenge>()
    for (c in battleChallengesReached(outcome, progress.completedChallenges())) {
        if (progress.completeChallenge(c.id)) newly.add(c)
    }
    newly.forEach { c ->
        meta.gold += c.rewardGold
        meta.goldEarnedThisRun += c.rewardGold
    }
    return newly
}

private fun handleUiTap(
    meta: RunMeta,
    progress: ProgressStore,
    screen: Screen,
    setScreen: (Screen) -> Unit,
    pos: Offset,
    w: Float,
    h: Float,
    onEnterArena: () -> Unit,
    ads: AdsManager? = null,
    activity: Activity? = null,
    onCharChanged: () -> Unit = {},
    createName: String = "",
    setCreateName: (String) -> Unit = {},
    createHeroIdx: Int = 0,
    setCreateHeroIdx: (Int) -> Unit = {},
    requestConfirm: (String, String) -> Unit = { _, _ -> },
    onPrivacyPolicy: () -> Unit = {},
    onAdPrivacy: () -> Unit = {},
    onLanguageToggle: () -> Unit = {},
    onOpenSeedPanel: () -> Unit = {},
    gearScroll: Float = 0f
) {
    if (meta.setAwakenedT > 0f) {
        // First tap only dismisses the reveal; never leak through to an equipment/shop action.
        meta.setAwakenedT = min(meta.setAwakenedT, 0.35f)
        return
    }
    when (screen) {
        Screen.TITLE -> {
            if (pos.x in w * 0.02f..w * 0.17f && pos.y in h * 0.015f..h * 0.105f) {
                onLanguageToggle()
                return
            }
            // 与 drawTitle 按钮区域严格对齐
            if (pos.x in w * 0.48f..w * 0.90f) {
                val hasSave = progress.hasActiveRun()
                val rows = if (hasSave) {
                    listOf(
                        h * 0.30f to h * 0.39f, // 继续
                        h * 0.40f to h * 0.49f, // 新冒险
                        h * 0.50f to h * 0.58f, // 角色
                        h * 0.59f to h * 0.67f, // 图鉴
                        h * 0.68f to h * 0.76f, // 说明
                        h * 0.77f to h * 0.85f  // 设置
                    )
                } else {
                    listOf(
                        h * 0.30f to h * 0.40f, // 开始
                        h * 0.42f to h * 0.51f, // 角色
                        h * 0.53f to h * 0.62f, // 图鉴
                        h * 0.64f to h * 0.73f, // 说明
                        h * 0.75f to h * 0.84f  // 设置
                    )
                }
                when {
                    hasSave && pos.y in rows[0].first..rows[0].second -> {
                        if (progress.loadActiveRun(meta)) setScreen(Screen.MAP)
                    }
                    hasSave && pos.y in rows[1].first..rows[1].second ->
                        requestConfirm("new_run", "")
                    hasSave && pos.y in rows[2].first..rows[2].second -> setScreen(Screen.CHAR_SELECT)
                    hasSave && pos.y in rows[3].first..rows[3].second -> setScreen(Screen.CODEX)
                    hasSave && pos.y in rows[4].first..rows[4].second -> setScreen(Screen.HOW_TO)
                    hasSave && pos.y in rows[5].first..rows[5].second -> setScreen(Screen.SETTINGS)
                    !hasSave && pos.y in rows[0].first..rows[0].second ->
                        startRunWithActiveCharacter(meta, progress, setScreen = setScreen)
                    !hasSave && pos.y in rows[1].first..rows[1].second -> setScreen(Screen.CHAR_SELECT)
                    !hasSave && pos.y in rows[2].first..rows[2].second -> setScreen(Screen.CODEX)
                    !hasSave && pos.y in rows[3].first..rows[3].second -> setScreen(Screen.HOW_TO)
                    !hasSave && pos.y in rows[4].first..rows[4].second -> setScreen(Screen.SETTINGS)
                }
            }
            // 标题信息区左半切换毒株代次，右半切换本轮携带的免疫记忆。
            if (pos.x in w * 0.48f..w * 0.70f && pos.y in h * 0.17f..h * 0.285f) {
                val next = (progress.preferredInkRank + 1)
                progress.preferredInkRank = if (next > progress.inkRankUnlocked) 0 else next
                meta.toast = "毒株 → ${mutationGenerationLabel(progress.preferredInkRank)}（最高${progress.inkRankUnlocked}代）"
                meta.toastT = 1.6f
            }
            if (pos.x in w * 0.70f..w * 0.92f && pos.y in h * 0.17f..h * 0.285f) {
                val memory = progress.cyclePreferredImmuneMemory()
                meta.toast = "免疫记忆 → ${memory.title}：${memory.desc}"
                meta.toastT = 1.8f
            }
            // 今日毒株：固定日种子（无存档或有存档均可，有存档需确认）
            if (pos.x in w * 0.06f..w * 0.46f && pos.y in h * 0.88f..h * 0.965f) {
                if (progress.hasActiveRun()) {
                    requestConfirm("daily_run", "")
                } else {
                    startRunWithActiveCharacter(meta, progress, daily = true, setScreen = setScreen)
                }
            }
            // 种子远征：弹出种子码面板
            if (pos.x in w * 0.50f..w * 0.90f && pos.y in h * 0.88f..h * 0.965f) {
                onOpenSeedPanel()
            }
        }
        Screen.CHAR_SELECT -> {
            val chars = progress.listCharacters()
            chars.take(6).forEachIndexed { i, c ->
                val y0 = h * 0.16f + i * h * 0.10f
                if (pos.y in y0..(y0 + h * 0.09f) && pos.x in w * 0.08f..w * 0.72f) {
                    progress.selectCharacter(c.id)
                    meta.characterName = c.name
                    meta.hero = c.hero
                    onCharChanged()
                    setScreen(if (progress.seenTutorial) Screen.TITLE else Screen.HOW_TO)
                    return
                }
                // 删除（需确认）
                if (pos.y in y0..(y0 + h * 0.09f) && pos.x in w * 0.74f..w * 0.92f) {
                    requestConfirm("delete_char", c.id)
                    return
                }
            }
            if (pos.y in h * 0.80f..h * 0.90f && pos.x in w * 0.06f..w * 0.33f) {
                if (chars.size < 6) {
                    setCreateName(GameCharacter.autoName(chars.size))
                    setCreateHeroIdx(0)
                    setScreen(Screen.CREATE_CHAR)
                } else {
                    meta.toast = "最多6个角色"
                    meta.toastT = 1.4f
                }
            }
            if (pos.y in h * 0.80f..h * 0.90f && pos.x in w * 0.365f..w * 0.635f) {
                setScreen(Screen.TRIALS)
            }
            if (pos.y in h * 0.80f..h * 0.90f && pos.x in w * 0.67f..w * 0.94f) {
                if (progress.activeCharacter() != null) setScreen(Screen.TITLE)
            }
        }
        Screen.CREATE_CHAR -> {
            // random name
            if (pos.y in h * 0.28f..h * 0.38f && pos.x in w * 0.62f..w * 0.88f) {
                setCreateName(GameCharacter.autoName((0..50).random()))
                return
            }
            HeroClass.entries.forEachIndexed { i, _ ->
                val x0 = w * 0.12f + i * w * 0.26f
                if (pos.y in h * 0.42f..h * 0.68f && pos.x in x0..(x0 + w * 0.24f)) {
                    setCreateHeroIdx(i)
                    return
                }
            }
            if (pos.y in h * 0.78f..h * 0.90f && pos.x in w * 0.30f..w * 0.70f) {
                val hero = HeroClass.entries[createHeroIdx.coerceIn(0, 2)]
                val c = progress.createCharacter(createName, hero)
                if (c != null) {
                    meta.characterName = c.name
                    meta.hero = c.hero
                    onCharChanged()
                    setScreen(if (progress.seenTutorial) Screen.TITLE else Screen.HOW_TO)
                } else {
                    meta.toast = "创建失败（名太长或槽满）"
                    meta.toastT = 1.5f
                }
            }
            if (pos.y in h * 0.92f..h && progress.listCharacters().isNotEmpty()) {
                setScreen(Screen.CHAR_SELECT)
            }
        }
        Screen.HOW_TO -> {
            // 打开图鉴按钮
            if (pos.y in h * 0.78f..h * 0.88f && pos.x in w * 0.15f..w * 0.48f) {
                setScreen(Screen.CODEX)
                return
            }
            progress.seenTutorial = true
            setScreen(Screen.TITLE)
        }
        Screen.CODEX -> {
            // 6 tabs: 武 甲 戒 鞋 套 挑战 —— 装备图鉴只放装备；挑战为生涯记录
            val tabW = w * 0.125f
            for (i in 0..5) {
                val x0 = w * 0.02f + i * (tabW + w * 0.01f)
                if (pos.y in h * 0.105f..h * 0.17f && pos.x in x0..(x0 + tabW)) {
                    meta.codexTab = i
                    meta.codexSelectedId = ""
                    return
                }
            }
            if (meta.codexTab in listOf(0, 1, 4)) {
                HeroClass.entries.forEachIndexed { i, _ ->
                    val x0 = w * 0.70f + i * w * 0.09f
                    if (pos.y in h * 0.105f..h * 0.17f && pos.x in x0..(x0 + w * 0.085f)) {
                        meta.codexHeroIndex = i
                        meta.codexSelectedId = ""
                        return
                    }
                }
            }
            val hero = HeroClass.entries[meta.codexHeroIndex.coerceIn(0, 2)]
            when (meta.codexTab) {
                0 -> {
                    val list = WeaponCatalog.all.filter { it.hero == hero || it.hero == null }
                        .sortedWith(compareBy<GearWeapon>({ !progress.isWeaponKnown(it.id) }, { -it.tierLevel() }, { -it.cost }))
                    list.take(8).forEachIndexed { i, wp ->
                        val y0 = h * 0.20f + i * h * 0.085f
                        if (pos.y in y0..(y0 + h * 0.08f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = wp.id
                            return
                        }
                    }
                }
                1 -> {
                    val list = ArmorCatalog.all.filter { it.hero == hero || it.hero == null }
                        .sortedWith(compareBy<GearArmor>({ !progress.isArmorKnown(it.id) }, { -it.tier }, { -it.cost }))
                    list.take(8).forEachIndexed { i, ar ->
                        val y0 = h * 0.20f + i * h * 0.085f
                        if (pos.y in y0..(y0 + h * 0.08f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = ar.id
                            return
                        }
                    }
                }
                2 -> {
                    RingCatalog.all.filter { it.canEquip(hero) }
                        .sortedWith(compareBy<GearAccessory>({ !progress.isRingKnown(it.id) }, { -it.tier }, { -it.cost }))
                        .take(8).forEachIndexed { i, r ->
                        val y0 = h * 0.20f + i * h * 0.085f
                        if (pos.y in y0..(y0 + h * 0.08f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = r.id
                            return
                        }
                    }
                }
                3 -> {
                    BootsCatalog.all.filter { it.canEquip(hero) }
                        .sortedWith(compareBy<GearAccessory>({ !progress.isBootsKnown(it.id) }, { -it.tier }, { -it.cost }))
                        .take(8).forEachIndexed { i, b ->
                        val y0 = h * 0.20f + i * h * 0.085f
                        if (pos.y in y0..(y0 + h * 0.08f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = b.id
                            return
                        }
                    }
                }
                4 -> {
                    SetCatalog.forHero(hero).forEachIndexed { i, set ->
                        val y0 = h * 0.20f + i * h * 0.12f
                        if (pos.y in y0..(y0 + h * 0.11f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = set.id
                            return
                        }
                    }
                }
                5 -> {
                    CodexChallenge.entries.forEachIndexed { i, c ->
                        val y0 = h * 0.20f + i * h * 0.085f
                        if (pos.y in y0..(y0 + h * 0.08f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = c.id
                            return
                        }
                    }
                }
            }
            if (pos.y in h * 0.90f..h * 0.99f) setScreen(Screen.TITLE)
        }
        Screen.LOGIN -> Unit
        Screen.TRIALS -> {
            HeroClass.entries.forEachIndexed { ci, hero ->
                val x0 = w * 0.05f + ci * w * 0.32f
                val colW = w * 0.28f
                if (pos.y in h * 0.66f..h * 0.73f) {
                    if (pos.x in (x0 + w * 0.01f)..(x0 + w * 0.01f + colW * 0.46f)) {
                        progress.setClassSkin(hero, SkinCatalog.defaultFor(hero).id)
                        return
                    }
                    if (pos.x in (x0 + colW * 0.51f)..(x0 + colW * 0.51f + colW * 0.46f)) {
                        if (progress.isAltSkinUnlocked(hero)) {
                            progress.setClassSkin(hero, HeroTrials.rewardSkinFor(hero).id)
                        } else {
                            meta.toast = "完成该职业全部试炼后解锁"
                            meta.toastT = 1.6f
                        }
                        return
                    }
                }
            }
            if (pos.y in h * 0.875f..h * 0.96f && pos.x in w * 0.35f..w * 0.65f) {
                setScreen(Screen.CHAR_SELECT)
            }
        }
        Screen.SETTINGS -> {
            if (pos.y in h * 0.02f..h * 0.13f && pos.x in w * 0.80f..w * 0.98f) {
                onLanguageToggle()
                return
            }
            if (pos.y in h * 0.40f..h * 0.48f) progress.soundOn = !progress.soundOn
            if (pos.y in h * 0.50f..h * 0.58f) progress.hapticsOn = !progress.hapticsOn
            if (pos.y in h * 0.59f..h * 0.67f && pos.x < w * 0.5f) onPrivacyPolicy()
            if (pos.y in h * 0.59f..h * 0.67f && pos.x >= w * 0.5f) onAdPrivacy()
            if (pos.y in h * 0.72f..h * 0.80f && pos.x in w * 0.25f..w * 0.75f) {
                progress.logoutLocal()
                setScreen(Screen.LOGIN)
                return
            }
            if (pos.y in h * 0.86f..h * 0.95f) setScreen(Screen.TITLE)
        }
        Screen.RESULT -> {
            // 看广告双倍本局金币
            if (!meta.adDoubleClaimed && meta.goldEarnedThisRun > 0 &&
                pos.y in h * 0.60f..h * 0.72f && pos.x in w * 0.18f..w * 0.82f
            ) {
                ads?.showRewarded(
                    activity,
                    onReward = {
                        val bonus = meta.goldEarnedThisRun
                        meta.goldEarnedThisRun += bonus
                        progress.lifetimeGold = progress.lifetimeGold + bonus
                        meta.adDoubleClaimed = true
                        meta.toast = "广告奖励：+${bonus} 生涯金币"
                        meta.toastT = 2f
                    },
                    onFail = {
                        meta.toast = "广告未就绪，稍后再试"
                        meta.toastT = 1.4f
                    }
                )
                return
            }
            // 返回标题
            if (pos.y in h * 0.78f..h * 0.92f && pos.x in w * 0.18f..w * 0.82f) {
                ads?.showInterstitial(activity, everyN = 2) {
                    setScreen(Screen.TITLE)
                } ?: setScreen(Screen.TITLE)
            }
        }
        Screen.CLASS_SELECT -> {
            val gap = w * 0.02f
            val cardW = (w * 0.9f - gap * 2f) / 3f
            val cardH = h * 0.62f
            val y0 = h * 0.22f
            HeroClass.entries.forEachIndexed { i, hc ->
                val x = w * 0.05f + i * (cardW + gap)
                if (pos.x in x..(x + cardW) && pos.y in y0..(y0 + cardH)) {
                    meta.resetRun(hc)
                    progress.recordRunStart()
                    progress.saveActiveRun(meta) // new run slot (overwrites old continue)
                    val ch = StoryBook.chapters[0]
                    meta.queueStory(
                        StoryBook.worldPremise + StoryBook.heroGreeting(hc) + ch.intro,
                        "MAP"
                    )
                    meta.addJournal("以${hc.displayName}身份踏上保存之路。")
                    meta.toast = "出征：${hc.displayName}"
                    meta.toastT = 1.5f
                    setScreen(Screen.STORY)
                }
            }
            if (pos.y in h * 0.88f..h) setScreen(Screen.TITLE)
        }
        Screen.STORY -> {
            fun finishStory() {
                meta.storyQueue = emptyList()
                meta.storyIndex = 0
                when (meta.storyReturn) {
                    "ARENA" -> {
                        if (meta.pendingArenaAfterStory) {
                            meta.pendingArenaAfterStory = false
                            onEnterArena()
                            setScreen(Screen.ARENA)
                        } else setScreen(Screen.MAP)
                    }
                    "LEVEL_UP" -> setScreen(Screen.LEVEL_UP)
                    "CORE_INK" -> setScreen(Screen.CORE_INK)
                    "RESULT" -> setScreen(Screen.RESULT)
                    "STAGE_CLEAR" -> setScreen(Screen.STAGE_CLEAR)
                    else -> setScreen(Screen.MAP)
                }
            }
            // skip button (right)
            if (pos.x in w * 0.62f..w * 0.90f && pos.y in h * 0.82f..h * 0.94f) {
                finishStory()
                return
            }
            // tap to advance one line
            if (!meta.advanceStory()) finishStory()
        }
        Screen.MAP -> {
            val stage = meta.stage()
            val node = currentNode(meta) ?: return
            // quit — bottom-right（确认）
            if (pos.y in h * 0.89f..h * 0.99f && pos.x in w * 0.82f..w * 0.99f) {
                requestConfirm("abandon", "")
                return
            }
            // 回标题并保留进度
            if (pos.y in h * 0.89f..h * 0.99f && pos.x in w * 0.02f..w * 0.20f) {
                progress.saveActiveRun(meta)
                meta.toast = "进度已保存，可继续冒险"
                meta.toastT = 1.2f
                setScreen(Screen.TITLE)
                return
            }
            // 出口：仅点底部「进入下一章」按钮
            val mapGeom = mapPanel(w, h)
            val bottomTopHit = mapGeom[4]
            val btnTopHit = bottomTopHit + h * 0.045f
            val btnHHit = h * 0.12f
            if (node.type == NodeType.EXIT && node.next.isEmpty()) {
                if (pos.y in btnTopHit..(btnTopHit + btnHHit) && pos.x in w * 0.25f..w * 0.75f) {
                    requestConfirm("exit_chapter", "")
                }
                return
            }
            // 顶栏四印：装 / 商 / 药 / 广
            if (pos.y in h * 0.02f..h * 0.10f) {
                when {
                    pos.x in w * 0.58f..w * 0.67f -> {
                        setScreen(Screen.GEAR)
                        return
                    }
                    pos.x in w * 0.68f..w * 0.77f -> {
                        meta.openTravelingShop()
                        setScreen(Screen.SHOP)
                        return
                    }
                    pos.x in w * 0.78f..w * 0.87f && meta.potions > 0 -> {
                        meta.potions--
                        meta.potionsUsedThisRun++
                        meta.ensureVitals()
                        meta.curHp = (meta.curHp + meta.maxHp() * 0.4f).coerceAtMost(meta.maxHp())
                        meta.toast = "用药回复 40% 生命"
                        meta.toastT = 1.4f
                        return
                    }
                    pos.x in w * 0.88f..w * 0.98f -> {
                        if (meta.adPotionClaims >= 2) {
                            meta.toast = "本局广告药已用尽(2/2)"
                            meta.toastT = 1.4f
                            return
                        }
                        ads?.showRewarded(
                            activity,
                            onReward = {
                                meta.adPotionClaims++
                                meta.potions++
                                meta.toast = "广告药 +1 (${meta.adPotionClaims}/2)"
                                meta.toastT = 1.6f
                            },
                            onFail = {
                                meta.toast = "广告未就绪"
                                meta.toastT = 1.2f
                            }
                        )
                        return
                    }
                }
            }
            // 底部选路按钮（与 drawMap 一致）
            val nextNodes = node.next.mapNotNull { id -> stage.nodes.find { it.id == id } }
            if (nextNodes.isNotEmpty()) {
                val gap = w * 0.015f
                val totalW = w * 0.78f
                val btnW = (totalW - gap * (nextNodes.size - 1).coerceAtLeast(0)) / nextNodes.size
                nextNodes.forEachIndexed { i, n ->
                    val x0 = w * 0.04f + i * (btnW + gap)
                    if (pos.y in btnTopHit..(btnTopHit + btnHHit) && pos.x in x0..(x0 + btnW)) {
                        enterNode(meta, n, setScreen, onEnterArena)
                        return
                    }
                }
            }
            // 点地图可走节点（适中半径，减少误点邻点）
            val hitR = min(w, h) * 0.055f
            for (nid in node.next) {
                val n = stage.nodes.find { it.id == nid } ?: continue
                val c = nodeCenter(n, w, h)
                val dx = pos.x - c.x
                val dy = pos.y - c.y
                if (dx * dx + dy * dy <= hitR * hitR) {
                    enterNode(meta, n, setScreen, onEnterArena)
                    return
                }
            }
        }
        Screen.EVENT -> {
            meta.eventChoices.forEachIndexed { i, _ ->
                val y = h * 0.50f + i * h * 0.11f
                if (pos.y in y..(y + h * 0.09f) && pos.x in w * 0.18f..w * 0.82f) {
                    meta.eventChoices[i].second.invoke()
                    meta.eventChoices = emptyList()
                    if (meta.pendingShopAfterEvent) {
                        meta.pendingShopAfterEvent = false
                        meta.openTravelingShop()
                        setScreen(Screen.SHOP)
                    } else {
                        setScreen(Screen.MAP)
                    }
                    return
                }
            }
            if (meta.eventChoices.isEmpty()) setScreen(Screen.MAP)
        }
        Screen.GEAR -> {
            if (pos.y in h * 0.90f..h * 0.99f) {
                setScreen(Screen.MAP)
                return
            }
            val sc = gearScroll
            data class GRow(val kind: String, val id: String, val name: String)
            val rows = ArrayList<GRow>()
            meta.ownedWeaponsSorted().forEach { rows.add(GRow("w", it.id, it.name)) }
            meta.ownedArmorsSorted().forEach { rows.add(GRow("a", it.id, it.name)) }
            meta.ownedRingsSorted().forEach { rows.add(GRow("r", it.id, it.name)) }
            meta.ownedBootsSorted().forEach { rows.add(GRow("b", it.id, it.name)) }
            val listTop = h * 0.26f
            val rowH = h * 0.09f
            rows.forEachIndexed { i, row ->
                val y = listTop + i * rowH + sc
                if (pos.y in y..(y + rowH * 0.88f) && pos.y in (h * 0.26f)..(h * 0.88f) && pos.x in w * 0.06f..w * 0.94f) {
                    when (row.kind) {
                        "w" -> {
                            meta.equipWeapon(row.id)
                            meta.toast = "装备 ${row.name}"
                        }
                        "a" -> {
                            meta.equipArmor(row.id)
                            meta.toast = "装备 ${row.name}"
                        }
                        "r" -> meta.equipRing(row.id)
                        "b" -> meta.equipBoots(row.id)
                    }
                    meta.toastT = 1.2f
                    return
                }
            }
        }
        Screen.LEVEL_UP -> {
            meta.levelChoices.forEachIndexed { i, p ->
                val y = h * 0.32f + i * h * 0.14f
                if (pos.y in y..(y + h * 0.12f) && pos.x in w * 0.1f..w * 0.9f) {
                    meta.passives.add(p)
                    meta.ensureVitals()
                    if (p == PassiveId.HP_UP) meta.curHp = meta.maxHp()
                    val unlocked = skillUnlocksAtLevel(meta.hero, meta.level)
                    meta.toast = if (unlocked.isNotEmpty()) {
                        "天赋：${p.title} · 学会 ${unlocked.joinToString { it.name }}!"
                    } else {
                        "获得天赋：${p.title}"
                    }
                    meta.toastT = 2.2f
                    meta.levelChoices = emptyList()
                    setScreen(if (meta.pendingMapAfterLevel) Screen.MAP else Screen.MAP)
                    meta.pendingMapAfterLevel = false
                    return
                }
            }
        }
        Screen.CORE_INK -> {
            meta.coreInkChoices.forEachIndexed { i, ink ->
                val y = h * 0.30f + i * h * 0.17f
                if (pos.y in y..(y + h * 0.145f) && pos.x in w * 0.08f..w * 0.92f) {
                    val rank = meta.coreInkRanks.upgrade(ink)
                    meta.addJournal("免疫核心·${ink.title}${rank}阶")
                    meta.toast = "${ink.glyph} ${ink.title} · ${rank}阶"
                    meta.toastT = 2.4f
                    meta.coreInkChoices = emptyList()
                    progress.saveActiveRun(meta)
                    val goLevel = meta.pendingLevelAfterCore && meta.levelChoices.isNotEmpty()
                    meta.pendingLevelAfterCore = false
                    setScreen(if (goLevel) Screen.LEVEL_UP else Screen.MAP)
                    return
                }
            }
        }
        Screen.SHOP -> {
            if (pos.y in h * 0.88f..h * 0.98f && pos.x in w * 0.54f..w * 0.92f) {
                setScreen(Screen.MAP)
                return
            }
            if (pos.y in h * 0.88f..h * 0.98f && pos.x in w * 0.08f..w * 0.46f) {
                setScreen(Screen.GEAR)
                return
            }
            fun afterBuyOk(boughtId: String? = null) {
                progress.unlockCollection(
                    meta.ownedWeapons, meta.ownedArmors,
                    meta.ownedRings, meta.ownedBoots
                )
                meta.shopWeaponOffers = meta.shopWeaponOffers.filter { it != boughtId }
                meta.shopArmorOffers = meta.shopArmorOffers.filter { it != boughtId }
                meta.shopRingOffers = meta.shopRingOffers.filter { it != boughtId }
                meta.shopBootsOffers = meta.shopBootsOffers.filter { it != boughtId }
                meta.refreshShopOffers()
            }
            val offers = buildShopOffers(meta)
            val cols = 2
            val cellW = w * 0.44f
            val cellH = h * 0.105f
            val gapX = w * 0.03f
            val startY = h * 0.14f
            offers.take(12).forEachIndexed { i, o ->
                val col = i % cols
                val row = i / cols
                val x = w * 0.045f + col * (cellW + gapX)
                val y = startY + row * (cellH + h * 0.012f)
                if (pos.y in y..(y + cellH) && pos.x in x..(x + cellW)) {
                    val err = when (o.kind) {
                        "w" -> meta.tryBuyWeapon(o.id)
                        "a" -> meta.tryBuyArmor(o.id)
                        "r" -> meta.tryBuyRing(o.id)
                        "b" -> meta.tryBuyBoots(o.id)
                        "f" -> meta.tryBuyFruit(o.id)
                        "potion" -> meta.tryBuyPotion(18)
                        "tome" -> {
                            val t = TomeCatalog.byId(o.id)
                            when {
                                t == null -> "无效"
                                !meta.buyTome(t) -> "金币不足(需${t.cost})"
                                else -> {
                                    meta.shopTomeId = null
                                    meta.toast = "研习 ${t.name}"
                                    meta.toastT = 1.4f
                                    null
                                }
                            }
                        }
                        else -> "无效"
                    }
                    if (err != null) {
                        meta.toast = err
                        meta.toastT = 1.5f
                    } else if (o.kind == "f") {
                        meta.shopFruitOffers = meta.shopFruitOffers.filter { it != o.id }
                        if (meta.shopFruitOffers.isEmpty()) {
                            meta.shopFruitOffers = ItemCatalog.shopFruits(2).map { it.id }
                        }
                    } else if (o.kind != "tome" && o.kind != "potion") {
                        afterBuyOk(o.id)
                    }
                    return
                }
            }
        }
        Screen.STAGE_CLEAR -> {
            if (meta.stageIndex + 1 < meta.stages().size) {
                val nextIdx = meta.stageIndex + 1
                meta.stageIndex = nextIdx
                meta.nodeId = 0
                meta.visited = mutableSetOf(0)
                meta.ensureVitals()
                meta.curHp = meta.maxHp()
                meta.curMp = meta.maxMp()
                // 过关即存检查点：本章内阵亡可从本章开头重来（不算生涯结束）
                progress.saveCheckpoint(meta)
                progress.saveActiveRun(meta)
                val ch = StoryBook.chapters.getOrNull(nextIdx)
                if (ch != null) {
                    meta.queueStory(ch.intro, "MAP")
                    meta.addJournal("进入${ch.title}")
                    setScreen(Screen.STORY)
                } else {
                    setScreen(Screen.MAP)
                }
                meta.toast = meta.stage().title
                meta.toastT = 2f
            } else {
                meta.fillResult(won = true)
                progress.recordRunEnd(true, meta.stageIndex, meta.level, meta.goldEarnedThisRun, meta.kills)
                meta.queueStory(StoryBook.ending(meta.hero, meta.kills, meta.level), "RESULT")
                setScreen(Screen.STORY)
            }
        }
        Screen.ARENA -> {
            // pause toggle top-left
            if (pos.y in h * 0.035f..h * 0.11f && pos.x in w * 0.02f..w * 0.16f) {
                meta.paused = !meta.paused
                return
            }
            if (meta.paused) {
                // resume
                if (pos.y in h * 0.48f..h * 0.58f) meta.paused = false
                // quit
                if (pos.y in h * 0.60f..h * 0.70f) {
                    meta.paused = false
                    meta.fillResult(won = false)
                    progress.recordRunEnd(false, meta.stageIndex, meta.level, meta.goldEarnedThisRun, meta.kills)
                    setScreen(Screen.RESULT)
                }
            }
        }
    }
}

private fun enterNode(
    meta: RunMeta,
    node: MapNode,
    setScreen: (Screen) -> Unit,
    onEnterArena: () -> Unit
) {
    meta.nodeId = node.id
    meta.visited.add(node.id)
    meta.ensureVitals()
    val stage = meta.stage()
    val enterBeats = StoryBook.nodeEnter(stage.id, node.name, node.type)

    fun goFight() {
        val el = node.resolvedElement()
        val theme = if (el != null) {
            val rec = el.beatenBy()
            "${el.gateName()} · 荐${rec.short}装(${rec.short}克${el.short})  "
        } else ""
        val taunt = StoryBook.combatTaunt(node.type, 0, node.waves.size.coerceAtLeast(1)) ?: ""
        val aff = meta.affixHudLine().let { if (it.isNotEmpty()) " · $it" else "" }
        meta.arenaBanner = (theme + taunt + aff).trim()
        meta.arenaBannerT = 3.2f
        meta.queueStory(enterBeats, "ARENA", thenArena = true)
        setScreen(Screen.STORY)
    }

    when (node.type) {
        NodeType.START -> {
            meta.queueStory(enterBeats, "MAP")
            setScreen(Screen.STORY)
        }
        NodeType.MOB, NodeType.ELITE, NodeType.BOSS, NodeType.CHALLENGE -> goFight()
        NodeType.TREASURE -> {
            val threat = meta.stageIndex * 5 + meta.roomsCleared
            val tierCap = maxGearTierForThreat(threat + 3)
            val weapons = WeaponCatalog.all.filter { it.tierLevel() in 1..tierCap && it.cost > 0 }.shuffled()
            val armors = ArmorCatalog.all.filter { it.tier in 1..tierCap && it.cost > 0 }.shuffled()
            val accessories = (RingCatalog.all.filter { it.tier in 1..tierCap && it.cost > 0 } +
                BootsCatalog.all.filter { it.tier in 1..tierCap && it.cost > 0 }).shuffled()
            val offers = buildList {
                weapons.getOrNull(0)?.let { add(Triple(it.name, 0, it.id)) }
                armors.getOrNull(0)?.let { add(Triple(it.name, 1, it.id)) }
                accessories.getOrNull(0)?.let { add(Triple(it.name, 2, it.id)) }
            }.shuffled()
            meta.eventTitle = node.name
            meta.eventBody = "三件免疫造物在此流转成形——只能取走一件。\n（重复的装备会折算成金币）"
            meta.eventChoices = offers.map { (name, kind, id) ->
                "取走「$name」" to {
                    var outcome = ""
                    when (kind) {
                        0 -> WeaponCatalog.byId(id)?.let { w ->
                            if (meta.grantWeapon(w)) {
                                if (w.canEquip(meta.hero) && w.atkBonus > meta.equippedWeapon().atkBonus) meta.equipWeapon(w.id)
                                outcome = "宝物入手：「${w.name}」"
                            } else {
                                meta.gold += 12; meta.goldEarnedThisRun += 12
                                outcome = "已是旧识（已有），折算 +12 金"
                            }
                        }
                        1 -> ArmorCatalog.byId(id)?.let { a ->
                            if (meta.grantArmor(a)) {
                                if (a.canEquip(meta.hero) && a.hpBonus > meta.equippedArmor().hpBonus) meta.equipArmor(a.id)
                                outcome = "宝物入手：「${a.name}」"
                            } else {
                                meta.gold += 12; meta.goldEarnedThisRun += 12
                                outcome = "已是旧识（已有），折算 +12 金"
                            }
                        }
                        else -> {
                            val acc = RingCatalog.byId(id) ?: BootsCatalog.byId(id)
                            if (acc != null) {
                                val freshRing = meta.grantRing(acc)
                                val freshBoots = if (freshRing) false else meta.grantBoots(acc)
                                if (freshRing && meta.equippedRingId.isBlank()) meta.equipRing(acc.id)
                                if (freshBoots && meta.equippedBootsId.isBlank()) meta.equipBoots(acc.id)
                                outcome = if (freshRing || freshBoots) "宝物入手：「${acc.name}」"
                                else {
                                    meta.gold += 12; meta.goldEarnedThisRun += 12
                                    "已是旧识（已有），折算 +12 金"
                                }
                            }
                        }
                    }
                    meta.addJournal(outcome.take(28))
                    meta.toast = outcome.take(36)
                    meta.toastT = 2.4f
                }
            }
            setScreen(Screen.EVENT)
        }
        NodeType.GOLD -> {
            val gMul = meta.combatMods().goldMul
            val got = (node.goldDrop * gMul).toInt().coerceAtLeast(node.goldDrop)
            meta.gold += got
            meta.goldEarnedThisRun += got
            meta.eventTitle = node.name
            meta.eventBody = enterBeats.joinToString("\n") { "${it.speaker}：${it.line}" } +
                "\n\n获得 ${got} 金" + (if (got > node.goldDrop) "（厚金加成）" else "") +
                "。\n可以招来行商立刻买装，或再赌一把。"
            val gamble = (got * 0.6f).toInt().coerceAtLeast(4)
            meta.eventChoices = listOf(
                "收下并买装" to {
                    meta.addJournal("在${node.name}拾取了${got}金并招行商。")
                    meta.pendingShopAfterEvent = true
                },
                "只拿金币" to {
                    meta.addJournal("在${node.name}拾取了${got}金。")
                },
                "再赌一把(+或-)" to {
                    // 简单乐趣：50% 翻倍赌金，50% 输掉一部分
                    val win = (System.nanoTime() and 1L) == 0L
                    if (win) {
                        meta.gold += gamble
                        meta.goldEarnedThisRun += gamble
                        meta.toast = "赌赢 +$gamble 金!"
                        meta.addJournal("在${node.name}赌赢了$gamble 金。")
                    } else {
                        val lose = gamble.coerceAtMost(meta.gold)
                        meta.gold = (meta.gold - lose).coerceAtLeast(0)
                        meta.toast = "赌输 −$lose 金…"
                        meta.addJournal("在${node.name}赌输了$lose 金。")
                    }
                    meta.toastT = 1.8f
                }
            )
            meta.toast = "+$got 金 · 可选买装/再赌"
            meta.toastT = 1.8f
            setScreen(Screen.EVENT)
        }
        NodeType.HEAL -> {
            if (node.goldDrop > 0) {
                val g = (node.goldDrop * meta.combatMods().goldMul).toInt().coerceAtLeast(1)
                meta.gold += g
                meta.goldEarnedThisRun += g
            }
            meta.eventTitle = node.name
            meta.eventBody = enterBeats.joinToString("\n") { "${it.speaker}：${it.line}" } +
                "\n\n泉边可饮。也可把气息压成下一战锋芒。"
            meta.eventChoices = listOf(
                "饮下（回血蓝）" to {
                    meta.curHp = (meta.curHp + meta.maxHp() * 0.45f).coerceAtMost(meta.maxHp())
                    meta.curMp = (meta.curMp + meta.maxMp() * 0.4f).coerceAtMost(meta.maxMp())
                    meta.addJournal("在泉水边喘了口气。")
                },
                "淬锋（少回血·攻+8一战）" to {
                    meta.curHp = (meta.curHp + meta.maxHp() * 0.2f).coerceAtMost(meta.maxHp())
                    meta.buffAtk = (meta.buffAtk + 8f)
                    meta.toast = "下一战攻+8"
                    meta.toastT = 1.6f
                    meta.addJournal("在泉边淬了锋。")
                }
            )
            setScreen(Screen.EVENT)
        }
        NodeType.TRAP -> {
            val dmg = node.trapDmg * meta.combatMods().dmgTakenMul
            // 陷阱改为风险换奖励：硬扛满伤但捡走机关里的金，巧解半伤无收益
            val loot = ((node.goldDrop.takeIf { it > 0 } ?: (8 + meta.inkRank * 2)) *
                meta.combatMods().goldMul).toInt().coerceAtLeast(5)
            meta.eventTitle = node.name
            meta.eventBody = enterBeats.joinToString("\n") { "${it.speaker}：${it.line}" } +
                "\n\n机关后似乎藏着${loot}金。硬扛取金，还是巧妙绕开？"
            meta.eventChoices = listOf(
                "硬扛机关（−${dmg.toInt()}HP · +${loot}金）" to {
                    meta.curHp = (meta.curHp - dmg).coerceAtLeast(1f)
                    meta.gold += loot
                    meta.goldEarnedThisRun += loot
                    meta.addJournal("硬扛了${node.name}，捡走${loot}金。")
                    meta.toast = "+$loot 金"
                    meta.toastT = 1.5f
                },
                "巧解绕行（−${(dmg * 0.5f).toInt()}HP）" to {
                    meta.curHp = (meta.curHp - dmg * 0.5f).coerceAtLeast(1f)
                    meta.addJournal("巧解绕过了${node.name}。")
                }
            )
            setScreen(Screen.EVENT)
        }
        NodeType.REST -> {
            meta.eventTitle = node.name
            meta.eventBody = enterBeats.joinToString("\n") { "${it.speaker}：${it.line}" } +
                "\n\n选择一种休整："
            meta.eventChoices = listOf(
                "回血 50%" to {
                    meta.curHp = (meta.curHp + meta.maxHp() * 0.5f).coerceAtMost(meta.maxHp())
                    meta.addJournal("篝火旁包扎伤口。")
                },
                "回蓝 70%" to {
                    meta.curMp = (meta.curMp + meta.maxMp() * 0.7f).coerceAtMost(meta.maxMp())
                    meta.addJournal("对着火光默念符诀。")
                },
                "花15金磨刀" to {
                    when {
                        meta.weaponLevel >= weaponUpgradeTable(meta.hero).lastIndex -> {
                            meta.toast = "武器已满级"
                            meta.toastT = 1.4f
                        }
                        meta.gold < 15 -> {
                            meta.toast = "金币不足(需15 现有${meta.gold})"
                            meta.toastT = 1.4f
                        }
                        else -> {
                            meta.gold -= 15
                            meta.weaponLevel++
                            meta.addJournal("火旁把武器磨利了一级。")
                            meta.toast = "武器锻造 +1"
                            meta.toastT = 1.4f
                        }
                    }
                }
            )
            setScreen(Screen.EVENT)
        }
        NodeType.EVENT -> {
            val script = StoryBook.eventScript(node.eventId)
            meta.eventTitle = script.first
            meta.eventBody = script.second
            meta.eventChoices = script.third.map { triple ->
                triple.first to {
                    var outcome = triple.second
                    when (triple.third) {
                        "blood_atk" -> {
                            meta.curHp = (meta.curHp - 30f).coerceAtLeast(1f)
                            meta.passives.add(PassiveId.ATK_UP)
                            meta.toast = "以血换攻"
                            meta.toastT = 1.3f
                        }
                        "buy_potion" -> {
                            val err = meta.tryBuyPotion(20)
                            if (err != null) {
                                outcome = err
                            }
                        }
                        "crit" -> meta.passives.add(PassiveId.CRIT)
                        "lifesteal" -> if (meta.gold >= 20) {
                            meta.gold -= 20
                            meta.passives.add(PassiveId.LIFESTEAL)
                            meta.toast = "获得吸血天赋"
                            meta.toastT = 1.3f
                        } else {
                            outcome = "金币不足(需20，现有${meta.gold})"
                        }
                        "smash" -> {
                            meta.gold += 35
                            meta.goldEarnedThisRun += 35
                            meta.curHp = (meta.curHp - 40f).coerceAtLeast(1f)
                        }
                        "gold15" -> {
                            meta.gold += 15
                            meta.goldEarnedThisRun += 15
                        }
                        "gold25" -> {
                            meta.gold += 25
                            meta.goldEarnedThisRun += 25
                        }
                        "heal_soft" -> {
                            meta.ensureVitals()
                            meta.curHp = (meta.curHp + meta.maxHp() * 0.35f).coerceAtMost(meta.maxHp())
                        }
                        "spd" -> {
                            if (meta.gold >= 20) {
                                meta.gold -= 20
                                meta.passives.add(PassiveId.SPD_UP)
                                meta.toast = "获得移速天赋"
                                meta.toastT = 1.3f
                            } else {
                                outcome = "金币不足(需20，现有${meta.gold})"
                            }
                        }
                        "hp" -> meta.passives.add(PassiveId.HP_UP)
                        "armor" -> meta.passives.add(PassiveId.ARMOR)
                        "skill_amp" -> {
                            meta.curHp = (meta.curHp - 25f).coerceAtLeast(1f)
                            meta.skillPowerBonus += 0.08f
                        }
                        "mana_cdr" -> {
                            meta.curMp = meta.maxMp()
                            meta.passives.add(PassiveId.CDR)
                        }
                        "fruit_gift" -> {
                            val fruit = ItemCatalog.randomFruit()
                            meta.addBagItem(fruit.id)
                            outcome = "墨滴凝成「${fruit.name}」"
                        }
                        "forge_weapon" -> when {
                            meta.weaponLevel >= weaponUpgradeTable(meta.hero).lastIndex -> outcome = "武器锻造已满"
                            meta.gold < 30 -> outcome = "金币不足(需30，现有${meta.gold})"
                            else -> {
                                meta.gold -= 30
                                meta.weaponLevel++
                                outcome = "武器锻造提升至 Lv${meta.weaponLevel}"
                            }
                        }
                        "mystery_weapon" -> if (meta.gold < 25) {
                            outcome = "金币不足(需25，现有${meta.gold})"
                        } else {
                            meta.gold -= 25
                            val weapon = WeaponCatalog.randomDrop(meta.hero)
                            if (weapon != null) {
                                val fresh = meta.grantWeapon(weapon)
                                if (fresh && weapon.atkBonus >= meta.equippedWeapon().atkBonus) meta.equipWeapon(weapon.id)
                                outcome = if (fresh) "锻炉赠予「${weapon.name}」" else "锻炉吐出已有兵器，返还10金"
                                if (!fresh) meta.gold += 10
                            }
                        }
                        "leave" -> Unit
                        else -> Unit
                    }
                    meta.addJournal(outcome.take(28))
                    meta.toast = outcome.take(36)
                    meta.toastT = 2.2f
                }
            }
            setScreen(Screen.EVENT)
        }
        NodeType.SHOP -> {
            meta.refreshShopOffers()
            meta.storyReturn = "MAP"
            setScreen(Screen.SHOP)
            meta.toast = meta.shopRecommendTip()
            meta.toastT = 2.4f
        }
        NodeType.EXIT -> {
            val ch = StoryBook.chapters.getOrNull(stage.chapterIndex)
            if (ch != null) {
                meta.queueStory(ch.clear, "STAGE_CLEAR")
                meta.addJournal("${ch.title} 告一段落。")
                setScreen(Screen.STORY)
            } else setScreen(Screen.STAGE_CLEAR)
        }
    }
}

/** 地图主面板几何：与 drawMap 严格一致（精简顶栏后地图更宽） */
/**
 * 移动中技能盘选槽：以按下点为「攻」（槽0），按相对滑动增量选技能。
 * 布局与实体按钮同构（缩放 0.6）：技1 左下、技2 左、技3 左上、必杀 上。
 * 只依赖相对增量 —— 本机固件在两指同按时平移第二指绝对坐标，但平移在增量中抵消。
 */
internal fun pickGhostSlot(dx: Float, dy: Float, w: Float, h: Float, radius: Float = 60f): Int {
    val ghosts = arrayOf(
        1 to (-0.072f * w to 0.048f * h),   // 技1：左下
        2 to (-0.120f * w to -0.036f * h),  // 技2：左
        3 to (-0.084f * w to -0.132f * h),  // 技3：左上
        4 to (-0.012f * w to -0.204f * h)   // 必杀：上
    )
    var best = 0
    var bestD = radius
    for ((slot, off) in ghosts) {
        val gx = dx - off.first
        val gy = dy - off.second
        val d = sqrt(gx * gx + gy * gy)
        if (d < bestD) {
            bestD = d
            best = slot
        }
    }
    return best
}

private fun mapPanel(w: Float, h: Float): FloatArray {
    // l, t, mw, mh, bottomTop
    val hudBottom = h * 0.11f
    val bottomTop = h * 0.72f
    val l = w * 0.03f
    val t = hudBottom + h * 0.01f
    val mw = w * 0.94f
    val mh = (bottomTop - t - h * 0.01f).coerceAtLeast(h * 0.45f)
    return floatArrayOf(l, t, mw, mh, bottomTop)
}

private fun nodeCenter(n: MapNode, w: Float, h: Float): Offset {
    val p = mapPanel(w, h)
    val l = p[0]; val t = p[1]; val mw = p[2]; val mh = p[3]
    // 内边距，避免节点贴边被裁切
    val padX = mw * 0.04f
    val padY = mh * 0.08f
    return Offset(
        l + padX + n.nx * (mw - padX * 2f),
        t + padY + n.ny * (mh - padY * 2f)
    )
}

/** 小五行相克图：金→木→土→水→火→金 */
private fun DrawScope.drawWuxingChart(tm: TextMeasurer, left: Float, top: Float, width: Float, height: Float) {
    drawRoundRect(Color(0xCC0F172A), Offset(left, top), Size(width, height), CornerRadius(12f))
    drawRoundRect(Color(0xFF64748B), Offset(left, top), Size(width, height), CornerRadius(12f), style = Stroke(1.5f))
    title(tm, "五行相克", left + width * 0.5f, top + 6f, Color(0xFFFDE68A), 11.sp)
    title(tm, "箭头=克制", left + width * 0.5f, top + 22f, Color(0xFF94A3B8), 8.sp)
    val order = listOf(WuXing.METAL, WuXing.WOOD, WuXing.EARTH, WuXing.WATER, WuXing.FIRE)
    val cx = left + width * 0.5f
    val cy = top + height * 0.52f
    val r = min(width, height) * 0.28f
    val pts = order.mapIndexed { i, el ->
        val ang = -Math.PI.toFloat() / 2f + i * (2f * Math.PI.toFloat() / 5f)
        el to Offset(cx + cos(ang) * r, cy + sin(ang) * r)
    }
    for (i in pts.indices) {
        val a = pts[i].second
        val b = pts[(i + 1) % pts.size].second
        drawLine(Color(0x88E2E8F0), a, b, 2.5f, StrokeCap.Round)
        // arrow head near target
        val mx = a.x + (b.x - a.x) * 0.72f
        val my = a.y + (b.y - a.y) * 0.72f
        drawCircle(Color(0xFFCBD5E1), 3f, Offset(mx, my))
    }
    pts.forEach { (el, p) ->
        drawCircle(Color(0xFF0F172A), 16f, p)
        drawCircle(el.color, 16f, p, style = Stroke(2.5f))
        title(tm, el.short, p.x, p.y - 7f, el.color, 12.sp)
    }
    title(tm, "金克木·木克土", left + width * 0.5f, top + height - 34f, Color(0xFF94A3B8), 8.sp)
    title(tm, "土克水·水克火·火克金", left + width * 0.5f, top + height - 18f, Color(0xFF94A3B8), 8.sp)
}

// ── Draw ────────────────────────────────────────────────────────────────

private fun DrawScope.drawImmuneTitleBackdrop(w: Float, h: Float, pulse: Float) {
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFFFFE8DF), Color(0xFFE8B5AD), Color(0xFF9F5360))),
        size = Size(w, h)
    )
    // 贯穿人体的血管主干；粗底线让它在浅色组织上保持可读。
    fun vessel(color: Color, y: Float, phase: Float, width: Float) {
        val path = Path()
        path.moveTo(-w * 0.05f, y)
        for (i in 0..12) {
            val x = w * i / 11f
            val yy = y + sin(i * 0.9f + phase + pulse * 0.35f) * h * 0.055f
            path.lineTo(x, yy)
        }
        drawPath(path, Color(0x26000000), style = Stroke(width + 8f, cap = StrokeCap.Round))
        drawPath(path, color.copy(alpha = 0.36f), style = Stroke(width, cap = StrokeCap.Round))
    }
    vessel(Color(0xFFD83B55), h * 0.34f, 0.2f, h * 0.035f)
    vessel(Color(0xFF3B82A0), h * 0.67f, 1.7f, h * 0.028f)

    // 半透明组织细胞与细胞核。
    for (i in 0 until 30) {
        val x = w * (((i * 157 + 41) % 997) / 997f)
        val y = h * (0.08f + (((i * 89 + 17) % 883) / 883f) * 0.80f)
        val r = h * (0.020f + (i % 5) * 0.004f)
        drawCircle(Color(0x20FFF7ED), r, Offset(x, y))
        drawCircle(Color(0x307C2D4A), r, Offset(x, y), style = Stroke(1.5f))
        if (i % 3 == 0) drawCircle(Color(0x388B2F55), r * 0.28f, Offset(x + r * 0.15f, y - r * 0.1f))
    }
    // 漂浮红细胞强调“人体内”而非山水卷轴。
    for (i in 0 until 12) {
        val x = w * (0.04f + i * 0.085f)
        val y = h * (0.50f + sin(i * 1.3f + pulse * 0.8f) * 0.18f)
        drawOval(
            Color(0x42BE123C),
            topLeft = Offset(x - h * 0.020f, y - h * 0.010f),
            size = Size(h * 0.040f, h * 0.020f)
        )
    }
}

private fun DrawScope.drawTitle(
    tm: TextMeasurer,
    w: Float,
    h: Float,
    pulse: Float,
    progress: ProgressStore,
    weapon: GearWeapon? = null,
    armor: GearArmor? = null,
    setDef: GearSetDef? = null
) {
    drawImmuneTitleBackdrop(w, h, pulse)
    val titleScale = min(w, h) * 0.075f
    val active = progress.activeCharacter()
    val skin = if (active != null) SkinCatalog.defaultFor(active.hero) else SkinCatalog.warriorDefault
    val heroX = w * 0.22f
    val heroY = h * 0.48f + sin(pulse * 2f) * 4f
    // 与实际装备联动：武器/防具分列角色两侧，套装带光环（同地图角色卡视觉语言）
    if (weapon != null) {
        drawWeaponArt(heroX - titleScale * 1.9f, heroY + titleScale * 0.05f, titleScale * 1.15f, weapon, pulse)
    }
    if (armor != null) {
        drawArmorArt(heroX + titleScale * 1.9f, heroY + titleScale * 0.1f, titleScale * 1.05f, armor, pulse)
    }
    if (setDef != null) {
        drawSetAura(heroX, heroY, titleScale, setDef, pulse)
    }
    // 手绘立绘优先（战斗内软体小人另走 Canvas 物理体）
    val activeHero = active?.hero
    val drewPortrait = activeHero != null &&
        drawHeroPortrait(activeHero, heroX, heroY + titleScale * 1.3f, titleScale * 2.6f)
    if (!drewPortrait) {
        drawCuteHero(heroX, heroY, titleScale * 1.2f, 1f, skin)
    }
    if (weapon != null || armor != null) {
        // 装备台座墨晕
        drawOval(
            Color(0x33000000),
            topLeft = Offset(heroX - titleScale * 1.7f, heroY + titleScale * 1.35f),
            size = Size(titleScale * 3.4f, titleScale * 0.5f)
        )
    }
    drawInkButton(w * 0.02f, h * 0.02f, w * 0.15f, h * 0.07f, Color(0xFF0F766E), 10f)
    title(
        tm,
        if (GameI18n.language == GameLanguage.CHINESE) "日本語" else "中文",
        w * 0.095f, h * 0.037f, Color(0xFFF5EBD4), 12.sp
    )
    // Stack the header from measured text heights. Fixed percentage baselines overlap
    // on high-density phones (for example 640 dpi), because sp grows independently
    // from the canvas height.
    var headerY = h * 0.035f
    fun headerLine(text: String, color: Color, size: TextUnit, gap: Float = h * 0.006f) {
        val shown = GameI18n.tr(text)
        val layout = tm.measure(shown, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
        drawText(layout, topLeft = Offset(w * 0.68f - layout.size.width / 2f, headerY))
        headerY += layout.size.height + gap
    }
    headerLine("免疫战线", Color(0xFF2C1810), 30.sp, h * 0.002f)
    headerLine("人体防卫战 · v${BuildConfig.VERSION_NAME}", Color(0xFF5C4033), 11.sp)
    if (active != null) {
        headerLine("角色 ${active.name} · ${active.hero.displayName}", Color(0xFF92400E), 13.sp)
        headerLine(
            "${mutationGenerationLabel(progress.preferredInkRank)}/${progress.inkRankUnlocked}代 · 免疫记忆「${progress.preferredImmuneMemory().title}」（左右点选）",
            Color(0xFF7C2D12), 10.sp
        )
    } else {
        headerLine("请先创建角色", Color(0xFFF87171), 13.sp)
    }
    fun menuBtn(y: Float, bh: Float, text: String, col: Color) {
        drawRoundRect(Color(0xEE3D2914), Offset(w * 0.48f, y), Size(w * 0.42f, bh), CornerRadius(12f))
        drawRoundRect(col, Offset(w * 0.48f, y), Size(w * 0.42f, bh), CornerRadius(12f), style = Stroke(2.2f))
        title(tm, text, w * 0.69f, y + bh * 0.28f, Color(0xFFF5EBD4), 15.sp)
    }
    val hasSave = progress.hasActiveRun()
    val (known, total) = progress.collectionProgress()
    if (hasSave) {
        menuBtn(h * 0.30f, h * 0.09f, "继续冒险", Color(0xFFFBBF24))
        title(tm, progress.activeRunSummary(), w * 0.69f, h * 0.365f, Color(0xFFFDE68A), 8.sp)
        menuBtn(h * 0.40f, h * 0.09f, "开始新冒险", Color(0xFF4ADE80))
        menuBtn(h * 0.50f, h * 0.08f, "切换角色", Color(0xFF38BDF8))
        menuBtn(h * 0.59f, h * 0.08f, "装备图鉴 $known/$total", Color(0xFFFBBF24))
        menuBtn(h * 0.68f, h * 0.08f, "操作说明", Color(0xFF60A5FA))
        menuBtn(h * 0.77f, h * 0.08f, "记录 / 设置", Color(0xFFA78BFA))
    } else {
        menuBtn(h * 0.30f, h * 0.10f, "开始冒险", Color(0xFF4ADE80))
        menuBtn(h * 0.42f, h * 0.09f, "切换角色", Color(0xFF38BDF8))
        menuBtn(h * 0.53f, h * 0.09f, "装备图鉴 $known/$total", Color(0xFFFBBF24))
        menuBtn(h * 0.64f, h * 0.09f, "操作说明", Color(0xFF60A5FA))
        menuBtn(h * 0.75f, h * 0.09f, "记录 / 设置", Color(0xFFA78BFA))
    }
    // 底部双入口：今日毒株（固定日种子）+ 种子远征（好友互发种子码打同一张图）
    drawInkButton(w * 0.06f, h * 0.88f, w * 0.40f, h * 0.085f, Color(0xFF9F1239))
    title(tm, "今日毒株", w * 0.26f, h * 0.892f, Color(0xFFF5EBD4), 13.sp)
    title(tm, dailyInkTitle(), w * 0.26f, h * 0.935f, Color(0xFFFECACA), 8.sp)
    drawInkButton(w * 0.50f, h * 0.88f, w * 0.40f, h * 0.085f, Color(0xFF7C2D12))
    title(tm, "种子远征", w * 0.70f, h * 0.892f, Color(0xFFF5EBD4), 13.sp)
    title(tm, "输入种子码 · 打同一张图", w * 0.70f, h * 0.935f, Color(0xFFFDBA74), 8.sp)
}

private fun DrawScope.drawCharSelect(
    tm: TextMeasurer,
    w: Float,
    h: Float,
    progress: ProgressStore,
    tick: Int,
    pulse: Float
) {
    @Suppress("UNUSED_VARIABLE")
    val _t = tick
    drawInkPaperBackdrop(w, h, pulse, progress.preferredInkRank)
    drawInkWoodBar(w * 0.15f, h * 0.03f, w * 0.70f, h * 0.09f)
    title(tm, "选择角色", w * 0.5f, h * 0.045f, Color(0xFFF5EBD4), 22.sp)
    title(tm, "点角色进入 · 右侧删除  ·  最多6个", w * 0.5f, h * 0.105f, Color(0xFF5C4033), 11.sp)
    val chars = progress.listCharacters()
    if (chars.isEmpty()) {
        title(tm, "还没有角色，去创建一个吧", w * 0.5f, h * 0.4f, Color(0xFF5C4033), 16.sp)
    }
    chars.take(6).forEachIndexed { i, c ->
        val y0 = h * 0.16f + i * h * 0.10f
        val on = c.id == progress.activeCharacterId()
        drawParchmentPanel(w * 0.08f, y0, w * 0.64f, h * 0.09f, strokeCol = if (on) Color(0xFFB91C1C) else Color(0xAA5C4033))
        drawCuteHero(w * 0.14f, y0 + h * 0.045f, h * 0.028f, 1f, progress.classSkin(c.hero), bob = sin(pulse + i) * 2f)
        title(tm, c.name, w * 0.32f, y0 + h * 0.012f, Color(0xFF2C1810), 14.sp)
        title(tm, c.summaryLine(), w * 0.32f, y0 + h * 0.048f, Color(0xFF5C4033), 10.sp)
        drawInkButton(w * 0.74f, y0 + h * 0.01f, w * 0.16f, h * 0.07f, Color(0xFFB91C1C), 10f)
        title(tm, "删除", w * 0.82f, y0 + h * 0.028f, Color(0xFFFECACA), 12.sp)
    }
    drawInkButton(w * 0.06f, h * 0.80f, w * 0.27f, h * 0.09f, Color(0xFF4D7C0F))
    title(tm, "创建新角色", w * 0.195f, h * 0.825f, Color(0xFFF5EBD4), 14.sp)
    drawInkButton(w * 0.365f, h * 0.80f, w * 0.27f, h * 0.09f, Color(0xFFB45309))
    title(tm, "职业试炼", w * 0.50f, h * 0.825f, Color(0xFFF5EBD4), 14.sp)
    drawInkButton(w * 0.67f, h * 0.80f, w * 0.27f, h * 0.09f, Color(0xFF78716C))
    title(tm, "进入标题", w * 0.805f, h * 0.825f, Color(0xFFF5EBD4), 14.sp)
}

/** 职业试炼：三职业各自 4 条生涯累计目标，全达成解锁备用皮肤并可在此更换 */
private fun DrawScope.drawTrials(
    meta: RunMeta,
    progress: ProgressStore,
    tm: TextMeasurer,
    w: Float,
    h: Float
) {
    drawInkPaperBackdrop(w, h, meta.pulse, progress.preferredInkRank)
    drawInkWoodBar(w * 0.2f, h * 0.03f, w * 0.6f, h * 0.09f)
    title(tm, "职业试炼", w * 0.5f, h * 0.045f, Color(0xFFF5EBD4), 22.sp)
    title(tm, "完成全部试炼解锁该职业皮肤 · 按职业累计", w * 0.5f, h * 0.105f, Color(0xFF5C4033), 11.sp)
    HeroClass.entries.forEachIndexed { ci, hero ->
        val x0 = w * 0.05f + ci * w * 0.32f
        val colW = w * 0.28f
        val cx = x0 + colW * 0.5f
        val stats = progress.trialStats(hero)
        val unlocked = progress.isAltSkinUnlocked(hero)
        val cur = progress.classSkin(hero)
        drawParchmentPanel(x0, h * 0.15f, colW, h * 0.68f, strokeCol = if (unlocked) Color(0xFF4ADE80) else Color(0xAA5C4033))
        title(tm, GameI18n.tr(hero.displayName), cx, h * 0.175f, hero.color, 14.sp)
        drawCuteHero(cx, h * 0.245f, h * 0.030f, 1f, cur, bob = sin(meta.pulse * 3f + ci * 2f) * 2.5f)
        HeroTrials.trialsFor(hero).forEachIndexed { ti, t ->
            val done = HeroTrials.isDone(t, stats)
            title(
                tm,
                (if (done) "✓ " else "") + GameI18n.tr(t.title) + " " + HeroTrials.progressNum(t, stats),
                cx, h * (0.32f + ti * 0.075f),
                if (done) Color(0xFF4ADE80) else Color(0xFF2C1810),
                11.sp
            )
            if (!done) {
                title(tm, t.desc, cx, h * (0.32f + ti * 0.075f) + h * 0.026f, Color(0xFF78716C), 8.sp)
            }
        }
        // 皮肤切换：默认皮始终可用；备用皮解锁后可选
        val def = SkinCatalog.defaultFor(hero)
        val alt = HeroTrials.rewardSkinFor(hero)
        val selDef = cur.id == def.id
        drawInkButton(x0 + w * 0.01f, h * 0.66f, colW * 0.46f, h * 0.07f, if (selDef) Color(0xFF4D7C0F) else Color(0xFF78716C), 8f)
        title(tm, def.displayName, x0 + w * 0.01f + colW * 0.23f, h * 0.68f, Color(0xFFF5EBD4), 10.sp)
        val selAlt = cur.id == alt.id
        drawInkButton(x0 + colW * 0.51f, h * 0.66f, colW * 0.46f, h * 0.07f, when {
            selAlt -> Color(0xFFB45309)
            unlocked -> Color(0xFF78716C)
            else -> Color(0xFF3F3F46)
        }, 8f)
        title(
            tm,
            if (unlocked) alt.displayName else "？？？",
            x0 + colW * 0.51f + colW * 0.23f, h * 0.68f,
            if (unlocked) Color(0xFFF5EBD4) else Color(0xFFA1A1AA),
            10.sp
        )
    }
    drawInkButton(w * 0.35f, h * 0.875f, w * 0.30f, h * 0.085f, Color(0xFF78716C))
    title(tm, "返回角色", w * 0.5f, h * 0.895f, Color(0xFFF5EBD4), 14.sp)
}

private fun DrawScope.drawCreateChar(
    tm: TextMeasurer,
    w: Float,
    h: Float,
    name: String,
    heroIdx: Int,
    pulse: Float
) {
    drawInkPaperBackdrop(w, h, pulse, 0)
    drawInkWoodBar(w * 0.2f, h * 0.04f, w * 0.6f, h * 0.08f)
    title(tm, "创建角色", w * 0.5f, h * 0.055f, Color(0xFFF5EBD4), 22.sp)
    title(tm, "名字 + 职业（之后开局固定该职业）", w * 0.5f, h * 0.135f, Color(0xFF5C4033), 12.sp)
    drawParchmentPanel(w * 0.18f, h * 0.28f, w * 0.42f, h * 0.10f)
    title(tm, name, w * 0.39f, h * 0.305f, Color(0xFF2C1810), 16.sp)
    drawInkButton(w * 0.62f, h * 0.28f, w * 0.24f, h * 0.10f, Color(0xFF0F766E))
    title(tm, "随机名", w * 0.74f, h * 0.305f, Color(0xFFF5EBD4), 14.sp)
    HeroClass.entries.forEachIndexed { i, hc ->
        val x0 = w * 0.12f + i * w * 0.26f
        val on = i == heroIdx
        drawParchmentPanel(
            x0, h * 0.42f, w * 0.24f, h * 0.26f,
            strokeCol = if (on) Color(0xFFB91C1C) else hc.color.copy(alpha = 0.7f)
        )
        drawCuteHero(x0 + w * 0.12f, h * 0.52f + sin(pulse * 2f + i) * 2f, h * 0.04f, 1f, SkinCatalog.defaultFor(hc))
        title(tm, hc.displayName, x0 + w * 0.12f, h * 0.60f, Color(0xFF2C1810), 14.sp)
    }
    drawInkButton(w * 0.30f, h * 0.78f, w * 0.40f, h * 0.10f, Color(0xFFB45309))
    title(tm, "确认创建", w * 0.5f, h * 0.805f, Color(0xFFF5EBD4), 16.sp)
    title(tm, "有角色时可点下方返回选择", w * 0.5f, h * 0.93f, Color(0xFF78716C), 10.sp)
}

private fun DrawScope.drawHowTo(tm: TextMeasurer, w: Float, h: Float) {
    drawInkPaperBackdrop(w, h, 0f, 0)
    drawInkWoodBar(w * 0.2f, h * 0.04f, w * 0.6f, h * 0.08f)
    title(tm, "怎么玩", w * 0.5f, h * 0.055f, Color(0xFFF5EBD4), 24.sp)
    drawParchmentPanel(w * 0.06f, h * 0.14f, w * 0.88f, h * 0.62f)
    val lines = listOf(
        "1. 创建免疫战士 → 选择毒株代次与免疫记忆",
        "2. 五器官路线：皮肤→肺部→胃肠→肝脏→心脏",
        "3. 战斗：左摇杆移动 · 右侧按钮攻击和技能",
        "4. 先击破带疗/鼓/卫徽记的战术怪 · 留意波次词缀",
        "5. 战场有灵力符阵挡弹 · 增益祭坛限时抢夺",
        "6. 宝物房三选一 · 挑战房高风险高回报",
        "7. 清除五器官后病毒进入下一代；装备与等级重置",
        "8. 免疫记忆永久保留 · 武器元素只影响弹色",
        "9. 免疫核心可重复升阶 · 改变冲锋/冰环/毒雾等技能机制",
        "10. 过关自动存档；阵亡后从本章重新出击",
        "目标：净化五器官 · 重构装备与技能 · 挑战更高变异代"
    )
    lines.forEachIndexed { i, s ->
        title(tm, s, w * 0.5f, h * 0.16f + i * h * 0.055f, Color(0xFF2C1810), 12.sp)
    }
    drawInkButton(w * 0.15f, h * 0.80f, w * 0.32f, h * 0.10f, Color(0xFFB45309))
    title(tm, "装备图鉴", w * 0.31f, h * 0.825f, Color(0xFFF5EBD4), 16.sp)
    drawInkButton(w * 0.52f, h * 0.80f, w * 0.32f, h * 0.10f, Color(0xFF4D7C0F))
    title(tm, "知道了", w * 0.68f, h * 0.825f, Color(0xFFF5EBD4), 16.sp)
}

/** 装备/技能图鉴：武甲戒鞋套技 · 已获得高亮 · 未获得灰显 */
private fun DrawScope.drawCodex(meta: RunMeta, progress: ProgressStore, tm: TextMeasurer, w: Float, h: Float) {
    drawInkPaperBackdrop(w, h, meta.pulse, progress.preferredInkRank)
    val (known, total) = progress.collectionProgress()
    drawInkWoodBar(w * 0.2f, h * 0.008f, w * 0.6f, h * 0.045f, 10f)
    var codexHeaderY = h * 0.008f
    fun codexHeaderLine(text: String, color: Color, size: TextUnit, gap: Float = h * 0.002f) {
        val shown = GameI18n.tr(text)
        val layout = tm.measure(shown, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
        drawText(layout, topLeft = Offset(w * 0.5f - layout.size.width / 2f, codexHeaderY))
        codexHeaderY += layout.size.height + gap
    }
    codexHeaderLine("图鉴", Color(0xFFF5EBD4), 16.sp)
    codexHeaderLine("亮色=已获得 · 灰暗=未获得  ·  收集 $known/$total", Color(0xFF5C4033), 10.sp, 0f)

    val tabs = listOf("武器", "防具", "戒指", "鞋子", "套装", "挑战")
    val tabW = w * 0.125f
    tabs.forEachIndexed { i, t ->
        val x0 = w * 0.02f + i * (tabW + w * 0.01f)
        val on = meta.codexTab == i
        drawRoundRect(if (on) Color(0xFFD97706) else Color(0xFF1E293B), Offset(x0, h * 0.105f), Size(tabW, h * 0.06f), CornerRadius(8f))
        title(tm, t, x0 + tabW * 0.5f, h * 0.118f, Color.White, 11.sp)
    }
    // 职业筛选只出现在有职业专属内容的 tab（武器/防具/套装）；戒指鞋子全通用；挑战为账号级生涯
    if (meta.codexTab in listOf(0, 1, 4)) {
        HeroClass.entries.forEachIndexed { i, hc ->
            val x0 = w * 0.70f + i * w * 0.09f
            val on = meta.codexHeroIndex == i
            drawRoundRect(if (on) hc.color.copy(alpha = 0.55f) else Color(0xFF1E293B), Offset(x0, h * 0.105f), Size(w * 0.085f, h * 0.06f), CornerRadius(8f))
            title(tm, GameI18n.tr(hc.displayName), x0 + w * 0.042f, h * 0.118f, if (on) Color.White else Color(0xFF94A3B8), 11.sp)
        }
    } else if (meta.codexTab == 5) {
        val doneN = progress.completedChallenges().size
        title(tm, "已达成 $doneN/${CodexChallenge.entries.size}", w * 0.745f, h * 0.118f, Color(0xFFFBBF24), 10.sp)
    } else {
        title(tm, GameI18n.tr("全部职业通用"), w * 0.745f, h * 0.118f, Color(0xFF94A3B8), 10.sp)
    }

    val hero = HeroClass.entries[meta.codexHeroIndex.coerceIn(0, 2)]
    val pulse = meta.pulse
    drawRoundRect(Color(0xCC0F172A), Offset(w * 0.02f, h * 0.18f), Size(w * 0.40f, h * 0.70f), CornerRadius(12f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xEE1E293B), Color(0xEE0F172A))),
        Offset(w * 0.44f, h * 0.18f), Size(w * 0.54f, h * 0.70f), CornerRadius(14f)
    )

    fun listBg(owned: Boolean, on: Boolean) = when {
        on -> Color(0xFF1E3A5F)
        owned -> Color(0xEE1E293B)
        else -> Color(0x99111827)
    }

    val previewCx = w * 0.71f
    val previewCy = h * 0.40f
    val previewS = min(w, h) * 0.15f

    when (meta.codexTab) {
        0 -> {
            val list = WeaponCatalog.all.filter { it.hero == hero || it.hero == null }
                .sortedWith(compareBy<GearWeapon>({ !progress.isWeaponKnown(it.id) }, { -it.tierLevel() }, { -it.cost }))
            val sel = meta.codexSelectedId.ifEmpty { list.firstOrNull()?.id.orEmpty() }
            list.take(8).forEachIndexed { i, wp ->
                val y0 = h * 0.20f + i * h * 0.085f
                val owned = progress.isWeaponKnown(wp.id)
                val on = wp.id == sel
                drawRoundRect(listBg(owned, on), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f))
                drawRoundRect(if (on) Color(0xFFFBBF24) else if (owned) wp.element.color else Color(0xFF475569), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f), style = Stroke(if (on) 3f else 1.2f))
                // 职业专属：左侧职业色竖条（通用装备无）
                wp.hero?.let { hc ->
                    drawRoundRect(hc.color, Offset(w * 0.033f, y0 + h * 0.012f), Size(w * 0.006f, h * 0.054f), CornerRadius(2f))
                }
                if (owned) drawWeaponArt(w * 0.07f, y0 + h * 0.038f, h * 0.028f, wp, pulse)
                else drawCircle(Color(0xFF334155), h * 0.022f, Offset(w * 0.07f, y0 + h * 0.038f))
                title(tm, if (owned) "${wp.tierLabel()} ${wp.name}" else "${wp.tierLabel()} ？？？", w * 0.22f, y0 + h * 0.01f, if (owned) Color.White else Color(0xFF64748B), 11.sp)
                title(tm, if (owned) "[${wp.element.short}] ${wp.proc.title}" else "未获得", w * 0.22f, y0 + h * 0.04f, if (owned) wp.element.color else Color(0xFF475569), 9.sp)
            }
            val wp = WeaponCatalog.byId(sel) ?: list.firstOrNull()
            if (wp != null) {
                val owned = progress.isWeaponKnown(wp.id)
                if (owned) drawWeaponShowcaseFx(previewCx, previewCy, previewS, wp, pulse)
                else {
                    drawCircle(Color(0xFF1E293B), previewS, Offset(previewCx, previewCy))
                    title(tm, "？", previewCx, previewCy - 10f, Color(0xFF64748B), 40.sp)
                }
                title(tm, if (owned) wp.name else "未获得 · ${wp.tierLabel()}", previewCx, h * 0.62f, if (owned) Color.White else Color(0xFF94A3B8), 15.sp)
                if (owned) {
                    title(tm, "${wp.rarityName()} 武器 · ${wp.classLabel()} · [${wp.element.short}]", previewCx, h * 0.66f, wp.element.color, 11.sp)
                    title(tm, wp.statsCompact(), previewCx, h * 0.70f, Color(0xFFE2E8F0), 11.sp)
                    title(tm, "特效 ${wp.proc.title}：${wp.proc.tip}", previewCx, h * 0.745f, Color(0xFFFBBF24), 12.sp)
                    title(tm, wp.flavor, previewCx, h * 0.79f, Color(0xFF94A3B8), 11.sp)
                } else {
                    title(tm, "掉落/商店获取后解锁全部信息与特效图", previewCx, h * 0.68f, Color(0xFF64748B), 12.sp)
                    title(tm, "阶段 ${wp.tierLabel()} · 武器槽", previewCx, h * 0.74f, Color(0xFF475569), 11.sp)
                }
            }
        }
        1 -> {
            val list = ArmorCatalog.all.filter { it.hero == hero || it.hero == null }
                .sortedWith(compareBy<GearArmor>({ !progress.isArmorKnown(it.id) }, { -it.tier }, { -it.cost }))
            val sel = meta.codexSelectedId.ifEmpty { list.firstOrNull()?.id.orEmpty() }
            list.take(8).forEachIndexed { i, ar ->
                val y0 = h * 0.20f + i * h * 0.085f
                val owned = progress.isArmorKnown(ar.id)
                val on = ar.id == sel
                drawRoundRect(listBg(owned, on), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f))
                drawRoundRect(if (on) Color(0xFFFBBF24) else if (owned) Color(0xFF86EFAC) else Color(0xFF475569), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f), style = Stroke(if (on) 3f else 1.2f))
                ar.hero?.let { hc ->
                    drawRoundRect(hc.color, Offset(w * 0.033f, y0 + h * 0.012f), Size(w * 0.006f, h * 0.054f), CornerRadius(2f))
                }
                if (owned) drawArmorArt(w * 0.07f, y0 + h * 0.038f, h * 0.028f, ar, pulse)
                title(tm, if (owned) "${ar.tierLabel()} ${ar.name}" else "${ar.tierLabel()} ？？？", w * 0.22f, y0 + h * 0.015f, if (owned) Color.White else Color(0xFF64748B), 11.sp)
                title(tm, if (owned) ar.statsCompact().take(14) else "未获得·防具", w * 0.22f, y0 + h * 0.045f, if (owned) Color(0xFF86EFAC) else Color(0xFF475569), 9.sp)
            }
            val ar = ArmorCatalog.byId(sel) ?: list.firstOrNull()
            if (ar != null) {
                val owned = progress.isArmorKnown(ar.id)
                if (owned) drawArmorShowcaseFx(previewCx, previewCy, previewS, ar, pulse)
                else title(tm, "？", previewCx, previewCy - 10f, Color(0xFF64748B), 40.sp)
                title(tm, if (owned) ar.name else "未获得 · ${ar.tierLabel()} 防具", previewCx, h * 0.62f, Color.White, 15.sp)
                if (owned) {
                    title(tm, ar.statsCompact(), previewCx, h * 0.68f, Color(0xFF86EFAC), 12.sp)
                    title(tm, ar.flavor, previewCx, h * 0.74f, Color(0xFF94A3B8), 11.sp)
                }
            }
        }
        2 -> {
            val list = RingCatalog.all.filter { it.canEquip(hero) }
                .sortedWith(compareBy<GearAccessory>({ !progress.isRingKnown(it.id) }, { -it.tier }, { -it.cost }))
            val sel = meta.codexSelectedId.ifEmpty { list.firstOrNull()?.id.orEmpty() }
            list.take(8).forEachIndexed { i, r ->
                val y0 = h * 0.20f + i * h * 0.085f
                val owned = progress.isRingKnown(r.id)
                val on = r.id == sel
                drawRoundRect(listBg(owned, on), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f))
                drawRoundRect(if (on) Color(0xFFFBBF24) else if (owned) Color(0xFFF472B6) else Color(0xFF475569), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f), style = Stroke(if (on) 3f else 1.2f))
                drawAccessoryArt(w * 0.07f, y0 + h * 0.038f, h * 0.028f, r, pulse, owned)
                title(tm, if (owned) "${r.tierLabel()} ${r.name}" else "${r.tierLabel()} ？？？", w * 0.22f, y0 + h * 0.015f, if (owned) Color.White else Color(0xFF64748B), 11.sp)
                title(tm, if (owned) "戒指 · ${r.statsCompact().take(12)}" else "未获得·戒指", w * 0.22f, y0 + h * 0.045f, if (owned) Color(0xFFF9A8D4) else Color(0xFF475569), 9.sp)
            }
            val r = RingCatalog.byId(sel) ?: list.firstOrNull()
            if (r != null) {
                val owned = progress.isRingKnown(r.id)
                drawAccessoryArt(previewCx, previewCy, previewS, r, pulse, owned)
                title(tm, if (owned) r.name else "未获得戒指", previewCx, h * 0.62f, Color.White, 15.sp)
                if (owned) {
                    title(tm, "戒指槽 · ${r.rarityName()} · ${r.classLabel()}", previewCx, h * 0.67f, Color(0xFFF9A8D4), 12.sp)
                    title(tm, r.statsCompact(), previewCx, h * 0.72f, Color(0xFFE2E8F0), 12.sp)
                    title(tm, r.flavor, previewCx, h * 0.77f, Color(0xFF94A3B8), 11.sp)
                } else title(tm, "商店/掉落获取 · 非武器", previewCx, h * 0.68f, Color(0xFF64748B), 12.sp)
            }
        }
        3 -> {
            val list = BootsCatalog.all.filter { it.canEquip(hero) }
                .sortedWith(compareBy<GearAccessory>({ !progress.isBootsKnown(it.id) }, { -it.tier }, { -it.cost }))
            val sel = meta.codexSelectedId.ifEmpty { list.firstOrNull()?.id.orEmpty() }
            list.take(8).forEachIndexed { i, b ->
                val y0 = h * 0.20f + i * h * 0.085f
                val owned = progress.isBootsKnown(b.id)
                val on = b.id == sel
                drawRoundRect(listBg(owned, on), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f))
                drawRoundRect(if (on) Color(0xFFFBBF24) else if (owned) Color(0xFF38BDF8) else Color(0xFF475569), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f), style = Stroke(if (on) 3f else 1.2f))
                drawAccessoryArt(w * 0.07f, y0 + h * 0.038f, h * 0.028f, b, pulse, owned)
                title(tm, if (owned) "${b.tierLabel()} ${b.name}" else "${b.tierLabel()} ？？？", w * 0.22f, y0 + h * 0.015f, if (owned) Color.White else Color(0xFF64748B), 11.sp)
                title(tm, if (owned) "鞋子 · ${b.statsCompact().take(12)}" else "未获得·鞋子", w * 0.22f, y0 + h * 0.045f, if (owned) Color(0xFF7DD3FC) else Color(0xFF475569), 9.sp)
            }
            val b = BootsCatalog.byId(sel) ?: list.firstOrNull()
            if (b != null) {
                val owned = progress.isBootsKnown(b.id)
                drawAccessoryArt(previewCx, previewCy, previewS, b, pulse, owned)
                title(tm, if (owned) b.name else "未获得鞋子", previewCx, h * 0.62f, Color.White, 15.sp)
                if (owned) {
                    title(tm, "鞋子槽 · ${b.rarityName()}", previewCx, h * 0.67f, Color(0xFF7DD3FC), 12.sp)
                    title(tm, b.statsCompact(), previewCx, h * 0.72f, Color(0xFFE2E8F0), 12.sp)
                    title(tm, b.flavor, previewCx, h * 0.77f, Color(0xFF94A3B8), 11.sp)
                }
            }
        }
        4 -> {
            val sets = SetCatalog.forHero(hero)
            val sel = meta.codexSelectedId.ifEmpty { sets.firstOrNull()?.id.orEmpty() }
            sets.forEachIndexed { i, set ->
                val y0 = h * 0.20f + i * h * 0.12f
                val on = set.id == sel
                val anyOwned = set.weaponIds.any { progress.isWeaponKnown(it) } || set.armorIds.any { progress.isArmorKnown(it) }
                drawRoundRect(listBg(anyOwned, on), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.11f), CornerRadius(10f))
                drawRoundRect(if (on) Color(0xFFFBBF24) else hero.color, Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.11f), CornerRadius(10f), style = Stroke(if (on) 3f else 1.2f))
                title(tm, "T${set.tier} ${set.name}", w * 0.22f, y0 + h * 0.02f, Color(0xFFFDE68A), 12.sp)
                title(tm, set.bonusTitle, w * 0.22f, y0 + h * 0.055f, Color(0xFF86EFAC), 11.sp)
            }
            val set = SetCatalog.byId(sel) ?: sets.firstOrNull()
            if (set != null) {
                // 套装阵容展示：立绘居中，套装武器/防具位图分列左右，光环承托
                drawSetAura(previewCx, previewCy + previewS * 0.15f, previewS * 0.55f, set, pulse)
                val portraitH = previewS * 1.1f
                val drewPortrait = drawHeroPortrait(hero, previewCx, previewCy + previewS * 0.55f, portraitH)
                if (!drewPortrait) {
                    drawCuteHero(previewCx, previewCy, previewS * 0.4f, 1f, SkinCatalog.defaultFor(hero), bob = sin(pulse * 3f) * 2f)
                }
                set.weaponIds.firstOrNull()?.let { wid ->
                    WeaponCatalog.byId(wid)?.let { gw ->
                        drawWeaponArt(previewCx - previewS * 0.85f, previewCy + previewS * 0.05f, previewS * 0.5f, gw, pulse)
                    }
                }
                set.armorIds.firstOrNull()?.let { aid ->
                    ArmorCatalog.byId(aid)?.let { ga ->
                        drawArmorArt(previewCx + previewS * 0.85f, previewCy + previewS * 0.1f, previewS * 0.48f, ga, pulse)
                    }
                }
                // 收集进度
                val pieces = set.weaponIds + set.armorIds
                val knownPieces = pieces.count {
                    it in set.weaponIds && progress.isWeaponKnown(it) || it in set.armorIds && progress.isArmorKnown(it)
                }
                title(tm, "${set.name} · 收集 $knownPieces/${pieces.size}", previewCx, h * 0.62f, Color(0xFFFDE68A), 15.sp)
                title(tm, "被动 ${set.bonusTitle}：${set.bonusTip}", previewCx, h * 0.68f, Color(0xFF86EFAC), 12.sp)
                title(tm, set.piecesLine().take(40), previewCx, h * 0.74f, Color(0xFF94A3B8), 10.sp)
                title(tm, "特效 ${set.proc.title}", previewCx, h * 0.79f, Color(0xFFFBBF24), 12.sp)
            }
        }
        5 -> {
            val done = progress.completedChallenges()
            val list = CodexChallenge.entries
            val selId = meta.codexSelectedId.ifEmpty { list.firstOrNull()?.id.orEmpty() }
            list.forEachIndexed { i, c ->
                val y0 = h * 0.20f + i * h * 0.085f
                val ok = c.id in done
                val on = c.id == selId
                drawRoundRect(listBg(ok, on), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f))
                drawRoundRect(
                    if (on) Color(0xFFFBBF24) else if (ok) Color(0xFF4ADE80) else Color(0xFF475569),
                    Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f),
                    style = Stroke(if (on) 3f else 1.2f)
                )
                title(tm, if (ok) "✓ ${c.title}" else c.title, w * 0.22f, y0 + h * 0.008f, if (ok) Color(0xFF86EFAC) else Color.White, 11.sp)
                title(tm, if (ok) "已达成" else c.desc.take(13), w * 0.22f, y0 + h * 0.042f, if (ok) Color(0xFF86EFAC) else Color(0xFF94A3B8), 9.sp)
            }
            val c = CodexChallenge.byId(selId) ?: list.first()
            val ok = c.id in done
            title(tm, if (ok) "✓ ${c.title}" else c.title, previewCx, h * 0.28f, if (ok) Color(0xFF86EFAC) else Color.White, 18.sp)
            title(tm, c.desc, previewCx, h * 0.36f, Color(0xFFE2E8F0), 12.sp)
            title(tm, "奖励 +${c.rewardGold}金（当局立即生效）", previewCx, h * 0.43f, Color(0xFFFBBF24), 12.sp)
            title(tm, "挑战奖励进入当局金币，死亡即失效——趁热用掉", previewCx, h * 0.62f, Color(0xFF94A3B8), 10.sp)
            title(tm, "生涯挑战 · 达成后自动记录", previewCx, h * 0.68f, Color(0xFF5C4033), 10.sp)
        }
    }
    drawRoundRect(Color(0xFF334155), Offset(w * 0.3f, h * 0.91f), Size(w * 0.4f, h * 0.07f), CornerRadius(12f))
    title(tm, "返回标题", w * 0.5f, h * 0.925f, Color.White, 14.sp)
}

private fun DrawScope.drawSettings(
    tm: TextMeasurer,
    w: Float,
    h: Float,
    progress: ProgressStore,
    privacyMessage: String
) {
    drawInkPaperBackdrop(w, h, 0f, progress.preferredInkRank)
    drawInkWoodBar(w * 0.18f, h * 0.04f, w * 0.64f, h * 0.08f)
    title(tm, "记录 / 设置", w * 0.5f, h * 0.055f, Color(0xFFF5EBD4), 22.sp)
    drawInkButton(w * 0.82f, h * 0.03f, w * 0.16f, h * 0.08f, Color(0xFF0F766E), 10f)
    title(
        tm,
        if (GameI18n.language == GameLanguage.CHINESE) "日本語" else "中文",
        w * 0.90f, h * 0.052f, Color(0xFFF5EBD4), 12.sp
    )
    drawParchmentPanel(w * 0.1f, h * 0.14f, w * 0.8f, h * 0.22f)
    title(tm, "出征 ${progress.runsStarted}  通关 ${progress.runsWon}  最高章 ${progress.bestStage}", w * 0.5f, h * 0.155f, Color(0xFF2C1810), 13.sp)
    title(tm, "累计金 ${progress.lifetimeGold}  击杀 ${progress.lifetimeKills}", w * 0.5f, h * 0.205f, Color(0xFFB45309), 13.sp)
    val ch = progress.activeCharacter()
    title(
        tm,
        "档案 ${progress.localUsername()}" + if (progress.isGuestAccount()) "（游客）" else "",
        w * 0.5f, h * 0.255f, Color(0xFF5C4033), 12.sp
    )
    if (progress.isGuestAccount()) {
        title(tm, "口令 ${progress.localPassword()}（本地，可截图）", w * 0.5f, h * 0.30f, Color(0xFF3F6212), 11.sp)
    }
    title(
        tm,
        "角色 ${ch?.name ?: "无"} · ${ch?.hero?.displayName ?: "-"}",
        w * 0.5f, h * 0.34f, Color(0xFFB91C1C), 12.sp
    )
    fun toggle(y: Float, label: String, on: Boolean) {
        drawParchmentPanel(w * 0.15f, y, w * 0.7f, h * 0.08f, strokeCol = if (on) Color(0xFF4D7C0F) else Color(0xAA5C4033))
        title(tm, "$label  ${if (on) "开" else "关"}", w * 0.5f, y + h * 0.02f, if (on) Color(0xFF3F6212) else Color(0xFF78716C), 15.sp)
    }
    toggle(h * 0.40f, "音效", progress.soundOn)
    toggle(h * 0.50f, "战斗震动", progress.hapticsOn)
    drawInkButton(w * 0.15f, h * 0.59f, w * 0.33f, h * 0.08f, Color(0xFF475569))
    title(tm, "隐私政策", w * 0.315f, h * 0.61f, Color(0xFFF5EBD4), 13.sp)
    drawInkButton(w * 0.52f, h * 0.59f, w * 0.33f, h * 0.08f, Color(0xFF475569))
    title(tm, "广告隐私选项", w * 0.685f, h * 0.61f, Color(0xFFF5EBD4), 13.sp)
    val privacyLine = privacyMessage.ifBlank {
        "广告仅在同意状态允许时加载 · ${if (AdConfig.useTestAds) "测试广告" else "正式广告"}"
    }
    title(tm, privacyLine.take(36), w * 0.5f, h * 0.68f, Color(0xFF78716C), 9.sp)
    drawInkButton(w * 0.2f, h * 0.72f, w * 0.6f, h * 0.08f, Color(0xFFB91C1C))
    title(tm, "退出登录", w * 0.5f, h * 0.74f, Color(0xFFFECACA), 15.sp)
    title(tm, "本地崩溃记录 · Play Vitals · v${BuildConfig.VERSION_NAME}", w * 0.5f, h * 0.82f, Color(0xFF78716C), 9.sp)
    drawInkButton(w * 0.2f, h * 0.86f, w * 0.6f, h * 0.09f, Color(0xFF78716C))
    title(tm, "返回标题", w * 0.5f, h * 0.88f, Color(0xFFF5EBD4), 16.sp)
}

private fun DrawScope.drawResult(meta: RunMeta, tm: TextMeasurer, w: Float, h: Float, progress: ProgressStore) {
    drawInkPaperBackdrop(w, h, meta.pulse, meta.stage().chapterIndex)
    drawInkWoodBar(w * 0.12f, h * 0.05f, w * 0.76f, h * 0.1f)
    val cycleCleared = meta.resultTitle.contains("成功") || meta.resultTitle.contains("清除")
    val titleCol = if (cycleCleared) Color(0xFFBBF7D0) else Color(0xFFFECACA)
    title(tm, meta.resultTitle, w * 0.5f, h * 0.07f, titleCol, 24.sp)
    drawParchmentPanel(w * 0.1f, h * 0.18f, w * 0.8f, h * 0.36f)
    // 整串翻译后按行渲染；颜色探针在原文上做（译文行结构一致）
    val translatedResult = GameI18n.tr(meta.resultBody)
    meta.resultBody.lines().take(6).forEachIndexed { i, raw ->
        val c = if (raw.contains("已写入本地")) Color(0xFF3F6212) else Color(0xFF2C1810)
        val shown = translatedResult.lines().getOrElse(i) { raw }
        title(tm, shown, w * 0.5f, h * 0.20f + i * h * 0.045f, c, 13.sp)
    }
    title(
        tm,
        "生涯清除 ${progress.lifetimeKills} · 周期完成 ${progress.runsWon} · 变异代 ${progress.preferredInkRank}/${progress.inkRankUnlocked}",
        w * 0.5f, h * 0.555f, Color(0xFF5C4033), 11.sp
    )
    if (cycleCleared) {
        title(
            tm, "下一代已开启：新感染特征 · 新免疫记忆 · 重构装备",
            w * 0.5f, h * 0.585f, Color(0xFF3F6212), 10.sp
        )
    }
    if (!meta.adDoubleClaimed && meta.goldEarnedThisRun > 0) {
        drawInkButton(w * 0.18f, h * 0.60f, w * 0.64f, h * 0.11f, Color(0xFFB45309))
        title(tm, "看广告 · 双倍本局金币(+${meta.goldEarnedThisRun})", w * 0.5f, h * 0.63f, Color(0xFFF5EBD4), 15.sp)
    } else if (meta.adDoubleClaimed) {
        title(tm, "已领取广告双倍金", w * 0.5f, h * 0.64f, Color(0xFF3F6212), 13.sp)
    }
    drawInkButton(w * 0.18f, h * 0.78f, w * 0.64f, h * 0.12f, Color(0xFF4D7C0F))
    title(tm, "返回标题", w * 0.5f, h * 0.815f, Color(0xFFF5EBD4), 17.sp)
    if (meta.toastT > 0f) title(tm, meta.toast, w * 0.5f, h * 0.74f, Color(0xFFB45309), 12.sp)
}

private fun DrawScope.drawStory(meta: RunMeta, tm: TextMeasurer, w: Float, h: Float) {
    drawInkPaperBackdrop(w, h, meta.pulse, meta.stage().chapterIndex)
    val beat = meta.currentBeat()
    val total = meta.storyQueue.size.coerceAtLeast(1)
    val idx = meta.storyIndex + 1
    title(tm, "旅途 · $idx / $total", w * 0.5f, h * 0.08f, Color(0xFF5C4033), 12.sp)
    val moodCol = when (beat?.mood) {
        StoryMood.TENSE -> Color(0xFFB91C1C)
        StoryMood.WARM -> Color(0xFF4D7C0F)
        StoryMood.FUNNY -> Color(0xFFB45309)
        StoryMood.DARK -> Color(0xFF57534E)
        else -> Color(0xFF0F766E)
    }
    drawParchmentPanel(w * 0.08f, h * 0.18f, w * 0.84f, h * 0.58f, strokeCol = moodCol.copy(alpha = 0.75f))
    if (beat != null) {
        title(tm, beat.speaker, w * 0.5f, h * 0.26f, moodCol, 20.sp)
        val line = beat.line
        if (line.length > 28) {
            val mid = line.length / 2
            val split = line.indexOf('，', mid - 6).takeIf { it > 0 } ?: line.indexOf('。', mid - 6).takeIf { it > 0 } ?: mid
            title(tm, line.substring(0, split + 1).trim(), w * 0.5f, h * 0.40f, Color(0xFF2C1810), 16.sp)
            title(tm, line.substring(split + 1).trim(), w * 0.5f, h * 0.50f, Color(0xFF3F3F46), 16.sp)
        } else {
            title(tm, line, w * 0.5f, h * 0.44f, Color(0xFF2C1810), 17.sp)
        }
    }
    title(tm, "轻触继续", w * 0.38f, h * 0.85f, Color(0xFF78716C), 13.sp)
    drawInkButton(w * 0.62f, h * 0.82f, w * 0.28f, h * 0.12f, Color(0xFF78716C))
    title(tm, "跳过剧情", w * 0.76f, h * 0.855f, Color(0xFFF5EBD4), 14.sp)
    if (meta.journal.isNotEmpty()) {
        title(tm, "摘录：${meta.journal.last()}", w * 0.38f, h * 0.93f, Color(0xFF78716C), 10.sp)
    }
}

private fun DrawScope.drawClassSelect(tm: TextMeasurer, w: Float, h: Float, pulse: Float) {
    drawInkPaperBackdrop(w, h, pulse, 0)
    drawInkWoodBar(w * 0.2f, h * 0.04f, w * 0.6f, h * 0.08f)
    title(tm, "选择职业", w * 0.5f, h * 0.055f, Color(0xFFF5EBD4), 22.sp)
    title(tm, "横屏操作：左拖移动 · 右斩/技能  ·  三职业三套路", w * 0.5f, h * 0.14f, Color(0xFF5C4033), 12.sp)
    val gap = w * 0.02f
    val cardW = (w * 0.9f - gap * 2f) / 3f
    val cardH = h * 0.62f
    val y0 = h * 0.22f
    HeroClass.entries.forEachIndexed { i, hc ->
        val x = w * 0.05f + i * (cardW + gap)
        drawParchmentPanel(x, y0, cardW, cardH, strokeCol = hc.color.copy(alpha = 0.85f), radius = 16f)
        val skin = SkinCatalog.defaultFor(hc)
        drawCuteHero(x + cardW * 0.5f, y0 + cardH * 0.36f + sin(pulse * 3f + i) * 3f, min(cardW, cardH) * 0.14f, 1f, skin)
        val sk = skillsFor(hc)
        title(tm, hc.displayName, x + cardW * 0.5f, y0 + cardH * 0.52f, Color(0xFF2C1810), 20.sp)
        title(tm, sk.joinToString("") { it.glyph }, x + cardW * 0.5f, y0 + cardH * 0.58f, Color(0xFFB45309), 12.sp)
        title(tm, sk.joinToString("·") { it.name }, x + cardW * 0.5f, y0 + cardH * 0.68f, Color(0xFF3F3F46), 8.sp)
        title(tm, "Lv1-5技能·五行武器", x + cardW * 0.5f, y0 + cardH * 0.80f, Color(0xFF78716C), 9.sp)
    }
    title(tm, "点下方空白返回标题", w * 0.5f, h * 0.92f, Color(0xFF78716C), 11.sp)
}

private fun DrawScope.drawMap(meta: RunMeta, screen: Screen, tm: TextMeasurer, w: Float, h: Float) {
    val stage = meta.stage()
    val pulse = meta.pulse
    val pulseN = 0.5f + 0.5f * sin(pulse * 3f)
    drawInkScrollBackdrop(stage, w, h, pulse, meta.inkRank)
    meta.ensureVitals()

    // ── 极简顶栏：章名 + 数值 + 三个动作印 ──
    drawInkWoodBar(6f, h * 0.01f, w - 12f, h * 0.105f, 10f)
    val ch = StoryBook.chapters.getOrNull(stage.chapterIndex)
    val inkTag = if (meta.inkRank > 0) " 变异${meta.inkRank}" else ""
    var statusY = h * 0.014f
    fun statusLine(text: String, color: Color, size: TextUnit, gap: Float = h * 0.001f) {
        val shown = GameI18n.tr(text)
        val layout = tm.measure(shown, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
        drawText(layout, topLeft = Offset(w * 0.22f - layout.size.width / 2f, statusY))
        statusY += layout.size.height + gap
    }
    statusLine((ch?.title ?: stage.title) + inkTag, Color(0xFFF5EBD4), 13.sp)
    statusLine(
        "Lv${meta.level}  金${meta.gold}  药${meta.potions}  ${meta.curHp.toInt()}/${meta.maxHp().toInt()}",
        Color(0xFFE7C98A), 9.sp
    )
    val aff = meta.affixHudLine()
    if (aff.isNotEmpty()) statusLine(aff, Color(0xFFD6BC9A), 7.sp, 0f)
    // 三印：装 / 商 / 药（广告药并入商旁长按不需要，改为第四小印）
    fun seal(x: Float, text: String, accent: Color) {
        drawRoundRect(Color(0xEE1C1410), Offset(x, h * 0.022f), Size(w * 0.09f, h * 0.07f), CornerRadius(8f))
        drawRoundRect(accent, Offset(x, h * 0.022f), Size(w * 0.09f, h * 0.07f), CornerRadius(8f), style = Stroke(1.8f))
        title(tm, text, x + w * 0.045f, h * 0.038f, Color(0xFFF5EBD4), 12.sp)
    }
    seal(w * 0.58f, "装", Color(0xFFB45309))
    seal(w * 0.68f, "商", Color(0xFF0F766E))
    seal(w * 0.78f, "药${meta.potions}", Color(0xFF4D7C0F))
    seal(w * 0.88f, "广", Color(0xFF7C3AED))

    val panel = mapPanel(w, h)
    val l = panel[0]; val t = panel[1]; val mw = panel[2]; val mh = panel[3]
    val bottomTop = panel[4]
    // 地图区几乎不套框，只留淡墨边，卷轴本身是画
    drawRoundRect(Color(0x18F5EBD4), Offset(l, t), Size(mw, mh), CornerRadius(12f))

    val cur = currentNode(meta)
    val nextIds = cur?.next.orEmpty()
    val nextList = nextIds.mapNotNull { id -> stage.nodes.find { it.id == id } }

    for (n in stage.nodes) {
        val a = nodeCenter(n, w, h)
        for (nid in n.next) {
            val bNode = stage.nodes.find { it.id == nid } ?: continue
            val b = nodeCenter(bNode, w, h)
            val visited = n.id in meta.visited && bNode.id in meta.visited
            val active = n.id == meta.nodeId || bNode.id in nextIds
            drawInkPathStroke(a, b, active, visited, stage.chapterIndex)
        }
    }
    val nodeRadii = HashMap<Int, Float>(stage.nodes.size)
    for (n in stage.nodes) {
        val c = nodeCenter(n, w, h)
        val el = n.resolvedElement()
        val isHere = n.id == meta.nodeId
        val isNext = n.id in nextIds
        val r = when {
            isNext -> 20f + 2f * pulseN
            isHere -> 18f
            else -> 12f
        }
        nodeRadii[n.id] = r
        drawInkNodeIcon(c, r, n.type, el, isNext || isHere, pulseN, stage.chapterIndex)
        if (n.id in meta.visited && !isHere && !isNext) {
            drawCircle(Color(0xFF4D7C0F), 3f, Offset(c.x + r * 0.5f, c.y - r * 0.5f))
        }
    }
    // 不在图上写长名 —— 只圈你与可走点
    cur?.let {
        val c = nodeCenter(it, w, h)
        val r = (nodeRadii[it.id] ?: 16f) + 5f + 2f * pulseN
        drawCircle(Color(0xAAB91C1C), r, c, style = Stroke(2.2f))
        drawInkHero(c.x, c.y - 2f, 9f, 1f, meta.skin(), hero = meta.hero)
    }
    nextList.forEach { n ->
        val c = nodeCenter(n, w, h)
        val r = (nodeRadii[n.id] ?: 16f) + 6f + 3f * pulseN
        drawCircle(Color(0x66B45309), r, c, style = Stroke(2f))
    }

    // ── 底部：大而简的选路 ──
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xF03D2914), Color(0xF51C1410))),
        Offset(0f, bottomTop), Size(w, h - bottomTop), CornerRadius(0f)
    )
    title(tm, "点下方上路 · 或点图上发光点", w * 0.5f, bottomTop + h * 0.01f, Color(0xFFD6BC9A), 10.sp)
    val btnTop = bottomTop + h * 0.04f
    val btnH = h * 0.13f
    if (nextList.isEmpty()) {
        if (cur?.type == NodeType.EXIT) {
            drawInkButton(w * 0.25f, btnTop, w * 0.50f, btnH, Color(0xFFB45309))
            title(tm, "进入下一章", w * 0.5f, btnTop + btnH * 0.32f, Color(0xFFF5EBD4), 16.sp)
        } else {
            title(tm, "无路", w * 0.5f, btnTop + btnH * 0.3f, Color(0xFFFECACA), 14.sp)
        }
    } else {
        val gap = w * 0.015f
        val totalW = w * 0.78f
        val btnW = (totalW - gap * (nextList.size - 1).coerceAtLeast(0)) / nextList.size
        nextList.forEachIndexed { i, n ->
            val x0 = w * 0.04f + i * (btnW + gap)
            val el = n.resolvedElement()
            val col = nodeColor(n.type)
            drawParchmentPanel(x0, btnTop, btnW, btnH, radius = 12f, strokeCol = col.copy(alpha = 0.9f))
            title(tm, n.name, x0 + btnW * 0.5f, btnTop + btnH * 0.18f, Color(0xFF2C1810), 13.sp)
            val kind = when (n.type) {
                NodeType.MOB -> "战"
                NodeType.ELITE -> "精"
                NodeType.BOSS -> "魁"
                NodeType.SHOP -> "商"
                NodeType.HEAL, NodeType.REST -> "憩"
                NodeType.GOLD -> "金"
                NodeType.EVENT -> "事"
                NodeType.TRAP -> "险"
                NodeType.EXIT -> "出"
                NodeType.TREASURE -> "宝"
                NodeType.CHALLENGE -> "挑"
                else -> "·"
            }
            val rec = el?.let { "荐${it.beatenBy().short}" } ?: nodeRiskHint(n).take(6)
            val trial = roomTrialFor(meta.runSeed, meta.stageIndex, n)
            val routeLine = if (trial != null) "$kind $rec·${trial.title}" else "$kind  $rec"
            title(tm, routeLine, x0 + btnW * 0.5f, btnTop + btnH * 0.55f, Color(0xFF5C4033), 10.sp)
        }
    }
    val footY = h * 0.91f
    drawInkButton(w * 0.02f, footY, w * 0.18f, h * 0.07f, Color(0xFF57534E), 10f)
    title(tm, "存档", w * 0.11f, footY + h * 0.018f, Color(0xFFF5EBD4), 12.sp)
    drawInkButton(w * 0.80f, footY, w * 0.18f, h * 0.07f, Color(0xFF9F1239), 10f)
    title(tm, "放弃", w * 0.89f, footY + h * 0.018f, Color(0xFFF5EBD4), 12.sp)

    if (meta.toastT > 0f) {
        drawParchmentPanel(w * 0.22f, h * 0.12f, w * 0.56f, h * 0.05f, radius = 8f)
        title(tm, meta.toast, w * 0.5f, h * 0.128f, Color(0xFFB45309), 11.sp)
    }
    if (screen == Screen.EVENT) {
        drawRect(Color(0x88000000), size = Size(w, h))
        drawParchmentPanel(w * 0.12f, h * 0.16f, w * 0.76f, h * 0.66f, radius = 18f, strokeCol = Color(0xCCB91C1C))
        title(tm, meta.eventTitle, w * 0.5f, h * 0.19f, Color(0xFFB91C1C), 20.sp)
        // 整串翻译后再拆行（键含换行，先拆会回落中文）；长行断句兼容中日标点
        val bodyLines = GameI18n.tr(meta.eventBody).replace('\n', '｜').split('｜').flatMap { line ->
            if (line.length <= 22) listOf(line) else {
                val mid = line.length / 2
                val sp = line.indexOfAny(charArrayOf('，', '。', '、'), (mid - 4).coerceAtLeast(0)).takeIf { it > 0 }
                    ?: mid
                listOf(line.substring(0, sp + 1).trim(), line.substring(sp + 1).trim()).filter { it.isNotEmpty() }
            }
        }.take(5)
        bodyLines.forEachIndexed { i, line ->
            title(tm, line, w * 0.5f, h * 0.26f + i * h * 0.045f, Color(0xFF2C1810), 13.sp)
        }
        val choiceTop = h * 0.50f
        meta.eventChoices.forEachIndexed { i, pair ->
            val y = choiceTop + i * h * 0.11f
            drawInkButton(w * 0.18f, y, w * 0.64f, h * 0.09f, Color(0xFFB45309))
            title(tm, pair.first, w * 0.5f, y + h * 0.025f, Color(0xFFF5EBD4), 14.sp)
        }
    }
    if (screen == Screen.STAGE_CLEAR) {
        drawRect(Color(0x88000000), size = Size(w, h))
        drawParchmentPanel(w * 0.18f, h * 0.30f, w * 0.64f, h * 0.32f, radius = 18f, strokeCol = Color(0xCC4D7C0F))
        title(tm, "关卡完成", w * 0.5f, h * 0.40f, Color(0xFF3F6212), 28.sp)
        title(tm, "轻触继续", w * 0.5f, h * 0.52f, Color(0xFF5C4033), 14.sp)
    }
}

private fun DrawScope.drawLevelUp(meta: RunMeta, tm: TextMeasurer, w: Float, h: Float) {
    drawInkPaperBackdrop(w, h, meta.pulse, meta.stage().chapterIndex)
    drawInkWoodBar(w * 0.2f, h * 0.08f, w * 0.6f, h * 0.1f)
    // 升级金色脉冲：呼吸感的背景光环
    val pulse = 0.5f + 0.5f * sin(meta.pulse * 5f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0x30FDE047), Color.Transparent)),
        min(w, h) * (0.3f + pulse * 0.1f), Offset(w * 0.5f, h * 0.1f)
    )
    title(tm, "升级! Lv${meta.level}", w * 0.5f, h * 0.10f, Color(0xFFFDE047), 26.sp)
    title(tm, "选择一项天赋", w * 0.5f, h * 0.20f, Color(0xFF5C4033), 14.sp)
    meta.levelChoices.forEachIndexed { i, p ->
        val y = h * 0.32f + i * h * 0.14f
        drawParchmentPanel(w * 0.1f, y, w * 0.8f, h * 0.12f, strokeCol = Color(0xAAB91C1C))
        title(tm, p.title, w * 0.5f, y + h * 0.025f, Color(0xFFB91C1C), 18.sp)
        title(tm, p.desc, w * 0.5f, y + h * 0.065f, Color(0xFF3F3F46), 13.sp)
    }
}

private fun DrawScope.drawCoreInkChoice(meta: RunMeta, tm: TextMeasurer, w: Float, h: Float) {
    drawInkPaperBackdrop(w, h, meta.pulse, meta.stage().chapterIndex)
    drawInkWoodBar(w * 0.16f, h * 0.055f, w * 0.68f, h * 0.105f)
    title(tm, "感染核心击破 · 免疫核心三选一", w * 0.5f, h * 0.078f, Color(0xFFF5EBD4), 23.sp)
    title(tm, "免疫核心会改变技能机制，同一路线可升至五阶", w * 0.5f, h * 0.19f, Color(0xFF5C4033), 13.sp)
    meta.coreInkChoices.forEachIndexed { i, ink ->
        val y = h * 0.30f + i * h * 0.17f
        val current = meta.coreInkRanks.rankOf(ink)
        val next = (current + 1).coerceAtMost(5)
        val accent = Color(ink.color)
        drawParchmentPanel(w * 0.08f, y, w * 0.84f, h * 0.145f, radius = 16f, strokeCol = accent.copy(alpha = 0.82f))
        drawCircle(Color(0xDD1C1917), h * 0.038f, Offset(w * 0.155f, y + h * 0.070f))
        drawCircle(accent, h * 0.038f, Offset(w * 0.155f, y + h * 0.070f), style = Stroke(3f))
        title(tm, ink.glyph, w * 0.155f, y + h * 0.056f, Color.White, 19.sp)
        title(tm, ink.title, w * 0.52f, y + h * 0.024f, accent, 17.sp)
        title(
            tm,
            if (current == 0) "未获得 → 一阶" else "$current 阶 → $next 阶",
            w * 0.85f, y + h * 0.028f, Color(0xFF78716C), 11.sp
        )
        title(tm, "改造「${ink.skillName}」", w * 0.52f, y + h * 0.061f, Color(0xFF3F6212), 12.sp)
        ink.desc.chunked(23).take(2).forEachIndexed { line, text ->
            title(tm, text, w * 0.52f, y + h * (0.092f + line * 0.025f), Color(0xFF3F3F46), 10.sp)
        }
    }
    val owned = meta.coreInkRanks.entries.filter { it.value > 0 }
    if (owned.isNotEmpty()) {
        title(
            tm,
            "已有：${owned.joinToString(" · ") { "${it.key.glyph}${it.value}" }}",
            w * 0.5f, h * 0.86f, Color(0xFF5C4033), 12.sp
        )
    }
}

private fun DrawScope.drawSetAwakenedOverlay(meta: RunMeta, tm: TextMeasurer, w: Float, h: Float) {
    val set = SetCatalog.byId(meta.setAwakenedId) ?: return
    val remaining = (meta.setAwakenedT / 3.2f).coerceIn(0f, 1f)
    val progress = 1f - remaining
    val alpha = min((progress / 0.12f).coerceIn(0f, 1f), (remaining / 0.18f).coerceIn(0f, 1f))
    val accent = Color(set.proc.fxColor())
    val cx = w * 0.5f
    val cy = h * 0.48f
    val burst = 0.82f + sin(progress * 18f) * 0.04f

    drawRect(Color(0xCC09090B).copy(alpha = 0.72f * alpha), size = Size(w, h))
    drawCircle(
        Brush.radialGradient(
            listOf(accent.copy(alpha = 0.32f * alpha), Color.Transparent),
            center = Offset(cx, cy), radius = min(w, h) * 0.42f
        ),
        min(w, h) * 0.42f,
        Offset(cx, cy)
    )
    repeat(16) { index ->
        val angle = index * (6.28318f / 16f) + progress * 0.35f
        val inner = min(w, h) * 0.13f
        val outer = min(w, h) * (0.31f + 0.025f * sin(index * 2.1f + progress * 9f))
        drawLine(
            accent.copy(alpha = 0.48f * alpha),
            Offset(cx + cos(angle) * inner, cy + sin(angle) * inner),
            Offset(cx + cos(angle) * outer, cy + sin(angle) * outer),
            if (index % 2 == 0) 4f else 2f,
            StrokeCap.Round
        )
    }
    drawParchmentPanel(w * 0.17f, h * 0.18f, w * 0.66f, h * 0.60f, radius = 22f, strokeCol = accent.copy(alpha = alpha))
    drawCircle(accent.copy(alpha = 0.18f * alpha), min(w, h) * 0.13f * burst, Offset(cx, cy))
    drawSetAura(cx, cy, min(w, h) * 0.075f, set, meta.pulse * 1.6f)
    drawCuteHero(cx, cy, min(w, h) * 0.07f, 1f, meta.skin(), bob = sin(meta.pulse * 5f) * 2f)

    val weapon = meta.equippedWeapon().takeIf { it.id in set.weaponIds }
        ?: set.weaponIds.firstNotNullOfOrNull { WeaponCatalog.byId(it) }
    val armor = meta.equippedArmor().takeIf { it.id in set.armorIds }
        ?: set.armorIds.firstNotNullOfOrNull { ArmorCatalog.byId(it) }
    weapon?.let { drawWeaponArt(cx - w * 0.17f, cy, min(w, h) * 0.095f, it, meta.pulse) }
    armor?.let { drawArmorArt(cx + w * 0.17f, cy, min(w, h) * 0.095f, it, meta.pulse) }

    title(tm, "套装觉醒", cx, h * 0.23f, Color(0xFFF5EBD4).copy(alpha = alpha), 18.sp)
    title(tm, set.name, cx, h * 0.30f, accent.copy(alpha = alpha), 28.sp)
    title(tm, "${set.bonusTitle} · ${set.bonusTip}", cx, h * 0.63f, Color(0xFF3F3F46).copy(alpha = alpha), 14.sp)
    title(tm, "特效「${set.proc.title}」已激活", cx, h * 0.69f, accent.copy(alpha = alpha), 13.sp)
}


/** 商店货架扁平条目（绘制与点击共用） */
private data class ShopOffer(
    val kind: String,
    val id: String,
    val name: String,
    val price: Int,
    val owned: Boolean,
    val rec: Boolean
)

private fun buildShopOffers(meta: RunMeta): List<ShopOffer> {
    val prefer = meta.nextCombatRecommendElement()
    val list = ArrayList<ShopOffer>(16)
    for (id in meta.shopWeaponOffers) {
        val wp = WeaponCatalog.byId(id) ?: continue
        list.add(ShopOffer("w", id, wp.name, wp.cost, id in meta.ownedWeapons, prefer != null && wp.element == prefer))
    }
    for (id in meta.shopArmorOffers) {
        val ar = ArmorCatalog.byId(id) ?: continue
        list.add(ShopOffer("a", id, ar.name, ar.cost, id in meta.ownedArmors, false))
    }
    for (id in meta.shopRingOffers) {
        val r = RingCatalog.byId(id) ?: continue
        list.add(ShopOffer("r", id, r.name, r.cost, id in meta.ownedRings, false))
    }
    for (id in meta.shopBootsOffers) {
        val b = BootsCatalog.byId(id) ?: continue
        list.add(ShopOffer("b", id, b.name, b.cost, id in meta.ownedBoots, false))
    }
    for (id in meta.shopFruitOffers) {
        val f = ItemCatalog.byId(id) ?: continue
        list.add(ShopOffer("f", id, f.name, f.cost, false, false))
    }
    list.add(ShopOffer("potion", "potion", "药水", 18, false, false))
    meta.shopTomeId?.let { tid ->
        TomeCatalog.byId(tid)?.let { t ->
            list.add(ShopOffer("tome", tid, t.name, t.cost, false, false))
        }
    }
    return list
}

private fun DrawScope.drawEquipmentIcon(
    kind: String,
    id: String,
    cx: Float,
    cy: Float,
    size: Float,
    pulse: Float
) {
    when (kind) {
        "w" -> WeaponCatalog.byId(id)?.let { drawWeaponArt(cx, cy, size, it, pulse) }
        "a" -> ArmorCatalog.byId(id)?.let { drawArmorArt(cx, cy, size, it, pulse) }
        "r" -> RingCatalog.byId(id)?.let { drawAccessoryArt(cx, cy, size, it, pulse) }
        "b" -> BootsCatalog.byId(id)?.let { drawAccessoryArt(cx, cy, size, it, pulse) }
        "f" -> {
            val color = ItemCatalog.byId(id)?.element?.color ?: Color(0xFFFDA4AF)
            drawCircle(color.copy(alpha = 0.2f), size * 0.8f, Offset(cx, cy))
            // 果实：珠体 + 叶
            drawCircle(
                Brush.radialGradient(
                    listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.4f)),
                    center = Offset(cx - size * 0.1f, cy - size * 0.1f), radius = size * 0.6f
                ),
                size * 0.42f, Offset(cx, cy)
            )
            drawCircle(Color(0x550F172A), size * 0.42f, Offset(cx, cy), style = Stroke(1f))
            drawLine(Color(0xFF4D7C0F), Offset(cx, cy - size * 0.4f), Offset(cx, cy - size * 0.52f), 1.8f, StrokeCap.Round)
            val leaf = Path().apply {
                moveTo(cx, cy - size * 0.48f)
                quadraticTo(cx + size * 0.22f, cy - size * 0.6f, cx + size * 0.28f, cy - size * 0.5f)
                quadraticTo(cx + size * 0.14f, cy - size * 0.4f, cx, cy - size * 0.48f)
                close()
            }
            drawPath(leaf, Color(0xFF4ADE80))
            drawCircle(Color.White.copy(alpha = 0.7f), size * 0.08f, Offset(cx - size * 0.14f, cy - size * 0.14f))
        }
        "tome" -> {
            // 卷轴：卷筒 + 纸面 + 朱印
            drawCircle(Color(0xFF7C3AED).copy(alpha = 0.15f), size * 0.8f, Offset(cx, cy))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFA78BFA), Color(0xFF6D28D9))),
                Offset(cx - size * 0.36f, cy - size * 0.4f), Size(size * 0.72f, size * 0.8f), CornerRadius(size * 0.06f)
            )
            drawRoundRect(Color(0xFF2E1065), Offset(cx - size * 0.36f, cy - size * 0.4f), Size(size * 0.72f, size * 0.8f), CornerRadius(size * 0.06f), style = Stroke(1.2f))
            // 纸面
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFFEF9E7), Color(0xFFEDE0BE))),
                Offset(cx - size * 0.26f, cy - size * 0.3f), Size(size * 0.52f, size * 0.6f), CornerRadius(3f)
            )
            for (i in 0..2) {
                drawLine(Color(0xFF92765A), Offset(cx - size * 0.18f, cy - size * 0.16f + i * size * 0.13f), Offset(cx + size * 0.18f, cy - size * 0.16f + i * size * 0.13f), 1.5f)
            }
            // 朱印
            drawCircle(Color(0xFFDC2626), size * 0.09f, Offset(cx + size * 0.16f, cy + size * 0.2f))
            // 卷筒头
            drawRoundRect(Color(0xFFFBBF24), Offset(cx - size * 0.4f, cy - size * 0.44f), Size(size * 0.8f, size * 0.09f), CornerRadius(size * 0.045f))
            drawRoundRect(Color(0xFFFBBF24), Offset(cx - size * 0.4f, cy + size * 0.31f), Size(size * 0.8f, size * 0.09f), CornerRadius(size * 0.045f))
        }
        else -> {
            drawCircle(Color(0xFF4D7C0F), size * 0.5f, Offset(cx, cy))
            drawCircle(Color(0xFF86EFAC), size * 0.2f, Offset(cx - size * 0.08f, cy - size * 0.08f))
        }
    }
}

private fun DrawScope.drawShop(meta: RunMeta, tm: TextMeasurer, w: Float, h: Float) {
    drawInkPaperBackdrop(w, h, meta.pulse, meta.stage().chapterIndex)
    drawInkWoodBar(8f, h * 0.012f, w - 16f, h * 0.10f, 12f)
    val eq = meta.equippedWeapon()
    title(tm, "行商", w * 0.5f, h * 0.025f, Color(0xFFF5EBD4), 18.sp)
    title(tm, "金${meta.gold} · 现装 ${eq.name}", w * 0.5f, h * 0.06f, Color(0xFFE7C98A), 12.sp)
    title(tm, meta.shopRecommendTip().take(28), w * 0.5f, h * 0.09f, Color(0xFFD6BC9A), 10.sp)

    val offers = buildShopOffers(meta)
    val cols = 2
    val cellW = w * 0.44f
    val cellH = h * 0.105f
    val gapX = w * 0.03f
    val startY = h * 0.14f
    offers.take(12).forEachIndexed { i, o ->
        val col = i % cols
        val row = i / cols
        val x = w * 0.045f + col * (cellW + gapX)
        val y = startY + row * (cellH + h * 0.012f)
        val can = !o.owned && meta.gold >= o.price
        val stroke = when {
            o.owned -> Color(0xFF78716C)
            !can -> Color(0xFFB91C1C)
            o.rec -> Color(0xFFB45309)
            else -> Color(0xFF5C4033)
        }
        drawParchmentPanel(
            x, y, cellW, cellH, radius = 10f, strokeCol = stroke,
            fill = if (!can && !o.owned) Color(0xEEF5D0D0) else Color(0xEEF5EBD4)
        )
        val tag = when {
            o.owned -> "有"
            o.rec -> "荐"
            else -> when (o.kind) {
                "w" -> "武"; "a" -> "甲"; "r" -> "戒"; "b" -> "鞋"; "f" -> "果"; "tome" -> "卷"; else -> "物"
            }
        }
        drawEquipmentIcon(o.kind, o.id, x + cellW * 0.13f, y + cellH * 0.46f, cellH * 0.34f, meta.pulse)
        title(tm, "$tag  ${o.name}", x + cellW * 0.58f, y + cellH * 0.18f, Color(0xFF2C1810), 13.sp)
        val price = when {
            o.owned -> "已拥有"
            !can -> "差${o.price - meta.gold}金"
            else -> "${o.price}金 · 点买"
        }
        title(tm, price, x + cellW * 0.58f, y + cellH * 0.55f, if (can && !o.owned) Color(0xFF3F6212) else Color(0xFF78716C), 11.sp)
    }
    drawInkButton(w * 0.08f, h * 0.88f, w * 0.38f, h * 0.08f, Color(0xFF0F766E))
    title(tm, "换装", w * 0.27f, h * 0.90f, Color(0xFFF5EBD4), 15.sp)
    drawInkButton(w * 0.54f, h * 0.88f, w * 0.38f, h * 0.08f, Color(0xFF5C4033))
    title(tm, "离开", w * 0.73f, h * 0.90f, Color(0xFFF5EBD4), 15.sp)
    if (meta.toastT > 0f) title(tm, meta.toast, w * 0.5f, h * 0.82f, Color(0xFFB45309), 12.sp)
}

/** 装备：上穿戴四格 · 下单列背包 */
private fun DrawScope.drawGear(meta: RunMeta, tm: TextMeasurer, w: Float, h: Float, scroll: Float = 0f) {
    val pulse = meta.pulse
    val sc = scroll
    drawInkPaperBackdrop(w, h, pulse, meta.stage().chapterIndex)
    drawInkWoodBar(8f, h * 0.01f, w - 16f, h * 0.08f, 10f)
    title(tm, "换装 · 点一项装备", w * 0.5f, h * 0.025f, Color(0xFFF5EBD4), 16.sp)

    val eq = meta.equippedWeapon()
    val worn = meta.equippedArmor()
    val ring = meta.equippedRing()
    val boots = meta.equippedBoots()
    // 当前穿戴
    val slots = listOf(
        Triple("w", eq.id, eq.name),
        Triple("a", worn.id, worn.name),
        Triple("r", ring?.id.orEmpty(), ring?.name ?: "无"),
        Triple("b", boots.id, boots.name)
    )
    slots.forEachIndexed { i, (kind, id, name) ->
        val x = w * 0.04f + i * w * 0.24f
        drawParchmentPanel(x, h * 0.11f, w * 0.22f, h * 0.12f, radius = 10f)
        if (id.isNotEmpty()) drawEquipmentIcon(kind, id, x + w * 0.055f, h * 0.17f, h * 0.046f, pulse)
        val k = when (kind) { "w" -> "武"; "a" -> "甲"; "r" -> "戒"; else -> "鞋" }
        title(tm, k, x + w * 0.145f, h * 0.125f, Color(0xFFB91C1C), 12.sp)
        title(tm, name.take(6), x + w * 0.145f, h * 0.165f, Color(0xFF2C1810), 11.sp)
    }
    drawInkHero(w * 0.88f, h * 0.17f, h * 0.045f, 1f, meta.skin(), bob = sin(pulse * 2f) * 2f, hero = meta.hero)

    // 单列列表
    data class Row(val kind: String, val id: String, val title: String, val sub: String, val on: Boolean)
    val rows = ArrayList<Row>()
    meta.ownedWeaponsSorted().forEach { wp ->
        rows.add(Row("w", wp.id, wp.name, "攻+${wp.atkBonus.toInt()}", wp.id == meta.equippedWeaponId))
    }
    meta.ownedArmorsSorted().forEach { ar ->
        rows.add(Row("a", ar.id, ar.name, ar.statsCompact().take(12), ar.id == meta.equippedArmorId))
    }
    meta.ownedRingsSorted().forEach { r ->
        rows.add(Row("r", r.id, r.name, r.statsCompact().take(12), r.id == meta.equippedRingId))
    }
    meta.ownedBootsSorted().forEach { b ->
        rows.add(Row("b", b.id, b.name, b.statsCompact().take(12), b.id == meta.equippedBootsId))
    }
    val listTop = h * 0.26f
    val rowH = h * 0.09f
    rows.forEachIndexed { i, row ->
        val y = listTop + i * rowH + sc
        if (y + rowH < listTop - 4f || y > h * 0.86f) return@forEachIndexed
        drawParchmentPanel(
            w * 0.06f, y, w * 0.88f, rowH * 0.88f, radius = 10f,
            strokeCol = if (row.on) Color(0xFF4D7C0F) else Color(0xAA5C4033),
            fill = if (row.on) Color(0xEEDAF0C8) else Color(0xEEF5EBD4)
        )
        val kind = when (row.kind) { "w" -> "武"; "a" -> "甲"; "r" -> "戒"; else -> "鞋" }
        drawEquipmentIcon(row.kind, row.id, w * 0.13f, y + rowH * 0.40f, rowH * 0.32f, pulse)
        title(tm, if (row.on) "●$kind ${row.title}" else "$kind ${row.title}", w * 0.53f, y + rowH * 0.12f, Color(0xFF2C1810), 14.sp)
        title(tm, row.sub, w * 0.53f, y + rowH * 0.48f, Color(0xFF5C4033), 11.sp)
    }
    drawInkButton(w * 0.25f, h * 0.90f, w * 0.5f, h * 0.08f, Color(0xFF5C4033))
    title(tm, "返回地图", w * 0.5f, h * 0.92f, Color(0xFFF5EBD4), 14.sp)
    if (meta.toastT > 0f) title(tm, meta.toast, w * 0.5f, h * 0.86f, Color(0xFFB45309), 12.sp)
}

private fun DrawScope.drawArena(
    sim: ArenaSim,
    meta: RunMeta,
    tm: TextMeasurer,
    w: Float,
    h: Float,
    stickX: Float,
    stickY: Float,
    joyActive: Boolean,
    joyOx: Float,
    joyOy: Float,
    joyKnobX: Float,
    joyKnobY: Float,
    basicHeld: Boolean,
    s1Held: Boolean,
    s2Held: Boolean,
    s3Held: Boolean = false,
    s4Held: Boolean = false,
    wheelActive: Boolean = false,
    wheelX: Float = 0f,
    wheelY: Float = 0f,
    wheelSlotSel: Int = 0,
    abandonArmedT: Long = 0L
) {
    val viewH = h // landscape: use full height for combat
    // camera follow：弱化 zoom/shake，避免攻击时整屏一跳一跳
    val maxCamX = (sim.width - w).coerceAtLeast(0f)
    val maxCamY = (sim.height - viewH).coerceAtLeast(0f)
    val zp = sim.zoomPunch * 0.45f
    val focusX = sim.player.x * (1f - zp * 0.12f) + sim.lastHitX * (zp * 0.12f)
    val focusY = sim.player.y * (1f - zp * 0.12f) + sim.lastHitY * (zp * 0.12f)
    val zoom = 1f + zp * 0.05f
    var camX = (focusX - w * 0.42f / zoom).coerceIn(0f, maxCamX)
    var camY = (focusY - viewH * 0.55f / zoom).coerceIn(0f, maxCamY)
    if (sim.shake > 0f) {
        camX += sin(sim.time * 55f) * sim.shake * 10f
        camY += cos(sim.time * 48f) * sim.shake * 8f
    }
    fun wx(x: Float): Float = (x - camX) * zoom + w * 0.5f * (1f - zoom)
    fun wy(y: Float): Float = (y - camY) * zoom + viewH * 0.5f * (1f - zoom)
    fun wr(r: Float): Float = r * zoom

    // top-down world field — 古画地席，按章节色系
    drawArenaBackdrop(
        meta.stage().chapterIndex, sim.environment, w, viewH, camX, camY, sim.time,
        sim.width, sim.height,
        { x -> wx(x) }, { y -> wy(y) }, { r -> wr(r) }
    )

    // 战场地形：灵力符阵结界（挡弹体、可绕行；旋转符文环随时间流转）
    for (o in sim.obstacles) {
        val ox = wx(o.x); val oy = wy(o.y); val or = wr(o.r)
        val spin = sim.time * 0.9f + o.x * 0.01f
        val pulse = 0.5f + 0.2f * sin(sim.time * 3f + o.x * 0.05f)
        drawCircle(Color(0xFF10201A).copy(alpha = 0.72f), or, Offset(ox, oy))
        drawCircle(Color(0xFF46C6A2).copy(alpha = 0.16f + 0.12f * pulse), or, Offset(ox, oy), style = Stroke(6f))
        drawCircle(Color(0xFF46C6A2).copy(alpha = 0.5f), or * 0.7f, Offset(ox, oy), style = Stroke(2.5f))
        val runes = 8
        for (i in 0 until runes) {
            val a = i * (6.28318f / runes) + spin
            drawLine(
                Color(0xFF7EF0CD).copy(alpha = 0.55f),
                Offset(ox + cos(a) * or * 0.78f, oy + sin(a) * or * 0.78f),
                Offset(ox + cos(a) * or * 0.94f, oy + sin(a) * or * 0.94f),
                4f, StrokeCap.Round
            )
        }
        drawCircle(Color(0xFF7EF0CD).copy(alpha = 0.18f + 0.12f * pulse), or * 0.18f, Offset(ox, oy))
    }
    // 增益祭坛：脉动彩印 + 剩余时间弧
    sim.shrine?.let { sh ->
        val a = (sh.life / sh.maxLife).coerceIn(0f, 1f)
        val pulse = 1f + sin(sim.time * 7f) * 0.08f
        val cx = wx(sh.x); val cy = wy(sh.y)
        val col = when (sh.buff) {
            0 -> Color(0xFFFB923C)
            1 -> Color(0xFF7DD3FC)
            2 -> Color(0xFFA78BFA)
            else -> Color(0xFFFBBF24)
        }
        val glyph = when (sh.buff) {
            0 -> "攻"
            1 -> "盾"
            2 -> "蓝"
            else -> "金"
        }
        drawCircle(col.copy(alpha = 0.22f + 0.25f * a), wr(52f) * pulse, Offset(cx, cy))
        drawCircle(col.copy(alpha = 0.85f * a), wr(26f) * pulse, Offset(cx, cy), style = Stroke(4f))
        drawArc(
            col.copy(alpha = a), -90f, 360f * a, false,
            Offset(cx - wr(42f), cy - wr(42f)), Size(wr(84f), wr(84f)), style = Stroke(3f)
        )
        title(tm, glyph, cx, cy - wr(12f), Color.White.copy(alpha = a), 16.sp)
    }

    // 墨迹残留层（在角色脚下，战斗中累积，清场可成画）
    drawInkMarkLayer(
        sim.inkMarks, sim.inkSettled, sim.inkSettleT,
        { x -> wx(x) }, { y -> wy(y) }, { r -> wr(r) }
    )
    if (sim.inkSettled) {
        val fade = (sim.inkSettleT / 2.5f).coerceIn(0f, 1f)
        title(
            tm, "落款 · 一纸墨战场",
            w * 0.5f, viewH * 0.12f,
            Color(0xFF2C1810).copy(alpha = 0.55f * fade),
            16.sp
        )
    }
    // 墨马必杀全屏（屏幕空间）
    if (sim.inkHorseT > 0f) {
        val prog = 1f - (sim.inkHorseT / sim.inkHorseMax)
        // 淡墨罩一层，像宣纸上突然落笔
        drawRect(Color(0x221C1410).copy(alpha = 0.12f + 0.1f * sin(prog * 6f)), size = Size(w, viewH))
        drawInkHorseCharge(prog, sim.inkHorseDir, w, viewH)
        title(tm, "墨马奔袭", w * 0.5f, viewH * 0.08f, Color(0xDD2C1810), 18.sp)
    }

    // 免疫核心的延迟回响：先看到收束信号环，随后触发第二次技能脉冲。
    for (echo in sim.echoPulses) {
        val progress = (1f - echo.life / echo.maxLife.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        val center = Offset(wx(echo.x), wy(echo.y))
        val radius = wr(echo.radius)
        val echoColor = Color(echo.color)
        drawCircle(echoColor.copy(alpha = 0.08f + progress * 0.12f), radius, center)
        drawCircle(echoColor.copy(alpha = 0.48f + progress * 0.35f), radius, center, style = Stroke(3f + progress * 3f))
        drawCircle(Color.White.copy(alpha = 0.25f + progress * 0.55f), radius * (1f - progress * 0.78f), center, style = Stroke(2f))
        title(tm, echo.glyph, center.x, center.y - 2f, echoColor.copy(alpha = 0.55f + progress * 0.4f), 18.sp)
    }

    // Boss 专属招式预警：实心淡区表示危险面，收束白线表示剩余反应时间。
    for (hazard in sim.bossHazards) {
        val progress = hazard.warningProgress()
        val pulse = 0.55f + sin(sim.time * 14f) * 0.18f
        val danger = Color(hazard.color)
        when (hazard.shape) {
            BossHazardShape.CIRCLE -> {
                val center = Offset(wx(hazard.x0), wy(hazard.y0))
                val radius = wr(hazard.outerRadius)
                drawCircle(danger.copy(alpha = 0.13f + progress * 0.12f), radius, center)
                drawCircle(danger.copy(alpha = pulse), radius, center, style = Stroke(4f + progress * 3f))
                drawCircle(Color.White.copy(alpha = 0.35f + progress * 0.45f), radius * (1f - progress * 0.82f), center, style = Stroke(2.5f))
            }
            BossHazardShape.RING -> {
                val center = Offset(wx(hazard.x0), wy(hazard.y0))
                val inner = wr(hazard.innerRadius)
                val outer = wr(hazard.outerRadius)
                val middle = (inner + outer) * 0.5f
                drawCircle(
                    danger.copy(alpha = 0.14f + progress * 0.12f),
                    middle,
                    center,
                    style = Stroke((outer - inner).coerceAtLeast(2f))
                )
                drawCircle(danger.copy(alpha = pulse), inner, center, style = Stroke(3.5f))
                drawCircle(danger.copy(alpha = pulse), outer, center, style = Stroke(3.5f))
                val sweep = inner + (outer - inner) * progress
                drawCircle(Color.White.copy(alpha = 0.32f + progress * 0.5f), sweep, center, style = Stroke(2f))
            }
            BossHazardShape.LINE -> {
                val start = Offset(wx(hazard.x0), wy(hazard.y0))
                val end = Offset(wx(hazard.x1), wy(hazard.y1))
                val lane = wr(hazard.width)
                drawLine(danger.copy(alpha = 0.12f + progress * 0.13f), start, end, lane, StrokeCap.Round)
                drawLine(danger.copy(alpha = pulse), start, end, 4f + progress * 3f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.25f + progress * 0.5f), start, end, 1.5f + progress * 2f, StrokeCap.Round)
            }
        }
        if (hazard.label.isNotEmpty()) {
            val labelX = if (hazard.shape == BossHazardShape.LINE) (hazard.x0 + hazard.x1) * 0.5f else hazard.x0
            val labelY = if (hazard.shape == BossHazardShape.LINE) (hazard.y0 + hazard.y1) * 0.5f else hazard.y0
            val labelLift = if (hazard.shape == BossHazardShape.LINE) 38f else hazard.outerRadius.coerceAtLeast(35f)
            title(
                tm, "预警 · ${hazard.label}",
                wx(labelX), wy(labelY) - wr(labelLift) - 10f,
                Color(0xFFFEE2E2), 13.sp
            )
        }
    }

    // rings under characters（屏外裁剪：省下的绘制就是帧率和响应速度）
    for (r in sim.rings) {
        val a = (r.life / r.maxLife).coerceIn(0f, 1f)
        val sxr = wx(r.x)
        if (sxr < -160f || sxr > w + 160f) continue
        val syr = wy(r.y)
        if (syr < -160f || syr > viewH + 160f) continue
        drawCircle(
            Color(r.color).copy(alpha = a * 0.7f),
            wr(r.r * (1.15f - a * 0.15f)),
            Offset(sxr, syr),
            style = Stroke(4f)
        )
    }
    // 笔锋斩弧：外圈淡晕 + 内圈亮笔，随生命收弧（剪影感的关键）
    for (a in sim.slashArcs) {
        val alpha = (a.life / a.maxLife).coerceIn(0f, 1f)
        val sweepDeg = a.sweep * (0.35f + 0.65f * alpha) * 57.29578f
        val startDeg = (a.startAngle + a.spin * (1f - alpha)) * 57.29578f
        val rad = wr(a.r)
        val tl = Offset(wx(a.x) - rad, wy(a.y) - rad)
        val sz = Size(rad * 2f, rad * 2f)
        drawArc(
            Color(a.color).copy(alpha = alpha * 0.26f), startDeg, sweepDeg, false, tl, sz,
            style = Stroke(wr(a.width) * 2.4f, cap = StrokeCap.Round)
        )
        drawArc(
            Color(a.color).copy(alpha = alpha * 0.9f), startDeg, sweepDeg, false, tl, sz,
            style = Stroke(wr(a.width), cap = StrokeCap.Round)
        )
    }
    // 墨锋碎片：锥形笔触，宽度随生命变细（水墨笔锋的飞白感）
    for (s in sim.shards) {
        val alpha = (s.life / s.maxLife).coerceIn(0f, 1f)
        val sxs = wx(s.x)
        if (sxs < -120f || sxs > w + 120f) continue
        val sys = wy(s.y)
        if (sys < -120f || sys > viewH + 120f) continue
        val tx = s.x - cos(s.angle) * s.len
        val ty = s.y - sin(s.angle) * s.len
        drawLine(
            Color(s.color).copy(alpha = alpha * 0.9f),
            Offset(sxs, sys),
            Offset(wx(tx), wy(ty)),
            wr(s.width) * (0.6f + alpha),
            StrokeCap.Round
        )
    }
    // Real lightning strokes: segmented, bright core + colored bloom.
    for (bolt in sim.bolts) {
        val alpha = (bolt.life / bolt.maxLife).coerceIn(0f, 1f)
        val x0 = wx(bolt.x0); val y0 = wy(bolt.y0)
        val x1 = wx(bolt.x1); val y1 = wy(bolt.y1)
        val dx = x1 - x0; val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val nx = -dy / length; val ny = dx / length
        var px = x0; var py = y0
        val segments = (length / 26f).toInt().coerceIn(8, 22)
        for (i in 1..segments) {
            val q = i / segments.toFloat()
            val jitter = if (i == segments) 0f else sin(i * 2.7f + sim.time * 38f) * (bolt.width * 1.1f)
            val tx = x0 + dx * q + nx * jitter
            val ty = y0 + dy * q + ny * jitter
            drawLine(Color(bolt.color).copy(alpha = alpha * 0.35f), Offset(px, py), Offset(tx, ty), bolt.width, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = alpha), Offset(px, py), Offset(tx, ty), bolt.width * 0.28f, StrokeCap.Round)
            px = tx; py = ty
        }
    }

    val t = sim.time
    for (e in sim.enemies) {
        if (e.dead) continue
        val kind = e.kind
        // bats/wisps hover higher
        val hover = when (kind) {
            EnemyKind.BAT, EnemyKind.WISP -> -wr(10f) + sin(t * 6f + e.x * 0.03f) * 4f
            else -> 0f
        }
        val bob = sin(t * 5f + e.x * 0.02f) * 2.5f
        val sq = e.squash.coerceIn(0f, 1f)
        val drawR = wr(e.radius * (1f + sq * 0.35f))
        val ex = wx(e.x)
        val ey = wy(e.y) + sq * wr(e.radius) * 0.25f + hover
        val eliteColor = if (e.eliteTrait == EnemyEliteTrait.NONE) Color(0xFFFBBF24)
            else Color(e.eliteTrait.color)
        if (e.specialAttack != EnemySignatureAttack.NONE && e.specialWindup > 0f) {
            val cue = e.specialAttack
            val cueColor = Color(cue.color)
            val progress = (1f - e.specialWindup / cue.windup.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            val aim = Offset(wx(e.specialAimX), wy(e.specialAimY))
            drawLine(cueColor.copy(alpha = 0.3f + progress * 0.45f), Offset(ex, ey), aim, 2f + progress * 2f, StrokeCap.Round)
            drawCircle(cueColor.copy(alpha = 0.16f + progress * 0.18f), drawR * (1.25f + progress * 0.35f), Offset(ex, ey))
            drawCircle(cueColor.copy(alpha = 0.8f), drawR * (1.55f - progress * 0.28f), Offset(ex, ey), style = Stroke(2.5f + progress * 1.5f))
            drawCircle(cueColor.copy(alpha = 0.68f), 18f + progress * 10f, aim, style = Stroke(2.5f))
            title(tm, cue.glyph, ex, ey - drawR * 1.8f, cueColor, 10.sp)
        }
        if (e.windup > 0f) drawCircle(Color(0x88FB923C), drawR * 1.55f, Offset(ex, ey), style = Stroke(4f))
        if (e.enraged) drawCircle(Color(0x55EF4444), drawR * 1.35f, Offset(ex, ey), style = Stroke(3f))
        if (e.elite) {
            drawCircle(eliteColor.copy(alpha = 0.24f), drawR * 1.42f, Offset(ex, ey))
            drawCircle(eliteColor.copy(alpha = 0.82f), drawR * 1.3f, Offset(ex, ey), style = Stroke(3f))
        }
        if (e.hitStun > 0f) {
            drawCircle(Color.White.copy(alpha = 0.25f), drawR * 1.25f, Offset(ex, ey))
        }
        drawShadowDisk(ex, ey - hover * 0.3f, drawR * (1f - sq * 0.15f))
        // freeze shell under sprite
        if (e.has(StatusType.FREEZE)) {
            drawCircle(Color(0x667DD3FC), drawR * 1.25f, Offset(ex, ey))
            drawCircle(Color(0xAAE0F2FE), drawR * 1.15f, Offset(ex, ey), style = Stroke(3f))
        }
        if (e.has(StatusType.POISON)) {
            drawCircle(Color(0x33A3E635), drawR * 1.2f, Offset(ex, ey))
        }
        val tacticalRole = kind.tacticalRole()
        if (tacticalRole != EnemyTacticalRole.NONE) {
            val roleColor = Color(tacticalRole.color)
            val auraRadius = if (tacticalRole == EnemyTacticalRole.GUARD) drawR * 2.1f else drawR * 1.45f
            drawCircle(
                roleColor.copy(alpha = 0.18f + 0.08f * sin(t * 4f + e.x * 0.01f)),
                auraRadius,
                Offset(ex, ey),
                style = Stroke(if (tacticalRole == EnemyTacticalRole.GUARD) 3.5f else 2.5f)
            )
        }
        if (e.has(StatusType.RAGE)) {
            drawCircle(Color(0x66FB923C), drawR * 1.55f, Offset(ex, ey), style = Stroke(3f))
        }
        drawInkEnemy(ex, ey, drawR * (1f - sq * 0.2f), kind, e.hitFlash, bob, e.facing, e.elite, e.enraged)
        if (e.eliteTrait != EnemyEliteTrait.NONE) {
            val traitCenter = Offset(ex + drawR * 0.88f, ey - drawR * 0.82f)
            drawCircle(Color(0xDD0F172A), 14f, traitCenter)
            drawCircle(eliteColor, 13f, traitCenter, style = Stroke(2f))
            title(tm, e.eliteTrait.badge, traitCenter.x, traitCenter.y - 7f, eliteColor, 9.sp)
        }
        if (tacticalRole != EnemyTacticalRole.NONE) {
            val roleEmblem = when (tacticalRole) {
                EnemyTacticalRole.HEALER -> StatusEmblem.HEALER
                EnemyTacticalRole.DRUMMER -> StatusEmblem.DRUMMER
                EnemyTacticalRole.GUARD -> StatusEmblem.GUARD
                EnemyTacticalRole.NONE -> StatusEmblem.VULN
            }
            drawStatusEmblem(ex - drawR * 0.92f, ey - drawR * 0.78f, 12f, roleEmblem, t)
        }
        if (sq > 0.4f) {
            drawCircle(Color.White.copy(alpha = sq * 0.3f), drawR * (1.15f + sq), Offset(ex, ey + drawR * 0.55f), style = Stroke(2.5f))
        }
        // 状态徽记（图形化，中日文通用）
        var tagY = ey - drawR * 1.75f
        if (e.has(StatusType.FREEZE)) {
            drawStatusEmblem(ex, tagY, 9f, StatusEmblem.FREEZE, t); tagY -= 22f
        }
        if (e.has(StatusType.POISON)) {
            drawStatusEmblem(ex, tagY, 9f, StatusEmblem.POISON, t); tagY -= 22f
        }
        if (e.has(StatusType.BURN)) {
            drawStatusEmblem(ex, tagY, 9f, StatusEmblem.BURN, t); tagY -= 22f
        }
        if (e.has(StatusType.VULN)) {
            drawStatusEmblem(ex, tagY, 9f, StatusEmblem.VULN, t)
        }
        // 五行标签
        title(tm, e.element.short, ex + drawR * 0.9f, ey - drawR * 0.2f, e.element.color, 10.sp)
        var sx = ex - drawR
        if (e.has(StatusType.BURN)) {
            drawStatusEmblem(sx + 8f, ey - drawR - 12f, 7f, StatusEmblem.BURN, t); sx += 18f
        }
        if (e.has(StatusType.POISON)) {
            drawStatusEmblem(sx + 8f, ey - drawR - 12f, 7f, StatusEmblem.POISON, t); sx += 18f
        }
        if (e.has(StatusType.SLOW) || e.has(StatusType.FREEZE)) {
            drawStatusEmblem(sx + 8f, ey - drawR - 12f, 7f, StatusEmblem.SLOW, t); sx += 18f
        }
        if (e.has(StatusType.VULN)) drawStatusEmblem(sx + 8f, ey - drawR - 12f, 7f, StatusEmblem.VULN, t)
        val bw = drawR * (if (e.elite) 3.2f else 2.5f)
        val barY = ey - drawR * 1.55f
        if (e.elite) {
            // 精英怪：加宽血条 + 金色描边 + 名牌
            drawRoundRect(Color(0xFFB45309), Offset(ex - bw / 2f - 2f, barY - 2f), Size(bw + 4f, 12f), CornerRadius(5f, 5f))
            drawRoundRect(Color(0xFF1C1410), Offset(ex - bw / 2f, barY), Size(bw, 8f), CornerRadius(4f, 4f))
            drawRoundRect(
                Color(0xFFEF4444),
                Offset(ex - bw / 2f, barY),
                Size(bw * (e.hp / e.maxHp).coerceIn(0f, 1f), 8f),
                CornerRadius(4f, 4f)
            )
            title(tm, "精英·${e.kind.displayName()}", ex, barY - 10f, Color(0xFFFBBF24), 8.sp)
        } else {
            drawRoundRect(Color(0xCC0F172A), Offset(ex - bw / 2f - 1f, barY - 1f), Size(bw + 2f, 9f), CornerRadius(4f, 4f))
            drawRoundRect(Color(0xFF334155), Offset(ex - bw / 2f, barY), Size(bw, 7f), CornerRadius(3f, 3f))
            drawRoundRect(
                Color(0xFFEF4444),
                Offset(ex - bw / 2f, barY),
                Size(bw * (e.hp / e.maxHp).coerceIn(0f, 1f), 7f),
                CornerRadius(3f, 3f)
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.25f),
                Offset(ex - bw / 2f, barY),
                Size(bw * (e.hp / e.maxHp).coerceIn(0f, 1f), 2.5f),
                CornerRadius(2f, 2f)
            )
        }
    }

    val p = sim.player
    // 人物位置稳定：几乎不位移，攻击感交给笔锋
    val lunge = sim.attackLunge
    val lungeX = cos(sim.slashAngle) * lunge * 2.2f
    val lungeY = sin(sim.slashAngle) * lunge * 2.2f
    val px = wx(p.x) + lungeX
    val py = wy(p.y) + lungeY
    // 攻击时压掉高频 bob，站得住
    val bob = when {
        sim.slashFx > 0.05f -> sin(t * 2f) * 0.4f
        kotlin.math.abs(p.vx) + kotlin.math.abs(p.vy) > 20f -> sin(t * 10f) * 1.6f
        else -> sin(t * 2.5f) * 0.8f
    }
    if (basicHeld) {
        val rr = meta.hero.attackRange * (sim.width / 1080f).coerceIn(0.85f, 2.2f) * 0.55f
        drawCircle(Color(0x221C1410), rr, Offset(px, py), style = Stroke(2f))
    }
    if (p.has(StatusType.SHIELD)) drawCircle(Color(0x66B45309), p.radius * 1.4f, Offset(px, py), style = Stroke(3f))
    if (sim.healPulse > 0f) drawCircle(Color(0x554D7C0F), p.radius * (1.45f + sim.healPulse), Offset(px, py), style = Stroke(2.5f))
    val invBlink = sim.playerInvuln > 0f && ((t * 18f).toInt() % 2 == 0)
    drawBattleArmorAura(px, py, wr(p.radius), meta.equippedArmor(), t)
    if (!invBlink) {
        drawShadowDisk(px, py, wr(p.radius))
        drawSetAura(px, py, wr(p.radius), meta.activeSet(), t)
        drawInkHero(px, py, wr(p.radius), p.facing, meta.skin(), p.hitFlash, bob, drawPlaceholderWeapon = false, hero = meta.hero)
    } else {
        drawShadowDisk(px, py, wr(p.radius) * 0.9f)
        drawCircle(Color(0x88F5EBD4), wr(p.radius) * 0.95f, Offset(px, py), style = Stroke(2.5f))
        drawSetAura(px, py, wr(p.radius), meta.activeSet(), t)
        drawInkHero(px, py, wr(p.radius), p.facing, meta.skin(), hitFlash = 1f, bob = bob, drawPlaceholderWeapon = false, hero = meta.hero)
    }
    drawBattleEquipmentForm(
        px, py, wr(p.radius), meta.hero,
        meta.equippedArmor(), meta.equippedRing(), meta.equippedBoots(),
        p.facing,
        moving = kotlin.math.abs(p.vx) + kotlin.math.abs(p.vy) > 20f,
        pulse = t
    )
    if (!invBlink) {
        val weaponSide = if (p.facing >= 0f) 1f else -1f
        val weaponScale = when (meta.hero) {
            HeroClass.WARRIOR -> 0.96f
            HeroClass.MAGE -> 0.78f
            HeroClass.TAOIST -> 0.80f
        }
        drawBattleWeaponArt(
            px + weaponSide * wr(p.radius) * 1.25f,
            py + wr(p.radius) * 0.02f,
            wr(p.radius) * weaponScale,
            meta.equippedWeapon(),
            p.facing,
            sim.slashFx
        )
    }
    // 笔锋斩（弧线+飞白），去掉金星扇形
    if (sim.slashFx > 0f) {
        val ang = sim.slashAngle
        val r = wr(meta.hero.attackRange * (sim.width / 1080f).coerceIn(0.85f, 2.2f) * sim.slashWidth)
        drawInkSlash(px, py, ang, r, sim.slashFx, sim.slashWidth)
    }

    // particles (impact sparks) — star-ish squares via circles
    for (pt in sim.particles) {
        val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
        val pr = wr(pt.r) * (0.6f + a * 0.7f)
        drawCircle(Color(pt.color).copy(alpha = a), pr, Offset(wx(pt.x), wy(pt.y)))
        if (a > 0.5f) {
            drawCircle(Color.White.copy(alpha = a * 0.5f), pr * 0.4f, Offset(wx(pt.x), wy(pt.y)))
        }
    }

    // fields (poison / array) — strong readable identity
    for (f in sim.fields) {
        val a = (f.life / f.maxLife).coerceIn(0.25f, 0.9f)
        val cx = wx(f.x)
        val cy = wy(f.y)
        val rr = wr(f.r)
        if (f.kind == 0 || f.kind == 3) {
            // poison mist: green fog blob
            drawCircle(Color(0xFFA3E635).copy(alpha = 0.22f * a), rr, Offset(cx, cy))
            drawCircle(Color(0xFF65A30D).copy(alpha = 0.35f * a), rr * 0.7f, Offset(cx, cy))
            drawCircle(Color(0xFFA3E635).copy(alpha = 0.7f * a), rr, Offset(cx, cy), style = Stroke(4f))
            if (f.kind == 3) {
                drawCircle(Color(0xFFECFCCB).copy(alpha = 0.65f * a), rr * 0.35f, Offset(cx, cy), style = Stroke(2f))
            }
            title(tm, if (f.kind == 3) "随身毒雾" else "毒雾", cx, cy - rr - 4f, Color(0xFFA3E635).copy(alpha = a), 12.sp)
        } else if (f.kind == 2) {
            drawCircle(Color(0xFFFF6B35).copy(alpha = 0.18f * a), rr, Offset(cx, cy))
            drawCircle(Color(0xFFB91C1C).copy(alpha = 0.28f * a), rr * 0.62f, Offset(cx, cy))
            drawCircle(Color(0xFFFFA94D).copy(alpha = 0.82f * a), rr, Offset(cx, cy), style = Stroke(3.5f))
            repeat(6) { index ->
                val angle = index * 1.047f + sim.time * 0.45f
                val flameX = cx + cos(angle) * rr * 0.52f
                val flameY = cy + sin(angle) * rr * 0.52f
                drawCircle(Color(0xFFFDE68A).copy(alpha = 0.55f * a), 3.5f + 2f * sin(sim.time * 5f + index), Offset(flameX, flameY))
            }
            title(tm, "余烬", cx, cy - rr - 4f, Color(0xFFFF6B35).copy(alpha = a), 11.sp)
        } else {
            // sage array: concentric + cross marks
            drawCircle(Color(0xFF4ADE80).copy(alpha = 0.16f * a), rr, Offset(cx, cy))
            for (k in 1..3) {
                drawCircle(
                    Color(0xFF86EFAC).copy(alpha = 0.55f * a),
                    rr * (k / 3.2f),
                    Offset(cx, cy),
                    style = Stroke(2.5f)
                )
            }
            drawLine(Color(0x884ADE80), Offset(cx - rr, cy), Offset(cx + rr, cy), 2f)
            drawLine(Color(0x884ADE80), Offset(cx, cy - rr), Offset(cx, cy + rr), 2f)
            drawCircle(Color(0xFF4ADE80).copy(alpha = 0.8f * a), rr, Offset(cx, cy), style = Stroke(4f))
            title(tm, "天师阵", cx, cy - rr - 6f, Color(0xFF4ADE80).copy(alpha = a), 13.sp)
            title(tm, "伤敌·回血", cx, cy + rr + 2f, Color(0xFF86EFAC).copy(alpha = a), 10.sp)
        }
    }
    for (s in sim.shots) {
        val sx = wx(s.x)
        val sy = wy(s.y)
        val px0 = wx(s.x - s.vx * 0.03f)
        val py0 = wy(s.y - s.vy * 0.03f)
        drawInkProjectile(sx, sy, px0, py0, wr(s.r) * 1.15f, s.style)
        // 武器五行色光晕：每把武器的基础弹带自身颜色
        if (s.tint != 0) {
            drawCircle(Color(s.tint).copy(alpha = 0.45f), wr(s.r) * 1.5f, Offset(sx, sy))
        }
        if (s.style == 6) {
            // 陨星：墨线从上方落下
            drawLine(Color(0x662C1810), Offset(sx, sy - wr(48f)), Offset(sx, sy), 3f, StrokeCap.Round)
        }
    }
    for (d in sim.drops) {
        val bounce = sin(t * 8f + d.x) * 3f
        val cx = wx(d.x)
        val cy = wy(d.y) + bounce
        when (d.kind) {
            1 -> {
                drawCircle(Color(0xFF4ADE80), 12f, Offset(cx, cy))
                drawCircle(Color(0xFFBBF7D0), 6f, Offset(cx - 2f, cy - 2f))
            }
            2 -> {
                drawCircle(Color(0x66F59E0B), 18f, Offset(cx, cy))
                drawCircle(Color(0xFFF59E0B), 14f, Offset(cx, cy))
                title(tm, "武", cx, cy - 7f, Color.White, 11.sp)
            }
            3 -> {
                drawCircle(Color(0x66A78BFA), 18f, Offset(cx, cy))
                drawCircle(Color(0xFFA78BFA), 14f, Offset(cx, cy))
                title(tm, "卷", cx, cy - 7f, Color.White, 11.sp)
            }
            4 -> {
                val el = ItemCatalog.byId(d.itemId)?.element
                val col = el?.color ?: Color(0xFFFDA4AF)
                drawCircle(col.copy(alpha = 0.4f), 17f, Offset(cx, cy))
                drawCircle(col, 13f, Offset(cx, cy))
                title(tm, el?.short ?: "果", cx, cy - 7f, Color.White, 11.sp)
            }
            5 -> {
                drawCircle(Color(0x6686EFAC), 17f, Offset(cx, cy))
                drawCircle(Color(0xFF4ADE80), 13f, Offset(cx, cy))
                title(tm, "甲", cx, cy - 7f, Color.White, 11.sp)
            }
            6 -> {
                drawCircle(Color(0x66F472B6), 17f, Offset(cx, cy))
                drawCircle(Color(0xFFF472B6), 13f, Offset(cx, cy))
                title(tm, "戒", cx, cy - 7f, Color.White, 11.sp)
            }
            7 -> {
                drawCircle(Color(0x6638BDF8), 17f, Offset(cx, cy))
                drawCircle(Color(0xFF38BDF8), 13f, Offset(cx, cy))
                title(tm, "鞋", cx, cy - 7f, Color.White, 11.sp)
            }
            else -> {
                drawCircle(Color(0xFFFACC15), 13f, Offset(cx, cy))
                drawCircle(Color(0xFFFDE68A), 7f, Offset(cx - 2f, cy - 2f))
            }
        }
    }
    for (f in sim.floats) {
        val a = (f.life / 0.95f).coerceIn(0f, 1f)
        val fs = (14f * f.scale).sp
        val layout = tm.measure(
            GameI18n.tr(f.text),
            TextStyle(Color(f.r / 255f, f.g / 255f, f.b / 255f, a), fontSize = fs, fontWeight = FontWeight.Bold)
        )
        drawText(layout, topLeft = Offset(wx(f.x) - layout.size.width / 2f, wy(f.y)))
    }
    // Every active skill gets a brief readable seal/burst; ultimates receive a larger cinematic beat.
    for (fx in sim.skillCastsFx) {
        val a = (fx.life / fx.maxLife).coerceIn(0f, 1f)
        val progress = 1f - a
        val cx = wx(fx.x); val cy = wy(fx.y)
        val base = wr(if (fx.ultimate) 86f else 54f)
        drawCircle(
            Brush.radialGradient(listOf(Color(fx.color).copy(alpha = a * 0.34f), Color.Transparent)),
            base * (0.8f + progress * 0.9f), Offset(cx, cy)
        )
        val rayCount = if (fx.ultimate) 12 else 8
        for (i in 0 until rayCount) {
            val angle = i * (6.28318f / rayCount) + progress * 0.8f
            val inner = base * 0.48f
            val outer = base * (0.8f + progress * 0.55f)
            drawLine(
                Color(fx.color).copy(alpha = a * 0.75f),
                Offset(cx + cos(angle) * inner, cy + sin(angle) * inner),
                Offset(cx + cos(angle) * outer, cy + sin(angle) * outer),
                if (fx.ultimate) 5f else 3f,
                StrokeCap.Round
            )
        }
        drawCircle(Color(fx.color).copy(alpha = a), base * (0.42f + progress * 0.35f), Offset(cx, cy), style = Stroke(if (fx.ultimate) 6f else 4f))
        title(tm, fx.glyph, cx, cy - if (fx.ultimate) 17f else 13f, Color.White.copy(alpha = a), if (fx.ultimate) 26.sp else 19.sp)
        title(tm, fx.name, cx, cy + base * 0.62f, Color(fx.color).copy(alpha = a), if (fx.ultimate) 15.sp else 11.sp)
    }
    // scene polish overlay
    drawArenaVignette(w, viewH)
    // 低血警告：HP<25% 时红色暗角脉动 + HP<15% 时加重
    val hpPct = if (sim.player.maxHp > 0f) sim.player.hp / sim.player.maxHp else 1f
    if (hpPct < 0.25f && !sim.finished) {
        val urgency = ((0.25f - hpPct) / 0.25f).coerceIn(0f, 1f)
        val heartbeat = 0.5f + 0.5f * sin(sim.time * (8f + urgency * 6f))
        val edgeAlpha = urgency * (0.12f + 0.14f * heartbeat)
        // 四边红色暗角
        drawRect(Brush.verticalGradient(listOf(Color(0x88EF4444).copy(alpha = edgeAlpha), Color.Transparent)), Offset(0f, 0f), Size(w, viewH * 0.12f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x88EF4444).copy(alpha = edgeAlpha)), startY = 0f, endY = 1f), Offset(0f, viewH * 0.88f), Size(w, viewH * 0.12f))
        drawRect(Brush.horizontalGradient(listOf(Color(0x88EF4444).copy(alpha = edgeAlpha), Color.Transparent)), Offset(0f, 0f), Size(w * 0.08f, viewH))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x88EF4444).copy(alpha = edgeAlpha)), startX = 0f, endX = 1f), Offset(w * 0.92f, 0f), Size(w * 0.08f, viewH))
    }
    // impact flash overlays (after world)
    if (sim.impactFlash > 0.02f) {
        drawRect(Color.White.copy(alpha = (sim.impactFlash * 0.22f).coerceIn(0f, 0.28f)), size = Size(w, viewH))
    }
    if (sim.player.hitFlash > 0.05f) {
        drawRect(Color(0x33EF4444).copy(alpha = (sim.player.hitFlash * 0.4f).coerceIn(0f, 0.32f)), size = Size(w, viewH))
    }

    // combo meter
    if (sim.comboCount >= 2) {
        val ct = comboTitle(sim.comboCount).ifBlank { "${sim.comboCount}连" }
        title(tm, ct, w * 0.5f, h * 0.18f, Color(0xFFFB923C), 20.sp)
    }
    if (sim.frenzyActive()) {
        title(tm, "墨气狂涌 x${sim.roomKills}", w * 0.5f, h * 0.235f, Color(0xFFF87171), 16.sp)
        drawCircle(Color(0x22EF4444), wr(p.radius) * 2.2f, Offset(px, py), style = Stroke(4f))
    }
    if (sim.isUltFree()) {
        title(tm, "必杀就绪！", w * 0.82f, h * 0.42f, Color(0xFFFBBF24), 13.sp)
    }
    if (sim.environment.active) {
        val envColor = Color(sim.environment.color)
        val chipW = w * 0.32f
        val chipH = h * 0.043f
        val chipX = (w - chipW) * 0.5f
        val chipY = h * 0.082f
        drawRoundRect(Color(0xB81C1410), Offset(chipX, chipY), Size(chipW, chipH), CornerRadius(8f))
        drawRoundRect(envColor.copy(alpha = 0.78f), Offset(chipX, chipY), Size(chipW, chipH), CornerRadius(8f), style = Stroke(1.4f))
        title(tm, sim.environment.title, w * 0.5f, chipY + h * 0.002f, envColor, 9.sp)
    }
    sim.roomTrial?.let { trial ->
        val trialY = h * 0.145f
        val accent = if (sim.finished && sim.trialSucceeded) Color(0xFF34D399) else Color(trial.accent)
        drawRoundRect(Color(0xB81C1410), Offset(w * 0.02f, trialY), Size(w * 0.46f, h * 0.055f), CornerRadius(8f))
        drawRoundRect(accent.copy(alpha = 0.75f), Offset(w * 0.02f, trialY), Size(w * 0.46f, h * 0.055f), CornerRadius(8f), style = Stroke(1.5f))
        title(tm, "试炼·${trial.title}", w * 0.13f, trialY + h * 0.007f, accent, 10.sp)
        title(tm, sim.trialProgressLine(), w * 0.35f, trialY + h * 0.007f, Color(0xFFF5EBD4), 9.sp)
    }
    if (sim.activeGearProc != GearProc.NONE) {
        val proc = sim.activeGearProc
        val procY = h * 0.145f
        val procX = w * 0.52f
        val procW = w * 0.46f
        val procH = h * 0.055f
        val color = Color(proc.fxColor())
        val pulseBoost = sim.gearProcPulse.coerceIn(0f, 1f)
        drawRoundRect(Color(0xB81C1410), Offset(procX, procY), Size(procW, procH), CornerRadius(8f))
        drawRoundRect(
            color.copy(alpha = 0.58f + pulseBoost * 0.38f),
            Offset(procX, procY), Size(procW, procH), CornerRadius(8f),
            style = Stroke(1.5f + pulseBoost * 2.5f)
        )
        if (sim.gearProcCooldownMax > 0f) {
            drawRoundRect(
                color.copy(alpha = 0.28f),
                Offset(procX + 3f, procY + procH - 7f),
                Size((procW - 6f) * sim.gearProcReadyFraction(), 4f),
                CornerRadius(2f)
            )
        }
        val sourceName = meta.activeSet()?.name ?: meta.equippedWeapon().name
        title(tm, "${proc.title} · ${sourceName.take(6)}", procX + procW * 0.30f, procY + h * 0.006f, color, 9.sp)
        val state = when {
            sim.gearProcCooldownMax <= 0f -> "常驻"
            sim.gearProcCooldown <= 0.01f -> "就绪"
            else -> String.format(Locale.ROOT, "%.1fs", sim.gearProcCooldown)
        }
        title(tm, state, procX + procW * 0.82f, procY + h * 0.006f, Color(0xFFF5EBD4), 9.sp)
    }
    // story combat banner
    if (meta.arenaBannerT > 0f && meta.arenaBanner.isNotEmpty()) {
        val a = (meta.arenaBannerT / 2.8f).coerceIn(0f, 1f)
        drawParchmentPanel(w * 0.18f, h * 0.18f, w * 0.64f, h * 0.09f, radius = 12f)
        title(tm, meta.arenaBanner, w * 0.5f, h * 0.20f, Color(0xFF2C1810).copy(alpha = a), 13.sp)
    }

    // HUD — 薄绢本：血/蓝/杀气 三笔 + 暂停
    val uCtrl = min(w, h)
    val controls = arenaControlLayout(w, h)
    val hudTop = h * 0.012f
    val hudH = h * 0.12f
    drawInkCombatHudFrame(6f, hudTop, w - 12f, hudH)
    drawRoundRect(Color(0xAA1C1410), Offset(10f, hudTop + 6f), Size(uCtrl * 0.11f, hudH * 0.55f), CornerRadius(8f))
    title(tm, if (meta.paused) "▶" else "Ⅱ", 10f + uCtrl * 0.055f, hudTop + hudH * 0.18f, Color(0xFFF5EBD4), 12.sp)
    val eqW = meta.equippedWeapon()
    title(
        tm,
        "波${sim.waveIndex + 1}/${sim.waveTotal} · ${eqW.name}" +
            if (sim.waveMod != WaveMod.NONE) " · 【${sim.waveMod.title}】" else "",
        w * 0.50f, hudTop + 4f, Color(0xFFE7C98A), 11.sp
    )
    if (meta.coreInkRanks.isNotEmpty()) {
        title(
            tm,
            "免疫核心 ${meta.coreInkRanks.entries.joinToString(" ") { "${it.key.glyph}${it.value}" }}",
            w * 0.50f, hudTop + 18f, Color(0xFFC4B5FD), 8.sp
        )
    }
    val barX = w * 0.14f
    val barW = w * 0.62f
    val hpY = hudTop + hudH * 0.32f
    drawInkBar(barX, hpY, barW, 12f, (p.hp / p.maxHp).coerceIn(0f, 1f), Color(0xFFB91C1C))
    title(tm, "${p.hp.toInt()}", barX + barW + w * 0.04f, hpY - 2f, Color(0xFFFECACA), 10.sp)
    val mpY = hpY + 16f
    drawInkBar(barX, mpY, barW, 10f, (sim.mp / sim.maxMp).coerceIn(0f, 1f), Color(0xFF1E3A5F))
    val ultY = mpY + 14f
    drawInkBar(barX, ultY, barW * 0.7f, 8f, (sim.ultCharge / 100f).coerceIn(0f, 1f), Color(0xFFB45309))
    title(tm, "金${sim.goldEarned}  药${meta.potions}", w * 0.88f, hudTop + 6f, Color(0xFFE7C98A), 10.sp)

    // potion seal
    val potCx = controls.potionX
    val potCy = controls.potionY
    val potR = uCtrl * 0.065f
    drawInkSkillSeal(potCx, potCy, potR, meta.potions > 0, false, Color(0xFF4D7C0F))
    title(tm, "药${meta.potions}", potCx, potCy - 8f, Color(0xFFF5EBD4), 12.sp)

    // mini HP over hero
    run {
        val bw = wr(p.radius) * 2.4f
        val bx = px - bw / 2f
        val by = py - wr(p.radius) * 1.7f
        drawRoundRect(Color(0xAA0F172A), Offset(bx, by), Size(bw, 8f), CornerRadius(3f))
        drawRoundRect(Color(0xFFEF4444), Offset(bx, by), Size(bw * (p.hp / p.maxHp).coerceIn(0f, 1f), 8f), CornerRadius(3f))
    }

    if (sim.waveAnnounce > 0f) {
        title(tm, "第 ${sim.waveIndex + 1} 波", w * 0.5f, viewH * 0.28f, Color(0xFFFBBF24).copy(alpha = sim.waveAnnounce.coerceIn(0f, 1f)), 28.sp)
    }
    if (sim.moveHint > 0f) {
        val a = (sim.moveHint / 3.5f).coerceIn(0f, 1f)
        title(tm, "左半屏拖动移动", w * 0.22f, h * 0.55f, Color.White.copy(alpha = a), 13.sp)
        title(tm, "移动中：右下攻击 · 右侧长按技能盘", w * 0.78f, h * 0.40f, Color.White.copy(alpha = a), 12.sp)
        title(tm, "技能盘滑到图标上松开=释放该技能", w * 0.78f, h * 0.34f, Color(0xFF86EFAC).copy(alpha = a), 12.sp)
    }

    // 水墨摇杆
    val ghostOx = if (joyActive) joyOx else w * 0.14f
    val ghostOy = if (joyActive) joyOy else h * 0.72f
    val joyR = uCtrl * 0.13f
    drawInkJoystick(ghostOx, ghostOy, joyR, joyActive, joyKnobX, joyKnobY)
    if (!joyActive) title(tm, "移", ghostOx, ghostOy + joyR + 2f, Color(0xFFD6BC9A), 11.sp)

    // 移动中技能盘：布局与实体按钮同构（缩放0.6），滑动选择
    if (wheelActive) {
        val sk = sim.skills
        val gr = uCtrl * 0.055f
        fun ghost(cx: Float, cy: Float, glyph: String, col: Color, sel: Boolean, locked: Boolean, lockLv: Int) {
            drawCircle(Color(0xB30F172A), gr + 5f, Offset(cx, cy))
            if (sel) drawCircle(col.copy(alpha = 0.35f), gr, Offset(cx, cy))
            drawCircle(if (locked) Color(0xFF57534E) else col, gr, Offset(cx, cy), style = Stroke(if (sel) 5f else 2.5f))
            title(tm, if (locked) "Lv$lockLv" else glyph, cx, cy - 8f, Color(0xFFF5EBD4), 13.sp)
        }
        ghost(wheelX, wheelY, sk[0].glyph, meta.hero.color, wheelSlotSel == 0, !sim.skillUnlocked(0), sk[0].unlockLevel)
        ghost(wheelX - w * 0.072f, wheelY + h * 0.048f, sk[1].glyph, Color(0xFFFB923C), wheelSlotSel == 1, !sim.skillUnlocked(1), sk[1].unlockLevel)
        ghost(wheelX - w * 0.120f, wheelY - h * 0.036f, sk[2].glyph, Color(0xFFA78BFA), wheelSlotSel == 2, !sim.skillUnlocked(2), sk[2].unlockLevel)
        ghost(wheelX - w * 0.084f, wheelY - h * 0.132f, sk[3].glyph, Color(0xFF2DD4BF), wheelSlotSel == 3, !sim.skillUnlocked(3), sk[3].unlockLevel)
        ghost(wheelX - w * 0.012f, wheelY - h * 0.204f, sk[4].glyph, Color(0xFFFBBF24), wheelSlotSel == 4, !sim.skillUnlocked(4), sk[4].unlockLevel)
    }

    fun skillBtn(
        cx: Float, cy: Float, r: Float, label: String, name: String,
        ready: Boolean, cd: Float, col: Color, held: Boolean,
        locked: Boolean = false, lockLv: Int = 1, tip: String = ""
    ) {
        if (locked) {
            drawInkSkillSeal(cx, cy, r, false, false, Color(0xFF57534E))
            title(tm, "Lv$lockLv", cx, cy - 8f, Color(0xFFD6BC9A), 11.sp)
            return
        }
        drawInkSkillSeal(cx, cy, r, ready, held, col)
        title(tm, label, cx, cy - 9f, Color(0xFFF5EBD4), 15.sp)
        when {
            !ready && cd > 0f -> title(tm, String.format(Locale.ROOT, "%.1f", cd), cx, cy + 8f, Color(0xFFE7C98A), 10.sp)
            else -> title(tm, name.take(2), cx, cy + r + 1f, Color(0xFFD6BC9A), 9.sp)
        }
    }
    val sk = sim.skills
    skillBtn(
        controls.attackX, controls.attackY, uCtrl * 0.10f, sk[0].glyph, sk[0].name,
        sim.skillReady(0), sim.skillCdLeft(0), meta.hero.color, basicHeld,
        locked = !sim.skillUnlocked(0), lockLv = sk[0].unlockLevel, tip = sk[0].tip.take(7)
    )
    skillBtn(
        controls.skill1X, controls.skill1Y, uCtrl * 0.072f, sk[1].glyph, sk[1].name,
        sim.skillReady(1), sim.skillCdLeft(1), Color(0xFFFB923C), s1Held,
        locked = !sim.skillUnlocked(1), lockLv = sk[1].unlockLevel, tip = sk[1].tip.take(8)
    )
    skillBtn(
        controls.skill2X, controls.skill2Y, uCtrl * 0.072f, sk[2].glyph, sk[2].name,
        sim.skillReady(2), sim.skillCdLeft(2), Color(0xFFA78BFA), s2Held,
        locked = !sim.skillUnlocked(2), lockLv = sk[2].unlockLevel, tip = sk[2].tip.take(8)
    )
    skillBtn(
        controls.skill3X, controls.skill3Y, uCtrl * 0.072f, sk[3].glyph, sk[3].name,
        sim.skillReady(3), sim.skillCdLeft(3), Color(0xFF2DD4BF), s3Held,
        locked = !sim.skillUnlocked(3), lockLv = sk[3].unlockLevel, tip = sk[3].tip.take(8)
    )
    val ultReady = sim.skillReady(4)
    val ultFree = sim.isUltFree()
    val ultLocked = !sim.skillUnlocked(4)
    skillBtn(
        controls.ultX, controls.ultY, uCtrl * 0.088f,
        sk[4].glyph,
        when {
            ultLocked -> sk[4].name
            ultFree -> "免费·${sk[4].name}"
            else -> "必杀·${sk[4].name}"
        },
        ultReady, sim.skillCdLeft(4),
        if (ultFree) Color(0xFFFDE047) else Color(0xFFFBBF24),
        s4Held,
        locked = ultLocked, lockLv = sk[4].unlockLevel, tip = sk[4].tip.take(9)
    )
    if (ultReady && !ultLocked) {
        drawCircle(
            Color(0x66FBBF24),
            uCtrl * 0.10f + if (ultFree) sin(t * 8f) * 4f else 0f,
            Offset(controls.ultX, controls.ultY),
            style = Stroke(if (ultFree) 5f else 3f)
        )
    }
    val we = meta.equippedWeapon()
    title(
        tm,
        "Lv${meta.level} ${we.classLabel()} ${we.name} ${we.statsCompact().take(14)}",
        w * 0.78f, h * 0.92f, we.element.color, 8.sp
    )

    if (meta.paused) {
        drawRect(Color(0xAA020617), size = Size(w, h))
        title(tm, "暂停", w * 0.5f, h * 0.30f, Color.White, 30.sp)
        title(tm, "种子 ${SeedCode.encode(meta.runSeed, meta.inkRank)}", w * 0.5f, h * 0.36f, Color(0xFF94A3B8), 11.sp)
        drawRoundRect(Color(0xFF22C55E), Offset(w * 0.2f, h * 0.42f), Size(w * 0.6f, h * 0.09f), CornerRadius(16f))
        title(tm, "继续战斗", w * 0.5f, h * 0.44f, Color.White, 17.sp)
        drawRoundRect(Color(0xFF2563EB), Offset(w * 0.2f, h * 0.54f), Size(w * 0.6f, h * 0.09f), CornerRadius(16f))
        title(tm, "保存并回标题", w * 0.5f, h * 0.56f, Color.White, 17.sp)
        val abandonArmed = SystemClock.uptimeMillis() - abandonArmedT in 1..3_000L
        drawRoundRect(
            if (abandonArmed) Color(0xFF7F1D1D) else Color(0xFFEF4444),
            Offset(w * 0.2f, h * 0.66f), Size(w * 0.6f, h * 0.09f), CornerRadius(16f)
        )
        title(tm, if (abandonArmed) "放弃本局？" else "放弃本局", w * 0.5f, h * 0.68f, Color.White, 17.sp)
    }
    if (sim.finished) {
        title(tm, if (sim.won) "胜利！" else "失败", w * 0.5f, viewH * 0.38f, if (sim.won) Color(0xFF4ADE80) else Color(0xFFEF4444), 34.sp)
        if (sim.won && sim.clearGrade.isNotEmpty()) {
            title(tm, "评价 ${sim.clearGrade}", w * 0.5f, viewH * 0.48f, Color(0xFFFBBF24), 26.sp)
            if (sim.clearBonusGold > 0) {
                title(tm, "+${sim.clearBonusGold}金  +${sim.clearBonusXp}经验", w * 0.5f, viewH * 0.56f, Color(0xFF86EFAC), 16.sp)
            }
        }
    }
}

private fun DrawScope.title(tm: TextMeasurer, text: String, x: Float, y: Float, color: Color, size: TextUnit) {
    val shown = GameI18n.tr(text)
    val layout = tm.measure(shown, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y))
}
