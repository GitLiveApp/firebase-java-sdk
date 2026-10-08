import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.initialize
import org.junit.After
import org.junit.Before
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

abstract class FirebaseTest {

    protected val options: FirebaseOptions =
        FirebaseOptions
            .Builder()
            .setProjectId("fir-java-sdk")
            .setApplicationId("1:341458593155:web:bf8e1aa37efe01f32d42b6")
            .setApiKey("AIzaSyCvVHjTJHyeStnzIE7J9LLtHqWk6reGM08")
            .setDatabaseUrl("https://fir-java-sdk-default-rtdb.firebaseio.com")
            .setStorageBucket("fir-java-sdk.appspot.com")
            .setGcmSenderId("341458593155")
            .build()

    protected val app: FirebaseApp by lazy {
        Firebase.initialize(Application(), options)
    }

    // Each test gets its own folder for Firebase's files and databases. It is kept until the next test run, so
    // background work that outlives a test writes into this folder instead of failing into a later test.
    protected val dataFolder = File("build/test-data/${UUID.randomUUID()}")

    protected open fun log(message: String) = println(message)

    @Before
    fun beforeEach() {
        FirebasePlatform.initializeFirebasePlatform(
            object : FirebasePlatform() {
                val storage = ConcurrentHashMap<String, String>()

                override fun store(
                    key: String,
                    value: String
                ) = storage.set(key, value)

                override fun retrieve(key: String) = storage[key]

                override fun clear(key: String) {
                    storage.remove(key)
                }

                override fun log(msg: String) = this@FirebaseTest.log(msg)

                override fun getDatabasePath(name: String) = File(dataFolder, name)

                override fun getFilesDir() = File(dataFolder, "files")
            }
        )
    }

    @After
    fun clear() {
        FirebaseApp.clearInstancesForTest()
    }
}
