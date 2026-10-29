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

import com.pokeskies.skiesclear.config.ClearConfig
import com.pokeskies.skiesclear.config.clearables.Clearable
import com.pokeskies.skiesclear.utils.I18n
import com.pokeskies.skiesclear.utils.Utils
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * Drives one entry from `config.json`.
 *
 * Everything that can be resolved ahead of time is resolved in the constructor: the sound ids, the
 * set of seconds at which a warning fires, and the placeholder keys contributed by each clearable.
 * `tick` runs about once a second per task, so the hot path is one `Int` set lookup and, only on a
 * warning second, one map lookup.
 *
 * Text is rendered per reader. Placeholder values such as `%time_remaining%` are built inside the
 * rendering loop because "5 minutes" differs per language.
 */
class ClearTask(
    /** The key this entry has in `clears`, exposed as the `{id}` placeholder. */
    val clearId: String,
    val clearConfig: ClearConfig
) {
    private var dimensions: List<ServerLevel>? = null
    private var timer: Int = clearConfig.interval

    /**
     * The interval this task was built from, frozen at construction time.
     *
     * Cannot be read back off [clearConfig]: the settings menus mutate that very object in place, so
     * by the time a reload happens the old task's `clearConfig.interval` already holds the new value
     * and a before/after comparison would always look unchanged. See [carryOverTimerFrom].
     */
    private val baseInterval: Int = clearConfig.interval

    private val clearSound: SoundEvent? = resolveSound(clearConfig.sounds.clear)

    /** Warning sounds keyed by the seconds remaining at which they fire. */
    private val warningSounds: Map<Int, Pair<SoundEvent, ClearConfig.Sounds.SoundSettings>> =
        buildMap {
            for ((rawKey, settings) in clearConfig.sounds.warnings) {
                val at = rawKey.toIntOrNull() ?: continue
                val event = resolveSound(settings) ?: continue
                put(at, event to settings)
            }
        }

    private val warningMessageKeys: Set<Int> =
        clearConfig.messages.warnings.keys.mapNotNullTo(LinkedHashSet()) { it.toIntOrNull() }

    /** Every second at which *something* happens, so idle ticks stay cheap. */
    private val warningTicks: Set<Int> = buildSet {
        addAll(warningMessageKeys)
        addAll(warningSounds.keys)
        addAll(clearConfig.warningSeconds)
    }

    // ------------------------------------------------------------------ clearing

    fun runClear(server: MinecraftServer, broadcast: Boolean): Int {
        val levels = getLevels(server)
        val totals = LinkedHashMap<Clearable<*>, Int>(clearConfig.clearables.size)
        for ((_, clearable) in clearConfig.clearables) {
            totals[clearable] = clearable.clearEntities(clearConfig, levels)
        }
        val total = totals.values.sum()
        if (!broadcast) return total

        val lines = clearConfig.messages.clear
        if (lines.isEmpty() && clearSound == null) return total

        val recipients = server.playerList.players.filter { shouldInform(it) }
        if (recipients.isEmpty()) return total

        Utils.broadcast(recipients, lines) { locale ->
            placeholders(total, totals, clearConfig.interval.toLong(), locale)
        }

        val soundSettings = clearConfig.sounds.clear
        val soundEvent = clearSound
        if (soundEvent != null && soundSettings != null) {
            for (player in recipients) {
                player.playNotifySound(soundEvent, SoundSource.MASTER, soundSettings.volume, soundSettings.pitch)
            }
        }
        return total
    }

    /**
     * One second of the countdown.
     *
     * Warnings fire on the second they are configured for, *before* the tick that consumes it, so
     * `warning_seconds: [3]` announces "3 seconds left" with exactly three seconds to go.
     *
     * The clear fires on the tick that takes the counter from `0` to `-1`, not the one that brings
     * it to `0`. Previously the two were separate ticks, which made every interval one second
     * longer than configured (a 5 s entry cleared every 119 ticks instead of 100).
     */
    fun tick(server: MinecraftServer) {
        if (timer in warningTicks) dispatchWarning(server)

        if (timer <= 0) {
            resetTimer()
            runClear(server, true)
            return
        }

        timer--
        if (timer <= 0) {
            resetTimer()
            runClear(server, true)
        }
    }

    private fun dispatchWarning(server: MinecraftServer) {
        // An explicit override wins; otherwise the shared `default` template is used for every
        // second this task is configured to warn on.
        //
        // The old condition was `timer in warningMessageKeys`, i.e. only seconds that already had a
        // `messages.warnings` entry got the fallback — which is circular. A stock config with
        // `warning_seconds: [60, 30, 10, 5, 3, 1]` therefore stayed completely mute at 60, 30, 3
        // and 1 second and only spoke at 10 and 5. Any second in `warningTicks` now warns.
        val lines = clearConfig.messages.warnings[timer.toString()]
            ?: clearConfig.messages.warnings[DEFAULT_WARNING_KEY]
            ?: if (timer in warningTicks) ClearConfig.DEFAULT_WARNING else null
        val sound = warningSounds[timer]
        if (lines == null && sound == null) return

        val recipients = server.playerList.players.filter { shouldInform(it) }
        // Debug-only breadcrumb: without it a working warning with nobody online is
        // indistinguishable from a warning that never fired.
        Utils.printDebug("Warning at ${timer}s for '$clearId' to ${recipients.size} player(s)")
        if (recipients.isEmpty()) return

        if (lines != null) {
            Utils.broadcast(recipients, lines) { locale ->
                placeholders(0, emptyMap(), timer.toLong(), locale)
            }
        }

        if (sound != null) {
            val (event, settings) = sound
            for (player in recipients) {
                player.playNotifySound(event, SoundSource.MASTER, settings.volume, settings.pitch)
            }
        }
    }

    // ------------------------------------------------------------------ placeholders

    /**
     * Builds the placeholder map for one reader.
     *
     * @param total      how many objects the clear removed
     * @param cleared    per-clearable counts, keyed by [Clearable.getPlaceholderKey]
     * @param remaining  seconds shown through `%time_remaining%`; defaults to the live timer
     * @param locale     language used for the human-readable duration
     */
    fun placeholders(
        total: Int,
        cleared: Map<Clearable<*>, Int>,
        remaining: Long? = null,
        locale: String? = null
    ): Map<String, String> {
        val resolved = locale ?: I18n.serverLocale()
        val seconds = remaining ?: timer.toLong()
        val map = LinkedHashMap<String, String>(cleared.size + 8)
        val name = displayName(resolved)
        map["clear"] = name
        // `id` is the config key, not the display name: `command.info.summary` renders
        // "id {id}", and before this fix that line showed the display name twice.
        map["id"] = clearId
        map["clear_amount"] = total.toString()
        map["clear_time"] = Utils.getFormattedTime(clearConfig.interval.toLong(), resolved)
        map["interval_seconds"] = clearConfig.interval.toString()
        map["time_remaining"] = Utils.getFormattedTime(seconds, resolved)
        map["remaining_seconds"] = seconds.toString()
        for ((clearable, amount) in cleared) {
            map[clearable.getPlaceholderKey()] = amount.toString()
        }
        // Clearables that were not part of this clear still resolve, so a template never shows `{key}`.
        for ((_, clearable) in clearConfig.clearables) {
            map.putIfAbsent(clearable.getPlaceholderKey(), "0")
        }
        return map
    }

    /**
     * Display name of this entry.
     *
     * `display_name` in the config wins. When it is absent we fall back to the localised
     * `<lang:message.clear.unnamed>` label instead of a hardcoded English word, so an entry without
     * a name still reads correctly for every player.
     */
    fun displayName(locale: String? = null): String {
        val configured = clearConfig.displayName
        if (configured.isNotBlank()) return configured
        return I18n.raw(DEFAULT_NAME_KEY, locale) ?: DEFAULT_NAME_FALLBACK
    }

    // ------------------------------------------------------------------ state

    /**
     * Levels this entry may clear in.
     *
     * An empty `dimensions` list means "every dimension" and is resolved once. A non-empty list
     * that matches nothing is a configuration error, not a synonym for "all": a single typo used to
     * silently widen a Nether-only entry to the whole server, which is the most destructive failure
     * mode this class has. That case keeps clearing *nothing* and says so loudly, so the operator
     * notices instead of finding their overworld emptied.
     */
    fun getLevels(server: MinecraftServer): List<ServerLevel> {
        dimensions?.let { return it }

        val configured = clearConfig.dimensions
        val resolved = if (configured.isEmpty()) {
            server.allLevels.toList()
        } else {
            server.allLevels.filter { configured.contains(it.dimension().location().toString()) }
        }

        if (resolved.isEmpty() && configured.isNotEmpty()) {
            // Not cached: a datapack or another mod may register the dimension later.
            warnUnresolvedDimensions(server, configured)
            return emptyList()
        }

        dimensions = resolved
        return resolved
    }

    private fun warnUnresolvedDimensions(server: MinecraftServer, configured: List<String>) {
        val known = server.allLevels.joinToString(", ") { it.dimension().location().toString() }
        Utils.printWarn(
            "Clear entry '$clearId' lists dimensions that do not exist: ${configured.joinToString(", ")}. " +
                "Nothing will be cleared for this entry. Known dimensions: $known"
        )
    }

    fun shouldInform(player: ServerPlayer): Boolean {
        if (!clearConfig.informDimensionsOnly) return true
        if (clearConfig.dimensions.isEmpty()) return true
        return player.level().dimension().location().toString() in clearConfig.dimensions
    }

    fun getTimer(): Int = timer

    fun resetTimer() {
        timer = clearConfig.interval
    }

    /**
     * Keeps the countdown from [previous] when the interval is unchanged.
     *
     * [ClearManager.reload] rebuilds every task object, which would otherwise restart the timer on
     * a language switch or a settings save. A changed interval is a deliberate request for a new
     * cadence, so that case starts over; a shortened interval also has to start over, otherwise the
     * remaining time could exceed the new ceiling.
     *
     * The comparison uses [baseInterval] on both sides — the live `clearConfig.interval` is the same
     * mutated object for both tasks by the time this runs.
     */
    fun carryOverTimerFrom(previous: ClearTask) {
        if (previous === this) return
        if (previous.baseInterval != baseInterval) return
        timer = previous.timer.coerceIn(0, baseInterval)
    }

    // ------------------------------------------------------------------ helpers

    private fun resolveSound(settings: ClearConfig.Sounds.SoundSettings?): SoundEvent? {
        if (settings == null || settings.sound.isEmpty()) return null
        return try {
            SoundEvent.createVariableRangeEvent(ResourceLocation.parse(settings.sound))
        } catch (e: Exception) {
            Utils.printWarn("Invalid sound id '${settings.sound}': ${e.message}")
            null
        }
    }

    companion object {
        /** Config key used for the shared warning template when a specific second has no override. */
        const val DEFAULT_WARNING_KEY: String = "default"

        /** Used when an entry has no `display_name`, so `%clear%` is never empty. */
        private const val DEFAULT_NAME_KEY: String = "message.clear.unnamed"

        /** Last-resort label when even the language file is unreadable. */
        private const val DEFAULT_NAME_FALLBACK: String = "unnamed"
    }
}
