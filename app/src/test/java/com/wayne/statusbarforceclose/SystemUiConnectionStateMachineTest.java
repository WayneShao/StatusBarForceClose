package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SystemUiConnectionStateMachineTest {
    @Test
    public void duplicateInflationCannotStartDuplicateBind() {
        SystemUiConnectionStateMachine machine = new SystemUiConnectionStateMachine();

        assertTrue(machine.startBinding());
        assertFalse(machine.startBinding());
        assertEquals(SystemUiBridgeState.BINDING, machine.snapshot().state());
    }

    @Test
    public void registrationMakesOnlyMatchingGenerationReady() {
        SystemUiConnectionStateMachine machine = new SystemUiConnectionStateMachine();
        machine.startBinding();
        assertTrue(machine.onConnected());

        assertFalse(machine.onRegistered("other-generation", "token-a"));
        assertTrue(machine.onRegistered(machine.generation(), "token-a"));
        assertEquals(SystemUiBridgeState.READY, machine.snapshot().state());
        assertEquals("token-a", machine.snapshot().sessionToken());
    }

    @Test
    public void disconnectClearsSessionAndAllowsReconnect() {
        SystemUiConnectionStateMachine machine = new SystemUiConnectionStateMachine();
        machine.startBinding();
        machine.onConnected();
        machine.onRegistered(machine.generation(), "token-a");

        machine.onDisconnected();

        assertEquals(SystemUiBridgeState.DISCONNECTED, machine.snapshot().state());
        assertNull(machine.snapshot().sessionToken());
        assertTrue(machine.startBinding());
    }

    @Test
    public void failedBindReturnsToDisconnected() {
        SystemUiConnectionStateMachine machine = new SystemUiConnectionStateMachine();
        machine.startBinding();

        machine.onBindFailed();

        assertEquals(SystemUiBridgeState.DISCONNECTED, machine.snapshot().state());
        assertNull(machine.snapshot().sessionToken());
    }
}
