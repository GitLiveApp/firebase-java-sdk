package com.google.firebase.auth

class FirebaseAuthUserCollisionException(errorCode: String, detailMessage: String) : FirebaseAuthException(errorCode, detailMessage) {
    /** The email of the account that already exists, when the server reported it. */
    var email: String? = null
        private set

    /** The credential the sign-in produced for the existing account, when the server returned one. */
    var updatedCredential: AuthCredential? = null
        private set

    internal fun withDetails(email: String?, updatedCredential: AuthCredential?): FirebaseAuthUserCollisionException = apply {
        this.email = email
        this.updatedCredential = updatedCredential
    }
}
