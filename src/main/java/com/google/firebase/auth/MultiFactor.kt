package com.google.firebase.auth

import android.app.Activity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

/*
 * Multi-factor authentication, mirroring the Android classes' shapes. The JVM port cannot enrol or verify a second
 * factor (phone verification and TOTP enrolment need the device flows), so the operations fail with
 * UnsupportedOperationException while the types let code written against the Android SDK compile.
 */

internal const val MULTI_FACTOR_UNSUPPORTED = "Multi-factor authentication is not supported by firebase-java-sdk"

internal fun <T> unsupportedTask(message: String): Task<T> = Tasks.forException(UnsupportedOperationException(message))

abstract class MultiFactorInfo {
    abstract val displayName: String?
    abstract val enrollmentTimestamp: Long
    abstract val factorId: String
    abstract val uid: String

    companion object {
        const val FACTOR_ID_KEY = "factorIdKey"
    }
}

class PhoneMultiFactorInfo(
    override val uid: String,
    override val displayName: String?,
    override val enrollmentTimestamp: Long,
    val phoneNumber: String
) : MultiFactorInfo() {
    override val factorId: String get() = PhoneMultiFactorGenerator.FACTOR_ID
}

class TotpMultiFactorInfo internal constructor(
    override val uid: String,
    override val displayName: String?,
    override val enrollmentTimestamp: Long
) : MultiFactorInfo() {
    override val factorId: String get() = TotpMultiFactorGenerator.FACTOR_ID
}

abstract class MultiFactorAssertion {
    abstract val factorId: String
}

class PhoneMultiFactorAssertion(internal val credential: PhoneAuthCredential) : MultiFactorAssertion() {
    override val factorId: String get() = PhoneMultiFactorGenerator.FACTOR_ID
}

class TotpMultiFactorAssertion(
    internal val enrollmentId: String?,
    internal val secret: TotpSecret?,
    internal val oneTimePassword: String?
) : MultiFactorAssertion() {
    override val factorId: String get() = TotpMultiFactorGenerator.FACTOR_ID
}

class PhoneMultiFactorGenerator {
    companion object {
        const val FACTOR_ID = "phone"

        @JvmStatic
        fun getAssertion(credential: PhoneAuthCredential): PhoneMultiFactorAssertion = PhoneMultiFactorAssertion(credential)
    }
}

class TotpMultiFactorGenerator private constructor() {
    companion object {
        const val FACTOR_ID = "totp"

        @JvmStatic
        fun generateSecret(session: MultiFactorSession): Task<TotpSecret> = unsupportedTask(MULTI_FACTOR_UNSUPPORTED)

        @JvmStatic
        fun getAssertionForEnrollment(secret: TotpSecret, oneTimePassword: String): TotpMultiFactorAssertion =
            TotpMultiFactorAssertion(null, secret, oneTimePassword)

        @JvmStatic
        fun getAssertionForSignIn(enrollmentId: String, oneTimePassword: String): TotpMultiFactorAssertion =
            TotpMultiFactorAssertion(enrollmentId, null, oneTimePassword)
    }
}

interface TotpSecret {
    val codeIntervalSeconds: Int
    val codeLength: Int
    val enrollmentCompletionDeadline: Long
    val hashAlgorithm: String
    val sessionInfo: String
    val sharedSecretKey: String

    fun generateQrCodeUrl(): String

    fun generateQrCodeUrl(accountName: String, issuer: String): String

    fun openInOtpApp(qrCodeUrl: String)

    fun openInOtpApp(qrCodeUrl: String, fallbackUrl: String, activity: Activity)
}

class MultiFactorSession

abstract class MultiFactor {
    abstract val enrolledFactors: List<MultiFactorInfo>
    abstract val session: Task<MultiFactorSession>

    abstract fun enroll(multiFactorAssertion: MultiFactorAssertion, displayName: String?): Task<Void>

    abstract fun unenroll(multiFactorInfo: MultiFactorInfo): Task<Void>

    abstract fun unenroll(factorUid: String): Task<Void>
}

/** The account's second factors; the JVM port enrols none. */
internal class UnsupportedMultiFactor : MultiFactor() {
    override val enrolledFactors: List<MultiFactorInfo> get() = emptyList()
    override val session: Task<MultiFactorSession> get() = unsupportedTask(MULTI_FACTOR_UNSUPPORTED)

    override fun enroll(multiFactorAssertion: MultiFactorAssertion, displayName: String?): Task<Void> = unsupportedTask(MULTI_FACTOR_UNSUPPORTED)

    override fun unenroll(multiFactorInfo: MultiFactorInfo): Task<Void> = unsupportedTask(MULTI_FACTOR_UNSUPPORTED)

    override fun unenroll(factorUid: String): Task<Void> = unsupportedTask(MULTI_FACTOR_UNSUPPORTED)
}

abstract class MultiFactorResolver {
    abstract val firebaseAuth: FirebaseAuth
    abstract val hints: List<MultiFactorInfo>
    abstract val session: MultiFactorSession

    abstract fun resolveSignIn(assertion: MultiFactorAssertion): Task<AuthResult>
}
