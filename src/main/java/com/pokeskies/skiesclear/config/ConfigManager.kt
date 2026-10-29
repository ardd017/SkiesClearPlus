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

import com.google.gson.JsonParseException
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

        if (CONFIG.clears.isEmpty()) {
            // Loud on purpose: an empty `clears` block is almost always a parse failure that fell
            // back to defaults, and the server would otherwise keep running while clearing nothing.
            Utils.printError(
                "No clear entries are loaded. If config.json is supposed to contain entries, " +
                    "check it for syntax errors — nothing will be cleared until it is fixed."
            )
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
                // UTF-8 explicitly, matching saveFile: a config saved on a GBK-configured server
                // must still load on a UTF-8 one and vice versa.
                JsonReader(file.reader(Charsets.UTF_8)).use { jsonReader ->
                    value = SkiesClear.INSTANCE.gsonPretty.fromJson(jsonReader, default::class.java) ?: default
                }
            } else if (create) {
                Files.createFile(file.toPath())
                file.writeText(SkiesClear.INSTANCE.gsonPretty.toJson(default), Charsets.UTF_8)
            }
        } catch (e: IOException) {
            Utils.printError("I/O error while handling '$filename': ${e.message}")
        } catch (e: JsonParseException) {
            // Gson's parse errors are RuntimeException, so they used to escape this method entirely
            // and be swallowed by load()'s broad catch. Falling back here keeps the failure close
            // to the file that caused it, and the broken file is preserved for inspection.
            Utils.printError("Malformed JSON in '$filename': ${e.message}")
            backupBrokenFile(dir, filename)
            // Recreate the file so the next start (and the GUI, which saves back into this path) has
            // a working file instead of the damaged one. The bundled resource is preferred over the
            // in-memory default: it carries the commented example entry, so an owner who broke the
            // file does not also lose the template. Only reached when the backup succeeded --
            // backupBrokenFile leaves the damaged file in place on failure.
            if (!file.exists()) {
                value = restoreDefaultFile(filename, default) ?: value
            }
        }
        return value
    }

    /**
     * Copies an unparseable config aside before anything can overwrite it.
     *
     * The old behaviour left the broken file untouched but also offered no clue where it was once a
     * GUI save rewrote it. `config.json.invalid` is deliberately *not* read back, so the server
     * keeps running on defaults instead of half-loading a damaged file.
     */
    private fun backupBrokenFile(dir: File, filename: String) {
        val source = File(dir, filename)
        if (!source.exists()) return
        val target = File(dir, "$filename.invalid")
        try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Utils.printError("The unreadable file has been copied to '${target.name}'.")
        } catch (e: Exception) {
            Utils.printError("Could not back up the unreadable file: ${e.message}")
            return
        }
        // Move the damaged file out of the way so the next start recreates a working default.
        // Leaving it in place meant the server stayed on built-in defaults until someone noticed
        // that clears=0 -- and a GUI save would silently overwrite the only copy of the rules.
        try {
            Files.delete(source.toPath())
        } catch (e: Exception) {
            Utils.printError("Could not move '$filename' aside: ${e.message}")
        }
    }

    /**
     * Writes a working file back after [backupBrokenFile] removed the damaged one, and returns what
     * was actually written.
     *
     * The bundled resource wins because it still contains the commented example entry; a bare
     * [MainConfig] has an empty `clears` map, which would leave the server running and clearing
     * nothing until someone wrote a config by hand. Re-reading it here means the freshly created
     * file and the in-memory config agree, so the first `/skiesclear info` already works.
     */
    private fun <T : Any> restoreDefaultFile(filename: String, default: T): T? {
        val file = File(SkiesClear.INSTANCE.configDir, filename)
        try {
            val stream = SkiesClear::class.java.classLoader.getResourceAsStream("$assetPackage/$filename")
            if (stream != null) {
                stream.use { Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            } else {
                file.writeText(SkiesClear.INSTANCE.gsonPretty.toJson(default), Charsets.UTF_8)
            }
            Utils.printError("A fresh '$filename' has been written from the bundled defaults.")
        } catch (e: Exception) {
            Utils.printError("Could not write a fresh '$filename': ${e.message}")
            return null
        }
        return runCatching { file.reader(Charsets.UTF_8).use { SkiesClear.INSTANCE.gsonPretty.fromJson(it, default::class.java) } }
            .getOrNull() ?: default
    }

    fun <T> saveFile(filename: String, `object`: T): Boolean {
        val file = File(SkiesClear.INSTANCE.configDir, filename)
        return try {
            file.parentFile?.mkdirs()
            // Explicit UTF-8: FileWriter uses the platform charset, which is GBK on a Chinese
            // Windows install. The mod would read its own output back fine, but a config written on
            // the server and edited on a laptop (or the other way round) would turn into mojibake —
            // `display_name` and every message line are free text.
            file.writeText(SkiesClear.INSTANCE.gsonPretty.toJson(`object`), Charsets.UTF_8)
            true
        } catch (e: Exception) {
            Utils.printError("Failed to save '$filename': ${e.message}")
            false
        }
    }
}
