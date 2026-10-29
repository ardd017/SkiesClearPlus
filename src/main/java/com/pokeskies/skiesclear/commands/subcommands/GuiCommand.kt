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
import com.mojang.brigadier.tree.LiteralCommandNode
import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.gui.SettingsMenu
import com.pokeskies.skiesclear.utils.I18n
import com.pokeskies.skiesclear.utils.SubCommand
import com.pokeskies.skiesclear.utils.Utils
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer

/**
 * `/skiesclear gui [id]` opens the in-game settings menu.
 *
 * With no argument it targets the only entry when there is exactly one, which is the common case for
 * a server that just installed the mod. The menu itself is a vanilla chest screen, so this command is
 * the only entry point it needs — see [SettingsMenu].
 */
class GuiCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("gui")
            .requires(Permissions.require(SettingsMenu.PERMISSION, SettingsMenu.DEFAULT_LEVEL))
            .executes { ctx -> open(ctx, null) }
            .then(
                Commands.argument("id", StringArgumentType.word())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(ConfigManager.CONFIG.clears.keys, builder)
                    }
                    .executes { ctx -> open(ctx, StringArgumentType.getString(ctx, "id")) }
            )
            .build()
    }

    companion object {
        private fun open(ctx: CommandContext<CommandSourceStack>, requested: String?): Int {
            val player = ctx.source.player as? ServerPlayer
            if (player == null) {
                Utils.sendLocalized(ctx.source, "command.error.player_only", error = true)
                return 0
            }

            val clears = ConfigManager.CONFIG.clears
            if (clears.isEmpty()) {
                Utils.sendLocalized(ctx.source, "command.error.no_clears", error = true)
                return 0
            }

            val id = requested ?: clears.keys.firstOrNull()
            if (id == null) {
                Utils.sendLocalized(ctx.source, "command.error.no_clears", error = true)
                return 0
            }
            if (!clears.containsKey(id)) {
                Utils.sendLocalized(ctx.source, "command.error.unknown_clear", mapOf("id" to id), error = true)
                return 0
            }

            if (!SettingsMenu.open(player, id)) {
                Utils.sendLocalized(ctx.source, "command.error.unknown_clear", mapOf("id" to id), error = true)
                return 0
            }
            return 1
        }
    }
}
