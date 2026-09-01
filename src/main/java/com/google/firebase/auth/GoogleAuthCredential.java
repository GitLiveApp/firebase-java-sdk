package com.google.firebase.auth;

public final class GoogleAuthCredential extends AuthCredential {
    GoogleAuthCredential(String idToken, String accessToken) {
        super(GoogleAuthProvider.PROVIDER_ID, idToken, accessToken, null);
        if (optionalValue(idToken, "idToken") == null && optionalValue(accessToken, "accessToken") == null) {
            throw new IllegalArgumentException("At least one ID token or access token is required.");
        }
    }
}
