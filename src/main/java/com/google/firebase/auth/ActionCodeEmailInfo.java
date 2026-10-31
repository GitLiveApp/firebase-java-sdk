package com.google.firebase.auth;

/** The two addresses of an email change action: {@link #getEmail()} the address after it, {@link #getPreviousEmail()} before. */
public abstract class ActionCodeEmailInfo extends ActionCodeInfo {
    public ActionCodeEmailInfo() {
    }

    ActionCodeEmailInfo(String email) {
        super(email);
    }

    public abstract String getPreviousEmail();
}
