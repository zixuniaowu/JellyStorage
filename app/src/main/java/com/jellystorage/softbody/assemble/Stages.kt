package com.jellystorage.softbody.assemble

import androidx.compose.ui.graphics.Color

data class EnemyDef(
    val name: String,
    val title: String,
    val hp: Int,
    val atk: Int,
    val def: Int,
    val color: Color,
    val bodyColor: Color,
    val weakTo: Set<Tag>,
    val resist: Set<Tag> = emptySet(),
    /** Must activate one of these synergies or damage is crushed. */
    val requireSynergy: Set<String> = emptySet(),
    val attackInterval: Float = 1.1f,
    val moveStyle: Int = 0, // 0 idle sway, 1 charge, 2 kite
    val flavor: String,
    val hint: String
)

data class StageDef(
    val id: Int,
    val chapter: String,
    val enemy: EnemyDef,
    val unlockWeapons: Int,
    val bgTop: Color,
    val bgBot: Color
)

fun allStages(): List<StageDef> = listOf(
    StageDef(
        1, "训练场",
        EnemyDef(
            "泥浆史莱姆", "软弱的试炼",
            hp = 120, atk = 9, def = 2,
            color = Color(0xFF4ADE80), bodyColor = Color(0xFFBBF7D0),
            weakTo = setOf(Tag.FIRE),
            resist = setOf(Tag.POISON),
            attackInterval = 1.25f,
            flavor = "第一只练习假想敌",
            hint = "装上「火焰爪」或任意带【火】的武器"
        ),
        unlockWeapons = 6,
        bgTop = Color(0xFF0F172A), bgBot = Color(0xFF14532D)
    ),
    StageDef(
        2, "灼热矿道",
        EnemyDef(
            "熔岩甲虫", "硬壳火焰虫",
            hp = 160, atk = 13, def = 10,
            color = Color(0xFFFB923C), bodyColor = Color(0xFFFED7AA),
            weakTo = setOf(Tag.ICE, Tag.HEAVY),
            resist = setOf(Tag.FIRE),
            requireSynergy = setOf("cryo_crush"),
            attackInterval = 1.15f, moveStyle = 1,
            flavor = "壳烫得发亮，普通火伤被无视",
            hint = "需要【冰】+【重】→「碎冰重击」（冰晶刺+火焰爪/雷霆锤/钻头牙）"
        ),
        unlockWeapons = 8,
        bgTop = Color(0xFF1C0A00), bgBot = Color(0xFF7C2D12)
    ),
    StageDef(
        3, "毒沼遗迹",
        EnemyDef(
            "毒沼幽灵", "腐蚀雾灵",
            hp = 150, atk = 16, def = 5,
            color = Color(0xFFA3E635), bodyColor = Color(0xFFD9F99D),
            weakTo = setOf(Tag.LIGHTNING, Tag.SHIELD),
            resist = setOf(Tag.POISON, Tag.DRAIN),
            requireSynergy = setOf("storm_shell"),
            attackInterval = 0.95f,
            flavor = "靠近就中毒，得靠反伤磨死",
            hint = "需要【雷】+【盾】→「雷甲反伤」（雷霆锤/电鞭 + 甲壳盾/霜甲）"
        ),
        unlockWeapons = 10,
        bgTop = Color(0xFF052E16), bgBot = Color(0xFF365314)
    ),
    StageDef(
        4, "永冻峡谷",
        EnemyDef(
            "冰原巨齿", "冻土霸主",
            hp = 200, atk = 15, def = 14,
            color = Color(0xFF38BDF8), bodyColor = Color(0xFFE0F2FE),
            weakTo = setOf(Tag.FIRE, Tag.ICE),
            resist = setOf(Tag.ICE),
            requireSynergy = setOf("steam_burst"),
            attackInterval = 1.2f, moveStyle = 1,
            flavor = "厚冰甲，只有热胀冷缩能破",
            hint = "需要【火】+【冰】→「蒸汽爆裂」"
        ),
        unlockWeapons = 11,
        bgTop = Color(0xFF0C4A6E), bgBot = Color(0xFF082F49)
    ),
    StageDef(
        5, "瘟疫巢穴",
        EnemyDef(
            "瘟疫母体", "无限增生",
            hp = 240, atk = 12, def = 7,
            color = Color(0xFF86EFAC), bodyColor = Color(0xFFBBF7D0),
            weakTo = setOf(Tag.POISON, Tag.DRAIN),
            resist = setOf(Tag.SHIELD),
            requireSynergy = setOf("plague_leech"),
            attackInterval = 1.0f,
            flavor = "血厚会回血，拼续航",
            hint = "需要【毒】+【吸】→「瘟疫吸血」（毒雾囊/疫牙 + 吸血触手）"
        ),
        unlockWeapons = 12,
        bgTop = Color(0xFF14532D), bgBot = Color(0xFF1A1A2E)
    ),
    StageDef(
        6, "雷暴工厂",
        EnemyDef(
            "雷暴傀儡", "过载核心",
            hp = 190, atk = 20, def = 6,
            color = Color(0xFFFACC15), bodyColor = Color(0xFFFEF9C3),
            weakTo = setOf(Tag.PIERCE, Tag.SWIFT),
            resist = setOf(Tag.LIGHTNING),
            requireSynergy = setOf("chain_drill"),
            attackInterval = 0.75f, moveStyle = 2,
            flavor = "攻速极快，点杀核心",
            hint = "需要【穿】+【迅】→「连锁钻击」（钻头牙/冰晶刺 + 电鞭/吸血触手/弹射胶）"
        ),
        unlockWeapons = 12,
        bgTop = Color(0xFF1E1B4B), bgBot = Color(0xFF422006)
    ),
    StageDef(
        7, "爆裂温室",
        EnemyDef(
            "爆裂孢囊", "不稳定有机体",
            hp = 210, atk = 17, def = 5,
            color = Color(0xFFFB7185), bodyColor = Color(0xFFFECDD3),
            weakTo = setOf(Tag.FIRE, Tag.POISON, Tag.BLAST),
            resist = setOf(Tag.HEAVY),
            requireSynergy = setOf("burn_toxic", "inferno"),
            attackInterval = 1.05f,
            flavor = "一碰就炸，用燃毒或熔火",
            hint = "「燃毒炼狱」火+毒 或 「熔火之心」火+爆"
        ),
        unlockWeapons = 12,
        bgTop = Color(0xFF4C0519), bgBot = Color(0xFF1A1030)
    ),
    StageDef(
        8, "终焉回廊",
        EnemyDef(
            "混沌胶皇", "最终试炼",
            hp = 320, atk = 22, def = 12,
            color = Color(0xFFC084FC), bodyColor = Color(0xFFE9D5FF),
            weakTo = setOf(Tag.FIRE, Tag.ICE, Tag.HEAVY),
            resist = setOf(Tag.SWIFT, Tag.BLAST),
            requireSynergy = setOf("steam_burst", "cryo_crush"),
            attackInterval = 0.9f, moveStyle = 1,
            flavor = "只有顶级奇妙组合能撕裂核心",
            hint = "「蒸汽爆裂」或「碎冰重击」二选一打穿"
        ),
        unlockWeapons = 12,
        bgTop = Color(0xFF2E1065), bgBot = Color(0xFF0F0320)
    ),
)
