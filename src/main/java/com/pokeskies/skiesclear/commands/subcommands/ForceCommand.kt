/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * Modifications for SkiesClear Plus Copyright 2026 ardd_017, licensed under the Apache License 2.0.
 *
 * This file has been MODIFIED from the upstream SkiesClear source. See ATTRIBUTION.md for the
 * list of changes.
 */
package com.pokeskies.skiesclear.commands.subcommands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.pokeskies.skiesclear.SkiesClear
import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.utils.I18n
import com.pokeskies.skiesclear.utils.SubCommand
import com.pokeskies.skiesclear.utils.Utils
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer

class ForceCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("force")
            .requires(Permissions.require("skiesclear.command.force", 4))
            .then(
                Commands.argument("id", StringArgumentType.string())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(ConfigManager.CONFIG.clears.keys, builder)
                    }
                    .executes { ctx -> force(ctx, StringArgumentType.getString(ctx, "id"), true) }
                    .then(
                        Commands.argument("announce", BoolArgumentType.bool())
                            .executes { ctx ->
                                force(
                                    ctx,
                                    StringArgumentType.getString(ctx, "id"),
                                    BoolArgumentType.getBool(ctx, "announce")
                                )
                            }
                    )
            )
            .build()
    }

    companion object {
        private fun force(ctx: CommandContext<CommandSourceStack>, id: String, announce: Boolean): Int {
            val clearConfig = ConfigManager.CONFIG.clears[id]
            if (clearConfig == null) {
                Utils.sendLocalized(ctx.source, "command.error.unknown_clear", mapOf("id" to id), error = true)
                return 0
            }
            if (!clearConfig.enabled) {
                Utils.sendLocalized(ctx.source, "command.error.clear_disabled", mapOf("id" to id), error = true)
                return 0
            }

            val clearTask = SkiesClear.INSTANCE.clearManager.getClearTask(id)
            if (clearTask == null) {
                Utils.sendLocalized(ctx.source, "command.error.task_missing", mapOf("id" to id), error = true)
                return 0
            }

            val cleared = clearTask.runClear(ctx.source.server, announce)
            clearTask.resetTimer()

            val locale = I18n.localeFor(ctx.source.player as? ServerPlayer)
            Utils.sendLocalized(
                ctx.source,
                "command.force.success",
                mapOf(
                    "id" to id,
                    "clear" to clearTask.displayName(locale),
                    "amount" to cleared.toString(),
                    "entities" to cleared.toString()
                )
            )
            return 1
        }
    }
}
