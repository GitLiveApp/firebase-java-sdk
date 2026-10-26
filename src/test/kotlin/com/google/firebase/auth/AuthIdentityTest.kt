package com.google.firebase.auth

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class AuthIdentityTest {
    private fun data(json: String) = jsonParser.parseToJsonElement(json).jsonObject
    private fun token(claims: String) = "e30." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(claims.toByteArray(Charsets.UTF_8)) + "."

    @Test fun responseIdentityTakesPrecedenceOverAccountAndToken() {
        assertEquals("response", responseUid(data("""{"localId":"response","users":[{"localId":"account"}]}"""), token("""{"user_id":"token"}""")))
    }

    @Test fun accountLookupUsesFirstUserIdentityAndProfile() {
        val response = data("""{"users":[{"localId":"account","email":"account@example.test","displayName":"Account"},{"localId":"other"}]}""")
        assertEquals("account", responseUid(response, token("""{"sub":"token"}""")))
        assertEquals("account@example.test", accountProfile(response)["email"]!!.jsonPrimitive.content)
        assertEquals("Account", accountProfile(response)["displayName"]!!.jsonPrimitive.content)
    }

    @Test fun customTokenResponseFallsBackToUserIdClaim() {
        assertEquals("watch", responseUid(data("""{"idToken":"irrelevant"}"""), token("""{"user_id":"watch","sub":"other"}""")))
    }

    @Test fun subjectIsTheLastFallback() {
        assertEquals("subject", responseUid(data("""{"uid":"","users":[]}"""), token("""{"sub":"subject"}""")))
    }

    @Test fun malformedTokenDoesNotPreventExplicitServerIdentity() {
        assertEquals("account", responseUid(data("""{"users":[{"localId":"account"}]}"""), "invalid"))
    }

    @Test fun missingIdentityIsRejected() {
        for (token in listOf("invalid", token("""{"sub":""}"""))) {
            assertThrows(IllegalArgumentException::class.java) { responseUid(data("{}"), token) }
        }
    }
}
