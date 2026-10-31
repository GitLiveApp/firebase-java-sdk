package com.google.firebase.auth

class FirebaseUserMetadata internal constructor(
    val creationTimestamp: Long,
    val lastSignInTimestamp: Long
)

interface SignInMethodQueryResult {
    val signInMethods: List<String>
}

internal class SignInMethodQueryResultImpl(override val signInMethods: List<String>) : SignInMethodQueryResult

internal class FirebaseAuthSettingsImpl : FirebaseAuthSettings() {
    internal var appVerificationDisabledForTesting = false
    internal var forceRecaptchaFlowForTesting = false

    override fun forceRecaptchaFlowForTesting(forceRecaptchaFlow: Boolean) {
        forceRecaptchaFlowForTesting = forceRecaptchaFlow
    }

    override fun setAppVerificationDisabledForTesting(disabled: Boolean) {
        appVerificationDisabledForTesting = disabled
    }

    override fun setAutoRetrievedSmsCodeForPhoneNumber(phoneNumber: String?, smsCode: String?) = Unit
}
