package com.google.firebase.auth

import android.app.Activity
import java.util.concurrent.TimeUnit

/** The options of a phone number verification, mirroring the Android class; see [PhoneAuthProvider.verifyPhoneNumber]. */
class PhoneAuthOptions private constructor(
    internal val auth: FirebaseAuth,
    internal val phoneNumber: String?,
    internal val timeout: Long?,
    internal val timeUnit: TimeUnit?,
    internal val activity: Activity?,
    internal val callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks?,
    internal val forceResendingToken: PhoneAuthProvider.ForceResendingToken?,
    internal val multiFactorSession: MultiFactorSession?,
    internal val multiFactorHint: PhoneMultiFactorInfo?,
    internal val requireSmsValidation: Boolean
) {
    class Builder(private val auth: FirebaseAuth) {
        private var phoneNumber: String? = null
        private var timeout: Long? = 60L
        private var timeUnit: TimeUnit? = TimeUnit.SECONDS
        private var activity: Activity? = null
        private var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks? = null
        private var forceResendingToken: PhoneAuthProvider.ForceResendingToken? = null
        private var multiFactorSession: MultiFactorSession? = null
        private var multiFactorHint: PhoneMultiFactorInfo? = null
        private var requireSmsValidation: Boolean = false

        fun setPhoneNumber(phoneNumber: String): Builder = apply { this.phoneNumber = phoneNumber }

        fun setTimeout(timeout: Long?, unit: TimeUnit): Builder = apply {
            this.timeout = timeout
            this.timeUnit = unit
        }

        fun setActivity(activity: Activity): Builder = apply { this.activity = activity }

        fun setCallbacks(callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks): Builder = apply { this.callbacks = callbacks }

        fun setForceResendingToken(token: PhoneAuthProvider.ForceResendingToken): Builder = apply { this.forceResendingToken = token }

        fun setMultiFactorSession(session: MultiFactorSession): Builder = apply { this.multiFactorSession = session }

        fun setMultiFactorHint(hint: PhoneMultiFactorInfo): Builder = apply { this.multiFactorHint = hint }

        fun requireSmsValidation(require: Boolean): Builder = apply { this.requireSmsValidation = require }

        fun build(): PhoneAuthOptions {
            requireNotNull(callbacks) { "You must specify callbacks via setCallbacks()." }
            require(phoneNumber != null || multiFactorHint != null) { "You must specify a phone number via setPhoneNumber() or a multi-factor hint via setMultiFactorHint()." }
            return PhoneAuthOptions(auth, phoneNumber, timeout, timeUnit, activity, callbacks, forceResendingToken, multiFactorSession, multiFactorHint, requireSmsValidation)
        }
    }

    companion object {
        @JvmStatic
        fun newBuilder(): Builder = Builder(FirebaseAuth.getInstance())

        @JvmStatic
        fun newBuilder(auth: FirebaseAuth): Builder = Builder(auth)
    }
}
