/*
 * Tests for the default folders FirebasePlatform gives Firebase for its databases and files, which
 * apps override to choose persistent folders of their own.
 */
import com.google.firebase.FirebasePlatform
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FirebasePlatformTest {

    private val platform = object : FirebasePlatform() {
        override fun store(key: String, value: String) {}
        override fun retrieve(key: String): String? = null
        override fun clear(key: String) {}
        override fun log(msg: String) {}
    }

    private val temp = System.getProperty("java.io.tmpdir")

    @Test
    fun `databases default to the temp folder`() {
        assertEquals(File("$temp${File.separatorChar}firestore.db"), platform.getDatabasePath("firestore.db"))
    }

    @Test
    fun `files default to firebase-files in the temp folder`() {
        assertEquals(File("$temp${File.separatorChar}firebase-files"), platform.getFilesDir())
    }
}
