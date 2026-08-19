package com.wayne.statusbarforceclose;

import java.util.Objects;
import java.util.function.Supplier;

final class RecoveryEventController {
    static final long SCREEN_ON_DELAY_MILLIS = 750L;

    private final MonotonicClock clock;
    private final DeadlineScheduler scheduler;
    private final Supplier<RootConnectionState> rootStateSource;
    private final RecoveryRequestSink sink;
    private final RecoveryGate gate;

    RecoveryEventController(
            MonotonicClock clock,
            DeadlineScheduler scheduler,
            Supplier<RootConnectionState> rootStateSource,
            RecoveryRequestSink sink,
            RecoveryGate gate) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.rootStateSource = Objects.requireNonNull(rootStateSource, "rootStateSource");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    void onTaskFront() {
        deliver(RecoveryReason.TASK_FRONT);
    }

    void onUserPresent() {
        deliver(RecoveryReason.USER_PRESENT);
    }

    void onScreenOn() {
        long now = clock.nowMillis();
        scheduler.schedule(now + SCREEN_ON_DELAY_MILLIS,
                () -> deliver(RecoveryReason.SCREEN_ON));
    }

    private void deliver(RecoveryReason reason) {
        RootConnectionState rootState = Objects.requireNonNull(
                rootStateSource.get(), "root state");
        long windowId = gate.tryAcquire(reason, rootState, clock.nowMillis());
        if (windowId != RecoveryGate.NO_WINDOW) {
            sink.request(reason, windowId);
        }
    }
}
