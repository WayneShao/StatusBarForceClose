package com.wayne.statusbarforceclose;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class ForceStopBridgeService extends Service {
    private static final String TAG = "StatusBarForceClose";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Object callbackLock = new Object();
    private final Map<ISystemUiCallback, CallbackRegistration> systemUiCallbacks =
            new IdentityHashMap<>();
    private final Map<IRuntimeObserver, IBinder.DeathRecipient> runtimeObservers =
            new IdentityHashMap<>();
    private final DiagnosticLogger logger = this::report;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BridgeRequestDispatcher dispatcher;
    private RootConnectionManager rootConnectionManager;

    private final IForceStopBridge.Stub bridge = new IForceStopBridge.Stub() {
        @Override
        public SystemUiRegistrationParcel registerSystemUi(
                int protocol, String generation, ISystemUiCallback callback) {
            if (callback == null) {
                return SystemUiRegistrationParcel.fromModel(SystemUiRegistration.rejected(
                        BridgeProtocol.Status.INVALID_ARGUMENT));
            }
            CallerIdentity caller = callerIdentity();
            SystemUiRegistration registration = dispatcher.registerSystemUi(
                    caller,
                    protocol,
                    generation,
                    configuration -> deliverConfiguration(callback, configuration));
            if (registration.status() == BridgeProtocol.Status.OK) {
                unlinkSystemUiCallbackById(registration.replacedCallbackId());
                linkSystemUiCallback(callback, registration);
            }
            logCall("register_systemui", caller, registration.status());
            return SystemUiRegistrationParcel.fromModel(registration);
        }

        @Override
        public int unregisterSystemUi(
                int protocol, String generation, String sessionToken) {
            CallerIdentity caller = callerIdentity();
            CallbackRegistration callback = findSystemUiRegistration(sessionToken);
            BridgeProtocol.Status status = dispatcher.unregisterSystemUi(
                    caller,
                    protocol,
                    generation,
                    sessionToken,
                    callback == null ? "missing-callback" : callback.callbackId);
            if (status == BridgeProtocol.Status.OK && callback != null) {
                unlinkSystemUiCallback(callback.callback);
            }
            logCall("unregister_systemui", caller, status);
            return status.ordinal();
        }

        @Override
        public BridgeConfigurationParcel getConfiguration(
                int protocol, String generation, String sessionToken) {
            return BridgeConfigurationParcel.fromModel(dispatcher.getConfiguration(
                    callerIdentity(), protocol, generation, sessionToken));
        }

        @Override
        public BackendResultParcel forceStopRoot(
                int protocol,
                String generation,
                String sessionToken,
                String packageName,
                int userId,
                boolean waitForConnection) {
            BackendResult result = dispatcher.forceStopRoot(
                    callerIdentity(),
                    protocol,
                    generation,
                    sessionToken,
                    packageName,
                    userId,
                    waitForConnection);
            return BackendResultParcel.fromModel(result);
        }

        @Override
        public int requestRecovery(
                int protocol,
                String generation,
                String sessionToken,
                int reason,
                long windowId) {
            RecoveryReason parsedReason = enumValue(RecoveryReason.values(), reason);
            return dispatcher.requestRecovery(
                    callerIdentity(),
                    protocol,
                    generation,
                    sessionToken,
                    parsedReason,
                    windowId).ordinal();
        }

        @Override
        public int requestRootForSettings(int protocol, String openToken) {
            return dispatcher.requestRootForSettings(
                    callerIdentity(), protocol, openToken).ordinal();
        }

        @Override
        public int updateConfiguration(
                int protocol, int executionMode, boolean backgroundOptimizationEnabled) {
            return dispatcher.updateConfiguration(
                    callerIdentity(),
                    protocol,
                    enumValue(ExecutionMode.values(), executionMode),
                    backgroundOptimizationEnabled).ordinal();
        }

        @Override
        public int setBackgroundOptimization(int protocol, boolean enabled) {
            return dispatcher.setBackgroundOptimization(
                    callerIdentity(), protocol, enabled).ordinal();
        }

        @Override
        public int registerRuntimeObserver(int protocol, IRuntimeObserver observer) {
            if (observer == null) {
                return BridgeProtocol.Status.INVALID_ARGUMENT.ordinal();
            }
            CallerIdentity caller = callerIdentity();
            String observerId = runtimeObserverId(observer);
            BridgeProtocol.Status status = dispatcher.registerRuntimeObserver(
                    caller,
                    protocol,
                    observerId,
                    state -> deliverRuntimeState(observer, state));
            if (status == BridgeProtocol.Status.OK) {
                linkRuntimeObserver(observer, observerId);
            }
            return status.ordinal();
        }

        @Override
        public boolean unregisterRuntimeObserver(int protocol, IRuntimeObserver observer) {
            if (observer == null) {
                return false;
            }
            boolean removed = dispatcher.unregisterRuntimeObserver(
                    callerIdentity(), protocol, runtimeObserverId(observer));
            if (removed) {
                unlinkRuntimeObserver(observer);
            }
            return removed;
        }

        @Override
        public BridgeRuntimeStateParcel getRuntimeState(int protocol) {
            return BridgeRuntimeStateParcel.fromModel(
                    dispatcher.getRuntimeState(callerIdentity(), protocol));
        }

        @Override
        public BridgeRuntimeStateParcel getSystemUiRuntimeState(
                int protocol, String generation, String sessionToken) {
            return BridgeRuntimeStateParcel.fromModel(dispatcher.getSystemUiRuntimeState(
                    callerIdentity(), protocol, generation, sessionToken));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        long versionCode;
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0)
                    .getLongVersionCode();
        } catch (Throwable failure) {
            versionCode = 0L;
            logger.error("bridge_version_lookup_failed", "package=" + getPackageName(), failure);
        }
        BridgeStateStore stateStore = new BridgeStateStore(this, versionCode);
        BridgeStateSnapshot initialState = stateStore.load();
        AtomicReference<BridgeRequestDispatcher> dispatcherReference = new AtomicReference<>();
        AtomicReference<BackgroundOptimizationController> optimizationReference =
                new AtomicReference<>();
        RootConnectionState durableRootState = initialState.rootJournal().terminalState() == null
                ? RootConnectionState.DISCONNECTED
                : initialState.rootJournal().terminalState();
        rootConnectionManager = new RootConnectionManager(
                new LibsuRootBindAdapter(this, logger),
                SystemClock::elapsedRealtime,
                (deadline, action) -> mainHandler.postDelayed(
                        action, Math.max(0L, deadline - SystemClock.elapsedRealtime())),
                action -> {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        action.run();
                    } else {
                        mainHandler.post(action);
                    }
                },
                terminalState -> {
                    BridgeRequestDispatcher active = dispatcherReference.get();
                    if (active != null) {
                        active.recordRootTerminal(terminalState);
                    }
                },
                10_000L,
                durableRootState,
                () -> {
                    BridgeRequestDispatcher active = dispatcherReference.get();
                    if (active != null) {
                        active.onRootStateChanged();
                    }
                    BackgroundOptimizationController optimization =
                            optimizationReference.get();
                    if (optimization != null
                            && rootConnectionManager.state() == RootConnectionState.CONNECTED
                            && active != null
                            && active.configuration().backgroundOptimizationEnabled()) {
                        mainHandler.post(() -> optimization.setEnabled(true));
                    }
                });
        BackgroundOptimizationController optimizationController =
                new BackgroundOptimizationController(stateStore, rootConnectionManager);
        optimizationReference.set(optimizationController);
        dispatcher = new BridgeRequestDispatcher(
                getApplicationInfo().uid,
                stateStore,
                rootConnectionManager,
                optimizationController,
                new SystemUiSessionRegistry(BridgeProtocol.VERSION, this::newSessionToken));
        dispatcherReference.set(dispatcher);
        if (durableRootState == RootConnectionState.DISCONNECTED) {
            rootConnectionManager.requestFreshConnection();
        }
        logger.info("bridge_service_created", "pid=" + android.os.Process.myPid()
                + " uid=" + android.os.Process.myUid() + " protocol=" + BridgeProtocol.VERSION);
    }

    @Override
    public IBinder onBind(Intent intent) {
        logger.info("bridge_service_bound", "intent=" + intent);
        return bridge;
    }

    private CallerIdentity callerIdentity() {
        int callingUid = Binder.getCallingUid();
        return new CallerIdentity(
                callingUid, getPackageManager().getPackagesForUid(callingUid));
    }

    private void linkSystemUiCallback(
            ISystemUiCallback callback, SystemUiRegistration registration) {
        IBinder.DeathRecipient deathRecipient = () -> {
            dispatcher.onSystemUiCallbackDied(registration.callbackId());
            synchronized (callbackLock) {
                systemUiCallbacks.remove(callback);
            }
        };
        try {
            callback.asBinder().linkToDeath(deathRecipient, 0);
            synchronized (callbackLock) {
                CallbackRegistration old = systemUiCallbacks.put(
                        callback,
                        new CallbackRegistration(
                                callback,
                                registration.callbackId(),
                                registration.sessionToken(),
                                deathRecipient));
                if (old != null) {
                    old.callback.asBinder().unlinkToDeath(old.deathRecipient, 0);
                }
            }
        } catch (RemoteException dead) {
            dispatcher.onSystemUiCallbackDied(registration.callbackId());
        }
    }

    private CallbackRegistration findSystemUiRegistration(String sessionToken) {
        synchronized (callbackLock) {
            for (CallbackRegistration registration : systemUiCallbacks.values()) {
                if (registration.sessionToken.equals(sessionToken)) {
                    return registration;
                }
            }
        }
        return null;
    }

    private void unlinkSystemUiCallback(ISystemUiCallback callback) {
        CallbackRegistration removed;
        synchronized (callbackLock) {
            removed = systemUiCallbacks.remove(callback);
        }
        if (removed != null) {
            callback.asBinder().unlinkToDeath(removed.deathRecipient, 0);
        }
    }

    private void unlinkSystemUiCallbackById(String callbackId) {
        if (callbackId == null) {
            return;
        }
        ISystemUiCallback match = null;
        synchronized (callbackLock) {
            for (CallbackRegistration registration : systemUiCallbacks.values()) {
                if (callbackId.equals(registration.callbackId)) {
                    match = registration.callback;
                    break;
                }
            }
        }
        if (match != null) {
            unlinkSystemUiCallback(match);
        }
    }

    private void linkRuntimeObserver(IRuntimeObserver observer, String observerId) {
        IBinder.DeathRecipient deathRecipient = () -> {
            dispatcher.unregisterRuntimeObserver(
                    new CallerIdentity(getApplicationInfo().uid,
                            new String[] {getPackageName()}),
                    BridgeProtocol.VERSION,
                    observerId);
            synchronized (callbackLock) {
                runtimeObservers.remove(observer);
            }
        };
        try {
            observer.asBinder().linkToDeath(deathRecipient, 0);
            synchronized (callbackLock) {
                IBinder.DeathRecipient old = runtimeObservers.put(observer, deathRecipient);
                if (old != null) {
                    observer.asBinder().unlinkToDeath(old, 0);
                }
            }
        } catch (RemoteException dead) {
            deathRecipient.binderDied();
        }
    }

    private void unlinkRuntimeObserver(IRuntimeObserver observer) {
        IBinder.DeathRecipient removed;
        synchronized (callbackLock) {
            removed = runtimeObservers.remove(observer);
        }
        if (removed != null) {
            observer.asBinder().unlinkToDeath(removed, 0);
        }
    }

    private static String runtimeObserverId(IRuntimeObserver observer) {
        return "runtime-observer-" + System.identityHashCode(observer.asBinder());
    }

    private static void deliverConfiguration(
            ISystemUiCallback callback, ForceStopConfiguration configuration) {
        try {
            callback.onConfigurationChanged(BridgeConfigurationParcel.fromModel(configuration));
        } catch (RemoteException failure) {
            throw new IllegalStateException("SystemUI callback is unavailable", failure);
        }
    }

    private static void deliverRuntimeState(
            IRuntimeObserver observer, BridgeRuntimeState state) {
        try {
            observer.onRuntimeStateChanged(BridgeRuntimeStateParcel.fromModel(state));
        } catch (RemoteException failure) {
            throw new IllegalStateException("Runtime observer is unavailable", failure);
        }
    }

    private String newSessionToken() {
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static <T> T enumValue(T[] values, int ordinal) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private void logCall(String operation, CallerIdentity caller, BridgeProtocol.Status status) {
        logger.log(
                status == BridgeProtocol.Status.OK ? Log.INFO : Log.WARN,
                "bridge_call",
                "operation=" + operation + " uid=" + caller.uid() + " status=" + status,
                null);
    }

    private void report(int priority, String event, String details, Throwable throwable) {
        if (!BuildConfig.DIAGNOSTICS_ENABLED) {
            return;
        }
        String message = "event=" + event + " " + details;
        Log.println(priority, TAG, throwable == null
                ? message
                : message + "\n" + Log.getStackTraceString(throwable));
    }

    private record CallbackRegistration(
            ISystemUiCallback callback,
            String callbackId,
            String sessionToken,
            IBinder.DeathRecipient deathRecipient) {
    }
}
