/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.commands.subcommands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.LiteralCommandNode
import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.utils.I18n
import com.pokeskies.skiesclear.utils.PlayerTracker
import com.pokeskies.skiesclear.utils.SubCommand
import com.pokeskies.skiesclear.utils.Utils
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider

/**
 * `/skiesclear broadcast` — inspect and toggle the join/startup announcements.
 *
 * Mirrors the booleans in `config.json` so an admin can silence the announcements without opening
 * the settings menu. `reset` clears `players.json`, which makes every player's next join count as a
 * first join again — useful after rewriting the welcome text.
 */
class BroadcastCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("broadcast")
            .requires(Permissions.require(PERMISSION, 3))
            .executes(Companion::show)
            .then(
                Commands.argument("option", StringArgumentType.word())
                    .suggests(SUGGESTIONS)
                    .executes { ctx -> toggle(ctx, StringArgumentType.getString(ctx, "option")) }
            )
            .build()
    }

    companion object {
        private const val PERMISSION = "skiesclear.command.broadcast"

        private const val STARTUP = "startup"
        private const val JOIN = "join"
        private const val FIRST_JOIN = "first_join"
        private const val RESET = "reset"

        private val OPTIONS = listOf(STARTUP, JOIN, FIRST_JOIN, RESET)

        private val SUGGESTIONS = SuggestionProvider<CommandSourceStack> { _, builder ->
            SharedSuggestionProvider.suggest(OPTIONS, builder)
        }

        fun show(ctx: CommandContext<CommandSourceStack>): Int {
            val config = ConfigManager.CONFIG.broadcast
            val locale = I18n.localeFor(ctx.source.player)
            Utils.sendLocalized(
                ctx.source,
                "command.broadcast.current",
                mapOf(
                    "startup" to state(config.startup, locale),
                    "join" to state(config.join, locale),
                    "first_join" to state(config.firstJoin, locale),
                )
            )
            return 1
        }

        fun toggle(ctx: CommandContext<CommandSourceStack>, raw: String): Int {
            val config = ConfigManager.CONFIG.broadcast

            if (raw.equals(RESET, ignoreCase = true)) {
                val forgotten = PlayerTracker.count()
                PlayerTracker.reset()
                Utils.sendLocalized(
                    ctx.source,
                    "message.broadcast.reset",
                    mapOf("count" to forgotten.toString())
                )
                return forgotten
            }

            val value = when (raw.lowercase()) {
                STARTUP -> (!config.startup).also { config.startup = it }
                JOIN -> (!config.join).also { config.join = it }
                FIRST_JOIN -> (!config.firstJoin).also { config.firstJoin = it }
                else -> {
                    // Unknown option: showing the current state beats an error with no context.
                    return show(ctx)
                }
            }

            ConfigManager.saveFile("config.json", ConfigManager.CONFIG)

            val locale = I18n.localeFor(ctx.source.player)
            Utils.sendLocalized(
                ctx.source,
                "command.broadcast.toggled",
                mapOf("option" to raw.lowercase(), "value" to state(value, locale))
            )
            return 1
        }

        private fun state(value: Boolean, locale: String?): String =
            I18n.raw(if (value) "gui.value.on" else "gui.value.off", locale) ?: value.toString()
    }
}
