package com.jellystorage.play

/**
 * 防具 + 套装图鉴体系。
 * 武器见 WeaponCatalog；套装 = 指定武器 + 指定防具同时装备。
 */

data class GearArmor(
    val id: String,
    val name: String,
    val hero: HeroClass?,
    val tier: Int,              // 1~5 阶段
    val rarity: Int = 0,        // 0普通 1精良 2史诗
    val cost: Int,
    val hpBonus: Float,
    val dr: Float = 0f,
    val mpBonus: Float = 0f,
    val skillAmp: Float = 0f,
    val crit: Float = 0f,
    val cdr: Float = 0f,
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
        append("血+${hpBonus.toInt()}")
        if (dr > 0.01f) append(" 减伤${(dr * 100).toInt()}%")
        if (mpBonus > 1f) append(" 蓝+${mpBonus.toInt()}")
        if (skillAmp > 0.01f) append(" 技${(skillAmp * 100).toInt()}%")
        if (crit > 0.01f) append(" 暴${(crit * 100).toInt()}%")
        if (cdr > 0.01f) append(" 冷-${(cdr * 100).toInt()}%")
    }
}

/**
 * 套装被动：2 件齐装触发（本游戏一武器+一防具即满套）。
 */
data class GearSetDef(
    val id: String,
    val name: String,
    val hero: HeroClass,
    val tier: Int,
    val weaponIds: List<String>,
    val armorIds: List<String>,
    val bonusTitle: String,
    val bonusTip: String,
    val atkBonus: Float = 0f,
    val skillAmp: Float = 0f,
    val dr: Float = 0f,
    val lifeSteal: Float = 0f,
    val cdr: Float = 0f,
    val crit: Float = 0f,
    val hpBonus: Float = 0f,
    val mpBonus: Float = 0f,
    /** 套装特殊被动 */
    val proc: GearProc = GearProc.NONE,
    val procPower: Float = 0f
) {
    fun matches(weaponId: String, armorId: String): Boolean =
        weaponId in weaponIds && armorId in armorIds

    fun piecesLine(): String =
        "武:${weaponIds.mapNotNull { WeaponCatalog.byId(it)?.name }.joinToString("/")} + " +
            "甲:${armorIds.mapNotNull { ArmorCatalog.byId(it)?.name }.joinToString("/")}"
}

object ArmorCatalog {
    val all: List<GearArmor> = listOf(
        // ── 起步 ──
        GearArmor("a_cloth", "布衣", null, 1, 0, 0, 0f, 0f, flavor = "人人一件"),
        // ── 战士 T1~T5 ──
        GearArmor("a_w_leather", "猎兵皮甲", HeroClass.WARRIOR, 1, 0, 22, 28f, 0.05f, flavor = "入门硬皮"),
        GearArmor("a_w_iron", "铁卫胸铠", HeroClass.WARRIOR, 2, 1, 40, 55f, 0.08f, flavor = "铁卫套装件"),
        GearArmor("a_w_blood", "血纹战甲", HeroClass.WARRIOR, 3, 1, 65, 80f, 0.10f, flavor = "越战越热"),
        GearArmor("a_w_quake", "裂地重铠", HeroClass.WARRIOR, 4, 2, 90, 120f, 0.14f, flavor = "踩得动地震"),
        GearArmor("a_w_flame", "炎魔战铠", HeroClass.WARRIOR, 5, 2, 120, 150f, 0.16f, flavor = "终末烈火"),
        // ── 法师 ──
        GearArmor("a_m_robe", "学徒法袍", HeroClass.MAGE, 1, 0, 22, 18f, 0.03f, mpBonus = 20f, skillAmp = 0.04f, flavor = "课堂出品"),
        GearArmor("a_m_frost", "寒霜法衣", HeroClass.MAGE, 2, 1, 42, 30f, 0.05f, mpBonus = 30f, skillAmp = 0.08f, flavor = "冰系共鸣"),
        GearArmor("a_m_storm", "雷纹法袍", HeroClass.MAGE, 3, 1, 68, 40f, 0.06f, mpBonus = 40f, skillAmp = 0.12f, cdr = 0.06f, flavor = "施法如雷"),
        GearArmor("a_m_abyss", "渊水祭服", HeroClass.MAGE, 4, 2, 95, 55f, 0.08f, mpBonus = 50f, skillAmp = 0.15f, flavor = "控场专精"),
        GearArmor("a_m_crown", "灭世法衣", HeroClass.MAGE, 5, 2, 125, 70f, 0.10f, mpBonus = 60f, skillAmp = 0.20f, flavor = "星火终章"),
        // ── 道士 ──
        GearArmor("a_t_cloth", "道童青衫", HeroClass.TAOIST, 1, 0, 22, 24f, 0.04f, mpBonus = 15f, flavor = "师父缝的"),
        GearArmor("a_t_jade", "玉清道袍", HeroClass.TAOIST, 2, 1, 42, 40f, 0.06f, mpBonus = 25f, skillAmp = 0.06f, flavor = "回春更润"),
        GearArmor("a_t_seal", "天师法衣", HeroClass.TAOIST, 3, 1, 68, 55f, 0.08f, mpBonus = 30f, skillAmp = 0.08f, flavor = "镇杀生威"),
        GearArmor("a_t_poison", "冥毒法衫", HeroClass.TAOIST, 4, 2, 95, 70f, 0.09f, mpBonus = 35f, crit = 0.05f, flavor = "毒雾缠身"),
        GearArmor("a_t_gourd", "紫金道铠", HeroClass.TAOIST, 5, 2, 125, 95f, 0.12f, mpBonus = 45f, skillAmp = 0.12f, flavor = "续航之极"),
        // ── 通用进阶 ──
        GearArmor("a_u_chain", "旅人锁子甲", null, 2, 1, 48, 45f, 0.07f, flavor = "谁都能穿"),
        GearArmor("a_u_scale", "五行鳞甲", null, 4, 2, 100, 90f, 0.11f, skillAmp = 0.06f, flavor = "通吃属性")
    )

    // helper for blood armor - I used invalid param lifeStealHint - fix below
    fun byId(id: String) = all.find { it.id == id }
    fun starter(hero: HeroClass): GearArmor = byId("a_cloth")!!
    fun usableBy(hero: HeroClass) = all.filter { it.canEquip(hero) }
    fun forHeroTier(hero: HeroClass, tier: Int) =
        all.filter { (it.hero == hero || it.hero == null) && it.tier == tier }
    fun shopOffers(hero: HeroClass, count: Int = 2, budget: Int = 999): List<GearArmor> {
        val pool = usableBy(hero).filter { it.cost > 0 }
        return pool.sortedBy { a ->
            val price = if (a.cost <= budget) a.cost else a.cost + 200
            val classBoost = if (a.hero == hero) 0 else 50
            price + classBoost * 10
        }.take(count)
    }
    fun randomDrop(hero: HeroClass): GearArmor? {
        val pool = usableBy(hero).filter { it.cost > 0 }
        if (pool.isEmpty()) return null
        val roll = kotlin.random.Random.nextFloat()
        val filtered = when {
            roll < 0.5f -> pool.filter { it.rarity == 0 }.ifEmpty { pool }
            roll < 0.85f -> pool.filter { it.rarity <= 1 }.ifEmpty { pool }
            else -> pool
        }
        return filtered.random()
    }
}

// Fix blood armor - remove invalid param by redefining all without lifeStealHint
// Actually the file has a compile error - I need to fix a_w_blood line

object SetCatalog {
    val all: List<GearSetDef> = listOf(
        // 战士
        GearSetDef(
            "set_w_iron", "铁卫之誓", HeroClass.WARRIOR, 1,
            weaponIds = listOf("w_steel", "w_guard_blade"),
            armorIds = listOf("a_w_leather", "a_w_iron"),
            bonusTitle = "铁壁反击", bonusTip = "受伤反震 + 减伤",
            dr = 0.06f, hpBonus = 20f, proc = GearProc.THORNS, procPower = 0.20f
        ),
        GearSetDef(
            "set_w_blood", "血饮战魂", HeroClass.WARRIOR, 2,
            weaponIds = listOf("w_blood", "w_thund_edge"),
            armorIds = listOf("a_w_blood", "a_w_iron"),
            bonusTitle = "嗜血狂澜", bonusTip = "吸血增强 · 命中叠战意",
            lifeSteal = 0.06f, crit = 0.05f, proc = GearProc.RAGE_ON_HIT, procPower = 0.18f
        ),
        GearSetDef(
            "set_w_quake", "裂地霸者", HeroClass.WARRIOR, 3,
            weaponIds = listOf("w_quake", "w_gold_spear"),
            armorIds = listOf("a_w_quake", "a_w_blood"),
            bonusTitle = "地裂斩", bonusTip = "重击大范围溅射",
            atkBonus = 8f, hpBonus = 40f, proc = GearProc.SPLASH, procPower = 0.48f
        ),
        GearSetDef(
            "set_w_flame", "炎魔灭阵", HeroClass.WARRIOR, 4,
            weaponIds = listOf("w_flame"),
            armorIds = listOf("a_w_flame", "a_w_quake"),
            bonusTitle = "炼狱燃刃", bonusTip = "攻击强力点燃 · 处决强化",
            atkBonus = 12f, lifeSteal = 0.04f, proc = GearProc.BURN, procPower = 0.28f
        ),
        // 法师
        GearSetDef(
            "set_m_frost", "寒霜秘仪", HeroClass.MAGE, 1,
            weaponIds = listOf("m_ice", "m_flame"),
            armorIds = listOf("a_m_robe", "a_m_frost"),
            bonusTitle = "永冻咒", bonusTip = "更高冻结几率",
            skillAmp = 0.08f, mpBonus = 15f, proc = GearProc.FREEZE, procPower = 0.26f
        ),
        GearSetDef(
            "set_m_storm", "雷霆奥义", HeroClass.MAGE, 2,
            weaponIds = listOf("m_thunder", "m_wood_orb"),
            armorIds = listOf("a_m_storm", "a_m_frost"),
            bonusTitle = "连锁奔雷", bonusTip = "技能弹射增强 · 冷却",
            skillAmp = 0.12f, cdr = 0.08f, proc = GearProc.CHAIN, procPower = 0.20f
        ),
        GearSetDef(
            "set_m_abyss", "渊水王座", HeroClass.MAGE, 3,
            weaponIds = listOf("m_abyss", "m_earth_tome"),
            armorIds = listOf("a_m_abyss", "a_m_storm"),
            bonusTitle = "潮汐法域", bonusTip = "技伤大增 · 吸灵回蓝",
            skillAmp = 0.16f, mpBonus = 25f, proc = GearProc.MP_SIPHON, procPower = 5f
        ),
        GearSetDef(
            "set_m_crown", "灭世星冠", HeroClass.MAGE, 4,
            weaponIds = listOf("m_crown"),
            armorIds = listOf("a_m_crown", "a_m_abyss"),
            bonusTitle = "陨星燃世", bonusTip = "强点燃 · 五行共鸣",
            skillAmp = 0.22f, crit = 0.06f, proc = GearProc.WUXING_AMP, procPower = 0.22f
        ),
        // 道士
        GearSetDef(
            "set_t_jade", "玉清回春", HeroClass.TAOIST, 1,
            weaponIds = listOf("t_talisman", "t_jade", "t_dust"),
            armorIds = listOf("a_t_cloth", "a_t_jade"),
            bonusTitle = "回春加持", bonusTip = "治疗量大幅提升",
            skillAmp = 0.06f, hpBonus = 25f, proc = GearProc.HEAL_AMP, procPower = 0.40f
        ),
        GearSetDef(
            "set_t_seal", "天师镇魂", HeroClass.TAOIST, 2,
            weaponIds = listOf("t_seal", "t_fire_charm"),
            armorIds = listOf("a_t_seal", "a_t_jade"),
            bonusTitle = "杀意护体", bonusTip = "击杀获得护盾",
            skillAmp = 0.10f, dr = 0.05f, proc = GearProc.KILL_SHIELD, procPower = 0.18f
        ),
        GearSetDef(
            "set_t_poison", "冥毒仙道", HeroClass.TAOIST, 3,
            weaponIds = listOf("t_poison_bell", "t_talisman"),
            armorIds = listOf("a_t_poison", "a_t_seal"),
            bonusTitle = "剧毒法界", bonusTip = "命中强毒",
            crit = 0.06f, skillAmp = 0.10f, proc = GearProc.POISON, procPower = 0.26f
        ),
        GearSetDef(
            "set_t_gourd", "紫金葫芦阵", HeroClass.TAOIST, 4,
            weaponIds = listOf("t_gourd"),
            armorIds = listOf("a_t_gourd", "a_t_poison"),
            bonusTitle = "吸灵回元", bonusTip = "命中大量回蓝 · 吸血",
            lifeSteal = 0.07f, mpBonus = 30f, skillAmp = 0.12f, proc = GearProc.MP_SIPHON, procPower = 7f
        )
    )

    fun byId(id: String) = all.find { it.id == id }
    fun forHero(h: HeroClass) = all.filter { it.hero == h }.sortedBy { it.tier }
    fun active(weaponId: String, armorId: String): GearSetDef? =
        all.firstOrNull { it.matches(weaponId, armorId) }

    fun involvingWeapon(weaponId: String) = all.filter { weaponId in it.weaponIds }
    fun involvingArmor(armorId: String) = all.filter { armorId in it.armorIds }
}

/** 武器阶段：按 rarity+atk 粗分，图鉴用 */
fun GearWeapon.tierLevel(): Int = when {
    cost <= 0 -> 1
    rarity >= 2 || atkBonus >= 38f -> 5
    rarity >= 1 && atkBonus >= 28f -> 4
    rarity >= 1 || atkBonus >= 18f -> 3
    atkBonus >= 10f -> 2
    else -> 1
}

fun GearWeapon.tierLabel(): String = "T${tierLevel()}"
