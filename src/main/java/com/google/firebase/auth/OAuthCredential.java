package com.google.firebase.auth;

public final class OAuthCredential extends AuthCredential {
    OAuthCredential(String provider, String idToken, String accessToken, String rawNonce) {
        super(provider, idToken, accessToken, rawNonce);
        requireOAuthToken(idToken, accessToken);
    }
}
