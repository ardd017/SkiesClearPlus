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

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.utils.SubCommand
import com.pokeskies.skiesclear.utils.Utils
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class DebugCommand : SubCommand {
    override fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("debug")
            .requires(Permissions.require("skiesclear.command.debug", 4))
            .executes(Companion::toggle)
            .build()
    }

    companion object {
        fun toggle(ctx: CommandContext<CommandSourceStack>): Int {
            val newMode = !ConfigManager.CONFIG.debug
            ConfigManager.CONFIG.debug = newMode
            ConfigManager.saveFile("config.json", ConfigManager.CONFIG)

            Utils.sendLocalized(
                ctx.source,
                if (newMode) "command.debug.enabled" else "command.debug.disabled"
            )
            return 1
        }
    }
}
