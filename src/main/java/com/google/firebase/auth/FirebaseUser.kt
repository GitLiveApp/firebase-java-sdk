package com.google.firebase.auth

import android.app.Activity
import android.net.Uri
import com.google.android.gms.tasks.Task

abstract class FirebaseUser : UserInfo {
    abstract override val uid: String
    abstract override val email: String?
    abstract override val photoUrl: Uri?
    abstract override val displayName: String?
    abstract override val phoneNumber: String?
    abstract override val isEmailVerified: Boolean
    abstract override val providerId: String
    abstract val isAnonymous: Boolean
    abstract val metadata: FirebaseUserMetadata
    abstract val providerData: List<UserInfo>

    /** The tenant the user belongs to; the JVM port signs in to the project's default tenant. */
    open val tenantId: String? get() = null

    val multiFactor: MultiFactor get() = UnsupportedMultiFactor()

    abstract fun delete(): Task<Void>

    abstract fun reload(): Task<Void>

    abstract fun getIdToken(forceRefresh: Boolean): Task<GetTokenResult>

    abstract fun updateEmail(email: String): Task<Unit>

    abstract fun updateProfile(request: UserProfileChangeRequest): Task<Unit>

    abstract fun linkWithCredential(credential: AuthCredential): Task<AuthResult>

    fun verifyBeforeUpdateEmail(newEmail: String): Task<Void> = verifyBeforeUpdateEmail(newEmail, null)

    abstract fun verifyBeforeUpdateEmail(newEmail: String, actionCodeSettings: ActionCodeSettings?): Task<Void>

    fun sendEmailVerification(): Task<Void> = sendEmailVerification(null)

    abstract fun sendEmailVerification(actionCodeSettings: ActionCodeSettings?): Task<Void>

    abstract fun updatePhoneNumber(credential: PhoneAuthCredential): Task<Void>

    fun startActivityForLinkWithProvider(activity: Activity, federatedAuthProvider: FederatedAuthProvider): Task<AuthResult> =
        unsupportedTask(ACTIVITY_FLOWS_UNSUPPORTED)

    fun startActivityForReauthenticateWithProvider(activity: Activity, federatedAuthProvider: FederatedAuthProvider): Task<AuthResult> =
        unsupportedTask(ACTIVITY_FLOWS_UNSUPPORTED)

    fun unlink(provider: String): Task<AuthResult> = TODO()

    fun updatePassword(password: String): Task<Unit> = TODO()

    fun reauthenticate(credential: AuthCredential): Task<Unit> = TODO()

    fun reauthenticateAndRetrieveData(credential: AuthCredential): Task<AuthResult> = TODO()
}

internal const val ACTIVITY_FLOWS_UNSUPPORTED = "Provider sign-in through an Activity is not available on the JVM; sign in with a credential instead"
