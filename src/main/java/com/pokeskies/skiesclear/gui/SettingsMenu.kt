/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.gui

import com.pokeskies.skiesclear.SkiesClear
import com.pokeskies.skiesclear.config.ClearConfig
import com.pokeskies.skiesclear.config.ConfigManager
import com.pokeskies.skiesclear.config.clearables.Clearable
import com.pokeskies.skiesclear.utils.I18n
import com.pokeskies.skiesclear.utils.Utils
import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

/**
 * In-game settings screen for one clear entry.
 *
 * ### Why a chest menu and not a `Screen`
 * SkiesClear ships as a **server-side** mod: `fabric.mod.json` declares only a `main` entrypoint, so
 * there is no client code at all and no way to register a custom [net.minecraft.client.gui.screens.Screen].
 * A `MenuType.GENERIC_9x6` chest menu solves that: the server opens it with
 * [ServerPlayer.openMenu] and every vanilla client already knows how to render and drive it. That
 * means no custom `MenuType` registration, no `PayloadTypeRegistry` entries, and no client source
 * set — the menu works for every player, including vanilla ones.
 *
 * ### Interaction model
 * All 54 top slots are display-only icons and the 36 player-inventory slots are decorative here, so
 * [clicked] never delegates to `super`: nothing can be picked up, moved, shift-clicked or thrown.
 * Every click is interpreted as a button press, applied to the **live** config objects, and then the
 * whole menu is resynced with [broadcastChanges].
 *
 * Edits stay in memory until *Save* is pressed, so a player can experiment freely; *Discard* re-reads
 * `config.json` from disk and throws the edits away.
 */
class SettingsMenu(
    syncId: Int,
    private val playerInventory: Inventory,
    val entryId: String,
    viewer: Player
) : AbstractContainerMenu(MenuType.GENERIC_9x6, syncId) {

    private val icons = SimpleContainer(SLOT_COUNT)
    private var snapshot: String = ""

    init {
        // 6 rows of icon slots, then the player inventory exactly where a vanilla double chest puts
        // it, so the client-side ChestMenu (also 90 slots) lines up pixel for pixel.
        for (row in 0 until ROWS) {
            for (column in 0 until COLUMNS) {
                addSlot(IconSlot(icons, column + row * COLUMNS, 8 + column * 18, 18 + row * 18))
            }
        }
        val offset = (ROWS - 4) * 18
        for (row in 0 until 3) {
            for (column in 0 until COLUMNS) {
                addSlot(IconSlot(playerInventory, column + row * COLUMNS + 9, 8 + column * 18, 103 + row * 18 + offset))
            }
        }
        for (column in 0 until COLUMNS) {
            addSlot(IconSlot(playerInventory, column, 8 + column * 18, 161 + offset))
        }

        render(I18n.localeFor(viewer as? ServerPlayer))
        snapshot = snapshotOf()
    }

    // ------------------------------------------------------------------ rendering

    /** Repaints every icon for [locale]. Cheap enough to run once a second. */
    fun render(locale: String?) {
        val resolved = locale ?: I18n.serverLocale()
        val entry = entry()

        fill(Items.GRAY_STAINED_GLASS_PANE, resolved)

        if (entry == null) {
            icons.setItem(SLOT_TITLE, icon(
                Items.BARRIER,
                t("gui.header.name", resolved, "clear" to entryId),
                listOf(t("gui.feedback.no_entry", resolved)),
                resolved
            ))
            return
        }

        // --- header
        val remaining = SkiesClear.INSTANCE.clearManager.getClearTask(entryId)?.getTimer()?.toLong()
        val headerLore = buildList {
            add(t("gui.header.lore.id", resolved, "id" to entryId))
            add(t("gui.header.lore.enabled", resolved, "enabled" to onOff(entry.enabled, resolved)))
            if (remaining != null) {
                add(t("gui.header.lore.remaining", resolved, "time_remaining" to Utils.getFormattedTime(remaining, resolved)))
            }
            if (snapshotOf() != snapshot) add(t("gui.header.unsaved", resolved))
        }
        icons.setItem(SLOT_TITLE, icon(
            if (entry.enabled) Items.CLOCK else Items.COMPARATOR,
            t("gui.header.name", resolved, "clear" to displayName(entry, resolved)),
            headerLore,
            resolved
        ))

        // --- interval bank
        val seconds = Utils.getFormattedTime(entry.interval.toLong(), resolved)
        val intervalItem = icon(
            Items.CLOCK,
            t("gui.interval.name", resolved),
            listOf(
                t("gui.interval.lore", resolved, "interval" to seconds, "seconds" to entry.interval.toString()),
                t("gui.interval.hint", resolved)
            ),
            resolved
        )
        icons.setItem(SLOT_INTERVAL_DISPLAY, intervalItem)
        icons.setItem(SLOT_INTERVAL_DOWN_60, step(entry, -60, Items.RED_DYE, resolved))
        icons.setItem(SLOT_INTERVAL_DOWN_10, step(entry, -10, Items.GRAY_DYE, resolved))
        icons.setItem(SLOT_INTERVAL_DOWN_1, step(entry, -1, Items.LIGHT_GRAY_DYE, resolved))
        icons.setItem(SLOT_INTERVAL_UP_1, step(entry, 1, Items.LIME_DYE, resolved))
        icons.setItem(SLOT_INTERVAL_UP_10, step(entry, 10, Items.LIME_DYE, resolved))
        icons.setItem(SLOT_INTERVAL_UP_60, step(entry, 60, Items.LIME_DYE, resolved))
        icons.setItem(SLOT_INTERVAL_RESET, icon(
            Items.COMPARATOR,
            t("gui.interval.reset", resolved),
            listOf(
                t(
                    "gui.interval.reset.lore", resolved,
                    "interval" to Utils.getFormattedTime(ClearConfig.DEFAULT_INTERVAL.toLong(), resolved)
                )
            ),
            resolved
        ))

        // --- behaviour toggles
        icons.setItem(SLOT_TOGGLE_ENABLED, toggle(entry.enabled, Items.LEVER, "gui.option.enabled", resolved))
        icons.setItem(SLOT_TOGGLE_NAMED, toggle(entry.clearNamed, Items.NAME_TAG, "gui.option.named", resolved))
        icons.setItem(SLOT_TOGGLE_PERSISTENT, toggle(entry.clearPersistent, Items.ITEM_FRAME, "gui.option.persistent", resolved))
        icons.setItem(SLOT_TOGGLE_PASSENGERS, toggle(entry.clearPassengers, Items.EGG, "gui.option.passengers", resolved))
        icons.setItem(
            SLOT_TOGGLE_DIMENSIONS,
            toggle(entry.informDimensionsOnly, Items.COMPASS, "gui.option.dimensions", resolved)
        )
        icons.setItem(SLOT_CLEAR_NOW, icon(
            Items.TNT,
            t("gui.action.clear_now", resolved),
            emptyList(),
            resolved
        ))
        icons.setItem(SLOT_GRACE, graceIcon(entry, resolved))

        // --- clearables
        icons.setItem(SLOT_CLEARABLE_ITEMS, clearableIcon(entry, "items", Items.HOPPER, resolved))
        icons.setItem(SLOT_CLEARABLE_ENTITIES, clearableIcon(entry, "entities", Items.ARROW, resolved))
        if (entry.clearables.containsKey("cobblemon")) {
            icons.setItem(SLOT_CLEARABLE_COBBLEMON, clearableIcon(entry, "cobblemon", Items.NETHER_STAR, resolved))
        }

        // --- footer
        icons.setItem(SLOT_CLOSE, icon(Items.BARRIER, t("gui.action.close", resolved), emptyList(), resolved))
        icons.setItem(SLOT_DISCARD, icon(Items.PAPER, t("gui.action.discard", resolved), emptyList(), resolved))
        icons.setItem(SLOT_DEBUG, toggle(ConfigManager.CONFIG.debug, Items.BELL, "gui.action.debug", resolved))
        icons.setItem(SLOT_SAVE, icon(Items.EMERALD, t("gui.action.save", resolved), emptyList(), resolved))
        icons.setItem(
            SLOT_LANGUAGE,
            icon(
                Items.BOOK,
                t("gui.action.language", resolved),
                listOf(
                    t(
                        "gui.action.language.lore", resolved,
                        "language" to ConfigManager.CONFIG.language,
                        "effective" to I18n.effective(ConfigManager.CONFIG.language)
                    )
                ),
                resolved
            )
        )
    }

    private fun step(entry: ClearConfig, delta: Int, item: Item, locale: String): ItemStack {
        val amountKey = if (delta < 0) "gui.interval.decrease" else "gui.interval.increase"
        return icon(
            item,
            t(amountKey, locale, "amount" to Math.abs(delta).toString()),
            listOf(
                t("gui.interval.step.lore", locale, "interval" to Utils.getFormattedTime(entry.interval.toLong(), locale))
            ),
            locale
        )
    }

    /**
     * The grace-period button.
     *
     * A dedicated icon rather than another row of steppers: it is one value, and the useful range is
     * small enough that ±5 s per click is comfortable. `0` is rendered as "off" because that is what
     * it means — the guard is skipped entirely.
     */
    private fun graceIcon(entry: ClearConfig, locale: String): ItemStack {
        val active = entry.graceSeconds > 0
        val value = if (active) {
            Utils.getFormattedTime(entry.graceSeconds.toLong(), locale)
        } else {
            t("gui.value.off", locale)
        }
        return icon(
            if (active) Items.SHIELD else Items.GRAY_STAINED_GLASS_PANE,
            t("gui.grace.name", locale),
            listOf(
                t("gui.grace.lore", locale, "grace" to value, "seconds" to entry.graceSeconds.toString()),
                t("gui.grace.hint", locale, "step" to GRACE_STEP.toString())
            ),
            locale
        )
    }

    private fun toggle(value: Boolean, item: Item, optionKey: String, locale: String): ItemStack {
        val material = if (value) Items.LIME_DYE else Items.GRAY_DYE
        return icon(
            material,
            t(optionKey, locale),
            listOf(
                t("gui.toggle.lore", locale, "value" to onOff(value, locale)),
                t("gui.toggle.hint", locale)
            ),
            locale
        )
    }

    private fun clearableIcon(entry: ClearConfig, key: String, item: Item, locale: String): ItemStack {
        val clearable = entry.clearables[key]
        val name = t("gui.clearable.name.$key", locale)
        if (clearable == null) {
            return icon(item, name, listOf(t("gui.clearable.missing", locale)), locale)
        }
        return icon(
            if (clearable.enabled) item else Items.GRAY_STAINED_GLASS_PANE,
            name,
            listOf(
                t("gui.clearable.lore", locale, "value" to onOff(clearable.enabled, locale)),
                t(
                    "gui.clearable.rules", locale,
                    "blacklist" to clearable.blacklist.size.toString(),
                    "whitelist" to clearable.whitelist.size.toString()
                ),
                t("gui.toggle.hint", locale)
            ),
            locale
        )
    }

    private fun fill(item: Item, locale: String) {
        val blank = icon(item, "", emptyList(), locale)
        for (slot in 0 until SLOT_COUNT) icons.setItem(slot, blank.copy())
    }

    // ------------------------------------------------------------------ interaction

    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        val serverPlayer = player as? ServerPlayer ?: return
        if (slotId == SLOT_CLICKED_OUTSIDE) return

        if (!Permissions.check(player, PERMISSION, DEFAULT_LEVEL)) {
            notify(serverPlayer, "gui.feedback.no_permission")
            return
        }

        if (perform(slotId, serverPlayer, button)) {
            render(I18n.localeFor(serverPlayer))
            broadcastChanges()
        }
    }

    /**
     * Applies the action bound to [slotId] and reports whether anything was handled.
     *
     * Split out of [clicked] so the action table can be exercised without a permission backend,
     * and so the permission gate stays in exactly one place. A right click ([button] == 1) flips
     * the direction of the stepper and language buttons, which is cheaper than adding more icons.
     */
    internal fun perform(slotId: Int, serverPlayer: ServerPlayer, button: Int = 0): Boolean {
        val rightClick = button == 1
        val entry = entry()
        if (entry == null) {
            notify(serverPlayer, "gui.feedback.no_entry")
            return false
        }

        when (slotId) {
            SLOT_INTERVAL_DOWN_60 -> adjust(serverPlayer, entry, if (rightClick) 60 else -60)
            SLOT_INTERVAL_DOWN_10 -> adjust(serverPlayer, entry, if (rightClick) 10 else -10)
            SLOT_INTERVAL_DOWN_1 -> adjust(serverPlayer, entry, if (rightClick) 1 else -1)
            SLOT_INTERVAL_UP_1 -> adjust(serverPlayer, entry, if (rightClick) -1 else 1)
            SLOT_INTERVAL_UP_10 -> adjust(serverPlayer, entry, if (rightClick) -10 else 10)
            SLOT_INTERVAL_UP_60 -> adjust(serverPlayer, entry, if (rightClick) -60 else 60)
            SLOT_INTERVAL_RESET -> adjust(serverPlayer, entry, ClearConfig.DEFAULT_INTERVAL - entry.interval)

            SLOT_TOGGLE_ENABLED -> entry.enabled = !entry.enabled
            SLOT_TOGGLE_NAMED -> entry.clearNamed = !entry.clearNamed
            SLOT_TOGGLE_PERSISTENT -> entry.clearPersistent = !entry.clearPersistent
            SLOT_TOGGLE_PASSENGERS -> entry.clearPassengers = !entry.clearPassengers
            SLOT_TOGGLE_DIMENSIONS -> entry.informDimensionsOnly = !entry.informDimensionsOnly
            SLOT_DEBUG -> ConfigManager.CONFIG.debug = !ConfigManager.CONFIG.debug

            SLOT_CLEARABLE_ITEMS -> flip(entry, "items")
            SLOT_CLEARABLE_ENTITIES -> flip(entry, "entities")
            SLOT_CLEARABLE_COBBLEMON -> flip(entry, "cobblemon")

            SLOT_CLEAR_NOW -> clearNow(serverPlayer, entry)
            SLOT_GRACE -> adjustGrace(serverPlayer, entry, if (rightClick) -GRACE_STEP else GRACE_STEP)
            SLOT_LANGUAGE -> cycleLanguage(serverPlayer, if (rightClick) -1 else 1)
            SLOT_SAVE -> save(serverPlayer)
            SLOT_DISCARD -> discard(serverPlayer)
            SLOT_CLOSE -> serverPlayer.closeContainer()
            else -> return false
        }
        return true
    }

    private fun adjust(player: ServerPlayer, entry: ClearConfig, delta: Int) {
        if (delta == 0) return
        val updated = (entry.interval + delta).coerceIn(ClearConfig.MIN_INTERVAL, ClearConfig.MAX_INTERVAL)
        if (updated == entry.interval) return
        entry.interval = updated
        notify(player, "gui.feedback.interval", "interval" to Utils.getFormattedTime(updated.toLong(), I18n.localeFor(player)))
    }

    private fun adjustGrace(player: ServerPlayer, entry: ClearConfig, delta: Int) {
        if (delta == 0) return
        val updated = (entry.graceSeconds + delta).coerceIn(0, ClearConfig.MAX_GRACE_SECONDS)
        if (updated == entry.graceSeconds) return
        entry.graceSeconds = updated
        val locale = I18n.localeFor(player)
        val rendered = if (updated > 0) {
            Utils.getFormattedTime(updated.toLong(), locale)
        } else {
            t("gui.value.off", locale)
        }
        notify(player, "gui.feedback.grace", "grace" to rendered)
    }

    private fun flip(entry: ClearConfig, key: String) {
        val clearable: Clearable<*> = entry.clearables[key] ?: return
        clearable.enabled = !clearable.enabled
    }

    private fun clearNow(player: ServerPlayer, entry: ClearConfig) {
        val server = SkiesClear.INSTANCE.server
        if (server == null) {
            notify(player, "gui.feedback.no_entry")
            return
        }
        val task = SkiesClear.INSTANCE.clearManager.getClearTask(entryId)
        if (task == null) {
            notify(player, "command.error.clear_disabled", "id" to entryId)
            return
        }
        val cleared = task.runClear(server, true)
        notify(player, "gui.feedback.cleared", "amount" to cleared.toString(), "clear" to task.displayName(I18n.localeFor(player)))
    }

    private fun cycleLanguage(player: ServerPlayer, direction: Int) {
        val options = listOf(AUTO) + I18n.supportedLocales
        val current = ConfigManager.CONFIG.language
        val index = options.indexOfFirst { it.equals(current, ignoreCase = true) }
        val size = options.size
        val base = if (index < 0) 0 else index
        val next = options[((base + direction) % size + size) % size]
        ConfigManager.CONFIG.language = next
        notify(
            player, "gui.feedback.language",
            "language" to next,
            "effective" to I18n.effective(next)
        )
    }

    private fun save(player: ServerPlayer) {
        if (!ConfigManager.saveFile("config.json", ConfigManager.CONFIG)) {
            notify(player, "gui.feedback.save_failed")
            return
        }
        SkiesClear.INSTANCE.reload()
        snapshot = snapshotOf()
        notify(player, "gui.feedback.saved")
    }

    private fun discard(player: ServerPlayer) {
        SkiesClear.INSTANCE.reload()
        snapshot = snapshotOf()
        notify(player, "gui.feedback.discarded")
    }

    /** The live entry. Re-resolved every time because `reload()` replaces the whole config object. */
    private fun entry(): ClearConfig? = ConfigManager.CONFIG.clears[entryId]

    private fun displayName(entry: ClearConfig, locale: String): String {
        if (entry.displayName.isNotBlank()) return entry.displayName
        return I18n.raw("message.clear.unnamed", locale) ?: entryId
    }

    private fun snapshotOf(): String {
        val entry = entry() ?: return "missing"
        val flags = entry.clearables.entries.joinToString(",") { "${it.key}=${it.value.enabled}" }
        return listOf(
            entry.enabled, entry.interval, entry.graceSeconds, entry.displayName, entry.clearNamed,
            entry.clearPersistent, entry.clearPassengers, entry.informDimensionsOnly, entry.dimensions,
            ConfigManager.CONFIG.debug, ConfigManager.CONFIG.language, flags
        ).joinToString("|")
    }

    // ------------------------------------------------------------------ menu plumbing

    override fun stillValid(player: Player): Boolean = true

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    override fun removed(player: Player) {}

    // ------------------------------------------------------------------ icon helpers

    private fun icon(item: Item, name: String, lore: List<String>, locale: String): ItemStack {
        val stack = ItemStack(item)
        if (name.isNotEmpty()) stack.set(DataComponents.CUSTOM_NAME, text(name, locale))
        val lines = lore.filter { it.isNotEmpty() }.map { text(it, locale) }
        if (lines.isNotEmpty()) stack.set(DataComponents.LORE, ItemLore(lines))
        return stack
    }

    /** MiniMessage → native component, forced non-italic so custom names and lore look like UI text. */
    private fun text(raw: String, locale: String): Component {
        return Component.empty().withStyle(Style.EMPTY.withItalic(false)).append(Utils.deserializeText(raw, locale))
    }

    private fun t(key: String, locale: String, vararg args: Pair<String, String>): String {
        return I18n.translate(key, locale, args.toMap())
    }

    private fun onOff(value: Boolean, locale: String): String {
        return I18n.translate(if (value) "gui.value.on" else "gui.value.off", locale)
    }

    private fun notify(player: ServerPlayer, key: String, vararg args: Pair<String, String>) {
        val locale = I18n.localeFor(player)
        player.sendSystemMessage(Utils.deserializeText(t(key, locale, *args), locale))
    }

    /** Display-only slot: cannot be filled, emptied, swapped or shift-clicked. */
    private class IconSlot(container: net.minecraft.world.Container, index: Int, x: Int, y: Int) :
        Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = false
        override fun mayPickup(player: Player): Boolean = false
        override fun isHighlightable(): Boolean = false
    }

    companion object {
        /** Permission node and the vanilla level it falls back to (ops). */
        const val PERMISSION: String = "skiesclear.command.gui"
        const val DEFAULT_LEVEL: Int = 3

        private const val AUTO = "auto"
        private const val ROWS = 6
        private const val COLUMNS = 9
        private const val SLOT_COUNT = ROWS * COLUMNS

        private const val SLOT_TITLE = 4

        private const val SLOT_INTERVAL_DOWN_60 = 9
        private const val SLOT_INTERVAL_DOWN_10 = 10
        private const val SLOT_INTERVAL_DOWN_1 = 11
        private const val SLOT_INTERVAL_DISPLAY = 12
        private const val SLOT_INTERVAL_UP_1 = 13
        private const val SLOT_INTERVAL_UP_10 = 14
        private const val SLOT_INTERVAL_UP_60 = 15
        private const val SLOT_INTERVAL_RESET = 16

        private const val SLOT_TOGGLE_ENABLED = 18
        private const val SLOT_TOGGLE_NAMED = 19
        private const val SLOT_TOGGLE_PERSISTENT = 20
        private const val SLOT_TOGGLE_PASSENGERS = 21
        private const val SLOT_TOGGLE_DIMENSIONS = 22

        /** Grace period stepper: left click +5s, right click -5s. */
        private const val SLOT_GRACE = 23
        private const val GRACE_STEP = 5
        private const val SLOT_CLEAR_NOW = 24

        private const val SLOT_CLEARABLE_ITEMS = 28
        private const val SLOT_CLEARABLE_ENTITIES = 30
        private const val SLOT_CLEARABLE_COBBLEMON = 32

        private const val SLOT_CLOSE = 45
        private const val SLOT_DISCARD = 48
        private const val SLOT_DEBUG = 49
        private const val SLOT_SAVE = 50
        private const val SLOT_LANGUAGE = 51

        private var tickerRegistered = false
        private var tickCounter = 0

        /**
         * Opens the settings menu for [entryId], or returns `false` when that entry does not exist.
         *
         * The title is resolved in the viewer's own language, matching how every other piece of
         * mod output is rendered.
         */
        fun open(player: ServerPlayer, entryId: String): Boolean {
            if (!ConfigManager.CONFIG.clears.containsKey(entryId)) return false
            installTicker()

            val locale = I18n.localeFor(player)
            val title = Utils.deserializeText(
                I18n.translate("gui.title", locale, mapOf("id" to entryId)),
                locale
            )
            player.openMenu(SimpleMenuProvider({ syncId, inventory, viewer ->
                SettingsMenu(syncId, inventory, entryId, viewer)
            }, title))
            return true
        }

        /**
         * Keeps the live "next clear in …" line on the header icon fresh.
         *
         * A menu has no tick of its own, so this piggybacks on the server tick at the same 1 Hz
         * cadence the clear tasks use, and only repaints menus that are actually open.
         */
        private fun installTicker() {
            if (tickerRegistered) return
            tickerRegistered = true
            ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
                if (++tickCounter < 20) return@EndTick
                tickCounter = 0
                for (player in server.playerList.players) {
                    val menu = player.containerMenu
                    if (menu is SettingsMenu) {
                        menu.render(I18n.localeFor(player))
                        menu.broadcastChanges()
                    }
                }
            })
        }
    }
}
