package com.jellystorage.play

enum class EnemyTacticalRole(
    val title: String,
    val badge: String,
    val color: Long,
    val hint: String
) {
    NONE("", "", 0L, ""),
    HEALER("续火幽医", "疗", 0xFF4ADE80, "优先击破：周期治疗受伤同伴"),
    DRUMMER("催阵鼓手", "鼓", 0xFFFB923C, "优先击破：周期加速附近敌人"),
    GUARD("甲壳护卫", "卫", 0xFFFBBF24, "拉开阵型：替附近同伴减伤")
}

/** 精英怪的局内变体；同一种怪在不同周目会形成不同处理优先级。 */
enum class EnemyEliteTrait(val title: String, val badge: String, val color: Long) {
    NONE("", "", 0L),
    SWIFT("疾影", "疾", 0xFF38BDF8),
    BULWARK("铁壁", "甲", 0xFFFBBF24),
    FRENZIED("狂怒", "怒", 0xFFEF4444)
}

/**
 * 普通怪招牌攻击。AI 决定走位，招牌攻击决定玩家需要观察和躲避的节奏。
 * Boss 使用 BossMechanics 的独立招式表，不在这里重复。
 */
enum class EnemySignatureAttack(
    val title: String,
    val glyph: String,
    val color: Long,
    val windup: Float,
    val cooldown: Float
) {
    NONE("", "", 0L, 0f, 99f),
    SLIME_POUNCE("弹跳扑击", "跃", 0xFF4ADE80, 0.42f, 3.2f),
    PINK_BURST("桃心散弹", "散", 0xFFF472B6, 0.55f, 4.0f),
    SPIKE_VOLLEY("棘刺齐射", "刺", 0xFFCBD5E1, 0.68f, 4.4f),
    BAT_SONIC("回声扇波", "声", 0xFFA78BFA, 0.48f, 3.6f),
    SKELETON_CLEAVE("骨刃横扫", "斩", 0xFFF5EBD4, 0.62f, 3.8f),
    GOBLIN_BOMB("火罐投掷", "火", 0xFFFB923C, 0.72f, 5.0f),
    RAT_DASH("掘地突袭", "突", 0xFFA8A29E, 0.36f, 3.0f),
    WISP_RING("幽火轮射", "轮", 0xFF7C3AED, 0.74f, 5.2f)
}

fun EnemyKind.signatureAttack(): EnemySignatureAttack = when (this) {
    EnemyKind.SLIME -> EnemySignatureAttack.SLIME_POUNCE
    EnemyKind.PINK_SLIME -> EnemySignatureAttack.PINK_BURST
    EnemyKind.SPIKE_SLIME -> EnemySignatureAttack.SPIKE_VOLLEY
    EnemyKind.BAT -> EnemySignatureAttack.BAT_SONIC
    EnemyKind.SKELETON -> EnemySignatureAttack.SKELETON_CLEAVE
    EnemyKind.GOBLIN -> EnemySignatureAttack.GOBLIN_BOMB
    EnemyKind.RAT -> EnemySignatureAttack.RAT_DASH
    EnemyKind.WISP -> EnemySignatureAttack.WISP_RING
    EnemyKind.BEETLE, EnemyKind.BOSS_SLIME, EnemyKind.BOSS_ORE -> EnemySignatureAttack.NONE
}

fun EnemyKind.tacticalRole(): EnemyTacticalRole = when (this) {
    EnemyKind.WISP -> EnemyTacticalRole.HEALER
    EnemyKind.GOBLIN -> EnemyTacticalRole.DRUMMER
    EnemyKind.BEETLE -> EnemyTacticalRole.GUARD
    else -> EnemyTacticalRole.NONE
}

internal fun guardDamageMultiplier(elite: Boolean): Float = if (elite) 0.76f else 0.82f
