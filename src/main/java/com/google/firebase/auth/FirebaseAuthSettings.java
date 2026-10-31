package com.google.firebase.auth;

/** Test settings of a {@link FirebaseAuth} instance; they concern phone verification, which the JVM port does not perform. */
public abstract class FirebaseAuthSettings {
    public FirebaseAuthSettings() {
    }

    public abstract void forceRecaptchaFlowForTesting(boolean forceRecaptchaFlow);

    public abstract void setAppVerificationDisabledForTesting(boolean disabled);

    public abstract void setAutoRetrievedSmsCodeForPhoneNumber(String phoneNumber, String smsCode);
}
