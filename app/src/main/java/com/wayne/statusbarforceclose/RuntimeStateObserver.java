package com.wayne.statusbarforceclose;

@FunctionalInterface
interface RuntimeStateObserver {
    void onRuntimeState(BridgeRuntimeState state);
}
