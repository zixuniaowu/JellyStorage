package com.jellystorage.softbody

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

data class PhysicsParams(
    val stiffness: Float = 4f,
    val edgeStiffness: Float = 3f,
    val damping: Float = 1.8f,
    val pressure: Float = 3f
)

data class BoundaryVertex(
    val relativePosition: Offset,
    val absolutePosition: Offset,
    val velocity: Offset
)

data class SoftBodyObject(
    val center: Offset,
    val velocity: Offset,
    val boundaryVertices: List<BoundaryVertex>,
    val restArea: Float
) {
    companion object {
        fun create(
            center: Offset = Offset(200f, 300f),
            numVertices: Int = 12,
            radius: Float = 60f
        ): SoftBodyObject {
            val angleStep = (2f * PI.toFloat()) / numVertices
            val vertices = List(numVertices) { i ->
                val angle = angleStep * i
                val relPos = Offset(cos(angle) * radius, sin(angle) * radius)
                BoundaryVertex(
                    relativePosition = relPos,
                    absolutePosition = center + relPos,
                    velocity = Offset.Zero
                )
            }
            return SoftBodyObject(
                center = center,
                velocity = Offset.Zero,
                boundaryVertices = vertices,
                restArea = polygonArea(vertices)
            )
        }

        private fun polygonArea(vertices: List<BoundaryVertex>): Float {
            val n = vertices.size
            var area = 0f
            for (i in 0 until n) {
                val j = (i + 1) % n
                area += vertices[i].absolutePosition.x * vertices[j].absolutePosition.y
                area -= vertices[j].absolutePosition.x * vertices[i].absolutePosition.y
            }
            return abs(area) * 0.5f
        }
    }

    fun update(dt: Float, physics: PhysicsParams = PhysicsParams(), isDragging: Boolean = false): SoftBodyObject {
        val clampedDt = dt.coerceIn(0f, 0.05f)
        val n = boundaryVertices.size
        if (n < 3) return this

        val forces = MutableList(n) { Offset.Zero }

        for (i in 0 until n) {
            val v = boundaryVertices[i]
            val diff = v.absolutePosition - center
            val dist = diff.getDistance()
            if (dist < 1e-4f) continue
            val dir = diff / dist
            val restDist = v.relativePosition.getDistance()
            val displacement = dist - restDist
            forces[i] -= dir * physics.stiffness * displacement
        }

        for (i in 0 until n) {
            val j = (i + 1) % n
            val vi = boundaryVertices[i]
            val vj = boundaryVertices[j]
            val diff = vi.absolutePosition - vj.absolutePosition
            val dist = diff.getDistance()
            if (dist < 1e-4f) continue
            val dir = diff / dist
            val restDist = (vi.relativePosition - vj.relativePosition).getDistance()
            val displacement = dist - restDist
            val f = dir * physics.edgeStiffness * displacement
            forces[i] -= f
            forces[j] += f
        }

        for (i in 0 until n) {
            forces[i] -= boundaryVertices[i].velocity * physics.damping
        }

        val area = polygonArea()
        if (restArea > 0.001f && area > 0.001f) {
            val areaRatio = area / restArea
            val pMag = physics.pressure * (1f - areaRatio)
            for (i in 0 until n) {
                val diff = boundaryVertices[i].absolutePosition - center
                val dist = diff.getDistance()
                if (dist < 1e-4f) continue
                forces[i] += (diff / dist) * pMag
            }
        }

        val newVertices = boundaryVertices.mapIndexed { i, v ->
            val newVel = (v.velocity + forces[i] * clampedDt) * 0.995f
            val newPos = v.absolutePosition + newVel * clampedDt
            v.copy(velocity = newVel, absolutePosition = newPos)
        }

        return if (isDragging) {
            copy(boundaryVertices = newVertices)
        } else {
            val newCenter = center + velocity * clampedDt
            val newCenterVel = velocity * 0.88f
            copy(center = newCenter, velocity = newCenterVel, boundaryVertices = newVertices)
        }
    }

    fun constrainToBounds(width: Float, height: Float): SoftBodyObject {
        val newVertices = boundaryVertices.map { v ->
            var pos = v.absolutePosition
            var vel = v.velocity
            if (pos.x < 0f) {
                pos = pos.copy(x = 0f)
                vel = vel.copy(x = -vel.x * 0.3f)
            }
            if (pos.x > width) {
                pos = pos.copy(x = width)
                vel = vel.copy(x = -vel.x * 0.3f)
            }
            if (pos.y < 0f) {
                pos = pos.copy(y = 0f)
                vel = vel.copy(y = -vel.y * 0.3f)
            }
            if (pos.y > height) {
                pos = pos.copy(y = height)
                vel = vel.copy(y = -vel.y * 0.3f)
            }
            v.copy(absolutePosition = pos, velocity = vel)
        }
        val cx = center.x.coerceIn(0f, width)
        val cy = center.y.coerceIn(0f, height)
        return copy(center = Offset(cx, cy), boundaryVertices = newVertices)
    }

    fun applyDrag(target: Offset): SoftBodyObject {
        return copy(
            center = target,
            velocity = (target - center) * 2f
        )
    }

    fun applyImpulse(impulse: Offset): SoftBodyObject {
        return copy(velocity = velocity + impulse)
    }

    fun withVelocity(v: Offset): SoftBodyObject = copy(velocity = v)

    /** Approximate rest radius from relative vertices. */
    fun restRadius(): Float {
        if (boundaryVertices.isEmpty()) return 0f
        var sum = 0f
        for (v in boundaryVertices) sum += v.relativePosition.getDistance()
        return sum / boundaryVertices.size
    }

    /** Current deformed radius (avg distance of verts from center). */
    fun currentRadius(): Float {
        if (boundaryVertices.isEmpty()) return 0f
        var sum = 0f
        for (v in boundaryVertices) sum += (v.absolutePosition - center).getDistance()
        return sum / boundaryVertices.size
    }

    fun containsPoint(point: Offset): Boolean {
        var inside = false
        var j = boundaryVertices.size - 1
        for (i in boundaryVertices.indices) {
            val vi = boundaryVertices[i].absolutePosition
            val vj = boundaryVertices[j].absolutePosition
            if ((vi.y > point.y) != (vj.y > point.y) &&
                point.x < (vj.x - vi.x) * (point.y - vi.y) / (vj.y - vi.y) + vi.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    val boundaryPositions: List<Offset>
        get() = boundaryVertices.map { it.absolutePosition }

    private fun polygonArea(): Float {
        val n = boundaryVertices.size
        var area = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += boundaryVertices[i].absolutePosition.x * boundaryVertices[j].absolutePosition.y
            area -= boundaryVertices[j].absolutePosition.x * boundaryVertices[i].absolutePosition.y
        }
        return abs(area) * 0.5f
    }
}
