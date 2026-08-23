package com.jellystorage.play

/** 战斗房的空间规则；同章不同节点会落入不同构型，而不是只换背景色。 */
enum class ArenaHazardPattern { NONE, CIRCLE, RING, LINE }

data class ArenaEnvironment(
    val id: String,
    val title: String,
    val rule: String,
    val chapter: Int,
    val variant: Int,
    val visualSeed: Int,
    val color: Long,
    val pattern: ArenaHazardPattern,
    val interval: Float,
    val damageRatio: Float,
    val slowOnHit: Boolean = false
) {
    val active: Boolean get() = pattern != ArenaHazardPattern.NONE

    companion object {
        val NONE = ArenaEnvironment(
            "none", "", "", 0, 0, 0,
            0L, ArenaHazardPattern.NONE, 99f, 0f
        )
    }
}

private data class ArenaEnvironmentTemplate(
    val id: String,
    val title: String,
    val rule: String,
    val color: Long,
    val pattern: ArenaHazardPattern,
    val interval: Float,
    val damageRatio: Float,
    val slowOnHit: Boolean = false
)

private val environmentChapters = listOf(
    listOf(
        ArenaEnvironmentTemplate("petal", "凝血边缘", "血小板环流·注意环形落点", 0xFF86EFAC, ArenaHazardPattern.RING, 10.5f, 0.045f),
        ArenaEnvironmentTemplate("mud", "组织液沟", "组织液横扫·命中减速", 0xFF78716C, ArenaHazardPattern.LINE, 9.2f, 0.04f, true),
        ArenaEnvironmentTemplate("root", "神经末梢", "痛觉信号会锁定脚下", 0xFF4D7C0F, ArenaHazardPattern.CIRCLE, 8.8f, 0.05f, true)
    ),
    listOf(
        ArenaEnvironmentTemplate("rockfall", "肺泡气室", "飞沫锁定片刻前的位置", 0xFFF59E0B, ArenaHazardPattern.CIRCLE, 8.2f, 0.065f),
        ArenaEnvironmentTemplate("minecart", "纤毛气道", "气流沿气道横穿战场", 0xFFD97706, ArenaHazardPattern.LINE, 7.8f, 0.07f),
        ArenaEnvironmentTemplate("crystal", "缺氧区", "缺氧波由中心向外扩散", 0xFF38BDF8, ArenaHazardPattern.RING, 9.0f, 0.06f, true)
    ),
    listOf(
        ArenaEnvironmentTemplate("syrup", "胃酸池", "酸液坠落并灼伤脚步", 0xFFE11D48, ArenaHazardPattern.CIRCLE, 7.7f, 0.065f, true),
        ArenaEnvironmentTemplate("arrow", "绒毛通道", "消化酶沿通道直线冲刷", 0xFFFBBF24, ArenaHazardPattern.LINE, 7.3f, 0.075f),
        ArenaEnvironmentTemplate("bell", "菌群环", "菌群信号形成扩散震环", 0xFFC084FC, ArenaHazardPattern.RING, 8.6f, 0.065f)
    ),
    listOf(
        ArenaEnvironmentTemplate("ink_tide", "胆汁管", "胆汁分道横穿净化区", 0xFF475569, ArenaHazardPattern.LINE, 7.0f, 0.07f, true),
        ArenaEnvironmentTemplate("sea_eye", "解毒小叶", "避开解毒反应的外沿冲击", 0xFF0284C7, ArenaHazardPattern.RING, 8.0f, 0.075f),
        ArenaEnvironmentTemplate("paper_rain", "代谢环", "毒素锁定落点后沉积", 0xFFA8A29E, ArenaHazardPattern.CIRCLE, 7.4f, 0.07f)
    ),
    listOf(
        ArenaEnvironmentTemplate("pine_wind", "心室激流", "高速血流成线扫过心室", 0xFF16A34A, ArenaHazardPattern.LINE, 7.2f, 0.065f, true),
        ArenaEnvironmentTemplate("thunder", "瓣膜通道", "瓣膜压力锁定脚下后爆发", 0xFFFACC15, ArenaHazardPattern.CIRCLE, 7.0f, 0.08f),
        ArenaEnvironmentTemplate("cloud_ring", "冠脉环", "脉搏形成内外压力环", 0xFF7DD3FC, ArenaHazardPattern.RING, 8.2f, 0.07f, true)
    )
)

fun arenaEnvironmentFor(chapterIndex: Int, nodeId: Int, nodeType: NodeType): ArenaEnvironment {
    // Boss 已有独立多阶段场地技，避免和环境技叠加造成不可读。
    if (nodeType == NodeType.BOSS) return ArenaEnvironment.NONE
    if (nodeType != NodeType.MOB && nodeType != NodeType.ELITE) return ArenaEnvironment.NONE
    val chapter = chapterIndex.coerceAtLeast(0) % environmentChapters.size
    val variant = ((nodeId * 7 + chapter * 3) and Int.MAX_VALUE) % 3
    val template = environmentChapters[chapter][variant]
    return ArenaEnvironment(
        id = template.id,
        title = template.title,
        rule = template.rule,
        chapter = chapter,
        variant = variant,
        visualSeed = chapter * 1009 + nodeId * 97 + variant * 31,
        color = template.color,
        pattern = template.pattern,
        interval = template.interval,
        damageRatio = template.damageRatio,
        slowOnHit = template.slowOnHit
    )
}
