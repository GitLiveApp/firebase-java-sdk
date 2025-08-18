import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.initialize
import org.junit.After
import org.junit.Before
import java.io.File

abstract class FirebaseTest {

    protected val app: FirebaseApp by lazy {
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

    @Before
    fun beforeEach() {
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

                override fun getDatabasePath(name: String) = File("./build/$name")
            }
        )
    }

    @After
    fun clear() {
        FirebaseApp.clearInstancesForTest()
    }
}
