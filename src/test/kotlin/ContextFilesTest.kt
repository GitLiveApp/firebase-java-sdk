/*
 * Tests for the file APIs on the android.content.Context shim, which Remote Config and
 * Installations use to persist configs and installation data in FirebasePlatform.getFilesDir().
 */
import android.app.Application
import android.content.Context
import com.google.firebase.FirebasePlatform
import fakes.FakeFirebasePlatform
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException

class ContextFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var filesDir: File

    private lateinit var platform: FakeFirebasePlatform

    private val context = Application()

    @Before
    fun setUp() {
        filesDir = File(folder.root, "files")
        platform = FakeFirebasePlatform(filesFolderPath = filesDir.path)
        FirebasePlatform.initializeFirebasePlatform(platform)
    }

    @After
    fun nothingUnexpectedWasLogged() {
        assertEquals(emptyList<String>(), platform.logs.toList())
    }

    @Test
    fun `filesDir is created when missing`() {
        assertFalse(filesDir.exists())
        assertEquals(filesDir, context.filesDir)
        assertTrue(filesDir.isDirectory)
    }

    @Test
    fun `written file can be read back`() {
        context.openFileOutput("config.json", 0).use { it.write("hello".toByteArray()) }
        assertEquals("hello", context.openFileInput("config.json").use { String(it.readBytes()) })
    }

    @Test
    fun `opening a missing file throws FileNotFoundException`() {
        assertThrows(FileNotFoundException::class.java) { context.openFileInput("missing.json") }
    }

    @Test
    fun `deleteFile removes the file`() {
        context.openFileOutput("config.json", 0).use { it.write("hello".toByteArray()) }
        assertTrue(context.deleteFile("config.json"))
        assertThrows(FileNotFoundException::class.java) { context.openFileInput("config.json") }
        assertFalse(context.deleteFile("config.json"))
    }

    @Test
    fun `append mode appends`() {
        context.openFileOutput("log.txt", 0).use { it.write("a".toByteArray()) }
        context.openFileOutput("log.txt", Context.MODE_APPEND).use { it.write("b".toByteArray()) }
        assertEquals("ab", context.openFileInput("log.txt").use { String(it.readBytes()) })
    }

    @Test
    fun `file names with colons are stored without colons`() {
        val name = "frc_1:341458593155:web:bf8e1aa37efe01f32d42b6_firebase_fetch.json"
        context.openFileOutput(name, 0).use { it.write("{}".toByteArray()) }
        assertEquals("{}", context.openFileInput(name).use { String(it.readBytes()) })
        assertTrue(filesDir.list()!!.none { ':' in it })
    }

    @Test
    fun `file names with path separators are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { context.openFileOutput("../escape.json", 0) }
    }

    @Test
    fun `preferences are stored as files in filesDir`() {
        context.getSharedPreferences("frc_1:341458593155:web:bf8e1aa37efe01f32d42b6_firebase_settings", 0)
            .edit().putLong("last_fetch_time_in_millis", 1L).commit()

        val stored = File(filesDir, "shared_prefs").list()!!.single()
        assertTrue(stored.endsWith(".json"))
        assertTrue(':' !in stored)
    }

    @Test
    fun `getDatabasePath creates its folder`() {
        val databases = File(folder.root, "databases")
        platform = FakeFirebasePlatform(databaseFolderPath = databases.path)
        FirebasePlatform.initializeFirebasePlatform(platform)

        assertEquals(File(databases, "firestore.db"), context.getDatabasePath("firestore.db"))
        assertTrue(databases.isDirectory)
    }

    @Test
    fun `any preferences name is accepted`() {
        val prefs = context.getSharedPreferences("com.google.firebase.common.prefs:W0RFRkFVTFRd", 0)

        assertTrue(prefs.getBoolean("firebase_data_collection_default_enabled", true))
    }
}
