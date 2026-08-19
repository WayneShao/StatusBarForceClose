package com.wayne.statusbarforceclose;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
            new ReconnectBackoff(1_000L, 1_000L, 1);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService recoveryWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "StatusBarForceClose-Recovery");
        thread.setDaemon(true);
        return thread;
    });
    private final RecoveryEventController recoveryController;

    private Context context;
    private IForceStopBridge bridge;
    private IBinder linkedBinder;
    private IBinder.DeathRecipient deathRecipient;
    private boolean bound;
    private boolean reconnectScheduled;
    private RecoveryEventRegistrar recoveryEventRegistrar;
    private volatile RootConnectionState lastRootState = RootConnectionState.DISCONNECTED;
    private volatile boolean rootStateFresh;
    private RecoveryRequest pendingRecovery;

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

        @Override
        public void onRuntimeStateChanged(BridgeRuntimeStateParcel parcel) {
            if (parcel == null) {
                logger.warn("systemui_runtime_state_ignored", "reason=null-parcel");
                return;
            }
            try {
                BridgeRuntimeState state = parcel.toModel();
                if (!state.systemUiConnected()) {
                    logger.warn("systemui_runtime_state_ignored", "reason=session-not-connected");
                    return;
                }
                lastRootState = state.rootState();
                rootStateFresh = true;
                logger.info("systemui_runtime_state_received", "root=" + state.rootState());
                if (!state.rootState().isRecoverable()) {
                    clearPendingRecovery();
                } else {
                    recoveryWorker.execute(SystemUiRuntime.this::flushPendingRecovery);
                }
            } catch (RuntimeException invalid) {
                logger.error("systemui_runtime_state_invalid", "rootState="
                        + parcel.rootState, invalid);
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            beginBridgeRegistration(name, service);
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
        recoveryController = new RecoveryEventController(
                android.os.SystemClock::elapsedRealtime,
                (deadline, action) -> mainHandler.postDelayed(
                        action,
                        Math.max(0L, deadline - android.os.SystemClock.elapsedRealtime())),
                this::recoveryRootState,
                this::requestRecovery,
                new RecoveryGate(5_000L));
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
        runOnMain(() -> {
            installRecoveryEventsIfNeeded();
            bindIfNeeded();
        });
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
                    BridgeRuntimeState model = state.toModel();
                    if (model.systemUiConnected()) {
                        rootState = model.rootState();
                        lastRootState = rootState;
                        rootStateFresh = true;
                    } else {
                        notifyConnectionFailure(activeBridge);
                    }
                }
            } catch (Throwable failure) {
                logger.error("systemui_runtime_state_failed", "generation="
                        + connection.generation(), failure);
                notifyConnectionFailure(activeBridge);
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
            notifyConnectionFailure(activeBridge);
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

    private void beginBridgeRegistration(ComponentName name, IBinder service) {
        if (!connection.onConnected()) {
            logger.warn("systemui_bridge_stale_connection", "component=" + name);
            return;
        }
        IForceStopBridge candidate = IForceStopBridge.Stub.asInterface(service);
        if (candidate == null) {
            handleDisconnected("missing-interface", true);
            return;
        }
        rootStateFresh = false;
        recoveryWorker.execute(() -> registerConnectedBridge(name, service, candidate));
    }

    private void registerConnectedBridge(
            ComponentName name,
            IBinder service,
            IForceStopBridge candidate) {
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
                mainHandler.post(() -> handleDisconnected("registration-rejected", true));
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
            refreshRootStateAndFlush(candidate);
            logger.info("systemui_bridge_ready", "component=" + name
                    + " generation=" + connection.generation()
                    + " newGeneration=" + registration.newGeneration);
        } catch (Throwable failure) {
            logger.error("systemui_bridge_registration_failed", "component=" + name, failure);
            mainHandler.post(() -> handleDisconnected("registration-failed", true));
        }
    }

    private void linkToBridgeDeath(IBinder binder) throws RemoteException {
        IBinder.DeathRecipient recipient = () -> mainHandler.post(
                () -> handleBinderDeath(binder));
        binder.linkToDeath(recipient, 0);
        synchronized (this) {
            unlinkDeathRecipientLocked();
            linkedBinder = binder;
            deathRecipient = recipient;
        }
    }

    private void handleBinderDeath(IBinder deadBinder) {
        synchronized (this) {
            if (linkedBinder != deadBinder) {
                logger.info("systemui_bridge_stale_death_ignored", "generation="
                        + connection.generation());
                return;
            }
        }
        handleDisconnected("binder-death", true);
    }

    private void notifyConnectionFailure(IForceStopBridge expectedBridge) {
        mainHandler.post(() -> {
            synchronized (SystemUiRuntime.this) {
                if (bridge != expectedBridge) {
                    logger.info("systemui_bridge_stale_failure_ignored", "generation="
                            + connection.generation());
                    return;
                }
            }
            handleDisconnected("remote-call-failed", true);
        });
    }

    private void installRecoveryEventsIfNeeded() {
        RecoveryEventRegistrar registrar;
        synchronized (this) {
            if (context == null) {
                return;
            }
            if (recoveryEventRegistrar == null) {
                recoveryEventRegistrar = new RecoveryEventRegistrar(
                        context, recoveryController, logger);
            }
            registrar = recoveryEventRegistrar;
        }
        registrar.installOnce();
    }

    private RootConnectionState recoveryRootState() {
        SystemUiBridgeState bridgeState = connection.snapshot().state();
        if (lastRootState.isTerminal()) {
            return lastRootState;
        }
        if (bridgeState == SystemUiBridgeState.DISCONNECTED) {
            return RootConnectionState.DISCONNECTED;
        }
        if (bridgeState != SystemUiBridgeState.READY || !rootStateFresh) {
            return RootConnectionState.CONNECTING;
        }
        return lastRootState;
    }

    private void requestRecovery(RecoveryReason reason, long windowId) {
        synchronized (this) {
            pendingRecovery = new RecoveryRequest(reason, windowId);
        }
        logger.info("recovery_signal_accepted", "reason=" + reason
                + " windowId=" + windowId);
        runOnMain(this::requestBridgeReconnectFromSignal);
        recoveryWorker.execute(this::flushPendingRecovery);
    }

    private void requestBridgeReconnectFromSignal() {
        synchronized (this) {
            if (connection.snapshot().state() == SystemUiBridgeState.DISCONNECTED
                    && !reconnectScheduled) {
                reconnectBackoff.reset();
            }
        }
        bindIfNeeded();
    }

    private void refreshRootStateAndFlush(IForceStopBridge expectedBridge) {
        SystemUiConnectionSnapshot snapshot = connection.snapshot();
        if (snapshot.state() != SystemUiBridgeState.READY) {
            return;
        }
        try {
            BridgeRuntimeStateParcel parcel = expectedBridge.getSystemUiRuntimeState(
                    BridgeProtocol.VERSION,
                    connection.generation(),
                    snapshot.sessionToken());
            BridgeRuntimeState model = parcel == null ? null : parcel.toModel();
            if (model == null || !model.systemUiConnected()) {
                notifyConnectionFailure(expectedBridge);
                return;
            }
            lastRootState = model.rootState();
            rootStateFresh = true;
            logger.info("recovery_root_state_refreshed", "state=" + model.rootState());
            if (model.rootState().isRecoverable()) {
                flushPendingRecovery();
            } else {
                clearPendingRecovery();
            }
        } catch (Throwable failure) {
            logger.error("recovery_root_state_failed", "generation="
                    + connection.generation(), failure);
            notifyConnectionFailure(expectedBridge);
        }
    }

    private void flushPendingRecovery() {
        RecoveryRequest request;
        IForceStopBridge activeBridge;
        SystemUiConnectionSnapshot snapshot = connection.snapshot();
        synchronized (this) {
            request = pendingRecovery;
            activeBridge = bridge;
        }
        if (request == null
                || snapshot.state() != SystemUiBridgeState.READY
                || activeBridge == null
                || !rootStateFresh
                || !lastRootState.isRecoverable()) {
            return;
        }
        try {
            int statusOrdinal = activeBridge.requestRecovery(
                    BridgeProtocol.VERSION,
                    connection.generation(),
                    snapshot.sessionToken(),
                    request.reason().ordinal(),
                    request.windowId());
            BridgeProtocol.Status status = protocolStatus(statusOrdinal);
            logger.info("recovery_request_result", "reason=" + request.reason()
                    + " windowId=" + request.windowId() + " status=" + status);
            if (status == BridgeProtocol.Status.OK) {
                synchronized (this) {
                    if (request.equals(pendingRecovery)) {
                        pendingRecovery = null;
                    }
                }
            } else if (status != BridgeProtocol.Status.OPERATION_FAILED) {
                clearPendingRecovery();
            }
        } catch (Throwable failure) {
            logger.error("recovery_request_failed", "reason=" + request.reason()
                    + " windowId=" + request.windowId(), failure);
            notifyConnectionFailure(activeBridge);
        }
    }

    private synchronized void clearPendingRecovery() {
        pendingRecovery = null;
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
        rootStateFresh = false;
        if (!lastRootState.isTerminal()) {
            lastRootState = RootConnectionState.DISCONNECTED;
        }
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
