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

import com.google.gson.stream.JsonReader
import com.pokeskies.skiesclear.SkiesClear
import com.pokeskies.skiesclear.utils.I18n
import com.pokeskies.skiesclear.utils.Utils
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ConfigManager {
    private val assetPackage = "assets/${SkiesClear.MOD_ID}"

    lateinit var CONFIG: MainConfig
        private set

    /** False until [load] succeeds; guards callers that run before config is available. */
    var isLoaded: Boolean = false
        private set

    fun load() {
        isLoaded = false
        copyDefaults()

        CONFIG = try {
            loadFile("config.json", MainConfig())
        } catch (e: Exception) {
            Utils.printError("Failed to read config.json, using built-in defaults: ${e.message}")
            MainConfig()
        }

        // Language files are read from the jar, so they must be ready before any text is rendered.
        I18n.load()

        isLoaded = true
        Utils.printDebug("Configuration loaded (language=${CONFIG.language}, clears=${CONFIG.clears.size})", true)
    }

    private fun copyDefaults() {
        val classLoader = SkiesClear::class.java.classLoader
        SkiesClear.INSTANCE.configDir.mkdirs()
        attemptDefaultFileCopy(classLoader, "config.json")
    }

    private fun attemptDefaultFileCopy(classLoader: ClassLoader, fileName: String) {
        val file = SkiesClear.INSTANCE.configDir.resolve(fileName)
        if (file.exists()) return
        try {
            val stream = classLoader.getResourceAsStream("$assetPackage/$fileName")
                ?: error("File not found $fileName")
            file.parentFile?.mkdirs()
            Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Utils.printError("Failed to copy the default file '$fileName': $e")
        }
    }

    fun <T : Any> loadFile(filename: String, default: T, path: String = "", create: Boolean = false): T {
        var dir = SkiesClear.INSTANCE.configDir
        if (path.isNotEmpty()) dir = dir.resolve(path)
        val file = File(dir, filename)
        var value: T = default
        try {
            Files.createDirectories(dir.toPath())
            if (file.exists()) {
                FileReader(file).use { reader ->
                    JsonReader(reader).use { jsonReader ->
                        value = SkiesClear.INSTANCE.gsonPretty.fromJson(jsonReader, default::class.java) ?: default
                    }
                }
            } else if (create) {
                Files.createFile(file.toPath())
                FileWriter(file).use { fileWriter ->
                    fileWriter.write(SkiesClear.INSTANCE.gsonPretty.toJson(default))
                }
            }
        } catch (e: IOException) {
            Utils.printError("I/O error while handling '$filename': ${e.message}")
        }
        return value
    }

    fun <T> saveFile(filename: String, `object`: T): Boolean {
        val file = File(SkiesClear.INSTANCE.configDir, filename)
        return try {
            file.parentFile?.mkdirs()
            FileWriter(file).use { fileWriter ->
                fileWriter.write(SkiesClear.INSTANCE.gsonPretty.toJson(`object`))
            }
            true
        } catch (e: Exception) {
            Utils.printError("Failed to save '$filename': ${e.message}")
            false
        }
    }
}
