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

import com.pokeskies.skiesclear.SkiesClear
import com.pokeskies.skiesclear.config.ClearConfig
import com.pokeskies.skiesclear.utils.Utils
import net.minecraft.advancements.critereon.NbtPredicate
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.entity.EntityTypeTest

class EntityClearable(
    enabled: Boolean = false,
    blacklist: List<String> = emptyList(),
    whitelist: List<String> = emptyList()
) : Clearable<Entity>(enabled, blacklist, whitelist) {

    /** Parsed rule sets; rebuilt by [initialize] so `/reload` picks up config edits. */
    private var blacklistedTags: List<String> = emptyList()
    private var blacklistedNbt: List<CompoundTag> = emptyList()
    private var whitelistedTags: List<String> = emptyList()
    private var whitelistedNbt: List<CompoundTag> = emptyList()

    override fun initialize() {
        blacklistedTags = generateTags(blacklist)
        whitelistedTags = generateTags(whitelist)
        blacklistedNbt = generateNBT(blacklist)
        whitelistedNbt = generateNBT(whitelist)
    }

    override fun getResourceLocation(entity: Entity): ResourceLocation {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
    }

    override fun clearEntities(clearConfig: ClearConfig, levels: List<ServerLevel>): Int {
        if (!enabled) return 0
        var removalCount = 0
        for (level in levels) {
            try {
                val entities = level.getEntities(EntityTypeTest.forClass(Entity::class.java)) { true }.filter { entity ->
                    if (entity is Player) return@filter false
                    if (entity is Mob && entity.isPersistenceRequired && !clearConfig.clearPersistent) return@filter false
                    if (entity.hasCustomName() && !clearConfig.clearNamed) return@filter false
                    if (entity.isPassenger && !clearConfig.clearPassengers) return@filter false
                    if (isWithinGrace(entity, clearConfig)) return@filter false
                    return@filter shouldClear(entity)
                }
                entities.forEach { entity -> entity.remove(Entity.RemovalReason.KILLED) }
                removalCount += entities.size
            } catch (exception: Exception) {
                Utils.printError("An exception was thrown while attempting to clear entities: $exception")
            }
        }
        return removalCount
    }

    override fun isBlacklisted(entity: Entity, id: ResourceLocation): Boolean {
        if (matchesTags(entity, blacklistedTags)) return true
        if (matchesNbt(entity, blacklistedNbt)) return true
        return super.isBlacklisted(entity, id)
    }

    override fun isWhitelisted(entity: Entity, id: ResourceLocation): Boolean {
        if (matchesTags(entity, whitelistedTags)) return true
        if (matchesNbt(entity, whitelistedNbt)) return true
        return super.isWhitelisted(entity, id)
    }

    private fun matchesTags(entity: Entity, tags: List<String>): Boolean {
        if (tags.isEmpty() || entity.tags.isEmpty()) return false
        return entity.tags.any { tag -> tags.contains(tag) }
    }

    /**
     * An NBT rule is a set of expected values. It matches only when *every* key in the rule is
     * present with an equal value on the entity — the previous version flipped `passes` on the first
     * matching key, so a rule with several keys matched far too eagerly.
     */
    private fun matchesNbt(entity: Entity, rules: List<CompoundTag>): Boolean {
        if (rules.isEmpty()) return false
        val actual = NbtPredicate.getEntityTagToCompare(entity)
        for (rule in rules) {
            var allMatch = true
            for (key in rule.allKeys) {
                if (!actual.contains(key)) {
                    allMatch = false
                    break
                }
                val expected: Tag? = rule.get(key)
                val found: Tag? = actual.get(key)
                if (expected == null || expected != found) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) return true
        }
        return false
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

    private fun generateNBT(list: List<String>): List<CompoundTag> {
        return list.mapNotNull { entry ->
            if (!entry.startsWith("#nbt=", ignoreCase = true)) return@mapNotNull null
            val raw = entry.split("=", limit = 2).getOrNull(1)?.trim()
            if (raw.isNullOrEmpty()) return@mapNotNull null
            try {
                SkiesClear.INSTANCE.gson.fromJson(raw, CompoundTag::class.java)
            } catch (e: Exception) {
                Utils.printError("Invalid #nbt rule '$raw': ${e.message}")
                null
            }
        }
    }

    override fun getPlaceholderKey(): String = "entities"

    override fun toString(): String {
        return "EntityClearable(enabled=$enabled, blacklist=$blacklist, whitelist=$whitelist)"
    }
}
