/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.minecraft.client.gui.screens.Screen

/**
 * Mod Menu entry point: supplies the gear-button screen.
 *
 * Only ever loaded by Mod Menu, which is why this class may reference Cloth Config types freely --
 * if Mod Menu is absent nothing instantiates it. If Cloth Config itself is missing the factory
 * returns null, which tells Mod Menu to fall back to its "no config screen" message.
 */
class SkiesClearModMenu : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory<Screen> { parent ->
            if (SkiesClearClient.canBuildConfigScreen()) SkiesClearConfigScreen.build(parent) else null
        }
}
