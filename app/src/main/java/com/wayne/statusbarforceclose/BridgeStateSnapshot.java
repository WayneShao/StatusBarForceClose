package com.wayne.statusbarforceclose;

import java.util.Objects;

record BridgeStateSnapshot(
        ForceStopConfiguration configuration,
        RootAttemptJournal rootJournal) {
    BridgeStateSnapshot {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(rootJournal, "rootJournal");
    }
}
