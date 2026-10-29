/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.client.config

import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config

/**
 * Client-side preferences, stored next to the other Cloth Config files as `config/skiesclear-client.json`.
 *
 * These are purely cosmetic and never travel to a server. Anything that changes gameplay lives in the
 * server config (`config/skiesclear/config.json`) and is edited by [com.pokeskies.skiesclear.client.SkiesClearConfigScreen].
 */
@Config(name = SkiesClearClientConfig.FILE_NAME)
class SkiesClearClientConfig : ConfigData {

    /** Show the explanatory tooltip under every entry. */
    var showTooltips: Boolean = true

    /** Ask Cloth Config for a confirmation prompt when leaving with unsaved edits. */
    var confirmOnSave: Boolean = true

    /** Locale used by the settings screen itself; `auto` follows the Minecraft language. */
    var screenLanguage: String = AUTO

    override fun validatePostLoad() {
        if (screenLanguage.isBlank()) screenLanguage = AUTO
        screenLanguage = screenLanguage.trim().lowercase().replace('-', '_')
    }

    companion object {
        /** Passed to `@Config(name = …)`; Cloth Config appends `.json` and puts it in the config folder. */
        const val FILE_NAME: String = "skiesclear-client"

        /** Follow whatever language Minecraft is set to. */
        const val AUTO: String = "auto"
    }
}
