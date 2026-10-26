package com.google.firebase.datastorage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.google.firebase.FirebasePlatform
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Replaces firebase-common's `JavaDataStorage`, the blocking facade over a preferences DataStore that the heartbeat
 * store uses, with one that keeps a single [DataStore] per file for the process.
 *
 * The original creates a new DataStore per instance through the Android `preferencesDataStore` delegate, and a
 * DataStore stays active for the life of the process, so a [com.google.firebase.FirebaseApp] initialised again after
 * `delete()` or `clearInstancesForTest()` (as test suites do) failed with "There are multiple DataStores active for
 * the same file". The file lives under [Context.filesDir], which [FirebasePlatform.getFilesDir] provides, and no
 * SharedPreferences migration runs: this port never kept the heartbeats there.
 */
class JavaDataStorage(val context: Context, val name: String) {
    private val dataStore: DataStore<Preferences> = stores.computeIfAbsent(file().absolutePath) { path ->
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { ex ->
                FirebasePlatform.firebasePlatform.log("CorruptionException in $name DataStore: $ex")
                emptyPreferences()
            },
            produceFile = { File(path) }
        )
    }

    private val editLock = ThreadLocal<Boolean>()

    /** Under [Context.filesDir] like Android, with the name URL-encoded as the Context file APIs do: it carries the app id, whose `:` Windows rejects. */
    private fun file(): File = File(context.filesDir, "datastore${File.separatorChar}${URLEncoder.encode(name, Charsets.UTF_8)}.preferences_pb")

    fun <T> getSync(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.firstOrNull()?.get(key) ?: defaultValue
    }

    fun <T> contains(key: Preferences.Key<T>): Boolean = runBlocking {
        dataStore.data.firstOrNull()?.contains(key) ?: false
    }

    fun <T> putSync(key: Preferences.Key<T>, value: T): Preferences = runBlocking {
        dataStore.edit { it[key] = value }
    }

    fun getAllSync(): Map<Preferences.Key<*>, Any> = runBlocking {
        dataStore.data.firstOrNull()?.asMap() ?: emptyMap()
    }

    fun editSync(transform: (MutablePreferences) -> Unit): Preferences {
        check(editLock.get() != true) { "Don't call JavaDataStorage.editSync from within a transform block" }
        return runBlocking {
            dataStore.edit {
                editLock.set(true)
                try {
                    transform(it)
                } finally {
                    editLock.set(false)
                }
            }
        }
    }

    private companion object {
        /** One DataStore per file for the process: a second one on the same file is an error in DataStore. */
        val stores = ConcurrentHashMap<String, DataStore<Preferences>>()
    }
}
