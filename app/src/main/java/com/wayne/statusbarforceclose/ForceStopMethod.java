package com.wayne.statusbarforceclose;

@FunctionalInterface
interface ForceStopMethod {
    BackendResult forceStop(String packageName, int userId, boolean waitForConnection);
}
