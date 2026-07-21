package com.jellystorage.softbody.assemble

import androidx.compose.ui.graphics.Color

enum class Tag(val label: String, val color: Color) {
    FIRE("火", Color(0xFFFF6B35)),
    ICE("冰", Color(0xFF67E8F9)),
    LIGHTNING("雷", Color(0xFFFACC15)),
    POISON("毒", Color(0xFF4ADE80)),
    SHIELD("盾", Color(0xFF60A5FA)),
    PIERCE("穿", Color(0xFFE879F9)),
    BLAST("爆", Color(0xFFFB7185)),
    DRAIN("吸", Color(0xFFC084FC)),
    SWIFT("迅", Color(0xFFA5B4FC)),
    HEAVY("重", Color(0xFFA8A29E)),
}

data class WeaponMod(
    val id: String,
    val name: String,
    val desc: String,
    val tags: Set<Tag>,
    val atk: Int,
    val def: Int = 0,
    val hp: Int = 0,
    val color: Color,
    /** Visual attack style in arena. */
    val style: AttackStyle
)

enum class AttackStyle {
    CLAW, SPIKE, HAMMER, MIST, SHELL, TENDRIL, BOUNCE, DRILL, WHIP, CORE
}

val ALL_WEAPONS: List<WeaponMod> = listOf(
    WeaponMod("flame_claw", "火焰爪", "近战火爪，持续灼烧", setOf(Tag.FIRE, Tag.HEAVY), 16, color = Color(0xFFFF6B35), style = AttackStyle.CLAW),
    WeaponMod("ice_spike", "冰晶刺", "高速冰刺穿透", setOf(Tag.ICE, Tag.PIERCE), 13, color = Color(0xFF67E8F9), style = AttackStyle.SPIKE),
    WeaponMod("thunder_hammer", "雷霆锤", "重锤落地感电", setOf(Tag.LIGHTNING, Tag.HEAVY), 15, color = Color(0xFFFACC15), style = AttackStyle.HAMMER),
    WeaponMod("poison_sac", "毒雾囊", "喷出毒雾区域", setOf(Tag.POISON, Tag.BLAST), 11, color = Color(0xFF4ADE80), style = AttackStyle.MIST),
    WeaponMod("shell_guard", "甲壳盾", "减伤并反震", setOf(Tag.SHIELD), 5, def = 14, hp = 40, color = Color(0xFF60A5FA), style = AttackStyle.SHELL),
    WeaponMod("vamp_tendril", "吸血触手", "命中回血", setOf(Tag.DRAIN, Tag.SWIFT), 12, color = Color(0xFFC084FC), style = AttackStyle.TENDRIL),
    WeaponMod("bounce_gel", "弹射胶", "弹跳弹道", setOf(Tag.BLAST, Tag.SWIFT), 10, color = Color(0xFFF9A8D4), style = AttackStyle.BOUNCE),
    WeaponMod("drill_bit", "钻头牙", "破甲连钻", setOf(Tag.PIERCE, Tag.HEAVY), 14, color = Color(0xFFE879F9), style = AttackStyle.DRILL),
    WeaponMod("frost_plate", "霜甲", "冰甲护体", setOf(Tag.ICE, Tag.SHIELD), 6, def = 12, hp = 28, color = Color(0xFFA5F3FC), style = AttackStyle.SHELL),
    WeaponMod("spark_whip", "电鞭", "连锁闪电鞭", setOf(Tag.LIGHTNING, Tag.SWIFT), 12, color = Color(0xFFFEF08A), style = AttackStyle.WHIP),
    WeaponMod("plague_fang", "疫牙", "毒吸一体", setOf(Tag.POISON, Tag.DRAIN), 11, color = Color(0xFF86EFAC), style = AttackStyle.TENDRIL),
    WeaponMod("magma_core", "岩浆核", "火爆弹幕", setOf(Tag.FIRE, Tag.BLAST), 14, color = Color(0xFFFB923C), style = AttackStyle.CORE),
)

data class Synergy(
    val id: String,
    val name: String,
    val desc: String,
    val need: Set<Tag>,
    val atkMult: Float = 1f,
    val defMult: Float = 1f,
    val bonusAtk: Int = 0,
    val bonusDef: Int = 0,
    val lifesteal: Float = 0f,
    val thorns: Float = 0f,
    val burnDps: Float = 0f,
    val multiShot: Int = 0,
    val projectileScale: Float = 1f,
    val special: String = "",
    val color: Color = Color(0xFFFFD700)
)

val ALL_SYNERGIES: List<Synergy> = listOf(
    Synergy("steam_burst", "蒸汽爆裂", "火+冰：巨大蒸汽弹，破甲", setOf(Tag.FIRE, Tag.ICE),
        atkMult = 1.4f, bonusAtk = 16, projectileScale = 1.8f, special = "steam", color = Color(0xFFE0F2FE)),
    Synergy("burn_toxic", "燃毒炼狱", "火+毒：强力灼毒", setOf(Tag.FIRE, Tag.POISON),
        atkMult = 1.15f, burnDps = 12f, special = "burn_toxic", color = Color(0xFF86EFAC)),
    Synergy("storm_shell", "雷甲反伤", "雷+盾：受击反雷", setOf(Tag.LIGHTNING, Tag.SHIELD),
        defMult = 1.4f, thorns = 0.5f, special = "thorns", color = Color(0xFFFACC15)),
    Synergy("chain_drill", "连锁钻击", "穿+迅：三连射", setOf(Tag.PIERCE, Tag.SWIFT),
        atkMult = 1.2f, multiShot = 2, special = "multi", color = Color(0xFFE879F9)),
    Synergy("plague_leech", "瘟疫吸血", "毒+吸：毒伤回血", setOf(Tag.POISON, Tag.DRAIN),
        lifesteal = 0.4f, burnDps = 7f, special = "plague", color = Color(0xFFC084FC)),
    Synergy("cryo_crush", "碎冰重击", "冰+重：暴击碾压", setOf(Tag.ICE, Tag.HEAVY),
        atkMult = 1.6f, special = "crit", color = Color(0xFF67E8F9)),
    Synergy("gel_storm", "胶爆雷暴", "爆+雷：扇形弹幕", setOf(Tag.BLAST, Tag.LIGHTNING),
        atkMult = 1.25f, multiShot = 1, bonusAtk = 10, special = "fan", color = Color(0xFFFBBF24)),
    Synergy("fortress", "要塞形态", "盾+重：超高减伤", setOf(Tag.SHIELD, Tag.HEAVY),
        defMult = 1.7f, bonusDef = 18, special = "fortress", color = Color(0xFF60A5FA)),
    Synergy("inferno", "熔火之心", "火+爆：连环火球", setOf(Tag.FIRE, Tag.BLAST),
        atkMult = 1.3f, multiShot = 1, burnDps = 8f, special = "inferno", color = Color(0xFFFB7185)),
    Synergy("vampire_swift", "疾影吸血", "吸+迅：高速吸血", setOf(Tag.DRAIN, Tag.SWIFT),
        atkMult = 1.12f, lifesteal = 0.3f, multiShot = 1, special = "vamp", color = Color(0xFFD8B4FE)),
)

fun weaponById(id: String): WeaponMod? = ALL_WEAPONS.find { it.id == id }

fun tagsFromLoadout(ids: List<String>): Set<Tag> =
    ids.mapNotNull { weaponById(it) }.flatMap { it.tags }.toSet()

fun activeSynergies(ids: List<String>): List<Synergy> {
    val tags = tagsFromLoadout(ids)
    return ALL_SYNERGIES.filter { s -> s.need.all { it in tags } }
}
