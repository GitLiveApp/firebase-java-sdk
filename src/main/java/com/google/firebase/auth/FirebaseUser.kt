package com.google.firebase.auth

import com.google.android.gms.tasks.Task

abstract class FirebaseUser {
    abstract val uid: String
    abstract val email: String?
    abstract val photoUrl: String?
    abstract val displayName: String?
    abstract val isAnonymous: Boolean

    abstract fun delete(): Task<Void>

    abstract fun reload(): Task<Void>

    abstract fun verifyBeforeUpdateEmail(
        newEmail: String,
        actionCodeSettings: ActionCodeSettings?
    ): Task<Unit>

    abstract fun updateEmail(email: String): Task<Unit>

    abstract fun getIdToken(forceRefresh: Boolean): Task<GetTokenResult>

    abstract fun updateProfile(request: UserProfileChangeRequest): Task<Unit>

    abstract val phoneNumber: String?
    abstract val isEmailVerified: Boolean
    abstract val metadata: FirebaseUserMetadata
    val multiFactor: MultiFactor get() = TODO()
    abstract val providerData: List<UserInfo>
    abstract val providerId: String

    abstract fun linkWithCredential(credential: AuthCredential): Task<AuthResult>

    fun sendEmailVerification(): Task<Unit> = TODO()

    fun sendEmailVerification(actionCodeSettings: ActionCodeSettings): Task<Unit> = TODO()

    fun unlink(provider: String): Task<AuthResult> = TODO()

    fun updatePassword(password: String): Task<Unit> = TODO()

    fun updatePhoneNumber(credential: AuthCredential): Task<Unit> = TODO()

    fun reauthenticate(credential: AuthCredential): Task<Unit> = TODO()

    fun reauthenticateAndRetrieveData(credential: AuthCredential): Task<AuthResult> = TODO()
}
