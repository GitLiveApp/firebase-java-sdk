package com.google.firebase.auth;

public final class OAuthCredential extends AuthCredential {
    OAuthCredential(String provider, String idToken, String accessToken, String rawNonce) {
        super(provider, idToken, accessToken, rawNonce);
        if (optionalValue(idToken, "idToken") == null && optionalValue(accessToken, "accessToken") == null) {
            throw new IllegalArgumentException("At least one ID token or access token is required.");
        }
    }
}
