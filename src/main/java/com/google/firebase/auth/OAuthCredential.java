package com.google.firebase.auth;

/** A generic OAuth credential, mirroring the Android class; {@link OAuthProvider} creates the instances. */
public abstract class OAuthCredential extends AuthCredential {

    public OAuthCredential() {
    }

    public abstract String getAccessToken();

    public abstract String getIdToken();

    public abstract String getSecret();

    @Override
    String idToken() {
        return getIdToken();
    }

    @Override
    String accessToken() {
        return getAccessToken();
    }

    @Override
    String oauthTokenSecret() {
        return getSecret();
    }
}
