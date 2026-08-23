package com.jellystorage.play

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jellystorage.R

/**
 * 装备位图注册表：全部装备使用 512×512 位图。
 * 传奇 9 件为 AI 手绘；其余 52 件由真机离屏渲染 Canvas 画稿后
 * 经 tools/ink_postprocess.py 水墨化（宣纸纹理/墨晕/飞白）生成。
 */
object GearArtAssets {
    private val resourceIds = mapOf(
        "a_cloth" to R.drawable.gear_a_cloth,
        "a_m_abyss" to R.drawable.gear_a_m_abyss,
        "a_m_crown" to R.drawable.gear_a_m_crown,
        "a_m_frost" to R.drawable.gear_a_m_frost,
        "a_m_robe" to R.drawable.gear_a_m_robe,
        "a_m_storm" to R.drawable.gear_a_m_storm,
        "a_t_cloth" to R.drawable.gear_a_t_cloth,
        "a_t_gourd" to R.drawable.gear_a_t_gourd,
        "a_t_jade" to R.drawable.gear_a_t_jade,
        "a_t_poison" to R.drawable.gear_a_t_poison,
        "a_t_seal" to R.drawable.gear_a_t_seal,
        "a_u_chain" to R.drawable.gear_a_u_chain,
        "a_u_scale" to R.drawable.gear_a_u_scale,
        "a_w_blood" to R.drawable.gear_a_w_blood,
        "a_w_flame" to R.drawable.gear_a_w_flame,
        "a_w_iron" to R.drawable.gear_a_w_iron,
        "a_w_leather" to R.drawable.gear_a_w_leather,
        "a_w_quake" to R.drawable.gear_a_w_quake,
        "b_cloth" to R.drawable.gear_b_cloth,
        "b_iron" to R.drawable.gear_b_iron,
        "b_leather" to R.drawable.gear_b_leather,
        "b_mage" to R.drawable.gear_b_mage,
        "b_quake" to R.drawable.gear_b_quake,
        "b_star" to R.drawable.gear_b_star,
        "b_tao" to R.drawable.gear_b_tao,
        "b_wind" to R.drawable.gear_b_wind,
        "m_abyss" to R.drawable.gear_m_abyss,
        "m_crown" to R.drawable.gear_m_crown,
        "m_earth_tome" to R.drawable.gear_m_earth_tome,
        "m_flame" to R.drawable.gear_m_flame,
        "m_ice" to R.drawable.gear_m_ice,
        "m_thunder" to R.drawable.gear_m_thunder,
        "m_wood_orb" to R.drawable.gear_m_wood_orb,
        "r_blood" to R.drawable.gear_r_blood,
        "r_ice" to R.drawable.gear_r_ice,
        "r_jade" to R.drawable.gear_r_jade,
        "r_penta" to R.drawable.gear_r_penta,
        "r_spark" to R.drawable.gear_r_spark,
        "r_thunder" to R.drawable.gear_r_thunder,
        "r_wood" to R.drawable.gear_r_wood,
        "t_dust" to R.drawable.gear_t_dust,
        "t_fire_charm" to R.drawable.gear_t_fire_charm,
        "t_gourd" to R.drawable.gear_t_gourd,
        "t_jade" to R.drawable.gear_t_jade,
        "t_poison_bell" to R.drawable.gear_t_poison_bell,
        "t_seal" to R.drawable.gear_t_seal,
        "t_talisman" to R.drawable.gear_t_talisman,
        "u_earth_hammer" to R.drawable.gear_u_earth_hammer,
        "u_penta" to R.drawable.gear_u_penta,
        "u_water_blade" to R.drawable.gear_u_water_blade,
        "u_wood_bow" to R.drawable.gear_u_wood_bow,
        "w_blood" to R.drawable.gear_w_blood,
        "w_flame" to R.drawable.gear_w_flame,
        "w_gold_spear" to R.drawable.gear_w_gold_spear,
        "w_guard_blade" to R.drawable.gear_w_guard_blade,
        "w_iron" to R.drawable.gear_w_iron,
        "w_peach" to R.drawable.gear_w_peach,
        "w_quake" to R.drawable.gear_w_quake,
        "w_staff" to R.drawable.gear_w_staff,
        "w_steel" to R.drawable.gear_w_steel,
        "w_thund_edge" to R.drawable.gear_w_thund_edge
    )

    @Volatile
    private var resources: Resources? = null

    // 双档缓存：小尺寸场景（战斗掉落/HUD/列表）用 128px 缩略图，解码快、内存小；
    // 大图预览用 512px 原图。LRU 逐条淘汰，不再整仓清空（避免战斗中反复重解码掉帧）。
    private val fullCache = object : LinkedHashMap<String, ImageBitmap>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
            size > 20
    }
    private val thumbCache = object : LinkedHashMap<String, ImageBitmap>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
            size > 72
    }

    fun initialize(context: Context) {
        resources = context.applicationContext.resources
    }

    /** 完整 512px 位图（大图预览用） */
    fun get(id: String): ImageBitmap? = decode(fullCache, id, 1)

    /** 128px 缩略图（≤120px 展示位用：掉落/HUD/列表），首次解码约 1-3ms */
    fun getThumb(id: String): ImageBitmap? = decode(thumbCache, id, 4)

    private fun decode(
        cache: LinkedHashMap<String, ImageBitmap>,
        id: String,
        sample: Int
    ): ImageBitmap? {
        synchronized(cache) { cache[id]?.let { return it } }
        val res = resources ?: return null
        val resId = resourceIds[id] ?: return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = try {
            BitmapFactory.decodeResource(res, resId, opts)?.asImageBitmap()
        } catch (_: Throwable) {
            null
        } ?: return null
        synchronized(cache) { cache[id] = bmp }
        return bmp
    }
}
