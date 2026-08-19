package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RootBridgeCallerPolicyTest {
    @Test
    public void acceptsSystemUi() {
        assertTrue(RootBridgeCallerPolicy.isAllowed(
                new String[] {"com.example.shared", "com.android.systemui"}));
    }

    @Test
    public void rejectsOtherPackages() {
        assertFalse(RootBridgeCallerPolicy.isAllowed(
                new String[] {"com.example.attacker"}));
    }

    @Test
    public void rejectsMissingPackage() {
        assertFalse(RootBridgeCallerPolicy.isAllowed(null));
    }

    @Test
    public void moduleOperationsRequireExactModuleUid() {
        assertTrue(RootBridgeCallerPolicy.isModuleUid(10300, 10300));
        assertFalse(RootBridgeCallerPolicy.isModuleUid(10301, 10300));
        assertFalse(RootBridgeCallerPolicy.isModuleUid(-1, 10300));
    }

    @Test
    public void systemUiIdentityAndModuleIdentityRemainIndependent() {
        assertTrue(RootBridgeCallerPolicy.isSystemUi(
                new String[] {"com.android.systemui"}));
        assertFalse(RootBridgeCallerPolicy.isSystemUi(
                new String[] {"com.wayne.statusbarforceclose"}));
        assertTrue(RootBridgeCallerPolicy.isModuleUid(10300, 10300));
    }
}
