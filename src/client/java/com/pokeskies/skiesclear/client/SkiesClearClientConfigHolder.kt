/*
 * SkiesClear Plus — a fork of SkiesClear by PokeSkies (https://github.com/PokeSkies/SkiesClear).
 *
 * Original code Copyright PokeSkies, licensed under the Apache License 2.0.
 * This file is NEW in SkiesClear Plus. Copyright 2026 ardd_017, licensed under the Apache
 * License 2.0. See ATTRIBUTION.md.
 */
package com.pokeskies.skiesclear.client

import com.pokeskies.skiesclear.client.config.SkiesClearClientConfig
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigHolder
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer

/**
 * Owns the AutoConfig registration for [SkiesClearClientConfig].
 *
 * AutoConfig writes `config/skiesclear-client.json` -- deliberately separate from the server
 * config at `config/skiesclear/config.json`, which stays authoritative and is edited by
 * [SkiesClearConfigScreen] instead.
 */
object SkiesClearClientConfigHolder {

    private var holder: ConfigHolder<SkiesClearClientConfig>? = null

    val config: SkiesClearClientConfig
        get() = holderOrRegister().config

    fun holderOrRegister(): ConfigHolder<SkiesClearClientConfig> {
        holder?.let { return it }
        val registered = AutoConfig.register(
            SkiesClearClientConfig::class.java,
            ConfigSerializerFactory,
        )
        holder = registered
        return registered
    }

    /** Persists the client preferences. `ConfigHolder.save()` is void, so success is reported manually. */
    fun save(): Boolean = try {
        holderOrRegister().save()
        true
    } catch (e: Exception) {
        SkiesClearClient.LOGGER.warn("[SkiesClear Plus] Could not save client settings: {}", e.message)
        false
    }

    private val ConfigSerializerFactory =
        me.shedaniel.autoconfig.serializer.ConfigSerializer.Factory<SkiesClearClientConfig> { definition, configClass ->
            GsonConfigSerializer(definition, configClass)
        }
}
