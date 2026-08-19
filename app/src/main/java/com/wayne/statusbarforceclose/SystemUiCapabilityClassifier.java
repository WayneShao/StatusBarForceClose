package com.wayne.statusbarforceclose;

import java.util.Objects;

final class SystemUiCapabilityClassifier {
    interface ApiProbe {
        boolean isPresent();
    }

    private final ApiProbe apiProbe;
    private SystemUiCapability capability;

    SystemUiCapabilityClassifier(ApiProbe apiProbe) {
        this.apiProbe = Objects.requireNonNull(apiProbe, "apiProbe");
    }

    synchronized SystemUiCapability capability() {
        if (capability == null) {
            capability = apiProbe.isPresent()
                    ? SystemUiCapability.AVAILABLE
                    : SystemUiCapability.UNAVAILABLE_UNSUPPORTED;
        }
        return capability;
    }

    synchronized void record(BackendStatus status) {
        Objects.requireNonNull(status, "status");
        if (capability() == SystemUiCapability.AVAILABLE
                && status == BackendStatus.PERMISSION_REJECTED) {
            capability = SystemUiCapability.FUSED_REJECTED;
        }
    }
}
