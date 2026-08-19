package com.wayne.statusbarforceclose;

import java.util.Objects;

record BackendResult(BackendKind backend, BackendStatus status, long elapsedMillis) {
    BackendResult {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(status, "status");
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("elapsedMillis must not be negative");
        }
    }

    boolean isSuccess() {
        return status == BackendStatus.SUCCESS;
    }
}
