package com.jellystorage.play

import androidx.compose.ui.graphics.Color

/**
 * 五行：金木水火土 — 相克决定伤害倍率。
 * 金克木、木克土、土克水、水克火、火克金。
 */
enum class WuXing(val display: String, val short: String, val color: Color) {
    METAL("金", "金", Color(0xFFFBBF24)),
    WOOD("木", "木", Color(0xFF4ADE80)),
    WATER("水", "水", Color(0xFF38BDF8)),
    FIRE("火", "火", Color(0xFFEF4444)),
    EARTH("土", "土", Color(0xFFD97706));

    fun beats(other: WuXing): Boolean = when (this) {
        METAL -> other == WOOD
        WOOD -> other == EARTH
        EARTH -> other == WATER
        WATER -> other == FIRE
        FIRE -> other == METAL
    }
}

/** 克制 1.4x，被克 0.72x，同属/无关 1.0x */
/** 克制 1.35x（+五行增幅）；被克不再惩罚——五行是加分题不是必修课 */
fun wuxingDamageMul(attack: WuXing, defend: WuXing, amp: Float = 0f): Float = when {
    attack.beats(defend) -> 1.35f + amp
    else -> 1f
}

/** 能克制 [this] 的属性（打这种关推荐带什么） 火克金、金克木… */
fun WuXing.beatenBy(): WuXing = when (this) {
    WuXing.METAL -> WuXing.FIRE
    WuXing.WOOD -> WuXing.METAL
    WuXing.EARTH -> WuXing.WOOD
    WuXing.WATER -> WuXing.EARTH
    WuXing.FIRE -> WuXing.WATER
}

fun WuXing.gateName(): String = "${short}关"

fun EnemyKind.element(): WuXing = when (this) {
    EnemyKind.SLIME -> WuXing.WOOD
    EnemyKind.PINK_SLIME -> WuXing.WATER
    EnemyKind.SPIKE_SLIME -> WuXing.METAL
    EnemyKind.BEETLE -> WuXing.EARTH
    EnemyKind.BAT -> WuXing.WOOD
    EnemyKind.SKELETON -> WuXing.METAL
    EnemyKind.GOBLIN -> WuXing.WOOD
    EnemyKind.RAT -> WuXing.EARTH
    EnemyKind.WISP -> WuXing.FIRE
    EnemyKind.BOSS_SLIME -> WuXing.WOOD
    EnemyKind.BOSS_ORE -> WuXing.EARTH
}

/**
 * 武器特效：命中/击杀/被动触发，按职业打造手感。
 */
enum class GearProc(val title: String, val tip: String) {
    NONE("无", ""),
    BURN("燃刃", "命中点燃"),
    FREEZE("霜凝", "几率冻结"),
    POISON("毒浸", "命中上毒"),
    SPLASH("裂波", "普攻溅射周围"),
    MP_SIPHON("吸灵", "命中回蓝"),
    LIFESTEAL_PROC("嗜血", "额外吸血"),
    KILL_SHIELD("杀意", "击杀短暂护盾"),
    EXECUTE("处决", "低血敌人加伤"),
    WUXING_AMP("共鸣", "克制倍率提高"),
    HEAL_AMP("回春", "治疗量提高"),
    THORNS("反震", "受伤反弹"),
    RAGE_ON_HIT("战意", "命中叠攻速感"),
    CHAIN("连锁", "技能更易连锁感")
}

/** Active hit procs use short internal cooldowns so their HUD state is honest and readable. */
fun GearProc.cooldownSeconds(): Float = when (this) {
    GearProc.BURN -> 0.45f
    GearProc.FREEZE -> 1.4f
    GearProc.POISON -> 0.65f
    GearProc.SPLASH -> 0.9f
    GearProc.MP_SIPHON -> 0.5f
    GearProc.LIFESTEAL_PROC -> 0.35f
    GearProc.KILL_SHIELD -> 1.6f
    GearProc.RAGE_ON_HIT -> 0.3f
    GearProc.CHAIN -> 0.85f
    else -> 0f
}

fun GearProc.fxColor(): Long = when (this) {
    GearProc.BURN, GearProc.EXECUTE, GearProc.RAGE_ON_HIT -> 0xFFEF4444
    GearProc.FREEZE -> 0xFF7DD3FC
    GearProc.POISON -> 0xFFA3E635
    GearProc.SPLASH, GearProc.KILL_SHIELD -> 0xFFFBBF24
    GearProc.MP_SIPHON, GearProc.CHAIN -> 0xFFA78BFA
    GearProc.LIFESTEAL_PROC -> 0xFFF472B6
    GearProc.HEAL_AMP -> 0xFF4ADE80
    GearProc.WUXING_AMP -> 0xFF38BDF8
    GearProc.THORNS -> 0xFFD97706
    else -> 0xFFE7C98A
}

/**
 * 可装备武器：职业限定 + 属性面板 + 特效。
 * hero=null 为通用器，任何职业可装但属性略逊专武。
 */
data class GearWeapon(
    val id: String,
    val name: String,
    val element: WuXing,
    val atkBonus: Float,
    val cost: Int,
    val hero: HeroClass? = null,
    val rarity: Int = 0, // 0普通 1精良 2史诗
    val crit: Float = 0f,       // 额外暴击率 0~0.3
    val skillAmp: Float = 0f,   // 技能伤害 +%
    val lifeSteal: Float = 0f,  // 吸血 0~0.2
    val cdr: Float = 0f,        // 冷却缩减 0~0.25
    val hpBonus: Float = 0f,
    val mpBonus: Float = 0f,
    val spdBonus: Float = 0f,   // 移速 +%
    val drBonus: Float = 0f,    // 减伤
    val proc: GearProc = GearProc.NONE,
    val procPower: Float = 0f,
    val flavor: String = ""
) {
    fun rarityName(): String = when (rarity) {
        2 -> "史诗"
        1 -> "精良"
        else -> "普通"
    }

    fun classLabel(): String = hero?.displayName ?: "通用"

    fun canEquip(h: HeroClass): Boolean = hero == null || hero == h

    fun isClassMatch(h: HeroClass): Boolean = hero == h

    fun effectLine(): String =
        if (proc == GearProc.NONE) "特效:无"
        else "特效:${proc.title}·${proc.tip}"

    fun statsCompact(): String = buildString {
        append("攻+${atkBonus.toInt()}")
        if (crit > 0.01f) append(" 暴${(crit * 100).toInt()}%")
        if (skillAmp > 0.01f) append(" 技${(skillAmp * 100).toInt()}%")
        if (lifeSteal > 0.01f) append(" 吸${(lifeSteal * 100).toInt()}%")
        if (cdr > 0.01f) append(" 冷-${(cdr * 100).toInt()}%")
        if (hpBonus > 1f) append(" 血+${hpBonus.toInt()}")
        if (mpBonus > 1f) append(" 蓝+${mpBonus.toInt()}")
        if (spdBonus > 0.01f) append(" 速${(spdBonus * 100).toInt()}%")
        if (drBonus > 0.01f) append(" 防${(drBonus * 100).toInt()}%")
    }

    fun detailLines(): List<String> = listOf(
        "${rarityName()} · ${classLabel()} · [${element.short}]",
        statsCompact(),
        effectLine(),
        flavor.ifBlank { "—" }
    )
}

object WeaponCatalog {
    val all: List<GearWeapon> = listOf(
        // ── 起步 ──
        GearWeapon(
            "w_iron", "破旧铁剑", WuXing.METAL, 0f, 0, HeroClass.WARRIOR, 0,
            flavor = "营里发的练习刀"
        ),
        GearWeapon(
            "w_staff", "学徒法杖", WuXing.FIRE, 0f, 0, HeroClass.MAGE, 0,
            flavor = "还带着教室的粉笔灰"
        ),
        GearWeapon(
            "w_peach", "桃木剑", WuXing.WOOD, 0f, 0, HeroClass.TAOIST, 0,
            flavor = "师父削的第一把"
        ),

        // ── 战士：高攻、吸血、溅射、处决 ──
        GearWeapon(
            "w_steel", "精钢长刀", WuXing.METAL, 12f, 28, HeroClass.WARRIOR, 0,
            crit = 0.04f, hpBonus = 12f, proc = GearProc.SPLASH, procPower = 0.28f,
            flavor = "中路开刃，适合贴脸"
        ),
        GearWeapon(
            "w_blood", "血饮战刃", WuXing.FIRE, 20f, 48, HeroClass.WARRIOR, 1,
            lifeSteal = 0.08f, crit = 0.06f, proc = GearProc.LIFESTEAL_PROC, procPower = 0.04f,
            flavor = "越砍越热"
        ),
        GearWeapon(
            "w_quake", "裂地巨斧", WuXing.EARTH, 30f, 72, HeroClass.WARRIOR, 1,
            hpBonus = 35f, drBonus = 0.05f, proc = GearProc.SPLASH, procPower = 0.42f,
            flavor = "抡圆了砸一片"
        ),
        GearWeapon(
            "w_gold_spear", "庚金枪", WuXing.METAL, 34f, 90, HeroClass.WARRIOR, 2,
            crit = 0.10f, spdBonus = 0.06f, proc = GearProc.EXECUTE, procPower = 0.30f,
            flavor = "戳穿甲壳的长锋"
        ),
        GearWeapon(
            "w_flame", "炎魔斩", WuXing.FIRE, 44f, 115, HeroClass.WARRIOR, 2,
            lifeSteal = 0.06f, crit = 0.08f, proc = GearProc.BURN, procPower = 0.18f,
            flavor = "刀锋带火，专克金关"
        ),
        GearWeapon(
            "w_thund_edge", "雷鸣双刃", WuXing.METAL, 26f, 65, HeroClass.WARRIOR, 1,
            crit = 0.12f, cdr = 0.06f, proc = GearProc.RAGE_ON_HIT, procPower = 0.15f,
            flavor = "连斩会越打越快"
        ),
        GearWeapon(
            "w_guard_blade", "铁卫军刀", WuXing.EARTH, 18f, 52, HeroClass.WARRIOR, 1,
            hpBonus = 45f, drBonus = 0.08f, lifeSteal = 0.04f, proc = GearProc.THORNS, procPower = 0.18f,
            flavor = "砍人也能挡刀"
        ),

        // ── 法师：技能增幅、点燃/冻结、回蓝、冷却 ──
        GearWeapon(
            "m_flame", "烈焰短杖", WuXing.FIRE, 11f, 28, HeroClass.MAGE, 0,
            skillAmp = 0.10f, mpBonus = 15f, proc = GearProc.BURN, procPower = 0.14f,
            flavor = "入门火系触媒"
        ),
        GearWeapon(
            "m_ice", "寒霜法珠", WuXing.WATER, 18f, 48, HeroClass.MAGE, 1,
            skillAmp = 0.12f, cdr = 0.08f, proc = GearProc.FREEZE, procPower = 0.18f,
            flavor = "冰环更粘人"
        ),
        GearWeapon(
            "m_thunder", "雷纹长杖", WuXing.METAL, 26f, 70, HeroClass.MAGE, 1,
            skillAmp = 0.16f, crit = 0.08f, proc = GearProc.CHAIN, procPower = 0.12f,
            flavor = "链雷跳得更欢"
        ),
        GearWeapon(
            "m_wood_orb", "青木法球", WuXing.WOOD, 22f, 58, HeroClass.MAGE, 1,
            skillAmp = 0.10f, mpBonus = 30f, lifeSteal = 0.03f, proc = GearProc.MP_SIPHON, procPower = 4f,
            flavor = "打人还能回蓝"
        ),
        GearWeapon(
            "m_crown", "灭世火冠", WuXing.FIRE, 40f, 115, HeroClass.MAGE, 2,
            skillAmp = 0.22f, crit = 0.06f, mpBonus = 20f, proc = GearProc.BURN, procPower = 0.25f,
            flavor = "大招星火燎原"
        ),
        GearWeapon(
            "m_abyss", "渊水法卷", WuXing.WATER, 32f, 95, HeroClass.MAGE, 2,
            skillAmp = 0.18f, cdr = 0.12f, proc = GearProc.FREEZE, procPower = 0.28f,
            flavor = "控场专精"
        ),
        GearWeapon(
            "m_earth_tome", "坤土典籍", WuXing.EARTH, 24f, 68, HeroClass.MAGE, 1,
            skillAmp = 0.14f, hpBonus = 25f, drBonus = 0.04f, proc = GearProc.WUXING_AMP, procPower = 0.16f,
            flavor = "稳扎稳打的法系"
        ),

        // ── 道士：毒、治疗、吸灵、续航 ──
        GearWeapon(
            "t_talisman", "灵符短剑", WuXing.WOOD, 10f, 28, HeroClass.TAOIST, 0,
            lifeSteal = 0.05f, mpBonus = 12f, proc = GearProc.POISON, procPower = 0.12f,
            flavor = "符刃带毒"
        ),
        GearWeapon(
            "t_dust", "五行拂尘", WuXing.EARTH, 17f, 48, HeroClass.TAOIST, 1,
            skillAmp = 0.08f, hpBonus = 20f, proc = GearProc.HEAL_AMP, procPower = 0.25f,
            flavor = "回春更甜"
        ),
        GearWeapon(
            "t_seal", "天师印", WuXing.METAL, 24f, 70, HeroClass.TAOIST, 1,
            skillAmp = 0.12f, cdr = 0.08f, proc = GearProc.KILL_SHIELD, procPower = 0.15f,
            flavor = "镇杀生威"
        ),
        GearWeapon(
            "t_fire_charm", "离火符剑", WuXing.FIRE, 24f, 62, HeroClass.TAOIST, 1,
            crit = 0.06f, lifeSteal = 0.05f, proc = GearProc.BURN, procPower = 0.16f,
            flavor = "符火双修"
        ),
        GearWeapon(
            "t_gourd", "紫金葫芦", WuXing.WATER, 38f, 115, HeroClass.TAOIST, 2,
            skillAmp = 0.14f, lifeSteal = 0.08f, mpBonus = 25f, proc = GearProc.MP_SIPHON, procPower = 6f,
            flavor = "续航之王"
        ),
        GearWeapon(
            "t_poison_bell", "冥毒铃", WuXing.WOOD, 28f, 88, HeroClass.TAOIST, 2,
            skillAmp = 0.10f, crit = 0.05f, proc = GearProc.POISON, procPower = 0.22f,
            flavor = "毒雾更狠"
        ),
        GearWeapon(
            "t_jade", "玉清如意", WuXing.WATER, 20f, 55, HeroClass.TAOIST, 1,
            hpBonus = 30f, mpBonus = 20f, cdr = 0.06f, proc = GearProc.HEAL_AMP, procPower = 0.35f,
            flavor = "奶妈本命"
        ),

        // ── 通用（略弱于专武，换属性灵活） ──
        GearWeapon(
            "u_water_blade", "壬水短刃", WuXing.WATER, 18f, 48, null, 1,
            crit = 0.05f, lifeSteal = 0.04f, proc = GearProc.FREEZE, procPower = 0.12f,
            flavor = "谁都能拿的水刃"
        ),
        GearWeapon(
            "u_earth_hammer", "坤土锤", WuXing.EARTH, 20f, 52, null, 1,
            hpBonus = 28f, drBonus = 0.04f, proc = GearProc.SPLASH, procPower = 0.22f,
            flavor = "重、稳、不挑职业"
        ),
        GearWeapon(
            "u_wood_bow", "甲木灵弓", WuXing.WOOD, 18f, 48, null, 1,
            spdBonus = 0.08f, crit = 0.06f, proc = GearProc.POISON, procPower = 0.10f,
            flavor = "风里的木箭"
        ),
        GearWeapon(
            "u_penta", "五行轮", WuXing.METAL, 34f, 120, null, 2,
            skillAmp = 0.10f, crit = 0.06f, proc = GearProc.WUXING_AMP, procPower = 0.20f,
            flavor = "通吃五行的圆轮"
        )
        // 星火戒已移至 RingCatalog（饰品槽，不是武器）
    )

    fun byId(id: String): GearWeapon? = all.find { it.id == id }

    fun starter(hero: HeroClass): GearWeapon = when (hero) {
        HeroClass.WARRIOR -> byId("w_iron")!!
        HeroClass.MAGE -> byId("w_staff")!!
        HeroClass.TAOIST -> byId("w_peach")!!
    }

    fun usableBy(hero: HeroClass): List<GearWeapon> =
        all.filter { it.canEquip(hero) }

    /** 专武优先：同职业 > 通用 */
    fun shopOffers(
        hero: HeroClass,
        count: Int = 3,
        prefer: WuXing? = null,
        budget: Int = 999
    ): List<GearWeapon> {
        val pool = usableBy(hero).filter { it.cost > 0 }
        fun rank(w: GearWeapon): Int {
            val classBoost = when {
                w.hero == hero -> 0
                w.hero == null -> 40
                else -> 200
            }
            val elBoost = if (prefer != null && w.element == prefer) 0 else 80
            val priceGap = when {
                w.cost <= budget -> (budget - w.cost) / 8
                else -> 200 + (w.cost - budget)
            }
            return classBoost + elBoost + priceGap + w.rarity * 2
        }
        return pool.sortedBy { rank(it) }.distinctBy { it.id }.take(count)
    }

    fun randomDrop(hero: HeroClass, maxRarity: Int = 2): GearWeapon? {
        val pool = usableBy(hero).filter { it.cost > 0 && it.rarity <= maxRarity.coerceIn(0, 2) }
        if (pool.isEmpty()) return null
        // 70% 专武，30% 通用
        val classPool = pool.filter { it.hero == hero }
        val uniPool = pool.filter { it.hero == null }
        val useClass = classPool.isNotEmpty() && (uniPool.isEmpty() || kotlin.random.Random.nextFloat() < 0.72f)
        val base = if (useClass) classPool else uniPool.ifEmpty { pool }
        val roll = kotlin.random.Random.nextFloat()
        val filtered = when {
            roll < 0.55f -> base.filter { it.rarity == 0 }.ifEmpty { base }
            roll < 0.88f -> base.filter { it.rarity <= 1 }.ifEmpty { base }
            else -> base
        }
        return filtered.random()
    }
}

/** 技能残卷：永久提升技能强度，可掉落/购买 */
data class SkillTome(
    val id: String,
    val name: String,
    val powerBonus: Float,
    val cost: Int,
    val tip: String
)

object TomeCatalog {
    val basic = SkillTome("tome_basic", "入门残卷", 0.08f, 28, "技能伤害 +8%")
    val mid = SkillTome("tome_mid", "精修残卷", 0.12f, 48, "技能伤害 +12%")
    val high = SkillTome("tome_high", "奥义残卷", 0.18f, 80, "技能伤害 +18%")
    val all = listOf(basic, mid, high)
    fun byId(id: String) = all.find { it.id == id }
    fun random() = all.random()
}

/**
 * 可消耗道具：五行果实等。
 */
data class BagItemDef(
    val id: String,
    val name: String,
    val tip: String,
    val element: WuXing? = null,
    val cost: Int = 20,
    val effect: String
)

object ItemCatalog {
    val fruits: List<BagItemDef> = listOf(
        BagItemDef("fruit_metal", "金之果", "下一战武器视为金·攻+6", WuXing.METAL, 22, "element"),
        BagItemDef("fruit_wood", "木之果", "立即回血 28%", WuXing.WOOD, 18, "heal"),
        BagItemDef("fruit_water", "水之果", "立即回蓝 40%", WuXing.WATER, 18, "mp"),
        BagItemDef("fruit_fire", "火之果", "下一战攻击附带点燃", WuXing.FIRE, 22, "burn"),
        BagItemDef("fruit_earth", "土之果", "下一战受伤-12%", WuXing.EARTH, 22, "armor")
    )
    val all = fruits
    fun byId(id: String) = all.find { it.id == id }
    fun randomFruit() = fruits.random()
    fun shopFruits(count: Int = 2) = fruits.shuffled().take(count)
}
