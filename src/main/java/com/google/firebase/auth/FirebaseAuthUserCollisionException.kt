package com.google.firebase.auth

class FirebaseAuthUserCollisionException(errorCode: String, detailMessage: String) : FirebaseAuthException(errorCode, detailMessage) {
    private var email: String? = null
    private var updatedCredential: AuthCredential? = null

    /** The email of the account that already exists, when the server reported it. */
    fun getEmail(): String? = email

    /** The credential the sign-in produced for the existing account, when the server returned one. */
    fun getUpdatedCredential(): AuthCredential? = updatedCredential

    internal fun withDetails(email: String?, updatedCredential: AuthCredential?): FirebaseAuthUserCollisionException = apply {
        this.email = email
        this.updatedCredential = updatedCredential
    }
}
