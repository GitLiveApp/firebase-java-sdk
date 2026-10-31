package com.google.firebase.auth;

public class GithubAuthProvider {
    public static final String PROVIDER_ID = "github.com";
    public static final String GITHUB_SIGN_IN_METHOD = "github.com";

    private GithubAuthProvider() {
    }

    public static AuthCredential getCredential(String token) {
        return new GithubAuthCredential(token);
    }
}
