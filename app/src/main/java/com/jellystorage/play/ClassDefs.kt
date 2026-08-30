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
        baseHp = 190f, baseMp = 150f, baseSpeed = 225f, baseAtk = 30f,
        attackRange = 360f, attackInterval = 0.38f, style = AttackStyle.FIREBALL
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
 * 每职业 5 技能：第一战解锁首个技能，之后隔级获得新招。
 * 完整招式跨越约 7~9 个战斗房，避免首关结束就失去成长目标。
 */
fun skillsFor(hero: HeroClass): List<SkillDef> = when (hero) {
    HeroClass.WARRIOR -> listOf(
        SkillDef(SkillSlot.BASIC, "斩击", "斩", 0.30f, 0f, "近战扇形·连斩加伤", 1),
        SkillDef(SkillSlot.S1, "冲锋", "冲", 4.2f, 16f, "突进重创·短无敌", 2),
        SkillDef(SkillSlot.S2, "铁壁", "盾", 6.5f, 18f, "护盾+反伤", 4),
        SkillDef(SkillSlot.S3, "旋风斩", "风", 4.8f, 20f, "自身周围旋斩多段", 6),
        SkillDef(SkillSlot.ULT, "墨马·裂地", "马", 11f, 34f, "必杀：全屏墨马奔袭·重创", 8)
    )
    HeroClass.MAGE -> listOf(
        SkillDef(SkillSlot.BASIC, "火球", "火", 0.42f, 0f, "火球溅射·点燃", 1),
        SkillDef(SkillSlot.S1, "魔法盾", "盾", 9f, 24f, "法盾护体·受击迟缓来敌", 1),
        SkillDef(SkillSlot.S2, "链雷", "雷", 5.0f, 24f, "全屏雷暴·连锁所有敌人", 4),
        SkillDef(SkillSlot.S3, "炎爆", "爆", 5.5f, 26f, "目标点范围大爆炸", 6),
        SkillDef(SkillSlot.ULT, "墨马·流火", "马", 11f, 40f, "必杀：全屏墨马·附燃", 8)
    )
    HeroClass.TAOIST -> listOf(
        SkillDef(SkillSlot.BASIC, "三符", "符", 0.38f, 0f, "三道灵符·命中回血", 1),
        SkillDef(SkillSlot.S1, "毒雾", "毒", 5.0f, 20f, "毒圈持续掉血+易伤", 2),
        SkillDef(SkillSlot.S2, "回春", "春", 6.0f, 22f, "大额回血·解控", 4),
        SkillDef(SkillSlot.S3, "镇符", "镇", 4.5f, 18f, "禁锢+范围减速", 6),
        SkillDef(SkillSlot.ULT, "墨马·天骑", "马", 11f, 36f, "必杀：全屏墨马·回血", 8)
    )
}

/** 当前等级升到下一级所需经验；前期快、后期逐步拉长。 */
fun xpRequirementForLevel(level: Int): Int = when (level.coerceAtLeast(1)) {
    1 -> 45
    2 -> 85
    3 -> 110
    4 -> 140
    5 -> 175
    6 -> 215
    7 -> 260
    else -> 260 + (level - 7) * 55
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
    EnemyKind.MINI_SLIME -> 13f
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
    EnemyKind.MINI_SLIME -> "芽孢体"
    EnemyKind.SLIME -> "球状菌"
    EnemyKind.PINK_SLIME -> "芽生菌"
    EnemyKind.SPIKE_SLIME -> "棘壳病毒"
    EnemyKind.BEETLE -> "护膜杆菌"
    EnemyKind.BAT -> "翼膜病毒"
    EnemyKind.SKELETON -> "坏死细胞"
    EnemyKind.GOBLIN -> "群落信使"
    EnemyKind.RAT -> "游走病菌"
    EnemyKind.WISP -> "孢子母体"
    EnemyKind.BOSS_SLIME -> "感染核心"
    EnemyKind.BOSS_ORE -> "变异核心"
}

fun EnemyKind.burstColor(): Long = when (this) {
    EnemyKind.MINI_SLIME -> 0xFF86EFAC
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
    START, MOB, ELITE, GOLD, HEAL, TRAP, SHOP, BOSS, EXIT, EVENT, REST, TREASURE, CHALLENGE
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
        1, "皮肤创口", "凝血边境 · 封住最初的入侵口",
        bgTop = 0xFFF3C4B2, bgBot = 0xFF8F3F4B, chapterIndex = 0,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.03f, 0.50f, "凝血前哨", listOf(1), blurb = "血小板正在搭建防线"),
            // 木关：荐金
            MapNode(
                1, NodeType.MOB, 0.12f, 0.50f, "菌落创面", listOf(2, 3, 4),
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
                goldDrop = 16, blurb = "首批病原体·荐金", element = WuXing.WOOD
            ),
            // 第一岔：金币袋可开行商；中路直接商店；下路战斗
            MapNode(2, NodeType.GOLD, 0.22f, 0.18f, "营养囊", listOf(5), goldDrop = 28, blurb = "吸收营养·强化装备"),
            MapNode(3, NodeType.SHOP, 0.22f, 0.50f, "抗体工坊", listOf(5), blurb = "调整首轮免疫装备"),
            MapNode(
                4, NodeType.MOB, 0.22f, 0.82f, "组织液沟", listOf(5),
                waves = longMob(62f, 11f), goldDrop = 20, blurb = "菌群密集·经验较多", element = WuXing.WOOD
            ),
            // 水关：荐土
            MapNode(
                5, NodeType.MOB, 0.32f, 0.50f, "炎症交界", listOf(6, 7, 8),
                waves = longMob(70f, 12f), goldDrop = 18, blurb = "细菌与病毒混编", element = WuXing.WATER
            ),
            MapNode(6, NodeType.REST, 0.42f, 0.18f, "血小板站", listOf(9), blurb = "稳固凝血屏障"),
            MapNode(7, NodeType.SHOP, 0.42f, 0.50f, "淋巴补给站", listOf(9), blurb = "按下一区域调整克制"),
            MapNode(8, NodeType.EVENT, 0.42f, 0.82f, "记忆细胞", listOf(9), eventId = "merchant", blurb = "交换免疫资源"),
            // 金关：荐火
            MapNode(
                9, NodeType.MOB, 0.52f, 0.50f, "表皮裂隙", listOf(10, 11, 12),
                waves = longMob(80f, 13f), goldDrop = 20, blurb = "棘壳病毒增多", element = WuXing.METAL
            ),
            MapNode(10, NodeType.HEAL, 0.60f, 0.18f, "修复因子", listOf(13), goldDrop = 8, blurb = "恢复细胞活性"),
            MapNode(
                11, NodeType.ELITE, 0.60f, 0.50f, "护膜菌阵", listOf(13),
                waves = longMob(88f, 14f, hard = true), goldDrop = 28, blurb = "耐药精英", element = WuXing.EARTH
            ),
            MapNode(12, NodeType.EVENT, 0.60f, 0.82f, "神经脉冲", listOf(13), eventId = "bard", blurb = "尝试激活应答"),
            MapNode(13, NodeType.SHOP, 0.70f, 0.50f, "抗体装配室", listOf(14, 15), blurb = "感染核心前换装"),
            // 终点纵向拉开，标签走上下避让
            MapNode(
                14, NodeType.MOB, 0.80f, 0.20f, "角质层边缘", listOf(16),
                waves = longMob(92f, 14f, hard = true), goldDrop = 22, blurb = "护膜菌聚集", element = WuXing.EARTH
            ),
            MapNode(
                15, NodeType.MOB, 0.80f, 0.80f, "毛囊哨口", listOf(16),
                waves = longMob(90f, 14f, hard = true), goldDrop = 22, blurb = "信使菌聚集", element = WuXing.WOOD
            ),
            MapNode(
                16, NodeType.ELITE, 0.88f, 0.50f, "耐药杆菌", listOf(17),
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
                goldDrop = 30, blurb = "感染核心前哨", element = WuXing.EARTH
            ),
            MapNode(
                17, NodeType.BOSS, 0.93f, 0.50f, "创口感染核心", listOf(18),
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
                goldDrop = 42, blurb = "皮肤Boss·封闭创口", element = WuXing.WOOD
            ),
            MapNode(18, NodeType.EXIT, 0.99f, 0.22f, "毛细血管", emptyList(), blurb = "随血流前往肺部")
        )
    ),
    // ── Chapter 2: branched mine ─────────────────────────────────
    StageDef(
        2, "肺泡云海", "呼吸回廊 · 在缺氧前清除飞沫病毒",
        bgTop = 0xFFCCECF4, bgBot = 0xFF47758B, chapterIndex = 1,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.03f, 0.50f, "支气管口", listOf(1, 2), blurb = "气流夹着陌生孢子"),
            MapNode(
                1, NodeType.MOB, 0.12f, 0.28f, "飞沫病毒群", listOf(3),
                waves = longMine(72f, 13f), goldDrop = 22, blurb = "高速游走病原体", element = WuXing.EARTH
            ),
            MapNode(2, NodeType.EVENT, 0.12f, 0.72f, "残留抗体", listOf(3), eventId = "stele", blurb = "读取旧感染记录"),
            MapNode(3, NodeType.HEAL, 0.22f, 0.50f, "氧气交换区", listOf(4, 5, 6), goldDrop = 10, blurb = "恢复活性后三路分流"),
            MapNode(
                4, NodeType.MOB, 0.34f, 0.22f, "坏死细胞带", listOf(7),
                waves = longMine(90f, 15f), goldDrop = 24, blurb = "金关", element = WuXing.METAL
            ),
            MapNode(
                5, NodeType.MOB, 0.34f, 0.50f, "肺泡混合区", listOf(7),
                waves = longMine(88f, 15f), goldDrop = 24, blurb = "水关", element = WuXing.WATER
            ),
            MapNode(
                6, NodeType.MOB, 0.34f, 0.78f, "孢子气道", listOf(7),
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
            MapNode(7, NodeType.SHOP, 0.46f, 0.50f, "肺门补给站", listOf(8, 9, 10), blurb = "按下一区域补充抗体"),
            MapNode(
                8, NodeType.ELITE, 0.58f, 0.22f, "孢子团块", listOf(11),
                waves = listOf(
                    WaveDef(listOf(WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 380f, 20f, 102f))),
                    mineCrew(120f, 17f),
                    longMine(115f, 17f).last()
                ),
                goldDrop = 34, blurb = "土关精英", element = WuXing.EARTH
            ),
            MapNode(
                9, NodeType.MOB, 0.58f, 0.50f, "塌陷肺泡", listOf(11),
                waves = longMine(110f, 17f), goldDrop = 26, blurb = "土关", element = WuXing.EARTH
            ),
            MapNode(10, NodeType.TRAP, 0.58f, 0.78f, "缺氧气道", listOf(11), trapDmg = 55f, blurb = "损失活性换取进度"),
            MapNode(11, NodeType.REST, 0.70f, 0.50f, "静息肺泡", listOf(12, 13), blurb = "短暂恢复氧合"),
            MapNode(
                12, NodeType.MOB, 0.80f, 0.32f, "纤毛长廊", listOf(14),
                waves = longMine(120f, 18f), goldDrop = 28, blurb = "金关", element = WuXing.METAL
            ),
            MapNode(
                13, NodeType.ELITE, 0.80f, 0.68f, "变异病毒卫", listOf(14),
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
                14, NodeType.BOSS, 0.90f, 0.50f, "肺部感染核心", listOf(15),
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
                goldDrop = 48, blurb = "肺部Boss·恢复氧合", element = WuXing.EARTH
            ),
            MapNode(15, NodeType.EXIT, 0.98f, 0.50f, "肺静脉", emptyList(), blurb = "含氧血流向胃肠区")
        )
    ),
    // ── Chapter 3: denser capital ────────────────────────────────
    StageDef(
        3, "胃肠菌林", "酸潮迷宫 · 分辨菌群与入侵者",
        bgTop = 0xFFEAA3B6, bgBot = 0xFF74334E, chapterIndex = 2,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.03f, 0.50f, "胃入口", listOf(1), blurb = "酸潮正在改变路径"),
            MapNode(
                1, NodeType.MOB, 0.12f, 0.50f, "胃壁菌斑", listOf(2, 3, 4),
                waves = longMob(115f, 17f, hard = true), goldDrop = 26, blurb = "金关", element = WuXing.METAL
            ),
            MapNode(2, NodeType.SHOP, 0.24f, 0.22f, "酶体工坊", listOf(5), blurb = "为下一区域调整装备"),
            MapNode(
                3, NodeType.MOB, 0.24f, 0.50f, "胃褶内廊", listOf(5),
                waves = longMob(120f, 18f, hard = true), goldDrop = 28, blurb = "火关", element = WuXing.FIRE
            ),
            MapNode(4, NodeType.EVENT, 0.24f, 0.78f, "菌群档案", listOf(5), eventId = "archive", blurb = "选择共生或排斥"),
            MapNode(
                5, NodeType.MOB, 0.36f, 0.50f, "菌群乱战", listOf(6, 7, 8),
                waves = longMob(128f, 18f, hard = true), goldDrop = 28, blurb = "木关", element = WuXing.WOOD
            ),
            MapNode(6, NodeType.REST, 0.48f, 0.22f, "绒毛静区", listOf(9), blurb = "短暂吸收营养"),
            MapNode(
                7, NodeType.ELITE, 0.48f, 0.50f, "菌膜队长", listOf(9),
                waves = longMob(135f, 19f, hard = true), goldDrop = 36, blurb = "金关精英", element = WuXing.METAL
            ),
            MapNode(8, NodeType.TRAP, 0.48f, 0.78f, "胃酸陷阱", listOf(9), trapDmg = 65f, blurb = "活性换取进度"),
            MapNode(
                9, NodeType.ELITE, 0.60f, 0.50f, "多层菌膜", listOf(10, 11),
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
            MapNode(10, NodeType.HEAL, 0.72f, 0.32f, "益生菌群", listOf(12), goldDrop = 12, blurb = "最后恢复活性"),
            MapNode(11, NodeType.SHOP, 0.72f, 0.68f, "肠道补给点", listOf(12), blurb = "感染核心前换装"),
            MapNode(
                12, NodeType.MOB, 0.82f, 0.50f, "小肠甬道", listOf(13),
                waves = longMob(150f, 21f, hard = true), goldDrop = 32, blurb = "火关", element = WuXing.FIRE
            ),
            MapNode(
                13, NodeType.BOSS, 0.92f, 0.50f, "肠道感染核心", listOf(14),
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
                goldDrop = 60, blurb = "胃肠Boss·平衡菌群", element = WuXing.WOOD
            ),
            MapNode(14, NodeType.EXIT, 0.98f, 0.50f, "肝门静脉", emptyList(), blurb = "携代谢物进入肝脏")
        )
    ),
    // ── Chapter 4: 肝脏净化区（拉长周目）──
    StageDef(
        4, "肝脏净化区", "解毒工厂 · 过滤毒素与坏死细胞",
        bgTop = 0xFFB86A5A, bgBot = 0xFF592B2A, chapterIndex = 3,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.04f, 0.50f, "肝小叶入口", listOf(1), blurb = "毒素浓度持续上升"),
            MapNode(1, NodeType.MOB, 0.14f, 0.50f, "毒素潮", listOf(2, 3, 4),
                waves = longMob(160f, 22f, hard = true), goldDrop = 30, blurb = "代谢废物混编", element = WuXing.WATER),
            MapNode(2, NodeType.GOLD, 0.26f, 0.20f, "糖原储备", listOf(5), goldDrop = 34, blurb = "补充战斗营养"),
            MapNode(3, NodeType.SHOP, 0.26f, 0.50f, "酶体装配站", listOf(5), blurb = "强化解毒装备"),
            MapNode(4, NodeType.ELITE, 0.26f, 0.80f, "脂质菌膜", listOf(5),
                waves = longMob(175f, 23f, hard = true), goldDrop = 40, blurb = "耐药精英", element = WuXing.EARTH),
            MapNode(5, NodeType.MOB, 0.40f, 0.50f, "肝窦", listOf(6, 7, 8),
                waves = longMob(180f, 24f, hard = true), goldDrop = 32, blurb = "坏死细胞聚集", element = WuXing.METAL),
            MapNode(6, NodeType.REST, 0.52f, 0.20f, "再生区", listOf(9), blurb = "肝细胞短暂修复"),
            MapNode(7, NodeType.EVENT, 0.52f, 0.50f, "代谢信号", listOf(9), eventId = "bard", blurb = "尝试重排代谢"),
            MapNode(8, NodeType.HEAL, 0.52f, 0.80f, "葡萄糖池", listOf(9), goldDrop = 12, blurb = "恢复细胞活性"),
            MapNode(9, NodeType.ELITE, 0.66f, 0.50f, "毒性菌团", listOf(10, 11),
                waves = longMob(195f, 25f, hard = true), goldDrop = 44, blurb = "高毒精英", element = WuXing.FIRE),
            MapNode(10, NodeType.SHOP, 0.78f, 0.28f, "解毒工坊", listOf(12), blurb = "终区换装"),
            MapNode(11, NodeType.TRAP, 0.78f, 0.72f, "胆汁裂谷", listOf(12), trapDmg = 70f, blurb = "酸性高危区"),
            MapNode(12, NodeType.BOSS, 0.90f, 0.50f, "肝部感染核心", listOf(13),
                waves = listOf(
                    WaveDef(listOf(WaveEnemy(EnemyKind.BOSS_SLIME, EnemyAi.BOSS, 900f, 28f, 100f))),
                    WaveDef(listOf(
                        WaveEnemy(EnemyKind.BOSS_ORE, EnemyAi.BOSS, 620f, 26f, 105f),
                        WaveEnemy(EnemyKind.WISP, EnemyAi.RANGED, 180f, 24f, 100f),
                        WaveEnemy(EnemyKind.BAT, EnemyAi.RANGED, 160f, 22f, 150f)
                    )),
                    longMob(200f, 26f, hard = true).last()
                ),
                goldDrop = 70, blurb = "肝脏Boss·恢复净化", element = WuXing.WATER),
            MapNode(13, NodeType.EXIT, 0.98f, 0.50f, "肝静脉", emptyList(), blurb = "净化血流进入心脏")
        )
    ),
    // ── Chapter 5: 心脏循环核（终章）──
    StageDef(
        5, "心脏循环核", "血流中枢 · 阻止感染扩散全身",
        bgTop = 0xFFB93650, bgBot = 0xFF3A0B18, chapterIndex = 4,
        nodes = listOf(
            MapNode(0, NodeType.START, 0.04f, 0.50f, "右心房", listOf(1), blurb = "感染正随静脉回流"),
            MapNode(1, NodeType.MOB, 0.16f, 0.50f, "右心室", listOf(2, 3),
                waves = longMob(200f, 26f, hard = true), goldDrop = 34, blurb = "湍流病原体", element = WuXing.WOOD),
            MapNode(2, NodeType.SHOP, 0.30f, 0.28f, "瓣膜补给站", listOf(4), blurb = "调整最终装备"),
            MapNode(3, NodeType.ELITE, 0.30f, 0.72f, "血栓菌团", listOf(4),
                waves = longMob(215f, 27f, hard = true), goldDrop = 46, blurb = "阻塞型精英", element = WuXing.METAL),
            MapNode(4, NodeType.MOB, 0.46f, 0.50f, "肺循环回路", listOf(5, 6, 7),
                waves = longMob(220f, 28f, hard = true), goldDrop = 36, blurb = "高速血流战", element = WuXing.EARTH),
            MapNode(5, NodeType.HEAL, 0.60f, 0.22f, "含氧血池", listOf(8), goldDrop = 14, blurb = "恢复细胞活性"),
            MapNode(6, NodeType.REST, 0.60f, 0.50f, "窦房结", listOf(8), blurb = "同步战斗节律"),
            MapNode(7, NodeType.EVENT, 0.60f, 0.78f, "心肌记忆", listOf(8), eventId = "stele", blurb = "读取循环记录"),
            MapNode(8, NodeType.ELITE, 0.74f, 0.50f, "冠脉双生菌", listOf(9),
                waves = longMob(240f, 29f, hard = true), goldDrop = 50, blurb = "双生高危精英", element = WuXing.FIRE),
            MapNode(9, NodeType.SHOP, 0.84f, 0.50f, "左心房工坊", listOf(10), blurb = "终战前换装"),
            MapNode(10, NodeType.BOSS, 0.93f, 0.50f, "循环变异母体", listOf(11),
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
                goldDrop = 90, blurb = "终局Boss·阻止全身扩散", element = WuXing.WOOD),
            MapNode(11, NodeType.EXIT, 0.99f, 0.22f, "主动脉出口", emptyList(), blurb = "本轮感染周期完成")
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
    NodeType.TREASURE -> Color(0xFFFDE047)
    NodeType.CHALLENGE -> Color(0xFFF87171)
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
    NodeType.TREASURE -> "宝"
    NodeType.CHALLENGE -> "挑"
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
    NodeType.TREASURE -> "寻宝"
    NodeType.CHALLENGE -> "挑战"
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
