/*
 * Tests that Firebase's heartbeat reporting (firebase-common), which Installations attaches to
 * its requests, stores its heartbeats through the file-backed preferences.
 */
import android.content.PreferencesFile
import com.google.firebase.heartbeatinfo.DefaultHeartBeatController
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HeartBeatTest : FirebaseTest() {

    @Test
    fun `registered heartbeat is stored for today`(): Unit = runTest {
        app.get(DefaultHeartBeatController::class.java).registerHeartBeat().await()

        // A copy is read by a new instance, so the check covers what reached the disk
        val stored = File(dataFolder, "files/shared_prefs").listFiles()!!.single { it.name.startsWith("FirebaseHeartBeat") }
        val heartbeats = PreferencesFile.at(stored.copyTo(File(dataFolder, "heartbeats-copy.json")))
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

        assertEquals(1L, heartbeats.getLong("fire-count", 0L))
        assertTrue(heartbeats.all.values.any { it is Set<*> && today in it })
    }
}
