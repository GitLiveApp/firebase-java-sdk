
import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

internal class FirebaseAuthTest : FirebaseTest() {
    @Test
    fun testCreateUserWithEmailAndPassword() =
        runTest {
            val email = "test+${UUID.randomUUID()}@test.com"
            val createResult = auth.createUserWithEmailAndPassword(email, "test123").await()
            assertNotEquals(null, createResult.user?.uid)
            // assertEquals(null, createResult.user?.displayName)
            // assertEquals(null, createResult.user?.phoneNumber)
            assertEquals(false, createResult.user?.isAnonymous)
            assertEquals(email, createResult.user?.email)
            assertNotEquals("", createResult.user!!.email)

            val signInResult = auth.signInWithEmailAndPassword(email, "test123").await()
            assertEquals(createResult.user?.uid, signInResult.user?.uid)
        }

    @Test
    fun testUpdateProfile() =
        runTest {
            val user = createUser()
            user
                ?.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest
                        .Builder()
                        .setDisplayName("testDisplayName")
                        .setPhotoUri(Uri.parse("https://picsum.photos/100"))
                        .build()
                )?.await()
            assertEquals("testDisplayName", auth.currentUser?.displayName)
            assertEquals("https://picsum.photos/100", auth.currentUser?.photoUrl)
        }

    @Test
    fun testSignInAnonymously() =
        runTest {
            val signInResult = auth.signInAnonymously().await()
            assertNotEquals("", signInResult.user!!.email)
            assertEquals(true, signInResult.user?.isAnonymous)
        }

    private suspend fun createUser(email: String = "test+${UUID.randomUUID()}@test.com"): FirebaseUser? =
        auth
            .createUserWithEmailAndPassword(email, "test123")
            .await()
            .user
}
