package com.google.firebase.auth

class FirebaseAuthWeakPasswordException(errorCode: String, detailMessage: String) : FirebaseAuthInvalidCredentialsException(errorCode, detailMessage)
