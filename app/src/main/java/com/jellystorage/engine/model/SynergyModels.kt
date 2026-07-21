package com.jellystorage.engine.model

/**
 * Weapon elemental / behavior tags for combinatoric synergy matching.
 * Names match the production engine contract (not softbody.assemble.Tag).
 */
enum class Tag {
    FIRE,
    ICE,
    THUNDER,
    POISON,
    SHIELD,
    PIERCE,
    BLAST,
    LIFESTEAL,
    AGILE,
    HEAVY
}

/**
 * Combat effect categories applied when a synergy activates.
 */
enum class EffectType {
    DAMAGE_SCALE,
    ARMOR_PIERCE,
    THORNS,
    CRIT_CHANCE,
    BURN_DOT,
    MULTI_SHOT,
    LIFESTEAL,
    DEFENSE_SCALE
}

/**
 * Equipable weapon definition used by the synergy resolver and battle sim.
 *
 * @param id stable identifier
 * @param name display name
 * @param baseDamage flat damage contribution per shot
 * @param fireRate shots per second (must be > 0)
 * @param tags combinatoric tags carried by this weapon
 */
data class Weapon(
    val id: String,
    val name: String,
    val baseDamage: Float,
    val fireRate: Float,
    val tags: Set<Tag>
) {
    init {
        require(id.isNotBlank()) { "Weapon.id must not be blank" }
        require(fireRate > 0f) { "Weapon.fireRate must be > 0, was $fireRate" }
        require(baseDamage >= 0f) { "Weapon.baseDamage must be >= 0" }
    }
}

/**
 * A resolved synergy effect produced by [evaluateSynergies].
 *
 * @param multiplier primary numeric magnitude (damage scale, thorns fraction, crit chance add, etc.)
 * @param effectType mechanical category
 * @param description human-readable effect summary
 */
data class SynergyEffect(
    val id: String,
    val name: String,
    val multiplier: Float,
    val effectType: EffectType,
    val description: String
)

/**
 * Internal registry entry: required tags (all must be present across equipped weapons)
 * and the ordered list of effects this synergy emits when matched.
 */
private data class SynergyDef(
    val id: String,
    val name: String,
    val requiredTags: Set<Tag>,
    val effects: List<SynergyEffect>
)

/**
 * High-priority synergies required by the engine contract, plus a few stable extras.
 * Matching is pure and deterministic: same equipped set → same ordered effect list.
 */
private val SYNERGY_REGISTRY: List<SynergyDef> = listOf(
    SynergyDef(
        id = "steam_blast",
        name = "Steam Blast",
        requiredTags = setOf(Tag.FIRE, Tag.ICE),
        effects = listOf(
            SynergyEffect(
                id = "steam_blast_damage",
                name = "Steam Blast",
                multiplier = 1.5f,
                effectType = EffectType.DAMAGE_SCALE,
                description = "FIRE+ICE: damage scaling 1.5x"
            ),
            SynergyEffect(
                id = "steam_blast_pierce",
                name = "Steam Blast",
                multiplier = 1.0f,
                effectType = EffectType.ARMOR_PIERCE,
                description = "FIRE+ICE: attacks ignore a portion of armor"
            )
        )
    ),
    SynergyDef(
        id = "thunder_armor",
        name = "Thunder Armor",
        requiredTags = setOf(Tag.THUNDER, Tag.SHIELD),
        effects = listOf(
            SynergyEffect(
                id = "thunder_armor_thorns",
                name = "Thunder Armor",
                multiplier = 0.35f,
                effectType = EffectType.THORNS,
                description = "THUNDER+SHIELD: return 35% of received damage as thorns"
            )
        )
    ),
    SynergyDef(
        id = "frozen_shatter",
        name = "Frozen Shatter",
        requiredTags = setOf(Tag.ICE, Tag.HEAVY),
        effects = listOf(
            SynergyEffect(
                id = "frozen_shatter_crit",
                name = "Frozen Shatter",
                multiplier = 0.40f,
                effectType = EffectType.CRIT_CHANCE,
                description = "ICE+HEAVY: +40% critical chance vs chilled targets"
            )
        )
    ),
    SynergyDef(
        id = "plague_leech",
        name = "Plague Leech",
        requiredTags = setOf(Tag.POISON, Tag.LIFESTEAL),
        effects = listOf(
            SynergyEffect(
                id = "plague_leech_ls",
                name = "Plague Leech",
                multiplier = 0.30f,
                effectType = EffectType.LIFESTEAL,
                description = "POISON+LIFESTEAL: heal for 30% of damage dealt"
            ),
            SynergyEffect(
                id = "plague_leech_dot",
                name = "Plague Leech",
                multiplier = 8.0f,
                effectType = EffectType.BURN_DOT,
                description = "POISON+LIFESTEAL: toxic damage over time"
            )
        )
    ),
    SynergyDef(
        id = "chain_drill",
        name = "Chain Drill",
        requiredTags = setOf(Tag.PIERCE, Tag.AGILE),
        effects = listOf(
            SynergyEffect(
                id = "chain_drill_multi",
                name = "Chain Drill",
                multiplier = 2.0f,
                effectType = EffectType.MULTI_SHOT,
                description = "PIERCE+AGILE: fire additional projectiles"
            )
        )
    ),
    SynergyDef(
        id = "fortress",
        name = "Fortress Form",
        requiredTags = setOf(Tag.SHIELD, Tag.HEAVY),
        effects = listOf(
            SynergyEffect(
                id = "fortress_def",
                name = "Fortress Form",
                multiplier = 1.5f,
                effectType = EffectType.DEFENSE_SCALE,
                description = "SHIELD+HEAVY: defense scaling 1.5x"
            )
        )
    )
)

/**
 * Collect the union of all tags present on equipped weapons.
 */
fun collectTags(equippedWeapons: List<Weapon>): Set<Tag> {
    if (equippedWeapons.isEmpty()) return emptySet()
    val out = LinkedHashSet<Tag>()
    for (w in equippedWeapons) {
        out.addAll(w.tags)
    }
    return out
}

/**
 * Pure, deterministic synergy matching.
 *
 * For each registry entry, if every required tag is present in the equipped
 * tag union, all of that entry's [SynergyEffect]s are appended in registry order.
 * The same equipped set always yields the same ordered list.
 *
 * High-priority contract synergies:
 * - FIRE+ICE → Steam Blast (1.5x damage + armor pierce)
 * - THUNDER+SHIELD → Thunder Armor (percentage thorns)
 * - ICE+HEAVY → Frozen Shatter (crit chance vs chilled)
 */
fun evaluateSynergies(equippedWeapons: List<Weapon>): List<SynergyEffect> {
    if (equippedWeapons.isEmpty()) return emptyList()
    val present = collectTags(equippedWeapons)
    if (present.isEmpty()) return emptyList()

    val result = ArrayList<SynergyEffect>(8)
    for (def in SYNERGY_REGISTRY) {
        var matched = true
        for (need in def.requiredTags) {
            if (need !in present) {
                matched = false
                break
            }
        }
        if (matched) {
            result.addAll(def.effects)
        }
    }
    return result
}

/**
 * Named facade for call-sites that prefer an object API.
 * Delegates to [evaluateSynergies] with identical semantics.
 */
object SynergyResolver {
    fun resolve(equippedWeapons: List<Weapon>): List<SynergyEffect> =
        evaluateSynergies(equippedWeapons)

    fun hasSynergy(equippedWeapons: List<Weapon>, synergyId: String): Boolean =
        evaluateSynergies(equippedWeapons).any { it.id.startsWith(synergyId) || it.name.equals(synergyId, ignoreCase = true) }
}

/** Catalog helpers for tests and battle bootstrap. */
object WeaponCatalog {
    val FLAME_CLAW = Weapon(
        id = "flame_claw",
        name = "Flame Claw",
        baseDamage = 16f,
        fireRate = 1.4f,
        tags = setOf(Tag.FIRE, Tag.HEAVY)
    )
    val ICE_SPIKE = Weapon(
        id = "ice_spike",
        name = "Ice Spike",
        baseDamage = 13f,
        fireRate = 1.8f,
        tags = setOf(Tag.ICE, Tag.PIERCE)
    )
    val THUNDER_HAMMER = Weapon(
        id = "thunder_hammer",
        name = "Thunder Hammer",
        baseDamage = 15f,
        fireRate = 1.1f,
        tags = setOf(Tag.THUNDER, Tag.HEAVY)
    )
    val SHELL_GUARD = Weapon(
        id = "shell_guard",
        name = "Shell Guard",
        baseDamage = 5f,
        fireRate = 0.8f,
        tags = setOf(Tag.SHIELD)
    )
    val DRILL_BIT = Weapon(
        id = "drill_bit",
        name = "Drill Bit",
        baseDamage = 14f,
        fireRate = 1.6f,
        tags = setOf(Tag.PIERCE, Tag.AGILE)
    )
    val POISON_SAC = Weapon(
        id = "poison_sac",
        name = "Poison Sac",
        baseDamage = 11f,
        fireRate = 1.2f,
        tags = setOf(Tag.POISON, Tag.BLAST)
    )
    val VAMP_TENDRIL = Weapon(
        id = "vamp_tendril",
        name = "Vamp Tendril",
        baseDamage = 12f,
        fireRate = 1.5f,
        tags = setOf(Tag.LIFESTEAL, Tag.AGILE)
    )

    fun defaultsForSteamBlast(): List<Weapon> = listOf(FLAME_CLAW, ICE_SPIKE)
    fun defaultsForThunderArmor(): List<Weapon> = listOf(THUNDER_HAMMER, SHELL_GUARD)
    fun defaultsForFrozenShatter(): List<Weapon> = listOf(ICE_SPIKE, FLAME_CLAW)
}
