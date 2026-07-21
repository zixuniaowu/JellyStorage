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
    var bossPhase: Int = 0
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
    var splash: Float = 0f
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
    var kind: Int = 0 // 0 poison 1 array

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
    val mods: CombatMods = CombatMods.NONE
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

    val skills = skillsFor(hero)
    val player: Actor
    val enemies = ArrayList<Actor>(24)
    val shots = ArrayList<Shot>(96)
    val drops = ArrayList<Drop>(24)
    val floats = ArrayList<FloatTxt>(64)
    val rings = ArrayList<RingFx>(24)
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
    private var fightTimer: Float = 0f
    /** set when room clears: S / A / B / C */
    var clearGrade: String = ""
        private set
    var clearBonusGold: Int = 0
        private set
    var clearBonusXp: Int = 0
        private set
    /** 濒死「残墨」触发一次 */
    private var clutchUsed: Boolean = false
    /** 供 UI 读的最近一次 Boss 阶段台词（读后清空） */
    var bossPhaseLine: String = ""
        private set

    fun consumeBossPhaseLine(): String {
        val s = bossPhaseLine
        bossPhaseLine = ""
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
    /** 战意：命中叠层加速普攻体感 */
    private var rageStacks: Int = 0
    private var rageT: Float = 0f

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
    }

    /** Multiplier from chapter/room threat + wave number. */
    fun threatMul(wave: Int = waveIndex): Float {
        val base = 1.08f + threatLevel * 0.095f
        val waveMul = 1f + wave * 0.11f
        return base * waveMul
    }

    fun weaponName(): String = weaponUpgradeTable(hero)[wLevel].name
    fun skillCdLeft(slot: Int): Float = skillCd.getOrElse(slot) { 0f }
    fun skillUnlocked(slot: Int): Boolean {
        val need = skills.getOrNull(slot)?.unlockLevel ?: 1
        return heroLevel >= need
    }

    fun skillUnlockLevel(slot: Int): Int = skills.getOrNull(slot)?.unlockLevel ?: 1

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

    fun consumeHaptic(): Int {
        val h = hapticEvent
        hapticEvent = 0
        return h
    }

    fun update(
        dt: Float, stickX: Float, stickY: Float,
        basic: Boolean, s1: Boolean, s2: Boolean, s3: Boolean = false, s4: Boolean = false
    ) {
        if (finished) return
        // hit-stop: freeze world briefly for punchy hits
        if (hitStop > 0f) {
            hitStop -= dt
            if (shake > 0f) shake = max(0f, shake - dt * 2.8f)
            if (impactFlash > 0f) impactFlash = max(0f, impactFlash - dt * 6f)
            if (zoomPunch > 0f) zoomPunch = max(0f, zoomPunch - dt * 4f)
            tickParticles(dt * 0.4f)
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
        mp = min(maxMp, mp + (11f * mpRegenMul) * d)
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
            if (basic && skillReady(0)) castSkill(0)
            if (s1 && skillReady(1)) castSkill(1)
            if (s2 && skillReady(2)) castSkill(2)
            if (s3 && skillReady(3)) castSkill(3)
            if (s4 && skillReady(4)) castSkill(4)
        }

        updateEnemies(d)
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

    /** 清场保底：至少一把武器机会 + 常掉果实 */
    private fun grantClearLoot() {
        if (lootedWeaponIds.isEmpty()) {
            WeaponCatalog.randomDrop(hero)?.let { w ->
                lootedWeaponIds.add(w.id)
                float(player.x, player.y - 50f, "清场装备:${w.name}", 251, 191, 36, 1.35f)
            }
        }
        if (lootedArmorIds.isEmpty() && prng.nextFloat() < 0.4f) {
            ArmorCatalog.randomDrop(hero)?.let { a ->
                lootedArmorIds.add(a.id)
                float(player.x, player.y - 64f, "清场防具:${a.name}", 134, 239, 172, 1.2f)
            }
        }
        // 高评价额外武器
        if (clearGrade == "S" || (clearGrade == "A" && prng.nextFloat() < 0.35f)) {
            WeaponCatalog.randomDrop(hero)?.let { w ->
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
        clearBonusGold = (gradeGold * mods.clearRewardMul * mods.goldMul).toInt()
        clearBonusXp = (gradeXp * mods.clearRewardMul * mods.xpMul).toInt()
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
    }

    private fun spawnWave(idx: Int) {
        enemies.clear()
        val wave = waveList[idx]
        val tm = threatMul(idx)
        for (we in wave.enemies) {
            val elite = we.elite || (idx >= 2 && prng.nextFloat() < 0.16f + threatLevel * 0.025f + mods.eliteChanceBonus)
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
            val hp = we.hp * tm * kindMulHp * eliteHp * 1.12f * mods.enemyHpMul
            val atk = we.atk * tm * kindMulAtk * eliteAtk * 1.12f * mods.enemyAtkMul
            val spd = we.speed * u * kindMulSpd * (1f + threatLevel * 0.025f) *
                (if (elite) 1.1f else 1f) * mods.enemySpdMul
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
                    kind = we.kind,
                    element = we.kind.element(),
                    ai = we.ai,
                    elite = elite,
                    thorns = when (we.kind) {
                        EnemyKind.SPIKE_SLIME -> 0.28f
                        EnemyKind.BEETLE -> if (elite) 0.12f else 0.06f
                        else -> 0f
                    },
                    armor = (kindArmor + eliteArmor).coerceIn(0f, 0.48f)
                )
            )
            if (elite) float(enemies.last().x, enemies.last().y - 30f, "精英!", 251, 146, 60, 1.1f)
        }
    }

    private fun castSkill(slot: Int) {
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
                0 -> fireball(target, player.atk * 1.15f * sm, StatusType.BURN, 2.5f, 8f * burnAmp)
                1 -> iceRing()
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
        slashFx = 1f
        slashWidth = 1.5f
    }

    private fun fireBlast(target: Actor?, sm: Float) {
        val tx = target?.x ?: (player.x + player.facing * 200f * u)
        val ty = target?.y ?: player.y
        rings.add(RingFx(tx, ty, 90f * u, 0.5f, 0.5f, 0xFFFF6B35))
        rings.add(RingFx(tx, ty, 50f * u, 0.45f, 0.45f, 0xFFFBBF24))
        burst(tx, ty, 24, 0xFFFF6B35, 220f * u, 0.5f)
        for (e in enemies) {
            if (e.dead) continue
            if (dist(tx, ty, e.x, e.y) <= 95f * u + e.radius) {
                damageEnemy(e, player.atk * 1.45f * sm, heavy = true)
                e.applyStatus(StatusType.BURN, 3.5f, 12f * burnAmp)
            }
        }
        float(tx, ty - 30f, "炎爆!", 255, 107, 53, 1.3f)
        shake = max(shake, 0.35f)
    }

    private fun sealBind(target: Actor?, sm: Float) {
        val focus = target ?: enemies.filter { !it.dead }.minByOrNull { dist(player.x, player.y, it.x, it.y) }
        val cx = focus?.x ?: player.x
        val cy = focus?.y ?: player.y
        rings.add(RingFx(cx, cy, 100f * u, 0.55f, 0.55f, 0xFFA78BFA))
        float(cx, cy - 36f, "镇符!", 167, 139, 250, 1.25f)
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
        while (cur != null && hops < 5 && hit.size < living.size) {
            hit.add(cur)
            damageEnemy(cur, player.atk * (1.05f - hops * 0.08f), heavy = hops == 0)
            cur.applyStatus(StatusType.VULN, 2.2f, 0.15f)
            // bolt visual as thin ring trail
            rings.add(RingFx(cur.x, cur.y, 28f * u, 0.28f, 0.28f, 0xFFA78BFA))
            burst(cur.x, cur.y, 6, 0xFFC4B5FD, 90f * u, 0.25f)
            // fake line via mid rings
            val mx = (prevX + cur.x) * 0.5f
            val my = (prevY + cur.y) * 0.5f
            rings.add(RingFx(mx, my, 12f * u, 0.15f, 0.15f, 0xFFE9D5FF))
            float(cur.x, cur.y - cur.radius - 6f, if (hops == 0) "雷击!" else "连锁!", 167, 139, 250, 0.95f)
            prevX = cur.x
            prevY = cur.y
            hops++
            val from = cur
            cur = living
                .filter { it !in hit }
                .filter { dist(from.x, from.y, it.x, it.y) < 220f * u }
                .minByOrNull { dist(from.x, from.y, it.x, it.y) }
        }
        float(player.x, player.y - 44f, "链雷 x$hops", 167, 139, 250, 1.2f)
        shake = max(shake, 0.25f)
    }

    /** Taoist S2: big heal + cleanse control. */
    private fun springHeal() {
        val amount = (player.maxHp * 0.32f + 20f + wLevel * 6f) * healAmp
        healPlayer(amount)
        // cleanse slow/freeze on self
        player.statuses.removeAll { it.type == StatusType.SLOW || it.type == StatusType.FREEZE || it.type == StatusType.POISON || it.type == StatusType.BURN }
        player.applyStatus(StatusType.SHIELD, 1.8f, player.maxHp * 0.08f)
        healPulse = 0.9f
        rings.add(RingFx(player.x, player.y, player.radius * 3f, 0.55f, 0.55f, 0xFF4ADE80))
        rings.add(RingFx(player.x, player.y, player.radius * 2f, 0.5f, 0.5f, 0xFF86EFAC))
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
        // 地上留一笔弧，不是光球
        leaveInkArc(player.x, player.y, ang, range * 0.92f, slashWidth)
        val dmgMul = mul * (1f + player.powerOf(StatusType.RAGE)) *
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
            if (kotlin.math.abs(da) < arc) {
                damageEnemy(e, player.atk * dmgMul, heavy = hits == 0)
                // 极轻粘滞，别把敌人拽得乱跳
                val dd = dist.coerceAtLeast(1f)
                e.x -= dx / dd * 2.5f * u
                e.y -= dy / dd * 2.5f * u
                hits++
            }
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
    }

    private fun fireball(target: Actor?, dmg: Float, st: StatusType?, stT: Float, stP: Float) {
        val ang = aimAngle(target)
        val sp = 460f * u
        shots.add(
            Shot(
                player.x + cos(ang) * player.radius, player.y + sin(ang) * player.radius,
                cos(ang) * sp, sin(ang) * sp, 1.7f, 15f * u, dmg, true, 1, st, stT, stP,
                splash = 52f * u
            )
        )
    }

    /** Taoist basic: 3 talismans in a fan (not a single mage bolt). */
    private fun talismanFan(target: Actor?) {
        val ang = aimAngle(target)
        val sp = 400f * u
        for (k in -1..1) {
            val a = ang + k * 0.22f
            shots.add(
                Shot(
                    player.x + cos(a) * player.radius,
                    player.y + sin(a) * player.radius,
                    cos(a) * sp, sin(a) * sp, 1.85f, 11f * u,
                    player.atk * (0.72f + if (k == 0) 0.18f else 0f),
                    true, 2, null, 0f, 0f
                )
            )
        }
        // small self heal on cast (sustain identity)
        healPlayer(3f + wLevel * 1.2f)
    }

    private fun poisonMist() {
        val rr = 120f * u
        // strong field DoT — poison is the identity
        fields.add(
            FieldFx(
                player.x, player.y, rr, 5.0f, 5.0f,
                dps = player.atk * 0.85f,
                healPerSec = 0f,
                color = 0xFFA3E635,
                kind = 0
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
        float(player.x, player.y - 44f, "毒雾 · 中毒$n", 163, 230, 53, 1.2f)
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

    private fun iceRing() {
        val r = 145f * u
        rings.add(RingFx(player.x, player.y, r, 0.65f, 0.65f, 0xFF7DD3FC))
        rings.add(RingFx(player.x, player.y, r * 0.7f, 0.55f, 0.55f, 0xFFE0F2FE))
        rings.add(RingFx(player.x, player.y, r * 0.4f, 0.45f, 0.45f, 0xFFBAE6FD))
        var frozen = 0
        for (e in enemies) {
            if (e.dead) continue
            if (dist(player.x, player.y, e.x, e.y) <= r + e.radius) {
                damageEnemy(e, player.atk * 0.85f)
                // hard freeze: cannot move/act
                e.applyStatus(StatusType.FREEZE, 2.0f, 1f)
                e.applyStatus(StatusType.SLOW, 3.5f, 0.7f)
                e.vx = 0f
                e.vy = 0f
                e.chargeVx = 0f
                e.chargeVy = 0f
                e.windup = 0f
                float(e.x, e.y - e.radius - 8f, "冻结!", 125, 211, 252, 1.15f)
                burst(e.x, e.y, 8, 0xFF7DD3FC, 80f * u, 0.35f)
                frozen++
            }
        }
        float(player.x, player.y - 44f, "冰环 · 冻结$frozen", 125, 211, 252, 1.2f)
        burst(player.x, player.y, 22, 0xFF7DD3FC, 160f * u, 0.45f)
        shake = max(shake, 0.22f)
    }

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
                    player.atk * 1.15f, true, 6,
                    StatusType.BURN, 2f, 10f * burnAmp,
                    splash = 70f * u
                )
            )
            rings.add(RingFx(mx, my, 28f * u, 0.5f, 0.5f, 0xFFA78BFA))
        }
    }

    private fun updateFields(d: Float) {
        var i = 0
        while (i < fields.size) {
            val f = fields[i]
            f.life -= d
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
                    if (f.kind == 0) {
                        e.applyStatus(StatusType.POISON, 1.2f, player.atk * 0.4f)
                        e.applyStatus(StatusType.SLOW, 0.5f, 0.3f)
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
            if (e.hitStun > 0f) continue // still recovering from hit
            val dx = player.x - e.x
            val dy = player.y - e.y
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            e.facing = if (dx >= 0f) 1f else -1f
            val slowed = if (e.has(StatusType.SLOW) || e.has(StatusType.FREEZE)) 0.45f else 1f
            if (e.has(StatusType.FREEZE)) continue

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
            val rage = if (e.enraged) 1.12f else 1f
            val phaseMul = 1f + e.bossPhase * 0.06f
            val spd = e.speed * slowed * rage * phaseMul

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

    private fun bossPhaseAnnounce(kind: EnemyKind, phase: Int): String = when (kind) {
        EnemyKind.BOSS_SLIME -> if (phase == 1) "糖浆沸腾" else "果核裸露"
        EnemyKind.BOSS_ORE -> if (phase == 1) "矿脉震颤" else "空罐回响"
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

    private fun contactHit(e: Actor, raw: Float) {
        val dealt = damagePlayer(raw)
        if (dealt > 0f && player.has(StatusType.REFLECT)) {
            damageEnemy(e, dealt * player.powerOf(StatusType.REFLECT).coerceAtLeast(0.2f))
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
        // 五行相克（共鸣武器提高克制）
        val wxMul = wuxingDamageMul(playerElement, e.element, wuxingAmp)
        dmg *= wxMul
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
        comboTimer = 1.85f * mods.comboWindowMul
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
        when (combatProc) {
            GearProc.BURN -> {
                if (heavy || prng.nextFloat() < 0.55f) {
                    e.applyStatus(StatusType.BURN, 2.6f, player.atk * combatProcPower.coerceAtLeast(0.1f) * burnAmp)
                    if (heavy) float(e.x, e.y - e.radius - 34f, "燃!", 255, 107, 53, 0.95f)
                }
            }
            GearProc.FREEZE -> {
                val chance = combatProcPower + if (crit) 0.12f else 0f
                if (prng.nextFloat() < chance) {
                    e.applyStatus(StatusType.FREEZE, 1.4f + combatProcPower, 1f)
                    float(e.x, e.y - e.radius - 34f, "冻!", 125, 211, 252, 1.0f)
                }
            }
            GearProc.POISON -> {
                e.applyStatus(StatusType.POISON, 3.2f, player.atk * combatProcPower.coerceAtLeast(0.08f))
                if (heavy) float(e.x, e.y - e.radius - 34f, "毒!", 163, 230, 53, 0.95f)
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
                }
            }
            GearProc.MP_SIPHON -> {
                val gain = combatProcPower.coerceAtLeast(2f) + if (heavy) 2f else 0f
                mp = min(maxMp, mp + gain)
                if (heavy) float(player.x, player.y - 30f, "+${gain.toInt()}蓝", 125, 211, 252, 0.9f)
            }
            GearProc.LIFESTEAL_PROC -> {
                healPlayer(dmg * combatProcPower.coerceIn(0.02f, 0.12f))
            }
            GearProc.RAGE_ON_HIT -> {
                rageStacks = (rageStacks + 1).coerceAtMost(6)
                rageT = 2.4f
                if (rageStacks >= 3 && heavy) float(player.x, player.y - 42f, "战意x$rageStacks", 248, 113, 113, 0.95f)
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
        if (combatProc == GearProc.KILL_SHIELD) {
            player.applyStatus(StatusType.SHIELD, 2.2f, player.maxHp * combatProcPower.coerceIn(0.08f, 0.22f))
            float(player.x, player.y - 40f, "杀意护盾", 251, 191, 36, 1.0f)
        }
        val g = ((goldPerKill + prng.nextInt(0, 6)) * goldMul).toInt().coerceAtLeast(1)
        drops.add(Drop(e.x, e.y, g, kind = 0))
        if (prng.nextFloat() < 0.16f + if (e.elite) 0.1f else 0f) {
            drops.add(Drop(e.x + 12f, e.y - 8f, gold = 0, kind = 1))
        }
        // 装备 / 残卷 / 果实 / 戒鞋 — 提高可见掉落率
        val lootChance = when {
            e.ai == EnemyAi.BOSS -> 0.85f
            e.elite -> 0.48f
            else -> 0.22f
        } + mods.dropBonus
        if (prng.nextFloat() < lootChance) {
            val roll = prng.nextFloat()
            when {
                roll < 0.38f -> {
                    WeaponCatalog.randomDrop(hero)?.let { w ->
                        drops.add(Drop(e.x - 14f, e.y + 8f, 0, kind = 2, itemId = w.id, life = 18f))
                    }
                }
                roll < 0.52f -> {
                    RingCatalog.randomDrop(hero)?.let { r ->
                        drops.add(Drop(e.x - 10f, e.y + 6f, 0, kind = 6, itemId = r.id, life = 18f))
                    }
                }
                roll < 0.66f -> {
                    BootsCatalog.randomDrop(hero)?.let { b ->
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
        if ((e.elite || e.ai == EnemyAi.BOSS) && prng.nextFloat() < 0.55f) {
            val extra = prng.nextFloat()
            when {
                extra < 0.45f -> WeaponCatalog.randomDrop(hero)?.let { w ->
                    drops.add(Drop(e.x + 18f, e.y - 6f, 0, kind = 2, itemId = w.id, life = 20f))
                }
                extra < 0.70f -> RingCatalog.randomDrop(hero)?.let { r ->
                    drops.add(Drop(e.x + 16f, e.y - 4f, 0, kind = 6, itemId = r.id, life = 20f))
                }
                else -> BootsCatalog.randomDrop(hero)?.let { b ->
                    drops.add(Drop(e.x + 14f, e.y - 8f, 0, kind = 7, itemId = b.id, life = 20f))
                }
            }
        }
        // 防具掉落
        val armorChance = when {
            e.ai == EnemyAi.BOSS -> 0.45f
            e.elite -> 0.22f
            else -> 0.08f
        }
        if (prng.nextFloat() < armorChance) {
            ArmorCatalog.randomDrop(hero)?.let { a ->
                drops.add(Drop(e.x + 8f, e.y + 14f, 0, kind = 5, itemId = a.id, life = 18f))
            }
        }
        val baseXp = when (e.ai) {
            EnemyAi.BOSS -> 40
            EnemyAi.CHARGER -> 14
            EnemyAi.RANGED -> 12
            EnemyAi.CHASE -> 8
        } + when (e.kind) {
            EnemyKind.BOSS_SLIME, EnemyKind.BOSS_ORE -> 20
            EnemyKind.SPIKE_SLIME, EnemyKind.BEETLE, EnemyKind.SKELETON -> 4
            else -> 0
        }
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
        hapticEvent = 3
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
        player.hitFlash = 0.28f
        shake = max(shake, 0.32f)
        hitStop = max(hitStop, 0.04f)
        hapticEvent = 4
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
