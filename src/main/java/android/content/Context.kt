package android.content

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import com.google.firebase.FirebasePlatform
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder

open class Context {

    // Firebase casts the application context to Application, so a plain Context provides one
    val applicationContext: Context
        get() = this as? Application ?: application

    private val application: Application by lazy { Application() }

    val mainLooper: Looper
        get() = Looper.getMainLooper()

    val packageName: String
        get() = "app.teamhub.TeamHub"

    val resources: Resources
        get() = Resources()

    val packageManager: PackageManager
        get() = PackageManager()

    val isDeviceProtectedStorage: Boolean
        get() = false

    // The JVM has no separate device-protected storage, so this context already is it
    fun createDeviceProtectedStorageContext(): Context = this

    val noBackupFilesDir: File
        get() = File(System.getProperty("java.io.tmpdir"))

    val classLoader: ClassLoader
        get() = ClassLoader.getSystemClassLoader()

    val contentResolver: ContentResolver
        get() = ContentResolver()

    val applicationInfo: ApplicationInfo = ApplicationInfo()

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        PreferencesFile.at(File(File(filesDir, "shared_prefs"), "${encodeFileName(name)}.json"))

    fun getSystemService(name: String): Any {
        when (name) {
            "power" -> return PowerManager()
            CONNECTIVITY_SERVICE -> return ConnectivityManager.instance
        }
        throw IllegalArgumentException(name)
    }

    fun getSystemService(clazz: Class<*>): Any {
        when (clazz) {
            UserManager::class.java -> return UserManager()
        }
        throw IllegalArgumentException(clazz.toString())
    }

    fun getDir(path: String, flags: Int): File {
        return File(System.getProperty("java.io.tmpdir"))
    }

    // Creates the folder, because the SQLite driver cannot create a database in a missing folder
    fun getDatabasePath(name: String): File =
        FirebasePlatform.firebasePlatform.getDatabasePath(name).apply { parentFile?.mkdirs() }

    val filesDir: File
        get() = FirebasePlatform.firebasePlatform.getFilesDir().apply { mkdirs() }

    fun openFileInput(name: String): FileInputStream = FileInputStream(fileStreamPath(name))

    fun openFileOutput(name: String, mode: Int): FileOutputStream =
        FileOutputStream(fileStreamPath(name), mode and MODE_APPEND != 0)

    fun deleteFile(name: String): Boolean = fileStreamPath(name).delete()

    private fun fileStreamPath(name: String): File {
        require('/' !in name && File.separatorChar !in name) { "File $name contains a path separator" }
        return File(filesDir, encodeFileName(name))
    }

    // URL-encodes the name so characters such as ':' in Firebase app IDs are valid on every OS
    private fun encodeFileName(name: String): String = URLEncoder.encode(name, Charsets.UTF_8)

    companion object {
        @JvmStatic
        val CONNECTIVITY_SERVICE = "connectivity"

        const val MODE_APPEND = 0x8000
    }
}
