/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.client

import com.pokeskies.skiesclear.SkiesClear
import com.pokeskies.skiesclear.client.config.SkiesClearClientConfig
import com.pokeskies.skiesclear.config.ClearConfig
import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.config.clearables.Clearable
import com.pokeskies.skiesclear.utils.I18n
import com.pokeskies.skiesclear.utils.PlayerTracker
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.File

/**
 * The Mod Menu gear screen.
 *
 * It edits the *server* config (`config/skiesclear/config.json`) in place through
 * [ConfigManager.CONFIG], which the mod's main entrypoint already loaded on the client. The file is
 * only written when the player presses Save, so the screen can be closed without side effects.
 *
 * Client-only preferences live in a second, AutoConfig-managed file and appear in their own
 * sub-category so the two never get mixed up.
 */
object SkiesClearConfigScreen {

    /**
     * Whether explanatory tooltips are drawn. Assigned once per [build] call from the client
     * preference; the screen is created and consumed synchronously so there is no cross-thread use.
     */
    private var tooltipsEnabled: Boolean = true

    fun build(parent: Screen?): Screen {
        val builder = ConfigBuilder.create()
        builder.setParentScreen(parent)
        builder.setTitle(ClientText.of("menu.title"))

        val client = SkiesClearClientConfigHolder.holderOrRegister().config
        tooltipsEnabled = client.showTooltips
        builder.setDoesConfirmSave(client.confirmOnSave)

        val entries = builder.entryBuilder()
        addGeneralCategory(builder, entries, client)
        addBroadcastCategory(builder, entries)
        addClearCategories(builder, entries)

        builder.setSavingRunnable { save() }
        return builder.build()
    }

    /**
     * Tooltip payload for a builder. Returns an empty Optional when the player turned tooltips off,
     * which is what makes [SkiesClearClientConfig.showTooltips] actually do something.
     */
    private fun tips(
        key: String,
        placeholders: Map<String, String> = emptyMap(),
    ): java.util.Optional<Array<Component>> =
        if (tooltipsEnabled) {
            java.util.Optional.of(arrayOf(ClientText.of(key, placeholders)))
        } else {
            java.util.Optional.empty()
        }

    /** Global switches plus the client-only preferences. */
    private fun addGeneralCategory(
        builder: ConfigBuilder,
        entries: ConfigEntryBuilder,
        client: SkiesClearClientConfig,
    ) {
        val category = builder.getOrCreateCategory(ClientText.of("menu.category.general"))

        if (!ConfigManager.isLoaded) {
            category.addEntry(
                entries.startTextDescription(ClientText.of("menu.error.not_loaded"))
                    .build(),
            )
            return
        }

        val config = ConfigManager.CONFIG

        category.addEntry(
            entries.startBooleanToggle(ClientText.of("menu.general.debug"), config.debug)
                .setDefaultValue(false)
                .setTooltip(tips("menu.general.debug.tooltip"))
                .setSaveConsumer { config.debug = it }
                .build(),
        )

        val languageOptions = listOf(SkiesClearClientConfig.AUTO) + I18n.supportedLocales
        category.addEntry(
            entries.startStringDropdownMenu(
                ClientText.of("menu.general.language"),
                I18n.normalize(config.language),
            )
                .setSelections(languageOptions)
                .setDefaultValue(SkiesClearClientConfig.AUTO)
                .setTooltip(tips("menu.general.language.tooltip"))
                .setSaveConsumer { config.language = it }
                .build(),
        )

        category.addEntry(
            entries.startTextDescription(
                ClientText.of("menu.general.file", mapOf("path" to configFilePath())),
            ).build(),
        )

        val clientCategory = entries.startSubCategory(ClientText.of("menu.category.client"))
        clientCategory.setExpanded(true)

        clientCategory.add(
            entries.startBooleanToggle(ClientText.of("menu.client.show_tooltips"), client.showTooltips)
                .setDefaultValue(true)
                .setTooltip(tips("menu.client.show_tooltips.tooltip"))
                .setSaveConsumer {
                    client.showTooltips = it
                    SkiesClearClientConfigHolder.save()
                }
                .build(),
        )
        clientCategory.add(
            entries.startBooleanToggle(ClientText.of("menu.client.confirm_on_save"), client.confirmOnSave)
                .setDefaultValue(true)
                .setTooltip(tips("menu.client.confirm_on_save.tooltip"))
                .setSaveConsumer {
                    client.confirmOnSave = it
                    SkiesClearClientConfigHolder.save()
                }
                .build(),
        )
        clientCategory.add(
            entries.startStringDropdownMenu(
                ClientText.of("menu.client.screen_language"),
                client.screenLanguage,
            )
                .setSelections(listOf(SkiesClearClientConfig.AUTO) + I18n.supportedLocales)
                .setDefaultValue(SkiesClearClientConfig.AUTO)
                .setTooltip(tips("menu.client.screen_language.tooltip"))
                .setSaveConsumer {
                    client.screenLanguage = it
                    SkiesClearClientConfigHolder.save()
                }
                .build(),
        )

        category.addEntry(clientCategory.build())
    }

    /**
     * Join / startup announcements. These live in `config.json` next to the clear entries, so the
     * toggles edit the live [BroadcastConfig] and the wording stays in the language files.
     */
    private fun addBroadcastCategory(builder: ConfigBuilder, entries: ConfigEntryBuilder) {
        if (!ConfigManager.isLoaded) return

        val category = builder.getOrCreateCategory(ClientText.of("menu.category.broadcast"))
        val broadcast = ConfigManager.CONFIG.broadcast
        val tooltip = tips("menu.broadcast.tooltip")

        category.addEntry(
            entries.startBooleanToggle(ClientText.of("menu.broadcast.startup"), broadcast.startup)
                .setDefaultValue(true)
                .setTooltip(tooltip)
                .setSaveConsumer { broadcast.startup = it }
                .build(),
        )
        category.addEntry(
            entries.startBooleanToggle(ClientText.of("menu.broadcast.join"), broadcast.join)
                .setDefaultValue(true)
                .setTooltip(tooltip)
                .setSaveConsumer { broadcast.join = it }
                .build(),
        )
        category.addEntry(
            entries.startBooleanToggle(ClientText.of("menu.broadcast.first_join"), broadcast.firstJoin)
                .setDefaultValue(true)
                .setTooltip(tooltip)
                .setSaveConsumer { broadcast.firstJoin = it }
                .build(),
        )
        category.addEntry(
            entries.startTextDescription(
                ClientText.of("menu.broadcast.known", mapOf("count" to PlayerTracker.count().toString())),
            ).build(),
        )
    }

    /** One category per clear entry, with its switches, interval, dimensions and clearables. */
    private fun addClearCategories(builder: ConfigBuilder, entries: ConfigEntryBuilder) {
        if (!ConfigManager.isLoaded) return

        val clears = ConfigManager.CONFIG.clears
        if (clears.isEmpty()) {
            builder.getOrCreateCategory(ClientText.of("menu.category.general"))
                .addEntry(entries.startTextDescription(ClientText.of("menu.error.no_entries")).build())
            return
        }

        for ((id, entry) in clears) {
            val label = entry.displayName.ifBlank { id }
            val category = builder.getOrCreateCategory(
                ClientText.of("menu.category.entry", mapOf("id" to id, "name" to label)),
            )
            addEntryFields(category, entries, id, entry)
        }
    }

    private fun addEntryFields(
        category: ConfigCategory,
        entries: ConfigEntryBuilder,
        id: String,
        entry: ClearConfig,
    ) {
        category.addEntry(
            entries.startBooleanToggle(ClientText.of("menu.entry.enabled"), entry.enabled)
                .setDefaultValue(true)
                .setTooltip(tips("menu.entry.enabled.tooltip", mapOf("id" to id)))
                .setSaveConsumer { entry.enabled = it }
                .build(),
        )

        category.addEntry(
            entries.startIntSlider(
                ClientText.of("menu.entry.interval"),
                entry.interval,
                ClearConfig.MIN_INTERVAL,
                ClearConfig.MAX_INTERVAL,
            )
                .setDefaultValue(ClearConfig.DEFAULT_INTERVAL)
                .setTextGetter { seconds ->
                    ClientText.of(
                        "menu.entry.interval.value",
                        mapOf(
                            "seconds" to seconds.toString(),
                            "formatted" to com.pokeskies.skiesclear.utils.Utils
                                .getFormattedTime(seconds.toLong(), ClientText.screenLocale()),
                        ),
                    )
                }
                .setTooltip(tips("menu.entry.interval.tooltip"))
                .setSaveConsumer { entry.interval = it }
                .build(),
        )

        // Precise entry for the same value. Cloth runs save consumers in entry order, so this field is
        // added *after* the slider and therefore wins when a player edits both in one pass. The slider
        // is click-and-drag imprecise; typing "450" into a field is not.
        category.addEntry(
            entries.startIntField(ClientText.of("menu.entry.interval.manual"), entry.interval)
                .setDefaultValue(ClearConfig.DEFAULT_INTERVAL)
                .setMin(ClearConfig.MIN_INTERVAL)
                .setMax(ClearConfig.MAX_INTERVAL)
                .setTooltip(tips("menu.entry.interval.manual.tooltip"))
                .setErrorSupplier { seconds ->
                    if (seconds in ClearConfig.MIN_INTERVAL..ClearConfig.MAX_INTERVAL) {
                        java.util.Optional.empty()
                    } else {
                        java.util.Optional.of(
                            ClientText.of(
                                "menu.entry.interval.error",
                                mapOf(
                                    "min" to ClearConfig.MIN_INTERVAL.toString(),
                                    "max" to ClearConfig.MAX_INTERVAL.toString(),
                                ),
                            ),
                        )
                    }
                }
                .setSaveConsumer { entry.interval = it }
                .build(),
        )

        category.addEntry(
            entries.startIntField(ClientText.of("menu.entry.grace"), entry.graceSeconds)
                .setDefaultValue(ClearConfig.DEFAULT_GRACE_SECONDS)
                .setMin(0)
                .setMax(ClearConfig.MAX_GRACE_SECONDS)
                .setTooltip(tips("menu.entry.grace.tooltip"))
                .setErrorSupplier { seconds ->
                    if (seconds in 0..ClearConfig.MAX_GRACE_SECONDS) {
                        java.util.Optional.empty()
                    } else {
                        java.util.Optional.of(
                            ClientText.of(
                                "menu.entry.grace.error",
                                mapOf(
                                    "min" to "0",
                                    "max" to ClearConfig.MAX_GRACE_SECONDS.toString(),
                                ),
                            ),
                        )
                    }
                }
                .setSaveConsumer { entry.graceSeconds = it }
                .build(),
        )

        category.addEntry(
            entries.startStrField(ClientText.of("menu.entry.display_name"), entry.displayName)
                .setDefaultValue("")
                .setTooltip(tips("menu.entry.display_name.tooltip", mapOf("id" to id)))
                .setSaveConsumer { entry.displayName = it }
                .build(),
        )

        category.addEntry(
            entries.startStrList(ClientText.of("menu.entry.dimensions"), entry.dimensions)
                .setDefaultValue(emptyList())
                .setTooltip(tips("menu.entry.dimensions.tooltip"))
                .setSaveConsumer { entry.dimensions = it }
                .build(),
        )

        val flags = entries.startSubCategory(ClientText.of("menu.category.flags"))
        flags.setExpanded(false)
        flags.add(flag(entries, "menu.flag.persistent", entry.clearPersistent) { entry.clearPersistent = it })
        flags.add(flag(entries, "menu.flag.named", entry.clearNamed) { entry.clearNamed = it })
        flags.add(flag(entries, "menu.flag.passengers", entry.clearPassengers) { entry.clearPassengers = it })
        flags.add(
            flag(entries, "menu.flag.inform_dimensions_only", entry.informDimensionsOnly) {
                entry.informDimensionsOnly = it
            },
        )
        category.addEntry(flags.build())

        val clearables = entries.startSubCategory(ClientText.of("menu.category.clearables"))
        clearables.setExpanded(false)
        if (entry.clearables.isEmpty()) {
            clearables.add(entries.startTextDescription(ClientText.of("menu.clearable.none")).build())
        } else {
            for ((type, clearable) in entry.clearables) {
                clearables.add(buildClearable(entries, type, clearable))
            }
        }
        category.addEntry(clearables.build())
    }

    private fun flag(
        entries: ConfigEntryBuilder,
        key: String,
        current: Boolean,
        apply: (Boolean) -> Unit,
    ) = entries.startBooleanToggle(ClientText.of(key), current)
        .setDefaultValue(false)
        .setTooltip(tips("menu.flag.tooltip"))
        .setSaveConsumer { apply(it) }
        .build()

    private fun buildClearable(
        entries: ConfigEntryBuilder,
        type: String,
        clearable: Clearable<*>,
    ) = entries.startSubCategory(clearableTitle(type))
        .setExpanded(false)
        .apply {
            add(
                entries.startBooleanToggle(ClientText.of("menu.clearable.enabled"), clearable.enabled)
                    .setDefaultValue(false)
                    .setSaveConsumer { clearable.enabled = it }
                    .build(),
            )
            // The rule syntax is not guessable from the label alone, so spell it out right above the
            // two lists instead of burying it in a tooltip the player has to hover.
            add(entries.startTextDescription(ClientText.of("menu.clearable.rules.hint")).build())
            add(
                entries.startStrList(ClientText.of("menu.clearable.blacklist"), clearable.blacklist)
                    .setDefaultValue(emptyList())
                    .setTooltip(tips("menu.clearable.blacklist.tooltip"))
                    .setSaveConsumer { clearable.blacklist = it }
                    .build(),
            )
            add(
                entries.startStrList(ClientText.of("menu.clearable.whitelist"), clearable.whitelist)
                    .setDefaultValue(emptyList())
                    .setTooltip(tips("menu.clearable.whitelist.tooltip"))
                    .setSaveConsumer { clearable.whitelist = it }
                    .build(),
            )
        }
        .build()

    /**
     * Translated heading for a clearable section.
     *
     * The config keys are always the English ids (`items`, `entities`, `cobblemon`); looking up
     * `gui.clearable.name.<id>` gives the player's own wording and falls back to the raw id for
     * clearable types added by forks that we have no translation for.
     */
    private fun clearableTitle(type: String): Component {
        val name = if (I18n.has("gui.clearable.name.$type")) {
            ClientText.of("gui.clearable.name.$type")
        } else {
            Component.literal(type)
        }
        return Component.empty().append(name).append(Component.literal(" ($type)"))
    }

    /** Writes the edited config, then rebuilds the live tasks when a server is running. */
    fun save(): Boolean {
        if (!ConfigManager.isLoaded) return false
        val written = ConfigManager.saveFile(CONFIG_FILE, ConfigManager.CONFIG)
        if (!written) {
            SkiesClear.LOGGER.warn("[SkiesClear Plus] Could not write {}", configFilePath())
            return false
        }
        if (SkiesClear.INSTANCE.server != null) {
            runCatching { SkiesClear.INSTANCE.reload() }
                .onFailure { SkiesClear.LOGGER.warn("[SkiesClear Plus] Reload after save failed: {}", it.message) }
        }
        SkiesClear.LOGGER.info("[SkiesClear Plus] Saved settings via Mod Menu")
        return true
    }

    private fun configFilePath(): String = File(SkiesClear.INSTANCE.configDir, CONFIG_FILE).path

    private const val CONFIG_FILE = "config.json"
}
