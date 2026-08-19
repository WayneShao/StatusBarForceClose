package com.wayne.statusbarforceclose;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

final class BridgeRequestDispatcher {
    private final int moduleUid;
    private final BridgeStateRepository repository;
    private final RootOperations rootOperations;
    private final OptimizationOperations optimizationOperations;
    private final SystemUiSessionRegistry sessionRegistry;
    private final BridgeObserverRegistry<ForceStopConfiguration> systemUiCallbacks =
            new BridgeObserverRegistry<>();
    private final BridgeObserverRegistry<BridgeRuntimeState> runtimeObservers =
            new BridgeObserverRegistry<>();
    private final Set<String> consumedRecoveryWindows = new HashSet<>();
    private final AtomicLong callbackIds = new AtomicLong();
    private BridgeStateSnapshot state;

    BridgeRequestDispatcher(
            int moduleUid,
            BridgeStateRepository repository,
            RootOperations rootOperations,
            OptimizationOperations optimizationOperations,
            SystemUiSessionRegistry sessionRegistry) {
        if (moduleUid < 0) {
            throw new IllegalArgumentException("moduleUid must not be negative");
        }
        this.moduleUid = moduleUid;
        this.repository = Objects.requireNonNull(repository, "repository");
        this.rootOperations = Objects.requireNonNull(rootOperations, "rootOperations");
        this.optimizationOperations = Objects.requireNonNull(
                optimizationOperations, "optimizationOperations");
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry");
        state = Objects.requireNonNull(repository.load(), "repository state");
    }

    synchronized SystemUiRegistration registerSystemUi(
            CallerIdentity caller,
            int protocol,
            String generation,
            SystemUiConfigurationCallback callback) {
        if (!isSystemUi(caller)) {
            return SystemUiRegistration.rejected(BridgeProtocol.Status.PERMISSION_REJECTED);
        }
        if (protocol != BridgeProtocol.VERSION) {
            return SystemUiRegistration.rejected(BridgeProtocol.Status.INCOMPATIBLE);
        }
        if (isBlank(generation) || callback == null) {
            return SystemUiRegistration.rejected(BridgeProtocol.Status.INVALID_ARGUMENT);
        }
        refreshState();
        String callbackId = "systemui-callback-" + callbackIds.incrementAndGet();
        SystemUiSessionRegistry.Registration registration = sessionRegistry.register(
                protocol, caller.uid(), generation, callbackId);
        if (!registration.accepted()) {
            return SystemUiRegistration.rejected(BridgeProtocol.Status.INCOMPATIBLE);
        }
        if (registration.replacedCallbackId() != null) {
            systemUiCallbacks.remove(registration.replacedCallbackId());
        }
        systemUiCallbacks.register(callbackId, callback::onConfiguration);

        RootAttemptJournal.TriggerResult trigger = state.rootJournal()
                .consumeSystemUiGeneration(generation);
        if (trigger.journal() != state.rootJournal()) {
            BridgeStateSnapshot updated = new BridgeStateSnapshot(
                    state.configuration(), trigger.journal(), state.optimizationJournal());
            if (!repository.commit(updated)) {
                systemUiCallbacks.remove(callbackId);
                sessionRegistry.unregister(registration.sessionToken());
                return SystemUiRegistration.rejected(BridgeProtocol.Status.STORAGE_ERROR);
            }
            state = updated;
        }
        try {
            callback.onConfiguration(state.configuration());
        } catch (RuntimeException failure) {
            systemUiCallbacks.remove(callbackId);
            sessionRegistry.unregister(registration.sessionToken());
            return SystemUiRegistration.rejected(BridgeProtocol.Status.OPERATION_FAILED);
        }
        if (trigger.permitsAttempt()) {
            rootOperations.requestFreshConnection();
        }
        notifyRuntimeObservers();
        return new SystemUiRegistration(
                BridgeProtocol.Status.OK,
                registration.sessionToken(),
                callbackId,
                registration.replacedCallbackId(),
                registration.newGeneration(),
                state.configuration());
    }

    synchronized boolean onSystemUiCallbackDied(String callbackId) {
        boolean removed = systemUiCallbacks.remove(callbackId);
        boolean invalidated = sessionRegistry.onCallbackDied(callbackId);
        if (removed || invalidated) {
            notifyRuntimeObservers();
        }
        return invalidated;
    }

    synchronized BridgeProtocol.Status unregisterSystemUi(
            CallerIdentity caller,
            int protocol,
            String generation,
            String sessionToken,
            String callbackId) {
        if (protocol != BridgeProtocol.VERSION) {
            return BridgeProtocol.Status.INCOMPATIBLE;
        }
        if (!authorizedSystemUi(caller, protocol, generation, sessionToken)) {
            return BridgeProtocol.Status.PERMISSION_REJECTED;
        }
        sessionRegistry.unregister(sessionToken);
        systemUiCallbacks.remove(callbackId);
        notifyRuntimeObservers();
        return BridgeProtocol.Status.OK;
    }

    synchronized ForceStopConfiguration getConfiguration(
            CallerIdentity caller,
            int protocol,
            String generation,
            String sessionToken) {
        if (protocol != BridgeProtocol.VERSION
                || !authorizedSystemUi(caller, protocol, generation, sessionToken)) {
            return ForceStopConfiguration.unconfigured();
        }
        return state.configuration();
    }

    synchronized BackendResult forceStopRoot(
            CallerIdentity caller,
            int protocol,
            String generation,
            String sessionToken,
            String packageName,
            int userId,
            boolean waitForConnection) {
        if (protocol != BridgeProtocol.VERSION) {
            return rootResult(BackendStatus.UNSUPPORTED);
        }
        if (!authorizedSystemUi(caller, protocol, generation, sessionToken)) {
            return rootResult(BackendStatus.PERMISSION_REJECTED);
        }
        if (isBlank(packageName) || userId < 0) {
            return rootResult(BackendStatus.OPERATION_FAILED);
        }
        return rootOperations.forceStop(packageName, userId, waitForConnection);
    }

    synchronized BridgeProtocol.Status updateConfiguration(
            CallerIdentity caller,
            int protocol,
            ExecutionMode mode,
            boolean backgroundOptimizationEnabled) {
        BridgeProtocol.Status authorization = authorizeModule(caller, protocol);
        if (authorization != BridgeProtocol.Status.OK) {
            return authorization;
        }
        if (mode == null) {
            return BridgeProtocol.Status.INVALID_ARGUMENT;
        }
        refreshState();
        boolean optimizationChanged = state.configuration().backgroundOptimizationEnabled()
                != backgroundOptimizationEnabled;
        ForceStopConfiguration updatedConfiguration = state.configuration().update(
                mode, backgroundOptimizationEnabled);
        BridgeStateSnapshot updated = new BridgeStateSnapshot(
                updatedConfiguration, state.rootJournal(), state.optimizationJournal());
        if (!repository.commit(updated)) {
            return BridgeProtocol.Status.STORAGE_ERROR;
        }
        state = updated;
        systemUiCallbacks.notifyObservers(updatedConfiguration);
        notifyRuntimeObservers();
        if (!optimizationChanged) {
            return BridgeProtocol.Status.OK;
        }
        boolean optimizationSuccess = optimizationOperations.setEnabled(
                backgroundOptimizationEnabled);
        refreshState();
        notifyRuntimeObservers();
        return optimizationSuccess
                ? BridgeProtocol.Status.OK
                : BridgeProtocol.Status.OPERATION_FAILED;
    }

    synchronized BridgeProtocol.Status requestRootForSettings(
            CallerIdentity caller, int protocol, String openToken) {
        BridgeProtocol.Status authorization = authorizeModule(caller, protocol);
        if (authorization != BridgeProtocol.Status.OK) {
            return authorization;
        }
        if (isBlank(openToken)) {
            return BridgeProtocol.Status.INVALID_ARGUMENT;
        }
        refreshState();
        RootAttemptJournal.TriggerResult trigger = state.rootJournal()
                .consumeSettingsOpen(openToken);
        if (trigger.journal() == state.rootJournal()) {
            return BridgeProtocol.Status.OK;
        }
        BridgeStateSnapshot updated = new BridgeStateSnapshot(
                state.configuration(), trigger.journal(), state.optimizationJournal());
        if (!repository.commit(updated)) {
            return BridgeProtocol.Status.STORAGE_ERROR;
        }
        state = updated;
        if (trigger.permitsAttempt()) {
            rootOperations.requestFreshConnection();
        }
        notifyRuntimeObservers();
        return trigger.permitsAttempt()
                ? BridgeProtocol.Status.OK
                : BridgeProtocol.Status.INCOMPATIBLE;
    }

    synchronized BridgeProtocol.Status requestRecovery(
            CallerIdentity caller,
            int protocol,
            String generation,
            String sessionToken,
            RecoveryReason reason,
            long windowId) {
        if (protocol != BridgeProtocol.VERSION) {
            return BridgeProtocol.Status.INCOMPATIBLE;
        }
        if (!authorizedSystemUi(caller, protocol, generation, sessionToken)) {
            return BridgeProtocol.Status.PERMISSION_REJECTED;
        }
        if (reason == null || windowId <= 0L) {
            return BridgeProtocol.Status.INVALID_ARGUMENT;
        }
        String recoveryKey = generation + ':' + windowId;
        if (consumedRecoveryWindows.add(recoveryKey)) {
            rootOperations.requestRecovery(reason, windowId);
        }
        return BridgeProtocol.Status.OK;
    }

    synchronized BridgeProtocol.Status registerRuntimeObserver(
            CallerIdentity caller,
            int protocol,
            String observerId,
            RuntimeStateObserver observer) {
        BridgeProtocol.Status authorization = authorizeModule(caller, protocol);
        if (authorization != BridgeProtocol.Status.OK) {
            return authorization;
        }
        if (isBlank(observerId) || observer == null) {
            return BridgeProtocol.Status.INVALID_ARGUMENT;
        }
        runtimeObservers.register(observerId, observer::onRuntimeState);
        observer.onRuntimeState(runtimeState());
        return BridgeProtocol.Status.OK;
    }

    synchronized boolean unregisterRuntimeObserver(
            CallerIdentity caller, int protocol, String observerId) {
        return authorizeModule(caller, protocol) == BridgeProtocol.Status.OK
                && runtimeObservers.remove(observerId);
    }

    synchronized BridgeProtocol.Status setBackgroundOptimization(
            CallerIdentity caller, int protocol, boolean enabled) {
        return updateConfiguration(
                caller, protocol, state.configuration().executionMode(), enabled);
    }

    synchronized ForceStopConfiguration configuration() {
        return state.configuration();
    }

    synchronized BridgeRuntimeState getRuntimeState(CallerIdentity caller, int protocol) {
        return authorizeModule(caller, protocol) == BridgeProtocol.Status.OK
                ? runtimeState()
                : rejectedRuntimeState();
    }

    synchronized BridgeRuntimeState getSystemUiRuntimeState(
            CallerIdentity caller,
            int protocol,
            String generation,
            String sessionToken) {
        return protocol == BridgeProtocol.VERSION
                && authorizedSystemUi(caller, protocol, generation, sessionToken)
                ? runtimeState()
                : rejectedRuntimeState();
    }

    synchronized void recordRootTerminal(RootConnectionState terminalState) {
        refreshState();
        RootAttemptJournal updatedJournal = state.rootJournal().recordTerminal(terminalState);
        BridgeStateSnapshot updated = new BridgeStateSnapshot(
                state.configuration(), updatedJournal, state.optimizationJournal());
        if (!repository.commit(updated)) {
            throw new IllegalStateException("Failed to persist terminal root state");
        }
        state = updated;
    }

    synchronized void onRootStateChanged() {
        notifyRuntimeObservers();
    }

    private boolean authorizedSystemUi(
            CallerIdentity caller, int protocol, String generation, String sessionToken) {
        return isSystemUi(caller)
                && sessionRegistry.isAuthorized(
                        protocol, caller.uid(), generation, sessionToken);
    }

    private BridgeProtocol.Status authorizeModule(CallerIdentity caller, int protocol) {
        if (protocol != BridgeProtocol.VERSION) {
            return BridgeProtocol.Status.INCOMPATIBLE;
        }
        return caller != null && RootBridgeCallerPolicy.isModuleUid(caller.uid(), moduleUid)
                ? BridgeProtocol.Status.OK
                : BridgeProtocol.Status.PERMISSION_REJECTED;
    }

    private static boolean isSystemUi(CallerIdentity caller) {
        return caller != null && RootBridgeCallerPolicy.isSystemUi(caller.packages());
    }

    private BridgeRuntimeState runtimeState() {
        return new BridgeRuntimeState(
                rootOperations.state(),
                sessionRegistry.hasActiveSession(),
                state.configuration().backgroundOptimizationEnabled());
    }

    private static BridgeRuntimeState rejectedRuntimeState() {
        return new BridgeRuntimeState(RootConnectionState.INCOMPATIBLE, false, false);
    }

    private void notifyRuntimeObservers() {
        runtimeObservers.notifyObservers(runtimeState());
    }

    private static BackendResult rootResult(BackendStatus status) {
        return new BackendResult(BackendKind.ROOT, status, 0L);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void refreshState() {
        state = Objects.requireNonNull(repository.load(), "repository state");
    }
}
