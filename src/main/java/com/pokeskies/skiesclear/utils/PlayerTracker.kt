/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.utils

import com.pokeskies.skiesclear.SkiesClear
import java.io.File
import java.util.UUID

/**
 * Remembers which players have joined this server before, so the first-join welcome fires exactly
 * once per player.
 *
 * The store is a flat `players.json` next to `config.json` holding an array of UUID strings. It is
 * deliberately tiny and append-only: a UUID is written the moment a first-join welcome is
 * delivered, and the file is rewritten in full (at most a few thousand short strings).
 *
 * Failing to read or write the file never breaks a join — the player just sees the welcome again
 * next time.
 */
object PlayerTracker {
    private const val FILE_NAME = "players.json"

    private val seen: MutableSet<String> = load()

    /** True when [uuid] has never been recorded before. */
    @Synchronized
    fun isFirstJoin(uuid: UUID): Boolean = uuid.toString() !in seen

    /**
     * Records [uuid] and persists the store.
     *
     * @return true when the UUID was new and therefore had to be written.
     */
    @Synchronized
    fun markSeen(uuid: UUID): Boolean {
        if (!seen.add(uuid.toString())) return false
        save()
        return true
    }

    /** Drops every remembered player, so the next join counts as a first join again. */
    @Synchronized
    fun reset() {
        seen.clear()
        save()
    }

    @Synchronized
    fun count(): Int = seen.size

    /** Re-reads `players.json` from disk; called after the config directory is known to exist. */
    @Synchronized
    fun reload() {
        seen.clear()
        seen.addAll(load())
    }

    private fun file(): File = File(SkiesClear.INSTANCE.configDir, FILE_NAME)

    private fun load(): MutableSet<String> {
        val target = file()
        if (!target.exists()) return LinkedHashSet()
        return try {
            val raw = target.readText(Charsets.UTF_8)
            if (raw.isBlank()) return LinkedHashSet()
            val parsed = SkiesClear.INSTANCE.gsonPretty.fromJson(raw, Array<String>::class.java)
                ?: return LinkedHashSet()
            LinkedHashSet(parsed.filter { it.isNotBlank() })
        } catch (e: Exception) {
            Utils.printError("Failed to read $FILE_NAME, treating everyone as new: ${e.message}")
            LinkedHashSet()
        }
    }

    private fun save() {
        try {
            val target = file()
            target.parentFile?.mkdirs()
            target.writeText(SkiesClear.INSTANCE.gsonPretty.toJson(seen.toList()), Charsets.UTF_8)
        } catch (e: Exception) {
            Utils.printError("Failed to save $FILE_NAME: ${e.message}")
        }
    }
}
