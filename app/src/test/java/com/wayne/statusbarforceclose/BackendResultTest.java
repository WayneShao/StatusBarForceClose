package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BackendResultTest {
    @Test
    public void onlySuccessIsSuccessful() {
        assertTrue(new BackendResult(BackendKind.ROOT, BackendStatus.SUCCESS, 12L).isSuccess());
        for (BackendStatus status : BackendStatus.values()) {
            if (status != BackendStatus.SUCCESS) {
                assertFalse(new BackendResult(BackendKind.SYSTEM_UI, status, 1L).isSuccess());
            }
        }
    }

    @Test
    public void rejectsNegativeElapsedTime() {
        assertThrows(IllegalArgumentException.class, () -> new BackendResult(
                BackendKind.ROOT, BackendStatus.OPERATION_FAILED, -1L));
    }

    @Test
    public void requiresBackendAndStatus() {
        assertThrows(NullPointerException.class, () -> new BackendResult(
                null, BackendStatus.SUCCESS, 0L));
        assertThrows(NullPointerException.class, () -> new BackendResult(
                BackendKind.ROOT, null, 0L));
    }
}
