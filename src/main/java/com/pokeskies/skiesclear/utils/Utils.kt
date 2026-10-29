/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * Modifications for SkiesClear Plus Copyright 2026 ardd_017, licensed under the Apache License 2.0.
 *
 * This file has been MODIFIED from the upstream SkiesClear source. See ATTRIBUTION.md for the
 * list of changes.
 */
package com.pokeskies.skiesclear.utils

import com.google.gson.*
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.pokeskies.skiesclear.SkiesClear
import com.pokeskies.skiesclear.config.ConfigManager
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.Registry
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.lang.reflect.Type
import java.util.*
import java.util.function.Function

object Utils {
    val miniMessage: MiniMessage = MiniMessage.miniMessage()

    // ------------------------------------------------------------------ text

    /**
     * Parses MiniMessage into a native component.
     *
     * Order matters: `<lang:key>` tags are expanded **before** placeholders are substituted, because
     * the placeholders usually live inside the translation value rather than in the config line.
     * Applying them the other way round leaves `{clear}` visible in game.
     */
    fun deserializeText(
        text: String,
        locale: String? = null,
        placeholders: Map<String, String> = emptyMap()
    ): Component {
        val resolved = locale ?: I18n.serverLocale()
        var expanded = I18n.expandLangTags(text, resolved)
        if (placeholders.isNotEmpty()) expanded = I18n.applyPlaceholders(expanded, placeholders)
        return SkiesClear.INSTANCE.adventure!!.toNative(miniMessage.deserialize(expanded))
    }

    /** Sends a localised, MiniMessage-styled message to a command source. */
    fun sendLocalized(
        source: CommandSourceStack,
        key: String,
        placeholders: Map<String, String> = emptyMap(),
        error: Boolean = false
    ) {
        val player = source.player as? ServerPlayer
        val locale = I18n.localeFor(player)
        val line = I18n.translate(key, locale, placeholders)
        val component = deserializeText(line, locale)
        if (error) source.sendFailure(component) else source.sendSuccess({ component }, false)
    }

    /** Sends a raw (already translated or config-provided) line to a command source. */
    fun sendRaw(source: CommandSourceStack, line: String, error: Boolean = false) {
        val locale = I18n.localeFor(source.player as? ServerPlayer)
        val component = deserializeText(line, locale)
        if (error) source.sendFailure(component) else source.sendSuccess({ component }, false)
    }

    /**
     * Sends a config-defined message list to a command source, resolving `<lang:key>` fragments and
     * placeholders in the source's own locale. Blank entries are skipped so configs can keep the
     * leading/trailing empty lines they use for spacing in broadcasts.
     */
    fun sendLines(
        source: CommandSourceStack,
        lines: List<String>,
        placeholders: Map<String, String> = emptyMap(),
        error: Boolean = false
    ) {
        if (lines.isEmpty()) return
        val locale = I18n.localeFor(source.player as? ServerPlayer)
        for (line in lines) {
            if (line.isBlank()) continue
            val component = deserializeText(line, locale, placeholders)
            if (error) source.sendFailure(component) else source.sendSuccess({ component }, false)
        }
    }

    /**
     * Broadcasts a config-defined message list to every player in [players], translating
     * `<lang:key>` fragments per player so each client reads the message in its own language.
     */
    fun broadcast(players: Collection<ServerPlayer>, lines: List<String>, placeholders: Map<String, String>) {
        broadcast(players, lines) { placeholders }
    }

    /**
     * Same as above, but the placeholder map is produced per locale. Durations ("5 minutes") are
     * language-specific, so a single shared map would leak the server's language into every
     * player's warning message.
     */
    fun broadcast(
        players: Collection<ServerPlayer>,
        lines: List<String>,
        placeholdersFor: (String) -> Map<String, String>
    ) {
        if (lines.isEmpty() || players.isEmpty()) return
        for (player in players) {
            val locale = I18n.localeFor(player)
            val placeholders = placeholdersFor(locale)
            for (line in lines) {
                if (line.isEmpty()) {
                    player.sendSystemMessage(Component.empty())
                } else {
                    player.sendSystemMessage(deserializeText(line, locale, placeholders))
                }
            }
        }
    }

    // ------------------------------------------------------------------ logging

    fun printDebug(message: String?, bypassCheck: Boolean = false) {
        if (bypassCheck || (ConfigManager.isLoaded && ConfigManager.CONFIG.debug))
            SkiesClear.LOGGER.info("[SkiesClear Plus] DEBUG: $message")
    }

    fun printError(message: String?) {
        SkiesClear.LOGGER.error("[SkiesClear Plus] ERROR: $message")
    }

    fun printInfo(message: String?) {
        SkiesClear.LOGGER.info("[SkiesClear Plus] $message")
    }

    fun printWarn(message: String?) {
        SkiesClear.LOGGER.warn("[SkiesClear Plus] $message")
    }

    // ------------------------------------------------------------------ time

    /**
     * Formats a duration using the unit labels defined in the language files
     * (`time.day`, `time.hour`, `time.minute`, `time.second`, `time.zero`).
     */
    fun getFormattedTime(time: Long, locale: String? = null): String {
        val resolved = locale ?: I18n.serverLocale()
        if (time <= 0) return I18n.translate("time.zero", resolved)

        val days = time / 86400
        val hours = time % 86400 / 3600
        val minutes = time % 86400 % 3600 / 60
        val seconds = time % 86400 % 3600 % 60

        val parts = ArrayList<String>(4)
        if (days > 0) parts.add(I18n.translate("time.day", resolved, mapOf("value" to days.toString())))
        if (hours > 0) parts.add(I18n.translate("time.hour", resolved, mapOf("value" to hours.toString())))
        if (minutes > 0) parts.add(I18n.translate("time.minute", resolved, mapOf("value" to minutes.toString())))
        if (seconds > 0) parts.add(I18n.translate("time.second", resolved, mapOf("value" to seconds.toString())))

        if (parts.isEmpty()) return I18n.translate("time.zero", resolved)
        return parts.joinToString(I18n.translate("time.separator", resolved))
    }

    // ------------------------------------------------------------------ serializers (thanks Patbox!)

    data class RegistrySerializer<T>(val registry: Registry<T>) : JsonSerializer<T>, JsonDeserializer<T> {
        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T? {
            val parsed = if (json.isJsonPrimitive) registry.get(ResourceLocation.tryParse(json.asString)) else null
            if (parsed == null)
                printError("There was an error while deserializing a Registry Type: $registry")
            return parsed
        }

        override fun serialize(src: T, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(registry.getKey(src).toString())
        }
    }

    data class CodecSerializer<T>(val codec: Codec<T>) : JsonSerializer<T>, JsonDeserializer<T> {
        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): T? {
            return try {
                codec.decode(JsonOps.INSTANCE, json).orThrow.first
            } catch (e: Throwable) {
                printError("There was an error while deserializing a Codec: $codec")
                null
            }
        }

        override fun serialize(src: T?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return try {
                if (src != null) codec.encodeStart(JsonOps.INSTANCE, src).orThrow else JsonNull.INSTANCE
            } catch (e: Throwable) {
                printError("There was an error while serializing a Codec: $codec")
                JsonNull.INSTANCE
            }
        }
    }

    class ResourceLocationSerializer : JsonSerializer<ResourceLocation>, JsonDeserializer<ResourceLocation> {
        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ResourceLocation? {
            return ResourceLocation.tryParse(json.asString)
        }

        override fun serialize(src: ResourceLocation, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.toString())
        }
    }
}

fun <A, B> Codec<A>.recordCodec(id: String, getter: Function<B, A>): RecordCodecBuilder<B, A> {
    return this.fieldOf(id).forGetter(getter)
}

fun <A, B> Codec<A>.optionalRecordCodec(id: String, getter: Function<B, A>, default: A): RecordCodecBuilder<B, A> {
    return this.fieldOf(id).orElse(default).forGetter(getter)
}
