package com.google.firebase.auth;

import kotlin.NotImplementedError;

import java.util.List;
import java.util.Map;

public class OAuthProvider {

    public OAuthProvider(Builder builder) {
        throw new NotImplementedError();
    }

    public static AuthCredential getCredential(String email, String password) {
        throw new NotImplementedError();
    }

    public static class Builder {
        public Builder setScopes(List<String> scopes) {
            throw new NotImplementedError();
        }
        public Builder addCustomParameters(Map<String, String> customParameters) {
            throw new NotImplementedError();
        }
        public OAuthProvider build() {
            throw new NotImplementedError();
        }
    }

    public static class CredentialBuilder {
        public Builder setAccessToken(String accessToken) {
            throw new NotImplementedError();
        }
        public Builder setIdToken(String idToken) {
            throw new NotImplementedError();
        }
        public Builder setIdTokenWithRawNonce(String idToken, String rawNonce) {
            throw new NotImplementedError();
        }
        public void build() {
            throw new NotImplementedError();
        }
    }

    public static Builder newBuilder(String provider, FirebaseAuth auth) {
        throw new NotImplementedError();
    }

    public static CredentialBuilder newCredentialBuilder(String provider) {
        throw new NotImplementedError();
    }
}
