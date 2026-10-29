/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokeskies.skiesclear.SkiesClear
import com.pokeskies.skiesclear.config.ConfigManager
import net.minecraft.server.level.ServerPlayer
import java.io.File
import java.net.JarURLConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight server-side translation layer.
 *
 * Translations live inside the mod jar at `assets/skiesclear/lang/<locale>.json` and are looked up
 * with a fallback chain: exact locale -> parent language -> regional alias -> [DEFAULT_LOCALE].
 *
 * Values may contain MiniMessage tags, so translators own colours as well as wording.
 * Placeholders use the `{name}` syntax and are substituted before MiniMessage parsing.
 */
object I18n {
    const val DEFAULT_LOCALE: String = "en_us"

    private const val LANG_PATH: String = "assets/${SkiesClear.MOD_ID}/lang"

    /** Used when the classloader cannot enumerate the lang directory (e.g. exotic launch wrappers). */
    private val KNOWN_LOCALES: List<String> = listOf(
        "en_us", "en_gb", "zh_cn", "zh_tw", "zh_hk",
        "ja_jp", "ko_kr", "ru_ru", "uk_ua",
        "de_de", "fr_fr", "es_es", "es_mx", "pt_br", "pt_pt",
        "it_it", "pl_pl", "tr_tr", "nl_nl", "sv_se", "cs_cz", "vi_vn", "th_th", "id_id",
        "nb_no", "nn_no", "no_no"
    )

    private val gson = Gson()
    private val translations = ConcurrentHashMap<String, Map<String, String>>()

    /** locale -> ordered fallback chain, cached because it is resolved on every message. */
    private val chains = ConcurrentHashMap<String, List<String>>()

    @Volatile
    private var loadedLocales: List<String> = emptyList()

    val availableLocales: List<String>
        get() = loadedLocales

    /**
     * Every selectable locale: the shipped files plus the regional aliases (`en_gb`, `zh_hk`,
     * `pt_pt`, `nb_no`, ...) whose fallback chain still lands on a shipped file.
     */
    val supportedLocales: List<String>
        get() {
            val merged = LinkedHashSet(loadedLocales)
            for (candidate in KNOWN_LOCALES) {
                if (isSupported(candidate)) merged += candidate
            }
            return merged.toList()
        }

    // ------------------------------------------------------------------ loading

    fun load() {
        translations.clear()
        chains.clear()

        val discovered = LinkedHashSet<String>()
        discovered += scanClasspath()
        if (discovered.isEmpty()) {
            KNOWN_LOCALES.filter { readResource("$LANG_PATH/$it.json") != null }.forEach { discovered += it }
        }

        for (locale in discovered) {
            val content = readResource("$LANG_PATH/$locale.json") ?: continue
            val parsed = try {
                gson.fromJson<Map<String, String>>(
                    content,
                    object : TypeToken<Map<String, String>>() {}.type
                )
            } catch (e: Exception) {
                Utils.printError("Failed to parse language file '$locale.json': ${e.message}")
                null
            } ?: continue
            translations[locale] = parsed.filterValues { it != null }
        }

        if (!translations.containsKey(DEFAULT_LOCALE)) {
            Utils.printError("Default language file '$DEFAULT_LOCALE.json' is missing from the jar!")
        }

        loadedLocales = translations.keys.sortedWith(compareBy({ it != DEFAULT_LOCALE }, { it }))
        Utils.printInfo("Loaded ${loadedLocales.size} language(s): ${loadedLocales.joinToString(", ")}")
    }

    private fun scanClasspath(): Set<String> {
        val found = LinkedHashSet<String>()
        val loader = SkiesClear::class.java.classLoader
        try {
            for (url in loader.getResources(LANG_PATH)) {
                when (url.protocol) {
                    "file" -> File(url.toURI()).listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
                        ?.forEach { found += it.nameWithoutExtension.lowercase() }

                    "jar" -> (url.openConnection() as? JarURLConnection)?.let { conn ->
                        conn.useCaches = false
                        conn.jarFile.use { jar ->
                            val prefix = "$LANG_PATH/"
                            val entries = jar.entries()
                            while (entries.hasMoreElements()) {
                                val name = entries.nextElement().name
                                if (name.startsWith(prefix) && name.endsWith(".json") && name.count { it == '/' } == prefix.count { c -> c == '/' }) {
                                    found += name.removePrefix(prefix).removeSuffix(".json").lowercase()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Utils.printError("Failed to scan the language directory: ${e.message}")
        }
        return found
    }

    private fun readResource(path: String): String? {
        return try {
            SkiesClear::class.java.classLoader.getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ lookup

    fun normalize(locale: String?): String {
        if (locale.isNullOrBlank()) return DEFAULT_LOCALE
        return locale.trim().lowercase().replace('-', '_')
    }

    fun chain(locale: String?): List<String> {
        val normalized = normalize(locale)
        return chains.computeIfAbsent(normalized) { buildChain(normalized) }
    }

    private fun buildChain(locale: String): List<String> {
        val out = LinkedHashSet<String>()
        out += locale

        val language = locale.substringBefore('_')
        if (language != locale) out += language

        when (language) {
            // Script variants: traditional Chinese clients should not fall back to simplified first.
            "zh" -> {
                if (locale == "zh_hk" || locale == "zh_mo" || locale == "zh_tw") out += "zh_tw" else out += "zh_cn"
                out += if (locale.startsWith("zh_tw")) "zh_cn" else "zh_tw"
            }
            "pt" -> out += if (locale == "pt_pt") "pt_br" else "pt_pt"
            "es" -> out += if (locale == "es_mx") "es_es" else "es_mx"
            "nb", "nn" -> out += "no_no"
            else -> Unit
        }

        out += DEFAULT_LOCALE
        return out.toList()
    }

    /** Raw translation for [key], or `null` when no locale in the chain defines it. */
    fun raw(key: String, locale: String?): String? {
        for (candidate in chain(locale)) {
            translations[candidate]?.get(key)?.let { return it }
        }
        return null
    }

    /** [raw] with a guaranteed non-null result; falls back to the key itself so problems stay visible. */
    fun translate(key: String, locale: String?, placeholders: Map<String, String> = emptyMap()): String {
        var value = raw(key, locale) ?: key
        if (placeholders.isNotEmpty()) value = applyPlaceholders(value, placeholders)
        return value
    }

    fun has(key: String): Boolean = translations.values.any { it.containsKey(key) }

    /**
     * True when [locale] can actually produce text: it either ships its own file or its fallback
     * chain reaches one. This is what makes `zh_hk` and `pt_pt` selectable even though only
     * `zh_tw`/`zh_cn` and `pt_br` are shipped — a traditional-Chinese client is a real locale even
     * when we have no dedicated file for its region.
     */
    fun isSupported(locale: String?): Boolean = chain(locale).any { translations.containsKey(it) }

    /** Locale that would actually be read for [locale]; useful for "effective" reporting. */
    fun effective(locale: String?): String = chain(locale).firstOrNull { translations.containsKey(it) } ?: DEFAULT_LOCALE

    // ------------------------------------------------------------------ rendering helpers

    /**
     * Substitutes placeholders into [text].
     *
     * Both spellings used by existing configs are honoured: the `{name}` form used by the language
     * files and the legacy `%name%` form, so a server owner can keep their old config.json verbatim.
     * A key that already carries a delimiter (`%x%`, `{x}`) is treated as a literal token.
     */
    fun applyPlaceholders(text: String, placeholders: Map<String, String>): String {
        var out = text
        for ((name, value) in placeholders) {
            out = when {
                name.startsWith("%") || name.startsWith("{") -> out.replace(name, value)
                else -> out.replace("{$name}", value).replace("%$name%", value)
            }
        }
        return out
    }

    private val LANG_TAG = Regex("<lang:([A-Za-z0-9_.\\-]+)((?:\\s+[A-Za-z0-9_.\\-]+=(?:\"[^\"]*\"|[^\\s>]+))*)\\s*>")
    private val LANG_ARG = Regex("([A-Za-z0-9_.\\-]+)=(?:\"([^\"]*)\"|([^\\s>]+))")

    /**
     * Replaces every `<lang:key>` (optionally `<lang:key:arg=value>`) with the translated text for
     * [locale]. Unknown keys are left untouched so they show up verbatim in game instead of vanishing.
     *
     * Translation values may themselves reference other keys, so expansion recurses; the depth cap
     * stops a mis-authored cyclic reference from hanging the tick loop.
     */
    fun expandLangTags(text: String, locale: String?): String {
        if (!text.contains("<lang:", ignoreCase = true)) return text
        var current = text
        var depth = 0
        while (depth < 8) {
            val next = LANG_TAG.replace(current) { match ->
                val key = match.groupValues[1]
                val args = parseArgs(match.groupValues[2])
                raw(key, locale)?.let { applyPlaceholders(it, args) } ?: match.value
            }
            // Unknown keys keep their `<lang:...>` tag; without this the loop would spin forever.
            if (next == current) break
            current = next
            if (!current.contains("<lang:", ignoreCase = true)) break
            depth++
        }
        return current
    }

    private fun parseArgs(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (m in LANG_ARG.findAll(raw)) {
            out[m.groupValues[1]] = m.groupValues[2].ifEmpty { m.groupValues[3] }
        }
        return out
    }

    // ------------------------------------------------------------------ locale resolution

    /** Locale used for server-side output (console, RCON, unattributed messages). */
    fun serverLocale(): String {
        val configured = if (ConfigManager.isLoaded) ConfigManager.CONFIG.language else "auto"
        return if (configured.equals("auto", ignoreCase = true)) DEFAULT_LOCALE else normalize(configured)
    }

    /** Locale for a specific player, honouring the `language` option in config.json. */
    fun localeFor(player: ServerPlayer?): String {
        val configured = if (ConfigManager.isLoaded) ConfigManager.CONFIG.language else "auto"
        if (!configured.equals("auto", ignoreCase = true)) return normalize(configured)
        if (player == null) return DEFAULT_LOCALE
        val clientLocale = try {
            player.clientInformation().language
        } catch (e: Throwable) {
            null
        }
        return normalize(clientLocale)
    }
}
