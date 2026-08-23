package com.jellystorage.play

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/** 离屏渲染三职业低/高阶战斗形态，供改造时做真实像素级检查。 */
@RunWith(AndroidJUnit4::class)
class HeroCombatFormRenderTest {
    @Test
    fun renderCombatFormAtlas() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        GearArtAssets.initialize(context)
        val bitmap = Bitmap.createBitmap(1200, 700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap.asImageBitmap())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(1200f, 700f)) {
            drawRect(Color(0xFFF1E7CF), size = Size(1200f, 700f))
            val forms = listOf(
                listOf("a_w_leather", "w_steel", "r_wood", "b_leather"),
                listOf("a_w_flame", "w_flame", "r_blood", "b_quake"),
                listOf("a_m_robe", "m_flame", "r_wood", "b_mage"),
                listOf("a_m_crown", "m_crown", "r_thunder", "b_star"),
                listOf("a_t_cloth", "t_talisman", "r_wood", "b_tao"),
                listOf("a_t_gourd", "t_gourd", "r_penta", "b_star")
            )
            forms.forEachIndexed { index, ids ->
                val hero = when (index / 2) {
                    0 -> HeroClass.WARRIOR
                    1 -> HeroClass.MAGE
                    else -> HeroClass.TAOIST
                }
                val column = index % 2
                val row = index / 2
                val cx = 255f + column * 560f
                val cy = 145f + row * 220f
                val radius = 72f
                val skin = SkinCatalog.defaultFor(hero)
                val armor = requireNotNull(ArmorCatalog.byId(ids[0]))
                val weapon = requireNotNull(WeaponCatalog.byId(ids[1]))
                val ring = requireNotNull(RingCatalog.byId(ids[2]))
                val boots = requireNotNull(BootsCatalog.byId(ids[3]))
                drawCircle(Color(0x18000000), 92f, Offset(cx, cy + 22f))
                drawInkHero(cx, cy, radius, 1f, skin, drawPlaceholderWeapon = false, hero = hero)
                drawBattleEquipmentForm(cx, cy, radius, hero, armor, ring, boots, 1f, moving = column == 1, pulse = 1.4f)
                val weaponScale = when (hero) {
                    HeroClass.WARRIOR -> 0.96f
                    HeroClass.MAGE -> 0.78f
                    HeroClass.TAOIST -> 0.80f
                }
                drawBattleWeaponArt(cx + radius * 1.25f, cy, radius * weaponScale, weapon, 1f, if (column == 1) 0.75f else 0f)
            }
        }
        val output = File(requireNotNull(context.getExternalFilesDir(null)), "hero_combat_forms.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        check(output.length() > 20_000L)
    }
}
