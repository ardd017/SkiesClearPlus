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

/**
 * `/skiesclear language` inspects or changes the global language.
 *
 * With `language: auto` (the default) every player is already served in their own client language,
 * so this command is only needed when an owner wants to pin one language for the whole server.
 */
class LanguageCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("language")
            .requires(Permissions.require("skiesclear.command.language", 3))
            .executes(Companion::show)
            .then(
                Commands.argument("locale", StringArgumentType.word())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(I18n.supportedLocales + AUTO, builder)
                    }
                    .executes { ctx -> set(ctx, StringArgumentType.getString(ctx, "locale")) }
            )
            .build()
    }

    companion object {
        private const val AUTO = "auto"

        private fun show(ctx: CommandContext<CommandSourceStack>): Int {
            val configured = ConfigManager.CONFIG.language
            val locale = I18n.localeFor(ctx.source.player as? ServerPlayer)
            Utils.sendLocalized(
                ctx.source,
                "command.language.current",
                mapOf(
                    "configured" to configured,
                    "effective" to I18n.effective(locale),
                    "available" to I18n.supportedLocales.joinToString(", ")
                )
            )
            return I18n.supportedLocales.size
        }

        private fun set(ctx: CommandContext<CommandSourceStack>, requested: String): Int {
            val value = requested.trim()
            val resolved = if (value.equals(AUTO, ignoreCase = true)) {
                AUTO
            } else {
                I18n.normalize(value)
            }

            // Every locale falls back to en_us, so `isSupported` alone would accept `kek`; the
            // selectable set is what actually distinguishes a real regional alias from a typo.
            if (resolved != AUTO && !I18n.supportedLocales.contains(resolved)) {
                Utils.sendLocalized(
                    ctx.source,
                    "command.language.unknown",
                    mapOf(
                        "locale" to value,
                        "available" to I18n.supportedLocales.joinToString(", ")
                    ),
                    error = true
                )
                return 0
            }

            ConfigManager.CONFIG.language = resolved
            ConfigManager.saveFile("config.json", ConfigManager.CONFIG)
            SkiesClear.INSTANCE.reload()

            Utils.sendLocalized(ctx.source, "command.language.changed", mapOf("locale" to resolved))
            return 1
        }
    }
}
