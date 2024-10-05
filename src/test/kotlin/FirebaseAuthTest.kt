import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.initialize
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

internal class FirebaseAuthTest : FirebaseTest() {

    private lateinit var auth: FirebaseAuth

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
            .setProjectId("fir-java-sdk")
            .setApplicationId("1:341458593155:web:bf8e1aa37efe01f32d42b6")
            .setApiKey("AIzaSyCvVHjTJHyeStnzIE7J9LLtHqWk6reGM08")
            .setDatabaseUrl("https://fir-java-sdk-default-rtdb.firebaseio.com")
            .setStorageBucket("fir-java-sdk.appspot.com")
            .setGcmSenderId("341458593155")
            .build()

        val firebaseApp = Firebase.initialize(Application(), options)
        auth = FirebaseAuth.getInstance(app = firebaseApp)
    }

    @Test
    fun testCreateUserWithEmailAndPassword() = runTest {
        val email = "test+${Random.nextInt(100000)}@test.com"
        val createResult = auth.createUserWithEmailAndPassword(
            email,
            "test123"
        ).await()
        assertNotEquals(null, createResult.user?.uid)
        // assertEquals(null, createResult.user?.displayName)
        // assertEquals(null, createResult.user?.phoneNumber)
        assertEquals(false, createResult.user?.isAnonymous)
        assertEquals(email, createResult.user?.email)

        val signInResult = auth.signInWithEmailAndPassword(email, "test123").await()
        assertEquals(createResult.user?.uid, signInResult.user?.uid)

        signInResult.user!!.delete()
    }

    @Test
    fun testSignInAnonymously() = runTest {
        val signInResult = auth.signInAnonymously().await()
        assertEquals(true, signInResult.user?.isAnonymous)
        signInResult.user!!.delete()
    }
}
