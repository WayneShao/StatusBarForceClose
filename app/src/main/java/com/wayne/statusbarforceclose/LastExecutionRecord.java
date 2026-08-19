package com.wayne.statusbarforceclose;

record LastExecutionRecord(BackendKind backend, long elapsedMillis) {
    LastExecutionRecord {
        if (backend == null && elapsedMillis != 0L) {
            throw new IllegalArgumentException("Missing execution must have zero elapsed time");
        }
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("elapsedMillis must not be negative");
        }
    }

    static LastExecutionRecord none() {
        return new LastExecutionRecord(null, 0L);
    }

    static LastExecutionRecord successful(BackendKind backend, long elapsedMillis) {
        if (backend == null) {
            throw new IllegalArgumentException("backend must not be null");
        }
        return new LastExecutionRecord(backend, elapsedMillis);
    }

    boolean isPresent() {
        return backend != null;
    }
}
