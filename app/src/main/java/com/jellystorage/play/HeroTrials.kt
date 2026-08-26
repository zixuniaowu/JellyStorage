package com.jellystorage.play

/** 试炼统计口径：跨局按职业累计 */
enum class TrialMetric { KILLS, MAX_COMBO, GRADE_A, BOSS_KILLS, SKILL_CASTS, FLAWLESS }

data class HeroTrial(
    val title: String,
    val desc: String,
    val metric: TrialMetric,
    val target: Int
)

/** 每职业一份累计值；k=击杀 c=最高连击 a=A级场数 b=Boss击杀 s=技能释放 f=无伤场数 */
data class TrialStats(
    val kills: Int = 0,
    val maxCombo: Int = 0,
    val gradeA: Int = 0,
    val bossKills: Int = 0,
    val skillCasts: Int = 0,
    val flawless: Int = 0
) {
    fun valueOf(m: TrialMetric): Int = when (m) {
        TrialMetric.KILLS -> kills
        TrialMetric.MAX_COMBO -> maxCombo
        TrialMetric.GRADE_A -> gradeA
        TrialMetric.BOSS_KILLS -> bossKills
        TrialMetric.SKILL_CASTS -> skillCasts
        TrialMetric.FLAWLESS -> flawless
    }

    fun encode() = listOf(kills, maxCombo, gradeA, bossKills, skillCasts, flawless).joinToString(",")

    companion object {
        fun decode(s: String?): TrialStats {
            val p = (s ?: "").split(",").mapNotNull { it.toIntOrNull() }
            return TrialStats(
                kills = p.getOrElse(0) { 0 },
                maxCombo = p.getOrElse(1) { 0 },
                gradeA = p.getOrElse(2) { 0 },
                bossKills = p.getOrElse(3) { 0 },
                skillCasts = p.getOrElse(4) { 0 },
                flawless = p.getOrElse(5) { 0 }
            )
        }
    }
}

/** 一场战斗结算后可用于试炼累计的数据 */
data class TrialBattleReport(
    val kills: Int,
    val maxCombo: Int,
    val grade: String,
    val bossKilled: Boolean,
    val skillCasts: Int,
    val flawless: Boolean
)

/** 职业试炼线：全部达成解锁该职业备用皮肤（不出售强度，纯成就） */
object HeroTrials {
    fun trialsFor(hero: HeroClass): List<HeroTrial> = when (hero) {
        HeroClass.WARRIOR -> listOf(
            HeroTrial("百战之刃", "累计击杀 150 名敌人", TrialMetric.KILLS, 150),
            HeroTrial("连绵剑势", "单场达成 20 连击", TrialMetric.MAX_COMBO, 20),
            HeroTrial("三度漂亮仗", "累计 3 场 A 评价以上", TrialMetric.GRADE_A, 3),
            HeroTrial("双杀首领", "累计击败 2 名章节 Boss", TrialMetric.BOSS_KILLS, 2)
        )
        HeroClass.MAGE -> listOf(
            HeroTrial("百战之杖", "累计击杀 150 名敌人", TrialMetric.KILLS, 150),
            HeroTrial("术数精深", "累计释放 60 次技能", TrialMetric.SKILL_CASTS, 60),
            HeroTrial("纤尘不染", "完成 1 场无伤战斗", TrialMetric.FLAWLESS, 1),
            HeroTrial("双杀首领", "累计击败 2 名章节 Boss", TrialMetric.BOSS_KILLS, 2)
        )
        HeroClass.TAOIST -> listOf(
            HeroTrial("百战之符", "累计击杀 150 名敌人", TrialMetric.KILLS, 150),
            HeroTrial("连绵符势", "单场达成 20 连击", TrialMetric.MAX_COMBO, 20),
            HeroTrial("纤尘不染", "完成 1 场无伤战斗", TrialMetric.FLAWLESS, 1),
            HeroTrial("三清首功", "累计击败 3 名章节 Boss", TrialMetric.BOSS_KILLS, 3)
        )
    }

    /** 该职业全部试炼是否达成（=皮肤解锁条件） */
    fun allDone(hero: HeroClass, st: TrialStats): Boolean =
        trialsFor(hero).all { st.valueOf(it.metric) >= it.target }

    /** 战斗结算累计：返回新统计。MAX_COMBO/FLAWLESS 等口径由调用方传入已算好的值 */
    fun accumulate(st: TrialStats, r: TrialBattleReport): TrialStats = TrialStats(
        kills = st.kills + r.kills,
        maxCombo = maxOf(st.maxCombo, r.maxCombo),
        gradeA = st.gradeA + if (r.grade == "A" || r.grade == "S") 1 else 0,
        bossKills = st.bossKills + if (r.bossKilled) 1 else 0,
        skillCasts = st.skillCasts + r.skillCasts,
        flawless = st.flawless + if (r.flawless) 1 else 0
    )

    /** 解锁奖励皮肤（对应 SkinCatalog 备用皮） */
    fun rewardSkinFor(hero: HeroClass): CharacterSkin = when (hero) {
        HeroClass.WARRIOR -> SkinCatalog.warriorPink
        HeroClass.MAGE -> SkinCatalog.mageStar
        HeroClass.TAOIST -> SkinCatalog.taoistCloud
    }

    fun isDone(t: HeroTrial, st: TrialStats): Boolean = st.valueOf(t.metric) >= t.target

    /** 纯数字进度（标题由 UI 层单独翻译后拼接） */
    fun progressNum(t: HeroTrial, st: TrialStats): String {
        val v = st.valueOf(t.metric).coerceAtMost(t.target)
        return "$v/${t.target}"
    }
}
