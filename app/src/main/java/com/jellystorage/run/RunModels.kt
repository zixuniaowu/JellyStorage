package com.jellystorage.run

import androidx.compose.ui.graphics.Color
import com.jellystorage.engine.model.Weapon
import com.jellystorage.engine.model.WeaponCatalog

enum class RoomType {
    START,
    MOB,
    ELITE,
    RESOURCE,
    TRAP,
    SHOP,
    BOSS,
    EXIT
}

enum class RunPhase {
    MAP,
    BATTLE,
    SHOP,
    EVENT,   // resource / trap popup
    STAGE_CLEAR,
    RUN_OVER
}

data class RoomDef(
    val id: Int,
    val type: RoomType,
    /** Normalized 0..1 position on 2D map (x along path, y for branches). */
    val nx: Float,
    val ny: Float,
    val label: String,
    val enemyHp: Float = 100f,
    val enemyAtk: Float = 10f,
    val enemyName: String = "小怪",
    val enemyColor: Long = 0xFF4ADE80,
    val goldReward: Int = 0,
    val healReward: Float = 0f,
    val trapDamage: Float = 0f,
    val trapGoldCost: Int = 0, // pay to skip trap
    val nextIds: List<Int> = emptyList()
)

data class StageMap(
    val stageId: Int,
    val title: String,
    val subtitle: String,
    val rooms: List<RoomDef>,
    val bgTop: Long = 0xFF0F172A,
    val bgBot: Long = 0xFF1E293B
)

data class ShopOffer(
    val weapon: Weapon,
    val price: Int
)

fun allStageMaps(): List<StageMap> = listOf(
    StageMap(
        stageId = 1,
        title = "果冻谷 · 第一关",
        subtitle = "熟悉地图：小怪 → 资源 → 陷阱 → 商店 → Boss",
        bgTop = 0xFF0F172A,
        bgBot = 0xFF14532D,
        rooms = listOf(
            RoomDef(0, RoomType.START, 0.08f, 0.50f, "起点", nextIds = listOf(1)),
            RoomDef(1, RoomType.MOB, 0.22f, 0.50f, "史莱姆", enemyHp = 90f, enemyAtk = 8f,
                enemyName = "绿史莱姆", goldReward = 12, nextIds = listOf(2, 3)),
            RoomDef(2, RoomType.RESOURCE, 0.36f, 0.32f, "果冻矿", goldReward = 18, healReward = 20f, nextIds = listOf(4)),
            RoomDef(3, RoomType.TRAP, 0.36f, 0.68f, "尖刺带", trapDamage = 25f, trapGoldCost = 8, nextIds = listOf(4)),
            RoomDef(4, RoomType.MOB, 0.50f, 0.50f, "双生胶", enemyHp = 120f, enemyAtk = 11f,
                enemyName = "双生胶", enemyColor = 0xFF67E8F9, goldReward = 16, nextIds = listOf(5)),
            RoomDef(5, RoomType.SHOP, 0.64f, 0.50f, "流浪商店", nextIds = listOf(6)),
            RoomDef(6, RoomType.ELITE, 0.78f, 0.50f, "精英守卫", enemyHp = 160f, enemyAtk = 14f,
                enemyName = "甲壳守卫", enemyColor = 0xFFFB923C, goldReward = 25, nextIds = listOf(7)),
            RoomDef(7, RoomType.BOSS, 0.92f, 0.50f, "谷主", enemyHp = 200f, enemyAtk = 16f,
                enemyName = "谷主·大果冻", enemyColor = 0xFFC084FC, goldReward = 40, nextIds = listOf(8)),
            RoomDef(8, RoomType.EXIT, 0.98f, 0.50f, "通关", nextIds = emptyList())
        )
    ),
    StageMap(
        stageId = 2,
        title = "雷暴矿道 · 第二关",
        subtitle = "分支更多，商店在中段，小心地雷",
        bgTop = 0xFF1E1B4B,
        bgBot = 0xFF422006,
        rooms = listOf(
            RoomDef(0, RoomType.START, 0.06f, 0.50f, "入口", nextIds = listOf(1, 2)),
            RoomDef(1, RoomType.MOB, 0.18f, 0.35f, "电蚊", enemyHp = 100f, enemyAtk = 12f,
                enemyName = "电蚊群", enemyColor = 0xFFFACC15, goldReward = 14, nextIds = listOf(3)),
            RoomDef(2, RoomType.TRAP, 0.18f, 0.65f, "落石", trapDamage = 30f, trapGoldCost = 10, nextIds = listOf(3)),
            RoomDef(3, RoomType.RESOURCE, 0.32f, 0.50f, "水晶", goldReward = 22, healReward = 15f, nextIds = listOf(4, 5)),
            RoomDef(4, RoomType.MOB, 0.46f, 0.30f, "矿傀", enemyHp = 140f, enemyAtk = 13f,
                enemyName = "矿傀儡", enemyColor = 0xFFA8A29E, goldReward = 18, nextIds = listOf(6)),
            RoomDef(5, RoomType.ELITE, 0.46f, 0.70f, "雷精", enemyHp = 170f, enemyAtk = 15f,
                enemyName = "雷精", enemyColor = 0xFFFDE047, goldReward = 28, nextIds = listOf(6)),
            RoomDef(6, RoomType.SHOP, 0.62f, 0.50f, "矿道黑市", nextIds = listOf(7)),
            RoomDef(7, RoomType.TRAP, 0.76f, 0.50f, "电网", trapDamage = 35f, trapGoldCost = 12, nextIds = listOf(8)),
            RoomDef(8, RoomType.BOSS, 0.90f, 0.50f, "矿脉之心", enemyHp = 240f, enemyAtk = 18f,
                enemyName = "矿脉之心", enemyColor = 0xFFEF4444, goldReward = 50, nextIds = listOf(9)),
            RoomDef(9, RoomType.EXIT, 0.97f, 0.50f, "出口", nextIds = emptyList())
        )
    )
)

fun defaultLoadout(): MutableList<Weapon> = mutableListOf(
    WeaponCatalog.FLAME_CLAW,
    WeaponCatalog.ICE_SPIKE
)

fun shopCatalog(stageId: Int, gold: Int): List<ShopOffer> {
    val pool = listOf(
        ShopOffer(WeaponCatalog.THUNDER_HAMMER, 22),
        ShopOffer(WeaponCatalog.SHELL_GUARD, 18),
        ShopOffer(WeaponCatalog.DRILL_BIT, 24),
        ShopOffer(WeaponCatalog.POISON_SAC, 16),
        ShopOffer(WeaponCatalog.VAMP_TENDRIL, 20),
        ShopOffer(WeaponCatalog.FLAME_CLAW, 14),
        ShopOffer(WeaponCatalog.ICE_SPIKE, 14)
    )
    // Rotate offers by stage
    val start = (stageId - 1) % pool.size
    return (0 until 3).map { pool[(start + it) % pool.size] }
}

fun roomColor(type: RoomType): Color = when (type) {
    RoomType.START -> Color(0xFF64748B)
    RoomType.MOB -> Color(0xFF4ADE80)
    RoomType.ELITE -> Color(0xFFFB923C)
    RoomType.RESOURCE -> Color(0xFFFACC15)
    RoomType.TRAP -> Color(0xFFEF4444)
    RoomType.SHOP -> Color(0xFF60A5FA)
    RoomType.BOSS -> Color(0xFFC084FC)
    RoomType.EXIT -> Color(0xFF2DD4BF)
}

fun roomIcon(type: RoomType): String = when (type) {
    RoomType.START -> "起"
    RoomType.MOB -> "怪"
    RoomType.ELITE -> "精"
    RoomType.RESOURCE -> "矿"
    RoomType.TRAP -> "陷"
    RoomType.SHOP -> "店"
    RoomType.BOSS -> "Boss"
    RoomType.EXIT -> "通"
}
