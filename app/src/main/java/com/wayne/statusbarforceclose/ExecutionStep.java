package com.wayne.statusbarforceclose;

import java.util.Objects;

record ExecutionStep(BackendKind backend, boolean waitForConnection) {
    ExecutionStep {
        Objects.requireNonNull(backend, "backend");
    }
}
