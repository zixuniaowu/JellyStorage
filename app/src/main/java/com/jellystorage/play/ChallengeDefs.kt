package com.jellystorage.play

/**
 * 图鉴挑战（生涯目标）：达成即永久记录，奖励立即进入当局金币。
 * 判定数据全部来自单场战斗结算与生涯记录，不依赖 Android 框架，可单测。
 */
enum class CodexChallenge(
    val id: String,
    val title: String,
    val desc: String,
    val rewardGold: Int
) {
    COMBO_30("combo_30", "疾风连击", "单场战斗中达成 30 连击", 60),
    SPEED_48("speed_48", "神速清场", "单场战斗 48 秒内清场", 80),
    FLAWLESS("flawless", "完美无伤", "单场战斗胜利且未受任何伤害", 100),
    GRADE_S("grade_s", "登峰造极", "单场战斗获得 S 评价", 120),
    NO_POTION_BOSS("no_potion_boss", "点药不沾", "本次远征未用药水击败章节 Boss", 150);

    companion object {
        fun byId(id: String): CodexChallenge? = entries.firstOrNull { it.id == id }
    }
}

/** 单场战斗结算快照，供挑战判定 */
data class BattleOutcome(
    val maxCombo: Int,
    val fightSeconds: Float,
    val damageTaken: Float,
    val grade: String,
    val bossBattle: Boolean,
    val potionsUsed: Int
)

/** 返回本场战斗满足条件且尚未完成的挑战（不落库、不发奖，由调用方决定） */
fun battleChallengesReached(outcome: BattleOutcome, alreadyDone: Set<String>): List<CodexChallenge> {
    val reached = mutableListOf<CodexChallenge>()
    fun add(c: CodexChallenge, cond: Boolean) {
        if (cond && c.id !in alreadyDone) reached.add(c)
    }
    add(CodexChallenge.COMBO_30, outcome.maxCombo >= 30)
    add(CodexChallenge.SPEED_48, outcome.fightSeconds in 0.01f..48f)
    add(CodexChallenge.FLAWLESS, outcome.damageTaken <= 0f)
    add(CodexChallenge.GRADE_S, outcome.grade == "S")
    add(CodexChallenge.NO_POTION_BOSS, outcome.bossBattle && outcome.potionsUsed <= 0)
    return reached
}
