import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.internal.Slashes
import com.google.firebase.storage.storage
import org.junit.Assert
import org.junit.Test

class FirebaseStorageTest : FirebaseTest() {

    @Test
    fun `parsing storage uri`() {
        val input = "gs://edifikana-stage.appspot.com"

        val normalized = Slashes.normalizeSlashes(input.substring(5))
        val fullUri = Slashes.preserveSlashEncode(normalized)
        val parsedUri = Uri.parse("gs://$fullUri")

        Assert.assertEquals("gs://edifikana-stage.appspot.com", parsedUri.toString())
    }

    @Test
    fun `loading default storage client`() {
        Firebase.storage(app)
    }

    @Test
    fun `getting root reference`() {
        val storage = Firebase.storage(app)
        val reference = storage.reference
        Assert.assertNotNull(reference)
    }

    @Test
    fun `getting child reference`() {
        val storage = Firebase.storage(app)
        val reference = storage.reference
        val downloadRef = reference.child("mountains.jpg")
        val downloadUrl = downloadRef.downloadUrl

        Assert.assertNotNull(downloadUrl)
    }
}
