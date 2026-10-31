package com.google.firebase.auth;

final class OAuthCredentialImpl extends OAuthCredential {
    private final String provider;
    private final String idToken;
    private final String accessToken;
    private final String secret;
    private final String rawNonce;

    OAuthCredentialImpl(String provider, String idToken, String accessToken, String secret, String rawNonce) {
        this.provider = requireValue(provider, "provider");
        this.idToken = optionalValue(idToken, "idToken");
        this.accessToken = optionalValue(accessToken, "accessToken");
        this.secret = optionalValue(secret, "secret");
        this.rawNonce = optionalValue(rawNonce, "rawNonce");
        requireOAuthToken(this.idToken, this.accessToken);
        if (this.rawNonce != null && this.idToken == null) {
            throw new IllegalArgumentException("An ID token is required when a raw nonce is supplied.");
        }
    }

    @Override
    public String getProvider() {
        return provider;
    }

    @Override
    public String getSignInMethod() {
        return provider;
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public String getIdToken() {
        return idToken;
    }

    @Override
    public String getSecret() {
        return secret;
    }

    @Override
    String rawNonce() {
        return rawNonce;
    }
}
