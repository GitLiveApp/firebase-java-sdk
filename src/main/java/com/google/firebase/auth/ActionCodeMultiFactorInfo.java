package com.google.firebase.auth;

public abstract class ActionCodeMultiFactorInfo extends ActionCodeInfo {
    public ActionCodeMultiFactorInfo() {
    }

    public abstract MultiFactorInfo getMultiFactorInfo();
}
