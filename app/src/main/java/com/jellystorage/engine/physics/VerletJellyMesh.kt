package com.jellystorage.engine.physics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Closed-loop 2D Verlet soft-body jelly mesh.
 *
 * Storage is primitive-array only for hot-path physics:
 * - [posX]/[posY]: current node positions (outer 0..outerCount-1, center at [centerIndex])
 * - [prevX]/[prevY]: previous positions for Verlet integration
 * - constraint pairs as parallel IntArrays (indexA, indexB) + rest lengths
 *
 * No commercial physics engines; pure CPU math.
 */
class VerletJellyMesh(
    val outerCount: Int = 12,
    radius: Float = 48f,
    centerX: Float = 0f,
    centerY: Float = 0f
) {
    init {
        require(outerCount >= 8) { "outerCount must be >= 8, was $outerCount" }
        require(radius > 0f) { "radius must be > 0" }
    }

    /** Total nodes = outer perimeter + 1 center anchor. */
    val nodeCount: Int = outerCount + 1
    val centerIndex: Int = outerCount

    val posX = FloatArray(nodeCount)
    val posY = FloatArray(nodeCount)
    val prevX = FloatArray(nodeCount)
    val prevY = FloatArray(nodeCount)

    /** Scratch for impact / constraint (no per-frame alloc). */
    private val scratchDx = FloatArray(nodeCount)
    private val scratchDy = FloatArray(nodeCount)

    private var constraintCount = 0
    private lateinit var consA: IntArray
    private lateinit var consB: IntArray
    private lateinit var consRest: FloatArray

    private val invMassOuter = 1f
    private val invMassCenter = 0.35f

    private val iterations = 4
    private val damping = 0.995f

    init {
        resetShape(radius, centerX, centerY)
        buildConstraints()
    }

    fun resetShape(radius: Float, centerX: Float, centerY: Float) {
        val step = (2.0 * PI / outerCount).toFloat()
        for (i in 0 until outerCount) {
            val a = step * i
            val x = centerX + cos(a) * radius
            val y = centerY + sin(a) * radius
            posX[i] = x
            posY[i] = y
            prevX[i] = x
            prevY[i] = y
        }
        posX[centerIndex] = centerX
        posY[centerIndex] = centerY
        prevX[centerIndex] = centerX
        prevY[centerIndex] = centerY
    }

    private fun buildConstraints() {
        // Adjacent outer ring (closed) + spoke to center + every-2 cross braces + diameters
        // upper bound: outer + outer + outer + outer/2
        val estimated = outerCount * 4 + 8
        val aList = IntArray(estimated)
        val bList = IntArray(estimated)
        val rList = FloatArray(estimated)
        var n = 0

        fun add(i: Int, j: Int) {
            val dx = posX[i] - posX[j]
            val dy = posY[i] - posY[j]
            val rest = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
            check(n < estimated) { "constraint buffer overflow n=$n estimated=$estimated" }
            aList[n] = i
            bList[n] = j
            rList[n] = rest
            n++
        }

        for (i in 0 until outerCount) {
            val j = (i + 1) % outerCount
            add(i, j)
        }
        for (i in 0 until outerCount) {
            add(i, centerIndex)
        }
        // Cross braces preserve volume / reduce collapse
        for (i in 0 until outerCount) {
            val j = (i + 2) % outerCount
            add(i, j)
        }
        // Optional diameter braces for even counts
        if (outerCount % 2 == 0) {
            val half = outerCount / 2
            for (i in 0 until half) {
                add(i, i + half)
            }
        }

        constraintCount = n
        consA = aList.copyOf(n)
        consB = bList.copyOf(n)
        consRest = rList.copyOf(n)
    }

    fun getCenterX(): Float = posX[centerIndex]
    fun getCenterY(): Float = posY[centerIndex]

    fun getConstraintCount(): Int = constraintCount

    /**
     * Verlet integration step with gravity, then distance constraint projection.
     */
    fun update(deltaTime: Float, gravityX: Float, gravityY: Float) {
        val dt = deltaTime.coerceIn(0.0001f, 0.05f)
        val dt2 = dt * dt

        // Integrate outer nodes
        for (i in 0 until outerCount) {
            val x = posX[i]
            val y = posY[i]
            val vx = (x - prevX[i]) * damping
            val vy = (y - prevY[i]) * damping
            prevX[i] = x
            prevY[i] = y
            posX[i] = x + vx + gravityX * dt2 * invMassOuter
            posY[i] = y + vy + gravityY * dt2 * invMassOuter
        }

        // Integrate center (lighter gravity influence via invMass)
        run {
            val i = centerIndex
            val x = posX[i]
            val y = posY[i]
            val vx = (x - prevX[i]) * damping
            val vy = (y - prevY[i]) * damping
            prevX[i] = x
            prevY[i] = y
            posX[i] = x + vx + gravityX * dt2 * invMassCenter
            posY[i] = y + vy + gravityY * dt2 * invMassCenter
        }

        // Constraint solve
        for (iter in 0 until iterations) {
            for (c in 0 until constraintCount) {
                val ia = consA[c]
                val ib = consB[c]
                val rest = consRest[c]
                val ax = posX[ia]
                val ay = posY[ia]
                val bx = posX[ib]
                val by = posY[ib]
                var dx = bx - ax
                var dy = by - ay
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
                val diff = (dist - rest) / dist
                // mass-weighted correction
                val wA = if (ia == centerIndex) invMassCenter else invMassOuter
                val wB = if (ib == centerIndex) invMassCenter else invMassOuter
                val wSum = wA + wB
                if (wSum <= 0f) continue
                val corrX = dx * 0.5f * diff
                val corrY = dy * 0.5f * diff
                val sA = wA / wSum
                val sB = wB / wSum
                posX[ia] = ax + corrX * 2f * sA
                posY[ia] = ay + corrY * 2f * sA
                posX[ib] = bx - corrX * 2f * sB
                posY[ib] = by - corrY * 2f * sB
            }
        }
    }

    /**
     * Impulse impact that warps nearby outer nodes, creating a jelly jiggle.
     * Force is applied as an instantaneous position delta (Verlet-friendly).
     */
    fun applyImpact(atX: Float, atY: Float, forceX: Float, forceY: Float) {
        val forceMag = sqrt(forceX * forceX + forceY * forceY)
        if (forceMag < 1e-6f) return
        val radius = estimatedRadius() * 2.2f
        val radiusSq = radius * radius

        for (i in 0 until outerCount) {
            val dx = posX[i] - atX
            val dy = posY[i] - atY
            val d2 = dx * dx + dy * dy
            if (d2 > radiusSq) continue
            val d = sqrt(d2).coerceAtLeast(1e-3f)
            val falloff = 1f - (d / radius)
            val w = falloff * falloff
            // Push node along force, stronger when closer to impact point
            posX[i] += forceX * w * 0.08f
            posY[i] += forceY * w * 0.08f
            // Slight inward warp toward center for squash feel
            val cx = posX[centerIndex]
            val cy = posY[centerIndex]
            val toCx = cx - posX[i]
            val toCy = cy - posY[i]
            posX[i] += toCx * w * 0.04f
            posY[i] += toCy * w * 0.04f
            scratchDx[i] = w
            scratchDy[i] = d
        }

        // Nudge center slightly in force direction
        posX[centerIndex] += forceX * 0.015f
        posY[centerIndex] += forceY * 0.015f
    }

    /**
     * Translate entire mesh so center lands at (x, y). Updates prev arrays to avoid velocity spikes.
     */
    fun setCenter(x: Float, y: Float) {
        val dx = x - posX[centerIndex]
        val dy = y - posY[centerIndex]
        if (dx == 0f && dy == 0f) return
        for (i in 0 until nodeCount) {
            posX[i] += dx
            posY[i] += dy
            prevX[i] += dx
            prevY[i] += dy
        }
    }

    fun estimatedRadius(): Float {
        val cx = posX[centerIndex]
        val cy = posY[centerIndex]
        var sum = 0f
        for (i in 0 until outerCount) {
            val dx = posX[i] - cx
            val dy = posY[i] - cy
            sum += sqrt(dx * dx + dy * dy)
        }
        return (sum / outerCount).coerceAtLeast(1f)
    }

    /**
     * Copy outer ring positions into destination arrays (must be length >= outerCount).
     */
    fun copyOuterPositions(outX: FloatArray, outY: FloatArray) {
        require(outX.size >= outerCount && outY.size >= outerCount)
        for (i in 0 until outerCount) {
            outX[i] = posX[i]
            outY[i] = posY[i]
        }
    }

    /** True if every outer node has an adjacent bond to the next (closed loop). */
    fun hasClosedOuterRing(): Boolean {
        for (i in 0 until outerCount) {
            val j = (i + 1) % outerCount
            var found = false
            for (c in 0 until constraintCount) {
                val a = consA[c]
                val b = consB[c]
                if ((a == i && b == j) || (a == j && b == i)) {
                    found = true
                    break
                }
            }
            if (!found) return false
        }
        return true
    }
}
