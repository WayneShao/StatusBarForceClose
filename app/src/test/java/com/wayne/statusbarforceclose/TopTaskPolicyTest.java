package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TopTaskPolicyTest {
    @Test
    public void permitsOrdinaryForegroundApplications() {
        assertTrue(TopTaskPolicy.canForceStop("com.example.reader", "com.example.keyboard"));
    }

    @Test
    public void protectsCoreInterfacePackages() {
        assertFalse(TopTaskPolicy.canForceStop(null, null));
        assertFalse(TopTaskPolicy.canForceStop("android", null));
        assertFalse(TopTaskPolicy.canForceStop("com.android.systemui", null));
        assertFalse(TopTaskPolicy.canForceStop("com.miui.home", null));
        assertFalse(TopTaskPolicy.canForceStop("com.wayne.statusbarforceclose", null));
    }

    @Test
    public void protectsTheActiveInputMethod() {
        assertFalse(TopTaskPolicy.canForceStop("com.example.keyboard", "com.example.keyboard"));
    }
}

