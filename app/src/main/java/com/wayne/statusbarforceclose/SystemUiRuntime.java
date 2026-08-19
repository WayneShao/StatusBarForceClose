package com.wayne.statusbarforceclose;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

final class SystemUiRuntime {
    private static final String MODULE_PACKAGE = "com.wayne.statusbarforceclose";
    private final DiagnosticLogger logger;
    private final ForceStopMethod localSystemUiMethod;
    private final SystemUiCapabilityClassifier capabilityClassifier;
    private final SystemUiRuntimePolicy policy = new SystemUiRuntimePolicy();
    private final SystemUiConnectionStateMachine connection =
            new SystemUiConnectionStateMachine();
    private final ConfigurationDelivery configurationDelivery = new ConfigurationDelivery();
    private final ReconnectBackoff reconnectBackoff =
            new ReconnectBackoff(1_000L, 16_000L, 5);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context context;
    private IForceStopBridge bridge;
    private IBinder linkedBinder;
    private IBinder.DeathRecipient deathRecipient;
    private boolean bound;
    private boolean reconnectScheduled;

    private final ISystemUiCallback configurationCallback = new ISystemUiCallback.Stub() {
        @Override
        public void onConfigurationChanged(BridgeConfigurationParcel parcel) {
            if (parcel == null) {
                logger.warn("systemui_config_ignored", "reason=null-parcel");
                return;
            }
            try {
                ForceStopConfiguration accepted = configurationDelivery.onConfiguration(
                        parcel.toModel());
                logger.info("systemui_config_received", "revision=" + accepted.revision()
                        + " mode=" + accepted.executionMode());
            } catch (RuntimeException invalid) {
                logger.error("systemui_config_invalid", "revision=" + parcel.revision, invalid);
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            registerConnectedBridge(name, service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            handleDisconnected("service-disconnected", true);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            handleDisconnected("binding-died", true);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            handleDisconnected("null-binding", true);
        }
    };

    SystemUiRuntime(DiagnosticLogger logger, ForceStopMethod localSystemUiMethod) {
        this.logger = logger;
        this.localSystemUiMethod = localSystemUiMethod;
        capabilityClassifier = new SystemUiCapabilityClassifier(this::isLocalSystemUiAvailable);
    }

    void ensureStarted(Context candidate) {
        if (candidate == null) {
            logger.warn("systemui_runtime_start_skipped", "reason=null-context");
            return;
        }
        Context applicationContext = candidate.getApplicationContext();
        synchronized (this) {
            if (context == null) {
                context = applicationContext == null ? candidate : applicationContext;
                logger.info("systemui_runtime_start", "generation=" + connection.generation()
                        + " capability=" + capabilityClassifier.capability());
            }
            if (connection.snapshot().state() == SystemUiBridgeState.DISCONNECTED
                    && !reconnectScheduled) {
                reconnectBackoff.reset();
            }
        }
        runOnMain(this::bindIfNeeded);
    }

    ForceStopResult forceStop(String packageName, int userId) {
        ForceStopConfiguration configuration = configurationDelivery.onDisconnected();
        SystemUiConnectionSnapshot snapshot = connection.snapshot();
        IForceStopBridge activeBridge;
        synchronized (this) {
            activeBridge = bridge;
        }

        RootConnectionState rootState = RootConnectionState.INCOMPATIBLE;
        if (snapshot.state() == SystemUiBridgeState.READY && activeBridge != null) {
            try {
                BridgeRuntimeStateParcel state = activeBridge.getSystemUiRuntimeState(
                        BridgeProtocol.VERSION,
                        connection.generation(),
                        snapshot.sessionToken());
                if (state != null) {
                    rootState = state.toModel().rootState();
                }
            } catch (Throwable failure) {
                logger.error("systemui_runtime_state_failed", "generation="
                        + connection.generation(), failure);
                notifyConnectionFailure();
            }
        }

        ExecutionPlan plan = policy.select(
                configuration, rootState, capabilityClassifier.capability());
        logger.info("systemui_execution_plan", "mode=" + configuration.executionMode()
                + " root=" + rootState + " capability=" + capabilityClassifier.capability()
                + " steps=" + plan.steps());
        ForceStopMethod rootMethod = (targetPackage, targetUser, waitForConnection) ->
                forceStopRoot(
                        activeBridge,
                        snapshot,
                        targetPackage,
                        targetUser,
                        waitForConnection);
        ForceStopMethod classifiedSystemUiMethod =
                (targetPackage, targetUser, waitForConnection) -> {
                    BackendResult result = localSystemUiMethod.forceStop(
                            targetPackage, targetUser, false);
                    capabilityClassifier.record(result.status());
                    return result;
                };
        return new ForceStopCoordinator(rootMethod, classifiedSystemUiMethod)
                .forceStop(plan, packageName, userId);
    }

    private BackendResult forceStopRoot(
            IForceStopBridge activeBridge,
            SystemUiConnectionSnapshot snapshot,
            String packageName,
            int userId,
            boolean waitForConnection) {
        long startedAt = android.os.SystemClock.elapsedRealtime();
        if (snapshot.state() != SystemUiBridgeState.READY || activeBridge == null) {
            return rootResult(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
        }
        try {
            BackendResultParcel parcel = activeBridge.forceStopRoot(
                    BridgeProtocol.VERSION,
                    connection.generation(),
                    snapshot.sessionToken(),
                    packageName,
                    userId,
                    waitForConnection);
            if (parcel == null) {
                return rootResult(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
            }
            BackendResult result = parcel.toModel();
            return result.backend() == BackendKind.ROOT
                    ? result
                    : rootResult(BackendStatus.OPERATION_FAILED, startedAt);
        } catch (Throwable failure) {
            logger.error("systemui_root_call_failed", "package=" + packageName
                    + " user=" + userId, failure);
            notifyConnectionFailure();
            return rootResult(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
        }
    }

    private void bindIfNeeded() {
        Context activeContext;
        synchronized (this) {
            activeContext = context;
            reconnectScheduled = false;
        }
        if (activeContext == null || !connection.startBinding()) {
            return;
        }
        Intent intent = new Intent().setComponent(new ComponentName(
                MODULE_PACKAGE, ForceStopBridgeService.class.getName()));
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            boolean accepted = activeContext.bindService(
                    intent, serviceConnection, Context.BIND_AUTO_CREATE);
            synchronized (this) {
                bound = accepted;
            }
            logger.info("systemui_bridge_bind", "accepted=" + accepted
                    + " generation=" + connection.generation());
            if (!accepted) {
                connection.onBindFailed();
                scheduleReconnect();
            }
        } catch (Throwable failure) {
            connection.onBindFailed();
            logger.error("systemui_bridge_bind_failed", "component=" + intent.getComponent(),
                    failure);
            scheduleReconnect();
        }
    }

    private void registerConnectedBridge(ComponentName name, IBinder service) {
        if (!connection.onConnected()) {
            logger.warn("systemui_bridge_stale_connection", "component=" + name);
            return;
        }
        IForceStopBridge candidate = IForceStopBridge.Stub.asInterface(service);
        if (candidate == null) {
            handleDisconnected("missing-interface", true);
            return;
        }
        try {
            SystemUiRegistrationParcel registration = candidate.registerSystemUi(
                    BridgeProtocol.VERSION, connection.generation(), configurationCallback);
            BridgeProtocol.Status status = registration == null
                    ? BridgeProtocol.Status.OPERATION_FAILED
                    : protocolStatus(registration.status);
            if (status != BridgeProtocol.Status.OK
                    || !connection.onRegistered(
                            connection.generation(), registration.sessionToken)) {
                logger.warn("systemui_bridge_registration_rejected", "status=" + status);
                handleDisconnected("registration-rejected", true);
                return;
            }
            if (registration.configuration != null) {
                configurationDelivery.onConfiguration(registration.configuration.toModel());
            }
            linkToBridgeDeath(service);
            synchronized (this) {
                bridge = candidate;
            }
            reconnectBackoff.reset();
            logger.info("systemui_bridge_ready", "component=" + name
                    + " generation=" + connection.generation()
                    + " newGeneration=" + registration.newGeneration);
        } catch (Throwable failure) {
            logger.error("systemui_bridge_registration_failed", "component=" + name, failure);
            handleDisconnected("registration-failed", true);
        }
    }

    private void linkToBridgeDeath(IBinder binder) throws RemoteException {
        IBinder.DeathRecipient recipient = () -> mainHandler.post(
                () -> handleDisconnected("binder-death", true));
        binder.linkToDeath(recipient, 0);
        synchronized (this) {
            unlinkDeathRecipientLocked();
            linkedBinder = binder;
            deathRecipient = recipient;
        }
    }

    private void notifyConnectionFailure() {
        mainHandler.post(() -> handleDisconnected("remote-call-failed", true));
    }

    private synchronized boolean isLocalSystemUiAvailable() {
        return BinderForceStopMethod.isAvailableTo(context);
    }

    private void handleDisconnected(String reason, boolean reconnect) {
        Context activeContext;
        boolean shouldUnbind;
        synchronized (this) {
            bridge = null;
            unlinkDeathRecipientLocked();
            activeContext = context;
            shouldUnbind = bound;
            bound = false;
        }
        connection.onDisconnected();
        configurationDelivery.onDisconnected();
        if (shouldUnbind && activeContext != null) {
            try {
                activeContext.unbindService(serviceConnection);
            } catch (IllegalArgumentException ignored) {
                logger.warn("systemui_bridge_unbind_ignored", "reason=" + reason);
            }
        }
        logger.warn("systemui_bridge_disconnected", "reason=" + reason
                + " reconnect=" + reconnect);
        if (reconnect) {
            scheduleReconnect();
        }
    }

    private synchronized void unlinkDeathRecipientLocked() {
        if (linkedBinder != null && deathRecipient != null) {
            linkedBinder.unlinkToDeath(deathRecipient, 0);
        }
        linkedBinder = null;
        deathRecipient = null;
    }

    private void scheduleReconnect() {
        Long delay;
        synchronized (this) {
            if (context == null || reconnectScheduled) {
                return;
            }
            delay = reconnectBackoff.nextDelay();
            if (delay == null) {
                logger.warn("systemui_bridge_reconnect_exhausted", "generation="
                        + connection.generation());
                return;
            }
            reconnectScheduled = true;
        }
        logger.info("systemui_bridge_reconnect_scheduled", "delayMs=" + delay);
        mainHandler.postDelayed(this::bindIfNeeded, delay);
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    private static BridgeProtocol.Status protocolStatus(int ordinal) {
        BridgeProtocol.Status[] values = BridgeProtocol.Status.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : BridgeProtocol.Status.INCOMPATIBLE;
    }

    private static BackendResult rootResult(BackendStatus status, long startedAt) {
        return new BackendResult(
                BackendKind.ROOT,
                status,
                android.os.SystemClock.elapsedRealtime() - startedAt);
    }
}
