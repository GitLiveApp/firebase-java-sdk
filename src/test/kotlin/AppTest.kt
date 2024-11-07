import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.initialize
import org.junit.Test

class AppTest : FirebaseTest() {
    @Test
    fun testInitialize() {
        FirebasePlatform.initializeFirebasePlatform(
            object : FirebasePlatform() {
                val storage = mutableMapOf<String, String>()

                override fun store(
                    key: String,
                    value: String,
                ) = storage.set(key, value)

                override fun retrieve(key: String) = storage[key]

                override fun clear(key: String) {
                    storage.remove(key)
                }

                override fun log(msg: String) = println(msg)
            },
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
        val app = Firebase.initialize(Application(), options)
    }
}
