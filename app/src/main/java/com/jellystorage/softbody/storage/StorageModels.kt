package com.jellystorage.softbody.storage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.jellystorage.softbody.SoftBodyObject

enum class GamePhase {
    MENU, PLAYING, UPGRADE, GAME_OVER
}

enum class JellyKind(
    val label: String,
    val radius: Float,
    val points: Int,
    val spawnWeight: Float,
    val stiffness: Float,
    val damping: Float,
    val mass: Float,
    val sticky: Boolean = false
) {
    NORMAL("Jelly", 42f, 10, 1.0f, 4.5f, 1.9f, 1f),
    TINY("Bean", 26f, 14, 0.55f, 5.5f, 2.0f, 0.7f),
    STICKY("Goo", 46f, 16, 0.45f, 3.2f, 3.2f, 1.15f, sticky = true),
    HEAVY("Blob", 58f, 22, 0.28f, 3.8f, 2.4f, 1.8f),
    BOUNCY("Spring", 38f, 18, 0.35f, 7.5f, 0.9f, 0.85f),
}

enum class SlotKind(
    val label: String,
    val mouthHalfWidth: Float,
    val mouthHalfHeight: Float,
    val scoreMult: Float,
    val maxRadius: Float,
    val color: Color
) {
    WIDE("宽口箱", 78f, 36f, 1.0f, 70f, Color(0xFF4ECDC4)),
    ROUND("中罐", 52f, 48f, 1.35f, 50f, Color(0xFFFFA94D)),
    NARROW("窄槽", 34f, 56f, 1.85f, 34f, Color(0xFFA855F7)),
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    var size: Float,
    val color: Color,
    val gravity: Float = 280f,
    val shrink: Float = 2.2f
)

data class FloatText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Color,
    var life: Float = 0.9f,
    val maxLife: Float = 0.9f
)

data class StorageJelly(
    val id: Int,
    val kind: JellyKind,
    val colorIdx: Int,
    var body: SoftBodyObject,
    var packing: Boolean = false,
    var packT: Float = 0f,
    var packSlot: Int = -1,
    var grabScale: Float = 1f,
    var wobble: Float = 0f
)

data class StorageSlot(
    val kind: SlotKind,
    /** Mouth center in screen space; y is top of jar mouth. */
    var mouth: Offset,
    var filled: Int = 0,
    val capacity: Int = 99,
    /** 0..1 pulse when successful pack */
    var flash: Float = 0f
) {
    fun mouthRect(widen: Float, unit: Float): Rect {
        val hw = kind.mouthHalfWidth * widen * unit
        val hh = kind.mouthHalfHeight * unit
        return Rect(mouth.x - hw, mouth.y - hh * 0.35f, mouth.x + hw, mouth.y + hh)
    }

    fun accepts(radius: Float, widen: Float, unit: Float): Boolean {
        val limit = kind.maxRadius * widen * unit
        return radius <= limit * 1.12f
    }
}

data class UpgradeDef(
    val id: String,
    val title: String,
    val desc: String
)

data class RunMods(
    var magnet: Float = 0f,
    var throwPower: Float = 1f,
    var slotWiden: Float = 1f,
    var chaosCap: Int = 12,
    var scoreMult: Float = 1f,
    var spawnSlow: Float = 1f,
    var softCatch: Float = 0f
)

data class StorageState(
    var phase: GamePhase = GamePhase.MENU,
    var jellies: MutableList<StorageJelly> = mutableListOf(),
    var slots: MutableList<StorageSlot> = mutableListOf(),
    var particles: MutableList<Particle> = mutableListOf(),
    var floatTexts: MutableList<FloatText> = mutableListOf(),
    var wave: Int = 1,
    var packedThisWave: Int = 0,
    var waveGoal: Int = 8,
    var waveTimeLeft: Float = 55f,
    var waveTimeMax: Float = 55f,
    var score: Int = 0,
    var bestScore: Int = 0,
    var combo: Int = 0,
    var comboTimer: Float = 0f,
    var totalPacked: Int = 0,
    var grabId: Int = -1,
    var grabOffset: Offset = Offset.Zero,
    var lastPointer: Offset = Offset.Zero,
    var pointerVel: Offset = Offset.Zero,
    var spawnTimer: Float = 1.2f,
    var nextJellyId: Int = 1,
    var mods: RunMods = RunMods(),
    var upgradeChoices: List<UpgradeDef> = emptyList(),
    var shake: Float = 0f,
    var announce: String = "",
    var announceT: Float = 0f,
    var deskW: Float = 1080f,
    var deskH: Float = 1920f,
    var playY0: Float = 90f,
    var playY1: Float = 1400f,
    /** Scales design units (tuned for ~1080px width) to device pixels. */
    var unit: Float = 1f,
    var failedReason: String = "",
    /** 0 = grab jelly, 1 = drag to bin, 2 = packed once, 3 = done */
    var tutorialStep: Int = 0,
    var tutorialHint: String = "按住彩色果冻，拖到底部箱子里",
    var tutorialT: Float = 99f,
    var tutorialPauseSpawn: Boolean = true,
    var pulseT: Float = 0f
) {
    fun u(v: Float): Float = v * unit
}

val jellyPalette = listOf(
    Color(0xFFFF6B8A),
    Color(0xFF4ECDC4),
    Color(0xFF45B7D1),
    Color(0xFFFFA94D),
    Color(0xFFA855F7),
    Color(0xFF22C55E),
    Color(0xFFEF4444),
    Color(0xFFEAB308)
)

val jellyBodyPalette = listOf(
    Color(0xFFFFD0DA),
    Color(0xFFC8F5F0),
    Color(0xFFC8E8F5),
    Color(0xFFFFE0C0),
    Color(0xFFE0C8F5),
    Color(0xFFC8F5D0),
    Color(0xFFF5C8C8),
    Color(0xFFF5F0C8)
)

val ALL_UPGRADES = listOf(
    UpgradeDef("magnet", "磁力手套", "箱子会轻轻吸附近的果冻"),
    UpgradeDef("throw", "甩力强化", "甩出去的力量 +25%"),
    UpgradeDef("widen", "扩口改造", "所有箱子能装稍大一点的果冻"),
    UpgradeDef("chaos", "更大桌面", "溢出上限 +2"),
    UpgradeDef("score", "金牌标签", "装箱得分 +20%"),
    UpgradeDef("calm", "慢速班次", "新果冻出现更慢 15%"),
    UpgradeDef("catch", "软着陆", "更容易塞进箱子"),
    UpgradeDef("time", "加时", "下一单倒计时 +12 秒")
)
