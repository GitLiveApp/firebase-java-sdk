import android.app.Application
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.initialize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirebaseAppTest : FirebaseTest() {
    @Test
    fun `initialize firebase`() {
        FirebasePlatform.initializeFirebasePlatform(
            object : FirebasePlatform() {
                val storage = mutableMapOf<String, String>()

                override fun store(
                    key: String,
                    value: String
                ) = storage.set(key, value)

                override fun retrieve(key: String) = storage[key]

                override fun clear(key: String) {
                    storage.remove(key)
                }

                override fun log(msg: String) = println(msg)

                // Keeps Firebase's data in this test's folder
                override fun getDatabasePath(name: String) = File(dataFolder, name)

                override fun getFilesDir() = File(dataFolder, "files")
            }
        )
        val options =
            FirebaseOptions
                .Builder()
                .setProjectId("fir-java-sdk")
                .setApplicationId("1:341458593155:web:bf8e1aa37efe01f32d42b6")
                .setApiKey("AIzaSyCvVHjTJHyeStnzIE7J9LLtHqWk6reGM08")
                .setDatabaseUrl("https://fir-java-sdk-default-rtdb.firebaseio.com")
                .setStorageBucket("fir-java-sdk.appspot.com")
                .setGcmSenderId("341458593155")
                .build()
        Firebase.initialize(Application(), options)
    }

    @Test
    fun `data collection default can be changed`() {
        // The Boolean? overload; the boolean one is deprecated
        val enabled: Boolean? = true
        val disabled: Boolean? = false

        app.setDataCollectionDefaultEnabled(enabled)
        assertTrue(app.isDataCollectionDefaultEnabled)

        app.setDataCollectionDefaultEnabled(disabled)
        assertFalse(app.isDataCollectionDefaultEnabled)
    }

    @Test
    fun `initialize firebase with a plain Context`() {
        val app = Firebase.initialize(Context(), options)

        assertTrue(app.applicationContext is Application)
    }
}
