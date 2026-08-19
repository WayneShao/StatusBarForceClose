package com.wayne.statusbarforceclose;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class RootBridgeForceStopMethod implements ForceStopMethod {
    private static final String MODULE_PACKAGE = "com.wayne.statusbarforceclose";
    private static final long CONNECTION_TIMEOUT_SECONDS = 10L;

    private final Context context;
    private final DiagnosticLogger logger;

    RootBridgeForceStopMethod(Context context, DiagnosticLogger logger) {
        this.context = context;
        this.logger = logger;
    }

    @Override
    public BackendResult forceStop(String packageName, int userId, boolean waitForConnection) {
        long startedAt = android.os.SystemClock.elapsedRealtime();
        if (packageName == null || packageName.isBlank() || userId < 0) {
            logger.warn("root_bridge_invalid_target", "package=" + packageName
                    + " user=" + userId);
            return result(BackendStatus.OPERATION_FAILED, startedAt);
        }

        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> remoteBinder = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                remoteBinder.set(service);
                logger.info("root_bridge_connected", "component=" + name.flattenToShortString());
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                logger.warn("root_bridge_disconnected", "component="
                        + name.flattenToShortString());
            }

            @Override
            public void onNullBinding(ComponentName name) {
                logger.warn("root_bridge_null_binding", "component="
                        + name.flattenToShortString());
                connected.countDown();
            }
        };

        boolean bound = false;
        try {
            Intent intent = new Intent().setComponent(new ComponentName(
                    MODULE_PACKAGE,
                    ForceStopBridgeService.class.getName()));
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            logger.info("root_bridge_bind_start", "package=" + packageName
                    + " user=" + userId + " component=" + intent.getComponent());
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            logger.info("root_bridge_bind_result", "accepted=" + bound);
            if (!bound || !connected.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("root_bridge_bind_timeout", "package=" + packageName
                        + " user=" + userId + " accepted=" + bound);
                return result(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
            }

            IForceStopBridge bridge = IForceStopBridge.Stub.asInterface(remoteBinder.get());
            if (bridge == null) {
                logger.warn("root_bridge_missing_binder", "package=" + packageName
                        + " user=" + userId);
                return result(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
            }
            boolean success = bridge.forceStop(packageName, userId);
            logger.log(
                    success ? android.util.Log.INFO : android.util.Log.WARN,
                    "root_bridge_result",
                    "package=" + packageName + " user=" + userId + " success=" + success,
                    null);
            return result(
                    success ? BackendStatus.SUCCESS : BackendStatus.OPERATION_FAILED,
                    startedAt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logger.error("root_bridge_interrupted", "package=" + packageName
                    + " user=" + userId, interrupted);
            return result(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
        } catch (Throwable throwable) {
            logger.error("root_bridge_exception", "package=" + packageName
                    + " user=" + userId, throwable);
            return result(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
        } finally {
            if (bound) {
                try {
                    context.unbindService(connection);
                    logger.info("root_bridge_unbound", "package=" + packageName
                            + " user=" + userId);
                } catch (Throwable throwable) {
                    logger.error("root_bridge_unbind_exception", "package=" + packageName
                            + " user=" + userId, throwable);
                }
            }
        }
    }

    private static BackendResult result(BackendStatus status, long startedAt) {
        return new BackendResult(
                BackendKind.ROOT,
                status,
                android.os.SystemClock.elapsedRealtime() - startedAt);
    }
}
