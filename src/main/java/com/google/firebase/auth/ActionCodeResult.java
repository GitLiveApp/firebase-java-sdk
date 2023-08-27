package com.google.firebase.auth;

public interface ActionCodeResult {
    int PASSWORD_RESET = 0;
    int VERIFY_EMAIL = 1;
    int RECOVER_EMAIL = 2;
    int ERROR = 3;
    int SIGN_IN_WITH_EMAIL_LINK = 4;
    int VERIFY_BEFORE_CHANGE_EMAIL = 5;
    int REVERT_SECOND_FACTOR_ADDITION = 6;
    int EMAIL = 0;
    int FROM_EMAIL = 1;

    int getOperation();

    ActionCodeInfo getInfo();
}