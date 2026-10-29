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

import com.pokeskies.skiesclear.config.ClearConfig
import com.pokeskies.skiesclear.utils.Utils
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.entity.EntityTypeTest

class ItemClearable(
    enabled: Boolean = false,
    blacklist: List<String> = emptyList(),
    whitelist: List<String> = emptyList()
) : Clearable<ItemEntity>(enabled, blacklist, whitelist) {
    override fun getResourceLocation(entity: ItemEntity): ResourceLocation {
        return BuiltInRegistries.ITEM.getKey(entity.item.item)
    }

    override fun clearEntities(clearConfig: ClearConfig, levels: List<ServerLevel>): Int {
        if (!enabled) return 0
        var removalCount = 0
        for (level in levels) {
            try {
                val entities = level.getEntities(EntityTypeTest.forClass(ItemEntity::class.java)) { true }.filter { entity ->
                    if (entity.hasCustomName() && !clearConfig.clearNamed) return@filter false
                    if (entity.isPassenger && !clearConfig.clearPassengers) return@filter false
                    if (entity.age == PERSISTENT_AGE && !clearConfig.clearPersistent) return@filter false
                    if (isWithinGrace(entity, clearConfig)) return@filter false
                    return@filter shouldClear(entity)
                }
                entities.forEach { entity -> entity.remove(Entity.RemovalReason.KILLED) }
                removalCount += entities.size
            } catch (exception: Exception) {
                Utils.printError("An exception was thrown while attempting to clear items: $exception")
            }
        }
        return removalCount
    }

    override fun getPlaceholderKey(): String = "items"

    override fun toString(): String {
        return "ItemClearable(enabled=$enabled, blacklist=$blacklist, whitelist=$whitelist)"
    }

    companion object {
        /** Vanilla marks items that never despawn with this age. */
        private const val PERSISTENT_AGE = -32768
    }
}
