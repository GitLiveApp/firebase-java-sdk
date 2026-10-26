package com.google.firebase.auth;

public final class GoogleAuthCredential extends AuthCredential {
    GoogleAuthCredential(String idToken, String accessToken) {
        super(GoogleAuthProvider.PROVIDER_ID, idToken, accessToken, null);
        requireOAuthToken(idToken, accessToken);
    }
}
