package com.wayne.statusbarforceclose;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.TaskStackListener;

import java.lang.reflect.Method;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

final class TaskStackListenerBridge extends TaskStackListener {
    private final RecoveryEventController controller;
    private final DiagnosticLogger logger;
    private final ProcessRegistrationGate registrationGate = new ProcessRegistrationGate();
    private boolean registered;

    TaskStackListenerBridge(
            RecoveryEventController controller,
            DiagnosticLogger logger) {
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo ignoredTaskInfo) {
        controller.onTaskFront();
    }

    @SuppressLint({"BlockedPrivateApi", "DiscouragedPrivateApi", "PrivateApi"})
    synchronized boolean registerOnce() {
        if (!registrationGate.tryStart()) {
            return registered;
        }
        try {
            TaskStackListener.class.getMethod(
                    "onTaskMovedToFront", ActivityManager.RunningTaskInfo.class);
            HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/app/ActivityTaskManager;",
                    "Landroid/app/ITaskStackListener;");
            Class<?> activityTaskManager = Class.forName("android.app.ActivityTaskManager");
            Method getService = activityTaskManager.getDeclaredMethod("getService");
            getService.setAccessible(true);
            Object service = getService.invoke(null);
            Class<?> listenerInterface = Class.forName("android.app.ITaskStackListener");
            Method register = service.getClass().getMethod(
                    "registerTaskStackListener", listenerInterface);
            register.invoke(service, this);
            registered = true;
            logger.info("task_listener_registered", "listener=" + getClass().getName());
        } catch (Throwable failure) {
            logger.error("task_listener_registration_failed", "listener="
                    + getClass().getName(), failure);
        }
        return registered;
    }
}
