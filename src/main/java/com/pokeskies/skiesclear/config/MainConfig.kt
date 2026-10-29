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

/**
 * The root of `config.json`.
 *
 * Every field is mutable because the in-game settings menu edits the live object graph and then
 * saves it; see [ClearConfig] for the same rationale.
 */
class MainConfig(
    /** Verbose logging to the server console. */
    var debug: Boolean = false,

    /**
     * Language used for all mod output.
     *
     * - `auto` (default) — every player is served in the locale their client reports, so one config
     *   file works for an international player base.
     * - a locale id such as `zh_cn` or `en_us` — forces that language for everyone.
     *
     * Locale ids use Minecraft's `lowercase_lowercase` form. See `assets/skiesclear/lang/` in the jar
     * for the shipped set.
     */
    var language: String = "auto",

    /**
     * Announcements that make the mod visible: a startup notice, a per-join notice and a one-off
     * welcome for brand new players. See [BroadcastConfig].
     */
    var broadcast: BroadcastConfig = BroadcastConfig(),

    var clears: Map<String, ClearConfig> = LinkedHashMap(),
) {
    override fun toString(): String {
        return "MainConfig(debug=$debug, language=$language, broadcast=$broadcast, clears=$clears)"
    }
}
