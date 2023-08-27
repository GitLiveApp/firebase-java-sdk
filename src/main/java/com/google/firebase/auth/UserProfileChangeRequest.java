package com.google.firebase.auth;

import android.net.Uri;
import kotlin.NotImplementedError;

public class UserProfileChangeRequest {
    public static class Builder {
        public Builder setDisplayName(String name) {
            throw new NotImplementedError();
        }
        public Builder setPhotoUri(Uri uri) {
            throw new NotImplementedError();
        }
        public UserProfileChangeRequest build() {
            throw new NotImplementedError();
        }
    }
}