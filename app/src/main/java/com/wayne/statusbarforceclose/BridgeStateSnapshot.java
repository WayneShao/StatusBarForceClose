package com.wayne.statusbarforceclose;

import java.util.Objects;

record BridgeStateSnapshot(
        ForceStopConfiguration configuration,
        RootAttemptJournal rootJournal,
        OptimizationJournal optimizationJournal,
        LastExecutionRecord lastExecution) {
    BridgeStateSnapshot(
            ForceStopConfiguration configuration, RootAttemptJournal rootJournal) {
        this(configuration, rootJournal, OptimizationJournal.initial(), LastExecutionRecord.none());
    }

    BridgeStateSnapshot(
            ForceStopConfiguration configuration,
            RootAttemptJournal rootJournal,
            OptimizationJournal optimizationJournal) {
        this(configuration, rootJournal, optimizationJournal, LastExecutionRecord.none());
    }

    BridgeStateSnapshot {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(rootJournal, "rootJournal");
        Objects.requireNonNull(optimizationJournal, "optimizationJournal");
        Objects.requireNonNull(lastExecution, "lastExecution");
    }
}
