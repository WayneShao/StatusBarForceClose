package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public final class ForceStopPolicyTest {
    @Test
    public void automaticUsesConnectedRootFirst() {
        assertPlan(List.of(step(BackendKind.ROOT, false), step(BackendKind.SYSTEM_UI, false)),
                ForceStopPolicy.select(ExecutionMode.AUTO, true, false, true));
    }

    @Test
    public void automaticUsesAvailableSystemUiWhileRootCanReconnect() {
        assertPlan(List.of(step(BackendKind.SYSTEM_UI, false), step(BackendKind.ROOT, true)),
                ForceStopPolicy.select(ExecutionMode.AUTO, false, true, true));
    }

    @Test
    public void automaticWaitsOnlyForRootWhenSystemUiUnavailable() {
        assertPlan(List.of(step(BackendKind.ROOT, true)),
                ForceStopPolicy.select(ExecutionMode.AUTO, false, true, false));
    }

    @Test
    public void automaticUsesOnlySystemUiWhenRootIsTerminal() {
        assertPlan(List.of(step(BackendKind.SYSTEM_UI, false)),
                ForceStopPolicy.select(ExecutionMode.AUTO, false, false, true));
    }

    @Test
    public void rootOnlyNeverAddsSystemUi() {
        assertPlan(List.of(step(BackendKind.ROOT, true)),
                ForceStopPolicy.select(ExecutionMode.ROOT_ONLY, false, true, true));
        assertPlan(List.of(),
                ForceStopPolicy.select(ExecutionMode.ROOT_ONLY, false, false, true));
    }

    @Test
    public void systemUiOnlyNeverAddsRoot() {
        assertPlan(List.of(step(BackendKind.SYSTEM_UI, false)),
                ForceStopPolicy.select(ExecutionMode.SYSTEM_UI_ONLY, true, true, true));
        assertPlan(List.of(),
                ForceStopPolicy.select(ExecutionMode.SYSTEM_UI_ONLY, true, true, false));
    }

    @Test
    public void rootFirstUsesDocumentedFallbackOrder() {
        assertPlan(List.of(step(BackendKind.ROOT, true), step(BackendKind.SYSTEM_UI, false)),
                ForceStopPolicy.select(ExecutionMode.ROOT_FIRST, false, true, true));
        assertPlan(List.of(step(BackendKind.SYSTEM_UI, false)),
                ForceStopPolicy.select(ExecutionMode.ROOT_FIRST, false, false, true));
    }

    @Test
    public void systemUiFirstUsesDocumentedFallbackOrder() {
        assertPlan(List.of(step(BackendKind.SYSTEM_UI, false), step(BackendKind.ROOT, false)),
                ForceStopPolicy.select(ExecutionMode.SYSTEM_UI_FIRST, true, false, true));
        assertPlan(List.of(step(BackendKind.ROOT, true)),
                ForceStopPolicy.select(ExecutionMode.SYSTEM_UI_FIRST, false, true, false));
    }

    @Test
    public void noUsableBackendProducesEmptyPlan() {
        for (ExecutionMode mode : ExecutionMode.values()) {
            assertPlan(List.of(), ForceStopPolicy.select(mode, false, false, false));
        }
    }

    private static ExecutionStep step(BackendKind backend, boolean waitForConnection) {
        return new ExecutionStep(backend, waitForConnection);
    }

    private static void assertPlan(List<ExecutionStep> expected, ExecutionPlan actual) {
        assertEquals(expected, actual.steps());
    }
}
