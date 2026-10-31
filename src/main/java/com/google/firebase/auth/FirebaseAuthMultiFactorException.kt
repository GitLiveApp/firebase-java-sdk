package com.google.firebase.auth

class FirebaseAuthMultiFactorException(
    errorCode: String,
    detailMessage: String,
    val resolver: MultiFactorResolver
) : FirebaseAuthException(errorCode, detailMessage)
