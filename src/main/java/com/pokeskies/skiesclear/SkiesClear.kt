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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokeskies.skiesclear.commands.BaseCommand
import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.utils.Announcer
import com.pokeskies.skiesclear.utils.CompoundTagAdaptor
import com.pokeskies.skiesclear.utils.I18n
import com.pokeskies.skiesclear.utils.PlayerTracker
import com.pokeskies.skiesclear.utils.Utils
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import org.apache.logging.log4j.LogManager
import java.io.File

class SkiesClear : ModInitializer {
    companion object {
        lateinit var INSTANCE: SkiesClear
            private set

        const val MOD_ID = "skiesclear"
        const val MOD_NAME = "SkiesClear Plus"

        val LOGGER = LogManager.getLogger(MOD_ID)
    }

    lateinit var configDir: File
        private set

    var adventure: FabricServerAudiences? = null
        private set
    var server: MinecraftServer? = null
        private set

    lateinit var clearManager: ClearManager
        private set

    val gson: Gson = GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(ResourceLocation::class.java, Utils.ResourceLocationSerializer())
        .registerTypeHierarchyAdapter(SoundEvent::class.java, Utils.RegistrySerializer(BuiltInRegistries.SOUND_EVENT))
        .registerTypeAdapter(CompoundTag::class.java, CompoundTagAdaptor())
        .create()

    val gsonPretty: Gson = gson.newBuilder().setPrettyPrinting().create()

    override fun onInitialize() {
        INSTANCE = this

        configDir = File(FabricLoader.getInstance().configDirectory, MOD_ID)
        ConfigManager.load()
        PlayerTracker.reload()

        clearManager = ClearManager()

        ServerLifecycleEvents.SERVER_STARTING.register(ServerStarting { server ->
            adventure = FabricServerAudiences.of(server)
            this.server = server
        })
        ServerLifecycleEvents.SERVER_STARTED.register(ServerStarted { server ->
            // Fires after `Done`, so the task list is final and the console gets one clear summary.
            try {
                Announcer.announceStartup(server)
            } catch (e: Exception) {
                Utils.printError("Failed to announce the clear schedule on startup: $e")
            }
        })
        ServerLifecycleEvents.SERVER_STOPPED.register(ServerStopped { _ ->
            adventure = null
            server = null
        })
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            try {
                Announcer.announceJoin(handler.player)
            } catch (e: Exception) {
                Utils.printError("Failed to announce the clear schedule to ${handler.player.name.string}: $e")
            }
        }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            BaseCommand().register(dispatcher)
        }

        Utils.printInfo("${MOD_NAME} initialised with ${I18n.availableLocales.size} language(s) available.")
    }

    /** Re-reads `config.json` and the language files, then rebuilds every clear task. */
    fun reload() {
        ConfigManager.load()
        clearManager.reload()
    }
}
