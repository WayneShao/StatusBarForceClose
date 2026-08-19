package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public final class SystemUiRuntimePolicyTest {
    private final SystemUiRuntimePolicy policy = new SystemUiRuntimePolicy();

    @Test
    public void unconfiguredClientNeverExecutes() {
        assertEquals(List.of(), policy.select(
                ForceStopConfiguration.unconfigured(),
                RootConnectionState.CONNECTED,
                SystemUiCapability.AVAILABLE).steps());
    }

    @Test
    public void connectedRootAndAvailableSystemUiUseConfiguredAutomaticOrder() {
        assertEquals(
                List.of(
                        new ExecutionStep(BackendKind.ROOT, false),
                        new ExecutionStep(BackendKind.SYSTEM_UI, false)),
                policy.select(
                        ForceStopConfiguration.bridgeDefaults(),
                        RootConnectionState.CONNECTED,
                        SystemUiCapability.AVAILABLE).steps());
    }

    @Test
    public void recoverableRootCanWaitButTerminalRootCannot() {
        ForceStopConfiguration rootOnly = ForceStopConfiguration.bridgeDefaults()
                .update(ExecutionMode.ROOT_ONLY, true);

        assertEquals(
                List.of(new ExecutionStep(BackendKind.ROOT, true)),
                policy.select(
                        rootOnly,
                        RootConnectionState.TRANSIENT_ERROR,
                        SystemUiCapability.FUSED_REJECTED).steps());
        assertEquals(List.of(), policy.select(
                rootOnly,
                RootConnectionState.DENIED,
                SystemUiCapability.AVAILABLE).steps());
        assertEquals(List.of(), policy.select(
                rootOnly,
                RootConnectionState.INCOMPATIBLE,
                SystemUiCapability.AVAILABLE).steps());
    }

    @Test
    public void connectingRootCanBeAwaitedWithinBridgeDeadline() {
        ForceStopConfiguration rootFirst = ForceStopConfiguration.bridgeDefaults()
                .update(ExecutionMode.ROOT_FIRST, true);

        assertEquals(
                List.of(
                        new ExecutionStep(BackendKind.ROOT, true),
                        new ExecutionStep(BackendKind.SYSTEM_UI, false)),
                policy.select(
                        rootFirst,
                        RootConnectionState.CONNECTING,
                        SystemUiCapability.AVAILABLE).steps());
    }

    @Test
    public void rejectedSystemUiIsRemovedFromEveryMode() {
        for (ExecutionMode mode : ExecutionMode.values()) {
            ForceStopConfiguration configuration = ForceStopConfiguration.bridgeDefaults()
                    .update(mode, true);
            for (ExecutionStep step : policy.select(
                    configuration,
                    RootConnectionState.CONNECTED,
                    SystemUiCapability.FUSED_REJECTED).steps()) {
                assertEquals(BackendKind.ROOT, step.backend());
            }
        }
    }
}
