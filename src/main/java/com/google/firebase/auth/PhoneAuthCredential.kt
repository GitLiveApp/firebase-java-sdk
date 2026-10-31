package com.google.firebase.auth

/** A phone credential: the verification id of a sent SMS and the code it carried, exchanged with `signInWithPhoneNumber`. */
class PhoneAuthCredential internal constructor(
    internal val verificationId: String,
    val smsCode: String
) : AuthCredential(), Cloneable {
    init {
        require(verificationId.isNotBlank()) { "verificationId must not be blank" }
        require(smsCode.isNotBlank()) { "smsCode must not be blank" }
    }

    override fun getProvider(): String = PhoneAuthProvider.PROVIDER_ID

    override fun getSignInMethod(): String = PhoneAuthProvider.PHONE_SIGN_IN_METHOD

    override fun toString(): String = "PhoneAuthCredential{provider='phone'}"
}
