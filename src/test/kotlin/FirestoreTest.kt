import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.firestore.firestore
import com.google.firebase.initialize
import fakes.FakeFirebasePlatform
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class FirestoreTest : FirebaseTest() {
    @Before
    fun initialize() {
        FirebasePlatform.initializeFirebasePlatform(FakeFirebasePlatform())
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
    fun testFirestore(): Unit = runBlocking {
        val data = Data("jim")
        val doc = Firebase.firestore.document("sally/jim")
        doc.set(data)
        assertEquals(data, doc.get().await().toObject(Data::class.java))
    }

    data class Data(val name: String = "none")
}
