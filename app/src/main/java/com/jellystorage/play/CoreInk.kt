package com.jellystorage.play

enum class CoreInkId(
    val hero: HeroClass,
    val title: String,
    val glyph: String,
    val skillName: String,
    val desc: String,
    val color: Long
) {
    WARRIOR_FIRE_TRAIL(
        HeroClass.WARRIOR, "赤线走笔", "焰", "冲锋",
        "冲锋沿途留下灼烧墨线；升阶提高持续时间与伤害。", 0xFFEF4444
    ),
    WARRIOR_BASTION_BURST(
        HeroClass.WARRIOR, "金城反震", "震", "铁壁",
        "铁壁展开时震伤周围敌人；升阶扩大范围与威力。", 0xFFFBBF24
    ),
    WARRIOR_WHIRL_ECHO(
        HeroClass.WARRIOR, "回锋双轮", "回", "旋风斩",
        "旋风斩后追加延迟外环；升阶提高回锋伤害。", 0xFFFB923C
    ),
    MAGE_TWIN_FROST(
        HeroClass.MAGE, "双生寒月", "霜", "魔法盾",
        "魔法盾持续期间，击中你的敌人会被寒霜迟缓；升阶提高护盾强度与持续时间。", 0xFF38BDF8
    ),
    MAGE_STORM_BRANCH(
        HeroClass.MAGE, "雷枝万象", "枝", "链雷",
        "链雷增加跳跃次数和搜索距离；升阶继续扩展雷网。", 0xFFA78BFA
    ),
    MAGE_CINDER_FIELD(
        HeroClass.MAGE, "余烬成阵", "烬", "炎爆",
        "炎爆落点留下持续燃烧区域；升阶提高范围与持续时间。", 0xFFFF6B35
    ),
    TAOIST_WANDERING_MIST(
        HeroClass.TAOIST, "随身瘴云", "云", "毒雾",
        "毒雾缓慢跟随施术者；升阶提高范围、持续时间与毒伤。", 0xFFA3E635
    ),
    TAOIST_SPRING_OVERFLOW(
        HeroClass.TAOIST, "回春溢脉", "溢", "回春",
        "回春的过量治疗转化为护盾；升阶提高转化比例。", 0xFF4ADE80
    ),
    TAOIST_SEAL_ECHO(
        HeroClass.TAOIST, "双镇回符", "镇", "镇符",
        "镇符短暂延迟后再度封锁原地；升阶提高回符伤害。", 0xFF8B5CF6
    )
}

fun coreInkChoices(hero: HeroClass): List<CoreInkId> = CoreInkId.entries.filter { it.hero == hero }

fun Map<CoreInkId, Int>.rankOf(id: CoreInkId): Int = (this[id] ?: 0).coerceIn(0, 5)

fun MutableMap<CoreInkId, Int>.upgrade(id: CoreInkId): Int {
    val next = (rankOf(id) + 1).coerceAtMost(5)
    this[id] = next
    return next
}

fun encodeCoreInkRanks(ranks: Map<CoreInkId, Int>): String =
    ranks.entries
        .filter { it.value > 0 }
        .joinToString(",") { "${it.key.name}:${it.value.coerceIn(1, 5)}" }

fun decodeCoreInkRanks(raw: String, hero: HeroClass): LinkedHashMap<CoreInkId, Int> {
    val result = linkedMapOf<CoreInkId, Int>()
    raw.split(",").filter { it.isNotBlank() }.forEach { pair ->
        val parts = pair.split(":")
        val id = parts.firstOrNull()?.let { name ->
            try {
                CoreInkId.valueOf(name)
            } catch (_: Exception) {
                null
            }
        }
        val rank = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 5)
        if (id != null && id.hero == hero && rank != null) result[id] = rank
    }
    return result
}
