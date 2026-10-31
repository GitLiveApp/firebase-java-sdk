package com.google.firebase.auth

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal data class AuthResultImpl(
    private val user: FirebaseUser?,
    private val additionalUserInfo: AdditionalUserInfo?,
    private val credential: AuthCredential?
) : AuthResult {
    override fun getUser(): FirebaseUser? = user

    override fun getAdditionalUserInfo(): AdditionalUserInfo? = additionalUserInfo

    override fun getCredential(): AuthCredential? = credential
}

/** What a sign-in response says about the account beyond the user: provider, raw profile and whether it was created. */
internal class AdditionalUserInfoImpl(
    private val providerId: String?,
    private val profile: Map<String, Any?>,
    private val username: String?,
    private val newUser: Boolean
) : AdditionalUserInfo {
    override fun getProviderId(): String? = providerId

    override fun getProfile(): Map<String, Any?> = profile

    override fun getUsername(): String? = username

    override fun isNewUser(): Boolean = newUser

    companion object {
        fun from(data: JsonObject, newUserByDefault: Boolean): AdditionalUserInfoImpl {
            val profile =
                data["rawUserInfo"]?.jsonPrimitive?.contentOrNull
                    ?.let { raw -> runCatching { jsonParser.parseToJsonElement(raw).jsonObject }.getOrNull() }
                    ?.let { json -> json.mapValues { (_, value) -> value.plainValue } }
                    .orEmpty<String, Any?>()
            return AdditionalUserInfoImpl(
                providerId = data["providerId"]?.jsonPrimitive?.contentOrNull,
                profile = profile,
                username = data["screenName"]?.jsonPrimitive?.contentOrNull,
                newUser = data["isNewUser"]?.jsonPrimitive?.booleanOrNull ?: newUserByDefault
            )
        }

        /** The OAuth credential a `signInWithIdp` response returns alongside the user, when it does. */
        fun credentialOf(data: JsonObject): AuthCredential? {
            val providerId = data["providerId"]?.jsonPrimitive?.contentOrNull ?: return null
            val idToken = data["oauthIdToken"]?.jsonPrimitive?.contentOrNull
            val accessToken = data["oauthAccessToken"]?.jsonPrimitive?.contentOrNull
            if (idToken == null && accessToken == null) return null
            return OAuthCredentialImpl(providerId, idToken, accessToken, data["oauthTokenSecret"]?.jsonPrimitive?.contentOrNull, null)
        }
    }
}

/** A JSON value as the Kotlin value the Android SDK's profile map holds: null, Boolean, Long, Double, String, List or Map. */
private val JsonElement.plainValue: Any?
    get() =
        when (this) {
            is JsonNull -> null
            is JsonArray -> map { it.plainValue }
            is JsonObject -> mapValues { (_, value) -> value.plainValue }
            is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
        }
