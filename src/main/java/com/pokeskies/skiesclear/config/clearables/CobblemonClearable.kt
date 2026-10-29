/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * Modifications for SkiesClear Plus Copyright 2026 ardd_017, licensed under the Apache License 2.0.
 *
 * This file has been MODIFIED from the upstream SkiesClear source. See ATTRIBUTION.md for the
 * list of changes.
 */
package com.pokeskies.skiesclear.config.clearables

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.pokeskies.skiesclear.config.ClearConfig
import com.pokeskies.skiesclear.utils.Utils
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.entity.EntityTypeTest

class CobblemonClearable(
    enabled: Boolean = true,
    blacklist: List<String> = emptyList(),
    whitelist: List<String> = emptyList()
) : Clearable<PokemonEntity>(enabled, blacklist, whitelist) {

    private var blacklistedAspects: List<String> = emptyList()
    private var whitelistedAspects: List<String> = emptyList()
    private var blacklistedTags: List<String> = emptyList()
    private var whitelistedTags: List<String> = emptyList()

    override fun initialize() {
        blacklistedAspects = generateAspects(blacklist)
        whitelistedAspects = generateAspects(whitelist)
        blacklistedTags = generateTags(blacklist)
        whitelistedTags = generateTags(whitelist)
    }

    override fun getResourceLocation(entity: PokemonEntity): ResourceLocation {
        return entity.pokemon.species.resourceIdentifier
    }

    override fun clearEntities(clearConfig: ClearConfig, levels: List<ServerLevel>): Int {
        if (!enabled) return 0
        var removalCount = 0
        for (level in levels) {
            try {
                val entities = level.getEntities(EntityTypeTest.forClass(PokemonEntity::class.java)) { true }.filter { entity ->
                    if (entity.isPersistenceRequired && !clearConfig.clearPersistent) return@filter false
                    if (entity.hasCustomName() && !clearConfig.clearNamed) return@filter false
                    if (entity.isPassenger && !clearConfig.clearPassengers) return@filter false
                    if (isWithinGrace(entity, clearConfig)) return@filter false
                    return@filter shouldClear(entity)
                }
                entities.forEach { entity -> entity.remove(Entity.RemovalReason.KILLED) }
                removalCount += entities.size
            } catch (exception: Exception) {
                Utils.printError("An exception was thrown while attempting to clear pokemon: $exception")
            }
        }
        return removalCount
    }

    override fun isBlacklisted(entity: PokemonEntity, id: ResourceLocation): Boolean {
        if (matchesFlags(entity, blacklist)) return true
        if (matchesSpecies(entity, blacklist)) return true
        if (matchesAspects(entity, blacklistedAspects)) return true
        if (matchesTags(entity, blacklistedTags)) return true
        return super.isBlacklisted(entity, id)
    }

    override fun isWhitelisted(entity: PokemonEntity, id: ResourceLocation): Boolean {
        // NOTE: this used to consult `blacklist` for #busy / #uncatchable, which made the whitelist
        // fire on entries that were meant to exclude a pokemon.
        if (matchesFlags(entity, whitelist)) return true
        if (matchesSpecies(entity, whitelist)) return true
        if (matchesAspects(entity, whitelistedAspects)) return true
        if (matchesTags(entity, whitelistedTags)) return true
        return super.isWhitelisted(entity, id)
    }

    private fun matchesFlags(entity: PokemonEntity, rules: List<String>): Boolean {
        if (rules.isEmpty()) return false
        val pokemon = entity.pokemon
        for (rule in rules) {
            val hit = when (rule.lowercase()) {
                "#owned" -> pokemon.isPlayerOwned() || pokemon.isNPCOwned()
                "#battling" -> entity.isBattling
                "#shiny" -> pokemon.shiny
                "#legendary" -> pokemon.isLegendary()
                "#mythical" -> pokemon.isMythical()
                "#ultrabeast" -> pokemon.isUltraBeast()
                "#busy" -> entity.isBusy
                "#uncatchable" -> entity.isUncatchable()
                else -> continue
            }
            if (hit) return true
        }
        return false
    }

    /** Species ids are `namespace:path`; a wildcard form such as `cobblemon:*` is supported too. */
    private fun matchesSpecies(entity: PokemonEntity, rules: List<String>): Boolean {
        val species = entity.pokemon.species.resourceIdentifier
        for (rule in rules) {
            if (rule.startsWith("#")) continue
            if (rule == "*") return true
            if (rule.endsWith(":*")) {
                if (species.namespace.equals(rule.dropLast(2), ignoreCase = true)) return true
            } else if (species.toString().equals(rule, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun matchesAspects(entity: PokemonEntity, aspects: List<String>): Boolean {
        if (aspects.isEmpty()) return false
        return entity.pokemon.aspects.any { aspect -> aspects.contains(aspect) }
    }

    private fun matchesTags(entity: PokemonEntity, tags: List<String>): Boolean {
        if (tags.isEmpty() || entity.tags.isEmpty()) return false
        return entity.tags.any { tag -> tags.contains(tag) }
    }

    override fun getPlaceholderKey(): String = "pokemon"

    private fun generateAspects(list: List<String>): List<String> {
        return list.mapNotNull { entry ->
            if (entry.startsWith("#aspect=", ignoreCase = true)) {
                entry.split("=", limit = 2).getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }
    }

    private fun generateTags(list: List<String>): List<String> {
        return list.mapNotNull { entry ->
            if (entry.startsWith("#tag=", ignoreCase = true)) {
                entry.split("=", limit = 2).getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }
    }

    override fun toString(): String {
        return "CobblemonClearable(enabled=$enabled, blacklist=$blacklist, whitelist=$whitelist)"
    }
}
