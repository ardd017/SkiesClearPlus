/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * Modifications for SkiesClear Plus Copyright 2026 ardd_017, licensed under the Apache License 2.0.
 *
 * This file has been MODIFIED from the upstream SkiesClear source. See ATTRIBUTION.md for the
 * list of changes.
 */
package com.pokeskies.skiesclear.config

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.pokeskies.skiesclear.config.clearables.Clearable
import com.pokeskies.skiesclear.utils.FlexibleListAdaptorFactory

/**
 * A single clear entry.
 *
 * Message defaults are **not** English literals any more: they are `<lang:...>` references that are
 * resolved against the reader's own locale at send time, so a server with one config file serves
 * every language. Server owners can still override any line with plain MiniMessage text.
 *
 * Every field is `var` on purpose: the in-game settings menu ([com.pokeskies.skiesclear.gui.SettingsMenu])
 * edits the live config objects and then asks [ConfigManager] to persist them, so the values must be
 * mutable. Mutating them never touches the file until the menu's *Save* button runs.
 */
class ClearConfig(
    var enabled: Boolean = true,
    var interval: Int = DEFAULT_INTERVAL,

    /** Shown through the `%clear%` placeholder. Falls back to the entry id when blank. */
    @SerializedName("display_name")
    var displayName: String = "",

    @JsonAdapter(FlexibleListAdaptorFactory::class)
    var dimensions: List<String> = emptyList(),

    var messages: Messages = Messages(),
    var sounds: Sounds = Sounds(),

    /**
     * Seconds (counted down from [interval]) at which a warning is sent. Entries listed in
     * [Messages.warnings] / [Sounds.warnings] are merged in automatically, so legacy configs that
     * only declared `warnings` keep working unchanged.
     */
    @SerializedName("warning_seconds")
    var warningSeconds: List<Int> = DEFAULT_WARNING_SECONDS,

    @JsonAdapter(Clearable.MapAdapter::class)
    var clearables: Map<String, Clearable<*>> = emptyMap(),

    @SerializedName("clear_persistent")
    var clearPersistent: Boolean = false,

    /**
     * Entities younger than this many seconds are skipped.
     *
     * A clear that fires two seconds after someone drops an item deletes it before they can pick it
     * back up — the most common complaint about timed clearing. Age comes from
     * [net.minecraft.world.entity.Entity.tickCount], so it covers dropped items, freshly spawned
     * mobs and newly spawned Pokémon alike. `0` disables the guard.
     */
    @SerializedName("grace_seconds")
    var graceSeconds: Int = DEFAULT_GRACE_SECONDS,

    @SerializedName("clear_named")
    var clearNamed: Boolean = false,

    @SerializedName("clear_passengers")
    var clearPassengers: Boolean = true,

    @SerializedName("inform_dimensions_only")
    var informDimensionsOnly: Boolean = true
) {
    class Messages(
        @JsonAdapter(FlexibleListAdaptorFactory::class)
        val clear: List<String> = DEFAULT_CLEAR,

        /** Legacy-friendly overrides keyed by the number of seconds remaining. */
        val warnings: Map<String, List<String>> = emptyMap(),

        @JsonAdapter(FlexibleListAdaptorFactory::class)
        val info: List<String> = DEFAULT_INFO,
    ) {
        override fun toString(): String {
            return "Messages(clear=$clear, warnings=$warnings, info=$info)"
        }
    }

    class Sounds(
        val clear: SoundSettings? = null,
        val warnings: Map<String, SoundSettings> = emptyMap(),
    ) {
        class SoundSettings(
            val sound: String = "",
            val volume: Float = 1.0f,
            val pitch: Float = 1.0f,
        ) {
            override fun toString(): String {
                return "SoundSettings(sound='$sound', volume=$volume, pitch=$pitch)"
            }
        }

        override fun toString(): String {
            return "Sounds(clear=$clear, warnings=$warnings)"
        }
    }

    override fun toString(): String {
        return "ClearConfig(enabled=$enabled, interval=$interval, displayName='$displayName', " +
            "graceSeconds=$graceSeconds, dimensions=$dimensions, messages=$messages, " +
            "sounds=$sounds, clearables=$clearables)"
    }

    companion object {
        /** Five minutes. The shipped config and the in-game menu's reset both use this. */
        const val DEFAULT_INTERVAL: Int = 300

        /** Lower/upper bound the settings menu clamps interactive edits to (1 second … 7 days). */
        const val MIN_INTERVAL: Int = 1
        const val MAX_INTERVAL: Int = 604_800

        /**
         * Fifteen seconds of protection for freshly spawned entities.
         *
         * Long enough to cover "I dropped the wrong stack and need it back", short enough that a
         * full clear still looks like a full clear. ClearLag++ and CobbleLagClear use two minutes,
         * but they are usually configured with a 5–20 minute interval; at a 5 minute default a two
         * minute grace would protect 40% of every cycle.
         */
        const val DEFAULT_GRACE_SECONDS: Int = 15

        /** Upper bound the settings menu clamps the grace period to (one full hour). */
        const val MAX_GRACE_SECONDS: Int = 3600

        val DEFAULT_WARNING_SECONDS: List<Int> = listOf(60, 30, 10, 5, 3, 1)

        /** Language-neutral defaults; the actual wording lives in the per-locale lang files. */
        val DEFAULT_CLEAR: List<String> = listOf("", "<lang:message.clear.success>", "")
        val DEFAULT_WARNING: List<String> = listOf("", "<lang:message.clear.warning>", "")
        val DEFAULT_INFO: List<String> = listOf("<lang:message.info.timer>")
    }
}
