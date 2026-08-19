package com.wayne.statusbarforceclose;

import android.content.Context;
import android.content.IntentFilter;

final class RecoveryEventRegistrar {
    private final Context context;
    private final RecoveryEventController controller;
    private final DiagnosticLogger logger;
    private final ProcessRegistrationGate installationGate = new ProcessRegistrationGate();
    private final RecoveryBroadcastReceiver screenOnReceiver;
    private final RecoveryBroadcastReceiver userPresentReceiver;
    private TaskStackListenerBridge taskStackListener;

    RecoveryEventRegistrar(
            Context context,
            RecoveryEventController controller,
            DiagnosticLogger logger) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
        screenOnReceiver = new RecoveryBroadcastReceiver(
                RecoveryActionRouter.ACTION_SCREEN_ON, controller);
        userPresentReceiver = new RecoveryBroadcastReceiver(
                RecoveryActionRouter.ACTION_USER_PRESENT, controller);
    }

    void installOnce() {
        if (!installationGate.tryStart()) {
            return;
        }
        registerTaskListener();
        registerReceiver("screen_on", screenOnReceiver, RecoveryActionRouter.ACTION_SCREEN_ON);
        registerReceiver(
                "user_present", userPresentReceiver, RecoveryActionRouter.ACTION_USER_PRESENT);
    }

    private void registerTaskListener() {
        try {
            taskStackListener = new TaskStackListenerBridge(controller, logger);
            taskStackListener.registerOnce();
        } catch (Throwable failure) {
            logger.error("task_listener_unavailable", "reason=linkage-or-construction", failure);
        }
    }

    private void registerReceiver(
            String name,
            RecoveryBroadcastReceiver receiver,
            String action) {
        try {
            context.registerReceiver(
                    receiver,
                    new IntentFilter(action),
                    Context.RECEIVER_EXPORTED);
            logger.info("recovery_receiver_registered", "name=" + name + " action=" + action);
        } catch (Throwable failure) {
            logger.error("recovery_receiver_registration_failed", "name=" + name
                    + " action=" + action, failure);
        }
    }
}
