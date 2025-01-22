import android.net.Uri
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AuthTest : FirebaseTest() {
    private val email = "email${UUID.randomUUID()}@example.com"

    @Before
    fun initialize() {
        auth.apply {
            useEmulator("localhost", 9099)
        }
    }

    @Test
    fun `should authenticate via anonymous auth`() =
        runTest {
            auth.signInAnonymously().await()

            assertEquals(true, auth.currentUser?.isAnonymous)
        }

    @Test
    fun `should create user via email and password`() =
        runTest {
            val createResult = auth.createUserWithEmailAndPassword(email, "test123").await()
            assertNotEquals(null, createResult.user?.uid)
            assertEquals(null, createResult.user?.displayName)
            // assertEquals(null, createResult.user?.phoneNumber)
            assertEquals(false, createResult.user?.isAnonymous)
            assertEquals(email, createResult.user?.email)
            assertNotEquals("", createResult.user!!.email)

            val signInResult = auth.signInWithEmailAndPassword(email, "test123").await()
            assertEquals(createResult.user?.uid, signInResult.user?.uid)
        }

    @Test
    fun `should authenticate via email and password`() =
        runTest {
            auth.createUserWithEmailAndPassword(email, "test123").await()

            auth.signInWithEmailAndPassword(email, "test123").await()

            assertEquals(false, auth.currentUser?.isAnonymous)
        }

    /*@Test
    fun `should authenticate via custom token`() =
        runTest {
            val user = auth.createUserWithEmailAndPassword(email, "test123").await()
            auth
                .signInWithCustomToken(
                    user.user
                        .getIdToken(false)
                        .await()
                        .token ?: "",
                ).await()

            assertEquals(false, auth.currentUser?.isAnonymous)
        }*/

    @Test
    fun `should update displayName and photoUrl`() =
        runTest {
            auth
                .createUserWithEmailAndPassword(email, "test123")
                .await()
                .user
            auth.currentUser
                ?.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest
                        .Builder()
                        .setDisplayName("testDisplayName")
                        .setPhotoUri(Uri.parse("https://picsum.photos/100"))
                        .build(),
                )?.await()
            assertEquals("testDisplayName", auth.currentUser?.displayName)
            assertEquals("https://picsum.photos/100", auth.currentUser?.photoUrl)
        }

    @Test
    fun `should sign in anonymously`() =
        runTest {
            val signInResult = auth.signInAnonymously().await()
            assertNotEquals("", signInResult.user!!.email)
            assertEquals(true, signInResult.user?.isAnonymous)
        }

    @Test
    fun `should throw exception on invalid password`() =
        runTest {
            auth.createUserWithEmailAndPassword(email, "test123").await()

            val exception =
                assertThrows(FirebaseAuthInvalidUserException::class.java) {
                    runBlocking {
                        auth.signInWithEmailAndPassword(email, "wrongpassword").await()
                    }
                }

            assertEquals("INVALID_LOGIN_CREDENTIALS", exception.errorCode)
        }
}
