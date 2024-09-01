import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.firestore.firestore
import com.google.firebase.initialize
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class FirestoreTest : FirebaseTest() {
    @Before
    fun initialize() {
        FirebasePlatform.initializeFirebasePlatform(object : FirebasePlatform() {
            val storage = mutableMapOf<String, String>()
            override fun store(key: String, value: String) = storage.set(key, value)
            override fun retrieve(key: String) = storage[key]
            override fun clear(key: String) { storage.remove(key) }
            override fun log(msg: String) = println(msg)
            override fun getDatabasePath(name: String) = File("./build/$name")
        })
        val options = FirebaseOptions.Builder()
            .setProjectId("my-firebase-project")
            .setApplicationId("1:27992087142:android:ce3b6448250083d1")
            .setApiKey("AIzaSyADUe90ULnQDuGShD9W23RDP0xmeDc6Mvw")
            // setDatabaseURL(...)
            // setStorageBucket(...)
            .build()
        Firebase.initialize(Application(), options)
        Firebase.firestore.disableNetwork()
    }

    @Test
    fun testFirestore(): Unit = runTest {
        val data = Data("jim")
        val doc = Firebase.firestore.document("sally/jim")
        doc.set(data)
        assertEquals(data, doc.get().await().toObject(Data::class.java))
    }

    data class Data(val name: String = "none")
}
