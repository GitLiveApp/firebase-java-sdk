package com.google.firebase.auth;

/** An email credential: an email with either a password or a sign-in link, as {@link EmailAuthProvider} creates them. */
public class EmailAuthCredential extends AuthCredential {
    private final String email;
    private final String password;
    private final String emailLink;

    EmailAuthCredential(String email, String password, String emailLink) {
        this.email = requireValue(email, "email");
        this.password = password;
        this.emailLink = emailLink;
    }

    @Override
    public String getProvider() {
        return EmailAuthProvider.PROVIDER_ID;
    }

    @Override
    public String getSignInMethod() {
        return emailLink != null ? EmailAuthProvider.EMAIL_LINK_SIGN_IN_METHOD : EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD;
    }

    String getEmail() {
        return email;
    }

    String getPassword() {
        return password;
    }

    String getEmailLink() {
        return emailLink;
    }
}
