package com.google.firebase.auth;

/** The account an action code concerns, from {@link FirebaseAuth#checkActionCode}. */
public class ActionCodeInfo {
    protected String email;

    public ActionCodeInfo() {
    }

    ActionCodeInfo(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
