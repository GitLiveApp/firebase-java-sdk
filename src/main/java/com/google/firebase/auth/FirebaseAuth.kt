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
    override val photoUrl: String?,
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
        photoUrl = photoUrl ?: data["photo_url"]?.jsonPrimitive?.contentOrNull,
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

    override val providerData: List<UserInfo>
        get() = providerIds.map { provider -> UserInfo(displayName, email, phoneNumber, photoUrl, provider, uid) }

    override val providerId: String
        get() = "firebase"

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

    // TODO implement ActionCodeSettings and pass it to the url
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
                )
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
                                user = setResult(responseBody)
                                source.setResult(AuthResult { user })
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
                                photoUrl = profile["photoUrl"]?.jsonPrimitive?.contentOrNull ?: previous.photoUrl,
                                displayName = profile["displayName"]?.jsonPrimitive?.contentOrNull ?: previous.displayName
                            )
                            source.setResult(AuthResult { user })
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
                                    photoUrl = user.photoUrl,
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
                                    photoUrl = newBody["photoUrl"]?.jsonPrimitive?.contentOrNull ?: prev.photoUrl,
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
                                    photoUrl = newBody["photoUrl"]?.jsonPrimitive?.contentOrNull ?: prev.photoUrl,
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

    fun sendPasswordResetEmail(email: String, settings: ActionCodeSettings?): Task<Unit> = TODO()
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
                    photoUrl = data["photoUrl"]?.jsonPrimitive?.contentOrNull ?: signedInUser.photoUrl,
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
        if (authCredential is PhoneAuthCredential) {
            enqueuePhoneAuthPost(authCredential, currentIdToken, setResult)
        } else {
            enqueueIdpAuthPost(authCredential, currentIdToken, setResult)
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
    fun checkActionCode(code: String): Task<ActionCodeResult> = TODO()

    fun confirmPasswordReset(
        code: String,
        newPassword: String
    ): Task<Unit> = TODO()

    fun fetchSignInMethodsForEmail(email: String): Task<SignInMethodQueryResult> = TODO()

    fun sendSignInLinkToEmail(
        email: String,
        actionCodeSettings: ActionCodeSettings
    ): Task<Unit> = TODO()

    fun verifyPasswordResetCode(code: String): Task<String> = TODO()

    fun updateCurrentUser(user: FirebaseUser): Task<Unit> = TODO()

    fun applyActionCode(code: String): Task<Unit> = TODO()

    val languageCode: String get() = TODO()

    fun isSignInWithEmailLink(link: String): Boolean = TODO()

    fun signInWithEmailLink(
        email: String,
        link: String
    ): Task<AuthResult> = TODO()

    fun setLanguageCode(value: String): Nothing = TODO()
}
