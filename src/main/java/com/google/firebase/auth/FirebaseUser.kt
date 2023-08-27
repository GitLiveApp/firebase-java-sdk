package com.google.firebase.auth

import com.google.android.gms.tasks.Task

abstract class FirebaseUser {
    abstract val uid: String
    abstract val isAnonymous: Boolean
    abstract fun delete(): Task<Void>
    abstract fun reload(): Task<Void>

    val email: String get() = TODO()
    val displayName: String get() = TODO()
    val phoneNumber: String get() = TODO()
    val photoUrl: String? get() = TODO()
    val isEmailVerified: Boolean get() = TODO()
    val metadata: FirebaseUserMetadata get() = TODO()
    val multiFactor: MultiFactor get() = TODO()
    val providerData: List<UserInfo> get() = TODO()
    val providerId: String get() = TODO()
    abstract fun getIdToken(forceRefresh: Boolean): Task<GetTokenResult>
    fun linkWithCredential(credential: AuthCredential): Task<AuthResult> = TODO()
    fun sendEmailVerification(): Task<Unit> = TODO()
    fun sendEmailVerification(actionCodeSettings: ActionCodeSettings): Task<Unit> = TODO()
    fun unlink(provider: String): Task<AuthResult> = TODO()
    fun updateEmail(email: String): Task<Unit> = TODO()
    fun updatePassword(password: String): Task<Unit> = TODO()
    fun updatePhoneNumber(credential: AuthCredential): Task<Unit> = TODO()
    fun updateProfile(request: UserProfileChangeRequest): Task<Unit> = TODO()
    fun verifyBeforeUpdateEmail(newEmail: String, actionCodeSettings: ActionCodeSettings?): Task<Unit> = TODO()
    fun reauthenticate(credential: AuthCredential): Task<Unit> = TODO()
    fun reauthenticateAndRetrieveData(credential: AuthCredential): Task<AuthResult> = TODO()
}
