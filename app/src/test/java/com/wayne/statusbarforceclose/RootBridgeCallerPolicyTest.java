package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RootBridgeCallerPolicyTest {
    @Test
    public void acceptsSystemUi() {
        assertTrue(RootBridgeCallerPolicy.isAllowed("com.android.systemui"));
    }

    @Test
    public void rejectsOtherPackages() {
        assertFalse(RootBridgeCallerPolicy.isAllowed("com.example.attacker"));
    }

    @Test
    public void rejectsMissingPackage() {
        assertFalse(RootBridgeCallerPolicy.isAllowed(null));
    }
}
