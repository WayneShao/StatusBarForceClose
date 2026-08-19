package com.wayne.statusbarforceclose;

import java.util.Objects;

record BridgeRuntimeState(
        RootConnectionState rootState,
        boolean systemUiConnected,
        boolean backgroundOptimizationEnabled) {
    BridgeRuntimeState {
        Objects.requireNonNull(rootState, "rootState");
    }
}
