import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.initialize
import org.junit.Before
import java.io.File

abstract class FirebaseTest {
    protected val app: FirebaseApp get() {
        val options = FirebaseOptions.Builder()
            .setProjectId("my-firebase-project")
            .setApplicationId("1:27992087142:android:ce3b6448250083d1")
            .setApiKey("AIzaSyADUe90ULnQDuGShD9W23RDP0xmeDc6Mvw")
            .setStorageBucket("fir-kotlin-sdk.appspot.com")
            .build()

        return Firebase.initialize(Application(), options)
    }

    @Before
    fun beforeEach() {
        FirebasePlatform.initializeFirebasePlatform(object : FirebasePlatform() {
            val storage = mutableMapOf<String, String>()
            override fun store(key: String, value: String) = storage.set(key, value)
            override fun retrieve(key: String) = storage[key]
            override fun clear(key: String) { storage.remove(key) }
            override fun log(msg: String) = println(msg)
            override fun getDatabasePath(name: String) = File("./build/$name")
        })
        FirebaseApp.clearInstancesForTest()
    }
}
