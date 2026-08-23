package com.jellystorage.play

import kotlin.math.min

const val MAX_MUTATION_GENERATION = 30

/** 前七代成长明显，之后改为软增长，避免线性膨胀让高周目失去可读性。 */
fun mutationHpScale(generation: Int): Float {
    val g = generation.coerceIn(0, MAX_MUTATION_GENERATION)
    return 1f + min(g, 7) * 0.20f + (g - 7).coerceAtLeast(0) * 0.10f
}

fun mutationAtkScale(generation: Int): Float {
    val g = generation.coerceIn(0, MAX_MUTATION_GENERATION)
    return 1f + min(g, 7) * 0.16f + (g - 7).coerceAtLeast(0) * 0.08f
}

fun mutationTrapScale(generation: Int): Float {
    val g = generation.coerceIn(0, MAX_MUTATION_GENERATION)
    return 1f + min(g, 7) * 0.12f + (g - 7).coerceAtLeast(0) * 0.06f
}

/**
 * 通关后保留的局外成长。每次感染周期只携带一种记忆，避免永久成长压过局内构筑。
 * unlockGeneration 沿用旧墨阶存档值，老玩家无需清档即可迁移。
 */
enum class ImmuneMemory(
    val id: String,
    val title: String,
    val desc: String,
    val unlockGeneration: Int,
    val hpMul: Float = 1f,
    val atkBonus: Float = 0f,
    val skillAmp: Float = 0f,
    val lifeSteal: Float = 0f,
    val crit: Float = 0f,
    val cdr: Float = 0f,
    val startGold: Int = 0
) {
    CLOTTING_BARRIER("clot", "凝血屏障", "最大生命+10%", 0, hpMul = 1.10f),
    RAPID_RESPONSE("rapid", "快速应答", "基础攻击+5", 1, atkBonus = 5f),
    ENERGY_RESERVE("energy", "代谢储备", "开局营养+18", 2, startGold = 18),
    ANTIBODY_ARCHIVE("antibody", "抗体档案", "技能威力+10%", 3, skillAmp = 0.10f),
    PHAGOCYTE_INSTINCT("phagocyte", "吞噬本能", "生命汲取+3%", 4, lifeSteal = 0.03f),
    ADAPTIVE_RECEPTOR("receptor", "适应受体", "暴击+5%·冷却-5%", 5, crit = 0.05f, cdr = 0.05f);

    companion object {
        fun byId(id: String?): ImmuneMemory? = entries.find { it.id == id }
        fun unlocked(generation: Int): List<ImmuneMemory> =
            entries.filter { generation.coerceAtLeast(0) >= it.unlockGeneration }
    }
}

fun mutationGenerationLabel(generation: Int): String =
    if (generation <= 0) "原始毒株" else "变异第${generation}代"
