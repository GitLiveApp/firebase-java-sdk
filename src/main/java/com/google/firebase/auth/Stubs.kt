package com.google.firebase.auth

import android.net.Uri
import com.google.android.gms.tasks.Task

class ActionCodeSettings {
    companion object {
        fun newBuilder(): Builder = TODO()
    }

    class Builder {
        fun setAndroidPackageName(packageName: String, installIfNotAvailable: Boolean, minimumVersion: String?): Builder = TODO()
        fun setDynamicLinkDomain(dynamicLinkDomain: String): Builder = TODO()
        fun setIOSBundleId(iOSBundleId: String): Builder = TODO()
        fun setUrl(url: String): Builder = TODO()
        fun setHandleCodeInApp(canHandleCodeInApp: Boolean): Builder = TODO()
        fun build(): ActionCodeSettings = TODO()
    }
}

class FirebaseAuthMultiFactorException(errorCode: String, detailMessage: String) : FirebaseAuthException(errorCode, detailMessage)

class UserInfo {
    val displayName: String? get() = TODO()
    val email: String get() = TODO()
    val phoneNumber: String get() = TODO()
    val photoUrl: Uri? get() = TODO()
    val providerId: String get() = TODO()
    val uid: String get() = TODO()
}

class FirebaseUserMetadata {
    val creationTimestamp: Long get() = TODO()
    val lastSignInTimestamp: Long get() = TODO()
}

class MultiFactor {
    val uid: String get() = TODO()
    val enrolledFactors: List<MultiFactorInfo> get() = TODO()
    val session: Task<MultiFactorSession> get() = TODO()
    fun enroll(multiFactorAssertion: MultiFactorAssertion, displayName: String?): Task<Unit> = TODO()
    fun unenroll(multiFactorInfo: MultiFactorInfo): Task<Unit> = TODO()
    fun unenroll(factorUid: String): Task<Unit> = TODO()
}
class MultiFactorInfo {
    val displayName: String? get() = TODO()
    val enrollmentTimestamp: Long get() = TODO()
    val factorId: String get() = TODO()
    val uid: String get() = TODO()
}
class MultiFactorAssertion {
    val factorId: String get() = TODO()
}
class MultiFactorSession
class MultiFactorResolver {
    val firebaseAuth: FirebaseAuth get() = TODO()
    val hints: List<MultiFactorInfo> get() = TODO()
    val session: MultiFactorSession get() = TODO()
    fun resolveSignIn(assertion: MultiFactorAssertion): Task<AuthResult> = TODO()
}

class PhoneAuthCredential : AuthCredential()
class OAuthCredential : AuthCredential()

interface SignInMethodQueryResult {
    val signInMethods: List<String>
}
