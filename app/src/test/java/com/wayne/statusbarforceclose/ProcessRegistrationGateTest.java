package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ProcessRegistrationGateTest {
    @Test
    public void registrationCanBeAttemptedOnlyOnce() {
        ProcessRegistrationGate gate = new ProcessRegistrationGate();

        assertTrue(gate.tryStart());
        assertFalse(gate.tryStart());
        assertFalse(gate.tryStart());
    }
}
