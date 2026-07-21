package com.jellystorage.play

/**
 * 本机角色槽：账号下可创建多个角色，登录后选择。
 */
data class GameCharacter(
    val id: String,
    val name: String,
    val hero: HeroClass,
    val createdAt: Long = System.currentTimeMillis(),
    val bestStage: Int = 0,
    val runs: Int = 0
) {
    fun summaryLine(): String =
        "${hero.displayName} · 最高第${bestStage.coerceAtLeast(0)}章 · 出征${runs}次"

    fun encode(): String =
        listOf(id, name.replace("|", "｜").replace("\n", ""), hero.name, createdAt.toString(), bestStage.toString(), runs.toString())
            .joinToString("|")

    companion object {
        fun decode(line: String): GameCharacter? {
            val p = line.split("|")
            if (p.size < 6) return null
            val hero = try {
                HeroClass.valueOf(p[2])
            } catch (_: Exception) {
                return null
            }
            return GameCharacter(
                id = p[0],
                name = p[1].ifBlank { "无名" },
                hero = hero,
                createdAt = p[3].toLongOrNull() ?: 0L,
                bestStage = p[4].toIntOrNull() ?: 0,
                runs = p[5].toIntOrNull() ?: 0
            )
        }

        fun autoName(index: Int): String {
            val adj = listOf("果冻", "星火", "青木", "寒霜", "雷纹", "玉清", "裂地", "流星")
            val noun = listOf("勇者", "行者", "小仙", "剑客", "法灵", "道童", "战魂")
            return adj[index % adj.size] + noun[(index * 3) % noun.size] + (index + 1)
        }
    }
}

object GuestIds {
    private val adj = listOf("果冻", "星火", "青木", "寒霜", "雷纹", "玉清", "裂地", "流星", "银辉", "赤焰")
    private val noun = listOf("旅人", "行者", "小仙", "剑客", "法灵", "道童", "战魂", "勇者")

    fun username(): String =
        adj.random() + noun.random() + (100..999).random()

    fun password(): String =
        buildString {
            val chars = "abcdefghjkmnpqrstuvwxyz23456789"
            repeat(6) { append(chars.random()) }
        }
}
