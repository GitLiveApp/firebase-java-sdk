import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.internal.Slashes
import com.google.firebase.storage.storage
import org.junit.Assert
import org.junit.Test

class FirestoreStorageTest : FirebaseTest() {

    @Test
    fun test_parsing_storage_uri() {
        val input = "gs://edifikana-stage.appspot.com"

        val normalized = Slashes.normalizeSlashes(input.substring(5))
        val fullUri = Slashes.preserveSlashEncode(normalized)
        val parsedUri = Uri.parse("gs://$fullUri")

        Assert.assertEquals("gs://edifikana-stage.appspot.com", parsedUri.toString())
    }

    @Test
    fun test_loading_default_storage_client() {
        Firebase.storage(app)
    }

    @Test
    fun test_getting_root_reference() {
        val storage = Firebase.storage(app)
        val reference = storage.reference
        Assert.assertNotNull(reference)
    }

    @Test
    fun test_getting_child_reference() {
        val storage = Firebase.storage(app)
        val reference = storage.reference
        val downloadRef = reference.child("mountains.jpg")
        val downloadUrl = downloadRef.downloadUrl

        Assert.assertNotNull(downloadUrl)
    }
}
