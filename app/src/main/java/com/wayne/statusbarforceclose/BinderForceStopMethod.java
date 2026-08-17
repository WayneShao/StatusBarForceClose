package com.wayne.statusbarforceclose;

import android.annotation.SuppressLint;

import java.lang.reflect.Method;

final class BinderForceStopMethod implements ForceStopMethod {
    @Override
    @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
    public boolean forceStop(String packageName, int userId) {
        if (packageName == null || userId < 0) {
            return false;
        }

        try {
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            Method getService = activityManagerClass.getDeclaredMethod("getService");
            getService.setAccessible(true);
            Object service = getService.invoke(null);
            if (service == null) {
                return false;
            }

            Class<?> interfaceClass = Class.forName("android.app.IActivityManager");
            Method forceStopPackage = interfaceClass.getMethod(
                    "forceStopPackage", String.class, int.class);
            forceStopPackage.invoke(service, packageName, userId);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
