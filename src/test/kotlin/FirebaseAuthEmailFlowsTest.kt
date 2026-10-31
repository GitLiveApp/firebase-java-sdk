/*
 * The email action-code flows against the Auth emulator, whose REST API hands out the codes it would have emailed.
 */
import com.google.firebase.auth.ActionCodeResult
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class FirebaseAuthEmailFlowsTest : FirebaseTest() {
    private val email = "flows${UUID.randomUUID()}@example.com"
    private val auth by lazy { FirebaseAuth.getInstance(app) }
    private val emulator = "http://localhost:9099"

    @Before
    fun initialize() {
        auth.useEmulator("localhost", 9099)
    }

    private fun oobCodes(): List<JsonObject> =
        URL("$emulator/emulator/v1/projects/${options.projectId}/oobCodes").openStream().use { stream ->
            Json.parseToJsonElement(stream.readBytes().decodeToString()).jsonObject["oobCodes"]!!.jsonArray.map { it.jsonObject }
        }

    private fun latestCode(requestType: String): JsonObject =
        oobCodes().last { it["email"]?.jsonPrimitive?.content == email && it["requestType"]?.jsonPrimitive?.content == requestType }

    private fun lookupEmailVerified(idToken: String): Boolean {
        val connection = URL("$emulator/identitytoolkit.googleapis.com/v1/accounts:lookup?key=${options.apiKey}").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(JsonObject(mapOf("idToken" to JsonPrimitive(idToken))).toString().toByteArray()) }
        val body = connection.inputStream.use { it.readBytes().decodeToString() }
        return Json.parseToJsonElement(body).jsonObject["users"]!!.jsonArray.first().jsonObject["emailVerified"]?.jsonPrimitive?.booleanOrNull ?: false
    }

    @Test
    fun `a password reset code is checked, verified and confirmed`() =
        runTest {
            auth.createUserWithEmailAndPassword(email, "oldpass1").await()
            auth.signOut()
            auth.sendPasswordResetEmail(email).await()
            val code = latestCode("PASSWORD_RESET")["oobCode"]!!.jsonPrimitive.content

            val result = auth.checkActionCode(code).await()
            assertEquals(ActionCodeResult.PASSWORD_RESET, result.operation)
            assertEquals(email, result.info?.email)
            assertEquals(email, auth.verifyPasswordResetCode(code).await())
            auth.confirmPasswordReset(code, "newpass2").await()
            assertEquals(email, auth.signInWithEmailAndPassword(email, "newpass2").await().user?.email)
        }

    @Test
    fun `an email link signs its address in`() =
        runTest {
            val settings = ActionCodeSettings.newBuilder().setUrl("https://example.com/finish").setHandleCodeInApp(true).build()
            auth.sendSignInLinkToEmail(email, settings).await()
            val link = latestCode("EMAIL_SIGNIN")["oobLink"]!!.jsonPrimitive.content

            assertTrue(auth.isSignInWithEmailLink(link))
            val result = auth.signInWithEmailLink(email, link).await()
            assertEquals(email, result.user?.email)
            assertTrue(result.additionalUserInfo!!.isNewUser)
            assertEquals(email, auth.currentUser?.email)
            assertTrue(auth.currentUser!!.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID })
        }

    @Test
    fun `an email verification code verifies the address`() =
        runTest {
            val user = auth.createUserWithEmailAndPassword(email, "pass1234").await().user!!
            assertFalse(user.isEmailVerified)
            user.sendEmailVerification().await()
            val code = latestCode("VERIFY_EMAIL")["oobCode"]!!.jsonPrimitive.content
            assertEquals(ActionCodeResult.VERIFY_EMAIL, auth.checkActionCode(code).await().operation)
            assertThrows(FirebaseAuthActionCodeException::class.java) { runBlocking { auth.verifyPasswordResetCode(code).await() } }
            auth.applyActionCode(code).await()
            assertTrue(lookupEmailVerified(user.getIdToken(false).await().token!!))
        }

    @Test
    fun `an email credential links to an anonymous account and signs in again`() =
        runTest {
            val anonymous = auth.signInAnonymously().await().user!!
            val linked = anonymous.linkWithCredential(EmailAuthProvider.getCredential(email, "linkpass1")).await()
            assertEquals(anonymous.uid, linked.user!!.uid)
            assertEquals(email, linked.user!!.email)
            assertFalse(linked.user!!.isAnonymous)

            val relink = assertThrows(FirebaseAuthUserCollisionException::class.java) {
                runBlocking { linked.user!!.linkWithCredential(EmailAuthProvider.getCredential(email, "another")).await() }
            }
            assertEquals("ERROR_PROVIDER_ALREADY_LINKED", relink.errorCode)
            auth.signOut()
            val signedIn = auth.signInWithCredential(EmailAuthProvider.getCredential(email, "linkpass1")).await()
            assertEquals(anonymous.uid, signedIn.user!!.uid)
            assertFalse(signedIn.additionalUserInfo!!.isNewUser)
            @Suppress("DEPRECATION")
            assertEquals(listOf(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD), auth.fetchSignInMethodsForEmail(email).await().signInMethods)
        }

    @Test
    fun `updateCurrentUser restores a signed-out user`() =
        runTest {
            val user = auth.createUserWithEmailAndPassword(email, "pass1234").await().user!!
            auth.signOut()
            assertNull(auth.currentUser)
            auth.updateCurrentUser(user).await()
            assertEquals(user.uid, auth.currentUser?.uid)
        }
}
