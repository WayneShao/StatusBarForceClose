package com.wayne.statusbarforceclose;

import java.util.Objects;

record BridgeStateSnapshot(
        ForceStopConfiguration configuration,
        RootAttemptJournal rootJournal,
        OptimizationJournal optimizationJournal) {
    BridgeStateSnapshot(
            ForceStopConfiguration configuration, RootAttemptJournal rootJournal) {
        this(configuration, rootJournal, OptimizationJournal.initial());
    }

    BridgeStateSnapshot {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(rootJournal, "rootJournal");
        Objects.requireNonNull(optimizationJournal, "optimizationJournal");
    }
}
