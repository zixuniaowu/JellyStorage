package com.jellystorage.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural verification of alloc-free canvas patterns in shipped source.
 * Reads the real JellyArenaCanvas.kt from the module source tree.
 */
class JellyArenaCanvasStructureTest {

    private fun canvasSource(): String {
        val candidates = listOf(
            File("src/main/java/com/jellystorage/ui/canvas/JellyArenaCanvas.kt"),
            File("app/src/main/java/com/jellystorage/ui/canvas/JellyArenaCanvas.kt")
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: error("JellyArenaCanvas.kt not found from cwd=${File(".").absolutePath}")
        return f.readText()
    }

    @Test
    fun canvas_reusesPath_withRewindAndQuadTo() {
        val src = canvasSource()
        assertTrue("Must reference android.graphics.Path", src.contains("android.graphics.Path") || src.contains("import android.graphics.Path"))
        assertTrue("Must rewind/reset path", src.contains(".rewind()") || src.contains(".reset()"))
        assertTrue("Must use quadratic Bezier (quadTo)", src.contains("quadTo(") || src.contains("quadraticTo("))
        assertTrue("Must expose JellyArenaCanvas composable", src.contains("fun JellyArenaCanvas"))
    }

    @Test
    fun canvas_drawLambda_doesNotConstructNewPath() {
        val src = canvasSource()
        // Strip block comments / KDoc so documentation mentions of Path() do not count
        val codeOnly = src.replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//.*"""), "")
        assertTrue(
            "Paths must be remembered/preallocated",
            codeOnly.contains("remember { Path()")
        )
        val pathCtorMatches = Regex("""Path\s*\(\s*\)""").findAll(codeOnly).toList()
        assertTrue("Expected preallocated Path() constructors", pathCtorMatches.isNotEmpty())
        for (m in pathCtorMatches) {
            val before = codeOnly.substring(0, m.range.first)
            assertTrue(
                "Path() only inside remember preallocation",
                before.takeLast(120).contains("remember")
            )
        }
        val drawIdx = codeOnly.indexOf("drawIntoCanvas {")
        assertTrue("Expected drawIntoCanvas lambda", drawIdx >= 0)
        val helperIdx = codeOnly.indexOf("fun buildSmoothJellyPath")
        assertTrue(helperIdx > drawIdx)
        val hotDraw = codeOnly.substring(drawIdx, helperIdx)
        assertFalse(
            "Hot draw must not construct Path()",
            Regex("""Path\s*\(\s*\)""").containsMatchIn(hotDraw)
        )
    }

    @Test
    fun buildSmoothJellyPath_isInternalHelper() {
        val src = canvasSource()
        assertTrue(src.contains("fun buildSmoothJellyPath"))
    }
}
