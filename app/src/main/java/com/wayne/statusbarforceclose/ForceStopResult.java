package com.wayne.statusbarforceclose;

record ForceStopResult(BackendKind backend, BackendStatus status, long elapsedMillis) {
    static ForceStopResult from(BackendResult result) {
        return new ForceStopResult(result.backend(), result.status(), result.elapsedMillis());
    }

    static ForceStopResult noBackend() {
        return new ForceStopResult(null, BackendStatus.OPERATION_FAILED, 0L);
    }

    boolean isSuccess() {
        return status == BackendStatus.SUCCESS;
    }
}
