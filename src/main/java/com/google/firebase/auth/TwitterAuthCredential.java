package com.google.firebase.auth;

public class TwitterAuthCredential extends AuthCredential {
    private final String token;
    private final String secret;

    TwitterAuthCredential(String token, String secret) {
        this.token = requireValue(token, "token");
        this.secret = requireValue(secret, "secret");
    }

    @Override
    public String getProvider() {
        return TwitterAuthProvider.PROVIDER_ID;
    }

    @Override
    public String getSignInMethod() {
        return TwitterAuthProvider.TWITTER_SIGN_IN_METHOD;
    }

    @Override
    String accessToken() {
        return token;
    }

    @Override
    String oauthTokenSecret() {
        return secret;
    }
}
