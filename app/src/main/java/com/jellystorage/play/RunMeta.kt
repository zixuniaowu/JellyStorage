package com.jellystorage.play

/**
 * Per-run state for a single adventure attempt.
 */
class RunMeta {
    var hero: HeroClass = HeroClass.WARRIOR
    /** 当前选中角色名（展示用） */
    var characterName: String = ""
    /** 本局结算是否已领过广告双倍金 */
    var adDoubleClaimed: Boolean = false
    /** 本局已用广告药水次数（上限 2） */
    var adPotionClaims: Int = 0
    /** 本局随机种子：重开地图细节不同 */
    var runSeed: Long = System.nanoTime()
    /** 墨阶 0..7：越高敌人越强、掉落越好，专为反复通关 */
    var inkRank: Int = 0
    private var stageCache: List<StageDef>? = null
    private var affixCache: List<InkAffix>? = null
    var skinId: String = SkinCatalog.warriorDefault.id
    var stageIndex = 0
    var nodeId = 0
    var gold = 32
    var goldEarnedThisRun = 0
    var weaponLevel = 0 // forge level (small bonus stacked with gear)
    var armorLevel = 0
    var potions = 1
    var level = 1
    var xp = 0
    var xpToLevel = 22
    var curHp = -1f
    var curMp = -1f
    val passives = linkedSetOf<PassiveId>()
    var visited = mutableSetOf(0)
    val ownedWeapons = linkedSetOf<String>()
    var equippedWeaponId: String = "w_iron"
    val ownedArmors = linkedSetOf<String>()
    var equippedArmorId: String = "a_cloth"
    val ownedRings = linkedSetOf<String>()
    var equippedRingId: String = ""
    val ownedBoots = linkedSetOf<String>()
    var equippedBootsId: String = "b_cloth"
    var skillPowerBonus: Float = 0f
    var shopRingOffers: List<String> = emptyList()
    var shopBootsOffers: List<String> = emptyList()
    val ownedTomes = mutableListOf<String>()
    /** 背包道具 id -> 数量（五行果实等） */
    val bag = linkedMapOf<String, Int>()
    var shopWeaponOffers: List<String> = emptyList()
    var shopArmorOffers: List<String> = emptyList()
    var shopTomeId: String? = null
    var shopFruitOffers: List<String> = emptyList()
    /** pending pickup toast from last fight */
    var pendingLootLine: String = ""
    var toast = ""
    var toastT = 0f
    var eventTitle = ""
    var eventBody = ""
    var eventChoices: List<Pair<String, () -> Unit>> = emptyList()
    var levelChoices: List<PassiveId> = emptyList()
    var pulse = 0f
    var pendingMapAfterLevel = false
    /** 事件结束后进商店（金币袋招行商） */
    var pendingShopAfterEvent = false
    var kills = 0
    var roomsCleared = 0
    var resultTitle = ""
    var resultBody = ""
    var paused = false
    var storyQueue: List<StoryBeat> = emptyList()
    var storyIndex: Int = 0
    var storyReturn: String = "MAP"
    val journal = mutableListOf<String>()
    var pendingArenaAfterStory = false
    var arenaBanner: String = ""
    var arenaBannerT: Float = 0f
    // ── 下一战临时增益（果实） ──
    var buffElement: WuXing? = null
    var buffAtk: Float = 0f
    var buffDr: Float = 0f
    var buffBurn: Boolean = false
    /** 图鉴：0武器 1防具 2套装 */
    var codexTab: Int = 0
    var codexHeroIndex: Int = 0
    /** 当前点选预览的装备 id（武器或防具） */
    var codexSelectedId: String = ""

    fun skin(): CharacterSkin = SkinCatalog.byId(skinId) ?: SkinCatalog.defaultFor(hero)

    fun equippedWeapon(): GearWeapon =
        WeaponCatalog.byId(equippedWeaponId) ?: WeaponCatalog.starter(hero)

    fun equippedArmor(): GearArmor =
        ArmorCatalog.byId(equippedArmorId) ?: ArmorCatalog.starter(hero)

    fun equippedRing(): GearAccessory? =
        if (equippedRingId.isBlank()) null else RingCatalog.byId(equippedRingId)

    fun equippedBoots(): GearAccessory =
        BootsCatalog.byId(equippedBootsId) ?: BootsCatalog.starter()

    /** 当前激活的套装（武器+防具匹配） */
    fun activeSet(): GearSetDef? =
        SetCatalog.active(equippedWeaponId, equippedArmorId)

    fun playerElement(): WuXing = buffElement ?: equippedWeapon().element

    fun totalAtkBonus(): Float {
        val forge = weaponUpgradeTable(hero).getOrNull(weaponLevel)?.atkBonus ?: 0f
        val set = activeSet()?.atkBonus ?: 0f
        val ring = equippedRing()?.atkBonus ?: 0f
        val boots = equippedBoots().atkBonus
        return equippedWeapon().atkBonus + forge + buffAtk + set + ring + boots
    }

    fun totalSkillAmp(): Float =
        skillPowerBonus + equippedWeapon().skillAmp + equippedArmor().skillAmp +
            (activeSet()?.skillAmp ?: 0f) + (equippedRing()?.skillAmp ?: 0f) + equippedBoots().skillAmp

    fun totalDr(): Float =
        equippedWeapon().drBonus + equippedArmor().dr + (activeSet()?.dr ?: 0f) + buffDr +
            (equippedRing()?.dr ?: 0f) + equippedBoots().dr

    fun totalLifeSteal(): Float =
        equippedWeapon().lifeSteal + (activeSet()?.lifeSteal ?: 0f) + (equippedRing()?.lifeSteal ?: 0f)

    fun totalCrit(): Float =
        equippedWeapon().crit + equippedArmor().crit + (activeSet()?.crit ?: 0f) +
            (equippedRing()?.crit ?: 0f) + equippedBoots().crit

    fun totalCdr(): Float =
        equippedWeapon().cdr + equippedArmor().cdr + (activeSet()?.cdr ?: 0f) +
            (equippedRing()?.cdr ?: 0f) + equippedBoots().cdr

    fun setProc(): Pair<GearProc, Float> {
        val set = activeSet()
        if (set != null && set.proc != GearProc.NONE) return set.proc to set.procPower
        val w = equippedWeapon()
        return w.proc to w.procPower
    }

    fun addBagItem(id: String, n: Int = 1) {
        if (n <= 0) return
        bag[id] = (bag[id] ?: 0) + n
    }

    fun bagCount(id: String): Int = bag[id] ?: 0

    /** 使用背包道具；成功返回 true */
    fun useBagItem(id: String): Boolean {
        val n = bag[id] ?: 0
        if (n <= 0) return false
        val def = ItemCatalog.byId(id) ?: return false
        ensureVitals()
        when (def.effect) {
            "heal" -> {
                curHp = (curHp + maxHp() * 0.28f).coerceAtMost(maxHp())
                toast = "使用${def.name}：回复生命"
            }
            "mp" -> {
                curMp = (curMp + maxMp() * 0.40f).coerceAtMost(maxMp())
                toast = "使用${def.name}：回复法力"
            }
            "element" -> {
                buffElement = def.element
                buffAtk = 6f
                toast = "使用${def.name}：下一战${def.element?.short}属性·攻+6"
            }
            "burn" -> {
                buffBurn = true
                toast = "使用${def.name}：下一战附带点燃"
            }
            "armor" -> {
                buffDr = 0.12f
                toast = "使用${def.name}：下一战减伤12%"
            }
            else -> return false
        }
        bag[id] = n - 1
        if (bag[id] == 0) bag.remove(id)
        toastT = 1.8f
        return true
    }

    /** 开战时消费一次性战前 buff 标记（减伤/点燃在战斗中生效后清） */
    fun consumeFightBuffsAfterCombat() {
        buffElement = null
        buffAtk = 0f
        buffDr = 0f
        buffBurn = false
    }

    fun openTravelingShop() {
        refreshShopOffers()
        toast = shopRecommendTip()
        toastT = 2.2f
    }

    /** 根据即将面对的战斗五行，优先上架克制武器（预算内） */
    fun refreshShopOffers(preferElement: WuXing? = null) {
        val prefer = preferElement ?: nextCombatRecommendElement()
        shopWeaponOffers = WeaponCatalog.shopOffers(hero, 3, prefer, budget = gold + 40).map { it.id }
        shopArmorOffers = ArmorCatalog.shopOffers(hero, 2, budget = gold + 40).map { it.id }
        shopRingOffers = RingCatalog.shopOffers(hero, 1).map { it.id }
        shopBootsOffers = BootsCatalog.shopOffers(hero, 1).map { it.id }
        shopTomeId = TomeCatalog.all.random().id
        shopFruitOffers = ItemCatalog.shopFruits(2).map { it.id }
    }

    /** 向前搜索最近战点，返回推荐武器五行（克制该关） */
    fun nextCombatRecommendElement(): WuXing? {
        val n = nextCombatNode() ?: return null
        return n.recommendWeaponElement()
    }

    fun nextCombatNode(): MapNode? {
        val stage = stage()
        val cur = stage.nodes.find { it.id == nodeId } ?: return null
        val queue = ArrayDeque(cur.next)
        val seen = mutableSetOf(cur.id)
        var guard = 0
        while (queue.isNotEmpty() && guard < 16) {
            guard++
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            val n = stage.nodes.find { it.id == id } ?: continue
            if (n.type == NodeType.MOB || n.type == NodeType.ELITE || n.type == NodeType.BOSS) return n
            queue.addAll(n.next)
        }
        return null
    }

    fun nextCombatThemeLine(): String {
        val stage = stage()
        val cur = stage.nodes.find { it.id == nodeId } ?: return ""
        val nexts = cur.next.mapNotNull { id -> stage.nodes.find { it.id == id } }
        if (nexts.isEmpty()) return "无下一路点"
        return nexts.joinToString(" | ") { n ->
            val el = n.resolvedElement()
            val base = "${n.name}(${nodeRiskTag(n.type)})"
            if (el != null) {
                val rec = el.beatenBy()
                "$base ${el.gateName()}→荐${rec.short}"
            } else base
        }
    }

    fun shopRecommendTip(): String {
        val n = nextCombatNode() ?: return "货架随机 · 按需购买"
        val el = n.resolvedElement() ?: return "下一战点：${n.name}"
        val rec = el.beatenBy()
        return "下一${el.gateName()}(${n.name}) · 荐买${rec.short}系 · ${rec.short}克${el.short}"
    }

    fun grantWeapon(w: GearWeapon): Boolean {
        if (w.id in ownedWeapons) return false
        ownedWeapons.add(w.id)
        return true
    }

    fun grantArmor(a: GearArmor): Boolean {
        if (a.id in ownedArmors) return false
        ownedArmors.add(a.id)
        return true
    }

    fun equipWeapon(id: String): Boolean {
        if (id !in ownedWeapons) return false
        val w = WeaponCatalog.byId(id) ?: return false
        if (!w.canEquip(hero)) {
            toast = "仅${w.classLabel()}可装备「${w.name}」"
            toastT = 1.6f
            return false
        }
        equippedWeaponId = id
        ensureVitals()
        val set = activeSet()
        if (set != null) {
            toast = "套装发动：${set.name}·${set.bonusTitle}"
            toastT = 2.0f
        }
        return true
    }

    fun equipArmor(id: String): Boolean {
        if (id !in ownedArmors) return false
        val a = ArmorCatalog.byId(id) ?: return false
        if (!a.canEquip(hero)) {
            toast = "仅${a.classLabel()}可穿「${a.name}」"
            toastT = 1.6f
            return false
        }
        equippedArmorId = id
        ensureVitals()
        val set = activeSet()
        if (set != null) {
            toast = "套装发动：${set.name}·${set.bonusTitle}"
            toastT = 2.0f
        }
        return true
    }

    fun grantRing(a: GearAccessory): Boolean {
        if (a.slot != AccSlot.RING || a.id in ownedRings) return false
        ownedRings.add(a.id)
        return true
    }

    fun grantBoots(a: GearAccessory): Boolean {
        if (a.slot != AccSlot.BOOTS || a.id in ownedBoots) return false
        ownedBoots.add(a.id)
        return true
    }

    fun equipRing(id: String): Boolean {
        if (id.isBlank()) {
            equippedRingId = ""
            return true
        }
        if (id !in ownedRings) return false
        val a = RingCatalog.byId(id) ?: return false
        if (!a.canEquip(hero)) {
            toast = "仅${a.classLabel()}可戴「${a.name}」"
            toastT = 1.6f
            return false
        }
        equippedRingId = id
        toast = "装备戒指 ${a.name}"
        toastT = 1.3f
        return true
    }

    fun equipBoots(id: String): Boolean {
        if (id !in ownedBoots) return false
        val a = BootsCatalog.byId(id) ?: return false
        if (!a.canEquip(hero)) {
            toast = "仅${a.classLabel()}可穿「${a.name}」"
            toastT = 1.6f
            return false
        }
        equippedBootsId = id
        toast = "装备鞋子 ${a.name}"
        toastT = 1.3f
        ensureVitals()
        return true
    }

    /**
     * 统一购买：钱不够绝不扣款、绝不入库。
     * @return null=成功，否则错误提示
     */
    fun tryBuyWeapon(id: String): String? {
        val w = WeaponCatalog.byId(id) ?: return "无效武器"
        if (w.cost <= 0) return "非卖品"
        if (id in ownedWeapons) return "已拥有「${w.name}」"
        if (!w.canEquip(hero)) return "仅${w.classLabel()}可买"
        if (gold < w.cost) return "金币不足(需${w.cost} 现有$gold)"
        gold = (gold - w.cost).coerceAtLeast(0)
        ownedWeapons.add(w.id)
        equipWeapon(w.id)
        toast = "购入 ${w.name} −${w.cost}金"
        toastT = 1.6f
        return null
    }

    fun tryBuyArmor(id: String): String? {
        val a = ArmorCatalog.byId(id) ?: return "无效防具"
        if (a.cost <= 0) return "非卖品"
        if (id in ownedArmors) return "已拥有「${a.name}」"
        if (!a.canEquip(hero)) return "仅${a.classLabel()}可买"
        if (gold < a.cost) return "金币不足(需${a.cost} 现有$gold)"
        gold = (gold - a.cost).coerceAtLeast(0)
        ownedArmors.add(a.id)
        equipArmor(a.id)
        toast = "购入防具 ${a.name} −${a.cost}金"
        toastT = 1.6f
        return null
    }

    fun tryBuyRing(id: String): String? {
        val a = RingCatalog.byId(id) ?: return "无效戒指"
        if (a.cost <= 0) return "非卖品"
        if (id in ownedRings) return "已拥有「${a.name}」"
        if (!a.canEquip(hero)) return "仅${a.classLabel()}可买"
        if (gold < a.cost) return "金币不足(需${a.cost} 现有$gold)"
        gold = (gold - a.cost).coerceAtLeast(0)
        ownedRings.add(a.id)
        equipRing(a.id)
        toast = "购入戒指 ${a.name} −${a.cost}金"
        toastT = 1.6f
        return null
    }

    fun tryBuyBoots(id: String): String? {
        val a = BootsCatalog.byId(id) ?: return "无效鞋子"
        if (a.cost <= 0) return "非卖品"
        if (id in ownedBoots) return "已拥有「${a.name}」"
        if (!a.canEquip(hero)) return "仅${a.classLabel()}可买"
        if (gold < a.cost) return "金币不足(需${a.cost} 现有$gold)"
        gold = (gold - a.cost).coerceAtLeast(0)
        ownedBoots.add(a.id)
        equipBoots(a.id)
        toast = "购入鞋子 ${a.name} −${a.cost}金"
        toastT = 1.6f
        return null
    }

    fun tryBuyFruit(id: String): String? {
        val f = ItemCatalog.byId(id) ?: return "无效道具"
        if (f.cost <= 0) return "非卖品"
        if (gold < f.cost) return "金币不足(需${f.cost} 现有$gold)"
        gold = (gold - f.cost).coerceAtLeast(0)
        addBagItem(f.id, 1)
        toast = "购入 ${f.name} −${f.cost}金"
        toastT = 1.5f
        return null
    }

    fun tryBuyPotion(price: Int = 18): String? {
        if (gold < price) return "金币不足(需$price 现有$gold)"
        gold = (gold - price).coerceAtLeast(0)
        potions++
        toast = "药水 +1 −${price}金"
        toastT = 1.3f
        return null
    }

    /** 背包列表：已装备优先 */
    fun ownedWeaponsSorted(): List<GearWeapon> =
        ownedWeapons.mapNotNull { WeaponCatalog.byId(it) }
            .filter { it.canEquip(hero) }
            .sortedWith(compareByDescending<GearWeapon> { it.id == equippedWeaponId }
                .thenBy { it.tierLevel() }
                .thenBy { it.cost })

    fun ownedArmorsSorted(): List<GearArmor> =
        ownedArmors.mapNotNull { ArmorCatalog.byId(it) }
            .filter { it.canEquip(hero) }
            .sortedWith(compareByDescending<GearArmor> { it.id == equippedArmorId }
                .thenBy { it.tier }
                .thenBy { it.cost })

    fun ownedRingsSorted(): List<GearAccessory> =
        ownedRings.mapNotNull { RingCatalog.byId(it) }
            .filter { it.canEquip(hero) }
            .sortedWith(compareByDescending<GearAccessory> { it.id == equippedRingId }
                .thenBy { it.tier }
                .thenBy { it.cost })

    fun ownedBootsSorted(): List<GearAccessory> =
        ownedBoots.mapNotNull { BootsCatalog.byId(it) }
            .filter { it.canEquip(hero) }
            .sortedWith(compareByDescending<GearAccessory> { it.id == equippedBootsId }
                .thenBy { it.tier }
                .thenBy { it.cost })

    fun buyTome(t: SkillTome): Boolean {
        if (gold < t.cost) return false
        gold -= t.cost
        ownedTomes.add(t.id)
        skillPowerBonus += t.powerBonus
        return true
    }

    fun queueStory(beats: List<StoryBeat>, returnTo: String, thenArena: Boolean = false) {
        if (beats.isEmpty()) {
            storyQueue = emptyList()
            storyIndex = 0
            pendingArenaAfterStory = thenArena
            return
        }
        storyQueue = beats
        storyIndex = 0
        storyReturn = returnTo
        pendingArenaAfterStory = thenArena
    }

    fun currentBeat(): StoryBeat? = storyQueue.getOrNull(storyIndex)

    fun advanceStory(): Boolean {
        if (storyIndex + 1 < storyQueue.size) {
            storyIndex++
            return true
        }
        storyQueue = emptyList()
        storyIndex = 0
        return false
    }

    fun addJournal(line: String) {
        if (journal.size > 24) journal.removeAt(0)
        journal.add(line)
    }

    /** 本局词缀（由种子+墨阶决定，继续游戏也能还原） */
    fun inkAffixes(): List<InkAffix> {
        if (affixCache == null) {
            affixCache = rollInkAffixes(runSeed, inkRank.coerceIn(0, 7))
        }
        return affixCache!!
    }

    fun combatMods(): CombatMods = combatModsFrom(inkAffixes())

    fun affixHudLine(): String = affixShort(inkAffixes())

    fun maxHp(): Float {
        val forgeArm = armorUpgradeTable()[armorLevel.coerceIn(0, armorUpgradeTable().lastIndex)]
        val gearHp = equippedWeapon().hpBonus + equippedArmor().hpBonus + (activeSet()?.hpBonus ?: 0f) +
            (equippedRing()?.hpBonus ?: 0f) + equippedBoots().hpBonus
        val mul = 1f + if (PassiveId.HP_UP in passives) 0.15f else 0f
        return (hero.baseHp + forgeArm.hpBonus + gearHp) * mul * combatMods().playerHpMul
    }

    fun maxMp(): Float =
        hero.baseMp + equippedWeapon().mpBonus + equippedArmor().mpBonus + (activeSet()?.mpBonus ?: 0f) +
            (equippedRing()?.mpBonus ?: 0f) + equippedBoots().mpBonus

    fun ensureVitals() {
        if (curHp < 0f) curHp = maxHp()
        if (curMp < 0f) curMp = maxMp()
        curHp = curHp.coerceIn(0f, maxHp())
        curMp = curMp.coerceIn(0f, maxMp())
    }

    fun addXp(amount: Int): Boolean {
        xp += amount
        var leveled = false
        // 合理曲线：约 1~2 战升 Lv2，中段 Lv3~4，Boss 前可摸 Lv5
        while (xp >= xpToLevel) {
            xp -= xpToLevel
            level++
            xpToLevel = when {
                level <= 2 -> 22
                level == 3 -> 36
                level == 4 -> 48
                else -> 40 + level * 12
            }
            leveled = true
        }
        return leveled
    }

    /** 本局关卡表（含墨阶缩放与种子变异） */
    fun stages(): List<StageDef> {
        if (stageCache == null) {
            stageCache = stagesForRun(runSeed, inkRank.coerceIn(0, 7))
        }
        return stageCache!!
    }

    fun stage(): StageDef {
        val list = stages()
        return list[stageIndex.coerceIn(0, list.lastIndex)]
    }

    fun invalidateStageCache() {
        stageCache = null
        affixCache = null
    }

    fun resetRun(h: HeroClass, rank: Int = inkRank, forcedSeed: Long? = null) {
        hero = h
        adDoubleClaimed = false
        adPotionClaims = 0
        runSeed = forcedSeed ?: System.nanoTime()
        inkRank = rank.coerceIn(0, 7)
        stageCache = null
        affixCache = null
        skinId = SkinCatalog.defaultFor(h).id
        weaponLevel = 0
        armorLevel = 0
        potions = 1 + (inkRank / 3)
        gold = 32 + inkRank * 6 + if (InkAffix.THICK_GOLD in inkAffixes()) 8 else 0
        goldEarnedThisRun = 0
        level = 1
        xp = 0
        xpToLevel = 22
        passives.clear()
        stageIndex = 0
        nodeId = 0
        visited = mutableSetOf(0)
        curHp = -1f
        curMp = -1f
        kills = 0
        roomsCleared = 0
        paused = false
        storyQueue = emptyList()
        storyIndex = 0
        journal.clear()
        pendingArenaAfterStory = false
        arenaBanner = ""
        arenaBannerT = 0f
        toast = ""
        toastT = 0f
        pendingLootLine = ""
        ownedWeapons.clear()
        val starter = WeaponCatalog.starter(h)
        ownedWeapons.add(starter.id)
        equippedWeaponId = starter.id
        ownedArmors.clear()
        ownedArmors.add("a_cloth")
        val t1 = ArmorCatalog.usableBy(h).filter { it.tier == 1 && it.cost > 0 }.firstOrNull()
        if (t1 != null) ownedArmors.add(t1.id)
        equippedArmorId = t1?.id ?: "a_cloth"
        ownedRings.clear()
        equippedRingId = ""
        ownedBoots.clear()
        ownedBoots.add("b_cloth")
        equippedBootsId = "b_cloth"
        skillPowerBonus = 0f
        ownedTomes.clear()
        bag.clear()
        addBagItem("fruit_wood", 1)
        shopFruitOffers = emptyList()
        shopArmorOffers = emptyList()
        shopRingOffers = emptyList()
        shopBootsOffers = emptyList()
        pendingShopAfterEvent = false
        buffElement = null
        buffAtk = 0f
        buffDr = 0f
        buffBurn = false
        refreshShopOffers()
        ensureVitals()
        val af = inkAffixes()
        toast = "本局词缀：${affixShort(af)}"
        toastT = 2.8f
        addJournal("落墨·${if (inkRank > 0) "墨$inkRank" else "试笔"} · ${affixLine(af)}")
    }

    fun fillResult(won: Boolean) {
        resultTitle = if (won) "远征成功！" else "远征失利"
        resultBody = buildString {
            append(if (won) "落款完成，画卷合上了。\n" else "墨渍未干，卷轴还可再展。\n")
            append("职业 ${hero.displayName} · 墨阶 $inkRank\n")
            append("等级 Lv$level  章节 ${stageIndex + 1}\n")
            append("清房 $roomsCleared  击杀 $kills\n")
            append("本局金币 $goldEarnedThisRun  持有 $gold\n")
            append("词缀 ${affixShort(inkAffixes()).ifBlank { "无" }}\n")
            append("武器 ${equippedWeapon().name}[${playerElement().short}]\n")
            append("天赋 ${if (passives.isEmpty()) "无" else passives.joinToString("、") { it.title }}\n")
            append("\n（生涯进度已写入本地）\n")
            if (journal.isNotEmpty()) {
                append("\n—— 旅途摘录 ——\n")
                journal.takeLast(3).forEach { append("· $it\n") }
            }
        }
    }
}
