import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthTest : FirebaseTest() {
    private fun createAuth(): FirebaseAuth {
        return FirebaseAuth(app).apply {
            useEmulator("localhost", 9099)
        }
    }

    @Test
    fun `should authenticate via anonymous auth`() = runTest {
        val auth = createAuth()

        auth.signInAnonymously().await()

        assertEquals(true, auth.currentUser?.isAnonymous)
    }

    @Test
    fun `should authenticate via email and password`() = runTest {
        val auth = createAuth()

        auth.signInWithEmailAndPassword("email@example.com", "securepassword").await()

        assertEquals(false, auth.currentUser?.isAnonymous)
    }

    @Test
    fun `should throw exception on invalid password`() {
        val auth = createAuth()

        val exception = assertThrows(FirebaseAuthInvalidUserException::class.java) {
            runBlocking {
                auth.signInWithEmailAndPassword("email@example.com", "wrongpassword").await()
            }
        }

        assertEquals("INVALID_PASSWORD", exception.errorCode)
    }
}
