package com.jellystorage.play

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 装备图标批量渲染：把全部非传奇装备的 Canvas 配图离屏渲染为 512px PNG，
 * 存到 app 外部文件目录（/sdcard/Android/data/com.jellystorage/files/gear_render/），
 * 供 Python 水墨后处理管线生成 drawable 位图。
 *
 * 运行：./gradlew connectedDebugAndroidTest（需连真机/模拟器）
 */
@RunWith(AndroidJUnit4::class)
class GearIconRenderTest {

    @Test
    fun renderAllGearIcons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // MediaStore.Downloads：公共目录，APK 被卸载后文件仍保留（AGP 测试后自动卸载会清掉私有目录）
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val resolver = context.contentResolver

        fun writePng(key: String, bmp: Bitmap) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$key.png")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/gear_render")
            }
            val uri = resolver.insert(collection, values) ?: error("insert failed for $key")
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ?: error("stream failed for $key")
        }

        var count = 0
        val canvasSize = 512f
        val contentSize = 285f

        fun render(key: String, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
            val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp.asImageBitmap())
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, canvas, Size(canvasSize, canvasSize)
            ) {
                translate(canvasSize / 2f - contentSize / 2f, canvasSize / 2f - contentSize / 2f) {
                    draw()
                }
            }
            writePng(key, bmp)
            bmp.recycle()
            count++
        }

        // 跳过已有手绘 PNG 的传奇件（GearArtAssets 里注册过的 9 件）
        val premium = setOf(
            "w_flame", "m_crown", "t_gourd", "a_w_flame", "a_m_crown", "a_t_seal",
            "u_penta", "r_thunder", "b_star"
        )
        WeaponCatalog.all
            .filter { it.id !in premium }
            .forEach { w -> render("gear_${w.id}") { drawWeaponArt(contentSize / 2f, contentSize / 2f, contentSize, w, 0f) } }
        ArmorCatalog.all
            .filter { it.id !in premium }
            .forEach { a -> render("gear_${a.id}") { drawArmorArt(contentSize / 2f, contentSize / 2f, contentSize, a, 0f) } }
        RingCatalog.all
            .filter { it.id !in premium }
            .forEach { r -> render("gear_${r.id}") { drawAccessoryArt(contentSize / 2f, contentSize / 2f, contentSize, r, 0f) } }
        BootsCatalog.all
            .filter { it.id !in premium }
            .forEach { b -> render("gear_${b.id}") { drawAccessoryArt(contentSize / 2f, contentSize / 2f, contentSize, b, 0f) } }

        check(count >= 50) { "expected >=50 rendered icons, got $count" }
    }
}
