package com.jellystorage.play

/**
 * 本地种子码：分享"同一张远征地图"的最小实现（无服务器）。
 * 格式：<seed 的 36 进制>_<墨阶>，例如 "1lz9f3_2"。
 * 36 进制含负号前缀（System.nanoTime 可能为负），'_' 不会出现在任一部分中。
 */
object SeedCode {
    sealed interface LaunchValidation {
        data class Valid(val seed: Long, val inkRank: Int) : LaunchValidation
        data object InvalidFormat : LaunchValidation
        data class LockedRank(val required: Int, val unlocked: Int) : LaunchValidation
    }

    fun encode(seed: Long, inkRank: Int): String =
        java.lang.Long.toString(seed, 36) + "_" + inkRank

    /** 返回 null 表示格式非法。不校验墨阶上限（由调用方按进度校验）。 */
    fun decode(code: String): Pair<Long, Int>? {
        val parts = code.trim().split("_")
        if (parts.size != 2) return null
        val seed = parts[0].toLongOrNull(36) ?: return null
        val rank = parts[1].toIntOrNull() ?: return null
        if (rank < 0 || rank > 99) return null
        return seed to rank
    }

    /**
     * 开局前一次性完成格式和解锁进度校验。
     * 调用方必须在清除现有远征之前取得 [LaunchValidation.Valid]。
     */
    fun validateForLaunch(code: String, unlockedRank: Int): LaunchValidation {
        val (seed, rank) = decode(code) ?: return LaunchValidation.InvalidFormat
        val safeUnlocked = unlockedRank.coerceAtLeast(0)
        return if (rank > safeUnlocked) {
            LaunchValidation.LockedRank(required = rank, unlocked = safeUnlocked)
        } else {
            LaunchValidation.Valid(seed = seed, inkRank = rank)
        }
    }

    /** 当前存档的种子码（无存档返回 null） */
    fun currentRunCode(meta: RunMeta): String? =
        if (meta.roomsCleared > 0 || meta.stageIndex > 0 || meta.kills > 0) encode(meta.runSeed, meta.inkRank)
        else null
}
