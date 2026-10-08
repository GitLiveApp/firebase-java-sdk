package fakes

import com.google.firebase.FirebasePlatform
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Fake used to store firebase data during testing. The [storage] is made purposefully public to allow for direct
 * access and modification if needed.
 */
class FakeFirebasePlatform(
    val storage: MutableMap<String, String> = ConcurrentHashMap(),
    databaseFolderPath: String = "./build/database/",
    filesFolderPath: String = "./build/files/"
) : FirebasePlatform() {

    private val databaseFolder = File(databaseFolderPath)

    private val filesFolder = File(filesFolderPath)

    // Captured so tests can assert on expected log output
    val logs: MutableList<String> = Collections.synchronizedList(mutableListOf())

    // Removes the logged lines starting with [prefix] and returns how many there were, so a test can check
    // its expected lines and then that nothing else was logged
    fun takeLogs(prefix: String): Int = synchronized(logs) {
        val taken = logs.filter { it.startsWith(prefix) }
        logs.removeAll(taken)
        taken.size
    }

    override fun store(key: String, value: String) { storage[key] = value }

    override fun retrieve(key: String) = storage[key]

    override fun clear(key: String) { storage.remove(key) }

    override fun log(msg: String) { logs.add(msg) }

    override fun getDatabasePath(name: String) = File(databaseFolder, name)

    override fun getFilesDir() = filesFolder
}
