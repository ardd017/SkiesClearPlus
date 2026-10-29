/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.utils

import com.pokeskies.skiesclear.ClearTask
import com.pokeskies.skiesclear.SkiesClear
import com.pokeskies.skiesclear.config.ConfigManager
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Sends the "the mod is alive" announcements: once at server start, on every join, and an extra
 * one-off welcome for a player who has never been on this server.
 *
 * Clearing runs on a multi-minute cycle by default, so without these a player can sit on the server
 * for five minutes and never learn the mod exists. The announcements reuse the same per-reader
 * rendering path as every other message ([Utils.broadcast]), so each player reads them in their own
 * language.
 *
 * All three are opt-out through [com.pokeskies.skiesclear.config.BroadcastConfig]; an empty line
 * list also silences a broadcast without touching the boolean.
 */
object Announcer {
    private const val ENTRY_KEY = "<lang:message.broadcast.entry>"

    /**
     * Prints the schedule to the console and, for an integrated server, to anyone already online.
     *
     * A dedicated server has no players at this point, so the console line is what actually
     * confirms the mod came up; the broadcast only matters when the server starts with players
     * attached (single player, or a reload-free `/start`-style host).
     */
    fun announceStartup(server: MinecraftServer) {
        val config = ConfigManager.CONFIG.broadcast
        val tasks = activeTasks()

        // Logged even when the broadcast is off: this line is the operator's proof the schedule is
        // live, and silencing the in-game notice should not also blind the console.
        SkiesClear.LOGGER.info(
            "[SkiesClear Plus] Started with {} active clear task(s): {}",
            tasks.size,
            tasks.joinToString(", ") { "${it.displayName()} (${it.clearConfig.interval}s)" },
        )

        if (!config.startup) return

        val recipients = server.playerList.players
        if (recipients.isEmpty() || config.startupLines.isEmpty()) return
        send(recipients, config.startupLines, tasks)
    }

    /**
     * Announcements for one joining player.
     *
     * Both the join notice and the first-join welcome are delivered here so a brand new player sees
     * one coherent block instead of two separate splashes a tick apart.
     *
     * The player is recorded as seen on every join, including while the welcome is switched off.
     * The alternative — only recording when the welcome fires — makes re-enabling `first_join`
     * greet every long-time player again. Admins who *want* that replay have `/skiesclear broadcast
     * reset`.
     */
    fun announceJoin(player: ServerPlayer) {
        val config = ConfigManager.CONFIG.broadcast
        val firstJoin = config.firstJoin && PlayerTracker.isFirstJoin(player.uuid)
        val tasks = activeTasks()

        if (config.join) send(listOf(player), config.joinLines, tasks)
        if (firstJoin) send(listOf(player), config.firstJoinLines, tasks)
        PlayerTracker.markSeen(player.uuid)
    }

    /**
     * Renders [lines] for each player, then one extra line per active clear entry.
     *
     * The per-entry lines use `message.broadcast.entry` and are only appended when at least one task
     * is running; with everything disabled the header alone still carries the count of `0`.
     */
    private fun send(recipients: Collection<ServerPlayer>, lines: List<String>, tasks: List<ClearTask>) {
        if (lines.isEmpty() || recipients.isEmpty()) return

        Utils.broadcast(recipients, lines) { basePlaceholders(tasks.size) }

        for (task in tasks) {
            Utils.broadcast(recipients, listOf(ENTRY_KEY)) { locale ->
                basePlaceholders(tasks.size) + taskPlaceholders(task, locale)
            }
        }
    }

    private fun activeTasks(): List<ClearTask> {
        val manager = runCatching { SkiesClear.INSTANCE.clearManager }.getOrNull() ?: return emptyList()
        return manager.taskIds().mapNotNull { manager.getClearTask(it) }
    }

    private fun basePlaceholders(count: Int): Map<String, String> = mapOf(
        "count" to count.toString(),
        "version" to modVersion(),
    )

    private fun taskPlaceholders(task: ClearTask, locale: String): Map<String, String> = mapOf(
        "clear" to task.displayName(locale),
        "clear_time" to Utils.getFormattedTime(task.clearConfig.interval.toLong(), locale),
        "interval_seconds" to task.clearConfig.interval.toString(),
        "time_remaining" to Utils.getFormattedTime(task.getTimer().toLong(), locale),
    )

    private fun modVersion(): String = try {
        FabricLoader.getInstance().getModContainer(SkiesClear.MOD_ID)
            .map { it.metadata.version.friendlyString }
            .orElse("?")
    } catch (e: Exception) {
        "?"
    }
}
