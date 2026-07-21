package com.jellystorage.play

/**
 * Narrative layer for 果冻勇者 — chapters, node flavor, events, boss lines.
 */
data class StoryBeat(
    val speaker: String,
    val line: String,
    val mood: StoryMood = StoryMood.NORMAL
)

enum class StoryMood { NORMAL, TENSE, WARM, FUNNY, DARK }

data class ChapterLore(
    val title: String,
    val subtitle: String,
    val intro: List<StoryBeat>,
    val clear: List<StoryBeat>,
    val worldNote: String
)

object StoryBook {
    val worldPremise = listOf(
        StoryBeat("旁白", "据说世界的核心是一颗会呼吸的果冻。"),
        StoryBeat("旁白", "它碎裂之后，大地长出会走动的软泥，矿脉也会低声哭泣。"),
        StoryBeat("旁白", "你是被「保存者盟会」派来的新人——任务很简单：把溢出的软体压回去。"),
        StoryBeat("旁白", "但没人告诉你，保存者本身，也在慢慢融化。")
    )

    fun heroGreeting(hero: HeroClass): List<StoryBeat> = when (hero) {
        HeroClass.WARRIOR -> listOf(
            StoryBeat("你", "剑还在。腿也在。那就够了。", StoryMood.TENSE),
            StoryBeat("老兵残响", "别笑，小子。草原上的史莱姆会把盔甲当点心。", StoryMood.FUNNY),
            StoryBeat("你", "……那我先把点心切了。", StoryMood.WARM)
        )
        HeroClass.MAGE -> listOf(
            StoryBeat("你", "火焰记得路线。寒冰记得边界。", StoryMood.NORMAL),
            StoryBeat("学徒日记", "导师说：法术不是力量，是「整理世界的语法」。", StoryMood.WARM),
            StoryBeat("你", "那我去给这片草地改个错别字。", StoryMood.FUNNY)
        )
        HeroClass.TAOIST -> listOf(
            StoryBeat("你", "符纸新裁，香灰未冷。", StoryMood.WARM),
            StoryBeat("云游老人", "道不是躲开，是把伤痛放回它该在的位置。", StoryMood.NORMAL),
            StoryBeat("你", "那我就把溢出的果冻，请回坛子里。", StoryMood.WARM)
        )
    }

    val chapters: List<ChapterLore> = listOf(
        ChapterLore(
            title = "第一章 · 果冻草原",
            subtitle = "软泥初醒的原野",
            intro = listOf(
                StoryBeat("信使", "东边的草在发抖。不是风，是地底有东西在吞。", StoryMood.TENSE),
                StoryBeat("盟会文书", "第一目标：清扫史莱姆窝，确认「果核裂纹」是否外溢。", StoryMood.NORMAL),
                StoryBeat("旁白", "你踏进草地时，空气甜得发腻——像融化的糖浆。", StoryMood.DARK)
            ),
            clear = listOf(
                StoryBeat("你", "草原安静了。但甜味还在。", StoryMood.NORMAL),
                StoryBeat("信使", "裂纹只是开口。真正的源头在矿道深处。", StoryMood.TENSE),
                StoryBeat("旁白", "远方的雷声不像天气，更像某种巨大的东西在翻身。", StoryMood.DARK)
            ),
            worldNote = "这里曾是游牧民族的牧场。果核碎裂后，牛羊变成了会跳的胶团。"
        ),
        ChapterLore(
            title = "第二章 · 哭泣矿道",
            subtitle = "会说话的石头",
            intro = listOf(
                StoryBeat("矿工幽灵", "别碰墙上的绿光……那是它的眼睛。", StoryMood.DARK),
                StoryBeat("盟会文书", "第二目标：抵达矿脉之心，压制「空罐回声」。", StoryMood.NORMAL),
                StoryBeat("你", "回声再响，也要先过我这关。", StoryMood.TENSE)
            ),
            clear = listOf(
                StoryBeat("矿脉魔（残响）", "你们保存的是形状……不是生命……", StoryMood.DARK),
                StoryBeat("你", "那我就两个都保住。", StoryMood.WARM),
                StoryBeat("旁白", "矿道塌了一角。光从裂缝里漏进来，像世界第一次学会呼吸。", StoryMood.WARM)
            ),
            worldNote = "矿工们曾用符文锁住果核碎片。锁坏了，人先消失，符还在墙上发烫。"
        ),
        ChapterLore(
            title = "第三章 · 空罐王城",
            subtitle = "盖子掉光的城",
            intro = listOf(
                StoryBeat("旁白", "城墙上的旗帜全是抹布。甜蜜在这里发了霉。", StoryMood.DARK),
                StoryBeat("盟会文书", "最终目标：封住空罐君王，让世界重新学会盖盖子。", StoryMood.NORMAL),
                StoryBeat("你", "罐子可以碎。人不可以。", StoryMood.TENSE)
            ),
            clear = listOf(
                StoryBeat("空罐君王（残响）", "……合上了？真的合上了？", StoryMood.DARK),
                StoryBeat("你", "合上了。今晚可以睡个整觉。", StoryMood.WARM),
                StoryBeat("旁白", "风第一次没有糖味。草原、矿道、王城——都安静下来。", StoryMood.WARM)
            ),
            worldNote = "王城曾用巨大的罐阵储存果核之力。盖子飞走那天，王国也飞走了。"
        ),
        ChapterLore(
            title = "第四章 · 墨海秘境",
            subtitle = "画中有画",
            intro = listOf(
                StoryBeat("旁白", "你踏进纸面。浪不是水，是未干的墨。", StoryMood.DARK),
                StoryBeat("画舫舟子", "客官，浪里有眼睛。别盯太久。", StoryMood.TENSE),
                StoryBeat("你", "那就让眼睛先眨眼。", StoryMood.FUNNY)
            ),
            clear = listOf(
                StoryBeat("你", "海静了。笔锋还在抖。", StoryMood.NORMAL),
                StoryBeat("旁白", "远峰露出一角空白——像等待落款的宣纸。", StoryMood.WARM)
            ),
            worldNote = "据说保存者盟会的档案室有一卷活画。有人进去，就再也没以原来的样子出来。"
        ),
        ChapterLore(
            title = "第五章 · 画魂峰",
            subtitle = "最后一笔",
            intro = listOf(
                StoryBeat("旁白", "峰高如悬。云是擦不掉的飞白。", StoryMood.TENSE),
                StoryBeat("盟会文书", "封卷。落款。让世界重新学会合上。", StoryMood.NORMAL),
                StoryBeat("你", "这一笔，我来。", StoryMood.WARM)
            ),
            clear = listOf(
                StoryBeat("画魂（残响）", "……写完了？", StoryMood.DARK),
                StoryBeat("你", "写完了。可以盖印了。", StoryMood.WARM),
                StoryBeat("旁白", "风过松林，像有人在轻轻吹干墨迹。", StoryMood.WARM)
            ),
            worldNote = "画魂不是怪物，是未完成的自我。你打赢它，等于替世界写上句号。"
        )
    )

    fun nodeEnter(stageId: Int, nodeName: String, type: NodeType): List<StoryBeat> {
        val common = when (type) {
            NodeType.START -> listOf(
                StoryBeat("旁白", "火堆很小，却把影子拉得很长。"),
                StoryBeat("你", "从这里出发。")
            )
            NodeType.MOB -> listOf(
                StoryBeat("旁白", "地面发黏。有东西在数你的脚步。", StoryMood.TENSE),
                StoryBeat("你", "那就让它们数错。", StoryMood.TENSE)
            )
            NodeType.ELITE -> listOf(
                StoryBeat("旁白", "空气突然变重，像有人把世界按了暂停。", StoryMood.TENSE),
                StoryBeat("未知", "……新的保存者？", StoryMood.DARK)
            )
            NodeType.BOSS -> bossIntro(stageId)
            NodeType.GOLD -> listOf(
                StoryBeat("旁白", "袋子鼓鼓的。有人逃得很急，或者送得很诚恳。", StoryMood.FUNNY),
                StoryBeat("你", "先收下。道理以后再讲。", StoryMood.FUNNY)
            )
            NodeType.HEAL -> listOf(
                StoryBeat("旁白", "泉水清得不真实，像从记忆里舀出来的。", StoryMood.WARM),
                StoryBeat("你", "谢谢。"),
            )
            NodeType.TRAP -> listOf(
                StoryBeat("旁白", "草叶反光的角度不对。", StoryMood.TENSE),
                StoryBeat("你", "——糟。", StoryMood.FUNNY)
            )
            NodeType.SHOP -> listOf(
                StoryBeat("铁匠", "刀钝了就别硬刚。我这不赊账……除非你讲个好笑的故事。", StoryMood.FUNNY),
                StoryBeat("你", "我刚被史莱姆亲过。算好笑吗？", StoryMood.FUNNY)
            )
            NodeType.REST -> listOf(
                StoryBeat("旁白", "篝火噼啪响，像在替你数心跳。", StoryMood.WARM),
                StoryBeat("你", "歇一歇。还有路。", StoryMood.WARM)
            )
            NodeType.EVENT -> listOf(
                StoryBeat("旁白", "命运喜欢在岔路口摆摊。", StoryMood.NORMAL)
            )
            NodeType.EXIT -> listOf(
                StoryBeat("旁白", "出口的风是干净的。", StoryMood.WARM)
            )
        }
        // node-specific spice
        val spice = when (nodeName) {
            "史莱姆窝" -> listOf(StoryBeat("幼软泥", "咕……（像在撒娇）", StoryMood.FUNNY))
            "混战原" -> listOf(StoryBeat("旁白", "这里没有阵线，只有互相撞在一起的慌张。", StoryMood.TENSE))
            "精英甲虫" -> listOf(StoryBeat("甲虫", "咔——（甲壳合上，像合上一本旧账）", StoryMood.TENSE))
            "草原霸主" -> listOf(StoryBeat("霸主", "草地是我的胃。", StoryMood.DARK))
            "矿鼠群" -> listOf(StoryBeat("矿鼠", "吱吱！（听起来像在骂你踩了它们的尾巴）", StoryMood.FUNNY))
            "矿精" -> listOf(StoryBeat("矿精", "把光还给我……", StoryMood.DARK))
            "矿脉魔" -> listOf(StoryBeat("矿脉魔", "保存者……也会碎。", StoryMood.DARK))
            "神秘商人" -> listOf(StoryBeat("商人", "现金、血、或者一个秘密。我都收。", StoryMood.FUNNY))
            "古碑" -> listOf(StoryBeat("碑文", "「先被记住的，后被吃掉。」", StoryMood.DARK))
            else -> emptyList()
        }
        return common + spice
    }

    fun bossIntro(stageId: Int): List<StoryBeat> = when (stageId) {
        1 -> listOf(
            StoryBeat("草原霸主", "你们把我切成牧场，又怪我饿。", StoryMood.DARK),
            StoryBeat("你", "饿可以。吃人不行。", StoryMood.TENSE),
            StoryBeat("草原霸主", "那就把剑放进胃里——我们谈谈消化。", StoryMood.FUNNY)
        )
        else -> listOf(
            StoryBeat("矿脉魔", "锁链是你们写的谎言。", StoryMood.DARK),
            StoryBeat("你", "那我改写结局。", StoryMood.TENSE),
            StoryBeat("矿脉魔", "来啊。让石头学会流血。", StoryMood.DARK)
        )
    }

    fun bossDefeat(stageId: Int): List<StoryBeat> = when (stageId) {
        1 -> listOf(
            StoryBeat("草原霸主", "……原来我也可以被记住，而不是被消化。", StoryMood.WARM),
            StoryBeat("旁白", "巨大的软体塌成一滩星光。草叶重新站直。", StoryMood.WARM)
        )
        else -> listOf(
            StoryBeat("矿脉魔", "保存……我……", StoryMood.DARK),
            StoryBeat("你", "睡吧。矿道会替你守夜。", StoryMood.WARM),
            StoryBeat("旁白", "符文的红光熄灭，像终于闭上的眼。", StoryMood.WARM)
        )
    }

    fun ending(hero: HeroClass, kills: Int, level: Int): List<StoryBeat> = listOf(
        StoryBeat("旁白", "两道裂纹暂时愈合。世界没有变好，只是更安静了一点。", StoryMood.WARM),
        StoryBeat("盟会文书", "任务评级：可保存。备注：该新人话有点多。", StoryMood.FUNNY),
        when (hero) {
            HeroClass.WARRIOR -> StoryBeat("你", "剑可以收。伤还要走一阵。", StoryMood.WARM)
            HeroClass.MAGE -> StoryBeat("你", "语法对了，故事才会继续。", StoryMood.WARM)
            HeroClass.TAOIST -> StoryBeat("你", "果冻回坛。心回位。", StoryMood.WARM)
        },
        StoryBeat("旁白", "击破 $kills 个溢出体，成长至 Lv$level。这不是结局——只是第一罐被盖上的盖子。", StoryMood.NORMAL),
        StoryBeat("？？？", "盖子下面……还有更深的甜。", StoryMood.DARK)
    )

    fun eventScript(eventId: String): Triple<String, String, List<Triple<String, String, String>>> {
        // title, body, choices: label, resultText, effectKey
        return when (eventId) {
            "merchant" -> Triple(
                "戴面具的行商",
                "他推着一车会发光的瓶子。面具裂缝里露出的不是眼睛，是缓慢蠕动的粉。\n「血换锋刃，金换药，或者……把你刚学会的恐惧卖给我。」",
                listOf(
                    Triple("献血换力量（-30HP，得锋刃）", "你划破手掌。行商笑出黏黏的声响：「锋利会记住你。」", "blood_atk"),
                    Triple("买药（20金）", "瓶子里是温的。像有人把阳光熬成了糖浆。", "buy_potion"),
                    Triple("什么都不买，转身就走", "行商挥挥手：「下次带故事来。」车轮碾过草地，没有留下辙印。", "leave")
                )
            )
            "stele" -> Triple(
                "会发热的古碑",
                "碑面刻着歪扭的符文，摸上去像活人的脉搏。\n风从碑后吹来，带着铁锈与奶糖混在一起的味道。",
                listOf(
                    Triple("默念会心之诀", "符文亮了一下。你的瞳孔深处闪过刀锋的弧光。", "crit"),
                    Triple("用20金涂满碑缝", "金币熔化进石头。你忽然听见自己的心跳，更贪、更准。", "lifesteal"),
                    Triple("砸开碑心（+35金，受伤）", "碑碎成金币雨。碎片也划开了你的肩甲——公平交易。", "smash")
                )
            )
            "bard" -> Triple(
                "流浪歌手",
                "他只肯唱半首歌：前半截是摇篮曲，后半截总被风吹走。\n琴弦是草茎搓的，却弹出金属的脆响。",
                listOf(
                    Triple("听完整段（回血）", "后半截突然补上了。你像被暖手的人揉了揉肩。", "heal_soft"),
                    Triple("丢20金进琴箱", "他点头：「下一站给你唱完整的。」金币变成轻盈。", "spd"),
                    Triple("什么也不给，继续赶路", "他笑笑：「赶路的人脚步最响。」", "leave")
                )
            )
            "archive" -> Triple(
                "旧档案室",
                "羊皮纸在冒汗。墨迹写着：「保存期限：永远」——后面被人划掉，改成「三天」。",
                listOf(
                    Triple("抄下体魄口诀", "纸边烫手。你的骨头好像更沉、更稳。", "hp"),
                    Triple("撕下金页（+25金）", "金箔飞起来，像一群逃跑的蛾。", "gold25"),
                    Triple("点火烧了它", "灰里跳出一枚滚烫的护符，贴在你胸口微微发热。", "armor")
                )
            )
            else -> Triple(
                "路边的低语",
                "草丛里有人说话，却看不见嘴。",
                listOf(Triple("把耳朵凑近（+15金）", "低语塞给你一枚还带着体温的硬币。", "gold15"))
            )
        }
    }

    fun combatTaunt(type: NodeType, wave: Int, total: Int): String? {
        if (type != NodeType.BOSS && type != NodeType.ELITE) {
            return if (wave == 0) listOf(
                "笔锋落下，软泥围上来了。",
                "空气甜得发腻——开战。",
                "脚底发黏。收势。"
            ).random()
            else if (wave + 1 >= total) "最后一波。落款。" else "还有后续……稳住笔。"
        }
        return when {
            type == NodeType.BOSS && wave == 0 -> listOf(
                "它认得你手里的武器。",
                "画中主笔抬眼看你。",
                "盖子震了一下——Boss现身。"
            ).random()
            type == NodeType.BOSS -> "墨意更浓了。它在叠痛苦。"
            else -> "精英甲壳反光——像在嘲笑你的笔法。"
        }
    }

    fun journalLine(stageTitle: String, nodeName: String, won: Boolean): String {
        return if (won) "「$stageTitle」·$nodeName 已清理。软泥退回地缝。"
        else "「$stageTitle」·$nodeName 失败。盟会将寄去一张空白悼词。"
    }
}
