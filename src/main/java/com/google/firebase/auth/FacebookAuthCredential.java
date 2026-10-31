package com.google.firebase.auth;

public class FacebookAuthCredential extends AuthCredential {
    private final String accessToken;

    FacebookAuthCredential(String accessToken) {
        this.accessToken = requireValue(accessToken, "accessToken");
    }

    @Override
    public String getProvider() {
        return FacebookAuthProvider.PROVIDER_ID;
    }

    @Override
    public String getSignInMethod() {
        return FacebookAuthProvider.FACEBOOK_SIGN_IN_METHOD;
    }

    @Override
    String accessToken() {
        return accessToken;
    }
}
