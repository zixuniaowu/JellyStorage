package com.jellystorage.engine.battle

import com.jellystorage.engine.model.EffectType
import com.jellystorage.engine.model.SynergyEffect
import com.jellystorage.engine.model.Weapon
import com.jellystorage.engine.model.evaluateSynergies
import com.jellystorage.engine.physics.VerletJellyMesh
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class BattleStatus {
    PREPARING,
    FIGHTING,
    VICTORY,
    DEFEAT
}

enum class ProjectileType {
    NORMAL,
    FIRE,
    ICE,
    THUNDER,
    POISON,
    STEAM,
    HEAVY
}

/**
 * Immutable snapshot consumed by UI / canvas.
 * Primitive-backed collections are copied to fixed arrays for stable iteration.
 */
data class BattleViewState(
    val status: BattleStatus,
    val playerHp: Float,
    val playerMaxHp: Float,
    val enemyHp: Float,
    val enemyMaxHp: Float,
    val playerX: Float,
    val playerY: Float,
    val enemyX: Float,
    val enemyY: Float,
    val playerRadius: Float,
    val enemyRadius: Float,
    val projectileCount: Int,
    val projectileX: FloatArray,
    val projectileY: FloatArray,
    val projectileVx: FloatArray,
    val projectileVy: FloatArray,
    val projectileType: IntArray,
    val projectileFromPlayer: BooleanArray,
    val projectileRadius: FloatArray,
    val floatCount: Int,
    val floatX: FloatArray,
    val floatY: FloatArray,
    val floatLife: FloatArray,
    val floatMaxLife: FloatArray,
    val floatValue: FloatArray,
    val floatIsCrit: BooleanArray,
    val floatIsCombo: BooleanArray,
    val comboCount: Int,
    val prepareTimer: Float,
    val elapsedMillis: Long,
    val activeSynergies: List<SynergyEffect>,
    val playerOuterX: FloatArray,
    val playerOuterY: FloatArray,
    val playerOuterCount: Int,
    val enemyOuterX: FloatArray,
    val enemyOuterY: FloatArray,
    val enemyOuterCount: Int
)

/**
 * Pure Kotlin MVI battle simulator. No Compose / Android View dependencies.
 *
 * Call [tick] with elapsed milliseconds each frame; read [viewState] for rendering.
 * Projectile / float pools are fixed-capacity to avoid GC thrashing.
 */
class BattleSimulator(
    private val arenaWidth: Float = 1080f,
    private val arenaHeight: Float = 720f,
    private val playerWeapons: List<Weapon> = emptyList(),
    private val enemyBaseDamage: Float = 12f,
    private val enemyFireRate: Float = 0.9f,
    playerMaxHp: Float = 200f,
    enemyMaxHp: Float = 180f
) {
    companion object {
        const val MAX_PROJECTILES = 64
        const val MAX_FLOATS = 48
        const val PREPARE_SECONDS = 1.2f
    }

    private val synergies: List<SynergyEffect> = evaluateSynergies(playerWeapons)

    private var status: BattleStatus = BattleStatus.PREPARING
    private var elapsedMs: Long = 0L
    private var prepareLeft: Float = PREPARE_SECONDS

    private var playerHp: Float = playerMaxHp
    private val playerMax: Float = playerMaxHp
    private var enemyHp: Float = enemyMaxHp
    private val enemyMax: Float = enemyMaxHp

    private val groundY: Float = arenaHeight * 0.78f
    private val playerMesh = VerletJellyMesh(
        outerCount = 12,
        radius = min(arenaWidth, arenaHeight) * 0.07f,
        centerX = arenaWidth * 0.25f,
        centerY = groundY - min(arenaWidth, arenaHeight) * 0.07f
    )
    private val enemyMesh = VerletJellyMesh(
        outerCount = 12,
        radius = min(arenaWidth, arenaHeight) * 0.08f,
        centerX = arenaWidth * 0.75f,
        centerY = groundY - min(arenaWidth, arenaHeight) * 0.08f
    )

    private var playerDef: Float = 6f
    private var enemyDef: Float = 5f
    private var damageScale: Float = 1f
    private var armorPierce: Float = 0f
    private var thornsFraction: Float = 0f
    private var critChance: Float = 0.08f
    private var lifesteal: Float = 0f
    private var multiShot: Int = 0
    private var burnDps: Float = 0f

    private val weaponTimers = FloatArray(max(1, playerWeapons.size))
    private var enemyFireTimer: Float = 0.5f

    // Projectile pool
    private var projCount = 0
    private val pX = FloatArray(MAX_PROJECTILES)
    private val pY = FloatArray(MAX_PROJECTILES)
    private val pVx = FloatArray(MAX_PROJECTILES)
    private val pVy = FloatArray(MAX_PROJECTILES)
    private val pType = IntArray(MAX_PROJECTILES)
    private val pFromPlayer = BooleanArray(MAX_PROJECTILES)
    private val pRadius = FloatArray(MAX_PROJECTILES)
    private val pDamage = FloatArray(MAX_PROJECTILES)
    private val pLife = FloatArray(MAX_PROJECTILES)
    private val pPierce = IntArray(MAX_PROJECTILES)
    /** True after a successful hit until projectile leaves the target radius (prevents multi-tick re-damage). */
    private val pLatched = BooleanArray(MAX_PROJECTILES)

    // Floating combat text pool
    private var floatCount = 0
    private val fX = FloatArray(MAX_FLOATS)
    private val fY = FloatArray(MAX_FLOATS)
    private val fLife = FloatArray(MAX_FLOATS)
    private val fMaxLife = FloatArray(MAX_FLOATS)
    private val fValue = FloatArray(MAX_FLOATS)
    private val fIsCrit = BooleanArray(MAX_FLOATS)
    private val fIsCombo = BooleanArray(MAX_FLOATS)

    private var combo = 0
    private var comboTimer = 0f
    private var prng = 1L

    // Snapshot buffers (reused — viewState arrays point here after tick)
    private val snapPX = FloatArray(MAX_PROJECTILES)
    private val snapPY = FloatArray(MAX_PROJECTILES)
    private val snapPVx = FloatArray(MAX_PROJECTILES)
    private val snapPVy = FloatArray(MAX_PROJECTILES)
    private val snapPType = IntArray(MAX_PROJECTILES)
    private val snapPFrom = BooleanArray(MAX_PROJECTILES)
    private val snapPRad = FloatArray(MAX_PROJECTILES)

    private val snapFX = FloatArray(MAX_FLOATS)
    private val snapFY = FloatArray(MAX_FLOATS)
    private val snapFLife = FloatArray(MAX_FLOATS)
    private val snapFMax = FloatArray(MAX_FLOATS)
    private val snapFVal = FloatArray(MAX_FLOATS)
    private val snapFCrit = BooleanArray(MAX_FLOATS)
    private val snapFCombo = BooleanArray(MAX_FLOATS)

    private val snapPlayerOuterX = FloatArray(16)
    private val snapPlayerOuterY = FloatArray(16)
    private val snapEnemyOuterX = FloatArray(16)
    private val snapEnemyOuterY = FloatArray(16)

    private var cachedView: BattleViewState = buildViewState()

    init {
        applySynergyStats()
        for (i in playerWeapons.indices) {
            weaponTimers[i] = 0.15f + i * 0.05f
        }
        cachedView = buildViewState()
    }

    val viewState: BattleViewState
        get() = cachedView

    fun getStatus(): BattleStatus = status

    private fun applySynergyStats() {
        for (e in synergies) {
            when (e.effectType) {
                EffectType.DAMAGE_SCALE -> damageScale *= e.multiplier
                EffectType.ARMOR_PIERCE -> armorPierce = max(armorPierce, 0.45f * e.multiplier)
                EffectType.THORNS -> thornsFraction = max(thornsFraction, e.multiplier)
                EffectType.CRIT_CHANCE -> critChance += e.multiplier
                EffectType.LIFESTEAL -> lifesteal += e.multiplier
                EffectType.MULTI_SHOT -> multiShot += e.multiplier.toInt().coerceAtLeast(1)
                EffectType.BURN_DOT -> burnDps += e.multiplier
                EffectType.DEFENSE_SCALE -> playerDef *= e.multiplier
            }
        }
        critChance = critChance.coerceIn(0f, 0.75f)
        lifesteal = lifesteal.coerceIn(0f, 0.6f)
        thornsFraction = thornsFraction.coerceIn(0f, 0.7f)
    }

    /**
     * Advance simulation by [elapsedMillis] milliseconds.
     * Safe to call every frame; clamps huge gaps to avoid explosions.
     */
    fun tick(elapsedMillis: Long) {
        if (status == BattleStatus.VICTORY || status == BattleStatus.DEFEAT) {
            // still fade floats / soft physics for visual outro
            val dt = (elapsedMillis.coerceIn(0L, 50L) / 1000f)
            updateFloats(dt)
            playerMesh.update(dt, 0f, 0f)
            enemyMesh.update(dt, 0f, 0f)
            cachedView = buildViewState()
            return
        }

        val ms = elapsedMillis.coerceIn(0L, 50L)
        elapsedMs += ms
        val dt = ms / 1000f

        when (status) {
            BattleStatus.PREPARING -> {
                prepareLeft -= dt
                // gentle idle physics
                playerMesh.update(dt, 0f, 40f)
                enemyMesh.update(dt, 0f, 40f)
                if (prepareLeft <= 0f) {
                    status = BattleStatus.FIGHTING
                    // Banner-style float must NOT use (combo + zero value) — that is reserved for K.O.
                    spawnFloat(
                        (playerMesh.getCenterX() + enemyMesh.getCenterX()) * 0.5f,
                        arenaHeight * 0.28f,
                        value = -1f,
                        isCrit = false,
                        isCombo = false,
                        life = 0.85f
                    )
                }
            }
            BattleStatus.FIGHTING -> {
                simulateFight(dt)
            }
            else -> Unit
        }

        cachedView = buildViewState()
    }

    private fun simulateFight(dt: Float) {
        // Soft-body idle sway / settle
        playerMesh.update(dt, 0f, 120f)
        enemyMesh.update(dt, 0f, 120f)

        // Keep centers on fight line
        val pTargetX = arenaWidth * 0.28f
        val eTargetX = arenaWidth * 0.72f
        val py = groundY - playerMesh.estimatedRadius()
        val ey = groundY - enemyMesh.estimatedRadius()
        softFollow(playerMesh, pTargetX, py, dt, 3.5f)
        softFollow(enemyMesh, eTargetX, ey, dt, 3.0f)

        // Burn DoT on enemy
        if (burnDps > 0f && enemyHp > 0f) {
            val dmg = burnDps * dt
            enemyHp = (enemyHp - dmg).coerceAtLeast(0f)
        }

        // Player weapon fire
        if (playerWeapons.isNotEmpty() && playerHp > 0f && enemyHp > 0f) {
            for (i in playerWeapons.indices) {
                weaponTimers[i] -= dt
                if (weaponTimers[i] <= 0f) {
                    val w = playerWeapons[i]
                    val interval = (1f / w.fireRate).coerceAtLeast(0.12f)
                    weaponTimers[i] = interval
                    firePlayerWeapon(w)
                }
            }
        }

        // Enemy fire
        if (enemyHp > 0f && playerHp > 0f) {
            enemyFireTimer -= dt
            if (enemyFireTimer <= 0f) {
                enemyFireTimer = (1f / enemyFireRate).coerceAtLeast(0.2f)
                fireEnemyShot()
            }
        }

        updateProjectiles(dt)
        updateFloats(dt)

        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 0
        }

        if (enemyHp <= 0f) {
            enemyHp = 0f
            status = BattleStatus.VICTORY
            spawnFloat(enemyMesh.getCenterX(), enemyMesh.getCenterY() - 40f, 0f, isCrit = true, isCombo = true, life = 1.2f)
        } else if (playerHp <= 0f) {
            playerHp = 0f
            status = BattleStatus.DEFEAT
            spawnFloat(playerMesh.getCenterX(), playerMesh.getCenterY() - 40f, 0f, isCrit = true, isCombo = false, life = 1.2f)
        }
    }

    private fun softFollow(mesh: VerletJellyMesh, tx: Float, ty: Float, dt: Float, speed: Float) {
        val cx = mesh.getCenterX()
        val cy = mesh.getCenterY()
        val nx = cx + (tx - cx) * (speed * dt).coerceIn(0f, 1f)
        val ny = cy + (ty - cy) * (speed * dt).coerceIn(0f, 1f)
        mesh.setCenter(nx, ny)
    }

    private fun firePlayerWeapon(w: Weapon) {
        val originX = playerMesh.getCenterX()
        val originY = playerMesh.getCenterY()
        val targetX = enemyMesh.getCenterX()
        val targetY = enemyMesh.getCenterY()
        val dx = targetX - originX
        val dy = targetY - originY
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val nx = dx / len
        val ny = dy / len
        val speed = 520f
        val shots = 1 + multiShot
        val baseDmg = w.baseDamage * damageScale

        val type = when {
            synergies.any { it.effectType == EffectType.ARMOR_PIERCE } &&
                w.tags.contains(com.jellystorage.engine.model.Tag.FIRE) -> ProjectileType.STEAM.ordinal
            w.tags.contains(com.jellystorage.engine.model.Tag.FIRE) -> ProjectileType.FIRE.ordinal
            w.tags.contains(com.jellystorage.engine.model.Tag.ICE) -> ProjectileType.ICE.ordinal
            w.tags.contains(com.jellystorage.engine.model.Tag.THUNDER) -> ProjectileType.THUNDER.ordinal
            w.tags.contains(com.jellystorage.engine.model.Tag.POISON) -> ProjectileType.POISON.ordinal
            w.tags.contains(com.jellystorage.engine.model.Tag.HEAVY) -> ProjectileType.HEAVY.ordinal
            else -> ProjectileType.NORMAL.ordinal
        }

        for (s in 0 until shots) {
            val spread = (s - (shots - 1) * 0.5f) * 0.12f
            val cosA = cosApprox(spread)
            val sinA = sinApprox(spread)
            val dirX = nx * cosA - ny * sinA
            val dirY = nx * sinA + ny * cosA
            spawnProjectile(
                originX + dirX * playerMesh.estimatedRadius(),
                originY + dirY * playerMesh.estimatedRadius() * 0.2f,
                dirX * speed,
                dirY * speed - 20f,
                type,
                fromPlayer = true,
                radius = 10f + w.baseDamage * 0.15f,
                damage = baseDmg,
                life = 2.2f,
                // Armor pierce is a damage-stat effect (see applyDamageToEnemy), not multi-frame re-hit.
                // Pierce>0 is reserved for future multi-target; keep 0 in 1v1 to avoid overlap re-damage.
                pierce = 0
            )
        }
    }

    private fun fireEnemyShot() {
        val originX = enemyMesh.getCenterX()
        val originY = enemyMesh.getCenterY()
        val targetX = playerMesh.getCenterX()
        val targetY = playerMesh.getCenterY()
        val dx = targetX - originX
        val dy = targetY - originY
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val speed = 400f
        spawnProjectile(
            originX + dx / len * enemyMesh.estimatedRadius(),
            originY,
            dx / len * speed,
            dy / len * speed * 0.3f,
            ProjectileType.NORMAL.ordinal,
            fromPlayer = false,
            radius = 12f,
            damage = enemyBaseDamage,
            life = 2.0f,
            pierce = 0
        )
    }

    private fun spawnProjectile(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        type: Int,
        fromPlayer: Boolean,
        radius: Float,
        damage: Float,
        life: Float,
        pierce: Int
    ) {
        if (projCount >= MAX_PROJECTILES) {
            // drop oldest
            removeProjectile(0)
        }
        val i = projCount
        pX[i] = x
        pY[i] = y
        pVx[i] = vx
        pVy[i] = vy
        pType[i] = type
        pFromPlayer[i] = fromPlayer
        pRadius[i] = radius
        pDamage[i] = damage
        pLife[i] = life
        pPierce[i] = pierce
        pLatched[i] = false
        projCount++
    }

    private fun removeProjectile(index: Int) {
        val last = projCount - 1
        if (index < 0 || index >= projCount) return
        if (index != last) {
            pX[index] = pX[last]
            pY[index] = pY[last]
            pVx[index] = pVx[last]
            pVy[index] = pVy[last]
            pType[index] = pType[last]
            pFromPlayer[index] = pFromPlayer[last]
            pRadius[index] = pRadius[last]
            pDamage[index] = pDamage[last]
            pLife[index] = pLife[last]
            pPierce[index] = pPierce[last]
            pLatched[index] = pLatched[last]
        }
        projCount--
    }

    private fun updateProjectiles(dt: Float) {
        var i = 0
        while (i < projCount) {
            pLife[i] -= dt
            pVy[i] += 80f * dt
            pX[i] += pVx[i] * dt
            pY[i] += pVy[i] * dt

            val hit = if (pFromPlayer[i]) {
                collideProjectileWithMesh(i, enemyMesh, againstEnemy = true)
            } else {
                collideProjectileWithMesh(i, playerMesh, againstEnemy = false)
            }

            val oob = pX[i] < -80f || pX[i] > arenaWidth + 80f ||
                pY[i] < -80f || pY[i] > arenaHeight + 80f

            if (hit) {
                // Damage applied exactly once via latch inside collide.
                // Consume projectile unless multi-target pierce remains AND we ejected clear.
                if (pPierce[i] > 0) {
                    pPierce[i]--
                    pVx[i] *= 0.85f
                    // Nudge past current target so latch can clear without re-hit stacking
                    val speed = sqrt(pVx[i] * pVx[i] + pVy[i] * pVy[i]).coerceAtLeast(1f)
                    val push = (if (pFromPlayer[i]) enemyMesh.estimatedRadius() else playerMesh.estimatedRadius()) +
                        pRadius[i] + 4f
                    pX[i] += (pVx[i] / speed) * push
                    pY[i] += (pVy[i] / speed) * push
                    pLatched[i] = false
                    i++
                } else {
                    removeProjectile(i)
                }
            } else if (pLife[i] <= 0f || oob) {
                removeProjectile(i)
            } else {
                i++
            }
        }
    }

    private fun collideProjectileWithMesh(index: Int, mesh: VerletJellyMesh, againstEnemy: Boolean): Boolean {
        val cx = mesh.getCenterX()
        val cy = mesh.getCenterY()
        val r = mesh.estimatedRadius() + pRadius[index]
        val dx = pX[index] - cx
        val dy = pY[index] - cy
        val d2 = dx * dx + dy * dy
        if (d2 > r * r) {
            // Left the hit volume — allow a future hit only if pierce remains
            pLatched[index] = false
            return false
        }

        // Still overlapping after already applying damage this contact: ignore
        if (pLatched[index]) return false

        val raw = pDamage[index]
        if (againstEnemy) {
            applyDamageToEnemy(raw, pX[index], pY[index])
            mesh.applyImpact(pX[index], pY[index], pVx[index] * 0.02f, pVy[index] * 0.02f + 40f)
        } else {
            applyDamageToPlayer(raw, pX[index], pY[index])
            mesh.applyImpact(pX[index], pY[index], pVx[index] * 0.02f, pVy[index] * 0.02f + 40f)
        }
        pLatched[index] = true
        return true
    }

    private fun applyDamageToEnemy(raw: Float, atX: Float, atY: Float) {
        if (enemyHp <= 0f) return
        val defFactor = (1f - armorPierce).coerceIn(0.2f, 1f)
        val mitigated = max(1f, raw - enemyDef * 0.4f * defFactor)
        val crit = nextFloat() < critChance
        val dmg = if (crit) mitigated * 1.85f else mitigated
        enemyHp = (enemyHp - dmg).coerceAtLeast(0f)

        combo++
        comboTimer = 1.2f
        spawnFloat(atX, atY - 20f, dmg, isCrit = crit, isCombo = combo >= 3, life = 0.85f)

        if (lifesteal > 0f) {
            val heal = dmg * lifesteal
            playerHp = (playerHp + heal).coerceAtMost(playerMax)
        }
    }

    private fun applyDamageToPlayer(raw: Float, atX: Float, atY: Float) {
        if (playerHp <= 0f) return
        val mitigated = max(1f, raw - playerDef * 0.45f)
        playerHp = (playerHp - mitigated).coerceAtLeast(0f)
        spawnFloat(atX, atY - 20f, mitigated, isCrit = false, isCombo = false, life = 0.75f)

        if (thornsFraction > 0f && enemyHp > 0f) {
            val th = mitigated * thornsFraction
            enemyHp = (enemyHp - th).coerceAtLeast(0f)
            spawnFloat(enemyMesh.getCenterX(), enemyMesh.getCenterY() - 30f, th, isCrit = false, isCombo = false, life = 0.7f)
            enemyMesh.applyImpact(
                enemyMesh.getCenterX(),
                enemyMesh.getCenterY(),
                -30f,
                -20f
            )
        }
    }

    private fun spawnFloat(
        x: Float,
        y: Float,
        value: Float,
        isCrit: Boolean,
        isCombo: Boolean,
        life: Float
    ) {
        if (floatCount >= MAX_FLOATS) {
            removeFloat(0)
        }
        val i = floatCount
        fX[i] = x
        fY[i] = y
        fLife[i] = life
        fMaxLife[i] = life
        fValue[i] = value
        fIsCrit[i] = isCrit
        fIsCombo[i] = isCombo
        floatCount++
    }

    private fun removeFloat(index: Int) {
        val last = floatCount - 1
        if (index < 0 || index >= floatCount) return
        if (index != last) {
            fX[index] = fX[last]
            fY[index] = fY[last]
            fLife[index] = fLife[last]
            fMaxLife[index] = fMaxLife[last]
            fValue[index] = fValue[last]
            fIsCrit[index] = fIsCrit[last]
            fIsCombo[index] = fIsCombo[last]
        }
        floatCount--
    }

    private fun updateFloats(dt: Float) {
        var i = 0
        while (i < floatCount) {
            fLife[i] -= dt
            fY[i] -= 55f * dt
            if (fLife[i] <= 0f) {
                removeFloat(i)
            } else {
                i++
            }
        }
    }

    private fun nextFloat(): Float {
        // xorshift32-ish on Long
        var x = prng
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        prng = x
        val bits = (x ushr 8) and 0xFFFFFF
        return (bits.toInt() and 0xFFFFFF) / 16_777_216f
    }

    private fun cosApprox(a: Float): Float {
        // small-angle friendly + wrap
        val x = a
        val x2 = x * x
        return 1f - x2 * 0.5f + x2 * x2 / 24f
    }

    private fun sinApprox(a: Float): Float {
        val x = a
        val x2 = x * x
        return x - x2 * x / 6f
    }

    private fun buildViewState(): BattleViewState {
        val pc = projCount
        for (i in 0 until pc) {
            snapPX[i] = pX[i]
            snapPY[i] = pY[i]
            snapPVx[i] = pVx[i]
            snapPVy[i] = pVy[i]
            snapPType[i] = pType[i]
            snapPFrom[i] = pFromPlayer[i]
            snapPRad[i] = pRadius[i]
        }
        val fc = floatCount
        for (i in 0 until fc) {
            snapFX[i] = fX[i]
            snapFY[i] = fY[i]
            snapFLife[i] = fLife[i]
            snapFMax[i] = fMaxLife[i]
            snapFVal[i] = fValue[i]
            snapFCrit[i] = fIsCrit[i]
            snapFCombo[i] = fIsCombo[i]
        }
        playerMesh.copyOuterPositions(snapPlayerOuterX, snapPlayerOuterY)
        enemyMesh.copyOuterPositions(snapEnemyOuterX, snapEnemyOuterY)

        return BattleViewState(
            status = status,
            playerHp = playerHp,
            playerMaxHp = playerMax,
            enemyHp = enemyHp,
            enemyMaxHp = enemyMax,
            playerX = playerMesh.getCenterX(),
            playerY = playerMesh.getCenterY(),
            enemyX = enemyMesh.getCenterX(),
            enemyY = enemyMesh.getCenterY(),
            playerRadius = playerMesh.estimatedRadius(),
            enemyRadius = enemyMesh.estimatedRadius(),
            projectileCount = pc,
            projectileX = snapPX,
            projectileY = snapPY,
            projectileVx = snapPVx,
            projectileVy = snapPVy,
            projectileType = snapPType,
            projectileFromPlayer = snapPFrom,
            projectileRadius = snapPRad,
            floatCount = fc,
            floatX = snapFX,
            floatY = snapFY,
            floatLife = snapFLife,
            floatMaxLife = snapFMax,
            floatValue = snapFVal,
            floatIsCrit = snapFCrit,
            floatIsCombo = snapFCombo,
            comboCount = combo,
            prepareTimer = prepareLeft,
            elapsedMillis = elapsedMs,
            activeSynergies = synergies,
            playerOuterX = snapPlayerOuterX,
            playerOuterY = snapPlayerOuterY,
            playerOuterCount = playerMesh.outerCount,
            enemyOuterX = snapEnemyOuterX,
            enemyOuterY = snapEnemyOuterY,
            enemyOuterCount = enemyMesh.outerCount
        )
    }
}
