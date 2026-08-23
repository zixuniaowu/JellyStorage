package com.jellystorage.play

import android.content.Context
import android.content.SharedPreferences

/**
 * Lifetime stats + optional mid-run save for "继续冒险".
 */
class ProgressStore(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var runsStarted: Int
        get() = sp.getInt(K_RUNS, 0)
        set(v) { sp.edit().putInt(K_RUNS, v).apply() }

    var runsWon: Int
        get() = sp.getInt(K_WINS, 0)
        set(v) { sp.edit().putInt(K_WINS, v).apply() }

    var bestStage: Int
        get() = sp.getInt(K_BEST_STAGE, 0)
        set(v) { sp.edit().putInt(K_BEST_STAGE, v).apply() }

    var bestLevel: Int
        get() = sp.getInt(K_BEST_LV, 1)
        set(v) { sp.edit().putInt(K_BEST_LV, v).apply() }

    var lifetimeGold: Int
        get() = sp.getInt(K_GOLD, 0)
        set(v) { sp.edit().putInt(K_GOLD, v).apply() }

    var lifetimeKills: Int
        get() = sp.getInt(K_KILLS, 0)
        set(v) { sp.edit().putInt(K_KILLS, v).apply() }

    var seenTutorial: Boolean
        get() = sp.getBoolean(K_TUT, false)
        set(v) { sp.edit().putBoolean(K_TUT, v).apply() }

    var soundOn: Boolean
        get() = sp.getBoolean(K_SND, true)
        set(v) { sp.edit().putBoolean(K_SND, v).apply() }

    var hapticsOn: Boolean
        get() = sp.getBoolean(K_HAP, true)
        set(v) { sp.edit().putBoolean(K_HAP, v).apply() }

    var language: GameLanguage
        get() = GameLanguage.fromCode(sp.getString(K_LANGUAGE, null))
        set(v) { sp.edit().putString(K_LANGUAGE, v.code).apply() }

    /** 已解锁最高病毒变异代次；沿用旧存档键，避免升级后丢失周目。 */
    var inkRankUnlocked: Int
        get() = sp.getInt(K_INK_UNLOCK, 0).coerceIn(0, MAX_MUTATION_GENERATION)
        set(v) { sp.edit().putInt(K_INK_UNLOCK, v.coerceIn(0, MAX_MUTATION_GENERATION)).apply() }

    /** 下次感染周期选用的病毒变异代次。 */
    var preferredInkRank: Int
        get() = sp.getInt(K_INK_PREF, 0).coerceIn(0, inkRankUnlocked)
        set(v) { sp.edit().putInt(K_INK_PREF, v.coerceIn(0, inkRankUnlocked)).apply() }

    var preferredImmuneMemoryId: String
        get() {
            val unlocked = ImmuneMemory.unlocked(inkRankUnlocked)
            val saved = ImmuneMemory.byId(sp.getString(K_IMMUNE_MEMORY, null))
            return (saved?.takeIf { it in unlocked } ?: unlocked.first()).id
        }
        set(v) {
            val selected = ImmuneMemory.byId(v)
                ?.takeIf { it in ImmuneMemory.unlocked(inkRankUnlocked) }
                ?: ImmuneMemory.CLOTTING_BARRIER
            sp.edit().putString(K_IMMUNE_MEMORY, selected.id).apply()
        }

    fun preferredImmuneMemory(): ImmuneMemory =
        ImmuneMemory.byId(preferredImmuneMemoryId) ?: ImmuneMemory.CLOTTING_BARRIER

    fun cyclePreferredImmuneMemory(): ImmuneMemory {
        val unlocked = ImmuneMemory.unlocked(inkRankUnlocked)
        val current = unlocked.indexOfFirst { it.id == preferredImmuneMemoryId }.coerceAtLeast(0)
        val next = unlocked[(current + 1) % unlocked.size]
        preferredImmuneMemoryId = next.id
        return next
    }

    /** 本地账号：仅存本机，无服务器 */
    fun hasLocalAccount(): Boolean = !sp.getString(K_USER, null).isNullOrBlank()

    fun localUsername(): String = sp.getString(K_USER, "") ?: ""

    fun localPassword(): String = sp.getString(K_PASS, "") ?: ""

    fun isGuestAccount(): Boolean = sp.getBoolean(K_GUEST, false)

    fun registerLocal(username: String, password: String, asGuest: Boolean = false): String? {
        val u = username.trim()
        val p = password
        if (u.length < 2) return "用户名至少2个字"
        if (p.length < 4) return "密码至少4位"
        // 本机单账号：禁止静默覆盖已有账号（避免误点「一键」丢档）
        if (hasLocalAccount() && localUsername() != u) {
            return "本机已有账号「${localUsername()}」，请登录；或设置里退出后清除"
        }
        sp.edit()
            .putString(K_USER, u)
            .putString(K_PASS, p)
            .putBoolean(K_SESSION, true)
            .putBoolean(K_GUEST, asGuest)
            .apply()
        return null
    }

    /**
     * 一键进入：已有账号则直接登录该账号；否则新建游客。
     * 不会静默覆盖已有用户名。
     */
    fun createGuestAndLogin(): Pair<String, String> {
        if (hasLocalAccount()) {
            sp.edit().putBoolean(K_SESSION, true).apply()
            return localUsername() to localPassword()
        }
        val u = GuestIds.username()
        val p = GuestIds.password()
        registerLocal(u, p, asGuest = true)
        return u to p
    }

    /** 清除本机账号与会话（角色/图鉴/生涯另计，避免误清角色） */
    fun clearLocalAccountOnly() {
        sp.edit()
            .remove(K_USER)
            .remove(K_PASS)
            .putBoolean(K_SESSION, false)
            .putBoolean(K_GUEST, false)
            .apply()
    }

    fun loginLocal(username: String, password: String): String? {
        if (!hasLocalAccount()) return "请先注册或一键进入"
        val u = username.trim()
        if (u != localUsername()) return "用户名不正确"
        if (password != localPassword()) return "密码不正确"
        sp.edit().putBoolean(K_SESSION, true).apply()
        return null
    }

    fun isSessionLoggedIn(): Boolean = sp.getBoolean(K_SESSION, false) && hasLocalAccount()

    fun logoutLocal() {
        sp.edit().putBoolean(K_SESSION, false).apply()
    }

    // ── 角色槽 ──

    fun listCharacters(): List<GameCharacter> {
        val raw = sp.getString(K_CHARS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull { GameCharacter.decode(it) }
    }

    fun activeCharacterId(): String = sp.getString(K_ACTIVE_CHAR, "") ?: ""

    fun activeCharacter(): GameCharacter? {
        val id = activeCharacterId()
        return listCharacters().find { it.id == id } ?: listCharacters().firstOrNull()
    }

    fun selectCharacter(id: String): Boolean {
        val c = listCharacters().find { it.id == id } ?: return false
        sp.edit().putString(K_ACTIVE_CHAR, c.id).apply()
        return true
    }

    fun createCharacter(name: String, hero: HeroClass): GameCharacter? {
        val list = listCharacters().toMutableList()
        if (list.size >= MAX_CHARS) return null
        val n = name.trim().ifBlank { GameCharacter.autoName(list.size) }
        if (n.length > 12) return null
        val c = GameCharacter(
            id = "c${System.currentTimeMillis().toString(36)}${list.size}",
            name = n,
            hero = hero
        )
        list.add(c)
        saveCharacters(list)
        sp.edit().putString(K_ACTIVE_CHAR, c.id).apply()
        return c
    }

    fun deleteCharacter(id: String) {
        val list = listCharacters().filter { it.id != id }
        saveCharacters(list)
        if (activeCharacterId() == id) {
            sp.edit().putString(K_ACTIVE_CHAR, list.firstOrNull()?.id ?: "").apply()
        }
        // 清该角色存档
        clearActiveRunFor(id)
    }

    fun touchCharacterRunStart(hero: HeroClass) {
        val id = activeCharacterId()
        if (id.isBlank()) return
        val list = listCharacters().map {
            if (it.id == id) it.copy(runs = it.runs + 1, hero = hero) else it
        }
        saveCharacters(list)
    }

    fun touchCharacterProgress(stageIndex: Int) {
        val id = activeCharacterId()
        if (id.isBlank()) return
        val stage = stageIndex + 1
        val list = listCharacters().map {
            if (it.id == id && stage > it.bestStage) it.copy(bestStage = stage) else it
        }
        saveCharacters(list)
    }

    private fun saveCharacters(list: List<GameCharacter>) {
        sp.edit().putString(K_CHARS, list.joinToString("\n") { it.encode() }).apply()
    }

    /** 存档键按当前角色隔离 */
    private fun rk(key: String): String {
        val id = activeCharacterId()
        return if (id.isBlank()) key else "c_${id}_$key"
    }

    fun hasActiveRun(): Boolean = sp.getBoolean(rk(K_RUN_ACTIVE), false)

    fun activeRunSummary(): String {
        if (!hasActiveRun()) return ""
        val heroRaw = sp.getString(rk(K_RUN_HERO), HeroClass.WARRIOR.name) ?: HeroClass.WARRIOR.name
        val hero = HeroClass.entries.firstOrNull { it.name == heroRaw }?.displayName ?: heroRaw
        val stage = sp.getInt(rk(K_RUN_STAGE), 0) + 1
        val lv = sp.getInt(rk(K_RUN_LV), 1)
        val node = sp.getString(rk(K_RUN_NODE_NAME), "路上") ?: "路上"
        val charName = activeCharacter()?.name?.let { "$it · " } ?: ""
        return "$charName$hero  Lv$lv  第${stage}章 · $node"
    }

    fun recordRunStart() {
        sp.edit().putInt(K_RUNS, runsStarted + 1).apply()
        touchCharacterRunStart(activeCharacter()?.hero ?: HeroClass.WARRIOR)
    }

    fun recordRunEnd(
        won: Boolean,
        stageIndex: Int,
        level: Int,
        goldEarned: Int,
        kills: Int
    ) {
        val ed = sp.edit()
        if (won) ed.putInt(K_WINS, runsWon + 1)
        val stage = stageIndex + 1
        if (stage > bestStage) ed.putInt(K_BEST_STAGE, stage)
        if (level > bestLevel) ed.putInt(K_BEST_LV, level)
        if (goldEarned > 0) ed.putInt(K_GOLD, lifetimeGold + goldEarned)
        if (kills > 0) ed.putInt(K_KILLS, lifetimeKills + kills)
        clearActiveRun(ed)
        ed.commit()
        touchCharacterProgress(stageIndex)
        // 完成感染周期后，病毒进入下一变异代，并解锁新的免疫记忆。
        if (won) {
            val next = (inkRankUnlocked + 1).coerceAtMost(MAX_MUTATION_GENERATION)
            if (next > inkRankUnlocked) inkRankUnlocked = next
            preferredInkRank = inkRankUnlocked
        }
    }

    /** Snapshot map progress so player can resume later. */
    fun saveActiveRun(meta: RunMeta) {
        meta.ensureVitals()
        val nodeName = try {
            meta.stage().nodes.find { it.id == meta.nodeId }?.name ?: "地图"
        } catch (_: Exception) {
            "地图"
        }
        sp.edit()
            .putBoolean(rk(K_RUN_ACTIVE), true)
            .putString(rk(K_RUN_HERO), meta.hero.name)
            .putString(rk(K_RUN_SKIN), meta.skinId)
            .putInt(rk(K_RUN_STAGE), meta.stageIndex)
            .putLong(rk(K_RUN_SEED), meta.runSeed)
            .putInt(rk(K_RUN_INK), meta.inkRank)
            .putString(rk(K_RUN_MEMORY), meta.immuneMemoryId)
            .putInt(rk(K_RUN_NODE), meta.nodeId)
            .putString(rk(K_RUN_NODE_NAME), nodeName)
            .putInt(rk(K_RUN_GOLD), meta.gold)
            .putInt(rk(K_RUN_GOLD_EARNED), meta.goldEarnedThisRun)
            .putInt(rk(K_RUN_WPN), meta.weaponLevel)
            .putInt(rk(K_RUN_ARM), meta.armorLevel)
            .putInt(rk(K_RUN_POT), meta.potions)
            .putInt(rk(K_RUN_LV), meta.level)
            .putInt(rk(K_RUN_XP), meta.xp)
            .putInt(rk(K_RUN_XP_NEED), meta.xpToLevel)
            .putFloat(rk(K_RUN_HP), meta.curHp)
            .putFloat(rk(K_RUN_MP), meta.curMp)
            .putString(rk(K_RUN_PASSIVES), meta.passives.joinToString(",") { it.name })
            .putString(
                rk(K_RUN_CORE_INKS),
                encodeCoreInkRanks(meta.coreInkRanks)
            )
            .putString(rk(K_RUN_VISITED), meta.visited.joinToString(","))
            .putInt(rk(K_RUN_KILLS), meta.kills)
            .putInt(rk(K_RUN_ROOMS), meta.roomsCleared)
            .putString(rk(K_RUN_JOURNAL), meta.journal.takeLast(12).joinToString("\u0001"))
            .putString(rk(K_RUN_WEAPONS), meta.ownedWeapons.joinToString(","))
            .putString(rk(K_RUN_EQ), meta.equippedWeaponId)
            .putString(rk(K_RUN_ARMORS), meta.ownedArmors.joinToString(","))
            .putString(rk(K_RUN_EQ_ARM), meta.equippedArmorId)
            .putString(rk(K_RUN_RINGS), meta.ownedRings.joinToString(","))
            .putString(rk(K_RUN_EQ_RING), meta.equippedRingId)
            .putString(rk(K_RUN_BOOTS), meta.ownedBoots.joinToString(","))
            .putString(rk(K_RUN_EQ_BOOTS), meta.equippedBootsId)
            .putFloat(rk(K_RUN_SKILL_POW), meta.skillPowerBonus)
            .putString(rk(K_RUN_TOMES), meta.ownedTomes.joinToString(","))
            .putString(rk(K_RUN_BAG), meta.bag.entries.joinToString(",") { "${it.key}:${it.value}" })
            .commit()
        unlockCollection(
            meta.ownedWeapons, meta.ownedArmors,
            meta.ownedRings, meta.ownedBoots
        )
        touchCharacterProgress(meta.stageIndex)
    }

    fun unlockCollection(
        weapons: Collection<String>,
        armors: Collection<String>,
        rings: Collection<String> = emptyList(),
        boots: Collection<String> = emptyList()
    ) {
        val wSet = discoveredWeapons().toMutableSet()
        val aSet = discoveredArmors().toMutableSet()
        val rSet = discoveredRings().toMutableSet()
        val bSet = discoveredBoots().toMutableSet()
        var changed = false
        weapons.forEach { if (wSet.add(it)) changed = true }
        armors.forEach { if (aSet.add(it)) changed = true }
        rings.forEach { if (rSet.add(it)) changed = true }
        boots.forEach { if (bSet.add(it)) changed = true }
        if (changed) {
            sp.edit()
                .putString(K_DISC_WPN, wSet.joinToString(","))
                .putString(K_DISC_ARM, aSet.joinToString(","))
                .putString(K_DISC_RING, rSet.joinToString(","))
                .putString(K_DISC_BOOTS, bSet.joinToString(","))
                .apply()
        }
    }

    fun discoveredWeapons(): Set<String> =
        (sp.getString(K_DISC_WPN, "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun discoveredArmors(): Set<String> =
        (sp.getString(K_DISC_ARM, "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun discoveredRings(): Set<String> =
        (sp.getString(K_DISC_RING, "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun discoveredBoots(): Set<String> =
        (sp.getString(K_DISC_BOOTS, "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun isWeaponKnown(id: String) = id in discoveredWeapons() || WeaponCatalog.byId(id)?.cost == 0
    fun isArmorKnown(id: String) = id in discoveredArmors() || ArmorCatalog.byId(id)?.cost == 0
    fun isRingKnown(id: String) = id in discoveredRings() || RingCatalog.byId(id)?.cost == 0
    fun isBootsKnown(id: String) = id in discoveredBoots() || BootsCatalog.byId(id)?.cost == 0

    fun collectionProgress(): Pair<Int, Int> {
        val total = WeaponCatalog.all.size + ArmorCatalog.all.size + RingCatalog.all.size + BootsCatalog.all.size
        val knownW = WeaponCatalog.all.count { isWeaponKnown(it.id) }
        val knownA = ArmorCatalog.all.count { isArmorKnown(it.id) }
        val knownR = RingCatalog.all.count { isRingKnown(it.id) }
        val knownB = BootsCatalog.all.count { isBootsKnown(it.id) }
        return (knownW + knownA + knownR + knownB) to total
    }

    fun loadActiveRun(meta: RunMeta): Boolean {
        if (!hasActiveRun()) return false
        val heroName = sp.getString(rk(K_RUN_HERO), null) ?: return false
        val hero = try {
            HeroClass.valueOf(heroName)
        } catch (_: Exception) {
            return false
        }
        meta.hero = hero
        meta.characterName = activeCharacter()?.name ?: ""
        meta.skinId = sp.getString(rk(K_RUN_SKIN), SkinCatalog.defaultFor(hero).id)
            ?: SkinCatalog.defaultFor(hero).id
        meta.runSeed = sp.getLong(rk(K_RUN_SEED), System.nanoTime())
        meta.inkRank = sp.getInt(rk(K_RUN_INK), 0).coerceIn(0, MAX_MUTATION_GENERATION)
        meta.immuneMemoryId = sp.getString(rk(K_RUN_MEMORY), preferredImmuneMemoryId)
            ?: ImmuneMemory.CLOTTING_BARRIER.id
        meta.invalidateStageCache()
        meta.stageIndex = sp.getInt(rk(K_RUN_STAGE), 0).coerceIn(0, meta.stages().lastIndex)
        meta.nodeId = sp.getInt(rk(K_RUN_NODE), 0)
        val stage = meta.stage()
        if (stage.nodes.none { it.id == meta.nodeId }) {
            meta.nodeId = stage.nodes.firstOrNull()?.id ?: 0
        }
        meta.gold = sp.getInt(rk(K_RUN_GOLD), 30)
        meta.goldEarnedThisRun = sp.getInt(rk(K_RUN_GOLD_EARNED), 0)
        meta.weaponLevel = sp.getInt(rk(K_RUN_WPN), 0)
        meta.armorLevel = sp.getInt(rk(K_RUN_ARM), 0)
        meta.potions = sp.getInt(rk(K_RUN_POT), 1)
        meta.level = sp.getInt(rk(K_RUN_LV), 1)
        meta.xp = sp.getInt(rk(K_RUN_XP), 0)
        // 经验曲线由版本规则决定，不沿用旧存档里过小的门槛（曾导致一战满技能）。
        meta.xpToLevel = xpRequirementForLevel(meta.level)
        meta.curHp = sp.getFloat(rk(K_RUN_HP), -1f)
        meta.curMp = sp.getFloat(rk(K_RUN_MP), -1f)
        meta.passives.clear()
        val pass = sp.getString(rk(K_RUN_PASSIVES), "") ?: ""
        if (pass.isNotBlank()) {
            pass.split(",").forEach { name ->
                try {
                    meta.passives.add(PassiveId.valueOf(name))
                } catch (_: Exception) { /* skip */ }
            }
        }
        meta.coreInkRanks.clear()
        val coreInks = sp.getString(rk(K_RUN_CORE_INKS), "") ?: ""
        meta.coreInkRanks.putAll(decodeCoreInkRanks(coreInks, meta.hero))
        meta.visited = (sp.getString(rk(K_RUN_VISITED), "0") ?: "0")
            .split(",")
            .mapNotNull { it.toIntOrNull() }
            .toMutableSet()
        if (meta.visited.isEmpty()) meta.visited.add(meta.nodeId)
        meta.kills = sp.getInt(rk(K_RUN_KILLS), 0)
        meta.roomsCleared = sp.getInt(rk(K_RUN_ROOMS), 0)
        meta.journal.clear()
        val j = sp.getString(rk(K_RUN_JOURNAL), "") ?: ""
        if (j.isNotBlank()) meta.journal.addAll(j.split("\u0001").filter { it.isNotBlank() })
        meta.paused = false
        meta.storyQueue = emptyList()
        meta.storyIndex = 0
        meta.pendingArenaAfterStory = false
        meta.coreInkChoices = emptyList()
        meta.pendingLevelAfterCore = false
        meta.arenaBanner = ""
        meta.arenaBannerT = 0f
        meta.setAwakenedId = ""
        meta.setAwakenedT = 0f
        meta.setAwakenedHapticPending = false
        meta.ownedWeapons.clear()
        val wpn = sp.getString(rk(K_RUN_WEAPONS), "") ?: ""
        if (wpn.isNotBlank()) meta.ownedWeapons.addAll(wpn.split(",").filter { it.isNotBlank() })
        if (meta.ownedWeapons.isEmpty()) meta.ownedWeapons.add(WeaponCatalog.starter(hero).id)
        meta.equippedWeaponId = sp.getString(rk(K_RUN_EQ), WeaponCatalog.starter(hero).id)
            ?: WeaponCatalog.starter(hero).id
        meta.ownedArmors.clear()
        val arms = sp.getString(rk(K_RUN_ARMORS), "") ?: ""
        if (arms.isNotBlank()) meta.ownedArmors.addAll(arms.split(",").filter { it.isNotBlank() })
        if (meta.ownedArmors.isEmpty()) meta.ownedArmors.add("a_cloth")
        meta.equippedArmorId = sp.getString(rk(K_RUN_EQ_ARM), "a_cloth") ?: "a_cloth"
        meta.ownedRings.clear()
        val rings = sp.getString(rk(K_RUN_RINGS), "") ?: ""
        if (rings.isNotBlank()) meta.ownedRings.addAll(rings.split(",").filter { it.isNotBlank() })
        meta.equippedRingId = sp.getString(rk(K_RUN_EQ_RING), "") ?: ""
        meta.ownedBoots.clear()
        val boots = sp.getString(rk(K_RUN_BOOTS), "") ?: ""
        if (boots.isNotBlank()) meta.ownedBoots.addAll(boots.split(",").filter { it.isNotBlank() })
        if (meta.ownedBoots.isEmpty()) meta.ownedBoots.add("b_cloth")
        meta.equippedBootsId = sp.getString(rk(K_RUN_EQ_BOOTS), "b_cloth") ?: "b_cloth"
        meta.skillPowerBonus = sp.getFloat(rk(K_RUN_SKILL_POW), 0f)
        meta.ownedTomes.clear()
        val tms = sp.getString(rk(K_RUN_TOMES), "") ?: ""
        if (tms.isNotBlank()) meta.ownedTomes.addAll(tms.split(",").filter { it.isNotBlank() })
        meta.bag.clear()
        val bagStr = sp.getString(rk(K_RUN_BAG), "") ?: ""
        if (bagStr.isNotBlank()) {
            bagStr.split(",").forEach { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val id = parts[0]
                    val n = parts[1].toIntOrNull() ?: 0
                    if (id.isNotBlank() && n > 0) meta.bag[id] = n
                }
            }
        }
        meta.toast = "继续冒险"
        meta.toastT = 1.6f
        meta.ensureVitals()
        return true
    }

    fun clearActiveRun() {
        clearActiveRun(sp.edit()).apply()
    }

    private fun clearActiveRunFor(charId: String) {
        val prefix = if (charId.isBlank()) "" else "c_${charId}_"
        sp.edit()
            .putBoolean("${prefix}$K_RUN_ACTIVE", false)
            .remove("${prefix}$K_RUN_HERO")
            .remove("${prefix}$K_RUN_NODE_NAME")
            .apply()
    }

    private fun clearActiveRun(ed: SharedPreferences.Editor): SharedPreferences.Editor {
        return ed.putBoolean(rk(K_RUN_ACTIVE), false)
            .remove(rk(K_RUN_HERO))
            .remove(rk(K_RUN_NODE_NAME))
    }

    companion object {
        private const val PREFS = "jelly_adventure_v1"
        private const val MAX_CHARS = 6
        private const val K_RUNS = "runs"
        private const val K_WINS = "wins"
        private const val K_BEST_STAGE = "best_stage"
        private const val K_BEST_LV = "best_lv"
        private const val K_GOLD = "life_gold"
        private const val K_KILLS = "life_kills"
        private const val K_TUT = "tut"
        private const val K_SND = "snd"
        private const val K_HAP = "hap"
        private const val K_LANGUAGE = "language"

        private const val K_RUN_ACTIVE = "run_active"
        private const val K_RUN_HERO = "run_hero"
        private const val K_RUN_SKIN = "run_skin"
        private const val K_RUN_STAGE = "run_stage"
        private const val K_RUN_NODE = "run_node"
        private const val K_RUN_NODE_NAME = "run_node_name"
        private const val K_RUN_GOLD = "run_gold"
        private const val K_RUN_GOLD_EARNED = "run_gold_e"
        private const val K_RUN_WPN = "run_wpn"
        private const val K_RUN_ARM = "run_arm"
        private const val K_RUN_POT = "run_pot"
        private const val K_RUN_LV = "run_lv"
        private const val K_RUN_XP = "run_xp"
        private const val K_RUN_XP_NEED = "run_xp_need"
        private const val K_RUN_HP = "run_hp"
        private const val K_RUN_MP = "run_mp"
        private const val K_RUN_PASSIVES = "run_pass"
        private const val K_RUN_CORE_INKS = "run_core_inks"
        private const val K_RUN_VISITED = "run_vis"
        private const val K_RUN_KILLS = "run_kills"
        private const val K_RUN_ROOMS = "run_rooms"
        private const val K_RUN_JOURNAL = "run_journal"
        private const val K_RUN_WEAPONS = "run_wpns"
        private const val K_RUN_EQ = "run_eq"
        private const val K_RUN_SKILL_POW = "run_skpow"
        private const val K_RUN_TOMES = "run_tomes"
        private const val K_RUN_BAG = "run_bag"
        private const val K_RUN_ARMORS = "run_armors"
        private const val K_RUN_EQ_ARM = "run_eq_arm"
        private const val K_DISC_WPN = "disc_wpn"
        private const val K_DISC_ARM = "disc_arm"
        private const val K_DISC_RING = "disc_ring"
        private const val K_DISC_BOOTS = "disc_boots"
        private const val K_RUN_RINGS = "run_rings"
        private const val K_RUN_EQ_RING = "run_eq_ring"
        private const val K_RUN_BOOTS = "run_boots"
        private const val K_RUN_EQ_BOOTS = "run_eq_boots"
        private const val K_USER = "local_user"
        private const val K_PASS = "local_pass"
        private const val K_SESSION = "local_session"
        private const val K_GUEST = "local_guest"
        private const val K_CHARS = "chars_v1"
        private const val K_ACTIVE_CHAR = "active_char"
        private const val K_INK_UNLOCK = "ink_unlock"
        private const val K_INK_PREF = "ink_pref"
        private const val K_IMMUNE_MEMORY = "immune_memory"
        private const val K_RUN_SEED = "run_seed"
        private const val K_RUN_INK = "run_ink"
        private const val K_RUN_MEMORY = "run_memory"
    }
}
