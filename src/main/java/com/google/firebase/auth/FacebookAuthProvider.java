package com.google.firebase.auth;

public class FacebookAuthProvider {
    public static final String PROVIDER_ID = "facebook.com";
    public static final String FACEBOOK_SIGN_IN_METHOD = "facebook.com";

    private FacebookAuthProvider() {
    }

    public static AuthCredential getCredential(String accessToken) {
        return new FacebookAuthCredential(accessToken);
    }
}
