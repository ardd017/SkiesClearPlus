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

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.pokeskies.skiesclear.config.ClearConfig
import com.pokeskies.skiesclear.utils.Utils
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import java.lang.reflect.Type

abstract class Clearable<T : Entity>(
    var enabled: Boolean = false,
    var blacklist: List<String> = emptyList(),
    var whitelist: List<String> = emptyList()
) {
    abstract fun getResourceLocation(entity: T): ResourceLocation

    open fun initialize() {}

    // Given a list over levels, clear the relevant entities and return a count of how many were cleared
    abstract fun clearEntities(clearConfig: ClearConfig, levels: List<ServerLevel>): Int

    open fun shouldClear(entity: T): Boolean {
        val rl = getResourceLocation(entity)

        if (isBlacklisted(entity, rl)) return false
        if (whitelist.isNotEmpty() && !isWhitelisted(entity, rl)) return false

        return true
    }

    /**
     * A namespace wildcard is written `namespace:*`. A bare `*`, or a pattern without a colon, is
     * treated as an exact id so a typo cannot silently blacklist an entire namespace.
     */
    protected fun matches(patterns: List<String>, id: ResourceLocation): Boolean {
        for (pattern in patterns) {
            if (pattern == "*") return true
            if (pattern.endsWith(":*")) {
                if (id.namespace.equals(pattern.dropLast(2), ignoreCase = true)) return true
            } else if (id.toString().equals(pattern, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    open fun isBlacklisted(entity: T, id: ResourceLocation): Boolean = matches(blacklist, id)

    open fun isWhitelisted(entity: T, id: ResourceLocation): Boolean = matches(whitelist, id)

    /**
     * True while [entity] is younger than the entry's `grace_seconds`.
     *
     * Shared by every clearable so a freshly dropped item, a freshly spawned mob and a freshly
     * spawned Pokémon all get the same protection. Uses [Entity.tickCount] rather than
     * [net.minecraft.world.entity.item.ItemEntity.age] because the latter is only advanced by item
     * entities; `tickCount` is the one counter every entity carries.
     */
    protected fun isWithinGrace(entity: T, clearConfig: ClearConfig): Boolean {
        val grace = clearConfig.graceSeconds
        if (grace <= 0) return false
        return entity.tickCount < grace * TICKS_PER_SECOND
    }

    inline fun <reified T> getType(): Class<T> {
        return T::class.java
    }

    /**
     * Suffix that identifies this clearable's count. Rendered as `%clear_amount_<key>%` in messages,
     * so `items` produces `%clear_amount_items%`.
     */
    abstract fun getPlaceholderKey(): String

    /** The `%...%` token for this clearable's count, used when building placeholder maps. */
    fun getPlaceholder(): String = "%clear_amount_${getPlaceholderKey()}%"

    /** Legacy helper kept for config compatibility; prefers the placeholder map path. */
    fun parse(string: String, total: Int): String = string.replace(getPlaceholder(), total.toString())

    /**
     * Reads and writes the `clearables` block.
     *
     * The deserializer half has to exist because the JSON keys (`items`, `entities`, `cobblemon`)
     * are not the Kotlin class names. The serializer half is what makes saving the config safe:
     * without it Gson wrote every clearable as `{}`, so any config save (a GUI click, `/skiesclear
     * language`) silently erased the player's black/whitelists. Each entry is written with its
     * concrete runtime class so subclass fields survive.
     */
    class MapAdapter :
        JsonSerializer<Map<String, Clearable<*>>>,
        JsonDeserializer<Map<String, Clearable<*>>> {

        override fun serialize(
            src: Map<String, Clearable<*>>,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val out = JsonObject()
            for ((key, clearable) in src) {
                out.add(key, context.serialize(clearable, clearable.javaClass))
            }
            return out
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): Map<String, Clearable<*>> {
            if (!json.isJsonObject) return LinkedHashMap()
            val out = LinkedHashMap<String, Clearable<*>>()
            for ((key, value) in json.asJsonObject.entrySet()) {
                val clearable = when (key.lowercase()) {
                    "items" -> context.deserialize<ItemClearable>(value, ItemClearable::class.java)
                    "entities" -> context.deserialize<EntityClearable>(value, EntityClearable::class.java)
                    "cobblemon" -> if (FabricLoader.getInstance().isModLoaded("cobblemon"))
                        context.deserialize<CobblemonClearable>(value, CobblemonClearable::class.java)
                    else {
                        Utils.printError("Cobblemon is not loaded, skipping Cobblemon clearable!")
                        continue
                    }

                    else -> {
                        // An unknown key must not take the whole config down with it.
                        Utils.printError("Unknown clearable type '$key', skipping it.")
                        continue
                    }
                }
                out[key] = clearable
            }
            return out
        }
    }

    companion object {
        /** Vanilla ticks 20 times a second, so ages are compared in ticks but configured in seconds. */
        private const val TICKS_PER_SECOND = 20
    }
}
