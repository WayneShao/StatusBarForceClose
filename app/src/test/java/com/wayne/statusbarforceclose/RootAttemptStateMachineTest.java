package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RootAttemptStateMachineTest {
    private static final long TIMEOUT_MILLIS = 10_000L;

    @Test
    public void initialWarmupInstallsAttemptAndTenSecondDeadline() {
        RootAttemptStateMachine machine = machine();

        long attemptId = machine.beginAttempt(4_000L);

        assertEquals(1L, attemptId);
        assertEquals(RootConnectionState.CONNECTING, machine.state());
        assertEquals(attemptId, machine.activeAttemptId());
        assertEquals(14_000L, machine.deadlineMillis());
        assertEquals(RootAttemptStateMachine.NO_ATTEMPT, machine.beginAttempt(4_001L));
    }

    @Test
    public void timeoutInvalidatesAttemptAndLateCallbacksAreRejected() {
        RootAttemptStateMachine machine = machine();
        long first = machine.beginAttempt(1_000L);

        assertEquals(RootAttemptStateMachine.NO_ATTEMPT, machine.expireIfDue(10_999L));
        assertEquals(first, machine.expireIfDue(11_000L));
        assertEquals(RootConnectionState.TRANSIENT_ERROR, machine.state());
        assertEquals(RootAttemptStateMachine.NO_ATTEMPT, machine.activeAttemptId());

        long second = machine.beginAttempt(11_001L);
        assertFalse(machine.onConnected(first));
        assertFalse(machine.onTransientError(first));
        assertEquals(RootConnectionState.CONNECTING, machine.state());
        assertEquals(second, machine.activeAttemptId());
        assertTrue(machine.onConnected(second));
        assertEquals(RootConnectionState.CONNECTED, machine.state());
    }

    @Test
    public void connectedControllerIsReusedUntilDisconnect() {
        RootAttemptStateMachine machine = machine();
        long attemptId = machine.beginAttempt(0L);
        assertTrue(machine.onConnected(attemptId));

        assertEquals(RootAttemptStateMachine.NO_ATTEMPT, machine.beginAttempt(2_000L));
        assertFalse(machine.expireIfDue(20_000L) > RootAttemptStateMachine.NO_ATTEMPT);
        assertTrue(machine.onDisconnected());
        assertEquals(RootConnectionState.DISCONNECTED, machine.state());
        assertFalse(machine.onDisconnected());
        assertTrue(machine.beginAttempt(20_001L) > attemptId);
    }

    @Test
    public void explicitDenialIsTerminalUntilFreshSettingsOrGenerationTrigger() {
        RootAttemptStateMachine machine = machine();
        long attemptId = machine.beginAttempt(0L);
        assertTrue(machine.onDenied(attemptId));
        assertEquals(RootConnectionState.DENIED, machine.state());
        assertEquals(RootAttemptStateMachine.NO_ATTEMPT, machine.beginAttempt(1L));

        assertFalse(machine.onSettingsOpen(false));
        assertEquals(RootConnectionState.DENIED, machine.state());
        assertTrue(machine.onSettingsOpen(true));
        assertEquals(RootConnectionState.DISCONNECTED, machine.state());

        assertTrue(machine.onDenied(machine.beginAttempt(2L)));
        assertFalse(machine.onSystemUiGeneration(false));
        assertTrue(machine.onSystemUiGeneration(true));
        assertEquals(RootConnectionState.DISCONNECTED, machine.state());
    }

    @Test
    public void incompatibleClearsOnlyAfterApkVersionChange() {
        RootAttemptStateMachine machine = machine();
        assertTrue(machine.onIncompatible(machine.beginAttempt(0L)));
        assertEquals(RootConnectionState.INCOMPATIBLE, machine.state());

        assertFalse(machine.onSettingsOpen(true));
        assertFalse(machine.onSystemUiGeneration(true));
        assertFalse(machine.onApkVersionChanged(false));
        assertEquals(RootConnectionState.INCOMPATIBLE, machine.state());
        assertTrue(machine.onApkVersionChanged(true));
        assertEquals(RootConnectionState.DISCONNECTED, machine.state());
    }

    @Test
    public void failureCallbacksClassifyOnlyTheActiveAttempt() {
        RootAttemptStateMachine machine = machine();
        long first = machine.beginAttempt(0L);
        assertFalse(machine.onDenied(first + 1L));
        assertFalse(machine.onIncompatible(first + 1L));
        assertEquals(RootConnectionState.CONNECTING, machine.state());

        assertTrue(machine.onTransientError(first));
        assertEquals(RootConnectionState.TRANSIENT_ERROR, machine.state());
        long second = machine.beginAttempt(1L);
        assertTrue(machine.onIncompatible(second));
        assertEquals(RootConnectionState.INCOMPATIBLE, machine.state());
    }

    private static RootAttemptStateMachine machine() {
        return new RootAttemptStateMachine(TIMEOUT_MILLIS);
    }
}
