package com.wayne.statusbarforceclose;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.IBinder;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Method;
import java.util.Objects;

final class RootSystemSettingsAdapter implements RootSystemSettings {
    private static final String MODULE_PACKAGE = "com.wayne.statusbarforceclose";
    private static final String OP_RUN_IN_BACKGROUND = "android:run_in_background";
    private static final String OP_RUN_ANY_IN_BACKGROUND = "android:run_any_in_background";

    private final Context context;
    private final int moduleUid;
    private Object deviceIdleController;
    private Method isWhitelisted;
    private Method addWhitelist;
    private Method removeWhitelist;
    private AppOpsManager appOpsManager;
    private Method strOpToOp;
    private Method setMode;

    RootSystemSettingsAdapter(Context context) throws Exception {
        this.context = Objects.requireNonNull(context, "context");
        moduleUid = context.getPackageManager().getPackageUid(MODULE_PACKAGE, 0);
        HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/os/ServiceManager",
                "Landroid/os/IDeviceIdleController",
                "Landroid/app/AppOpsManager");
    }

    @Override
    public synchronized int query(OptimizationItem item) {
        try {
            return switch (item) {
                case DOZE_WHITELIST -> queryDozeWhitelist();
                case RUN_IN_BACKGROUND, RUN_ANY_IN_BACKGROUND -> queryAppOp(item);
            };
        } catch (Throwable failure) {
            return UNSUPPORTED;
        }
    }

    @Override
    public synchronized boolean set(OptimizationItem item, int value) {
        try {
            return switch (item) {
                case DOZE_WHITELIST -> setDozeWhitelist(value);
                case RUN_IN_BACKGROUND, RUN_ANY_IN_BACKGROUND -> setAppOp(item, value);
            };
        } catch (Throwable failure) {
            return false;
        }
    }

    private int queryDozeWhitelist() throws Exception {
        ensureDeviceIdleController();
        return Boolean.TRUE.equals(isWhitelisted.invoke(deviceIdleController, MODULE_PACKAGE))
                ? 1
                : 0;
    }

    private boolean setDozeWhitelist(int value) throws Exception {
        if (value != 0 && value != 1) {
            return false;
        }
        ensureDeviceIdleController();
        if (value == 1) {
            addWhitelist.invoke(deviceIdleController, MODULE_PACKAGE);
        } else {
            removeWhitelist.invoke(deviceIdleController, MODULE_PACKAGE);
        }
        return queryDozeWhitelist() == value;
    }

    private int queryAppOp(OptimizationItem item) throws Exception {
        ensureAppOps();
        return appOpsManager.unsafeCheckOpRawNoThrow(
                appOpName(item), moduleUid, MODULE_PACKAGE);
    }

    private boolean setAppOp(OptimizationItem item, int value) throws Exception {
        if (value < 0 || value == RootSystemSettings.UNSUPPORTED) {
            return false;
        }
        ensureAppOps();
        int operation = (int) strOpToOp.invoke(null, appOpName(item));
        setMode.invoke(appOpsManager, operation, moduleUid, MODULE_PACKAGE, value);
        return queryAppOp(item) == value;
    }

    private void ensureDeviceIdleController() throws Exception {
        if (deviceIdleController != null) {
            return;
        }
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getDeclaredMethod("getService", String.class);
        IBinder binder = (IBinder) getService.invoke(null, "deviceidle");
        if (binder == null) {
            throw new IllegalStateException("deviceidle Binder is unavailable");
        }
        Class<?> stub = Class.forName("android.os.IDeviceIdleController$Stub");
        Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
        deviceIdleController = asInterface.invoke(null, binder);
        if (deviceIdleController == null) {
            throw new IllegalStateException("IDeviceIdleController is unavailable");
        }
        Class<?> controllerClass = Class.forName("android.os.IDeviceIdleController");
        isWhitelisted = controllerClass.getMethod("isPowerSaveWhitelistApp", String.class);
        addWhitelist = controllerClass.getMethod("addPowerSaveWhitelistApp", String.class);
        removeWhitelist = controllerClass.getMethod("removePowerSaveWhitelistApp", String.class);
    }

    private void ensureAppOps() throws Exception {
        if (appOpsManager != null) {
            return;
        }
        appOpsManager = context.getSystemService(AppOpsManager.class);
        if (appOpsManager == null) {
            throw new IllegalStateException("AppOpsManager is unavailable");
        }
        strOpToOp = AppOpsManager.class.getDeclaredMethod("strOpToOp", String.class);
        strOpToOp.setAccessible(true);
        setMode = AppOpsManager.class.getDeclaredMethod(
                "setMode", int.class, int.class, String.class, int.class);
        setMode.setAccessible(true);
    }

    private static String appOpName(OptimizationItem item) {
        return switch (item) {
            case RUN_IN_BACKGROUND -> OP_RUN_IN_BACKGROUND;
            case RUN_ANY_IN_BACKGROUND -> OP_RUN_ANY_IN_BACKGROUND;
            case DOZE_WHITELIST -> throw new IllegalArgumentException("Not an AppOp item");
        };
    }
}
