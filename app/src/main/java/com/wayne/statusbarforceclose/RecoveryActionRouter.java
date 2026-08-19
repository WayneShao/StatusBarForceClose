package com.wayne.statusbarforceclose;

final class RecoveryActionRouter {
    static final String ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON";
    static final String ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT";

    private RecoveryActionRouter() {
    }

    static RecoveryReason route(String action) {
        if (ACTION_SCREEN_ON.equals(action)) {
            return RecoveryReason.SCREEN_ON;
        }
        if (ACTION_USER_PRESENT.equals(action)) {
            return RecoveryReason.USER_PRESENT;
        }
        return null;
    }
}
