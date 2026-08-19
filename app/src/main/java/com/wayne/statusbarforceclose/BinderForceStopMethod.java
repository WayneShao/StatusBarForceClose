package com.wayne.statusbarforceclose;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class BinderForceStopMethod implements ForceStopMethod {
    private final DiagnosticLogger logger;

    BinderForceStopMethod(DiagnosticLogger logger) {
        this.logger = logger;
    }

    @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
    static boolean isApiPresent() {
        try {
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            activityManagerClass.getDeclaredMethod("getService");
            Class<?> interfaceClass = Class.forName("android.app.IActivityManager");
            interfaceClass.getMethod("forceStopPackage", String.class, int.class);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    static boolean isAvailableTo(Context context) {
        return context != null
                && isApiPresent()
                && context.checkSelfPermission("android.permission.FORCE_STOP_PACKAGES")
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
    public BackendResult forceStop(String packageName, int userId, boolean waitForConnection) {
        long startedAt = android.os.SystemClock.elapsedRealtime();
        if (packageName == null || userId < 0) {
            logger.warn("binder_invalid_target", "package=" + packageName + " user=" + userId);
            return result(BackendStatus.OPERATION_FAILED, startedAt);
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
                return result(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
            }

            Class<?> interfaceClass = Class.forName("android.app.IActivityManager");
            Method forceStopPackage = interfaceClass.getMethod(
                    "forceStopPackage", String.class, int.class);
            forceStopPackage.invoke(service, packageName, userId);
            logger.info("binder_success", "package=" + packageName + " user=" + userId);
            return result(BackendStatus.SUCCESS, startedAt);
        } catch (Throwable throwable) {
            logger.error("binder_exception", "package=" + packageName + " user=" + userId,
                    throwable);
            return result(classify(throwable), startedAt);
        }
    }

    private static BackendStatus classify(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof ClassNotFoundException || cause instanceof NoSuchMethodException) {
            return BackendStatus.UNSUPPORTED;
        }
        if (cause instanceof SecurityException) {
            return BackendStatus.PERMISSION_REJECTED;
        }
        if (cause instanceof android.os.RemoteException) {
            return BackendStatus.TRANSIENT_TRANSPORT_FAILURE;
        }
        return BackendStatus.OPERATION_FAILED;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static BackendResult result(BackendStatus status, long startedAt) {
        return new BackendResult(
                BackendKind.SYSTEM_UI,
                status,
                android.os.SystemClock.elapsedRealtime() - startedAt);
    }
}
