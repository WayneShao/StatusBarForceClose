package com.wayne.statusbarforceclose;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.topjohnwu.superuser.ipc.RootService;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ForceStopBridgeService extends Service {
    private static final String TAG = "StatusBarForceClose";
    private static final long ROOT_CONNECTION_TIMEOUT_SECONDS = 10L;

    private final Object rootLock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DiagnosticLogger logger = this::report;
    private volatile IRootActivityController rootController;
    private CompletableFuture<IRootActivityController> rootConnectionFuture;

    private final ServiceConnection rootConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IRootActivityController connected = IRootActivityController.Stub.asInterface(service);
            synchronized (rootLock) {
                rootController = connected;
                if (rootConnectionFuture != null) {
                    rootConnectionFuture.complete(connected);
                }
            }
            logger.info("libsu_root_connected", "component=" + name.flattenToShortString()
                    + " binder=" + (connected != null));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (rootLock) {
                rootController = null;
                rootConnectionFuture = null;
            }
            logger.warn("libsu_root_disconnected", "component=" + name.flattenToShortString());
        }

        @Override
        public void onNullBinding(ComponentName name) {
            synchronized (rootLock) {
                if (rootConnectionFuture != null) {
                    rootConnectionFuture.complete(null);
                }
            }
            logger.warn("libsu_root_null_binding", "component=" + name.flattenToShortString());
        }
    };

    private final IForceStopBridge.Stub bridge = new IForceStopBridge.Stub() {
        @Override
        public boolean forceStop(String packageName, int userId) {
            int callingUid = Binder.getCallingUid();
            String[] callingPackages = getPackageManager().getPackagesForUid(callingUid);
            logger.info("bridge_request_received", "callingUid=" + callingUid
                    + " packages=" + Arrays.toString(callingPackages)
                    + " target=" + packageName + " user=" + userId);
            if (!RootBridgeCallerPolicy.isAllowed(callingPackages)) {
                logger.warn("bridge_caller_rejected", "callingUid=" + callingUid
                        + " packages=" + Arrays.toString(callingPackages));
                throw new SecurityException("Force-stop bridge caller is not SystemUI");
            }
            if (packageName == null || packageName.isBlank() || userId < 0) {
                logger.warn("bridge_target_rejected", "package=" + packageName
                        + " user=" + userId);
                return false;
            }

            long identity = Binder.clearCallingIdentity();
            try {
                IRootActivityController controller = getRootController();
                if (controller == null) {
                    logger.warn("bridge_root_unavailable", "package=" + packageName
                            + " user=" + userId);
                    return false;
                }
                boolean success = controller.forceStop(packageName, userId);
                logger.log(
                        success ? Log.INFO : Log.WARN,
                        "bridge_root_result",
                        "package=" + packageName + " user=" + userId
                                + " success=" + success,
                        null);
                return success;
            } catch (Throwable throwable) {
                resetRootController();
                logger.error("bridge_root_exception", "package=" + packageName
                        + " user=" + userId, throwable);
                return false;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        logger.info("bridge_service_created", "pid=" + android.os.Process.myPid()
                + " uid=" + android.os.Process.myUid());
    }

    @Override
    public IBinder onBind(Intent intent) {
        logger.info("bridge_service_bound", "intent=" + intent);
        return bridge;
    }

    private IRootActivityController getRootController() throws Exception {
        IRootActivityController existing = rootController;
        if (existing != null && existing.asBinder().isBinderAlive()) {
            logger.info("libsu_root_reused", "binderAlive=true");
            return existing;
        }

        CompletableFuture<IRootActivityController> future;
        synchronized (rootLock) {
            existing = rootController;
            if (existing != null && existing.asBinder().isBinderAlive()) {
                return existing;
            }
            if (rootConnectionFuture == null) {
                rootConnectionFuture = new CompletableFuture<>();
                logger.info("libsu_root_bind_queued", "service="
                        + RootActivityManagerService.class.getName());
                mainHandler.post(this::bindRootServiceOnMainThread);
            }
            future = rootConnectionFuture;
        }
        return future.get(ROOT_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void bindRootServiceOnMainThread() {
        try {
            logger.info("libsu_root_bind_start", "thread="
                    + Thread.currentThread().getName());
            RootService.bind(
                    new Intent(this, RootActivityManagerService.class),
                    rootConnection);
        } catch (Throwable throwable) {
            synchronized (rootLock) {
                if (rootConnectionFuture != null) {
                    rootConnectionFuture.completeExceptionally(throwable);
                }
            }
            logger.error("libsu_root_bind_exception", "service="
                    + RootActivityManagerService.class.getName(), throwable);
        }
    }

    private void resetRootController() {
        synchronized (rootLock) {
            rootController = null;
            rootConnectionFuture = null;
        }
    }

    private void report(int priority, String event, String details, Throwable throwable) {
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
}
