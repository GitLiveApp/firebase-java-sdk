package com.google.firebase.auth;

import android.app.Activity;
import com.google.firebase.FirebaseException;
import kotlin.NotImplementedError;

import java.util.concurrent.TimeUnit;

public class PhoneAuthProvider {
    public static PhoneAuthCredential getCredential(String email, String password) {
        throw new NotImplementedError();
    }
    public static PhoneAuthProvider getInstance(FirebaseAuth auth) {
        throw new NotImplementedError();
    }

    public void verifyPhoneNumber(String phoneNumber, long timeout, TimeUnit unit, Activity activity, PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks) {
        throw new NotImplementedError();
    }

    public void verifyPhoneNumber(String phoneNumber, long timeout, TimeUnit unit, Activity activity, PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks, ForceResendingToken forceResending) {
        throw new NotImplementedError();
    }

    public static abstract class OnVerificationStateChangedCallbacks {
        public void onCodeAutoRetrievalTimeOut(String verificationId) {
            throw new NotImplementedError();
        }
        public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken forceResendingToken) {
            throw new NotImplementedError();
        }
        public abstract void onVerificationCompleted(PhoneAuthCredential credential);
        public abstract void onVerificationFailed(FirebaseException exception);
    }

    public static class ForceResendingToken {

    }
}
