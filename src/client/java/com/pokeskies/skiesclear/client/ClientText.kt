/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.client

import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.client.config.SkiesClearClientConfig
import com.pokeskies.skiesclear.utils.I18n
import net.kyori.adventure.platform.fabric.FabricClientAudiences
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Client-side twin of `Utils.deserializeText`.
 *
 * The common helper renders through `FabricServerAudiences`, which only exists once a server has
 * started. A settings screen has to draw before that, so this object keeps its own
 * `FabricClientAudiences` instance and produces the same `Component` chain:
 * expand `<lang:...>` tags, substitute placeholders, then parse MiniMessage.
 */
object ClientText {

    private val audiences: FabricClientAudiences by lazy { FabricClientAudiences.of() }

    private val fallback: String = I18n.DEFAULT_LOCALE

    /** Locale used to draw the settings screen: the client preference, else Minecraft's language. */
    fun screenLocale(): String {
        val configured = runCatching { SkiesClearClientConfigHolder.config.screenLanguage }.getOrNull()
        if (configured != null && !configured.equals(SkiesClearClientConfig.AUTO, ignoreCase = true)) {
            return I18n.effective(configured)
        }
        val minecraft = runCatching { Minecraft.getInstance().languageManager.selected }.getOrNull()
        return I18n.effective(minecraft ?: fallback)
    }

    /** Locale the mod's own messages use, mirroring `I18n.serverLocale()` but without the server. */
    fun configLocale(): String {
        val configured = runCatching {
            if (ConfigManager.isLoaded) ConfigManager.CONFIG.language else SkiesClearClientConfig.AUTO
        }.getOrNull() ?: SkiesClearClientConfig.AUTO
        if (configured.equals(SkiesClearClientConfig.AUTO, ignoreCase = true)) return screenLocale()
        return I18n.effective(configured)
    }

    /** Translated, MiniMessage-parsed component for [key]. Never throws; falls back to the key. */
    fun of(key: String, placeholders: Map<String, String> = emptyMap(), locale: String? = null): Component {
        val resolved = locale ?: screenLocale()
        return raw(I18n.translate(key, resolved, placeholders), resolved)
    }

    /** Parses an already-resolved string (a config value holding MiniMessage, for instance). */
    fun raw(text: String, locale: String? = null): Component {
        val resolved = locale ?: screenLocale()
        val expanded = I18n.applyPlaceholders(I18n.expandLangTags(text, resolved), emptyMap())
        return runCatching { audiences.toNative(com.pokeskies.skiesclear.utils.Utils.miniMessage.deserialize(expanded)) }
            .getOrElse { Component.literal(expanded) }
    }

    /** Same as [of], but plain text -- handy for `setTextGetter` and friends. */
    fun plain(key: String, placeholders: Map<String, String> = emptyMap(), locale: String? = null): String {
        val resolved = locale ?: screenLocale()
        val line = I18n.translate(key, resolved, placeholders)
        return I18n.applyPlaceholders(I18n.expandLangTags(line, resolved), emptyMap())
    }
}
