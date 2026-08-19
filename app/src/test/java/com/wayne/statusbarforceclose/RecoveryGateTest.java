package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RecoveryGateTest {
    private static final long COOLDOWN_MILLIS = 5_000L;

    @Test
    public void allReasonsShareOneMonotonicCooldown() {
        RecoveryGate gate = new RecoveryGate(COOLDOWN_MILLIS);

        long firstWindow = gate.tryAcquire(
                RecoveryReason.SCREEN_ON, RootConnectionState.DISCONNECTED, 100L);
        assertTrue(firstWindow > RecoveryGate.NO_WINDOW);
        assertEquals(RecoveryGate.NO_WINDOW, gate.tryAcquire(
                RecoveryReason.USER_PRESENT, RootConnectionState.TRANSIENT_ERROR, 5_099L));
        assertEquals(RecoveryGate.NO_WINDOW, gate.tryAcquire(
                RecoveryReason.TASK_FRONT, RootConnectionState.DISCONNECTED, 5_099L));

        long secondWindow = gate.tryAcquire(
                RecoveryReason.TASK_FRONT, RootConnectionState.TRANSIENT_ERROR, 5_100L);
        assertTrue(secondWindow > firstWindow);
    }

    @Test
    public void connectedConnectingAndTerminalStatesDoNotConsumeCooldown() {
        RecoveryGate gate = new RecoveryGate(COOLDOWN_MILLIS);

        assertEquals(RecoveryGate.NO_WINDOW, gate.tryAcquire(
                RecoveryReason.TASK_FRONT, RootConnectionState.CONNECTED, 10L));
        assertEquals(RecoveryGate.NO_WINDOW, gate.tryAcquire(
                RecoveryReason.SCREEN_ON, RootConnectionState.CONNECTING, 20L));
        assertEquals(RecoveryGate.NO_WINDOW, gate.tryAcquire(
                RecoveryReason.USER_PRESENT, RootConnectionState.DENIED, 30L));
        assertEquals(RecoveryGate.NO_WINDOW, gate.tryAcquire(
                RecoveryReason.TASK_FRONT, RootConnectionState.INCOMPATIBLE, 40L));

        assertTrue(gate.tryAcquire(
                RecoveryReason.USER_PRESENT, RootConnectionState.DISCONNECTED, 41L)
                > RecoveryGate.NO_WINDOW);
    }

    @Test
    public void transientErrorIsRetryableButCooldownDoesNotMoveBackward() {
        RecoveryGate gate = new RecoveryGate(COOLDOWN_MILLIS);
        long firstWindow = gate.tryAcquire(
                RecoveryReason.USER_PRESENT, RootConnectionState.TRANSIENT_ERROR, 9_000L);

        assertTrue(firstWindow > RecoveryGate.NO_WINDOW);
        assertEquals(RecoveryGate.NO_WINDOW, gate.tryAcquire(
                RecoveryReason.TASK_FRONT, RootConnectionState.DISCONNECTED, 8_999L));
        assertEquals(RecoveryGate.NO_WINDOW, gate.tryAcquire(
                RecoveryReason.TASK_FRONT, RootConnectionState.DISCONNECTED, 13_999L));
        assertTrue(gate.tryAcquire(
                RecoveryReason.TASK_FRONT, RootConnectionState.DISCONNECTED, 14_000L)
                > firstWindow);
    }
}
