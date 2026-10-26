/*
 * Tests that Firebase's heartbeat reporting (firebase-common), which Installations attaches to
 * its requests, stores its heartbeats in the preferences DataStore under the files directory.
 */
import com.google.firebase.datastorage.JavaDataStorage
import com.google.firebase.heartbeatinfo.DefaultHeartBeatController
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HeartBeatTest : FirebaseTest() {

    @Test
    fun `registered heartbeat is stored for today`(): Unit = runTest {
        app.get(DefaultHeartBeatController::class.java).registerHeartBeat().await()

        // The store writes through to its file, named after the heartbeat store with the app id encoded
        val stored = File(dataFolder, "files/datastore").listFiles()!!.single { it.name.startsWith("FirebaseHeartBeat") }
        val name = URLDecoder.decode(stored.name.removeSuffix(".preferences_pb"), Charsets.UTF_8)
        val heartbeats = JavaDataStorage(app.applicationContext, name).getAllSync().mapKeys { it.key.name }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

        assertEquals(1L, heartbeats["fire-count"])
        assertTrue(heartbeats.values.any { it is Set<*> && today in it })
    }
}
