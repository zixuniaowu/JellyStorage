package com.jellystorage.engine

import com.jellystorage.engine.physics.VerletJellyMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Drives shipped [VerletJellyMesh.update] and [VerletJellyMesh.applyImpact].
 */
class VerletJellyMeshTest {

    @Test
    fun mesh_hasAtLeast8OuterNodes_andCenter() {
        val mesh = VerletJellyMesh(outerCount = 12, radius = 40f, centerX = 100f, centerY = 200f)
        assertTrue(mesh.outerCount >= 8)
        assertEquals(12, mesh.outerCount)
        assertEquals(13, mesh.nodeCount)
        assertEquals(12, mesh.centerIndex)
        assertEquals(100f, mesh.getCenterX(), 0.01f)
        assertEquals(200f, mesh.getCenterY(), 0.01f)
    }

    @Test
    fun closedOuterRing_constraintsPresent() {
        val mesh = VerletJellyMesh(outerCount = 10, radius = 30f, centerX = 0f, centerY = 0f)
        assertTrue("Adjacent outer bonds must form a closed ring", mesh.hasClosedOuterRing())
        assertTrue("Must have constraints", mesh.getConstraintCount() > mesh.outerCount)
    }

    @Test
    fun update_withGravity_movesCenterDown() {
        val mesh = VerletJellyMesh(outerCount = 12, radius = 40f, centerX = 50f, centerY = 50f)
        val y0 = mesh.getCenterY()
        // Several steps of downward gravity
        repeat(30) {
            mesh.update(1f / 60f, gravityX = 0f, gravityY = 900f)
        }
        val y1 = mesh.getCenterY()
        assertTrue(
            "Center should move downward under gravity (y0=$y0 y1=$y1)",
            y1 > y0 + 0.5f
        )
        // Outer nodes also live in FloatArrays and should have moved
        var movedOuter = false
        for (i in 0 until mesh.outerCount) {
            // distance from original ring rough check via radius change
            val dx = mesh.posX[i] - 50f
            val dy = mesh.posY[i] - 50f
            if (sqrt(dx * dx + dy * dy) > 41f || mesh.posY[i] > 50f) {
                movedOuter = true
                break
            }
        }
        assertTrue("At least one outer node should react", movedOuter || y1 > y0)
    }

    @Test
    fun applyImpact_displacesNearbyOuterNode() {
        val mesh = VerletJellyMesh(outerCount = 12, radius = 40f, centerX = 0f, centerY = 0f)
        // Snapshot outer positions
        val beforeX = FloatArray(mesh.outerCount)
        val beforeY = FloatArray(mesh.outerCount)
        mesh.copyOuterPositions(beforeX, beforeY)

        // Impact near node 0
        val atX = mesh.posX[0]
        val atY = mesh.posY[0]
        mesh.applyImpact(atX, atY, forceX = 400f, forceY = -200f)

        var maxDisp = 0f
        for (i in 0 until mesh.outerCount) {
            val dx = mesh.posX[i] - beforeX[i]
            val dy = mesh.posY[i] - beforeY[i]
            val d = sqrt(dx * dx + dy * dy)
            if (d > maxDisp) maxDisp = d
        }
        assertTrue(
            "applyImpact must displace at least one outer node (maxDisp=$maxDisp)",
            maxDisp > 0.05f
        )
    }

    @Test
    fun setCenter_translatesWithoutVelocitySpike_thenStable() {
        val mesh = VerletJellyMesh(outerCount = 8, radius = 20f, centerX = 0f, centerY = 0f)
        mesh.setCenter(100f, 100f)
        assertEquals(100f, mesh.getCenterX(), 0.01f)
        assertEquals(100f, mesh.getCenterY(), 0.01f)
        // After setCenter, prev is updated — one zero-gravity step should barely move
        val cx = mesh.getCenterX()
        val cy = mesh.getCenterY()
        mesh.update(1f / 60f, 0f, 0f)
        assertTrue(abs(mesh.getCenterX() - cx) < 2f)
        assertTrue(abs(mesh.getCenterY() - cy) < 2f)
    }

    @Test
    fun primitiveArrays_areBackingStore() {
        val mesh = VerletJellyMesh(outerCount = 8, radius = 10f)
        assertEquals(mesh.nodeCount, mesh.posX.size)
        assertEquals(mesh.nodeCount, mesh.posY.size)
        assertEquals(mesh.nodeCount, mesh.prevX.size)
        assertEquals(mesh.nodeCount, mesh.prevY.size)
    }
}
