package com.wayne.statusbarforceclose;

import java.util.Objects;

final class SystemUiRuntimePolicy {
    ExecutionPlan select(
            ForceStopConfiguration configuration,
            RootConnectionState rootState,
            SystemUiCapability systemUiCapability) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(rootState, "rootState");
        Objects.requireNonNull(systemUiCapability, "systemUiCapability");
        if (!configuration.isUsable()) {
            return new ExecutionPlan(java.util.List.of());
        }

        return ForceStopPolicy.select(
                configuration.executionMode(),
                rootState == RootConnectionState.CONNECTED,
                rootCanWait(rootState),
                systemUiCapability.isAvailable());
    }

    private static boolean rootCanWait(RootConnectionState rootState) {
        return rootState == RootConnectionState.DISCONNECTED
                || rootState == RootConnectionState.CONNECTING
                || rootState == RootConnectionState.TRANSIENT_ERROR;
    }
}
