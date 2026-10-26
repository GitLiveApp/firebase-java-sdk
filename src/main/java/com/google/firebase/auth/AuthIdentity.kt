package com.google.firebase.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

/** Account lookup wraps its profile in users, unlike sign-in responses. */
internal fun accountProfile(data: JsonObject): JsonObject =
    (data["users"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject ?: data

/** Token claims are a fallback for identity, never a replacement for server validation. */
internal fun responseUid(data: JsonObject, idToken: String): String {
    fun JsonObject.uid(): String? = sequenceOf("uid", "user_id", "localId")
        .mapNotNull { (get(it) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        .firstOrNull()
    data.uid()?.let { return it }
    accountProfile(data).uid()?.let { return it }
    val claims = runCatching {
        val payload = idToken.split('.')[1]
        jsonParser.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)) as? JsonObject
    }.getOrNull()
    return sequenceOf("user_id", "sub")
        .mapNotNull { (claims?.get(it) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        .firstOrNull() ?: throw IllegalArgumentException("Auth response has no user identity")
}
