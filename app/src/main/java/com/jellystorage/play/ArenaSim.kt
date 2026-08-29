package com.jellystorage.play

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Status(
    val type: StatusType,
    var t: Float,
    var power: Float = 0f
)

data class Actor(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var hp: Float,
    var maxHp: Float,
    var radius: Float,
    var facing: Float = 1f,
    var attackCd: Float = 0f,
    var hitFlash: Float = 0f,
    var atk: Float,
    var speed: Float,
    val isPlayer: Boolean,
    var dead: Boolean = false,
    var kind: EnemyKind = EnemyKind.SLIME,
    var element: WuXing = WuXing.WOOD,
    var ai: EnemyAi = EnemyAi.CHASE,
    val statuses: MutableList<Status> = mutableListOf(),
    var windup: Float = 0f,
    var chargeVx: Float = 0f,
    var chargeVy: Float = 0f,
    /** 0..1 hit squash (wide/short) for impact feel */
    var squash: Float = 0f,
    /** brief stun where AI freezes */
    var hitStun: Float = 0f,
    /** low-HP frenzy */
    var enraged: Boolean = false,
    var elite: Boolean = false,
    /** reflect fraction of contact damage to player attacker (spike slime) */
    var thorns: Float = 0f,
    /** 0..0.5 damage reduction on incoming hits */
    var armor: Float = 0f,
    /** Boss 阶段 0=初 / 1=半 / 2=绝 */
    var bossPhase: Int = 0,
    /** Chapter boss signature pattern cursor. */
    var bossPatternStep: Int = 0,
    /** Healer/drummer support action timer; unused by non-support actors. */
    var supportCd: Float = 2.8f,
    /** 精英变体：改变轮廓光和核心属性。 */
    var eliteTrait: EnemyEliteTrait = EnemyEliteTrait.NONE,
    /** 种类专属攻击的冷却、预警与锁定点。 */
    var specialCd: Float = 2.5f,
    var specialWindup: Float = 0f,
    var specialAttack: EnemySignatureAttack = EnemySignatureAttack.NONE,
    var specialAimX: Float = 0f,
    var specialAimY: Float = 0f
) {
    fun has(st: StatusType): Boolean = statuses.any { it.type == st && it.t > 0f }
    fun powerOf(st: StatusType): Float = statuses.firstOrNull { it.type == st && it.t > 0f }?.power ?: 0f
    fun applyStatus(st: StatusType, duration: Float, power: Float = 0f) {
        val exist = statuses.firstOrNull { it.type == st }
        if (exist != null) {
            exist.t = max(exist.t, duration)
            exist.power = max(exist.power, power)
        } else statuses.add(Status(st, duration, power))
    }
}

data class Shot(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var r: Float,
    var dmg: Float,
    var fromPlayer: Boolean,
    var style: Int, // 1 fire 2 spirit 3 poison 4 ice 5 enemy 6 meteor
    var status: StatusType? = null,
    var statusT: Float = 0f,
    var statusPow: Float = 0f,
    /** fireball splash radius (world units) */
    var splash: Float = 0f,
    /** 武器五行色覆盖（0 = 按弹种默认色，ARGB int） */
    var tint: Int = 0
)

/** Persistent field: poison mist / taoist array */
data class FieldFx(
    var x: Float,
    var y: Float,
    var r: Float,
    var life: Float,
    var maxLife: Float,
    var dps: Float,
    var healPerSec: Float,
    var color: Long,
    var kind: Int = 0 // 0 poison 1 array 2 cinder/fire trail 3 following poison

)

/** kind: 0 gold, 1 heal, 2 weapon, 3 skill tome, 4 fruit/item, 5 armor, 6 ring, 7 boots */
data class Drop(
    var x: Float,
    var y: Float,
    var gold: Int,
    var life: Float = 14f,
    var kind: Int = 0,
    var itemId: String = ""
)
data class FloatTxt(var x: Float, var y: Float, var text: String, var life: Float, var r: Int, var g: Int, var b: Int, var scale: Float = 1f)
data class RingFx(var x: Float, var y: Float, var r: Float, var life: Float, var maxLife: Float, var color: Long)

/** 墨锋碎片：定向飞散的锥形笔触（冰晶/火星/符纸屑/治愈灵粒共用） */
data class ShardFx(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val angle: Float,
    val len: Float,
    var life: Float, val maxLife: Float,
    val color: Long,
    val width: Float
)

/** 笔锋斩弧：挥砍/旋风的剪影弧线，随生命淡出并收弧（绘制为粗圆头弧） */
data class SlashArcFx(
    val x: Float, val y: Float,
    val r: Float,
    val startAngle: Float,
    val sweep: Float,
    var life: Float, val maxLife: Float,
    val color: Long,
    val width: Float,
    val spin: Float
)
data class SkillCastFx(
    var x: Float,
    var y: Float,
    val name: String,
    val glyph: String,
    var life: Float,
    val maxLife: Float,
    val color: Long,
    val ultimate: Boolean = false
)
data class BoltFx(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    var life: Float,
    val maxLife: Float,
    val color: Long
)
data class EchoPulseFx(
    var x: Float,
    var y: Float,
    var life: Float,
    val maxLife: Float,
    val radius: Float,
    val damage: Float,
    val color: Long,
    val glyph: String,
    val label: String,
    val status: StatusType? = null,
    val statusDuration: Float = 0f,
    val statusPower: Float = 0f
)
/** Impact spark / debris particle */
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var maxLife: Float,
    var r: Float,
    var color: Long,
    var gravity: Float = 0f
)

/**
 * 场上墨迹残留（世界坐标）。
 * kind: 0=笔锋线 1=墨晕 2=飞白点
 * 战斗中缓慢淡；清场后 settle 成一幅写意，几乎不消。
 */
data class InkMark(
    var x0: Float,
    var y0: Float,
    var x1: Float,
    var y1: Float,
    var thick: Float,
    var life: Float,
    var maxLife: Float,
    var kind: Int = 0,
    var color: Long = 0xFF1C1410,
    var r: Float = 0f
)

/** 平滑的房间难度曲线：章节与清房数共同提高强度，后期不会突然翻倍。 */
fun arenaThreatMultiplier(threatLevel: Int, wave: Int = 0): Float {
    val threat = threatLevel.coerceIn(0, 32).toFloat()
    val room = 1f + threat * 0.055f + threat * threat * 0.0015f
    return room * (1f + wave.coerceAtLeast(0) * 0.08f)
}

fun eliteSpawnChance(threatLevel: Int): Float =
    (0.07f + threatLevel.coerceAtLeast(0) * 0.012f).coerceAtMost(0.38f)

fun enemySpeedThreatMultiplier(threatLevel: Int): Float =
    (1f + threatLevel.coerceAtLeast(0) * 0.012f).coerceAtMost(1.25f)

/** 装备品质随章节推进；第一章不会直接掉终局史诗。 */
fun maxGearRarityForThreat(threatLevel: Int): Int = when {
    threatLevel < 5 -> 0
    threatLevel < 15 -> 1
    else -> 2
}

fun maxGearTierForThreat(threatLevel: Int): Int =
    (1 + threatLevel.coerceAtLeast(0) / 5).coerceIn(1, 5)

/**
 * Deep arena: multi-skill, statuses, multi-wave, enemy AI variants.
 */
class ArenaSim(
    val width: Float,
    val height: Float,
    val hero: HeroClass,
    weaponLevel: Int,
    armorLevel: Int,
    val passives: Set<PassiveId>,
    startHp: Float,
    startMp: Float,
    waves: List<WaveDef>,
    val goldPerKill: Int,
    /** 0=ch1 intro … higher = tougher global scale */
    val threatLevel: Int = 1,
    /** run level — gates S1 / ultimate */
    val heroLevel: Int = 1,
    val playerElement: WuXing = WuXing.METAL,
    val gearAtkBonus: Float = 0f,
    val skillPowerBonus: Float = 0f,
    /** 土之果等：额外减伤 0~0.3 */
    val extraDr: Float = 0f,
    /** 火之果：普攻/技能附带点燃 */
    val fruitBurn: Boolean = false,
    /** 当前装备完整面板（职业/属性/特效） */
    val gear: GearWeapon = GearWeapon("none", "无", WuXing.METAL, 0f, 0),
    /** 防具/套装附加 */
    val extraArmorHp: Float = 0f,
    val extraMp: Float = 0f,
    val setDr: Float = 0f,
    val setCrit: Float = 0f,
    val setLifeSteal: Float = 0f,
    val setCdr: Float = 0f,
    val setSkillAmp: Float = 0f,
    /** 鞋子等附加移速 */
    val extraSpd: Float = 0f,
    val overrideProc: GearProc = GearProc.NONE,
    val overrideProcPower: Float = 0f,
    /** 墨阶词缀等局内修正 */
    val mods: CombatMods = CombatMods.NONE,
    /** 本战明示挑战：改变局部规则并提供额外奖励 */
    val roomTrial: RoomTrial? = null,
    /** Boss 后获得的技能机制改造及阶级。 */
    val coreInkRanks: Map<CoreInkId, Int> = emptyMap(),
    /** 仅章节终点传入；把复用的 Boss 身体映射为五种真正不同的首领战。 */
    val bossEncounter: BossEncounter? = null,
    /** 当前战斗房的场地身份、构型和周期环境技。 */
    val environment: ArenaEnvironment = ArenaEnvironment.NONE
) {
    private val upgrades = weaponUpgradeTable(hero)
    private val wLevel = weaponLevel.coerceIn(0, upgrades.lastIndex)
    private val armors = armorUpgradeTable()
    private val aLevel = armorLevel.coerceIn(0, armors.lastIndex)
    // gearAtkBonus 已含武器/锻造/饰品，勿再叠锻造
    private val atkBonus = gearAtkBonus
    private val armorHp = armors[aLevel].hpBonus + gear.hpBonus + extraArmorHp
    private val armorDr = armors[aLevel].dr + gear.drBonus + setDr
    private val skillMul = 1f + skillPowerBonus + gear.skillAmp + setSkillAmp
    private val combatProc: GearProc =
        if (overrideProc != GearProc.NONE) overrideProc else gear.proc
    private val combatProcPower: Float =
        if (overrideProc != GearProc.NONE) overrideProcPower else gear.procPower
    private fun coreRank(id: CoreInkId): Int = coreInkRanks.rankOf(id)

    val skills = skillsFor(hero)
    val player: Actor
    val enemies = ArrayList<Actor>(24)
    val shots = ArrayList<Shot>(96)
    val drops = ArrayList<Drop>(24)
    val floats = ArrayList<FloatTxt>(64)
    val rings = ArrayList<RingFx>(24)
    val skillCastsFx = ArrayList<SkillCastFx>(12)
    val bolts = ArrayList<BoltFx>(16)
    val shards = ArrayList<ShardFx>(48)
    val slashArcs = ArrayList<SlashArcFx>(12)
    val echoPulses = ArrayList<EchoPulseFx>(12)
    val bossHazards = ArrayList<BossHazard>(24)
    val particles = ArrayList<Particle>(128)
    val fields = ArrayList<FieldFx>(8)
    /** 墨迹层：攻击留下，清场可落款成画 */
    val inkMarks = ArrayList<InkMark>(160)
    /** 清场后墨迹定格为写意山水 */
    var inkSettled: Boolean = false
        private set
    var inkSettleT: Float = 0f
        private set
    /** 墨马必杀：剩余时间，>0 时全屏奔马演出 */
    var inkHorseT: Float = 0f
        private set
    val inkHorseMax: Float = 1.55f
    /** 0=向右 1=向左 */
    var inkHorseDir: Float = 1f
        private set
    private var inkHorseHitWave: Int = -1

    var mp: Float
        private set
    val maxMp: Float
    var goldEarned: Int = 0
        private set
    var xpEarned: Int = 0
        private set
    var finished: Boolean = false
        private set
    var won: Boolean = false
        private set
    var slashFx: Float = 0f
    var slashAngle: Float = 0f
    var slashWidth: Float = 1f
    /** 战士普攻三连击拍：第 3 刀为重斩（更宽弧 + 击退） */
    private var slashSwing = 0
    /** 法师魔法盾剩余时间：盾期间受击迟缓来敌 */
    var manaShieldT: Float = 0f
        private set
    var healPulse: Float = 0f
    var playerInvuln: Float = 0f
        private set
    var time: Float = 0f
        private set
    var waveIndex: Int = 0
        private set
    val waveTotal: Int
    var waveAnnounce: Float = 1.2f
    var shake: Float = 0f
        private set
    var hitStop: Float = 0f
        private set
    var lastHitX: Float = 0f
        private set
    var lastHitY: Float = 0f
        private set
    var moveHint: Float = 3.5f
    /** brief breather before next wave spawns */
    var waveGap: Float = 0f
        private set
    /** 0=none 1=light hit 2=crit 3=kill 4=player hurt — consumed by UI for haptics */
    var hapticEvent: Int = 0
    var attackLunge: Float = 0f
        private set
    var comboCount: Int = 0
        private set
    private var comboTimer: Float = 0f
    /** camera zoom punch 0..1 */
    var zoomPunch: Float = 0f
        private set
    /** full-screen white flash 0..1 */
    var impactFlash: Float = 0f
        private set
    /** time-scale residual after hitstop for slow-mo tail */
    var slowMo: Float = 0f
        private set
    /** 0..100 ultimate charge — full = free S2 once */
    var ultCharge: Float = 0f
        private set
    /** kill frenzy timer — extra damage & speed */
    var frenzyT: Float = 0f
        private set
    var roomKills: Int = 0
        private set
    var fightTimer: Float = 0f
    /** set when room clears: S / A / B / C */
    var clearGrade: String = ""
        private set
    var clearBonusGold: Int = 0
        private set
    var clearBonusXp: Int = 0
        private set
    var trialSucceeded: Boolean = false
        private set
    var maxCombo: Int = 0
        private set
    var nonBasicSkillCasts: Int = 0
        private set
    var playerDamageTaken: Float = 0f
        private set
    /** 濒死「残墨」触发一次 */
    private var clutchUsed: Boolean = false
    /** 供 UI 读的最近一次 Boss 阶段台词（读后清空） */
    var bossPhaseLine: String = ""
        private set
    var tacticalHintLine: String = ""
        private set

    fun consumeBossPhaseLine(): String {
        val s = bossPhaseLine
        bossPhaseLine = ""
        return s
    }

    fun consumeTacticalHintLine(): String {
        val s = tacticalHintLine
        tacticalHintLine = ""
        return s
    }

    private val skillCd = floatArrayOf(0f, 0f, 0f, 0f, 0f)
    /** loot collected this fight for RunMeta to grant */
    val lootedWeaponIds = ArrayList<String>(4)
    val lootedRingIds = ArrayList<String>(3)
    val lootedBootsIds = ArrayList<String>(3)
    val lootedTomeIds = ArrayList<String>(4)
    val lootedItemIds = ArrayList<String>(6)
    val lootedArmorIds = ArrayList<String>(4)
    private val pad = 40f
    private val prng = Random(System.nanoTime())
    private val lootRarityCap = maxGearRarityForThreat(threatLevel)
    private val lootTierCap = maxGearTierForThreat(threatLevel)
    private val waveList = if (waves.isEmpty()) {
        listOf(WaveDef(listOf(WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, 50f, 8f))))
    } else waves
    private val u = (width / 1080f).coerceIn(0.85f, 2.2f)

    private val atkMul = (1f + if (PassiveId.ATK_UP in passives) 0.12f else 0f) * mods.playerAtkMul
    private val spdMul = (1f + (if (PassiveId.SPD_UP in passives) 0.12f else 0f) + gear.spdBonus + extraSpd) * mods.playerSpdMul
    private val hpMul = (1f + if (PassiveId.HP_UP in passives) 0.15f else 0f) * mods.playerHpMul
    private val critChance = (if (PassiveId.CRIT in passives) 0.18f else 0.05f) + gear.crit + setCrit
    private val cdr = ((if (PassiveId.CDR in passives) 0.15f else 0f) + gear.cdr + setCdr).coerceIn(0f, 0.4f)
    private val lifesteal = (if (PassiveId.LIFESTEAL in passives) 0.08f else 0f) + gear.lifeSteal + setLifeSteal
    private val mpRegenMul = (1f + if (PassiveId.MP_REGEN in passives) 0.4f else 0f) * mods.mpRegenMul
    private val dmgTakenMul = (1f - armorDr.coerceIn(0f, 0.55f)) *
        (if (PassiveId.ARMOR in passives) 0.88f else 1f) *
        (1f - extraDr.coerceIn(0f, 0.35f)) *
        mods.dmgTakenMul
    private val burnAmp = if (PassiveId.BURN_AMP in passives) 1.4f else 1f
    private val goldMul = (if (PassiveId.GOLD_FIND in passives) 1.25f else 1f) * mods.goldMul
    private val wuxingAmp = if (combatProc == GearProc.WUXING_AMP) combatProcPower else 0f
    private val healAmp = if (combatProc == GearProc.HEAL_AMP) 1f + combatProcPower else 1f
    val activeGearProc: GearProc get() = combatProc
    val gearProcCooldownMax: Float = combatProc.cooldownSeconds()
    var gearProcCooldown: Float = 0f
        private set
    var gearProcPulse: Float = 0f
        private set
    var gearProcTriggerCount: Int = 0
        private set

    fun gearProcReadyFraction(): Float = if (gearProcCooldownMax <= 0f) 1f else {
        (1f - gearProcCooldown / gearProcCooldownMax).coerceIn(0f, 1f)
    }
    /** 战意：命中叠层加速普攻体感 */
    private var rageStacks: Int = 0
    private var rageT: Float = 0f
    /** 普攻输入缓冲剩余时间 */
    private var basicBuffer: Float = 0f
    private var environmentTimer: Float = if (environment.active) 4.2f + environment.variant * 0.7f else 99f

    init {
        waveTotal = waveList.size
        maxMp = hero.baseMp + gear.mpBonus + extraMp
        mp = startMp.coerceIn(0f, maxMp)
        val maxHp = (hero.baseHp + armorHp) * hpMul
        val hp = startHp.coerceIn(1f, maxHp)
        player = Actor(
            x = width * 0.35f,
            y = height * 0.55f,
            hp = hp,
            maxHp = maxHp,
            radius = 32f * u,
            atk = (hero.baseAtk + atkBonus) * atkMul,
            speed = hero.baseSpeed * u * 1.08f * spdMul,
            isPlayer = true
        )
        spawnWave(0)
        roomTrial?.let {
            float(width * 0.5f, height * 0.18f, "试炼·${it.title}", 250, 204, 21, 1.35f)
        }
        if (environment.active) {
            float(width * 0.5f, height * 0.27f, environment.title, 245, 235, 220, 1.25f)
        }
    }

    /** Multiplier from chapter/room threat + wave number. */
    fun threatMul(wave: Int = waveIndex): Float = arenaThreatMultiplier(threatLevel, wave)

    fun weaponName(): String = weaponUpgradeTable(hero)[wLevel].name
    fun skillCdLeft(slot: Int): Float = skillCd.getOrElse(slot) { 0f }
    fun skillUnlocked(slot: Int): Boolean {
        val need = skills.getOrNull(slot)?.unlockLevel ?: 1
        return heroLevel >= need
    }

    fun skillUnlockLevel(slot: Int): Int = skills.getOrNull(slot)?.unlockLevel ?: 1

    /** 输入层提示（未解锁技能等）：走战斗飘字通道 */
    fun showHint(text: String) {
        float(player.x, player.y - 60f, text, 148, 163, 184, 1.15f)
    }

    fun skillReady(slot: Int): Boolean {
        if (!skillUnlocked(slot)) return false
        if (skillCd.getOrElse(slot) { 1f } > 0f) return false
        if (slot == 0) return true
        // last slot = ultimate; full charge = free cast
        val last = skills.lastIndex
        if (slot == last && ultCharge >= 100f) return true
        val cost = skills.getOrNull(slot)?.mp ?: 0f
        return mp + 0.01f >= cost
    }

    fun isUltFree(): Boolean = skillUnlocked(skills.lastIndex) && ultCharge >= 100f
    fun frenzyActive(): Boolean = frenzyT > 0f
    fun ultSlot(): Int = skills.lastIndex

    /** 输入层边沿触发：快速点按（DOWN/UP 同帧合并）也不会丢普攻 */
    fun requestBasicTap() = requestTap(0)

    /** 输入层边沿触发：技能点按，ready 立即出手；未就绪给飘字反馈（点了必知道发生了什么） */
    fun requestTap(slot: Int) {
        runCatching {
            android.util.Log.d(
                "JellyCast",
                "request slot=$slot ready=${skillReady(slot)} cd=${"%.3f".format(skillCdLeft(slot))} finished=$finished dead=${player.dead}"
            )
        }
        if (finished || player.dead) return
        if (!skillUnlocked(slot)) {
            showHint("Lv${skillUnlockLevel(slot)} 解锁此技能")
            return
        }
        if (skillReady(slot)) {
            castSkill(slot)
            return
        }
        val def = skills.getOrNull(slot) ?: return
        val isUlt = slot == skills.lastIndex
        when {
            // 普攻不飘字；缓冲要盖过普攻 CD（0.30~0.42s）——快速连点时落在 CD 里的那次
            // 下一次冷却结束必出手，不能吞（曾造成「点了没反应」）
            slot == 0 -> basicBuffer = 0.5f
            !isUlt && mp + 0.01f < def.mp ->
                float(player.x, player.y - 36f, "法力不足", 125, 211, 252)
            skillCdLeft(slot) > 0f ->
                float(player.x, player.y - 36f, "冷却 ${"%.1f".format(skillCdLeft(slot))}s", 148, 163, 184)
        }
    }

    fun trialProgressLine(): String = roomTrial?.let {
        "${it.objective(waveTotal)} · ${it.progress(waveTotal, fightTimer, maxCombo, nonBasicSkillCasts, playerDamageTaken, player.maxHp)}"
    }.orEmpty()

    fun consumeHaptic(): Int {
        val h = hapticEvent
        hapticEvent = 0
        return h
    }

    fun update(
        dt: Float, stickX: Float, stickY: Float,
        basic: Boolean, s1: Boolean, s2: Boolean, s3: Boolean = false, s4: Boolean = false
    ) {
        if (finished) {
            // 战斗已定：仅驱动余韵（粒子/飘字/光环），让结算前的画面继续"呼吸"
            time += dt
            tickParticles(dt)
            tickCastFx(dt)
            tickEchoPulses(dt)
            updateFloats(dt)
            var ri = 0
            while (ri < rings.size) {
                rings[ri].life -= dt
                if (rings[ri].life <= 0f) rings.removeAt(ri) else ri++
            }
            if (shake > 0f) shake = max(0f, shake - dt * 3.6f)
            if (impactFlash > 0f) impactFlash = max(0f, impactFlash - dt * 7f)
            if (zoomPunch > 0f) zoomPunch = max(0f, zoomPunch - dt * 5.5f)
            return
        }
        // hit-stop: freeze world briefly for punchy hits
        if (hitStop > 0f) {
            hitStop -= dt
            tickGearProcTimers(dt)
            if (shake > 0f) shake = max(0f, shake - dt * 2.8f)
            if (impactFlash > 0f) impactFlash = max(0f, impactFlash - dt * 6f)
            if (zoomPunch > 0f) zoomPunch = max(0f, zoomPunch - dt * 4f)
            tickParticles(dt * 0.4f)
            tickCastFx(dt * 0.4f)
            tickEchoPulses(dt * 0.4f)
            // squash still eases during freeze
            for (e in enemies) {
                if (e.squash > 0f) e.squash = max(0f, e.squash - dt * 3.5f)
            }
            return
        }
        var d = dt.coerceIn(0f, 0.05f)
        // short slow-mo tail after big hits
        if (slowMo > 0f) {
            slowMo = max(0f, slowMo - dt * 2.2f)
            d *= 0.55f + 0.45f * (1f - slowMo)
        }
        time += d
        tickGearProcTimers(d)
        if (waveAnnounce > 0f) waveAnnounce -= d
        if (moveHint > 0f) moveHint -= d
        if (shake > 0f) shake = max(0f, shake - d * 3.6f)
        if (zoomPunch > 0f) zoomPunch = max(0f, zoomPunch - d * 5.5f)
        if (impactFlash > 0f) impactFlash = max(0f, impactFlash - d * 7f)
        if (comboTimer > 0f) {
            comboTimer -= d
            if (comboTimer <= 0f) comboCount = 0
        }
        if (frenzyT > 0f) frenzyT = max(0f, frenzyT - d)
        if (rageT > 0f) {
            rageT = max(0f, rageT - d)
            if (rageT <= 0f) rageStacks = 0
        }
        fightTimer += d
        if (attackLunge > 0f) attackLunge = max(0f, attackLunge - d * 9f)
        if (waveGap > 0f) {
            waveGap = max(0f, waveGap - d)
            if (waveGap <= 0f && waveIndex < waveList.size) {
                spawnWave(waveIndex)
                waveAnnounce = 1.2f
                float(width * 0.5f, height * 0.2f, "第 ${waveIndex + 1} 波!", 250, 204, 21)
            }
        }

        tickStatuses(player, d)
        for (i in skillCd.indices) if (skillCd[i] > 0f) skillCd[i] -= d
        // tighter mana — skills matter more
        mp = min(maxMp, mp + (15f * mpRegenMul * (roomTrial?.mpRegenMul ?: 1f)) * d)
        if (player.hitFlash > 0f) player.hitFlash -= d
        if (playerInvuln > 0f) playerInvuln -= d
        if (slashFx > 0f) slashFx -= d * 3.5f
        if (healPulse > 0f) healPulse -= d
        for (e in enemies) {
            if (e.squash > 0f) e.squash = max(0f, e.squash - d * 6f)
            if (e.hitStun > 0f) e.hitStun -= d
            if (e.hitFlash > 0f) e.hitFlash -= d
        }
        tickParticles(d)
        tickCastFx(d)
        tickEchoPulses(d)

        // snappy move: direct velocity from stick (no laggy accel)
        val slow = if (player.has(StatusType.SLOW) || player.has(StatusType.FREEZE)) 0.5f else 1f
        val frenzySpd = if (frenzyT > 0f) 1.18f else 1f
        val sm = sqrt(stickX * stickX + stickY * stickY)
        if (sm > 0.08f && !player.has(StatusType.FREEZE)) {
            // scale so small stick = walk, full = run
            val mag = sm.coerceIn(0f, 1f)
            val nx = stickX / sm
            val ny = stickY / sm
            player.vx = nx * player.speed * slow * mag * frenzySpd
            player.vy = ny * player.speed * slow * mag * frenzySpd
            player.facing = if (nx >= 0f) 1f else -1f
            moveHint = 0f
        } else {
            player.vx *= 0.55f
            player.vy *= 0.55f
            if (kotlin.math.abs(player.vx) < 8f) player.vx = 0f
            if (kotlin.math.abs(player.vy) < 8f) player.vy = 0f
            // face nearest when idle / attacking
            nearestEnemy()?.let { e -> player.facing = if (e.x >= player.x) 1f else -1f }
        }
        player.x = (player.x + player.vx * d).coerceIn(pad + player.radius, width - pad - player.radius)
        player.y = (player.y + player.vy * d).coerceIn(pad + player.radius, height - pad - player.radius)

        if (!player.dead) {
            // hold-to-fire basic; one-shot skills on press edge handled by caller via held flags
            // 普攻输入缓冲：连点落在攻击间隔里不再被吞掉（0.18s 内冷却一好立即出手）
            if (basic) {
                if (skillReady(0)) castSkill(0) else basicBuffer = 0.18f
            } else if (basicBuffer > 0f) {
                basicBuffer -= dt
                if (skillReady(0)) {
                    castSkill(0)
                    basicBuffer = 0f
                }
            }
            if (s1 && skillReady(1)) castSkill(1)
            if (s2 && skillReady(2)) castSkill(2)
            if (s3 && skillReady(3)) castSkill(3)
            if (s4 && skillReady(4)) castSkill(4)
        }

        updateEnemies(d)
        tickArenaEnvironment(d)
        tickBossHazards(d)
        updateShots(d)
        updateFields(d)
        updateDrops(d)
        updateFloats(d)
        tickInkMarks(d)
        tickInkHorse(d)
        var ri = 0
        while (ri < rings.size) {
            rings[ri].life -= d
            if (rings[ri].life <= 0f) rings.removeAt(ri) else ri++
        }
        separateEnemies()

        if (player.dead || player.hp <= 0f) {
            player.dead = true
            finished = true
            won = false
        } else if (enemies.all { it.dead } && waveGap <= 0f) {
            if (waveIndex + 1 < waveList.size) {
                waveIndex++
                waveGap = 0.85f
                float(width * 0.5f, height * 0.22f, "下一波逼近…", 251, 146, 60, 1.15f)
            } else {
                vacuumAllDrops()
                finished = true
                won = true
                computeClearGrade()
                grantClearLoot()
                settleInkLandscape()
            }
        }
    }

    private fun tickArenaEnvironment(d: Float) {
        if (!environment.active || finished || waveGap > 0f) return
        if (enemies.none { !it.dead }) {
            // 普通房清场即安全，不能在胜利结算前被残留环境预警补刀。
            bossHazards.clear()
            return
        }
        environmentTimer -= d
        if (environmentTimer > 0f) return
        environmentTimer = environment.interval * (0.92f + prng.nextFloat() * 0.16f)
        val warning = when (environment.pattern) {
            ArenaHazardPattern.CIRCLE -> 1.05f
            ArenaHazardPattern.RING -> 1.15f
            ArenaHazardPattern.LINE -> 0.95f
            ArenaHazardPattern.NONE -> return
        }
        val damage = player.maxHp * environment.damageRatio
        val label = environment.title
        when (environment.pattern) {
            ArenaHazardPattern.CIRCLE -> {
                val px = (player.x + player.vx * 0.38f).coerceIn(pad + 70f * u, width - pad - 70f * u)
                val py = (player.y + player.vy * 0.38f).coerceIn(pad + 70f * u, height - pad - 70f * u)
                bossHazards.add(
                    BossHazard(
                        BossHazardShape.CIRCLE, px, py,
                        outerRadius = (62f + environment.variant * 9f) * u,
                        life = warning, damage = damage, color = environment.color,
                        label = label, slowOnHit = environment.slowOnHit
                    )
                )
            }
            ArenaHazardPattern.RING -> {
                val centerX = width * (0.38f + environment.variant * 0.12f)
                val centerY = height * (0.42f + (environment.visualSeed % 3) * 0.08f)
                bossHazards.add(
                    BossHazard(
                        BossHazardShape.RING, centerX, centerY,
                        outerRadius = (165f + environment.variant * 18f) * u,
                        innerRadius = (86f + environment.variant * 10f) * u,
                        life = warning, damage = damage, color = environment.color,
                        label = label, slowOnHit = environment.slowOnHit
                    )
                )
            }
            ArenaHazardPattern.LINE -> {
                val horizontal = ((environment.visualSeed + environmentTimer.toInt()) and 1) == 0
                val lane = if (horizontal) {
                    val y = height * (0.28f + prng.nextFloat() * 0.44f)
                    BossHazard(
                        BossHazardShape.LINE, pad, y, width - pad, y,
                        width = (50f + environment.variant * 8f) * u,
                        life = warning, damage = damage, color = environment.color,
                        label = label, slowOnHit = environment.slowOnHit
                    )
                } else {
                    val x = width * (0.26f + prng.nextFloat() * 0.48f)
                    BossHazard(
                        BossHazardShape.LINE, x, pad, x, height - pad,
                        width = (50f + environment.variant * 8f) * u,
                        life = warning, damage = damage, color = environment.color,
                        label = label, slowOnHit = environment.slowOnHit
                    )
                }
                bossHazards.add(lane)
            }
            ArenaHazardPattern.NONE -> Unit
        }
    }

    private fun tickGearProcTimers(d: Float) {
        if (gearProcCooldown > 0f) gearProcCooldown = max(0f, gearProcCooldown - d)
        if (gearProcPulse > 0f) gearProcPulse = max(0f, gearProcPulse - d * 1.8f)
    }

    private fun markGearProcTriggered() {
        if (combatProc == GearProc.NONE) return
        gearProcCooldown = gearProcCooldownMax
        gearProcPulse = 1f
        gearProcTriggerCount++
        val color = combatProc.fxColor()
        if (skillCastsFx.size >= 10) skillCastsFx.removeAt(0)
        skillCastsFx.add(
            SkillCastFx(
                player.x, player.y, "装备·${combatProc.title}", combatProc.title.take(1),
                0.55f, 0.55f, color, false
            )
        )
        rings.add(RingFx(player.x, player.y, player.radius * 2.2f, 0.36f, 0.36f, color))
        hapticEvent = max(hapticEvent, 1)
        impactFlash = max(impactFlash, 0.08f)
    }

    private fun pushInk(mark: InkMark) {
        if (inkMarks.size >= 180) {
            // 丢掉最淡的
            var minI = 0
            var minL = Float.MAX_VALUE
            for (i in inkMarks.indices) {
                val l = inkMarks[i].life / inkMarks[i].maxLife
                if (l < minL) {
                    minL = l
                    minI = i
                }
            }
            inkMarks.removeAt(minI)
        }
        inkMarks.add(mark)
    }

    /** 笔锋线段残留 */
    fun leaveInkStroke(
        x0: Float, y0: Float, x1: Float, y1: Float,
        thick: Float,
        life: Float = 14f,
        color: Long = 0xFF1C1410
    ) {
        pushInk(InkMark(x0, y0, x1, y1, thick, life, life, kind = 0, color = color))
    }

    /** 墨晕 */
    fun leaveInkWash(x: Float, y: Float, r: Float, life: Float = 16f, color: Long = 0x661C1410) {
        pushInk(InkMark(x, y, x, y, 0f, life, life, kind = 1, color = color, r = r))
    }

    /** 飞白散点 */
    fun leaveInkDots(x: Float, y: Float, n: Int = 4, color: Long = 0xFF2C1810) {
        for (i in 0 until n) {
            val ox = x + (prng.nextFloat() - 0.5f) * 28f * u
            val oy = y + (prng.nextFloat() - 0.5f) * 22f * u
            pushInk(
                InkMark(
                    ox, oy, ox, oy, 0f,
                    life = 12f + prng.nextFloat() * 6f,
                    maxLife = 16f,
                    kind = 2,
                    color = color,
                    r = 1.5f + prng.nextFloat() * 3.5f
                )
            )
        }
    }

    /** 弧形斩迹：多段折线，像一笔写在地上 */
    fun leaveInkArc(cx: Float, cy: Float, ang: Float, radius: Float, width: Float) {
        val span = 0.9f + width * 0.12f
        val segs = 8
        var px = cx + cos(ang - span * 0.5f) * radius * 0.3f
        var py = cy + sin(ang - span * 0.5f) * radius * 0.3f
        for (i in 1..segs) {
            val t = i / segs.toFloat()
            val a = ang - span * 0.5f + span * t
            val rad = radius * (0.3f + t * 0.7f)
            val nx = cx + cos(a) * rad
            val ny = cy + sin(a) * rad
            val thick = when {
                t < 0.2f -> 2.5f + t * 18f
                t > 0.75f -> 10f * (1f - (t - 0.75f) / 0.25f) + 2f
                else -> 9f + width * 4f
            } * u * 0.9f
            val col = if (t < 0.15f || t > 0.85f) 0xCC1C1410 else 0xEE1C1410
            leaveInkStroke(px, py, nx, ny, thick, life = 18f + prng.nextFloat() * 8f, color = col)
            // 中段一点朱
            if (t in 0.35f..0.55f && prng.nextFloat() < 0.4f) {
                leaveInkStroke(px, py, nx, ny, thick * 0.35f, life = 12f, color = 0x99B91C1C)
            }
            px = nx
            py = ny
        }
        leaveInkDots(
            cx + cos(ang + span * 0.4f) * radius * 0.9f,
            cy + sin(ang + span * 0.4f) * radius * 0.9f,
            5
        )
    }

    private fun tickInkMarks(d: Float) {
        if (inkSettled) {
            inkSettleT += d
            return
        }
        var i = 0
        while (i < inkMarks.size) {
            // 慢淡：像墨干，不是立刻消失
            inkMarks[i].life -= d * 0.35f
            if (inkMarks[i].life <= 0f) inkMarks.removeAt(i) else i++
        }
    }

    /**
     * 清场落款：三分法 + 远近虚实 + 留白。
     * 上留天、中远山、下近坡；左松丛、右孤舟与朱印。
     */
    private fun settleInkLandscape() {
        if (inkSettled) return
        inkSettled = true
        inkSettleT = 0f
        for (m in inkMarks) {
            m.life = m.maxLife * 3f
            m.maxLife = m.life
        }
        // —— 天际留白：不画上 1/3 ——
        // 远山（淡、高、平远）
        fun ridge(baseY: Float, amp: Float, thick: Float, col: Long, phase: Float) {
            var x = width * 0.05f
            var px = x
            var py = baseY
            while (x < width * 0.95f) {
                x += width * 0.045f
                val ny = baseY + sin(x * 0.009f + phase) * amp + cos(x * 0.017f) * amp * 0.35f
                leaveInkStroke(px, py, x, ny, thick, 999f, col)
                px = x
                py = ny
            }
        }
        ridge(height * 0.30f, 14f * u, 2.2f * u, 0x281C1410, 0.2f)
        ridge(height * 0.36f, 22f * u, 3.2f * u, 0x381C1410, 1.1f)
        ridge(height * 0.44f, 28f * u, 4.5f * u, 0x4A1C1410, 2.0f)
        // 主峰（黄金分割偏右）
        val peakX = width * 0.62f
        val peakY = height * 0.24f
        leaveInkStroke(peakX - 55f * u, height * 0.42f, peakX, peakY, 5f * u, 999f, 0x661C1410)
        leaveInkStroke(peakX, peakY, peakX + 70f * u, height * 0.44f, 5f * u, 999f, 0x661C1410)
        leaveInkWash(peakX, height * 0.40f, 48f * u, 999f, 0x221C1410)
        // 中景雾带（横拖一笔）
        leaveInkWash(width * 0.45f, height * 0.50f, width * 0.38f, 999f, 0x181C1410)
        leaveInkStroke(width * 0.1f, height * 0.52f, width * 0.9f, height * 0.51f, 2f * u, 999f, 0x221C1410)
        // 近坡（浓、低）
        leaveInkWash(width * 0.4f, height * 0.78f, width * 0.5f, 999f, 0x2A1C1410)
        leaveInkStroke(width * 0.05f, height * 0.74f, width * 0.95f, height * 0.76f, 6f * u, 999f, 0x551C1410)
        leaveInkStroke(width * 0.08f, height * 0.82f, width * 0.92f, height * 0.84f, 8f * u, 999f, 0x661C1410)
        // 左三分：松丛（近实）
        for (i in 0..3) {
            val tx = width * (0.12f + i * 0.07f)
            val ty = height * (0.66f + (i % 2) * 0.03f)
            val hgt = (36f + i * 6f) * u
            leaveInkStroke(tx, ty, tx, ty - hgt, 2.8f * u, 999f, 0xBB1C1410)
            leaveInkStroke(tx, ty - hgt * 0.7f, tx - 16f * u, ty - hgt * 0.4f, 2.2f * u, 999f, 0x991C1410)
            leaveInkStroke(tx, ty - hgt * 0.7f, tx + 16f * u, ty - hgt * 0.4f, 2.2f * u, 999f, 0x991C1410)
            leaveInkStroke(tx, ty - hgt * 0.45f, tx - 12f * u, ty - hgt * 0.2f, 1.8f * u, 999f, 0x881C1410)
            leaveInkStroke(tx, ty - hgt * 0.45f, tx + 12f * u, ty - hgt * 0.2f, 1.8f * u, 999f, 0x881C1410)
        }
        // 右下：孤舟（小、简）
        val bx = width * 0.74f
        val by = height * 0.58f
        leaveInkStroke(bx, by, bx + 38f * u, by + 2f * u, 3.2f * u, 999f, 0xCC1C1410)
        leaveInkStroke(bx + 12f * u, by, bx + 18f * u, by - 18f * u, 2f * u, 999f, 0xAA1C1410)
        leaveInkWash(bx + 20f * u, by + 6f * u, 14f * u, 999f, 0x221C1410)
        // 右上留白处朱印
        leaveInkWash(width * 0.88f, height * 0.18f, 20f * u, 999f, 0x55B91C1C)
        leaveInkStroke(width * 0.85f, height * 0.16f, width * 0.91f, height * 0.16f, 2f * u, 999f, 0xAAB91C1C)
        leaveInkStroke(width * 0.85f, height * 0.19f, width * 0.91f, height * 0.19f, 2f * u, 999f, 0xAAB91C1C)
        leaveInkStroke(width * 0.85f, height * 0.22f, width * 0.91f, height * 0.22f, 2f * u, 999f, 0xAAB91C1C)
        // 题跋感短线（左下）
        leaveInkStroke(width * 0.08f, height * 0.90f, width * 0.22f, height * 0.90f, 1.5f * u, 999f, 0x661C1410)
        float(width * 0.5f, height * 0.14f, "落款 · 一纸墨战场", 44, 40, 35, 1.45f)
        hapticEvent = max(hapticEvent, 2)
    }

    /** 必杀：墨马自一侧横扫全场 */
    private fun castInkHorse(sm: Float) {
        inkHorseT = inkHorseMax
        inkHorseDir = if (player.facing >= 0f) 1f else -1f
        inkHorseHitWave = -1
        float(player.x, player.y - 56f, "墨马奔袭!", 44, 40, 35, 1.55f)
        impactFlash = max(impactFlash, 0.28f)
        shake = max(shake, 0.22f)
        slowMo = max(slowMo, 0.2f)
        // 起笔：一侧大墨晕
        val startX = if (inkHorseDir > 0f) width * 0.05f else width * 0.95f
        leaveInkWash(startX, height * 0.5f, 80f * u, life = 22f, color = 0x551C1410)
        // 预铺一道横贯墨道
        leaveInkStroke(
            width * 0.05f, height * 0.52f,
            width * 0.95f, height * 0.50f,
            7f * u, life = 24f, color = 0x661C1410
        )
        // 职业余韵
        when (hero) {
            HeroClass.WARRIOR -> player.applyStatus(StatusType.RAGE, 3f, 0.2f)
            HeroClass.MAGE -> Unit
            HeroClass.TAOIST -> healPlayer(player.maxHp * 0.12f)
        }
        @Suppress("UNUSED_VARIABLE")
        val keep = sm
    }

    private fun tickInkHorse(d: Float) {
        if (inkHorseT <= 0f) return
        inkHorseT = max(0f, inkHorseT - d)
        val progress = 1f - (inkHorseT / inkHorseMax) // 0..1
        // 分波伤害：横扫时 3 段全屏打击
        val wave = when {
            progress < 0.25f -> 0
            progress < 0.55f -> 1
            progress < 0.85f -> 2
            else -> 3
        }
        if (wave != inkHorseHitWave && wave in 0..2) {
            inkHorseHitWave = wave
            val sm = skillMul
            val mul = when (hero) {
                HeroClass.WARRIOR -> 1.55f + wave * 0.25f
                HeroClass.MAGE -> 1.4f + wave * 0.22f
                HeroClass.TAOIST -> 1.35f + wave * 0.2f
            } * sm
            for (e in enemies) {
                if (e.dead) continue
                damageEnemy(e, player.atk * mul, heavy = wave == 0)
                when (hero) {
                    HeroClass.WARRIOR -> e.applyStatus(StatusType.VULN, 2.5f, 0.18f)
                    HeroClass.MAGE -> e.applyStatus(StatusType.BURN, 2.8f, 10f * burnAmp)
                    HeroClass.TAOIST -> e.applyStatus(StatusType.SLOW, 2f, 0.35f)
                }
                // 马蹄下墨
                leaveInkWash(e.x, e.y, e.radius * 1.4f, life = 14f, color = 0x441C1410)
                leaveInkDots(e.x, e.y, 3)
            }
            // 奔过时再铺一道墨
            val y = height * (0.42f + wave * 0.08f)
            leaveInkStroke(
                width * 0.04f, y,
                width * 0.96f, y + (if (wave % 2 == 0) 8f else -6f) * u,
                (5f + wave * 2f) * u, life = 20f, color = 0x771C1410
            )
            leaveInkDots(width * (0.2f + wave * 0.25f), y, 6)
            hapticEvent = max(hapticEvent, if (wave == 0) 3 else 2)
            shake = max(shake, 0.14f)
            if (hero == HeroClass.TAOIST && wave == 1) {
                healPlayer(player.maxHp * 0.06f)
            }
        }
    }

    /** 清场时吸走场上全部掉落，避免漏捡武器 */
    private fun vacuumAllDrops() {
        for (drop in drops) {
            when (drop.kind) {
                1 -> healPlayer(player.maxHp * 0.12f)
                2 -> {
                    if (drop.itemId.isNotEmpty() && drop.itemId !in lootedWeaponIds) {
                        lootedWeaponIds.add(drop.itemId)
                    }
                }
                3 -> {
                    if (drop.itemId.isNotEmpty()) lootedTomeIds.add(drop.itemId)
                }
                4 -> {
                    if (drop.itemId.isNotEmpty()) lootedItemIds.add(drop.itemId)
                }
                5 -> {
                    if (drop.itemId.isNotEmpty() && drop.itemId !in lootedArmorIds) {
                        lootedArmorIds.add(drop.itemId)
                    }
                }
                6 -> {
                    if (drop.itemId.isNotEmpty() && drop.itemId !in lootedRingIds) {
                        lootedRingIds.add(drop.itemId)
                    }
                }
                7 -> {
                    if (drop.itemId.isNotEmpty() && drop.itemId !in lootedBootsIds) {
                        lootedBootsIds.add(drop.itemId)
                    }
                }
                else -> goldEarned += drop.gold
            }
        }
        drops.clear()
    }

    /** 清场奖励：首房保底入门装；之后由精英、评价与墨阶决定，不再每房塞一把武器。 */
    private fun grantClearLoot() {
        val noGear = lootedWeaponIds.isEmpty() && lootedArmorIds.isEmpty() &&
            lootedRingIds.isEmpty() && lootedBootsIds.isEmpty()
        val clearGearChance = 0.28f + mods.dropBonus.coerceAtMost(0.25f)
        if (noGear && (threatLevel <= 1 || clearGrade == "S" || prng.nextFloat() < clearGearChance)) {
            if (prng.nextFloat() < 0.58f) {
                WeaponCatalog.randomDrop(hero, lootRarityCap)?.let { w ->
                    lootedWeaponIds.add(w.id)
                    float(player.x, player.y - 50f, "清场装备:${w.name}", 251, 191, 36, 1.35f)
                }
            } else {
                ArmorCatalog.randomDrop(hero, lootRarityCap, lootTierCap)?.let { a ->
                    lootedArmorIds.add(a.id)
                    float(player.x, player.y - 50f, "清场防具:${a.name}", 134, 239, 172, 1.3f)
                }
            }
        }
        // 神来之笔偶尔再给一件武器，不让普通房奖励超过精英/Boss。
        if (clearGrade == "S" && prng.nextFloat() < 0.35f) {
            WeaponCatalog.randomDrop(hero, lootRarityCap)?.let { w ->
                if (w.id !in lootedWeaponIds) {
                    lootedWeaponIds.add(w.id)
                    float(player.x, player.y - 70f, "评价奖励:${w.name}", 253, 224, 71, 1.2f)
                }
            }
        }
        // 五行果实（词缀提高掉落）
        if (prng.nextFloat() < 0.55f + mods.dropBonus || lootedItemIds.isEmpty()) {
            val f = ItemCatalog.randomFruit()
            lootedItemIds.add(f.id)
            float(player.x, player.y - 88f, "果实:${f.name}", f.element?.let { 250 } ?: 200, 180, 100, 1.15f)
        }
        if (mods.dropBonus > 0.1f && prng.nextFloat() < mods.dropBonus) {
            val f2 = ItemCatalog.randomFruit()
            lootedItemIds.add(f2.id)
            float(player.x, player.y - 100f, "墨馈:${f2.name}", 250, 180, 100, 1.1f)
        }
    }

    private fun computeClearGrade() {
        val hpRatio = (player.hp / player.maxHp).coerceIn(0f, 1f)
        val speedBonus = when {
            fightTimer < 25f -> 2
            fightTimer < 45f -> 1
            else -> 0
        }
        val score = (hpRatio * 3f).toInt() + speedBonus + min(2, comboCount / 6) + if (roomKills >= 8) 1 else 0
        clearGrade = when {
            score >= 6 -> "S"
            score >= 4 -> "A"
            score >= 2 -> "B"
            else -> "C"
        }
        val gradeGold = when (clearGrade) {
            "S" -> 12
            "A" -> 8
            "B" -> 4
            else -> 0
        }
        val gradeXp = when (clearGrade) {
            "S" -> 18
            "A" -> 12
            "B" -> 6
            else -> 0
        }
        trialSucceeded = roomTrial?.succeeded(
            waveTotal, fightTimer, maxCombo, nonBasicSkillCasts, playerDamageTaken, player.maxHp
        ) == true
        val trialGold = if (trialSucceeded) roomTrial?.rewardGold ?: 0 else 0
        val trialXp = if (trialSucceeded) roomTrial?.rewardXp ?: 0 else 0
        clearBonusGold = ((gradeGold + trialGold) * mods.clearRewardMul * mods.goldMul).toInt()
        clearBonusXp = ((gradeXp + trialXp) * mods.clearRewardMul * mods.xpMul).toInt()
        goldEarned += clearBonusGold
        xpEarned += clearBonusXp
        val gradeName = when (clearGrade) {
            "S" -> "神来之笔 S"
            "A" -> "笔走龙蛇 A"
            "B" -> "中规中矩 B"
            else -> "墨未干 C"
        }
        float(width * 0.5f, height * 0.28f, gradeName, 250, 204, 21, 1.6f)
        if (clearBonusGold > 0) float(width * 0.5f, height * 0.36f, "奖励 +${clearBonusGold}金 +${clearBonusXp}经验", 167, 243, 208, 1.2f)
        roomTrial?.let { trial ->
            if (trialSucceeded) {
                float(width * 0.5f, height * 0.44f, "${trial.title}·达成!", 52, 211, 153, 1.35f)
            } else {
                float(width * 0.5f, height * 0.44f, "${trial.title}·未达成", 148, 163, 184, 1.05f)
            }
        }
    }

    private fun spawnWave(idx: Int) {
        enemies.clear()
        val wave = waveList[idx]
        val tm = threatMul(idx)
        for (we in wave.enemies) {
            val elite = we.elite || (
                idx >= 2 && prng.nextFloat() <
                    (eliteSpawnChance(threatLevel) + mods.eliteChanceBonus).coerceAtMost(0.48f)
                )
            val eliteTrait = if (elite) {
                EnemyEliteTrait.entries[1 + prng.nextInt(EnemyEliteTrait.entries.size - 1)]
            } else EnemyEliteTrait.NONE
            val kindMulHp = when (we.kind) {
                EnemyKind.SPIKE_SLIME -> 1.35f
                EnemyKind.BEETLE, EnemyKind.SKELETON -> 1.3f
                EnemyKind.GOBLIN -> 1.15f
                EnemyKind.BAT, EnemyKind.RAT -> 0.95f
                EnemyKind.BOSS_SLIME, EnemyKind.BOSS_ORE -> 1.35f
                EnemyKind.WISP -> 1.05f
                else -> 1.12f
            }
            val kindMulAtk = when (we.kind) {
                EnemyKind.GOBLIN, EnemyKind.BEETLE -> 1.22f
                EnemyKind.WISP, EnemyKind.BAT -> 1.18f
                EnemyKind.SKELETON -> 1.15f
                EnemyKind.BOSS_SLIME, EnemyKind.BOSS_ORE -> 1.28f
                EnemyKind.SPIKE_SLIME -> 1.12f
                else -> 1.1f
            }
            val kindMulSpd = when (we.kind) {
                EnemyKind.RAT, EnemyKind.BAT -> 1.22f
                EnemyKind.GOBLIN -> 1.14f
                EnemyKind.SPIKE_SLIME -> 0.92f
                EnemyKind.BEETLE -> 1.08f
                else -> 1.04f
            }
            val kindArmor = when (we.kind) {
                EnemyKind.SPIKE_SLIME -> 0.18f
                EnemyKind.BEETLE -> 0.22f
                EnemyKind.SKELETON -> 0.14f
                EnemyKind.BOSS_SLIME, EnemyKind.BOSS_ORE -> 0.2f
                else -> 0.04f
            }
            val eliteHp = if (elite) 1.55f else 1f
            val eliteAtk = if (elite) 1.28f else 1f
            val eliteArmor = if (elite) 0.12f else 0f
            val traitHp = if (eliteTrait == EnemyEliteTrait.BULWARK) 1.22f else 1f
            val traitAtk = if (eliteTrait == EnemyEliteTrait.FRENZIED) 1.18f else 1f
            val traitSpd = when (eliteTrait) {
                EnemyEliteTrait.SWIFT -> 1.20f
                EnemyEliteTrait.FRENZIED -> 1.08f
                else -> 1f
            }
            val traitArmor = if (eliteTrait == EnemyEliteTrait.BULWARK) 0.08f else 0f
            val hp = we.hp * tm * kindMulHp * eliteHp * traitHp * 1.12f * mods.enemyHpMul *
                (roomTrial?.enemyHpMul ?: 1f)
            val atk = we.atk * tm * kindMulAtk * eliteAtk * traitAtk * 1.12f * mods.enemyAtkMul *
                (roomTrial?.enemyAtkMul ?: 1f)
            val spd = we.speed * u * kindMulSpd * enemySpeedThreatMultiplier(threatLevel) *
                (if (elite) 1.1f else 1f) * traitSpd * mods.enemySpdMul *
                (roomTrial?.enemySpeedMul ?: 1f)
            enemies.add(
                Actor(
                    x = width * (0.48f + prng.nextFloat() * 0.38f),
                    y = height * (0.20f + prng.nextFloat() * 0.58f),
                    hp = hp,
                    maxHp = hp,
                    radius = we.kind.baseRadius() * u * (if (elite) 1.14f else 1f),
                    atk = atk,
                    speed = spd,
                    isPlayer = false,
                    attackCd = prng.nextFloat() * 0.45f,
                    supportCd = 2.4f + prng.nextFloat() * 1.8f,
                    specialCd = 1.8f + prng.nextFloat() * 2.4f,
                    kind = we.kind,
                    element = we.kind.element(),
                    ai = we.ai,
                    elite = elite,
                    eliteTrait = eliteTrait,
                    thorns = when (we.kind) {
                        EnemyKind.SPIKE_SLIME -> 0.28f
                        EnemyKind.BEETLE -> if (elite) 0.12f else 0.06f
                        else -> 0f
                    },
                    armor = (kindArmor + eliteArmor + traitArmor).coerceIn(0f, 0.48f)
                )
            )
            if (elite) {
                val trait = enemies.last().eliteTrait
                val (r, g, b) = when (trait) {
                    EnemyEliteTrait.SWIFT -> Triple(56, 189, 248)
                    EnemyEliteTrait.BULWARK -> Triple(251, 191, 36)
                    EnemyEliteTrait.FRENZIED -> Triple(239, 68, 68)
                    else -> Triple(251, 146, 60)
                }
                float(
                    enemies.last().x, enemies.last().y - 30f,
                    "精英·${trait.title}", r, g, b, 1.15f
                )
            }
        }
        val roles = enemies.map { it.kind.tacticalRole() }
            .filter { it != EnemyTacticalRole.NONE }
            .distinct()
        tacticalHintLine = when {
            roles.isEmpty() -> ""
            roles.size == 1 -> roles.first().hint
            else -> "战术目标：${roles.joinToString(" / ") { "${it.badge}${it.title}" }}"
        }
    }

    private fun castSkill(slot: Int) {
        runCatching {
            android.util.Log.d("JellyCast", "cast slot=$slot hero=$hero at=(${player.x.toInt()},${player.y.toInt()})")
        }
        val def = skills.getOrNull(slot) ?: return
        if (!skillUnlocked(slot)) {
            if (slot != 0) float(player.x, player.y - 40f, "Lv${def.unlockLevel} 解锁「${def.name}」", 148, 163, 184, 1.1f)
            return
        }
        val isUlt = slot == skills.lastIndex
        val freeUlt = isUlt && ultCharge >= 100f
        if (slot != 0 && !freeUlt && mp < def.mp) {
            float(player.x, player.y - 36f, "法力不足", 125, 211, 252)
            return
        }
        if (slot != 0 && !freeUlt) mp -= def.mp
        if (freeUlt) {
            ultCharge = 0f
            float(player.x, player.y - 56f, "必杀解放!", 250, 204, 21, 1.5f)
            impactFlash = max(impactFlash, 0.4f)
            shake = max(shake, 0.35f)
        }
        skillCd[slot] = def.cd * (1f - cdr) * (if (freeUlt) 0.75f else 1f)
        if (slot != 0) {
            nonBasicSkillCasts++
            if (skillCastsFx.size >= 10) skillCastsFx.removeAt(0)
            val castColor = when (hero) {
                HeroClass.WARRIOR -> if (isUlt) 0xFFFBBF24 else 0xFFFB923C
                HeroClass.MAGE -> if (slot == 1) 0xFF7DD3FC else if (slot == 3) 0xFFFF6B35 else 0xFFA78BFA
                HeroClass.TAOIST -> if (slot == 1) 0xFFA3E635 else 0xFF4ADE80
            }
            val castLife = if (isUlt) 1.05f else 0.62f
            skillCastsFx.add(
                SkillCastFx(player.x, player.y, def.name, def.glyph, castLife, castLife, castColor, isUlt)
            )
            hapticEvent = max(hapticEvent, if (isUlt) 6 else 5)
            impactFlash = max(impactFlash, if (isUlt) 0.38f else 0.12f)
            zoomPunch = max(zoomPunch, if (isUlt) 0.3f else 0.08f)
        }
        val target = nearestEnemy()
        val sm = skillMul
        when (hero) {
            HeroClass.WARRIOR -> when (slot) {
                0 -> warriorSlash(target, 1f * sm, 1.05f)
                1 -> warriorDash(target)
                2 -> warriorIronWall()
                3 -> warriorWhirl(sm)
                4 -> castInkHorse(sm)
            }
            HeroClass.MAGE -> when (slot) {
                0 -> {
                    fireball(target, player.atk * 1.15f * sm, StatusType.BURN, 2.5f, 8f * burnAmp)
                    // 满蓝双发：蓝量≥90%时补一发偏轴火球（不耗蓝；放技能掉蓝后回落单发，形成节奏循环）
                    if (mp >= maxMp * 0.9f) {
                        twinFireball(target, player.atk * 1.15f * sm * 0.85f, StatusType.BURN, 2.5f, 8f * burnAmp)
                    }
                }
                1 -> manaShield()
                2 -> chainLightning(target)
                3 -> fireBlast(target, sm)
                4 -> castInkHorse(sm)
            }
            HeroClass.TAOIST -> when (slot) {
                0 -> talismanFan(target)
                1 -> poisonMist()
                2 -> springHeal()
                3 -> sealBind(target, sm)
                4 -> castInkHorse(sm)
            }
        }
        if (isUlt && !freeUlt) {
            ultCharge = max(0f, ultCharge - 35f)
        }
    }

    private fun warriorWhirl(sm: Float) {
        float(player.x, player.y - 44f, "旋风斩!", 251, 146, 60, 1.25f)
        rings.add(RingFx(player.x, player.y, 120f * u, 0.45f, 0.45f, 0xFFFB923C))
        rings.add(RingFx(player.x, player.y, 80f * u, 0.4f, 0.4f, 0xFFFBBF24))
        var hits = 0
        for (e in enemies) {
            if (e.dead) continue
            if (dist(player.x, player.y, e.x, e.y) <= 125f * u + e.radius) {
                damageEnemy(e, player.atk * 1.15f * sm, heavy = hits == 0)
                damageEnemy(e, player.atk * 0.55f * sm)
                hits++
            }
        }
        burst(player.x, player.y, 18, 0xFFFB923C, 200f * u, 0.4f)
        // 旋风双弧：外弧顺时针、内弧逆时针的笔锋剪影 + 切向碎片
        slashArc(player.x, player.y, 118f * u, prng.nextFloat() * 6.28318f, 6.28318f, 0.3f, 0xFFFB923C, 12f, spin = 5.5f)
        slashArc(player.x, player.y, 86f * u, prng.nextFloat() * 6.28318f, 6.28318f, 0.34f, 0xFFFBBF24, 8f, spin = -6.5f)
        shardBurst(player.x, player.y, 10, 0xFFFDBA74, 260f * u, 0.35f, 16f * u)
        val echoRank = coreRank(CoreInkId.WARRIOR_WHIRL_ECHO)
        if (echoRank > 0) {
            scheduleEcho(
                player.x, player.y, 0.52f, 155f * u,
                player.atk * (0.5f + echoRank * 0.18f) * sm,
                0xFFFB923C, "回", "回锋"
            )
        }
        slashFx = 1f
        slashWidth = 1.5f
    }

    private fun fireBlast(target: Actor?, sm: Float) {
        val tx = target?.x ?: (player.x + player.facing * 200f * u)
        val ty = target?.y ?: player.y
        rings.add(RingFx(tx, ty, 110f * u, 0.5f, 0.5f, 0xFFFF6B35))
        rings.add(RingFx(tx, ty, 60f * u, 0.45f, 0.45f, 0xFFFBBF24))
        burst(tx, ty, 30, 0xFFFF6B35, 260f * u, 0.55f)
        // 爆心火星 + 余焰双层飞散
        shardBurst(tx, ty, 14, 0xFFFF8C42, 340f * u, 0.5f, 20f * u)
        shardBurst(tx, ty, 8, 0xFFFBBF24, 210f * u, 0.42f, 13f * u)
        for (e in enemies) {
            if (e.dead) continue
            if (dist(tx, ty, e.x, e.y) <= 112f * u + e.radius) {
                damageEnemy(e, player.atk * 2.3f * sm, heavy = true)
                e.applyStatus(StatusType.BURN, 3.5f, 16f * burnAmp)
            }
        }
        float(tx, ty - 30f, "炎爆!", 255, 107, 53, 1.3f)
        val cinderRank = coreRank(CoreInkId.MAGE_CINDER_FIELD)
        if (cinderRank > 0) {
            val life = 2.4f + cinderRank * 0.55f
            fields.add(
                FieldFx(
                    tx, ty, (72f + cinderRank * 7f) * u, life, life,
                    dps = player.atk * (0.24f + cinderRank * 0.07f) * sm,
                    healPerSec = 0f, color = 0xFFFF6B35, kind = 2
                )
            )
            float(tx, ty - 50f, "余烬成阵·${cinderRank}阶", 255, 107, 53, 0.95f)
        }
        shake = max(shake, 0.35f)
    }

    private fun sealBind(target: Actor?, sm: Float) {
        val focus = target ?: enemies.filter { !it.dead }.minByOrNull { dist(player.x, player.y, it.x, it.y) }
        val cx = focus?.x ?: player.x
        val cy = focus?.y ?: player.y
        rings.add(RingFx(cx, cy, 100f * u, 0.55f, 0.55f, 0xFFA78BFA))
                float(cx, cy - 36f, "镇符!", 167, 139, 250, 1.25f)
        // 镇印迸裂：紫墨向四周压出
        shardBurst(cx, cy, 8, 0xFFC4B5FD, 250f * u, 0.4f, 15f * u)
        for (e in enemies) {
            if (e.dead) continue
            if (dist(cx, cy, e.x, e.y) <= 105f * u + e.radius) {
                damageEnemy(e, player.atk * 0.9f * sm)
                e.applyStatus(StatusType.FREEZE, 1.2f, 1f)
                e.applyStatus(StatusType.SLOW, 3.5f, 0.55f)
                e.vx = 0f; e.vy = 0f
                float(e.x, e.y - e.radius, "镇!", 192, 132, 252, 1.0f)
            }
        }
        burst(cx, cy, 14, 0xFFA78BFA, 120f * u, 0.4f)
        val echoRank = coreRank(CoreInkId.TAOIST_SEAL_ECHO)
        if (echoRank > 0) {
            scheduleEcho(
                cx, cy, 0.62f, (108f + echoRank * 4f) * u,
                player.atk * (0.42f + echoRank * 0.16f) * sm,
                0xFFA78BFA, "镇", "回符",
                StatusType.FREEZE, 0.55f + echoRank * 0.12f, 1f
            )
        }
    }

    /** Warrior S2: defensive identity — shield + reflect + brief regen. */
    private fun warriorIronWall() {
        player.applyStatus(StatusType.SHIELD, 4.0f, player.maxHp * 0.28f)
        player.applyStatus(StatusType.REFLECT, 4.0f, 0.4f)
        player.applyStatus(StatusType.RAGE, 2.5f, 0.12f)
        healPulse = 0.7f
        rings.add(RingFx(player.x, player.y, player.radius * 2.5f, 0.5f, 0.5f, 0xFFFBBF24))
        rings.add(RingFx(player.x, player.y, player.radius * 1.6f, 0.45f, 0.45f, 0xFFF59E0B))
        float(player.x, player.y - 44f, "铁壁!", 251, 191, 36, 1.25f)
        float(player.x, player.y - 24f, "护盾·反伤", 253, 224, 71, 1.0f)
        burst(player.x, player.y, 14, 0xFFFBBF24, 100f * u, 0.35f)
        val burstRank = coreRank(CoreInkId.WARRIOR_BASTION_BURST)
        if (burstRank > 0) {
            val radius = (95f + burstRank * 12f) * u
            var first = true
            enemies.filter { !it.dead && dist(player.x, player.y, it.x, it.y) <= radius + it.radius }
                .forEach { enemy ->
                    damageEnemy(enemy, player.atk * (0.48f + burstRank * 0.2f), heavy = first)
                    enemy.applyStatus(StatusType.VULN, 1.8f, 0.12f + burstRank * 0.02f)
                    first = false
                }
            rings.add(RingFx(player.x, player.y, radius, 0.48f, 0.48f, 0xFFFBBF24))
            float(player.x, player.y - 62f, "金城反震·${burstRank}阶", 253, 224, 71, 1.0f)
        }
    }

    /** Mage S2: chain lightning — bounces between enemies. */
    private fun chainLightning(start: Actor?) {
        val living = enemies.filter { !it.dead }.toMutableList()
        if (living.isEmpty()) {
            float(player.x, player.y - 36f, "无目标", 148, 163, 184)
            return
        }
        var cur = start?.takeIf { !it.dead } ?: living.minByOrNull { dist(player.x, player.y, it.x, it.y) }
        var hops = 0
        val hit = mutableSetOf<Actor>()
        var prevX = player.x
        var prevY = player.y
        val branchRank = coreRank(CoreInkId.MAGE_STORM_BRANCH)
        val maxHops = 6 + branchRank
        val searchRange = (220f + branchRank * 28f) * u
        while (cur != null && hops < maxHops && hit.size < living.size) {
            hit.add(cur)
            damageEnemy(cur, player.atk * (1.3f - hops * 0.06f), heavy = hops == 0)
            cur.applyStatus(StatusType.VULN, 2.2f, 0.2f)
            // bolt visual as thin ring trail
            rings.add(RingFx(cur.x, cur.y, 28f * u, 0.28f, 0.28f, 0xFFA78BFA))
            burst(cur.x, cur.y, 6, 0xFFC4B5FD, 90f * u, 0.25f)
            // 落点电离碎片 + 随机分叉侧雷
            shardBurst(cur.x, cur.y, 3, 0xFFC4B5FD, 210f * u, 0.24f, 12f * u)
            val forkA = prng.nextFloat() * 6.28318f
            bolts.add(
                BoltFx(cur.x, cur.y, cur.x + cos(forkA) * 36f * u, cur.y + sin(forkA) * 36f * u, 0.18f, 0.18f, 0xFFC4B5FD)
            )
            // fake line via mid rings
            val mx = (prevX + cur.x) * 0.5f
            val my = (prevY + cur.y) * 0.5f
            rings.add(RingFx(mx, my, 12f * u, 0.15f, 0.15f, 0xFFE9D5FF))
            bolts.add(BoltFx(prevX, prevY, cur.x, cur.y, 0.24f, 0.24f, 0xFFE9D5FF))
            float(cur.x, cur.y - cur.radius - 6f, if (hops == 0) "雷击!" else "连锁!", 167, 139, 250, 0.95f)
            prevX = cur.x
            prevY = cur.y
            hops++
            val from = cur
            cur = living
                .filter { it !in hit }
                .filter { dist(from.x, from.y, it.x, it.y) < searchRange }
                .minByOrNull { dist(from.x, from.y, it.x, it.y) }
        }
        val branch = if (branchRank > 0) " · 雷枝${branchRank}阶" else ""
        float(player.x, player.y - 44f, "链雷 x$hops$branch", 167, 139, 250, 1.2f)
        shake = max(shake, 0.25f)
    }

    /** Taoist S2: big heal + cleanse control. */
    private fun springHeal() {
        val amount = (player.maxHp * 0.32f + 20f + wLevel * 6f) * healAmp
        val missingBefore = max(0f, player.maxHp - player.hp)
        healPlayer(amount)
        // cleanse slow/freeze on self
        player.statuses.removeAll { it.type == StatusType.SLOW || it.type == StatusType.FREEZE || it.type == StatusType.POISON || it.type == StatusType.BURN }
        player.applyStatus(StatusType.SHIELD, 1.8f, player.maxHp * 0.08f)
        val overflowRank = coreRank(CoreInkId.TAOIST_SPRING_OVERFLOW)
        val overflow = max(0f, amount - missingBefore)
        if (overflowRank > 0 && overflow > 0f) {
            val shield = overflow * (0.45f + overflowRank * 0.15f)
            player.applyStatus(StatusType.SHIELD, 3.0f + overflowRank * 0.25f, shield)
            float(player.x, player.y - 68f, "溢脉护盾 +${shield.toInt()}", 167, 243, 208, 1.0f)
        }
        healPulse = 0.9f
        rings.add(RingFx(player.x, player.y, player.radius * 3f, 0.55f, 0.55f, 0xFF4ADE80))
        rings.add(RingFx(player.x, player.y, player.radius * 2f, 0.5f, 0.5f, 0xFF86EFAC))
        // 治愈灵粒：自体向上升起的绿色笔触
        shardBurst(player.x, player.y, 9, 0xFF86EFAC, 110f * u, 0.62f, 13f * u, -1.5707964f, 1.3f, 5f)
        burst(player.x, player.y, 20, 0xFF86EFAC, 140f * u, 0.45f)
        float(player.x, player.y - 48f, "回春!", 74, 222, 128, 1.35f)
        float(player.x, player.y - 28f, "+${amount.toInt()}HP", 167, 243, 208, 1.1f)
        // mild aura heal nearby? no — pure sustain identity
    }

    private fun addUlt(amount: Float) {
        val before = ultCharge
        ultCharge = min(100f, ultCharge + amount)
        if (before < 100f && ultCharge >= 100f) {
            float(player.x, player.y - 50f, "必杀就绪!", 253, 224, 71, 1.35f)
            rings.add(RingFx(player.x, player.y, player.radius * 2.8f, 0.4f, 0.4f, 0xFFFBBF24))
            hapticEvent = max(hapticEvent, 2)
        }
    }

    /** Combat potion — 40% max HP. */
    fun tryUsePotion(): Boolean {
        if (player.dead || finished) return false
        if (player.hp >= player.maxHp - 0.5f) {
            float(player.x, player.y - 36f, "满血", 148, 163, 184)
            return false
        }
        healPlayer(player.maxHp * 0.4f)
        rings.add(RingFx(player.x, player.y, player.radius * 2.6f, 0.4f, 0.4f, 0xFF4ADE80))
        burst(player.x, player.y, 14, 0xFF86EFAC, 120f * u, 0.4f)
        float(player.x, player.y - 44f, "用药!", 74, 222, 128, 1.25f)
        return true
    }

    private fun warriorSlash(target: Actor?, mul: Float, arc: Float) {
        val ang = if (target != null) atan2(target.y - player.y, target.x - player.x)
        else if (player.facing > 0f) 0f else PI.toFloat()
        slashFx = 1f
        slashAngle = ang
        slashWidth = 1.08f + min(0.25f, comboCount * 0.04f)
        // 仅极轻“压笔”，避免人像一跳一跳
        attackLunge = 0.22f
        val range = hero.attackRange * u * (1f + if (player.has(StatusType.RAGE)) 0.15f else 0f)
        // 三连击拍：第 3 刀重斩——更宽弧、更高伤害、强击退
        slashSwing = (slashSwing + 1) % 3
        val heavy = slashSwing == 0
        val effArc = if (heavy) arc * 1.35f else arc
        // 地上留一笔弧，不是光球
        leaveInkArc(player.x, player.y, ang, range * 0.92f, slashWidth)
        // 亮色斩弧剪影 + 刃尖火花（叠在墨弧上；重斩更大更沉）
        if (heavy) {
            slashArc(player.x, player.y, range * 0.95f, ang - effArc, effArc * 2f, 0.24f, 0xFFFB923C, 14f, spin = 0.7f)
            shardBurst(player.x + cos(ang) * range * 0.75f, player.y + sin(ang) * range * 0.75f, 6, 0xFFFB923C, 210f * u, 0.3f, 15f * u, ang, 1.3f, 5f)
        } else {
            slashArc(player.x, player.y, range * 0.78f, ang - effArc, effArc * 2f, 0.22f, 0xFFFFE3B8, 12f, spin = 0.5f)
            shardBurst(player.x + cos(ang) * range * 0.7f, player.y + sin(ang) * range * 0.7f, 3, 0xFFFFC98A, 150f * u, 0.22f, 12f * u, ang, 1.1f, 4f)
        }
        val dmgMul = mul * (if (heavy) 1.55f else 1f) * (1f + player.powerOf(StatusType.RAGE)) *
            (1f + min(0.55f, comboCount * mods.comboDmgPerStack))
        var hits = 0
        for (e in enemies) {
            if (e.dead) continue
            val dx = e.x - player.x
            val dy = e.y - player.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > range + e.radius) continue
            val a = atan2(dy, dx)
            var da = a - ang
            while (da > PI) da -= (2 * PI).toFloat()
            while (da < -PI) da += (2 * PI).toFloat()
            if (kotlin.math.abs(da) < effArc) {
                damageEnemy(e, player.atk * dmgMul, heavy = hits == 0)
                // 极轻粘滞，别把敌人拽得乱跳；重斩则强击退
                val dd = dist.coerceAtLeast(1f)
                val push = if (heavy) 16f * u else 2.5f * u
                e.x -= dx / dd * push
                e.y -= dy / dd * push
                hits++
            }
        }
        if (heavy && hits > 0) {
            float(player.x, player.y - 60f, "重斩!", 251, 146, 60, 1.1f)
            shake = max(shake, 0.14f)
        }
        if (hits == 0) {
            burst(player.x + cos(ang) * range * 0.6f, player.y + sin(ang) * range * 0.6f, 4, 0x44FFFFFF, 40f * u, 0.2f)
        }
    }

    /** Warrior ultimate: 3 expanding shock rings. */
    private fun warriorQuake() {
        player.applyStatus(StatusType.RAGE, 4f, 0.28f)
        player.applyStatus(StatusType.SHIELD, 2.2f, player.maxHp * 0.12f)
        float(player.x, player.y - 48f, "裂地斩!", 251, 146, 60, 1.4f)
        shake = max(shake, 0.55f)
        impactFlash = max(impactFlash, 0.35f)
        hitStop = max(hitStop, 0.08f)
        for (i in 0..2) {
            val rr = (90f + i * 55f) * u
            rings.add(RingFx(player.x, player.y, rr, 0.5f - i * 0.08f, 0.5f - i * 0.08f, 0xFFFBBF24))
            for (e in enemies) {
                if (e.dead) continue
                val d0 = dist(player.x, player.y, e.x, e.y)
                if (d0 <= rr + e.radius) {
                    damageEnemy(e, player.atk * (1.35f + i * 0.25f), heavy = i == 0)
                    e.applyStatus(StatusType.VULN, 3f, 0.22f)
                    e.applyStatus(StatusType.SLOW, 1.2f, 0.4f)
                }
            }
        }
        burst(player.x, player.y, 28, 0xFFF59E0B, 280f * u, 0.55f, gravity = 200f * u)
    }

    private fun warriorDash(target: Actor?) {
        val ang = aimAngle(target)
        val dist = 170f * u
        val startX = player.x
        val startY = player.y
        // trail particles along dash
        for (i in 0..5) {
            val t = i / 5f
            burst(
                player.x + cos(ang) * dist * t,
                player.y + sin(ang) * dist * t,
                3, 0xAAEF4444, 50f * u, 0.28f
            )
        }
        player.x = (player.x + cos(ang) * dist).coerceIn(pad + player.radius, width - pad - player.radius)
        player.y = (player.y + sin(ang) * dist).coerceIn(pad + player.radius, height - pad - player.radius)
        playerInvuln = 0.25f
        slashFx = 1f
        slashAngle = ang
        slashWidth = 1.2f
        attackLunge = 0.35f
        shake = max(shake, 0.16f)
        // 冲锋笔触拖痕：起→终一条亮线 + 沿途碎片
        bolts.add(BoltFx(startX, startY, player.x, player.y, 0.22f, 0.22f, 0xFFFFC98A))
        shardBurst(player.x, player.y, 6, 0xFFFCA5A5, 200f * u, 0.3f, 13f * u, ang + 3.14159f, 1.2f, 4f)
        for (e in enemies) {
            if (e.dead) continue
            if (dist(player.x, player.y, e.x, e.y) < player.radius + e.radius + 48f * u) {
                damageEnemy(e, player.atk * 1.7f, heavy = true)
                val dx = e.x - player.x
                val dy = e.y - player.y
                val dd = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                e.x = (e.x + dx / dd * 18f * u).coerceIn(pad + e.radius, width - pad - e.radius)
                e.y = (e.y + dy / dd * 18f * u).coerceIn(pad + e.radius, height - pad - e.radius)
            }
        }
        float(player.x, player.y - 36f, "冲锋!", 248, 113, 113, 1.3f)
        rings.add(RingFx(player.x, player.y, 50f * u, 0.35f, 0.35f, 0xFFFF6B8A))
        val trailRank = coreRank(CoreInkId.WARRIOR_FIRE_TRAIL)
        if (trailRank > 0) {
            val segments = 3 + trailRank.coerceAtMost(2)
            val life = 2.1f + trailRank * 0.48f
            repeat(segments) { index ->
                val q = (index + 0.5f) / segments
                fields.add(
                    FieldFx(
                        startX + (player.x - startX) * q,
                        startY + (player.y - startY) * q,
                        (29f + trailRank * 3f) * u,
                        life, life,
                        dps = player.atk * (0.18f + trailRank * 0.055f),
                        healPerSec = 0f, color = 0xFFEF4444, kind = 2
                    )
                )
            }
            leaveInkStroke(startX, startY, player.x, player.y, (12f + trailRank * 2f) * u, life + 8f, 0xCCB91C1C)
            float(player.x, player.y - 54f, "赤线走笔·${trailRank}阶", 248, 113, 113, 0.95f)
        }
    }

    private fun fireball(target: Actor?, dmg: Float, st: StatusType?, stT: Float, stP: Float) {
        val ang = aimAngle(target)
        val sp = 520f * u
        shots.add(
            Shot(
                player.x + cos(ang) * player.radius, player.y + sin(ang) * player.radius,
                cos(ang) * sp, sin(ang) * sp, 1.7f, 15f * u, dmg, true, 1, st, stT, stP,
                splash = 62f * u, tint = gear.element.argb()
            )
        )
    }

    /** 满蓝双发的第二发：偏轴火球，与主弹同弹速同特效（法师普攻的变化拍） */
    private fun twinFireball(target: Actor?, dmg: Float, st: StatusType?, stT: Float, stP: Float) {
        val ang = aimAngle(target) + 0.38f
        val sp = 520f * u
        shots.add(
            Shot(
                player.x + cos(ang) * player.radius, player.y + sin(ang) * player.radius,
                cos(ang) * sp, sin(ang) * sp, 1.7f, 15f * u, dmg, true, 1, st, stT, stP,
                splash = 62f * u, tint = gear.element.argb()
            )
        )
    }

    /** Mage S1: 魔法盾 — 吸收伤害的法盾；盾期间击中你的敌人被寒霜迟缓。 */
    private fun manaShield() {
        val rank = coreRank(CoreInkId.MAGE_TWIN_FROST)
        val dur = 5f + rank * 0.6f
        player.applyStatus(StatusType.SHIELD, dur, player.maxHp * (0.32f + rank * 0.05f))
        manaShieldT = dur
        float(player.x, player.y - 44f, "魔法盾!", 125, 211, 252, 1.25f)
        rings.add(RingFx(player.x, player.y, player.radius * 2.8f, 0.55f, 0.55f, 0xFF7DD3FC))
        rings.add(RingFx(player.x, player.y, player.radius * 1.8f, 0.5f, 0.5f, 0xFFE0F2FE))
        burst(player.x, player.y, 12, 0xFF7DD3FC, 120f * u, 0.4f)
        shardBurst(player.x, player.y, 6, 0xFFBAE6FD, 150f * u, 0.45f, 13f * u)
        if (rank > 0) float(player.x, player.y - 62f, "霜盾·${rank}阶", 125, 211, 252, 0.95f)
        shake = max(shake, 0.12f)
    }

    /** Taoist basic: 3 talismans in a fan (not a single mage bolt). */
    private fun talismanFan(target: Actor?) {
        val ang = aimAngle(target)
        val sp = 400f * u
        // 撒符纸屑：随扇面方向飘散
        shardBurst(player.x, player.y, 5, 0xFFD9F99D, 190f * u, 0.3f, 12f * u, ang, 1.1f, 4f)
        for (k in -1..1) {
            val a = ang + k * 0.22f
            // 中间符带迟缓：三符各有存在感
            shots.add(
                Shot(
                    player.x + cos(a) * player.radius,
                    player.y + sin(a) * player.radius,
                    cos(a) * sp, sin(a) * sp, 1.85f, 11f * u,
                    player.atk * (0.72f + if (k == 0) 0.18f else 0f),
                    true, 2,
                    if (k == 0) StatusType.SLOW else null, 1.6f, 0.7f,
                    tint = gear.element.argb()
                )
            )
        }
        // small self heal on cast (sustain identity)
        healPlayer(3f + wLevel * 1.2f)
    }

    private fun poisonMist() {
        val wanderingRank = coreRank(CoreInkId.TAOIST_WANDERING_MIST)
        val rr = (120f + wanderingRank * 7f) * u
        val life = 5.0f + wanderingRank * 0.55f
        // strong field DoT — poison is the identity
        fields.add(
            FieldFx(
                player.x, player.y, rr, life, life,
                dps = player.atk * (0.85f + wanderingRank * 0.08f),
                healPerSec = 0f,
                color = 0xFFA3E635,
                kind = if (wanderingRank > 0) 3 else 0
            )
        )
        rings.add(RingFx(player.x, player.y, rr, 0.55f, 0.55f, 0xFFA3E635))
        rings.add(RingFx(player.x, player.y, rr * 0.6f, 0.45f, 0.45f, 0xFF65A30D))
        var n = 0
        for (e in enemies) {
            if (e.dead) continue
            if (dist(player.x, player.y, e.x, e.y) <= rr + e.radius) {
                // power = HP lost per second while poisoned
                e.applyStatus(StatusType.POISON, 5.5f, player.atk * 0.45f)
                e.applyStatus(StatusType.VULN, 4f, 0.28f)
                e.applyStatus(StatusType.SLOW, 3f, 0.35f)
                damageEnemy(e, player.atk * 0.5f)
                float(e.x, e.y - e.radius - 6f, "中毒!", 163, 230, 53, 1.1f)
                n++
            }
        }
        val wandering = if (wanderingRank > 0) " · 随身${wanderingRank}阶" else ""
        float(player.x, player.y - 44f, "毒雾 · 中毒$n$wandering", 163, 230, 53, 1.2f)
        burst(player.x, player.y, 20, 0xFFA3E635, 150f * u, 0.45f)
    }

    private fun sageArray() {
        val rr = 140f * u
        fields.add(
            FieldFx(
                player.x, player.y, rr, 6.0f, 6.0f,
                dps = player.atk * 0.9f,
                healPerSec = 18f + wLevel * 4f,
                color = 0xFF4ADE80,
                kind = 1
            )
        )
        player.applyStatus(StatusType.REFLECT, 4f, 0.3f)
        player.applyStatus(StatusType.SHIELD, 2.5f, player.maxHp * 0.1f)
        // multiple rings = readable "formation"
        for (i in 0..3) {
            rings.add(RingFx(player.x, player.y, rr * (0.35f + i * 0.22f), 0.7f, 0.7f, if (i % 2 == 0) 0xFF4ADE80 else 0xFF86EFAC))
        }
        float(player.x, player.y - 52f, "天师阵!", 74, 222, 128, 1.45f)
        float(player.x, player.y - 28f, "阵内：伤敌·回血", 167, 243, 208, 1.05f)
        burst(player.x, player.y, 24, 0xFF4ADE80, 180f * u, 0.5f)
        shake = max(shake, 0.32f)
        impactFlash = max(impactFlash, 0.2f)
    }

    /** 冰环已被魔法盾接替（S1）。原冰环特效元素（寒霜碎片）由魔法盾沿用。 */

    /** Mage ultimate: meteors rain near target / screen. */
    private fun meteorRain(target: Actor?) {
        float(player.x, player.y - 48f, "陨星雨!", 167, 139, 250, 1.4f)
        shake = max(shake, 0.45f)
        impactFlash = max(impactFlash, 0.3f)
        val focusX = target?.x ?: (player.x + player.facing * 180f * u)
        val focusY = target?.y ?: player.y
        val living = enemies.filter { !it.dead }
        for (i in 0 until 7) {
            val e = living.getOrNull(i % living.size.coerceAtLeast(1))
            val mx = if (e != null) e.x + (prng.nextFloat() - 0.5f) * 40f * u
            else focusX + (prng.nextFloat() - 0.5f) * 220f * u
            val my = if (e != null) e.y + (prng.nextFloat() - 0.5f) * 40f * u
            else focusY + (prng.nextFloat() - 0.5f) * 160f * u
            // delayed impact via short-lived high shot from above
            shots.add(
                Shot(
                    mx, my - 80f * u,
                    0f, 420f * u,
                    0.35f + i * 0.05f, 18f * u,
                    player.atk * 1.55f, true, 6,
                    StatusType.BURN, 2f, 13f * burnAmp,
                    splash = 78f * u
                )
            )
            rings.add(RingFx(mx, my, 28f * u, 0.5f, 0.5f, 0xFFA78BFA))
            // 落点预警环：随陨星下落同步收束，命中即爆
            val tele = 0.35f + i * 0.05f + 0.4f
            rings.add(RingFx(mx, my, 76f * u, tele, tele, 0xFFFDBA74))
        }
    }

    private fun scheduleEcho(
        x: Float,
        y: Float,
        delay: Float,
        radius: Float,
        damage: Float,
        color: Long,
        glyph: String,
        label: String,
        status: StatusType? = null,
        statusDuration: Float = 0f,
        statusPower: Float = 0f
    ) {
        echoPulses.add(
            EchoPulseFx(
                x, y, delay, delay, radius, damage, color, glyph, label,
                status, statusDuration, statusPower
            )
        )
    }

    private fun tickEchoPulses(d: Float) {
        var index = 0
        while (index < echoPulses.size) {
            val pulse = echoPulses[index]
            pulse.life -= d
            if (pulse.life > 0f) {
                index++
                continue
            }
            var first = true
            for (enemy in enemies) {
                if (enemy.dead || dist(pulse.x, pulse.y, enemy.x, enemy.y) > pulse.radius + enemy.radius) continue
                damageEnemy(enemy, pulse.damage, heavy = first)
                pulse.status?.let { status ->
                    enemy.applyStatus(status, pulse.statusDuration, pulse.statusPower)
                    if (status == StatusType.FREEZE) {
                        enemy.vx = 0f; enemy.vy = 0f
                        enemy.chargeVx = 0f; enemy.chargeVy = 0f
                    }
                }
                first = false
            }
            rings.add(RingFx(pulse.x, pulse.y, pulse.radius, 0.48f, 0.48f, pulse.color))
            rings.add(RingFx(pulse.x, pulse.y, pulse.radius * 0.62f, 0.4f, 0.4f, pulse.color))
            burst(pulse.x, pulse.y, 16, pulse.color, 155f * u, 0.4f)
            float(pulse.x, pulse.y - pulse.radius * 0.55f, pulse.label, 245, 235, 220, 1.15f)
            shake = max(shake, 0.25f)
            impactFlash = max(impactFlash, 0.16f)
            hapticEvent = max(hapticEvent, 5)
            echoPulses.removeAt(index)
        }
    }

    /** 定向碎片飞散：angleBase+spread（弧度）决定扇形方向；不传 angleBase 则全向 */
    private fun shardBurst(
        x: Float, y: Float, n: Int, color: Long, speed: Float, life: Float, len: Float,
        angleBase: Float = Float.NaN, spread: Float = 6.28318f, width: Float = 6f
    ) {
        for (i in 0 until n) {
            if (shards.size > 90) return
            val a = if (angleBase.isNaN()) prng.nextFloat() * 6.28318f
            else angleBase + (prng.nextFloat() - 0.5f) * spread
            val sp = speed * (0.5f + prng.nextFloat() * 0.8f)
            shards.add(
                ShardFx(
                    x, y, cos(a) * sp, sin(a) * sp, a,
                    len * (0.95f + prng.nextFloat() * 0.7f),
                    life * (0.85f + prng.nextFloat() * 0.5f), life, color, width
                )
            )
        }
    }

    /** 斩弧：startAngle 起 sweep 弧度；spin 为生命期内的追加旋转（旋风用） */
    private fun slashArc(
        x: Float, y: Float, r: Float, startAngle: Float, sweep: Float,
        life: Float, color: Long, width: Float = 9f, spin: Float = 0f
    ) {
        if (slashArcs.size > 14) slashArcs.removeAt(0)
        slashArcs.add(SlashArcFx(x, y, r, startAngle, sweep, life, life, color, width, spin))
    }

    private fun updateSlashFx(d: Float) {
        if (manaShieldT > 0f) {
            manaShieldT -= d
            // 盾期微光脉冲
            if ((manaShieldT * 2.2f).toInt() != ((manaShieldT + d) * 2.2f).toInt()) {
                rings.add(RingFx(player.x, player.y, player.radius * 2.3f, 0.42f, 0.42f, 0xFF7DD3FC))
            }
        }
        var i = 0
        while (i < shards.size) {
            val s = shards[i]
            s.life -= d
            if (s.life <= 0f) {
                shards.removeAt(i)
                continue
            }
            s.x += s.vx * d
            s.y += s.vy * d
            val drag = (1f - 2.4f * d).coerceAtLeast(0f)
            s.vx *= drag
            s.vy *= drag
            i++
        }
        var j = 0
        while (j < slashArcs.size) {
            val a = slashArcs[j]
            a.life -= d
            if (a.life <= 0f) slashArcs.removeAt(j) else j++
        }
    }

    private fun updateFields(d: Float) {
        updateSlashFx(d)
        var i = 0
        while (i < fields.size) {
            val f = fields[i]
            f.life -= d
            if (f.kind == 3) {
                val follow = (3.6f * d).coerceIn(0f, 1f)
                f.x += (player.x - f.x) * follow
                f.y += (player.y - f.y) * follow
            }
            // pulse ring visual
            if ((f.life * 6f).toInt() != ((f.life + d) * 6f).toInt()) {
                rings.add(RingFx(f.x, f.y, f.r * 0.95f, 0.22f, 0.22f, f.color))
                if (f.kind == 1) {
                    rings.add(RingFx(f.x, f.y, f.r * 0.5f, 0.2f, 0.2f, 0xFF86EFAC))
                }
            }
            for (e in enemies) {
                if (e.dead) continue
                if (dist(f.x, f.y, e.x, e.y) <= f.r + e.radius) {
                    e.hp -= f.dps * d
                    if (f.kind == 0 || f.kind == 3) {
                        e.applyStatus(StatusType.POISON, 1.2f, player.atk * 0.4f)
                        e.applyStatus(StatusType.SLOW, 0.5f, 0.3f)
                    } else if (f.kind == 2) {
                        e.applyStatus(StatusType.BURN, 1.1f, player.atk * 0.22f * burnAmp)
                    }
                    if (e.hp <= 0f && !e.dead) killEnemy(e)
                }
            }
            if (f.healPerSec > 0f && dist(f.x, f.y, player.x, player.y) <= f.r + player.radius) {
                val before = player.hp
                healPlayer(f.healPerSec * d)
                // occasional heal float so array feels active
                if (player.hp > before && (f.life * 3f).toInt() != ((f.life + d) * 3f).toInt()) {
                    float(player.x, player.y - 28f, "阵愈", 74, 222, 128, 0.9f)
                }
            }
            if (f.life <= 0f) fields.removeAt(i) else i++
        }
    }

    private fun updateEnemies(d: Float) {
        var ei = 0
        while (ei < enemies.size) {
            val e = enemies[ei++]
            if (e.dead) continue
            tickStatuses(e, d)
            if (e.attackCd > 0f) e.attackCd -= d
            if (e.supportCd > 0f) e.supportCd -= d
            if (e.specialCd > 0f) e.specialCd -= d
            if (e.hitStun > 0f) continue // still recovering from hit
            val dx = player.x - e.x
            val dy = player.y - e.y
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            e.facing = if (dx >= 0f) 1f else -1f
            val slowed = if (e.has(StatusType.SLOW) || e.has(StatusType.FREEZE)) 0.45f else 1f
            if (e.has(StatusType.FREEZE)) continue

            tickEnemySupport(e)

            // low-HP enrage (once)
            if (!e.enraged && e.hp < e.maxHp * 0.32f) {
                e.enraged = true
                e.speed *= 1.28f
                e.atk *= 1.18f
                float(e.x, e.y - e.radius - 10f, "狂暴!", 239, 68, 68, 1.2f)
                rings.add(RingFx(e.x, e.y, e.radius * 2.2f, 0.35f, 0.35f, 0xFFEF4444))
            }
            // Boss 多阶段：过半 / 绝境
            if (e.ai == EnemyAi.BOSS) {
                val ratio = e.hp / e.maxHp.coerceAtLeast(1f)
                if (e.bossPhase < 1 && ratio < 0.66f) {
                    e.bossPhase = 1
                    e.speed *= 1.12f
                    e.atk *= 1.1f
                    bossPhaseLine = bossPhaseAnnounce(e.kind, 1)
                    float(e.x, e.y - e.radius - 18f, "二相·$bossPhaseLine", 251, 191, 36, 1.35f)
                    rings.add(RingFx(e.x, e.y, e.radius * 3.2f, 0.5f, 0.5f, 0xFFFBBF24))
                    shake = max(shake, 0.55f)
                    impactFlash = max(impactFlash, 0.35f)
                    hapticEvent = max(hapticEvent, 2)
                } else if (e.bossPhase < 2 && ratio < 0.33f) {
                    e.bossPhase = 2
                    e.enraged = true
                    e.speed *= 1.18f
                    e.atk *= 1.15f
                    bossPhaseLine = bossPhaseAnnounce(e.kind, 2)
                    float(e.x, e.y - e.radius - 18f, "绝相·$bossPhaseLine", 248, 113, 113, 1.45f)
                    rings.add(RingFx(e.x, e.y, e.radius * 3.8f, 0.55f, 0.55f, 0xFFEF4444))
                    shake = max(shake, 0.7f)
                    slowMo = max(slowMo, 0.35f)
                    impactFlash = max(impactFlash, 0.5f)
                    hapticEvent = max(hapticEvent, 3)
                    // 绝相召唤小怪压力
                    if (enemies.count { !it.dead } < 10) {
                        spawnBossMinion(e, count = 2)
                    }
                }
            }
            // 每种普通怪拥有独立的可预警招牌攻击；预警阶段停步，给玩家明确反应窗口。
            if (e.ai != EnemyAi.BOSS && tickEnemySignatureAttack(e, d, dist)) continue
            val rage = if (e.enraged) 1.12f else 1f
            val supportHaste = if (e.has(StatusType.RAGE)) 1.22f else 1f
            val phaseMul = 1f + e.bossPhase * 0.06f
            val spd = e.speed * slowed * rage * supportHaste * phaseMul

            when (e.ai) {
                EnemyAi.CHASE -> {
                    // slight lead / flank: bias perpendicular when close pack
                    var mx = dx / dist
                    var my = dy / dist
                    if (dist < 140f * u) {
                        mx += -dy / dist * 0.25f
                        my += dx / dist * 0.25f
                        val ml = sqrt(mx * mx + my * my).coerceAtLeast(1e-3f)
                        mx /= ml; my /= ml
                    }
                    e.vx = mx * spd
                    e.vy = my * spd
                    e.x = (e.x + e.vx * d).coerceIn(pad + e.radius, width - pad - e.radius)
                    e.y = (e.y + e.vy * d).coerceIn(pad + e.radius, height - pad - e.radius)
                    if (dist < e.radius + player.radius + 8f && e.attackCd <= 0f) {
                        contactHit(e, e.atk * 1.05f)
                        e.attackCd = if (e.elite) 0.7f else 0.82f
                    }
                }
                EnemyAi.RANGED -> {
                    val prefer = 200f * u
                    if (dist < prefer - 30f) {
                        e.vx = -dx / dist * spd * 1.05f
                        e.vy = -dy / dist * spd * 1.05f
                    } else if (dist > prefer + 50f) {
                        e.vx = dx / dist * spd * 0.95f
                        e.vy = dy / dist * spd * 0.95f
                    } else {
                        e.vx = -dy / dist * spd * 0.85f
                        e.vy = dx / dist * spd * 0.85f
                    }
                    e.x = (e.x + e.vx * d).coerceIn(pad + e.radius, width - pad - e.radius)
                    e.y = (e.y + e.vy * d).coerceIn(pad + e.radius, height - pad - e.radius)
                    if (e.attackCd <= 0f && dist < 400f * u) {
                        // lead the shot slightly
                        val lead = 0.12f
                        val ang = atan2(dy + player.vy * lead, dx + player.vx * lead)
                        val sp = 320f * u
                        val n = if (e.kind == EnemyKind.WISP || e.elite) 2 else 1
                        for (k in 0 until n) {
                            val a = ang + if (n == 1) 0f else (k - 0.5f) * 0.18f
                            shots.add(
                                Shot(
                                    e.x + cos(a) * e.radius, e.y + sin(a) * e.radius,
                                    cos(a) * sp, sin(a) * sp, 2.4f, 11f * u, e.atk * 0.95f,
                                    false, 5
                                )
                            )
                        }
                        e.attackCd = if (e.elite) 1.15f else 1.35f
                    }
                }
                EnemyAi.CHARGER -> {
                    if (e.windup > 0f) {
                        e.windup -= d
                        if (e.windup <= 0f) {
                            e.chargeVx = dx / dist * e.speed * 3.6f * rage
                            e.chargeVy = dy / dist * e.speed * 3.6f * rage
                            e.attackCd = 0.6f
                        }
                    } else if (e.attackCd > 0f && (e.chargeVx != 0f || e.chargeVy != 0f)) {
                        e.x = (e.x + e.chargeVx * d).coerceIn(pad + e.radius, width - pad - e.radius)
                        e.y = (e.y + e.chargeVy * d).coerceIn(pad + e.radius, height - pad - e.radius)
                        e.attackCd -= d
                        if (dist < e.radius + player.radius + 10f) {
                            contactHit(e, e.atk * 1.4f)
                            e.chargeVx = 0f
                            e.chargeVy = 0f
                            e.attackCd = 1.2f
                        } else if (e.attackCd <= 0f) {
                            e.chargeVx = 0f
                            e.chargeVy = 0f
                            e.attackCd = 1.5f
                        }
                    } else {
                        e.vx = dx / dist * spd * 0.8f
                        e.vy = dy / dist * spd * 0.8f
                        e.x = (e.x + e.vx * d).coerceIn(pad + e.radius, width - pad - e.radius)
                        e.y = (e.y + e.vy * d).coerceIn(pad + e.radius, height - pad - e.radius)
                        if (dist < 300f * u && e.attackCd <= 0f) {
                            e.windup = 0.4f
                            e.attackCd = 0f
                            float(e.x, e.y - e.radius - 8f, "!", 251, 146, 60)
                        }
                    }
                }
                EnemyAi.BOSS -> {
                    val phase = e.bossPhase
                    // 冲锋中优先
                    if (e.chargeVx != 0f || e.chargeVy != 0f) {
                        e.x = (e.x + e.chargeVx * d).coerceIn(pad + e.radius, width - pad - e.radius)
                        e.y = (e.y + e.chargeVy * d).coerceIn(pad + e.radius, height - pad - e.radius)
                        e.attackCd -= d
                        if (dist < e.radius + player.radius + 12f) {
                            contactHit(e, e.atk * 1.4f)
                            e.chargeVx = 0f
                            e.chargeVy = 0f
                            e.attackCd = 1.1f
                        } else if (e.attackCd <= 0f) {
                            e.chargeVx = 0f
                            e.chargeVy = 0f
                            e.attackCd = 0.9f
                        }
                    } else {
                        val prefer = (150f - phase * 18f) * u
                        if (dist < prefer) {
                            e.vx = -dx / dist * spd * 0.9f
                            e.vy = -dy / dist * spd * 0.9f
                        } else {
                            e.vx = dx / dist * spd * 0.85f
                            e.vy = dy / dist * spd * 0.85f
                        }
                        e.vx += -dy / dist * spd * (0.4f + phase * 0.12f)
                        e.vy += dx / dist * spd * (0.4f + phase * 0.12f)
                        e.x = (e.x + e.vx * d).coerceIn(pad + e.radius, width - pad - e.radius)
                        e.y = (e.y + e.vy * d).coerceIn(pad + e.radius, height - pad - e.radius)
                        if (e.attackCd <= 0f) {
                            val pattern = e.bossPatternStep++ % 4
                            val signature = bossEncounter?.let { encounter ->
                                when (pattern) {
                                    0 -> encounter.moveA
                                    2 -> if (phase >= 1) encounter.moveB else encounter.moveA
                                    else -> null
                                }
                            }
                            if (signature != null) {
                                castSignatureBossMove(e, signature)
                                e.attackCd = if (phase >= 2) 1.25f else 1.55f
                            } else {
                                val roll = prng.nextFloat()
                                when {
                                roll < 0.28f - phase * 0.04f -> {
                                    val rr = (130f + phase * 25f) * u
                                    rings.add(RingFx(e.x, e.y, rr, 0.42f, 0.42f, 0xFFC084FC))
                                    if (dist < rr + player.radius) contactHit(e, e.atk * (1.2f + phase * 0.12f))
                                    float(e.x, e.y - e.radius, bossSkillName(e.kind, 0), 192, 132, 252)
                                    e.attackCd = if (phase >= 2) 1.0f else 1.3f
                                }
                                roll < 0.62f -> {
                                    val ang = atan2(dy, dx)
                                    val sp = (340f + phase * 40f) * u
                                    val fan = 2 + phase
                                    for (k in -fan..fan) {
                                        val a = ang + k * (0.14f - phase * 0.01f)
                                        shots.add(
                                            Shot(
                                                e.x, e.y, cos(a) * sp, sin(a) * sp, 2.1f, 12f * u,
                                                e.atk * (0.82f + phase * 0.06f), false, 5
                                            )
                                        )
                                    }
                                    float(e.x, e.y - e.radius, bossSkillName(e.kind, 1), 251, 191, 36, 0.95f)
                                    e.attackCd = if (e.enraged) 1.15f else 1.35f
                                }
                                roll < 0.82f && phase >= 1 -> {
                                    e.chargeVx = dx / dist * e.speed * (3.4f + phase * 0.45f)
                                    e.chargeVy = dy / dist * e.speed * (3.4f + phase * 0.45f)
                                    e.attackCd = 0.55f
                                    float(e.x, e.y - e.radius, bossSkillName(e.kind, 2), 248, 113, 113)
                                }
                                else -> {
                                    if (enemies.count { !it.dead } < 8 + phase) {
                                        spawnBossMinion(e, count = 1 + if (phase >= 2) 1 else 0)
                                        float(e.x, e.y - e.radius, bossSkillName(e.kind, 3), 167, 139, 250)
                                    } else {
                                        val ang = atan2(dy, dx)
                                        val sp = 380f * u
                                        for (k in 0..5) {
                                            val a = ang + k * 1.047f
                                            shots.add(
                                                Shot(
                                                    e.x, e.y, cos(a) * sp, sin(a) * sp, 1.8f, 11f * u,
                                                    e.atk * 0.75f, false, 5
                                                )
                                            )
                                        }
                                        float(e.x, e.y - e.radius, "六合弹", 196, 181, 253)
                                    }
                                    e.attackCd = if (e.enraged) 1.1f else 1.35f
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 返回 true 表示本帧处于招牌攻击预警/出招，暂停常规 AI。 */
    private fun tickEnemySignatureAttack(e: Actor, d: Float, playerDistance: Float): Boolean {
        if (e.specialAttack != EnemySignatureAttack.NONE) {
            e.vx = 0f
            e.vy = 0f
            e.specialWindup -= d
            if (e.specialWindup <= 0f) executeEnemySignatureAttack(e, e.specialAttack)
            return true
        }
        if (e.specialCd > 0f) return false
        // 不打断冲锋状态机；冲锋落地后再进入种类招牌攻击。
        if (e.windup > 0f || e.chargeVx != 0f || e.chargeVy != 0f) return false
        val attack = e.kind.signatureAttack()
        if (attack == EnemySignatureAttack.NONE) return false
        val triggerRange = when (attack) {
            EnemySignatureAttack.SLIME_POUNCE -> 190f
            EnemySignatureAttack.PINK_BURST -> 270f
            EnemySignatureAttack.SPIKE_VOLLEY -> 330f
            EnemySignatureAttack.BAT_SONIC -> 390f
            EnemySignatureAttack.SKELETON_CLEAVE -> 155f
            EnemySignatureAttack.GOBLIN_BOMB -> 330f
            EnemySignatureAttack.RAT_DASH -> 245f
            EnemySignatureAttack.WISP_RING -> 370f
            EnemySignatureAttack.NONE -> 0f
        } * u
        if (playerDistance > triggerRange) return false
        e.specialAttack = attack
        e.specialWindup = attack.windup
        // 锁定短期预判位置，预警线与实际落点一致，躲开后不会被攻击偷偷追踪。
        e.specialAimX = (player.x + player.vx * 0.22f).coerceIn(pad, width - pad)
        e.specialAimY = (player.y + player.vy * 0.22f).coerceIn(pad, height - pad)
        float(e.x, e.y - e.radius - 12f, attack.title, 251, 191, 36, 0.9f)
        return true
    }

    private fun executeEnemySignatureAttack(e: Actor, attack: EnemySignatureAttack) {
        val ax = e.specialAimX - e.x
        val ay = e.specialAimY - e.y
        val al = sqrt(ax * ax + ay * ay).coerceAtLeast(1f)
        val nx = ax / al
        val ny = ay / al
        val elitePower = if (e.elite) 1.12f else 1f

        fun shot(angle: Float, speed: Float, damage: Float, style: Int, status: StatusType? = null, statusT: Float = 0f) {
            shots.add(
                Shot(
                    e.x + cos(angle) * e.radius, e.y + sin(angle) * e.radius,
                    cos(angle) * speed * u, sin(angle) * speed * u,
                    2.4f, 11f * u, e.atk * damage * elitePower, false, style,
                    status = status, statusT = statusT
                )
            )
        }

        when (attack) {
            EnemySignatureAttack.SLIME_POUNCE -> {
                e.x = (e.x + nx * 92f * u).coerceIn(pad + e.radius, width - pad - e.radius)
                e.y = (e.y + ny * 92f * u).coerceIn(pad + e.radius, height - pad - e.radius)
                if (dist(e.x, e.y, player.x, player.y) < e.radius + player.radius + 42f * u) {
                    contactHit(e, e.atk * 1.28f * elitePower)
                }
                rings.add(RingFx(e.x, e.y, 72f * u, 0.34f, 0.34f, attack.color))
                e.squash = 0.9f
            }
            EnemySignatureAttack.PINK_BURST -> {
                val center = atan2(ay, ax)
                for (k in -2..2) shot(center + k * 0.2f, 285f, 0.72f, 11)
            }
            EnemySignatureAttack.SPIKE_VOLLEY -> {
                for (k in 0..7) shot(k * (PI.toFloat() / 4f), 260f, 0.68f, 8)
                rings.add(RingFx(e.x, e.y, 100f * u, 0.42f, 0.42f, attack.color))
            }
            EnemySignatureAttack.BAT_SONIC -> {
                val center = atan2(ay, ax)
                for (k in -1..1) shot(center + k * 0.22f, 335f, 0.7f, 7, StatusType.SLOW, 1.15f)
            }
            EnemySignatureAttack.SKELETON_CLEAVE -> {
                if (dist(e.x, e.y, player.x, player.y) < 158f * u + player.radius) {
                    contactHit(e, e.atk * 1.42f * elitePower)
                }
                rings.add(RingFx(e.x + nx * 45f * u, e.y + ny * 45f * u, 118f * u, 0.3f, 0.3f, attack.color))
            }
            EnemySignatureAttack.GOBLIN_BOMB -> {
                val center = atan2(ay, ax)
                shot(center, 245f, 1.05f, 10, StatusType.BURN, 2.4f)
            }
            EnemySignatureAttack.RAT_DASH -> {
                e.x = (e.x + nx * 128f * u).coerceIn(pad + e.radius, width - pad - e.radius)
                e.y = (e.y + ny * 128f * u).coerceIn(pad + e.radius, height - pad - e.radius)
                if (dist(e.x, e.y, player.x, player.y) < e.radius + player.radius + 34f * u) {
                    contactHit(e, e.atk * 1.18f * elitePower)
                }
                leaveInkStroke(e.x - nx * 100f * u, e.y - ny * 100f * u, e.x, e.y, 7f * u, 4f, 0x6678716C)
            }
            EnemySignatureAttack.WISP_RING -> {
                val offset = atan2(ay, ax) * 0.25f
                for (k in 0..5) shot(offset + k * (PI.toFloat() / 3f), 235f, 0.62f, 12, StatusType.SLOW, 0.9f)
                rings.add(RingFx(e.x, e.y, 125f * u, 0.5f, 0.5f, attack.color))
            }
            EnemySignatureAttack.NONE -> Unit
        }
        burst(e.x, e.y, 7, attack.color, 82f * u, 0.3f)
        e.specialAttack = EnemySignatureAttack.NONE
        e.specialWindup = 0f
        e.specialCd = attack.cooldown * if (e.elite) 0.82f else 1f
        e.attackCd = max(e.attackCd, 0.45f)
    }

    private fun tickEnemySupport(source: Actor) {
        if (source.supportCd > 0f) return
        when (source.kind.tacticalRole()) {
            EnemyTacticalRole.HEALER -> {
                val range = 280f * u
                val target = enemies
                    .asSequence()
                    .filter { ally ->
                        !ally.dead && ally !== source && ally.ai != EnemyAi.BOSS && ally.hp < ally.maxHp * 0.92f &&
                            dist(source.x, source.y, ally.x, ally.y) <= range
                    }
                    .minByOrNull { ally -> ally.hp / ally.maxHp.coerceAtLeast(1f) }
                if (target == null) {
                    source.supportCd = 1.2f
                    return
                }
                val amount = target.maxHp * if (source.elite) 0.13f else 0.09f
                target.hp = min(target.maxHp, target.hp + amount)
                target.hitFlash = 0.12f
                bolts.add(BoltFx(source.x, source.y, target.x, target.y, 0.34f, 0.34f, 0xFF86EFAC))
                rings.add(RingFx(target.x, target.y, target.radius * 1.6f, 0.42f, 0.42f, 0xFF4ADE80))
                float(target.x, target.y - target.radius - 16f, "回春 +${amount.toInt()}", 74, 222, 128, 1.0f)
                float(source.x, source.y - source.radius - 14f, "续火", 134, 239, 172, 0.9f)
                source.supportCd = if (source.elite) 4.5f else 5.5f
            }
            EnemyTacticalRole.DRUMMER -> {
                val allies = enemies.filter { ally ->
                    !ally.dead && ally !== source && dist(source.x, source.y, ally.x, ally.y) <= 230f * u
                }
                if (allies.isEmpty()) {
                    source.supportCd = 1.4f
                    return
                }
                allies.forEach { ally -> ally.applyStatus(StatusType.RAGE, 3.2f, 0.22f) }
                rings.add(RingFx(source.x, source.y, 230f * u, 0.48f, 0.48f, 0xFFFB923C))
                burst(source.x, source.y, 9, 0xFFFB923C, 100f * u, 0.32f)
                float(source.x, source.y - source.radius - 16f, "催阵鼓!", 251, 146, 60, 1.05f)
                source.supportCd = if (source.elite) 5.2f else 6.4f
            }
            else -> Unit
        }
    }

    private fun castSignatureBossMove(boss: Actor, move: BossMove) {
        val phase = boss.bossPhase
        val warmup = (1.05f - phase * 0.1f).coerceAtLeast(0.78f)
        val predictedX = (player.x + player.vx * 0.35f).coerceIn(pad + player.radius, width - pad - player.radius)
        val predictedY = (player.y + player.vy * 0.35f).coerceIn(pad + player.radius, height - pad - player.radius)
        val accent = bossEncounter?.accent ?: boss.kind.burstColor()
        val damage = boss.atk * (0.88f + phase * 0.08f)
        var first = true

        fun add(
            shape: BossHazardShape,
            x0: Float,
            y0: Float,
            x1: Float = x0,
            y1: Float = y0,
            outer: Float = 0f,
            inner: Float = 0f,
            laneWidth: Float = 0f,
            delay: Float = warmup,
            damageMul: Float = 1f,
            slow: Boolean = false
        ) {
            bossHazards.add(
                BossHazard(
                    shape = shape,
                    x0 = x0,
                    y0 = y0,
                    x1 = x1,
                    y1 = y1,
                    outerRadius = outer,
                    innerRadius = inner,
                    width = laneWidth,
                    life = delay,
                    damage = damage * damageMul,
                    color = accent,
                    label = if (first) move.title else "",
                    slowOnHit = slow
                )
            )
            first = false
        }

        fun centeredLine(cx: Float, cy: Float, angle: Float, laneWidth: Float, delay: Float = warmup) {
            val len = max(width, height) * 1.45f
            val dx = cos(angle) * len
            val dy = sin(angle) * len
            add(BossHazardShape.LINE, cx - dx, cy - dy, cx + dx, cy + dy, laneWidth = laneWidth, delay = delay)
        }

        when (move) {
            BossMove.SYRUP_METEORS -> {
                for (i in -1..1) {
                    add(
                        BossHazardShape.CIRCLE,
                        (predictedX + i * 78f * u).coerceIn(pad, width - pad),
                        predictedY,
                        outer = (58f + phase * 6f) * u,
                        delay = warmup + (i + 1) * 0.12f,
                        slow = true
                    )
                }
            }
            BossMove.CORE_RING -> add(
                BossHazardShape.RING, boss.x, boss.y,
                outer = (175f + phase * 16f) * u,
                inner = (78f - phase * 5f).coerceAtLeast(55f) * u,
                damageMul = 1.15f
            )
            BossMove.MINECART_RIFT -> {
                val angle = atan2(predictedY - boss.y, predictedX - boss.x)
                centeredLine(boss.x, boss.y, angle, (48f + phase * 6f) * u)
            }
            BossMove.CRYSTAL_CROSS -> {
                centeredLine(predictedX, predictedY, 0f, (38f + phase * 5f) * u)
                centeredLine(predictedX, predictedY, PI.toFloat() * 0.5f, (38f + phase * 5f) * u, warmup + 0.1f)
            }
            BossMove.ROYAL_SEAL -> {
                add(BossHazardShape.CIRCLE, predictedX, predictedY, outer = (82f + phase * 8f) * u, damageMul = 1.12f)
                add(
                    BossHazardShape.CIRCLE,
                    (width - predictedX).coerceIn(pad, width - pad),
                    (height - predictedY).coerceIn(pad, height - pad),
                    outer = 64f * u,
                    delay = warmup + 0.18f
                )
            }
            BossMove.EMPTY_ECHO -> {
                add(BossHazardShape.RING, boss.x, boss.y, outer = 142f * u, inner = 68f * u)
                add(
                    BossHazardShape.RING, boss.x, boss.y,
                    outer = (255f + phase * 12f) * u,
                    inner = 178f * u,
                    delay = warmup + 0.22f,
                    damageMul = 1.05f
                )
            }
            BossMove.TIDAL_LANES -> {
                centeredLine(width * 0.5f, predictedY, 0f, 58f * u)
                val secondY = if (predictedY < height * 0.5f) predictedY + 145f * u else predictedY - 145f * u
                centeredLine(width * 0.5f, secondY.coerceIn(pad, height - pad), 0f, 58f * u, warmup + 0.2f)
            }
            BossMove.SEA_VORTEX -> add(
                BossHazardShape.RING, width * 0.5f, height * 0.52f,
                outer = (235f + phase * 12f) * u,
                inner = 92f * u,
                damageMul = 1.08f,
                slow = true
            )
            BossMove.FIVE_STROKES -> {
                val start = atan2(predictedY - boss.y, predictedX - boss.x)
                repeat(5) { i ->
                    centeredLine(boss.x, boss.y, start + i * (PI.toFloat() / 5f), (30f + phase * 4f) * u)
                }
            }
            BossMove.FINAL_SIGNATURE -> {
                val points = listOf(
                    predictedX to predictedY,
                    (width - predictedX) to predictedY,
                    predictedX to (height - predictedY),
                    (width - predictedX) to (height - predictedY)
                )
                points.forEachIndexed { index, (x, y) ->
                    add(
                        BossHazardShape.CIRCLE,
                        x.coerceIn(pad, width - pad),
                        y.coerceIn(pad, height - pad),
                        outer = (64f + phase * 5f) * u,
                        delay = warmup + index * 0.09f,
                        damageMul = 1.12f
                    )
                }
            }
        }

        boss.hitStun = max(boss.hitStun, warmup * 0.55f)
        bossPhaseLine = "${bossEncounter?.title ?: "首领"} · ${move.title} · 走位!"
        float(boss.x, boss.y - boss.radius - 20f, "蓄势·${move.title}", 248, 113, 113, 1.15f)
        rings.add(RingFx(boss.x, boss.y, boss.radius * 2.4f, 0.5f, 0.5f, accent))
        hapticEvent = max(hapticEvent, 2)
    }

    private fun tickBossHazards(d: Float) {
        var index = 0
        while (index < bossHazards.size) {
            val hazard = bossHazards[index]
            hazard.life -= d
            if (hazard.life > 0f) {
                index++
                continue
            }

            val hit = hazard.contains(player.x, player.y, player.radius)
            if (hit) {
                damagePlayer(hazard.damage)
                if (hazard.slowOnHit) player.applyStatus(StatusType.SLOW, 1.5f, 0.45f)
            } else if (hazard.label.isNotEmpty()) {
                float(player.x, player.y - player.radius - 10f, "闪避!", 74, 222, 128, 1.05f)
            }

            when (hazard.shape) {
                BossHazardShape.CIRCLE -> {
                    rings.add(RingFx(hazard.x0, hazard.y0, hazard.outerRadius, 0.32f, 0.32f, hazard.color))
                    leaveInkWash(hazard.x0, hazard.y0, hazard.outerRadius * 0.65f, 11f, hazard.color and 0x66FFFFFF)
                    burst(hazard.x0, hazard.y0, 10, hazard.color, 120f * u, 0.35f)
                }
                BossHazardShape.RING -> {
                    rings.add(RingFx(hazard.x0, hazard.y0, hazard.outerRadius, 0.38f, 0.38f, hazard.color))
                    rings.add(RingFx(hazard.x0, hazard.y0, hazard.innerRadius, 0.38f, 0.38f, hazard.color))
                    leaveInkWash(hazard.x0, hazard.y0, hazard.outerRadius * 0.38f, 12f, hazard.color and 0x55FFFFFF)
                }
                BossHazardShape.LINE -> {
                    leaveInkStroke(
                        hazard.x0, hazard.y0, hazard.x1, hazard.y1,
                        hazard.width * 0.72f, 15f, hazard.color
                    )
                    burst(hazard.x0, hazard.y0, 6, hazard.color, 95f * u, 0.28f)
                    burst(hazard.x1, hazard.y1, 6, hazard.color, 95f * u, 0.28f)
                }
            }
            shake = max(shake, if (hit) 0.55f else 0.32f)
            impactFlash = max(impactFlash, if (hit) 0.32f else 0.16f)
            bossHazards.removeAt(index)
        }
    }

    private fun bossPhaseAnnounce(kind: EnemyKind, phase: Int): String = when (kind) {
        EnemyKind.BOSS_SLIME -> bossEncounter?.let { if (phase == 1) it.phaseOne else it.phaseTwo }
            ?: if (phase == 1) "糖浆沸腾" else "果核裸露"
        EnemyKind.BOSS_ORE -> bossEncounter?.let { if (phase == 1) it.phaseOne else it.phaseTwo }
            ?: if (phase == 1) "矿脉震颤" else "空罐回响"
        else -> if (phase == 1) "墨意翻涌" else "绝笔将至"
    }

    private fun bossSkillName(kind: EnemyKind, slot: Int): String = when (kind) {
        EnemyKind.BOSS_SLIME -> when (slot) {
            0 -> "糖浆震"
            1 -> "黏弹雨"
            2 -> "果冻冲"
            else -> "分身软泥"
        }
        EnemyKind.BOSS_ORE -> when (slot) {
            0 -> "岩脉砸"
            1 -> "碎晶射"
            2 -> "矿车冲"
            else -> "唤石卫"
        }
        else -> when (slot) {
            0 -> "墨圈"
            1 -> "笔锋扇"
            2 -> "走笔冲"
            else -> "点墨兵"
        }
    }

    private fun spawnBossMinion(boss: Actor, count: Int) {
        val kind = when (boss.kind) {
            EnemyKind.BOSS_ORE -> EnemyKind.BEETLE
            EnemyKind.BOSS_SLIME -> EnemyKind.SLIME
            else -> EnemyKind.RAT
        }
        repeat(count) {
            if (enemies.count { !it.dead } >= 12) return
            val ang = prng.nextFloat() * 6.28f
            val hp = 38f * threatMul() * mods.enemyHpMul
            enemies.add(
                Actor(
                    x = (boss.x + cos(ang) * 80f * u).coerceIn(pad + 20f, width - pad - 20f),
                    y = (boss.y + sin(ang) * 80f * u).coerceIn(pad + 20f, height - pad - 20f),
                    hp = hp,
                    maxHp = hp,
                    radius = kind.baseRadius() * u,
                    atk = 10f * threatMul() * mods.enemyAtkMul,
                    speed = 120f * u * mods.enemySpdMul,
                    isPlayer = false,
                    kind = kind,
                    element = kind.element(),
                    ai = EnemyAi.CHASE
                )
            )
        }
    }

    /** 冰环已由魔法盾接替 S1（2026-08 平衡迭代）。 */
    private fun contactHit(e: Actor, raw: Float) {
        val dealt = damagePlayer(raw)
        if (dealt > 0f) {
            if (player.has(StatusType.REFLECT)) {
                damageEnemy(e, dealt * player.powerOf(StatusType.REFLECT).coerceAtLeast(0.2f))
            }
            // 魔法盾反制：盾期间咬到你的敌人被寒霜迟缓
            if (manaShieldT > 0f) {
                e.applyStatus(StatusType.SLOW, 2.5f, 0.5f)
                float(e.x, e.y - e.radius - 6f, "缓", 125, 211, 252, 0.9f)
                shardBurst(e.x, e.y, 3, 0xFFBAE6FD, 160f * u, 0.25f, 11f * u)
            }
        }
        // spike / elite thorns when they bite you
        if (dealt > 0f && e.thorns > 0f) {
            // already applied as part of their atk; extra chip
            damagePlayer(raw * e.thorns * 0.5f, ignoreInvuln = true, showFloat = false)
        }
    }

    private fun updateShots(d: Float) {
        var si = 0
        while (si < shots.size) {
            val s = shots[si]
            s.life -= d
            val ox = s.x
            val oy = s.y
            s.x += s.vx * d
            s.y += s.vy * d
            // 飞行中留下墨线，不是一串球
            if (s.fromPlayer && (si + (time * 40f).toInt()) % 2 == 0) {
                val col = when (s.style) {
                    1 -> 0x99B91C1C
                    2 -> 0x883F6212
                    3 -> 0x774A6741
                    6 -> 0x883F3F46
                    else -> 0x881C1410
                }
                leaveInkStroke(ox, oy, s.x, s.y, s.r * 0.9f, life = 8f + prng.nextFloat() * 4f, color = col)
            }
            var removed = false
            if (s.fromPlayer) {
                // meteors explode on ground arrival
                if (s.style == 6 && s.life < 0.08f) {
                    leaveInkWash(s.x, s.y, s.splash.coerceAtLeast(40f * u) * 0.55f, life = 14f, color = 0x552C1810)
                    leaveInkDots(s.x, s.y, 8)
                    for (e in enemies) {
                        if (e.dead) continue
                        if (dist(s.x, s.y, e.x, e.y) <= s.splash + e.radius) {
                            damageEnemy(e, s.dmg, heavy = true)
                            s.status?.let { e.applyStatus(it, s.statusT, s.statusPow) }
                        }
                    }
                    removed = true
                }
                for (e in enemies) {
                    if (removed || e.dead) continue
                    if (hitCircle(s.x, s.y, s.r, e.x, e.y, e.radius)) {
                        damageEnemy(e, s.dmg)
                        s.status?.let { e.applyStatus(it, s.statusT, s.statusPow) }
                        if (s.style == 3) e.applyStatus(StatusType.VULN, 2.5f, 0.15f)
                        // fireball splash
                        if (s.splash > 0f) {
                            rings.add(RingFx(s.x, s.y, s.splash, 0.28f, 0.28f, 0xFFFF6B35))
                            for (o in enemies) {
                                if (o.dead || o === e) continue
                                if (dist(s.x, s.y, o.x, o.y) <= s.splash + o.radius) {
                                    damageEnemy(o, s.dmg * 0.45f)
                                    s.status?.let { o.applyStatus(it, s.statusT * 0.6f, s.statusPow * 0.6f) }
                                }
                            }
                        }
                        // talisman on-hit heal
                        if (s.style == 2) healPlayer(2.5f + wLevel * 0.4f)
                        removed = true
                        break
                    }
                }
            } else {
                if (!player.dead && hitCircle(s.x, s.y, s.r, player.x, player.y, player.radius)) {
                    damagePlayer(s.dmg)
                    removed = true
                }
            }
            if (removed || s.life <= 0f || s.x < -50f || s.x > width + 50f || s.y < -50f || s.y > height + 50f) {
                shots.removeAt(si)
            } else si++
        }
    }

    private fun updateDrops(d: Float) {
        val magnetR = 170f * u
        var di = 0
        while (di < drops.size) {
            val drop = drops[di]
            drop.life -= d
            val dist = dist(player.x, player.y, drop.x, drop.y)
            if (dist < magnetR && dist > 1f) {
                val pull = (1f - dist / magnetR) * 450f * d
                drop.x += (player.x - drop.x) / dist * pull
                drop.y += (player.y - drop.y) / dist * pull
            }
            if (dist < player.radius + 28f) {
                when (drop.kind) {
                    1 -> {
                        healPlayer(player.maxHp * 0.12f)
                        float(player.x, player.y - 40f, "拾取回血", 74, 222, 128, 1.15f)
                        burst(player.x, player.y, 10, 0xFF86EFAC, 90f * u, 0.3f)
                    }
                    2 -> {
                        if (drop.itemId.isNotEmpty() && drop.itemId !in lootedWeaponIds) {
                            lootedWeaponIds.add(drop.itemId)
                            val n = WeaponCatalog.byId(drop.itemId)?.name ?: "武器"
                            float(player.x, player.y - 44f, "武器:$n", 251, 191, 36, 1.2f)
                        }
                    }
                    3 -> {
                        if (drop.itemId.isNotEmpty()) {
                            lootedTomeIds.add(drop.itemId)
                            val n = TomeCatalog.byId(drop.itemId)?.name ?: "残卷"
                            float(player.x, player.y - 44f, "残卷:$n", 167, 139, 250, 1.2f)
                        }
                    }
                    4 -> {
                        if (drop.itemId.isNotEmpty()) {
                            lootedItemIds.add(drop.itemId)
                            val n = ItemCatalog.byId(drop.itemId)?.name ?: "道具"
                            float(player.x, player.y - 44f, "道具:$n", 252, 165, 165, 1.2f)
                        }
                    }
                    5 -> {
                        if (drop.itemId.isNotEmpty() && drop.itemId !in lootedArmorIds) {
                            lootedArmorIds.add(drop.itemId)
                            val n = ArmorCatalog.byId(drop.itemId)?.name ?: "防具"
                            float(player.x, player.y - 44f, "防具:$n", 134, 239, 172, 1.2f)
                        }
                    }
                    6 -> {
                        if (drop.itemId.isNotEmpty() && drop.itemId !in lootedRingIds) {
                            lootedRingIds.add(drop.itemId)
                            val n = RingCatalog.byId(drop.itemId)?.name ?: "戒指"
                            float(player.x, player.y - 44f, "戒指:$n", 244, 114, 182, 1.2f)
                        }
                    }
                    7 -> {
                        if (drop.itemId.isNotEmpty() && drop.itemId !in lootedBootsIds) {
                            lootedBootsIds.add(drop.itemId)
                            val n = BootsCatalog.byId(drop.itemId)?.name ?: "鞋子"
                            float(player.x, player.y - 44f, "鞋子:$n", 56, 189, 248, 1.2f)
                        }
                    }
                    else -> {
                        goldEarned += drop.gold
                        float(player.x, player.y - 40f, "+${drop.gold}金", 250, 204, 21)
                        addUlt(1.5f)
                    }
                }
                drops.removeAt(di)
            } else if (drop.life <= 0f) drops.removeAt(di) else di++
        }
    }

    private fun updateFloats(d: Float) {
        var fi = 0
        while (fi < floats.size) {
            val f = floats[fi]
            f.life -= d
            f.y -= 42f * d
            if (f.life <= 0f) floats.removeAt(fi) else fi++
        }
    }

    private fun separateEnemies() {
        for (i in enemies.indices) {
            for (j in i + 1 until enemies.size) {
                val a = enemies[i]
                val b = enemies[j]
                if (a.dead || b.dead) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
                val minD = a.radius + b.radius
                if (dist < minD) {
                    val push = (minD - dist) * 0.5f
                    val nx = dx / dist
                    val ny = dy / dist
                    a.x -= nx * push
                    a.y -= ny * push
                    b.x += nx * push
                    b.y += ny * push
                }
            }
        }
    }

    private fun tickStatuses(a: Actor, d: Float) {
        var i = 0
        while (i < a.statuses.size) {
            val s = a.statuses[i]
            s.t -= d
            when (s.type) {
                StatusType.BURN -> if (!a.isPlayer) {
                    // damage over time tick ~ each 0.35s approximated
                    a.hp -= s.power * d
                    if (a.hp <= 0f && !a.dead) killEnemy(a)
                } else {
                    damagePlayer(s.power * d * 0.35f, ignoreInvuln = true, showFloat = false)
                }
                StatusType.POISON -> if (!a.isPlayer) {
                    // power = damage per second
                    a.hp -= s.power * d
                    if (a.hp <= 0f && !a.dead) killEnemy(a)
                }
                StatusType.FREEZE -> if (!a.isPlayer) {
                    a.vx = 0f
                    a.vy = 0f
                    a.chargeVx = 0f
                    a.chargeVy = 0f
                    a.windup = 0f
                }
                StatusType.SHIELD -> { /* handled in damage */ }
                else -> Unit
            }
            if (s.t <= 0f) a.statuses.removeAt(i) else i++
        }
    }

    private fun damageEnemy(e: Actor, raw: Float, heavy: Boolean = false) {
        var dmg = max(1f, raw)
        if (frenzyT > 0f) dmg *= 1.22f
        if (rageT > 0f && rageStacks > 0) dmg *= 1f + rageStacks * 0.03f
        // 五行已移除玩法惩罚：共鸣武器改为纯伤害增幅
        val wxMul = 1f + wuxingAmp
        dmg *= wxMul
        // 命中微反馈：每次命中都有白瞬环（打击感的基线）
        if (rings.size < 26) {
            rings.add(
                RingFx(
                    e.x + (prng.nextFloat() - 0.5f) * 12f * u,
                    e.y - e.radius * 0.4f + (prng.nextFloat() - 0.5f) * 8f * u,
                    e.radius * 1.5f, 0.13f, 0.13f, 0xFFFFF7ED
                )
            )
        }
        if (mods.eliteBonusDmg > 0f && (e.elite || e.ai == EnemyAi.BOSS ||
                e.kind == EnemyKind.BOSS_SLIME || e.kind == EnemyKind.BOSS_ORE)
        ) {
            dmg *= 1f + mods.eliteBonusDmg
        }
        // 处决：低血加伤
        if (combatProc == GearProc.EXECUTE && e.hp / e.maxHp.coerceAtLeast(1f) < 0.35f) {
            dmg *= 1f + combatProcPower
            if (heavy) float(e.x, e.y - e.radius - 28f, "处决!", 248, 113, 113, 1.05f)
        }
        // armor / shell DR
        dmg *= (1f - e.armor.coerceIn(0f, 0.5f))
        val guard = if (e.ai == EnemyAi.BOSS || e.kind.tacticalRole() == EnemyTacticalRole.GUARD) null else {
            enemies.firstOrNull { ally ->
                !ally.dead && ally.kind.tacticalRole() == EnemyTacticalRole.GUARD &&
                    dist(ally.x, ally.y, e.x, e.y) <= 125f * u
            }
        }
        if (guard != null) {
            dmg *= guardDamageMultiplier(guard.elite)
            if (heavy) {
                bolts.add(BoltFx(guard.x, guard.y, e.x, e.y, 0.22f, 0.22f, 0xFFFDE68A))
                float(e.x, e.y - e.radius - 30f, "护卫", 251, 191, 36, 0.8f)
            }
        }
        if (e.has(StatusType.VULN)) dmg *= 1f + e.powerOf(StatusType.VULN)
        var crit = false
        if (prng.nextFloat() < critChance + if (frenzyT > 0f) 0.06f else 0f) {
            dmg *= 1.85f
            crit = true
        }
        if (e.enraged) dmg *= 0.92f
        e.hp -= dmg
        addUlt(if (heavy) 3.5f else 2f + if (crit) 2f else 0f)
        if (wxMul > 1.1f && heavy) {
            float(e.x, e.y - e.radius - 20f, "克制!", 250, 204, 21, 1.05f)
        } else if (wxMul < 0.85f && heavy) {
            float(e.x, e.y - e.radius - 20f, "被克", 148, 163, 184, 0.95f)
        }
        if (fruitBurn && (heavy || prng.nextFloat() < 0.45f)) {
            e.applyStatus(StatusType.BURN, 2.4f, player.atk * 0.12f * burnAmp)
        }
        // 武器特效
        applyGearProcOnHit(e, dmg, heavy, crit)
        e.hitFlash = if (crit) 0.35f else 0.24f
        e.squash = 1f
        // shorter stun so packs keep pressure
        e.hitStun = if (crit) 0.12f else if (heavy) 0.08f else 0.045f
        val dx = e.x - player.x
        val dy = e.y - player.y
        val dd = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val knock = (if (crit) 10f else if (heavy) 7f else 4f) * u
        e.x = (e.x + dx / dd * knock).coerceIn(pad + e.radius, width - pad - e.radius)
        e.y = (e.y + dy / dd * knock).coerceIn(pad + e.radius, height - pad - e.radius)
        lastHitX = e.x
        lastHitY = e.y
        // thorns on hit (spike slime hurts back)
        if (e.thorns > 0f && !player.dead) {
            damagePlayer(dmg * e.thorns * 0.35f, ignoreInvuln = true, showFloat = false)
        }
        // 命中：墨晕+飞白，少粒子光球
        leaveInkWash(e.x, e.y, e.radius * (if (crit) 1.6f else 1.1f), life = 11f, color = if (crit) 0x55B91C1C else 0x441C1410)
        leaveInkDots(e.x, e.y, if (crit) 7 else if (heavy) 4 else 2)
        if (crit) {
            // 一笔短锋从中心散开
            val a0 = prng.nextFloat() * 6.28f
            leaveInkStroke(
                e.x, e.y,
                e.x + cos(a0) * e.radius * 2.2f, e.y + sin(a0) * e.radius * 2.2f,
                3.5f * u, life = 12f, color = 0xAAB91C1C
            )
        }
        // 轻命中几乎不晃镜；重击/暴击才压一下，避免整屏一跳一跳
        shake = max(shake, if (crit) 0.22f else if (heavy) 0.12f else 0.04f)
        hitStop = max(hitStop, if (crit) 0.055f else if (heavy) 0.035f else 0.015f)
        zoomPunch = max(zoomPunch, if (crit) 0.18f else if (heavy) 0.08f else 0.0f)
        impactFlash = max(impactFlash, if (crit) 0.22f else if (heavy) 0.08f else 0.0f)
        if (crit) slowMo = max(slowMo, 0.14f)
        hapticEvent = max(hapticEvent, if (crit) 2 else 1)
        comboCount++
        comboTimer = 1.85f * mods.comboWindowMul * (roomTrial?.comboWindowMul ?: 1f)
        maxCombo = max(maxCombo, comboCount)
        if (comboCount == 5 || comboCount == 10 || comboCount == 15 || comboCount == 20) {
            val title = comboTitle(comboCount).ifBlank { "${comboCount}连!" }
            float(player.x, player.y - player.radius - 30f, title, 251, 146, 60, 1.45f)
            addUlt(8f)
        }
        if (crit) float(e.x, e.y - e.radius - 12f, "暴击 ${dmg.toInt()}", 250, 204, 21, 1.55f)
        else float(e.x, e.y - e.radius - 6f, "${dmg.toInt()}", 255, 255, 255, 1.25f)
        if (comboCount >= 3 && comboCount % 2 == 1) {
            val title = comboTitle(comboCount).ifBlank { "${comboCount}连!" }
            float(player.x, player.y - player.radius - 24f, title, 251, 146, 60, 1.15f)
        }
        if (lifesteal > 0f) healPlayer(dmg * lifesteal)
        if (e.hp <= 0f) killEnemy(e)
    }

    private fun applyGearProcOnHit(e: Actor, dmg: Float, heavy: Boolean, crit: Boolean) {
        if (gearProcCooldown > 0f) return
        when (combatProc) {
            GearProc.BURN -> {
                if (heavy || prng.nextFloat() < 0.55f) {
                    e.applyStatus(StatusType.BURN, 2.6f, player.atk * combatProcPower.coerceAtLeast(0.1f) * burnAmp)
                    if (heavy) float(e.x, e.y - e.radius - 34f, "燃!", 255, 107, 53, 0.95f)
                    markGearProcTriggered()
                }
            }
            GearProc.FREEZE -> {
                val chance = combatProcPower + if (crit) 0.12f else 0f
                if (prng.nextFloat() < chance) {
                    e.applyStatus(StatusType.FREEZE, 1.4f + combatProcPower, 1f)
                    float(e.x, e.y - e.radius - 34f, "冻!", 125, 211, 252, 1.0f)
                    markGearProcTriggered()
                }
            }
            GearProc.POISON -> {
                e.applyStatus(StatusType.POISON, 3.2f, player.atk * combatProcPower.coerceAtLeast(0.08f))
                if (heavy) float(e.x, e.y - e.radius - 34f, "毒!", 163, 230, 53, 0.95f)
                markGearProcTriggered()
            }
            GearProc.SPLASH -> {
                if (heavy || crit) {
                    val r = 95f * u * (1f + combatProcPower)
                    for (o in enemies) {
                        if (o.dead || o === e) continue
                        if (dist(e.x, e.y, o.x, o.y) < r) {
                            o.hp -= dmg * combatProcPower.coerceIn(0.15f, 0.55f)
                            o.hitFlash = 0.18f
                            if (o.hp <= 0f) killEnemy(o)
                        }
                    }
                    rings.add(RingFx(e.x, e.y, r * 0.6f, 0.2f, 0.2f, 0xFFFBBF24))
                    markGearProcTriggered()
                }
            }
            GearProc.MP_SIPHON -> {
                val gain = combatProcPower.coerceAtLeast(2f) + if (heavy) 2f else 0f
                mp = min(maxMp, mp + gain)
                if (heavy) float(player.x, player.y - 30f, "+${gain.toInt()}蓝", 125, 211, 252, 0.9f)
                markGearProcTriggered()
            }
            GearProc.LIFESTEAL_PROC -> {
                healPlayer(dmg * combatProcPower.coerceIn(0.02f, 0.12f))
                markGearProcTriggered()
            }
            GearProc.RAGE_ON_HIT -> {
                rageStacks = (rageStacks + 1).coerceAtMost(6)
                rageT = 2.4f
                if (rageStacks >= 3 && heavy) float(player.x, player.y - 42f, "战意x$rageStacks", 248, 113, 113, 0.95f)
                markGearProcTriggered()
            }
            GearProc.CHAIN -> {
                if (heavy && prng.nextFloat() < 0.45f + combatProcPower) {
                    val next = enemies.filter { !it.dead && it !== e }
                        .minByOrNull { dist(e.x, e.y, it.x, it.y) }
                    if (next != null && dist(e.x, e.y, next.x, next.y) < 200f * u) {
                        next.hp -= dmg * 0.35f
                        next.hitFlash = 0.2f
                        rings.add(RingFx(next.x, next.y, next.radius * 1.2f, 0.18f, 0.18f, 0xFFA78BFA))
                        if (next.hp <= 0f) killEnemy(next)
                        markGearProcTriggered()
                    }
                }
            }
            else -> Unit
        }
    }

    private fun killEnemy(e: Actor) {
        if (e.dead) return
        e.dead = true
        e.hp = 0f
        e.squash = 1f
        if (combatProc == GearProc.KILL_SHIELD && gearProcCooldown <= 0f) {
            player.applyStatus(StatusType.SHIELD, 2.2f, player.maxHp * combatProcPower.coerceIn(0.08f, 0.22f))
            float(player.x, player.y - 40f, "杀意护盾", 251, 191, 36, 1.0f)
            markGearProcTriggered()
        }
        val g = ((goldPerKill + prng.nextInt(0, 6)) * goldMul).toInt().coerceAtLeast(1)
        drops.add(Drop(e.x, e.y, g, kind = 0))
        if (prng.nextFloat() < 0.16f + if (e.elite) 0.1f else 0f) {
            drops.add(Drop(e.x + 12f, e.y - 8f, gold = 0, kind = 1))
        }
        // 普通怪主要掉金；装备聚焦精英/Boss，掉落才有期待感。
        val lootChance = when {
            e.ai == EnemyAi.BOSS -> 0.70f
            e.elite -> 0.30f
            else -> 0.04f
        } + mods.dropBonus
        if (prng.nextFloat() < lootChance) {
            val roll = prng.nextFloat()
            when {
                roll < 0.38f -> {
                    WeaponCatalog.randomDrop(hero, lootRarityCap)?.let { w ->
                        drops.add(Drop(e.x - 14f, e.y + 8f, 0, kind = 2, itemId = w.id, life = 18f))
                    }
                }
                roll < 0.52f -> {
                    RingCatalog.randomDrop(hero, lootRarityCap, lootTierCap)?.let { r ->
                        drops.add(Drop(e.x - 10f, e.y + 6f, 0, kind = 6, itemId = r.id, life = 18f))
                    }
                }
                roll < 0.66f -> {
                    BootsCatalog.randomDrop(hero, lootRarityCap, lootTierCap)?.let { b ->
                        drops.add(Drop(e.x + 10f, e.y + 6f, 0, kind = 7, itemId = b.id, life = 18f))
                    }
                }
                roll < 0.80f -> {
                    val t = TomeCatalog.random()
                    drops.add(Drop(e.x + 12f, e.y + 10f, 0, kind = 3, itemId = t.id, life = 18f))
                }
                else -> {
                    val f = ItemCatalog.randomFruit()
                    drops.add(Drop(e.x - 6f, e.y - 12f, 0, kind = 4, itemId = f.id, life = 18f))
                }
            }
        }
        // 精英/Boss 额外再掉一次装备
        if ((e.elite || e.ai == EnemyAi.BOSS) && prng.nextFloat() < 0.25f) {
            val extra = prng.nextFloat()
            when {
                extra < 0.45f -> WeaponCatalog.randomDrop(hero, lootRarityCap)?.let { w ->
                    drops.add(Drop(e.x + 18f, e.y - 6f, 0, kind = 2, itemId = w.id, life = 20f))
                }
                extra < 0.70f -> RingCatalog.randomDrop(hero, lootRarityCap, lootTierCap)?.let { r ->
                    drops.add(Drop(e.x + 16f, e.y - 4f, 0, kind = 6, itemId = r.id, life = 20f))
                }
                else -> BootsCatalog.randomDrop(hero, lootRarityCap, lootTierCap)?.let { b ->
                    drops.add(Drop(e.x + 14f, e.y - 8f, 0, kind = 7, itemId = b.id, life = 20f))
                }
            }
        }
        // 防具掉落
        val armorChance = when {
            e.ai == EnemyAi.BOSS -> 0.35f
            e.elite -> 0.14f
            else -> 0.015f
        }
        if (prng.nextFloat() < armorChance) {
            ArmorCatalog.randomDrop(hero, lootRarityCap, lootTierCap)?.let { a ->
                drops.add(Drop(e.x + 8f, e.y + 14f, 0, kind = 5, itemId = a.id, life = 18f))
            }
        }
        val baseXp = when (e.ai) {
            EnemyAi.BOSS -> 12
            EnemyAi.CHARGER -> 4
            EnemyAi.RANGED -> 3
            EnemyAi.CHASE -> 2
        } + when (e.kind) {
            EnemyKind.BOSS_SLIME, EnemyKind.BOSS_ORE -> 8
            EnemyKind.SPIKE_SLIME, EnemyKind.BEETLE, EnemyKind.SKELETON -> 1
            else -> 0
        } + if (e.elite) 3 else 0
        xpEarned += (baseXp * mods.xpMul).toInt().coerceAtLeast(1)
        roomKills++
        addUlt(12f + if (e.elite) 8f else 0f + if (e.ai == EnemyAi.BOSS) 20f else 0f)
        // kill streak frenzy（墨暴词缀更易触发）
        val need = mods.frenzyKillNeed.coerceIn(3, 8)
        if (roomKills == need || roomKills == need * 2 || roomKills == need * 3) {
            frenzyT = max(frenzyT, 4.5f)
            float(player.x, player.y - 60f, "墨气狂涌! x$roomKills", 248, 113, 113, 1.45f)
            rings.add(RingFx(player.x, player.y, player.radius * 3f, 0.45f, 0.45f, 0xFFEF4444))
            player.applyStatus(StatusType.RAGE, 3.5f, 0.18f)
            hapticEvent = max(hapticEvent, 2)
        }
        // 击破：一摊墨 + 几笔散锋，不炸金球
        leaveInkWash(e.x, e.y, e.radius * 2.4f, life = 20f, color = 0x661C1410)
        leaveInkDots(e.x, e.y, 8)
        for (k in 0..3) {
            val a = k * 1.57f + prng.nextFloat() * 0.4f
            leaveInkStroke(
                e.x, e.y,
                e.x + cos(a) * e.radius * 2.8f,
                e.y + sin(a) * e.radius * 2.8f,
                3f * u, life = 16f, color = 0xAA1C1410
            )
        }
        float(e.x, e.y - e.radius - 18f, e.kind.displayName(), 60, 50, 40, 0.9f)
        shake = max(shake, 0.28f)
        hitStop = max(hitStop, 0.06f)
        zoomPunch = max(zoomPunch, 0.22f)
        impactFlash = max(impactFlash, 0.45f)
        slowMo = max(slowMo, 0.4f)
        hapticEvent = max(hapticEvent, 3)
        float(e.x, e.y - 50f, "击破!", 250, 204, 21, 1.55f)
    }

    private fun damagePlayer(raw: Float, ignoreInvuln: Boolean = false, showFloat: Boolean = true): Float {
        if (player.dead) return 0f
        if (!ignoreInvuln && playerInvuln > 0f) {
            if (showFloat) float(player.x, player.y - 36f, "闪!", 125, 211, 252, 0.9f)
            return 0f
        }
        var dmg = max(0.5f, raw * dmgTakenMul)
        val sh = player.statuses.firstOrNull { it.type == StatusType.SHIELD && it.t > 0f }
        if (sh != null && sh.power > 0f) {
            val absorb = min(sh.power, dmg)
            sh.power -= absorb
            dmg -= absorb
            if (sh.power <= 0f) sh.t = 0f
            burst(player.x, player.y, 5, 0xFFFBBF24, 60f * u, 0.25f)
        }
        if (dmg <= 0f) {
            if (showFloat) float(player.x, player.y - player.radius, "格挡", 251, 191, 36, 1.1f)
            return 0f
        }
        // 反震：受伤反弹给最近敌人
        if (combatProc == GearProc.THORNS && dmg > 0f) {
            nearestEnemy()?.let { foe ->
                if (!foe.dead) {
                    foe.hp -= dmg * combatProcPower.coerceIn(0.1f, 0.35f)
                    foe.hitFlash = 0.15f
                    if (foe.hp <= 0f) killEnemy(foe)
                }
            }
        }
        player.hp -= dmg
        playerDamageTaken += dmg
        player.hitFlash = 0.28f
        shake = max(shake, 0.32f)
        hitStop = max(hitStop, 0.04f)
        hapticEvent = max(hapticEvent, 4)
        burst(player.x, player.y, 8, 0xFFEF4444, 90f * u, 0.3f)
        if (!ignoreInvuln) {
            // shorter i-frames — can't face-tank packs forever
            playerInvuln = 0.28f
            nearestEnemy()?.let { e ->
                val dx = player.x - e.x
                val dy = player.y - e.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                player.x = (player.x + dx / dist * 8f * u).coerceIn(pad + player.radius, width - pad - player.radius)
                player.y = (player.y + dy / dist * 8f * u).coerceIn(pad + player.radius, height - pad - player.radius)
            }
        }
        if (showFloat) float(player.x, player.y - player.radius - 8f, "-${dmg.toInt()}", 248, 113, 113, 1.25f)
        // 濒死残墨：本场一次，保命并短暂无敌
        if (!clutchUsed && player.hp > 0f && player.hp < player.maxHp * 0.14f) {
            clutchUsed = true
            player.hp = max(player.hp, player.maxHp * 0.12f)
            playerInvuln = max(playerInvuln, 1.1f)
            player.applyStatus(StatusType.RAGE, 2.2f, 0.2f)
            float(player.x, player.y - 50f, "残墨不灭!", 253, 224, 71, 1.5f)
            rings.add(RingFx(player.x, player.y, player.radius * 2.8f, 0.4f, 0.4f, 0xFFFBBF24))
            hapticEvent = max(hapticEvent, 2)
        }
        if (player.hp <= 0f) {
            // 最后一滴也试一次残墨（若未触发）
            if (!clutchUsed) {
                clutchUsed = true
                player.hp = player.maxHp * 0.15f
                player.dead = false
                playerInvuln = 1.35f
                float(player.x, player.y - 50f, "绝处残墨!", 253, 224, 71, 1.55f)
                rings.add(RingFx(player.x, player.y, player.radius * 3f, 0.45f, 0.45f, 0xFFF59E0B))
            } else {
                player.hp = 0f
                player.dead = true
                burst(player.x, player.y, 20, 0xFFEF4444, 180f * u, 0.5f)
            }
        }
        return dmg
    }

    private fun burst(
        x: Float,
        y: Float,
        n: Int,
        color: Long,
        speed: Float,
        life: Float,
        gravity: Float = 0f
    ) {
        for (i in 0 until n) {
            if (particles.size > 120) break
            val a = prng.nextFloat() * (PI * 2).toFloat()
            val sp = speed * (0.35f + prng.nextFloat() * 0.75f)
            particles.add(
                Particle(
                    x, y,
                    cos(a) * sp, sin(a) * sp,
                    life * (0.6f + prng.nextFloat() * 0.5f),
                    life,
                    (3f + prng.nextFloat() * 5f) * u,
                    color,
                    gravity
                )
            )
        }
    }

    private fun tickParticles(d: Float) {
        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.life -= d
            p.vy += p.gravity * d
            p.x += p.vx * d
            p.y += p.vy * d
            p.vx *= 0.98f
            p.vy *= 0.98f
            if (p.life <= 0f) particles.removeAt(i) else i++
        }
    }

    private fun tickCastFx(d: Float) {
        var i = 0
        while (i < skillCastsFx.size) {
            val fx = skillCastsFx[i]
            fx.life -= d
            if (fx.life <= 0f) skillCastsFx.removeAt(i) else i++
        }
        i = 0
        while (i < bolts.size) {
            val fx = bolts[i]
            fx.life -= d
            if (fx.life <= 0f) bolts.removeAt(i) else i++
        }
    }

    fun healPlayer(amount: Float) {
        val before = player.hp
        player.hp = min(player.maxHp, player.hp + amount)
        if (player.hp > before) {
            healPulse = 0.4f
            float(player.x, player.y - 36f, "+${(player.hp - before).toInt()}", 74, 222, 128)
        }
    }

    private fun aimAngle(target: Actor?): Float =
        if (target != null) atan2(target.y - player.y, target.x - player.x)
        else if (player.facing > 0f) 0f else PI.toFloat()

    private fun nearestEnemy(): Actor? {
        var best: Actor? = null
        var bestD = Float.MAX_VALUE
        for (e in enemies) {
            if (e.dead) continue
            val d = dist(player.x, player.y, e.x, e.y)
            if (d < bestD) {
                bestD = d
                best = e
            }
        }
        return best
    }

    private fun float(x: Float, y: Float, text: String, r: Int, g: Int, b: Int, scale: Float = 1f) {
        floats.add(FloatTxt(x + prng.nextFloat() * 10f - 5f, y, text, 0.95f, r, g, b, scale))
    }

    private fun hitCircle(x: Float, y: Float, r: Float, cx: Float, cy: Float, cr: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        val rr = r + cr
        return dx * dx + dy * dy <= rr * rr
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }
}
