package com.jellystorage.play

import kotlin.random.Random

/** 波次修饰符：每波敌阵随机主题（关卡反单调） */
enum class WaveMod(
    val title: String,
    val desc: String,
    val color: Long
) {
    NONE("", "", 0L),
    SWIFT("疾风", "本波敌全体移速提升", 0xFF38BDF8),
    FRENZIED("狂暴", "本波敌全体攻击提升", 0xFFEF4444),
    TOUGH("坚壳", "本波敌全体生命提升", 0xFFFBBF24),
    SWARM("蜂拥", "本波增援一只小怪", 0xFFA3E635),
    FISSION("裂变", "本波敌死亡时分裂", 0xFFA78BFA),
    BLOODTHIRST("血怒", "本波敌攻击吸血", 0xFFDC2626);

    companion object {
        private val pool = listOf(SWIFT, FRENZIED, TOUGH, SWARM, FISSION, BLOODTHIRST)

        /** 前两波保持干净（新手第一场平稳过渡）；第三波起 70% 概率出 */
        fun roll(idx: Int, rng: Random): WaveMod =
            if (idx <= 1 || rng.nextFloat() < 0.30f) NONE else pool[rng.nextInt(pool.size)]
    }
}
