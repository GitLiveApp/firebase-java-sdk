package com.google.firebase.auth;

import java.util.Map;

public interface AdditionalUserInfo {
    Map<String, Object> getProfile();

    String getProviderId();

    String getUsername();

    boolean isNewUser();
}
