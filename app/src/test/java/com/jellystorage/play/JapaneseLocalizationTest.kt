package com.jellystorage.play

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseLocalizationTest {
    @After
    fun restoreLanguage() {
        GameI18n.language = GameLanguage.CHINESE
    }

    @Test
    fun chineseModeKeepsSourceAndToggleIsStable() {
        GameI18n.language = GameLanguage.CHINESE
        assertEquals("开始冒险", GameI18n.tr("开始冒险"))
        assertEquals(GameLanguage.JAPANESE, GameLanguage.CHINESE.toggled())
        assertEquals(GameLanguage.CHINESE, GameLanguage.JAPANESE.toggled())
    }

    @Test
    fun japaneseModeTranslatesDynamicText() {
        GameI18n.language = GameLanguage.JAPANESE
        assertEquals("墨絵の遠征・v1.11.0", GameI18n.tr("水墨远征 · v1.11.0"))
        assertEquals("48秒以内に一掃", GameI18n.tr("48秒内清场"))
        assertEquals("最大14コンボ", GameI18n.tr("最高14连击"))
        assertEquals("金貨不足（必要：30・所持：12）", GameI18n.tr("金币不足(需30，现有12)"))
        assertEquals("CT 5.2秒・消費MP 24", GameI18n.tr("冷却 5.2s · 耗蓝 24"))
        assertEquals("Lv4・金貨80・薬2・HP 320/400", GameI18n.tr("Lv4  金80  药2  320/400"))
        assertEquals("皮膚創傷 · 変異3", GameI18n.tr("皮肤创口 · 变异3"))
        assertEquals(
            "変異第3世代／上限7・免疫記憶「凝固バリア」（左右をタップ）",
            GameI18n.tr("变异第3代/7代 · 免疫记忆「凝血屏障」（左右点选）")
        )
        // 本机档案/口令改名后必须仍有日文映射（档/案/口/令不在防泄漏字符表内，显式锁死）
        assertEquals(
            "作成しました：ユーザー guest・パスワード pass123（スクリーンショットで保存してください）",
            GameI18n.tr("已生成：用户 guest  口令 pass123（请截图保存）")
        )
        assertEquals("アカウント guest・クリア2・遠征3・撃破40", GameI18n.tr("档案 guest  通关2 出征3 击杀40"))
        assertEquals("パスワード pass123（端末内保存・スクリーンショット可）", GameI18n.tr("口令 pass123（本地，可截图）"))
        assertEquals("パスワード", GameI18n.tr("口令"))
        assertEquals("登録済みですか？ ログイン", GameI18n.tr("已有档案？去登录"))
        assertEquals("アカウントは端末内保存・広告収益で無料運営", GameI18n.tr("档案仅本机保存 · 广告支持免费运营"))
        assertEquals("パスワードは4文字以上必要です", GameI18n.tr("口令至少4位"))
        assertEquals("パスワードが正しくありません", GameI18n.tr("口令不正确"))
    }

    @Test
    fun releaseContentHasNoSimplifiedChineseLeakage() {
        GameI18n.language = GameLanguage.JAPANESE
        val source = mutableListOf<String>()

        HeroClass.entries.forEach { hero ->
            source += hero.displayName
            source += hero.desc
            skillsFor(hero).forEach { source += listOf(it.name, it.glyph, it.tip) }
            weaponUpgradeTable(hero).forEach { source += it.name }
            StoryBook.heroGreeting(hero).forEach { source += listOf(it.speaker, it.line) }
            StoryBook.ending(hero, 37, 8).forEach { source += listOf(it.speaker, it.line) }
        }
        PassiveId.entries.forEach { source += listOf(it.title, it.desc) }
        armorUpgradeTable().forEach { source += it.name }
        EnemyKind.entries.forEach { source += it.displayName() }

        stageDefs().forEach { stage ->
            source += listOf(stage.title, stage.tip)
            stage.nodes.forEach { node ->
                source += listOf(node.name, node.blurb, nodeRiskTag(node.type), nodeRiskHint(node), nodeRecommendLine(node))
                StoryBook.nodeEnter(stage.id, node.name, node.type).forEach { source += listOf(it.speaker, it.line) }
            }
        }

        source += StoryBook.worldPremise.flatMap { listOf(it.speaker, it.line) }
        StoryBook.chapters.forEach { chapter ->
            source += listOf(chapter.title, chapter.subtitle, chapter.worldNote)
            (chapter.intro + chapter.clear).forEach { source += listOf(it.speaker, it.line) }
        }
        (1..5).forEach { stage ->
            (StoryBook.bossIntro(stage) + StoryBook.bossDefeat(stage)).forEach { source += listOf(it.speaker, it.line) }
        }
        listOf("merchant", "stele", "bard", "archive", "inkwell", "spirit_forge", "other").forEach { id ->
            val event = StoryBook.eventScript(id)
            source += event.first
            source += event.second
            event.third.forEach { source += listOf(it.first, it.second) }
        }
        source += listOf(
            StoryBook.journalLine("果冻草原", "史莱姆窝", true),
            StoryBook.journalLine("哭泣矿道", "矿脉魔", false),
            "第 3 波", "波2/5", "墨阶3/5", "陷阱-45", "荐火克金",
            "已生成：用户 guest  口令 pass123（请截图保存）",
            "档案 guest  通关2 出征3 击杀40",
            "口令 pass123（本地，可截图）",
            "出征 3  通关 2  最高章 4",
            "累计金 4143  击杀 396",
            "音效  开",
            "战斗震动  关",
            "生涯击杀 40 · 通关 2 · 最佳章 4",
            "金币不足(需30 现有12)",
            "30金 · 点买",
            "使用火之果：下一战火属性·攻+6",
            "使用木之果：回复生命",
            "下一木关(史莱姆窝) · 荐买金系 · 金克木",
            "仅战士可装备「精钢长刀」",
            "购入 精钢长刀 −28金",
            "购入戒指 木纹指环 −18金",
            "清场装备:精钢长刀",
            "清场防具:猎兵皮甲",
            "奖励 +14金 +10经验",
            "疾书阵·达成!",
            "连墨阵·未达成",
            "墨气狂涌! x12",
            "战意x4",
            "暴击 128",
            "战 荐金·灵涌阵",
            "广"
        )

        WeaponCatalog.all.forEach { source += listOf(it.name, it.flavor, it.proc.title, it.proc.tip, it.statsCompact()) }
        ArmorCatalog.all.forEach { source += listOf(it.name, it.flavor, it.statsCompact()) }
        RingCatalog.all.plus(BootsCatalog.all).forEach { source += listOf(it.name, it.flavor, it.statsCompact()) }
        SetCatalog.all.forEach { source += listOf(it.name, it.bonusTitle, it.bonusTip, it.piecesLine()) }
        TomeCatalog.all.forEach { source += listOf(it.name, it.tip) }
        ItemCatalog.all.forEach { source += listOf(it.name, it.tip) }
        CoreInkId.entries.forEach { source += listOf(it.title, it.glyph, it.skillName, it.desc) }
        BossMove.entries.forEach { source += it.title }
        BossEncounter.entries.forEach { source += listOf(it.title, it.phaseOne, it.phaseTwo) }
        RoomTrial.entries.forEach { source += listOf(it.title, it.rule, it.objective(3), it.progress(3, 12f, 9, 4, 15f, 100f)) }
        EnemySignatureAttack.entries.forEach { source += it.title }
        (0..4).forEach { chapter ->
            (0..2).forEach { node ->
                arenaEnvironmentFor(chapter, node, NodeType.MOB).let { source += listOf(it.title, it.rule) }
            }
        }

        val forbidden = "这还进关战级伤术书买卖门为击药过开选备录设验钱边轻续觉线敌护冻烧远归风云龙马兽鸟们个让从该仅则处发后东块条叶时气长动头师灵压锁图层净强满稳临场终势义险铁矿饮鸣纹习广荐"
            .toSet()
        val leaked = source.distinct().map { it to GameI18n.tr(it) }
            .filter { (_, translated) -> translated.any { it in forbidden } }

        assertTrue(
            "Japanese output still contains simplified Chinese:\n" +
                leaked.joinToString("\n") { (zh, ja) -> "$zh -> $ja" },
            leaked.isEmpty()
        )
    }

    @Test
    fun playerFacingSourceLiteralsHaveNoSimplifiedChineseLeakage() {
        GameI18n.language = GameLanguage.JAPANESE
        val relative = "src/main/java/com/jellystorage/play"
        val sourceDir = sequenceOf(File(relative), File("app/$relative"))
            .firstOrNull(File::isDirectory)
            ?: error("Cannot find play source directory from ${File(".").absolutePath}")
        val files = listOf(
            "PlayScreen.kt", "LoginPanel.kt", "ArenaSim.kt", "RunMeta.kt", "InkAffix.kt",
            "ProgressStore.kt", "ClassDefs.kt", "StoryData.kt", "LootSystem.kt", "GearSets.kt",
            "GearAccessories.kt", "CoreInk.kt", "BossMechanics.kt", "RoomTrial.kt", "ArenaEnvironment.kt"
        )
        val literal = Regex("\\\"([^\\\"\\r\\n]*(?:\\\\.[^\\\"\\r\\n]*)*)\\\"")
        val forbidden = "这还进关战级伤术书买卖门为击药过开选备录设验钱边轻续觉线敌护冻烧远归风云龙马兽鸟们个让从该仅则处发后东块条叶时气长动头师灵压锁图层净强满稳临场终势义险铁矿饮鸣纹习广荐"
            .toSet()
        val leaked = files.flatMap { name ->
            literal.findAll(File(sourceDir, name).readText()).map { match ->
                match.groupValues[1].replace("\\n", "\n")
            }.filter { text -> '$' !in text && text.any { it in '\u4e00'..'\u9fff' } }.toList()
        }.distinct().map { it to GameI18n.tr(it) }
            .filter { (_, translated) -> translated.any { it in forbidden } }

        assertTrue(
            "Japanese rendering still leaks simplified Chinese literals:\n" +
                leaked.joinToString("\n") { (zh, ja) -> "$zh -> $ja" },
            leaked.isEmpty()
        )
    }
}
