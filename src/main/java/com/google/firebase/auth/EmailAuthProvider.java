package com.google.firebase.auth;

import kotlin.NotImplementedError;

public class EmailAuthProvider {
    public static AuthCredential getCredential(String email, String password) {
        throw new NotImplementedError();
    }
    public static AuthCredential getCredentialWithLink(String email, String emailLink) {
        throw new NotImplementedError();
    }
}
