package com.google.firebase.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public abstract class AuthCredential {
    private final String provider;
    private final String idToken;
    private final String accessToken;
    private final String rawNonce;

    protected AuthCredential(String provider) {
        this(provider, null, null, null);
    }

    protected AuthCredential(String provider, String idToken, String accessToken, String rawNonce) {
        this.provider = requireValue(provider, "provider");
        this.idToken = optionalValue(idToken, "idToken");
        this.accessToken = optionalValue(accessToken, "accessToken");
        this.rawNonce = optionalValue(rawNonce, "rawNonce");

        if (this.rawNonce != null && this.idToken == null) {
            throw new IllegalArgumentException("An ID token is required when a raw nonce is supplied.");
        }
    }

    public String getProvider() {
        return provider;
    }

    public String getSignInMethod() {
        return provider;
    }

    String toIdpPostBody() {
        List<String> values = new ArrayList<>();
        values.add(formValue("providerId", provider));
        if (idToken != null) {
            values.add(formValue("id_token", idToken));
        }
        if (accessToken != null) {
            values.add(formValue("access_token", accessToken));
        }
        if (rawNonce != null) {
            values.add(formValue("nonce", rawNonce));
        }
        return String.join("&", values);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{provider='" + provider + "'}";
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

    private static String formValue(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" +
            URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
