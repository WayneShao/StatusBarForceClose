package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StatusBarHookStrategySelectorTest {
    @Test
    public void prefersVerifiedMiuiHierarchy() {
        assertEquals(
                StatusBarHookStrategySelector.Strategy.MIUI_DISPATCH,
                StatusBarHookStrategySelector.select(true, true, true));
    }

    @Test
    public void selectsOplusListenerWhenMiuiHierarchyIsAbsent() {
        assertEquals(
                StatusBarHookStrategySelector.Strategy.OPLUS_INFLATE_LISTENER,
                StatusBarHookStrategySelector.select(true, false, true));
    }

    @Test
    public void rejectsMissingPhoneStatusBarView() {
        assertEquals(
                StatusBarHookStrategySelector.Strategy.UNSUPPORTED,
                StatusBarHookStrategySelector.select(false, true, true));
    }

    @Test
    public void rejectsMissingOplusMarker() {
        assertEquals(
                StatusBarHookStrategySelector.Strategy.UNSUPPORTED,
                StatusBarHookStrategySelector.select(true, false, false));
    }

    @Test
    public void rejectsUnrecognizedStructure() {
        assertEquals(
                StatusBarHookStrategySelector.Strategy.UNSUPPORTED,
                StatusBarHookStrategySelector.select(false, false, false));
    }
}
