import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebasePlatform
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUserImpl
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.initialize
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FirebaseAuthCredentialTest : FirebaseTest() {
    private lateinit var server: MockWebServer
    private lateinit var auth: FirebaseAuth
    private lateinit var platform: RecordingFirebasePlatform

    @Before
    fun initializeAuth() {
        platform = RecordingFirebasePlatform()
        FirebasePlatform.initializeFirebasePlatform(platform)
        server = MockWebServer().apply { start() }
        auth = FirebaseAuth.getInstance(app).apply { useEmulator(server.hostName, server.port) }
    }

    @After
    fun stopServer() {
        server.shutdown()
        platform.close()
    }

    @Test
    fun `google credential accepts either token and never renders secrets`() {
        val credential = GoogleAuthProvider.getCredential("header.payload.signature", null)

        assertEquals("google.com", credential.provider)
        assertFalse(credential.toString().contains("header.payload.signature"))
        assertThrows(IllegalArgumentException::class.java) {
            GoogleAuthProvider.getCredential(null, null)
        }
    }

    @Test
    fun `generic oauth credential builder matches android API and redacts secrets`() {
        val credential =
            OAuthProvider
                .newCredentialBuilder("apple.com")
                .setAccessToken("access-secret")
                .setIdTokenWithRawNonce("id-secret", "raw-secret")
                .build()

        assertTrue(credential is OAuthCredential)
        assertEquals("apple.com", credential.provider)
        assertFalse(credential.toString().contains("access-secret"))
        assertFalse(credential.toString().contains("id-secret"))
        assertFalse(credential.toString().contains("raw-secret"))
    }

    @Test
    fun `phone credential is validated and never renders verification data`() {
        val credential = PhoneAuthProvider.getCredential("verification-secret", "123456")

        assertEquals("phone", credential.provider)
        assertFalse(credential.toString().contains("verification-secret"))
        assertFalse(credential.toString().contains("123456"))
        assertThrows(IllegalArgumentException::class.java) {
            PhoneAuthProvider.getCredential("", "123456")
        }
    }

    @Test
    fun `sign in with google credential calls signInWithIdp and persists the user`() =
        runTest {
            server.enqueue(successfulAuthResponse(idToken = "firebase-id-token"))

            val result =
                auth
                    .signInWithCredential(GoogleAuthProvider.getCredential("google-id-token", "google-access-token"))
                    .await()

            assertEquals("jvm-user", result.user?.uid)
            assertEquals("jvm@example.com", auth.currentUser?.email)
            assertTrue(auth.currentUser?.isEmailVerified == true)
            assertEquals(listOf("google.com"), auth.currentUser?.providerData?.map { it.providerId })
            assertTrue(platform.values.values.single().contains("firebase-id-token"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals(
                "/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp",
                request.requestUrl?.encodedPath
            )
            val json = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("http://localhost", json.getValue("requestUri").jsonPrimitive.content)
            assertTrue(json.getValue("returnSecureToken").jsonPrimitive.content.toBoolean())
            val postBody = decodeForm(json.getValue("postBody").jsonPrimitive.content)
            assertEquals("google.com", postBody["providerId"])
            assertEquals("google-id-token", postBody["id_token"])
            assertEquals("google-access-token", postBody["access_token"])
        }

    @Test
    fun `google session is restored from durable storage after app and platform recreation`() =
        runTest {
            val storageFile = Files.createTempFile("firebase-auth-session", ".properties").toFile()
            storageFile.delete()
            storageFile.deleteOnExit()
            platform.close()
            platform = RecordingFirebasePlatform(storageFile)
            FirebasePlatform.initializeFirebasePlatform(platform)
            auth = FirebaseAuth.getInstance(app).apply { useEmulator(server.hostName, server.port) }
            val persistedToken = firebaseToken()
            server.enqueue(successfulAuthResponse(idToken = persistedToken))
            auth.signInWithCredential(GoogleAuthProvider.getCredential("google-token", null)).await()
            assertTrue(Json.parseToJsonElement(platform.values.values.single()).jsonObject.containsKey("tokenIssuedAt"))
            val options = app.options

            FirebaseApp.clearInstancesForTest()
            platform.close()
            platform = RecordingFirebasePlatform(storageFile)
            FirebasePlatform.initializeFirebasePlatform(platform)
            val recreatedApp = Firebase.initialize(Application(), options)
            auth = FirebaseAuth.getInstance(recreatedApp).apply { useEmulator(server.hostName, server.port) }

            assertEquals("jvm-user", auth.currentUser?.uid)
            assertEquals("jvm@example.com", auth.currentUser?.email)
            assertEquals(listOf("google.com"), auth.currentUser?.providerData?.map { it.providerId })
            assertTrue(auth.currentUser!!.isEmailVerified)
            assertEquals(1600000000000L, auth.currentUser!!.metadata.creationTimestamp)
            assertEquals(1700000000000L, auth.currentUser!!.metadata.lastSignInTimestamp)
            assertTrue(platform.values.values.single().contains(persistedToken))
            assertEquals(persistedToken, auth.currentUser!!.getIdToken(false).await().token)
            assertEquals(1, server.requestCount)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("{}"))
            auth.currentUser!!.delete().await()
            assertEquals("/www.googleapis.com/identitytoolkit/v3/relyingparty/deleteAccount", server.takeRequest().requestUrl?.encodedPath)
        }

    @Test
    fun `unknown account creation does not use the token receipt time`() {
        val user = FirebaseUserImpl(
            app,
            Json.parseToJsonElement(
                """{"localId":"jvm-user","idToken":"token","refreshToken":"refresh","expiresIn":3600}"""
            ).jsonObject
        )
        assertEquals(0L, user.metadata.creationTimestamp)
        assertTrue(user.tokenIssuedAt > 0)
    }

    @Test
    fun `legacy stored users retain known metadata and conservatively refresh token time`() {
        val user = FirebaseUserImpl(
            app,
            Json.parseToJsonElement(
                """{"uid":"jvm-user","idToken":"token","refreshToken":"refresh","expiresIn":3600,"createdAt":1600000000000,"lastSignInAt":1700000000000,"isEmailVerified":true}"""
            ).jsonObject
        )
        assertTrue(user.isEmailVerified)
        assertEquals(1700000000000L, user.metadata.lastSignInTimestamp)
        assertEquals(0L, user.metadata.creationTimestamp)
        assertEquals(0L, user.tokenIssuedAt)
    }

    @Test
    fun `restored expired token refreshes using persisted token time`() = runTest {
        server.enqueue(successfulAuthResponse(idToken = "expired-token"))
        auth.signInWithCredential(GoogleAuthProvider.getCredential("google-token", null)).await()
        server.takeRequest()
        val storageKey = platform.values.keys.single()
        val stored = Json.parseToJsonElement(platform.values.getValue(storageKey)).jsonObject
        platform.values[storageKey] = JsonObject(stored + ("tokenIssuedAt" to JsonPrimitive(1L))).toString()
        val options = app.options
        FirebaseApp.clearInstancesForTest()
        val recreatedApp = Firebase.initialize(Application(), options)
        auth = FirebaseAuth.getInstance(recreatedApp).apply { useEmulator(server.hostName, server.port) }
        val refreshedToken = firebaseToken()
        server.enqueue(refreshResponse(refreshedToken))

        assertEquals(refreshedToken, auth.currentUser!!.getIdToken(false).await().token)
        assertEquals("/securetoken.googleapis.com/v1/token", server.takeRequest().requestUrl?.encodedPath)
        assertEquals(1600000000000L, auth.currentUser!!.metadata.creationTimestamp)
        assertEquals(1700000000000L, auth.currentUser!!.metadata.lastSignInTimestamp)
    }

    @Test
    fun `forced token refresh exchanges the refresh token and persists the result`() =
        runTest {
            server.enqueue(successfulAuthResponse(idToken = "cached-token"))
            auth.signInWithCredential(GoogleAuthProvider.getCredential("google-token", null)).await()
            server.takeRequest()
            val refreshedToken = firebaseToken()
            server.enqueue(refreshResponse(refreshedToken))

            val result = auth.currentUser!!.getIdToken(true).await()

            assertEquals(refreshedToken, result.token)
            assertEquals(1600000000000L, auth.currentUser!!.metadata.creationTimestamp)
            assertEquals(1700000000000L, auth.currentUser!!.metadata.lastSignInTimestamp)
            assertTrue(auth.currentUser!!.isEmailVerified)
            val request = server.takeRequest()
            assertEquals("/securetoken.googleapis.com/v1/token", request.requestUrl?.encodedPath)
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("refresh-token", body.getValue("refresh_token").jsonPrimitive.content)
            assertTrue(platform.values.values.single().contains(refreshedToken))
            assertEquals(refreshedToken, auth.currentUser!!.getIdToken(false).await().token)
            assertEquals(2, server.requestCount)
            server.enqueue(MockResponse().setBody("{}"))
            auth.currentUser!!.delete().await()
            assertEquals("/www.googleapis.com/identitytoolkit/v3/relyingparty/deleteAccount", server.takeRequest().requestUrl?.encodedPath)
        }

    @Test
    fun `expired token refreshes even when force refresh is false`() =
        runTest {
            server.enqueue(successfulAuthResponse(idToken = "expired-token", expiresIn = 0))
            auth.signInWithCredential(GoogleAuthProvider.getCredential("google-token", null)).await()
            server.takeRequest()
            val refreshedToken = firebaseToken()
            server.enqueue(refreshResponse(refreshedToken))

            val result = auth.currentUser!!.getIdToken(false).await()

            assertEquals(refreshedToken, result.token)
            assertEquals("/securetoken.googleapis.com/v1/token", server.takeRequest().requestUrl?.encodedPath)
            assertEquals(1600000000000L, auth.currentUser!!.metadata.creationTimestamp)
            assertEquals(1700000000000L, auth.currentUser!!.metadata.lastSignInTimestamp)
        }

    @Test
    fun `link credential sends current token and atomically replaces the session`() =
        runTest {
            server.enqueue(successfulAuthResponse(idToken = "original-token"))
            auth.signInWithCredential(GoogleAuthProvider.getCredential("google-token", null)).await()
            server.takeRequest()

            server.enqueue(successfulAuthResponse(idToken = "linked-token", providerId = "apple.com"))
            val linked =
                auth.currentUser!!
                    .linkWithCredential(
                        OAuthProvider
                            .newCredentialBuilder("apple.com")
                            .setIdTokenWithRawNonce("apple-token", "nonce-value")
                            .build()
                    ).await()

            assertEquals("jvm-user", linked.user?.uid)
            assertEquals(listOf("google.com", "apple.com"), linked.user?.providerData?.map { it.providerId })
            assertTrue(platform.values.values.single().contains("linked-token"))
            val requestJson = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals("original-token", requestJson.getValue("idToken").jsonPrimitive.content)
            val postBody = decodeForm(requestJson.getValue("postBody").jsonPrimitive.content)
            assertEquals("apple.com", postBody["providerId"])
            assertEquals("apple-token", postBody["id_token"])
            assertEquals("nonce-value", postBody["nonce"])
        }

    @Test
    fun `phone credential uses phone endpoint for sign in and linking`() =
        runTest {
            server.enqueue(phoneResponse("phone-token"))

            auth.signInWithCredential(PhoneAuthProvider.getCredential("phone-session", "123456")).await()

            val signIn = server.takeRequest()
            assertEquals("/identitytoolkit.googleapis.com/v1/accounts:signInWithPhoneNumber", signIn.requestUrl?.encodedPath)
            val signInJson = Json.parseToJsonElement(signIn.body.readUtf8()).jsonObject
            assertEquals("phone-session", signInJson.getValue("sessionInfo").jsonPrimitive.content)
            assertEquals("123456", signInJson.getValue("code").jsonPrimitive.content)
            assertEquals(listOf("phone"), auth.currentUser!!.providerData.map { it.providerId })

            server.enqueue(successfulAuthResponse(idToken = "google-token"))
            auth.signInWithCredential(GoogleAuthProvider.getCredential("google-token", null)).await()
            server.takeRequest()

            server.enqueue(phoneResponse("linked-phone-token"))
            auth.currentUser!!.linkWithCredential(PhoneAuthProvider.getCredential("link-session", "654321")).await()

            val linkJson = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals("google-token", linkJson.getValue("idToken").jsonPrimitive.content)
            assertEquals("link-session", linkJson.getValue("sessionInfo").jsonPrimitive.content)
            assertEquals("654321", linkJson.getValue("code").jsonPrimitive.content)
            assertEquals(listOf("google.com", "phone"), auth.currentUser!!.providerData.map { it.providerId })
            assertEquals(1600000000000L, auth.currentUser!!.metadata.creationTimestamp)
            assertEquals(1700000000000L, auth.currentUser!!.metadata.lastSignInTimestamp)
        }

    @Test
    fun `failed link preserves the current session`() =
        runTest {
            server.enqueue(successfulAuthResponse(idToken = "original-token"))
            auth.signInWithCredential(GoogleAuthProvider.getCredential("google-token", null)).await()
            server.takeRequest()

            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"error":{"message":"FEDERATED_USER_ID_ALREADY_LINKED"}}""")
            )

            val exception =
                assertThrows(FirebaseAuthInvalidUserException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        auth.currentUser!!
                            .linkWithCredential(
                                OAuthProvider.newCredentialBuilder("github.com").setAccessToken("github-token").build()
                            ).await()
                    }
                }
            assertEquals("jvm-user", auth.currentUser?.uid)
            assertTrue(platform.values.values.single().contains("original-token"))
            assertTrue(exception.message.orEmpty().contains("FEDERATED_USER_ID_ALREADY_LINKED"))
            assertFalse(exception.message.orEmpty().contains("original-token"))
            assertFalse(exception.message.orEmpty().contains("github-token"))
            assertFalse(exception.message.orEmpty().contains(app.options.apiKey))
        }

    @Test
    fun `link response for a different user is rejected without changing the session`() =
        runTest {
            server.enqueue(successfulAuthResponse(idToken = "original-token"))
            auth.signInWithCredential(GoogleAuthProvider.getCredential("google-token", null)).await()
            server.takeRequest()

            server.enqueue(successfulAuthResponse(idToken = "other-token", uid = "other-user"))

            assertThrows(FirebaseAuthInvalidUserException::class.java) {
                kotlinx.coroutines.runBlocking {
                    auth.currentUser!!
                        .linkWithCredential(
                            OAuthProvider.newCredentialBuilder("github.com").setAccessToken("github-token").build()
                        ).await()
                }
            }
            assertEquals("jvm-user", auth.currentUser?.uid)
            assertTrue(platform.values.values.single().contains("original-token"))
            assertFalse(platform.values.values.single().contains("other-token"))
        }

    @Test
    fun `credential sign in notifies auth state and id token listeners`() =
        runTest {
            val authStateChanged = CountDownLatch(1)
            val idTokenChanged = CountDownLatch(1)
            val callbackThreads = mutableListOf<String>()
            val authListener =
                object : FirebaseAuth.AuthStateListener {
                    override fun onAuthStateChanged(auth: FirebaseAuth) {
                        if (auth.currentUser?.uid == "jvm-user") {
                            callbackThreads += Thread.currentThread().name
                            authStateChanged.countDown()
                        }
                    }
                }
            val tokenListener =
                object : FirebaseAuth.IdTokenListener {
                    override fun onIdTokenChanged(auth: FirebaseAuth) {
                        if (auth.currentUser?.uid == "jvm-user") {
                            callbackThreads += Thread.currentThread().name
                            idTokenChanged.countDown()
                        }
                    }
                }
            auth.addAuthStateListener(authListener)
            auth.addIdTokenListener(tokenListener)
            server.enqueue(successfulAuthResponse(idToken = "listener-token"))

            auth.signInWithCredential(GoogleAuthProvider.getCredential("google-token", null)).await()

            assertTrue(authStateChanged.await(5, TimeUnit.SECONDS))
            assertTrue(idTokenChanged.await(5, TimeUnit.SECONDS))
            assertEquals(2, callbackThreads.size)
            assertTrue(callbackThreads.all { it.startsWith("firebase-platform-main") })
            auth.removeAuthStateListener(authListener)
            auth.removeIdTokenListener(tokenListener)
        }

    private fun successfulAuthResponse(
        idToken: String,
        uid: String = "jvm-user",
        providerId: String = "google.com",
        expiresIn: Int = 3600
    ) =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "localId": "$uid",
                  "idToken": "$idToken",
                  "refreshToken": "refresh-token",
                  "expiresIn": "$expiresIn",
                  "createdAt": "1600000000000",
                  "lastLoginAt": "1700000000000",
                  "email": "jvm@example.com",
                  "emailVerified": true,
                  "providerId": "$providerId"
                }
                """.trimIndent()
            )

    private fun phoneResponse(idToken: String) =
        MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"localId":"jvm-user","idToken":"$idToken","refreshToken":"refresh-token","expiresIn":"3600","phoneNumber":"+15555550123"}"""
        )

    private fun refreshResponse(idToken: String) =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "user_id": "jvm-user",
                  "id_token": "$idToken",
                  "refresh_token": "rotated-refresh-token",
                  "expires_in": "3600"
                }
                """.trimIndent()
            )

    private fun firebaseToken(): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"aud\":\"fir-java-sdk\"}".toByteArray())
        return "header.$payload.signature"
    }

    private fun decodeForm(value: String): Map<String, String> =
        value
            .split('&')
            .associate { part ->
                val (key, encodedValue) = part.split('=', limit = 2)
                key to URLDecoder.decode(encodedValue, StandardCharsets.UTF_8.name())
            }

    private class RecordingFirebasePlatform(
        private val storageFile: File? = null
    ) : FirebasePlatform() {
        private val mainExecutor: ExecutorService =
            Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "firebase-platform-main") }

        override val mainDispatcher = mainExecutor.asCoroutineDispatcher()

        val values =
            mutableMapOf<String, String>().apply {
                storageFile
                    ?.takeIf(File::isFile)
                    ?.inputStream()
                    ?.use { input ->
                        Properties().apply { load(input) }.forEach { key, value -> put(key.toString(), value.toString()) }
                    }
            }

        override fun store(
            key: String,
            value: String
        ) {
            values[key] = value
            persist()
        }

        override fun retrieve(key: String): String? = values[key]

        override fun clear(key: String) {
            values.remove(key)
            persist()
        }

        override fun log(msg: String) = Unit

        override fun getDatabasePath(name: String): File = File("./build/$name")

        private fun persist() {
            val file = storageFile ?: return
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                Properties()
                    .apply {
                        this@RecordingFirebasePlatform.values.forEach { (key, value) -> setProperty(key, value) }
                    }.store(output, null)
            }
        }

        fun close() {
            mainDispatcher.close()
            mainExecutor.shutdownNow()
        }
    }
}
