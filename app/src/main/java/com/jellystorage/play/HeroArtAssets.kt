package com.jellystorage.play

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jellystorage.R
import kotlin.math.roundToInt

/**
 * 职业手绘立绘（AI 水墨风，512px 透明位图）。
 * 用于标题屏、图鉴套装预览、地图角色卡等展示位；
 * 战斗内软体小人保留 Canvas 物理体，不用立绘。
 */
object HeroArtAssets {
    private val resourceIds = mapOf(
        HeroClass.WARRIOR to R.drawable.hero_warrior,
        HeroClass.MAGE to R.drawable.hero_mage,
        HeroClass.TAOIST to R.drawable.hero_taoist
    )

    @Volatile
    private var resources: Resources? = null
    private val cache = mutableMapOf<HeroClass, ImageBitmap>()

    fun initialize(context: Context) {
        resources = context.applicationContext.resources
    }

    fun get(hero: HeroClass): ImageBitmap? {
        cache[hero]?.let { return it }
        val res = resources ?: return null
        val resId = resourceIds[hero] ?: return null
        return synchronized(cache) {
            cache[hero] ?: BitmapFactory.decodeResource(res, resId)?.asImageBitmap()?.also {
                cache[hero] = it
            }
        }
    }
}

/** 立绘绘制：高度按给定 size 等比缩放、底对齐 anchorY，返回是否绘制成功 */
fun DrawScope.drawHeroPortrait(
    hero: HeroClass,
    cx: Float,
    anchorY: Float,
    height: Float
): Boolean {
    val img = HeroArtAssets.get(hero) ?: return false
    val scale = height / img.height.toFloat()
    val w = img.width * scale
    drawImage(
        image = img,
        dstOffset = IntOffset((cx - w / 2f).roundToInt(), (anchorY - height).roundToInt()),
        dstSize = IntSize(w.roundToInt(), height.roundToInt()),
        filterQuality = FilterQuality.High
    )
    return true
}
