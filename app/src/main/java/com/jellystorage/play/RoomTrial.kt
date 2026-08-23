package com.jellystorage.play

/**
 * A visible combat-room rule. Trials change the feel of a room and reward the
 * player for adapting instead of merely clearing another stat-scaled wave.
 */
enum class RoomTrial(
    val title: String,
    val rule: String,
    val accent: Long,
    val enemyHpMul: Float = 1f,
    val enemyAtkMul: Float = 1f,
    val enemySpeedMul: Float = 1f,
    val mpRegenMul: Float = 1f,
    val comboWindowMul: Float = 1f,
    val rewardGold: Int,
    val rewardXp: Int
) {
    RUSH(
        "疾书阵", "敌人更快·限时清场", 0xFFF59E0B,
        enemyHpMul = 0.94f, enemySpeedMul = 1.12f,
        rewardGold = 14, rewardXp = 10
    ),
    COMBO(
        "连墨阵", "连击窗延长·冲击连段", 0xFFFB7185,
        enemyHpMul = 1.06f, comboWindowMul = 1.5f,
        rewardGold = 12, rewardXp = 14
    ),
    ARCANE(
        "灵涌阵", "回蓝加快·多放技能", 0xFF818CF8,
        enemyAtkMul = 1.08f, mpRegenMul = 1.7f,
        rewardGold = 12, rewardXp = 16
    ),
    GUARD(
        "守砚阵", "敌攻更高·控制受伤", 0xFF34D399,
        enemyAtkMul = 1.15f,
        rewardGold = 16, rewardXp = 12
    );

    fun objective(waveTotal: Int): String = when (this) {
        RUSH -> "${rushLimit(waveTotal).toInt()}秒内清场"
        COMBO -> "最高${comboTarget(waveTotal)}连击"
        ARCANE -> "释放${skillTarget(waveTotal)}次技能"
        GUARD -> "失血不超过28%"
    }

    fun progress(
        waveTotal: Int,
        fightTime: Float,
        maxCombo: Int,
        skillCasts: Int,
        damageTaken: Float,
        maxHp: Float
    ): String = when (this) {
        RUSH -> "${fightTime.toInt()}/${rushLimit(waveTotal).toInt()}秒"
        COMBO -> "$maxCombo/${comboTarget(waveTotal)}连"
        ARCANE -> "$skillCasts/${skillTarget(waveTotal)}次"
        GUARD -> "失血${((damageTaken / maxHp.coerceAtLeast(1f)) * 100f).toInt()}%/28%"
    }

    fun succeeded(
        waveTotal: Int,
        fightTime: Float,
        maxCombo: Int,
        skillCasts: Int,
        damageTaken: Float,
        maxHp: Float
    ): Boolean = when (this) {
        RUSH -> fightTime <= rushLimit(waveTotal)
        COMBO -> maxCombo >= comboTarget(waveTotal)
        ARCANE -> skillCasts >= skillTarget(waveTotal)
        GUARD -> damageTaken <= maxHp * 0.28f
    }

    private fun rushLimit(waveTotal: Int): Float = 24f + waveTotal.coerceAtLeast(1) * 12f
    private fun comboTarget(waveTotal: Int): Int = (8 + waveTotal.coerceAtLeast(1) * 2).coerceAtMost(18)
    private fun skillTarget(waveTotal: Int): Int = (waveTotal.coerceAtLeast(1) * 2).coerceAtLeast(4)
}

/** Stable for a run/node so save-and-resume and map preview always agree. */
fun roomTrialFor(runSeed: Long, stageIndex: Int, node: MapNode): RoomTrial? {
    if (node.type != NodeType.MOB && node.type != NodeType.ELITE && node.type != NodeType.BOSS) return null
    val mixed = runSeed xor
        ((stageIndex + 1L) * -7046029254386353131L) xor
        ((node.id + 17L) * -4658895280553007687L)
    val pool = RoomTrial.entries
    val index = ((mixed xor (mixed ushr 32)).toInt() and Int.MAX_VALUE) % pool.size
    return pool[index]
}
