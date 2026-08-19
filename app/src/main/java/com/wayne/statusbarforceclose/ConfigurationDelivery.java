package com.wayne.statusbarforceclose;

import java.util.Objects;

final class ConfigurationDelivery {
    private ForceStopConfiguration lastKnown = ForceStopConfiguration.unconfigured();

    synchronized ForceStopConfiguration onConfiguration(ForceStopConfiguration candidate) {
        lastKnown = lastKnown.acceptNewer(Objects.requireNonNull(candidate, "candidate"));
        return lastKnown;
    }

    synchronized ForceStopConfiguration onDisconnected() {
        return lastKnown;
    }
}
