package com.google.firebase.auth;

public class GithubAuthCredential extends AuthCredential {
    private final String token;

    GithubAuthCredential(String token) {
        this.token = requireValue(token, "token");
    }

    @Override
    public String getProvider() {
        return GithubAuthProvider.PROVIDER_ID;
    }

    @Override
    public String getSignInMethod() {
        return GithubAuthProvider.GITHUB_SIGN_IN_METHOD;
    }

    @Override
    String accessToken() {
        return token;
    }
}
