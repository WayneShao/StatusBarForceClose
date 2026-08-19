package com.wayne.statusbarforceclose;

import java.util.Objects;

record BridgeRuntimeState(
        RootConnectionState rootState,
        boolean systemUiConnected,
        boolean backgroundOptimizationEnabled,
        SystemUiCapability systemUiCapability,
        LastExecutionRecord lastExecution) {
    BridgeRuntimeState {
        Objects.requireNonNull(rootState, "rootState");
        Objects.requireNonNull(systemUiCapability, "systemUiCapability");
        Objects.requireNonNull(lastExecution, "lastExecution");
    }
}
