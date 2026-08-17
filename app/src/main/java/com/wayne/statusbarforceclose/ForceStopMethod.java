package com.wayne.statusbarforceclose;

@FunctionalInterface
interface ForceStopMethod {
    boolean forceStop(String packageName, int userId);
}
