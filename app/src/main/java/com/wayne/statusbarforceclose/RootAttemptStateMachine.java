package com.wayne.statusbarforceclose;

final class RootAttemptStateMachine {
    static final long NO_ATTEMPT = -1L;

    private final long timeoutMillis;
    private RootConnectionState state = RootConnectionState.DISCONNECTED;
    private long nextAttemptId;
    private long activeAttemptId = NO_ATTEMPT;
    private long deadlineMillis = NO_ATTEMPT;

    RootAttemptStateMachine(long timeoutMillis) {
        this(timeoutMillis, RootConnectionState.DISCONNECTED);
    }

    RootAttemptStateMachine(long timeoutMillis, RootConnectionState initialState) {
        if (timeoutMillis <= 0L) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        if (initialState != RootConnectionState.DISCONNECTED
                && initialState != RootConnectionState.DENIED
                && initialState != RootConnectionState.INCOMPATIBLE) {
            throw new IllegalArgumentException("Invalid durable initial root state");
        }
        this.timeoutMillis = timeoutMillis;
        state = initialState;
    }

    synchronized RootConnectionState state() {
        return state;
    }

    synchronized long activeAttemptId() {
        return activeAttemptId;
    }

    synchronized long deadlineMillis() {
        return deadlineMillis;
    }

    synchronized long beginAttempt(long nowMillis) {
        requireMonotonicTimestamp(nowMillis);
        if (!state.isRecoverable()) {
            return NO_ATTEMPT;
        }
        nextAttemptId++;
        if (nextAttemptId == NO_ATTEMPT) {
            nextAttemptId++;
        }
        activeAttemptId = nextAttemptId;
        deadlineMillis = saturatedAdd(nowMillis, timeoutMillis);
        state = RootConnectionState.CONNECTING;
        return activeAttemptId;
    }

    synchronized long expireIfDue(long nowMillis) {
        requireMonotonicTimestamp(nowMillis);
        if (state != RootConnectionState.CONNECTING || nowMillis < deadlineMillis) {
            return NO_ATTEMPT;
        }
        long expiredAttemptId = activeAttemptId;
        clearActiveAttempt();
        state = RootConnectionState.TRANSIENT_ERROR;
        return expiredAttemptId;
    }

    synchronized boolean onConnected(long attemptId) {
        return complete(attemptId, RootConnectionState.CONNECTED);
    }

    synchronized boolean onTransientError(long attemptId) {
        return complete(attemptId, RootConnectionState.TRANSIENT_ERROR);
    }

    synchronized boolean onDenied(long attemptId) {
        return complete(attemptId, RootConnectionState.DENIED);
    }

    synchronized boolean onIncompatible(long attemptId) {
        return complete(attemptId, RootConnectionState.INCOMPATIBLE);
    }

    synchronized boolean onDisconnected() {
        if (state != RootConnectionState.CONNECTED) {
            return false;
        }
        state = RootConnectionState.DISCONNECTED;
        return true;
    }

    synchronized boolean onSettingsOpen(boolean freshToken) {
        return clearDenied(freshToken);
    }

    synchronized boolean onSystemUiGeneration(boolean newGeneration) {
        return clearDenied(newGeneration);
    }

    synchronized boolean onApkVersionChanged(boolean changed) {
        if (!changed || state != RootConnectionState.INCOMPATIBLE) {
            return false;
        }
        state = RootConnectionState.DISCONNECTED;
        return true;
    }

    private boolean complete(long attemptId, RootConnectionState newState) {
        if (state != RootConnectionState.CONNECTING
                || activeAttemptId == NO_ATTEMPT
                || activeAttemptId != attemptId) {
            return false;
        }
        clearActiveAttempt();
        state = newState;
        return true;
    }

    private boolean clearDenied(boolean eligibleTrigger) {
        if (!eligibleTrigger || state != RootConnectionState.DENIED) {
            return false;
        }
        state = RootConnectionState.DISCONNECTED;
        return true;
    }

    private void clearActiveAttempt() {
        activeAttemptId = NO_ATTEMPT;
        deadlineMillis = NO_ATTEMPT;
    }

    private static long saturatedAdd(long left, long right) {
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static void requireMonotonicTimestamp(long nowMillis) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("monotonic timestamp must not be negative");
        }
    }
}
