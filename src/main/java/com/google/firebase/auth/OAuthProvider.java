package com.google.firebase.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OAuthProvider {
    private final String provider;
    private final FirebaseAuth auth;
    private final List<String> scopes;
    private final Map<String, String> customParameters;

    public OAuthProvider(Builder builder) {
        provider = builder.provider;
        auth = builder.auth;
        scopes = Collections.unmodifiableList(new ArrayList<>(builder.scopes));
        customParameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.customParameters));
    }

    public static AuthCredential getCredential(String providerId, String idToken, String accessToken) {
        return new OAuthCredential(providerId, idToken, accessToken, null);
    }

    public String getProviderId() {
        return provider;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public Map<String, String> getCustomParameters() {
        return customParameters;
    }

    FirebaseAuth getAuth() {
        return auth;
    }

    public static final class Builder {
        private final String provider;
        private final FirebaseAuth auth;
        private List<String> scopes = Collections.emptyList();
        private final Map<String, String> customParameters = new LinkedHashMap<>();

        private Builder(String provider, FirebaseAuth auth) {
            this.provider = AuthCredential.requireValue(provider, "provider");
            if (auth == null) {
                throw new IllegalArgumentException("auth must not be null.");
            }
            this.auth = auth;
        }

        public Builder setScopes(List<String> scopes) {
            if (scopes == null) {
                throw new IllegalArgumentException("scopes must not be null.");
            }
            this.scopes = new ArrayList<>(scopes);
            return this;
        }

        public Builder addCustomParameters(Map<String, String> customParameters) {
            if (customParameters == null) {
                throw new IllegalArgumentException("customParameters must not be null.");
            }
            this.customParameters.putAll(customParameters);
            return this;
        }

        public OAuthProvider build() {
            return new OAuthProvider(this);
        }
    }

    public static final class CredentialBuilder {
        private final String provider;
        private String accessToken;
        private String idToken;
        private String rawNonce;

        private CredentialBuilder(String provider) {
            this.provider = AuthCredential.requireValue(provider, "provider");
        }

        public CredentialBuilder setAccessToken(String accessToken) {
            this.accessToken = AuthCredential.requireValue(accessToken, "accessToken");
            return this;
        }

        public CredentialBuilder setIdToken(String idToken) {
            this.idToken = AuthCredential.requireValue(idToken, "idToken");
            rawNonce = null;
            return this;
        }

        public CredentialBuilder setIdTokenWithRawNonce(String idToken, String rawNonce) {
            this.idToken = AuthCredential.requireValue(idToken, "idToken");
            this.rawNonce = AuthCredential.optionalValue(rawNonce, "rawNonce");
            return this;
        }

        public AuthCredential build() {
            return new OAuthCredential(provider, idToken, accessToken, rawNonce);
        }
    }

    public static Builder newBuilder(String provider) {
        return new Builder(provider, FirebaseAuth.getInstance());
    }

    public static Builder newBuilder(String provider, FirebaseAuth auth) {
        return new Builder(provider, auth);
    }

    public static CredentialBuilder newCredentialBuilder(String provider) {
        return new CredentialBuilder(provider);
    }
}
