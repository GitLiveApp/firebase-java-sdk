package com.google.firebase.auth;

public class EmailAuthProvider {
    public static final String PROVIDER_ID = "password";
    public static final String EMAIL_PASSWORD_SIGN_IN_METHOD = "password";
    public static final String EMAIL_LINK_SIGN_IN_METHOD = "emailLink";

    private EmailAuthProvider() {
    }

    public static AuthCredential getCredential(String email, String password) {
        return new EmailAuthCredential(email, AuthCredential.requireValue(password, "password"), null);
    }

    public static AuthCredential getCredentialWithLink(String email, String emailLink) {
        ActionCodeUrl url = ActionCodeUrl.parseLink(AuthCredential.requireValue(emailLink, "emailLink"));
        if (url == null || url.getCode() == null) {
            throw new IllegalArgumentException(
                "Given link is not a valid email link. Please use FirebaseAuth#isSignInWithEmailLink(String) to determine this before calling this function"
            );
        }
        return new EmailAuthCredential(email, null, emailLink);
    }
}
