package com.google.firebase.auth

import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebasePlatform
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.internal.InternalTokenResult
import com.google.firebase.internal.api.FirebaseNoSignedInUserException
import kotlinx.coroutines.launch
import android.app.Activity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

internal val jsonParser = Json { ignoreUnknownKeys = true }

class UrlFactory(
    private val app: FirebaseApp,
    private val emulatorUrl: String? = null
) {
    fun buildUrl(uri: String): String = "${emulatorUrl ?: "https://"}$uri?key=${app.options.apiKey}"
}

@Serializable
data class FirebaseUserImpl internal constructor(
    @Transient
    private val app: FirebaseApp = FirebaseApp.getInstance(),
    override val isAnonymous: Boolean,
    override val uid: String,
    val idToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val createdAt: Long,
    override val email: String?,
    @SerialName("photoUrl")
    val photoUrlString: String?,
    override val displayName: String?,
    override val phoneNumber: String? = null,
    override val isEmailVerified: Boolean = false,
    val lastSignInAt: Long,
    val providerIds: List<String> = emptyList(),
    val tokenIssuedAt: Long,
    @Transient
    private val urlFactory: UrlFactory = UrlFactory(app)
) : FirebaseUser() {
    constructor(
        app: FirebaseApp,
        data: JsonObject,
        isAnonymous: Boolean = data["isAnonymous"]?.jsonPrimitive?.booleanOrNull ?: false,
        email: String? = data.getOrElse("email") { null }?.jsonPrimitive?.contentOrNull,
        photoUrl: String? = data.getOrElse("photoUrl") { null }?.jsonPrimitive?.contentOrNull,
        displayName: String? = data.getOrElse("displayName") { null }?.jsonPrimitive?.contentOrNull,
        phoneNumber: String? = data["phoneNumber"]?.jsonPrimitive?.contentOrNull,
        isEmailVerified: Boolean = (data["isEmailVerified"] ?: data["emailVerified"])?.jsonPrimitive?.booleanOrNull ?: false,
        createdAt: Long = if ("uid" in data && "tokenIssuedAt" !in data) 0 else data["createdAt"]?.jsonPrimitive?.longOrNull ?: 0,
        lastSignInAt: Long = (data["lastSignInAt"] ?: data["lastLoginAt"])?.jsonPrimitive?.longOrNull ?: if ("uid" in data) 0 else System.currentTimeMillis(),
        existingProviderData: List<UserInfo> = emptyList(),
        urlFactory: UrlFactory = UrlFactory(app)
    ) : this(
        app = app,
        isAnonymous = isAnonymous,
        uid =
        responseUid(data, data["idToken"]?.jsonPrimitive?.contentOrNull ?: data.getValue("id_token").jsonPrimitive.content),
        idToken = data["idToken"]?.jsonPrimitive?.contentOrNull ?: data.getValue("id_token").jsonPrimitive.content,
        refreshToken = data["refreshToken"]?.jsonPrimitive?.contentOrNull ?: data.getValue("refresh_token").jsonPrimitive.content,
        expiresIn = data["expiresIn"]?.jsonPrimitive?.intOrNull ?: data.getValue("expires_in").jsonPrimitive.int,
        createdAt = createdAt,
        email = email,
        photoUrlString = photoUrl ?: data["photo_url"]?.jsonPrimitive?.contentOrNull,
        displayName = displayName ?: data["display_name"]?.jsonPrimitive?.contentOrNull,
        phoneNumber = phoneNumber,
        isEmailVerified = isEmailVerified,
        lastSignInAt = lastSignInAt,
        providerIds = resolveProviderIds(data, existingProviderData, email, phoneNumber, isAnonymous),
        // Old persisted users mixed account creation and token time; refresh rather than extend their token lifetime.
        tokenIssuedAt = data["tokenIssuedAt"]?.jsonPrimitive?.longOrNull ?: if ("uid" in data) 0 else System.currentTimeMillis(),
        urlFactory = urlFactory
    )

    override val metadata: FirebaseUserMetadata
        get() = FirebaseUserMetadata(createdAt, lastSignInAt)

    override val photoUrl: Uri?
        get() = photoUrlString?.let(Uri::parse)

    override val providerData: List<UserInfo>
        get() = providerIds.map { provider -> ProviderUserInfo(displayName, email, phoneNumber, photoUrlString, provider, uid, isEmailVerified) }

    override val providerId: String
        get() = FirebaseAuthProvider.PROVIDER_ID

    val claims: Map<String, Any?> by lazy {
        jsonParser
            .parseToJsonElement(String(Base64.getUrlDecoder().decode(idToken.split(".")[1])))
            .jsonObject
            .run { value as? Map<String, Any?>? }
            .orEmpty()
    }

    internal val JsonElement.value get(): Any? =
        when (this) {
            is JsonNull -> null
            is JsonArray -> map { it.value }
            is JsonObject -> jsonObject.mapValues { (_, it) -> it.value }
            is JsonPrimitive -> booleanOrNull ?: doubleOrNull ?: content
            else -> TODO()
        }

    override fun delete(): Task<Void> {
        val source = TaskCompletionSource<Void>()
        val body = JsonObject(mapOf("idToken" to JsonPrimitive(idToken))).toString().toRequestBody(FirebaseAuth.getInstance(app).json)
        val request =
            Request
                .Builder()
                .url(urlFactory.buildUrl("www.googleapis.com/identitytoolkit/v3/relyingparty/deleteAccount"))
                .post(body)
                .build()
        FirebaseAuth.getInstance(app).client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    source.setException(FirebaseException(e.toString(), e))
                }

                @Throws(IOException::class)
                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    if (!response.isSuccessful) {
                        FirebaseAuth.getInstance(app).signOut()
                        source.setException(
                            FirebaseAuth.getInstance(app).createAuthInvalidUserException(
                                "deleteAccount",
                                request,
                                response
                            )
                        )
                    } else {
                        source.setResult(null)
                    }
                }
            }
        )
        return source.task
    }

    override fun updateEmail(email: String): Task<Unit> = FirebaseAuth.getInstance(app).updateEmail(email)

    override fun reload(): Task<Void> {
        val source = TaskCompletionSource<Void>()
        FirebaseAuth.getInstance(app).refreshToken(this, source) { null }
        return source.task
    }

    override fun verifyBeforeUpdateEmail(
        newEmail: String,
        actionCodeSettings: ActionCodeSettings?
    ): Task<Unit> {
        val source = TaskCompletionSource<Unit>()
        val body =
            JsonObject(
                mapOf(
                    "idToken" to JsonPrimitive(idToken),
                    "email" to JsonPrimitive(email),
                    "newEmail" to JsonPrimitive(newEmail),
                    "requestType" to JsonPrimitive(OobRequestType.VERIFY_AND_CHANGE_EMAIL.name)
                ) + actionCodeSettings?.toRequestFields().orEmpty()
            ).toString().toRequestBody(FirebaseAuth.getInstance(app).json)
        val request =
            Request
                .Builder()
                .url(urlFactory.buildUrl("identitytoolkit.googleapis.com/v1/accounts:sendOobCode"))
                .post(body)
                .build()
        FirebaseAuth.getInstance(app).client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    source.setException(FirebaseException(e.toString(), e))
                    e.printStackTrace()
                }

                @Throws(IOException::class)
                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    if (!response.isSuccessful) {
                        FirebaseAuth.getInstance(app).signOut()
                        source.setException(
                            FirebaseAuth.getInstance(app).createAuthInvalidUserException(
                                "verifyEmail",
                                request,
                                response
                            )
                        )
                    } else {
                        source.setResult(null)
                    }
                }
            }
        )
        return source.task
    }

    override fun getIdToken(forceRefresh: Boolean) = FirebaseAuth.getInstance(app).getAccessToken(forceRefresh)

    override fun updateProfile(request: UserProfileChangeRequest): Task<Unit> = FirebaseAuth.getInstance(app).updateProfile(request)

    override fun linkWithCredential(credential: AuthCredential): Task<AuthResult> =
        FirebaseAuth.getInstance(app).linkWithCredential(this, credential)

    override fun sendEmailVerification(actionCodeSettings: ActionCodeSettings?): Task<Unit> =
        FirebaseAuth.getInstance(app).sendOobCode(
            "sendEmailVerification",
            OobRequestType.VERIFY_EMAIL,
            mapOf("idToken" to JsonPrimitive(idToken)),
            actionCodeSettings
        )

    override fun updatePhoneNumber(credential: PhoneAuthCredential): Task<Unit> =
        FirebaseAuth.getInstance(app).updatePhoneNumber(this, credential)

    fun updateProfile(
        displayName: String?,
        photoUrl: String?
    ): Task<Unit> {
        val request =
            UserProfileChangeRequest
                .Builder()
                .apply { setDisplayName(displayName) }
                .apply { setPhotoUri(photoUrl?.let { Uri.parse(it) }) }
                .build()
        return FirebaseAuth.getInstance(app).updateProfile(request)
    }
}

private fun resolveProviderIds(
    data: JsonObject,
    existingProviderData: List<UserInfo>,
    email: String?,
    phoneNumber: String?,
    isAnonymous: Boolean
): List<String> {
    val stored =
        data["providerIds"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
    val nested =
        data["providerUserInfo"]
            ?.jsonArray
            ?.mapNotNull { it.jsonObject["providerId"]?.jsonPrimitive?.contentOrNull }
            .orEmpty()
    val direct = listOfNotNull(data["providerId"]?.jsonPrimitive?.contentOrNull)
    val explicit = existingProviderData.map { it.providerId } + stored + nested + direct
    val inferred =
        if (explicit.isEmpty()) {
            when {
                isAnonymous -> emptyList()
                phoneNumber != null -> listOf("phone")
                email != null -> listOf("password")
                else -> emptyList()
            }
        } else {
            emptyList()
        }
    val phoneProvider = if (!isAnonymous && phoneNumber != null) listOf("phone") else emptyList()
    return (explicit + inferred + phoneProvider).distinct()
}

class FirebaseAuth constructor(
    val app: FirebaseApp
) : InternalAuthProvider {
    internal val json = "application/json; charset=utf-8".toMediaType()
    internal val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    private fun enqueueAuthPost(
        url: String,
        body: RequestBody,
        setResult: (responseBody: String) -> FirebaseUserImpl?
    ): TaskCompletionSource<AuthResult> {
        val source = TaskCompletionSource<AuthResult>()
        val request = Request.Builder()
            .url(urlFactory.buildUrl(url))
            .post(body)
            .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    source.setException(FirebaseException(e.toString(), e))
                }

                @Throws(IOException::class)
                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    try {
                        if (!response.isSuccessful) {
                            source.setException(
                                createAuthInvalidUserException("accounts", request, response)
                            )
                        } else {
                            val responseBody = response.body?.use { it.string() }
                            if (responseBody == null) {
                                source.setException(
                                    FirebaseAuthInvalidUserException("UNKNOWN_ERROR", "accounts API returned an empty response.")
                                )
                            } else {
                                val data = jsonParser.parseToJsonElement(responseBody).jsonObject
                                user = setResult(responseBody)
                                source.setResult(
                                    AuthResultImpl(
                                        user,
                                        AdditionalUserInfoImpl.from(data, newUserByDefault = url.endsWith("signUp") || url.endsWith("signupNewUser")),
                                        AdditionalUserInfoImpl.credentialOf(data)
                                    )
                                )
                            }
                        }
                    } catch (exception: Exception) {
                        source.setException(exception)
                    }
                }
            }
        )
        return source
    }

    companion object {
        @JvmStatic
        fun getInstance(): FirebaseAuth = getInstance(FirebaseApp.getInstance())

        @JvmStatic
        fun getInstance(app: FirebaseApp): FirebaseAuth = app.get(FirebaseAuth::class.java)

        private const val REFRESH_TOKEN_TAG = "refresh_token_tag"
    }

    private val internalIdTokenListeners = CopyOnWriteArrayList<com.google.firebase.auth.internal.IdTokenListener>()
    private val idTokenListeners = CopyOnWriteArrayList<IdTokenListener>()
    private val authStateListeners = CopyOnWriteArrayList<AuthStateListener>()

    val currentUser: FirebaseUser?
        get() = user

    val FirebaseApp.key get() = "com.google.firebase.auth.FIREBASE_USER${"[$name]".takeUnless { isDefaultApp }.orEmpty()}"

    private var user: FirebaseUserImpl? =
        FirebasePlatform.firebasePlatform
            .runCatching { retrieve(app.key)?.let { FirebaseUserImpl(app, data = jsonParser.parseToJsonElement(it).jsonObject) } }
            .onFailure { it.printStackTrace() }
            .getOrNull()

        private set(value) {
            if (field != value) {
                val prev = field
                field = value

                if (value == null) {
                    FirebasePlatform.firebasePlatform.clear(app.key)
                } else {
                    FirebasePlatform.firebasePlatform.store(app.key, jsonParser.encodeToString(FirebaseUserImpl.serializer(), value))
                }

                FirebasePlatform.firebasePlatform.mainScope.launch {
                    if (prev?.uid != value?.uid) {
                        authStateListeners.forEach { l -> l.onAuthStateChanged(this@FirebaseAuth) }
                    }

                    if (prev?.idToken != value?.idToken) {
                        val result = InternalTokenResult(value?.idToken)
                        for (listener in internalIdTokenListeners) {
                            Log.i("FirebaseAuth", "Calling onIdTokenChanged for ${value?.uid} on listener $listener")
                            listener.onIdTokenChanged(result)
                        }
                        for (listener in idTokenListeners) {
                            listener.onIdTokenChanged(this@FirebaseAuth)
                        }
                    }
                }
            }
        }

    private var urlFactory = UrlFactory(app)

    fun signInAnonymously(): Task<AuthResult> {
        val source = enqueueAuthPost(
            url = "identitytoolkit.googleapis.com/v1/accounts:signUp",
            body = JsonObject(mapOf("returnSecureToken" to JsonPrimitive(true))).toString().toRequestBody(json),
            setResult = { responseBody ->
                FirebaseUserImpl(app, jsonParser.parseToJsonElement(responseBody).jsonObject, isAnonymous = true, urlFactory = urlFactory)
            }
        )
        return source.task
    }

    fun signInWithCustomToken(customToken: String): Task<AuthResult> {
        val source = enqueueAuthPost(
            url = "www.googleapis.com/identitytoolkit/v3/relyingparty/verifyCustomToken",
            body = JsonObject(mapOf("token" to JsonPrimitive(customToken), "returnSecureToken" to JsonPrimitive(true))).toString().toRequestBody(json),
            setResult = { responseBody ->
                FirebaseUserImpl(app, jsonParser.parseToJsonElement(responseBody).jsonObject, urlFactory = urlFactory)
            }
        )
        return source.task.onSuccessTask { updateByGetAccountInfo() }
    }

    private fun updateByGetAccountInfo(): Task<AuthResult> {
        val source = TaskCompletionSource<AuthResult>()

        val body =
            JsonObject(mapOf("idToken" to JsonPrimitive(user?.idToken))).toString().toRequestBody(json)
        val request =
            Request
                .Builder()
                .url(urlFactory.buildUrl("www.googleapis.com/identitytoolkit/v3/relyingparty/getAccountInfo"))
                .post(body)
                .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    source.setException(FirebaseException(e.toString(), e))
                }

                @Throws(IOException::class)
                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    if (!response.isSuccessful) {
                        source.setException(
                            createAuthInvalidUserException("updateWithAccountInfo", request, response)
                        )
                    } else {
                        try {
                            val data = jsonParser.parseToJsonElement(response.body?.use { it.string() } ?: "").jsonObject
                            val profile = accountProfile(data)
                            val previous = requireNotNull(user) { "No user for account lookup" }
                            user = previous.copy(
                                uid = responseUid(data, previous.idToken),
                                createdAt = profile["createdAt"]?.jsonPrimitive?.longOrNull ?: previous.createdAt,
                                email = profile["email"]?.jsonPrimitive?.contentOrNull ?: previous.email,
                                photoUrlString = profile["photoUrl"]?.jsonPrimitive?.contentOrNull ?: previous.photoUrlString,
                                displayName = profile["displayName"]?.jsonPrimitive?.contentOrNull ?: previous.displayName
                            )
                            source.setResult(AuthResultImpl(user, null, null))
                        } catch (exception: Exception) {
                            source.setException(exception)
                        }
                    }
                }
            }
        )
        return source.task
    }

    fun createUserWithEmailAndPassword(
        email: String,
        password: String
    ): Task<AuthResult> {
        val source =
            enqueueAuthPost(
                url = "www.googleapis.com/identitytoolkit/v3/relyingparty/signupNewUser",
                body =
                JsonObject(
                    mapOf(
                        "email" to JsonPrimitive(email),
                        "password" to JsonPrimitive(password),
                        "returnSecureToken" to JsonPrimitive(true)
                    )
                ).toString().toRequestBody(json),
                setResult = { responseBody ->
                    FirebaseUserImpl(
                        app = app,
                        data = jsonParser.parseToJsonElement(responseBody).jsonObject,
                        urlFactory = urlFactory
                    )
                }
            )
        return source.task
    }

    fun signInWithEmailAndPassword(
        email: String,
        password: String
    ): Task<AuthResult> {
        val source =
            enqueueAuthPost(
                url = "www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword",
                body =
                JsonObject(
                    mapOf(
                        "email" to JsonPrimitive(email),
                        "password" to JsonPrimitive(password),
                        "returnSecureToken" to JsonPrimitive(true)
                    )
                ).toString().toRequestBody(json),
                setResult = { responseBody ->
                    FirebaseUserImpl(app, jsonParser.parseToJsonElement(responseBody).jsonObject, urlFactory = urlFactory)
                }
            )
        return source.task
    }

    internal fun createAuthInvalidUserException(
        action: String,
        request: Request,
        response: Response
    ): FirebaseAuthInvalidUserException {
        val body = response.body?.use { it.string() }.orEmpty()
        val errorCode =
            runCatching {
                jsonParser
                    .parseToJsonElement(body)
                    .jsonObject["error"]
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.getOrNull() ?: "UNKNOWN_ERROR"

        return FirebaseAuthInvalidUserException(
            errorCode,
            "$errorCode: $action API returned an error, " +
                "with endpoint [${request.method}] ${request.url.encodedPath} -- " +
                "response [${response.code}] ${response.message}"
        )
    }

    fun signOut() {
        // cancel token refresher
        client
            .dispatcher
            .queuedCalls()
            .find { it.request().tag() == REFRESH_TOKEN_TAG }
            ?.cancel() ?: {
            client
                .dispatcher
                .runningCalls()
                .find { it.request().tag() == REFRESH_TOKEN_TAG }
                ?.cancel()
        }
        user = null
    }

    override fun getAccessToken(forceRefresh: Boolean): Task<GetTokenResult> {
        val user = user ?: return Tasks.forException(FirebaseNoSignedInUserException("Please sign in before trying to get a token."))

        if (!forceRefresh && user.tokenIssuedAt + user.expiresIn * 1000L - 5 * 60 * 1000 > System.currentTimeMillis()) {
//            Log.i("FirebaseAuth", "returning existing token for user ${user.uid} from getAccessToken")
            return Tasks.forResult(GetTokenResult(user.idToken, user.claims))
        }
//        Log.i("FirebaseAuth", "Refreshing access token forceRefresh=$forceRefresh tokenIssuedAt=${user.tokenIssuedAt} expiresIn=${user.expiresIn}")
        val source = TaskCompletionSource<GetTokenResult>()
        refreshToken(user, source) { GetTokenResult(it.idToken, it.claims) }
        return source.task
    }

    private var refreshSource = TaskCompletionSource<FirebaseUserImpl>().apply { setException(Exception()) }

    internal fun <T> refreshToken(
        user: FirebaseUserImpl,
        source: TaskCompletionSource<T>,
        map: (user: FirebaseUserImpl) -> T?
    ) {
        refreshSource = refreshSource
            .takeUnless { it.task.isComplete }
            ?: enqueueRefreshTokenCall(user)
        refreshSource.task.addOnSuccessListener { source.setResult(map(it)) }
        refreshSource.task.addOnFailureListener { source.setException(FirebaseNetworkException(it.toString())) }
    }

    private fun enqueueRefreshTokenCall(user: FirebaseUserImpl): TaskCompletionSource<FirebaseUserImpl> {
        val source = TaskCompletionSource<FirebaseUserImpl>()
        val body =
            JsonObject(
                mapOf(
                    "refresh_token" to JsonPrimitive(user.refreshToken),
                    "grant_type" to JsonPrimitive("refresh_token")
                )
            ).toString().toRequestBody(json)
        val request =
            Request
                .Builder()
                .url(urlFactory.buildUrl("securetoken.googleapis.com/v1/token"))
                .post(body)
                .tag(REFRESH_TOKEN_TAG)
                .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    source.setException(e)
                }

                @Throws(IOException::class)
                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    val responseBody = response.body?.use { it.string() }

                    if (!response.isSuccessful || responseBody == null) {
                        signOutAndThrowInvalidUserException(
                            responseBody.orEmpty(),
                            "token API returned an error"
                        )
                    } else {
                        jsonParser.parseToJsonElement(responseBody).jsonObject.apply {
                            val newUser =
                                FirebaseUserImpl(
                                    app = app,
                                    data = this,
                                    isAnonymous = user.isAnonymous,
                                    email = user.email,
                                    photoUrl = user.photoUrlString,
                                    displayName = user.displayName,
                                    phoneNumber = user.phoneNumber,
                                    isEmailVerified = user.isEmailVerified,
                                    createdAt = user.createdAt,
                                    lastSignInAt = user.lastSignInAt,
                                    existingProviderData = user.providerData,
                                    urlFactory = urlFactory
                                )
                            if (newUser.claims["aud"] != app.options.projectId) {
                                signOutAndThrowInvalidUserException(
                                    newUser.claims.toString(),
                                    "Project ID's do not match ${newUser.claims["aud"]} != ${app.options.projectId}"
                                )
                            } else {
                                this@FirebaseAuth.user = newUser
                                source.setResult(newUser)
                            }
                        }
                    }
                }

                private fun signOutAndThrowInvalidUserException(
                    body: String,
                    message: String
                ) {
                    signOut()
                    source.setException(FirebaseAuthInvalidUserException(body, message))
                }
            }
        )
        return source
    }

    internal fun updateEmail(email: String): Task<Unit> {
        val source = TaskCompletionSource<Unit>()

        val body =
            JsonObject(
                mapOf(
                    "idToken" to JsonPrimitive(user?.idToken),
                    "email" to JsonPrimitive(email),
                    "returnSecureToken" to JsonPrimitive(true)
                )
            ).toString().toRequestBody(json)
        val request =
            Request
                .Builder()
                .url(urlFactory.buildUrl("identitytoolkit.googleapis.com/v1/accounts:update"))
                .post(body)
                .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    source.setException(FirebaseException(e.toString(), e))
                }

                @Throws(IOException::class)
                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    if (!response.isSuccessful) {
                        signOut()
                        source.setException(
                            createAuthInvalidUserException(
                                "updateEmail",
                                request,
                                response
                            )
                        )
                    } else {
                        val newBody =
                            jsonParser
                                .parseToJsonElement(
                                    response.body?.use { it.string() } ?: ""
                                ).jsonObject

                        user?.let { prev ->
                            user =
                                FirebaseUserImpl(
                                    app = app,
                                    isAnonymous = prev.isAnonymous,
                                    uid = prev.uid,
                                    idToken = newBody["idToken"]?.jsonPrimitive?.contentOrNull ?: prev.idToken,
                                    refreshToken = newBody["refreshToken"]?.jsonPrimitive?.contentOrNull ?: prev.refreshToken,
                                    expiresIn = newBody["expiresIn"]?.jsonPrimitive?.intOrNull ?: prev.expiresIn,
                                    createdAt = prev.createdAt,
                                    email = newBody["newEmail"]?.jsonPrimitive?.contentOrNull ?: prev.email,
                                    photoUrlString = newBody["photoUrl"]?.jsonPrimitive?.contentOrNull ?: prev.photoUrlString,
                                    displayName = newBody["displayName"]?.jsonPrimitive?.contentOrNull ?: prev.displayName,
                                    phoneNumber = prev.phoneNumber,
                                    isEmailVerified = prev.isEmailVerified,
                                    lastSignInAt = prev.lastSignInAt,
                                    providerIds = prev.providerIds,
                                    tokenIssuedAt = if (newBody["idToken"]?.jsonPrimitive?.contentOrNull != null) System.currentTimeMillis() else prev.tokenIssuedAt,
                                    urlFactory = urlFactory
                                )
                        }
                        source.setResult(null)
                    }
                }
            }
        )
        return source.task
    }

    internal fun updateProfile(request: UserProfileChangeRequest): Task<Unit> {
        val source = TaskCompletionSource<Unit>()

        val body =
            JsonObject(
                mapOf(
                    "idToken" to JsonPrimitive(user?.idToken),
                    "displayName" to JsonPrimitive(request.displayName),
                    "photoUrl" to JsonPrimitive(request.photoUrl),
                    "returnSecureToken" to JsonPrimitive(true)
                )
            ).toString().toRequestBody(json)
        val req =
            Request
                .Builder()
                .url(urlFactory.buildUrl("identitytoolkit.googleapis.com/v1/accounts:update"))
                .post(body)
                .build()

        client.newCall(req).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    source.setException(FirebaseException(e.toString(), e))
                }

                @Throws(IOException::class)
                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    if (!response.isSuccessful) {
                        signOut()
                        source.setException(
                            createAuthInvalidUserException(
                                "updateProfile",
                                req,
                                response
                            )
                        )
                    } else {
                        val newBody =
                            jsonParser
                                .parseToJsonElement(
                                    response.body?.use { it.string() } ?: ""
                                ).jsonObject

                        user?.let { prev ->
                            user =
                                FirebaseUserImpl(
                                    app = app,
                                    isAnonymous = prev.isAnonymous,
                                    uid = prev.uid,
                                    idToken = newBody["idToken"]?.jsonPrimitive?.contentOrNull ?: prev.idToken,
                                    refreshToken = newBody["refreshToken"]?.jsonPrimitive?.contentOrNull ?: prev.refreshToken,
                                    expiresIn = newBody["expiresIn"]?.jsonPrimitive?.intOrNull ?: prev.expiresIn,
                                    createdAt = prev.createdAt,
                                    email = newBody["newEmail"]?.jsonPrimitive?.contentOrNull ?: prev.email,
                                    photoUrlString = newBody["photoUrl"]?.jsonPrimitive?.contentOrNull ?: prev.photoUrlString,
                                    displayName = newBody["displayName"]?.jsonPrimitive?.contentOrNull ?: prev.displayName,
                                    phoneNumber = prev.phoneNumber,
                                    isEmailVerified = prev.isEmailVerified,
                                    lastSignInAt = prev.lastSignInAt,
                                    providerIds = prev.providerIds,
                                    tokenIssuedAt = if (newBody["idToken"]?.jsonPrimitive?.contentOrNull != null) System.currentTimeMillis() else prev.tokenIssuedAt,
                                    urlFactory = urlFactory
                                )
                        }
                        source.setResult(null)
                    }
                }
            }
        )
        return source.task
    }

    override fun getUid(): String? = user?.uid

    override fun addIdTokenListener(listener: com.google.firebase.auth.internal.IdTokenListener) {
        internalIdTokenListeners.addIfAbsent(listener)
        FirebasePlatform.firebasePlatform.mainScope.launch {
            listener.onIdTokenChanged(InternalTokenResult(user?.idToken))
        }
    }

    override fun removeIdTokenListener(listener: com.google.firebase.auth.internal.IdTokenListener) {
        internalIdTokenListeners.remove(listener)
    }

    @Synchronized
    fun addAuthStateListener(listener: AuthStateListener) {
        authStateListeners.addIfAbsent(listener)
        FirebasePlatform.firebasePlatform.mainScope.launch {
            listener.onAuthStateChanged(this@FirebaseAuth)
        }
    }

    @Synchronized
    fun removeAuthStateListener(listener: AuthStateListener) {
        authStateListeners.remove(listener)
    }

    @FunctionalInterface
    interface AuthStateListener {
        fun onAuthStateChanged(auth: FirebaseAuth)
    }

    @FunctionalInterface
    interface IdTokenListener {
        fun onIdTokenChanged(auth: FirebaseAuth)
    }

    fun addIdTokenListener(listener: IdTokenListener) {
        idTokenListeners.addIfAbsent(listener)
        FirebasePlatform.firebasePlatform.mainScope.launch {
            listener.onIdTokenChanged(this@FirebaseAuth)
        }
    }

    fun removeIdTokenListener(listener: IdTokenListener) {
        idTokenListeners.remove(listener)
    }

    fun useEmulator(host: String, port: Int) {
        urlFactory = UrlFactory(app, "http://$host:$port/")
        user = user?.copy(urlFactory = urlFactory)
    }

    fun sendPasswordResetEmail(email: String): Task<Unit> = sendPasswordResetEmail(email, null)

    fun sendPasswordResetEmail(email: String, settings: ActionCodeSettings?): Task<Unit> =
        sendOobCode("sendPasswordResetEmail", OobRequestType.PASSWORD_RESET, mapOf("email" to JsonPrimitive(email)), settings)

    /** Posts [fields] with [type] to `accounts:sendOobCode`, the request behind every action email. */
    internal fun sendOobCode(
        action: String,
        type: OobRequestType,
        fields: Map<String, JsonElement>,
        settings: ActionCodeSettings?
    ): Task<Unit> =
        postJson(
            action,
            "identitytoolkit.googleapis.com/v1/accounts:sendOobCode",
            JsonObject(mapOf("requestType" to JsonPrimitive(type.name)) + fields + settings?.toRequestFields().orEmpty())
        ).onSuccessTask { Tasks.forResult(Unit) }

    /**
     * Posts [body] to [url] and completes with the parsed response, or with the error [createAuthInvalidUserException]
     * reads from an unsuccessful one; the language code, when set, travels as the `X-Firebase-Locale` header.
     */
    private fun postJson(
        action: String,
        url: String,
        body: JsonObject
    ): Task<JsonObject> {
        val source = TaskCompletionSource<JsonObject>()
        val request =
            Request
                .Builder()
                .url(urlFactory.buildUrl(url))
                .post(body.toString().toRequestBody(json))
                .apply { languageCode?.let { header("X-Firebase-Locale", it) } }
                .build()
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    source.setException(FirebaseException(e.toString(), e))
                }

                @Throws(IOException::class)
                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    if (!response.isSuccessful) {
                        source.setException(createAuthInvalidUserException(action, request, response))
                    } else {
                        try {
                            val responseBody = response.body?.use { it.string() }.orEmpty().ifBlank { "{}" }
                            source.setResult(jsonParser.parseToJsonElement(responseBody).jsonObject)
                        } catch (exception: Exception) {
                            source.setException(exception)
                        }
                    }
                }
            }
        )
        return source.task
    }

    private fun enqueueEmailAuthPost(
        credential: EmailAuthCredential,
        currentIdToken: String?,
        setResult: (responseBody: String) -> FirebaseUserImpl
    ): Task<AuthResult> {
        val link = credential.emailLink
        val values = linkedMapOf<String, JsonElement>("email" to JsonPrimitive(credential.email))
        val url =
            if (link != null) {
                val code = ActionCodeUrl.parseLink(link)?.code
                    ?: return Tasks.forException(IllegalArgumentException("The email link carries no action code"))
                values["oobCode"] = JsonPrimitive(code)
                "identitytoolkit.googleapis.com/v1/accounts:signInWithEmailLink"
            } else {
                values["password"] = JsonPrimitive(credential.password)
                values["returnSecureToken"] = JsonPrimitive(true)
                // Linking a password sets it on the signed-in account; signing in verifies it.
                if (currentIdToken != null) "identitytoolkit.googleapis.com/v1/accounts:update" else "www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword"
            }
        if (currentIdToken != null) {
            values["idToken"] = JsonPrimitive(currentIdToken)
        }
        return enqueueAuthPost(url, JsonObject(values).toString().toRequestBody(json), setResult).task
    }

    /** Links the phone number [credential] verifies to [currentUser], as the Android `updatePhoneNumber` does. */
    internal fun updatePhoneNumber(
        currentUser: FirebaseUserImpl,
        credential: PhoneAuthCredential
    ): Task<Unit> = linkWithCredential(currentUser, credential).onSuccessTask { Tasks.forResult(Unit) }
    fun signInWithCredential(authCredential: AuthCredential): Task<AuthResult> =
        enqueueCredentialAuthPost(authCredential, currentIdToken = null) { responseBody ->
            FirebaseUserImpl(
                app = app,
                data = jsonParser.parseToJsonElement(responseBody).jsonObject,
                urlFactory = urlFactory
            )
        }

    internal fun linkWithCredential(
        currentUser: FirebaseUserImpl,
        authCredential: AuthCredential
    ): Task<AuthResult> {
        val signedInUser = user
        if (signedInUser == null || signedInUser.uid != currentUser.uid) {
            return Tasks.forException(
                FirebaseAuthInvalidUserException(
                    "ERROR_USER_MISMATCH",
                    "The supplied user is not the currently signed-in user."
                )
            )
        }

        return enqueueCredentialAuthPost(authCredential, currentIdToken = signedInUser.idToken) { responseBody ->
            val data = jsonParser.parseToJsonElement(responseBody).jsonObject
            val linkedUser =
                FirebaseUserImpl(
                    app = app,
                    data = data,
                    isAnonymous = false,
                    email = data["email"]?.jsonPrimitive?.contentOrNull ?: signedInUser.email,
                    photoUrl = data["photoUrl"]?.jsonPrimitive?.contentOrNull ?: signedInUser.photoUrlString,
                    displayName = data["displayName"]?.jsonPrimitive?.contentOrNull ?: signedInUser.displayName,
                    phoneNumber = data["phoneNumber"]?.jsonPrimitive?.contentOrNull ?: signedInUser.phoneNumber,
                    isEmailVerified = data["emailVerified"]?.jsonPrimitive?.booleanOrNull ?: signedInUser.isEmailVerified,
                    createdAt = signedInUser.createdAt,
                    lastSignInAt = signedInUser.lastSignInAt,
                    existingProviderData = signedInUser.providerData,
                    urlFactory = urlFactory
                )
            if (linkedUser.uid != signedInUser.uid) {
                throw FirebaseAuthInvalidUserException(
                    "ERROR_USER_MISMATCH",
                    "The linked credential returned a different Firebase user."
                )
            }
            linkedUser
        }
    }

    private fun enqueueCredentialAuthPost(
        authCredential: AuthCredential,
        currentIdToken: String?,
        setResult: (responseBody: String) -> FirebaseUserImpl
    ): Task<AuthResult> =
        when (authCredential) {
            is PhoneAuthCredential -> enqueuePhoneAuthPost(authCredential, currentIdToken, setResult)
            is EmailAuthCredential -> enqueueEmailAuthPost(authCredential, currentIdToken, setResult)
            else -> enqueueIdpAuthPost(authCredential, currentIdToken, setResult)
        }

    private fun enqueueIdpAuthPost(
        authCredential: AuthCredential,
        currentIdToken: String?,
        setResult: (responseBody: String) -> FirebaseUserImpl
    ): Task<AuthResult> {
        val values =
            linkedMapOf<String, JsonElement>(
                "postBody" to JsonPrimitive(authCredential.toIdpPostBody()),
                "requestUri" to JsonPrimitive("http://localhost"),
                "returnIdpCredential" to JsonPrimitive(true),
                "returnSecureToken" to JsonPrimitive(true)
            )
        if (currentIdToken != null) {
            values["idToken"] = JsonPrimitive(currentIdToken)
        }
        return enqueueAuthPost(
            url = "identitytoolkit.googleapis.com/v1/accounts:signInWithIdp",
            body = JsonObject(values).toString().toRequestBody(json),
            setResult = setResult
        ).task
    }

    private fun enqueuePhoneAuthPost(
        credential: PhoneAuthCredential,
        currentIdToken: String?,
        setResult: (responseBody: String) -> FirebaseUserImpl
    ): Task<AuthResult> {
        val values =
            linkedMapOf<String, JsonElement>(
                "sessionInfo" to JsonPrimitive(credential.verificationId),
                "code" to JsonPrimitive(credential.smsCode)
            )
        if (currentIdToken != null) {
            values["idToken"] = JsonPrimitive(currentIdToken)
        }
        return enqueueAuthPost(
            url = "identitytoolkit.googleapis.com/v1/accounts:signInWithPhoneNumber",
            body = JsonObject(values).toString().toRequestBody(json),
            setResult = setResult
        ).task
    }
    /** What an action code is for and which account it concerns, from `accounts:resetPassword` called with the code alone. */
    fun checkActionCode(code: String): Task<ActionCodeResult> =
        postJson("checkActionCode", "identitytoolkit.googleapis.com/v1/accounts:resetPassword", JsonObject(mapOf("oobCode" to JsonPrimitive(code))))
            .onSuccessTask { data -> Tasks.forResult<ActionCodeResult>(ActionCodeResultImpl(requireNotNull(data))) }

    fun confirmPasswordReset(
        code: String,
        newPassword: String
    ): Task<Unit> =
        postJson(
            "confirmPasswordReset",
            "identitytoolkit.googleapis.com/v1/accounts:resetPassword",
            JsonObject(mapOf("oobCode" to JsonPrimitive(code), "newPassword" to JsonPrimitive(newPassword)))
        ).onSuccessTask { Tasks.forResult(Unit) }

    /** The email a password reset code was issued for, which also checks that the code is valid. */
    fun verifyPasswordResetCode(code: String): Task<String> =
        postJson("verifyPasswordResetCode", "identitytoolkit.googleapis.com/v1/accounts:resetPassword", JsonObject(mapOf("oobCode" to JsonPrimitive(code))))
            .onSuccessTask { data -> Tasks.forResult(requireNotNull(data)["email"]?.jsonPrimitive?.contentOrNull ?: throw FirebaseAuthActionCodeException("ERROR_INVALID_ACTION_CODE", "The action code has no email")) }

    /** Applies a verification, email recovery or second-factor reversal code. */
    fun applyActionCode(code: String): Task<Unit> =
        postJson("applyActionCode", "identitytoolkit.googleapis.com/v1/accounts:update", JsonObject(mapOf("oobCode" to JsonPrimitive(code))))
            .onSuccessTask { Tasks.forResult(Unit) }

    @Deprecated("Email enumeration protection hides the sign-in methods of other accounts; Firebase recommends against this call")
    fun fetchSignInMethodsForEmail(email: String): Task<SignInMethodQueryResult> =
        postJson(
            "fetchSignInMethodsForEmail",
            "identitytoolkit.googleapis.com/v1/accounts:createAuthUri",
            JsonObject(mapOf("identifier" to JsonPrimitive(email), "continueUri" to JsonPrimitive("http://localhost")))
        ).onSuccessTask { data ->
            val methods = requireNotNull(data)["signinMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            Tasks.forResult<SignInMethodQueryResult>(SignInMethodQueryResultImpl(methods))
        }

    /** Emails [email] a sign-in link leading to [actionCodeSettings]' URL, which must be handled in the app. */
    fun sendSignInLinkToEmail(
        email: String,
        actionCodeSettings: ActionCodeSettings
    ): Task<Unit> {
        if (!actionCodeSettings.canHandleCodeInApp()) {
            return Tasks.forException(IllegalArgumentException("You must set canHandleCodeInApp in your ActionCodeSettings to true for Email-Link Sign-in."))
        }
        return sendOobCode("sendSignInLinkToEmail", OobRequestType.EMAIL_SIGNIN, mapOf("email" to JsonPrimitive(email)), actionCodeSettings)
    }

    fun isSignInWithEmailLink(link: String): Boolean = ActionCodeUrl.parseLink(link)?.operation == ActionCodeResult.SIGN_IN_WITH_EMAIL_LINK

    fun signInWithEmailLink(
        email: String,
        link: String
    ): Task<AuthResult> =
        signInWithCredential(EmailAuthProvider.getCredentialWithLink(email, link))

    /** Makes [user], obtained from this port's own sign-in, the current user. */
    fun updateCurrentUser(user: FirebaseUser): Task<Unit> {
        val restored = user as? FirebaseUserImpl
            ?: return Tasks.forException(IllegalArgumentException("The user must come from a firebase-java-sdk sign-in"))
        this.user = restored.copy(app = app, urlFactory = urlFactory)
        return Tasks.forResult(Unit)
    }

    /** The language of the emails this instance sends, as the `X-Firebase-Locale` header; null for the project default. */
    var languageCode: String? = null

    /** Sets [languageCode] to the JVM's default locale. */
    fun useAppLanguage() {
        languageCode = Locale.getDefault().toLanguageTag()
    }

    /** The tenant to sign in to; the JVM port always uses the project's default tenant. */
    var tenantId: String? = null

    /** The domain hosting the project's sign-in helpers; the JVM port runs no browser flows, so it is only kept. */
    var customAuthDomain: String? = null

    val firebaseAuthSettings: FirebaseAuthSettings = FirebaseAuthSettingsImpl()

    /** The result of a provider sign-in started through an Activity; there is never one on the JVM. */
    val pendingAuthResult: Task<AuthResult>? get() = null

    /** reCAPTCHA Enterprise protects phone and email flows on Android; the JVM port has nothing to initialise. */
    fun initializeRecaptchaConfig(): Task<Void> = Tasks.forResult(null)

    /** Revokes an Apple sign-in [token] for the current user, as the Android SDK does before an account deletion. */
    fun revokeAccessToken(token: String): Task<Void> {
        val idToken = user?.idToken
            ?: return Tasks.forException(FirebaseNoSignedInUserException("Please sign in before trying to revoke an access token."))
        return postJson(
            "revokeAccessToken",
            "identitytoolkit.googleapis.com/v2/accounts:revokeToken",
            JsonObject(
                mapOf(
                    "providerId" to JsonPrimitive("apple.com"),
                    "tokenType" to JsonPrimitive("ACCESS_TOKEN"),
                    "token" to JsonPrimitive(token),
                    "idToken" to JsonPrimitive(idToken)
                )
            )
        ).onSuccessTask { Tasks.forResult(null) }
    }

    fun startActivityForSignInWithProvider(
        activity: Activity,
        federatedAuthProvider: FederatedAuthProvider
    ): Task<AuthResult> = unsupportedTask(ACTIVITY_FLOWS_UNSUPPORTED)
}
