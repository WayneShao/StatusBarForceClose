package com.wayne.statusbarforceclose;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

final class RecoveryBroadcastReceiver extends BroadcastReceiver {
    private final String expectedAction;
    private final RecoveryEventController controller;

    RecoveryBroadcastReceiver(String expectedAction, RecoveryEventController controller) {
        RecoveryReason expectedReason = RecoveryActionRouter.route(expectedAction);
        if (expectedReason == null) {
            throw new IllegalArgumentException("Unsupported recovery action");
        }
        this.expectedAction = expectedAction;
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!expectedAction.equals(action)) {
            return;
        }
        RecoveryReason reason = RecoveryActionRouter.route(action);
        if (reason == RecoveryReason.SCREEN_ON) {
            controller.onScreenOn();
        } else if (reason == RecoveryReason.USER_PRESENT) {
            controller.onUserPresent();
        }
    }
}
