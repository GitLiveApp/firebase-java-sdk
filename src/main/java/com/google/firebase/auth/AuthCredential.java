package com.google.firebase.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A credential a user signs in with, mirroring the Android class: {@link #getProvider()} identifies the credential's
 * provider and {@link #getSignInMethod()} the method within it. The package-private token accessors feed the Identity
 * Toolkit {@code signInWithIdp} post body; a credential that is not exchanged that way (email, phone) leaves them null.
 */
public abstract class AuthCredential {

    protected AuthCredential() {
    }

    public abstract String getProvider();

    public abstract String getSignInMethod();

    /** The OAuth ID token, posted as {@code id_token}; null when the credential carries none. */
    String idToken() {
        return null;
    }

    /** The OAuth access token, posted as {@code access_token}; null when the credential carries none. */
    String accessToken() {
        return null;
    }

    /** The raw nonce the ID token was requested with, posted as {@code nonce}; null when there is none. */
    String rawNonce() {
        return null;
    }

    /** The OAuth 1 token secret (Twitter), posted as {@code oauth_token_secret}; null when there is none. */
    String oauthTokenSecret() {
        return null;
    }

    String toIdpPostBody() {
        List<String> values = new ArrayList<>();
        values.add(formValue("providerId", getProvider()));
        if (idToken() != null) {
            values.add(formValue("id_token", idToken()));
        }
        if (accessToken() != null) {
            values.add(formValue("access_token", accessToken()));
        }
        if (rawNonce() != null) {
            values.add(formValue("nonce", rawNonce()));
        }
        if (oauthTokenSecret() != null) {
            values.add(formValue("oauth_token_secret", oauthTokenSecret()));
        }
        return String.join("&", values);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{provider='" + getProvider() + "'}";
    }

    static String requireValue(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or blank.");
        }
        return value;
    }

    static String optionalValue(String value, String name) {
        if (value != null && value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }

    static void requireOAuthToken(String idToken, String accessToken) {
        if (idToken == null && accessToken == null) {
            throw new IllegalArgumentException("At least one ID token or access token is required.");
        }
    }

    private static String formValue(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" +
            URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
