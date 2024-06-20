import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.initialize
import com.google.firebase.storage.internal.Slashes
import com.google.firebase.storage.storage
import fakes.FakeFirebasePlatform
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class FirestoreStorageTest : FirebaseTest() {

    private lateinit var app: FirebaseApp

    @Before
    fun initialize() {
        FirebasePlatform.initializeFirebasePlatform(FakeFirebasePlatform())
        val options = FirebaseOptions
            .Builder()
            .setProjectId("my-firebase-project")
            .setApplicationId("1:27992087142:android:ce3b6448250083d1")
            .setApiKey("AIzaSyADUe90ULnQDuGShD9W23RDP0xmeDc6Mvw")
            .setStorageBucket("fir-kotlin-sdk.appspot.com")
            // setDatabaseURL(...)
            .build()
        app = Firebase.initialize(Application(), options)
    }

    @Test
    fun test_parsing_storage_uri() {
        val input = "gs://edifikana-stage.appspot.com"

        val normalized = Slashes.normalizeSlashes(input.substring(5))
        val fullUri = Slashes.preserveSlashEncode(normalized)
        val parsedUri = Uri.parse("gs://$fullUri");

        Assert.assertEquals("gs://edifikana-stage.appspot.com", parsedUri.toString())
    }

    @Test
    fun test_loading_default_storage_client() {
        Firebase.storage
    }

    @Test
    fun test_getting_root_reference() {
        val storage = Firebase.storage
        val reference = storage.reference
        Assert.assertNotNull(reference)
    }

    @Test
    fun test_getting_child_reference() {
        val storage = Firebase.storage
        val reference = storage.reference
        val downloadRef = reference.child("mountains.jpg")
        val downloadUrl = downloadRef.downloadUrl

        Assert.assertNotNull(downloadUrl)
    }
}
