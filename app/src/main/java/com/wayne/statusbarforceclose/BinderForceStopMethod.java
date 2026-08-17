package com.wayne.statusbarforceclose;

import android.annotation.SuppressLint;

import java.lang.reflect.Method;

final class BinderForceStopMethod implements ForceStopMethod {
    private final DiagnosticLogger logger;

    BinderForceStopMethod(DiagnosticLogger logger) {
        this.logger = logger;
    }

    @Override
    @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
    public boolean forceStop(String packageName, int userId) {
        if (packageName == null || userId < 0) {
            logger.warn("binder_invalid_target", "package=" + packageName + " user=" + userId);
            return false;
        }

        try {
            logger.info("binder_attempt", "package=" + packageName + " user=" + userId);
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            Method getService = activityManagerClass.getDeclaredMethod("getService");
            getService.setAccessible(true);
            Object service = getService.invoke(null);
            if (service == null) {
                logger.warn("binder_service_missing", "package=" + packageName
                        + " user=" + userId);
                return false;
            }

            Class<?> interfaceClass = Class.forName("android.app.IActivityManager");
            Method forceStopPackage = interfaceClass.getMethod(
                    "forceStopPackage", String.class, int.class);
            forceStopPackage.invoke(service, packageName, userId);
            logger.info("binder_success", "package=" + packageName + " user=" + userId);
            return true;
        } catch (Throwable throwable) {
            logger.error("binder_exception", "package=" + packageName + " user=" + userId,
                    throwable);
            return false;
        }
    }
}
