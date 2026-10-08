import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseFirestoreTest : FirebaseTest() {

    // Stops Firestore's background work, such as cache garbage collection, so it does not log into later tests
    @After
    fun terminateFirestore(): Unit = runTest {
        Firebase.firestore(app).terminate().await()
    }

    @Test
    fun `set and get a document`(): Unit = runTest {
        val firestore = Firebase.firestore(app)
        firestore.disableNetwork().await()

        val data = Data("jim")
        val doc = firestore.document("sally/jim")
        doc.set(data)
        assertEquals(data, doc.get().await().toObject(Data::class.java))
    }

    data class Data(val name: String = "none")
}
