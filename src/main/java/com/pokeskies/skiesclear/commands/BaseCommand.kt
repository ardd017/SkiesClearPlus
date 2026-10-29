/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * Modifications for SkiesClear Plus Copyright 2026 ardd_017, licensed under the Apache License 2.0.
 *
 * This file has been MODIFIED from the upstream SkiesClear source. See ATTRIBUTION.md for the
 * list of changes.
 */
package com.pokeskies.skiesclear.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.LiteralCommandNode
import com.pokeskies.skiesclear.SkiesClear
import com.pokeskies.skiesclear.commands.subcommands.BroadcastCommand
import com.pokeskies.skiesclear.commands.subcommands.DebugCommand
import com.pokeskies.skiesclear.commands.subcommands.ForceCommand
import com.pokeskies.skiesclear.commands.subcommands.GuiCommand
import com.pokeskies.skiesclear.commands.subcommands.InfoCommand
import com.pokeskies.skiesclear.commands.subcommands.LanguageCommand
import com.pokeskies.skiesclear.commands.subcommands.ReloadCommand
import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.utils.I18n
import com.pokeskies.skiesclear.utils.Utils
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer

class BaseCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val subCommands: List<LiteralCommandNode<CommandSourceStack>> = listOf(
            DebugCommand().build(),
            ReloadCommand().build(),
            ForceCommand().build(),
            InfoCommand().build(),
            LanguageCommand().build(),
            GuiCommand().build(),
            BroadcastCommand().build(),
        )

        for (alias in ALIASES) {
            val root = Commands.literal(alias)
                .requires(Permissions.require("skiesclear.command.base", 4))
                .executes { ctx -> listClears(ctx.source) }
                .build()
            subCommands.forEach(root::addChild)
            dispatcher.root.addChild(root)
        }
    }

    companion object {
        private val ALIASES = listOf("skiesclear")

        /** `/skiesclear` with no arguments lists every enabled clear entry and its remaining time. */
        fun listClears(source: CommandSourceStack): Int {
            val locale = I18n.localeFor(source.player as? ServerPlayer)
            val enabled = ConfigManager.CONFIG.clears.filterValues { it.enabled }
            if (enabled.isEmpty()) {
                Utils.sendLocalized(source, "command.error.no_clears", error = true)
                return 0
            }

            var shown = 0
            for ((clearId, clearConfig) in enabled) {
                val task = SkiesClear.INSTANCE.clearManager.getClearTask(clearId) ?: continue
                Utils.sendLines(source, clearConfig.messages.info, task.placeholders(0, emptyMap(), null, locale))
                shown++
            }

            if (shown == 0) {
                Utils.sendLocalized(source, "command.error.no_clears", error = true)
                return 0
            }
            return shown
        }
    }
}
