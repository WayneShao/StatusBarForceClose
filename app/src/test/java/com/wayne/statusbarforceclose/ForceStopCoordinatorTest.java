package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class ForceStopCoordinatorTest {
    @Test
    public void successfulFirstAttemptStopsThePlan() {
        List<BackendKind> calls = new ArrayList<>();
        ForceStopCoordinator coordinator = coordinator(
                calls, BackendStatus.SUCCESS, BackendStatus.SUCCESS);

        ForceStopResult result = coordinator.forceStop(
                new ExecutionPlan(List.of(
                        new ExecutionStep(BackendKind.ROOT, false),
                        new ExecutionStep(BackendKind.SYSTEM_UI, false))),
                "com.example.reader",
                0);

        assertTrue(result.isSuccess());
        assertEquals(BackendKind.ROOT, result.backend());
        assertEquals(List.of(BackendKind.ROOT), calls);
    }

    @Test
    public void failedFirstAttemptUsesDocumentedFallback() {
        List<BackendKind> calls = new ArrayList<>();
        ForceStopCoordinator coordinator = coordinator(
                calls, BackendStatus.TRANSIENT_TRANSPORT_FAILURE, BackendStatus.SUCCESS);

        ForceStopResult result = coordinator.forceStop(
                new ExecutionPlan(List.of(
                        new ExecutionStep(BackendKind.ROOT, true),
                        new ExecutionStep(BackendKind.SYSTEM_UI, false))),
                "com.example.reader",
                10);

        assertTrue(result.isSuccess());
        assertEquals(BackendKind.SYSTEM_UI, result.backend());
        assertEquals(List.of(BackendKind.ROOT, BackendKind.SYSTEM_UI), calls);
    }

    @Test
    public void permissionRejectionAndUnsupportedAllowDocumentedFallback() {
        for (BackendStatus first : List.of(
                BackendStatus.PERMISSION_REJECTED,
                BackendStatus.UNSUPPORTED,
                BackendStatus.OPERATION_FAILED)) {
            List<BackendKind> calls = new ArrayList<>();
            ForceStopCoordinator coordinator = coordinator(calls, BackendStatus.SUCCESS, first);

            ForceStopResult result = coordinator.forceStop(
                    new ExecutionPlan(List.of(
                            new ExecutionStep(BackendKind.SYSTEM_UI, false),
                            new ExecutionStep(BackendKind.ROOT, false))),
                    "com.example.reader",
                    0);

            assertTrue(result.isSuccess());
            assertEquals(BackendKind.ROOT, result.backend());
        }
    }

    @Test
    public void reportsLastFailureWhenAllAttemptsFail() {
        ForceStopCoordinator coordinator = coordinator(
                new ArrayList<>(),
                BackendStatus.TRANSIENT_TRANSPORT_FAILURE,
                BackendStatus.PERMISSION_REJECTED);

        ForceStopResult result = coordinator.forceStop(
                new ExecutionPlan(List.of(
                        new ExecutionStep(BackendKind.SYSTEM_UI, false),
                        new ExecutionStep(BackendKind.ROOT, true))),
                "com.example.reader",
                0);

        assertFalse(result.isSuccess());
        assertEquals(BackendKind.ROOT, result.backend());
        assertEquals(BackendStatus.TRANSIENT_TRANSPORT_FAILURE, result.status());
    }

    @Test
    public void emptyPlanFailsWithoutCallingEitherBackend() {
        List<BackendKind> calls = new ArrayList<>();
        ForceStopResult result = coordinator(calls, BackendStatus.SUCCESS, BackendStatus.SUCCESS)
                .forceStop(new ExecutionPlan(List.of()), "com.example.reader", 0);

        assertFalse(result.isSuccess());
        assertEquals(null, result.backend());
        assertEquals(List.of(), calls);
    }

    private static ForceStopCoordinator coordinator(
            List<BackendKind> calls,
            BackendStatus rootStatus,
            BackendStatus systemUiStatus) {
        return new ForceStopCoordinator(
                (packageName, userId, waitForConnection) -> {
                    calls.add(BackendKind.ROOT);
                    return new BackendResult(BackendKind.ROOT, rootStatus, 3L);
                },
                (packageName, userId, waitForConnection) -> {
                    calls.add(BackendKind.SYSTEM_UI);
                    return new BackendResult(BackendKind.SYSTEM_UI, systemUiStatus, 2L);
                });
    }
}
