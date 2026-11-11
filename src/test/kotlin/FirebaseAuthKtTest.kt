/*
 * Exercises the AuthKt facade, the Kotlin extensions the Android SDK's firebase-auth ships, against the builders and
 * instances they stand for. None of it needs a server.
 */
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.actionCodeSettings
import com.google.firebase.auth.auth
import com.google.firebase.auth.oAuthCredential
import com.google.firebase.auth.oAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAuthKtTest : FirebaseTest() {

    @Test
    fun `auth extensions return the FirebaseAuth of the app`() {
        assertSame(FirebaseAuth.getInstance(app), Firebase.auth(app))
        assertSame(FirebaseAuth.getInstance(), Firebase.auth)
    }

    @Test
    fun `builder functions apply their init to a new builder`() {
        val auth = Firebase.auth(app)

        val settings = actionCodeSettings {
            setUrl("https://example.com/finish")
            setHandleCodeInApp(true)
        }
        assertEquals("https://example.com/finish", settings.url)

        val provider = oAuthProvider("apple.com") { setScopes(listOf("email")) }
        assertEquals("apple.com", provider.providerId)
        assertEquals(listOf("email"), provider.scopes)

        val providerWithAuth = oAuthProvider("github.com", auth) { addCustomParameter("locale", "fr") }
        assertEquals("github.com", providerWithAuth.providerId)
        assertEquals(mapOf("locale" to "fr"), providerWithAuth.customParameters)

        val credential = oAuthCredential("apple.com") {
            setIdToken("id")
            setAccessToken("access")
        }
        assertTrue(credential is OAuthCredential)
        assertEquals("apple.com", credential.provider)
        assertEquals("id", (credential as OAuthCredential).idToken)
        assertEquals("access", credential.accessToken)

        val request = userProfileChangeRequest {
            setDisplayName("Name")
            setPhotoUri(Uri.parse("https://example.com/photo.png"))
        }
        assertEquals("Name", request.displayName)
        assertEquals(Uri.parse("https://example.com/photo.png"), request.photoUri)
    }
}
