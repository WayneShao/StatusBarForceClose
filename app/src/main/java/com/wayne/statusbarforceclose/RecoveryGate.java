package com.wayne.statusbarforceclose;

import java.util.Objects;

final class RecoveryGate {
    static final long NO_WINDOW = 0L;

    private final long cooldownMillis;
    private boolean hasAcceptedWindow;
    private long lastAcceptedAt;
    private long nextWindowId;

    RecoveryGate(long cooldownMillis) {
        if (cooldownMillis <= 0L) {
            throw new IllegalArgumentException("cooldownMillis must be positive");
        }
        this.cooldownMillis = cooldownMillis;
    }

    synchronized long tryAcquire(
            RecoveryReason reason,
            RootConnectionState rootState,
            long nowMillis) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(rootState, "rootState");
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("monotonic timestamp must not be negative");
        }
        if (!rootState.isRecoverable()) {
            return NO_WINDOW;
        }
        if (hasAcceptedWindow) {
            long elapsed = nowMillis - lastAcceptedAt;
            if (elapsed < 0L || elapsed < cooldownMillis) {
                return NO_WINDOW;
            }
        }
        hasAcceptedWindow = true;
        lastAcceptedAt = nowMillis;
        nextWindowId++;
        if (nextWindowId == NO_WINDOW) {
            nextWindowId++;
        }
        return nextWindowId;
    }
}
