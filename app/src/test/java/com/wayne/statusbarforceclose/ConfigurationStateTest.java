package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ConfigurationStateTest {
    @Test
    public void bridgeDefaultsToAutomaticWithOptimizationEnabled() {
        ForceStopConfiguration configuration = ForceStopConfiguration.bridgeDefaults();

        assertEquals(ConfigurationState.CONFIGURED, configuration.state());
        assertEquals(ExecutionMode.AUTO, configuration.executionMode());
        assertTrue(configuration.backgroundOptimizationEnabled());
        assertEquals(1L, configuration.revision());
    }

    @Test
    public void systemUiStartsUnconfiguredAndFailsClosed() {
        ForceStopConfiguration configuration = ForceStopConfiguration.unconfigured();

        assertEquals(ConfigurationState.UNCONFIGURED, configuration.state());
        assertNull(configuration.executionMode());
        assertFalse(configuration.isUsable());
    }

    @Test
    public void updatesAdvanceRevisionEvenWhenOnlyOneFieldChanges() {
        ForceStopConfiguration initial = ForceStopConfiguration.bridgeDefaults();
        ForceStopConfiguration modeChanged = initial.update(ExecutionMode.ROOT_ONLY, true);
        ForceStopConfiguration optimizationChanged = modeChanged.update(
                ExecutionMode.ROOT_ONLY, false);

        assertEquals(2L, modeChanged.revision());
        assertEquals(ExecutionMode.ROOT_ONLY, modeChanged.executionMode());
        assertEquals(3L, optimizationChanged.revision());
        assertFalse(optimizationChanged.backgroundOptimizationEnabled());
    }

    @Test
    public void remoteSnapshotsAcceptOnlyStrictlyNewerRevision() {
        ForceStopConfiguration unconfigured = ForceStopConfiguration.unconfigured();
        ForceStopConfiguration revisionOne = ForceStopConfiguration.bridgeDefaults();
        ForceStopConfiguration revisionTwo = revisionOne.update(ExecutionMode.ROOT_FIRST, true);

        assertEquals(revisionOne, unconfigured.acceptNewer(revisionOne));
        assertEquals(revisionTwo, revisionOne.acceptNewer(revisionTwo));
        assertEquals(revisionTwo, revisionTwo.acceptNewer(revisionTwo));
        assertEquals(revisionTwo, revisionTwo.acceptNewer(revisionOne));
        assertEquals(revisionTwo, revisionTwo.acceptNewer(ForceStopConfiguration.unconfigured()));
    }
}
