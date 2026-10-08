/*
 * SharedPreferences stored as one JSON file per preferences name under
 * FirebasePlatform.getFilesDir()/shared_prefs, as Android stores them in the app's data folder.
 * Keeping preferences beside the other files Firebase writes to getFilesDir() means they are kept
 * or lost together. Every caller of a file path shares one instance, which holds the values in
 * memory and replaces the whole file atomically on commit.
 */
package android.content

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

internal class PreferencesFile private constructor(private val file: File) : SharedPreferences {

    // Loaded from the file on first use; read and replaced only while holding this instance's lock
    private var loadedValues: Map<String, Any>? = null

    // The values the file last held, which differ from loadedValues after a failed write
    private var storedValues: Map<String, Any> = emptyMap()

    override fun contains(key: String): Boolean = values().containsKey(key)

    override fun getString(key: String, defaultValue: String?): String? = values()[key] as String? ?: defaultValue

    override fun getInt(key: String, defValue: Int): Int = values()[key] as Int? ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values()[key] as Long? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values()[key] as Boolean? ?: defValue

    // Returns a copy, so callers cannot change the stored set without an edit
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (values()[key] as Set<*>?)?.mapTo(HashSet()) { it as String } ?: defValues

    override fun getAll(): Map<String, Any> =
        values().mapValues { (_, value) -> if (value is Set<*>) HashSet(value) else value }

    override fun edit(): SharedPreferences.Editor = Edit()

    private fun values(): Map<String, Any> = synchronized(this) {
        loadedValues ?: read().also {
            loadedValues = it
            storedValues = it
        }
    }

    private fun read(): Map<String, Any> {
        if (!file.exists()) return emptyMap()
        return try {
            Json.parseToJsonElement(file.readText()).jsonObject.mapValues { (_, value) -> decode(value) }
        } catch (e: Exception) {
            // Unreadable content is discarded; the next commit replaces the file
            Log.w(TAG, "Ignoring unreadable preferences file $file", e)
            emptyMap()
        }
    }

    private fun save(changes: Map<String, Any?>, clear: Boolean): Boolean {
        synchronized(this) {
            val updated = if (clear) mutableMapOf() else values().toMutableMap()
            changes.forEach { (key, value) -> if (value == null) updated.remove(key) else updated[key] = value }
            // As on Android, memory is updated first, so the new values stay readable if the write fails
            loadedValues = updated
            // A missing file reads as empty, so it only needs writing when it would hold values
            if (updated == storedValues && (updated.isEmpty() || file.exists())) return true
            try {
                write(updated)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write preferences file $file", e)
                return false
            }
            storedValues = updated
            return true
        }
    }

    // Writes a temporary file and moves it over the target, so a failed write leaves the previous file intact.
    // The temporary file is synced to disk first, so a power loss cannot leave an empty file behind.
    private fun write(values: Map<String, Any>) {
        val folder = file.parentFile
        // Succeeds when another file's write has just created the folder
        Files.createDirectories(folder.toPath())
        val temporary = File.createTempFile(file.name, ".tmp", folder)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(JsonObject(values.mapValues { (_, value) -> encode(value) }).toString().toByteArray())
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    // Collects changes until commit() or apply(), as Android's editor does
    private inner class Edit : SharedPreferences.Editor {

        // A null value removes the key
        private val changes = mutableMapOf<String, Any?>()

        private var clearRequested = false

        override fun putString(key: String, value: String?) = change(key, value)

        override fun putInt(key: String, value: Int) = change(key, value)

        override fun putLong(key: String, value: Long) = change(key, value)

        override fun putBoolean(key: String, value: Boolean) = change(key, value)

        override fun putStringSet(key: String, values: Set<String>?) = change(key, values?.toHashSet())

        override fun remove(key: String) = change(key, null)

        override fun clear(): SharedPreferences.Editor = synchronized(this) {
            clearRequested = true
            this
        }

        override fun commit(): Boolean = synchronized(this) {
            save(changes.toMap(), clearRequested).also {
                changes.clear()
                clearRequested = false
            }
        }

        override fun apply() {
            commit()
        }

        private fun change(key: String, value: Any?): SharedPreferences.Editor = synchronized(this) {
            changes[key] = value
            this
        }
    }

    companion object {
        private const val TAG = "PreferencesFile"

        private val instances = ConcurrentHashMap<File, PreferencesFile>()

        fun at(file: File): PreferencesFile = instances.computeIfAbsent(file.absoluteFile) { PreferencesFile(it) }

        // Each value is a one-entry object naming its type, e.g. {"int": 3}, so reads restore the type
        private fun encode(value: Any): JsonElement = buildJsonObject {
            when (value) {
                is String -> put("string", value)
                is Int -> put("int", value)
                is Long -> put("long", value)
                is Boolean -> put("boolean", value)
                is Set<*> -> putJsonArray("stringSet") { value.forEach { add(it as String) } }
                else -> throw IllegalArgumentException("Unsupported preference value $value")
            }
        }

        private fun decode(element: JsonElement): Any {
            val (type, value) = element.jsonObject.entries.single()
            return when (type) {
                "string" -> value.jsonPrimitive.content
                "int" -> value.jsonPrimitive.int
                "long" -> value.jsonPrimitive.long
                "boolean" -> value.jsonPrimitive.boolean
                "stringSet" -> value.jsonArray.mapTo(HashSet()) { it.jsonPrimitive.content }
                else -> throw IllegalArgumentException("Unknown preference type $type")
            }
        }
    }
}
