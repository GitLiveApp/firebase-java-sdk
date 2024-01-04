import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.firestore.firestore
import com.google.firebase.initialize
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test


class FirestoreTest {
    @Before
    fun initialize() {
        FirebasePlatform.initializeFirebasePlatform(object : FirebasePlatform() {
            val storage = mutableMapOf<String, String>()
            override fun store(key: String, value: String) = storage.set(key, value)
            override fun retrieve(key: String) = storage[key]
            override fun clear(key: String) { storage.remove(key) }
            override fun log(msg: String) = println(msg)
        })
        val options = FirebaseOptions.Builder()
            .setProjectId("my-firebase-project")
            .setApplicationId("1:27992087142:android:ce3b6448250083d1")
            .setApiKey("AIzaSyADUe90ULnQDuGShD9W23RDP0xmeDc6Mvw")
            // setDatabaseURL(...)
            // setStorageBucket(...)
            .build()
        Firebase.initialize(Application(), options)
    }

    @Test
    fun testFirestore(): Unit = runBlocking {
        Firebase.firestore.document("sally/jim").get().await()
    }
}