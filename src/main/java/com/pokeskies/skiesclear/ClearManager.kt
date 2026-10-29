/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * Modifications for SkiesClear Plus Copyright 2026 ardd_017, licensed under the Apache License 2.0.
 *
 * This file has been MODIFIED from the upstream SkiesClear source. See ATTRIBUTION.md for the
 * list of changes.
 */
package com.pokeskies.skiesclear

import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.utils.Utils
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer

/**
 * Owns the live [ClearTask] instances and drives them off the server tick loop.
 *
 * Tasks are ticked once per second (20 ticks). The counter was previously `if (ticks++ > 20)`,
 * which fired on tick 21 and then every 21 ticks — a slow drift that made `interval` inaccurate.
 */
class ClearManager {
    private val clearTasks: MutableMap<String, ClearTask> = LinkedHashMap()
    private var tickCounter = 0

    init {
        reload()

        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
            if (++tickCounter < TICKS_PER_SECOND) return@EndTick
            tickCounter = 0
            // Snapshot: a task may reload the map indirectly (e.g. via a command run from a callback).
            for (task in clearTasks.values.toList()) {
                try {
                    task.tick(server)
                } catch (e: Exception) {
                    Utils.printError("A clear task threw during its tick: $e")
                }
            }
        })
    }

    /**
     * Rebuilds the task map from the current config.
     *
     * Every `ClearTask` starts its timer at `interval`, so a naive rebuild would reset the
     * countdown on every call — and this runs on `/skiesclear reload`, on `/skiesclear language`
     * and on every save from either settings menu. Switching language with 8 seconds left used to
     * jump the next clear five minutes into the future. Tasks whose `interval` did not change keep
     * their remaining time; only a changed interval (or a new entry) starts over.
     */
    fun reload() {
        val previous = clearTasks.toMap()
        clearTasks.clear()
        val config = ConfigManager.CONFIG
        for ((id, clearConfig) in config.clears) {
            if (!clearConfig.enabled) continue
            for ((_, clearable) in clearConfig.clearables) {
                try {
                    clearable.initialize()
                } catch (e: Exception) {
                    Utils.printError("Failed to initialise clearable '$id': $e")
                }
            }
            val task = ClearTask(id, clearConfig)
            previous[id]?.let { task.carryOverTimerFrom(it) }
            clearTasks[id] = task
        }
        Utils.printDebug("Registered ${clearTasks.size} active clear task(s): ${clearTasks.keys}", true)
    }

    fun getClearTask(id: String): ClearTask? = clearTasks[id]

    /** Ids of the tasks that are currently running, for command suggestions. */
    fun taskIds(): Collection<String> = clearTasks.keys

    companion object {
        private const val TICKS_PER_SECOND = 20
    }
}
