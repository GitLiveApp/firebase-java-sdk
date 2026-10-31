/*
 * Exercises the parts of the Firebase Auth API that need no server: the Android SDK's shapes, constants, builders,
 * credential types and the settings FirebaseAuth keeps.
 */
import android.app.Activity
import android.net.Uri
import com.google.firebase.FirebaseException
import com.google.firebase.auth.ActionCodeResult
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.ActionCodeUrl
import com.google.firebase.auth.EmailAuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FacebookAuthCredential
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FederatedAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.FirebaseAuthProvider
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GithubAuthProvider
import com.google.firebase.auth.MultiFactorInfo
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorGenerator
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.google.firebase.auth.TwitterAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class FirebaseAuthApiTest : FirebaseTest() {
    private val auth by lazy { FirebaseAuth.getInstance(app) }

    @Test
    fun `action code settings carry their fields`() {
        val settings =
            ActionCodeSettings
                .newBuilder()
                .setUrl("https://example.com/finish")
                .setHandleCodeInApp(true)
                .setIOSBundleId("com.example.ios")
                .setAndroidPackageName("com.example", true, "12")
                .setLinkDomain("example.page.link")
                .build()
        assertEquals("https://example.com/finish", settings.getUrl())
        assertTrue(settings.canHandleCodeInApp())
        assertEquals("com.example.ios", settings.getIOSBundle())
        assertEquals("com.example", settings.getAndroidPackageName())
        assertTrue(settings.getAndroidInstallApp())
        assertEquals("12", settings.getAndroidMinimumVersion())
        assertEquals("example.page.link", settings.getLinkDomain())

        val builder = ActionCodeSettings.newBuilder().setUrl("https://example.com")
        assertEquals("https://example.com", builder.getUrl())
        assertFalse(builder.getHandleCodeInApp())
        assertNull(builder.getLinkDomain())
        assertThrows(IllegalArgumentException::class.java) { ActionCodeSettings.newBuilder().build() }
    }

    @Test
    fun `action links are parsed, unwrapping dynamic links`() {
        val link =
            "https://fir-java-sdk.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=abc123&apiKey=key" +
                "&continueUrl=https%3A%2F%2Fexample.com%2Ffinish&lang=en"
        val url = ActionCodeUrl.parseLink(link)!!
        assertEquals(ActionCodeResult.PASSWORD_RESET, url.operation)
        assertEquals("abc123", url.code)
        assertEquals("key", url.apiKey)
        assertEquals("https://example.com/finish", url.continueUrl)
        assertEquals("en", url.languageCode)

        val inner = "https://fir-java-sdk.firebaseapp.com/__/auth/action?mode=signIn&oobCode=xyz"
        val wrapped = "https://example.page.link/?link=" + URLEncoder.encode(inner, "UTF-8")
        assertEquals(ActionCodeResult.SIGN_IN_WITH_EMAIL_LINK, ActionCodeUrl.parseLink(wrapped)!!.operation)
        assertTrue(auth.isSignInWithEmailLink(wrapped))
        assertFalse(auth.isSignInWithEmailLink(link))
        assertNull(ActionCodeUrl.parseLink("https://example.com/"))
        assertNull(ActionCodeUrl.parseLink("not a url"))
    }

    @Test
    fun `providers expose the android constants and credential types`() {
        assertEquals("password", EmailAuthProvider.PROVIDER_ID)
        assertEquals("emailLink", EmailAuthProvider.EMAIL_LINK_SIGN_IN_METHOD)
        assertEquals("facebook.com", FacebookAuthProvider.PROVIDER_ID)
        assertEquals("github.com", GithubAuthProvider.GITHUB_SIGN_IN_METHOD)
        assertEquals("twitter.com", TwitterAuthProvider.TWITTER_SIGN_IN_METHOD)
        assertEquals("phone", PhoneAuthProvider.PHONE_SIGN_IN_METHOD)
        assertEquals("firebase", FirebaseAuthProvider.PROVIDER_ID)
        assertEquals("factorIdKey", MultiFactorInfo.FACTOR_ID_KEY)

        val email = EmailAuthProvider.getCredential("a@b.c", "secret")
        assertTrue(email is EmailAuthCredential)
        assertEquals("password", email.provider)
        assertEquals("password", email.signInMethod)
        assertFalse(email.toString().contains("secret"))
        val link = EmailAuthProvider.getCredentialWithLink("a@b.c", "https://x/?mode=signIn&oobCode=code1")
        assertEquals("emailLink", link.signInMethod)
        assertThrows(IllegalArgumentException::class.java) { EmailAuthProvider.getCredentialWithLink("a@b.c", "https://x/") }

        assertTrue(FacebookAuthProvider.getCredential("token") is FacebookAuthCredential)
        assertEquals("github.com", GithubAuthProvider.getCredential("token").provider)
        val twitter = TwitterAuthProvider.getCredential("token", "secret")
        assertEquals("twitter.com", twitter.provider)
        assertFalse(twitter.toString().contains("secret"))

        val phone = PhoneAuthProvider.getCredential("verification", "123456")
        assertEquals("123456", phone.smsCode)
        assertEquals("phone", phone.signInMethod)
    }

    @Test
    fun `oauth builders expose their state`() {
        val builder = OAuthProvider.newBuilder("apple.com", auth).setScopes(listOf("email")).addCustomParameter("locale", "fr")
        assertEquals(listOf("email"), builder.scopes)
        val provider = builder.build()
        assertEquals("apple.com", provider.providerId)
        assertEquals(mapOf("locale" to "fr"), provider.customParameters)
        assertTrue(provider is FederatedAuthProvider)

        val credentialBuilder = OAuthProvider.newCredentialBuilder("apple.com").setIdToken("id").setAccessToken("access")
        assertEquals("id", credentialBuilder.idToken)
        assertEquals("access", credentialBuilder.accessToken)
        val credential = credentialBuilder.build() as OAuthCredential
        assertEquals("id", credential.idToken)
        assertEquals("access", credential.accessToken)
        assertNull(credential.secret)
    }

    @Test
    fun `profile change requests expose their fields`() {
        val request = UserProfileChangeRequest.Builder().setDisplayName("Name").setPhotoUri(Uri.parse("https://example.com/p.png")).build()
        assertEquals("Name", request.displayName)
        assertEquals("https://example.com/p.png", request.photoUri.toString())
        val builder = UserProfileChangeRequest.Builder().setDisplayName("Other")
        assertEquals("Other", builder.getDisplayName())
        assertNull(builder.getPhotoUri())
    }

    @Test
    fun `auth keeps language, tenant and auth domain settings`() =
        runTest {
            assertNull(auth.languageCode)
            auth.languageCode = "de"
            assertEquals("de", auth.languageCode)
            auth.useAppLanguage()
            assertEquals(Locale.getDefault().toLanguageTag(), auth.languageCode)
            auth.languageCode = null

            auth.tenantId = "tenant"
            assertEquals("tenant", auth.tenantId)
            auth.tenantId = null
            auth.customAuthDomain = "auth.example.com"
            assertEquals("auth.example.com", auth.customAuthDomain)

            assertNull(auth.pendingAuthResult)
            assertNotNull(auth.firebaseAuthSettings)
            auth.firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
            auth.initializeRecaptchaConfig().await()
        }

    @Test
    fun `exceptions carry the android details`() {
        val collision = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "exists")
        assertNull(collision.getEmail())
        assertNull(collision.getUpdatedCredential())
        assertEquals("too short", FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "too short").getReason())
        assertNull(FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak").getReason())
        assertEquals("ERROR_MISSING_ACTIVITY", FirebaseAuthMissingActivityForRecaptchaException().errorCode)
    }

    @Test
    fun `phone verification reports that it is unsupported`() {
        var failure: FirebaseException? = null
        val options =
            PhoneAuthOptions
                .newBuilder(auth)
                .setPhoneNumber("+15555550100")
                .setTimeout(30L, TimeUnit.SECONDS)
                .setCallbacks(
                    object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        override fun onVerificationCompleted(credential: PhoneAuthCredential) = fail("the JVM cannot verify a phone number")

                        override fun onVerificationFailed(exception: FirebaseException) {
                            failure = exception
                        }
                    }
                ).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        assertNotNull(failure)
        assertThrows(IllegalArgumentException::class.java) { PhoneAuthOptions.newBuilder(auth).setPhoneNumber("+1").build() }

        assertEquals("phone", PhoneMultiFactorGenerator.getAssertion(PhoneAuthProvider.getCredential("v", "1")).factorId)
        assertEquals("totp", TotpMultiFactorGenerator.getAssertionForSignIn("enrollment", "000000").factorId)
    }

    @Test
    fun `activity flows fail on the jvm`() {
        val task = auth.startActivityForSignInWithProvider(Activity(), OAuthProvider.newBuilder("apple.com", auth).build())
        assertTrue(task.isComplete)
        assertTrue(task.exception is UnsupportedOperationException)
    }
}
