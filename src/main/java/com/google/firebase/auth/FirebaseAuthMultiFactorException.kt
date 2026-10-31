package com.google.firebase.auth

class FirebaseAuthMultiFactorException(
    errorCode: String,
    detailMessage: String,
    private val resolver: MultiFactorResolver
) : FirebaseAuthException(errorCode, detailMessage) {
    fun getResolver(): MultiFactorResolver = resolver
}
