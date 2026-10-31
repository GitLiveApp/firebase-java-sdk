package com.google.firebase.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/** The result of `accounts:resetPassword` called with a code alone: its `requestType` and the account it concerns. */
internal class ActionCodeResultImpl(data: JsonObject) : ActionCodeResult {
    private val operation: Int =
        when (data["requestType"]?.jsonPrimitive?.contentOrNull) {
            "PASSWORD_RESET" -> ActionCodeResult.PASSWORD_RESET
            "VERIFY_EMAIL" -> ActionCodeResult.VERIFY_EMAIL
            "RECOVER_EMAIL" -> ActionCodeResult.RECOVER_EMAIL
            "EMAIL_SIGNIN" -> ActionCodeResult.SIGN_IN_WITH_EMAIL_LINK
            "VERIFY_AND_CHANGE_EMAIL" -> ActionCodeResult.VERIFY_BEFORE_CHANGE_EMAIL
            "REVERT_SECOND_FACTOR_ADDITION" -> ActionCodeResult.REVERT_SECOND_FACTOR_ADDITION
            else -> ActionCodeResult.ERROR
        }

    private val email = data["email"]?.jsonPrimitive?.contentOrNull
    private val newEmail = data["newEmail"]?.jsonPrimitive?.contentOrNull

    private val info: ActionCodeInfo? =
        when (operation) {
            // Recovering restores `email`; the address being left is `newEmail`.
            ActionCodeResult.RECOVER_EMAIL -> EmailInfo(email, newEmail)
            ActionCodeResult.VERIFY_BEFORE_CHANGE_EMAIL -> EmailInfo(newEmail, email)
            ActionCodeResult.REVERT_SECOND_FACTOR_ADDITION -> data["mfaInfo"]?.jsonObject?.let { MultiFactorEmailInfo(email, multiFactorInfoOf(it)) }
            ActionCodeResult.ERROR -> null
            else -> email?.let { ActionCodeInfo(it) }
        }

    override fun getOperation(): Int = operation

    override fun getInfo(): ActionCodeInfo? = info

    @Deprecated("Use getInfo()")
    override fun getData(key: Int): String? =
        when (key) {
            ActionCodeResult.EMAIL -> info?.email
            ActionCodeResult.FROM_EMAIL -> (info as? ActionCodeEmailInfo)?.previousEmail
            else -> null
        }

    private class EmailInfo(email: String?, private val previousEmail: String?) : ActionCodeEmailInfo(email) {
        override fun getPreviousEmail(): String? = previousEmail
    }

    private class MultiFactorEmailInfo(email: String?, private val multiFactorInfo: MultiFactorInfo) : ActionCodeMultiFactorInfo() {
        init {
            this.email = email
        }

        override fun getMultiFactorInfo(): MultiFactorInfo = multiFactorInfo
    }

    private companion object {
        /** The factor a `mfaInfo` object describes: a phone factor when it carries a number, a TOTP factor otherwise. */
        fun multiFactorInfoOf(info: JsonObject): MultiFactorInfo {
            val uid = info["mfaEnrollmentId"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val displayName = info["displayName"]?.jsonPrimitive?.contentOrNull
            val enrolledAt =
                info["enrolledAt"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
            val phoneNumber = info["phoneInfo"]?.jsonPrimitive?.contentOrNull
            return if (phoneNumber != null) PhoneMultiFactorInfo(uid, displayName, enrolledAt, phoneNumber) else TotpMultiFactorInfo(uid, displayName, enrolledAt)
        }
    }
}
