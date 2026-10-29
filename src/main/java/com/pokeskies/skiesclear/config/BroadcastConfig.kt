/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.config

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.pokeskies.skiesclear.utils.FlexibleListAdaptorFactory

/**
 * Announcements that tell players the mod is alive.
 *
 * Clearing usually runs on a five minute cycle, so a player who joins right after a clear sees
 * nothing at all for minutes and cannot tell whether the mod is installed. These three broadcasts
 * cover that gap:
 *
 *  - [startup] — once per server boot, so the console and anyone already online see the schedule.
 *  - [join] — every time a player joins, listing every active entry and its countdown.
 *  - [firstJoin] — only on a player's very first join on this server (tracked in `players.json`).
 *
 * Turning a boolean off leaves the corresponding `*_lines` untouched, so admins can park a message
 * without losing the wording. An empty line list also disables that broadcast.
 *
 * Every field is `var` for the same reason as [ClearConfig]: the settings menu edits the live
 * object graph and only [ConfigManager] writes it to disk.
 */
class BroadcastConfig(
    /** Announce the schedule once when the server finishes starting. */
    @SerializedName("startup")
    var startup: Boolean = true,

    /** Announce the schedule to a player whenever they join. */
    @SerializedName("join")
    var join: Boolean = true,

    /** Extra one-off welcome for a player who has never joined this server before. */
    @SerializedName("first_join")
    var firstJoin: Boolean = true,

    /** Lines for the startup announcement. Placeholders: `{count}`, `{version}`. */
    @SerializedName("startup_lines")
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    var startupLines: List<String> = DEFAULT_STARTUP,

    /** Header lines for the join announcement. Placeholders: `{count}`, `{version}`. */
    @SerializedName("join_lines")
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    var joinLines: List<String> = DEFAULT_JOIN,

    /** Lines for the first-join welcome. Placeholders: `{count}`, `{version}`. */
    @SerializedName("first_join_lines")
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    var firstJoinLines: List<String> = DEFAULT_FIRST_JOIN,
) {
    override fun toString(): String {
        return "BroadcastConfig(startup=$startup, join=$join, firstJoin=$firstJoin)"
    }

    companion object {
        /** Language-neutral defaults; the wording lives in the per-locale lang files. */
        val DEFAULT_STARTUP: List<String> = listOf("", "<lang:message.broadcast.startup>", "")
        val DEFAULT_JOIN: List<String> = listOf("", "<lang:message.broadcast.join>", "")
        val DEFAULT_FIRST_JOIN: List<String> = listOf("<lang:message.broadcast.first_join>")
    }
}
