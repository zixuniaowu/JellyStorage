package com.jellystorage.play

import androidx.compose.ui.graphics.Color

enum class HeroClass(
    val displayName: String,
    val desc: String,
    val color: Color,
    val baseHp: Float,
    val baseMp: Float,
    val baseSpeed: Float,
    val baseAtk: Float,
    val attackRange: Float,
    val attackInterval: Float,
    val style: AttackStyle
) {
    WARRIOR(
        "战士",
        "斩冲盾风裂 · 五行武器",
        Color(0xFFEF4444),
        baseHp = 270f, baseMp = 100f, baseSpeed = 250f, baseAtk = 28f,
        attackRange = 102f, attackInterval = 0.30f, style = AttackStyle.MELEE_SLASH
    ),
    MAGE(
        "法师",
        "火冰雷爆星 · 五行法器",
        Color(0xFF60A5FA),
        baseHp = 170f, baseMp = 150f, baseSpeed = 225f, baseAtk = 25f,
        attackRange = 360f, attackInterval = 0.42f, style = AttackStyle.FIREBALL
    ),
    TAOIST(
        "道士",
        "符毒春镇阵 · 五行法宝",
        Color(0xFF4ADE80),
        baseHp = 210f, baseMp = 125f, baseSpeed = 240f, baseAtk = 21f,
        attackRange = 280f, attackInterval = 0.38f, style = AttackStyle.SPIRIT_BOLT
    )
}

enum class AttackStyle { MELEE_SLASH, FIREBALL, SPIRIT_BOLT }

enum class SkillSlot { BASIC, S1, S2, S3, ULT }

data class SkillDef(
    val slot: SkillSlot,
    val name: String,
    val glyph: String,
    val cd: Float,
    val mp: Float,
    val tip: String,
    /** player level required to use (1 = from start) */
    val unlockLevel: Int = 1
)

/**
 * 每职业 5 技能：Lv1普攻 · Lv2 · Lv3 · Lv4 · Lv5必杀
 */
fun skillsFor(hero: HeroClass): List<SkillDef> = when (hero) {
    HeroClass.WARRIOR -> listOf(
        SkillDef(SkillSlot.BASIC, "斩击", "斩", 0.30f, 0f, "近战扇形·连斩加伤", 1),
        SkillDef(SkillSlot.S1, "冲锋", "冲", 4.2f, 16f, "突进重创·短无敌", 2),
        SkillDef(SkillSlot.S2, "铁壁", "盾", 6.5f, 18f, "护盾+反伤", 3),
        SkillDef(SkillSlot.S3, "旋风斩", "风", 5.5f, 22f, "自身周围旋斩多段", 4),
        SkillDef(SkillSlot.ULT, "墨马·裂地", "马", 11f, 34f, "必杀：全屏墨马奔袭·重创", 5)
    )
    HeroClass.MAGE -> listOf(
        SkillDef(SkillSlot.BASIC, "火球", "火", 0.42f, 0f, "火球溅射·点燃", 1),
        SkillDef(SkillSlot.S1, "冰环", "冰", 5.2f, 24f, "冻结·无法行动", 2),
        SkillDef(SkillSlot.S2, "链雷", "雷", 5.8f, 26f, "连锁弹射多目标", 3),
        SkillDef(SkillSlot.S3, "炎爆", "爆", 6.2f, 28f, "目标点范围大爆炸", 4),
        SkillDef(SkillSlot.ULT, "墨马·流火", "马", 11f, 40f, "必杀：全屏墨马·附燃", 5)
    )
    HeroClass.TAOIST -> listOf(
        SkillDef(SkillSlot.BASIC, "三符", "符", 0.38f, 0f, "三道灵符·命中回血", 1),
        SkillDef(SkillSlot.S1, "毒雾", "毒", 5.0f, 20f, "毒圈持续掉血+易伤", 2),
        SkillDef(SkillSlot.S2, "回春", "春", 6.0f, 22f, "大额回血·解控", 3),
        SkillDef(SkillSlot.S3, "镇符", "镇", 5.5f, 24f, "禁锢+范围减速", 4),
        SkillDef(SkillSlot.ULT, "墨马·天骑", "马", 11f, 36f, "必杀：全屏墨马·回血", 5)
    )
}

/** Levels that grant new skills — for level-up toast. */
fun skillUnlocksAtLevel(hero: HeroClass, level: Int): List<SkillDef> =
    skillsFor(hero).filter { it.unlockLevel == level }

enum class PassiveId(
    val title: String,
    val desc: String
) {
    ATK_UP("锋刃", "攻击 +12%"),
    HP_UP("体魄", "最大生命 +15%"),
    SPD_UP("疾步", "移速 +12%"),
    CRIT("会心", "暴击率 +18%"),
    CDR("迅捷", "技能冷却 -15%"),
    LIFESTEAL("嗜血", "造成伤害 8% 吸血"),
    MP_REGEN("灵泉", "回蓝 +40%"),
    ARMOR("坚甲", "受伤 -12%"),
    BURN_AMP("焦油", "燃烧伤害 +40%"),
    GOLD_FIND("财运", "掉金 +25%")
}

fun randomPassives(owned: Set<PassiveId>, count: Int = 3): List<PassiveId> {
    val pool = PassiveId.entries.filter { it !in owned }.shuffled()
    if (pool.isEmpty()) return PassiveId.entries.shuffled().take(count)
    return pool.take(count.coerceAtMost(pool.size))
}

data class WeaponUpgrade(val level: Int, val name: String, val atkBonus: Float, val cost: Int)
data class ArmorUpgrade(val level: Int, val name: String, val hpBonus: Float, val dr: Float, val cost: Int)

fun weaponUpgradeTable(hero: HeroClass): List<WeaponUpgrade> = when (hero) {
    HeroClass.WARRIOR -> listOf(
        WeaponUpgrade(0, "破旧铁剑", 0f, 0),
        WeaponUpgrade(1, "精钢长刀", 10f, 28),
        WeaponUpgrade(2, "血饮战刃", 20f, 50),
        WeaponUpgrade(3, "裂地巨斧", 34f, 80),
        WeaponUpgrade(4, "炎魔斩", 50f, 120)
    )
    HeroClass.MAGE -> listOf(
        WeaponUpgrade(0, "学徒法杖", 0f, 0),
        WeaponUpgrade(1, "烈焰短杖", 9f, 28),
        WeaponUpgrade(2, "寒霜法珠", 18f, 50),
        WeaponUpgrade(3, "雷纹长杖", 32f, 80),
        WeaponUpgrade(4, "灭世火冠", 48f, 120)
    )
    HeroClass.TAOIST -> listOf(
        WeaponUpgrade(0, "桃木剑", 0f, 0),
        WeaponUpgrade(1, "灵符短剑", 8f, 28),
        WeaponUpgrade(2, "五行拂尘", 17f, 50),
        WeaponUpgrade(3, "天师印", 30f, 80),
        WeaponUpgrade(4, "紫金葫芦", 46f, 120)
    )
}

fun armorUpgradeTable(): List<ArmorUpgrade> = listOf(
    ArmorUpgrade(0, "布衣", 0f, 0f, 0),
    ArmorUpgrade(1, "皮甲", 30f, 0.06f, 22),
    ArmorUpgrade(2, "锁子甲", 60f, 0.10f, 40),
    ArmorUpgrade(3, "精钢甲", 100f, 0.14f, 65),
    ArmorUpgrade(4, "玄铁铠", 150f, 0.18f, 100)
)

enum class StatusType {
    BURN, SLOW, POISON, SHIELD, RAGE, VULN, FREEZE, REFLECT
}

enum class EnemyAi {
    CHASE, RANGED, CHARGER, BOSS
}

data class WaveEnemy(
    val kind: EnemyKind,
    val ai: EnemyAi,
    val hp: Float,
    val atk: Float,
    val speed: Float = 110f,
    val elite: Boolean = false
)

data class WaveDef(val enemies: List<WaveEnemy>)

/** Collision radius scale (before arena u). */
fun EnemyKind.baseRadius(): Float = when (this) {
    EnemyKind.SLIME -> 22f
    EnemyKind.PINK_SLIME -> 20f
    EnemyKind.SPIKE_SLIME -> 24f
    EnemyKind.BEETLE -> 26f
    EnemyKind.BAT -> 18f
    EnemyKind.SKELETON -> 24f
    EnemyKind.GOBLIN -> 23f
    EnemyKind.RAT -> 17f
    EnemyKind.WISP -> 16f
    EnemyKind.BOSS_SLIME -> 42f
    EnemyKind.BOSS_ORE -> 40f
}

fun EnemyKind.displayName(): String = when (this) {
    EnemyKind.SLIME -> "绿史莱姆"
    EnemyKind.PINK_SLIME -> "粉史莱姆"
    EnemyKind.SPIKE_SLIME -> "刺壳史莱姆"
    EnemyKind.BEETLE -> "甲虫"
    EnemyKind.BAT -> "夜蝠"
    EnemyKind.SKELETON -> "骷髅"
    EnemyKind.GOBLIN -> "小哥布林"
    EnemyKind.RAT -> "矿鼠"
    EnemyKind.WISP -> "幽火"
    EnemyKind.BOSS_SLIME -> "草原霸主"
    EnemyKind.BOSS_ORE -> "矿脉魔"
}

fun EnemyKind.burstColor(): Long = when (this) {
    EnemyKind.SLIME -> 0xFF4ADE80
    EnemyKind.PINK_SLIME -> 0xFFF472B6
    EnemyKind.SPIKE_SLIME -> 0xFF94A3B8
    EnemyKind.BEETLE -> 0xFFFB923C
    EnemyKind.BAT -> 0xFF64748B
    EnemyKind.SKELETON -> 0xFFE2E8F0
    EnemyKind.GOBLIN -> 0xFF86EFAC
    EnemyKind.RAT -> 0xFFA8A29E
    EnemyKind.WISP -> 0xFFA78BFA
    EnemyKind.BOSS_SLIME -> 0xFFC084FC
    EnemyKind.BOSS_ORE -> 0xFFFBBF24
}

enum class NodeType {
    START, MOB, ELITE, GOLD, HEAL, TRAP, SHOP, BOSS, EXIT, EVENT, REST
}

data class MapNode(
    val id: Int,
    val type: NodeType,
    val nx: Float,
    val ny: Float,
    val name: String,
    val next: List<Int>,
    val waves: List<WaveDef> = emptyList(),
    val goldDrop: Int = 0,
    val trapDmg: Float = 0f,
    val eventId: String = "",
    /** short flavor under node name */
    val blurb: String = "",
    /** 关卡主五行（战斗房）；null 则从波次推断 */
    val element: WuXing? = null
)

/** 解析关卡五行：优先配置，否则从敌人投票 */
fun MapNode.resolvedElement(): WuXing? {
    element?.let { return it }
    if (waves.isEmpty()) return null
    val votes = waves.flatMap { it.enemies }.map { it.kind.element() }
    if (votes.isEmpty()) return null
    return votes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}

fun MapNode.recommendWeaponElement(): WuXing? = resolvedElement()?.beatenBy()

data class StageDef(
    val id: Int,
    val title: String,
    val tip: String,
    val nodes: List<MapNode>,
    val bgTop: Long,
    val bgBot: Long,
    val chapterIndex: Int = 0
)

private fun slime(n: Int, hp: Float, atk: Float) = WaveDef(
    List(n) { i ->
        val kind = when (i % 3) {
            0 -> EnemyKind.SLIME
            1 -> EnemyKind.PINK_SLIME
            else -> EnemyKind.SPIKE_SLIME
        }
        WaveEnemy(
            kind, EnemyAi.CHASE,
            hp * (if (kind == EnemyKind.SPIKE_SLIME) 1.4f else 1.25f),
            atk * 1.22f,
            110f + i * 5f
        )
    }
)

private fun mixed(hp: Float, atk: Float) = WaveDef(
    listOf(
        WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, hp * 1.25f, atk * 1.2f, 118f),
        WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, hp * 1.2f, atk * 1.28f, 132f),
        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, hp * 0.95f, atk * 1.18f, 152f),
        WaveEnemy(EnemyKind.BEETLE, EnemyAi.CHARGER, hp * 1.4f, atk * 1.32f, 142f),
        WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, hp * 1.35f, atk * 1.22f, 102f),
        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, hp * 0.9f, atk * 1.2f, 100f)
    )
)

private fun mineCrew(hp: Float, atk: Float) = WaveDef(
    listOf(
        WaveEnemy(EnemyKind.RAT, EnemyAi.CHASE, hp * 1.05f, atk * 1.12f, 165f),
        WaveEnemy(EnemyKind.RAT, EnemyAi.CHASE, hp * 1.05f, atk * 1.12f, 170f),
        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHASE, hp * 1.35f, atk * 1.22f, 110f),
        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, hp * 0.95f, atk * 1.28f, 102f),
        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, hp * 0.9f, atk * 1.18f, 158f),
        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHASE, hp * 1.3f, atk * 1.2f, 112f)
    )
)

/** 4–5 waves, denser packs. */
private fun longMob(hp: Float, atk: Float, hard: Boolean = false): List<WaveDef> {
    val m = if (hard) 1.4f else 1.22f
    return listOf(
        slime(4, hp * m, atk * m),
        mixed(hp * 1.15f * m, atk * 1.15f * m),
        WaveDef(listOf(
            WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, hp * 1.25f * m, atk * 1.2f * m, 134f),
            WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, hp * 1.2f * m, atk * 1.2f * m, 136f),
            WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, hp * 1.45f * m, atk * 1.25f * m, 102f),
            WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, hp * 1.0f * m, atk * 1.2f * m, 154f),
            WaveEnemy(EnemyKind.BEETLE, EnemyAi.CHARGER, hp * 1.5f * m, atk * 1.3f * m, 144f),
            WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, hp * 0.95f * m, atk * 1.25f * m, 100f),
            WaveEnemy(EnemyKind.PINK_SLIME, EnemyAi.CHASE, hp * 1.15f * m, atk * 1.15f * m, 120f)
        )),
        WaveDef(listOf(
            WaveEnemy(EnemyKind.BEETLE, EnemyAi.CHARGER, hp * 1.55f * m, atk * 1.35f * m, 146f, elite = true),
            WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, hp * 1.3f * m, atk * 1.25f * m, 136f),
            WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, hp * 1.3f * m, atk * 1.25f * m, 140f),
            WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, hp * 1.5f * m, atk * 1.28f * m, 104f),
            WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, hp * 1.05f * m, atk * 1.22f * m, 156f),
            WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, hp * 1.05f * m, atk * 1.22f * m, 160f),
            WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, hp * 1.0f * m, atk * 1.28f * m, 102f)
        )),
        if (hard) WaveDef(listOf(
            WaveEnemy(EnemyKind.BEETLE, EnemyAi.CHARGER, hp * 1.7f * m, atk * 1.4f * m, 148f, elite = true),
            WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, hp * 1.55f * m, atk * 1.3f * m, 105f, elite = true),
            WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, hp * 1.35f * m, atk * 1.3f * m, 138f),
            WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, hp * 1.1f * m, atk * 1.35f * m, 105f),
            WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, hp * 1.1f * m, atk * 1.25f * m, 158f)
        )) else slime(5, hp * 1.25f * m, atk * 1.2f * m)
    )
}

private fun longMine(hp: Float, atk: Float): List<WaveDef> = listOf(
    mineCrew(hp * 1.2f, atk * 1.2f),
    WaveDef(listOf(
        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHASE, hp * 1.4f, atk * 1.28f, 108f),
        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHASE, hp * 1.35f, atk * 1.28f, 112f),
        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, hp * 1.0f, atk * 1.32f, 98f),
        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, hp * 0.95f, atk * 1.22f, 160f),
        WaveEnemy(EnemyKind.RAT, EnemyAi.CHASE, hp * 1.1f, atk * 1.15f, 168f),
        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHARGER, hp * 1.45f, atk * 1.3f, 140f)
    )),
    mineCrew(hp * 1.35f, atk * 1.3f),
    WaveDef(listOf(
        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHARGER, hp * 1.65f, atk * 1.4f, 148f, elite = true),
        WaveEnemy(EnemyKind.RAT, EnemyAi.CHASE, hp * 1.15f, atk * 1.2f, 172f),
        WaveEnemy(EnemyKind.RAT, EnemyAi.CHASE, hp * 1.15f, atk * 1.2f, 175f),
        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, hp * 1.1f, atk * 1.35f, 102f),
        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, hp * 1.1f, atk * 1.35f, 104f),
        WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, hp * 1.3f, atk * 1.3f, 132f),
        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHASE, hp * 1.4f, atk * 1.28f, 110f)
    )),
    WaveDef(listOf(
        WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, hp * 1.8f, atk * 1.35f, 100f, elite = true),
        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, hp * 1.15f, atk * 1.3f, 100f),
        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHARGER, hp * 1.5f, atk * 1.35f, 145f)
    ))
)

fun stageDefs(): List<StageDef> = listOf(
    // ── Chapter 1: multi-fork prairie (risk / reward routes) ─────
    StageDef(
        1, "果冻草原", "五行关卡 · 买对武器再开打",
        bgTop = 0xFF0F172A, bgBot = 0xFF14532D, chapterIndex = 0,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.03f, 0.50f, "营地", listOf(1), blurb = "盟会临时落脚点"),
            // 木关：荐金
            MapNode(
                1, NodeType.MOB, 0.12f, 0.50f, "史莱姆窝", listOf(2, 3, 4),
                waves = listOf(
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, 48f, 7f, 110f),
                        WaveEnemy(EnemyKind.PINK_SLIME, EnemyAi.CHASE, 46f, 7f, 116f),
                        WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, 48f, 7f, 112f)
                    )),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, 62f, 9f, 98f),
                        WaveEnemy(EnemyKind.SLIME, EnemyAi.CHASE, 52f, 8f, 112f),
                        WaveEnemy(EnemyKind.PINK_SLIME, EnemyAi.CHASE, 50f, 8f, 118f),
                        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, 36f, 8f, 152f)
                    )),
                    slime(4, 56f, 9f),
                    mixed(60f, 10f)
                ),
                goldDrop = 16, blurb = "木关·够买入门刀", element = WuXing.WOOD
            ),
            // 第一岔：金币袋可开行商；中路直接商店；下路战斗
            MapNode(2, NodeType.GOLD, 0.22f, 0.18f, "金币袋", listOf(5), goldDrop = 28, blurb = "拿金·可买装"),
            MapNode(3, NodeType.SHOP, 0.22f, 0.50f, "早市铁匠", listOf(5), blurb = "第一站就能买装"),
            MapNode(
                4, NodeType.MOB, 0.22f, 0.82f, "黏液冲沟", listOf(5),
                waves = longMob(62f, 11f), goldDrop = 20, blurb = "木关·多经验", element = WuXing.WOOD
            ),
            // 水关：荐土
            MapNode(
                5, NodeType.MOB, 0.32f, 0.50f, "混战原", listOf(6, 7, 8),
                waves = longMob(70f, 12f), goldDrop = 18, blurb = "水关混编", element = WuXing.WATER
            ),
            MapNode(6, NodeType.REST, 0.42f, 0.18f, "篝火", listOf(9), blurb = "稳妥休整"),
            MapNode(7, NodeType.SHOP, 0.42f, 0.50f, "流动铁匠", listOf(9), blurb = "按下一关买克制"),
            MapNode(8, NodeType.EVENT, 0.42f, 0.82f, "神秘商人", listOf(9), eventId = "merchant", blurb = "交易有代价"),
            // 金关：荐火
            MapNode(
                9, NodeType.MOB, 0.52f, 0.50f, "泥沼小径", listOf(10, 11, 12),
                waves = longMob(80f, 13f), goldDrop = 20, blurb = "金关刺壳多", element = WuXing.METAL
            ),
            MapNode(10, NodeType.HEAL, 0.60f, 0.18f, "草泉", listOf(13), goldDrop = 8, blurb = "回血"),
            MapNode(
                11, NodeType.ELITE, 0.60f, 0.50f, "甲虫关卡", listOf(13),
                waves = longMob(88f, 14f, hard = true), goldDrop = 28, blurb = "土关精英", element = WuXing.EARTH
            ),
            MapNode(12, NodeType.EVENT, 0.60f, 0.82f, "流浪歌手", listOf(13), eventId = "bard", blurb = "赌一把"),
            MapNode(13, NodeType.SHOP, 0.70f, 0.50f, "铁匠铺", listOf(14, 15), blurb = "Boss前换装"),
            // 终点纵向拉开，标签走上下避让
            MapNode(
                14, NodeType.MOB, 0.80f, 0.20f, "甲虫林缘", listOf(16),
                waves = longMob(92f, 14f, hard = true), goldDrop = 22, blurb = "土关", element = WuXing.EARTH
            ),
            MapNode(
                15, NodeType.MOB, 0.80f, 0.80f, "哥布林哨", listOf(16),
                waves = longMob(90f, 14f, hard = true), goldDrop = 22, blurb = "木关", element = WuXing.WOOD
            ),
            MapNode(
                16, NodeType.ELITE, 0.88f, 0.50f, "精英甲虫", listOf(17),
                waves = listOf(
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BEETLE, EnemyAi.CHARGER, 220f, 18f, 142f, elite = true),
                        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, 110f, 14f, 150f),
                        WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, 130f, 15f, 100f),
                        WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, 115f, 15f, 132f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 95f, 14f, 100f)
                    )),
                    mixed(120f, 16f),
                    longMob(110f, 16f, hard = true).last()
                ),
                goldDrop = 30, blurb = "土关前哨", element = WuXing.EARTH
            ),
            MapNode(
                17, NodeType.BOSS, 0.93f, 0.50f, "草原霸主", listOf(18),
                waves = listOf(
                    WaveDef(listOf(WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 620f, 20f, 98f))),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 360f, 22f, 108f),
                        WaveEnemy(EnemyKind.PINK_SLIME, EnemyAi.CHASE, 90f, 14f, 122f),
                        WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, 100f, 15f, 128f),
                        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, 70f, 14f, 152f),
                        WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, 110f, 15f, 102f)
                    )),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 420f, 24f, 112f),
                        WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, 120f, 16f, 100f, elite = true),
                        WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, 120f, 16f, 105f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 90f, 16f, 100f)
                    )),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 480f, 26f, 115f),
                        WaveEnemy(EnemyKind.BEETLE, EnemyAi.CHARGER, 180f, 18f, 145f, elite = true),
                        WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, 120f, 16f, 130f)
                    ))
                ),
                goldDrop = 42, blurb = "木关Boss·荐金", element = WuXing.WOOD
            ),
            MapNode(18, NodeType.EXIT, 0.99f, 0.22f, "东行隘口", emptyList(), blurb = "通往矿道的风")
        )
    ),
    // ── Chapter 2: branched mine ─────────────────────────────────
    StageDef(
        2, "哭泣矿道", "五行矿脉 · 看关型买克制装",
        bgTop = 0xFF1E1B4B, bgBot = 0xFF422006, chapterIndex = 1,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.03f, 0.50f, "矿口", listOf(1, 2), blurb = "灯灭了一半"),
            MapNode(
                1, NodeType.MOB, 0.12f, 0.28f, "矿鼠群", listOf(3),
                waves = longMine(72f, 13f), goldDrop = 22, blurb = "土关", element = WuXing.EARTH
            ),
            MapNode(2, NodeType.EVENT, 0.12f, 0.72f, "古碑", listOf(3), eventId = "stele", blurb = "安静但诡异"),
            MapNode(3, NodeType.HEAL, 0.22f, 0.50f, "泉水", listOf(4, 5, 6), goldDrop = 10, blurb = "三路再分"),
            MapNode(
                4, NodeType.MOB, 0.34f, 0.22f, "骷髅队", listOf(7),
                waves = longMine(90f, 15f), goldDrop = 24, blurb = "金关", element = WuXing.METAL
            ),
            MapNode(
                5, NodeType.MOB, 0.34f, 0.50f, "混编巷", listOf(7),
                waves = longMine(88f, 15f), goldDrop = 24, blurb = "水关", element = WuXing.WATER
            ),
            MapNode(
                6, NodeType.MOB, 0.34f, 0.78f, "幽火廊", listOf(7),
                waves = listOf(
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 70f, 16f, 98f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 70f, 16f, 102f),
                        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, 60f, 15f, 155f),
                        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, 60f, 15f, 158f)
                    )),
                    mineCrew(95f, 16f),
                    longMine(100f, 16f)[2],
                    longMine(100f, 16f).last()
                ),
                goldDrop = 24, blurb = "火关", element = WuXing.FIRE
            ),
            MapNode(7, NodeType.SHOP, 0.46f, 0.50f, "黑市", listOf(8, 9, 10), blurb = "按下一关补克制"),
            MapNode(
                8, NodeType.ELITE, 0.58f, 0.22f, "矿精", listOf(11),
                waves = listOf(
                    WaveDef(listOf(WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 380f, 20f, 102f))),
                    mineCrew(120f, 17f),
                    longMine(115f, 17f).last()
                ),
                goldDrop = 34, blurb = "土关精英", element = WuXing.EARTH
            ),
            MapNode(
                9, NodeType.MOB, 0.58f, 0.50f, "塌方区", listOf(11),
                waves = longMine(110f, 17f), goldDrop = 26, blurb = "土关", element = WuXing.EARTH
            ),
            MapNode(10, NodeType.TRAP, 0.58f, 0.78f, "落石道", listOf(11), trapDmg = 55f, blurb = "受伤换进度"),
            MapNode(11, NodeType.REST, 0.70f, 0.50f, "避难所", listOf(12, 13), blurb = "矿工留下的被褥"),
            MapNode(
                12, NodeType.MOB, 0.80f, 0.32f, "锁链大厅", listOf(14),
                waves = longMine(120f, 18f), goldDrop = 28, blurb = "金关", element = WuXing.METAL
            ),
            MapNode(
                13, NodeType.ELITE, 0.80f, 0.68f, "符文守卫", listOf(14),
                waves = listOf(
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHARGER, 200f, 20f, 148f, elite = true),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 130f, 18f, 100f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 130f, 18f, 102f),
                        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHASE, 160f, 18f, 112f)
                    )),
                    longMine(125f, 18f).last()
                ),
                goldDrop = 32, blurb = "金关精英", element = WuXing.METAL
            ),
            MapNode(
                14, NodeType.BOSS, 0.90f, 0.50f, "矿脉魔", listOf(15),
                waves = listOf(
                    WaveDef(listOf(WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 700f, 22f, 92f))),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 420f, 24f, 110f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 140f, 18f, 100f),
                        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHARGER, 180f, 20f, 150f),
                        WaveEnemy(EnemyKind.RAT, EnemyAi.CHASE, 100f, 16f, 170f)
                    )),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 500f, 26f, 115f),
                        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHASE, 150f, 20f, 112f),
                        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHASE, 150f, 20f, 116f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 130f, 20f, 102f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 130f, 20f, 104f)
                    )),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 560f, 28f, 118f),
                        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHARGER, 200f, 22f, 150f, elite = true),
                        WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, 140f, 18f, 100f)
                    ))
                ),
                goldDrop = 48, blurb = "土关Boss·荐木", element = WuXing.EARTH
            ),
            MapNode(15, NodeType.EXIT, 0.98f, 0.50f, "天光裂缝", emptyList(), blurb = "第一次像样的呼吸")
        )
    ),
    // ── Chapter 3: denser capital ────────────────────────────────
    StageDef(
        3, "空罐王城", "终局五行 · 黑市换装再冲",
        bgTop = 0xFF2E1065, bgBot = 0xFF1C1917, chapterIndex = 2,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.03f, 0.50f, "城门残骸", listOf(1), blurb = "门栓是糖做的"),
            MapNode(
                1, NodeType.MOB, 0.12f, 0.50f, "卫队残部", listOf(2, 3, 4),
                waves = longMob(115f, 17f, hard = true), goldDrop = 26, blurb = "金关", element = WuXing.METAL
            ),
            MapNode(2, NodeType.SHOP, 0.24f, 0.22f, "典当行", listOf(5), blurb = "荐装克制下一关"),
            MapNode(
                3, NodeType.MOB, 0.24f, 0.50f, "城墙内廊", listOf(5),
                waves = longMob(120f, 18f, hard = true), goldDrop = 28, blurb = "火关", element = WuXing.FIRE
            ),
            MapNode(4, NodeType.EVENT, 0.24f, 0.78f, "旧档案", listOf(5), eventId = "archive", blurb = "赌天赋"),
            MapNode(
                5, NodeType.MOB, 0.36f, 0.50f, "广场乱斗", listOf(6, 7, 8),
                waves = longMob(128f, 18f, hard = true), goldDrop = 28, blurb = "木关", element = WuXing.WOOD
            ),
            MapNode(6, NodeType.REST, 0.48f, 0.22f, "钟楼底", listOf(9), blurb = "喘息"),
            MapNode(
                7, NodeType.ELITE, 0.48f, 0.50f, "卫队长", listOf(9),
                waves = longMob(135f, 19f, hard = true), goldDrop = 36, blurb = "金关精英", element = WuXing.METAL
            ),
            MapNode(8, NodeType.TRAP, 0.48f, 0.78f, "糖浆陷阱", listOf(9), trapDmg = 65f, blurb = "血换进度"),
            MapNode(
                9, NodeType.ELITE, 0.60f, 0.50f, "王室甲虫", listOf(10, 11),
                waves = listOf(
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BEETLE, EnemyAi.CHARGER, 260f, 22f, 148f, elite = true),
                        WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 340f, 20f, 102f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 140f, 19f, 100f),
                        WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, 140f, 18f, 132f)
                    )),
                    longMob(140f, 20f, hard = true).last(),
                    mixed(150f, 20f)
                ),
                goldDrop = 40, blurb = "土关精英", element = WuXing.EARTH
            ),
            MapNode(10, NodeType.HEAL, 0.72f, 0.32f, "圣坛残片", listOf(12), goldDrop = 12, blurb = "最后回血"),
            MapNode(11, NodeType.SHOP, 0.72f, 0.68f, "黑市摊", listOf(12), blurb = "Boss前换装"),
            MapNode(
                12, NodeType.MOB, 0.82f, 0.50f, "王座甬道", listOf(13),
                waves = longMob(150f, 21f, hard = true), goldDrop = 32, blurb = "火关", element = WuXing.FIRE
            ),
            MapNode(
                13, NodeType.BOSS, 0.92f, 0.50f, "空罐君王", listOf(14),
                waves = listOf(
                    WaveDef(listOf(WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 780f, 24f, 102f))),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 500f, 24f, 108f),
                        WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, 150f, 20f, 132f),
                        WaveEnemy(EnemyKind.GOBLIN, EnemyAi.CHASE, 150f, 20f, 136f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 140f, 20f, 100f)
                    )),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 560f, 26f, 112f),
                        WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 450f, 24f, 110f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 150f, 22f, 102f),
                        WaveEnemy(EnemyKind.SPIKE_SLIME, EnemyAi.CHASE, 160f, 20f, 100f, elite = true)
                    )),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 620f, 28f, 115f),
                        WaveEnemy(EnemyKind.BEETLE, EnemyAi.CHARGER, 240f, 24f, 150f, elite = true),
                        WaveEnemy(EnemyKind.SKELETON, EnemyAi.CHARGER, 220f, 22f, 148f, elite = true)
                    ))
                ),
                goldDrop = 60, blurb = "木关Boss·荐金", element = WuXing.WOOD
            ),
            MapNode(14, NodeType.EXIT, 0.98f, 0.50f, "封罐之门", emptyList(), blurb = "故事可以盖上了")
        )
    ),
    // ── Chapter 4: 墨海秘境（拉长周目）──
    StageDef(
        4, "墨海秘境", "画中有画 · 云涛藏锋",
        bgTop = 0xFFEDE6D6, bgBot = 0xFF9A9588, chapterIndex = 3,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.04f, 0.50f, "入卷口", listOf(1), blurb = "纸香扑面"),
            MapNode(1, NodeType.MOB, 0.14f, 0.50f, "墨沫潮", listOf(2, 3, 4),
                waves = longMob(160f, 22f, hard = true), goldDrop = 30, blurb = "水关", element = WuXing.WATER),
            MapNode(2, NodeType.GOLD, 0.26f, 0.20f, "漂金笺", listOf(5), goldDrop = 34, blurb = "浮在浪上"),
            MapNode(3, NodeType.SHOP, 0.26f, 0.50f, "画舫商", listOf(5), blurb = "舟上铁匠"),
            MapNode(4, NodeType.ELITE, 0.26f, 0.80f, "浪里甲", listOf(5),
                waves = longMob(175f, 23f, hard = true), goldDrop = 40, blurb = "土关精英", element = WuXing.EARTH),
            MapNode(5, NodeType.MOB, 0.40f, 0.50f, "云脊", listOf(6, 7, 8),
                waves = longMob(180f, 24f, hard = true), goldDrop = 32, blurb = "金关", element = WuXing.METAL),
            MapNode(6, NodeType.REST, 0.52f, 0.20f, "闲亭", listOf(9), blurb = "半日闲"),
            MapNode(7, NodeType.EVENT, 0.52f, 0.50f, "题诗处", listOf(9), eventId = "bard", blurb = "赌一句"),
            MapNode(8, NodeType.HEAL, 0.52f, 0.80f, "清泉砚", listOf(9), goldDrop = 12, blurb = "洗笔"),
            MapNode(9, NodeType.ELITE, 0.66f, 0.50f, "墨龙影", listOf(10, 11),
                waves = longMob(195f, 25f, hard = true), goldDrop = 44, blurb = "火关精英", element = WuXing.FIRE),
            MapNode(10, NodeType.SHOP, 0.78f, 0.28f, "朱印斋", listOf(12), blurb = "终局换装"),
            MapNode(11, NodeType.TRAP, 0.78f, 0.72f, "破纸渊", listOf(12), trapDmg = 70f, blurb = "慎行"),
            MapNode(12, NodeType.BOSS, 0.90f, 0.50f, "海眼巨鲲", listOf(13),
                waves = listOf(
                    WaveDef(listOf(WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 900f, 28f, 100f))),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 620f, 26f, 105f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 180f, 24f, 100f),
                        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, 160f, 22f, 150f)
                    )),
                    longMob(200f, 26f, hard = true).last()
                ),
                goldDrop = 70, blurb = "水关Boss·荐土", element = WuXing.WATER),
            MapNode(13, NodeType.EXIT, 0.98f, 0.50f, "出卷", emptyList(), blurb = "翻到下一页")
        )
    ),
    // ── Chapter 5: 画魂峰（终章）──
    StageDef(
        5, "画魂峰", "落款之前 · 最后一笔",
        bgTop = 0xFFE8F0E8, bgBot = 0xFF8FA88C, chapterIndex = 4,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.04f, 0.50f, "峰麓", listOf(1), blurb = "松风入袖"),
            MapNode(1, NodeType.MOB, 0.16f, 0.50f, "翠障", listOf(2, 3),
                waves = longMob(200f, 26f, hard = true), goldDrop = 34, blurb = "木关", element = WuXing.WOOD),
            MapNode(2, NodeType.SHOP, 0.30f, 0.28f, "山门铺", listOf(4), blurb = "荐装"),
            MapNode(3, NodeType.ELITE, 0.30f, 0.72f, "守峰兽", listOf(4),
                waves = longMob(215f, 27f, hard = true), goldDrop = 46, blurb = "金关精英", element = WuXing.METAL),
            MapNode(4, NodeType.MOB, 0.46f, 0.50f, "十八盘", listOf(5, 6, 7),
                waves = longMob(220f, 28f, hard = true), goldDrop = 36, blurb = "土关", element = WuXing.EARTH),
            MapNode(5, NodeType.HEAL, 0.60f, 0.22f, "天池", listOf(8), goldDrop = 14, blurb = "洗尘"),
            MapNode(6, NodeType.REST, 0.60f, 0.50f, "云榻", listOf(8), blurb = "小憩"),
            MapNode(7, NodeType.EVENT, 0.60f, 0.78f, "残碑", listOf(8), eventId = "stele", blurb = "古意"),
            MapNode(8, NodeType.ELITE, 0.74f, 0.50f, "峰影双煞", listOf(9),
                waves = longMob(240f, 29f, hard = true), goldDrop = 50, blurb = "火关精英", element = WuXing.FIRE),
            MapNode(9, NodeType.SHOP, 0.84f, 0.50f, "绝顶市", listOf(10), blurb = "Boss前"),
            MapNode(10, NodeType.BOSS, 0.93f, 0.50f, "画魂真形", listOf(11),
                waves = listOf(
                    WaveDef(listOf(WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 1000f, 30f, 100f))),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 700f, 28f, 108f),
                        WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 500f, 26f, 105f)
                    )),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 800f, 32f, 110f),
                        WaveEnemy(EnemyKind.BEETLE, EnemyAi.CHARGER, 280f, 28f, 150f, elite = true),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 200f, 26f, 100f)
                    ))
                ),
                goldDrop = 90, blurb = "木关Boss·荐金", element = WuXing.WOOD),
            MapNode(11, NodeType.EXIT, 0.99f, 0.22f, "落款", emptyList(), blurb = "一卷终了")
        )
    )
)

fun nodeColor(t: NodeType): Color = when (t) {
    NodeType.START -> Color(0xFF64748B)
    NodeType.MOB -> Color(0xFF4ADE80)
    NodeType.ELITE -> Color(0xFFFB923C)
    NodeType.GOLD -> Color(0xFFFACC15)
    NodeType.HEAL -> Color(0xFF34D399)
    NodeType.TRAP -> Color(0xFFEF4444)
    NodeType.SHOP -> Color(0xFF60A5FA)
    NodeType.BOSS -> Color(0xFFC084FC)
    NodeType.EXIT -> Color(0xFF2DD4BF)
    NodeType.EVENT -> Color(0xFFF472B6)
    NodeType.REST -> Color(0xFFFDBA74)
}

fun nodeGlyph(t: NodeType): String = when (t) {
    NodeType.START -> "起"
    NodeType.MOB -> "怪"
    NodeType.ELITE -> "精"
    NodeType.GOLD -> "金"
    NodeType.HEAL -> "泉"
    NodeType.TRAP -> "陷"
    NodeType.SHOP -> "店"
    NodeType.BOSS -> "Boss"
    NodeType.EXIT -> "通"
    NodeType.EVENT -> "事"
    NodeType.REST -> "休"
}

/** Short risk tag for map choices. */
fun nodeRiskTag(t: NodeType): String = when (t) {
    NodeType.MOB -> "战斗"
    NodeType.ELITE -> "高危"
    NodeType.BOSS -> "Boss"
    NodeType.TRAP -> "受伤"
    NodeType.GOLD -> "发财"
    NodeType.HEAL, NodeType.REST -> "休整"
    NodeType.SHOP -> "商店"
    NodeType.EVENT -> "抉择"
    NodeType.EXIT -> "通关"
    NodeType.START -> "起点"
}

fun nodeRiskHint(n: MapNode): String {
    val waves = n.waves.size
    val el = n.resolvedElement()
    val elTag = el?.let { "·${it.gateName()}" } ?: ""
    return when (n.type) {
        NodeType.MOB, NodeType.ELITE ->
            if (waves > 0) "${nodeRiskTag(n.type)}$elTag·${waves}波" else nodeRiskTag(n.type) + elTag
        NodeType.BOSS -> "Boss$elTag·${waves.coerceAtLeast(1)}波"
        NodeType.TRAP -> "陷阱-${n.trapDmg.toInt()}"
        NodeType.SHOP -> "商店·备战"
        else -> nodeRiskTag(n.type)
    }
}

fun nodeRecommendLine(n: MapNode): String {
    val el = n.resolvedElement() ?: return ""
    val rec = el.beatenBy()
    return "荐${rec.short}克${el.short}"
}
