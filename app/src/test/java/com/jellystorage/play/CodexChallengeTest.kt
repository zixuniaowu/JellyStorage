package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexChallengeTest {

    private fun outcome(
        combo: Int = 0,
        sec: Float = 60f,
        dmg: Float = 50f,
        grade: String = "B",
        boss: Boolean = false,
        potions: Int = 0
    ) = BattleOutcome(combo, sec, dmg, grade, boss, potions)

    @Test
    fun `各挑战按结算数据独立判定`() {
        val r = battleChallengesReached(outcome(combo = 30), emptySet())
        assertEquals(listOf(CodexChallenge.COMBO_30), r)

        assertEquals(listOf(CodexChallenge.SPEED_48), battleChallengesReached(outcome(sec = 48f), emptySet()))
        assertEquals(emptyList(), battleChallengesReached(outcome(sec = 48.01f), emptySet()))

        assertEquals(listOf(CodexChallenge.FLAWLESS), battleChallengesReached(outcome(dmg = 0f), emptySet()))
        assertEquals(listOf(CodexChallenge.GRADE_S), battleChallengesReached(outcome(grade = "S"), emptySet()))
    }

    @Test
    fun `无药挑战要求Boss战且本局零用药`() {
        assertEquals(
            listOf(CodexChallenge.NO_POTION_BOSS),
            battleChallengesReached(outcome(boss = true, potions = 0), emptySet())
        )
        // 非 Boss 战不给
        assertTrue(battleChallengesReached(outcome(boss = false, potions = 0), emptySet()).isEmpty())
        // 用过药不给（哪怕又买回来）
        assertTrue(battleChallengesReached(outcome(boss = true, potions = 1), emptySet()).isEmpty())
    }

    @Test
    fun `已完成挑战不重复返回`() {
        val done = setOf(CodexChallenge.COMBO_30.id, CodexChallenge.GRADE_S.id)
        val r = battleChallengesReached(outcome(combo = 45, grade = "S", sec = 30f), done)
        assertEquals(listOf(CodexChallenge.SPEED_48), r)
    }

    @Test
    fun `五行进度行格式稳定`() {
        assertEquals("金水 2/5", bossElementProgressLine(setOf(WuXing.METAL, WuXing.WATER)))
        assertEquals(" 0/5", bossElementProgressLine(emptySet()))
        assertEquals(5, WuXing.entries.size)
    }

    @Test
    fun `奖励金额为正且互不相同档位`() {
        val rewards = CodexChallenge.entries.map { it.rewardGold }
        assertTrue(rewards.all { it > 0 })
        assertEquals(rewards.size, rewards.toSet().size)
        assertEquals(6, CodexChallenge.entries.size)
    }
}
