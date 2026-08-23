package com.jellystorage.play

/**
 * 饰品：戒指 / 鞋子 — 与武器、防具并列的装备槽。
 */
enum class AccSlot(val display: String) {
    RING("戒指"),
    BOOTS("鞋子")
}

data class GearAccessory(
    val id: String,
    val name: String,
    val slot: AccSlot,
    val hero: HeroClass? = null,
    val tier: Int = 1,
    val rarity: Int = 0,
    val cost: Int,
    val atkBonus: Float = 0f,
    val crit: Float = 0f,
    val skillAmp: Float = 0f,
    val lifeSteal: Float = 0f,
    val cdr: Float = 0f,
    val hpBonus: Float = 0f,
    val mpBonus: Float = 0f,
    val spdBonus: Float = 0f,
    val dr: Float = 0f,
    val element: WuXing? = null,
    val flavor: String = ""
) {
    fun rarityName(): String = when (rarity) {
        2 -> "史诗"
        1 -> "精良"
        else -> "普通"
    }

    fun classLabel(): String = hero?.displayName ?: "通用"
    fun canEquip(h: HeroClass) = hero == null || hero == h
    fun tierLabel(): String = "T$tier"
    fun statsCompact(): String = buildString {
        if (atkBonus > 0.1f) append("攻+${atkBonus.toInt()} ")
        if (hpBonus > 0.1f) append("血+${hpBonus.toInt()} ")
        if (mpBonus > 0.1f) append("蓝+${mpBonus.toInt()} ")
        if (crit > 0.01f) append("暴${(crit * 100).toInt()}% ")
        if (skillAmp > 0.01f) append("技${(skillAmp * 100).toInt()}% ")
        if (lifeSteal > 0.01f) append("吸${(lifeSteal * 100).toInt()}% ")
        if (cdr > 0.01f) append("冷-${(cdr * 100).toInt()}% ")
        if (spdBonus > 0.01f) append("速${(spdBonus * 100).toInt()}% ")
        if (dr > 0.01f) append("减${(dr * 100).toInt()}% ")
    }.trim().ifEmpty { "—" }
}

object RingCatalog {
    val all: List<GearAccessory> = listOf(
        GearAccessory("r_wood", "木纹指环", AccSlot.RING, null, 1, 0, 18, mpBonus = 8f, element = WuXing.WOOD, flavor = "入门小戒"),
        GearAccessory("r_spark", "星火戒", AccSlot.RING, null, 2, 1, 42, skillAmp = 0.08f, mpBonus = 10f, atkBonus = 4f, element = WuXing.FIRE, flavor = "口袋里的小火种"),
        GearAccessory("r_ice", "寒霜戒", AccSlot.RING, HeroClass.MAGE, 2, 1, 48, skillAmp = 0.10f, cdr = 0.05f, element = WuXing.WATER, flavor = "法师爱用"),
        GearAccessory("r_blood", "血玉戒", AccSlot.RING, HeroClass.WARRIOR, 3, 1, 55, lifeSteal = 0.05f, atkBonus = 6f, element = WuXing.FIRE, flavor = "越战越补"),
        GearAccessory("r_jade", "玉清戒", AccSlot.RING, HeroClass.TAOIST, 3, 1, 52, skillAmp = 0.06f, hpBonus = 20f, element = WuXing.WATER, flavor = "回春添彩"),
        GearAccessory("r_thunder", "雷纹戒", AccSlot.RING, null, 4, 2, 75, crit = 0.08f, skillAmp = 0.08f, element = WuXing.METAL, flavor = "出手带电"),
        GearAccessory("r_penta", "五行宝戒", AccSlot.RING, null, 5, 2, 100, skillAmp = 0.12f, crit = 0.05f, atkBonus = 8f, element = WuXing.METAL, flavor = "集齐的证明")
    )

    fun byId(id: String) = all.find { it.id == id }
    fun usableBy(h: HeroClass) = all.filter { it.canEquip(h) }
    fun shopOffers(h: HeroClass, n: Int = 1) = usableBy(h).filter { it.cost > 0 }.shuffled().take(n)
    fun randomDrop(h: HeroClass, maxRarity: Int = 2, maxTier: Int = 5) = usableBy(h).filter {
        it.cost > 0 && it.rarity <= maxRarity.coerceIn(0, 2) && it.tier <= maxTier.coerceIn(1, 5)
    }.randomOrNull()
}

object BootsCatalog {
    val all: List<GearAccessory> = listOf(
        GearAccessory("b_cloth", "布履", AccSlot.BOOTS, null, 1, 0, 0, spdBonus = 0.02f, flavor = "出门必备"),
        GearAccessory("b_leather", "猎皮靴", AccSlot.BOOTS, null, 1, 0, 20, spdBonus = 0.05f, hpBonus = 10f, flavor = "轻便"),
        GearAccessory("b_iron", "铁胫甲", AccSlot.BOOTS, HeroClass.WARRIOR, 2, 1, 38, dr = 0.04f, hpBonus = 25f, spdBonus = 0.03f, flavor = "踩得稳"),
        GearAccessory("b_wind", "疾风靴", AccSlot.BOOTS, null, 2, 1, 45, spdBonus = 0.12f, crit = 0.03f, element = WuXing.WOOD, flavor = "跑图神器"),
        GearAccessory("b_mage", "法丝履", AccSlot.BOOTS, HeroClass.MAGE, 3, 1, 50, mpBonus = 20f, skillAmp = 0.06f, spdBonus = 0.06f, element = WuXing.WATER, flavor = "施法不绊脚"),
        GearAccessory("b_tao", "云游履", AccSlot.BOOTS, HeroClass.TAOIST, 3, 1, 48, hpBonus = 18f, mpBonus = 12f, spdBonus = 0.07f, element = WuXing.EARTH, flavor = "走遍三山"),
        GearAccessory("b_quake", "裂地战靴", AccSlot.BOOTS, HeroClass.WARRIOR, 4, 2, 80, hpBonus = 40f, dr = 0.06f, atkBonus = 5f, flavor = "一步一震"),
        GearAccessory("b_star", "流星靴", AccSlot.BOOTS, null, 5, 2, 95, spdBonus = 0.15f, crit = 0.06f, skillAmp = 0.05f, element = WuXing.FIRE, flavor = "身法如星")
    )

    fun byId(id: String) = all.find { it.id == id }
    fun usableBy(h: HeroClass) = all.filter { it.canEquip(h) }
    fun shopOffers(h: HeroClass, n: Int = 1) = usableBy(h).filter { it.cost > 0 }.shuffled().take(n)
    fun randomDrop(h: HeroClass, maxRarity: Int = 2, maxTier: Int = 5) = usableBy(h).filter {
        it.cost > 0 && it.rarity <= maxRarity.coerceIn(0, 2) && it.tier <= maxTier.coerceIn(1, 5)
    }.randomOrNull()
    fun starter() = byId("b_cloth")!!
}

fun GearAccessory.slotLabel(): String = slot.display
