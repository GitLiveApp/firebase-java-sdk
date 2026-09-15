package com.google.firebase.auth;

public final class GoogleAuthProvider {
    public static final String PROVIDER_ID = "google.com";
    public static final String GOOGLE_SIGN_IN_METHOD = "google.com";

    private GoogleAuthProvider() {
    }

    public static AuthCredential getCredential(String idToken, String accessToken) {
        return new GoogleAuthCredential(idToken, accessToken);
    }
}
