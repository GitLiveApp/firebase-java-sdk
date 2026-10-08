/*
 * Tests for PreferencesFile, the SharedPreferences that stores each preferences name as a JSON
 * file under FirebasePlatform.getFilesDir()/shared_prefs.
 */
import android.content.PreferencesFile
import com.google.firebase.FirebasePlatform
import fakes.FakeFirebasePlatform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class PreferencesFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val platform = FakeFirebasePlatform()

    private fun file(name: String) = File(folder.root, "shared_prefs/$name.json")

    private fun prefs(name: String = "frc_app_firebase_settings") = PreferencesFile.at(file(name))

    // A non-empty folder in place of the file makes every write fail, on every OS and for every user
    private fun blockWrites(name: String) {
        file(name).delete()
        File(file(name), "blocker").apply { parentFile.mkdirs() }.createNewFile()
    }

    private fun unblockWrites(name: String) {
        file(name).deleteRecursively()
    }

    @Before
    fun setUp() {
        FirebasePlatform.initializeFirebasePlatform(platform)
    }

    @After
    fun nothingUnexpectedWasLogged() {
        assertEquals(emptyList<String>(), platform.logs.toList())
    }

    @Test
    fun `values round-trip with their types`() {
        val prefs = prefs()
        prefs.edit()
            .putString("string", "abc")
            .putInt("int", -1)
            .putLong("long", 42L)
            .putBoolean("boolean", true)
            .putStringSet("stringSet", setOf("x", "y"))
            .commit()

        assertEquals("abc", prefs.getString("string", null))
        assertEquals(-1, prefs.getInt("int", 0))
        assertEquals(42L, prefs.getLong("long", 0L))
        assertTrue(prefs.getBoolean("boolean", false))
        assertEquals(setOf("x", "y"), prefs.getStringSet("stringSet", null))
        assertEquals(
            mapOf("string" to "abc", "int" to -1, "long" to 42L, "boolean" to true, "stringSet" to setOf("x", "y")),
            prefs.all
        )
    }

    @Test
    fun `a written file is read back with its types`() {
        prefs("written").edit()
            .putString("string", "abc")
            .putInt("int", -1)
            .putLong("long", 42L)
            .putBoolean("boolean", true)
            .putStringSet("stringSet", setOf("x", "y"))
            .commit()
        // A copy is read by a new instance, as after a restart, not from the writer's memory
        file("written").copyTo(file("copy"))

        assertEquals(
            mapOf("string" to "abc", "int" to -1, "long" to 42L, "boolean" to true, "stringSet" to setOf("x", "y")),
            prefs("copy").all
        )
    }

    @Test
    fun `missing keys return defaults`() {
        val prefs = prefs()

        assertEquals("default", prefs.getString("missing", "default"))
        assertEquals(7, prefs.getInt("missing", 7))
        assertEquals(7L, prefs.getLong("missing", 7L))
        assertTrue(prefs.getBoolean("missing", true))
        assertEquals(setOf("default"), prefs.getStringSet("missing", setOf("default")))
        assertFalse(prefs.contains("missing"))
    }

    @Test
    fun `remove and putString null delete the key`() {
        val prefs = prefs()
        prefs.edit().putString("first", "a").putString("second", "b").commit()

        prefs.edit().remove("first").putString("second", null).commit()

        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `files do not see each other's keys`() {
        prefs("first").edit().putString("key", "first").commit()

        assertNull(prefs("second").getString("key", null))
    }

    @Test
    fun `clear affects only its own file`() {
        prefs("first").edit().putString("key", "first").commit()
        prefs("second").edit().putString("key", "second").commit()

        prefs("first").edit().clear().commit()

        assertTrue(prefs("first").all.isEmpty())
        assertEquals("second", prefs("second").getString("key", null))
    }

    @Test
    fun `clear applies before the edits it is batched with`() {
        val prefs = prefs()
        prefs.edit().putString("old", "value").commit()

        prefs.edit().putString("kept", "value").clear().commit()

        assertEquals(mapOf("kept" to "value"), prefs.all)
    }

    @Test
    fun `edits are not visible before commit`() {
        val prefs = prefs()
        val edit = prefs.edit().putString("key", "value")

        assertFalse(prefs.contains("key"))
        edit.commit()
        assertTrue(prefs.contains("key"))
    }

    @Test
    fun `commit writes json and leaves no temporary files`() {
        prefs().edit().putInt("num_failed_fetches", 3).commit()

        val stored = Json.parseToJsonElement(file("frc_app_firebase_settings").readText()).jsonObject
        assertEquals(setOf("num_failed_fetches"), stored.keys)
        assertEquals(listOf("frc_app_firebase_settings.json"), file("frc_app_firebase_settings").parentFile.list()!!.toList())
    }

    @Test
    fun `existing file is loaded when first opened`() {
        file("restored").parentFile.mkdirs()
        file("restored").writeText(
            """{"num_failed_fetches":{"int":3},"last_fetch_time_in_millis":{"long":42},"fire-core":{"stringSet":["2026-10-08"]}}"""
        )

        val prefs = prefs("restored")

        assertEquals(3, prefs.getInt("num_failed_fetches", 0))
        assertEquals(42L, prefs.getLong("last_fetch_time_in_millis", 0L))
        assertEquals(setOf("2026-10-08"), prefs.getStringSet("fire-core", null))
    }

    @Test
    fun `commits that change nothing do not write the file`() {
        val prefs = prefs("unchanged")

        assertTrue(prefs.edit().commit())
        assertTrue(prefs.edit().clear().remove("missing").commit())

        assertFalse(file("unchanged").exists())
    }

    @Test
    fun `missing file reads as empty`() {
        val prefs = prefs("absent")

        assertTrue(prefs.all.isEmpty())
        assertFalse(file("absent").exists())
    }

    @Test
    fun `corrupt file is logged, read as empty and overwritten`() {
        file("corrupt").parentFile.mkdirs()
        file("corrupt").writeText("not json")

        val prefs = prefs("corrupt")

        assertTrue(prefs.all.isEmpty())
        assertEquals(1, platform.takeLogs("PreferencesFile Ignoring unreadable preferences file"))
        prefs.edit().putString("key", "value").commit()
        assertEquals(setOf("key"), Json.parseToJsonElement(file("corrupt").readText()).jsonObject.keys)
    }

    @Test
    fun `failed commit keeps the new values in memory and returns false`() {
        val prefs = prefs()
        prefs.edit().putString("key", "before").commit()
        blockWrites("frc_app_firebase_settings")

        assertFalse(prefs.edit().putString("key", "after").commit())
        // As on Android, memory is updated even when the file cannot be written
        assertEquals("after", prefs.getString("key", null))
        assertEquals(1, platform.takeLogs("PreferencesFile Failed to write preferences file"))
    }

    @Test
    fun `commit after a failed write writes the values`() {
        val prefs = prefs()
        prefs.edit().putString("key", "before").commit()
        blockWrites("frc_app_firebase_settings")
        assertFalse(prefs.edit().putString("key", "after").commit())
        assertEquals(1, platform.takeLogs("PreferencesFile Failed to write preferences file"))
        unblockWrites("frc_app_firebase_settings")

        assertTrue(prefs.edit().putString("key", "after").commit())

        assertEquals(setOf("key"), Json.parseToJsonElement(file("frc_app_firebase_settings").readText()).jsonObject.keys)
    }

    @Test
    fun `commit recreates a deleted file even when values are unchanged`() {
        val prefs = prefs()
        prefs.edit().putString("key", "value").commit()
        file("frc_app_firebase_settings").delete()

        prefs.edit().putString("key", "value").commit()

        assertTrue(file("frc_app_firebase_settings").exists())
    }

    @Test
    fun `concurrent commits all land in the file`() {
        val prefs = prefs()
        (0 until 8).map { i ->
            thread { repeat(50) { j -> prefs.edit().putInt("key-$i-$j", j).commit() } }
        }.forEach { it.join() }

        assertEquals(400, prefs.all.size)
        assertEquals(400, Json.parseToJsonElement(file("frc_app_firebase_settings").readText()).jsonObject.size)
    }

    @Test
    fun `files first written concurrently in a new folder are all saved`() {
        repeat(20) { round ->
            val start = CountDownLatch(1)
            val results = Collections.synchronizedList(mutableListOf<Boolean>())
            (0 until 8).map { i ->
                val prefs = PreferencesFile.at(File(folder.root, "round-$round/shared_prefs/file-$i.json"))
                thread {
                    start.await()
                    results.add(prefs.edit().putInt("key", i).commit())
                }
            }.also { start.countDown() }.forEach { it.join() }

            assertEquals(List(8) { true }, results.toList())
        }
    }

    @Test
    fun `changing a returned string set does not change the stored value`() {
        val prefs = prefs()
        prefs.edit().putStringSet("dates", setOf("2026-10-08")).commit()

        (prefs.getStringSet("dates", null) as MutableSet<String>).add("2026-10-09")
        @Suppress("UNCHECKED_CAST")
        (prefs.all["dates"] as MutableSet<String>).add("2026-10-10")

        assertEquals(setOf("2026-10-08"), prefs.getStringSet("dates", null))
    }

    @Test
    fun `same path returns the same instance`() {
        assertSame(prefs("shared"), prefs("shared"))
    }
}
