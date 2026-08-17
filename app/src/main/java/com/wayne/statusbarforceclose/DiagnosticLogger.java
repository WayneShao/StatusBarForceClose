package com.wayne.statusbarforceclose;

interface DiagnosticLogger {
    void log(int priority, String event, String details, Throwable throwable);

    default void info(String event, String details) {
        log(android.util.Log.INFO, event, details, null);
    }

    default void warn(String event, String details) {
        log(android.util.Log.WARN, event, details, null);
    }

    default void error(String event, String details, Throwable throwable) {
        log(android.util.Log.ERROR, event, details, throwable);
    }
}
