package com.jellystorage.play

/**
 * 人体内免疫战争的叙事层：器官章节、感染节点、事件和首领对白。
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
        StoryBeat("旁白", "这里不是星球，而是一具正在发热的人体。"),
        StoryBeat("旁白", "一道微小创口让未知病原体越过皮肤，开始沿血流扩散。"),
        StoryBeat("免疫中枢", "白细胞战士，依次净化皮肤、肺、胃肠、肝脏与心脏。"),
        StoryBeat("免疫中枢", "记住它们。下一轮感染时，病毒也会记住你。", StoryMood.DARK)
    )

    fun heroGreeting(hero: HeroClass): List<StoryBeat> = when (hero) {
        HeroClass.WARRIOR -> listOf(
            StoryBeat("你", "细胞膜完整，吞噬刃就绪。", StoryMood.TENSE),
            StoryBeat("中性粒细胞", "别被它们柔软的外形骗了，菌膜比盔甲更难破。", StoryMood.NORMAL),
            StoryBeat("你", "那就连菌膜一起切开。", StoryMood.WARM)
        )
        HeroClass.MAGE -> listOf(
            StoryBeat("你", "炎症负责标记，低温负责封锁。", StoryMood.NORMAL),
            StoryBeat("B细胞", "抗体不是魔法，是精准识别后的答案。", StoryMood.WARM),
            StoryBeat("你", "那就给每个病原体一份答案。", StoryMood.FUNNY)
        )
        HeroClass.TAOIST -> listOf(
            StoryBeat("你", "调节因子已装填，修复信号稳定。", StoryMood.WARM),
            StoryBeat("调节性T细胞", "免疫不是毁灭一切，而是让反应停在正确的位置。", StoryMood.NORMAL),
            StoryBeat("你", "病原体清除，正常细胞留下。", StoryMood.WARM)
        )
    }

    val chapters: List<ChapterLore> = listOf(
        ChapterLore(
            title = "第一章 · 皮肤创口",
            subtitle = "最初防线的裂口",
            intro = listOf(
                StoryBeat("血小板", "创口还没闭合，菌群正在穿过凝血网。", StoryMood.TENSE),
                StoryBeat("免疫中枢", "第一目标：压制创口感染核心，完成表皮封闭。", StoryMood.NORMAL),
                StoryBeat("旁白", "组织液泛着红光，每一次脉动都把敌人推得更深。", StoryMood.DARK)
            ),
            clear = listOf(
                StoryBeat("你", "创口已经封住，但一部分病毒进入了血流。", StoryMood.NORMAL),
                StoryBeat("免疫中枢", "追踪信号抵达肺部。不要让它们占据肺泡。", StoryMood.TENSE),
                StoryBeat("旁白", "你跃入毛细血管，随血流冲向呼吸深处。", StoryMood.DARK)
            ),
            worldNote = "皮肤负责阻挡大多数入侵者；一旦防线破损，凝血与先天免疫会同时启动。"
        ),
        ChapterLore(
            title = "第二章 · 肺泡云海",
            subtitle = "每一次呼吸都在交战",
            intro = listOf(
                StoryBeat("肺泡巨噬细胞", "飞沫病毒黏住了纤毛，氧气交换正在下降。", StoryMood.DARK),
                StoryBeat("免疫中枢", "第二目标：清除肺部感染核心，恢复氧合。", StoryMood.NORMAL),
                StoryBeat("你", "先让气道重新流动。", StoryMood.TENSE)
            ),
            clear = listOf(
                StoryBeat("病毒母体（残响）", "你封住一个肺泡，还能封住每一次呼吸吗？", StoryMood.DARK),
                StoryBeat("你", "不能。所以我会让身体记住你。", StoryMood.WARM),
                StoryBeat("旁白", "肺泡重新舒张，氧气像晨光一样进入血液。", StoryMood.WARM)
            ),
            worldNote = "肺泡薄而脆弱。这里的气流、缺氧与飞沫会不断改变战场位置。"
        ),
        ChapterLore(
            title = "第三章 · 胃肠菌林",
            subtitle = "共生与感染的边界",
            intro = listOf(
                StoryBeat("旁白", "这里本就住着亿万菌群，敌我边界比任何器官都模糊。", StoryMood.DARK),
                StoryBeat("免疫中枢", "第三目标：保护共生菌，清除劫持肠壁的感染核心。", StoryMood.NORMAL),
                StoryBeat("你", "能帮身体的留下，越界的清除。", StoryMood.TENSE)
            ),
            clear = listOf(
                StoryBeat("感染核心（残响）", "没有我们，你也无法消化这座森林。", StoryMood.DARK),
                StoryBeat("你", "共生不是纵容入侵。", StoryMood.WARM),
                StoryBeat("旁白", "绒毛重新舒展，营养与毒素一起流向肝门静脉。", StoryMood.WARM)
            ),
            worldNote = "胃酸会灼伤双方，肠道菌群则可能成为援军或屏障；选择路线比单纯击杀更重要。"
        ),
        ChapterLore(
            title = "第四章 · 肝脏净化区",
            subtitle = "身体的解毒工厂",
            intro = listOf(
                StoryBeat("库普弗细胞", "毒素太多，肝小叶正在失去过滤能力。", StoryMood.DARK),
                StoryBeat("免疫中枢", "第四目标：移除坏死细胞与毒性菌团，恢复净化。", StoryMood.TENSE),
                StoryBeat("你", "把能代谢的交给肝脏，不能的交给我。", StoryMood.FUNNY)
            ),
            clear = listOf(
                StoryBeat("你", "毒素曲线下降了。", StoryMood.NORMAL),
                StoryBeat("旁白", "净化后的血流涌向心脏，那里传来不规律的搏动。", StoryMood.WARM)
            ),
            worldNote = "肝脏会再生，但持续的毒性与炎症会拖慢修复；战斗越久，场地越危险。"
        ),
        ChapterLore(
            title = "第五章 · 心脏循环核",
            subtitle = "全身扩散前的最后防线",
            intro = listOf(
                StoryBeat("旁白", "四个心腔像巨大的泵，每次收缩都让战场改变方向。", StoryMood.TENSE),
                StoryBeat("免疫中枢", "最终目标：消灭循环变异母体，阻止病原体扩散全身。", StoryMood.NORMAL),
                StoryBeat("你", "这一轮感染，到这里结束。", StoryMood.WARM)
            ),
            clear = listOf(
                StoryBeat("变异母体（残响）", "下一代……会避开你的抗体……", StoryMood.DARK),
                StoryBeat("你", "那免疫记忆也会继续进化。", StoryMood.WARM),
                StoryBeat("旁白", "心律恢复稳定，但新的变异序列已在远处亮起。", StoryMood.WARM)
            ),
            worldNote = "心脏将病原体送往全身，也把免疫细胞送往所有战场。这里既是终点，也是下一轮的起点。"
        )
    )

    fun nodeEnter(stageId: Int, nodeName: String, type: NodeType): List<StoryBeat> {
        val common = when (type) {
            NodeType.START -> listOf(
                StoryBeat("免疫中枢", "区域扫描完成，感染路径已经标记。"),
                StoryBeat("你", "开始净化。")
            )
            NodeType.MOB -> listOf(
                StoryBeat("旁白", "病原体信号快速增殖，正在包围你的细胞膜。", StoryMood.TENSE),
                StoryBeat("你", "锁定抗原，开始吞噬。", StoryMood.TENSE)
            )
            NodeType.ELITE -> listOf(
                StoryBeat("旁白", "耐药菌膜正在形成，普通攻击很难穿透。", StoryMood.TENSE),
                StoryBeat("变异体", "你的抗体已经过时了。", StoryMood.DARK)
            )
            NodeType.BOSS -> bossIntro(stageId)
            NodeType.GOLD -> listOf(
                StoryBeat("旁白", "一团可吸收营养漂在组织液中。", StoryMood.FUNNY),
                StoryBeat("你", "先补充能量，再讨论代谢。", StoryMood.FUNNY)
            )
            NodeType.HEAL -> listOf(
                StoryBeat("旁白", "修复因子覆盖破损膜层，细胞活性开始恢复。", StoryMood.WARM),
                StoryBeat("你", "修复完成。"),
            )
            NodeType.TRAP -> listOf(
                StoryBeat("旁白", "毒性读数突然越过安全线。", StoryMood.TENSE),
                StoryBeat("你", "膜层受损——继续移动。", StoryMood.FUNNY)
            )
            NodeType.SHOP -> listOf(
                StoryBeat("抗体工程师", "武器不匹配抗原，再高的攻击也只是浪费能量。", StoryMood.NORMAL),
                StoryBeat("你", "按下一区域的病原体重新装配。", StoryMood.NORMAL)
            )
            NodeType.REST -> listOf(
                StoryBeat("旁白", "修复信号稳定下来，细胞器重新恢复节律。", StoryMood.WARM),
                StoryBeat("你", "短暂休整。下一处器官还在等待。", StoryMood.WARM)
            )
            NodeType.EVENT -> listOf(
                StoryBeat("旁白", "一段陌生生物信号在岔路口等待回应。", StoryMood.NORMAL)
            )
            NodeType.EXIT -> listOf(
                StoryBeat("旁白", "这一器官的指标恢复稳定，血流开启下一条通道。", StoryMood.WARM)
            )
        }
        // node-specific spice
        val spice = when (nodeName) {
            "菌落创面" -> listOf(StoryBeat("球状菌", "分裂、占据、继续分裂。", StoryMood.DARK))
            "炎症交界" -> listOf(StoryBeat("旁白", "免疫信号与病原体在这里撞成一片。", StoryMood.TENSE))
            "耐药杆菌" -> listOf(StoryBeat("护膜杆菌", "你的识别序列，已经被我们读完。", StoryMood.TENSE))
            "创口感染核心" -> listOf(StoryBeat("感染核心", "创口是门，而我已经进来了。", StoryMood.DARK))
            "飞沫病毒群" -> listOf(StoryBeat("翼膜病毒", "每一次呼吸，都会复制我们。", StoryMood.DARK))
            "孢子团块" -> listOf(StoryBeat("孢子母体", "氧气属于生长最快的一方。", StoryMood.DARK))
            "肺部感染核心" -> listOf(StoryBeat("变异核心", "你的肺泡会成为新的培养皿。", StoryMood.DARK))
            "记忆细胞" -> listOf(StoryBeat("记忆细胞", "我保存抗原，也保存上一次失败的原因。", StoryMood.WARM))
            "残留抗体" -> listOf(StoryBeat("抗体档案", "先被识别的，会更快被清除。", StoryMood.WARM))
            else -> emptyList()
        }
        return common + spice
    }

    fun bossIntro(stageId: Int): List<StoryBeat> = when (stageId) {
        1 -> listOf(
            StoryBeat("创口感染核心", "你封得住伤口，封不住已经进入的我们。", StoryMood.DARK),
            StoryBeat("你", "那就从这里开始清除。", StoryMood.TENSE)
        )
        2 -> listOf(
            StoryBeat("肺部感染核心", "咳嗽会替我打开更多道路。", StoryMood.DARK),
            StoryBeat("你", "先恢复呼吸，再切断道路。", StoryMood.TENSE)
        )
        3 -> listOf(
            StoryBeat("肠道感染核心", "你分得清谁是共生者，谁是入侵者吗？", StoryMood.DARK),
            StoryBeat("你", "越过黏膜的，就是目标。", StoryMood.TENSE)
        )
        4 -> listOf(
            StoryBeat("肝部感染核心", "毒素已经让你的净化系统超载。", StoryMood.DARK),
            StoryBeat("你", "所以先清除制造毒素的源头。", StoryMood.TENSE)
        )
        else -> listOf(
            StoryBeat("循环变异母体", "击败我，下一代也会从血流中回来。", StoryMood.DARK),
            StoryBeat("你", "下一代见。身体会记得。", StoryMood.TENSE)
        )
    }

    fun bossDefeat(stageId: Int): List<StoryBeat> = when (stageId) {
        1 -> listOf(
            StoryBeat("感染核心", "你会记住我的抗原……", StoryMood.DARK),
            StoryBeat("旁白", "凝血网收紧，创口终于闭合。", StoryMood.WARM)
        )
        2 -> listOf(
            StoryBeat("变异核心", "复制……中止……", StoryMood.DARK),
            StoryBeat("旁白", "肺泡重新舒张，氧合读数恢复。", StoryMood.WARM)
        )
        3 -> listOf(
            StoryBeat("感染核心", "菌群会填补我留下的位置。", StoryMood.DARK),
            StoryBeat("旁白", "共生菌重新占据肠壁，酸潮趋于平稳。", StoryMood.WARM)
        )
        4 -> listOf(
            StoryBeat("感染核心", "净化无法抹掉全部损伤。", StoryMood.DARK),
            StoryBeat("旁白", "肝细胞启动再生，毒素读数持续下降。", StoryMood.WARM)
        )
        else -> listOf(
            StoryBeat("变异母体", "序列已经……传给下一代……", StoryMood.DARK),
            StoryBeat("旁白", "心律恢复，但免疫中枢记录下了新的变异信号。", StoryMood.WARM)
        )
    }

    fun ending(hero: HeroClass, kills: Int, level: Int): List<StoryBeat> = listOf(
        StoryBeat("旁白", "五个器官恢复稳定，本轮感染周期已经结束。", StoryMood.WARM),
        StoryBeat("免疫中枢", "抗原序列已写入免疫记忆。局内装备与能力即将代谢清除。", StoryMood.NORMAL),
        when (hero) {
            HeroClass.WARRIOR -> StoryBeat("你", "吞噬结束。等待下一次应答。", StoryMood.WARM)
            HeroClass.MAGE -> StoryBeat("你", "抗体序列稳定，可以进入记忆库。", StoryMood.WARM)
            HeroClass.TAOIST -> StoryBeat("你", "炎症退去，修复开始。", StoryMood.WARM)
        },
        StoryBeat("旁白", "本轮清除 $kills 个病原体，成长至 Lv$level。等级会重置，记忆不会。", StoryMood.NORMAL),
        StoryBeat("未知毒株", "变异完成。下一次感染，开始。", StoryMood.DARK)
    )

    fun eventScript(eventId: String): Triple<String, String, List<Triple<String, String, String>>> {
        // title, body, choices: label, resultText, effectKey
        return when (eventId) {
            "merchant" -> Triple(
                "树突细胞补给员",
                "它拖着一串发光囊泡，里面封存着从创口采集的抗原。\n「活性换攻击，营养换修复剂，所有强化都要付出代谢成本。」",
                listOf(
                    Triple("消耗活性换攻击（-30HP）", "膜层短暂破损，吞噬刃却变得更锋利。", "blood_atk"),
                    Triple("合成修复剂（20营养）", "温热囊泡融入细胞质，成为一份修复剂。", "buy_potion"),
                    Triple("保留资源，继续前进", "补给员收回囊泡：「保存活性也是一种策略。」", "leave")
                )
            )
            "stele" -> Triple(
                "残留抗原库",
                "旧感染留下的抗原片段仍在脉冲。靠近后，受体开始自动匹配。",
                listOf(
                    Triple("读取弱点序列", "受体完成校准，你更容易命中病原体的薄弱位置。", "crit"),
                    Triple("投入20营养重建受体", "新受体接入膜层，吞噬时能回收更多活性。", "lifesteal"),
                    Triple("分解抗原库（+35营养，受伤）", "回收的营养涌入细胞，但毒性碎片同时划伤膜层。", "smash")
                )
            )
            "bard" -> Triple(
                "神经节律信号",
                "一段规律电脉冲沿神经末梢传来，正在等待你的细胞与它同步。",
                listOf(
                    Triple("同步修复节律（回血）", "脉冲扫过破损处，膜层按同一节拍重新闭合。", "heal_soft"),
                    Triple("消耗20营养加速传导", "电脉冲变得轻快，移动反应明显加速。", "spd"),
                    Triple("保持原节律继续", "信号渐渐远去，你保留了当前资源。", "leave")
                )
            )
            "archive" -> Triple(
                "菌群记忆库",
                "大量共生菌序列在这里沉睡。错误激活可能带来毒性，正确读取则能强化身体。",
                listOf(
                    Triple("激活屏障菌群", "共生菌覆盖膜层，最大活性得到提升。", "hp"),
                    Triple("代谢休眠菌群（+25营养）", "菌群化作可用营养，但记忆库也空了一块。", "gold25"),
                    Triple("触发炎症清扫", "短促高热烧尽异常序列，并留下保护性反应。", "armor")
                )
            )
            "inkwell" -> Triple(
                "细胞因子池",
                "高浓度信号分子悬在池中。它能放大技能反应，也可能触发过强炎症。",
                listOf(
                    Triple("过载应答（-25HP，技能增强）", "细胞因子涌入膜层，技能反应被大幅放大。", "skill_amp"),
                    Triple("重置信号（回满蓝，得迅捷）", "干扰信号消退，只剩清晰的战斗节律。", "mana_cdr"),
                    Triple("封存样本（获得五行果）", "信号分子在囊泡中凝结成一枚元素样本。", "fruit_gift")
                )
            )
            "spirit_forge" -> Triple(
                "核糖体装配台",
                "装配台按照抗原序列折叠武器蛋白。屏幕提示：投入营养，输出一种强化。",
                listOf(
                    Triple("投入30营养强化武器", "新蛋白包覆刃口，旧缺口都变成更稳定的结构。", "forge_weapon"),
                    Triple("投入25营养装配新武器", "装配台吐出一件仍带生物荧光的新武器。", "mystery_weapon"),
                    Triple("分解装配台余料（+25营养）", "可回收片段化作营养，装配台随即进入休眠。", "gold25")
                )
            )
            else -> Triple(
                "受体低语",
                "一枚游离受体反复发送同一段微弱信号。",
                listOf(Triple("接收信号（+15营养）", "信号转化为一小份可用营养。", "gold15"))
            )
        }
    }

    fun combatTaunt(type: NodeType, wave: Int, total: Int): String? {
        if (type != NodeType.BOSS && type != NodeType.ELITE) {
            return if (wave == 0) listOf(
                "抗原锁定，病原体正在聚集。",
                "炎症信号升高——开战。",
                "细胞膜承压。准备吞噬。"
            ).random()
            else if (wave + 1 >= total) "最后一波。完成净化。" else "增殖还在继续……稳住防线。"
        }
        return when {
            type == NodeType.BOSS && wave == 0 -> listOf(
                "感染核心识别出了你的抗体。",
                "高浓度病原体信号正在成形。",
                "器官指标骤降——感染核心出现。"
            ).random()
            type == NodeType.BOSS -> "变异信号增强。它正在改变攻击方式。"
            else -> "耐药菌膜反光——正面攻击会被削弱。"
        }
    }

    fun journalLine(stageTitle: String, nodeName: String, won: Boolean): String {
        return if (won) "「$stageTitle」·$nodeName 已净化。器官指标恢复。"
        else "「$stageTitle」·$nodeName 失守。感染正在继续扩散。"
    }
}
