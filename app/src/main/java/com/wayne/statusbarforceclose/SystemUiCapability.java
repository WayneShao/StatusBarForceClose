package com.wayne.statusbarforceclose;

enum SystemUiCapability {
    AVAILABLE,
    UNAVAILABLE_UNSUPPORTED,
    FUSED_REJECTED;

    boolean isAvailable() {
        return this == AVAILABLE;
    }
}
