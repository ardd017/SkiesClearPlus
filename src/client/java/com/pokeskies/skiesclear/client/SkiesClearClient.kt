/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Client entry point.
 *
 * The mod itself is server-sided: every message is rendered on the server and sent as a component.
 * This entry point exists purely to wire the optional client-side integrations:
 *
 *  - Mod Menu, which supplies the gear button in the mod list and asks [SkiesClearModMenu] for a
 *    config screen.
 *  - Cloth Config, which provides the actual widgets for that screen.
 *
 * Both are compile-only dependencies. If either one is missing the whole class is never touched,
 * because nothing outside of [SkiesClearModMenu] references it and the `modmenu` entrypoint is only
 * invoked by Mod Menu itself.
 *
 * This is deliberately a `class` rather than a Kotlin `object`: a `object` compiles to a class with
 * a private constructor, and Fabric's default entrypoint adapter instantiates entrypoints through
 * `Constructor.newInstance()`, which then throws `IllegalAccessException` and crashes the client
 * during "Initializing game". Everything static lives in the companion object instead.
 */
class SkiesClearClient : ClientModInitializer {

    override fun onInitializeClient() {
        val cloth = FabricLoader.getInstance().isModLoaded(CLOTH_CONFIG_ID)
        val modMenu = FabricLoader.getInstance().isModLoaded(MOD_MENU_ID)
        LOGGER.info(
            "[SkiesClear Plus] Client integrations: cloth-config={}, modmenu={}",
            if (cloth) "yes" else "no",
            if (modMenu) "yes" else "no",
        )
    }

    companion object {
        const val CLOTH_CONFIG_ID: String = "cloth-config"
        const val MOD_MENU_ID: String = "modmenu"

        val LOGGER: Logger = LoggerFactory.getLogger("skiesclear-client")

        /** True when Cloth Config is present, so a config screen can actually be built. */
        fun canBuildConfigScreen(): Boolean = FabricLoader.getInstance().isModLoaded(CLOTH_CONFIG_ID)
    }
}
