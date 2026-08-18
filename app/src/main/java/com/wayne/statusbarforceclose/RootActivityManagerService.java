package com.wayne.statusbarforceclose;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.topjohnwu.superuser.ipc.RootService;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class RootActivityManagerService extends RootService {
    private static final String TAG = "StatusBarForceClose";

    private final IRootActivityController.Stub controller =
            new IRootActivityController.Stub() {
                @Override
                public boolean forceStop(String packageName, int userId) {
                    if (packageName == null || packageName.isBlank() || userId < 0) {
                        report(Log.WARN, "root_target_rejected", "package=" + packageName
                                + " user=" + userId, null);
                        return false;
                    }

                    long identity = Binder.clearCallingIdentity();
                    try {
                        report(Log.INFO, "root_force_stop_start", "package=" + packageName
                                + " user=" + userId + " pid=" + android.os.Process.myPid()
                                + " uid=" + android.os.Process.myUid(), null);
                        RootActivityManager.forceStopPackage(packageName, userId);
                        report(Log.INFO, "root_force_stop_result", "package=" + packageName
                                + " user=" + userId + " success=true", null);
                        return true;
                    } catch (Throwable throwable) {
                        report(Log.ERROR, "root_force_stop_exception", "package=" + packageName
                                + " user=" + userId, unwrap(throwable));
                        return false;
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        report(Log.INFO, "root_service_bound", "pid=" + android.os.Process.myPid()
                + " uid=" + android.os.Process.myUid(), null);
        return controller;
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocation
                && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return throwable;
    }

    private static void report(int priority, String event, String details, Throwable throwable) {
        if (!BuildConfig.DIAGNOSTICS_ENABLED) {
            return;
        }
        String message = "event=" + event + " " + details;
        if (throwable == null) {
            Log.println(priority, TAG, message);
        } else {
            Log.println(priority, TAG, message + "\n" + Log.getStackTraceString(throwable));
        }
    }

    private static final class RootActivityManager {
        private static Object activityManager;
        private static Method forceStopPackage;

        private RootActivityManager() {
        }

        @SuppressLint({"PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
        static synchronized void forceStopPackage(String packageName, int userId)
                throws ReflectiveOperationException {
            if (activityManager == null || forceStopPackage == null) {
                HiddenApiBypass.addHiddenApiExemptions(
                        "Landroid/os/ServiceManager",
                        "Landroid/app/IActivityManager");
                Class<?> serviceManager = Class.forName("android.os.ServiceManager");
                Method getService = serviceManager.getDeclaredMethod("getService", String.class);
                IBinder binder = (IBinder) getService.invoke(null, "activity");
                if (binder == null) {
                    throw new IllegalStateException("ActivityManager binder is unavailable");
                }

                Class<?> stub = Class.forName("android.app.IActivityManager$Stub");
                Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
                activityManager = asInterface.invoke(null, binder);
                if (activityManager == null) {
                    throw new IllegalStateException("IActivityManager is unavailable");
                }
                forceStopPackage = activityManager.getClass().getMethod(
                        "forceStopPackage",
                        String.class,
                        int.class);
            }
            forceStopPackage.invoke(activityManager, packageName, userId);
        }
    }
}
