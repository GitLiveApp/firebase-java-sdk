package com.google.firebase.auth;

public class GoogleAuthCredential extends AuthCredential {
    private final String idToken;
    private final String accessToken;

    GoogleAuthCredential(String idToken, String accessToken) {
        this.idToken = optionalValue(idToken, "idToken");
        this.accessToken = optionalValue(accessToken, "accessToken");
        requireOAuthToken(this.idToken, this.accessToken);
    }

    @Override
    public String getProvider() {
        return GoogleAuthProvider.PROVIDER_ID;
    }

    @Override
    public String getSignInMethod() {
        return GoogleAuthProvider.GOOGLE_SIGN_IN_METHOD;
    }

    @Override
    String idToken() {
        return idToken;
    }

    @Override
    String accessToken() {
        return accessToken;
    }
}
