package com.jellystorage.play

import kotlin.random.Random

/**
 * 每局墨阶词缀：同种子固定，重开多变。
 * 高墨阶多抽 1~3 条，专为「再玩一把」的新鲜感。
 */
enum class InkAffix(
    val id: String,
    val title: String,
    val desc: String,
    val minRank: Int
) {
    THICK_GOLD("gold", "厚金", "金币+28%", 0),
    SHARP_HAIR("atk", "锋毫", "造成伤害+14%", 0),
    IRON_BONE("hp", "铁骨", "最大生命+16%", 0),
    QUICK_INK("spd", "疾墨", "移速+12%", 1),
    BRUSH_FLOW("combo", "笔势", "连击更久·连伤更高", 1),
    DENSE_FOE("dense", "稠敌", "敌更肉·掉落更丰", 1),
    SPIRIT_WELL("spirit", "灵泉", "经验+22%·回蓝更快", 2),
    WOLF_PACK("wolf", "恶狼", "精英更频更狠", 2),
    SOFT_STEP("soft", "轻尘", "受伤-10%", 3),
    LUCKY_SEAL("luck", "福印", "清场评价奖励+50%", 3),
    STORM_INK("storm", "墨暴", "狂热更易触发", 4),
    BOSS_HUNTER("hunter", "猎魁", "对精英/Boss+18%伤", 4),
    // 风险词缀：强收益 + 明确代价（高墨阶更常出现）
    BLOOD_INK("blood", "血墨", "伤害+20% · 受伤+12%", 2),
    GREED_BRUSH("greed", "贪毫", "金币+40% · 生命-10%", 3),
    THIN_PAPER("thin", "薄纸", "移速+18% · 生命-12%", 2),
    CURSED_SEAL("curse", "咒印", "经验+30% · 敌伤+15%", 4);

    companion object {
        fun byId(id: String): InkAffix? = entries.find { it.id == id }
    }
}

/** 传入战斗模拟的合并修正 */
data class CombatMods(
    val goldMul: Float = 1f,
    val xpMul: Float = 1f,
    val enemyHpMul: Float = 1f,
    val enemyAtkMul: Float = 1f,
    val enemySpdMul: Float = 1f,
    val playerAtkMul: Float = 1f,
    val playerSpdMul: Float = 1f,
    val playerHpMul: Float = 1f,
    val dmgTakenMul: Float = 1f,
    val dropBonus: Float = 0f,
    val eliteChanceBonus: Float = 0f,
    val comboWindowMul: Float = 1f,
    val comboDmgPerStack: Float = 0.05f,
    val mpRegenMul: Float = 1f,
    val clearRewardMul: Float = 1f,
    val frenzyKillNeed: Int = 6,
    val eliteBonusDmg: Float = 0f
) {
    companion object {
        val NONE = CombatMods()
    }
}

/** 本局抽取词缀（确定性） */
fun rollInkAffixes(seed: Long, inkRank: Int): List<InkAffix> {
    val rank = inkRank.coerceIn(0, 7)
    if (rank <= 0) {
        // 墨0：仍给 1 条轻量正面，新手也能感到「局有不同」
        val rng = Random(seed xor 0xA11CE)
        return listOf(
            listOf(InkAffix.THICK_GOLD, InkAffix.SHARP_HAIR, InkAffix.IRON_BONE).random(rng)
        )
    }
    val rng = Random(seed xor (rank * 9973L))
    val count = when {
        rank >= 6 -> 3
        rank >= 3 -> 2
        else -> 1
    }.coerceAtMost(3)
    val pool = InkAffix.entries.filter { rank >= it.minRank }.shuffled(rng)
    return pool.take(count)
}

fun combatModsFrom(affixes: List<InkAffix>): CombatMods {
    var gold = 1f
    var xp = 1f
    var eHp = 1f
    var eAtk = 1f
    var eSpd = 1f
    var pAtk = 1f
    var pSpd = 1f
    var pHp = 1f
    var taken = 1f
    var drop = 0f
    var elite = 0f
    var comboWin = 1f
    var comboStack = 0.05f
    var mpR = 1f
    var clear = 1f
    var frenzyNeed = 6
    var eliteDmg = 0f
    for (a in affixes) {
        when (a) {
            InkAffix.THICK_GOLD -> gold *= 1.28f
            InkAffix.SHARP_HAIR -> pAtk *= 1.14f
            InkAffix.IRON_BONE -> pHp *= 1.16f
            InkAffix.QUICK_INK -> pSpd *= 1.12f
            InkAffix.BRUSH_FLOW -> {
                comboWin *= 1.45f
                comboStack = 0.075f
            }
            InkAffix.DENSE_FOE -> {
                eHp *= 1.22f
                drop += 0.22f
            }
            InkAffix.SPIRIT_WELL -> {
                xp *= 1.22f
                mpR *= 1.35f
            }
            InkAffix.WOLF_PACK -> {
                elite += 0.18f
                eAtk *= 1.12f
                eHp *= 1.08f
            }
            InkAffix.SOFT_STEP -> taken *= 0.90f
            InkAffix.LUCKY_SEAL -> {
                clear *= 1.5f
                drop += 0.12f
            }
            InkAffix.STORM_INK -> frenzyNeed = 4
            InkAffix.BOSS_HUNTER -> eliteDmg = 0.18f
            InkAffix.BLOOD_INK -> {
                pAtk *= 1.20f
                taken *= 1.12f
            }
            InkAffix.GREED_BRUSH -> {
                gold *= 1.40f
                pHp *= 0.90f
            }
            InkAffix.THIN_PAPER -> {
                pSpd *= 1.18f
                pHp *= 0.88f
            }
            InkAffix.CURSED_SEAL -> {
                xp *= 1.30f
                eAtk *= 1.15f
            }
        }
    }
    return CombatMods(
        goldMul = gold,
        xpMul = xp,
        enemyHpMul = eHp,
        enemyAtkMul = eAtk,
        enemySpdMul = eSpd,
        playerAtkMul = pAtk,
        playerSpdMul = pSpd,
        playerHpMul = pHp,
        dmgTakenMul = taken,
        dropBonus = drop,
        eliteChanceBonus = elite,
        comboWindowMul = comboWin,
        comboDmgPerStack = comboStack,
        mpRegenMul = mpR,
        clearRewardMul = clear,
        frenzyKillNeed = frenzyNeed,
        eliteBonusDmg = eliteDmg
    )
}

fun affixLine(affixes: List<InkAffix>): String =
    if (affixes.isEmpty()) "本局无词缀" else affixes.joinToString(" · ") { "「${it.title}」${it.desc}" }

fun affixShort(affixes: List<InkAffix>): String =
    if (affixes.isEmpty()) "" else affixes.joinToString(" ") { it.title }

/** 连斩称号 */
fun comboTitle(n: Int): String = when {
    n >= 20 -> "墨杀·$n"
    n >= 15 -> "狂笔·$n"
    n >= 10 -> "十连破墨!"
    n >= 7 -> "七连·锋!"
    n >= 5 -> "五连斩!"
    n >= 3 -> "三连!"
    n >= 2 -> "${n}连"
    else -> ""
}

/** 今日画题种子：全天相同，方便互相攀比 */
fun dailyInkSeed(): Long {
    val cal = java.util.Calendar.getInstance()
    val y = cal.get(java.util.Calendar.YEAR)
    val m = cal.get(java.util.Calendar.MONTH) + 1
    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return y * 10000L + m * 100L + d + 0xDA114EEDL
}

fun dailyInkTitle(): String {
    val seed = dailyInkSeed()
    val aff = rollInkAffixes(seed, 2)
    return "今日画题 · ${affixShort(aff)}"
}
