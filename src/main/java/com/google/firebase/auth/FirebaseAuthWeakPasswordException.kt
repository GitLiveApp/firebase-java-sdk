package com.google.firebase.auth

class FirebaseAuthWeakPasswordException(
    errorCode: String,
    detailMessage: String,
    /** Why the password was rejected, when the server said. */
    val reason: String?
) : FirebaseAuthInvalidCredentialsException(errorCode, detailMessage) {
    constructor(errorCode: String, detailMessage: String) : this(errorCode, detailMessage, null)
}
