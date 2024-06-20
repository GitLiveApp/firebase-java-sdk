package fakes

import com.google.firebase.FirebasePlatform
import java.io.File

/**
 * Fake used to store firebase data during testing. The [storage] is made purposefully public to allow for direct
 * access and modification if needed.
 */
class FakeFirebasePlatform(
    val storage: MutableMap<String, String> = mutableMapOf(),
    databaseFolderPath: String = "./build/database/"
) : FirebasePlatform() {

    private val databaseFolder = File(databaseFolderPath)

    override fun store(key: String, value: String) { storage[key] = value }

    override fun retrieve(key: String) = storage[key]

    override fun clear(key: String) { storage.remove(key) }

    override fun log(msg: String) = println(msg)

    override fun getDatabasePath(name: String) = File(databaseFolder, name)
}
