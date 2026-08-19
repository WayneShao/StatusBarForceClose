package com.wayne.statusbarforceclose;

final class ReconnectBackoff {
    private final long initialDelayMillis;
    private final long maximumDelayMillis;
    private final int maximumAttempts;
    private int attempts;

    ReconnectBackoff(long initialDelayMillis, long maximumDelayMillis, int maximumAttempts) {
        if (initialDelayMillis <= 0L
                || maximumDelayMillis < initialDelayMillis
                || maximumAttempts <= 0) {
            throw new IllegalArgumentException("Invalid reconnect backoff");
        }
        this.initialDelayMillis = initialDelayMillis;
        this.maximumDelayMillis = maximumDelayMillis;
        this.maximumAttempts = maximumAttempts;
    }

    synchronized Long nextDelay() {
        if (attempts >= maximumAttempts) {
            return null;
        }
        long multiplier = 1L << Math.min(attempts, 30);
        long delay = Math.min(maximumDelayMillis, initialDelayMillis * multiplier);
        attempts++;
        return delay;
    }

    synchronized void reset() {
        attempts = 0;
    }
}
