package com.google.firebase.auth

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** The parts of an action link, mirroring the Android class; [parseLink] reads one, unwrapping a Dynamic Link. */
class ActionCodeUrl private constructor(
    val apiKey: String?,
    val code: String?,
    val continueUrl: String?,
    val languageCode: String?,
    val operation: Int
) {
    companion object {
        /** The action link's parts, or null when [link] is not a URL with an `oobCode` and a `mode`. */
        @JvmStatic
        fun parseLink(link: String): ActionCodeUrl? {
            val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
            val params = queryParameters(uri.rawQuery)
            params["link"]?.let { return parseLink(it) }
            params["deep_link_id"]?.let { return parseLink(it) }
            val code = params["oobCode"] ?: return null
            val mode = params["mode"] ?: return null
            return ActionCodeUrl(params["apiKey"], code, params["continueUrl"], params["lang"], operationOf(mode))
        }

        private fun queryParameters(rawQuery: String?): Map<String, String> =
            rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { parameter ->
                val separator = parameter.indexOf('=')
                if (separator < 0) {
                    decode(parameter) to ""
                } else {
                    decode(parameter.substring(0, separator)) to decode(parameter.substring(separator + 1))
                }
            }

        private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

        private fun operationOf(mode: String): Int =
            when (mode) {
                "resetPassword" -> ActionCodeResult.PASSWORD_RESET
                "verifyEmail" -> ActionCodeResult.VERIFY_EMAIL
                "recoverEmail" -> ActionCodeResult.RECOVER_EMAIL
                "signIn" -> ActionCodeResult.SIGN_IN_WITH_EMAIL_LINK
                "verifyAndChangeEmail" -> ActionCodeResult.VERIFY_BEFORE_CHANGE_EMAIL
                "revertSecondFactorAddition" -> ActionCodeResult.REVERT_SECOND_FACTOR_ADDITION
                else -> ActionCodeResult.ERROR
            }
    }
}
