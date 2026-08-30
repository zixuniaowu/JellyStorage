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
    SWARM("蜂拥", "本波增援两只小怪", 0xFFA3E635);

    companion object {
        private val pool = listOf(SWIFT, FRENZIED, TOUGH, SWARM)

        /** 首波必无修饰符（新手第一场保持干净）；之后 70% 概率出 */
        fun roll(idx: Int, rng: Random): WaveMod =
            if (idx <= 0 || rng.nextFloat() < 0.30f) NONE else pool[rng.nextInt(pool.size)]
    }
}
