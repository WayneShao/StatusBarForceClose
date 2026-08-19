package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class RecoveryEventControllerTest {
    @Test
    public void taskFrontIsDeliveredImmediatelyWithoutTaskPayload() {
        Fixture fixture = new Fixture();

        fixture.controller.onTaskFront();

        assertEquals(List.of(new RecoveryRequest(RecoveryReason.TASK_FRONT, 1L)),
                fixture.requests);
    }

    @Test
    public void screenOnIsEvaluatedAfterExactlySevenHundredFiftyMillis() {
        Fixture fixture = new Fixture();
        fixture.clock.now = 100L;

        fixture.controller.onScreenOn();

        assertEquals(850L, fixture.scheduler.deadline);
        assertEquals(List.of(), fixture.requests);
        fixture.clock.now = 850L;
        fixture.scheduler.action.run();
        assertEquals(List.of(new RecoveryRequest(RecoveryReason.SCREEN_ON, 1L)),
                fixture.requests);
    }

    @Test
    public void userPresentWinsAndDelayedScreenSignalSharesCooldown() {
        Fixture fixture = new Fixture();
        fixture.clock.now = 100L;
        fixture.controller.onScreenOn();
        fixture.clock.now = 200L;

        fixture.controller.onUserPresent();
        fixture.clock.now = 850L;
        fixture.scheduler.action.run();

        assertEquals(List.of(new RecoveryRequest(RecoveryReason.USER_PRESENT, 1L)),
                fixture.requests);
    }

    @Test
    public void connectedConnectingAndTerminalStatesNeverReachSink() {
        for (RootConnectionState state : List.of(
                RootConnectionState.CONNECTED,
                RootConnectionState.CONNECTING,
                RootConnectionState.DENIED,
                RootConnectionState.INCOMPATIBLE)) {
            Fixture fixture = new Fixture();
            fixture.rootState = state;
            fixture.controller.onTaskFront();
            fixture.controller.onUserPresent();
            assertEquals(List.of(), fixture.requests);
        }
    }

    @Test
    public void exactProtectedActionsAreRoutedAndEverythingElseIsIgnored() {
        assertEquals(RecoveryReason.SCREEN_ON,
                RecoveryActionRouter.route("android.intent.action.SCREEN_ON"));
        assertEquals(RecoveryReason.USER_PRESENT,
                RecoveryActionRouter.route("android.intent.action.USER_PRESENT"));
        assertNull(RecoveryActionRouter.route("android.intent.action.SCREEN_OFF"));
        assertNull(RecoveryActionRouter.route("com.example.SCREEN_ON"));
        assertNull(RecoveryActionRouter.route(null));
    }

    private static final class Fixture {
        private final FakeClock clock = new FakeClock();
        private final FakeScheduler scheduler = new FakeScheduler();
        private final List<RecoveryRequest> requests = new ArrayList<>();
        private RootConnectionState rootState = RootConnectionState.DISCONNECTED;
        private final RecoveryEventController controller = new RecoveryEventController(
                clock,
                scheduler,
                () -> rootState,
                (reason, windowId) -> requests.add(new RecoveryRequest(reason, windowId)),
                new RecoveryGate(5_000L));
    }

    private static final class FakeClock implements MonotonicClock {
        private long now;

        @Override
        public long nowMillis() {
            return now;
        }
    }

    private static final class FakeScheduler implements DeadlineScheduler {
        private long deadline;
        private Runnable action;

        @Override
        public void schedule(long deadlineMillis, Runnable scheduledAction) {
            deadline = deadlineMillis;
            action = scheduledAction;
        }
    }
}
