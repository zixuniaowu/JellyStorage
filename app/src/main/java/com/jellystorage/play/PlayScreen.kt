package com.jellystorage.play

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
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
import com.jellystorage.softbody.rememberHapticAudioManager
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class Screen {
    LOGIN, CHAR_SELECT, CREATE_CHAR, TITLE, HOW_TO, SETTINGS, CLASS_SELECT, STORY, MAP, ARENA, SHOP, GEAR, CODEX, EVENT, LEVEL_UP, STAGE_CLEAR, RESULT
}

private const val APP_VERSION = "1.7.3"

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
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
    val tm = rememberTextMeasurer()
    val context = LocalContext.current
    val activity = context.findActivity()
    val progress = remember { ProgressStore(context) }
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
    LaunchedEffect(Unit) { ads.ensureInit() }
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
                skillPowerBonus = meta.skillPowerBonus,
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
                mods = meta.combatMods()
            )
            arena = sim
            var prev = 0L
            var done = false
            while (screen == Screen.ARENA) {
                withFrameNanos { now ->
                    if (prev == 0L) {
                        prev = now
                        return@withFrameNanos
                    }
                    val dt = ((now - prev) / 1e9f).coerceIn(0f, 0.05f)
                    prev = now
                    if (!done && !meta.paused) {
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
                            meta.arenaBanner = StoryBook.combatTaunt(
                                node?.type ?: NodeType.MOB,
                                sim.waveIndex,
                                sim.waveTotal
                            ) ?: "新的一波。"
                            meta.arenaBannerT = 2.4f
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
                            if (sim.won) {
                                meta.gold += sim.goldEarned
                                meta.goldEarnedThisRun += sim.goldEarned
                                meta.roomsCleared++
                                meta.curHp = sim.player.hp
                                meta.curMp = sim.mp
                                // grant loot from fight (武器/残卷/果实)
                                val lootBits = mutableListOf<String>()
                                for (id in sim.lootedWeaponIds) {
                                    WeaponCatalog.byId(id)?.let { w ->
                                        if (meta.grantWeapon(w)) {
                                            lootBits.add("${w.classLabel()}${w.name}[${w.element.short}]")
                                            // 可装备且更高攻则自动装上
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
                                    ItemCatalog.byId(id)?.let { it ->
                                        meta.addBagItem(it.id, 1)
                                        lootBits.add(it.name)
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
                                val leveled = meta.addXp(sim.xpEarned)
                                val grade = sim.clearGrade.ifEmpty { "B" }
                                meta.toast = buildString {
                                    append("清场 $grade +${sim.goldEarned}金 +${sim.xpEarned}经验")
                                    if (lootBits.isNotEmpty()) append(" · 掉落 ${lootBits.joinToString()}")
                                }
                                meta.toastT = 2.8f
                                meta.arenaBanner = "评价 $grade！"
                                meta.arenaBannerT = 1.8f
                                // boss defeat monologue
                                if (node?.type == NodeType.BOSS) {
                                    meta.queueStory(
                                        StoryBook.bossDefeat(stage.id),
                                        if (leveled) "LEVEL_UP" else "MAP"
                                    )
                                    if (leveled) {
                                        meta.levelChoices = randomPassives(meta.passives, 3)
                                        meta.pendingMapAfterLevel = true
                                    }
                                    screen = Screen.STORY
                                } else if (leveled) {
                                    meta.levelChoices = randomPassives(meta.passives, 3)
                                    meta.pendingMapAfterLevel = true
                                    screen = Screen.LEVEL_UP
                                } else {
                                    screen = Screen.MAP
                                }
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
                    acc += dt
                    if (acc >= 0.033f) { // ~30fps UI
                        acc = 0f
                        frame = now.toFloat()
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // rebind when size ready — critical so hitboxes match real pixels
            .pointerInput(screen, arenaKey, size.width, size.height, createName, createHeroIdx, charTick, confirmKind, confirmPayload, gearScroll) {
                if (size.width <= 0 || size.height <= 0) return@pointerInput
                if (screen == Screen.LOGIN) return@pointerInput
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
                if (screen == Screen.ARENA) {
                    awaitPointerEventScope {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val u = min(w, h) // landscape-safe control scale
                        // RIGHT controls (thumb cluster)
                        // 5 skill cluster
                        val atkCx = w * 0.88f
                        val atkCy = h * 0.76f
                        val atkR = u * 0.10f
                        val s1Cx = w * 0.76f
                        val s1Cy = h * 0.84f
                        val s1R = u * 0.072f
                        val s2Cx = w * 0.68f
                        val s2Cy = h * 0.70f
                        val s2R = u * 0.072f
                        val s3Cx = w * 0.74f
                        val s3Cy = h * 0.54f
                        val s3R = u * 0.072f
                        val ultCx = w * 0.86f
                        val ultCy = h * 0.42f
                        val ultR = u * 0.088f
                        val potCx = w * 0.93f
                        val potCy = h * 0.28f
                        val potR = u * 0.07f
                        val maxJoy = u * 0.13f
                        var joyId: PointerId? = null
                        var joyOrigin = Offset(w * 0.14f, h * 0.72f)
                        var basicId: PointerId? = null
                        var s1Id: PointerId? = null
                        var s2Id: PointerId? = null
                        var s3Id: PointerId? = null
                        var s4Id: PointerId? = null
                        fun inC(p: Offset, cx: Float, cy: Float, r: Float): Boolean {
                            val dx = p.x - cx
                            val dy = p.y - cy
                            return dx * dx + dy * dy <= r * r
                        }
                        fun applyJoy(p: Offset) {
                            val dx = p.x - joyOrigin.x
                            val dy = p.y - joyOrigin.y
                            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                            val cl = min(len, maxJoy)
                            val nx = (dx / len) * (cl / maxJoy)
                            val ny = (dy / len) * (cl / maxJoy)
                            stickX = nx
                            stickY = ny
                            joyKnobX = joyOrigin.x + (dx / len) * cl
                            joyKnobY = joyOrigin.y + (dy / len) * cl
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            for (ch in event.changes) {
                                val p = ch.position
                                if (ch.changedToDown()) {
                                    // pause hitbox always
                                    if (p.y < h * 0.12f && p.x < w * 0.18f) {
                                        meta.paused = !meta.paused
                                        if (meta.paused) {
                                            basicHeld = false; s1Held = false; s2Held = false; s3Held = false; s4Held = false
                                            stickX = 0f; stickY = 0f; joyActive = false
                                        }
                                        ch.consume()
                                        continue
                                    }
                                    if (meta.paused) {
                                        if (p.y in h * 0.42f..h * 0.52f) meta.paused = false
                                        // 保存回标题 → 可继续冒险
                                        if (p.y in h * 0.54f..h * 0.64f) {
                                            meta.paused = false
                                            meta.curHp = arena?.player?.hp ?: meta.curHp
                                            meta.curMp = arena?.mp ?: meta.curMp
                                            progress.saveActiveRun(meta)
                                            screen = Screen.TITLE
                                            arena = null
                                        }
                                        // 放弃本局
                                        if (p.y in h * 0.66f..h * 0.76f) {
                                            meta.paused = false
                                            meta.fillResult(false)
                                            progress.recordRunEnd(
                                                false, meta.stageIndex, meta.level,
                                                meta.goldEarnedThisRun, meta.kills
                                            )
                                            screen = Screen.RESULT
                                            arena = null
                                        }
                                        ch.consume()
                                        continue
                                    }
                                    when {
                                        inC(p, potCx, potCy, potR * 1.3f) -> {
                                            val sim = arena
                                            if (sim != null && meta.potions > 0) {
                                                if (sim.tryUsePotion()) {
                                                    meta.potions--
                                                    meta.curHp = sim.player.hp
                                                    meta.toast = "用药 +${(sim.player.maxHp * 0.4f).toInt()} HP  剩${meta.potions}瓶"
                                                    meta.toastT = 1.5f
                                                } else {
                                                    meta.toast = if (meta.potions <= 0) "没有药水" else "生命已满"
                                                    meta.toastT = 1.1f
                                                }
                                            } else {
                                                meta.toast = "没有药水（地图商店可买）"
                                                meta.toastT = 1.3f
                                            }
                                            ch.consume()
                                        }
                                        inC(p, atkCx, atkCy, atkR * 1.25f) -> {
                                            basicId = ch.id; basicHeld = true; ch.consume()
                                        }
                                        inC(p, s1Cx, s1Cy, s1R * 1.25f) -> {
                                            s1Id = ch.id; s1Held = true; ch.consume()
                                        }
                                        inC(p, s2Cx, s2Cy, s2R * 1.25f) -> {
                                            s2Id = ch.id; s2Held = true; ch.consume()
                                        }
                                        inC(p, s3Cx, s3Cy, s3R * 1.25f) -> {
                                            s3Id = ch.id; s3Held = true; ch.consume()
                                        }
                                        inC(p, ultCx, ultCy, ultR * 1.25f) -> {
                                            s4Id = ch.id; s4Held = true; ch.consume()
                                        }
                                        // LEFT ~48%: floating joystick
                                        p.x < w * 0.48f -> {
                                            joyId = ch.id
                                            joyOrigin = p
                                            joyOx = p.x
                                            joyOy = p.y
                                            joyActive = true
                                            applyJoy(p)
                                            ch.consume()
                                        }
                                        // right empty area also attacks
                                        else -> {
                                            basicId = ch.id; basicHeld = true; ch.consume()
                                        }
                                    }
                                } else if (ch.pressed) {
                                    when (ch.id) {
                                        joyId -> {
                                            applyJoy(p)
                                            ch.consume()
                                        }
                                        basicId -> {
                                            basicHeld = true; ch.consume()
                                        }
                                        s1Id -> {
                                            s1Held = true; ch.consume()
                                        }
                                        s2Id -> {
                                            s2Held = true; ch.consume()
                                        }
                                        s3Id -> {
                                            s3Held = true; ch.consume()
                                        }
                                        s4Id -> {
                                            s4Held = true; ch.consume()
                                        }
                                    }
                                } else {
                                    if (ch.id == joyId) {
                                        joyId = null
                                        stickX = 0f
                                        stickY = 0f
                                        joyActive = false
                                        ch.consume()
                                    }
                                    if (ch.id == basicId) {
                                        basicId = null; basicHeld = false; ch.consume()
                                    }
                                    if (ch.id == s1Id) {
                                        s1Id = null; s1Held = false; ch.consume()
                                    }
                                    if (ch.id == s2Id) {
                                        s2Id = null; s2Held = false; ch.consume()
                                    }
                                    if (ch.id == s3Id) {
                                        s3Id = null; s3Held = false; ch.consume()
                                    }
                                    if (ch.id == s4Id) {
                                        s4Id = null; s4Held = false; ch.consume()
                                    }
                                }
                            }
                        }
                    }
                } else {
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
                            gearScroll = gearScroll
                        )
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frame
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        when (screen) {
            Screen.TITLE -> drawTitle(tm, w, h, meta.pulse, progress)
            Screen.HOW_TO -> drawHowTo(tm, w, h)
            Screen.SETTINGS -> drawSettings(tm, w, h, progress)
            Screen.CHAR_SELECT -> drawCharSelect(tm, w, h, progress, charTick, meta.pulse)
            Screen.CREATE_CHAR -> drawCreateChar(tm, w, h, createName, createHeroIdx, meta.pulse)
            Screen.CLASS_SELECT -> drawClassSelect(tm, w, h, meta.pulse)
            Screen.STORY -> drawStory(meta, tm, w, h)
            Screen.MAP, Screen.EVENT, Screen.STAGE_CLEAR ->
                drawMap(meta, screen, tm, w, h)
            Screen.SHOP -> drawShop(meta, tm, w, h)
            Screen.GEAR -> drawGear(meta, tm, w, h, gearScroll)
            Screen.CODEX -> drawCodex(meta, progress, tm, w, h)
            Screen.LEVEL_UP -> drawLevelUp(meta, tm, w, h)
            Screen.RESULT -> drawResult(meta, tm, w, h, progress)
            Screen.LOGIN -> drawRect(
                Brush.verticalGradient(listOf(Color(0xFF0B1020), Color(0xFF1E1B4B))),
                size = Size(w, h)
            )
            Screen.ARENA -> {
                val sim = arena
                if (sim != null) {
                    drawArena(
                        sim, meta, tm, w, h, stickX, stickY,
                        joyActive, joyOx, joyOy, joyKnobX, joyKnobY,
                        basicHeld, s1Held, s2Held, s3Held, s4Held
                    )
                } else drawRect(Color(0xFF0B1020), size = Size(w, h))
            }
        }
        if (confirmKind.isNotEmpty()) {
            drawConfirmLayer(tm, w, h, confirmKind)
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
                guestHint = "已生成：用户 $u  密码 $p（请截图保存）"
                loginMsg = ""
                screen = afterLoginScreen(progress)
            },
            onAutoFill = {
                loginUser = GuestIds.username()
                loginPass = GuestIds.password()
                loginIsRegister = true
                loginMsg = "已填入随机账号，点「注册进入」"
                guestHint = "用户 ${loginUser} / 密码 ${loginPass}"
            }
        )
    }
    }
}

/** 用当前选中角色直接开局（职业固定为创建时选择） */
private fun startRunWithActiveCharacter(
    meta: RunMeta,
    progress: ProgressStore,
    daily: Boolean = false,
    setScreen: (Screen) -> Unit
) {
    val ch = progress.activeCharacter()
    if (ch == null) {
        setScreen(Screen.CHAR_SELECT)
        return
    }
    progress.clearActiveRun()
    val rank = if (daily) progress.preferredInkRank.coerceAtLeast(1).coerceAtMost(progress.inkRankUnlocked.coerceAtLeast(1))
    else progress.preferredInkRank
    val seed = if (daily) dailyInkSeed() else null
    meta.resetRun(ch.hero, rank, forcedSeed = seed)
    meta.characterName = ch.name
    progress.recordRunStart()
    progress.saveActiveRun(meta)
    val storyCh = StoryBook.chapters[0]
    meta.queueStory(
        StoryBook.worldPremise + StoryBook.heroGreeting(ch.hero) + storyCh.intro,
        "MAP"
    )
    val tag = if (daily) "今日画题" else "出征"
    meta.addJournal("$tag · ${ch.name}（${ch.hero.displayName}） · 墨$rank · ${meta.affixHudLine()}")
    meta.toast = "$tag：${ch.name} · ${meta.affixHudLine()}"
    meta.toastT = 2.2f
    setScreen(Screen.STORY)
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
        "daily_run" -> "今日画题？" to "使用今日固定种子出征（全天相同）。\n会覆盖当前存档。词缀见标题左下。"
        "delete_char" -> "删除角色？" to "角色与其存档将永久删除。\n此操作不可撤销。"
        "exit_chapter" -> "进入下一章？" to "确认离开本章，前往下一章节。"
        "abandon" -> "放弃本局？" to "本局结束，进度写入生涯统计。\n可在标题重新出征。"
        else -> "确认？" to "请确认操作"
    }
    title(tm, titleText, w * 0.5f, h * 0.34f, Color(0xFFFDE68A), 20.sp)
    body.lines().forEachIndexed { i, line ->
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
    gearScroll: Float = 0f
) {
    when (screen) {
        Screen.TITLE -> {
            // 与 drawTitle 按钮区域严格对齐
            if (pos.x in w * 0.48f..w * 0.90f) {
                val hasSave = progress.hasActiveRun()
                val rows = if (hasSave) {
                    listOf(
                        h * 0.26f to h * 0.35f, // 继续
                        h * 0.36f to h * 0.45f, // 新冒险
                        h * 0.46f to h * 0.54f, // 角色
                        h * 0.55f to h * 0.63f, // 图鉴
                        h * 0.64f to h * 0.72f, // 说明
                        h * 0.73f to h * 0.82f  // 设置
                    )
                } else {
                    listOf(
                        h * 0.28f to h * 0.38f, // 开始
                        h * 0.40f to h * 0.49f, // 角色
                        h * 0.51f to h * 0.60f, // 图鉴
                        h * 0.62f to h * 0.71f, // 说明
                        h * 0.73f to h * 0.82f  // 设置
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
            // 点角色信息区切换墨阶（左半下方）
            if (pos.x in w * 0.50f..w * 0.92f && pos.y in h * 0.21f..h * 0.255f) {
                val next = (progress.preferredInkRank + 1)
                progress.preferredInkRank = if (next > progress.inkRankUnlocked) 0 else next
                meta.toast = "出征墨阶 → ${progress.preferredInkRank}（最高已解${progress.inkRankUnlocked}）"
                meta.toastT = 1.6f
            }
            // 今日画题：左下角色旁小钮（无存档或有存档均可，有存档需确认）
            if (pos.x in w * 0.06f..w * 0.40f && pos.y in h * 0.78f..h * 0.88f) {
                if (progress.hasActiveRun()) {
                    requestConfirm("daily_run", "")
                } else {
                    startRunWithActiveCharacter(meta, progress, daily = true, setScreen = setScreen)
                }
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
            if (pos.y in h * 0.80f..h * 0.90f && pos.x in w * 0.15f..w * 0.48f) {
                if (chars.size < 6) {
                    setCreateName(GameCharacter.autoName(chars.size))
                    setCreateHeroIdx(0)
                    setScreen(Screen.CREATE_CHAR)
                } else {
                    meta.toast = "最多6个角色"
                    meta.toastT = 1.4f
                }
            }
            if (pos.y in h * 0.80f..h * 0.90f && pos.x in w * 0.52f..w * 0.85f) {
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
            // 6 tabs: 武 甲 戒 鞋 套 技
            val tabW = w * 0.11f
            for (i in 0..5) {
                val x0 = w * 0.02f + i * (tabW + w * 0.01f)
                if (pos.y in h * 0.065f..h * 0.14f && pos.x in x0..(x0 + tabW)) {
                    meta.codexTab = i
                    meta.codexSelectedId = ""
                    return
                }
            }
            HeroClass.entries.forEachIndexed { i, _ ->
                val x0 = w * 0.70f + i * w * 0.09f
                if (pos.y in h * 0.065f..h * 0.14f && pos.x in x0..(x0 + w * 0.085f)) {
                    meta.codexHeroIndex = i
                    meta.codexSelectedId = ""
                    return
                }
            }
            val hero = HeroClass.entries[meta.codexHeroIndex.coerceIn(0, 2)]
            when (meta.codexTab) {
                0 -> {
                    val list = WeaponCatalog.all.filter { it.hero == hero || it.hero == null }
                        .sortedWith(compareBy({ it.tierLevel() }, { it.cost }))
                    list.take(8).forEachIndexed { i, wp ->
                        val y0 = h * 0.17f + i * h * 0.085f
                        if (pos.y in y0..(y0 + h * 0.08f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = wp.id
                            return
                        }
                    }
                }
                1 -> {
                    val list = ArmorCatalog.all.filter { it.hero == hero || it.hero == null }
                        .sortedWith(compareBy({ it.tier }, { it.cost }))
                    list.take(8).forEachIndexed { i, ar ->
                        val y0 = h * 0.17f + i * h * 0.085f
                        if (pos.y in y0..(y0 + h * 0.08f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = ar.id
                            return
                        }
                    }
                }
                2 -> {
                    RingCatalog.all.filter { it.canEquip(hero) }.take(8).forEachIndexed { i, r ->
                        val y0 = h * 0.17f + i * h * 0.085f
                        if (pos.y in y0..(y0 + h * 0.08f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = r.id
                            return
                        }
                    }
                }
                3 -> {
                    BootsCatalog.all.filter { it.canEquip(hero) }.take(8).forEachIndexed { i, b ->
                        val y0 = h * 0.17f + i * h * 0.085f
                        if (pos.y in y0..(y0 + h * 0.08f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = b.id
                            return
                        }
                    }
                }
                4 -> {
                    SetCatalog.forHero(hero).forEachIndexed { i, set ->
                        val y0 = h * 0.17f + i * h * 0.12f
                        if (pos.y in y0..(y0 + h * 0.11f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = set.id
                            return
                        }
                    }
                }
                5 -> {
                    skillsFor(hero).forEachIndexed { i, sk ->
                        val y0 = h * 0.17f + i * h * 0.12f
                        if (pos.y in y0..(y0 + h * 0.11f) && pos.x in w * 0.02f..w * 0.42f) {
                            meta.codexSelectedId = "skill_${sk.slot.name}"
                            return
                        }
                    }
                }
            }
            if (pos.y in h * 0.90f..h * 0.99f) setScreen(Screen.TITLE)
        }
        Screen.LOGIN -> Unit
        Screen.SETTINGS -> {
            if (pos.y in h * 0.40f..h * 0.48f) progress.soundOn = !progress.soundOn
            if (pos.y in h * 0.50f..h * 0.58f) progress.hapticsOn = !progress.hapticsOn
            if (pos.y in h * 0.66f..h * 0.74f) {
                progress.logoutLocal()
                setScreen(Screen.LOGIN)
                return
            }
            if (pos.y in h * 0.80f..h * 0.90f) setScreen(Screen.TITLE)
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
        else -> Unit
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
        NodeType.MOB, NodeType.ELITE, NodeType.BOSS -> goFight()
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
            meta.eventTitle = node.name
            meta.eventBody = enterBeats.joinToString("\n") { "${it.speaker}：${it.line}" } +
                "\n\n机关触发！可硬扛或试着巧解。"
            meta.eventChoices = listOf(
                "硬扛 (−${dmg.toInt()}HP)" to {
                    meta.curHp = (meta.curHp - dmg).coerceAtLeast(1f)
                    meta.addJournal("硬扛了${node.name}。")
                },
                "巧解（半伤，少金）" to {
                    meta.curHp = (meta.curHp - dmg * 0.5f).coerceAtLeast(1f)
                    val fee = 6 + meta.inkRank
                    if (meta.gold >= fee) {
                        meta.gold -= fee
                        meta.toast = "巧解成功 −$fee 金"
                    } else {
                        meta.toast = "钱不够，还是挨了半下"
                    }
                    meta.toastT = 1.5f
                    meta.addJournal("巧解${node.name}。")
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
                                meta.toast = err
                                meta.toastT = 1.4f
                            }
                        }
                        "crit" -> meta.passives.add(PassiveId.CRIT)
                        "lifesteal" -> if (meta.gold >= 20) {
                            meta.gold -= 20
                            meta.passives.add(PassiveId.LIFESTEAL)
                            meta.toast = "获得吸血天赋"
                            meta.toastT = 1.3f
                        } else {
                            meta.toast = "金币不足(需20)"
                            meta.toastT = 1.4f
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
                                meta.toast = "金币不足(需20)"
                                meta.toastT = 1.4f
                            }
                        }
                        "hp" -> meta.passives.add(PassiveId.HP_UP)
                        "armor" -> meta.passives.add(PassiveId.ARMOR)
                        "leave" -> Unit
                        else -> Unit
                    }
                    meta.addJournal(triple.second.take(28))
                    meta.toast = triple.second.take(36)
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

private fun DrawScope.drawTitle(tm: TextMeasurer, w: Float, h: Float, pulse: Float, progress: ProgressStore) {
    // 标题也走古画绢本底，和地图统一
    drawRect(Brush.verticalGradient(listOf(Color(0xFFF3E9D2), Color(0xFFE0D0B0), Color(0xFFC4A882))), size = Size(w, h))
    drawInkScrollBackdrop(
        StageDef(0, "", "", emptyList(), 0xFFF3E9D2, 0xFFC4A882, chapterIndex = 0),
        w, h, pulse, progress.preferredInkRank
    )
    val titleScale = min(w, h) * 0.075f
    val active = progress.activeCharacter()
    val skin = if (active != null) SkinCatalog.defaultFor(active.hero) else SkinCatalog.warriorDefault
    drawCuteHero(w * 0.22f, h * 0.48f + sin(pulse * 2f) * 4f, titleScale * 1.2f, 1f, skin)
    title(tm, "果冻勇者", w * 0.68f, h * 0.06f, Color(0xFF2C1810), 30.sp)
    title(tm, "水墨远征 · v$APP_VERSION", w * 0.68f, h * 0.13f, Color(0xFF5C4033), 11.sp)
    if (active != null) {
        title(tm, "角色 ${active.name} · ${active.hero.displayName}", w * 0.68f, h * 0.18f, Color(0xFFFDE68A), 13.sp)
        title(
            tm,
            "${active.summaryLine()} · 墨阶${progress.preferredInkRank}/${progress.inkRankUnlocked}（点标题墨阶可切换）",
            w * 0.68f, h * 0.225f, Color(0xFF7C2D12), 10.sp
        )
    } else {
        title(tm, "请先创建角色", w * 0.68f, h * 0.18f, Color(0xFFF87171), 13.sp)
    }
    fun menuBtn(y: Float, bh: Float, text: String, col: Color) {
        drawRoundRect(Color(0xEE3D2914), Offset(w * 0.48f, y), Size(w * 0.42f, bh), CornerRadius(12f))
        drawRoundRect(col, Offset(w * 0.48f, y), Size(w * 0.42f, bh), CornerRadius(12f), style = Stroke(2.2f))
        title(tm, text, w * 0.69f, y + bh * 0.28f, Color(0xFFF5EBD4), 15.sp)
    }
    val hasSave = progress.hasActiveRun()
    val (known, total) = progress.collectionProgress()
    if (hasSave) {
        menuBtn(h * 0.26f, h * 0.09f, "继续冒险", Color(0xFFFBBF24))
        title(tm, progress.activeRunSummary(), w * 0.69f, h * 0.335f, Color(0xFFFDE68A), 9.sp)
        menuBtn(h * 0.36f, h * 0.09f, "开始新冒险", Color(0xFF4ADE80))
        menuBtn(h * 0.46f, h * 0.08f, "切换角色", Color(0xFF38BDF8))
        menuBtn(h * 0.55f, h * 0.08f, "装备图鉴 $known/$total", Color(0xFFFBBF24))
        menuBtn(h * 0.64f, h * 0.08f, "操作说明", Color(0xFF60A5FA))
        menuBtn(h * 0.73f, h * 0.08f, "记录 / 设置", Color(0xFFA78BFA))
    } else {
        menuBtn(h * 0.28f, h * 0.10f, "开始冒险", Color(0xFF4ADE80))
        menuBtn(h * 0.40f, h * 0.09f, "切换角色", Color(0xFF38BDF8))
        menuBtn(h * 0.51f, h * 0.09f, "装备图鉴 $known/$total", Color(0xFFFBBF24))
        menuBtn(h * 0.62f, h * 0.09f, "操作说明", Color(0xFF60A5FA))
        menuBtn(h * 0.73f, h * 0.09f, "记录 / 设置", Color(0xFFA78BFA))
    }
    // 今日画题入口（固定日种子，可攀比）
    drawInkButton(w * 0.06f, h * 0.78f, w * 0.34f, h * 0.09f, Color(0xFF9F1239))
    title(tm, "今日画题", w * 0.23f, h * 0.795f, Color(0xFFF5EBD4), 13.sp)
    title(tm, dailyInkTitle(), w * 0.23f, h * 0.835f, Color(0xFFFECACA), 8.sp)
    title(
        tm,
        "账号 ${progress.localUsername()}  通关${progress.runsWon} 出征${progress.runsStarted} 击杀${progress.lifetimeKills}" +
            if (hasSave) " ·有存档" else "",
        w * 0.5f, h * 0.92f, Color(0xFF5C4033), 10.sp
    )
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
        drawCuteHero(w * 0.14f, y0 + h * 0.045f, h * 0.028f, 1f, SkinCatalog.defaultFor(c.hero), bob = sin(pulse + i) * 2f)
        title(tm, c.name, w * 0.32f, y0 + h * 0.012f, Color(0xFF2C1810), 14.sp)
        title(tm, c.summaryLine(), w * 0.32f, y0 + h * 0.048f, Color(0xFF5C4033), 10.sp)
        drawInkButton(w * 0.74f, y0 + h * 0.01f, w * 0.16f, h * 0.07f, Color(0xFFB91C1C), 10f)
        title(tm, "删除", w * 0.82f, y0 + h * 0.028f, Color(0xFFFECACA), 12.sp)
    }
    drawInkButton(w * 0.15f, h * 0.80f, w * 0.32f, h * 0.09f, Color(0xFF4D7C0F))
    title(tm, "创建新角色", w * 0.31f, h * 0.825f, Color(0xFFF5EBD4), 15.sp)
    drawInkButton(w * 0.52f, h * 0.80f, w * 0.32f, h * 0.09f, Color(0xFF78716C))
    title(tm, "进入标题", w * 0.68f, h * 0.825f, Color(0xFFF5EBD4), 15.sp)
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
        "1. 本机登录 → 创建角色 → 选墨阶出征",
        "2. 五章画卷：春山→秋壑→雪夜→墨海→奇峰",
        "3. 地图分叉看关型买克制装；发光点可走，详情报看底部",
        "4. 战斗：左摇杆 · 右技能；武/甲/戒/鞋四槽养成",
        "5. 通关抬升墨阶：敌人更强、掉落更丰，专为反复玩",
        "6. 每局种子不同：支线事件/数值微调，地图不全一样",
        "7. 五行：火克金·金克木·木克土·土克水·水克火",
        "8. 新冒险需确认；广告药本局限2次",
        "目标：落款五章 · 冲高墨阶 · 集齐套装流派"
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
    drawInkWoodBar(w * 0.2f, h * 0.008f, w * 0.6f, h * 0.05f, 10f)
    title(tm, "图鉴", w * 0.5f, h * 0.015f, Color(0xFFF5EBD4), 16.sp)
    title(tm, "亮色=已获得 · 灰暗=未获得  ·  收集 $known/$total", w * 0.5f, h * 0.055f, Color(0xFF5C4033), 10.sp)

    val tabs = listOf("武器", "防具", "戒指", "鞋子", "套装", "技能")
    val tabW = w * 0.11f
    tabs.forEachIndexed { i, t ->
        val x0 = w * 0.02f + i * (tabW + w * 0.01f)
        val on = meta.codexTab == i
        drawRoundRect(if (on) Color(0xFFD97706) else Color(0xFF1E293B), Offset(x0, h * 0.065f), Size(tabW, h * 0.06f), CornerRadius(8f))
        title(tm, t, x0 + tabW * 0.5f, h * 0.078f, Color.White, 11.sp)
    }
    HeroClass.entries.forEachIndexed { i, hc ->
        val x0 = w * 0.70f + i * w * 0.09f
        val on = meta.codexHeroIndex == i
        drawRoundRect(if (on) hc.color.copy(alpha = 0.55f) else Color(0xFF1E293B), Offset(x0, h * 0.065f), Size(w * 0.085f, h * 0.06f), CornerRadius(8f))
        title(tm, hc.displayName, x0 + w * 0.042f, h * 0.078f, if (on) Color.White else Color(0xFF94A3B8), 11.sp)
    }

    val hero = HeroClass.entries[meta.codexHeroIndex.coerceIn(0, 2)]
    val pulse = meta.pulse
    drawRoundRect(Color(0xCC0F172A), Offset(w * 0.02f, h * 0.14f), Size(w * 0.40f, h * 0.73f), CornerRadius(12f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xEE1E293B), Color(0xEE0F172A))),
        Offset(w * 0.44f, h * 0.14f), Size(w * 0.54f, h * 0.73f), CornerRadius(14f)
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
                .sortedWith(compareBy({ it.tierLevel() }, { it.cost }))
            val sel = meta.codexSelectedId.ifEmpty { list.firstOrNull()?.id.orEmpty() }
            list.take(8).forEachIndexed { i, wp ->
                val y0 = h * 0.16f + i * h * 0.085f
                val owned = progress.isWeaponKnown(wp.id)
                val on = wp.id == sel
                drawRoundRect(listBg(owned, on), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f))
                drawRoundRect(if (on) Color(0xFFFBBF24) else if (owned) wp.element.color else Color(0xFF475569), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f), style = Stroke(if (on) 3f else 1.2f))
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
            val list = ArmorCatalog.all.filter { it.hero == hero || it.hero == null }.sortedWith(compareBy({ it.tier }, { it.cost }))
            val sel = meta.codexSelectedId.ifEmpty { list.firstOrNull()?.id.orEmpty() }
            list.take(8).forEachIndexed { i, ar ->
                val y0 = h * 0.16f + i * h * 0.085f
                val owned = progress.isArmorKnown(ar.id)
                val on = ar.id == sel
                drawRoundRect(listBg(owned, on), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f))
                drawRoundRect(if (on) Color(0xFFFBBF24) else if (owned) Color(0xFF86EFAC) else Color(0xFF475569), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.078f), CornerRadius(10f), style = Stroke(if (on) 3f else 1.2f))
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
            val sel = meta.codexSelectedId.ifEmpty { list.firstOrNull()?.id.orEmpty() }
            list.take(8).forEachIndexed { i, r ->
                val y0 = h * 0.16f + i * h * 0.085f
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
            val sel = meta.codexSelectedId.ifEmpty { list.firstOrNull()?.id.orEmpty() }
            list.take(8).forEachIndexed { i, b ->
                val y0 = h * 0.16f + i * h * 0.085f
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
                val y0 = h * 0.16f + i * h * 0.12f
                val on = set.id == sel
                val anyOwned = set.weaponIds.any { progress.isWeaponKnown(it) } || set.armorIds.any { progress.isArmorKnown(it) }
                drawRoundRect(listBg(anyOwned, on), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.11f), CornerRadius(10f))
                drawRoundRect(if (on) Color(0xFFFBBF24) else hero.color, Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.11f), CornerRadius(10f), style = Stroke(if (on) 3f else 1.2f))
                title(tm, "T${set.tier} ${set.name}", w * 0.22f, y0 + h * 0.02f, Color(0xFFFDE68A), 12.sp)
                title(tm, set.bonusTitle, w * 0.22f, y0 + h * 0.055f, Color(0xFF86EFAC), 11.sp)
            }
            val set = SetCatalog.byId(sel) ?: sets.firstOrNull()
            if (set != null) {
                drawSetAura(previewCx, previewCy, previewS * 0.5f, set, pulse)
                drawCuteHero(previewCx, previewCy, previewS * 0.4f, 1f, SkinCatalog.defaultFor(hero), bob = sin(pulse * 3f) * 2f)
                title(tm, set.name, previewCx, h * 0.62f, Color(0xFFFDE68A), 15.sp)
                title(tm, "被动 ${set.bonusTitle}：${set.bonusTip}", previewCx, h * 0.68f, Color(0xFF86EFAC), 12.sp)
                title(tm, set.piecesLine().take(40), previewCx, h * 0.74f, Color(0xFF94A3B8), 10.sp)
                title(tm, "特效 ${set.proc.title}", previewCx, h * 0.79f, Color(0xFFFBBF24), 12.sp)
            }
        }
        5 -> {
            // 技能树
            val skills = skillsFor(hero)
            val selSlot = meta.codexSelectedId.removePrefix("skill_").ifEmpty { skills.first().slot.name }
            skills.forEachIndexed { i, sk ->
                val y0 = h * 0.16f + i * h * 0.12f
                val on = sk.slot.name == selSlot
                drawRoundRect(if (on) Color(0xFF1E3A5F) else Color(0xEE1E293B), Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.11f), CornerRadius(10f))
                drawRoundRect(if (on) Color(0xFFFBBF24) else hero.color, Offset(w * 0.03f, y0), Size(w * 0.38f, h * 0.11f), CornerRadius(10f), style = Stroke(if (on) 3f else 1.5f))
                drawCircle(hero.color.copy(alpha = 0.4f), h * 0.03f, Offset(w * 0.08f, y0 + h * 0.055f))
                title(tm, sk.glyph, w * 0.08f, y0 + h * 0.035f, Color.White, 16.sp)
                title(tm, "Lv${sk.unlockLevel} ${sk.name}", w * 0.22f, y0 + h * 0.02f, Color.White, 13.sp)
                title(tm, sk.tip.take(14), w * 0.22f, y0 + h * 0.055f, Color(0xFF94A3B8), 10.sp)
            }
            val sk = skills.find { it.slot.name == selSlot } ?: skills.first()
            // skill tree visual
            skills.forEachIndexed { i, s ->
                val sx = w * 0.55f + i * w * 0.08f
                val sy = h * 0.35f
                if (i < skills.lastIndex) drawLine(Color(0x66FBBF24), Offset(sx, sy), Offset(sx + w * 0.08f, sy), 3f)
                val on = s.slot.name == sk.slot.name
                drawCircle(if (on) hero.color else Color(0xFF334155), if (on) 22f else 16f, Offset(sx, sy))
                title(tm, s.glyph, sx, sy - 10f, Color.White, 14.sp)
                title(tm, "Lv${s.unlockLevel}", sx, sy + 18f, Color(0xFF94A3B8), 9.sp)
            }
            title(tm, sk.name, previewCx, h * 0.55f, Color.White, 18.sp)
            title(tm, "解锁等级 Lv${sk.unlockLevel} · 槽位 ${sk.slot.name}", previewCx, h * 0.60f, Color(0xFFFBBF24), 12.sp)
            title(tm, "冷却 ${sk.cd}s · 耗蓝 ${sk.mp.toInt()}", previewCx, h * 0.65f, Color(0xFF7DD3FC), 12.sp)
            title(tm, sk.tip, previewCx, h * 0.71f, Color(0xFFE2E8F0), 13.sp)
            title(tm, "战斗中达到对应等级自动学会", previewCx, h * 0.78f, Color(0xFF94A3B8), 11.sp)
        }
    }
    drawRoundRect(Color(0xFF334155), Offset(w * 0.3f, h * 0.91f), Size(w * 0.4f, h * 0.07f), CornerRadius(12f))
    title(tm, "返回标题", w * 0.5f, h * 0.925f, Color.White, 14.sp)
}

private fun DrawScope.drawSettings(tm: TextMeasurer, w: Float, h: Float, progress: ProgressStore) {
    drawInkPaperBackdrop(w, h, 0f, progress.preferredInkRank)
    drawInkWoodBar(w * 0.18f, h * 0.04f, w * 0.64f, h * 0.08f)
    title(tm, "记录 / 设置", w * 0.5f, h * 0.055f, Color(0xFFF5EBD4), 22.sp)
    drawParchmentPanel(w * 0.1f, h * 0.14f, w * 0.8f, h * 0.22f)
    title(tm, "出征 ${progress.runsStarted}  通关 ${progress.runsWon}  最高章 ${progress.bestStage}", w * 0.5f, h * 0.155f, Color(0xFF2C1810), 13.sp)
    title(tm, "累计金 ${progress.lifetimeGold}  击杀 ${progress.lifetimeKills}", w * 0.5f, h * 0.205f, Color(0xFFB45309), 13.sp)
    val ch = progress.activeCharacter()
    title(
        tm,
        "账号 ${progress.localUsername()}" + if (progress.isGuestAccount()) "（游客）" else "",
        w * 0.5f, h * 0.255f, Color(0xFF5C4033), 12.sp
    )
    if (progress.isGuestAccount()) {
        title(tm, "密码 ${progress.localPassword()}（本地，可截图）", w * 0.5f, h * 0.30f, Color(0xFF3F6212), 11.sp)
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
    title(tm, "广告：结算双倍金 · 广告药本局≤2 · 正式ID请自配", w * 0.5f, h * 0.595f, Color(0xFF78716C), 10.sp)
    title(tm, "崩溃报告已落盘(Play Vitals 自动采集) · v$APP_VERSION", w * 0.5f, h * 0.635f, Color(0xFF78716C), 9.sp)
    drawInkButton(w * 0.2f, h * 0.66f, w * 0.6f, h * 0.08f, Color(0xFFB91C1C))
    title(tm, "退出登录", w * 0.5f, h * 0.68f, Color(0xFFFECACA), 15.sp)
    drawInkButton(w * 0.2f, h * 0.80f, w * 0.6f, 56f, Color(0xFF78716C))
    title(tm, "返回标题", w * 0.5f, h * 0.815f, Color(0xFFF5EBD4), 16.sp)
}

private fun DrawScope.drawResult(meta: RunMeta, tm: TextMeasurer, w: Float, h: Float, progress: ProgressStore) {
    drawInkPaperBackdrop(w, h, meta.pulse, meta.stage().chapterIndex)
    drawInkWoodBar(w * 0.12f, h * 0.05f, w * 0.76f, h * 0.1f)
    val titleCol = if (meta.resultTitle.contains("成功")) Color(0xFFBBF7D0) else Color(0xFFFECACA)
    title(tm, meta.resultTitle, w * 0.5f, h * 0.07f, titleCol, 24.sp)
    drawParchmentPanel(w * 0.1f, h * 0.18f, w * 0.8f, h * 0.36f)
    meta.resultBody.lines().take(6).forEachIndexed { i, line ->
        val c = if (line.contains("已写入本地")) Color(0xFF3F6212) else Color(0xFF2C1810)
        title(tm, line, w * 0.5f, h * 0.20f + i * h * 0.045f, c, 13.sp)
    }
    title(tm, "生涯击杀 ${progress.lifetimeKills} · 通关 ${progress.runsWon} · 最佳章 ${progress.bestStage}", w * 0.5f, h * 0.56f, Color(0xFF5C4033), 12.sp)
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
    drawInkWoodBar(6f, h * 0.01f, w - 12f, h * 0.095f, 10f)
    val ch = StoryBook.chapters.getOrNull(stage.chapterIndex)
    val inkTag = if (meta.inkRank > 0) " 墨${meta.inkRank}" else ""
    title(tm, (ch?.title ?: stage.title) + inkTag, w * 0.22f, h * 0.025f, Color(0xFFF5EBD4), 14.sp)
    title(
        tm,
        "Lv${meta.level}  金${meta.gold}  药${meta.potions}  ${meta.curHp.toInt()}/${meta.maxHp().toInt()}",
        w * 0.22f, h * 0.055f, Color(0xFFE7C98A), 11.sp
    )
    val aff = meta.affixHudLine()
    if (aff.isNotEmpty()) title(tm, aff, w * 0.22f, h * 0.078f, Color(0xFFD6BC9A), 8.sp)
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
            drawInkPathStroke(a, b, active, visited)
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
        drawInkNodeIcon(c, r, n.type, el, isNext || isHere, pulseN)
        if (n.id in meta.visited && !isHere && !isNext) {
            drawCircle(Color(0xFF4D7C0F), 3f, Offset(c.x + r * 0.5f, c.y - r * 0.5f))
        }
    }
    // 不在图上写长名 —— 只圈你与可走点
    cur?.let {
        val c = nodeCenter(it, w, h)
        val r = (nodeRadii[it.id] ?: 16f) + 5f + 2f * pulseN
        drawCircle(Color(0xAAB91C1C), r, c, style = Stroke(2.2f))
        drawInkHero(c.x, c.y - 2f, 9f, 1f, meta.skin())
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
                else -> "·"
            }
            val rec = el?.let { "荐${it.beatenBy().short}" } ?: nodeRiskHint(n).take(6)
            title(tm, "$kind  $rec", x0 + btnW * 0.5f, btnTop + btnH * 0.55f, Color(0xFF5C4033), 11.sp)
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
        val bodyLines = meta.eventBody.replace('\n', '｜').split('｜').flatMap { line ->
            if (line.length <= 22) listOf(line) else {
                val mid = line.length / 2
                val sp = line.indexOf('，', mid - 4).takeIf { it > 0 }
                    ?: line.indexOf('。', mid - 4).takeIf { it > 0 } ?: mid
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
    title(tm, "升级! Lv${meta.level}", w * 0.5f, h * 0.10f, Color(0xFFF5EBD4), 26.sp)
    title(tm, "选择一项天赋", w * 0.5f, h * 0.20f, Color(0xFF5C4033), 14.sp)
    meta.levelChoices.forEachIndexed { i, p ->
        val y = h * 0.32f + i * h * 0.14f
        drawParchmentPanel(w * 0.1f, y, w * 0.8f, h * 0.12f, strokeCol = Color(0xAAB91C1C))
        title(tm, p.title, w * 0.5f, y + h * 0.025f, Color(0xFFB91C1C), 18.sp)
        title(tm, p.desc, w * 0.5f, y + h * 0.065f, Color(0xFF3F3F46), 13.sp)
    }
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
        title(tm, "$tag  ${o.name}", x + cellW * 0.5f, y + cellH * 0.18f, Color(0xFF2C1810), 13.sp)
        val price = when {
            o.owned -> "已拥有"
            !can -> "差${o.price - meta.gold}金"
            else -> "${o.price}金 · 点买"
        }
        title(tm, price, x + cellW * 0.5f, y + cellH * 0.55f, if (can && !o.owned) Color(0xFF3F6212) else Color(0xFF78716C), 11.sp)
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
        "武" to eq.name,
        "甲" to worn.name,
        "戒" to (ring?.name ?: "无"),
        "鞋" to boots.name
    )
    slots.forEachIndexed { i, (k, v) ->
        val x = w * 0.04f + i * w * 0.24f
        drawParchmentPanel(x, h * 0.11f, w * 0.22f, h * 0.12f, radius = 10f)
        title(tm, k, x + w * 0.11f, h * 0.125f, Color(0xFFB91C1C), 12.sp)
        title(tm, v.take(6), x + w * 0.11f, h * 0.165f, Color(0xFF2C1810), 12.sp)
    }
    drawInkHero(w * 0.88f, h * 0.17f, h * 0.045f, 1f, meta.skin(), bob = sin(pulse * 2f) * 2f)

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
        title(tm, if (row.on) "●$kind ${row.title}" else "$kind ${row.title}", w * 0.5f, y + rowH * 0.12f, Color(0xFF2C1810), 14.sp)
        title(tm, row.sub, w * 0.5f, y + rowH * 0.48f, Color(0xFF5C4033), 11.sp)
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
    s4Held: Boolean = false
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
        meta.stage().chapterIndex, w, viewH, camX, camY, sim.time,
        sim.width, sim.height,
        { x -> wx(x) }, { y -> wy(y) }, { r -> wr(r) }
    )

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

    // rings under characters
    for (r in sim.rings) {
        val a = (r.life / r.maxLife).coerceIn(0f, 1f)
        drawCircle(
            Color(r.color).copy(alpha = a * 0.7f),
            wr(r.r * (1.15f - a * 0.15f)),
            Offset(wx(r.x), wy(r.y)),
            style = Stroke(4f)
        )
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
        if (e.windup > 0f) drawCircle(Color(0x88FB923C), drawR * 1.55f, Offset(ex, ey), style = Stroke(4f))
        if (e.enraged) drawCircle(Color(0x55EF4444), drawR * 1.35f, Offset(ex, ey), style = Stroke(3f))
        if (e.elite) drawCircle(Color(0x66FBBF24), drawR * 1.25f, Offset(ex, ey), style = Stroke(2.5f))
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
        drawInkEnemy(ex, ey, drawR * (1f - sq * 0.2f), kind, e.hitFlash, bob, e.facing, e.elite, e.enraged)
        if (sq > 0.4f) {
            drawCircle(Color.White.copy(alpha = sq * 0.3f), drawR * (1.15f + sq), Offset(ex, ey + drawR * 0.55f), style = Stroke(2.5f))
        }
        // status labels (readable)
        var tagY = ey - drawR * 1.75f
        if (e.has(StatusType.FREEZE)) {
            title(tm, "冻", ex, tagY, Color(0xFF7DD3FC), 11.sp); tagY -= 14f
        }
        if (e.has(StatusType.POISON)) {
            title(tm, "毒", ex, tagY, Color(0xFFA3E635), 11.sp); tagY -= 14f
        }
        if (e.has(StatusType.BURN)) {
            title(tm, "燃", ex, tagY, Color(0xFFFF6B35), 11.sp); tagY -= 14f
        }
        if (e.has(StatusType.VULN)) {
            title(tm, "弱", ex, tagY, Color(0xFFF472B6), 11.sp)
        }
        // 五行标签
        title(tm, e.element.short, ex + drawR * 0.9f, ey - drawR * 0.2f, e.element.color, 10.sp)
        var sx = ex - drawR
        if (e.has(StatusType.BURN)) {
            drawCircle(Color(0xFFFF6B35), 6f, Offset(sx, ey - drawR - 10f)); sx += 14f
        }
        if (e.has(StatusType.POISON)) {
            drawCircle(Color(0xFFA3E635), 6f, Offset(sx, ey - drawR - 10f)); sx += 14f
        }
        if (e.has(StatusType.SLOW) || e.has(StatusType.FREEZE)) {
            drawCircle(Color(0xFF7DD3FC), 6f, Offset(sx, ey - drawR - 10f)); sx += 14f
        }
        if (e.has(StatusType.VULN)) drawCircle(Color(0xFFF472B6), 6f, Offset(sx, ey - drawR - 10f))
        val bw = drawR * 2.5f
        val barY = ey - drawR * 1.55f
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
    if (!invBlink) {
        drawShadowDisk(px, py, wr(p.radius))
        drawSetAura(px, py, wr(p.radius), meta.activeSet(), t)
        drawInkHero(px, py, wr(p.radius), p.facing, meta.skin(), p.hitFlash, bob)
    } else {
        drawShadowDisk(px, py, wr(p.radius) * 0.9f)
        drawCircle(Color(0x88F5EBD4), wr(p.radius) * 0.95f, Offset(px, py), style = Stroke(2.5f))
        drawSetAura(px, py, wr(p.radius), meta.activeSet(), t)
        drawInkHero(px, py, wr(p.radius), p.facing, meta.skin(), hitFlash = 1f, bob = bob)
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
        if (f.kind == 0) {
            // poison mist: green fog blob
            drawCircle(Color(0xFFA3E635).copy(alpha = 0.22f * a), rr, Offset(cx, cy))
            drawCircle(Color(0xFF65A30D).copy(alpha = 0.35f * a), rr * 0.7f, Offset(cx, cy))
            drawCircle(Color(0xFFA3E635).copy(alpha = 0.7f * a), rr, Offset(cx, cy), style = Stroke(4f))
            title(tm, "毒雾", cx, cy - rr - 4f, Color(0xFFA3E635).copy(alpha = a), 12.sp)
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
            f.text,
            TextStyle(Color(f.r / 255f, f.g / 255f, f.b / 255f, a), fontSize = fs, fontWeight = FontWeight.Bold)
        )
        drawText(layout, topLeft = Offset(wx(f.x) - layout.size.width / 2f, wy(f.y)))
    }
    // scene polish overlay
    drawArenaVignette(w, viewH)
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
    // story combat banner
    if (meta.arenaBannerT > 0f && meta.arenaBanner.isNotEmpty()) {
        val a = (meta.arenaBannerT / 2.8f).coerceIn(0f, 1f)
        drawParchmentPanel(w * 0.18f, h * 0.18f, w * 0.64f, h * 0.09f, radius = 12f)
        title(tm, meta.arenaBanner, w * 0.5f, h * 0.20f, Color(0xFF2C1810).copy(alpha = a), 13.sp)
    }

    // HUD — 薄绢本：血/蓝/杀气 三笔 + 暂停
    val uCtrl = min(w, h)
    val hudTop = h * 0.012f
    val hudH = h * 0.12f
    drawInkCombatHudFrame(6f, hudTop, w - 12f, hudH)
    drawRoundRect(Color(0xAA1C1410), Offset(10f, hudTop + 6f), Size(uCtrl * 0.11f, hudH * 0.55f), CornerRadius(8f))
    title(tm, if (meta.paused) "▶" else "Ⅱ", 10f + uCtrl * 0.055f, hudTop + hudH * 0.18f, Color(0xFFF5EBD4), 12.sp)
    val eqW = meta.equippedWeapon()
    title(
        tm,
        "波${sim.waveIndex + 1}/${sim.waveTotal} · ${eqW.name}",
        w * 0.50f, hudTop + 4f, Color(0xFFE7C98A), 11.sp
    )
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
    val potCx = w * 0.93f
    val potCy = h * 0.26f
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
        title(tm, "右下普攻 · 上技能必杀", w * 0.78f, h * 0.40f, Color.White.copy(alpha = a), 12.sp)
        title(tm, "右上「药」喝药水", w * 0.78f, h * 0.34f, Color(0xFF86EFAC).copy(alpha = a), 12.sp)
    }

    // 水墨摇杆
    val ghostOx = if (joyActive) joyOx else w * 0.14f
    val ghostOy = if (joyActive) joyOy else h * 0.72f
    val joyR = uCtrl * 0.13f
    drawInkJoystick(ghostOx, ghostOy, joyR, joyActive, joyKnobX, joyKnobY)
    if (!joyActive) title(tm, "移", ghostOx, ghostOy + joyR + 2f, Color(0xFFD6BC9A), 11.sp)

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
            !ready && cd > 0f -> title(tm, String.format("%.1f", cd), cx, cy + 8f, Color(0xFFE7C98A), 10.sp)
            else -> title(tm, name.take(2), cx, cy + r + 1f, Color(0xFFD6BC9A), 9.sp)
        }
    }
    val sk = sim.skills
    skillBtn(
        w * 0.88f, h * 0.76f, uCtrl * 0.10f, sk[0].glyph, sk[0].name,
        sim.skillReady(0), sim.skillCdLeft(0), meta.hero.color, basicHeld,
        locked = !sim.skillUnlocked(0), lockLv = sk[0].unlockLevel, tip = sk[0].tip.take(7)
    )
    skillBtn(
        w * 0.76f, h * 0.84f, uCtrl * 0.072f, sk[1].glyph, sk[1].name,
        sim.skillReady(1), sim.skillCdLeft(1), Color(0xFFFB923C), s1Held,
        locked = !sim.skillUnlocked(1), lockLv = sk[1].unlockLevel, tip = sk[1].tip.take(8)
    )
    skillBtn(
        w * 0.68f, h * 0.70f, uCtrl * 0.072f, sk[2].glyph, sk[2].name,
        sim.skillReady(2), sim.skillCdLeft(2), Color(0xFFA78BFA), s2Held,
        locked = !sim.skillUnlocked(2), lockLv = sk[2].unlockLevel, tip = sk[2].tip.take(8)
    )
    skillBtn(
        w * 0.74f, h * 0.54f, uCtrl * 0.072f, sk[3].glyph, sk[3].name,
        sim.skillReady(3), sim.skillCdLeft(3), Color(0xFF2DD4BF), s3Held,
        locked = !sim.skillUnlocked(3), lockLv = sk[3].unlockLevel, tip = sk[3].tip.take(8)
    )
    val ultReady = sim.skillReady(4)
    val ultFree = sim.isUltFree()
    val ultLocked = !sim.skillUnlocked(4)
    skillBtn(
        w * 0.86f, h * 0.42f, uCtrl * 0.088f,
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
            Offset(w * 0.86f, h * 0.42f),
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
        drawRoundRect(Color(0xFF22C55E), Offset(w * 0.2f, h * 0.42f), Size(w * 0.6f, h * 0.09f), CornerRadius(16f))
        title(tm, "继续战斗", w * 0.5f, h * 0.44f, Color.White, 17.sp)
        drawRoundRect(Color(0xFF2563EB), Offset(w * 0.2f, h * 0.54f), Size(w * 0.6f, h * 0.09f), CornerRadius(16f))
        title(tm, "保存并回标题", w * 0.5f, h * 0.56f, Color.White, 17.sp)
        drawRoundRect(Color(0xFFEF4444), Offset(w * 0.2f, h * 0.66f), Size(w * 0.6f, h * 0.09f), CornerRadius(16f))
        title(tm, "放弃本局", w * 0.5f, h * 0.68f, Color.White, 17.sp)
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
    val layout = tm.measure(text, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y))
}
