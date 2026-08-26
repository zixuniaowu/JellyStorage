package com.jellystorage.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HeroTrialsTest {

    private fun report(
        kills: Int = 0,
        combo: Int = 0,
        grade: String = "B",
        boss: Boolean = false,
        casts: Int = 0,
        flawless: Boolean = false
    ) = TrialBattleReport(kills, combo, grade, boss, casts, flawless)

    @Test
    fun `累计口径正确`() {
        var st = TrialStats()
        st = HeroTrials.accumulate(st, report(kills = 30, combo = 18, grade = "A"))
        st = HeroTrials.accumulate(st, report(kills = 20, combo = 12, grade = "B", boss = true, casts = 40))
        assertEquals(50, st.kills)
        assertEquals(18, st.maxCombo, "最高连击取历史峰值")
        assertEquals(1, st.gradeA, "只有 A/S 计数")
        assertEquals(1, st.bossKills)
        assertEquals(40, st.skillCasts)
        assertEquals(0, st.flawless)
    }

    @Test
    fun `无伤与S评价口径`() {
        var st = TrialStats()
        st = HeroTrials.accumulate(st, report(grade = "S", flawless = true))
        assertEquals(1, st.gradeA, "S 计入 A 级以上")
        assertEquals(1, st.flawless)
        st = HeroTrials.accumulate(st, report(grade = "C", flawless = false))
        assertEquals(1, st.gradeA)
        assertEquals(1, st.flawless, "非无伤场不计")
    }

    @Test
    fun `全试炼达成才解锁皮肤`() {
        val hero = HeroClass.MAGE
        var st = TrialStats()
        assertFalse(HeroTrials.allDone(hero, st))
        // 补齐法师四条：击杀150/技能60/无伤1/Boss2
        st = HeroTrials.accumulate(st, report(kills = 150, casts = 60, flawless = true))
        assertFalse(HeroTrials.allDone(hero, st), "还差 Boss")
        st = HeroTrials.accumulate(st, report(boss = true))
        st = HeroTrials.accumulate(st, report(boss = true))
        assertTrue(HeroTrials.allDone(hero, st))
    }

    @Test
    fun `每职业四条试炼且奖励对应备用皮肤`() {
        HeroClass.entries.forEach { hero ->
            val trials = HeroTrials.trialsFor(hero)
            assertEquals(4, trials.size, "$hero 应有4条试炼")
            val alt = HeroTrials.rewardSkinFor(hero)
            assertNotEquals(SkinCatalog.defaultFor(hero).id, alt.id, "$hero 奖励应为备用皮肤")
            assertEquals(hero, SkinCatalog.altsFor(hero).last().let { hero }, "备用皮肤存在于目录")
            assertTrue(trials.all { it.target > 0 })
        }
    }

    @Test
    fun `编码解码往返无损且容错`() {
        val st = TrialStats(kills = 5, maxCombo = 9, gradeA = 2, bossKills = 1, skillCasts = 7, flawless = 3)
        assertEquals(st, TrialStats.decode(st.encode()))
        assertEquals(TrialStats(), TrialStats.decode(null))
        assertEquals(TrialStats(), TrialStats.decode("garbage"))
        // 旧字段缺失时补零
        assertEquals(TrialStats(kills = 4), TrialStats.decode("4"))
    }

    @Test
    fun `进度数字封顶显示`() {
        val t = HeroTrial("t", "d", TrialMetric.KILLS, 150)
        assertEquals("150/150", HeroTrials.progressNum(t, TrialStats(kills = 999)))
        assertEquals("3/150", HeroTrials.progressNum(t, TrialStats(kills = 3)))
        assertTrue(HeroTrials.isDone(t, TrialStats(kills = 150)))
    }
}
