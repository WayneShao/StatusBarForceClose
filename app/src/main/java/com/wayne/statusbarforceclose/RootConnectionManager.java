package com.wayne.statusbarforceclose;

import java.util.Objects;
import java.util.function.Consumer;

final class RootConnectionManager implements RootOperations, RootSystemSettings {
    private final Object lock = new Object();
    private final RootBindAdapter bindAdapter;
    private final MonotonicClock clock;
    private final DeadlineScheduler scheduler;
    private final MainThreadExecutor mainExecutor;
    private final Consumer<RootConnectionState> terminalStateSink;
    private final Runnable stateChanged;
    private final RootAttemptStateMachine stateMachine;
    private Attempt activeAttempt;
    private RootController connectedController;

    RootConnectionManager(
            RootBindAdapter bindAdapter,
            MonotonicClock clock,
            DeadlineScheduler scheduler,
            MainThreadExecutor mainExecutor,
            Consumer<RootConnectionState> terminalStateSink,
            long timeoutMillis) {
        this(
                bindAdapter,
                clock,
                scheduler,
                mainExecutor,
                terminalStateSink,
                timeoutMillis,
                RootConnectionState.DISCONNECTED,
                () -> { });
    }

    RootConnectionManager(
            RootBindAdapter bindAdapter,
            MonotonicClock clock,
            DeadlineScheduler scheduler,
            MainThreadExecutor mainExecutor,
            Consumer<RootConnectionState> terminalStateSink,
            long timeoutMillis,
            RootConnectionState initialState,
            Runnable stateChanged) {
        this.bindAdapter = Objects.requireNonNull(bindAdapter, "bindAdapter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.mainExecutor = Objects.requireNonNull(mainExecutor, "mainExecutor");
        this.terminalStateSink = Objects.requireNonNull(
                terminalStateSink, "terminalStateSink");
        this.stateChanged = Objects.requireNonNull(stateChanged, "stateChanged");
        stateMachine = new RootAttemptStateMachine(timeoutMillis, initialState);
    }

    @Override
    public RootConnectionState state() {
        synchronized (lock) {
            refreshDeadControllerLocked();
            return stateMachine.state();
        }
    }

    @Override
    public BackendResult forceStop(
            String packageName, int userId, boolean waitForConnection) {
        long startedAt = clock.nowMillis();
        if (packageName == null || packageName.isBlank() || userId < 0) {
            return result(BackendStatus.OPERATION_FAILED, startedAt);
        }
        RootController controller;
        synchronized (lock) {
            refreshDeadControllerLocked();
            controller = connectedController;
        }
        if (controller == null) {
            ensureConnection();
            if (waitForConnection) {
                controller = waitForController();
            }
        }
        synchronized (lock) {
            if (controller == null) {
                controller = connectedController;
            }
        }
        if (controller == null || !controller.isAlive()) {
            return result(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
        }
        try {
            return result(
                    controller.forceStop(packageName, userId)
                            ? BackendStatus.SUCCESS
                            : BackendStatus.OPERATION_FAILED,
                    startedAt);
        } catch (Exception failure) {
            synchronized (lock) {
                if (connectedController == controller) {
                    connectedController = null;
                    stateMachine.onDisconnected();
                    lock.notifyAll();
                }
            }
            return result(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, startedAt);
        }
    }

    @Override
    public void requestFreshConnection() {
        boolean cleared;
        synchronized (lock) {
            cleared = stateMachine.onSettingsOpen(true);
        }
        if (cleared) {
            notifyStateChanged();
        }
        ensureConnection();
    }

    @Override
    public void requestRecovery(RecoveryReason reason, long windowId) {
        if (reason != null && windowId > 0L) {
            ensureConnection();
        }
    }

    RootController connectedControllerForTest() {
        synchronized (lock) {
            return connectedController;
        }
    }

    @Override
    public int query(OptimizationItem item) {
        Objects.requireNonNull(item, "item");
        RootController controller;
        synchronized (lock) {
            refreshDeadControllerLocked();
            controller = connectedController;
        }
        if (controller == null) {
            return RootSystemSettings.UNSUPPORTED;
        }
        try {
            return controller.queryOptimizationItem(item);
        } catch (Exception failure) {
            return RootSystemSettings.UNSUPPORTED;
        }
    }

    @Override
    public boolean set(OptimizationItem item, int value) {
        Objects.requireNonNull(item, "item");
        RootController controller;
        synchronized (lock) {
            refreshDeadControllerLocked();
            controller = connectedController;
        }
        if (controller == null) {
            return false;
        }
        try {
            return controller.setOptimizationItem(item, value);
        } catch (Exception failure) {
            return false;
        }
    }

    private void ensureConnection() {
        Attempt attempt;
        synchronized (lock) {
            refreshDeadControllerLocked();
            long attemptId = stateMachine.beginAttempt(clock.nowMillis());
            if (attemptId == RootAttemptStateMachine.NO_ATTEMPT) {
                return;
            }
            attempt = new Attempt(attemptId);
            activeAttempt = attempt;
        }
        notifyStateChanged();

        try {
            attempt.binding = bindAdapter.bind(new AttemptListener(attempt));
            scheduler.schedule(stateMachine.deadlineMillis(), () -> onDeadline(attempt));
        } catch (RuntimeException failure) {
            synchronized (lock) {
                if (stateMachine.onTransientError(attempt.id)) {
                    activeAttempt = null;
                    lock.notifyAll();
                }
            }
            unbind(attempt);
            notifyStateChanged();
        }
    }

    private RootController waitForController() {
        synchronized (lock) {
            while (stateMachine.state() == RootConnectionState.CONNECTING) {
                long remaining = stateMachine.deadlineMillis() - clock.nowMillis();
                if (remaining <= 0L) {
                    break;
                }
                try {
                    lock.wait(remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return connectedController;
        }
    }

    private void onDeadline(Attempt attempt) {
        boolean expired;
        synchronized (lock) {
            expired = stateMachine.expireIfDue(clock.nowMillis()) == attempt.id;
            if (expired) {
                activeAttempt = null;
                lock.notifyAll();
            }
        }
        if (expired) {
            unbind(attempt);
            notifyStateChanged();
        }
    }

    private void onConnected(Attempt attempt, RootController controller) {
        if (controller == null) {
            onTerminal(attempt, RootConnectionState.INCOMPATIBLE);
            return;
        }
        boolean accepted;
        synchronized (lock) {
            accepted = stateMachine.onConnected(attempt.id);
            if (accepted) {
                connectedController = controller;
                activeAttempt = attempt;
                lock.notifyAll();
            }
        }
        if (!accepted) {
            unbind(attempt);
        } else {
            notifyStateChanged();
        }
    }

    private void onDisconnected(Attempt attempt) {
        synchronized (lock) {
            if (activeAttempt != attempt) {
                return;
            }
            connectedController = null;
            if (!stateMachine.onDisconnected()) {
                stateMachine.onTransientError(attempt.id);
            }
            activeAttempt = null;
            lock.notifyAll();
        }
        notifyStateChanged();
    }

    private void onFailure(Attempt attempt) {
        boolean accepted;
        synchronized (lock) {
            accepted = stateMachine.onTransientError(attempt.id);
            if (accepted) {
                activeAttempt = null;
                lock.notifyAll();
            }
        }
        if (accepted) {
            unbind(attempt);
            notifyStateChanged();
        }
    }

    private void onTerminal(Attempt attempt, RootConnectionState terminalState) {
        boolean accepted;
        synchronized (lock) {
            accepted = terminalState == RootConnectionState.DENIED
                    ? stateMachine.onDenied(attempt.id)
                    : stateMachine.onIncompatible(attempt.id);
            if (accepted) {
                activeAttempt = null;
                lock.notifyAll();
            }
        }
        if (accepted) {
            terminalStateSink.accept(terminalState);
            unbind(attempt);
            notifyStateChanged();
        }
    }

    private void refreshDeadControllerLocked() {
        if (connectedController != null && !connectedController.isAlive()) {
            Attempt disconnectedAttempt = activeAttempt;
            connectedController = null;
            stateMachine.onDisconnected();
            activeAttempt = null;
            lock.notifyAll();
            if (disconnectedAttempt != null) {
                unbind(disconnectedAttempt);
            }
        }
    }

    private void unbind(Attempt attempt) {
        RootBindAdapter.Binding binding = attempt.binding;
        if (binding != null) {
            mainExecutor.execute(binding::unbind);
        }
    }

    private BackendResult result(BackendStatus status, long startedAt) {
        return new BackendResult(
                BackendKind.ROOT,
                status,
                Math.max(0L, clock.nowMillis() - startedAt));
    }

    private void notifyStateChanged() {
        stateChanged.run();
    }

    private final class AttemptListener implements RootBindAdapter.Listener {
        private final Attempt attempt;

        AttemptListener(Attempt attempt) {
            this.attempt = attempt;
        }

        @Override
        public void connected(RootController controller) {
            onConnected(attempt, controller);
        }

        @Override
        public void disconnected() {
            onDisconnected(attempt);
        }

        @Override
        public void denied() {
            onTerminal(attempt, RootConnectionState.DENIED);
        }

        @Override
        public void incompatible() {
            onTerminal(attempt, RootConnectionState.INCOMPATIBLE);
        }

        @Override
        public void failed(Throwable failure) {
            onFailure(attempt);
        }
    }

    private static final class Attempt {
        private final long id;
        private volatile RootBindAdapter.Binding binding;

        Attempt(long id) {
            this.id = id;
        }
    }
}
