package com.google.firebase.auth

/** Raised on Android when a reCAPTCHA flow has no Activity to run in; the JVM port never runs one. */
class FirebaseAuthMissingActivityForRecaptchaException :
    FirebaseAuthException("ERROR_MISSING_ACTIVITY", "An Activity is required to start the reCAPTCHA flow, which is not available on the JVM.")
