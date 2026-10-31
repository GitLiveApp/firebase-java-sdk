package com.google.firebase.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import java.util.concurrent.TimeUnit

/**
 * Phone number sign-in, mirroring the Android class. The JVM port exchanges a verification id and SMS code it is given
 * ([getCredential]) but cannot run a verification itself: that needs reCAPTCHA or Play Integrity, so [verifyPhoneNumber]
 * reports a failure to the callbacks.
 */
class PhoneAuthProvider private constructor() {

    @Deprecated("Use PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions)")
    fun verifyPhoneNumber(
        phoneNumber: String,
        timeout: Long,
        unit: TimeUnit,
        activity: Activity,
        callbacks: OnVerificationStateChangedCallbacks
    ) {
        callbacks.onVerificationFailed(FirebaseException(VERIFICATION_UNSUPPORTED))
    }

    @Deprecated("Use PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions)")
    fun verifyPhoneNumber(
        phoneNumber: String,
        timeout: Long,
        unit: TimeUnit,
        activity: Activity,
        callbacks: OnVerificationStateChangedCallbacks,
        forceResendingToken: ForceResendingToken?
    ) {
        callbacks.onVerificationFailed(FirebaseException(VERIFICATION_UNSUPPORTED))
    }

    abstract class OnVerificationStateChangedCallbacks {
        open fun onCodeAutoRetrievalTimeOut(verificationId: String) {}

        open fun onCodeSent(verificationId: String, forceResendingToken: ForceResendingToken) {}

        abstract fun onVerificationCompleted(credential: PhoneAuthCredential)

        abstract fun onVerificationFailed(exception: FirebaseException)
    }

    class ForceResendingToken

    companion object {
        const val PROVIDER_ID = "phone"
        const val PHONE_SIGN_IN_METHOD = "phone"

        internal const val VERIFICATION_UNSUPPORTED = "Phone number verification is not supported by firebase-java-sdk"

        @JvmStatic
        fun getCredential(verificationId: String, smsCode: String): PhoneAuthCredential = PhoneAuthCredential(verificationId, smsCode)

        @JvmStatic
        @Deprecated("Use the static PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions)")
        fun getInstance(): PhoneAuthProvider = PhoneAuthProvider()

        @JvmStatic
        @Deprecated("Use the static PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions)")
        fun getInstance(auth: FirebaseAuth): PhoneAuthProvider = PhoneAuthProvider()

        /** Reports [VERIFICATION_UNSUPPORTED] to the options' callbacks, as the JVM cannot verify a phone number. */
        @JvmStatic
        fun verifyPhoneNumber(options: PhoneAuthOptions) {
            val callbacks = options.callbacks ?: throw UnsupportedOperationException(VERIFICATION_UNSUPPORTED)
            callbacks.onVerificationFailed(FirebaseException(VERIFICATION_UNSUPPORTED))
        }
    }
}
