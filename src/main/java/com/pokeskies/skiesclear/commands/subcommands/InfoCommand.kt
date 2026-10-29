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

class InfoCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("info")
            .requires(Permissions.require("skiesclear.command.info", 4))
            .executes { ctx -> showAll(ctx.source) }
            .then(
                Commands.argument("id", StringArgumentType.string())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(ConfigManager.CONFIG.clears.keys, builder)
                    }
                    .executes { ctx -> showOne(ctx, StringArgumentType.getString(ctx, "id")) }
            )
            .build()
    }

    companion object {
        /** `/skiesclear info <id>` — details for a single entry. */
        private fun showOne(ctx: CommandContext<CommandSourceStack>, id: String): Int {
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

            val locale = I18n.localeFor(ctx.source.player as? ServerPlayer)
            val placeholders = clearTask.placeholders(0, emptyMap(), null, locale)
            Utils.sendLines(ctx.source, clearConfig.messages.info, placeholders)
            Utils.sendLocalized(
                ctx.source,
                "command.info.summary",
                placeholders + mapOf(
                    "id" to id,
                    "dimensions" to dimensionList(clearConfig.dimensions, locale),
                    "enabled" to clearConfig.enabled.toString(),
                    "interval" to clearConfig.interval.toString()
                )
            )
            return 1
        }

        /** `/skiesclear info` — one line per enabled entry. */
        private fun showAll(source: CommandSourceStack): Int {
            val enabled = ConfigManager.CONFIG.clears.filterValues { it.enabled }
            if (enabled.isEmpty()) {
                Utils.sendLocalized(source, "command.error.no_clears", error = true)
                return 0
            }

            val locale = I18n.localeFor(source.player as? ServerPlayer)
            var shown = 0
            for ((id, clearConfig) in enabled) {
                val clearTask = SkiesClear.INSTANCE.clearManager.getClearTask(id) ?: continue
                Utils.sendLines(
                    source,
                    clearConfig.messages.info,
                    clearTask.placeholders(0, emptyMap(), null, locale)
                )
                shown++
            }

            if (shown == 0) {
                Utils.sendLocalized(source, "command.error.no_clears", error = true)
                return 0
            }
            return shown
        }

        private fun dimensionList(dimensions: List<String>, locale: String?): String {
            if (dimensions.isEmpty()) return I18n.translate("command.info.all_dimensions", locale)
            return dimensions.joinToString(", ")
        }
    }
}
